package com.mhs.player.player.subtitles.model

/**
 * Standardized subtitle result model used across all providers.
 */
data class SubtitleResult(
    val id: String,
    val title: String,
    val titleMal: String? = null,
    val language: String,
    val languageCode: String,
    val provider: String,
    val downloadUrl: String,
    val releaseName: String? = null,
    val translator: String? = null,
    val posterUrl: String? = null,
    val rating: String? = null,
    val uploadedAt: String? = null,
    val downloads: Int = 0,
    val fps: Double = 0.0,
    val genres: String? = null,
    val releaseYear: Int? = null,
    val releaseType: String? = null,
    val directDownloadLink: String? = null // For providers with immediate links
)
