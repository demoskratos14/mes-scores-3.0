package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Partage de jeux personnalisés par fichier JSON : un ou plusieurs jeux créés par l'utilisateur,
 * que quelqu'un d'autre peut importer dans son appli. Logique pure (org.json seulement), testée
 * dans CustomGameShareTest.
 *
 * Format : `{ "format": "mes-scores-jeux", "version": 1, "exportedAt": …, "games": [ … ] }`,
 * chaque jeu gardant son propre format ([GameRules.toJson]).
 */
object CustomGameShare {

    const val FORMAT = "mes-scores-jeux"
    const val VERSION = 1

    /** Raison d'un import impossible ; le texte affiché est choisi par l'interface (strings.xml). */
    enum class ImportError { NOT_A_GAME_FILE, NEWER_VERSION }

    sealed interface ParseResult {
        /** [invalid] : nombre de jeux du fichier qui n'ont pas pu être relus (ignorés). */
        data class Ok(val games: List<GameRules>, val invalid: Int) : ParseResult
        data class Error(val reason: ImportError) : ParseResult
    }

    /** Bilan d'une fusion : [games] est la nouvelle liste complète des jeux personnalisés. */
    data class MergeOutcome(val games: List<GameRules>, val added: Int, val unchanged: Int)

    fun export(games: List<GameRules>, exportedAt: Long): String {
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
            return ParseResult.Error(ImportError.NOT_A_GAME_FILE)
        }
        if (obj.optString("format") != FORMAT || obj.optJSONArray("games") == null) {
            return ParseResult.Error(ImportError.NOT_A_GAME_FILE)
        }
        if (obj.optInt("version", 1) > VERSION) return ParseResult.Error(ImportError.NEWER_VERSION)
        val array = obj.getJSONArray("games")
        var invalid = 0
        val games = (0 until array.length()).mapNotNull { i ->
            try {
                GameRules.fromJson(array.getJSONObject(i))
            } catch (e: Exception) {
                invalid++
                null
            }
        }
        return ParseResult.Ok(games, invalid)
    }

    /**
     * Ajoute [incoming] à [existing] sans rien écraser. Un jeu déjà présent à l'identique (même id,
     * même contenu) est ignoré, ce qui rend l'import répétable ; un jeu de même id mais modifié est
     * ajouté comme copie avec un nouvel id ([newId]), tout comme un jeu dont l'id est celui d'un jeu
     * prédéfini.
     */
    fun merge(existing: List<GameRules>, incoming: List<GameRules>, newId: () -> String): MergeOutcome {
        val result = existing.toMutableList()
        var added = 0
        var unchanged = 0
        incoming.forEach { game ->
            val same = result.firstOrNull { it.id == game.id }
            when {
                same != null && same.toJson().toString() == game.toJson().toString() -> unchanged++
                same != null || game.id.startsWith("builtin_") -> {
                    result.add(game.copy(id = newId()))
                    added++
                }
                else -> {
                    result.add(game)
                    added++
                }
            }
        }
        return MergeOutcome(result, added, unchanged)
    }

    /** Nom de fichier pour le jeu [game] : lettres et chiffres, le reste devient « _ ». */
    fun fileName(game: GameRules): String {
        val base = game.name.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("").trim('_').take(40)
        return (base.ifEmpty { "jeu" }) + ".json"
    }
}
