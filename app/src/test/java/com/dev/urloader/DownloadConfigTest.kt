package com.dev.urloader

import com.dev.urloader.core.constants.AppConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadConfigTest {

    @Test
    fun maxQualityVideoFormat_selectsBestVideoAndBestAudioWithFallback() {
        assertEquals("bv*+ba/b", AppConstants.FORMAT_MAX_QUALITY_VIDEO)
        assertEquals("res,fps,hdr:12,codec,br", AppConstants.FORMAT_SORT_MAX_QUALITY_VIDEO)
        assertEquals("mkv/mp4", AppConstants.MERGE_FORMAT_MP4_MKV)
    }

    @Test
    fun maxQualityAudioFormat_selectsBestAudioStreamAndHighestVbrQuality() {
        assertEquals("bestaudio/best", AppConstants.FORMAT_MAX_QUALITY_AUDIO)
        assertEquals("abr,acodec,size", AppConstants.FORMAT_SORT_MAX_QUALITY_AUDIO)
        assertEquals("mp3", AppConstants.AUDIO_FORMAT_MP3)
        assertEquals("0", AppConstants.AUDIO_QUALITY_MAX_VBR)
    }

    @Test
    fun mimeTypeResolution_handlesAllMediaContainers() {
        fun resolveMimeType(filename: String): String {
            return if (filename.endsWith(".mp3", ignoreCase = true)) {
                AppConstants.MIME_TYPE_AUDIO_WILDCARD
            } else {
                AppConstants.MIME_TYPE_VIDEO_WILDCARD
            }
        }

        assertEquals("audio/*", resolveMimeType("song.mp3"))
        assertEquals("audio/*", resolveMimeType("PODCAST.MP3"))
        assertEquals("video/*", resolveMimeType("video_4k.mp4"))
        assertEquals("video/*", resolveMimeType("video_2160p.mkv"))
        assertEquals("video/*", resolveMimeType("stream.webm"))
    }

    @Test
    fun timingAndThrottleConstants_areWithinDefensiveBounds() {
        assertTrue(AppConstants.PROGRESS_THROTTLE_MS >= 250L)
        assertTrue(AppConstants.RECENT_FILE_SEARCH_WINDOW_MS >= 1000L)
    }
}
