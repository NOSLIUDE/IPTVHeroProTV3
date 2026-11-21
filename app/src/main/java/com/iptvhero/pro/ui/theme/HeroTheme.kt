package com.iptvhero.pro.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val MarvelRed = Color(0xFFE62429)
val MarvelYellow = Color(0xFFFFCC00)
val DcBlue = Color(0xFF0476F2)
val DcCyan = Color(0xFF00E5FF)
val InkDark = Color(0xFF07070F)
val TextPrimary = Color(0xFFF0F0F5)
val TextDim = Color(0xFF9CA3B8)
val GoldFocus = Color(0xFFF5B301)

enum class HeroThemeType(
    val label: String,
    val headerBrush: Brush,
    val backgroundBrush: Brush
) {
    DUEL(
        label = "Duelo (Escarlate vs Cobalto)",
        headerBrush = Brush.horizontalGradient(listOf(MarvelRed.copy(alpha = 0.92f), DcBlue.copy(alpha = 0.92f))),
        backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF1A0912), InkDark, Color(0xFF081426)))
    ),
    CRIMSON(
        label = "Escarlate & Ouro",
        headerBrush = Brush.horizontalGradient(listOf(MarvelRed, Color(0xFFA01218))),
        backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF24080C), InkDark))
    ),
    COBALT(
        label = "Cobalto & Ciano",
        headerBrush = Brush.horizontalGradient(listOf(DcBlue, Color(0xFF00A8CC))),
        backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF07192E), InkDark))
    ),
    MULTIVERSE(
        label = "Multiverso Cosmico",
        headerBrush = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), MarvelRed, DcBlue)),
        backgroundBrush = Brush.verticalGradient(listOf(Color(0xFF190E36), InkDark))
    )
}
