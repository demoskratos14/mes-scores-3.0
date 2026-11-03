package com.aventure.messcores

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Les 52 images du jeu de cartes (res/drawable-nodpi), dans l'ordre de [FrenchCards.card] : pique, cœur, carreau, trèfle, de l'as au roi. */
private val FRENCH_CARD_IMAGES = intArrayOf(
    R.drawable.fr_card_s01, R.drawable.fr_card_s02, R.drawable.fr_card_s03, R.drawable.fr_card_s04, R.drawable.fr_card_s05, R.drawable.fr_card_s06, R.drawable.fr_card_s07, R.drawable.fr_card_s08, R.drawable.fr_card_s09, R.drawable.fr_card_s10, R.drawable.fr_card_s11, R.drawable.fr_card_s12, R.drawable.fr_card_s13,
    R.drawable.fr_card_h01, R.drawable.fr_card_h02, R.drawable.fr_card_h03, R.drawable.fr_card_h04, R.drawable.fr_card_h05, R.drawable.fr_card_h06, R.drawable.fr_card_h07, R.drawable.fr_card_h08, R.drawable.fr_card_h09, R.drawable.fr_card_h10, R.drawable.fr_card_h11, R.drawable.fr_card_h12, R.drawable.fr_card_h13,
    R.drawable.fr_card_d01, R.drawable.fr_card_d02, R.drawable.fr_card_d03, R.drawable.fr_card_d04, R.drawable.fr_card_d05, R.drawable.fr_card_d06, R.drawable.fr_card_d07, R.drawable.fr_card_d08, R.drawable.fr_card_d09, R.drawable.fr_card_d10, R.drawable.fr_card_d11, R.drawable.fr_card_d12, R.drawable.fr_card_d13,
    R.drawable.fr_card_c01, R.drawable.fr_card_c02, R.drawable.fr_card_c03, R.drawable.fr_card_c04, R.drawable.fr_card_c05, R.drawable.fr_card_c06, R.drawable.fr_card_c07, R.drawable.fr_card_c08, R.drawable.fr_card_c09, R.drawable.fr_card_c10, R.drawable.fr_card_c11, R.drawable.fr_card_c12, R.drawable.fr_card_c13,
)

/** L'image de la carte [card] du jeu de 52 cartes. */
@DrawableRes
internal fun frenchCardImage(card: Int): Int = FRENCH_CARD_IMAGES[card.coerceIn(0, FrenchCards.DECK - 1)]

/** Largeur / hauteur des images des cartes. */
internal const val FRENCH_CARD_RATIO = 192f / 279f

/** Filtre des cartes qu'on ne peut pas jouer : désaturées et assombries. */
private val BlockedFilter = ColorFilter.colorMatrix(
    ColorMatrix().apply { setToSaturation(0f) }.also { m ->
        val s = 0.7f
        val a = m.values
        for (row in 0..2) for (col in 0..2) a[row * 5 + col] *= s
    }
)

/**
 * Une carte à jouer affichée par son image. [playable] faux : grisée ; [selected] entoure la carte ;
 * [onClick] null : simple affichage. [description] est lue par TalkBack.
 */
@Composable
internal fun ImageCard(
    @DrawableRes image: Int,
    description: String,
    playable: Boolean,
    modifier: Modifier = Modifier,
    ratio: Float = FRENCH_CARD_RATIO,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .aspectRatio(ratio)
            .clip(shape)
            .then(if (selected) Modifier.border(4.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics { contentDescription = description }
    ) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            colorFilter = if (playable) null else BlockedFilter,
            alpha = if (playable) 1f else 0.8f,
            modifier = Modifier.matchParentSize()
        )
    }
}
