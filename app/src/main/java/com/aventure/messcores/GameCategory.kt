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
    "builtin_skyjo", "builtin_skyjo_action", "builtin_uno", "builtin_milleb", "builtin_sixqp", "builtin_papayoo", "builtin_cabo",
    "builtin_rummikub", "builtin_dominos", "builtin_trio", "builtin_wizard", "builtin_phase10", "builtin_splendor",
    "builtin_dixit", "builtin_ttr", "builtin_azul", "builtin_carcassonne", "builtin_catane",
    "builtin_sevenwonders", "builtin_wingspan", "builtin_hanabi", "builtin_scrabble", "builtin_trivial",
    "builtin_flip7", "builtin_mastermind", "builtin_morpion", "builtin_connect4", "builtin_battleship", "builtin_nim", "builtin_pfc", "builtin_reflex", "builtin_emoji", "builtin_circles", "builtin_simon", "builtin_rhythm", "builtin_tetris", "builtin_breakout", "builtin_asteroids", "builtin_invaders", "builtin_shmup", "builtin_xonix", "builtin_towerdefense", "builtin_snake", "builtin_bubble", "builtin_marble", "builtin_minesweeper", "builtin_sudoku", "builtin_pancake", "builtin_drive", "builtin_drink", "builtin_runner",
    "builtin_gomoku", "builtin_dots", "builtin_draughts", "builtin_othello", "builtin_hangman", "builtin_chess", "builtin_werewolf"
)

/**
 * Vrai si le jeu se joue dans l'appli (accueil « Jeux jouables ») : le Mastermind, le Morpion, le Puissance 4, le touché coulé, le jeu des allumettes, Pierre-feuille-ciseaux, le Duel de réflexes, la Chasse à l'émoji, les Ronds, Simon, le jeu de rythme, le Tetris, le Casse-briques, les Astéroïdes, les Envahisseurs, le Xonix, le Snake, le Bubble Shooter, le Marble Shooter, le Démineur, le Gomoku, la
 * Pipopipette, les dames, l'Othello, le Pendu et les échecs, qui ont leur propre écran, les jeux de dés qui ont des dés à lancer (voir [DiceSetups]) et le Huit américain, le Uno, Cœurs, le Barbu, Oh Hell, Yaniv, le Rami, le Président, Flip 7 et le Skyjo Action, jeux de cartes
 * distribués par l'appli (voir [HuitRound], [UnoRound], [HeartsRound], [BarbuRound], [OhHellRound], [YanivRound], [RamiRound], [PresidentRound], [Flip7Round], [SkyjoActionRound], [TrioRound] et [DominoRound], ce dernier jouant avec des dominos). Les autres jeux de cartes restent des feuilles de score : les cartes n'y sont pas distribuées. Le poker garde son propre écran de tournoi
 * et distribue ses cartes en option, sur un ou plusieurs téléphones (voir [PokerSettings.dealCards] et [PokerOnline]). King of Tokyo aussi : il n'a que des dés génériques sur sa feuille (voir [hasDiceOnSheet]), il n'est donc pas listé ici.
 */
fun GameRules.isPlayableGame(): Boolean =
    id == MASTERMIND_ID || id == MORPION_ID || id == CONNECT4_ID || id == BATTLESHIP_ID || id == NIM_ID || id == PFC_ID || id == REFLEX_ID || id == EMOJI_ID || id == CIRCLES_ID || id == SIMON_ID || id == RHYTHM_ID || id == TETRIS_ID || id == BREAKOUT_ID || id == ASTEROIDS_ID || id == INVADERS_ID || id == SHMUP_ID || id == XONIX_ID || id == TOWER_DEFENSE_ID || id == SNAKE_ID || id == BUBBLE_ID || id == MARBLE_ID || id == MINESWEEPER_ID || id == SUDOKU_ID || id == PANCAKE_ID || id == DRIVE_ID || id == DRINK_ID || id == RUNNER_ID ||
        id == GOMOKU_ID || id == DOTS_ID || id == DRAUGHTS_ID || id == OTHELLO_ID || id == HANGMAN_ID || id == CHESS_ID ||
        id == HUIT_ID || id == UNO_ID || id == HEARTS_ID || id == TAROT_ID || id == OH_HELL_ID || id == BARBU_ID || id == YANIV_ID || id == RAMI_ID || id == PRESIDENT_ID || id == FLIP7_ID || id == SKYJO_ACTION_ID || id == DOMINOS_ID || id == TRIO_ID || id == WEREWOLF_ID || (DiceSetups.forGame(id) != null && !hasDiceOnSheet())

/**
 * Vrai si le jeu peut se jouer seul (accueil « Jeux solo ») : Tetris, Snake, Casse-briques… mais aussi les Ronds et Simon,
 * qui se jouent seul ou à plusieurs et sont donc aussi listés dans « Jeux jouables » (voir [isSoloOnlyGame]).
 */
fun GameRules.isSoloGame(): Boolean = isPlayableGame() && minPlayers == 1

/** Vrai si le jeu ne se joue qu'à un seul joueur : il n'apparaît alors que dans « Jeux solo », pas dans « Jeux jouables ». */
fun GameRules.isSoloOnlyGame(): Boolean = isSoloGame() && maxPlayers == 1

/** Vrai si le jeu a une feuille de scores : le Mastermind, le Morpion, le Puissance 4, le touché coulé, le jeu des allumettes, les dames, l'Othello, le Pendu et les échecs ont chacun leur écran, sans feuille. */
fun GameRules.hasScoreSheet(): Boolean =
    id != MASTERMIND_ID && id != MORPION_ID && id != CONNECT4_ID && id != BATTLESHIP_ID && id != NIM_ID && id != PFC_ID && id != REFLEX_ID && id != EMOJI_ID && id != CIRCLES_ID && id != SIMON_ID && id != RHYTHM_ID && id != TETRIS_ID && id != BREAKOUT_ID && id != ASTEROIDS_ID && id != INVADERS_ID && id != SHMUP_ID && id != XONIX_ID && id != TOWER_DEFENSE_ID && id != SNAKE_ID && id != BUBBLE_ID && id != MARBLE_ID && id != MINESWEEPER_ID && id != SUDOKU_ID && id != PANCAKE_ID && id != DRIVE_ID && id != DRINK_ID && id != RUNNER_ID &&
        id != GOMOKU_ID && id != DOTS_ID && id != DRAUGHTS_ID && id != OTHELLO_ID && id != HANGMAN_ID && id != CHESS_ID && id != WEREWOLF_ID

// Jeux jouables qui ne se partagent pas (encore) entre plusieurs téléphones : les jeux d'adresse ou d'arcade qui se jouent seul.
// Le Huit américain (voir [HuitOnline]), Cœurs, le Barbu (voir [BarbuOnline]), Oh Hell (voir [OhHellOnline]), le Président, Yaniv, le Rami (voir [RamiOnline]), Flip 7, le Domino (voir [DominoOnline]), le Trio (voir [TrioOnline]), le Uno (voir [UnoOnline]) et Skyjo Action (voir [SkyjoActionOnline]) se jouent chacun sur son téléphone : chaque invité ne reçoit que sa main.
// Le Mastermind, le Morpion, Simon, les Ronds (voir [CirclesOnline]), le Duel de réflexes, le Gomoku, la Pipopipette, le jeu des allumettes, les dames, l'Othello, le Pendu et les échecs ont leur table en ligne, et les jeux de dés se partagent comme leur feuille de scores (dés compris).
private val SINGLE_PHONE_GAMES = setOf(RHYTHM_ID, TETRIS_ID, BREAKOUT_ID, ASTEROIDS_ID, INVADERS_ID, SHMUP_ID, XONIX_ID, TOWER_DEFENSE_ID, SNAKE_ID, BUBBLE_ID, MARBLE_ID, MINESWEEPER_ID, SUDOKU_ID, PANCAKE_ID, DRIVE_ID, DRINK_ID, RUNNER_ID)

/** Vrai si le jeu jouable peut se jouer en connectant plusieurs téléphones (deux téléphones dessinés dans la liste). */
fun GameRules.isMultiPhone(): Boolean = isPlayableGame() && id !in SINGLE_PHONE_GAMES

// Jeux qui ne se jouent qu'en connectant plusieurs téléphones : un téléphone par joueur (et un narrateur pour le Loup-garou, voir
// [WerewolfOnline]). Ils n'apparaissent que dans la salle multijoueur, jamais dans « Jeux jouables » ni dans « Jeux solo ».
private val MULTI_PHONE_ONLY_GAMES = setOf(WEREWOLF_ID)

/** Vrai si le jeu ne se joue que sur plusieurs téléphones. */
fun GameRules.isMultiPhoneOnly(): Boolean = id in MULTI_PHONE_ONLY_GAMES

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
