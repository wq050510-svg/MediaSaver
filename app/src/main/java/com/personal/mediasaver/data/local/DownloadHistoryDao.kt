package com.personal.mediasaver.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {

    @Query("SELECT * FROM download_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM download_history WHERE platform = :platform ORDER BY createdAt DESC")
    fun observeByPlatform(platform: String): Flow<List<DownloadHistoryEntity>>

    @Query("SELECT * FROM download_history WHERE taskId = :taskId LIMIT 1")
    suspend fun getByTaskId(taskId: String): DownloadHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadHistoryEntity): Long

    @Query(
        """
        UPDATE download_history
        SET status = :status, progress = :progress
        WHERE taskId = :taskId
        """
    )
    suspend fun updateProgress(taskId: String, status: String, progress: Int)

    @Query(
        """
        UPDATE download_history
        SET title = :title,
            author = :author,
            mediaType = :mediaType,
            thumbnailUrl = CASE WHEN :thumbnailUrl != '' THEN :thumbnailUrl ELSE thumbnailUrl END
        WHERE taskId = :taskId
        """
    )
    suspend fun updateMetadata(
        taskId: String,
        title: String,
        author: String,
        mediaType: String,
        thumbnailUrl: String,
    )

    @Query(
        """
        UPDATE download_history
        SET status = 'SUCCESS',
            progress = 100,
            savedCount = :savedCount,
            primaryContentUri = :primaryContentUri,
            primaryMimeType = :primaryMimeType,
            errorLog = ''
        WHERE taskId = :taskId
        """
    )
    suspend fun markSuccess(
        taskId: String,
        savedCount: Int,
        primaryContentUri: String,
        primaryMimeType: String,
    )

    @Query(
        """
        UPDATE download_history
        SET status = 'FAILED',
            errorLog = :errorLog
        WHERE taskId = :taskId
        """
    )
    suspend fun markFailed(taskId: String, errorLog: String)

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM download_history")
    suspend fun clearAll()
}
