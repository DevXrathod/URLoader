package com.dev.urloader

import com.dev.urloader.core.common.PlatformDetector
import com.dev.urloader.core.common.SupportedPlatform
import com.dev.urloader.core.common.UrlExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UrlExtractorTest {

    @Test
    fun extractUrl_fromYouTubeShareText_stripsSiParameter() {
        val sharedText = "Check this out: https://youtu.be/dQw4w9WgXcQ?si=abcdef12345"
        val extracted = UrlExtractor.extractUrl(sharedText)

        assertNotNull(extracted)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", extracted)
    }

    @Test
    fun extractUrl_fromInstagramShareText_stripsIgshParameter() {
        val sharedText = "Reel: https://www.instagram.com/reel/C_xyz123/?igsh=abcdef=="
        val extracted = UrlExtractor.extractUrl(sharedText)

        assertNotNull(extracted)
        assertEquals("https://www.instagram.com/reel/C_xyz123/", extracted)
    }

    @Test
    fun extractUrl_fromTextWithTrailingPunctuation_cleansProperly() {
        val sharedText = "Watch this video: https://youtu.be/dQw4w9WgXcQ."
        val extracted = UrlExtractor.extractUrl(sharedText)

        assertNotNull(extracted)
        assertEquals("https://youtu.be/dQw4w9WgXcQ", extracted)

        val inParens = "Check (https://youtu.be/dQw4w9WgXcQ)"
    }

    @Test
    fun extractUrl_fromNullOrInvalidText_returnsNullDefensively() {
        assertNull(UrlExtractor.extractUrl(null))
        assertNull(UrlExtractor.extractUrl(""))
        assertNull(UrlExtractor.extractUrl("   "))
        assertNull(UrlExtractor.extractUrl("Hey check out this video but there is no link"))
    }

    @Test
    fun detectPlatform_correctlyIdentifiesPlatforms() {
        assertEquals(
            SupportedPlatform.YOUTUBE,
            PlatformDetector.detect("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            SupportedPlatform.YOUTUBE,
            PlatformDetector.detect("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals(
            SupportedPlatform.INSTAGRAM,
            PlatformDetector.detect("https://www.instagram.com/reel/C_xyz123/")
        )
        assertEquals(
            SupportedPlatform.TIKTOK,
            PlatformDetector.detect("https://www.tiktok.com/@user/video/123456789")
        )
        assertEquals(
            SupportedPlatform.TWITTER_X,
            PlatformDetector.detect("https://x.com/user/status/123456789")
        )
        assertEquals(
            SupportedPlatform.REDDIT,
            PlatformDetector.detect("https://reddit.com/r/videos/comments/xyz123/")
        )
        assertEquals(
            SupportedPlatform.GENERIC,
            PlatformDetector.detect("https://example.com/video.mp4")
        )
    }
}
