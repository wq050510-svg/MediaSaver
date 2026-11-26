package com.personal.mediasaver.domain.model

enum class Platform(
    val key: String,
    val displayName: String,
    val defaultOverseasProxy: Boolean,
) {
    DOUYIN(
        key = "douyin",
        displayName = "抖音",
        defaultOverseasProxy = false,
    ),
    BILIBILI(
        key = "bilibili",
        displayName = "B站",
        defaultOverseasProxy = false,
    ),
    X(
        key = "x",
        displayName = "X (Twitter)",
        defaultOverseasProxy = true,
    ),
    INSTAGRAM(
        key = "instagram",
        displayName = "Instagram",
        defaultOverseasProxy = true,
    ),
    UNKNOWN(
        key = "unknown",
        displayName = "未知平台",
        defaultOverseasProxy = false,
    );

    companion object {
        val supportedPlatforms: List<Platform> = listOf(DOUYIN, BILIBILI, X, INSTAGRAM)

        fun fromKey(value: String?): Platform {
            if (value.isNullOrBlank()) return UNKNOWN
            val normalized = value.trim().lowercase()
            return entries.firstOrNull {
                it.key == normalized || it.name.lowercase() == normalized
            } ?: UNKNOWN
        }
    }
}
