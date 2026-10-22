package com.aventure.messcores

import androidx.compose.runtime.mutableStateListOf

/**
 * État et logique d'un [ScoreMode] : tout ce qui change d'un mode à l'autre est ici, pour que
 * [ScoreViewModel] n'ait plus qu'à déléguer au gestionnaire du mode de la partie en cours.
 *
 * Le ViewModel garde un exemplaire de chaque gestionnaire pour toute sa durée de vie (les écrans
 * lisent leurs listes observables, qui ne doivent donc pas changer d'identité d'une partie à l'autre) :
 * [clear] les vide tous au début d'une partie, puis [start] ou [restore] initialise le seul qui sert.
 *
 * Les gestionnaires ne connaissent ni les noms ni les couleurs des joueurs (ils restent dans le
 * ViewModel) : seul le nombre de joueurs leur est donné.
 */
internal interface ModeState {

    /** Vide tout l'état du mode (début d'une nouvelle partie ou reprise d'une partie enregistrée). */
    fun clear()

    /** Fin de partie ([ScoreViewModel.closeGame]) : ne garde que ce qui doit disparaître à la fermeture. */
    fun close()

    /** Initialise une nouvelle partie de [playerCount] joueurs. Appelé après [clear]. */
    fun start(playerCount: Int)

    /** Restaure la partie [saved]. Appelé après [clear] ; les règles du ViewModel sont déjà celles de [saved]. */
    fun restore(saved: SavedGame, playerCount: Int)

    /** Renvoie [saved] complété des données propres au mode (cases, compteurs, manches, tournoi, match). */
    fun applyTo(saved: SavedGame): SavedGame

    /** Vrai s'il y a déjà quelque chose de saisi (donc à perdre si l'on quitte). */
    fun hasProgress(): Boolean

    val canUndo: Boolean
    val canRedo: Boolean
    fun undo()
    fun redo()

    /** Total d'un joueur dans ce mode. */
    fun total(player: Int): Int

    /** Nombre de manches réellement jouées. */
    fun roundsPlayed(): Int

    /**
     * Fin de partie propre au mode (poker, tennis/padel). Null : le mode n'a pas de règle de fin à lui,
     * c'est la condition générale des règles du jeu ([ScoreViewModel.isGameOver]) qui décide.
     */
    fun gameOver(): Boolean? = null

    /** Rangs propres au mode (poker). Null : classement général d'après les totaux. */
    fun ranks(): List<Int>? = null
}

/**
 * Valeur d'une saisie après application du signe et de la règle de multiplication [multiplierId]
 * (règle inconnue : multiplication normale). Partagée par le tableau et les manches à équipes.
 */
internal fun GameRules.effectiveValue(base: Int, isNegative: Boolean, multiplierId: String): Int {
    val rule = multipliers.find { it.id == multiplierId } ?: GameRules.NORMAL_MULTIPLIER
    val magnitude = base * rule.factor + rule.bonus
    return if (isNegative) -magnitude else magnitude
}

/**
 * Pile d'annulation et de rétablissement des saisies de case et de compteur (modes TABLE et COUNTER),
 * limitée à [MAX_UNDO] saisies.
 */
internal class UndoHistory {

    companion object {
        /** Nombre maximal de saisies mémorisées pour l'annulation. */
        const val MAX_UNDO = 100
    }

    private val undoStack = mutableStateListOf<UndoAction>()
    private val redoStack = mutableStateListOf<RedoAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    /** [clearRedo] est faux uniquement quand un rétablissement rejoue une saisie : toute autre saisie vide les rétablissables. */
    fun push(action: UndoAction, clearRedo: Boolean = true) {
        undoStack.add(action)
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        if (clearRedo) redoStack.clear()
    }

    fun popUndo(): UndoAction? = undoStack.removeLastOrNull()

    fun popRedo(): RedoAction? = redoStack.removeLastOrNull()

    fun pushRedo(action: RedoAction) {
        redoStack.add(action)
    }
}
