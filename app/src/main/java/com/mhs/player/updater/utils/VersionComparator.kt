package com.mhs.player.updater.utils

object VersionComparator {

    /**
     * Compares two semantic version strings.
     * Returns a positive number if [latest] is newer than [current],
     * negative if older, and 0 if equal.
     */
    fun isNewerVersion(current: String, latest: String): Boolean {
        return compareVersions(current, latest) < 0
    }

    fun compareVersions(ver1: String, ver2: String): Int {
        val clean1 = ver1.trim().lowercase().removePrefix("v")
        val clean2 = ver2.trim().lowercase().removePrefix("v")

        // Split core version (e.g. 1.2.3) and qualifier (e.g. beta, nightly)
        val parts1 = clean1.split("-", limit = 2)
        val parts2 = clean2.split("-", limit = 2)

        val core1 = parts1[0]
        val core2 = parts2[0]

        val coreComponents1 = core1.split(".").map { it.toIntOrNull() ?: 0 }
        val coreComponents2 = core2.split(".").map { it.toIntOrNull() ?: 0 }

        val maxLength = maxOf(coreComponents1.size, coreComponents2.size)

        for (i in 0 until maxLength) {
            val comp1 = coreComponents1.getOrElse(i) { 0 }
            val comp2 = coreComponents2.getOrElse(i) { 0 }
            if (comp1 != comp2) {
                return comp1.compareTo(comp2)
            }
        }

        // If core versions are equal, compare qualifiers
        val hasQual1 = parts1.size > 1
        val hasQual2 = parts2.size > 1

        return when {
            // A stable release (no qualifier) is newer than a pre-release (with qualifier)
            !hasQual1 && hasQual2 -> 1
            hasQual1 && !hasQual2 -> -1
            // Both have qualifiers
            hasQual1 && hasQual2 -> {
                val qual1 = parts1[1]
                val qual2 = parts2[1]
                
                // Special handling for common release qualifiers
                val weight1 = getQualifierWeight(qual1)
                val weight2 = getQualifierWeight(qual2)
                
                if (weight1 != weight2) {
                    weight1.compareTo(weight2)
                } else {
                    qual1.compareTo(qual2) // fallback lexicographical
                }
            }
            // Neither has qualifier
            else -> 0
        }
    }

    private fun getQualifierWeight(qualifier: String): Int {
        return when {
            qualifier.contains("nightly") -> 1
            qualifier.contains("alpha") -> 2
            qualifier.contains("beta") -> 3
            qualifier.contains("rc") -> 4
            else -> 0
        }
    }
}
