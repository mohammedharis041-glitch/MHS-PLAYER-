package com.mhs.player.player.subtitles.providers

import com.mhs.player.player.subtitles.SubtitleProvider
import com.mhs.player.player.subtitles.model.SubtitleResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSubtitleProvider @Inject constructor() : SubtitleProvider {
    
    override val providerId: String = "local"

    override suspend fun search(query: String, languageCode: String, apiKey: String?): List<SubtitleResult> {
        // Query here is expected to be the full video path for local search
        val videoFile = File(query)
        if (!videoFile.exists()) return emptyList()
        
        return try {
            val parentDir = videoFile.parentFile ?: return emptyList()
            val baseName = videoFile.nameWithoutExtension.lowercase()
            
            parentDir.listFiles { _, name ->
                val lower = name.lowercase()
                (lower.endsWith(".srt") || lower.endsWith(".vtt") || lower.endsWith(".ass")) &&
                (lower.contains(baseName) || baseName.contains(lower.substringBeforeLast(".")))
            }?.map { file ->
                SubtitleResult(
                    id = "local_${file.absolutePath}",
                    title = "[Local] ${file.name}",
                    language = "Local File",
                    languageCode = "local",
                    provider = providerId,
                    downloadUrl = file.absolutePath,
                    releaseName = file.name,
                    uploadedAt = "Local"
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun download(subtitle: SubtitleResult, videoTitle: String, apiKey: String?): File? {
        // For local files, "download" just returns the file itself
        val file = File(subtitle.downloadUrl)
        return if (file.exists()) file else null
    }
}
