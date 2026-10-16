package com.dev.urloader

import com.dev.urloader.core.common.PlatformDetector
import com.dev.urloader.core.common.SupportedPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDetectorTest {

    @Test
    fun detect_youtubeUrls_resolvesCorrectly() {
        val urls = listOf(
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com/shorts/F6D_NMaffno",
            "https://www.youtube.com/embed/dQw4w9WgXcQ"
        )
        for (url in urls) {
            assertEquals("Failed for $url", SupportedPlatform.YOUTUBE, PlatformDetector.detect(url))
        }
    }

    @Test
    fun detect_instagramUrls_resolvesCorrectly() {
        val urls = listOf(
            "https://www.instagram.com/reel/C_xyz123/",
            "https://instagram.com/p/DVF-0YeEViL/",
            "https://instagr.am/p/12345"
        )
        for (url in urls) {
            assertEquals("Failed for $url", SupportedPlatform.INSTAGRAM, PlatformDetector.detect(url))
        }
    }

    @Test
    fun detect_tiktokUrls_resolvesCorrectly() {
        val urls = listOf(
            "https://www.tiktok.com/@user/video/123456789",
            "https://vm.tiktok.com/ZM8abc123/",
            "https://m.tiktok.com/v/123456789.html"
        )
        for (url in urls) {
            assertEquals("Failed for $url", SupportedPlatform.TIKTOK, PlatformDetector.detect(url))
        }
    }

    @Test
    fun detect_twitterXUrls_resolvesCorrectly() {
        val urls = listOf(
            "https://twitter.com/Android/status/123456789",
            "https://x.com/Android/status/123456789",
            "https://mobile.twitter.com/user/status/123"
        )
        for (url in urls) {
            assertEquals("Failed for $url", SupportedPlatform.TWITTER_X, PlatformDetector.detect(url))
        }
    }

    @Test
    fun detect_redditUrls_resolvesCorrectly() {
        val urls = listOf(
            "https://reddit.com/r/videos/comments/xyz123/title/",
            "https://www.reddit.com/r/gifs/comments/abc456/",
            "https://redd.it/xyz123"
        )
        for (url in urls) {
            assertEquals("Failed for $url", SupportedPlatform.REDDIT, PlatformDetector.detect(url))
        }
    }

    @Test
    fun detect_genericAndEdgeCases_returnsGeneric() {
        assertEquals(SupportedPlatform.GENERIC, PlatformDetector.detect(null))
        assertEquals(SupportedPlatform.GENERIC, PlatformDetector.detect(""))
        assertEquals(SupportedPlatform.GENERIC, PlatformDetector.detect("   "))
        assertEquals(SupportedPlatform.GENERIC, PlatformDetector.detect("https://vimeo.com/123456"))
        assertEquals(SupportedPlatform.GENERIC, PlatformDetector.detect("https://dailymotion.com/video/x123"))
        assertEquals(SupportedPlatform.GENERIC, PlatformDetector.detect("not a url at all"))
    }
}
