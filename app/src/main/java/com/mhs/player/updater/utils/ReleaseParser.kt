package com.mhs.player.updater.utils

object ReleaseParser {

    // ── Force-update ──────────────────────────────────────────────────────

    /**
     * Checks if the release body instructs a mandatory/forced update.
     * Matches: [force-update] or [force_update]
     */
    fun isForcedUpdate(body: String?): Boolean {
        if (body.isNullOrEmpty()) return false
        return body.contains("[force-update]", ignoreCase = true) ||
               body.contains("[force_update]", ignoreCase = true)
    }

    // ── Version code extraction ───────────────────────────────────────────

    /**
     * Extracts an explicit versionCode embedded in the release body, name, or tag name suffix.
     *
     * Supported formats:
     *   - Body/Name (case-insensitive): [versionCode: 5] or versionCode=5 or versionCode: 5
     *   - Name (parentheses): "MHS Player v1.0.2 (4)" -> 4
     *   - Tag Suffix (trailing number after dash/plus/dot): "v1.0.2-4" -> 4, "1.0.2+4" -> 4
     *   - Pure numeric tag: "4" -> 4
     */
    fun extractVersionCode(body: String?, name: String?, tagName: String?): Int? {
        // 1. Try body first
        body?.let {
            val pattern = Regex("""\bversion[Cc]ode\b\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            pattern.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { code -> return code }
        }

        // 2. Try name
        name?.let { n ->
            // Check for [versionCode: X] or versionCode=X in the name
            val pattern1 = Regex("""\bversion[Cc]ode\b\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
            pattern1.find(n)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { code -> return code }

            // Check for parenthesis: e.g. "MHS Player v1.0.2 (4)"
            val pattern2 = Regex("""\((\d+)\)""")
            pattern2.find(n)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { code -> return code }
        }

        // 3. Try tag name
        tagName?.let { tag ->
            val cleanTag = tag.trim().removePrefix("v")
            if (cleanTag.all { it.isDigit() }) {
                cleanTag.toIntOrNull()?.let { return it }
            }
            // Check for trailing number after dash, plus, or dot: e.g., "1.0.2-4" or "1.0.2+4" or "1.0.2.4"
            val pattern3 = Regex("""[-+.](\d+)$""")
            pattern3.find(cleanTag)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { code -> return code }
        }

        return null
    }

    // ── Changelog ────────────────────────────────────────────────────────

    /**
     * Extracts and cleans the changelog from the release notes.
     * Removes instruction tags like [force-update], [versionCode: N].
     */
    fun cleanReleaseBody(body: String?): String {
        if (body.isNullOrEmpty()) return "No release notes provided."
        return body
            .replace(Regex("""(?:\[)?force[-_]update(?:\])?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?:\[)?version[Cc]ode\s*[:=]\s*\d+(?:\])?""", RegexOption.IGNORE_CASE), "")
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
            .ifEmpty { "No release notes provided." }
    }
}
