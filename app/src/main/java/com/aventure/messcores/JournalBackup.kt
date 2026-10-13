package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Export et import du journal des parties dans un fichier JSON, pour survivre à une
 * désinstallation ou à un changement de téléphone. Logique pure (aucune dépendance à Android
 * hormis org.json), testée dans JournalBackupTest.
 *
 * Format : `{ "format": "mes-scores-journal", "version": 1, "exportedAt": …, "games": [ … ] }`,
 * chaque partie gardant son propre format ([SavedGame.toJson], qui a son propre numéro de version).
 * [VERSION] ne change que si l'enveloppe elle-même change.
 */
object JournalBackup {

    const val FORMAT = "mes-scores-journal"
    const val VERSION = 1

    /** Raison d'un import impossible ; le texte affiché est choisi par l'interface (strings.xml). */
    enum class ImportError { NOT_A_BACKUP, NEWER_VERSION }

    /** Résultat de la lecture d'un fichier de sauvegarde. */
    sealed interface ParseResult {
        /** [invalid] : nombre de parties du fichier qui n'ont pas pu être relues (ignorées). */
        data class Ok(val games: List<SavedGame>, val invalid: Int) : ParseResult
        data class Error(val reason: ImportError) : ParseResult
    }

    /** Bilan d'une fusion : [games] est le nouveau journal complet. */
    data class MergeOutcome(
        val games: List<SavedGame>,
        val added: Int,
        /** Parties déjà présentes remplacées par une version plus récente du fichier. */
        val updated: Int,
        /** Parties du fichier ignorées car le journal avait déjà la même version ou une plus récente. */
        val unchanged: Int
    )

    fun export(games: List<SavedGame>, exportedAt: Long): String {
        val obj = JSONObject()
        obj.put("format", FORMAT)
        obj.put("version", VERSION)
        obj.put("exportedAt", exportedAt)
        val array = JSONArray()
        games.forEach { array.put(it.toJson()) }
        obj.put("games", array)
        return obj.toString(2)
    }

    fun parse(text: String): ParseResult {
        val obj = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return ParseResult.Error(ImportError.NOT_A_BACKUP)
        }
        if (obj.optString("format") != FORMAT || obj.optJSONArray("games") == null) {
            return ParseResult.Error(ImportError.NOT_A_BACKUP)
        }
        if (obj.optInt("version", 1) > VERSION) {
            return ParseResult.Error(ImportError.NEWER_VERSION)
        }
        val array = obj.getJSONArray("games")
        var invalid = 0
        val games = (0 until array.length()).mapNotNull { i ->
            try {
                SavedGame.fromJson(array.getJSONObject(i))
            } catch (e: Exception) {
                invalid++
                null
            }
        }
        return ParseResult.Ok(games, invalid)
    }

    /**
     * Fusionne [incoming] dans [existing] sans rien effacer : une partie de même id n'est remplacée
     * que si la version importée est strictement plus récente ([SavedGame.savedAt]).
     * Le résultat n'est pas limité à [GameHistoryRepository.MAX_SAVED_GAMES] : voir [trimToLimit].
     */
    fun merge(existing: List<SavedGame>, incoming: List<SavedGame>): MergeOutcome {
        val byId = LinkedHashMap<String, SavedGame>()
        existing.forEach { byId[it.id] = it }
        var added = 0
        var updated = 0
        var unchanged = 0
        // Si le fichier contient deux fois le même id, on ne garde que la plus récente.
        incoming.sortedBy { it.savedAt }.forEach { game ->
            val current = byId[game.id]
            when {
                current == null -> { byId[game.id] = game; added++ }
                game.savedAt > current.savedAt -> { byId[game.id] = game; updated++ }
                else -> unchanged++
            }
        }
        return MergeOutcome(byId.values.toList(), added, updated, unchanged)
    }

    /**
     * Ramène [games] à [limit] parties au plus : les plus anciennes sont supprimées, les parties
     * terminées d'abord, puis les parties en cours. La partie d'id [keepId] n'est jamais supprimée.
     */
    fun trimToLimit(games: List<SavedGame>, limit: Int, keepId: String? = null): List<SavedGame> {
        val excess = games.size - limit
        if (excess <= 0) return games
        val dropped = games
            .filter { it.id != keepId }
            .sortedWith(compareByDescending<SavedGame> { it.isFinished }.thenBy { it.savedAt })
            .take(excess)
            .map { it.id }
            .toSet()
        return games.filterNot { it.id in dropped }
    }
}
