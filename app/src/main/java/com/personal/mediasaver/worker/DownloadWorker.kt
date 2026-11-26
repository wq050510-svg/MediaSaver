package com.personal.mediasaver.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personal.mediasaver.MediaSaverApp
import com.personal.mediasaver.R
import com.personal.mediasaver.data.local.DownloadHistoryEntity
import com.personal.mediasaver.domain.model.DownloadStatus
import com.personal.mediasaver.domain.model.MediaType
import com.personal.mediasaver.domain.model.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val container
        get() = (applicationContext as MediaSaverApp).container

    private val notificationManager: NotificationManager
        get() = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: UUID.randomUUID().toString()
        val sourceUrl = inputData.getString(KEY_URL).orEmpty()
        val platform = Platform.fromKey(inputData.getString(KEY_PLATFORM))
        val initialTitle = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "${platform.displayName} 媒体" }
        val formatId = inputData.getString(KEY_FORMAT_ID).orEmpty().ifBlank { null }
        val selectedIndices = inputData.getIntArray(KEY_SELECTED_INDICES)?.toList()

        if (sourceUrl.isBlank()) {
            return Result.failure()
        }

        val notificationId = taskId.hashCode() and 0x7FFFFFFF
        val tempDir = File(applicationContext.cacheDir, "downloads/$taskId")
        val dao = container.downloadHistoryDao

        try {
            runCatching {
                setForeground(createForegroundInfo(notificationId, initialTitle, 0, "正在准备下载…"))
            }
            dao.updateProgress(taskId, DownloadStatus.DOWNLOADING.name, 2)

            val lastReportedProgress = AtomicInteger(-1)
            val lastReportedTime = AtomicLong(0L)
            val updateScope = CoroutineScope(Dispatchers.IO)

            val downloadResult = container.engineBridge.downloadMedia(
                url = sourceUrl,
                platform = platform,
                outputDir = tempDir,
                formatId = formatId,
                selectedIndices = selectedIndices,
                onProgress = { pct ->
                    val clamped = pct.coerceIn(0, 99)
                    val now = System.currentTimeMillis()
                    val prev = lastReportedProgress.get()
                    if (clamped >= prev + 4 || now - lastReportedTime.get() >= 500L) {
                        lastReportedProgress.set(clamped)
                        lastReportedTime.set(now)
                        updateScope.launch {
                            dao.updateProgress(taskId, DownloadStatus.DOWNLOADING.name, clamped)
                            runCatching {
                                setForeground(
                                    createForegroundInfo(
                                        notificationId = notificationId,
                                        title = initialTitle,
                                        progress = clamped,
                                        contentText = "正在下载 ($clamped%)",
                                    )
                                )
                            }
                        }
                    }
                },
            )

            if (!downloadResult.isSuccess) {
                val errLog = downloadResult.errorMessage.ifBlank { "未知下载错误，未获取到任何媒体文件。" }
                dao.markFailed(taskId, errLog)
                postFinalNotification(
                    notificationId = notificationId,
                    title = "下载失败：$initialTitle",
                    content = errLog.lineSequence().firstOrNull().orEmpty().take(120),
                )
                return Result.failure()
            }

            val finalTitle = downloadResult.title.ifBlank { initialTitle }
            val finalAuthor = downloadResult.author
            dao.updateMetadata(
                taskId = taskId,
                title = finalTitle,
                author = finalAuthor,
                mediaType = downloadResult.mediaType,
                thumbnailUrl = "",
            )
            dao.updateProgress(taskId, DownloadStatus.EXPORTING.name, 95)
            runCatching {
                setForeground(createForegroundInfo(notificationId, finalTitle, 95, "正在写入系统相册…"))
            }

            val exported = container.mediaStoreExporter.exportFiles(
                files = downloadResult.files,
                titleHint = finalTitle,
                platformKey = platform.key,
            )

            if (exported.isEmpty()) {
                val msg = "媒体文件已下载但写入系统相册失败。"
                dao.markFailed(taskId, msg)
                postFinalNotification(notificationId, "保存失败：$finalTitle", msg)
                return Result.failure()
            }

            val primary = exported.first()
            dao.markSuccess(
                taskId = taskId,
                savedCount = exported.size,
                primaryContentUri = primary.uri.toString(),
                primaryMimeType = primary.mimeType,
            )

            postFinalNotification(
                notificationId = notificationId,
                title = "已保存到相册：$finalTitle",
                content = "共保存 ${exported.size} 个媒体文件",
            )
            return Result.success()
        } catch (err: Throwable) {
            val log = err.stackTraceToString()
            dao.markFailed(taskId, log)
            postFinalNotification(
                notificationId = notificationId,
                title = "下载异常：$initialTitle",
                content = err.message ?: "请查看错误日志",
            )
            return Result.failure()
        } finally {
            // Strictly clean up cacheDir/downloads/{taskId}/
            container.mediaStoreExporter.cleanupTempDir(tempDir)
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String,
        progress: Int,
        contentText: String,
    ): ForegroundInfo {
        val notification = buildNotification(
            title = title,
            contentText = contentText,
            progress = progress,
            ongoing = true,
        )
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun buildNotification(
        title: String,
        contentText: String,
        progress: Int?,
        ongoing: Boolean,
    ): Notification {
        val builder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle(title.take(60))
            .setContentText(contentText)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress != null) {
            builder.setProgress(100, progress.coerceIn(0, 100), progress == 0)
        }
        return builder.build()
    }

    private fun postFinalNotification(notificationId: Int, title: String, content: String) {
        runCatching {
            val notification = buildNotification(
                title = title,
                contentText = content,
                progress = null,
                ongoing = false,
            )
            notificationManager.notify(notificationId + 1, notification)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "media_saver_downloads"

        private const val KEY_TASK_ID = "task_id"
        private const val KEY_URL = "url"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_TITLE = "title"
        private const val KEY_AUTHOR = "author"
        private const val KEY_MEDIA_TYPE = "media_type"
        private const val KEY_THUMBNAIL = "thumbnail"
        private const val KEY_FORMAT_ID = "format_id"
        private const val KEY_SELECTED_INDICES = "selected_indices"

        suspend fun enqueue(
            context: Context,
            url: String,
            platform: Platform,
            title: String,
            author: String,
            mediaType: MediaType,
            thumbnailUrl: String,
            formatId: String? = null,
            selectedIndices: List<Int>? = null,
        ): String {
            val app = context.applicationContext as MediaSaverApp
            val taskId = UUID.randomUUID().toString()

            app.container.downloadHistoryDao.upsert(
                DownloadHistoryEntity(
                    taskId = taskId,
                    sourceUrl = url,
                    platform = platform.key,
                    title = title.ifBlank { "${platform.displayName} 媒体" },
                    author = author,
                    mediaType = mediaType.name,
                    thumbnailUrl = thumbnailUrl,
                    status = DownloadStatus.QUEUED.name,
                    progress = 0,
                    createdAt = System.currentTimeMillis(),
                )
            )

            val inputBuilder = Data.Builder()
                .putString(KEY_TASK_ID, taskId)
                .putString(KEY_URL, url)
                .putString(KEY_PLATFORM, platform.key)
                .putString(KEY_TITLE, title)
                .putString(KEY_AUTHOR, author)
                .putString(KEY_MEDIA_TYPE, mediaType.name)
                .putString(KEY_THUMBNAIL, thumbnailUrl)

            if (!formatId.isNullOrBlank()) {
                inputBuilder.putString(KEY_FORMAT_ID, formatId)
            }
            if (!selectedIndices.isNullOrEmpty()) {
                inputBuilder.putIntArray(KEY_SELECTED_INDICES, selectedIndices.toIntArray())
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputBuilder.build())
                .setConstraints(constraints)
                .addTag("media_download_$taskId")
                .build()

            WorkManager.getInstance(context.applicationContext).enqueue(request)
            return taskId
        }
    }
}
