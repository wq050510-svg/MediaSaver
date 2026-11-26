package com.personal.mediasaver.domain.sanitizer

import com.personal.mediasaver.domain.model.Platform
import com.personal.mediasaver.domain.model.ProxySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlSanitizerTest {

    @Test
    fun `extracts and cleans Douyin short link from dirty share text`() {
        val dirty = "5.89 复制打开抖音，看看【旅行日记的作品】秋天的喀纳斯太美了！ https://v.douyin.com/iRNB8x9K/ 02/15 q@R.kP Hic:/"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.DOUYIN, result!!.platform)
        assertEquals("https://v.douyin.com/iRNB8x9K/", result.cleanUrl)
    }

    @Test
    fun `extracts Douyin note link and strips tracking query params`() {
        val dirty = "快看这个图文 https://www.douyin.com/note/7345678901234567890?previous_page=app_code_link&enter_from=share&utm_source=copy。"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.DOUYIN, result!!.platform)
        assertEquals("https://www.douyin.com/note/7345678901234567890", result.cleanUrl)
    }

    @Test
    fun `extracts Bilibili b23 short link from dirty share copy`() {
        val dirty = "【4K60帧演示视频-哔哩哔哩】 https://b23.tv/AbCdEfG?share_source=copy_web&vd_source=abcdef123456"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.BILIBILI, result!!.platform)
        assertEquals("https://b23.tv/AbCdEfG", result.cleanUrl)
    }

    @Test
    fun `preserves Bilibili multi-part page parameter while removing tracking params`() {
        val dirty = "推荐合集 https://www.bilibili.com/video/BV1GJ411x7h7/?p=3&spm_id_from=333.999.0.0&vd_source=12345&buvid=XY999"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.BILIBILI, result!!.platform)
        assertEquals("https://www.bilibili.com/video/BV1GJ411x7h7?p=3", result.cleanUrl)
    }

    @Test
    fun `extracts bare Bilibili BV id without scheme`() {
        val dirty = "这个视频不错，去搜一下 BV1xx411c7mD 看看！"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.BILIBILI, result!!.platform)
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD", result.cleanUrl)
    }

    @Test
    fun `extracts X link and strips s and t tracking parameters`() {
        val dirty = "Check out this thread: https://x.com/NASA/status/1845678901234567890?s=46&t=AbCdEfGhIjKlMnOpQrStUv!"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.X, result!!.platform)
        assertEquals("https://x.com/NASA/status/1845678901234567890", result.cleanUrl)
    }

    @Test
    fun `normalizes twitter and fxtwitter aliases to x platform`() {
        val dirty = "https://fxtwitter.com/elonmusk/status/1800000000000000000?s=20&ref_src=twsrc"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.X, result!!.platform)
        assertEquals("https://x.com/elonmusk/status/1800000000000000000", result.cleanUrl)
    }

    @Test
    fun `extracts Instagram reel link and strips igsh parameter`() {
        val dirty = "看看这个 Reel https://www.instagram.com/reel/C8xYzAb12Cd/?igsh=MzRlODBiNWFlZA==&utm_source=ig_web_copy_link"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.INSTAGRAM, result!!.platform)
        assertEquals("https://www.instagram.com/reel/C8xYzAb12Cd", result.cleanUrl)
    }

    @Test
    fun `preserves Instagram img_index for carousel post while stripping igsh`() {
        val dirty = "https://www.instagram.com/p/C9aBcDeFgHi/?img_index=2&igsh=abc123"
        val result = UrlSanitizer.extractAndSanitize(dirty)

        assertNotNull(result)
        assertEquals(Platform.INSTAGRAM, result!!.platform)
        assertEquals("https://www.instagram.com/p/C9aBcDeFgHi?img_index=2", result.cleanUrl)
    }

    @Test
    fun `returns UNKNOWN platform for unsupported url`() {
        val result = UrlSanitizer.extractAndSanitize("Visit https://example.com/page?utm_source=test")
        assertNotNull(result)
        assertEquals(Platform.UNKNOWN, result!!.platform)
        assertEquals("https://example.com/page", result.cleanUrl)
    }

    @Test
    fun `returns null for blank or text without links`() {
        assertNull(UrlSanitizer.extractAndSanitize(null))
        assertNull(UrlSanitizer.extractAndSanitize("   "))
        assertNull(UrlSanitizer.extractAndSanitize("这是一段没有链接的普通中文文本"))
    }

    @Test
    fun `proxy validation and split tunneling work as specified`() {
        assertTrue(ProxySettings.isValidProxyUrl("http://127.0.0.1:7890"))
        assertTrue(ProxySettings.isValidProxyUrl("socks5://127.0.0.1:10808"))
        assertTrue(ProxySettings.isValidProxyUrl("socks5h://192.168.1.5:7891"))
        assertFalse(ProxySettings.isValidProxyUrl("127.0.0.1:7890"))
        assertFalse(ProxySettings.isValidProxyUrl("ftp://127.0.0.1:21"))
        assertFalse(ProxySettings.isValidProxyUrl(""))

        val splitEnabled = ProxySettings(
            enabled = true,
            proxyUrl = "http://127.0.0.1:7890",
            onlyOverseas = true,
        )
        assertNull(splitEnabled.resolveProxyFor(Platform.DOUYIN))
        assertNull(splitEnabled.resolveProxyFor(Platform.BILIBILI))
        assertEquals("http://127.0.0.1:7890", splitEnabled.resolveProxyFor(Platform.X))
        assertEquals("http://127.0.0.1:7890", splitEnabled.resolveProxyFor(Platform.INSTAGRAM))

        val globalEnabled = splitEnabled.copy(onlyOverseas = false)
        assertEquals("http://127.0.0.1:7890", globalEnabled.resolveProxyFor(Platform.DOUYIN))
        assertEquals("http://127.0.0.1:7890", globalEnabled.resolveProxyFor(Platform.BILIBILI))
    }
}
