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

    fun builtInGames(): List<GameRules> = allBuiltInGames(context)

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

    /** Bilan d'un import de jeux partagés. */
    data class SharedImport(val added: Int, val unchanged: Int, val invalid: Int)

    /** Texte du fichier à partager pour le jeu personnalisé [game] (voir [CustomGameShare]). */
    fun sharedGameJson(game: GameRules): String = CustomGameShare.export(listOf(game), System.currentTimeMillis())

    /**
     * Ajoute à mes jeux personnalisés ceux du fichier [text] (voir [CustomGameShare.merge]) ; rien n'est
     * écrasé. Renvoie null, et appelle [onError], si le fichier n'est pas un jeu partagé.
     */
    fun importSharedGames(text: String, onError: (CustomGameShare.ImportError) -> Unit): SharedImport? =
        when (val result = CustomGameShare.parse(text)) {
            is CustomGameShare.ParseResult.Error -> { onError(result.reason); null }
            is CustomGameShare.ParseResult.Ok -> {
                val outcome = CustomGameShare.merge(customGames(), result.games, ::newId)
                if (outcome.added > 0) persist(outcome.games)
                SharedImport(outcome.added, outcome.unchanged, result.invalid)
            }
        }

    private fun persist(games: List<GameRules>) {
        val array = JSONArray()
        games.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_CUSTOM_GAMES, array.toString()) }
    }
}
