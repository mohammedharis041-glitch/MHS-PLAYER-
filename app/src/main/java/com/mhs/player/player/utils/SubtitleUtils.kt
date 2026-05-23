package com.mhs.player.player.utils

import android.util.Log

object SubtitleUtils {
    
    /**
     * Robustly removes HTML tags and decodes common entities.
     */
    fun stripHtml(text: String?): String {
        if (text == null) return ""
        
        // 1. Remove tags
        var clean = text.replace(Regex("<[^>]*>"), "")
        
        // 2. Decode common entities
        clean = clean.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&#039;", "'")
            .replace("&#34;", "\"")
            
        // 3. Catch-all for remaining &...; entities
        clean = clean.replace(Regex("&[a-zA-Z0-9#]+;"), " ")
        
        // 4. Normalize whitespace
        return clean.replace(Regex("\\s{2,}"), " ").trim()
    }
}
