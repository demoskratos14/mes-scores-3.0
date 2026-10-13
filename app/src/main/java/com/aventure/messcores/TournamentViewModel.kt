package com.aventure.messcores

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject

/**
 * Porte l'état du championnat pour l'interface et le sauvegarde. Les règles (tirage, byes, finale,
 * correction d'un résultat) vivent dans [TournamentEngine], testé sans Android.
 */
class TournamentViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("mes_scores_tournament", Context.MODE_PRIVATE)

    /** État courant, remplacé en bloc à chaque action : Compose recompose les écrans qui le lisent. */
    private var state by mutableStateOf(restore())

    val participants: List<String> get() = state.participants
    val finished: Boolean get() = state.finished
    val rounds: List<List<TournamentMatch>> get() = state.rounds

    /** Vrai si un championnat (en cours ou terminé) est disponible, y compris restauré après un redémarrage. */
    val hasTournament: Boolean get() = state.hasTournament

    /** Format du championnat en cours (élimination directe ou poules). */
    val format: TournamentFormat get() = state.format

    /**
     * Démarre un nouveau championnat. En élimination directe, l'ordre du tableau est tiré au sort, ou suit
     * l'ordre de [names] si [ordered] ; [ordered] est sans effet en poules.
     */
    fun startTournament(
        names: List<String>,
        format: TournamentFormat = TournamentFormat.KNOCKOUT,
        ordered: Boolean = false
    ) = update(
        when (format) {
            TournamentFormat.KNOCKOUT -> TournamentEngine.start(names, ordered = ordered)
            TournamentFormat.ROUND_ROBIN -> TournamentEngine.startRoundRobin(names)
        }
    )

    /** Classement des poules (vide en élimination directe). */
    fun standings(): List<PouleRow> = state.standings()

    /** Désigne [winner] (index dans [participants]) comme vainqueur du match indiqué. */
    fun setWinner(roundIndex: Int, matchIndex: Int, winner: Int) =
        update(TournamentEngine.setWinner(state, roundIndex, matchIndex, winner))

    /** Annule le résultat d'un match (voir [TournamentEngine.resetMatch]). */
    fun resetMatch(roundIndex: Int, matchIndex: Int) =
        update(TournamentEngine.resetMatch(state, roundIndex, matchIndex))

    fun hasPlayedAfter(roundIndex: Int): Boolean = state.hasPlayedAfter(roundIndex)
    fun championIndex(): Int? = state.championIndex()
    fun thirdPlaceIndex(): Int? = state.thirdPlaceIndex()
    fun podium(): List<Int> = state.podium()

    private fun update(newState: TournamentState) {
        state = newState
        save(newState)
    }

    // --- Sauvegarde : le championnat survit à la fermeture de l'appli. ---

    private fun save(s: TournamentState) {
        if (!s.hasTournament) {
            prefs.edit { remove(KEY_TOURNAMENT) }
            return
        }
        val obj = JSONObject()
        obj.put("version", FORMAT_VERSION)
        obj.put("participants", JSONArray(s.participants))
        obj.put("finished", s.finished)
        obj.put("format", s.format.name)
        val roundsArray = JSONArray()
        s.rounds.forEach { round ->
            val roundArray = JSONArray()
            round.forEach { match ->
                val m = JSONObject()
                m.put("round", match.round)
                match.label?.let { m.put("label", it) }
                match.playerAIndex?.let { m.put("a", it) }
                match.playerBIndex?.let { m.put("b", it) }
                match.winnerIndex?.let { m.put("winner", it) }
                roundArray.put(m)
            }
            roundsArray.put(roundArray)
        }
        obj.put("rounds", roundsArray)
        prefs.edit { putString(KEY_TOURNAMENT, obj.toString()) }
    }

    private fun restore(): TournamentState {
        val json = prefs.getString(KEY_TOURNAMENT, null) ?: return TournamentState()
        return try {
            val obj = JSONObject(json)
            val names = obj.getJSONArray("participants").let { a -> (0 until a.length()).map { a.getString(it) } }
            val roundsArray = obj.getJSONArray("rounds")
            val rounds = (0 until roundsArray.length()).map { r ->
                val roundArray = roundsArray.getJSONArray(r)
                (0 until roundArray.length()).map { i ->
                    val m = roundArray.getJSONObject(i)
                    TournamentMatch(
                        round = m.getInt("round"),
                        label = if (m.has("label")) m.getString("label") else null,
                        playerAIndex = if (m.has("a")) m.getInt("a") else null,
                        playerBIndex = if (m.has("b")) m.getInt("b") else null,
                        winnerIndex = if (m.has("winner")) m.getInt("winner") else null
                    )
                }
            }
            // Les anciennes sauvegardes n'ont pas de format : c'était toujours de l'élimination directe.
            val format = try {
                TournamentFormat.valueOf(obj.optString("format", TournamentFormat.KNOCKOUT.name))
            } catch (e: IllegalArgumentException) {
                TournamentFormat.KNOCKOUT
            }
            TournamentState(names, rounds, obj.optBoolean("finished", false), format)
        } catch (e: Exception) {
            // Sauvegarde illisible : on repart sans championnat plutôt que de planter au démarrage.
            // Le texte brut est mis de côté (une seule copie, la plus ancienne) avant qu'un nouvel
            // enregistrement ne l'écrase, comme pour le journal et les jeux personnalisés.
            Log.w(TAG, "Championnat sauvegardé illisible, copie de sécurité conservée", e)
            if (!prefs.contains(KEY_BACKUP)) prefs.edit { putString(KEY_BACKUP, json) }
            TournamentState()
        }
    }

    private companion object {
        const val KEY_TOURNAMENT = "tournament"
        /** Copie de sécurité du texte brut quand le championnat sauvegardé n'a pas pu être relu. */
        const val KEY_BACKUP = "tournament_unreadable_backup"
        const val FORMAT_VERSION = 1
        const val TAG = "TournamentViewModel"
    }
}
