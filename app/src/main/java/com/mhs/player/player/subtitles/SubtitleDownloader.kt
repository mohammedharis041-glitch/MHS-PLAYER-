package com.mhs.player.player.subtitles

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubtitleDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun download(url: String, subtitleId: String, videoTitle: String): File? = withContext(Dispatchers.IO) {
        try {
            Log.d("MHSPlayer-Subtitles", "Pipeline: Downloader starting for URL: $url")
            
            val subDir = File(context.cacheDir, "subtitles").also { it.mkdirs() }
            
            // Clean up stale subtitle files older than 24 hours to prevent cache cluttering
            try {
                subDir.listFiles()?.forEach { file ->
                    if (file.isFile && (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000)) {
                        file.delete()
                        Log.d("MHSPlayer-Subtitles", "Pipeline: Cleaned stale cached subtitle: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("MHSPlayer-Subtitles", "Pipeline: Stale cache clean failure", e)
            }

            val safeTitle = videoTitle.replace(Regex("[^a-zA-Z0-9_]"), "_").take(40)
            
            val conn = openConnectionWithRedirects(url)
            
            if (conn.responseCode !in 200..299) {
                Log.e("MHSPlayer-Subtitles", "Pipeline: Download failed with code ${conn.responseCode}")
                return@withContext null
            }

            val bufferedInput = java.io.BufferedInputStream(conn.inputStream)
            
            // Read first 4 bytes to check ZIP signature (magic bytes PK\u0003\u0004)
            bufferedInput.mark(4)
            val header = ByteArray(4)
            val bytesRead = bufferedInput.read(header)
            bufferedInput.reset()
            
            val isZipSignature = bytesRead == 4 &&
                    header[0] == 0x50.toByte() && // 'P'
                    header[1] == 0x4B.toByte() && // 'K'
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            
            val contentType = conn.contentType?.lowercase() ?: ""
            val contentDisposition = conn.getHeaderField("Content-Disposition")?.lowercase() ?: ""
            
            val isZip = isZipSignature ||
                        contentType.contains("zip") || 
                        contentDisposition.contains(".zip") || 
                        url.contains(".zip", true)
            
            Log.d("MHSPlayer-Subtitles", "Pipeline: Stream check - isZipSignature: $isZipSignature, Content-Type: '$contentType', Content-Disposition: '$contentDisposition', final isZip: $isZip")

            // Generate unique identifier to completely prevent cache overwrite issues
            val uniqueId = "${subtitleId}_${System.currentTimeMillis()}"

            if (isZip) {
                Log.d("MHSPlayer-Subtitles", "Pipeline: Detected ZIP file, extracting...")
                return@withContext extractBestSubtitleFromZip(bufferedInput, subDir, safeTitle, uniqueId)
            } else {
                // Determine extension from content-type or URL
                val ext = when {
                    contentType.contains("srt") || url.contains(".srt", true) -> "srt"
                    contentType.contains("ass") || url.contains(".ass", true) -> "ass"
                    contentType.contains("vtt") || url.contains(".vtt", true) -> "vtt"
                    else -> "srt" // Fallback
                }
                
                val outFile = File(subDir, "${safeTitle}_$uniqueId.$ext")
                bufferedInput.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("MHSPlayer-Subtitles", "Pipeline: Download SUCCESS: ${outFile.name}")
                return@withContext outFile
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "Pipeline: Downloader error", e)
            null
        }
    }

    private fun extractBestSubtitleFromZip(
        inputStream: java.io.InputStream, 
        outputDir: File, 
        baseName: String, 
        uniqueId: String
    ): File? {
        var bestFile: File? = null
        try {
            ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name.lowercase()
                    if (!entry.isDirectory && (entryName.endsWith(".srt") || entryName.endsWith(".ass") || entryName.endsWith(".vtt"))) {
                        val ext = entryName.substringAfterLast(".")
                        val outFile = File(outputDir, "${baseName}_${uniqueId}_extracted.$ext")
                        
                        FileOutputStream(outFile).use { output ->
                            zip.copyTo(output)
                        }
                        
                        Log.d("MHSPlayer-Subtitles", "Pipeline: Extracted $entryName from ZIP")
                        
                        // Heuristic: Prefer SRT if multiple files, or just take the first one
                        if (bestFile == null || entryName.contains("malayalam", true) || entryName.contains("english", true)) {
                            bestFile = outFile
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e("MHSPlayer-Subtitles", "Pipeline: ZIP extraction failed", e)
        }
        return bestFile
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var urlString = initialUrl
        var redirects = 0
        val maxRedirects = 5
        var cookie: String? = null
        
        while (redirects < maxRedirects) {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            if (cookie != null) {
                conn.setRequestProperty("Cookie", cookie)
            }
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                
                val newUrl = conn.getHeaderField("Location")
                val setCookie = conn.getHeaderField("Set-Cookie")
                if (setCookie != null) {
                    cookie = setCookie
                }
                
                Log.d("MHSPlayer-Subtitles", "Pipeline: Redirecting from $urlString to $newUrl (Status: $status)")
                
                if (newUrl == null) {
                    throw java.io.IOException("Redirect response missing Location header")
                }
                
                urlString = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                    newUrl
                } else {
                    val base = URL(urlString)
                    URL(base, newUrl).toString()
                }
                
                conn.disconnect()
                redirects++
            } else {
                return conn
            }
        }
        throw java.io.IOException("Too many redirects: $redirects")
    }
}
