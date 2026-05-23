package com.mhs.player.navigation

import android.util.Base64
import java.nio.charset.StandardCharsets

/**
 * Encodes route arguments with Base64 (URL-safe, no padding) so values containing
 * `/`, `:`, or `%` do not break Navigation path segments.
 */
object NavRouteEncoder {
    fun encode(value: String): String =
        Base64.encodeToString(
            value.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    fun decode(encoded: String): String {
        if (encoded.isBlank()) return encoded
        return try {
            val bytes = Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_PADDING)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            // Legacy routes created with URLEncoder.encode
            java.net.URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        }
    }
}
