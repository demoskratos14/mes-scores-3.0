package com.aventure.messcores

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Log
import androidx.core.content.edit
import androidx.core.util.readText
import androidx.core.util.writeText
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

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

internal fun TeamRound.toJson(): JSONObject {
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
    contractInput?.let { list -> obj.put("contractInput", JSONArray(list)) }
    return obj
}

internal fun teamRoundFromJson(obj: JSONObject): TeamRound {
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
        },
        contractInput = obj.optJSONArray("contractInput")?.let { array ->
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
    val phases: List<Int>? = null,
    /** Tournoi de poker en cours (mode POKER), ou null pour les autres jeux. */
    val poker: PokerGame? = null,
    /** Match de tennis ou de padel (mode RACKET) : réglages et points gagnés, ou null pour les autres jeux. */
    val racket: RacketMatch? = null
) {
    /**
     * Le contenu de la partie sans ce qui change tout seul d'un instant à l'autre (heure
     * d'enregistrement, chrono du poker) : sert à repérer qu'il n'y a rien de nouveau à sauvegarder.
     */
    fun contentKey(): SavedGame = copy(savedAt = 0L, poker = poker?.copy(clockMs = 0L))

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
        ScoreMode.POKER -> players.indices.map { poker?.seats?.getOrNull(it)?.stack ?: 0 }
        // Tennis / padel : sets gagnés par le camp de chaque joueur.
        ScoreMode.RACKET -> racket?.let { RacketScoring.setsByPlayer(RacketScoring.replay(it), players.size) }
            ?: players.map { 0 }
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
        poker?.let { obj.put("poker", it.toJson()) }
        racket?.let { obj.put("racket", it.toJson()) }
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

            val poker = obj.optJSONObject("poker")?.let { PokerGame.fromJson(it) }
            val racket = obj.optJSONObject("racket")?.let { RacketMatch.fromJson(it) }

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
                phases = phases,
                poker = poker,
                racket = racket
            )
        }
    }
}

/**
 * Journal des parties : une partie (en cours ou terminée) = un fichier JSON dans
 * `filesDir/journal/`, nommé `<jeu>@<id>.json` (les deux identifiants sont encodés pour être
 * sûrs comme nom de fichier). Indépendant des jeux définis dans [GameRepository].
 *
 * - Enregistrer une partie n'écrit que son fichier (écriture atomique : [AtomicFile]) ; avant,
 *   chaque auto-sauvegarde relisait et réécrivait tout le journal.
 * - Un fichier illisible est mis de côté (extension `.bad`) sans gêner les autres parties.
 * - La limite [MAX_SAVED_GAMES] s'applique à chaque jeu séparément ; le nom du fichier commence
 *   par le jeu, donc la vérifier ne demande qu'une liste de noms, sans rien lire.
 * - L'ancien journal (un seul texte JSON dans les SharedPreferences) est converti en fichiers au
 *   premier accès, puis laissé en place comme filet de sécurité.
 */
class GameHistoryRepository(context: Context) {

    /** Ancien emplacement du journal : ne sert plus qu'à la migration et au marqueur de migration. */
    private val prefs = context.getSharedPreferences("mes_scores_history", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, DIR_NAME)

    companion object {
        /** Nombre maximal de parties conservées *par jeu* : au-delà, les plus anciennes de ce jeu sont supprimées. */
        const val MAX_SAVED_GAMES = 100

        /** Dossier du journal dans `filesDir` (à garder en phase avec les règles de sauvegarde Android). */
        const val DIR_NAME = "journal"

        private const val EXT = ".json"
        private const val BAD_EXT = ".bad"
        /** Sépare le jeu de la partie dans le nom de fichier ; « @ » est toujours encodé par [Uri.encode]. */
        private const val SEP = "@"
        private const val KEY_SAVED_GAMES = "saved_games"
        /** Copie de sécurité du texte brut quand au moins une entrée de l'ancien journal n'a pas pu être relue. */
        private const val KEY_BACKUP = "saved_games_unreadable_backup"
        private const val KEY_MIGRATED = "journal_migrated_to_files"
        private const val TAG = "GameHistory"
    }

    private fun gamePrefix(gameId: String) = Uri.encode(gameId) + SEP
    private fun idSuffix(id: String) = SEP + Uri.encode(id) + EXT
    private fun fileOf(game: SavedGame) = File(dir, gamePrefix(game.gameRules.id) + Uri.encode(game.id) + EXT)

    /** Fichiers de parties du journal, éventuellement ceux d'un seul jeu ([prefix]) ou d'une seule partie ([suffix]). */
    private fun journalFiles(prefix: String? = null, suffix: String? = null): List<File> =
        dir.listFiles { f ->
            f.isFile && f.name.endsWith(EXT) &&
                (prefix == null || f.name.startsWith(prefix)) &&
                (suffix == null || f.name.endsWith(suffix))
        }.orEmpty().toList()

    /** Relit un fichier ; un fichier au contenu abîmé est mis de côté, une simple erreur de lecture le laisse en place. */
    private fun readFile(file: File): SavedGame? = try {
        SavedGame.fromJson(JSONObject(AtomicFile(file).readText()))
    } catch (e: IOException) {
        Log.w(TAG, "Lecture impossible de ${file.name}, laissé en place", e)
        null
    } catch (e: Exception) {
        Log.w(TAG, "Partie ${file.name} illisible, mise de côté", e)
        file.renameTo(File(file.path + BAD_EXT))
        null
    }

    /** Parties triées de la plus récente à la plus ancienne. */
    fun listGames(): List<SavedGame> {
        migrateIfNeeded()
        return journalFiles().mapNotNull { readFile(it) }.sortedByDescending { it.savedAt }
    }

    /**
     * Écrit le fichier de [game] (remplace le précédent de même id) sans appliquer la limite.
     * Une erreur d'écriture (disque plein…) est journalisée mais ne fait pas planter l'appli.
     */
    private fun writeGame(game: SavedGame) {
        try {
            dir.mkdirs()
            val target = fileOf(game)
            AtomicFile(target).writeText(game.toJson().toString())
            // Une seule entrée par id, même si le jeu enregistré avait changé de nom de fichier.
            journalFiles(suffix = idSuffix(game.id)).filter { it != target }.forEach { it.delete() }
        } catch (e: IOException) {
            Log.w(TAG, "Écriture impossible de la partie ${game.id}", e)
        }
    }

    /**
     * Enregistre [game] : remplace l'entrée existante de même id si elle existe, sinon l'ajoute.
     * Si ce jeu dépasse [MAX_SAVED_GAMES] parties, les plus anciennes sont supprimées, les parties
     * terminées d'abord, puis les parties en cours. [game] n'est jamais supprimée.
     */
    fun saveGame(game: SavedGame) {
        migrateIfNeeded()
        writeGame(game)
        trimGame(game.gameRules.id, keepId = game.id)
    }

    /** Ne lit rien tant que le jeu [gameId] reste sous la limite : seuls les noms de fichiers sont comptés. */
    private fun trimGame(gameId: String, keepId: String?) {
        val files = journalFiles(prefix = gamePrefix(gameId))
        if (files.size <= MAX_SAVED_GAMES) return
        val games = files.mapNotNull { readFile(it) }
        val kept = JournalBackup.trimToLimit(games, MAX_SAVED_GAMES, keepId).map { it.id }.toSet()
        games.filter { it.id !in kept }.forEach { deleteGame(it.id) }
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
     * n'est pas une sauvegarde valide ; [onError] reçoit alors la raison. Seules les parties ajoutées
     * ou mises à jour sont écrites ; la limite par jeu est appliquée ensuite.
     */
    fun importJson(text: String, onError: (JournalBackup.ImportError) -> Unit): ImportSummary? {
        when (val parsed = JournalBackup.parse(text)) {
            is JournalBackup.ParseResult.Error -> {
                onError(parsed.reason)
                return null
            }
            is JournalBackup.ParseResult.Ok -> {
                val existing = listGames()
                val merged = JournalBackup.merge(existing, parsed.games)
                val kept = JournalBackup.trimToLimitPerGame(merged.games, MAX_SAVED_GAMES)
                val keptIds = kept.map { it.id }.toSet()
                val savedAtById = existing.associate { it.id to it.savedAt }
                kept.filter { savedAtById[it.id] != it.savedAt }.forEach { writeGame(it) }
                merged.games.filter { it.id !in keptIds }.forEach { deleteGame(it.id) }
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
        listGames().filter { it.isFinished }.forEach { deleteGame(it.id) }
    }

    fun deleteGame(id: String) {
        migrateIfNeeded()
        journalFiles(suffix = idSuffix(id)).forEach { it.delete() }
    }

    /**
     * Convertit l'ancien journal (un texte JSON dans les SharedPreferences) en un fichier par partie.
     * Rejouable sans risque : une partie déjà présente en fichier n'est pas touchée, et le marqueur
     * n'est posé qu'une fois tout écrit. L'ancien texte reste dans les préférences (filet de sécurité).
     */
    private fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        try {
            dir.mkdirs()
            legacyGames().forEach { game ->
                if (journalFiles(suffix = idSuffix(game.id)).isEmpty()) {
                    AtomicFile(fileOf(game)).writeText(game.toJson().toString())
                }
            }
            prefs.edit(commit = true) { putBoolean(KEY_MIGRATED, true) }
        } catch (e: IOException) {
            Log.w(TAG, "Migration du journal reportée au prochain accès", e)
        }
    }

    /** Relit l'ancien journal des SharedPreferences (même tolérance aux entrées abîmées qu'avant). */
    private fun legacyGames(): List<SavedGame> {
        val json = prefs.getString(KEY_SAVED_GAMES, null) ?: return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            // Tout le texte est illisible : on le met de côté, il ne sera jamais écrasé.
            Log.w(TAG, "Ancien journal illisible, copie de sécurité conservée", e)
            backUp(json)
            return emptyList()
        }
        var skipped = 0
        val games = (0 until array.length()).mapNotNull { i ->
            try {
                SavedGame.fromJson(array.getJSONObject(i))
            } catch (e: Exception) {
                skipped++
                Log.w(TAG, "Entrée $i de l'ancien journal illisible, ignorée", e)
                null
            }
        }
        if (skipped > 0) backUp(json)
        return games
    }

    /** Conserve le texte brut de l'ancien journal (une seule copie, la plus ancienne) pour une récupération manuelle. */
    private fun backUp(rawJson: String) {
        if (!prefs.contains(KEY_BACKUP)) prefs.edit { putString(KEY_BACKUP, rawJson) }
    }
}
