package com.personal.mediasaver

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.personal.mediasaver.data.engine.MediaEngineBridge
import com.personal.mediasaver.data.engine.MediaStoreExporter
import com.personal.mediasaver.data.local.DownloadHistoryDao
import com.personal.mediasaver.data.local.MediaSaverDatabase
import com.personal.mediasaver.data.local.SettingsDataStore
import com.personal.mediasaver.worker.DownloadWorker

class AppContainer(application: Application) {
    val database: MediaSaverDatabase = MediaSaverDatabase.getInstance(application)
    val downloadHistoryDao: DownloadHistoryDao = database.downloadHistoryDao()
    val settingsDataStore: SettingsDataStore = SettingsDataStore(application)
    val engineBridge: MediaEngineBridge = MediaEngineBridge(
        context = application,
        settingsDataStore = settingsDataStore,
    )
    val mediaStoreExporter: MediaStoreExporter = MediaStoreExporter(application)
}

class MediaSaverApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        container = AppContainer(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                DownloadWorker.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_downloads_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_downloads_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
