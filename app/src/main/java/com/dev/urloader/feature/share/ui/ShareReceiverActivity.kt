package com.dev.urloader.feature.share.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.dev.urloader.R
import com.dev.urloader.core.common.MediaDownloadManager
import com.dev.urloader.core.common.PlatformDetector
import com.dev.urloader.core.common.UrlExtractor
import com.dev.urloader.databinding.ActivityShareReceiverBinding

class ShareReceiverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityShareReceiverBinding
    private var targetUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Enable real-time background blur on Android 12+ (SDK 31-36) for liquid glass effect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes.blurBehindRadius = 55
            } catch (_: Exception) {
                // Graceful fallback for devices without blur support
            }
        }

        binding = ActivityShareReceiverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardSheet.clipToOutline = true

        setupInsets()
        resolveIncomingShareIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        binding.blurMeshBackground.startAnimation()
    }

    override fun onStop() {
        binding.blurMeshBackground.stopAnimation()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveIncomingShareIntent(intent)
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.cardSheet) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val layoutParams = v.layoutParams as? ConstraintLayout.LayoutParams
            if (layoutParams != null) {
                layoutParams.bottomMargin = resources.getDimensionPixelSize(R.dimen.sheet_floating_margin) + systemBars.bottom
                v.layoutParams = layoutParams
            }
            insets
        }
    }

    private fun resolveIncomingShareIntent(incomingIntent: Intent?) {
        if (incomingIntent == null) {
            finish()
            return
        }

        val rawText = when (incomingIntent.action) {
            Intent.ACTION_SEND -> incomingIntent.getStringExtra(Intent.EXTRA_TEXT)
            else -> incomingIntent.dataString
        }

        val extractedUrl = UrlExtractor.extractUrl(rawText)
        if (extractedUrl.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.share_no_url_found), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        targetUrl = extractedUrl
        bindUi(extractedUrl)
    }

    private fun bindUi(url: String) {
        val platform = PlatformDetector.detect(url)
        val platformName = getString(platform.displayNameRes)

        binding.tvPlatformBadge.text = getString(R.string.share_detected_platform, platformName)
        val badgeBg = ContextCompat.getColor(this, platform.badgeBgRes)
        val badgeText = ContextCompat.getColor(this, platform.badgeTextRes)
        binding.tvPlatformBadge.backgroundTintList = ColorStateList.valueOf(badgeBg)
        binding.tvPlatformBadge.setTextColor(badgeText)

        binding.tvUrlPreview.text = url

        binding.scrimRoot.setOnClickListener { finish() }
        binding.cardSheet.setOnClickListener { /* prevent dismiss when clicking the card */ }
        binding.btnClose.setOnClickListener { finish() }

        binding.btnCopyUrl.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("URLoader Link", url)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.share_copied_toast), Toast.LENGTH_SHORT).show()
        }

        binding.btnDownloadVideo.setOnClickListener {
            dispatchDownload(url, isAudioOnly = false)
        }

        binding.btnDownloadAudio.setOnClickListener {
            dispatchDownload(url, isAudioOnly = true)
        }
    }

    private fun dispatchDownload(url: String, isAudioOnly: Boolean) {
        val result = MediaDownloadManager.startDownload(this, url, isAudioOnly)
        if (result.isSuccess) {
            Toast.makeText(this, getString(R.string.share_status_started), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.share_status_failed), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
