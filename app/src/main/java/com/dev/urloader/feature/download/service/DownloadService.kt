package com.dev.urloader.feature.download.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.dev.urloader.R
import com.dev.urloader.core.common.DownloadRecord
import com.dev.urloader.core.common.LiveDownloadProgress
import com.dev.urloader.core.common.MediaDownloadManager
import com.dev.urloader.core.common.PlatformDetector
import com.dev.urloader.core.constants.AppConstants
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "DownloadService"

/**
 * Foreground Service responsible for media stream extraction and downloading via yt-dlp & FFmpeg.
 *
 * Key Capabilities:
 * - Runs as a Foreground Service with FOREGROUND_SERVICE_TYPE_DATA_SYNC (compliant with SDK 34-36).
 * - Android 16 Live Activity promoted ongoing notification integration.
 * - Self-healing pipeline: automatically updates yt-dlp nightly and retries upon failure.
 * - Asynchronous cancellation destroying native process by process ID.
 * - MediaScanner integration for immediate Android system media store indexing.
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    private lateinit var notificationManager: NotificationManager

    @Volatile
    private var currentProcessId: String? = null

    @Volatile
    private var isCancelled: Boolean = false

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent.action == AppConstants.ACTION_CANCEL_DOWNLOAD) {
            handleCancel()
            return START_NOT_STICKY
        }

        if (intent.action != AppConstants.ACTION_START_DOWNLOAD) {
            stopSelf()
            return START_NOT_STICKY
        }

        val url = intent.getStringExtra(AppConstants.EXTRA_SHARED_URL)
        val isAudioOnly = intent.getBooleanExtra(AppConstants.EXTRA_AUDIO_ONLY, false)

        if (url.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        isCancelled = false
        val platform = PlatformDetector.detect(url)
        val platformName = getString(platform.displayNameRes)

        val initialNotification = buildProgressNotification(
            title = getString(R.string.notification_downloading, platformName),
            content = getString(R.string.notification_download_preparing),
            progress = 0,
            isIndeterminate = true
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    AppConstants.NOTIFICATION_ID_FOREGROUND,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(AppConstants.NOTIFICATION_ID_FOREGROUND, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not start foreground service", e)
        }

        MediaDownloadManager.updateLiveProgress(
            LiveDownloadProgress(
                url = url,
                platform = platform,
                progress = 0,
                etaInSeconds = 0,
                statusText = getString(R.string.notification_download_preparing),
                isIndeterminate = true
            )
        )

        downloadJob?.cancel()
        downloadJob = serviceScope.launch {
            executeDownloadWithAutoUpdate(url, isAudioOnly, platformName)
        }

        return START_NOT_STICKY
    }

    private fun handleCancel() {
        isCancelled = true
        currentProcessId?.let { id ->
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying process $id: ${e.message}")
            }
        }
        downloadJob?.cancel()
        MediaDownloadManager.clearLiveProgress()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(AppConstants.NOTIFICATION_ID_FOREGROUND)
        stopSelf()
    }

    private fun executeDownloadWithAutoUpdate(
        url: String,
        isAudioOnly: Boolean,
        platformName: String
    ) {
        val downloadStartTime = System.currentTimeMillis()

        try {
            YoutubeDL.getInstance().init(applicationContext)
            FFmpeg.getInstance().init(applicationContext)
        } catch (_: Exception) {
            // Already initialized
        }

        val saveDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            AppConstants.DIRECTORY_URLOADER
        )
        if (!saveDir.exists()) {
            saveDir.mkdirs()
        }

        val processId = "dl_${System.currentTimeMillis()}"
        currentProcessId = processId

        fun createRequest(): YoutubeDLRequest {
            return YoutubeDLRequest(url).apply {
                addOption(AppConstants.OPTION_OUTPUT, "${saveDir.absolutePath}/${AppConstants.OUTPUT_FILENAME_TEMPLATE}")
                addOption(AppConstants.OPTION_NO_MTIME)
                addOption(AppConstants.OPTION_NO_PLAYLIST)
                addOption(AppConstants.OPTION_RESTRICT_FILENAMES)
                addOption(AppConstants.OPTION_NO_WARNINGS)
                if (isAudioOnly) {
                    addOption(AppConstants.OPTION_FORMAT, AppConstants.FORMAT_MAX_QUALITY_AUDIO)
                    addOption(AppConstants.OPTION_FORMAT_SORT, AppConstants.FORMAT_SORT_MAX_QUALITY_AUDIO)
                    addOption(AppConstants.OPTION_EXTRACT_AUDIO)
                    addOption(AppConstants.OPTION_AUDIO_FORMAT, AppConstants.AUDIO_FORMAT_MP3)
                    addOption(AppConstants.OPTION_AUDIO_QUALITY, AppConstants.AUDIO_QUALITY_MAX_VBR)
                } else {
                    addOption(AppConstants.OPTION_FORMAT, AppConstants.FORMAT_MAX_QUALITY_VIDEO)
                    addOption(AppConstants.OPTION_FORMAT_SORT, AppConstants.FORMAT_SORT_MAX_QUALITY_VIDEO)
                    addOption(AppConstants.OPTION_MERGE_OUTPUT_FORMAT, AppConstants.MERGE_FORMAT_MP4_MKV)
                }
            }
        }

        var executionError: Exception? = null

        // Attempt 1: Execute download
        val firstAttemptSuccess = try {
            runYoutubeDlExecution(createRequest(), processId, platformName, url)
            true
        } catch (e: Exception) {
            executionError = e
            false
        }

        if (isCancelled) return

        // If attempt 1 fails, auto-update from GitHub and retry
        if (!firstAttemptSuccess) {
            Log.w(TAG, "First download attempt failed. Auto-updating engine and retrying...", executionError)

            val updateNotification = buildProgressNotification(
                title = getString(R.string.notification_downloading, platformName),
                content = getString(R.string.notification_download_updating_engine),
                progress = 0,
                isIndeterminate = true
            )
            notificationManager.notify(AppConstants.NOTIFICATION_ID_FOREGROUND, updateNotification)

            try {
                YoutubeDL.getInstance().updateYoutubeDL(
                    applicationContext,
                    YoutubeDL.UpdateChannel.NIGHTLY
                )
            } catch (updateEx: Exception) {
                Log.e(TAG, "Engine update attempt failed", updateEx)
            }

            if (isCancelled) return

            // Attempt 2: Retry with updated engine
            try {
                val retryProcessId = "dl_retry_${System.currentTimeMillis()}"
                currentProcessId = retryProcessId
                runYoutubeDlExecution(createRequest(), retryProcessId, platformName, url)
                executionError = null
            } catch (retryEx: Exception) {
                executionError = retryEx
            }
        }

        if (isCancelled) return

        try {
            if (executionError == null) {
                val downloadedFiles = saveDir.listFiles { file ->
                    file.isFile && file.lastModified() >= (downloadStartTime - AppConstants.RECENT_FILE_SEARCH_WINDOW_MS)
                }
                val newestFile = downloadedFiles?.maxByOrNull { it.lastModified() }

                if (newestFile != null) {
                    MediaScannerConnection.scanFile(
                        this,
                        arrayOf(newestFile.absolutePath),
                        null,
                        null
                    )
                }

                val filename = newestFile?.name ?: "${AppConstants.DIRECTORY_URLOADER}_media"
                val platform = PlatformDetector.detect(url)
                val record = DownloadRecord(
                    id = System.currentTimeMillis(),
                    url = url,
                    platform = platform,
                    filename = filename,
                    timestamp = System.currentTimeMillis(),
                    isAudioOnly = isAudioOnly,
                    filePath = newestFile?.absolutePath
                )
                MediaDownloadManager.addCompletedDownload(applicationContext, record)

                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(AppConstants.NOTIFICATION_ID_FOREGROUND)
                showCompletionNotification(filename, newestFile)
            } else {
                Log.e(TAG, "Download failed for url: $url", executionError)
                MediaDownloadManager.clearLiveProgress()
                stopForeground(STOP_FOREGROUND_REMOVE)
                notificationManager.cancel(AppConstants.NOTIFICATION_ID_FOREGROUND)
                showFailureNotification(cleanErrorMessage(executionError?.message))
            }
        } finally {
            stopSelf()
        }
    }

    private fun runYoutubeDlExecution(
        request: YoutubeDLRequest,
        processId: String,
        platformName: String,
        url: String
    ) {
        var lastUpdateMillis = 0L
        val platform = PlatformDetector.detect(url)

        YoutubeDL.getInstance().execute(request, processId) { progress, etaInSeconds, _ ->
            if (isCancelled) return@execute

            val currentMillis = System.currentTimeMillis()
            if (currentMillis - lastUpdateMillis > AppConstants.PROGRESS_THROTTLE_MS) {
                lastUpdateMillis = currentMillis
                val progressInt = progress.toInt().coerceIn(0, 100)
                val contentText = getString(R.string.notification_download_progress, progressInt, etaInSeconds)

                // Update system Live Activity / notification
                val notification = buildProgressNotification(
                    title = getString(R.string.notification_downloading, platformName),
                    content = contentText,
                    progress = progressInt,
                    isIndeterminate = progressInt == 0
                )
                notificationManager.notify(AppConstants.NOTIFICATION_ID_FOREGROUND, notification)

                // Update in-app Live Activity state
                MediaDownloadManager.updateLiveProgress(
                    LiveDownloadProgress(
                        url = url,
                        platform = platform,
                        progress = progressInt,
                        etaInSeconds = etaInSeconds,
                        statusText = contentText,
                        isIndeterminate = progressInt == 0
                    )
                )
            }
        }
    }

    private fun cleanErrorMessage(rawMessage: String?): String {
        if (rawMessage.isNullOrBlank()) return getString(R.string.notification_failed)

        val lines = rawMessage.lines().map { it.trim() }.filter { it.isNotBlank() }
        val errorLine = lines.find { it.startsWith("ERROR:", ignoreCase = true) }
        if (errorLine != null) {
            return errorLine.substringAfter("ERROR:").trim()
        }

        val nonWarningLine = lines.find { !it.startsWith("WARNING:", ignoreCase = true) }
        if (nonWarningLine != null) {
            return nonWarningLine
        }

        return lines.firstOrNull() ?: getString(R.string.notification_failed)
    }

    private fun buildProgressNotification(
        title: String,
        content: String,
        progress: Int,
        isIndeterminate: Boolean
    ): Notification {
        val cancelIntent = Intent(this, DownloadService::class.java).apply {
            action = AppConstants.ACTION_CANCEL_DOWNLOAD
        }
        val pendingCancel = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, AppConstants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(content)
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(content)
            )
            .addAction(R.drawable.ic_close, getString(R.string.live_activity_cancel), pendingCancel)

        // Request Android 16 Live Update chip in the status bar & prominent placement
        builder.extras.putBoolean(AppConstants.EXTRA_REQUEST_PROMOTED_ONGOING, true)

        return builder.build()
    }

    private fun showCompletionNotification(title: String, file: File?) {
        val openIntent = if (file != null && file.exists()) {
            try {
                val contentUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.provider",
                    file
                )
                val mimeType = if (file.name.endsWith(".mp3", ignoreCase = true)) {
                    AppConstants.MIME_TYPE_AUDIO_WILDCARD
                } else {
                    AppConstants.MIME_TYPE_VIDEO_WILDCARD
                }
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contentUri, mimeType)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not create FileProvider URI", e)
                null
            }
        } else {
            null
        }

        val pendingIntent = openIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, AppConstants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(getString(R.string.notification_complete, title))
            .setContentText(getString(R.string.notification_download_saved_to))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(AppConstants.NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun showFailureNotification(errorMessage: String) {
        val notification = NotificationCompat.Builder(this, AppConstants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_close)
            .setContentTitle(getString(R.string.notification_failed))
            .setContentText(errorMessage)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(AppConstants.NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConstants.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun startDownload(context: Context, url: String, isAudioOnly: Boolean = false) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = AppConstants.ACTION_START_DOWNLOAD
                putExtra(AppConstants.EXTRA_SHARED_URL, url)
                putExtra(AppConstants.EXTRA_AUDIO_ONLY, isAudioOnly)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelDownload(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = AppConstants.ACTION_CANCEL_DOWNLOAD
            }
            context.startService(intent)
        }
    }
}
