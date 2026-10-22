package com.aventure.messcores

import androidx.compose.runtime.mutableStateListOf

/**
 * Mode COUNTER : un compteur par joueur, modifié par des appuis sur + et − (annulables).
 *
 * [isGameOver] interroge la fin de partie générale du ViewModel : une partie terminée n'accepte plus d'appui.
 */
internal class CounterMode(
    private val isGameOver: () -> Boolean
) : ModeState {

    private val _counters = mutableStateListOf<Int>()
    private val history = UndoHistory()

    /** Valeur du compteur de chaque joueur. */
    val values: List<Int> get() = _counters

    override fun clear() {
        _counters.clear()
        history.clear()
    }

    override fun close() = history.clear()

    override fun start(playerCount: Int) {
        repeat(playerCount) { _counters.add(0) }
    }

    override fun restore(saved: SavedGame, playerCount: Int) {
        val counters = saved.counters
        if (counters.isNullOrEmpty()) {
            repeat(playerCount) { _counters.add(0) }
        } else {
            counters.forEach { _counters.add(it) }
        }
    }

    override fun applyTo(saved: SavedGame): SavedGame = saved.copy(counters = _counters.toList())

    override fun hasProgress(): Boolean = _counters.any { it != 0 }

    fun increment(player: Int, delta: Int) {
        if (isGameOver()) return
        if (player !in _counters.indices) return
        _counters[player] = _counters[player] + delta
        history.push(UndoAction.CounterChange(player, delta))
    }

    override val canUndo: Boolean get() = history.canUndo
    override val canRedo: Boolean get() = history.canRedo

    override fun undo() {
        val action = history.popUndo() as? UndoAction.CounterChange ?: return
        if (action.player in _counters.indices) {
            _counters[action.player] = _counters[action.player] - action.delta
            history.pushRedo(RedoAction.CounterChange(action.player, action.delta))
        }
    }

    override fun redo() {
        val action = history.popRedo() as? RedoAction.CounterChange ?: return
        if (action.player in _counters.indices) {
            _counters[action.player] = _counters[action.player] + action.delta
            history.push(UndoAction.CounterChange(action.player, action.delta), clearRedo = false)
        }
    }

    override fun total(player: Int): Int = _counters.getOrElse(player) { 0 }

    override fun roundsPlayed(): Int = 0
}
