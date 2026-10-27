package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.random.Random

/**
 * Partie de Pendu à deux sur le même téléphone. Elle vit dans le ViewModel pour survivre à une rotation de l'écran,
 * mais pas à la fermeture de l'appli : le Pendu n'est pas enregistré dans le journal.
 *
 * Le téléphone passe de main en main : tant que [handoffTo] est renseigné, l'écran n'affiche que « Passe le téléphone
 * à… », sans le mot, pour que celui qui devine ne voie pas le mot pendant qu'on le choisit.
 */
class HangmanViewModel : ViewModel() {

    var rules by mutableStateOf<GameRules?>(null)
        private set

    var players by mutableStateOf<List<String>>(emptyList())
        private set

    var settings by mutableStateOf(HangmanSettings())
        private set

    /** Null tant que les réglages ne sont pas validés ; sinon la séance : choix du mot, lettres, résultat de chaque manche. */
    var game by mutableStateOf<HangmanGame?>(null)
        private set

    /** Le joueur à qui il faut passer le téléphone ; null quand le bon joueur l'a en main. */
    var handoffTo by mutableStateOf<Int?>(null)
        private set

    /** Prépare une partie pour ces joueurs : écran de réglages. */
    fun open(rules: GameRules, playerNames: List<String>) {
        this.rules = rules
        players = playerNames.take(2)
        settings = HangmanSettings()
        clearGame()
    }

    /** Un invité qui rejoint une partie à deux téléphones : seules les règles du jeu servent (titre, fiche de règles). */
    fun prepareOnline(rules: GameRules) {
        this.rules = rules
    }

    fun updateSettings(newSettings: HangmanSettings) {
        settings = newSettings.coerced()
    }

    /** Valide les réglages : le joueur tiré au sort ([random]) prend le téléphone pour choisir le premier mot. */
    fun start(random: Random = Random.Default) {
        if (players.size < 2) return
        val current = Hangman.newGame(players, settings, Hangman.randomFirst(random))
        game = current
        handoffTo = current.setter
    }

    /** Le bon joueur a le téléphone : l'écran de son tour s'affiche. */
    fun takePhone() {
        handoffTo = null
    }

    /** Celui qui choisit saisit son mot ; le téléphone passe ensuite à celui qui devine. Faux si le mot n'est pas jouable. */
    fun submitWord(raw: String): Boolean {
        val current = game ?: return false
        if (handoffTo != null) return false
        val next = Hangman.withWord(current, raw) ?: return false
        game = next
        handoffTo = next.guesser
        return true
    }

    /** Celui qui devine propose une lettre. */
    fun guess(letter: Char) {
        val current = game ?: return
        if (handoffTo != null) return
        game = Hangman.guess(current, letter) ?: return
    }

    /** Manche suivante : les rôles s'échangent et le téléphone passe à celui qui choisit le prochain mot. */
    fun nextRound() {
        val current = game ?: return
        val next = Hangman.nextRound(current) ?: return
        game = next
        handoffTo = next.setter
    }

    fun close() {
        clearGame()
        players = emptyList()
        rules = null
    }

    private fun clearGame() {
        game = null
        handoffTo = null
    }
}
