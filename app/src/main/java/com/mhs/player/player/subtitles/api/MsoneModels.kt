package com.mhs.player.player.subtitles.api

import com.google.gson.annotations.SerializedName

/**
 * Real MSone API response models based on sample JSON files.
 */

data class MsoneSearchResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("post_url") val postUrl: String? = null
)

data class MsoneReleaseResponse(
    @SerializedName("id") val id: Long,
    @SerializedName("url") val url: String? = null,
    @SerializedName("title_eng") val titleEng: String? = null,
    @SerializedName("title_mal") val titleMal: String? = null,
    @SerializedName("release_year") val releaseYear: Int? = null,
    @SerializedName("release_number") val releaseNumber: String? = null,
    @SerializedName("release_type") val releaseType: List<MsoneTerm>? = null,
    @SerializedName("poster") val poster: String? = null,
    @SerializedName("poster_raw") val posterRaw: String? = null,
    @SerializedName("languages") val languages: List<MsoneTerm>? = null,
    @SerializedName("genres") val genres: List<MsoneTerm>? = null,
    @SerializedName("imdb_url") val imdbUrl: String? = null,
    @SerializedName("imdb_rating") val imdbRating: String? = null,
    @SerializedName("package_id") val packageId: String? = null,
    @SerializedName("file_name") val fileName: String? = null,
    @SerializedName("download_count") val downloadCount: Int? = null,
    @SerializedName("translators") val translators: List<MsoneTerm>? = null,
    @SerializedName("content") val content: String? = null
)

data class MsoneTerm(
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("name_mal") val nameMal: String? = null
)
