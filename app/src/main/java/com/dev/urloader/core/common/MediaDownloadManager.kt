package com.dev.urloader.core.common

import android.content.Context
import android.os.Environment
import com.dev.urloader.core.constants.AppConstants
import com.dev.urloader.core.constants.PrefKeys
import com.dev.urloader.feature.download.service.DownloadService
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class DownloadRecord(
    val id: Long,
    val url: String,
    val platform: SupportedPlatform,
    val filename: String,
    val timestamp: Long,
    val isAudioOnly: Boolean,
    val filePath: String? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("url", url)
            put("platform", platform.name)
            put("filename", filename)
            put("timestamp", timestamp)
            put("isAudioOnly", isAudioOnly)
            put("filePath", filePath ?: "")
        }
    }

    companion object {
        fun fromJson(json: JSONObject): DownloadRecord? {
            return try {
                val platformName = json.optString("platform", SupportedPlatform.GENERIC.name)
                val platform = try {
                    SupportedPlatform.valueOf(platformName)
                } catch (_: Exception) {
                    SupportedPlatform.GENERIC
                }
                DownloadRecord(
                    id = json.optLong("id", System.currentTimeMillis()),
                    url = json.optString("url", ""),
                    platform = platform,
                    filename = json.optString("filename", ""),
                    timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                    isAudioOnly = json.optBoolean("isAudioOnly", false),
                    filePath = json.optString("filePath").takeIf { it.isNotEmpty() }
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

data class LiveDownloadProgress(
    val url: String,
    val platform: SupportedPlatform,
    val progress: Int,
    val etaInSeconds: Long,
    val statusText: String,
    val isIndeterminate: Boolean
)

/**
 * Central download manager and persistence engine.
 *
 * Responsibilities:
 * - Dispatches downloads to [DownloadService].
 * - Publishes live download progress StateFlow for real-time UI binding.
 * - Manages verified local download history (max 100 items), backed by persistent storage.
 * - Performs background disk reconciliation with the `Downloads/URLoader` directory.
 */
object MediaDownloadManager {

    private val _recentDownloads = MutableStateFlow<List<DownloadRecord>>(emptyList())
    val recentDownloads: StateFlow<List<DownloadRecord>> = _recentDownloads.asStateFlow()

    private val _activeDownload = MutableStateFlow<LiveDownloadProgress?>(null)
    val activeDownload: StateFlow<LiveDownloadProgress?> = _activeDownload.asStateFlow()

    private val syncScope = CoroutineScope(Dispatchers.IO)

    /**
     * Starts the media extraction and download pipeline via DownloadService.
     * Uses native yt-dlp engine to extract media streams from YouTube, Instagram,
     * TikTok, X, Reddit, and 1,800+ other supported platforms.
     */
    fun startDownload(
        context: Context,
        url: String,
        isAudioOnly: Boolean = false
    ): Result<Unit> {
        return try {
            DownloadService.startDownload(context, url, isAudioOnly)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Requests cancellation of the currently active download.
     */
    fun cancelDownload(context: Context) {
        DownloadService.cancelDownload(context)
        _activeDownload.value = null
    }

    /**
     * Updates real-time progress for in-app Live Activity observing.
     */
    fun updateLiveProgress(progress: LiveDownloadProgress) {
        _activeDownload.value = progress
    }

    /**
     * Clears active progress when download completes, fails, or is cancelled.
     */
    fun clearLiveProgress() {
        _activeDownload.value = null
    }

    const val MAX_HISTORY_ITEMS = 100

    /**
     * Records a completed download in local persistent storage and memory, keeping max 100 items.
     */
    fun addCompletedDownload(context: Context, record: DownloadRecord) {
        val current = _recentDownloads.value
        val updated = (listOf(record) + current.filter { it.filename != record.filename }).take(MAX_HISTORY_ITEMS)
        _recentDownloads.value = updated
        _activeDownload.value = null
        saveHistory(context, updated)
    }

    fun addCompletedDownload(record: DownloadRecord) {
        val current = _recentDownloads.value
        val updated = (listOf(record) + current.filter { it.filename != record.filename }).take(MAX_HISTORY_ITEMS)
        _recentDownloads.value = updated
        _activeDownload.value = null
    }

    /**
     * Synchronizes memory state with both persistent SharedPreferences and physical
     * files present in the Downloads/URLoader directory.
     */
    fun syncWithDisk(context: Context) {
        syncScope.launch {
            try {
                val savedRecords = loadHistory(context).toMutableList()
                val knownFilenames = savedRecords.map { it.filename }.toMutableSet()

                val saveDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    AppConstants.DIRECTORY_URLOADER
                )

                if (saveDir.exists() && saveDir.isDirectory) {
                    val diskFiles = saveDir.listFiles { file ->
                        file.isFile && !file.name.endsWith(".part", ignoreCase = true) && !file.name.endsWith(".ytdl", ignoreCase = true)
                    }

                    if (diskFiles != null) {
                        for (file in diskFiles) {
                            if (!knownFilenames.contains(file.name)) {
                                val isAudio = file.name.endsWith(".mp3", ignoreCase = true) ||
                                        file.name.endsWith(".m4a", ignoreCase = true) ||
                                        file.name.endsWith(".aac", ignoreCase = true)
                                val detectedPlatform = PlatformDetector.detect(file.name)
                                val diskRecord = DownloadRecord(
                                    id = file.lastModified(),
                                    url = "",
                                    platform = detectedPlatform,
                                    filename = file.name,
                                    timestamp = file.lastModified(),
                                    isAudioOnly = isAudio,
                                    filePath = file.absolutePath
                                )
                                savedRecords.add(diskRecord)
                                knownFilenames.add(file.name)
                            }
                        }
                    }
                }

                // Verify file existence for records with filePath and limit to MAX_HISTORY_ITEMS
                val verifiedRecords = savedRecords.filter { record ->
                    val path = record.filePath
                    if (path != null) {
                        File(path).exists()
                    } else {
                        File(saveDir, record.filename).exists()
                    }
                }.sortedByDescending { it.timestamp }.take(MAX_HISTORY_ITEMS)

                _recentDownloads.value = verifiedRecords
                saveHistory(context, verifiedRecords)
            } catch (_: Exception) {
                // Graceful fallback to prevent crashes on edge file access conditions
            }
        }
    }

    fun loadHistory(context: Context): List<DownloadRecord> {
        return try {
            val prefs = context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(PrefKeys.KEY_DOWNLOAD_HISTORY, null) ?: return emptyList()
            val array = JSONArray(jsonString)
            val list = mutableListOf<DownloadRecord>()
            val maxCount = minOf(array.length(), MAX_HISTORY_ITEMS)
            for (i in 0 until maxCount) {
                val obj = array.optJSONObject(i) ?: continue
                DownloadRecord.fromJson(obj)?.let { list.add(it) }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveHistory(context: Context, records: List<DownloadRecord>) {
        try {
            val prefs = context.getSharedPreferences(PrefKeys.PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            val limited = records.take(MAX_HISTORY_ITEMS)
            for (record in limited) {
                array.put(record.toJson())
            }
            prefs.edit().putString(PrefKeys.KEY_DOWNLOAD_HISTORY, array.toString()).apply()
        } catch (_: Exception) {
            // Ignore write failures gracefully
        }
    }
}

