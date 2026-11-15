package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant de Cœurs parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val HEARTS_ID = "builtin_coeurs"

/** Les cartes de Cœurs : voir [FrenchCards] pour leur numérotation. */
internal object HeartsScoring {

    /** La dame de pique (13 points). */
    val QUEEN_OF_SPADES: Int = FrenchCards.card(FrenchCards.SPADE, 12)

    /** Le 2 de trèfle : celui qui l'a commence la manche. */
    val TWO_OF_CLUBS: Int = FrenchCards.card(FrenchCards.CLUB, 2)

    /** Points d'une manche entière : les 13 cœurs (1 point chacun) et la dame de pique (13). */
    const val TOTAL_POINTS = 26

    /** Cartes passées à son voisin au début de la manche. */
    const val PASS_COUNT = 3

    /** Points d'une carte ramassée : 1 pour un cœur, 13 pour la dame de pique, 0 pour les autres. */
    fun points(card: Int): Int = when {
        FrenchCards.suit(card) == FrenchCards.HEART -> 1
        card == QUEEN_OF_SPADES -> 13
        else -> 0
    }

    /** Vrai pour une carte qui rapporte des points (un cœur ou la dame de pique). */
    fun isPoints(card: Int): Boolean = points(card) > 0

    /** Cartes retirées du paquet pour que tout le monde en ait autant : 2♦ à 3 joueurs ; 2♦ et 2♠ à 5 ; 2♦, 3♦, 2♠ et 3♠ à 6. */
    fun removedCards(playerCount: Int): List<Int> = when (playerCount) {
        3 -> listOf(FrenchCards.card(FrenchCards.DIAMOND, 2))
        5 -> listOf(FrenchCards.card(FrenchCards.DIAMOND, 2), FrenchCards.card(FrenchCards.SPADE, 2))
        6 -> listOf(
            FrenchCards.card(FrenchCards.DIAMOND, 2), FrenchCards.card(FrenchCards.DIAMOND, 3),
            FrenchCards.card(FrenchCards.SPADE, 2), FrenchCards.card(FrenchCards.SPADE, 3)
        )
        else -> emptyList()
    }

    /** Cartes de chaque joueur : 17 à 3, 13 à 4, 10 à 5, 8 à 6. */
    fun handSize(playerCount: Int): Int = (FrenchCards.DECK - removedCards(playerCount).size) / playerCount.coerceAtLeast(1)

    /**
     * Vrai si [card] peut être posée par un joueur qui a [hand] en main, dans un pli [trick] (vide : il l'ouvre), à [tricksPlayed]
     * plis ramassés et [heartsBroken] cœurs ouverts. Sert aussi à un invité, qui prévient son joueur sans attendre l'organisateur.
     */
    fun canPlay(card: Int, hand: List<Int>, trick: List<HeartsPlay>, tricksPlayed: Int, heartsBroken: Boolean): Boolean {
        if (card !in hand) return false
        val firstTrick = tricksPlayed == 0
        if (trick.isEmpty()) {
            // Au premier pli, celui qui a le 2 de trèfle le pose.
            if (firstTrick) return card == TWO_OF_CLUBS
            val isHeart = FrenchCards.suit(card) == FrenchCards.HEART
            return !(isHeart && !heartsBroken && hand.any { FrenchCards.suit(it) != FrenchCards.HEART })
        }
        val led = FrenchCards.suit(trick.first().card)
        if (hand.any { FrenchCards.suit(it) == led }) return FrenchCards.suit(card) == led
        // Sans la couleur demandée : on défausse ce qu'on veut, sauf des points au premier pli (quand on a autre chose).
        return !(firstTrick && isPoints(card) && hand.any { !isPoints(it) })
    }

    /**
     * Décalage de la passe de la manche [roundNumber] : 1 (au joueur suivant, « à gauche »), -1 (au précédent, « à droite »),
     * la moitié de la table (« en face », seulement à 4 et 6 joueurs) ou 0 (pas de passe), dans cet ordre, en boucle.
     */
    fun passOffset(roundNumber: Int, playerCount: Int): Int {
        val cycle = if (playerCount % 2 == 0) listOf(1, -1, playerCount / 2, 0) else listOf(1, -1, 0)
        return cycle[Math.floorMod(roundNumber - 1, cycle.size)]
    }
}

/** Une carte posée dans un pli par [player]. */
internal data class HeartsPlay(val player: Int, val card: Int)

/** Où en est la manche : chacun choisit ses cartes à passer, puis on joue les plis. */
internal enum class HeartsPhase { PASSING, PLAYING }

/**
 * La table de Cœurs telle que la voit un joueur : tout le public, sa main et les cartes qu'il vient de recevoir, jamais la main des
 * autres (voir [HeartsRound.tableFor]). C'est ce que l'organisateur envoie à chaque invité (voir [HeartsOnline]).
 */
internal data class HeartsTable(
    val playerCount: Int,
    val roundNumber: Int,
    val phase: HeartsPhase,
    val current: Int,
    val passOffset: Int,
    /** Pour chaque joueur : a-t-il déjà choisi ses cartes à passer ? */
    val passed: List<Boolean>,
    val trick: List<HeartsPlay>,
    val trickWinner: Int,
    val tricksPlayed: Int,
    val heartsBroken: Boolean,
    val roundOver: Boolean,
    val settled: Boolean,
    val started: Boolean,
    val handSizes: List<Int>,
    /** Points de chacun pour la manche (ce qu'il a ramassé ; le grand chelem une fois la manche finie). */
    val points: List<Int>,
    val moonShooter: Int,
    val hand: List<Int>,
    val received: List<Int>
) {
    /** Vrai quand tous ont joué dans le pli : on le montre à tous avant que le gagnant ne le ramasse. */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Le joueur qui reçoit les cartes de [player] à la passe. */
    fun passTarget(player: Int): Int = Math.floorMod(player + passOffset, playerCount.coerceAtLeast(1))

    /** Points d'un pli (cœurs et dame de pique qu'il contient). */
    fun trickPoints(): Int = trick.sumOf { HeartsScoring.points(it.card) }

    /** Vrai si [card] peut être posée par le joueur de cette vue (sa main, ce pli) ; ne dit pas si c'est son tour. */
    fun isPlayable(card: Int): Boolean = phase == HeartsPhase.PLAYING && !roundOver && !trickDone &&
        HeartsScoring.canPlay(card, hand, trick, tricksPlayed, heartsBroken)
}

/**
 * Une partie de Cœurs jouée tour par tour sur un seul téléphone que l'on se passe, ou chacun sur son téléphone (voir [HeartsOnline] :
 * l'organisateur tient cette partie et n'envoie à chaque joueur que [tableFor] sa place) ; 3 à 6 joueurs.
 *
 * - L'appli distribue tout le paquet (voir [HeartsScoring.removedCards] à 3, 5 et 6 joueurs). Chacun, à son tour, voit sa main
 *   ([reveal]) et choisit 3 cartes à passer ([confirmPass]) ; une fois que tous ont choisi, les cartes changent de main (le
 *   sens de la passe change à chaque manche, avec une manche sans passe : [HeartsScoring.passOffset]).
 * - Celui qui a le 2 de trèfle commence. Il faut fournir la couleur demandée si on le peut ; sinon on défausse ce qu'on veut,
 *   sauf au premier pli où l'on ne donne ni cœur ni dame de pique (sans rien d'autre en main, on le peut). On ne commence pas
 *   un pli par un cœur tant qu'aucun cœur n'a été joué ([heartsBroken]), sauf si l'on n'a que des cœurs ([isPlayable]).
 * - Quand tout le monde a joué, la plus forte carte de la couleur demandée (l'as est la plus fort) ramasse le pli
 *   ([trickDone] : tout le monde le voit, puis [collectTrick]) et commence le suivant.
 * - La manche finit après le dernier pli. Chaque cœur ramassé vaut 1 point et la dame de pique 13 ([roundPoints]) ; celui qui
 *   ramasse les 26 points réussit le grand chelem : il marque 0 et chacun des autres 26 ([moonShooter]).
 * - [settle] inscrit ces points dans une ligne libre du tableau des scores (le total de chacun, la fin de partie à 100 points
 *   et le journal viennent de ce tableau, le plus petit total gagnant) ; [nextRound] distribue la manche suivante.
 *
 * [startRound] : numéro de la manche (une partie reprise continue après les manches déjà inscrites). [shuffle] mélange une liste
 * de cartes (remplaçable dans les tests).
 */
internal class HeartsRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    /** Passe en cours ou jeu des plis. */
    var phase by mutableStateOf(HeartsPhase.PLAYING)
        private set

    /** Le joueur qui agit : celui qui choisit ses cartes à passer, puis celui qui joue. */
    var current by mutableIntStateOf(0)
        private set

    /** Les mains de chaque joueur (même ordre que les joueurs). */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Les cartes que chaque joueur vient de recevoir à la passe (elles restent signalées jusqu'à sa première carte jouée). */
    var received by mutableStateOf<List<Set<Int>>>(emptyList())
        private set

    /** Les cartes du pli en cours, dans l'ordre où elles ont été posées. */
    var trick by mutableStateOf<List<HeartsPlay>>(emptyList())
        private set

    /** Les cartes ramassées par chaque joueur depuis le début de la manche. */
    var taken by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Le joueur qui ramasse le pli quand il est complet ; -1 sinon. */
    var trickWinner by mutableIntStateOf(-1)
        private set

    /** Plis ramassés depuis le début de la manche. */
    var tricksPlayed by mutableIntStateOf(0)
        private set

    /** Vrai dès qu'un cœur a été joué : on peut alors commencer un pli par un cœur. */
    var heartsBroken by mutableStateOf(false)
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

    /** Les cartes choisies à passer par chaque joueur (null : pas encore choisi). */
    private var given: List<List<Int>?> = emptyList()

    /** Coups joués (cartes passées ou posées, plis ramassés) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** Décalage de la passe de cette manche (0 : pas de passe), voir [HeartsScoring.passOffset]. */
    val passOffset: Int get() = HeartsScoring.passOffset(roundNumber, playerCount)

    /** Le joueur qui reçoit les cartes de [player] à la passe. */
    fun passTarget(player: Int): Int = Math.floorMod(player + passOffset, playerCount.coerceAtLeast(1))

    /** La main du joueur qui agit. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Vrai quand tous ont joué dans le pli : on le montre à tous, puis on le ramasse ([collectTrick]). */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Points d'un pli (cœurs et dame de pique qu'il contient). */
    fun trickPoints(plays: List<HeartsPlay> = trick): Int = plays.sumOf { HeartsScoring.points(it.card) }

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** La couleur demandée par le pli en cours ; -1 s'il est vide. */
    val ledSuit: Int get() = trick.firstOrNull()?.let { FrenchCards.suit(it.card) } ?: -1

    /** Vrai si [card] peut être posée maintenant par le joueur qui agit (voir la description de la classe). */
    fun isPlayable(card: Int): Boolean {
        if (phase != HeartsPhase.PLAYING || roundOver || trickDone) return false
        return HeartsScoring.canPlay(card, currentHand, trick, tricksPlayed, heartsBroken)
    }

    /** Vrai si le joueur [player] a déjà choisi ses cartes à passer (pendant la passe). */
    fun hasPassed(player: Int): Boolean = given.getOrNull(player) != null

    /**
     * La table telle que la voit le joueur [viewer] : le public, sa main et les cartes qu'il vient de recevoir. [viewer] hors des
     * joueurs (-1) : un spectateur, qui ne voit aucune main.
     */
    fun tableFor(viewer: Int): HeartsTable = HeartsTable(
        playerCount = playerCount,
        roundNumber = roundNumber,
        phase = phase,
        current = current,
        passOffset = passOffset,
        passed = List(playerCount) { hasPassed(it) },
        trick = trick,
        trickWinner = trickWinner,
        tricksPlayed = tricksPlayed,
        heartsBroken = heartsBroken,
        roundOver = roundOver,
        settled = settled,
        started = inProgress,
        handSizes = hands.map { it.size },
        points = roundPoints(),
        moonShooter = moonShooter,
        hand = hands.getOrElse(viewer) { emptyList() }.sortedWith(compareBy({ FrenchCards.suit(it) }, { FrenchCards.strength(it) })),
        received = received.getOrElse(viewer) { emptySet() }.toList()
    )

    /** Le joueur qui a ramassé tous les points de la manche (grand chelem) ; -1 s'il n'y en a pas, ou tant que la manche n'est pas finie. */
    val moonShooter: Int
        get() = if (roundOver) rawPoints().indexOfFirst { it == HeartsScoring.TOTAL_POINTS } else -1

    private fun rawPoints(): List<Int> = taken.map { cards -> cards.sumOf { HeartsScoring.points(it) } }

    /** Points de chacun pour la manche : ce qu'il a ramassé ; après un grand chelem, 0 pour celui qui l'a réussi et 26 pour les autres. */
    fun roundPoints(): List<Int> {
        val raw = rawPoints()
        val shooter = moonShooter
        return if (shooter >= 0) raw.indices.map { if (it == shooter) 0 else HeartsScoring.TOTAL_POINTS } else raw
    }

    // ---------- La passe ----------

    /** Le joueur qui agit montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver && !trickDone) revealed = true
    }

    /**
     * Le joueur qui choisit ses cartes passe [cards] (exactement [HeartsScoring.PASS_COUNT] cartes différentes de sa main). Quand
     * le dernier a choisi, les cartes changent de main et celui qui a le 2 de trèfle commence. Faux, sans rien changer, si le
     * coup n'est pas permis.
     */
    fun confirmPass(cards: List<Int>): Boolean {
        if (phase != HeartsPhase.PASSING || !revealed || roundOver) return false
        return recordPass(current, cards)
    }

    /** Enregistre les cartes à passer de [player] ; quand tous ont choisi, les cartes changent de main. */
    private fun recordPass(player: Int, cards: List<Int>): Boolean {
        if (player !in 0 until playerCount || hasPassed(player)) return false
        if (cards.size != HeartsScoring.PASS_COUNT || cards.distinct().size != cards.size || !hands[player].containsAll(cards)) return false
        given = given.mapIndexed { index, chosen -> if (index == player) cards else chosen }
        moves++
        if (given.all { it != null }) {
            exchange()
        } else if (hasPassed(current)) {
            // Celui dont c'était le tour de choisir a fini : le téléphone passe au suivant qui n'a pas choisi.
            current = nextToPass(current)
            revealed = false
        }
        return true
    }

    /** Le premier joueur après [from] (en tournant) qui n'a pas encore choisi ses cartes. */
    private fun nextToPass(from: Int): Int {
        for (step in 1..playerCount) {
            val index = (from + step) % playerCount
            if (!hasPassed(index)) return index
        }
        return from
    }

    /**
     * Donne le téléphone à [player] pour qu'il choisisse ses cartes à passer, s'il ne l'a pas fait : chacun sur son téléphone,
     * l'organisateur ne doit pas attendre un joueur distant pour faire choisir ceux qui jouent sur son téléphone.
     */
    fun focusPass(player: Int) {
        if (phase != HeartsPhase.PASSING || roundOver || player == current || player !in 0 until playerCount || hasPassed(player)) return
        current = player
        revealed = false
    }

    private fun exchange() {
        val offset = passOffset
        val count = playerCount
        val chosen = given.map { it.orEmpty() }
        received = List(count) { player -> chosen[Math.floorMod(player - offset, count)].toSet() }
        hands = hands.mapIndexed { player, hand -> hand.filter { it !in chosen[player] } + chosen[Math.floorMod(player - offset, count)] }
        given = List(count) { null }
        startPlaying()
    }

    private fun startPlaying() {
        phase = HeartsPhase.PLAYING
        current = hands.indexOfFirst { HeartsScoring.TWO_OF_CLUBS in it }.coerceAtLeast(0)
        revealed = false
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
        received = received.mapIndexed { index, set -> if (index == player) emptySet() else set }
        trick = trick + HeartsPlay(player, card)
        if (FrenchCards.suit(card) == FrenchCards.HEART) heartsBroken = true
        moves++
        revealed = false
        if (trickDone) {
            val led = ledSuit
            val winner = trick.filter { FrenchCards.suit(it.card) == led }.maxByOrNull { FrenchCards.strength(it.card) }?.player ?: player
            trickWinner = winner
            val won = trick.map { it.card }
            taken = taken.mapIndexed { index, cards -> if (index == winner) cards + won else cards }
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

    /** Distribue la manche suivante (le sens de la passe change) ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        roundNumber++
        deal()
        return true
    }

    /** Mélange et distribue tout le paquet ; commence par la passe, sauf dans une manche sans passe. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val removed = HeartsScoring.removedCards(count)
        val deck = (0 until FrenchCards.DECK).filter { it !in removed }.toMutableList()
        shuffle(deck)
        val size = HeartsScoring.handSize(count)
        hands = List(count) { player -> deck.subList(player * size, (player + 1) * size).toList() }
        received = List(count) { emptySet() }
        taken = List(count) { emptyList() }
        trick = emptyList()
        trickWinner = -1
        tricksPlayed = 0
        heartsBroken = false
        roundOver = false
        settled = false
        given = List(count) { null }
        moves = 0
        if (passOffset == 0) {
            startPlaying()
        } else {
            phase = HeartsPhase.PASSING
            current = 0
            revealed = false
        }
    }

    /** Installe une table précise (mains, pli en cours, cartes ramassées) : sert aux tests. Le jeu des plis commence, sans passe. */
    internal fun load(
        hands: List<List<Int>>,
        current: Int = 0,
        trick: List<HeartsPlay> = emptyList(),
        tricksPlayed: Int = 0,
        heartsBroken: Boolean = false,
        taken: List<List<Int>> = hands.map { emptyList() }
    ) {
        require(hands.size == playerCount && current in 0 until playerCount)
        this.hands = hands
        this.current = current
        this.trick = trick
        this.tricksPlayed = tricksPlayed
        this.heartsBroken = heartsBroken
        this.taken = taken
        phase = HeartsPhase.PLAYING
        received = List(playerCount) { emptySet() }
        trickWinner = -1
        revealed = false
        roundOver = false
        settled = false
        given = List(playerCount) { null }
        moves = 0
    }

    // ---------- Chacun sur son téléphone ----------

    /** Le joueur [seat] choisit ses cartes à passer, à son rythme (il n'a pas besoin de « voir sa main » : il l'a sur son téléphone). */
    fun passFor(seat: Int, cards: List<Int>): Boolean {
        if (phase != HeartsPhase.PASSING || roundOver) return false
        return recordPass(seat, cards)
    }

    /** [play] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun playFor(seat: Int, card: Int): Boolean {
        if (seat != current || phase != HeartsPhase.PLAYING || trickDone || roundOver) return false
        revealed = true
        return play(card).also { if (!it) revealed = false }
    }

    /** [collectTrick] pour le joueur [seat], qui doit être celui qui ramasse le pli. */
    fun collectFor(seat: Int): Boolean = seat == trickWinner && collectTrick()
}
