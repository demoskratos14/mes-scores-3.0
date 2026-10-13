package com.aventure.messcores

import android.content.Context

/**
 * Règles prédéfinies : Jeux de cartes classiques (rubrique « Cartes »).
 * Pour ajouter un jeu de cette rubrique, ajouter un bloc GameRules ici,
 * puis son identifiant dans GameCategory.kt (sauf pour « Autres »).
 */
internal fun cardGames(context: Context): List<GameRules> = listOf(
    GameRules(
        id = "builtin_tarot",
        name = context.getString(R.string.game_tarot),
        minPlayers = 3,
        maxPlayers = 5,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.VARIABLE_TEAMS,
        multipliers = listOf(
            GameRules.NORMAL_MULTIPLIER.copy(label = context.getString(R.string.tarot_bid_petite)),
            ScoreMultiplier(id = "tarot_garde", label = context.getString(R.string.tarot_bid_garde), factor = 2),
            ScoreMultiplier(id = "tarot_garde_sans", label = context.getString(R.string.tarot_bid_garde_sans), factor = 4),
            ScoreMultiplier(id = "tarot_garde_contre", label = context.getString(R.string.tarot_bid_garde_contre), factor = 6)
        )
    ),
    GameRules(
        id = "builtin_belote",
        name = context.getString(R.string.game_belote),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 501, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_poker",
        name = context.getString(R.string.game_poker),
        minPlayers = 2,
        maxPlayers = 10,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.POKER
    ),
    GameRules(
        id = "builtin_rami",
        name = context.getString(R.string.game_rami),
        minPlayers = 2,
        maxPlayers = 6,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 500, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_coinche",
        name = context.getString(R.string.game_coinche),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        multipliers = listOf(
            GameRules.NORMAL_MULTIPLIER,
            ScoreMultiplier(id = "coinche_coinched", label = context.getString(R.string.coinche_coinched), factor = 2),
            ScoreMultiplier(id = "coinche_redoubled", label = context.getString(R.string.coinche_redoubled), factor = 4)
        ),
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 1000, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_president",
        name = context.getString(R.string.game_president),
        minPlayers = 3,
        maxPlayers = 8,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_coeurs",
        name = context.getString(R.string.game_coeurs),
        minPlayers = 3,
        maxPlayers = 6,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 100, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_pique",
        name = context.getString(R.string.game_pique),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 500, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_gin",
        name = context.getString(R.string.game_gin),
        minPlayers = 2,
        maxPlayers = 2,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 100, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_huit",
        name = context.getString(R.string.game_huit),
        minPlayers = 2,
        maxPlayers = 7,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 100, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_cribbage",
        name = context.getString(R.string.game_cribbage),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 121, stopImmediately = true)
    ),
    GameRules(
        id = "builtin_manille",
        name = context.getString(R.string.game_manille),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        multipliers = listOf(
            GameRules.NORMAL_MULTIPLIER,
            ScoreMultiplier(id = "manille_contre", label = context.getString(R.string.manille_contre), factor = 2),
            ScoreMultiplier(id = "manille_surcontre", label = context.getString(R.string.manille_surcontre), factor = 4)
        ),
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 101, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_scopa",
        name = context.getString(R.string.game_scopa),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 11, stopImmediately = false, tieBreakOnEqualLeaders = true)
    ),
    GameRules(
        id = "builtin_yaniv",
        name = context.getString(R.string.game_yaniv),
        minPlayers = 2,
        maxPlayers = 8,
        lowestWins = true,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 201, stopImmediately = false)
    ),
    GameRules(
        id = "builtin_barbu",
        name = context.getString(R.string.game_barbu),
        minPlayers = 3,
        maxPlayers = 6,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE
    ),
    GameRules(
        id = "builtin_ohhell",
        name = context.getString(R.string.game_ohhell),
        minPlayers = 3,
        maxPlayers = 7,
        lowestWins = false,
        allowNegativeScores = true,
        scoreMode = ScoreMode.TABLE,
        bidScoring = BidScoring.OH_HELL
    ),
    GameRules(
        id = "builtin_sueca",
        name = context.getString(R.string.game_sueca),
        minPlayers = 2,
        maxPlayers = 4,
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE,
        endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 4, stopImmediately = true)
    )
)
