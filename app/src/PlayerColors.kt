package com.aventure.messcores

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

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

/**
 * Une couleur distincte par joueur. Les couleurs déjà choisies ([chosen], une entrée par joueur,
 * null = pas de choix) sont conservées ; les autres joueurs reçoivent une couleur tirée au sort
 * parmi celles qui restent libres. La palette n'est réutilisée qu'au-delà de ses couleurs.
 */
internal fun assignPlayerColors(
    count: Int,
    chosen: List<Color?> = emptyList(),
    random: Random = Random.Default
): List<Color> {
    if (count == 0) return emptyList()
    val result = arrayOfNulls<Color>(count)
    val used = mutableSetOf<Color>()
    for (i in 0 until count) {
        val color = chosen.getOrNull(i)
        if (color != null && used.add(color)) result[i] = color
    }
    val pool = PLAYER_COLOR_PALETTE.filter { it !in used }.shuffled(random)
    var next = 0
    for (i in 0 until count) {
        if (result[i] != null) continue
        result[i] = if (pool.isNotEmpty()) pool[next++ % pool.size] else PLAYER_COLOR_PALETTE[i % PLAYER_COLOR_PALETTE.size]
    }
    return result.map { it ?: PLAYER_COLOR_PALETTE[0] }
}

/** Vrai si un joueur autre que [playerIndex] utilise déjà [color]. */
internal fun isColorTakenByOther(colors: List<Color>, playerIndex: Int, color: Color): Boolean =
    colors.withIndex().any { (i, c) -> i != playerIndex && c == color }

/** Code court (A, B, C…) d'une couleur de la palette, pour le mode daltonien ; null hors palette. */
internal fun colorCode(color: Color): String? {
    val index = PLAYER_COLOR_PALETTE.indexOf(color)
    return if (index >= 0) ('A' + index).toString() else null
}
