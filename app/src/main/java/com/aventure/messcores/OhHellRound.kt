package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant d'Oh Hell (Oh Pétard) parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val OH_HELL_ID = "builtin_ohhell"

/** Les règles d'Oh Hell : nombre de cartes par manche, donneur, cartes jouables, gagnant d'un pli. Les cartes sont celles de [FrenchCards]. */
internal object OhHellScoring {

    /** Cartes distribuées à chacun dans la plus grosse manche : une carte du paquet reste pour l'atout (17 à 3 joueurs, 12 à 4, 10 à 5, 8 à 6, 7 à 7). */
    fun maxCards(playerCount: Int): Int = ((FrenchCards.DECK - 1) / playerCount.coerceAtLeast(1)).coerceAtLeast(1)

    /** Nombre de manches d'une partie : le nombre de cartes monte de 1 au maximum, puis redescend à 1. */
    fun totalRounds(playerCount: Int): Int = 2 * maxCards(playerCount) - 1

    /** Cartes distribuées à chacun à la manche [round] (1, 2, … maximum, … 2, 1). */
    fun cardsFor(round: Int, playerCount: Int): Int {
        val max = maxCards(playerCount)
        val index = round.coerceIn(1, totalRounds(playerCount))
        return if (index <= max) index else 2 * max - index
    }

    /** Le donneur de la manche [round] : on change de donneur à chaque manche, dans l'ordre des joueurs. */
    fun dealer(round: Int, playerCount: Int): Int = Math.floorMod(round - 1, playerCount.coerceAtLeast(1))

    /**
     * L'annonce que le dernier à annoncer n'a pas le droit de faire : celle qui ferait que le total des annonces égale le nombre de plis
     * ([cardCount]), pour que quelqu'un au moins rate la sienne. -1 s'il n'y en a pas (ce n'est pas le dernier à annoncer, ou le total des autres sort déjà de 0 à [cardCount]).
     */
    fun forbiddenBid(bids: List<Int>, cardCount: Int): Int {
        if (bids.isEmpty() || bids.count { it >= 0 } != bids.size - 1) return -1
        val forbidden = cardCount - bids.filter { it >= 0 }.sum()
        return if (forbidden in 0..cardCount) forbidden else -1
    }

    /** Vrai si [card] peut être posée par un joueur qui a [hand] en main, dans un pli [trick] (vide : il l'ouvre, avec n'importe quelle carte). */
    fun canPlay(card: Int, hand: List<Int>, trick: List<OhHellPlay>): Boolean {
        if (card !in hand) return false
        val led = trick.firstOrNull() ?: return true
        val ledSuit = FrenchCards.suit(led.card)
        // Il faut fournir la couleur demandée si on l'a ; sinon, n'importe quelle carte (un atout par exemple).
        return FrenchCards.suit(card) == ledSuit || hand.none { FrenchCards.suit(it) == ledSuit }
    }

    /** Le joueur qui gagne le pli [trick] (complet ou non) : le plus gros atout s'il y en a, sinon la plus forte carte de la couleur demandée (l'as est le plus fort). */
    fun trickWinner(trick: List<OhHellPlay>, trumpSuit: Int): Int {
        if (trick.isEmpty()) return -1
        val trumps = trick.filter { FrenchCards.suit(it.card) == trumpSuit }
        val ledSuit = FrenchCards.suit(trick.first().card)
        val pool = if (trumps.isNotEmpty()) trumps else trick.filter { FrenchCards.suit(it.card) == ledSuit }
        return pool.maxByOrNull { FrenchCards.strength(it.card) }?.player ?: trick.first().player
    }
}

/** Une carte posée dans un pli par [player]. */
internal data class OhHellPlay(val player: Int, val card: Int)

/** Où en est la manche : chacun annonce ses plis (le donneur en dernier), puis on joue les plis. */
internal enum class OhHellPhase { BIDDING, PLAYING }

/**
 * La table d'Oh Hell telle que la voit un joueur : tout le public (atout, annonces, plis), sa main, jamais la main des autres (voir
 * [OhHellRound.tableFor]). C'est ce que l'organisateur envoie à chaque invité (voir [OhHellOnline]).
 */
internal data class OhHellTable(
    val playerCount: Int,
    val roundNumber: Int,
    /** Cartes distribuées à chacun cette manche (= plis de la manche). */
    val cardCount: Int,
    val phase: OhHellPhase,
    val current: Int,
    /** La carte retournée qui désigne l'atout. */
    val trump: Int,
    /** L'annonce de chaque joueur (-1 : pas encore annoncé). */
    val bids: List<Int>,
    /** Les plis ramassés par chaque joueur depuis le début de la manche. */
    val tricksWon: List<Int>,
    val trick: List<OhHellPlay>,
    val trickWinner: Int,
    val tricksPlayed: Int,
    val roundOver: Boolean,
    val settled: Boolean,
    val started: Boolean,
    val handSizes: List<Int>,
    /** Points de chacun pour la manche, une fois la manche finie (des 0 avant). */
    val points: List<Int>,
    val hand: List<Int>
) {
    /** La couleur d'atout de la manche. */
    val trumpSuit: Int get() = FrenchCards.suit(trump)

    /** Nombre de manches de la partie. */
    val totalRounds: Int get() = OhHellScoring.totalRounds(playerCount)

    /** Le donneur de la manche. */
    val dealer: Int get() = OhHellScoring.dealer(roundNumber, playerCount)

    /** Vrai quand tous ont joué dans le pli : on le montre à tous avant que le gagnant ne le ramasse. */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Vrai quand la dernière manche de la partie est inscrite : la partie est finie. */
    val finished: Boolean get() = roundNumber > totalRounds || (settled && roundNumber >= totalRounds)

    /** Total des annonces déjà faites. */
    val bidsTotal: Int get() = bids.filter { it >= 0 }.sum()

    /** L'annonce interdite au joueur qui annonce maintenant (-1 : aucune). */
    val forbiddenBid: Int get() = if (phase == OhHellPhase.BIDDING) OhHellScoring.forbiddenBid(bids, cardCount) else -1

    /** Vrai si le joueur qui annonce maintenant peut annoncer [value] plis. */
    fun isBidAllowed(value: Int): Boolean = phase == OhHellPhase.BIDDING && !roundOver && value in 0..cardCount && value != forbiddenBid

    /** Vrai si [card] peut être posée par le joueur de cette vue (sa main, ce pli) ; ne dit pas si c'est son tour. */
    fun isPlayable(card: Int): Boolean = phase == OhHellPhase.PLAYING && !roundOver && !trickDone &&
        OhHellScoring.canPlay(card, hand, trick)
}

/**
 * Une partie d'Oh Hell (Oh Pétard) jouée tour par tour sur un seul téléphone que l'on se passe, ou chacun sur son téléphone (voir
 * [OhHellOnline] : l'organisateur tient cette partie et n'envoie à chaque joueur que [tableFor] sa place) ; 3 à 7 joueurs.
 *
 * - À chaque manche, l'appli distribue [cardCount] cartes à chacun (1 à la première manche, puis 2, 3… jusqu'au maximum, puis on
 *   redescend : [OhHellScoring.cardsFor]) et retourne une carte du paquet : sa couleur est l'atout ([trump]).
 * - Le joueur après le donneur annonce le premier ; chacun, à son tour, voit sa main ([reveal]) puis annonce combien de plis il fera
 *   ([bid]). Le donneur annonce en dernier et ne peut pas annoncer le nombre qui ferait égaler le total des annonces et le nombre de
 *   plis ([forbiddenBid]).
 * - Celui qui a annoncé le premier ouvre le premier pli. Il faut fournir la couleur demandée si on le peut ; sinon on pose ce qu'on veut.
 *   Le plus gros atout ramasse le pli, ou à défaut la plus forte carte de la couleur demandée ([OhHellScoring.trickWinner]) ; le
 *   gagnant ouvre le pli suivant ([trickDone] : tout le monde voit le pli, puis [collectTrick]).
 * - La manche finit après le dernier pli. Chacun marque 10 points plus son annonce s'il a fait exactement ses plis, sinon −1 par pli
 *   d'écart ([BidScoring]) : [settle] inscrit ces points dans une ligne libre du tableau des scores (les totaux, le journal viennent de
 *   ce tableau, le plus grand total gagnant) ; [nextRound] distribue la manche suivante. La partie est finie ([finished]) après la
 *   dernière manche.
 *
 * [startRound] : numéro de la manche (une partie reprise continue après les manches déjà inscrites). [shuffle] mélange une liste de
 * cartes (remplaçable dans les tests).
 */
internal class OhHellRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    /** Annonces en cours ou jeu des plis. */
    var phase by mutableStateOf(OhHellPhase.BIDDING)
        private set

    /** Le joueur qui agit : celui qui annonce, puis celui qui joue. */
    var current by mutableIntStateOf(0)
        private set

    /** Les mains de chaque joueur (même ordre que les joueurs). */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** L'annonce de chaque joueur pour la manche (-1 : pas encore annoncé). */
    var bids by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les plis ramassés par chaque joueur depuis le début de la manche. */
    var tricksWon by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les cartes du pli en cours, dans l'ordre où elles ont été posées. */
    var trick by mutableStateOf<List<OhHellPlay>>(emptyList())
        private set

    /** Le joueur qui ramasse le pli quand il est complet ; -1 sinon. */
    var trickWinner by mutableIntStateOf(-1)
        private set

    /** Plis ramassés depuis le début de la manche. */
    var tricksPlayed by mutableIntStateOf(0)
        private set

    /** La carte retournée qui désigne l'atout. */
    var trump by mutableIntStateOf(0)
        private set

    /** Cartes distribuées à chacun dans la manche en cours. */
    var cardCount by mutableIntStateOf(1)
        private set

    /** Vrai quand le joueur qui agit a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai quand la manche est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Coups joués (annonces, cartes posées, plis ramassés) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** Nombre de manches de la partie. */
    val totalRounds: Int get() = OhHellScoring.totalRounds(playerCount)

    /** Le donneur de la manche en cours. */
    val dealer: Int get() = OhHellScoring.dealer(roundNumber, playerCount)

    /** Celui qui annonce le premier et ouvre le premier pli : le joueur après le donneur. */
    val firstPlayer: Int get() = (dealer + 1) % playerCount.coerceAtLeast(1)

    /** La couleur d'atout de la manche. */
    val trumpSuit: Int get() = FrenchCards.suit(trump)

    /** Vrai quand la dernière manche de la partie est inscrite (ou qu'une partie reprise a déjà joué toutes ses manches). */
    val finished: Boolean get() = roundNumber > totalRounds || (settled && roundNumber >= totalRounds)

    /** La main du joueur qui agit. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Vrai quand tous ont joué dans le pli : on le montre à tous, puis on le ramasse ([collectTrick]). */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** L'annonce interdite au joueur qui annonce maintenant (-1 : aucune). */
    val forbiddenBid: Int get() = if (phase == OhHellPhase.BIDDING) OhHellScoring.forbiddenBid(bids, cardCount) else -1

    /** Vrai si le joueur qui annonce maintenant peut annoncer [value] plis. */
    fun isBidAllowed(value: Int): Boolean = phase == OhHellPhase.BIDDING && !roundOver && value in 0..cardCount && value != forbiddenBid

    /** Vrai si [card] peut être posée maintenant par le joueur qui agit. */
    fun isPlayable(card: Int): Boolean {
        if (phase != OhHellPhase.PLAYING || roundOver || trickDone) return false
        return OhHellScoring.canPlay(card, currentHand, trick)
    }

    /**
     * La table telle que la voit le joueur [viewer] : le public et sa main. [viewer] hors des joueurs (-1) : un spectateur, qui ne
     * voit aucune main.
     */
    fun tableFor(viewer: Int): OhHellTable = OhHellTable(
        playerCount = playerCount,
        roundNumber = roundNumber,
        cardCount = cardCount,
        phase = phase,
        current = current,
        trump = trump,
        bids = bids,
        tricksWon = tricksWon,
        trick = trick,
        trickWinner = trickWinner,
        tricksPlayed = tricksPlayed,
        roundOver = roundOver,
        settled = settled,
        started = inProgress,
        handSizes = hands.map { it.size },
        points = roundPoints(),
        hand = hands.getOrElse(viewer) { emptyList() }.sortedWith(compareBy({ FrenchCards.suit(it) }, { FrenchCards.strength(it) }))
    )

    /** Points de chacun pour la manche : 10 + l'annonce si elle est exacte, sinon −1 par pli d'écart ; des 0 tant que la manche n'est pas finie. */
    fun roundPoints(): List<Int> = List(playerCount) { player ->
        if (roundOver) BidScoring.score(BidScoring.OH_HELL, bids.getOrElse(player) { 0 }.coerceAtLeast(0), tricksWon.getOrElse(player) { 0 }) else 0
    }

    // ---------- Les annonces ----------

    /** Le joueur qui agit montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver && !trickDone) revealed = true
    }

    /**
     * Le joueur qui annonce annonce [value] plis (de 0 au nombre de cartes ; pas l'annonce interdite au dernier, voir [forbiddenBid]).
     * Quand le dernier a annoncé, le jeu des plis commence. Faux, sans rien changer, si le coup n'est pas permis.
     */
    fun bid(value: Int): Boolean {
        if (phase != OhHellPhase.BIDDING || !revealed || roundOver) return false
        return recordBid(current, value)
    }

    /** Enregistre l'annonce de [player], qui doit être celui dont c'est le tour d'annoncer. */
    private fun recordBid(player: Int, value: Int): Boolean {
        if (phase != OhHellPhase.BIDDING || roundOver || player != current || !isBidAllowed(value)) return false
        bids = bids.mapIndexed { index, bid -> if (index == player) value else bid }
        moves++
        revealed = false
        if (bids.all { it >= 0 }) {
            phase = OhHellPhase.PLAYING
            current = firstPlayer
        } else {
            current = (current + 1) % playerCount
        }
        return true
    }

    // ---------- Les plis ----------

    /**
     * Pose [card] (qui doit être dans la main du joueur et jouable). Si c'est la dernière carte du pli, il est complet
     * ([trickDone], [trickWinner]) et personne ne joue avant [collectTrick]. Renvoie faux, sans rien changer, si le coup n'est pas permis.
     */
    fun play(card: Int): Boolean {
        if (!revealed || !isPlayable(card)) return false
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand - card else hand }
        trick = trick + OhHellPlay(player, card)
        moves++
        revealed = false
        if (trickDone) {
            trickWinner = OhHellScoring.trickWinner(trick, trumpSuit)
        } else {
            current = (current + 1) % playerCount
        }
        return true
    }

    /**
     * Tout le monde a vu le pli complet : le gagnant l'empoche et commence le suivant ; après le dernier pli, la manche est finie.
     * Faux si aucun pli n'est complet.
     */
    fun collectTrick(): Boolean {
        if (!trickDone || roundOver) return false
        val winner = trickWinner
        tricksPlayed++
        moves++
        tricksWon = tricksWon.mapIndexed { index, won -> if (index == winner) won + 1 else won }
        trick = emptyList()
        trickWinner = -1
        if (hands.all { it.isEmpty() }) {
            roundOver = true
        } else {
            current = winner
        }
        revealed = false
        return true
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

    /** Distribue la manche suivante ; faux tant que celle-ci n'est pas inscrite, et après la dernière manche de la partie. */
    fun nextRound(): Boolean {
        if (!settled || finished) return false
        roundNumber++
        deal()
        return true
    }

    /** Mélange le paquet, distribue les cartes de la manche et retourne l'atout : le joueur après le donneur annonce le premier. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val size = OhHellScoring.cardsFor(roundNumber, count)
        val deck = (0 until FrenchCards.DECK).toMutableList()
        shuffle(deck)
        hands = List(count) { player -> deck.subList(player * size, (player + 1) * size).toList() }
        trump = deck[count * size]
        cardCount = size
        bids = List(count) { -1 }
        tricksWon = List(count) { 0 }
        trick = emptyList()
        trickWinner = -1
        tricksPlayed = 0
        roundOver = false
        settled = false
        moves = 0
        phase = OhHellPhase.BIDDING
        current = firstPlayer
        revealed = false
    }

    /**
     * Installe une table précise (mains, atout, annonces, pli en cours) : sert aux tests. Sans [bids], les annonces restent à faire
     * ([phase] : annonces) ; avec toutes les annonces, le jeu des plis commence.
     */
    internal fun load(
        hands: List<List<Int>>,
        trump: Int,
        current: Int = 0,
        bids: List<Int> = hands.map { -1 },
        trick: List<OhHellPlay> = emptyList(),
        tricksWon: List<Int> = hands.map { 0 },
        tricksPlayed: Int = 0
    ) {
        require(hands.size == playerCount && current in 0 until playerCount && bids.size == playerCount && tricksWon.size == playerCount)
        this.hands = hands
        this.trump = trump
        this.current = current
        this.bids = bids
        this.trick = trick
        this.tricksWon = tricksWon
        this.tricksPlayed = tricksPlayed
        // Cartes de départ : celles qu'il reste au joueur qui n'a pas encore posé, plus les plis déjà joués (un pli complet a pris une carte à tous).
        cardCount = (hands.maxOfOrNull { it.size } ?: 0) + tricksPlayed + (if (trick.size == playerCount) 1 else 0)
        phase = if (bids.all { it >= 0 }) OhHellPhase.PLAYING else OhHellPhase.BIDDING
        trickWinner = -1
        revealed = false
        roundOver = false
        settled = false
        moves = 0
    }

    // ---------- Chacun sur son téléphone ----------

    /** [bid] pour le joueur [seat], qui doit être celui dont c'est le tour d'annoncer (il a sa main sur son téléphone). */
    fun bidFor(seat: Int, value: Int): Boolean = recordBid(seat, value)

    /** [play] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun playFor(seat: Int, card: Int): Boolean {
        if (seat != current || phase != OhHellPhase.PLAYING || trickDone || roundOver) return false
        revealed = true
        return play(card).also { if (!it) revealed = false }
    }

    /** [collectTrick] pour le joueur [seat], qui doit être celui qui ramasse le pli. */
    fun collectFor(seat: Int): Boolean = seat == trickWinner && collectTrick()
}
