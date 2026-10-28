package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

/** Où en est le tour du Cul de chouette : le joueur lance, ou il faut dire qui marque / perd (chouette-velute, suite). */
internal enum class CdcPhase { PLAY, CLAIM }

/** Les combinaisons d'un lancer de trois dés. */
internal enum class CdcKind { CUL_DE_CHOUETTE, CHOUETTE_VELUTE, VELUTE, CHOUETTE, SUITE, NEANT }

/**
 * Le résultat d'un lancer : sa combinaison, les points qu'elle vaut ([points], toujours positif : pour une
 * [CdcKind.SUITE], c'est la perte de points du dernier joueur à réagir) et la valeur du dé qui la fait
 * (la chouette, la velute ou le cul ; le plus petit dé d'une suite).
 */
internal data class CdcRoll(val kind: CdcKind, val points: Int, val value: Int)

/** Valeur des lancers du Cul de chouette : voir la fiche de règles du jeu (« Les combinaisons »). */
internal object CdcScoring {

    /** Nombre de dés lancés : deux chouettes et un cul. */
    const val DICE = 3

    /** Points perdus par le dernier joueur à réagir à une suite. */
    const val SUITE_PENALTY = 10

    /**
     * La combinaison de [dice] :
     * - trois dés identiques : cul de chouette, 40 points plus 10 fois la valeur du dé ;
     * - un dé égal à la somme des deux autres : velute, 2 fois le carré de ce dé ; si deux dés sont aussi identiques
     *   (1-1-2), c'est une chouette-velute, qui vaut la velute mais que seul le plus rapide marque ;
     * - deux dés identiques : chouette, le carré de leur valeur ;
     * - trois dés qui se suivent : suite, le dernier à réagir perd [SUITE_PENALTY] points ;
     * - sinon : néant.
     * 1-2-3 est à la fois une velute et une suite : la velute l'emporte.
     */
    fun roll(dice: List<Int>): CdcRoll {
        if (dice.size != DICE) return CdcRoll(CdcKind.NEANT, 0, 0)
        val (low, middle, high) = dice.sorted()
        if (low == middle && middle == high) return CdcRoll(CdcKind.CUL_DE_CHOUETTE, 40 + 10 * low, low)
        val pair = if (low == middle) low else if (middle == high) middle else null
        // Un dé égal à la somme des deux autres est forcément le plus grand.
        if (high == low + middle) {
            val points = 2 * high * high
            return CdcRoll(if (pair != null) CdcKind.CHOUETTE_VELUTE else CdcKind.VELUTE, points, high)
        }
        if (pair != null) return CdcRoll(CdcKind.CHOUETTE, pair * pair, pair)
        if (middle == low + 1 && high == middle + 1) return CdcRoll(CdcKind.SUITE, SUITE_PENALTY, low)
        return CdcRoll(CdcKind.NEANT, 0, 0)
    }
}

/**
 * Une partie de Cul de chouette jouée tour par tour, de la même façon pour tous les téléphones.
 *
 * - Le joueur qui joue lance les trois dés une seule fois ; la combinaison (voir [CdcScoring.roll]) rapporte ou
 *   fait perdre des points, puis la main passe au joueur suivant ([endTurn]).
 * - Chouette-velute et suite se jouent à la rapidité des joueurs : l'appli demande qui a crié le premier, ou qui a
 *   réagi en dernier ([phase] vaut alors [CdcPhase.CLAIM]) ; on peut aussi répondre « personne ».
 * - Chaque lancer est inscrit dans une ligne du tableau des scores ([settle]) : le total de chacun, la fin de partie
 *   (343 points) et le journal viennent de ce tableau, comme pour une partie notée à la main.
 *
 * [startPlayer] : le joueur qui lance en premier (une partie reprise continue avec le joueur suivant).
 * [rollDie] tire un dé de 1 à 6 (remplaçable dans les tests).
 */
internal class CdcRound(
    val playerCount: Int,
    startPlayer: Int = 0,
    private val rollDie: () -> Int = { Random.nextInt(1, 7) }
) {
    private val first = startPlayer.coerceIn(0, (playerCount - 1).coerceAtLeast(0))

    /** Les dés du joueur qui joue : un seul lancer, sans pouvoir en garder. */
    val turn = DiceTurn(DiceSetup(diceCount = CdcScoring.DICE, allowHold = false), rollDie)

    /** Le joueur qui joue. */
    var current by mutableIntStateOf(first)
        private set

    var phase by mutableStateOf(CdcPhase.PLAY)
        private set

    /** Le résultat du lancer du joueur ; null s'il n'a pas encore lancé les dés. */
    val lastRoll: CdcRoll? get() = if (turn.hasRolled) CdcScoring.roll(turn.values) else null

    /** Vrai si le lancer dépend de la rapidité des joueurs : il faut dire qui marque (chouette-velute) ou qui perd (suite). */
    val needsClaim: Boolean
        get() = lastRoll?.kind.let { it == CdcKind.CHOUETTE_VELUTE || it == CdcKind.SUITE }

    /** Vrai si le joueur qui joue peut lancer les dés. */
    val canRoll: Boolean get() = phase == CdcPhase.PLAY && !turn.hasRolled

    /** Vrai quand le lancer est réglé : le joueur n'a plus qu'à passer la main. */
    val turnOver: Boolean get() = phase == CdcPhase.PLAY && turn.hasRolled

    /** Lance les dés du joueur qui joue. Renvoie vrai si le lancer est réglé tout de suite (sinon, voir [needsClaim]). */
    fun roll(): Boolean {
        if (!canRoll) return false
        turn.roll()
        if (needsClaim) phase = CdcPhase.CLAIM
        return !needsClaim
    }

    /** Le joueur et la variation de points du lancer ; [claimant] : qui a crié le premier (chouette-velute) ou réagi en dernier (suite). */
    internal fun outcome(claimant: Int?): Pair<Int, Int>? {
        val roll = lastRoll ?: return null
        return when (roll.kind) {
            CdcKind.CHOUETTE_VELUTE -> if (claimant == null) current to 0 else claimant to roll.points
            CdcKind.SUITE -> if (claimant == null) current to 0 else claimant to -roll.points
            CdcKind.NEANT -> current to 0
            else -> current to roll.points
        }
    }

    /**
     * Inscrit le lancer dans la prochaine ligne du tableau des scores (0 quand rien n'est marqué), une seule fois,
     * par le téléphone qui a fait le geste : juste après [roll] quand il renvoie vrai, ou quand on désigne [claimant] (voir [outcome]).
     */
    fun settle(viewModel: ScoreViewModel, claimant: Int? = null) {
        if (claimant != null && claimant !in 0 until playerCount) return
        val (player, delta) = outcome(claimant) ?: return
        viewModel.setCell(viewModel.roundsPlayed(), player, abs(delta), delta < 0)
        if (phase == CdcPhase.CLAIM) phase = CdcPhase.PLAY
    }

    /** Le lancer est réglé : la main passe au joueur suivant. */
    fun endTurn() {
        if (!turnOver) return
        current = (current + 1) % playerCount.coerceAtLeast(1)
        turn.reset()
    }

    // ---------- Sauvegarde et partage ----------

    private fun resetAll() {
        current = first
        phase = CdcPhase.PLAY
        turn.reset()
    }

    /** État sous forme de liste d'entiers, pour survivre à une rotation de l'écran et être partagé (voir [restoreFrom]). */
    fun snapshot(): List<Int> = buildList {
        add(phase.ordinal)
        add(playerCount)
        add(current)
        addAll(turn.snapshot())
    }

    /** Rétablit l'état produit par [snapshot] ; une liste invalide remet la partie au départ. */
    fun restoreFrom(data: List<Int>) {
        resetAll()
        try {
            var cursor = 0
            fun next(): Int = data[cursor++]

            val newPhase = CdcPhase.values()[next()]
            require(next() == playerCount)
            val newCurrent = next()
            require(newCurrent in 0 until playerCount)

            phase = newPhase
            current = newCurrent
            turn.restoreFrom(data.drop(cursor))
        } catch (e: Exception) {
            resetAll()
        }
    }
}
