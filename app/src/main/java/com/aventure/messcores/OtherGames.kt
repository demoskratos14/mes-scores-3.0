package com.aventure.messcores

import android.content.Context

/**
 * Règles prédéfinies : Sports et loisirs : pétanque, bowling, fléchettes… (rubrique « Autres »).
 * Pour ajouter un jeu de cette rubrique, ajouter un bloc GameRules ici,
 * puis son identifiant dans GameCategory.kt (sauf pour « Autres »).
 */
internal fun otherGames(context: Context): List<GameRules> = listOf(
    GameRules(
        id = "builtin_petanque",
        name = context.getString(R.string.game_petanque),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 13, stopImmediately = true)
    ),
    GameRules(
        id = "builtin_molkky",
        name = context.getString(R.string.game_molkky),
        minPlayers = 2,
        maxPlayers = 10,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        exactTarget = 50,
        exactReset = 25,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 50, stopImmediately = true)
    ),
    GameRules(
        id = "builtin_darts301",
        name = context.getString(R.string.game_darts301),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        countdownFrom = 301
    ),
    GameRules(
        id = "builtin_darts501",
        name = context.getString(R.string.game_darts501),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        countdownFrom = 501
    ),
    GameRules(
        id = "builtin_bowling",
        name = context.getString(R.string.game_bowling),
        minPlayers = 1,
        maxPlayers = 6,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.BOWLING
    ),
    GameRules(
        id = "builtin_golf",
        name = context.getString(R.string.game_golf),
        minPlayers = 1,
        maxPlayers = 6,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_pingpong",
        name = context.getString(R.string.game_pingpong),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 11, stopImmediately = true, minLead = 2)
    ),
    GameRules(
        id = "builtin_badminton",
        name = context.getString(R.string.game_badminton),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 21, stopImmediately = true, minLead = 2, maxScore = 30)
    ),
    GameRules(
        id = "builtin_volley",
        name = context.getString(R.string.game_volley),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 25, stopImmediately = true, minLead = 2)
    ),
    GameRules(
        id = "builtin_babyfoot",
        name = context.getString(R.string.game_babyfoot),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.COUNTER,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 10, stopImmediately = true)
    )
)
