package com.aventure.messcores

import android.content.Context

/**
 * Règles prédéfinies : Jeu classique (tableau de scores générique).
 * Pour ajouter un jeu de cette rubrique, ajouter un bloc GameRules ici,
 * puis son identifiant dans GameCategory.kt (sauf pour « Autres »).
 */
internal fun classicGames(context: Context): List<GameRules> = listOf(
    GameRules(
        id = "builtin_generic",
        name = context.getString(R.string.game_generic),
        lowestWins = false,
        allowNegativeScores = false,
        scoreMode = ScoreMode.TABLE
    )
)
