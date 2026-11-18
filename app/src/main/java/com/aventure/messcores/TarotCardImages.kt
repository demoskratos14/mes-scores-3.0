package com.aventure.messcores

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Les 78 images du jeu de Tarot (res/drawable-nodpi/tarot_*.webp), dans l'ordre de [TarotCards] : les 4 couleurs (pique, cœur,
 * carreau, trèfle) de 1 à 14 (valet, cavalier, dame, roi), les atouts 1 à 21, puis l'Excuse.
 */
private val TAROT_IMAGES = intArrayOf(
    R.drawable.tarot_s01, R.drawable.tarot_s02, R.drawable.tarot_s03, R.drawable.tarot_s04, R.drawable.tarot_s05, R.drawable.tarot_s06, R.drawable.tarot_s07, R.drawable.tarot_s08, R.drawable.tarot_s09, R.drawable.tarot_s10, R.drawable.tarot_s11, R.drawable.tarot_s12, R.drawable.tarot_s13, R.drawable.tarot_s14,
    R.drawable.tarot_h01, R.drawable.tarot_h02, R.drawable.tarot_h03, R.drawable.tarot_h04, R.drawable.tarot_h05, R.drawable.tarot_h06, R.drawable.tarot_h07, R.drawable.tarot_h08, R.drawable.tarot_h09, R.drawable.tarot_h10, R.drawable.tarot_h11, R.drawable.tarot_h12, R.drawable.tarot_h13, R.drawable.tarot_h14,
    R.drawable.tarot_d01, R.drawable.tarot_d02, R.drawable.tarot_d03, R.drawable.tarot_d04, R.drawable.tarot_d05, R.drawable.tarot_d06, R.drawable.tarot_d07, R.drawable.tarot_d08, R.drawable.tarot_d09, R.drawable.tarot_d10, R.drawable.tarot_d11, R.drawable.tarot_d12, R.drawable.tarot_d13, R.drawable.tarot_d14,
    R.drawable.tarot_c01, R.drawable.tarot_c02, R.drawable.tarot_c03, R.drawable.tarot_c04, R.drawable.tarot_c05, R.drawable.tarot_c06, R.drawable.tarot_c07, R.drawable.tarot_c08, R.drawable.tarot_c09, R.drawable.tarot_c10, R.drawable.tarot_c11, R.drawable.tarot_c12, R.drawable.tarot_c13, R.drawable.tarot_c14,
    R.drawable.tarot_t01, R.drawable.tarot_t02, R.drawable.tarot_t03, R.drawable.tarot_t04, R.drawable.tarot_t05, R.drawable.tarot_t06, R.drawable.tarot_t07, R.drawable.tarot_t08, R.drawable.tarot_t09, R.drawable.tarot_t10, R.drawable.tarot_t11, R.drawable.tarot_t12, R.drawable.tarot_t13, R.drawable.tarot_t14, R.drawable.tarot_t15, R.drawable.tarot_t16, R.drawable.tarot_t17, R.drawable.tarot_t18, R.drawable.tarot_t19, R.drawable.tarot_t20, R.drawable.tarot_t21,
    R.drawable.tarot_excuse
)

/** L'image de la carte [card] du jeu de Tarot. */
@DrawableRes
internal fun tarotCardImage(card: Int): Int = TAROT_IMAGES[card.coerceIn(0, TarotCards.DECK - 1)]

/** Largeur / hauteur des images des cartes de Tarot. */
internal const val TAROT_CARD_RATIO = 400f / 705f

/** Le dos du jeu de Tarot (res/drawable-nodpi/tarot_back.webp), pour le chien qu'on ne montre pas. */
@Composable
internal fun TarotCardBack(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.tarot_back),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = modifier
            .aspectRatio(TAROT_CARD_RATIO)
            .clip(RoundedCornerShape(6.dp))
    )
}

/** Les noms des cartes de Tarot dans la langue de l'appli (« roi de cœur », « Atout 21 », « Excuse »), lus par TalkBack. */
internal class TarotNamer(private val resources: android.content.res.Resources) {
    private val ranks = resources.getString(R.string.tarot_ranks).split(',')
    private val suits = resources.getString(R.string.huit_suits).split(',')

    fun name(card: Int): String = when {
        TarotCards.isExcuse(card) -> resources.getString(R.string.tarot_excuse_name)
        TarotCards.isTrump(card) -> resources.getString(R.string.tarot_trump_name, TarotCards.trumpNumber(card))
        else -> resources.getString(
            R.string.huit_card_desc,
            ranks.getOrElse(TarotCards.rank(card) - 1) { "" },
            suits.getOrElse(TarotCards.suit(card)) { "" }
        )
    }
}

/** Les noms des cartes de Tarot, créés une fois pour la langue de l'appli. */
@Composable
internal fun rememberTarotNamer(): TarotNamer {
    val resources = LocalResources.current
    return remember(resources) { TarotNamer(resources) }
}

/**
 * Une carte de Tarot, affichée par son image. [selected] entoure la carte ; [onClick] null : simple affichage. Les cartes ne sont
 * jamais grisées (ce serait une aide) : une carte refusée l'est par un message.
 */
@Composable
internal fun TarotCardFace(
    card: Int,
    namer: TarotNamer,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    ImageCard(
        image = tarotCardImage(card),
        description = namer.name(card),
        playable = true,
        modifier = modifier,
        ratio = TAROT_CARD_RATIO,
        selected = selected,
        onClick = onClick
    )
}
