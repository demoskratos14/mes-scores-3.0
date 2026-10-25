package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlin.random.Random

/**
 * Partie de touché coulé à deux sur le même téléphone. Elle vit dans le ViewModel pour survivre à une rotation de
 * l'écran, mais pas à la fermeture de l'appli : le touché coulé n'est pas enregistré dans le journal.
 *
 * Le téléphone passe de main en main : tant que [handoffTo] est renseigné, l'écran n'affiche que « Passe le téléphone
 * à… », sans aucune grille, pour que personne ne voie la flotte de l'autre.
 */
class BattleshipViewModel : ViewModel() {

    var rules by mutableStateOf<GameRules?>(null)
        private set

    var players by mutableStateOf<List<String>>(emptyList())
        private set

    var settings by mutableStateOf(BattleshipSettings())
        private set

    /** Null tant que les réglages ne sont pas validés ; sinon la partie, du placement des flottes aux résultats. */
    var game by mutableStateOf<BattleshipGame?>(null)
        private set

    /** Le joueur à qui il faut passer le téléphone ; null quand le bon joueur l'a en main. */
    var handoffTo by mutableStateOf<Int?>(null)
        private set

    /** Le joueur qui a le téléphone en main (le dernier à avoir appuyé sur « C'est moi »), ou -1. */
    var viewer by mutableIntStateOf(-1)
        private set

    /** Le tir qui vient d'être fait, affiché jusqu'à ce que le joueur passe à la suite ; null sinon. */
    var lastShot by mutableStateOf<BattleshipShot?>(null)
        private set

    /** Prépare une partie pour ces joueurs : écran de réglages. */
    fun open(rules: GameRules, playerNames: List<String>) {
        this.rules = rules
        players = playerNames.take(2)
        settings = BattleshipSettings()
        clearGame()
    }

    /** Un invité qui rejoint une partie à deux téléphones : seules les règles du jeu servent (titre, fiche de règles). */
    fun prepareOnline(rules: GameRules) {
        this.rules = rules
    }

    fun updateSettings(newSettings: BattleshipSettings) {
        settings = newSettings
    }

    /** Valide les réglages : le premier joueur prend le téléphone pour placer sa flotte. [random] tire au sort qui tirera en premier. */
    fun start(random: Random = Random.Default) {
        if (players.size < 2) return
        val first = random.nextInt(2)
        game = Battleship.newGame(players, settings, first)
        lastShot = null
        viewer = -1
        handoffTo = 0
    }

    /** Le bon joueur a le téléphone : l'écran de son tour s'affiche. */
    fun takePhone() {
        val next = handoffTo ?: return
        viewer = next
        handoffTo = null
    }

    /** Valide la flotte du joueur qui place : puis le téléphone passe à l'autre, ou au premier tireur. */
    fun submitFleet(ships: List<List<Int>>) {
        val current = game ?: return
        val player = current.fleets.indexOfFirst { it == null }
        if (player < 0) return
        val next = Battleship.withFleet(current, player, ships) ?: return
        game = next
        handoffTo = next.fleets.indexOfFirst { it == null }.takeIf { it >= 0 } ?: next.turn
    }

    /** Le joueur dont c'est le tour tire sur [cell]. */
    fun fire(cell: Int) {
        val current = game ?: return
        if (lastShot != null || handoffTo != null || current.turn != viewer) return
        val next = Battleship.fire(current, cell) ?: return
        lastShot = next.shots[viewer].last()
        game = next
    }

    /** Après avoir lu le résultat de son tir : tirer encore, passer le téléphone, ou voir le résultat de la partie. */
    fun afterShot() {
        val current = game ?: return
        if (lastShot == null) return
        lastShot = null
        if (current.phase == BattleshipPhase.PLAYING && current.turn != viewer) handoffTo = current.turn
    }

    /** Rejouer avec les mêmes joueurs : retour aux réglages, de nouvelles flottes sont à placer. */
    fun replay() {
        clearGame()
    }

    fun close() {
        clearGame()
        players = emptyList()
        rules = null
    }

    private fun clearGame() {
        game = null
        handoffTo = null
        viewer = -1
        lastShot = null
    }
}
