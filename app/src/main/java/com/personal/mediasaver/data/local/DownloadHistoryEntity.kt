package com.personal.mediasaver.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personal.mediasaver.domain.model.DownloadStatus
import com.personal.mediasaver.domain.model.DownloadTask
import com.personal.mediasaver.domain.model.MediaType
import com.personal.mediasaver.domain.model.Platform

@Entity(
    tableName = "download_history",
    indices = [
        Index(value = ["taskId"], unique = true),
        Index(value = ["platform"]),
        Index(value = ["createdAt"]),
    ]
)
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val taskId: String,
    val sourceUrl: String,
    val platform: String,
    val title: String,
    val author: String,
    val mediaType: String,
    val thumbnailUrl: String,
    val status: String,
    val progress: Int = 0,
    val savedCount: Int = 0,
    val primaryContentUri: String = "",
    val primaryMimeType: String = "",
    val errorLog: String = "",
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toDomain(): DownloadTask = DownloadTask(
        id = id,
        taskId = taskId,
        sourceUrl = sourceUrl,
        platform = Platform.fromKey(platform),
        title = title,
        author = author,
        mediaType = MediaType.fromRaw(mediaType),
        thumbnailUrl = thumbnailUrl,
        status = DownloadStatus.fromRaw(status),
        progress = progress,
        savedCount = savedCount,
        primaryContentUri = primaryContentUri,
        primaryMimeType = primaryMimeType,
        errorLog = errorLog,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(task: DownloadTask): DownloadHistoryEntity = DownloadHistoryEntity(
            id = task.id,
            taskId = task.taskId,
            sourceUrl = task.sourceUrl,
            platform = task.platform.key,
            title = task.title,
            author = task.author,
            mediaType = task.mediaType.name,
            thumbnailUrl = task.thumbnailUrl,
            status = task.status.name,
            progress = task.progress,
            savedCount = task.savedCount,
            primaryContentUri = task.primaryContentUri,
            primaryMimeType = task.primaryMimeType,
            errorLog = task.errorLog,
            createdAt = task.createdAt,
        )
    }
}
