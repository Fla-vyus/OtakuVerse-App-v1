package com.example.data.model

import androidx.compose.ui.graphics.Color

object ImageUrlUtils {
    /**
     * Replaces '/cover/large/' with '/cover/medium/' on AniList URLs for card listings.
     * Generates a fallback chain: medium -> original -> alternatives.
     */
    fun getOptimizedCoverUrl(url: String?, isListThumbnail: Boolean = true): String {
        if (url.isNullOrBlank()) return ""
        if (isListThumbnail && url.contains("/cover/large/")) {
            return url.replace("/cover/large/", "/cover/medium/")
        }
        return url
    }

    fun parseColorHex(hex: String?, defaultColor: Color = Color(0xFF1E3A6E)): Color {
        if (hex.isNullOrBlank()) return defaultColor
        return try {
            val clean = hex.trim().removePrefix("#")
            val fullHex = if (clean.length == 6) "FF$clean" else clean
            Color(android.graphics.Color.parseColor("#$clean"))
        } catch (e: Exception) {
            defaultColor
        }
    }
}
