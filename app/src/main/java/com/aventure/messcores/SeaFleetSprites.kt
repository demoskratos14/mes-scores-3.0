package com.aventure.messcores

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Les images de la mer : le fond de la grille et les vrais navires (vus de dessus, détourés, proue à droite).
 * Un navire par bateau de [Battleship.FLEET] : porte-avions (5), frégate (4), sous-marin et torpilleur (3, un pour
 * chacun des deux bateaux de longueur 3) et patrouilleur (2). Si une image ne peut pas être lue, l'appelant garde le dessin vectoriel.
 */
internal object SeaFleetSprites {
    private val SHIPS: Map<Int, List<Int>> = mapOf(
        5 to listOf(R.drawable.ship_sea_5),
        4 to listOf(R.drawable.ship_sea_4),
        3 to listOf(R.drawable.ship_sea_3, R.drawable.ship_sea_3b),
        2 to listOf(R.drawable.ship_sea_2)
    )

    private val cache = HashMap<Int, ImageBitmap?>()
    private var background: ImageBitmap? = null
    private var loaded = false

    /** Lit les images une fois pour toutes (appel sans effet les fois suivantes). */
    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        for (id in SHIPS.values.flatten()) {
            cache[id] = decode(context, id)
        }
        background = decode(context, R.drawable.bg_sea)
    }

    private fun decode(context: Context, id: Int): ImageBitmap? = try {
        BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }

    @Synchronized
    fun bitmap(length: Int, variant: Int): ImageBitmap? {
        val ids = SHIPS[length] ?: return null
        return cache[ids[variant.coerceIn(0, ids.lastIndex)]]
    }

    @Synchronized
    fun background(): ImageBitmap? = background
}

/** Peint la mer sur toute la grille ; rend false si l'image n'est pas disponible. */
internal fun DrawScope.drawSeaImage(topLeft: Offset, side: Float): Boolean {
    val image = SeaFleetSprites.background() ?: return false
    drawImage(
        image = image,
        dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
        dstSize = IntSize(side.roundToInt(), side.roundToInt())
    )
    return true
}

/**
 * Peint le navire dans le repère du bateau (couché vers la droite, [length] cases sur une). Les proportions de
 * l'image sont gardées (au plus un quart d'étirement pour remplir la longueur) ; le porte-avions, très large,
 * déborde un peu des cases voisines. Rend false si l'image n'est pas disponible.
 */
internal fun DrawScope.drawSeaSprite(length: Int, cs: Float, sunk: Boolean, alpha: Float, variant: Int = 0): Boolean {
    val sprite = SeaFleetSprites.bitmap(length, variant) ?: return false
    val box = length * cs * 0.97f
    val ratio = sprite.width.toFloat() / sprite.height
    val height = min(cs * 1.4f, box / ratio)
    val width = min(box, height * ratio * 1.25f)
    val x = (length * cs - width) / 2f
    val y = (cs - height) / 2f
    drawImage(
        image = sprite,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        alpha = alpha,
        colorFilter = if (sunk) SUNK_SPRITE_FILTER else null
    )
    return true
}
