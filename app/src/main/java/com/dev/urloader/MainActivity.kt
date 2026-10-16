package com.dev.urloader

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.dev.urloader.core.common.DownloadRecord
import com.dev.urloader.core.common.MediaDownloadManager
import com.dev.urloader.core.common.PermissionUtils
import com.dev.urloader.core.common.UrlExtractor
import com.dev.urloader.core.constants.AppConstants
import com.dev.urloader.databinding.ActivityMainBinding
import com.dev.urloader.feature.download.ui.DownloadHistoryAdapter
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Primary dashboard hub providing:
 * - Direct URL pasting & format selection (Video / Audio only).
 * - Real-time Live Activity download progress monitoring.
 * - Download library management with instant FileProvider media launching.
 * - Engine version management and manual update controls.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var downloadHistoryAdapter: DownloadHistoryAdapter

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                binding.cardPermission.visibility = View.GONE
                Toast.makeText(this, getString(R.string.home_permission_granted), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupPermissionBanner()
        setupDirectDownloader()
        setupEngineManagement()
        setupRecentDownloads()
        observeActiveDownload()
        observeRecentDownloads()
    }

    override fun onStart() {
        super.onStart()
        binding.blurMeshBackground.startAnimation()
    }

    override fun onResume() {
        super.onResume()
        refreshClipboardChip()
        MediaDownloadManager.syncWithDisk(this)
    }

    override fun onStop() {
        super.onStop()
        binding.blurMeshBackground.stopAnimation()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBars.top,
                v.paddingRight,
                systemBars.bottom
            )
            insets
        }
    }

    private fun setupPermissionBanner() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = PermissionUtils.hasNotificationPermission(this)
            binding.cardPermission.visibility = if (hasPermission) View.GONE else View.VISIBLE
            binding.btnGrantPermission.setOnClickListener {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            binding.cardPermission.visibility = View.GONE
        }
    }

    private fun setupDirectDownloader() {
        binding.tilInputUrl.setEndIconOnClickListener {
            pasteFromClipboard()
        }

        binding.btnDirectDownload.setOnClickListener {
            triggerDownload(isAudioOnly = false)
        }
        binding.btnDirectAudio.setOnClickListener {
            triggerDownload(isAudioOnly = true)
        }
        binding.chipPasteClipboard.setOnClickListener {
            pasteFromClipboard()
        }
        binding.etInputUrl.setOnFocusChangeListener { _, _ -> refreshClipboardChip() }
    }

    private fun refreshClipboardChip() {
        val clipboardUrl = clipboardUrl()
        val fieldEmpty = binding.etInputUrl.text.isNullOrBlank()
        binding.chipPasteClipboard.visibility =
            if (!clipboardUrl.isNullOrBlank() && fieldEmpty) View.VISIBLE else View.GONE
    }

    private fun pasteFromClipboard() {
        val pastedText = clipboardText()
        val extractedUrl = UrlExtractor.extractUrl(pastedText)
        if (!extractedUrl.isNullOrBlank()) {
            binding.etInputUrl.setText(extractedUrl)
        } else if (!pastedText.isNullOrBlank()) {
            binding.etInputUrl.setText(pastedText)
        }
        binding.chipPasteClipboard.visibility = View.GONE
    }

    private fun clipboardText(): String? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        return clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
    }

    private fun clipboardUrl(): String? = UrlExtractor.extractUrl(clipboardText())

    private fun setupEngineManagement() {
        refreshEngineVersion()

        binding.btnUpdateEngine.setOnClickListener {
            binding.btnUpdateEngine.isEnabled = false
            binding.tvEngineStatus.text = getString(R.string.home_engine_updating)

            lifecycleScope.launch(Dispatchers.IO) {
                val updateStatus = try {
                    YoutubeDL.getInstance().updateYoutubeDL(
                        applicationContext,
                        YoutubeDL.UpdateChannel.NIGHTLY
                    )
                } catch (e: Exception) {
                    null
                }

                withContext(Dispatchers.Main) {
                    binding.btnUpdateEngine.isEnabled = true
                    if (updateStatus != null) {
                        binding.tvEngineStatus.text = getString(R.string.home_engine_updated)
                        Toast.makeText(this@MainActivity, getString(R.string.home_engine_updated), Toast.LENGTH_SHORT).show()
                    } else {
                        binding.tvEngineStatus.text = getString(R.string.home_engine_already_latest)
                        Toast.makeText(this@MainActivity, getString(R.string.home_engine_already_latest), Toast.LENGTH_SHORT).show()
                    }
                    refreshEngineVersion()
                }
            }
        }
    }

    private fun refreshEngineVersion() {
        val version = try {
            YoutubeDL.getInstance().version(applicationContext)
        } catch (_: Exception) {
            null
        }

        if (!version.isNullOrBlank()) {
            binding.tvEngineVersion.text = getString(R.string.home_engine_version, version)
        }
    }

    private fun triggerDownload(isAudioOnly: Boolean) {
        val rawInput = binding.etInputUrl.text?.toString()?.trim()
        val cleanedUrl = UrlExtractor.extractUrl(rawInput)

        if (cleanedUrl.isNullOrBlank()) {
            binding.tilInputUrl.error = getString(R.string.share_no_url_found)
            return
        }

        binding.tilInputUrl.error = null
        val result = MediaDownloadManager.startDownload(this, cleanedUrl, isAudioOnly)
        if (result.isSuccess) {
            Toast.makeText(this, getString(R.string.share_status_started), Toast.LENGTH_SHORT).show()
            binding.etInputUrl.text = null
            refreshClipboardChip()
        } else {
            Toast.makeText(this, getString(R.string.share_status_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLiveCard() {
        binding.cardLiveActivity.visibility = View.VISIBLE
    }

    private fun hideLiveCard() {
        binding.cardLiveActivity.visibility = View.GONE
    }

    private fun observeActiveDownload() {
        lifecycleScope.launch {
            MediaDownloadManager.activeDownload.collectLatest { live ->
                if (live == null) {
                    hideLiveCard()
                } else {
                    showLiveCard()
                    val platformName = getString(live.platform.displayNameRes)
                    binding.tvLivePlatformBadge.text = platformName
                    val badgeBg = ContextCompat.getColor(this@MainActivity, live.platform.badgeBgRes)
                    val badgeText = ContextCompat.getColor(this@MainActivity, live.platform.badgeTextRes)
                    binding.tvLivePlatformBadge.backgroundTintList = ColorStateList.valueOf(badgeBg)
                    binding.tvLivePlatformBadge.setTextColor(badgeText)

                    binding.tvLiveUrl.text = live.url
                    if (live.isIndeterminate) {
                        binding.progressLiveDownload.isIndeterminate = true
                    } else {
                        binding.progressLiveDownload.isIndeterminate = false
                        binding.progressLiveDownload.setProgressCompat(live.progress, true)
                    }
                    binding.tvLiveStatusText.text = live.statusText

                    binding.btnLiveCancel.setOnClickListener {
                        MediaDownloadManager.cancelDownload(this@MainActivity)
                    }
                }
            }
        }
    }

    private fun setupRecentDownloads() {
        downloadHistoryAdapter = DownloadHistoryAdapter { record ->
            openDownloadedMedia(record)
        }
        binding.rvRecentList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = downloadHistoryAdapter
            itemAnimator = null
        }
    }

    private fun observeRecentDownloads() {
        lifecycleScope.launch {
            MediaDownloadManager.recentDownloads.collectLatest { downloads ->
                renderRecentDownloads(downloads)
            }
        }
    }

    private fun renderRecentDownloads(downloads: List<DownloadRecord>) {
        if (downloads.isEmpty()) {
            binding.tvEmptyRecent.visibility = View.VISIBLE
            binding.rvRecentList.visibility = View.GONE
        } else {
            binding.tvEmptyRecent.visibility = View.GONE
            binding.rvRecentList.visibility = View.VISIBLE
            downloadHistoryAdapter.submitList(downloads)
        }
    }

    private fun openDownloadedMedia(record: DownloadRecord) {
        val saveDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            AppConstants.DIRECTORY_URLOADER
        )
        val file = record.filePath?.let { File(it) } ?: File(saveDir, record.filename)
        if (!file.exists() || !file.isFile) {
            Toast.makeText(this, getString(R.string.home_file_not_found), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                this,
                "${packageName}.provider",
                file
            )
            val mimeType = if (record.isAudioOnly || file.name.endsWith(".mp3", ignoreCase = true)) {
                AppConstants.MIME_TYPE_AUDIO_WILDCARD
            } else {
                AppConstants.MIME_TYPE_VIDEO_WILDCARD
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.home_cannot_open_file), Toast.LENGTH_SHORT).show()
        }
    }
}