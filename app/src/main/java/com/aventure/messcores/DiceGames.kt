package com.aventure.messcores

import android.content.Context

/**
 * Règles prédéfinies : Jeux de dés (rubrique « Dés »).
 * Pour ajouter un jeu de cette rubrique, ajouter un bloc GameRules ici,
 * puis son identifiant dans GameCategory.kt (sauf pour « Autres »).
 */
internal fun diceGames(context: Context): List<GameRules> = listOf(
    GameRules(
        id = "builtin_yams",
        name = context.getString(R.string.game_yams),
        minPlayers = 1,
        maxPlayers = 8,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.YAMS
    ),
    GameRules(
        id = "builtin_421",
        name = context.getString(R.string.game_421),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    GameRules(
        id = "builtin_cdc",
        name = context.getString(R.string.game_cdc),
        minPlayers = 2,
        maxPlayers = 10,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 343, stopImmediately = true)
    ),
    GameRules(
        id = "builtin_dixmille",
        name = context.getString(R.string.game_dixmille),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 10000, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_cochon",
        name = context.getString(R.string.game_cochon),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 100, stopImmediately = true)
    ),
    GameRules(
        id = "builtin_zombie",
        name = context.getString(R.string.game_zombie),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 13, stopImmediately = false, tieBreakOnEqualLeaders = true)
    ),
    GameRules(
        id = "builtin_shutbox",
        name = context.getString(R.string.game_shutbox),
        minPlayers = 1,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        // Chaque joueur inscrit sa manche à son tour : la partie s'arrête quand le dernier a joué la dernière manche.
        endCondition = EndCondition(type = EndConditionType.ROUND_COUNT, roundCount = ShutBoxScoring.ROUNDS, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_bunco",
        name = context.getString(R.string.game_bunco),
        minPlayers = 2,
        maxPlayers = 12,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.ROUND_COUNT, roundCount = BuncoScoring.ROUNDS)
    ),
    GameRules(
        id = "builtin_kot",
        name = context.getString(R.string.game_kot),
        minPlayers = 2,
        maxPlayers = 6,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 20, stopImmediately = true)
    ),
    GameRules(
        id = "builtin_mexicain",
        name = context.getString(R.string.game_mexicain),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER
    ),
    GameRules(
        id = "builtin_qwixx",
        name = context.getString(R.string.game_qwixx),
        minPlayers = 2,
        maxPlayers = 5,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.QWIXX
    )
)
