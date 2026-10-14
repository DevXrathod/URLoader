package com.dev.urloader.core.constants

object AppConstants {
    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "urloader_media_downloads"
    const val EXTRA_SHARED_URL = "com.dev.urloader.extra.SHARED_URL"
    const val EXTRA_AUDIO_ONLY = "com.dev.urloader.extra.AUDIO_ONLY"

    const val MIME_TYPE_PLAIN_TEXT = "text/plain"
    const val MIME_TYPE_VIDEO_MP4 = "video/mp4"

    const val DIRECTORY_URLOADER = "URLoader"

    const val ACTION_START_DOWNLOAD = "com.dev.urloader.action.START_DOWNLOAD"
    const val ACTION_CANCEL_DOWNLOAD = "com.dev.urloader.action.CANCEL_DOWNLOAD"
    const val NOTIFICATION_ID_FOREGROUND = 1001
    const val NOTIFICATION_ID_COMPLETE = 1002

    // Android 16 Live Updates / Promoted Ongoing Notification Key
    const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    // Default download timeouts and buffers
    const val CONNECT_TIMEOUT_MS = 15000
    const val READ_TIMEOUT_MS = 30000
    const val PROGRESS_THROTTLE_MS = 500L
    const val RECENT_FILE_SEARCH_WINDOW_MS = 5000L

    // MIME Types
    const val MIME_TYPE_AUDIO_WILDCARD = "audio/*"
    const val MIME_TYPE_VIDEO_WILDCARD = "video/*"

    // yt-dlp CLI Option Flags
    const val OPTION_OUTPUT = "-o"
    const val OPTION_FORMAT = "-f"
    const val OPTION_FORMAT_SORT = "-S"
    const val OPTION_MERGE_OUTPUT_FORMAT = "--merge-output-format"
    const val OPTION_EXTRACT_AUDIO = "-x"
    const val OPTION_AUDIO_FORMAT = "--audio-format"
    const val OPTION_AUDIO_QUALITY = "--audio-quality"
    const val OPTION_NO_MTIME = "--no-mtime"
    const val OPTION_NO_PLAYLIST = "--no-playlist"
    const val OPTION_RESTRICT_FILENAMES = "--restrict-filenames"
    const val OPTION_NO_WARNINGS = "--no-warnings"

    // Max Quality Format & Sorting Specifications
    // bv*+ba: best video-only + best audio-only (YouTube DASH/HLS), fallback to best combined.
    const val FORMAT_MAX_QUALITY_VIDEO = "bv*+ba/b"
    const val FORMAT_MAX_QUALITY_AUDIO = "bestaudio/best"
    // Prefer perceptual quality over file size. Sorting by size first often picks
    // bulky H.264 over smaller-but-better VP9/AV1 at the same resolution.
    const val FORMAT_SORT_MAX_QUALITY_VIDEO = "res,fps,hdr:12,codec,br"
    const val FORMAT_SORT_MAX_QUALITY_AUDIO = "abr,acodec,size"
    // MKV first so VP9/AV1 + Opus can remux without ffmpeg re-encoding.
    // MP4 is fallback when the codecs already fit that container.
    const val MERGE_FORMAT_MP4_MKV = "mkv/mp4"
    const val AUDIO_FORMAT_MP3 = "mp3"
    const val AUDIO_QUALITY_MAX_VBR = "0"
    const val OUTPUT_FILENAME_TEMPLATE = "%(title)s.%(ext)s"
}

