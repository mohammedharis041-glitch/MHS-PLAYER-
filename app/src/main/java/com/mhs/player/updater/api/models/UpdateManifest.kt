package com.mhs.player.updater.api.models

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String?,
    val apkUrl: String?,
    val apkSize: Long = 0L,
    val mandatory: Boolean = false,
    val changelog: String? = "",
    val prerelease: Boolean = false,
    val publishedAt: String? = "2026-05-23T00:00:00Z"
) {
    val tagName: String get() = "v${versionName ?: "1.0.0"}"
}
