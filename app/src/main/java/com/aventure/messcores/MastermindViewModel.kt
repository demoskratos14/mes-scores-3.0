package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Partie de Mastermind en cours. Elle vit dans le ViewModel pour survivre à une rotation de l'écran,
 * mais pas à la fermeture de l'appli : le Mastermind n'est pas enregistré dans le journal.
 */
class MastermindViewModel : ViewModel() {

    var rules by mutableStateOf<GameRules?>(null)
        private set

    var players by mutableStateOf<List<String>>(emptyList())
        private set

    var settings by mutableStateOf(MastermindSettings())
        private set

    /** Null tant que les réglages ne sont pas validés ; sinon la partie, de la saisie des suites aux résultats. */
    var state by mutableStateOf<MastermindState?>(null)
        private set

    /** Prépare une partie pour ces joueurs : écran de réglages. */
    fun open(rules: GameRules, playerNames: List<String>) {
        this.rules = rules
        players = playerNames
        settings = MastermindSettings()
        state = null
    }

    /** Un invité qui rejoint une partie à plusieurs téléphones : seules les règles du jeu servent (titre, fiche de règles). */
    fun prepareOnline(rules: GameRules) {
        this.rules = rules
    }

    fun updateSettings(newSettings: MastermindSettings) {
        settings = newSettings.coerced()
    }

    /** Valide les réglages : chaque joueur va saisir sa suite secrète. */
    fun start() {
        state = Mastermind.newGame(players, settings)
    }

    fun submitSecret(pegs: List<Int>) {
        state = state?.let { Mastermind.withSecret(it, pegs) ?: it }
    }

    fun submitGuess(pegs: List<Int>) {
        state = state?.let { Mastermind.withGuess(it, pegs) ?: it }
    }

    fun nextTarget() {
        state = state?.let { Mastermind.nextTarget(it) }
    }

    /** Rejouer avec les mêmes joueurs : retour aux réglages, de nouvelles suites sont à saisir. */
    fun replay() {
        state = null
    }

    fun close() {
        state = null
        players = emptyList()
        rules = null
    }
}
