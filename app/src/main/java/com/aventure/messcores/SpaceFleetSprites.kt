package com.aventure.messcores

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Les vrais vaisseaux (photos de maquettes détourées, nez à droite) qui remplacent le dessin vectoriel sur le fond étoilé.
 * Un vaisseau par bateau de [Battleship.FLEET] ; pour les deux de longueur 3, [variant] choisit lequel (0 ou 1).
 * Si une image ne peut pas être lue, [bitmap] rend null et l'appelant garde le dessin vectoriel.
 */
internal object SpaceFleetSprites {
    private val RESOURCES: Map<Int, List<Int>> = mapOf(
        5 to listOf(R.drawable.ship_space_5),
        4 to listOf(R.drawable.ship_space_4),
        3 to listOf(R.drawable.ship_space_3a, R.drawable.ship_space_3b),
        2 to listOf(R.drawable.ship_space_2)
    )

    private val cache = HashMap<Int, ImageBitmap?>()

    /** Lit les images une fois pour toutes (appel sans effet les fois suivantes). */
    @Synchronized
    fun load(context: Context) {
        if (cache.isNotEmpty()) return
        for (id in RESOURCES.values.flatten()) {
            cache[id] = try {
                BitmapFactory.decodeResource(context.resources, id)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    @Synchronized
    fun bitmap(length: Int, variant: Int): ImageBitmap? {
        val ids = RESOURCES[length] ?: return null
        return cache[ids[variant.coerceIn(0, ids.lastIndex)]]
    }
}

/** Rang d'un bateau parmi ceux de même longueur dans [lengths] : distingue les deux vaisseaux de longueur 3. */
internal fun spriteVariant(lengths: List<Int>, index: Int): Int = lengths.take(index).count { it == lengths[index] }

// Un vaisseau détruit : gris sombre, sans couleur.
internal val SUNK_SPRITE_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.15f, 0.29f, 0.06f, 0f, 0f,
            0.15f, 0.29f, 0.06f, 0f, 0f,
            0.15f, 0.29f, 0.06f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/**
 * Peint [sprite] dans le repère du vaisseau (couché vers la droite, [length] cases sur une) : à la hauteur d'une case
 * (un peu plus, les vaisseaux sont larges), allongé (d'au plus un tiers, mais sur toute la longueur pour le porte-vaisseaux), et centré.
 */
internal fun DrawScope.drawSpaceSprite(sprite: ImageBitmap, length: Int, cs: Float, sunk: Boolean, alpha: Float) {
    val box = length * cs * 0.97f
    val ratio = sprite.width.toFloat() / sprite.height
    val height = min(cs * 1.2f, box / ratio)
    val natural = height * ratio
    // Le porte-vaisseaux (5 cases) est étiré sur toute sa longueur, quitte à déformer ; les autres d'au plus un tiers.
    val width = if (length >= 5) box else min(box, natural * 1.35f)
    val x = (length * cs - width) / 2f
    val y = (cs - height) / 2f
    drawImage(
        image = sprite,
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize(width.roundToInt(), height.roundToInt()),
        alpha = alpha,
        colorFilter = if (sunk) SUNK_SPRITE_FILTER else null
    )
}
