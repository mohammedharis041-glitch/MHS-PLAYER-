package com.mhs.player.ui.theme.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object AppShapes {
    val RoundedXS = RoundedCornerShape(6.dp)
    val RoundedSM = RoundedCornerShape(10.dp)
    val RoundedMD = RoundedCornerShape(16.dp)
    val RoundedLG = RoundedCornerShape(24.dp)
    val RoundedXL = RoundedCornerShape(32.dp)
    
    val StandardShapes = Shapes(
        extraSmall = RoundedXS,
        small = RoundedSM,
        medium = RoundedMD,
        large = RoundedLG,
        extraLarge = RoundedXL
    )
}
