package com.personal.mediasaver.domain.sanitizer

import com.personal.mediasaver.domain.model.Platform
import java.net.URI

data class SanitizedLink(
    val cleanUrl: String,
    val platform: Platform,
    val rawUrl: String,
)

object UrlSanitizer {

    private val URL_REGEX = Regex(
        pattern = """https?://[^\s\u3000"'<>()\[\]{}【】《》「」『』（）]+""",
        option = RegexOption.IGNORE_CASE
    )

    private val BARE_BV_REGEX = Regex(
        pattern = """\b(BV[0-9A-Za-z]{10})\b"""
    )

    private val TRAILING_PUNCTUATION = setOf(
        '.', ',', '!', '?', ';', ':', ')', ']', '}', '"', '\'',
        '，', '。', '！', '？', '；', '：', '）', '】', '》', '」', '』', '、'
    )

    private val COMMON_TRACKING_PARAMS = setOf(
        "igsh",
        "igshid",
        "s",
        "t",
        "spm_id_from",
        "from_spmid",
        "vd_source",
        "buvid",
        "is_story_h5",
        "mid",
        "plat_id",
        "share_from",
        "share_medium",
        "share_plat",
        "share_session_id",
        "share_source",
        "share_tag",
        "timestamp",
        "unique_k",
        "up_id",
        "bbid",
        "ts",
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "fbclid",
        "gclid",
        "si",
        "ref_src",
        "ref_url",
        "cxt",
        "u_code",
        "did",
        "iid",
        "with_sec_did",
        "enter_from",
        "previous_page",
        "extra_params",
        "from_ssr",
        "share_token",
        "sec_uid",
        "feature",
    )

    private val PLATFORM_PRESERVED_PARAMS: Map<Platform, Set<String>> = mapOf(
        Platform.BILIBILI to setOf("p", "bvid", "aid", "cid"),
        Platform.DOUYIN to setOf("modal_id"),
        Platform.INSTAGRAM to setOf("img_index"),
        Platform.X to emptySet(),
    )

    fun extractAndSanitize(rawText: String?): SanitizedLink? {
        if (rawText.isNullOrBlank()) return null

        val matches = URL_REGEX.findAll(rawText).map { cleanTrailing(it.value) }.toList()
        if (matches.isNotEmpty()) {
            // Prefer the first URL that maps to a supported platform; fall back to the first URL.
            val prioritized = matches.firstOrNull { detectPlatform(it) != Platform.UNKNOWN } ?: matches.first()
            val normalizedUrl = normalizeHostAliases(prioritized)
            val platform = detectPlatform(normalizedUrl)
            val cleaned = stripTrackingParameters(normalizedUrl, platform)
            return SanitizedLink(
                cleanUrl = cleaned,
                platform = platform,
                rawUrl = prioritized,
            )
        }

        // Fallback: detect bare Bilibili BV IDs (e.g., "看看这个 BV1GJ411x7h7")
        val bvMatch = BARE_BV_REGEX.find(rawText)
        if (bvMatch != null) {
            val bvid = bvMatch.groupValues[1]
            val url = "https://www.bilibili.com/video/$bvid"
            return SanitizedLink(
                cleanUrl = url,
                platform = Platform.BILIBILI,
                rawUrl = bvid,
            )
        }

        return null
    }

    fun detectPlatform(url: String): Platform {
        val host = parseHost(url)?.lowercase() ?: return Platform.UNKNOWN
        return when {
            host == "douyin.com" ||
                host.endsWith(".douyin.com") ||
                host == "iesdouyin.com" ||
                host.endsWith(".iesdouyin.com") -> Platform.DOUYIN

            host == "bilibili.com" ||
                host.endsWith(".bilibili.com") ||
                host == "b23.tv" ||
                host.endsWith(".b23.tv") ||
                host == "bili2233.cn" ||
                host.endsWith(".bili2233.cn") -> Platform.BILIBILI

            host == "x.com" ||
                host.endsWith(".x.com") ||
                host == "twitter.com" ||
                host.endsWith(".twitter.com") ||
                host == "vxtwitter.com" ||
                host == "fxtwitter.com" ||
                host == "fixupx.com" ||
                host == "t.co" -> Platform.X

            host == "instagram.com" ||
                host.endsWith(".instagram.com") ||
                host == "instagr.am" ||
                host == "ddinstagram.com" -> Platform.INSTAGRAM

            else -> Platform.UNKNOWN
        }
    }

    private fun normalizeHostAliases(url: String): String {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return url
        val host = parsed.host?.lowercase() ?: return url
        val normalizedHost = when (host) {
            "vxtwitter.com", "fxtwitter.com", "fixupx.com", "mobile.twitter.com" -> "x.com"
            "ddinstagram.com" -> "www.instagram.com"
            else -> return url
        }
        return runCatching {
            URI(
                parsed.scheme,
                parsed.userInfo,
                normalizedHost,
                parsed.port,
                parsed.path,
                parsed.query,
                null
            ).toASCIIString()
        }.getOrDefault(url)
    }

    private fun stripTrackingParameters(url: String, platform: Platform): String {
        val parsed = runCatching { URI(url) }.getOrNull() ?: return url
        val rawQuery = parsed.rawQuery
        val preservedForPlatform = PLATFORM_PRESERVED_PARAMS[platform].orEmpty()

        val filteredQuery = if (rawQuery.isNullOrBlank()) {
            null
        } else {
            val kept = rawQuery.split("&")
                .filter { it.isNotBlank() }
                .filter { pair ->
                    val key = pair.substringBefore("=").lowercase()
                    if (key in preservedForPlatform) {
                        true
                    } else if (key in COMMON_TRACKING_PARAMS || key.startsWith("utm_")) {
                        false
                    } else {
                        // For X and Instagram, drop unknown query noise except preserved params
                        platform != Platform.X && platform != Platform.INSTAGRAM
                    }
                }
            if (kept.isEmpty()) null else kept.joinToString("&")
        }

        val scheme = parsed.scheme?.lowercase() ?: "https"
        val host = parsed.host ?: return url
        val port = if (parsed.port != -1 &&
            !((scheme == "http" && parsed.port == 80) || (scheme == "https" && parsed.port == 443))
        ) {
            ":${parsed.port}"
        } else {
            ""
        }
        val path = parsed.rawPath.orEmpty().let { rawPath ->
            if (rawPath.length > 1 && rawPath.endsWith("/") && platform != Platform.DOUYIN) {
                rawPath.trimEnd('/')
            } else {
                rawPath
            }
        }
        return buildString {
            append(scheme).append("://").append(host).append(port).append(path)
            if (!filteredQuery.isNullOrBlank()) {
                append('?').append(filteredQuery)
            }
        }
    }

    private fun cleanTrailing(raw: String): String {
        var result = raw.trim()
        while (result.isNotEmpty() && result.last() in TRAILING_PUNCTUATION) {
            result = result.dropLast(1)
        }
        return result
    }

    private fun parseHost(url: String): String? {
        return runCatching { URI(url).host }.getOrNull()
    }
}
