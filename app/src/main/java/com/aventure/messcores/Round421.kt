package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Où en est la partie de 421 : avant la manche de départ, manche en cours (de départ ou non), ou manche terminée. */
internal enum class Phase421 { DRAW, PLAY, DONE }

/**
 * Résultat d'une manche : celui qui a le meilleur jeu, celui qui a le moins bon et la valeur du meilleur jeu.
 * [draw] : le meilleur et le moins bon jeu sont à égalité parfaite (même jeu, même nombre de lancers) : la manche
 * est nulle, aucun jeton ne bouge et elle est rejouée.
 */
internal data class Result421(val winner: Int, val loser: Int, val points: Int, val draw: Boolean = false)

/**
 * Une manche de 421 jouée tour par tour, de la même façon pour tous les téléphones.
 *
 * - Au départ, on joue une manche de départ ([opening]) : une vraie manche, mais sans jetons. Celui qui a le meilleur
 *   jeu commence la partie ; en cas d'égalité parfaite, la manche de départ est rejouée. On peut aussi choisir
 *   directement le premier joueur.
 * - Le premier joueur de la manche lance jusqu'à 3 fois et garde son jeu quand il veut : le nombre de lancers
 *   qu'il a faits devient la limite de tous les autres joueurs.
 * - Chacun joue dans l'ordre de la table ; quand le dernier a gardé son jeu, la manche est terminée. Le moins bon
 *   jeu perd la manche (voir [result]) et prend les jetons du meilleur (voir [Dice421.settle]) ; il commence la
 *   manche suivante.
 *
 * [rollDie] tire un dé de 1 à 6 (remplaçable dans les tests).
 */
internal class Round421(
    val playerCount: Int,
    val maxRolls: Int = 3,
    private val rollDie: () -> Int = { Random.nextInt(1, 7) }
) {
    /** Les dés du joueur qui joue : réinitialisés à chaque joueur, avec la limite de lancers de la manche. */
    val turn = DiceTurn(DiceSetup(diceCount = 3, maxRolls = maxRolls), rollDie)

    var phase by mutableStateOf(Phase421.DRAW)
        private set

    /** Vrai pendant la manche de départ : elle désigne le premier joueur, mais aucun jeton ne bouge. */
    var opening by mutableStateOf(false)
        private set

    /** Le premier joueur de la manche ; -1 tant qu'il n'a pas été désigné ou choisi. */
    var starter by mutableIntStateOf(-1)
        private set

    /** Les joueurs de la manche, dans l'ordre où ils jouent. */
    var order by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Place, dans [order], du joueur qui joue. */
    var position by mutableIntStateOf(0)
        private set

    /** Nombre de lancers autorisés : celui du jeu tant que le premier joueur n'a pas gardé le sien. */
    var limit by mutableIntStateOf(maxRolls)
        private set

    /** Le jeu gardé par chaque joueur (vide : n'a pas encore joué cette manche). */
    var hands by mutableStateOf(List(playerCount) { emptyList<Int>() })
        private set

    /** Nombre de lancers faits par chaque joueur cette manche. */
    var rolls by mutableStateOf(List(playerCount) { 0 })
        private set

    /** Jetons déplacés à la fin de la manche (voir [recordTokens]). */
    var tokens by mutableIntStateOf(0)
        private set

    /** Vrai si ces jetons viennent du milieu (la charge), faux s'ils ont été pris au gagnant (la décharge). */
    var tokensFromPot by mutableStateOf(true)
        private set

    /** Le joueur à qui c'est de jouer : celui de la manche, ou le perdant d'une manche terminée. */
    val currentPlayer: Int
        get() = when (phase) {
            Phase421.DRAW -> starter.coerceAtLeast(0)
            Phase421.PLAY -> order.getOrElse(position) { 0 }
            Phase421.DONE -> result()?.loser ?: 0
        }

    /** Vrai pendant le tour du premier joueur de la manche : c'est lui qui fixe le nombre de lancers. */
    val isStarterTurn: Boolean get() = phase == Phase421.PLAY && position == 0

    /** Vrai quand le joueur qui joue a lancé les dés au moins une fois, donc peut garder son jeu. */
    val canKeep: Boolean get() = phase == Phase421.PLAY && turn.hasRolled

    // ---------- Manche de départ ----------

    /**
     * Lance la manche de départ : tous les joueurs de [active] jouent une manche, en commençant par le premier de la
     * table, sans que les jetons comptent. Le meilleur jeu désigne le premier joueur (voir [finishOpening]).
     */
    fun startOpening(active: List<Int>) {
        if (phase != Phase421.DRAW || starter >= 0) return
        opening = true
        startRound(0, active)
    }

    /**
     * Termine la manche de départ : le gagnant commence la première vraie manche ; en cas d'égalité parfaite, la
     * manche de départ est rejouée avec le même premier joueur. [active] : les joueurs de la manche.
     */
    fun finishOpening(active: List<Int>) {
        if (phase != Phase421.DONE || !opening) return
        val result = result() ?: return
        if (result.draw) {
            startRound(order.first(), active)
        } else {
            opening = false
            startRound(result.winner, active)
        }
    }

    /** Choisit directement le premier joueur, sans tirage. */
    fun chooseStarter(player: Int) {
        if (phase != Phase421.DRAW || player !in 0 until playerCount) return
        starter = player
    }

    /** Commence la première manche avec le joueur choisi directement ; [active] : les joueurs de la manche. */
    fun begin(active: List<Int>) {
        if (phase != Phase421.DRAW || starter < 0) return
        startRound(starter, active)
    }

    /** Manche suivante : le perdant de la manche qui vient de finir commence ; [active] : les joueurs de la manche. */
    fun nextRound(active: List<Int>) {
        if (phase != Phase421.DONE) return
        // Manche nulle : on la rejoue, avec le même premier joueur.
        val result = result()
        startRound(if (result?.draw == true) order.first() else result?.loser ?: starter, active)
    }

    private fun startRound(first: Int, active: List<Int>) {
        val ring = active.filter { it in 0 until playerCount }.distinct().sorted()
        if (ring.size < 2) return
        val start = ring.indexOf(first).coerceAtLeast(0)
        order = ring.drop(start) + ring.take(start)
        starter = order.first()
        position = 0
        limit = maxRolls
        hands = List(playerCount) { emptyList() }
        rolls = List(playerCount) { 0 }
        tokens = 0
        tokensFromPot = true
        turn.reset()
        turn.rollLimit = limit
        phase = Phase421.PLAY
    }

    // ---------- Une manche ----------

    /**
     * Le joueur qui joue garde son jeu. Si c'est le premier de la manche, son nombre de lancers devient la limite
     * des suivants. Renvoie vrai si c'était le dernier joueur : la manche est terminée.
     */
    fun keepHand(): Boolean {
        if (!canKeep) return false
        val player = currentPlayer
        val newHands = hands.toMutableList()
        newHands[player] = turn.values.toList()
        hands = newHands
        val newRolls = rolls.toMutableList()
        newRolls[player] = turn.rollsUsed
        rolls = newRolls
        if (position == 0) limit = turn.rollsUsed
        position += 1
        turn.reset()
        turn.rollLimit = limit
        if (position >= order.size) phase = Phase421.DONE
        return phase == Phase421.DONE
    }

    /**
     * Le résultat de la manche, une fois que tous les joueurs ont gardé leur jeu. À jeux égaux, celui qui a fait
     * le moins de lancers est meilleur ; à lancers égaux, celui qui a joué le premier. Si le meilleur et le moins
     * bon jeu sont à égalité parfaite (même force, même nombre de lancers), la manche est nulle ([Result421.draw]).
     */
    fun result(): Result421? {
        if (order.isEmpty() || order.any { hands.getOrNull(it).isNullOrEmpty() }) return null
        val ranked = order.withIndex().sortedWith(
            compareByDescending<IndexedValue<Int>> { Dice421.strength(hands[it.value]) }
                .thenBy { rolls[it.value] }
                .thenBy { it.index }
        )
        val winner = ranked.first().value
        val loser = ranked.last().value
        val draw = Dice421.strength(hands[winner]) == Dice421.strength(hands[loser]) && rolls[winner] == rolls[loser]
        return Result421(winner = winner, loser = loser, points = Dice421.combo(hands[winner]).points, draw = draw)
    }

    /** Retient les jetons déplacés ([fromPot] : pris au milieu), pour les afficher sur tous les téléphones. */
    fun recordTokens(count: Int, fromPot: Boolean) {
        tokens = count
        tokensFromPot = fromPot
    }

    /**
     * Rejoue la manche qui vient de finir (après avoir rendu ses jetons) : mêmes joueurs, même premier joueur,
     * jeux effacés.
     */
    fun replayRound() {
        if (phase != Phase421.DONE) return
        startRound(order.first(), order)
    }

    // ---------- Sauvegarde et partage ----------

    private fun resetAll() {
        phase = Phase421.DRAW
        opening = false
        starter = -1
        order = emptyList()
        position = 0
        limit = maxRolls
        hands = List(playerCount) { emptyList() }
        rolls = List(playerCount) { 0 }
        tokens = 0
        tokensFromPot = true
        turn.reset()
        turn.rollLimit = limit
    }

    /** État sous forme de liste d'entiers, pour survivre à une rotation de l'écran et être partagé (voir [restoreFrom]). */
    fun snapshot(): List<Int> = buildList {
        // La manche de départ est codée dans la phase (+10), pour garder un format simple.
        add(phase.ordinal + if (opening) 10 else 0)
        add(playerCount)
        add(starter)
        add(position)
        add(limit)
        add(tokens)
        add(if (tokensFromPot) 1 else 0)
        add(order.size)
        addAll(order)
        addAll(rolls)
        hands.forEach { hand ->
            add(hand.size)
            addAll(hand)
        }
        addAll(turn.snapshot())
    }

    /** Rétablit l'état produit par [snapshot] ; une liste invalide remet la partie au tirage. */
    fun restoreFrom(data: List<Int>) {
        resetAll()
        try {
            var cursor = 0
            fun next(): Int = data[cursor++]
            // Une taille lue dans le message ne peut pas dépasser ce que la manche peut contenir.
            fun size(max: Int): Int = next().also { require(it in 0..max) }
            fun players(): List<Int> = List(size(playerCount)) { next() }.also { list ->
                require(list.all { it in 0 until playerCount })
            }

            val rawPhase = next()
            require(rawPhase >= 0)
            val newOpening = rawPhase >= 10
            val newPhase = Phase421.values()[rawPhase % 10]
            require(next() == playerCount)
            val newStarter = next()
            val newPosition = next()
            val newLimit = next()
            val newTokens = next()
            val newFromPot = next() == 1
            val newOrder = players()
            val newRolls = List(playerCount) { next() }
            val newHands = List(playerCount) { List(size(3)) { next() } }
            require(newStarter in -1 until playerCount && newLimit in 1..maxRolls && newPosition in 0..newOrder.size)
            require(newRolls.all { it in 0..maxRolls })
            require(newHands.all { it.isEmpty() || (it.size == 3 && it.all { die -> die in 1..6 }) })

            phase = newPhase
            starter = newStarter
            position = newPosition
            limit = newLimit
            tokens = newTokens
            tokensFromPot = newFromPot
            opening = newOpening
            order = newOrder
            rolls = newRolls
            hands = newHands
            turn.restoreFrom(data.drop(cursor))
            turn.rollLimit = limit
        } catch (e: Exception) {
            resetAll()
        }
    }
}
