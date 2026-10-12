package com.aventure.messcores

import androidx.compose.ui.graphics.Color

// Palette de couleurs suffisamment contrastées entre elles pour rester lisibles
// une fois utilisées comme fond de case ou comme couleur de texte.
// Elle doit toujours compter plus de couleurs que le nombre maximal de joueurs (12),
// pour qu'il reste des couleurs libres à choisir quand la table est pleine.
internal val PLAYER_COLOR_PALETTE = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00897B), Color(0xFFD81B60), Color(0xFF3949AB),
    Color(0xFF6D4C41), Color(0xFF7CB342), Color(0xFFF4511E), Color(0xFF546E7A),
    Color(0xFF00ACC1), Color(0xFF9E9D24), Color(0xFF5E35B1), Color(0xFFB71C1C),
    Color(0xFF1565C0), Color(0xFFFFA000)
)

/** Une couleur distincte par joueur, tirée au sort ; la palette est réutilisée au-delà de ses couleurs. */
internal fun assignPlayerColors(count: Int): List<Color> {
    if (count == 0) return emptyList()
    val shuffled = PLAYER_COLOR_PALETTE.shuffled()
    return List(count) { i -> shuffled[i % shuffled.size] }
}

/** Vrai si un joueur autre que [playerIndex] utilise déjà [color]. */
internal fun isColorTakenByOther(colors: List<Color>, playerIndex: Int, color: Color): Boolean =
    colors.withIndex().any { (i, c) -> i != playerIndex && c == color }
