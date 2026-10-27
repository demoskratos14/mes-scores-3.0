package com.aventure.messcores

import android.content.Context

/**
 * Règles prédéfinies : Jeux à paquet, plateau ou matériel spécial (rubrique « Spéciaux »).
 * Pour ajouter un jeu de cette rubrique, ajouter un bloc GameRules ici,
 * puis son identifiant dans GameCategory.kt (sauf pour « Autres »).
 */
internal fun specialGames(context: Context): List<GameRules> = listOf(
    GameRules(
        id = "builtin_skyjo",
        name = context.getString(R.string.game_skyjo),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 100, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_uno",
        name = context.getString(R.string.game_uno),
        minPlayers = 2,
        maxPlayers = 10,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_milleb",
        name = context.getString(R.string.game_milleb),
        minPlayers = 2,
        maxPlayers = 6,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 5000, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_sixqp",
        name = context.getString(R.string.game_sixqp),
        minPlayers = 2,
        maxPlayers = 10,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 66, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_papayoo",
        name = context.getString(R.string.game_papayoo),
        minPlayers = 3,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_cabo",
        name = context.getString(R.string.game_cabo),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 101, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_rummikub",
        name = context.getString(R.string.game_rummikub),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_dominos",
        name = context.getString(R.string.game_dominos),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 100, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_wizard",
        name = context.getString(R.string.game_wizard),
        minPlayers = 3,
        maxPlayers = 6,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE,
        bidScoring = BidScoring.WIZARD
    ),
    GameRules(
        id = "builtin_phase10",
        name = context.getString(R.string.game_phase10),
        minPlayers = 2,
        maxPlayers = 6,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        phases = 10
    ),
    GameRules(
        id = "builtin_splendor",
        name = context.getString(R.string.game_splendor),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 15, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_dixit",
        name = context.getString(R.string.game_dixit),
        minPlayers = 3,
        maxPlayers = 8,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 30, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_ttr",
        name = context.getString(R.string.game_ttr),
        minPlayers = 2,
        maxPlayers = 5,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_azul",
        name = context.getString(R.string.game_azul),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_carcassonne",
        name = context.getString(R.string.game_carcassonne),
        minPlayers = 2,
        maxPlayers = 5,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_catane",
        name = context.getString(R.string.game_catane),
        minPlayers = 3,
        maxPlayers = 6,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 10, stopImmediately = true)
    ),
    GameRules(
        id = "builtin_sevenwonders",
        name = context.getString(R.string.game_sevenwonders),
        minPlayers = 3,
        maxPlayers = 7,
        lowestWins = false,
        // Le militaire peut être négatif (jetons de défaite).
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.CUSTOM,
        sheetRows = listOf(
            R.string.sheet_sevenwonders_military, R.string.sheet_sevenwonders_coins,
            R.string.sheet_sevenwonders_wonders, R.string.sheet_sevenwonders_civil,
            R.string.sheet_sevenwonders_commerce, R.string.sheet_sevenwonders_guilds,
            R.string.sheet_sevenwonders_science
        ).map { CustomSheetRow(context.getString(it)) }
    ),
    GameRules(
        id = "builtin_wingspan",
        name = context.getString(R.string.game_wingspan),
        minPlayers = 1,
        maxPlayers = 5,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.CUSTOM,
        sheetRows = listOf(
            R.string.sheet_wingspan_birds to R.string.sheet_wingspan_birds_hint,
            R.string.sheet_wingspan_bonus to R.string.sheet_wingspan_bonus_hint,
            R.string.sheet_wingspan_goals to R.string.sheet_wingspan_goals_hint,
            R.string.sheet_wingspan_eggs to R.string.sheet_wingspan_eggs_hint,
            R.string.sheet_wingspan_food to R.string.sheet_wingspan_food_hint,
            R.string.sheet_wingspan_tucked to R.string.sheet_wingspan_tucked_hint,
            // Extension Océanie : à laisser vide sans elle.
            R.string.sheet_wingspan_nectar to R.string.sheet_wingspan_nectar_hint
        ).map { (label, hint) -> CustomSheetRow(context.getString(label), hint = hint) }
    ),
    // Jeu coopératif : une seule colonne (l'équipe), une ligne par couleur avec la plus haute carte posée.
    GameRules(
        id = "builtin_hanabi",
        name = context.getString(R.string.game_hanabi),
        minPlayers = 1,
        maxPlayers = 1,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.CUSTOM,
        sheetRows = listOf(
            R.string.sheet_hanabi_white, R.string.sheet_hanabi_red, R.string.sheet_hanabi_blue,
            R.string.sheet_hanabi_yellow, R.string.sheet_hanabi_green
        ).map { CustomSheetRow(context.getString(it), listOf(0, 1, 2, 3, 4, 5), R.string.sheet_hanabi_hint) } +
            // Variante multicolore : à laisser à 0 sans elle.
            CustomSheetRow(context.getString(R.string.sheet_hanabi_rainbow), listOf(0, 1, 2, 3, 4, 5), R.string.sheet_hanabi_rainbow_hint)
    ),
    GameRules(
        id = "builtin_scrabble",
        name = context.getString(R.string.game_scrabble),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_trivial",
        name = context.getString(R.string.game_trivial),
        minPlayers = 2,
        maxPlayers = 6,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 6, stopImmediately = true)
    ),
    // Flip 7 : 200 points à atteindre ; la partie s'arrête à la fin de la manche où quelqu'un y arrive,
    // et une égalité en tête se joue en manche supplémentaire. La règle officielle va jusqu'à 18 joueurs,
    // l'appli s'arrête à 12 (la palette de couleurs et le tableau ne vont pas plus loin).
    GameRules(
        id = "builtin_flip7",
        name = context.getString(R.string.game_flip7),
        minPlayers = 3,
        maxPlayers = 12,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 200, stopImmediately = false, tieBreakOnEqualLeaders = true)
    ),
    // Mastermind : écran à part (voir MastermindScreen), le mode de score ci-dessous n'est pas utilisé.
    GameRules(
        id = MASTERMIND_ID,
        name = context.getString(R.string.game_mastermind),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Morpion : écran à part (voir TicTacToeScreen), à deux joueurs sur un téléphone ; le mode de score n'est pas utilisé.
    GameRules(
        id = MORPION_ID,
        name = context.getString(R.string.game_morpion),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Touché coulé : écran à part (voir BattleshipScreen), à deux joueurs ; le mode de score n'est pas utilisé.
    GameRules(
        id = BATTLESHIP_ID,
        name = context.getString(R.string.game_battleship),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Puissance 4 : écran à part (voir Connect4Screen), à deux joueurs sur un téléphone ou chacun sur le sien ; le mode de score n'est pas utilisé.
    GameRules(
        id = CONNECT4_ID,
        name = context.getString(R.string.game_connect4),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Jeu des allumettes : écran à part (voir NimScreen), à deux joueurs sur un téléphone ; le mode de score n'est pas utilisé.
    GameRules(
        id = NIM_ID,
        name = context.getString(R.string.game_nim),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Pierre-feuille-ciseaux : écran à part (voir RockPaperScissorsScreen), à deux joueurs sur un téléphone ou chacun sur le sien ; le mode de score n'est pas utilisé.
    GameRules(
        id = PFC_ID,
        name = context.getString(R.string.game_pfc),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Gomoku : écran à part (voir DuoGameScreen), à deux joueurs ; le mode de score n'est pas utilisé.
    GameRules(
        id = GOMOKU_ID,
        name = context.getString(R.string.game_gomoku),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Pipopipette : écran à part (voir DuoGameScreen), à deux joueurs ; le mode de score n'est pas utilisé.
    GameRules(
        id = DOTS_ID,
        name = context.getString(R.string.game_dots),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Dames : écran à part (voir DraughtsScreen), à deux joueurs sur un téléphone ou chacun sur le sien ; le mode de score n'est pas utilisé.
    GameRules(
        id = DRAUGHTS_ID,
        name = context.getString(R.string.game_draughts),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Othello : écran à part (voir OthelloScreen), à deux joueurs sur un ou deux téléphones ; le mode de score n'est pas utilisé.
    GameRules(
        id = OTHELLO_ID,
        name = context.getString(R.string.game_othello),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    // Pendu : écran à part (voir HangmanScreen), à deux joueurs ; le mode de score n'est pas utilisé.
    GameRules(
        id = HANGMAN_ID,
        name = context.getString(R.string.game_hangman),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    )
)
