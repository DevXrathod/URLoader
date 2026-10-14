package com.dev.urloader.core.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import com.dev.urloader.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lightweight, zero-dependency favicon caching engine.
 * Downloads a micro copy (64px) of the source domain favicon and caches it locally
 * on disk so subsequent loads are offline and instantaneous.
 */
object FaviconLoader {

    private val loaderScope = CoroutineScope(Dispatchers.IO)
    private val memoryCache = ConcurrentHashMap<String, Bitmap>()

    fun loadFavicon(
        imageView: ImageView,
        url: String?,
        platform: SupportedPlatform? = null
    ) {
        val host = resolveHost(url, platform)
        if (host.isNullOrBlank()) {
            imageView.setImageResource(R.drawable.ic_link)
            return
        }

        // 1. Check in-memory L1 cache
        val memCached = memoryCache[host]
        if (memCached != null) {
            imageView.setImageBitmap(memCached)
            return
        }

        // Set placeholder and tag to guard against view recycling
        imageView.setImageResource(R.drawable.ic_link)
        imageView.tag = host

        val context = imageView.context.applicationContext
        val cacheDir = File(context.filesDir, "favicons").apply { if (!exists()) mkdirs() }
        val localFile = File(cacheDir, "${sanitizeHost(host)}.png")

        loaderScope.launch {
            // 2. Check disk L2 cache
            var bitmap: Bitmap? = null
            if (localFile.exists() && localFile.length() > 0) {
                try {
                    bitmap = BitmapFactory.decodeFile(localFile.absolutePath)
                } catch (_: Exception) {
                    localFile.delete()
                }
            }

            // 3. Fetch from Google Favicons API if not cached locally
            if (bitmap == null) {
                bitmap = fetchAndStore(host, localFile)
            }

            if (bitmap != null) {
                memoryCache[host] = bitmap
                withContext(Dispatchers.Main) {
                    if (imageView.tag == host) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun resolveHost(url: String?, platform: SupportedPlatform?): String? {
        if (!url.isNullOrBlank()) {
            val extracted = extractHostFromUrl(url)
            if (!extracted.isNullOrBlank()) return extracted
        }

        return when (platform) {
            SupportedPlatform.YOUTUBE -> "youtube.com"
            SupportedPlatform.INSTAGRAM -> "instagram.com"
            SupportedPlatform.TIKTOK -> "tiktok.com"
            SupportedPlatform.TWITTER_X -> "x.com"
            SupportedPlatform.REDDIT -> "reddit.com"
            SupportedPlatform.GENERIC, null -> null
        }
    }

    private fun extractHostFromUrl(input: String): String? {
        return try {
            val formatted = if (!input.startsWith("http://") && !input.startsWith("https://")) {
                "https://$input"
            } else {
                input
            }
            val uri = Uri.parse(formatted)
            uri.host?.lowercase()?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeHost(host: String): String {
        return host.replace(Regex("[^a-zA-Z0-9.-]"), "_")
    }

    private fun fetchAndStore(host: String, targetFile: File): Bitmap? {
        return try {
            val faviconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=64"
            val connection = URL(faviconUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.instanceFollowRedirects = true

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    val bytes = input.readBytes()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) {
                        targetFile.outputStream().use { out ->
                            out.write(bytes)
                        }
                        bitmap
                    } else null
                }
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
