package com.aventure.messcores

import android.content.Context
import androidx.core.content.edit
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import java.util.UUID

/**
 * Gère les jeux disponibles : quelques jeux prédéfinis, plus les jeux
 * personnalisés créés par l'utilisateur, sauvegardés dans les SharedPreferences
 * (donc conservés d'une ouverture de l'app à l'autre, sans dépendance externe).
 */
class GameRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("mes_scores_games", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_CUSTOM_GAMES = "custom_games"
        /** Copie de sécurité du texte brut quand au moins un jeu n'a pas pu être relu. */
        const val KEY_BACKUP = "custom_games_unreadable_backup"
        const val TAG = "GameRepository"
    }

    fun builtInGames(): List<GameRules> = listOf(
        GameRules(
            id = "builtin_generic",
            name = context.getString(R.string.game_generic),
            lowestWins = false,
            allowNegativeScores = false,
            scoreMode = ScoreMode.TABLE
        ),
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
            id = "builtin_uno",
            name = context.getString(R.string.game_uno),
            minPlayers = 2,
            maxPlayers = 10,
            lowestWins = false,
            allowNegativeScores = false,
            scoreMode = ScoreMode.TABLE
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
            scoreMode = ScoreMode.TABLE
        ),
        GameRules(
            id = "builtin_bunco",
            name = context.getString(R.string.game_bunco),
            minPlayers = 2,
            maxPlayers = 12,
            lowestWins = false,
            allowNegativeScores = false,
            scoreMode = ScoreMode.TABLE
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

    fun customGames(): List<GameRules> {
        val json = prefs.getString(KEY_CUSTOM_GAMES, null) ?: return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            Log.w(TAG, "Liste des jeux illisible, copie de sécurité conservée", e)
            backUp(json)
            return emptyList()
        }
        var skipped = 0
        // Un jeu abîmé est ignoré seul : les autres jeux personnalisés restent disponibles.
        val games = (0 until array.length()).mapNotNull { i ->
            try {
                GameRules.fromJson(array.getJSONObject(i))
            } catch (e: Exception) {
                skipped++
                Log.w(TAG, "Jeu personnalisé $i illisible, ignoré", e)
                null
            }
        }
        if (skipped > 0) backUp(json)
        return games
    }

    /** Conserve le texte brut (une seule copie, la plus ancienne) pour une récupération manuelle. */
    private fun backUp(rawJson: String) {
        if (!prefs.contains(KEY_BACKUP)) prefs.edit { putString(KEY_BACKUP, rawJson) }
    }

    fun allGames(): List<GameRules> = builtInGames() + customGames()

    fun saveCustomGame(game: GameRules) {
        val current = customGames().toMutableList()
        current.add(game)
        persist(current)
    }

    /** Remplace le jeu personnalisé de même id par [game] (modification). Sans effet si l'id est inconnu. */
    fun updateCustomGame(game: GameRules) {
        val current = customGames()
        if (current.none { it.id == game.id }) return
        persist(current.map { if (it.id == game.id) game else it })
    }

    /**
     * Supprime un jeu personnalisé. Les parties déjà enregistrées au journal ne sont pas touchées :
     * chacune embarque sa propre copie des règles.
     */
    fun deleteCustomGame(id: String) {
        persist(customGames().filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun persist(games: List<GameRules>) {
        val array = JSONArray()
        games.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_CUSTOM_GAMES, array.toString()) }
    }
}
