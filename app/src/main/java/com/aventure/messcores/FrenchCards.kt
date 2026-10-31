package com.aventure.messcores

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource

/**
 * Le jeu de 52 cartes (Huit américain, Cœurs…). Une carte est un entier de 0 à 51 : `couleur * 13 + valeur - 1`, la valeur
 * allant de 1 (l'as) à 13 (le roi) et la couleur de 0 à 3 (pique, cœur, carreau, trèfle).
 */
internal object FrenchCards {
    const val SPADE = 0
    const val HEART = 1
    const val DIAMOND = 2
    const val CLUB = 3

    /** Nombre de couleurs du paquet. */
    const val SUITS = 4

    /** Nombre de valeurs par couleur : de l'as (1) au roi (13). */
    const val RANKS = 13

    /** Cartes du paquet (52, sans joker). */
    const val DECK = SUITS * RANKS

    /** Valeur de la carte [card], de 1 (as) à 13 (roi). */
    fun rank(card: Int): Int = card % RANKS + 1

    /** Couleur de la carte [card], de 0 à 3. */
    fun suit(card: Int): Int = card / RANKS

    /** La carte de couleur [suit] et de valeur [rank]. */
    fun card(suit: Int, rank: Int): Int = suit * RANKS + rank - 1

    /** Force de la carte [card] quand l'as est la plus forte : de 2 (le 2) à 14 (l'as). */
    fun strength(card: Int): Int = if (rank(card) == 1) 14 else rank(card)
}

/** Symboles des quatre couleurs, dans l'ordre de [FrenchCards.suit] : pique, cœur, carreau, trèfle. */
private val SUIT_SYMBOLS = listOf("♠", "♥", "♦", "♣")

/** Noms des valeurs (A, 2…10, V, D, R) et des couleurs, selon la langue de l'appli. */
internal class FrenchLabels(ranks: String, suits: String) {
    private val rankList = ranks.split(',')
    private val suitList = suits.split(',')

    fun rank(card: Int): String = rankList.getOrElse(FrenchCards.rank(card) - 1) { "" }

    fun suitName(suit: Int): String = suitList.getOrElse(suit) { "" }

    fun symbol(suit: Int): String = SUIT_SYMBOLS.getOrElse(suit) { "" }

    /** Texte écrit sur la carte : sa valeur et le symbole de sa couleur. */
    fun face(card: Int): String = rank(card) + symbol(FrenchCards.suit(card))

    /** Symbole et nom d'une couleur : « ♥ cœur ». */
    fun suitLabel(suit: Int): String = symbol(suit) + " " + suitName(suit)
}

/** Les labels du jeu de 52 cartes dans la langue de l'appli. */
@Composable
internal fun rememberFrenchLabels(): FrenchLabels =
    FrenchLabels(stringResource(R.string.huit_ranks), stringResource(R.string.huit_suits))

/** Rouge pour les cœurs et les carreaux, noir pour les piques et les trèfles. */
internal fun frenchSuitColor(suit: Int): Color =
    if (suit == FrenchCards.HEART || suit == FrenchCards.DIAMOND) CardRed else CardBlack

/**
 * Une carte à jouer : fond clair, valeur et couleur en noir ou en rouge. [playable] faux : estompée et non touchable ;
 * [selected] entoure la carte ; [onClick] null : simple affichage.
 */
@Composable
internal fun FrenchCardFace(
    card: Int,
    labels: FrenchLabels,
    playable: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val suit = FrenchCards.suit(card)
    val name = stringResource(R.string.huit_card_desc, labels.rank(card), labels.suitName(suit))
    PlayingCard(
        text = labels.face(card),
        description = if (playable) name else stringResource(R.string.huit_card_blocked, name),
        background = if (playable) CardWhite else CardBlocked,
        textColor = if (playable) frenchSuitColor(suit) else frenchSuitColor(suit).copy(alpha = 0.55f),
        modifier = modifier,
        selected = selected,
        onClick = onClick
    )
}
