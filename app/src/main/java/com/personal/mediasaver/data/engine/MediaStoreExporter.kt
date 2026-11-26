package com.personal.mediasaver.data.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.personal.mediasaver.domain.model.DownloadedFileInfo
import com.personal.mediasaver.domain.model.MediaItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedMediaItem(
    val uri: Uri,
    val mimeType: String,
    val type: MediaItemType,
)

class MediaStoreExporter(private val context: Context) {

    suspend fun exportFiles(
        files: List<DownloadedFileInfo>,
        titleHint: String,
        platformKey: String,
    ): List<ExportedMediaItem> = withContext(Dispatchers.IO) {
        val exported = mutableListOf<ExportedMediaItem>()
        val safeBase = sanitizeFilename(titleHint).ifBlank { "media_$platformKey" }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        files.forEachIndexed { index, info ->
            val source = File(info.path)
            if (!source.exists() || !source.isFile || source.length() == 0L) return@forEachIndexed

            val ext = source.extension.lowercase(Locale.US).ifBlank {
                when (info.itemType) {
                    MediaItemType.VIDEO -> "mp4"
                    MediaItemType.IMAGE -> "jpg"
                    MediaItemType.AUDIO -> "m4a"
                }
            }
            val suffix = if (files.size > 1) "_${index + 1}" else ""
            val displayName = "${safeBase}_${timestamp}${suffix}.$ext"
            val mimeType = info.mimeType.ifBlank { defaultMimeType(info.itemType, ext) }

            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaScopedMediaStore(source, displayName, mimeType, info.itemType)
            } else {
                exportViaLegacyPublicDir(source, displayName, mimeType, info.itemType)
            }
            if (uri != null) {
                exported.add(ExportedMediaItem(uri = uri, mimeType = mimeType, type = info.itemType))
            }
        }
        exported
    }

    private fun exportViaScopedMediaStore(
        source: File,
        displayName: String,
        mimeType: String,
        type: MediaItemType,
    ): Uri? {
        val (collectionUri, relativePath) = when (type) {
            MediaItemType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/MediaSaver"
            MediaItemType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/MediaSaver"
            MediaItemType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/MediaSaver"
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val itemUri = resolver.insert(collectionUri, values) ?: return null
        return try {
            resolver.openOutputStream(itemUri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: error("无法打开系统相册写入流: $itemUri")

            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(itemUri, publishValues, null, null)
            itemUri
        } catch (err: Throwable) {
            resolver.delete(itemUri, null, null)
            throw err
        }
    }

    @Suppress("DEPRECATION")
    private fun exportViaLegacyPublicDir(
        source: File,
        displayName: String,
        mimeType: String,
        type: MediaItemType,
    ): Uri? {
        val rootDirType = when (type) {
            MediaItemType.VIDEO -> Environment.DIRECTORY_MOVIES
            MediaItemType.IMAGE -> Environment.DIRECTORY_PICTURES
            MediaItemType.AUDIO -> Environment.DIRECTORY_MUSIC
        }
        val targetDir = File(Environment.getExternalStoragePublicDirectory(rootDirType), "MediaSaver")
        targetDir.mkdirs()
        val targetFile = File(targetDir, displayName)
        source.inputStream().use { input ->
            targetFile.outputStream().use { out -> input.copyTo(out) }
        }
        MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf(mimeType),
            null
        )
        return Uri.fromFile(targetFile)
    }

    fun cleanupTempDir(dir: File) {
        runCatching {
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    private fun sanitizeFilename(raw: String): String {
        return raw.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
            .trim()
            .take(48)
    }

    private fun defaultMimeType(type: MediaItemType, ext: String): String = when (type) {
        MediaItemType.VIDEO -> if (ext == "webm") "video/webm" else "video/mp4"
        MediaItemType.IMAGE -> when (ext) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        MediaItemType.AUDIO -> if (ext == "mp3") "audio/mpeg" else "audio/mp4"
    }
}
