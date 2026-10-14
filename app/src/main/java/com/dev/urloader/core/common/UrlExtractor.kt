package com.dev.urloader.core.common

import java.net.URI
import java.util.regex.Pattern

object UrlExtractor {

    private val WEB_URL_PATTERN: Pattern = Pattern.compile(
        "https?://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]",
        Pattern.CASE_INSENSITIVE
    )

    private val TRACKING_PARAMS = listOf(
        "si",
        "igsh",
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "fbclid",
        "_r",
        "_t",
        "feature"
    )

    /**
     * Extracts the first valid HTTP/HTTPS URL from any shared text block.
     * Guards defensively against null/empty strings and malformed input.
     */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null

        return try {
            val matcher = WEB_URL_PATTERN.matcher(text)
            if (matcher.find()) {
                val rawUrl = matcher.group(0) ?: return null
                cleanUrl(rawUrl)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Removes common tracking query parameters while preserving necessary video IDs and timestamps.
     */
    fun cleanUrl(rawUrl: String): String {
        val sanitized = rawUrl.trim().trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')
        return try {
            val uri = URI(sanitized)
            val query = uri.query ?: return sanitized

            val cleanedQuery = query.split("&")
                .filter { param ->
                    val key = param.substringBefore("=").lowercase()
                    key !in TRACKING_PARAMS
                }
                .joinToString("&")

            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            val queryPart = if (cleanedQuery.isNotBlank()) "?$cleanedQuery" else ""
            val fragmentPart = if (!uri.fragment.isNullOrBlank()) "#${uri.fragment}" else ""

            "${uri.scheme}://${uri.host}$portPart${uri.path ?: ""}$queryPart$fragmentPart"
        } catch (_: Exception) {
            rawUrl
        }
    }
}
