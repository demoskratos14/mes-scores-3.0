package com.aventure.messcores

import androidx.annotation.StringRes

/** Rubrique d'un jeu dans la liste de choix (l'ordre de déclaration est l'ordre d'affichage). */
enum class GameCategory(@StringRes val label: Int) {
    CLASSIC(R.string.category_classic),
    CARDS(R.string.category_cards),
    DICE(R.string.category_dice),
    SPECIAL(R.string.category_special),
    OTHER(R.string.category_other)
}

// Jeux de cartes classiques (cartes à nombre et figures, quel que soit le paquet : 32, 52, 78…).
private val CARD_GAMES = setOf(
    "builtin_tarot", "builtin_belote", "builtin_rami", "builtin_coinche", "builtin_president",
    "builtin_coeurs", "builtin_pique", "builtin_gin", "builtin_huit", "builtin_cribbage",
    "builtin_manille", "builtin_scopa", "builtin_yaniv", "builtin_barbu", "builtin_ohhell"
)

private val DICE_GAMES = setOf(
    "builtin_yams", "builtin_421", "builtin_cdc", "builtin_dixmille", "builtin_cochon", "builtin_zombie",
    "builtin_shutbox", "builtin_bunco", "builtin_kot", "builtin_mexicain", "builtin_qwixx"
)

// Jeux à paquet, plateau ou matériel spécial (cartes spéciales, tuiles, plateaux de jeux de société).
private val SPECIAL_GAMES = setOf(
    "builtin_skyjo", "builtin_uno", "builtin_milleb", "builtin_sixqp", "builtin_papayoo", "builtin_cabo",
    "builtin_rummikub", "builtin_dominos", "builtin_wizard", "builtin_phase10", "builtin_splendor",
    "builtin_dixit", "builtin_ttr", "builtin_azul", "builtin_carcassonne", "builtin_catane",
    "builtin_sevenwonders", "builtin_scrabble", "builtin_trivial"
)

/**
 * Rubrique du jeu dans la liste. Elle se déduit de l'identifiant (rien n'est enregistré avec la partie) :
 * les sports et loisirs (pétanque, bowling, fléchettes…), les jeux créés par l'utilisateur et tout jeu
 * inconnu vont dans « Autres ».
 */
fun GameRules.category(): GameCategory = when (id) {
    "builtin_generic" -> GameCategory.CLASSIC
    in CARD_GAMES -> GameCategory.CARDS
    in DICE_GAMES -> GameCategory.DICE
    in SPECIAL_GAMES -> GameCategory.SPECIAL
    else -> GameCategory.OTHER
}
