package com.dev.urloader.core.common

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.dev.urloader.R
import java.net.URI

enum class SupportedPlatform(
    @get:StringRes val displayNameRes: Int,
    @get:ColorRes val badgeBgRes: Int,
    @get:ColorRes val badgeTextRes: Int
) {
    YOUTUBE(
        displayNameRes = R.string.platform_youtube,
        badgeBgRes = R.color.badge_youtube_bg,
        badgeTextRes = R.color.badge_youtube_text
    ),
    INSTAGRAM(
        displayNameRes = R.string.platform_instagram,
        badgeBgRes = R.color.badge_instagram_bg,
        badgeTextRes = R.color.badge_instagram_text
    ),
    TIKTOK(
        displayNameRes = R.string.platform_tiktok,
        badgeBgRes = R.color.badge_tiktok_bg,
        badgeTextRes = R.color.badge_tiktok_text
    ),
    TWITTER_X(
        displayNameRes = R.string.platform_twitter,
        badgeBgRes = R.color.badge_twitter_bg,
        badgeTextRes = R.color.badge_twitter_text
    ),
    REDDIT(
        displayNameRes = R.string.platform_reddit,
        badgeBgRes = R.color.badge_reddit_bg,
        badgeTextRes = R.color.badge_reddit_text
    ),
    GENERIC(
        displayNameRes = R.string.platform_generic,
        badgeBgRes = R.color.badge_generic_bg,
        badgeTextRes = R.color.badge_generic_text
    )
}

/**
 * Platform detection engine. Resolves domains to their corresponding [SupportedPlatform]
 * for themed badges, styling, and platform-specific format optimization.
 */
object PlatformDetector {

    fun detect(url: String?): SupportedPlatform {
        if (url.isNullOrBlank()) return SupportedPlatform.GENERIC

        val host = try {
            URI(url).host?.lowercase() ?: ""
        } catch (_: Exception) {
            url.lowercase()
        }

        return when {
            host.contains("youtube.com") || host.contains("youtu.be") -> SupportedPlatform.YOUTUBE
            host.contains("instagram.com") || host.contains("instagr.am") -> SupportedPlatform.INSTAGRAM
            host.contains("tiktok.com") -> SupportedPlatform.TIKTOK
            host.contains("twitter.com") || host.contains("x.com") -> SupportedPlatform.TWITTER_X
            host.contains("reddit.com") || host.contains("redd.it") -> SupportedPlatform.REDDIT
            else -> SupportedPlatform.GENERIC
        }
    }
}
