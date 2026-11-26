package com.personal.mediasaver.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadHistoryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class MediaSaverDatabase : RoomDatabase() {
    abstract fun downloadHistoryDao(): DownloadHistoryDao

    companion object {
        @Volatile
        private var instance: MediaSaverDatabase? = null

        fun getInstance(context: Context): MediaSaverDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MediaSaverDatabase::class.java,
                    "media_saver.db"
                ).fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
