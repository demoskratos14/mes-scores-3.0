package com.aventure.messcores

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.core.content.edit
import kotlin.math.PI
import kotlin.math.sin

/** Le fond de la grille du touché coulé : la mer (par défaut) ou un ciel étoilé. Purement visuel, propre à chaque téléphone. */
enum class BattleshipBackdrop { SEA, STARS }

val LocalBattleshipBackdrop = compositionLocalOf { BattleshipBackdrop.SEA }

/** Mémorise le fond choisi, pour le retrouver à la prochaine partie. */
object BattleshipBackdropPreference {
    private const val PREFS = "mes_scores_setup"
    private const val KEY = "battleship_backdrop"

    fun load(context: Context): BattleshipBackdrop =
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) == BattleshipBackdrop.STARS.name) {
            BattleshipBackdrop.STARS
        } else {
            BattleshipBackdrop.SEA
        }

    fun save(context: Context, backdrop: BattleshipBackdrop) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, backdrop.name) }
    }
}

private val SEA_COLOR = Color(0xFFBBDEFB)
private val SKY_TOP = Color(0xFF050B24)
private val SKY_BOTTOM = Color(0xFF1B2A5C)

/** Nombre d'étoiles du ciel : fixe, pour que la grille ne change pas d'aspect d'un affichage à l'autre. */
private const val STAR_COUNT = 90

/** Peint le fond de la grille (les dix colonnes et dix lignes, à partir de [topLeft]). [time] (0..1) fait scintiller les étoiles. */
internal fun DrawScope.drawBoardBackdrop(backdrop: BattleshipBackdrop, topLeft: Offset, side: Float, cs: Float, time: Float = 0f) {
    val size = Size(side, side)
    if (backdrop == BattleshipBackdrop.SEA) {
        drawRect(SEA_COLOR, topLeft = topLeft, size = size)
        return
    }
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(SKY_TOP, SKY_BOTTOM),
            startY = topLeft.y,
            endY = topLeft.y + side
        ),
        topLeft = topLeft,
        size = size
    )
    // Étoiles placées par une suite déterministe : toujours les mêmes, donc pas de papillotement à chaque recomposition.
    var seed = 12345L
    fun next(): Float {
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        return (seed % 10000L) / 10000f
    }
    for (i in 0 until STAR_COUNT) {
        val x = topLeft.x + next() * side
        val y = topLeft.y + next() * side
        val base = 0.35f + 0.65f * next()
        val phase = next()
        val big = i % 9 == 0
        val twinkle = 0.75f + 0.25f * sin(2f * PI.toFloat() * (time + phase))
        val radius = cs * (if (big) 0.045f else 0.022f) * (0.7f + base * 0.6f)
        val color = if (i % 5 == 0) Color(0xFFFFF3C4) else Color.White
        drawCircle(color.copy(alpha = (base * twinkle).coerceIn(0f, 1f)), radius = radius, center = Offset(x, y))
        if (big) {
            // Les plus brillantes ont de petits rayons en croix.
            val ray = cs * 0.1f
            val c = color.copy(alpha = 0.55f * twinkle)
            drawLine(c, Offset(x - ray, y), Offset(x + ray, y), strokeWidth = cs * 0.012f)
            drawLine(c, Offset(x, y - ray), Offset(x, y + ray), strokeWidth = cs * 0.012f)
        }
    }
}
