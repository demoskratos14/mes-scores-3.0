package com.aventure.messcores

import androidx.annotation.StringRes

/** Rubrique d'un jeu dans la liste de choix (l'ordre de déclaration est l'ordre d'affichage). */
enum class GameCategory(@StringRes val label: Int) {
    CLASSIC(R.string.category_classic),
    CARDS(R.string.category_cards),
    DICE(R.string.category_dice),
    OTHER(R.string.category_other)
}

// Jeux de cartes classiques (cartes à nombre et figures, quel que soit le paquet : 32, 52, 78…).
// Skyjo et Uno ont des paquets spéciaux : ils vont dans « Autres ».
private val CARD_GAMES = setOf(
    "builtin_tarot", "builtin_belote", "builtin_rami", "builtin_coinche", "builtin_president",
    "builtin_coeurs", "builtin_pique", "builtin_gin", "builtin_huit", "builtin_cribbage",
    "builtin_manille", "builtin_scopa", "builtin_yaniv", "builtin_barbu", "builtin_ohhell"
)

private val DICE_GAMES = setOf(
    "builtin_yams", "builtin_421", "builtin_cdc", "builtin_dixmille", "builtin_cochon", "builtin_zombie",
    "builtin_shutbox", "builtin_bunco", "builtin_kot", "builtin_mexicain", "builtin_qwixx"
)

/**
 * Rubrique du jeu dans la liste. Elle se déduit de l'identifiant (rien n'est enregistré avec la partie) :
 * les jeux à paquet spécial (Skyjo, Uno), ceux créés par l'utilisateur et tout jeu inconnu vont dans « Autres ».
 */
fun GameRules.category(): GameCategory = when (id) {
    "builtin_generic" -> GameCategory.CLASSIC
    in CARD_GAMES -> GameCategory.CARDS
    in DICE_GAMES -> GameCategory.DICE
    else -> GameCategory.OTHER
}
