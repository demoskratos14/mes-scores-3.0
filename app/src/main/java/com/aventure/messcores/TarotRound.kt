package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant du Tarot parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val TAROT_ID = "builtin_tarot"

/**
 * Le jeu de 78 cartes du Tarot. Une carte est un entier de 0 à 77 :
 * - 0 à 55 : les cartes de couleur, `couleur * 14 + valeur - 1`. La valeur va de 1 à 10, puis 11 (valet), 12 (cavalier), 13 (dame)
 *   et 14 (roi) ; la couleur est celle de [FrenchCards] (0 pique, 1 cœur, 2 carreau, 3 trèfle) ;
 * - 56 à 76 : les atouts 1 à 21 (le 1 est le petit) ;
 * - 77 : l'Excuse.
 * L'ordre numérique est donc aussi l'ordre de tri de la main (couleurs, puis atouts, puis Excuse).
 */
internal object TarotCards {
    /** Cartes par couleur : de 1 à 10, valet, cavalier, dame, roi. */
    const val SUIT_SIZE = 14

    /** Cartes de couleur (4 × 14). */
    const val SUIT_CARDS = 56

    /** Nombre d'atouts. */
    const val TRUMPS = 21

    /** Le petit : l'atout 1. */
    const val PETIT = 56

    /** Le 21 d'atout. */
    const val TWENTY_ONE = 76

    /** L'Excuse. */
    const val EXCUSE = 77

    /** Cartes du jeu entier. */
    const val DECK = 78

    /** Valeur du roi. */
    const val KING_RANK = 14

    /** Valeur de la dame. */
    const val QUEEN_RANK = 13

    fun isSuitCard(card: Int): Boolean = card in 0 until SUIT_CARDS

    fun isTrump(card: Int): Boolean = card in SUIT_CARDS until EXCUSE

    fun isExcuse(card: Int): Boolean = card == EXCUSE

    /** Couleur (0 à 3) d'une carte de couleur. */
    fun suit(card: Int): Int = card / SUIT_SIZE

    /** Valeur (1 à 14) d'une carte de couleur. */
    fun rank(card: Int): Int = card % SUIT_SIZE + 1

    /** Numéro (1 à 21) d'un atout. */
    fun trumpNumber(card: Int): Int = card - SUIT_CARDS + 1

    /** La carte de couleur [suit] et de valeur [rank]. */
    fun card(suit: Int, rank: Int): Int = suit * SUIT_SIZE + rank - 1

    /** Le roi de la couleur [suit]. */
    fun king(suit: Int): Int = card(suit, KING_RANK)

    /** Les bouts : le petit, le 21 et l'Excuse. */
    fun isOudler(card: Int): Boolean = card == PETIT || card == TWENTY_ONE || card == EXCUSE

    fun isKing(card: Int): Boolean = isSuitCard(card) && rank(card) == KING_RANK

    /**
     * Valeur de la carte en demi-points (pour éviter les fractions) : roi 9 (4,5 points), dame 7, cavalier 5, valet 3, bout 9, toute
     * autre carte 1 (0,5 point). Les 78 cartes valent 182 demi-points, soit 91 points.
     */
    fun halfPoints(card: Int): Int = when {
        isOudler(card) -> 9
        isSuitCard(card) -> when (rank(card)) {
            KING_RANK -> 9
            QUEEN_RANK -> 7
            12 -> 5
            11 -> 3
            else -> 1
        }
        else -> 1
    }
}

/** Les enchères, de la plus basse à la plus haute (0 : passe). */
internal object TarotBid {
    const val PASS = 0
    const val PETITE = 1
    const val GARDE = 2
    const val GARDE_SANS = 3
    const val GARDE_CONTRE = 4

    /** Coefficient du contrat (petite ×1, garde ×2, garde sans ×4, garde contre ×6). */
    fun factor(bid: Int): Int = when (bid) {
        PETITE -> 1
        GARDE -> 2
        GARDE_SANS -> 4
        GARDE_CONTRE -> 6
        else -> 0
    }
}

/** Une carte posée dans un pli par [player]. */
internal data class TarotPlay(val player: Int, val card: Int)

/** Une poignée montrée par [player] : [level] 1 (simple), 2 (double) ou 3 (triple), avec les atouts [trumps] qu'il a montrés. */
internal data class TarotHandful(val player: Int, val level: Int, val trumps: List<Int>)

/** Où en est la donne : enchères, appel du roi (à 5 joueurs), chien et écart, puis jeu des plis. */
internal enum class TarotPhase { BIDDING, CALLING, DISCARD, PLAYING }

/**
 * Le résultat d'une donne, une fois le dernier pli joué : tout ce qu'il faut pour l'afficher et l'inscrire au tableau des scores.
 * [attackHalf] : demi-points du camp du preneur ; [points] : ses points entiers (le demi-point est arrondi en faveur du camp
 * qui gagne) ; [petitAuBout] : 0 aucun, 1 preneur, 2 défense ; [handful] : niveau de la plus forte poignée (0 : aucune) et
 * [extraHandful] les points des autres poignées ; [unit] : le score payé par chaque défenseur (voir [computeTarotRound]).
 */
internal data class TarotOutcome(
    val taker: Int,
    val partner: Int,
    val contract: Int,
    val attackHalf: Int,
    val bouts: Int,
    val required: Int,
    val points: Int,
    val difference: Int,
    val success: Boolean,
    val petitAuBout: Int,
    val handful: Int,
    val extraHandful: Int,
    val slamAnnounced: Boolean,
    val slamSucceeded: Boolean,
    val defenseSlam: Boolean,
    val unit: Int,
    val deltas: List<Int>
)

/** Les règles qui ne dépendent d'aucun état : cartes jouables, gagnant d'un pli, écart, poignées. */
internal object TarotRules {

    /** Cartes du chien : 6 à 3 et 4 joueurs, 3 à 5 joueurs. */
    fun dogSize(playerCount: Int): Int = if (playerCount == 5) 3 else 6

    /** Cartes de chaque joueur : 24 à 3 joueurs, 18 à 4, 15 à 5. */
    fun handSize(playerCount: Int): Int = (TarotCards.DECK - dogSize(playerCount)) / playerCount.coerceAtLeast(1)

    /** Vrai si la main [hand] n'a qu'un atout et que c'est le petit : la donne est annulée (« petit sec »). */
    fun isPetitSec(hand: List<Int>): Boolean {
        val trumps = hand.filter { TarotCards.isTrump(it) }
        return trumps.size == 1 && trumps[0] == TarotCards.PETIT
    }

    /**
     * Vrai si [card] peut être posée par un joueur qui a [hand] en main dans le pli [trick] (vide : il l'ouvre). Il faut fournir la
     * couleur demandée ; sans elle, couper avec un atout ; et monter sur le plus fort atout du pli quand on le peut. L'Excuse se joue
     * toujours ; si elle ouvre le pli, c'est la carte suivante qui donne la couleur demandée.
     */
    fun canPlay(card: Int, hand: List<Int>, trick: List<TarotPlay>): Boolean {
        if (card !in hand) return false
        if (TarotCards.isExcuse(card)) return true
        val led = trick.firstOrNull { !TarotCards.isExcuse(it.card) }?.card ?: return true
        val highestTrump = trick.filter { TarotCards.isTrump(it.card) }.maxOfOrNull { TarotCards.trumpNumber(it.card) } ?: 0
        val trumps = hand.filter { TarotCards.isTrump(it) }
        val canOvertrump = trumps.any { TarotCards.trumpNumber(it) > highestTrump }
        if (TarotCards.isTrump(led)) {
            if (trumps.isEmpty()) return true
            if (!TarotCards.isTrump(card)) return false
            return !canOvertrump || TarotCards.trumpNumber(card) > highestTrump
        }
        val suit = TarotCards.suit(led)
        val hasSuit = hand.any { TarotCards.isSuitCard(it) && TarotCards.suit(it) == suit }
        if (hasSuit) return TarotCards.isSuitCard(card) && TarotCards.suit(card) == suit
        // Sans la couleur demandée : on coupe, en montant si on peut ; sans atout, on se défausse de ce qu'on veut.
        if (trumps.isEmpty()) return true
        if (!TarotCards.isTrump(card)) return false
        return !canOvertrump || TarotCards.trumpNumber(card) > highestTrump
    }

    /**
     * Le joueur qui remporte le pli : le plus gros atout s'il y en a, sinon la plus forte carte de la couleur demandée. L'Excuse ne
     * gagne jamais (voir [TarotRound] pour son cas particulier au dernier pli d'un chelem).
     */
    fun trickWinner(trick: List<TarotPlay>): Int {
        val real = trick.filter { !TarotCards.isExcuse(it.card) }
        if (real.isEmpty()) return trick.firstOrNull()?.player ?: -1
        val trumps = real.filter { TarotCards.isTrump(it.card) }
        if (trumps.isNotEmpty()) return trumps.maxByOrNull { it.card }?.player ?: -1
        val suit = TarotCards.suit(real.first().card)
        return real.filter { TarotCards.suit(it.card) == suit }.maxByOrNull { TarotCards.rank(it.card) }?.player ?: -1
    }

    /**
     * Vrai si [cards] est un écart permis dans la main [hand] : exactement [count] cartes de la main, ni roi ni bout, et un atout
     * seulement faute d'autre carte (toutes les cartes ordinaires de la main sont alors écartées).
     */
    fun isValidDiscard(cards: List<Int>, hand: List<Int>, count: Int): Boolean {
        if (cards.size != count || cards.distinct().size != cards.size || !hand.containsAll(cards)) return false
        if (cards.any { TarotCards.isOudler(it) || TarotCards.isKing(it) }) return false
        if (cards.any { TarotCards.isTrump(it) }) {
            val plain = hand.filter { !TarotCards.isTrump(it) && !TarotCards.isOudler(it) && !TarotCards.isKing(it) }
            if (!cards.containsAll(plain)) return false
        }
        return true
    }

    /** Atouts à montrer pour une poignée simple, double puis triple. */
    fun handfulThresholds(playerCount: Int): List<Int> = when (playerCount) {
        3 -> listOf(13, 15, 18)
        5 -> listOf(8, 10, 13)
        else -> listOf(10, 13, 15)
    }

    /** Niveau de poignée que permettent [trumpCount] atouts : 0 aucune, 1 simple, 2 double, 3 triple. */
    fun handfulLevel(playerCount: Int, trumpCount: Int): Int = handfulThresholds(playerCount).count { trumpCount >= it }

    /** Points d'une poignée de niveau [level] (0 à 3) : 0, 20, 30 ou 40. */
    fun handfulPoints(level: Int): Int = listOf(0, 20, 30, 40).getOrElse(level) { 0 }

    /** Les cartes qu'un preneur à 5 joueurs peut appeler : les 4 rois, ou les 4 dames s'il a les 4 rois. */
    fun callOptions(hand: List<Int>): List<Int> {
        val kings = (0 until FrenchCards.SUITS).map { TarotCards.king(it) }
        return if (kings.all { it in hand }) {
            (0 until FrenchCards.SUITS).map { TarotCards.card(it, TarotCards.QUEEN_RANK) }
        } else {
            kings
        }
    }
}

/**
 * La table de Tarot telle que la voit un joueur : tout le public, sa main, jamais celle des autres (voir [TarotRound.tableFor]).
 * C'est ce que l'organisateur envoie à chaque invité (voir [TarotOnline]).
 *
 * [bids] : l'enchère de chacun (-1 : pas encore parlé, 0 passe, 1 à 4) ; [taker] et [contract] : le preneur et son enchère (-1
 * et 0 avant la fin des enchères) ; [calledCard] : la carte appelée à 5 joueurs ; [partner] : le partenaire une fois qu'il a posé
 * la carte appelée (-1 tant qu'on ne le sait pas) ; [dog] : le chien montré à tous pendant l'écart (vide sinon) ;
 * [passedOut] : vrai si la donne vient d'être refaite parce que tout le monde a passé.
 */
internal data class TarotTable(
    val playerCount: Int,
    val roundNumber: Int,
    val dealer: Int,
    val phase: TarotPhase,
    val current: Int,
    val bids: List<Int>,
    val taker: Int,
    val contract: Int,
    val calledCard: Int,
    val partner: Int,
    val dog: List<Int>,
    val slamAnnounced: Boolean,
    val trick: List<TarotPlay>,
    val trickWinner: Int,
    val tricksPlayed: Int,
    val roundOver: Boolean,
    val settled: Boolean,
    val started: Boolean,
    val passedOut: Boolean,
    val handSizes: List<Int>,
    val handfuls: List<TarotHandful>,
    val discardedTrumps: List<Int>,
    val outcome: TarotOutcome?,
    val hand: List<Int>
) {
    /** Vrai quand tous ont joué dans le pli : on le montre à tous avant que le gagnant ne le ramasse. */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Cartes à écarter : celles du chien en petite et en garde, aucune quand le chien n'est pas pris. */
    val discardCount: Int get() = if (contract == TarotBid.PETITE || contract == TarotBid.GARDE) TarotRules.dogSize(playerCount) else 0

    /** La plus haute enchère déjà faite (0 si personne n'a encore misé). */
    val highestBid: Int get() = (bids.maxOrNull() ?: 0).coerceAtLeast(0)

    /** Vrai si [card] peut être posée par le joueur de cette vue (sa main, ce pli) ; ne dit pas si c'est son tour. */
    fun isPlayable(card: Int): Boolean =
        phase == TarotPhase.PLAYING && !roundOver && !trickDone && TarotRules.canPlay(card, hand, trick)

    /** Niveau de poignée que la main de cette vue permet (0 : aucune). */
    fun handfulLevelOf(): Int = TarotRules.handfulLevel(playerCount, hand.count { TarotCards.isTrump(it) })

    /** Vrai si le joueur [seat] peut annoncer une poignée maintenant : c'est son tour, il n'a pas encore joué de carte et en a assez. */
    fun canDeclareHandful(seat: Int): Boolean =
        phase == TarotPhase.PLAYING && !roundOver && !trickDone && current == seat && seat >= 0 &&
            handfuls.none { it.player == seat } && hand.size == TarotRules.handSize(playerCount) && handfulLevelOf() > 0
}

/**
 * Une donne de Tarot jouée tour par tour sur un seul téléphone que l'on se passe, ou chacun sur son téléphone (voir
 * [TarotOnline] : l'organisateur tient cette partie et n'envoie à chaque joueur que [tableFor] sa place) ; 3 à 5 joueurs.
 *
 * - L'appli distribue (24 cartes à 3 joueurs, 18 à 4, 15 à 5, et le chien) ; une main qui n'a que le petit comme atout (« petit
 *   sec ») fait refaire la donne. Chacun parle une fois, à son tour : passe, ou une enchère plus haute que la précédente (petite,
 *   garde, garde sans le chien, garde contre le chien). Si tous passent, le donneur suivant redistribue ([passedOut]).
 * - À 5 joueurs, le preneur appelle un roi ([callFor]) : son propriétaire est son partenaire, connu seulement quand il pose le roi.
 * - En petite et en garde, le chien est montré à tous puis le preneur écarte autant de cartes ([discardFor]) ; en garde sans le
 *   chien, le chien reste au camp du preneur ; en garde contre, il va à la défense. Le preneur peut annoncer un chelem (il ouvre alors).
 * - Chacun, avant sa première carte, peut montrer une poignée s'il a assez d'atouts ([handfulFor]).
 * - On fournit la couleur demandée ; sinon on coupe, en montant si on peut ([TarotRules.canPlay]). Le plus fort atout, ou à défaut
 *   la plus forte carte de la couleur demandée, ramasse le pli ([trickDone], puis [collect]). L'Excuse ne gagne pas le pli : elle
 *   reste à son camp (qui rend une carte basse s'il l'a jouée dans un pli adverse) ; jouée au dernier pli, elle est perdue, sauf dans un chelem annoncé.
 * - La donne finit après le dernier pli : [outcome] donne les bouts, les points, le petit au bout, les poignées et le chelem, et
 *   [settle] inscrit la manche au tableau des scores comme la saisie de la feuille de Tarot ([computeTarotRound]) ; [nextRound]
 *   distribue la suivante, le donneur changeant à chaque fois.
 *
 * [startRound] : numéro de la manche (une partie reprise continue après les manches déjà inscrites). [shuffle] mélange une liste de
 * cartes (remplaçable dans les tests).
 */
internal class TarotRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    /** Enchères, appel, écart ou jeu des plis. */
    var phase by mutableStateOf(TarotPhase.BIDDING)
        private set

    /** Le joueur qui agit : celui qui parle, appelle, écarte ou joue. */
    var current by mutableIntStateOf(0)
        private set

    /** Le donneur : celui qui parle en premier est le suivant. */
    var dealer by mutableIntStateOf((startRound - 1).coerceAtLeast(0) % playerCount.coerceAtLeast(1))
        private set

    /** Les mains de chaque joueur, triées (même ordre que les joueurs). */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** L'enchère de chacun : -1 pas encore parlé, 0 passe, 1 à 4 ([TarotBid]). */
    var bids by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Le preneur (-1 avant la fin des enchères) et son enchère. */
    var taker by mutableIntStateOf(-1)
        private set
    var contract by mutableIntStateOf(0)
        private set

    /** La carte appelée à 5 joueurs (-1 sinon) et le partenaire une fois découvert (-1 tant qu'on ne le sait pas). */
    var calledCard by mutableIntStateOf(-1)
        private set
    var partner by mutableIntStateOf(-1)
        private set

    /** Vrai si le preneur a annoncé un chelem. */
    var slamAnnounced by mutableStateOf(false)
        private set

    /** Vrai tant que le chien est montré à tous (pendant l'écart). */
    var dogShown by mutableStateOf(false)
        private set

    /** Vrai si la donne vient d'être refaite parce que tout le monde avait passé. */
    var passedOut by mutableStateOf(false)
        private set

    /** Les cartes du pli en cours, dans l'ordre où elles ont été posées. */
    var trick by mutableStateOf<List<TarotPlay>>(emptyList())
        private set

    /** Le joueur qui ramasse le pli quand il est complet ; -1 sinon. */
    var trickWinner by mutableIntStateOf(-1)
        private set

    /** Plis ramassés depuis le début de la donne. */
    var tricksPlayed by mutableIntStateOf(0)
        private set

    /** Les poignées montrées pendant cette donne. */
    var handfuls by mutableStateOf<List<TarotHandful>>(emptyList())
        private set

    /** Les atouts que le preneur a dû écarter : ils sont montrés à tous. */
    var discardedTrumps by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Vrai quand le joueur qui agit a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai quand la donne est finie ([outcome] est alors connu). */
    var roundOver by mutableStateOf(false)
        private set

    /** Vrai quand la manche finie est inscrite au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours (il ne change pas quand la donne est refaite). */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Le résultat de la donne finie ; null tant qu'elle n'est pas finie. */
    var outcome by mutableStateOf<TarotOutcome?>(null)
        private set

    /**
     * Écrit le libellé de la manche dans l'historique (équipe, contrat, bouts, points…) : il dépend de la langue de l'appli, que
     * seul l'écran connaît. Sans lui, [settle] se contente des noms des attaquants.
     */
    var labeler: ((TarotOutcome, Set<Int>) -> String)? = null

    /** Coups joués (enchères, appel, écart, cartes, plis ramassés) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    private var dog: List<Int> = emptyList()
    private var discarded: List<Int> = emptyList()
    private var taken: List<List<Int>> = emptyList()
    private var trickCounts: List<Int> = emptyList()
    private var excusePlayer = -1
    private var excuseWinner = -1
    private var excuseLost = false
    private var lastTrick: List<TarotPlay> = emptyList()
    private var lastWinner = -1

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** Vrai quand tous ont joué dans le pli : on le montre à tous, puis on le ramasse ([collect]). */
    val trickDone: Boolean get() = trick.size == playerCount && playerCount > 0

    /** Vrai quand la donne a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** Le joueur qui agit est-il le preneur ? */
    private fun isAttacker(player: Int): Boolean = player == taker || (partner >= 0 && player == partner)

    /** Les cartes que peut appeler le preneur à 5 joueurs. */
    fun callOptions(): List<Int> = TarotRules.callOptions(hands.getOrElse(taker) { emptyList() })

    /**
     * La table telle que la voit le joueur [viewer] : le public et sa main. [viewer] hors des joueurs (-1) : un spectateur, qui ne
     * voit aucune main.
     */
    fun tableFor(viewer: Int): TarotTable = TarotTable(
        playerCount = playerCount,
        roundNumber = roundNumber,
        dealer = dealer,
        phase = phase,
        current = current,
        bids = bids,
        taker = taker,
        contract = contract,
        calledCard = calledCard,
        partner = partner,
        dog = if (phase == TarotPhase.DISCARD && dogShown) dog else emptyList(),
        slamAnnounced = slamAnnounced,
        trick = trick,
        trickWinner = trickWinner,
        tricksPlayed = tricksPlayed,
        roundOver = roundOver,
        settled = settled,
        started = inProgress,
        passedOut = passedOut,
        handSizes = hands.map { it.size },
        handfuls = handfuls,
        discardedTrumps = discardedTrumps,
        outcome = outcome,
        hand = hands.getOrElse(viewer) { emptyList() }
    )

    /** Le joueur qui agit montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver) revealed = true
    }

    // ---------- Les enchères ----------

    /**
     * Le joueur [seat], qui doit être celui qui parle, annonce [level] : 0 (passe) ou une enchère plus haute que la précédente.
     * Quand le dernier a parlé, le plus haut devient preneur ; si tous ont passé, la donne est refaite. Faux, sans rien
     * changer, si le coup n'est pas permis.
     */
    fun bidFor(seat: Int, level: Int): Boolean {
        if (phase != TarotPhase.BIDDING || seat != current || roundOver) return false
        if (level < TarotBid.PASS || level > TarotBid.GARDE_CONTRE) return false
        val highest = (bids.maxOrNull() ?: 0).coerceAtLeast(0)
        if (level != TarotBid.PASS && level <= highest) return false
        bids = bids.mapIndexed { index, bid -> if (index == seat) level else bid }
        passedOut = false
        moves++
        revealed = false
        if (bids.all { it >= 0 }) endBidding() else current = (current + 1) % playerCount
        return true
    }

    private fun endBidding() {
        val best = bids.maxOrNull() ?: 0
        if (best <= TarotBid.PASS) {
            // Tout le monde a passé : le donneur suivant redistribue.
            dealer = (dealer + 1) % playerCount
            deal()
            passedOut = true
            return
        }
        taker = bids.indexOf(best)
        contract = best
        current = taker
        if (playerCount == 5) {
            phase = TarotPhase.CALLING
        } else {
            startDiscard()
        }
    }

    /** Le preneur à 5 joueurs appelle [card] (un roi, ou une dame s'il a les 4 rois) : son propriétaire est son partenaire. */
    fun callFor(seat: Int, card: Int): Boolean {
        if (phase != TarotPhase.CALLING || seat != current || seat != taker || card !in callOptions()) return false
        calledCard = card
        moves++
        revealed = false
        startDiscard()
        return true
    }

    // ---------- Le chien et l'écart ----------

    private fun startDiscard() {
        current = taker
        phase = TarotPhase.DISCARD
        revealed = false
        if (contract == TarotBid.PETITE || contract == TarotBid.GARDE) {
            // Le chien est montré à tous, puis rejoint la main du preneur, qui écarte autant de cartes.
            dogShown = true
            hands = hands.mapIndexed { index, hand -> if (index == taker) (hand + dog).sorted() else hand }
        }
    }

    /**
     * Le preneur écarte [cards] (voir [TarotRules.isValidDiscard] ; aucune carte en garde sans et en garde contre) et peut
     * annoncer un chelem ([slam]) ; le jeu des plis commence. Faux, sans rien changer, si l'écart n'est pas permis.
     */
    fun discardFor(seat: Int, cards: List<Int>, slam: Boolean): Boolean {
        if (phase != TarotPhase.DISCARD || seat != current || seat != taker) return false
        val count = if (contract == TarotBid.PETITE || contract == TarotBid.GARDE) TarotRules.dogSize(playerCount) else 0
        if (!TarotRules.isValidDiscard(cards, hands[seat], count)) return false
        hands = hands.mapIndexed { index, hand -> if (index == seat) hand.filter { it !in cards } else hand }
        discarded = cards
        discardedTrumps = cards.filter { TarotCards.isTrump(it) }
        slamAnnounced = slam
        if (count > 0) {
            dog = emptyList()
            dogShown = false
        }
        moves++
        phase = TarotPhase.PLAYING
        current = if (slam) taker else (dealer + 1) % playerCount
        revealed = false
        return true
    }

    // ---------- Les poignées ----------

    /**
     * Le joueur [seat], qui doit être celui dont c'est le tour et n'avoir encore posé aucune carte, montre une poignée : il a assez
     * d'atouts pour un niveau (voir [TarotRules.handfulThresholds]) et en montre exactement le nombre demandé (les plus petits).
     * Faux, sans rien changer, si ce n'est pas permis.
     */
    fun handfulFor(seat: Int): Boolean {
        if (phase != TarotPhase.PLAYING || seat != current || trickDone || roundOver) return false
        if (handfuls.any { it.player == seat } || hands[seat].size != TarotRules.handSize(playerCount)) return false
        val trumps = hands[seat].filter { TarotCards.isTrump(it) }
        val level = TarotRules.handfulLevel(playerCount, trumps.size)
        if (level == 0) return false
        val shown = trumps.take(TarotRules.handfulThresholds(playerCount)[level - 1])
        handfuls = handfuls + TarotHandful(seat, level, shown)
        moves++
        return true
    }

    // ---------- Les plis ----------

    /** Vrai si [card] peut être posée maintenant par le joueur qui agit. */
    fun isPlayable(card: Int): Boolean {
        if (phase != TarotPhase.PLAYING || roundOver || trickDone) return false
        return TarotRules.canPlay(card, hands.getOrElse(current) { emptyList() }, trick)
    }

    /**
     * Le joueur [seat], qui doit être celui dont c'est le tour, pose [card] (dans sa main et jouable). Si c'est la dernière carte
     * du pli, il est complet ([trickDone], [trickWinner]) et personne ne joue avant [collect]. Faux, sans rien changer, si le coup
     * n'est pas permis.
     */
    fun playFor(seat: Int, card: Int): Boolean {
        if (phase != TarotPhase.PLAYING || seat != current || trickDone || roundOver) return false
        if (!TarotRules.canPlay(card, hands[seat], trick)) return false
        hands = hands.mapIndexed { index, hand -> if (index == seat) hand - card else hand }
        trick = trick + TarotPlay(seat, card)
        // La carte appelée découvre le partenaire (sauf si le preneur la joue lui-même).
        if (card == calledCard && seat != taker) partner = seat
        moves++
        revealed = false
        if (trickDone) finishTrick() else current = (current + 1) % playerCount
        return true
    }

    private fun finishTrick() {
        val last = hands.all { it.isEmpty() }
        var winner = TarotRules.trickWinner(trick)
        val excusePlay = trick.firstOrNull { TarotCards.isExcuse(it.card) }
        if (excusePlay != null) {
            excusePlayer = excusePlay.player
            val playerAttacks = isAttacker(excusePlay.player)
            val noDefenseTrick = trickCounts.indices.none { !isAttacker(it) && trickCounts[it] > 0 }
            if (last && slamAnnounced && playerAttacks && noDefenseTrick) {
                // Dernier pli d'un chelem annoncé : l'Excuse le gagne.
                winner = excusePlay.player
                excuseLost = false
            } else {
                // Jouée au dernier pli, l'Excuse est perdue si l'autre camp ramasse le pli.
                excuseLost = last && isAttacker(winner) != playerAttacks
            }
            excuseWinner = winner
        }
        trickWinner = winner
        trickCounts = trickCounts.mapIndexed { index, count -> if (index == winner) count + 1 else count }
        val won = trick.map { it.card }
        taken = taken.mapIndexed { index, cards -> if (index == winner) cards + won else cards }
        lastTrick = trick
        lastWinner = winner
    }

    /**
     * Le joueur [seat], qui doit être celui qui a gagné le pli complet, l'empoche : il commence le suivant ; après le dernier pli,
     * la donne est finie. Faux si aucun pli n'est complet ou si ce n'est pas lui.
     */
    fun collectFor(seat: Int): Boolean {
        if (!trickDone || roundOver || seat != trickWinner) return false
        tricksPlayed++
        moves++
        val winner = trickWinner
        trick = emptyList()
        trickWinner = -1
        if (hands.all { it.isEmpty() }) {
            roundOver = true
            outcome = computeOutcome()
        } else {
            current = winner
        }
        revealed = false
        return true
    }

    /** [collectFor] pour le gagnant du pli complet (l'organisateur le fait pour les places jouées sur son téléphone). */
    fun collect(): Boolean = collectFor(trickWinner)

    // ---------- Le comptage ----------

    private fun computeOutcome(): TarotOutcome {
        val attackers = setOfNotNull(taker, if (partner >= 0) partner else null)
        val attackPile = mutableListOf<Int>()
        val defensePile = mutableListOf<Int>()
        for (player in 0 until playerCount) {
            if (player in attackers) attackPile.addAll(taken[player]) else defensePile.addAll(taken[player])
        }
        // L'écart appartient au preneur ; le chien non pris va au preneur (garde sans) ou à la défense (garde contre).
        attackPile.addAll(discarded)
        if (contract == TarotBid.GARDE_SANS) attackPile.addAll(dog)
        if (contract == TarotBid.GARDE_CONTRE) defensePile.addAll(dog)
        placeExcuse(attackers, attackPile, defensePile)

        val attackHalf = attackPile.sumOf { TarotCards.halfPoints(it) }
        val bouts = attackPile.count { TarotCards.isOudler(it) }
        val required = tarotRequiredPoints(bouts)
        val success = attackHalf >= 2 * required
        // Le demi-point est arrondi en faveur du camp qui gagne.
        val points = if (success) (attackHalf + 1) / 2 else attackHalf / 2

        var petit = 0
        val petitPlay = lastTrick.firstOrNull { it.card == TarotCards.PETIT }
        if (petitPlay != null) {
            val playerAttacks = petitPlay.player in attackers
            if (playerAttacks == (lastWinner in attackers)) petit = if (playerAttacks) 1 else 2
        }

        val levels = handfuls.map { it.level }
        val mainLevel = levels.maxOrNull() ?: 0
        val extra = levels.sumOf { TarotRules.handfulPoints(it) } - TarotRules.handfulPoints(mainLevel)
        val defenseSlam = attackers.sumOf { trickCounts.getOrElse(it) { 0 } } == 0

        val result = computeTarotRound(
            playerCount = playerCount,
            taker = taker,
            partner = if (partner >= 0) partner else null,
            factor = TarotBid.factor(contract),
            bouts = bouts,
            points = points,
            petitAuBout = petit,
            handful = mainLevel,
            slamAnnounced = slamAnnounced,
            defenseSlam = defenseSlam,
            extraHandful = extra
        )
        return TarotOutcome(
            taker = taker,
            partner = partner,
            contract = contract,
            attackHalf = attackHalf,
            bouts = bouts,
            required = required,
            points = points,
            difference = result.difference,
            success = result.success,
            petitAuBout = petit,
            handful = mainLevel,
            extraHandful = extra,
            slamAnnounced = slamAnnounced,
            slamSucceeded = result.slamSucceeded,
            defenseSlam = result.defenseSlamActive,
            unit = result.unit,
            deltas = result.deltas ?: List(playerCount) { 0 }
        )
    }

    /**
     * Remet l'Excuse dans le tas de son propriétaire. Si elle a été jouée dans un pli ramassé par l'autre camp, son propriétaire
     * donne en échange une carte basse (sans valeur, sinon la moins chère) ; s'il n'a rien à donner, ou si elle a été perdue au
     * dernier pli, c'est l'autre camp qui la garde.
     */
    private fun placeExcuse(attackers: Set<Int>, attackPile: MutableList<Int>, defensePile: MutableList<Int>) {
        if (excusePlayer < 0) return
        attackPile.removeAll { it == TarotCards.EXCUSE }
        defensePile.removeAll { it == TarotCards.EXCUSE }
        val ownerAttacks = excusePlayer in attackers
        val winnerAttacks = excuseWinner in attackers
        val ownerPile = if (ownerAttacks) attackPile else defensePile
        val otherPile = if (ownerAttacks) defensePile else attackPile
        if (excuseLost) {
            (if (winnerAttacks) attackPile else defensePile).add(TarotCards.EXCUSE)
            return
        }
        ownerPile.add(TarotCards.EXCUSE)
        if (ownerAttacks == winnerAttacks) return
        val swap = ownerPile.filter { !TarotCards.isOudler(it) }.minByOrNull { TarotCards.halfPoints(it) }
        if (swap == null) {
            ownerPile.removeAll { it == TarotCards.EXCUSE }
            otherPile.add(TarotCards.EXCUSE)
        } else {
            ownerPile.remove(swap)
            otherPile.add(swap)
        }
    }

    // ---------- Manches et scores ----------

    /**
     * Inscrit la manche finie au tableau des scores (une seule fois), comme la saisie de la feuille de Tarot : le score de chaque
     * joueur, avec la saisie d'origine pour pouvoir la corriger. Renvoie vrai si la manche vient d'être inscrite.
     */
    fun settle(viewModel: ScoreViewModel): Boolean {
        if (!roundOver || settled) return false
        val result = outcome ?: return false
        val attackers = setOfNotNull(taker, if (partner >= 0) partner else null)
        val label = labeler?.invoke(result, attackers) ?: attackers.joinToString(" + ") { viewModel.players.getOrElse(it) { "" } }
        viewModel.appendTeamRound(
            TeamRound(
                teamALabel = label,
                teamAPlayers = attackers,
                value = result.unit,
                tarotInput = TarotRoundInput(
                    taker = taker,
                    partner = if (partner >= 0) partner else null,
                    multiplierIndex = (result.contract - 1).coerceIn(0, 3),
                    bouts = result.bouts,
                    points = result.points,
                    petitAuBout = result.petitAuBout,
                    handful = result.handful,
                    slamAnnounced = result.slamAnnounced,
                    defenseSlam = result.defenseSlam
                ),
                deltas = result.deltas
            )
        )
        settled = true
        return true
    }

    /** Distribue la manche suivante (le donneur change) ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        roundNumber++
        dealer = (dealer + 1) % playerCount
        deal()
        return true
    }

    /** Mélange et distribue le jeu : la donne repart des enchères, le joueur après le donneur parle le premier. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val size = TarotRules.handSize(count)
        val dogSize = TarotRules.dogSize(count)
        var dealt: List<List<Int>>
        var newDog: List<Int>
        var attempts = 0
        do {
            val deck = (0 until TarotCards.DECK).toMutableList()
            shuffle(deck)
            dealt = List(count) { player -> deck.subList(player * size, (player + 1) * size).sorted() }
            newDog = deck.subList(count * size, count * size + dogSize).toList()
            attempts++
        } while (attempts < 100 && dealt.any { TarotRules.isPetitSec(it) })
        hands = dealt
        dog = newDog
        discarded = emptyList()
        discardedTrumps = emptyList()
        taken = List(count) { emptyList() }
        trickCounts = List(count) { 0 }
        bids = List(count) { -1 }
        taker = -1
        contract = 0
        calledCard = -1
        partner = -1
        slamAnnounced = false
        dogShown = false
        passedOut = false
        trick = emptyList()
        trickWinner = -1
        tricksPlayed = 0
        handfuls = emptyList()
        excusePlayer = -1
        excuseWinner = -1
        excuseLost = false
        lastTrick = emptyList()
        lastWinner = -1
        roundOver = false
        settled = false
        outcome = null
        moves = 0
        phase = TarotPhase.BIDDING
        current = (dealer + 1) % count
        revealed = false
    }

    /** Installe une donne précise (mains, chien) au début des enchères : sert aux tests. */
    internal fun load(hands: List<List<Int>>, dog: List<Int>, dealer: Int = 0) {
        require(hands.size == playerCount && dealer in 0 until playerCount)
        this.hands = hands.map { it.sorted() }
        this.dog = dog
        this.dealer = dealer
        bids = List(playerCount) { -1 }
        taker = -1
        contract = 0
        calledCard = -1
        partner = -1
        slamAnnounced = false
        dogShown = false
        passedOut = false
        discarded = emptyList()
        discardedTrumps = emptyList()
        taken = List(playerCount) { emptyList() }
        trickCounts = List(playerCount) { 0 }
        trick = emptyList()
        trickWinner = -1
        tricksPlayed = 0
        handfuls = emptyList()
        excusePlayer = -1
        excuseWinner = -1
        excuseLost = false
        lastTrick = emptyList()
        lastWinner = -1
        roundOver = false
        settled = false
        outcome = null
        moves = 0
        phase = TarotPhase.BIDDING
        current = (dealer + 1) % playerCount
        revealed = false
    }
}
