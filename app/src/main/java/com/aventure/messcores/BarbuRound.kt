package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant du Barbu parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val BARBU_ID = "builtin_barbu"

/** Les sept contrats du Barbu : chaque joueur n'en choisit qu'un à la fois, et une seule fois chacun. */
internal enum class BarbuContract {
    NO_TRICKS, NO_HEARTS, NO_QUEENS, NO_KING, NO_LAST, SALAD, DOMINO;

    /** Vrai pour les contrats qui se jouent en plis (tous sauf le Domino). */
    val isTricks: Boolean get() = this != DOMINO
}

/** Les cartes et les points du Barbu : voir [FrenchCards] pour la numérotation des cartes. */
internal object BarbuScoring {

    /** Le roi de cœur, le « Barbu ». */
    val KING_OF_HEARTS: Int = FrenchCards.card(FrenchCards.HEART, 13)

    /** Pénalités des contrats en plis (négatives : elles s'ajoutent au total du joueur). */
    const val TRICK_POINTS = -2
    const val HEART_POINTS = -5
    const val QUEEN_POINTS = -10
    const val KING_POINTS = -20
    const val LAST_TRICK_POINTS = -40

    /** Une donne par contrat et par joueur : la partie dure 7 donnes par joueur. */
    const val CONTRACT_COUNT = 7

    /** Nombre de donnes d'une partie entière. */
    fun totalDeals(playerCount: Int): Int = CONTRACT_COUNT * playerCount

    fun isQueen(card: Int): Boolean = FrenchCards.rank(card) == 12

    fun isHeart(card: Int): Boolean = FrenchCards.suit(card) == FrenchCards.HEART

    /**
     * Les points d'un joueur pour une donne en plis : [taken] ses cartes ramassées, [tricksWon] ses plis, [wonLastTrick] s'il a ramassé
     * le dernier. La Salade additionne toutes les pénalités.
     */
    fun tricksPoints(contract: BarbuContract, taken: List<Int>, tricksWon: Int, wonLastTrick: Boolean): Int {
        val tricks = TRICK_POINTS * tricksWon
        val hearts = HEART_POINTS * taken.count { isHeart(it) }
        val queens = QUEEN_POINTS * taken.count { isQueen(it) }
        val king = if (KING_OF_HEARTS in taken) KING_POINTS else 0
        val last = if (wonLastTrick) LAST_TRICK_POINTS else 0
        return when (contract) {
            BarbuContract.NO_TRICKS -> tricks
            BarbuContract.NO_HEARTS -> hearts
            BarbuContract.NO_QUEENS -> queens
            BarbuContract.NO_KING -> king
            BarbuContract.NO_LAST -> last
            BarbuContract.SALAD -> tricks + hearts + queens + king + last
            BarbuContract.DOMINO -> 0
        }
    }

    /**
     * Points du Domino selon la place d'arrivée [place] (0 : le premier à se vider) à [playerCount] joueurs : le premier gagne
     * 40 points, le dernier en perd 40, les autres sont répartis entre les deux.
     */
    fun dominoPoints(playerCount: Int, place: Int): Int = when (playerCount) {
        3 -> listOf(40, 0, -40)
        4 -> listOf(40, 10, -10, -40)
        5 -> listOf(40, 20, 0, -20, -40)
        6 -> listOf(40, 24, 8, -8, -24, -40)
        else -> listOf(40, -40)
    }.getOrElse(place) { 0 }

    /** Cartes de chaque joueur : tout le paquet est distribué (voir [HeartsScoring.removedCards] à 3, 5 et 6 joueurs). */
    fun handSize(playerCount: Int): Int = HeartsScoring.handSize(playerCount)

    /** Vrai si [card] peut suivre le pli [trick] : on fournit la couleur demandée quand on le peut, sinon on défausse ce qu'on veut. */
    fun canFollow(card: Int, hand: List<Int>, trick: List<BarbuPlay>): Boolean {
        if (card !in hand) return false
        if (trick.isEmpty()) return true
        val led = FrenchCards.suit(trick.first().card)
        return FrenchCards.suit(card) == led || hand.none { FrenchCards.suit(it) == led }
    }

    /**
     * Vrai si [card] peut être posée au Domino : [low] et [high] donnent, pour chaque couleur, la plus basse et la plus haute valeur
     * posées (0 : couleur pas encore ouverte) ; [base] est la valeur de la première carte posée (0 : rien n'est posé, tout est permis).
     * Les valeurs vont de 2 à 14 (l'as est au-dessus du roi, jamais en dessous du 2).
     */
    fun dominoPlayable(card: Int, low: List<Int>, high: List<Int>, base: Int): Boolean {
        if (base == 0) return true
        val suit = FrenchCards.suit(card)
        val strength = FrenchCards.strength(card)
        val lo = low.getOrElse(suit) { 0 }
        val hi = high.getOrElse(suit) { 0 }
        if (lo == 0) return strength == base
        return strength == lo - 1 || strength == hi + 1
    }

    /**
     * Vrai si [card] peut ouvrir le Domino à [playerCount] joueurs : sa valeur doit exister dans les quatre couleurs. À 3, 5 et 6 joueurs
     * des cartes basses sont retirées du paquet (voir [HeartsScoring.removedCards]) : une couleur dont la carte de départ manque ne
     * s'ouvrirait jamais et la donne resterait bloquée.
     */
    fun canStartDomino(card: Int, playerCount: Int): Boolean =
        HeartsScoring.removedCards(playerCount).none { FrenchCards.strength(it) == FrenchCards.strength(card) }

    /** La carte de couleur [suit] dont la force (de 2 à 14) est [strength]. */
    fun cardOf(suit: Int, strength: Int): Int = FrenchCards.card(suit, if (strength == 14) 1 else strength)
}

/** Une carte posée dans un pli par [player]. */
internal data class BarbuPlay(val player: Int, val card: Int)

/** Où en est la donne : le donneur choisit son contrat, puis on joue. */
internal enum class BarbuPhase { CHOOSING, PLAYING }

/**
 * La table de Barbu telle que la voit un joueur : tout le public et sa main, jamais la main des autres (voir
 * [BarbuRound.tableFor]). C'est ce que l'organisateur envoie à chaque invité (voir [BarbuOnline]).
 */
internal data class BarbuTable(
    val playerCount: Int,
    val roundNumber: Int,
    /** Le joueur qui annonce le contrat de cette donne (et ouvre le jeu). */
    val declarer: Int,
    val phase: BarbuPhase,
    /** Le contrat de la donne ; null tant qu'il n'est pas choisi. */
    val contract: BarbuContract?,
    val current: Int,
    /** Pour chaque joueur, ses contrats déjà joués (un bit par [BarbuContract.ordinal]). */
    val usedMasks: List<Int>,
    val trick: List<BarbuPlay>,
    val trickWinner: Int,
    val tricksPlayed: Int,
    /** Plis ramassés par chaque joueur depuis le début de la donne. */
    val tricksWon: List<Int>,
    val roundOver: Boolean,
    val settled: Boolean,
    val started: Boolean,
    val finished: Boolean,
    val handSizes: List<Int>,
    /** Points de chacun pour la donne (négatifs : des pénalités). */
    val points: List<Int>,
    val hand: List<Int>,
    /** Domino : plus basse et plus haute valeur posées dans chaque couleur (0 : pas ouverte), et valeur de départ (0 : rien de posé). */
    val dominoLow: List<Int>,
    val dominoHigh: List<Int>,
    val dominoBase: Int,
    /** Domino : les joueurs dans l'ordre où ils se sont vidés. */
    val finishOrder: List<Int>,
    /** Domino : les joueurs qui ont dû passer à la dernière carte posée (aucune carte jouable). */
    val skipped: List<Int>
) {
    /** Vrai quand tous ont joué dans le pli : on le montre à tous avant que le gagnant ne le ramasse. */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Vrai si le joueur [player] a déjà joué le contrat [contract]. */
    fun hasUsed(player: Int, contract: BarbuContract): Boolean = (usedMasks.getOrElse(player) { 0 } shr contract.ordinal) and 1 == 1

    /** Les contrats que [player] peut encore annoncer ; tous s'il les a tous joués (partie reprise). */
    fun available(player: Int): List<BarbuContract> =
        BarbuContract.entries.filter { !hasUsed(player, it) }.ifEmpty { BarbuContract.entries.toList() }

    /** Vrai si [card] peut être posée par le joueur de cette vue (sa main, ce pli) ; ne dit pas si c'est son tour. */
    fun isPlayable(card: Int): Boolean {
        if (phase != BarbuPhase.PLAYING || roundOver || contract == null) return false
        return if (contract == BarbuContract.DOMINO) {
            card in hand && BarbuScoring.dominoPlayable(card, dominoLow, dominoHigh, dominoBase) &&
                (dominoBase != 0 || BarbuScoring.canStartDomino(card, playerCount))
        } else {
            !trickDone && BarbuScoring.canFollow(card, hand, trick)
        }
    }
}

/**
 * Une partie de Barbu jouée tour par tour sur un seul téléphone que l'on se passe, ou chacun sur son téléphone (voir [BarbuOnline] :
 * l'organisateur tient cette partie et n'envoie à chaque joueur que [tableFor] sa place) ; 3 à 6 joueurs.
 *
 * - Une partie compte 7 donnes par joueur ([BarbuScoring.totalDeals]). À chaque donne, l'appli distribue tout le paquet et le donneur
 *   ([declarer], qui change à chaque donne) regarde sa main puis annonce un contrat ([chooseContract]) parmi ceux qu'il n'a pas
 *   encore joués ([BarbuContract]). C'est lui qui ouvre le jeu.
 * - Contrats en plis : on fournit la couleur demandée si on le peut, sinon on défausse ce qu'on veut ; la plus forte carte de la
 *   couleur demandée (l'as est la plus forte) ramasse le pli ([collectTrick]) et commence le suivant. Pas de plis : −2 par pli ;
 *   Pas de cœurs : −5 par cœur ; Pas de dames : −10 par dame ; Pas de roi de cœur : −20 ; Pas de dernier pli : −40 ; la Salade
 *   additionne toutes ces pénalités.
 * - Domino : la première carte posée fixe la valeur de départ ; chaque couleur s'ouvre avec la carte de cette valeur, puis se
 *   prolonge vers le haut et vers le bas (l'as est au-dessus du roi). On n'ouvre pas avec une valeur dont une couleur manque au
 *   paquet à 3, 5 et 6 joueurs ([BarbuScoring.canStartDomino]). Qui ne peut rien poser passe son tour. Le premier à se vider
 *   gagne 40 points, le dernier en perd 40 ([BarbuScoring.dominoPoints]).
 * - [settle] inscrit les points de la donne (négatifs permis) dans une ligne libre du tableau des scores, [nextRound] distribue la
 *   donne suivante. Après la dernière donne, la partie est finie ([finished]) : le total le plus élevé gagne.
 *
 * Les contrats déjà joués ne sont retenus que pendant que la partie est ouverte (une partie reprise depuis le journal les remet à zéro).
 *
 * [startRound] : numéro de la donne (une partie reprise continue après les donnes déjà inscrites). [shuffle] mélange une liste de
 * cartes (remplaçable dans les tests).
 */
internal class BarbuRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    var phase by mutableStateOf(BarbuPhase.CHOOSING)
        private set

    /** Le contrat de la donne ; null tant que le donneur ne l'a pas annoncé. */
    var contract by mutableStateOf<BarbuContract?>(null)
        private set

    /** Le joueur qui agit : le donneur qui choisit, puis celui qui joue. */
    var current by mutableIntStateOf(0)
        private set

    /** Les mains de chaque joueur (même ordre que les joueurs). */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Les cartes du pli en cours, dans l'ordre où elles ont été posées. */
    var trick by mutableStateOf<List<BarbuPlay>>(emptyList())
        private set

    /** Les cartes ramassées par chaque joueur depuis le début de la donne. */
    var taken by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Les plis ramassés par chaque joueur depuis le début de la donne. */
    var tricksWon by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Le joueur qui ramasse le pli quand il est complet ; -1 sinon. */
    var trickWinner by mutableIntStateOf(-1)
        private set

    /** Plis ramassés depuis le début de la donne. */
    var tricksPlayed by mutableIntStateOf(0)
        private set

    /** Le joueur qui a ramassé le dernier pli ; -1 tant qu'il n'est pas joué. */
    var lastTrickWinner by mutableIntStateOf(-1)
        private set

    /** Vrai quand le joueur qui agit a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai quand la donne est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Vrai quand les points de la donne finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la donne en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Domino : plus basse et plus haute valeur posées dans chaque couleur (0 : couleur pas ouverte). */
    var dominoLow by mutableStateOf(List(FrenchCards.SUITS) { 0 })
        private set
    var dominoHigh by mutableStateOf(List(FrenchCards.SUITS) { 0 })
        private set

    /** Domino : valeur de la première carte posée (0 : rien de posé). */
    var dominoBase by mutableIntStateOf(0)
        private set

    /** Domino : les joueurs dans l'ordre où ils se sont vidés. */
    var finishOrder by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Domino : les joueurs qui ont dû passer à la dernière carte posée. */
    var skipped by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les contrats déjà joués par chaque joueur (un bit par [BarbuContract.ordinal]). */
    var usedMasks by mutableStateOf(List(playerCount.coerceAtLeast(1)) { 0 })
        private set

    /** Coups joués depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** Le donneur de la donne en cours : il change à chaque donne, chacun annonce 7 fois en tout. */
    val declarer: Int get() = Math.floorMod(roundNumber - 1, playerCount.coerceAtLeast(1))

    /** Vrai quand la partie entière est jouée : toutes les donnes sont faites et inscrites. */
    val finished: Boolean
        get() = roundNumber > BarbuScoring.totalDeals(playerCount) ||
            (roundOver && settled && roundNumber >= BarbuScoring.totalDeals(playerCount))

    /** La main du joueur qui agit. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Vrai quand tous ont joué dans le pli : on le montre à tous, puis on le ramasse ([collectTrick]). */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Vrai quand la donne en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** Vrai si [player] a déjà joué le contrat [contract]. */
    fun hasUsed(player: Int, contract: BarbuContract): Boolean = (usedMasks.getOrElse(player) { 0 } shr contract.ordinal) and 1 == 1

    /** Les contrats que [player] peut encore annoncer ; tous s'il les a tous joués (partie reprise). */
    fun available(player: Int): List<BarbuContract> =
        BarbuContract.entries.filter { !hasUsed(player, it) }.ifEmpty { BarbuContract.entries.toList() }

    /** Vrai si [card] peut être posée maintenant par le joueur qui agit (voir la description de la classe). */
    fun isPlayable(card: Int): Boolean {
        if (phase != BarbuPhase.PLAYING || roundOver) return false
        return if (contract == BarbuContract.DOMINO) {
            card in currentHand && BarbuScoring.dominoPlayable(card, dominoLow, dominoHigh, dominoBase) &&
                (dominoBase != 0 || BarbuScoring.canStartDomino(card, playerCount))
        } else {
            !trickDone && BarbuScoring.canFollow(card, currentHand, trick)
        }
    }

    /**
     * La table telle que la voit le joueur [viewer] : le public et sa main. [viewer] hors des joueurs (-1) : un spectateur, qui ne voit
     * aucune main.
     */
    fun tableFor(viewer: Int): BarbuTable = BarbuTable(
        playerCount = playerCount,
        roundNumber = roundNumber,
        declarer = declarer,
        phase = phase,
        contract = contract,
        current = current,
        usedMasks = usedMasks,
        trick = trick,
        trickWinner = trickWinner,
        tricksPlayed = tricksPlayed,
        tricksWon = tricksWon,
        roundOver = roundOver,
        settled = settled,
        started = inProgress,
        finished = finished,
        handSizes = hands.map { it.size },
        points = roundPoints(),
        hand = hands.getOrElse(viewer) { emptyList() }.sortedWith(compareBy({ FrenchCards.suit(it) }, { FrenchCards.strength(it) })),
        dominoLow = dominoLow,
        dominoHigh = dominoHigh,
        dominoBase = dominoBase,
        finishOrder = finishOrder,
        skipped = skipped
    )

    /** Points de chacun pour la donne (négatifs : des pénalités) ; ceux du Domino ne comptent qu'une fois la donne finie. */
    fun roundPoints(): List<Int> {
        val chosen = contract ?: return List(playerCount) { 0 }
        if (chosen == BarbuContract.DOMINO) {
            return List(playerCount) { player ->
                val place = finishOrder.indexOf(player)
                if (roundOver && place >= 0) BarbuScoring.dominoPoints(playerCount, place) else 0
            }
        }
        return List(playerCount) { player ->
            BarbuScoring.tricksPoints(
                chosen,
                taken.getOrElse(player) { emptyList() },
                tricksWon.getOrElse(player) { 0 },
                lastTrickWinner == player
            )
        }
    }

    // ---------- Le contrat ----------

    /** Le donneur montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver && !trickDone) revealed = true
    }

    /**
     * Le donneur annonce [chosen], qu'il n'a pas encore joué ; il ouvre alors le jeu. Faux, sans rien changer, si le coup n'est pas
     * permis (pas la phase du choix, contrat déjà joué par lui, main pas montrée).
     */
    fun chooseContract(chosen: BarbuContract): Boolean {
        if (phase != BarbuPhase.CHOOSING || !revealed || roundOver) return false
        return record(chosen)
    }

    /** [chooseContract] pour le joueur [seat], qui doit être le donneur : il a sa main sur son téléphone. */
    fun chooseFor(seat: Int, chosen: BarbuContract): Boolean {
        if (phase != BarbuPhase.CHOOSING || roundOver || seat != declarer) return false
        return record(chosen)
    }

    private fun record(chosen: BarbuContract): Boolean {
        if (chosen !in available(declarer)) return false
        contract = chosen
        usedMasks = usedMasks.mapIndexed { index, mask -> if (index == declarer) mask or (1 shl chosen.ordinal) else mask }
        phase = BarbuPhase.PLAYING
        current = declarer
        revealed = false
        moves++
        return true
    }

    // ---------- Le jeu ----------

    /**
     * Pose [card] (qui doit être dans la main du joueur et jouable). En plis, si c'est la dernière carte du pli, il est complet
     * ([trickDone], [trickWinner]) et personne ne joue avant [collectTrick]. Au Domino, le tour passe au joueur suivant qui peut
     * poser une carte. Renvoie faux, sans rien changer, si le coup n'est pas permis.
     */
    fun play(card: Int): Boolean {
        if (!revealed || !isPlayable(card)) return false
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand - card else hand }
        moves++
        revealed = false
        if (contract == BarbuContract.DOMINO) {
            playDomino(player, card)
        } else {
            playTrick(player, card)
        }
        return true
    }

    private fun playTrick(player: Int, card: Int) {
        trick = trick + BarbuPlay(player, card)
        if (trickDone) {
            val led = FrenchCards.suit(trick.first().card)
            val winner = trick.filter { FrenchCards.suit(it.card) == led }.maxByOrNull { FrenchCards.strength(it.card) }?.player ?: player
            trickWinner = winner
            val won = trick.map { it.card }
            taken = taken.mapIndexed { index, cards -> if (index == winner) cards + won else cards }
            tricksWon = tricksWon.mapIndexed { index, count -> if (index == winner) count + 1 else count }
            // Le dernier pli : sa pénalité est connue dès qu'il est complet.
            if (hands.all { it.isEmpty() }) lastTrickWinner = winner
        } else {
            current = (current + 1) % playerCount
        }
    }

    private fun playDomino(player: Int, card: Int) {
        val suit = FrenchCards.suit(card)
        val strength = FrenchCards.strength(card)
        if (dominoBase == 0) dominoBase = strength
        dominoLow = dominoLow.mapIndexed { index, value ->
            if (index == suit) (if (value == 0) strength else minOf(value, strength)) else value
        }
        dominoHigh = dominoHigh.mapIndexed { index, value ->
            if (index == suit) maxOf(value, strength) else value
        }
        if (hands[player].isEmpty()) finishOrder = finishOrder + player
        val left = (0 until playerCount).filter { hands[it].isNotEmpty() }
        if (left.size <= 1) {
            // Il ne reste qu'un joueur avec des cartes : il termine dernier, la donne est finie.
            finishOrder = finishOrder + left.filter { it !in finishOrder }
            skipped = emptyList()
            roundOver = true
            return
        }
        // Le joueur suivant qui peut poser une carte : ceux qui ne le peuvent pas passent.
        val passed = ArrayList<Int>()
        for (step in 1..playerCount) {
            val next = (player + step) % playerCount
            val hand = hands[next]
            if (hand.isEmpty()) continue
            if (hand.any { BarbuScoring.dominoPlayable(it, dominoLow, dominoHigh, dominoBase) }) {
                current = next
                break
            }
            passed.add(next)
        }
        skipped = passed
    }

    /**
     * Tout le monde a vu le pli complet : le gagnant l'empoche et commence le suivant ; après le dernier pli, la donne est finie.
     * Faux si aucun pli n'est complet.
     */
    fun collectTrick(): Boolean {
        if (contract == BarbuContract.DOMINO || !trickDone || roundOver) return false
        tricksPlayed++
        moves++
        val winner = trickWinner
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

    // ---------- Donnes et scores ----------

    /**
     * Inscrit les points de la donne finie dans la première ligne vide du tableau des scores (une seule fois). Renvoie vrai
     * si les points viennent d'être inscrits.
     */
    fun settle(viewModel: ScoreViewModel): Boolean {
        if (!roundOver || settled) return false
        if (!writeRoundPoints(viewModel, roundPoints())) return false
        settled = true
        return true
    }

    /** Distribue la donne suivante ; faux tant que celle-ci n'est pas inscrite, ou quand la partie est finie. */
    fun nextRound(): Boolean {
        if (!settled || finished) return false
        roundNumber++
        deal()
        return true
    }

    /** Mélange et distribue tout le paquet ; le donneur annonce ensuite son contrat. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val removed = HeartsScoring.removedCards(count)
        val deck = (0 until FrenchCards.DECK).filter { it !in removed }.toMutableList()
        shuffle(deck)
        val size = BarbuScoring.handSize(count)
        hands = List(count) { player -> deck.subList(player * size, (player + 1) * size).toList() }
        taken = List(count) { emptyList() }
        tricksWon = List(count) { 0 }
        trick = emptyList()
        trickWinner = -1
        tricksPlayed = 0
        lastTrickWinner = -1
        roundOver = false
        settled = false
        contract = null
        dominoLow = List(FrenchCards.SUITS) { 0 }
        dominoHigh = List(FrenchCards.SUITS) { 0 }
        dominoBase = 0
        finishOrder = emptyList()
        skipped = emptyList()
        moves = 0
        phase = BarbuPhase.CHOOSING
        current = declarer
        revealed = false
    }

    /** Installe une table précise (contrat, mains, pli en cours) : sert aux tests. Le jeu commence, le contrat est déjà annoncé. */
    internal fun load(
        contract: BarbuContract,
        hands: List<List<Int>>,
        current: Int = 0,
        trick: List<BarbuPlay> = emptyList(),
        tricksPlayed: Int = 0,
        taken: List<List<Int>> = hands.map { emptyList() },
        tricksWon: List<Int> = hands.map { 0 }
    ) {
        require(hands.size == playerCount && current in 0 until playerCount)
        this.contract = contract
        this.hands = hands
        this.current = current
        this.trick = trick
        this.tricksPlayed = tricksPlayed
        this.taken = taken
        this.tricksWon = tricksWon
        phase = BarbuPhase.PLAYING
        trickWinner = -1
        lastTrickWinner = -1
        revealed = false
        roundOver = false
        settled = false
        dominoLow = List(FrenchCards.SUITS) { 0 }
        dominoHigh = List(FrenchCards.SUITS) { 0 }
        dominoBase = 0
        finishOrder = emptyList()
        skipped = emptyList()
        moves = 0
    }

    // ---------- Chacun sur son téléphone ----------

    /** [play] pour le joueur [seat], qui doit être celui dont c'est le tour (sa main est sur son téléphone : pas de « voir ma main »). */
    fun playFor(seat: Int, card: Int): Boolean {
        if (seat != current || phase != BarbuPhase.PLAYING || roundOver) return false
        if (contract != BarbuContract.DOMINO && trickDone) return false
        revealed = true
        return play(card).also { if (!it) revealed = false }
    }

    /** [collectTrick] pour le joueur [seat], qui doit être celui qui ramasse le pli. */
    fun collectFor(seat: Int): Boolean = seat == trickWinner && collectTrick()
}
