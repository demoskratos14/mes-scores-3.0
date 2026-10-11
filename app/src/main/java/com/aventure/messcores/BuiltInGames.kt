package com.aventure.messcores

import android.content.Context

/**
 * Tous les jeux prédéfinis, rubrique par rubrique (une rubrique = un fichier).
 * La liste de choix regroupe les jeux par rubrique ; l'ordre ici n'a donc
 * d'importance qu'à l'intérieur d'une même rubrique.
 */
internal fun allBuiltInGames(context: Context): List<GameRules> =
    classicGames(context) +
        cardGames(context) +
        diceGames(context) +
        specialGames(context) +
        otherGames(context)
