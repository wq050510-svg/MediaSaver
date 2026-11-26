package com.personal.mediasaver.data.engine

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.personal.mediasaver.data.local.SettingsDataStore
import com.personal.mediasaver.domain.model.DownloadEngineResult
import com.personal.mediasaver.domain.model.EngineHealth
import com.personal.mediasaver.domain.model.ParsedMedia
import com.personal.mediasaver.domain.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

interface PyProgressCallback {
    fun onProgress(percent: Int)
}

class MediaEngineBridge(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun ensurePython(): Python {
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(context.applicationContext))
        }
        return Python.getInstance()
    }

    /**
     * Strictly resolves libffmpeg.so from nativeLibraryDir (Android 10+ W^X compliant).
     * Never checks or executes binaries under filesDir or cacheDir.
     */
    fun getFfmpegBinaryPath(): String? {
        val nativeDir = context.applicationInfo.nativeLibraryDir ?: return null
        val ffmpegFile = File(nativeDir, "libffmpeg.so")
        return if (ffmpegFile.exists() && ffmpegFile.isFile) ffmpegFile.absolutePath else null
    }

    suspend fun checkHealth(): EngineHealth = withContext(Dispatchers.IO) {
        val ffmpegPath = getFfmpegBinaryPath().orEmpty()
        runCatching {
            val router = ensurePython().getModule("router")
            val rawJson = router.callAttr("health_check", ffmpegPath).toString()
            json.decodeFromString<EngineHealth>(rawJson)
        }.getOrElse { err ->
            EngineHealth(
                status = "error",
                ffmpegAvailable = ffmpegPath.isNotBlank(),
                ffmpegPath = ffmpegPath,
                errorMessage = err.stackTraceToString(),
            )
        }
    }

    suspend fun parseUrl(
        url: String,
        platform: Platform,
    ): ParsedMedia = withContext(Dispatchers.IO) {
        val proxySettings = settingsDataStore.getProxySettings()
        val proxyUrl = proxySettings.resolveProxyFor(platform).orEmpty()
        val ffmpegPath = getFfmpegBinaryPath().orEmpty()

        runCatching {
            val router = ensurePython().getModule("router")
            val rawJson = router.callAttr(
                "parse",
                url,
                platform.name,
                "",
                proxyUrl,
                ffmpegPath,
            ).toString()
            json.decodeFromString<ParsedMedia>(rawJson)
        }.getOrElse { err ->
            ParsedMedia(
                status = "error",
                platform = platform.name,
                errorMessage = "Python 解析异常: ${err.message}\n${err.stackTraceToString()}",
            )
        }
    }

    suspend fun downloadMedia(
        url: String,
        platform: Platform,
        outputDir: File,
        formatId: String? = null,
        selectedIndices: List<Int>? = null,
        onProgress: (Int) -> Unit = {},
    ): DownloadEngineResult = withContext(Dispatchers.IO) {
        val proxySettings = settingsDataStore.getProxySettings()
        val proxyUrl = proxySettings.resolveProxyFor(platform).orEmpty()
        val ffmpegPath = getFfmpegBinaryPath().orEmpty()
        val indicesJson = if (!selectedIndices.isNullOrEmpty()) {
            json.encodeToString(selectedIndices)
        } else {
            ""
        }

        val callback = object : PyProgressCallback {
            override fun onProgress(percent: Int) {
                onProgress(percent)
            }
        }

        runCatching {
            outputDir.mkdirs()
            val router = ensurePython().getModule("router")
            val rawJson = router.callAttr(
                "download",
                url,
                outputDir.absolutePath,
                platform.name,
                "",
                proxyUrl,
                ffmpegPath,
                formatId.orEmpty(),
                indicesJson,
                callback,
            ).toString()
            json.decodeFromString<DownloadEngineResult>(rawJson)
        }.getOrElse { err ->
            DownloadEngineResult(
                status = "error",
                platform = platform.name,
                errorMessage = "Python 下载异常: ${err.message}\n${err.stackTraceToString()}",
            )
        }
    }
}
