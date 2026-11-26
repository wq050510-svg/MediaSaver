package com.personal.mediasaver.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class MediaType {
    VIDEO,
    GALLERY,
    MIXED;

    companion object {
        fun fromRaw(raw: String?): MediaType = when (raw?.trim()?.uppercase()) {
            "GALLERY" -> GALLERY
            "MIXED" -> MIXED
            else -> VIDEO
        }
    }
}

enum class MediaItemType {
    VIDEO,
    IMAGE,
    AUDIO;

    companion object {
        fun fromRaw(raw: String?): MediaItemType = when (raw?.trim()?.uppercase()) {
            "IMAGE" -> IMAGE
            "AUDIO" -> AUDIO
            else -> VIDEO
        }
    }
}

@Serializable
data class MediaItem(
    val url: String = "",
    val type: String = "VIDEO",
    val thumbnail: String = "",
    val quality: String = "",
    @SerialName("format_id")
    val formatId: String = "",
    val ext: String = "",
) {
    val itemType: MediaItemType
        get() = MediaItemType.fromRaw(type)
}

@Serializable
data class ParsedMedia(
    val status: String = "error",
    val platform: String = "UNKNOWN",
    val title: String = "",
    val author: String = "",
    @SerialName("media_type")
    val mediaType: String = "VIDEO",
    val items: List<MediaItem> = emptyList(),
    @SerialName("error_message")
    val errorMessage: String = "",
    @SerialName("resolved_url")
    val resolvedUrl: String = "",
    @SerialName("thumbnail")
    val coverThumbnail: String = "",
) {
    val isSuccess: Boolean
        get() = status.equals("ok", ignoreCase = true)

    val platformEnum: Platform
        get() = Platform.fromKey(platform)

    val mediaTypeEnum: MediaType
        get() = MediaType.fromRaw(mediaType)

    val bestCover: String
        get() = coverThumbnail.ifBlank {
            items.firstOrNull { it.thumbnail.isNotBlank() }?.thumbnail
                ?: items.firstOrNull { it.itemType == MediaItemType.IMAGE && it.url.isNotBlank() }?.url
                ?: ""
        }
}

@Serializable
data class DownloadedFileInfo(
    val path: String = "",
    val type: String = "VIDEO",
    @SerialName("mime_type")
    val mimeType: String = "",
) {
    val itemType: MediaItemType
        get() = MediaItemType.fromRaw(type)
}

@Serializable
data class DownloadEngineResult(
    val status: String = "error",
    val platform: String = "UNKNOWN",
    val title: String = "",
    val author: String = "",
    @SerialName("media_type")
    val mediaType: String = "VIDEO",
    val items: List<MediaItem> = emptyList(),
    val files: List<DownloadedFileInfo> = emptyList(),
    @SerialName("error_message")
    val errorMessage: String = "",
) {
    val isSuccess: Boolean
        get() = status.equals("ok", ignoreCase = true) && files.isNotEmpty()
}

@Serializable
data class EngineHealth(
    val status: String = "error",
    @SerialName("python_version")
    val pythonVersion: String = "Unknown",
    @SerialName("yt_dlp_version")
    val ytDlpVersion: String = "Unknown",
    @SerialName("gallery_dl_version")
    val galleryDlVersion: String = "Unknown",
    @SerialName("requests_version")
    val requestsVersion: String = "Unknown",
    @SerialName("ffmpeg_available")
    val ffmpegAvailable: Boolean = false,
    @SerialName("ffmpeg_path")
    val ffmpegPath: String = "",
    @SerialName("error_message")
    val errorMessage: String = "",
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    EXPORTING,
    SUCCESS,
    FAILED;

    companion object {
        fun fromRaw(raw: String?): DownloadStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: FAILED
    }
}

data class DownloadTask(
    val id: Long = 0L,
    val taskId: String,
    val sourceUrl: String,
    val platform: Platform,
    val title: String,
    val author: String,
    val mediaType: MediaType,
    val thumbnailUrl: String,
    val status: DownloadStatus,
    val progress: Int = 0,
    val savedCount: Int = 0,
    val primaryContentUri: String = "",
    val primaryMimeType: String = "",
    val errorLog: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

data class ProxySettings(
    val enabled: Boolean = false,
    val proxyUrl: String = "",
    val onlyOverseas: Boolean = true,
) {
    fun resolveProxyFor(platform: Platform): String? {
        if (!enabled) return null
        val trimmed = proxyUrl.trim()
        if (!isValidProxyUrl(trimmed)) return null
        if (onlyOverseas && !platform.defaultOverseasProxy) return null
        return trimmed
    }

    companion object {
        private val PROXY_REGEX = Regex(
            pattern = "^(https?|socks5h?)://[^\\s/@]+(:[^\\s/@]+)?@?[^\\s/:]+:\\d{1,5}/?$",
            option = RegexOption.IGNORE_CASE
        )

        fun isValidProxyUrl(input: String): Boolean {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return false
            val lower = trimmed.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("socks5://") && !lower.startsWith("socks5h://")) {
                return false
            }
            return PROXY_REGEX.matches(trimmed)
        }
    }
}

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "夜晚");

    companion object {
        fun fromKey(raw: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: SYSTEM
    }
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
)
