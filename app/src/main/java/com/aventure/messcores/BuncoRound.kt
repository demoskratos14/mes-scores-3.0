package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Où en est la manche de Bunco : un joueur lance ses dés, ou la manche est terminée (un joueur a atteint 21 points). */
internal enum class BuncoPhase { PLAY, DONE }

/** Ce que rapporte un lancer : un Bunco, trois dés identiques (autre numéro), des dés du numéro de la manche, ou rien. */
internal enum class BuncoKind { BUNCO, TRIPLE, MATCHES, NOTHING }

/** Le résultat d'un lancer de 3 dés : sa nature et les points qu'il rapporte. */
internal data class BuncoRoll(val kind: BuncoKind, val points: Int)

/** Valeur des lancers du Bunco : voir la fiche de règles du jeu (« Le tour de jeu »). */
internal object BuncoScoring {

    /** Nombre de manches : à la manche N, on cherche des dés montrant N. */
    const val ROUNDS = 6

    /** Nombre de dés lancés. */
    const val DICE = 3

    /** Points qui font gagner la manche à un joueur (un Bunco les rapporte d'un coup). */
    const val TARGET = 21

    const val BUNCO_POINTS = 21
    const val TRIPLE_POINTS = 5

    /**
     * Ce que rapportent [dice] à la manche [round] : 21 points pour trois dés du numéro de la manche (Bunco),
     * 5 points pour trois dés identiques d'un autre numéro, sinon 1 point par dé du numéro de la manche.
     */
    fun roll(dice: List<Int>, round: Int): BuncoRoll {
        if (dice.size != DICE) return BuncoRoll(BuncoKind.NOTHING, 0)
        val matches = dice.count { it == round }
        return when {
            matches == DICE -> BuncoRoll(BuncoKind.BUNCO, BUNCO_POINTS)
            dice.all { it == dice[0] } -> BuncoRoll(BuncoKind.TRIPLE, TRIPLE_POINTS)
            matches > 0 -> BuncoRoll(BuncoKind.MATCHES, matches)
            else -> BuncoRoll(BuncoKind.NOTHING, 0)
        }
    }
}

/**
 * Une partie de Bunco jouée tour par tour, de la même façon pour tous les téléphones.
 *
 * - La partie compte 6 manches ; à la manche N, les dés qui montrent N rapportent des points
 *   (voir [BuncoScoring.roll]). Le premier joueur change à chaque manche : le joueur 1 à la manche 1,
 *   le joueur 2 à la manche 2, etc.
 * - Le joueur qui joue lance les 3 dés. Tant que le lancer rapporte des points, il relance ; au premier lancer
 *   qui ne rapporte rien, son tour est fini ([endTurn]) et la main passe au joueur suivant.
 * - La manche se termine dès qu'un joueur atteint [BuncoScoring.TARGET] points : c'est lui qui la gagne. Les points
 *   de chacun sont alors inscrits dans le tableau des scores (voir [BuncoSection]).
 *
 * [startRound] : manche de départ (une partie reprise au milieu repart de la manche suivante).
 * [rollDie] tire un dé de 1 à 6 (remplaçable dans les tests).
 */
internal class BuncoRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val rollDie: () -> Int = { Random.nextInt(1, 7) }
) {
    private val firstRound = startRound.coerceIn(1, BuncoScoring.ROUNDS)

    /** Les dés du joueur qui joue : réinitialisés à chaque joueur, sans pouvoir en garder. */
    val turn = DiceTurn(DiceSetup(diceCount = BuncoScoring.DICE, allowHold = false), rollDie)

    /** Numéro de la manche (1 à 6) : c'est le numéro des dés qui rapportent des points. */
    var round by mutableIntStateOf(firstRound)
        private set

    var phase by mutableStateOf(BuncoPhase.PLAY)
        private set

    /** Le joueur qui joue ; à la fin de la manche, celui qui l'a gagnée. */
    var current by mutableIntStateOf(starterOf(firstRound))
        private set

    /** Points de chaque joueur dans la manche en cours. */
    var points by mutableStateOf(List(playerCount) { 0 })
        private set

    /** Le gagnant de la manche ; -1 tant qu'elle n'est pas terminée. */
    var winner by mutableIntStateOf(-1)
        private set

    /** Nombre d'actions faites dans la partie (lancer, passer la main, manche suivante) : l'état le plus avancé l'emporte (voir [mergeFrom]). */
    var version by mutableIntStateOf(0)
        private set

    /** Le premier joueur de la manche [round] : il change à chaque manche. */
    fun starterOf(round: Int): Int = (round - 1) % playerCount.coerceAtLeast(1)

    /** Ce qu'a rapporté le dernier lancer du joueur ; null s'il n'a pas encore lancé les dés. */
    val lastRoll: BuncoRoll? get() = if (turn.hasRolled) BuncoScoring.roll(turn.values, round) else null

    /** Vrai quand le dernier lancer n'a rien rapporté : le joueur n'a plus qu'à passer la main. */
    val turnOver: Boolean get() = phase == BuncoPhase.PLAY && lastRoll?.points == 0

    /** Vrai si le joueur qui joue peut lancer les dés (il lance tant qu'il marque). */
    val canRoll: Boolean get() = phase == BuncoPhase.PLAY && !turnOver && turn.canRoll

    /** Vrai pendant la dernière manche. */
    val isLastRound: Boolean get() = round >= BuncoScoring.ROUNDS

    /** Lance les dés du joueur qui joue. Renvoie vrai si ce lancer termine la manche (21 points atteints). */
    fun roll(): Boolean {
        if (!canRoll) return false
        turn.roll()
        version += 1
        val gained = BuncoScoring.roll(turn.values, round).points
        points = points.toMutableList().also { it[current] += gained }
        if (points[current] >= BuncoScoring.TARGET) {
            winner = current
            phase = BuncoPhase.DONE
        }
        return phase == BuncoPhase.DONE
    }

    /** Le lancer n'a rien rapporté : la main passe au joueur suivant. */
    fun endTurn() {
        if (!turnOver) return
        version += 1
        current = (current + 1) % playerCount.coerceAtLeast(1)
        turn.reset()
    }

    /** Manche suivante (sans effet après la dernière) : points remis à zéro, nouveau premier joueur. */
    fun nextRound() {
        if (phase != BuncoPhase.DONE || isLastRound) return
        version += 1
        round += 1
        current = starterOf(round)
        points = List(playerCount) { 0 }
        winner = -1
        turn.reset()
        phase = BuncoPhase.PLAY
    }

    /** Inscrit les points de chaque joueur dans la ligne de la manche du tableau des scores (0 pour ceux qui n'ont rien marqué). */
    fun writeTo(viewModel: ScoreViewModel) {
        points.forEachIndexed { player, value -> viewModel.setCell(round - 1, player, value, false) }
    }

    // ---------- Sauvegarde et partage ----------

    private fun resetAll() {
        round = firstRound
        phase = BuncoPhase.PLAY
        current = starterOf(firstRound)
        points = List(playerCount) { 0 }
        winner = -1
        version = 0
        turn.reset()
    }

    /** État sous forme de liste d'entiers, pour survivre à une rotation de l'écran et être partagé (voir [restoreFrom]). */
    fun snapshot(): List<Int> = buildList {
        add(phase.ordinal)
        add(playerCount)
        add(round)
        add(current)
        add(winner)
        addAll(points)
        add(version)
        addAll(turn.snapshot())
    }

    /** Un état lu depuis une liste produite par [snapshot], sans encore l'appliquer. */
    private class Parsed(
        val phase: BuncoPhase,
        val round: Int,
        val current: Int,
        val winner: Int,
        val points: List<Int>,
        val version: Int,
        val dice: List<Int>
    )

    /** Lit [data] ; null si la liste n'est pas un état valide pour cette partie. */
    private fun parse(data: List<Int>): Parsed? = try {
        var cursor = 0
        fun next(): Int = data[cursor++]

        val newPhase = BuncoPhase.values()[next()]
        require(next() == playerCount)
        val newRound = next()
        val newCurrent = next()
        val newWinner = next()
        val newPoints = List(playerCount) { next() }
        val newVersion = next()
        require(newRound in 1..BuncoScoring.ROUNDS)
        require(newCurrent in 0 until playerCount && newWinner in -1 until playerCount)
        // Un joueur a moins de 21 points avant son dernier lancer, qui en rapporte au plus 21.
        require(newPoints.all { it in 0 until 2 * BuncoScoring.TARGET })
        require(newVersion >= 0)
        Parsed(newPhase, newRound, newCurrent, newWinner, newPoints, newVersion, data.drop(cursor))
    } catch (e: Exception) {
        null
    }

    private fun applyState(state: Parsed) {
        phase = state.phase
        round = state.round
        current = state.current
        winner = state.winner
        points = state.points
        version = state.version
        turn.restoreFrom(state.dice)
    }

    /** Rétablit l'état produit par [snapshot] ; une liste invalide remet la partie au départ. */
    fun restoreFrom(data: List<Int>) {
        resetAll()
        parse(data)?.let { applyState(it) }
    }

    /**
     * Fusionne l'état [data] reçu d'un autre téléphone avec celui-ci, au lieu de le remplacer : la partie se joue un
     * joueur à la fois, donc l'état le plus avancé (celui qui a vu le plus d'actions) l'emporte, et un message en retard
     * ne défait plus un lancer ou un tour déjà joué. Deux actions faites au même instant sur le même état se départagent
     * de la même façon sur tous les téléphones, qui finissent donc avec le même état.
     */
    fun mergeFrom(data: List<Int>) {
        val remote = parse(data) ?: return
        val remoteWins = if (remote.version != version) remote.version > version else compareIntLists(data, snapshot()) > 0
        if (remoteWins) applyState(remote)
    }
}
