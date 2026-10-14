package com.dev.urloader

import android.app.Application
import android.util.Log
import com.dev.urloader.core.common.MediaDownloadManager
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "URLoaderApp"

class URLoaderApp : Application() {

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        MediaDownloadManager.syncWithDisk(this)
        initDownloadEngine()
    }

    private fun initDownloadEngine() {
        try {
            YoutubeDL.getInstance().init(this)
            FFmpeg.getInstance().init(this)

            // Proactively update yt-dlp to latest nightly/stable release in background
            appScope.launch {
                try {
                    val status = YoutubeDL.getInstance().updateYoutubeDL(
                        applicationContext,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                    Log.d(TAG, "Engine auto-update status: $status")
                } catch (e: Exception) {
                    Log.w(TAG, "Background engine update check: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Engine initialization error: ${e.message}", e)
        }
    }
}
