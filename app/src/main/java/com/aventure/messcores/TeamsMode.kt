package com.aventure.messcores

import androidx.compose.runtime.mutableStateListOf

/**
 * Mode VARIABLE_TEAMS : les manches à équipes variables (Tarot, bridge, skat…). Pas d'annulation
 * ici : chaque manche se corrige ou se supprime depuis l'historique des manches.
 */
internal class TeamsMode(
    private val rules: () -> GameRules
) : ModeState {

    private val _rounds = mutableStateListOf<TeamRound>()

    /** Manches jouées, dans l'ordre. */
    val rounds: List<TeamRound> get() = _rounds

    override fun clear() = _rounds.clear()

    override fun close() = Unit

    override fun start(playerCount: Int) = Unit

    override fun restore(saved: SavedGame, playerCount: Int) {
        saved.teamRounds?.forEach { _rounds.add(it) }
    }

    override fun applyTo(saved: SavedGame): SavedGame = saved.copy(teamRounds = _rounds.toList())

    override fun hasProgress(): Boolean = _rounds.isNotEmpty()

    /**
     * Ajoute une manche à équipes variables : [teamAPlayers] regroupe les joueurs de
     * l'équipe qui reçoit [baseValue] (signé et multiplié selon [multiplierId]) ;
     * tous les autres joueurs reçoivent l'opposé de ce score.
     */
    fun add(teamALabel: String, teamAPlayers: Set<Int>, baseValue: Int, isNegative: Boolean, multiplierId: String) {
        val value = rules().effectiveValue(baseValue, isNegative, multiplierId)
        _rounds.add(TeamRound(teamALabel, teamAPlayers, value))
    }

    /** Ajoute une manche déjà entièrement calculée (voir [TeamRound.deltas]). */
    fun append(round: TeamRound) {
        _rounds.add(round)
    }

    /** Remplace la manche d'index [index] (correction d'une manche déjà saisie). */
    fun replace(index: Int, round: TeamRound) {
        if (index in _rounds.indices) _rounds[index] = round
    }

    fun remove(index: Int) {
        if (index in _rounds.indices) _rounds.removeAt(index)
    }

    override val canUndo: Boolean get() = false
    override val canRedo: Boolean get() = false
    override fun undo() = Unit
    override fun redo() = Unit

    override fun total(player: Int): Int = _rounds.sumOf { round ->
        round.deltas?.getOrNull(player)
            ?: if (player in round.teamAPlayers) round.value else -round.value
    }

    override fun roundsPlayed(): Int = _rounds.size
}
