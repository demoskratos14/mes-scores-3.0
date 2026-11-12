package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant de Flip 7 parmi les jeux prédéfinis (rubrique « Spéciaux »). */
internal const val FLIP7_ID = "builtin_flip7"

/**
 * Les cartes de Flip 7. Une carte est un numéro de type : deux cartes du même type se ressemblent (le paquet en contient
 * plusieurs exemplaires).
 *
 * - 0 à 12 : les cartes Nombre (il y en a autant d'exemplaires que leur valeur, un seul pour le 0) ;
 * - [FREEZE], [FLIP_THREE], [SECOND_CHANCE] : les cartes Action (3 de chaque) ;
 * - [PLUS_2] à [PLUS_10] et [TIMES_2] : les cartes Bonus (une de chaque).
 *
 * Le paquet a donc 79 + 9 + 6 = 94 cartes.
 */
internal object Flip7Cards {
    const val FREEZE = 13
    const val FLIP_THREE = 14
    const val SECOND_CHANCE = 15
    const val PLUS_2 = 16
    const val PLUS_4 = 17
    const val PLUS_6 = 18
    const val PLUS_8 = 19
    const val PLUS_10 = 20
    const val TIMES_2 = 21

    /** Nombre de types de cartes. */
    const val TYPES = 22

    /** Nombre de cartes Nombre différentes réunies pour faire un « Flip 7 ». */
    const val FLIP7_COUNT = 7

    /** Points gagnés en plus par celui qui fait un « Flip 7 ». */
    const val FLIP7_BONUS = 15

    /** Cartes qu'on doit piocher d'un coup avec « Pioche trois ». */
    const val FLIP_THREE_DRAWS = 3

    fun isNumber(card: Int): Boolean = card in 0..12

    fun isBonus(card: Int): Boolean = card in PLUS_2..PLUS_10

    /** Points d'une carte « +N » : 2, 4, 6, 8 ou 10. */
    fun bonusValue(card: Int): Int = if (isBonus(card)) (card - PLUS_2 + 1) * 2 else 0

    /** Le paquet complet (94 cartes), dans l'ordre des types. */
    fun deck(): MutableList<Int> {
        val cards = mutableListOf<Int>()
        cards += 0
        for (value in 1..12) repeat(value) { cards += value }
        repeat(3) { cards += FREEZE; cards += FLIP_THREE; cards += SECOND_CHANCE }
        for (bonus in PLUS_2..TIMES_2) cards += bonus
        return cards
    }

    /**
     * Points d'un joueur qui n'a pas sauté : la somme de ses cartes Nombre, doublée par « ×2 », plus les cartes « +N », plus
     * [FLIP7_BONUS] s'il a fait un Flip 7 ([flip7]).
     */
    fun score(numbers: List<Int>, extras: List<Int>, flip7: Boolean): Int {
        var total = numbers.sum()
        if (TIMES_2 in extras) total *= 2
        total += extras.sumOf { bonusValue(it) }
        if (flip7) total += FLIP7_BONUS
        return total
    }
}

/** Où en est un joueur dans la manche. */
internal enum class Flip7Status { ACTIVE, STAYED, FROZEN, BUSTED }

/** Ce qu'une carte Action en attente demande de choisir. */
internal enum class Flip7Choice { FREEZE, FLIP_THREE, GIVE_SECOND_CHANCE }

/**
 * Une carte Action posée en attendant que son joueur choisisse sur qui elle s'applique : [source] est celui qui l'a piochée,
 * [targets] les joueurs qui peuvent la recevoir.
 */
internal data class Flip7Pending(val choice: Flip7Choice, val card: Int, val source: Int, val targets: List<Int>)

/** Ce qui vient de se passer sur la table (annoncé à voix haute par TalkBack et écrit sous la pioche). */
internal enum class Flip7EventKind { DRAW, BUST, SAVED, FLIP7, STAY, FREEZE, FLIP_THREE, SECOND_CHANCE_GIVEN, SECOND_CHANCE_LOST, DECK_EMPTY }

/** [player] : le joueur concerné ; [card] : la carte piochée (DRAW) ; [other] : l'autre joueur (cible d'un gel…), -1 sinon. */
internal data class Flip7Event(val kind: Flip7EventKind, val player: Int, val card: Int = -1, val other: Int = -1)

/**
 * Une partie de Flip 7 jouée sur un seul téléphone (3 joueurs ou plus). Aucune carte n'est cachée : tout le monde voit la
 * table, on se passe simplement le tour.
 *
 * - Au début de la manche, chacun reçoit une carte (le joueur après le donneur commence). Une carte Action reçue se joue tout
 *   de suite.
 * - À son tour, le joueur pioche ([hit]) ou s'arrête ([stay]) et marque ses points.
 * - Piocher un nombre qu'on a déjà fait « sauter » ([Flip7Status.BUSTED], 0 point), sauf avec une Seconde chance, qu'on
 *   défausse avec le doublon.
 * - « Gel » : le joueur choisi marque ses points et sort de la manche. « Pioche trois » : le joueur choisi doit piocher trois
 *   cartes (les Gel et Pioche trois qu'il y trouve ne se jouent qu'après la troisième). « Seconde chance » : on n'en garde
 *   qu'une, une deuxième se donne à un autre joueur qui n'en a pas (sinon elle est défaussée).
 * - Sept nombres différents devant soi : « Flip 7 », la manche s'arrête aussitôt et ce joueur gagne 15 points de plus.
 * - [settle] inscrit les points de la manche dans une ligne du tableau des scores (le total de chacun, la fin de partie à
 *   200 points et le journal viennent de ce tableau) ; [nextRound] relance une manche, avec le même paquet : les cartes
 *   jouées sont défaussées et remélangées quand la pioche est vide.
 *
 * [dealer] : le donneur. [startRound] : numéro de la manche. [shuffle] mélange une liste de cartes (remplaçable dans les tests).
 */
internal class Flip7Round(
    val playerCount: Int,
    dealer: Int = 0,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    private var dealerIndex = dealer.coerceIn(0, (playerCount - 1).coerceAtLeast(0))

    /** Les cartes Nombre devant chaque joueur, dans l'ordre où elles sont arrivées (un doublon qui fait sauter y reste). */
    var numbers by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Les autres cartes devant chaque joueur : bonus, Gel et Pioche trois joués, Seconde chance gardée. */
    var extras by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    var status by mutableStateOf<List<Flip7Status>>(emptyList())
        private set

    /** La pioche : la prochaine carte est la première. */
    var stock by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les cartes défaussées : elles refont la pioche quand elle est vide. */
    var discard by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Le joueur dont c'est le tour de piocher ou de s'arrêter ; -1 quand la manche est finie. */
    var current by mutableIntStateOf(-1)
        private set

    /** La carte Action en attente d'un choix, null quand il n'y en a pas (voir [chooseTarget]). */
    var pending by mutableStateOf<Flip7Pending?>(null)
        private set

    /** Dernier événement de la table. */
    var lastEvent by mutableStateOf<Flip7Event?>(null)
        private set

    /** Vrai quand la manche est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Le joueur qui a fait un Flip 7 ; -1 s'il n'y en a pas eu dans cette manche. */
    var flip7Player by mutableIntStateOf(-1)
        private set

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Coups joués (pioches, arrêts) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    // ---------- File des pioches à faire ----------

    /** Les cartes Action piochées pendant un « Pioche trois » : elles se jouent après la troisième carte. */
    private class Frame {
        val deferred = mutableListOf<Int>()
    }

    private sealed interface Task
    private data class DrawTask(val player: Int, val frame: Frame?) : Task
    private data class ResolveTask(val player: Int, val frame: Frame) : Task

    private val tasks = ArrayDeque<Task>()

    /** Vrai tant que la première carte de chaque joueur est distribuée. */
    private var dealing = false

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** Le joueur qui commence : celui qui suit le donneur. */
    val firstPlayer: Int get() = (dealerIndex + 1) % playerCount.coerceAtLeast(1)

    /** Points qu'un joueur marquerait maintenant : 0 s'il a sauté. */
    fun pointsOf(player: Int): Int {
        if (status.getOrNull(player) == Flip7Status.BUSTED) return 0
        return Flip7Cards.score(numbers.getOrElse(player) { emptyList() }, extras.getOrElse(player) { emptyList() }, flip7 = player == flip7Player)
    }

    /** Points de chacun pour la manche (0 pour celui qui a sauté). */
    fun roundPoints(): List<Int> = List(playerCount) { pointsOf(it) }

    /** Vrai si le joueur dont c'est le tour peut piocher ou s'arrêter. */
    val canAct: Boolean get() = !roundOver && pending == null && current in 0 until playerCount && status[current] == Flip7Status.ACTIVE

    /** Celui qui a le plus de points dans cette manche (le premier à égalité) ; -1 tant qu'elle n'est pas finie. */
    val winner: Int
        get() {
            if (!roundOver) return -1
            val points = roundPoints()
            val best = points.maxOrNull() ?: 0
            return if (best <= 0) -1 else points.indexOf(best)
        }

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour pioche une carte ; faux si ce n'est pas permis. */
    fun hit(): Boolean {
        if (!canAct) return false
        moves++
        tasks.addFirst(DrawTask(current, null))
        advance()
        return true
    }

    /** Le joueur dont c'est le tour s'arrête et marque ses points ; faux si ce n'est pas permis. */
    fun stay(): Boolean {
        if (!canAct) return false
        moves++
        val player = current
        status = status.mapIndexed { index, value -> if (index == player) Flip7Status.STAYED else value }
        lastEvent = Flip7Event(Flip7EventKind.STAY, player)
        passTurn()
        return true
    }

    /** Applique la carte Action en attente ([pending]) au joueur [target], qui doit en faire partie ; faux sinon. */
    fun chooseTarget(target: Int): Boolean {
        val choice = pending ?: return false
        if (roundOver || target !in choice.targets) return false
        pending = null
        when (choice.choice) {
            Flip7Choice.GIVE_SECOND_CHANCE -> {
                addExtra(target, choice.card)
                lastEvent = Flip7Event(Flip7EventKind.SECOND_CHANCE_GIVEN, choice.source, choice.card, target)
            }
            Flip7Choice.FREEZE -> {
                addExtra(target, choice.card)
                status = status.mapIndexed { index, value -> if (index == target) Flip7Status.FROZEN else value }
                lastEvent = Flip7Event(Flip7EventKind.FREEZE, choice.source, choice.card, target)
                dropPendingDraws(target)
            }
            Flip7Choice.FLIP_THREE -> {
                addExtra(target, choice.card)
                lastEvent = Flip7Event(Flip7EventKind.FLIP_THREE, choice.source, choice.card, target)
                val frame = Frame()
                tasks.addFirst(ResolveTask(target, frame))
                repeat(Flip7Cards.FLIP_THREE_DRAWS) { tasks.addFirst(DrawTask(target, frame)) }
            }
        }
        advance()
        return true
    }

    // ---------- Déroulement ----------

    /** Fait les pioches en attente jusqu'à ce qu'un choix soit nécessaire, que la manche s'arrête ou que le tour soit fini. */
    private fun advance() {
        while (pending == null && !roundOver) {
            val task = tasks.removeFirstOrNull() ?: break
            when (task) {
                is DrawTask -> draw(task)
                is ResolveTask -> resolve(task)
            }
        }
        if (!roundOver && pending == null && tasks.isEmpty()) passTurn()
    }

    /** Un joueur pioche une carte et la joue ; sans effet s'il n'est plus dans la manche. */
    private fun draw(task: DrawTask) {
        val player = task.player
        if (status[player] != Flip7Status.ACTIVE) return
        val card = takeCard()
        if (card == null) {
            lastEvent = Flip7Event(Flip7EventKind.DECK_EMPTY, player)
            endRound()
            return
        }
        lastEvent = Flip7Event(Flip7EventKind.DRAW, player, card)
        when {
            Flip7Cards.isNumber(card) -> playNumber(player, card)
            Flip7Cards.isBonus(card) || card == Flip7Cards.TIMES_2 -> addExtra(player, card)
            card == Flip7Cards.SECOND_CHANCE -> playSecondChance(player, card)
            // Gel et Pioche trois : pendant un « Pioche trois », ils attendent la fin des trois cartes.
            task.frame != null -> task.frame.deferred += card
            else -> askTarget(if (card == Flip7Cards.FREEZE) Flip7Choice.FREEZE else Flip7Choice.FLIP_THREE, card, player)
        }
    }

    private fun playNumber(player: Int, card: Int) {
        if (card in numbers[player]) {
            if (Flip7Cards.SECOND_CHANCE in extras[player]) {
                // La Seconde chance sauve le joueur : elle part à la défausse avec le doublon.
                val kept = extras[player].toMutableList().also { it.remove(Flip7Cards.SECOND_CHANCE) }
                extras = extras.mapIndexed { index, list -> if (index == player) kept else list }
                discard = discard + card + Flip7Cards.SECOND_CHANCE
                lastEvent = Flip7Event(Flip7EventKind.SAVED, player, card)
            } else {
                numbers = numbers.mapIndexed { index, list -> if (index == player) list + card else list }
                status = status.mapIndexed { index, value -> if (index == player) Flip7Status.BUSTED else value }
                lastEvent = Flip7Event(Flip7EventKind.BUST, player, card)
                dropPendingDraws(player)
            }
            return
        }
        numbers = numbers.mapIndexed { index, list -> if (index == player) list + card else list }
        if (numbers[player].size >= Flip7Cards.FLIP7_COUNT) {
            flip7Player = player
            lastEvent = Flip7Event(Flip7EventKind.FLIP7, player, card)
            endRound()
        }
    }

    private fun playSecondChance(player: Int, card: Int) {
        if (Flip7Cards.SECOND_CHANCE !in extras[player]) {
            addExtra(player, card)
            return
        }
        // Déjà une Seconde chance : celle-ci se donne à un autre joueur de la manche qui n'en a pas.
        val takers = activePlayers().filter { it != player && Flip7Cards.SECOND_CHANCE !in extras[it] }
        if (takers.isEmpty()) {
            discard = discard + card
            lastEvent = Flip7Event(Flip7EventKind.SECOND_CHANCE_LOST, player, card)
        } else {
            pending = Flip7Pending(Flip7Choice.GIVE_SECOND_CHANCE, card, player, takers)
        }
    }

    private fun askTarget(choice: Flip7Choice, card: Int, source: Int) {
        pending = Flip7Pending(choice, card, source, activePlayers())
    }

    /** Après un « Pioche trois » : joue les Gel et Pioche trois piochés pendant les trois cartes, un par un. */
    private fun resolve(task: ResolveTask) {
        val frame = task.frame
        if (frame.deferred.isEmpty()) return
        if (status[task.player] != Flip7Status.ACTIVE) {
            // Le joueur a sauté, est gelé ou s'est arrêté entre-temps : ces cartes sont défaussées.
            discard = discard + frame.deferred
            frame.deferred.clear()
            return
        }
        val card = frame.deferred.removeAt(0)
        // Le reste attend : il passe après ce que la carte qu'on va jouer fait faire.
        tasks.addFirst(task)
        askTarget(if (card == Flip7Cards.FREEZE) Flip7Choice.FREEZE else Flip7Choice.FLIP_THREE, card, task.player)
    }

    /** Retire les pioches que [player] n'a plus à faire (il a sauté ou il est gelé) ; les cartes en attente vont à la défausse. */
    private fun dropPendingDraws(player: Int) {
        val kept = ArrayDeque<Task>()
        tasks.forEach { task ->
            when {
                task is DrawTask && task.frame != null && task.player == player -> Unit
                task is ResolveTask && task.player == player -> {
                    discard = discard + task.frame.deferred
                    task.frame.deferred.clear()
                }
                else -> kept += task
            }
        }
        tasks.clear()
        tasks.addAll(kept)
    }

    /** Passe la main au joueur suivant encore dans la manche, ou finit la manche s'il n'y en a plus. */
    private fun passTurn() {
        if (roundOver) return
        if (activePlayers().isEmpty()) {
            endRound()
            return
        }
        if (dealing) {
            dealing = false
            current = nextActive(firstPlayer - 1)
        } else {
            current = nextActive(current)
        }
    }

    /** Le premier joueur encore dans la manche après [from] (lui-même s'il est le seul). */
    private fun nextActive(from: Int): Int {
        val count = playerCount.coerceAtLeast(1)
        for (step in 1..count) {
            val candidate = ((from + step) % count + count) % count
            if (status[candidate] == Flip7Status.ACTIVE) return candidate
        }
        return -1
    }

    private fun activePlayers(): List<Int> = status.indices.filter { status[it] == Flip7Status.ACTIVE }

    private fun addExtra(player: Int, card: Int) {
        extras = extras.mapIndexed { index, list -> if (index == player) list + card else list }
    }

    /** La première carte de la pioche (remélangée au besoin avec la défausse) ; null s'il n'y en a plus du tout. */
    private fun takeCard(): Int? {
        if (stock.isEmpty()) {
            val refill = discard.toMutableList()
            if (refill.isEmpty()) return null
            shuffle(refill)
            stock = refill
            discard = emptyList()
        }
        val card = stock.first()
        stock = stock.drop(1)
        return card
    }

    private fun endRound() {
        // Les Gel et Pioche trois restés en attente d'un « Pioche trois » vont à la défausse : aucune carte ne se perd.
        tasks.forEach { if (it is ResolveTask) { discard = discard + it.frame.deferred; it.frame.deferred.clear() } }
        tasks.clear()
        pending = null
        roundOver = true
        current = -1
    }

    // ---------- Manches et scores ----------

    /**
     * Inscrit les points de la manche finie dans la première ligne vide du tableau des scores (une seule fois). Renvoie vrai
     * si les points viennent d'être inscrits.
     */
    fun settle(viewModel: ScoreViewModel): Boolean {
        if (!roundOver || settled) return false
        if (!writeRoundPoints(viewModel, roundPoints())) return false
        settled = true
        return true
    }

    /** Relance une manche, donnée par le joueur qui suit le donneur ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        roundNumber++
        dealerIndex = (dealerIndex + 1) % playerCount.coerceAtLeast(1)
        collectCards()
        deal()
        return true
    }

    /** Les cartes de la table partent à la défausse. */
    private fun collectCards() {
        discard = discard + numbers.flatten() + extras.flatten()
    }

    /** Mélange le paquet si besoin, vide la table et donne une carte à chacun, en commençant par celui qui suit le donneur. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        if (stock.isEmpty() && discard.isEmpty()) {
            val deck = Flip7Cards.deck()
            shuffle(deck)
            stock = deck
        }
        clearTable()
        dealing = true
        for (step in 0 until count) tasks.addLast(DrawTask((firstPlayer + step) % count, null))
        current = firstPlayer
        advance()
        // Un tour de donne sans aucune carte à choisir compte pour rien : la manche n'est « commencée » qu'au premier coup.
        moves = 0
    }

    private fun clearTable() {
        val count = playerCount.coerceAtLeast(1)
        tasks.clear()
        numbers = List(count) { emptyList() }
        extras = List(count) { emptyList() }
        status = List(count) { Flip7Status.ACTIVE }
        pending = null
        lastEvent = null
        roundOver = false
        flip7Player = -1
        settled = false
        moves = 0
        current = -1
    }

    /**
     * Installe une table précise : sert aux tests. [stock] est la pioche (la première carte sort d'abord), c'est à [current]
     * de jouer, les joueurs n'ont encore aucune carte devant eux.
     */
    internal fun load(stock: List<Int>, current: Int = 0, discard: List<Int> = emptyList()) {
        require(current in 0 until playerCount)
        clearTable()
        this.stock = stock
        this.discard = discard
        dealing = false
        this.current = current
    }
}
