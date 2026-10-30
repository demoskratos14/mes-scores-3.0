package com.aventure.messcores

import androidx.annotation.StringRes
import java.text.Collator
import java.util.Locale

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
    "builtin_manille", "builtin_scopa", "builtin_yaniv", "builtin_barbu", "builtin_ohhell", "builtin_sueca",
    "builtin_poker", "builtin_bridge", "builtin_skat"
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
    "builtin_sevenwonders", "builtin_wingspan", "builtin_hanabi", "builtin_scrabble", "builtin_trivial",
    "builtin_flip7", "builtin_mastermind", "builtin_morpion", "builtin_connect4", "builtin_battleship", "builtin_nim", "builtin_pfc",
    "builtin_gomoku", "builtin_dots", "builtin_draughts", "builtin_othello", "builtin_hangman", "builtin_chess"
)

/**
 * Vrai si le jeu se joue dans l'appli (accueil « Jeux jouables ») : le Mastermind, le Morpion, le Puissance 4, le touché coulé, le jeu des allumettes, Pierre-feuille-ciseaux, le Gomoku, la
 * Pipopipette, les dames, l'Othello, le Pendu et les échecs, qui ont leur propre écran, les jeux de dés qui ont des dés à lancer (voir [DiceSetups]) et le Huit américain, premier jeu de cartes
 * distribué par l'appli (voir [HuitRound]). Les autres jeux de cartes, le poker compris, restent des feuilles de score :
 * les cartes n'y sont pas distribuées. King of Tokyo aussi : il n'a que des dés génériques sur sa feuille (voir [hasDiceOnSheet]), il n'est donc pas listé ici.
 */
fun GameRules.isPlayableGame(): Boolean =
    id == MASTERMIND_ID || id == MORPION_ID || id == CONNECT4_ID || id == BATTLESHIP_ID || id == NIM_ID || id == PFC_ID ||
        id == GOMOKU_ID || id == DOTS_ID || id == DRAUGHTS_ID || id == OTHELLO_ID || id == HANGMAN_ID || id == CHESS_ID ||
        id == HUIT_ID || (DiceSetups.forGame(id) != null && !hasDiceOnSheet())

/** Vrai si le jeu a une feuille de scores : le Mastermind, le Morpion, le Puissance 4, le touché coulé, le jeu des allumettes, les dames, l'Othello, le Pendu et les échecs ont chacun leur écran, sans feuille. */
fun GameRules.hasScoreSheet(): Boolean =
    id != MASTERMIND_ID && id != MORPION_ID && id != CONNECT4_ID && id != BATTLESHIP_ID && id != NIM_ID && id != PFC_ID &&
        id != GOMOKU_ID && id != DOTS_ID && id != DRAUGHTS_ID && id != OTHELLO_ID && id != HANGMAN_ID && id != CHESS_ID

// Jeux jouables qui ne se partagent pas (encore) entre plusieurs téléphones : le Huit américain, dont chaque joueur cache sa main : on se passe le téléphone.
// Le Mastermind, le Morpion, le Gomoku, la Pipopipette, le jeu des allumettes, les dames, l'Othello, le Pendu et les échecs ont leur table en ligne, et les jeux de dés se partagent comme leur feuille de scores (dés compris).
private val SINGLE_PHONE_GAMES = setOf(HUIT_ID)

/** Vrai si le jeu jouable peut se jouer en connectant plusieurs téléphones (deux téléphones dessinés dans la liste). */
fun GameRules.isMultiPhone(): Boolean = isPlayableGame() && id !in SINGLE_PHONE_GAMES

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

/**
 * Jeux triés par ordre alphabétique du nom, selon la langue [locale] : la casse et les accents ne
 * comptent pas (« Écarté » se range avec les E), les chiffres passent avant les lettres. À égalité,
 * l'ordre d'origine est conservé. Sert à ranger chaque rubrique de la liste de choix.
 */
fun List<GameRules>.sortedAlphabetically(locale: Locale): List<GameRules> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
}
