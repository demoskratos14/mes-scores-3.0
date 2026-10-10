package com.aventure.messcores

import android.content.Context
import androidx.core.content.edit
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** État figé d'une case du tableau (mode TABLE), pour la sauvegarde d'une partie en cours. */
data class CellSnapshot(
    val baseValue: Int?,
    val isNegative: Boolean,
    val multiplierId: String
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        baseValue?.let { obj.put("baseValue", it) }
        obj.put("isNegative", isNegative)
        obj.put("multiplierId", multiplierId)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): CellSnapshot = CellSnapshot(
            baseValue = if (obj.has("baseValue") && !obj.isNull("baseValue")) obj.getInt("baseValue") else null,
            isNegative = obj.optBoolean("isNegative", false),
            multiplierId = obj.optString("multiplierId", GameRules.NORMAL_MULTIPLIER_ID)
        )
    }
}

private fun TeamRound.toJson(): JSONObject {
    val obj = JSONObject()
    obj.put("teamALabel", teamALabel)
    val playersArray = JSONArray()
    teamAPlayers.forEach { playersArray.put(it) }
    obj.put("teamAPlayers", playersArray)
    obj.put("value", value)
    tarotInput?.let { input ->
        val inputObj = JSONObject()
        inputObj.put("taker", input.taker)
        input.partner?.let { inputObj.put("partner", it) }
        inputObj.put("multiplierIndex", input.multiplierIndex)
        inputObj.put("bouts", input.bouts)
        inputObj.put("points", input.points)
        inputObj.put("petitAuBout", input.petitAuBout)
        inputObj.put("handful", input.handful)
        inputObj.put("slamAnnounced", input.slamAnnounced)
        inputObj.put("defenseSlam", input.defenseSlam)
        obj.put("tarotInput", inputObj)
    }
    deltas?.let { list ->
        val deltasArray = JSONArray()
        list.forEach { deltasArray.put(it) }
        obj.put("deltas", deltasArray)
    }
    return obj
}

private fun teamRoundFromJson(obj: JSONObject): TeamRound {
    val playersArray = obj.optJSONArray("teamAPlayers")
    val playersSet = if (playersArray == null) {
        emptySet()
    } else {
        (0 until playersArray.length()).map { playersArray.getInt(it) }.toSet()
    }
    return TeamRound(
        teamALabel = obj.getString("teamALabel"),
        teamAPlayers = playersSet,
        value = obj.getInt("value"),
        tarotInput = obj.optJSONObject("tarotInput")?.let { input ->
            TarotRoundInput(
                taker = input.getInt("taker"),
                partner = if (input.has("partner")) input.getInt("partner") else null,
                multiplierIndex = input.getInt("multiplierIndex"),
                bouts = input.getInt("bouts"),
                points = input.getInt("points"),
                petitAuBout = input.getInt("petitAuBout"),
                handful = input.getInt("handful"),
                slamAnnounced = input.getBoolean("slamAnnounced"),
                defenseSlam = input.getBoolean("defenseSlam")
            )
        },
        deltas = obj.optJSONArray("deltas")?.let { array ->
            (0 until array.length()).map { array.getInt(it) }
        }
    )
}

/**
 * Une partie enregistrée dans le journal, en cours ou terminée. [id] identifie la partie
 * de façon stable : ré-enregistrer la même partie en cours met à jour l'entrée existante
 * au lieu d'en créer une nouvelle. Seul le champ correspondant au [ScoreMode] de
 * [gameRules] est rempli ([cellSnapshots] pour TABLE, [counters] pour COUNTER,
 * [teamRounds] pour VARIABLE_TEAMS).
 */
data class SavedGame(
    val id: String,
    val savedAt: Long,
    val gameRules: GameRules,
    val players: List<String>,
    val playerColorsArgb: List<Int>,
    val cellSnapshots: List<List<CellSnapshot>>? = null,
    val counters: List<Int>? = null,
    val teamRounds: List<TeamRound>? = null,
    val isFinished: Boolean,
    /** Phase atteinte par chaque joueur (Phase 10), ou null pour les autres jeux. */
    val phases: List<Int>? = null
) {
    /** Total de chaque joueur au moment de l'enregistrement (même index que [players]). */
    fun totals(): List<Int> = when (gameRules.scoreMode) {
        ScoreMode.TABLE -> players.indices.map { p ->
            // Score de chaque manche (ou catégorie) pour ce joueur ; null = case vide.
            val values = cellSnapshots.orEmpty().map { round ->
                val cell = round.getOrNull(p)
                val base = cell?.baseValue
                if (cell == null || base == null) {
                    null
                } else {
                    val rule = gameRules.multipliers.find { it.id == cell.multiplierId }
                        ?: GameRules.NORMAL_MULTIPLIER
                    val magnitude = base * rule.factor + rule.bonus
                    if (cell.isNegative) -magnitude else magnitude
                }
            }
            gameRules.tableTotal(values)
        }
        ScoreMode.COUNTER -> players.indices.map { counters?.getOrNull(it) ?: 0 }
        ScoreMode.VARIABLE_TEAMS -> players.indices.map { p ->
            teamRounds.orEmpty().fold(0) { acc, round ->
                acc + (round.deltas?.getOrNull(p)
                    ?: if (p in round.teamAPlayers) round.value else -round.value)
            }
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        // Numéro de version du format : permet de migrer les anciennes entrées si le format change.
        obj.put("version", FORMAT_VERSION)
        obj.put("id", id)
        obj.put("savedAt", savedAt)
        obj.put("gameRules", gameRules.toJson())
        obj.put("players", JSONArray(players))
        obj.put("playerColorsArgb", JSONArray(playerColorsArgb))
        obj.put("isFinished", isFinished)

        cellSnapshots?.let { rounds ->
            val roundsArray = JSONArray()
            rounds.forEach { round ->
                val roundArray = JSONArray()
                round.forEach { roundArray.put(it.toJson()) }
                roundsArray.put(roundArray)
            }
            obj.put("cellSnapshots", roundsArray)
        }
        counters?.let { obj.put("counters", JSONArray(it)) }
        phases?.let { obj.put("phases", JSONArray(it)) }
        teamRounds?.let { rounds ->
            val array = JSONArray()
            rounds.forEach { array.put(it.toJson()) }
            obj.put("teamRounds", array)
        }
        return obj
    }

    companion object {
        /** Version actuelle du format JSON d'une partie. Les entrées sans numéro sont en version 1. */
        const val FORMAT_VERSION = 1

        fun fromJson(obj: JSONObject): SavedGame {
            // Point d'entrée des migrations futures : if (obj.optInt("version", 1) < 2) { … }
            val playersArray = obj.getJSONArray("players")
            val players = (0 until playersArray.length()).map { playersArray.getString(it) }

            val colorsArray = obj.optJSONArray("playerColorsArgb")
            val colors = if (colorsArray == null) {
                emptyList()
            } else {
                (0 until colorsArray.length()).map { colorsArray.getInt(it) }
            }

            val cellSnapshots = obj.optJSONArray("cellSnapshots")?.let { roundsArray ->
                (0 until roundsArray.length()).map { i ->
                    val roundArray = roundsArray.getJSONArray(i)
                    (0 until roundArray.length()).map { j -> CellSnapshot.fromJson(roundArray.getJSONObject(j)) }
                }
            }

            val counters = obj.optJSONArray("counters")?.let { array ->
                (0 until array.length()).map { array.getInt(it) }
            }

            val phases = obj.optJSONArray("phases")?.let { array ->
                (0 until array.length()).map { array.getInt(it) }
            }

            val teamRounds = obj.optJSONArray("teamRounds")?.let { array ->
                (0 until array.length()).map { i -> teamRoundFromJson(array.getJSONObject(i)) }
            }

            return SavedGame(
                id = obj.getString("id"),
                savedAt = obj.getLong("savedAt"),
                gameRules = GameRules.fromJson(obj.getJSONObject("gameRules")),
                players = players,
                playerColorsArgb = colors,
                cellSnapshots = cellSnapshots,
                counters = counters,
                teamRounds = teamRounds,
                isFinished = obj.optBoolean("isFinished", false),
                phases = phases
            )
        }
    }
}

/**
 * Journal des parties : sauvegarde et relit la liste des parties (en cours ou terminées)
 * dans les SharedPreferences, indépendamment des jeux définis dans [GameRepository].
 */
class GameHistoryRepository(context: Context) {

    private val prefs = context.getSharedPreferences("mes_scores_history", Context.MODE_PRIVATE)

    companion object {
        /** Nombre maximal de parties conservées : au-delà, les plus anciennes sont supprimées. */
        const val MAX_SAVED_GAMES = 50
        private const val KEY_SAVED_GAMES = "saved_games"
        /** Copie de sécurité du texte brut quand au moins une entrée n'a pas pu être relue. */
        private const val KEY_BACKUP = "saved_games_unreadable_backup"
        private const val TAG = "GameHistory"
    }

    /** Parties triées de la plus récente à la plus ancienne. */
    fun listGames(): List<SavedGame> {
        val json = prefs.getString(KEY_SAVED_GAMES, null) ?: return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            // Tout le texte est illisible : on le met de côté avant qu'un enregistrement ne l'écrase.
            Log.w(TAG, "Journal illisible, copie de sécurité conservée", e)
            backUp(json)
            return emptyList()
        }
        var skipped = 0
        // Une entrée abîmée est ignorée seule : les autres parties restent lisibles.
        val games = (0 until array.length()).mapNotNull { i ->
            try {
                SavedGame.fromJson(array.getJSONObject(i))
            } catch (e: Exception) {
                skipped++
                Log.w(TAG, "Entrée $i du journal illisible, ignorée", e)
                null
            }
        }
        if (skipped > 0) backUp(json)
        return games.sortedByDescending { it.savedAt }
    }

    /** Conserve le texte brut du journal (une seule copie, la plus ancienne) pour une récupération manuelle. */
    private fun backUp(rawJson: String) {
        if (!prefs.contains(KEY_BACKUP)) prefs.edit { putString(KEY_BACKUP, rawJson) }
    }

    /**
     * Enregistre [game] : remplace l'entrée existante de même id si elle existe, sinon l'ajoute.
     * Si le journal dépasse [MAX_SAVED_GAMES], les parties les plus anciennes sont supprimées,
     * les parties terminées d'abord, puis les parties en cours. [game] n'est jamais supprimée.
     */
    fun saveGame(game: SavedGame) {
        val current = listGames().filterNot { it.id == game.id } + game
        persist(JournalBackup.trimToLimit(current, MAX_SAVED_GAMES, keepId = game.id))
    }

    /** Contenu du fichier de sauvegarde du journal (voir [JournalBackup]). */
    fun exportJson(): String = JournalBackup.export(listGames(), System.currentTimeMillis())

    /** Bilan d'un import : parties ajoutées, mises à jour, déjà à jour, illisibles, et supprimées faute de place. */
    data class ImportSummary(
        val added: Int,
        val updated: Int,
        val unchanged: Int,
        val invalid: Int,
        val droppedForSpace: Int
    )

    /**
     * Importe le contenu d'un fichier de sauvegarde en le fusionnant au journal actuel (rien n'est
     * effacé ; voir [JournalBackup.merge]). Renvoie null et laisse le journal intact si le fichier
     * n'est pas une sauvegarde valide ; [onError] reçoit alors la raison.
     */
    fun importJson(text: String, onError: (JournalBackup.ImportError) -> Unit): ImportSummary? {
        when (val parsed = JournalBackup.parse(text)) {
            is JournalBackup.ParseResult.Error -> {
                onError(parsed.reason)
                return null
            }
            is JournalBackup.ParseResult.Ok -> {
                val merged = JournalBackup.merge(listGames(), parsed.games)
                val kept = JournalBackup.trimToLimit(merged.games, MAX_SAVED_GAMES)
                persist(kept)
                return ImportSummary(
                    added = merged.added,
                    updated = merged.updated,
                    unchanged = merged.unchanged,
                    invalid = parsed.invalid,
                    droppedForSpace = merged.games.size - kept.size
                )
            }
        }
    }

    /** Supprime toutes les parties terminées (les parties en cours sont conservées). */
    fun deleteFinishedGames() {
        persist(listGames().filterNot { it.isFinished })
    }

    fun deleteGame(id: String) {
        persist(listGames().filterNot { it.id == id })
    }

    private fun persist(games: List<SavedGame>) {
        val array = JSONArray()
        games.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_SAVED_GAMES, array.toString()) }
    }
}
