package com.mhs.player.player.subtitles.parser

import java.io.File
import java.util.regex.Pattern

data class SubtitleCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

class SrtParser {
    fun parse(file: File): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        try {
            val bytes = file.readBytes()
            if (bytes.isEmpty()) return cues
            
            val content = decodeBytes(bytes)
            val lines = content.lines()
            if (lines.isEmpty()) return cues
            
            val isAss = file.name.endsWith(".ass", ignoreCase = true) || file.name.endsWith(".ssa", ignoreCase = true)
            
            if (isAss) {
                parseAss(lines, cues)
            } else {
                parseSrtOrVtt(lines, cues)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cues
    }

    private fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        if (bytes.size >= 3) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            val b2 = bytes[2].toInt() and 0xFF
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
        }
        try {
            val decoder = Charsets.UTF_8.newDecoder()
            val charBuffer = decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return charBuffer.toString()
        } catch (e: Exception) {
            return String(bytes, java.nio.charset.Charset.forName("windows-1252"))
        }
    }

    private fun parseSrtOrVtt(lines: List<String>, cues: MutableList<SubtitleCue>) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("-->") || line.contains("->")) {
                // Parse timestamps
                val parts = line.split(Regex("\\s*-+>\\s*"))
                if (parts.size >= 2) {
                    val startMs = parseTimestampToMs(parts[0].trim())
                    val endMs = parseTimestampToMs(parts[1].trim())
                    
                    if (startMs >= 0 && endMs >= startMs) {
                        // Gather subtitle text lines
                        val textLines = mutableListOf<String>()
                        i++
                        while (i < lines.size && lines[i].trim().isNotEmpty() && !lines[i].contains("-->") && !lines[i].contains("->")) {
                            val txtLine = lines[i].trim()
                            // Skip line if it's just an index number before a timestamp
                            if (txtLine.matches(Regex("\\d+")) && i + 1 < lines.size && (lines[i+1].contains("-->") || lines[i+1].contains("->"))) {
                                // This is an index number for the NEXT cue, stop gathering text
                                break
                            }
                            textLines.add(txtLine)
                            i++
                        }
                        val text = textLines.joinToString("\n").replace(Regex("<[^>]*>"), "").trim()
                        if (text.isNotEmpty()) {
                            cues.add(SubtitleCue(startMs, endMs, text))
                        }
                        continue
                    }
                }
            }
            i++
        }
    }

    private fun parseAss(lines: List<String>, cues: MutableList<SubtitleCue>) {
        // Format of ASS dialogue: Dialogue: Marked,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                try {
                    val content = trimmed.substringAfter("Dialogue:").trim()
                    val parts = content.split(",", limit = 10)
                    if (parts.size >= 10) {
                        val startMs = parseAssTimestamp(parts[1].trim())
                        val endMs = parseAssTimestamp(parts[2].trim())
                        var text = parts[9]
                            .replace(Regex("\\{[^}]*\\}"), "") // Strip format blocks like {\pos(10,20)}
                            .replace("\\N", "\n")             // Replace ASS newlines
                            .replace("\\n", "\n")
                            .trim()
                        
                        if (startMs >= 0 && endMs >= startMs && text.isNotEmpty()) {
                            cues.add(SubtitleCue(startMs, endMs, text))
                        }
                    }
                } catch (e: Exception) {
                    // Ignore malformed dialogue rows
                }
            }
        }
    }

    private fun parseTimestampToMs(timeStr: String): Long {
        try {
            // Support formats like: 00:00:00,000 or 00:00:00.000 or 00:00.000
            val clean = timeStr.replace(',', '.')
            val parts = clean.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val secParts = parts[2].split(".")
                val s = secParts[0].toLong()
                val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                return (h * 3600 + m * 60 + s) * 1000 + ms
            } else if (parts.size == 2) {
                // Short VTT format: mm:ss.ms
                val m = parts[0].toLong()
                val secParts = parts[1].split(".")
                val s = secParts[0].toLong()
                val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3).toLong() else 0L
                return (m * 60 + s) * 1000 + ms
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return -1L
    }

    private fun parseAssTimestamp(timeStr: String): Long {
        try {
            // Format: h:mm:ss.cs (centiseconds)
            val parts = timeStr.split(":")
            if (parts.size == 3) {
                val h = parts[0].toLong()
                val m = parts[1].toLong()
                val secParts = parts[2].split(".")
                val s = secParts[0].toLong()
                val cs = if (secParts.size > 1) secParts[1].padEnd(2, '0').take(2).toLong() else 0L
                return (h * 3600 + m * 60 + s) * 1000 + cs * 10
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return -1L
    }
}
