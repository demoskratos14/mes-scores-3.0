package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Où en est la partie de 421 : tirage du premier joueur, manche en cours, ou manche terminée. */
internal enum class Phase421 { DRAW, PLAY, DONE }

/** Résultat d'une manche : celui qui a le meilleur jeu, celui qui a le moins bon et la valeur du meilleur jeu. */
internal data class Result421(val winner: Int, val loser: Int, val points: Int)

/**
 * Une manche de 421 jouée tour par tour, de la même façon pour tous les téléphones.
 *
 * - Au départ, on tire au sort le premier joueur : chacun lance un dé à son tour, le plus fort commence (les
 *   ex æquo relancent entre eux). On peut aussi le choisir directement.
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

    /** Tirage : le dé de chaque joueur (0 : pas encore lancé). */
    var drawValues by mutableStateOf(List(playerCount) { 0 })
        private set

    /** Tirage : les joueurs encore en lice (tous au début, puis les ex æquo qui relancent). */
    var drawPool by mutableStateOf((0 until playerCount).toList())
        private set

    /** Tirage : vrai quand un ex æquo vient de faire relancer des joueurs. */
    var drawTie by mutableStateOf(false)
        private set

    /** Le premier joueur de la manche ; -1 tant que le tirage n'a pas désigné personne. */
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

    /** Le prochain joueur à lancer son dé au tirage, ou null quand le tirage est fait. */
    val drawNext: Int? get() = if (starter >= 0) null else drawPool.firstOrNull { drawValues.getOrElse(it) { 0 } == 0 }

    /** Le joueur à qui c'est de jouer : celui du tirage, de la manche, ou le perdant d'une manche terminée. */
    val currentPlayer: Int
        get() = when (phase) {
            Phase421.DRAW -> drawNext ?: starter.coerceAtLeast(0)
            Phase421.PLAY -> order.getOrElse(position) { 0 }
            Phase421.DONE -> result()?.loser ?: 0
        }

    /** Vrai pendant le tour du premier joueur de la manche : c'est lui qui fixe le nombre de lancers. */
    val isStarterTurn: Boolean get() = phase == Phase421.PLAY && position == 0

    /** Vrai quand le joueur qui joue a lancé les dés au moins une fois, donc peut garder son jeu. */
    val canKeep: Boolean get() = phase == Phase421.PLAY && turn.hasRolled

    // ---------- Tirage du premier joueur ----------

    /** Le joueur dont c'est le tour lance son dé ; quand tous ont lancé, le plus fort commence, les ex æquo relancent. */
    fun drawRoll() {
        if (phase != Phase421.DRAW) return
        val player = drawNext ?: return
        drawTie = false
        val values = drawValues.toMutableList()
        values[player] = rollDie()
        if (drawPool.any { values[it] == 0 }) {
            drawValues = values
            return
        }
        val best = drawPool.maxOf { values[it] }
        val leaders = drawPool.filter { values[it] == best }
        if (leaders.size == 1) {
            drawValues = values
            starter = leaders.first()
        } else {
            // Égalité en tête : seuls les ex æquo relancent, leur dé est remis à zéro.
            leaders.forEach { values[it] = 0 }
            drawTie = true
            drawPool = leaders
            drawValues = values
        }
    }

    /** Choisit directement le premier joueur, sans tirage. */
    fun chooseStarter(player: Int) {
        if (phase != Phase421.DRAW || player !in 0 until playerCount) return
        drawTie = false
        starter = player
    }

    /** Commence la première manche avec le joueur désigné par le tirage (ou choisi) ; [active] : les joueurs de la manche. */
    fun begin(active: List<Int>) {
        if (phase != Phase421.DRAW || starter < 0) return
        startRound(starter, active)
    }

    /** Manche suivante : le perdant de la manche qui vient de finir commence ; [active] : les joueurs de la manche. */
    fun nextRound(active: List<Int>) {
        if (phase != Phase421.DONE) return
        startRound(result()?.loser ?: starter, active)
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
     * le moins de lancers est meilleur ; à lancers égaux, celui qui a joué le premier.
     */
    fun result(): Result421? {
        if (order.isEmpty() || order.any { hands.getOrNull(it).isNullOrEmpty() }) return null
        val ranked = order.withIndex().sortedWith(
            compareByDescending<IndexedValue<Int>> { Dice421.strength(hands[it.value]) }
                .thenBy { rolls[it.value] }
                .thenBy { it.index }
        )
        val winner = ranked.first().value
        return Result421(winner = winner, loser = ranked.last().value, points = Dice421.combo(hands[winner]).points)
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
        drawValues = List(playerCount) { 0 }
        drawPool = (0 until playerCount).toList()
        drawTie = false
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
        add(phase.ordinal)
        add(playerCount)
        add(starter)
        add(position)
        add(limit)
        add(tokens)
        add(if (tokensFromPot) 1 else 0)
        add(if (drawTie) 1 else 0)
        add(order.size)
        addAll(order)
        add(drawPool.size)
        addAll(drawPool)
        addAll(drawValues)
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

            val newPhase = Phase421.values()[next()]
            require(next() == playerCount)
            val newStarter = next()
            val newPosition = next()
            val newLimit = next()
            val newTokens = next()
            val newFromPot = next() == 1
            val newTie = next() == 1
            val newOrder = players()
            val newPool = players()
            val newDraw = List(playerCount) { next() }
            val newRolls = List(playerCount) { next() }
            val newHands = List(playerCount) { List(size(3)) { next() } }
            require(newStarter in -1 until playerCount && newLimit in 1..maxRolls && newPosition in 0..newOrder.size)
            require(newDraw.all { it in 0..6 } && newRolls.all { it in 0..maxRolls })
            require(newHands.all { it.isEmpty() || (it.size == 3 && it.all { die -> die in 1..6 }) })

            phase = newPhase
            starter = newStarter
            position = newPosition
            limit = newLimit
            tokens = newTokens
            tokensFromPot = newFromPot
            drawTie = newTie
            order = newOrder
            drawPool = newPool
            drawValues = newDraw
            rolls = newRolls
            hands = newHands
            turn.restoreFrom(data.drop(cursor))
            turn.rollLimit = limit
        } catch (e: Exception) {
            resetAll()
        }
    }
}
