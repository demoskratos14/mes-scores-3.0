package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Mode RACKET (tennis, padel) : les réglages du match et la suite des points gagnés ; tout le
 * score (points, jeux, sets) s'en déduit via [RacketScoring]. Annuler retire le dernier point,
 * rétablir le rejoue.
 */
internal class RacketMode(
    private val rules: () -> GameRules
) : ModeState {

    /** Réglages du match (nombre de sets, point d'or, set décisif) : modifiables tant qu'aucun point n'est joué. */
    var config by mutableStateOf(RacketConfig())
        private set

    private var playerCount = 0

    /** Camp (0 ou 1) qui a gagné chaque point, dans l'ordre. */
    private val points = mutableStateListOf<Int>()

    // Points annulés, rétablissables tant qu'aucun nouveau point n'est joué.
    private val redoPoints = mutableStateListOf<Int>()

    /** État du match (points, jeux, sets) : recalculé à partir des points gagnés. */
    val state: RacketState get() = RacketScoring.replay(config, points)

    override fun clear() {
        config = RacketConfig()
        points.clear()
        redoPoints.clear()
    }

    override fun close() = clear()

    override fun start(playerCount: Int) {
        this.playerCount = playerCount
        config = RacketConfig.defaultsFor(rules())
    }

    override fun restore(saved: SavedGame, playerCount: Int) {
        this.playerCount = playerCount
        config = saved.racket?.config ?: RacketConfig.defaultsFor(saved.gameRules)
        saved.racket?.points?.forEach { points.add(it) }
    }

    override fun applyTo(saved: SavedGame): SavedGame = saved.copy(racket = RacketMatch(config, points.toList()))

    override fun hasProgress(): Boolean = points.isNotEmpty()

    /** Ajoute un point au camp [side] (0 ou 1). Sans effet une fois le match terminé. */
    fun point(side: Int) {
        if (side !in 0..1) return
        if (state.winner != null) return
        points.add(side)
        redoPoints.clear()
    }

    /** Change les réglages du match ; refusé dès que le premier point est joué. Renvoie vrai si changé. */
    fun setConfig(newConfig: RacketConfig): Boolean {
        if (points.isNotEmpty()) return false
        config = newConfig
        redoPoints.clear()
        return true
    }

    /**
     * Nouvel ordre des joueurs si les joueurs d'index [side0] forment le camp 1 (les autres le camp 2),
     * ou null si c'est refusé : un point est déjà joué, [side0] n'a pas la taille du camp 1, contient un
     * index invalide ou en double, ou l'ordre ne change pas. C'est au ViewModel, qui détient les noms et
     * les couleurs, d'appliquer cet ordre ; il appelle ensuite [onTeamsChanged].
     */
    fun teamOrder(count: Int, side0: List<Int>): List<Int>? {
        if (points.isNotEmpty()) return null
        if (side0.size != (count + 1) / 2 || side0.distinct().size != side0.size || side0.any { it !in 0 until count }) return null
        val order = RacketScoring.orderForSide0(count, side0)
        if (order == (0 until count).toList()) return null
        return order
    }

    /** À appeler une fois l'ordre des joueurs modifié : plus rien de rétablissable. */
    fun onTeamsChanged() = redoPoints.clear()

    override val canUndo: Boolean get() = points.isNotEmpty()
    override val canRedo: Boolean get() = redoPoints.isNotEmpty()

    override fun undo() {
        val lastPoint = points.removeLastOrNull() ?: return
        redoPoints.add(lastPoint)
    }

    override fun redo() {
        val nextPoint = redoPoints.removeLastOrNull() ?: return
        points.add(nextPoint)
    }

    /** Tennis / padel : sets gagnés par le camp du joueur. */
    override fun total(player: Int): Int = state.setsWon(RacketScoring.sideOf(player, playerCount))

    override fun roundsPlayed(): Int = state.sets.size

    /** Terminé dès qu'un camp a gagné le nombre de sets prévu. */
    override fun gameOver(): Boolean = state.winner != null
}
