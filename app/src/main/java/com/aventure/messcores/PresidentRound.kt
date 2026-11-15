package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant du Président parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val PRESIDENT_ID = "builtin_president"

/** Les places d'un classement de manche, de la meilleure à la pire. */
internal enum class PresidentRole { PRESIDENT, VICE_PRESIDENT, NEUTRAL, VICE_TROU, TROU }

/** Ce que répond le moteur à une tentative de pose ([PresidentRound.check]). */
internal enum class PlayCheck { OK, NOT_YOUR_TURN, EMPTY, NOT_IN_HAND, NOT_A_SET, WRONG_COUNT, TOO_WEAK }

/** Où en est la manche : les échanges de cartes du début, le jeu, ou la fin (classement). */
internal enum class PresidentPhase { EXCHANGE, PLAY, OVER }

/**
 * Un échange de cartes en début de manche entre [high] (le mieux classé de la manche d'avant) et [low] : [toHigh] (les [size]
 * meilleures cartes de [low]) est donné d'office ; [toLow], que [high] choisit dans sa main, reste null tant qu'il ne l'a pas fait.
 */
internal data class PresidentExchange(val high: Int, val low: Int, val size: Int, val toHigh: List<Int>, val toLow: List<Int>?)

/** Un échange tel qu'un joueur le voit : [toLow] n'est connu que des deux joueurs concernés (vide pour les autres) ; [done] : le choix est fait. */
internal data class ExchangeView(
    val high: Int,
    val low: Int,
    val size: Int,
    val toHigh: List<Int>,
    val toLow: List<Int>,
    val done: Boolean
)

/**
 * La table vue par un joueur : tout ce qui est public, plus sa propre main ([hand]) et, pendant les échanges, le nombre de cartes
 * qu'il doit rendre ([giveCount]). C'est ce que l'organisateur envoie à chaque invité : jamais la main des autres.
 */
internal data class PresidentTable(
    val playerCount: Int,
    val roundNumber: Int,
    val phase: PresidentPhase,
    val current: Int,
    val top: List<Int>,
    val topPlayer: Int,
    val skipped: Int,
    val handSizes: List<Int>,
    val order: List<Int>,
    val points: List<Int>,
    val settled: Boolean,
    val started: Boolean,
    val exchanges: List<ExchangeView>,
    val hand: List<Int>,
    val giveCount: Int
) {
    /** La place (0 = premier sorti) du joueur [player], ou -1 s'il n'est pas encore sorti. */
    fun placeOf(player: Int): Int = order.indexOf(player)

    /** Le rôle du joueur [player] à la fin de la manche ; null tant qu'il n'est pas classé. */
    fun roleOf(player: Int): PresidentRole? = placeOf(player).takeIf { it >= 0 }?.let { PresidentScoring.role(it, playerCount) }
}

/**
 * Règles du Président : voir la fiche de règles du jeu.
 *
 * Les cartes sont celles de [FrenchCards] (0 à 51). La plus forte est le 2, puis l'as, le roi… jusqu'au 3.
 */
internal object PresidentScoring {

    /** Nombre maximal de cartes posées d'un coup (les quatre cartes d'une même valeur). */
    const val MAX_SET = 4

    /** Force de la carte [card] : de 3 (le 3) à 15 (le 2) ; l'as vaut 14. */
    fun strength(card: Int): Int = when (val rank = FrenchCards.rank(card)) {
        2 -> 15
        1 -> 14
        else -> rank
    }

    /** Clé de tri d'une main : par force, puis par couleur. */
    fun sortKey(card: Int): Int = strength(card) * FrenchCards.SUITS + FrenchCards.suit(card)

    /** Vrai si [cards] est une pose possible : 1 à 4 cartes de même valeur. */
    fun isSet(cards: List<Int>): Boolean =
        cards.size in 1..MAX_SET && cards.map { FrenchCards.rank(it) }.distinct().size == 1

    /**
     * Dit si le joueur qui a [hand] peut poser [cards] sur le pli [top] (vide : il l'ouvre). Sert au moteur et aux téléphones
     * invités, qui préviennent leur joueur avant d'envoyer sa pose.
     */
    fun checkCards(hand: List<Int>, top: List<Int>, cards: List<Int>): PlayCheck {
        if (cards.isEmpty()) return PlayCheck.EMPTY
        if (cards.distinct().size != cards.size || !hand.containsAll(cards)) return PlayCheck.NOT_IN_HAND
        if (!isSet(cards)) return PlayCheck.NOT_A_SET
        if (top.isNotEmpty()) {
            if (cards.size != top.size) return PlayCheck.WRONG_COUNT
            if (strength(cards[0]) < strength(top[0])) return PlayCheck.TOO_WEAK
        }
        return PlayCheck.OK
    }

    /** Le rôle de la place [place] (0 = premier sorti) à [playerCount] joueurs. Le vice-président et le vice-trou n'existent qu'à 4 joueurs ou plus. */
    fun role(place: Int, playerCount: Int): PresidentRole = when {
        place <= 0 -> PresidentRole.PRESIDENT
        place >= playerCount - 1 -> PresidentRole.TROU
        playerCount >= 4 && place == 1 -> PresidentRole.VICE_PRESIDENT
        playerCount >= 4 && place == playerCount - 2 -> PresidentRole.VICE_TROU
        else -> PresidentRole.NEUTRAL
    }

    /** Points d'un rôle : Président +2, vice-président +1, neutre 0, vice-trou −1, Trou −2. */
    fun points(role: PresidentRole): Int = when (role) {
        PresidentRole.PRESIDENT -> 2
        PresidentRole.VICE_PRESIDENT -> 1
        PresidentRole.NEUTRAL -> 0
        PresidentRole.VICE_TROU -> -1
        PresidentRole.TROU -> -2
    }
}

/**
 * Une partie de Président, jouée tour par tour sur un seul téléphone que l'on se passe, ou chacun sur son téléphone (voir
 * [PresidentOnline] : l'organisateur tient cette partie et n'envoie à chaque joueur que [tableFor] sa place).
 *
 * - L'appli distribue tout le paquet de 52 cartes (le reste de la division va aux premiers joueurs). À partir de la
 *   deuxième manche ont lieu les échanges ([PresidentPhase.EXCHANGE]) : le Trou donne d'office ses 2 meilleures cartes au
 *   Président, qui **choisit** 2 cartes de sa main à lui rendre ([giveBack]) ; le vice-trou et le vice-président font de même avec
 *   1 carte (à 4 joueurs ou plus). Le Trou commence la manche. À la première manche (ou pour une partie reprise), commence le
 *   joueur qui a le 3 de trèfle, sauf si [startPlayer] est donné.
 * - Celui qui ouvre le pli pose 1 à 4 cartes de même valeur ([PresidentScoring.isSet]). Les suivants posent autant de cartes,
 *   de valeur égale ou supérieure ([check]), ou passent ([pass]). Poser une valeur égale fait sauter le joueur suivant.
 * - Le pli est fini quand tous les autres joueurs encore en jeu ont passé (ou ont été sautés) : celui qui a posé en dernier
 *   rouvre le pli (le joueur suivant s'il n'a plus de cartes).
 * - Qui n'a plus de carte est sorti : l'ordre de sortie donne la place de chacun ; le dernier joueur est le Trou.
 * - [settle] inscrit les points de la manche (voir [PresidentScoring.points]) dans une ligne libre du tableau des scores ;
 *   [nextRound] distribue la manche suivante.
 *
 * Sur un seul téléphone, le joueur dont c'est le tour montre sa main ([reveal]) avant de jouer. Chacun sur son téléphone,
 * l'organisateur applique les actions d'un invité avec [playFor], [passFor] et [giveBackFor], qui vérifient la place.
 *
 * [shuffle] mélange une liste de cartes (remplaçable dans les tests).
 */
internal class PresidentRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val startPlayer: Int? = null,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    /** Le joueur qui joue (ou qui choisit ses cartes à rendre pendant les échanges). */
    var current by mutableIntStateOf(0)
        private set

    /** Les mains de chaque joueur (même ordre que les joueurs). */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Les cartes du pli en cours (la dernière pose) ; vide quand le pli est ouvert à nouveau. */
    var top by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Le joueur qui a fait la dernière pose du pli ; -1 quand le pli est vide. */
    var topPlayer by mutableIntStateOf(-1)
        private set

    /** Le joueur sauté par la dernière pose (valeur égale) ; -1 sinon. */
    var skipped by mutableIntStateOf(-1)
        private set

    /** Vrai quand le joueur dont c'est le tour a demandé à voir sa main (sur un seul téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Les échanges, les cartes, ou la fin de la manche. */
    var phase by mutableStateOf(PresidentPhase.PLAY)
        private set

    /** Vrai quand la manche est finie. */
    val roundOver: Boolean get() = phase == PresidentPhase.OVER

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Les joueurs dans l'ordre où ils ont posé leur dernière carte (complété par le dernier à la fin de la manche). */
    var order by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les échanges de cartes de cette manche (vide à la première), avec le choix de ceux qui rendent des cartes. */
    var exchanges by mutableStateOf<List<PresidentExchange>>(emptyList())
        private set

    /** Le joueur qui ouvre le premier pli de la manche. */
    private var leader = 0

    /** Joueurs qui ont passé (ou été sautés) depuis la dernière pose. */
    private var passes = 0

    /** Coups joués (poses et passes) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal(null)
    }

    // ---------- Lecture de la table ----------

    /** La main du joueur qui joue. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Vrai quand le pli est vide : le joueur qui joue l'ouvre et ne peut pas passer. */
    val leading: Boolean get() = top.isEmpty()

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** L'échange dont le Président (ou le vice) doit encore choisir les cartes à rendre ; null si aucun. */
    val pendingExchange: PresidentExchange? get() = exchanges.firstOrNull { it.toLow == null }

    /** La place (0 = premier sorti) du joueur [player], ou -1 s'il n'est pas encore sorti. */
    fun placeOf(player: Int): Int = order.indexOf(player)

    /** Le rôle du joueur [player] à la fin de la manche ; null tant qu'il n'est pas classé. */
    fun roleOf(player: Int): PresidentRole? =
        placeOf(player).takeIf { it >= 0 }?.let { PresidentScoring.role(it, playerCount) }

    /** Points de chacun pour la manche (zéro partout tant qu'elle n'est pas finie). */
    fun roundPoints(): List<Int> =
        List(playerCount) { player -> if (roundOver) roleOf(player)?.let { PresidentScoring.points(it) } ?: 0 else 0 }

    /**
     * La table telle que la voit le joueur [viewer] : le public, sa main et ce qu'il doit rendre. [viewer] hors des joueurs
     * (-1) : un spectateur, qui ne voit aucune main. Les cartes rendues par un échange ne sont montrées qu'aux deux joueurs concernés.
     */
    fun tableFor(viewer: Int): PresidentTable = PresidentTable(
        playerCount = playerCount,
        roundNumber = roundNumber,
        phase = phase,
        current = current,
        top = top,
        topPlayer = topPlayer,
        skipped = skipped,
        handSizes = hands.map { it.size },
        order = order,
        points = roundPoints(),
        settled = settled,
        started = inProgress,
        exchanges = exchanges.map { exchange ->
            val concerned = viewer == exchange.high || viewer == exchange.low
            ExchangeView(
                high = exchange.high,
                low = exchange.low,
                size = exchange.size,
                toHigh = exchange.toHigh,
                toLow = if (concerned) exchange.toLow ?: emptyList() else emptyList(),
                done = exchange.toLow != null
            )
        },
        hand = hands.getOrElse(viewer) { emptyList() }.sortedBy { PresidentScoring.sortKey(it) },
        giveCount = if (phase == PresidentPhase.EXCHANGE && pendingExchange?.high == viewer) pendingExchange?.size ?: 0 else 0
    )

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver) revealed = true
    }

    /**
     * Le joueur qui choisit rend [cards] (autant que demandé, prises dans sa main) à celui qui lui a donné ses meilleures cartes.
     * Faux, sans rien changer, si ce n'est pas son tour de choisir. Quand tous les échanges sont faits, le jeu commence.
     */
    fun giveBack(cards: List<Int>): Boolean {
        if (!revealed || phase != PresidentPhase.EXCHANGE) return false
        val pending = pendingExchange ?: return false
        if (pending.high != current || cards.size != pending.size) return false
        if (cards.distinct().size != cards.size || !currentHand.containsAll(cards)) return false
        hands = hands.mapIndexed { index, hand ->
            when (index) {
                pending.high -> hand - cards.toSet()
                pending.low -> hand + cards
                else -> hand
            }
        }
        val given = cards.sortedBy { PresidentScoring.sortKey(it) }
        exchanges = exchanges.map { if (it.high == pending.high && it.low == pending.low) it.copy(toLow = given) else it }
        val next = pendingExchange
        if (next != null) {
            current = next.high
        } else {
            phase = PresidentPhase.PLAY
            current = leader
        }
        revealed = false
        return true
    }

    /** Dit si [cards] peut être posé maintenant par le joueur qui joue (rien n'est changé). */
    fun check(cards: List<Int>): PlayCheck {
        if (!revealed || phase != PresidentPhase.PLAY) return PlayCheck.NOT_YOUR_TURN
        return PresidentScoring.checkCards(currentHand, top, cards)
    }

    /** Pose [cards] si c'est permis ; renvoie [PlayCheck.OK], ou la raison du refus (rien n'est alors changé). */
    fun play(cards: List<Int>): PlayCheck {
        val result = check(cards)
        if (result != PlayCheck.OK) return result
        val player = current
        val equal = top.isNotEmpty() && PresidentScoring.strength(cards[0]) == PresidentScoring.strength(top[0])
        val remaining = currentHand - cards.toSet()
        hands = hands.mapIndexed { index, hand -> if (index == player) remaining else hand }
        top = cards.sortedBy { PresidentScoring.sortKey(it) }
        topPlayer = player
        passes = 0
        skipped = -1
        moves++
        if (remaining.isEmpty()) {
            order = order + player
            if (activeCount() <= 1) {
                finishRound()
                return PlayCheck.OK
            }
        }
        var next = nextActive(player)
        if (equal && next != player) {
            // Une valeur égale fait sauter le joueur suivant : c'est comme s'il passait.
            skipped = next
            passes++
            next = nextActive(next)
        }
        current = next
        if (closeTrickIfDone()) skipped = -1
        // Le même joueur qui continue (l'autre étant sauté) garde le téléphone : sa main reste visible.
        revealed = current == player
        return PlayCheck.OK
    }

    /** Le joueur qui joue passe son tour ; faux si c'est impossible (il doit ouvrir le pli). */
    fun pass(): Boolean {
        if (!revealed || phase != PresidentPhase.PLAY || top.isEmpty()) return false
        passes++
        moves++
        if (!closeTrickIfDone()) current = nextActive(current)
        revealed = false
        return true
    }

    // ---------- Chacun sur son téléphone ----------

    /** [play] pour le joueur [seat], qui doit être celui dont c'est le tour (il n'a pas besoin de « voir sa main » : il l'a sur son téléphone). */
    fun playFor(seat: Int, cards: List<Int>): PlayCheck {
        if (seat != current || phase != PresidentPhase.PLAY) return PlayCheck.NOT_YOUR_TURN
        revealed = true
        return play(cards).also { if (it != PlayCheck.OK) revealed = false }
    }

    /** [pass] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun passFor(seat: Int): Boolean {
        if (seat != current || phase != PresidentPhase.PLAY) return false
        revealed = true
        return pass().also { if (!it) revealed = false }
    }

    /** [giveBack] pour le joueur [seat], qui doit être celui qui choisit. */
    fun giveBackFor(seat: Int, cards: List<Int>): Boolean {
        if (seat != current || phase != PresidentPhase.EXCHANGE) return false
        revealed = true
        return giveBack(cards).also { if (!it) revealed = false }
    }

    /** Les joueurs qui ont encore des cartes. */
    private fun activeCount(): Int = hands.count { it.isNotEmpty() }

    /** Le prochain joueur après [from] qui a encore des cartes. */
    private fun nextActive(from: Int): Int {
        for (step in 1..playerCount) {
            val index = (from + step) % playerCount
            if (hands[index].isNotEmpty()) return index
        }
        return from
    }

    /** Si tous les autres joueurs en jeu ont passé, ferme le pli et donne la main à celui qui l'ouvre ; renvoie vrai dans ce cas. */
    private fun closeTrickIfDone(): Boolean {
        if (topPlayer < 0) return false
        val stillIn = hands[topPlayer].isNotEmpty()
        val others = activeCount() - if (stillIn) 1 else 0
        if (passes < others) return false
        current = if (stillIn) topPlayer else nextActive(topPlayer)
        top = emptyList()
        topPlayer = -1
        passes = 0
        return true
    }

    private fun finishRound() {
        val last = hands.indices.firstOrNull { hands[it].isNotEmpty() }
        if (last != null && last !in order) order = order + last
        phase = PresidentPhase.OVER
        revealed = false
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

    /** Distribue la manche suivante, avec les échanges de cartes ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        val previous = order
        roundNumber++
        deal(previous)
        return true
    }

    /**
     * Mélange et distribue tout le paquet ; [previousOrder] (l'ordre de sortie de la manche d'avant) déclenche les échanges :
     * le Trou (et le vice-trou) donne d'office ses meilleures cartes, le Président (et le vice) choisira ce qu'il rend.
     */
    private fun deal(previousOrder: List<Int>?) {
        val count = playerCount.coerceAtLeast(1)
        val deck = (0 until FrenchCards.DECK).toMutableList()
        shuffle(deck)
        // Le reste de la division va à d'autres joueurs d'une manche à l'autre.
        val offset = (roundNumber - 1) % count
        val dealt = List(count) { mutableListOf<Int>() }
        deck.forEachIndexed { index, card -> dealt[(index + offset) % count].add(card) }
        var newHands = dealt.map { it.toList() }
        val pending = mutableListOf<PresidentExchange>()
        if (previousOrder != null && previousOrder.size == count && count >= 2) {
            val pairs = buildList {
                add(0 to 2)
                if (count >= 4) add(1 to 1)
            }
            for ((place, size) in pairs) {
                val high = previousOrder[place]
                val low = previousOrder[count - 1 - place]
                if (high == low) continue
                val best = newHands[low].sortedWith(compareByDescending<Int> { PresidentScoring.strength(it) }.thenByDescending { it }).take(size)
                newHands = newHands.mapIndexed { index, hand ->
                    when (index) {
                        high -> hand + best
                        low -> hand - best.toSet()
                        else -> hand
                    }
                }
                pending.add(PresidentExchange(high = high, low = low, size = size, toHigh = best, toLow = null))
            }
        }
        hands = newHands
        exchanges = pending
        val club3 = FrenchCards.card(FrenchCards.CLUB, 3)
        leader = when {
            previousOrder != null && previousOrder.isNotEmpty() -> previousOrder.last()
            startPlayer != null -> startPlayer.coerceIn(0, count - 1)
            else -> newHands.indexOfFirst { club3 in it }.coerceAtLeast(0)
        }
        phase = if (pending.isEmpty()) PresidentPhase.PLAY else PresidentPhase.EXCHANGE
        current = if (pending.isEmpty()) leader else pending.first().high
        top = emptyList()
        topPlayer = -1
        skipped = -1
        revealed = false
        settled = false
        order = emptyList()
        passes = 0
        moves = 0
    }

    /** Installe une table précise (mains, pli en cours, joueur) : sert aux tests. */
    internal fun load(
        hands: List<List<Int>>,
        current: Int = 0,
        top: List<Int> = emptyList(),
        topPlayer: Int = if (top.isEmpty()) -1 else 0
    ) {
        require(hands.size == playerCount && current in 0 until playerCount)
        this.hands = hands
        this.current = current
        this.top = top
        this.topPlayer = topPlayer
        leader = current
        skipped = -1
        exchanges = emptyList()
        order = emptyList()
        revealed = false
        phase = PresidentPhase.PLAY
        settled = false
        passes = 0
        moves = 0
    }
}
