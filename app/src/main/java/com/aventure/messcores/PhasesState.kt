package com.aventure.messcores

import androidx.compose.runtime.mutableStateListOf

/**
 * Phase atteinte par chaque joueur (Phase 10). Ce n'est pas un mode de jeu : la phase se superpose
 * au mode du jeu (compteur ou tableau), d'où une classe à part plutôt qu'un [ModeState].
 */
internal class PhasesState {

    private val _values = mutableStateListOf<Int>()

    /** Phase de chaque joueur ; vide pour les jeux sans phases. */
    val values: List<Int> get() = _values

    fun clear() = _values.clear()

    fun start(rules: GameRules, playerCount: Int) {
        if (rules.phases != null) repeat(playerCount) { _values.add(1) }
    }

    fun restore(saved: SavedGame) {
        if (saved.gameRules.phases == null) return
        val savedPhases = saved.phases
        if (savedPhases != null && savedPhases.size == saved.players.size) {
            savedPhases.forEach { _values.add(it) }
        } else {
            repeat(saved.players.size) { _values.add(1) }
        }
    }

    /** Phase d'un joueur : de 1 à [phaseCount], ou [phaseCount] + 1 s'il a terminé la dernière. */
    fun phaseFor(player: Int): Int = _values.getOrElse(player) { 1 }

    fun set(player: Int, phase: Int, phaseCount: Int) {
        if (player in _values.indices) _values[player] = phase.coerceIn(1, phaseCount + 1)
    }
}
