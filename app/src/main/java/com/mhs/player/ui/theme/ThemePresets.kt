package com.mhs.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Modern theme presets for MHS Player v2.
 * Each preset defines background/surface colors for a distinct visual identity.
 */
data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val previewColors: List<Color>
)

object ThemePresets {
    val AMOLED = ThemePreset(
        id = "AMOLED",
        name = "AMOLED Black",
        description = "Pure black for OLED screens",
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0A),
        surfaceVariant = Color(0xFF1A1A1A),
        previewColors = listOf(Color(0xFF000000), Color(0xFF1A1A1A), Color(0xFF5046E5))
    )
    
    val MIDNIGHT = ThemePreset(
        id = "MIDNIGHT",
        name = "Midnight Blue",
        description = "Deep blue-tinted dark",
        background = Color(0xFF0A0E1A),
        surface = Color(0xFF111827),
        surfaceVariant = Color(0xFF1F2937),
        previewColors = listOf(Color(0xFF0A0E1A), Color(0xFF1F2937), Color(0xFF3B82F6))
    )
    
    val CHARCOAL = ThemePreset(
        id = "CHARCOAL",
        name = "Charcoal",
        description = "Soft dark grey",
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        surfaceVariant = Color(0xFF2D2D2D),
        previewColors = listOf(Color(0xFF121212), Color(0xFF2D2D2D), Color(0xFF9C27B0))
    )
    
    val FOREST = ThemePreset(
        id = "FOREST",
        name = "Forest Night",
        description = "Dark green-tinted",
        background = Color(0xFF0A1410),
        surface = Color(0xFF111F18),
        surfaceVariant = Color(0xFF1C2E22),
        previewColors = listOf(Color(0xFF0A1410), Color(0xFF1C2E22), Color(0xFF4CAF50))
    )
    
    val SUNSET = ThemePreset(
        id = "SUNSET",
        name = "Sunset",
        description = "Warm dark with orange tint",
        background = Color(0xFF160F0A),
        surface = Color(0xFF221610),
        surfaceVariant = Color(0xFF32221A),
        previewColors = listOf(Color(0xFF160F0A), Color(0xFF32221A), Color(0xFFFF6B35))
    )

    val all = listOf(AMOLED, MIDNIGHT, CHARCOAL, FOREST, SUNSET)
    
    fun getById(id: String): ThemePreset = all.find { it.id == id } ?: AMOLED
}

/**
 * Curated accent colors for personalization.
 */
object AccentColors {
    val options = listOf(
        "#5046E5" to "Indigo",
        "#3B82F6" to "Blue",
        "#06B6D4" to "Cyan",
        "#10B981" to "Emerald",
        "#84CC16" to "Lime",
        "#F59E0B" to "Amber",
        "#F97316" to "Orange",
        "#EF4444" to "Red",
        "#EC4899" to "Pink",
        "#A855F7" to "Purple"
    )
    
    fun parse(hex: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: Exception) {
            Color(0xFF5046E5)
        }
    }
}
