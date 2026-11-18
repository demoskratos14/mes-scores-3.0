package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant du Rami parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val RAMI_ID = "builtin_rami"

/**
 * Une combinaison posée sur la table, cartes rangées dans l'ordre.
 *
 * - Suite ([run] vrai) : au moins 3 cartes de la même couleur qui se suivent. [key] est la position de la première carte, de 1
 *   (l'as placé avant le 2) à 14 (l'as placé après le roi) ; la carte d'indice `i` est à la position `key + i`. Un joker tient la
 *   place de la carte de sa position.
 * - Brelan ou carré ([run] faux) : 3 ou 4 cartes de même valeur et de couleurs différentes. [key] est la valeur (1 à 13) ; les
 *   cartes naturelles sont d'abord, les jokers à la fin.
 */
internal data class RamiMeld(val cards: List<Int>, val run: Boolean, val key: Int)

/** Ce que donne une pose de cartes. */
internal enum class RamiCheck { OK, INVALID, TOO_FEW_POINTS }

/**
 * Règles des cartes du Rami : voir la fiche de règles du jeu.
 *
 * Le paquet a 108 cartes : deux jeux de 52 cartes ([FrenchCards], 0 à 51 puis 52 à 103, la valeur et la couleur d'une carte se
 * lisent avec [base]) et quatre jokers (104 à 107).
 */
internal object RamiScoring {

    /** Cartes des deux jeux de 52 cartes, sans les jokers. */
    const val NATURALS = FrenchCards.DECK * 2

    /** Jokers du paquet. */
    const val JOKERS = 4

    /** Cartes du paquet : deux jeux de 52 cartes et 4 jokers. */
    const val DECK = NATURALS + JOKERS

    /** Cartes distribuées à chacun. */
    const val HAND_SIZE = 13

    /** Points que doit valoir la première pose d'un joueur (son « ouverture »). */
    const val OPEN_MIN = 51

    /** Points d'un joker resté en main en fin de manche. */
    const val JOKER_HAND_POINTS = 25

    /** Position de l'as placé après le roi dans une suite. */
    const val ACE_HIGH = 14

    /** Cartes d'un brelan ou d'un carré, au plus. */
    const val MAX_SET = 4

    /** Cartes qu'on peut poser d'un coup : les 2 ^ 14 découpages possibles restent vite calculés. */
    const val MAX_LAYOUT = 14

    /** Vrai pour un joker. */
    fun isJoker(card: Int): Boolean = card >= NATURALS

    /** La carte du jeu de 52 cartes (0 à 51) qui a la valeur et la couleur de [card] (qui ne doit pas être un joker). */
    fun base(card: Int): Int = card % FrenchCards.DECK

    /** Valeur de [card], de 1 (as) à 13 (roi). */
    fun rank(card: Int): Int = FrenchCards.rank(base(card))

    /** Couleur de [card], de 0 à 3. */
    fun suit(card: Int): Int = FrenchCards.suit(base(card))

    /** Points d'une valeur (1 à 13) dans un brelan ou un carré : as = 11, figures = 10, les autres = leur valeur. */
    private fun rankPoints(value: Int): Int = if (value == 1) 11 else minOf(value, 10)

    /** Points d'une position (1 à 14) dans une suite : as bas = 1, as haut = 11, figures = 10, les autres = leur valeur. */
    private fun positionPoints(position: Int): Int = when (position) {
        1 -> 1
        ACE_HIGH -> 11
        else -> minOf(position, 10)
    }

    /** Points d'une carte restée en main : joker = 25, as = 11, figures = 10, les autres = leur valeur. */
    fun points(card: Int): Int = if (isJoker(card)) JOKER_HAND_POINTS else rankPoints(rank(card))

    /** Points d'une main. */
    fun handPoints(hand: List<Int>): Int = hand.sumOf { points(it) }

    /** Points d'une combinaison posée (un joker vaut la carte qu'il remplace) : ils servent à l'ouverture. */
    fun meldPoints(meld: RamiMeld): Int =
        if (meld.run) meld.cards.indices.sumOf { positionPoints(meld.key + it) } else meld.cards.size * rankPoints(meld.key)

    /** Clé de tri d'une main : par couleur puis par valeur (les deux jeux côte à côte), les jokers à la fin. */
    fun sortKey(card: Int): Int = if (isJoker(card)) 1000 + card else suit(card) * 100 + rank(card) * 2 + card / FrenchCards.DECK

    // ---------- Les combinaisons ----------

    /** Brelan ou carré : 3 ou 4 cartes de même valeur, de couleurs différentes, dont au moins 2 sans joker. */
    private fun asSet(cards: List<Int>): RamiMeld? {
        if (cards.size < 3 || cards.size > MAX_SET) return null
        val naturals = cards.filterNot { isJoker(it) }
        if (naturals.size < 2) return null
        val value = rank(naturals.first())
        if (naturals.any { rank(it) != value }) return null
        if (naturals.map { suit(it) }.toSet().size != naturals.size) return null
        return RamiMeld(naturals.sortedBy { suit(it) } + cards.filter { isJoker(it) }, run = false, key = value)
    }

    /**
     * Suite : au moins 3 cartes de la même couleur qui se suivent, dont au moins 2 sans joker. Un joker remplace une carte
     * manquante ou prolonge la suite (vers le roi d'abord, puis vers l'as). L'as se place avant le 2 ou après le roi, pas les deux
     * (sauf avec deux as) ; à choix égal, on garde le placement qui vaut le plus de points.
     */
    private fun asRun(cards: List<Int>): RamiMeld? {
        if (cards.size < 3 || cards.size > ACE_HIGH) return null
        val naturals = cards.filterNot { isJoker(it) }
        val jokers = cards.filter { isJoker(it) }
        if (naturals.size < 2) return null
        val color = suit(naturals.first())
        if (naturals.any { suit(it) != color }) return null
        val aces = naturals.filter { rank(it) == 1 }
        if (aces.size > 2) return null
        // Combien d'as sont placés après le roi : aucun ou un avec un seul as, un des deux avec deux as.
        val highChoices = when (aces.size) {
            0 -> listOf(0)
            1 -> listOf(0, 1)
            else -> listOf(1)
        }
        var best: RamiMeld? = null
        for (high in highChoices) {
            val others = naturals.filter { rank(it) != 1 }.map { rank(it) to it }
            val acePlaces = aces.mapIndexed { index, card -> (if (index < aces.size - high) 1 else ACE_HIGH) to card }
            val placed = (others + acePlaces).sortedBy { it.first }
            val positions = placed.map { it.first }
            if (positions.toSet().size != positions.size) continue
            val low = positions.first()
            val top = positions.last()
            val gaps = top - low + 1 - placed.size
            if (gaps > jokers.size) continue
            val spare = jokers.size - gaps
            val above = minOf(spare, ACE_HIGH - top)
            val below = spare - above
            if (below > low - 1) continue
            val free = jokers.toMutableList()
            val result = mutableListOf<Int>()
            repeat(below) { result += free.removeAt(0) }
            placed.forEachIndexed { index, place ->
                if (index > 0) repeat(place.first - placed[index - 1].first - 1) { result += free.removeAt(0) }
                result += place.second
            }
            repeat(above) { result += free.removeAt(0) }
            val meld = RamiMeld(result, run = true, key = low - below)
            val current = best
            if (current == null || meldPoints(meld) > meldPoints(current)) best = meld
        }
        return best
    }

    /** La combinaison que forment exactement [cards] (sans doublon) : un brelan, un carré ou une suite ; null sinon. */
    fun meldOf(cards: List<Int>): RamiMeld? {
        if (cards.toSet().size != cards.size) return null
        val set = asSet(cards)
        val run = asRun(cards)
        return when {
            set == null -> run
            run == null -> set
            meldPoints(set) >= meldPoints(run) -> set
            else -> run
        }
    }

    /**
     * Découpe [cards] en combinaisons qui les utilisent toutes ; parmi les découpages possibles, celui qui vaut le plus de points.
     * Null si ce n'est pas possible (ou s'il y a plus de [MAX_LAYOUT] cartes).
     */
    fun layout(cards: List<Int>): List<RamiMeld>? {
        val count = cards.size
        if (count < 3 || count > MAX_LAYOUT) return null
        val full = (1 shl count) - 1
        val melds = arrayOfNulls<RamiMeld>(full + 1)
        for (mask in 1..full) {
            if (Integer.bitCount(mask) < 3) continue
            melds[mask] = meldOf(cards.filterIndexed { index, _ -> (mask and (1 shl index)) != 0 })
        }
        // best[mask] : le plus de points qu'on tire en découpant les cartes de mask ; -1 si c'est impossible.
        val best = IntArray(full + 1) { -1 }
        val choice = IntArray(full + 1)
        best[0] = 0
        for (mask in 1..full) {
            val lowest = mask and -mask
            var sub = mask
            while (sub > 0) {
                if ((sub and lowest) != 0) {
                    val meld = melds[sub]
                    val rest = best[mask xor sub]
                    if (meld != null && rest >= 0) {
                        val total = meldPoints(meld) + rest
                        if (total > best[mask]) {
                            best[mask] = total
                            choice[mask] = sub
                        }
                    }
                }
                sub = (sub - 1) and mask
            }
        }
        if (best[full] < 0) return null
        val result = mutableListOf<RamiMeld>()
        var mask = full
        while (mask != 0) {
            val sub = choice[mask]
            result += melds[sub] ?: return null
            mask = mask xor sub
        }
        return result
    }

    /** Ce que donne la pose de [cards] : valable, pas des combinaisons, ou une première pose qui ne vaut pas [OPEN_MIN] points. */
    fun checkMeld(cards: List<Int>, opened: Boolean): RamiCheck {
        val parts = layout(cards) ?: return RamiCheck.INVALID
        return if (opened || parts.sumOf { meldPoints(it) } >= OPEN_MIN) RamiCheck.OK else RamiCheck.TOO_FEW_POINTS
    }

    // ---------- Compléter une combinaison de la table ----------

    /** Vrai si [card] (une carte naturelle) peut tenir la position [position] d'une suite. */
    private fun fits(card: Int, position: Int): Boolean =
        if (rank(card) == 1) position == 1 || position == ACE_HIGH else rank(card) == position

    private fun runSuit(meld: RamiMeld): Int? = meld.cards.firstOrNull { !isJoker(it) }?.let { suit(it) }

    /**
     * [meld] complétée par [card] : une suite se prolonge à son bout haut ou, sinon, à son bout bas ; un brelan devient un carré.
     * Un joker va au bout haut d'une suite tant qu'il y a de la place. Null si la carte ne va pas.
     */
    fun attach(meld: RamiMeld, card: Int): RamiMeld? {
        if (meld.run) {
            val end = meld.key + meld.cards.size - 1
            if (isJoker(card)) {
                return when {
                    end < ACE_HIGH -> meld.copy(cards = meld.cards + card)
                    meld.key > 1 -> meld.copy(cards = listOf(card) + meld.cards, key = meld.key - 1)
                    else -> null
                }
            }
            if (suit(card) != runSuit(meld)) return null
            if (end < ACE_HIGH && fits(card, end + 1)) return meld.copy(cards = meld.cards + card)
            if (meld.key > 1 && fits(card, meld.key - 1)) return meld.copy(cards = listOf(card) + meld.cards, key = meld.key - 1)
            return null
        }
        if (meld.cards.size >= MAX_SET) return null
        if (isJoker(card)) return meld.copy(cards = meld.cards + card)
        if (rank(card) != meld.key) return null
        if (meld.cards.any { !isJoker(it) && suit(it) == suit(card) }) return null
        val naturals = meld.cards.filterNot { isJoker(it) } + card
        return meld.copy(cards = naturals.sortedBy { suit(it) } + meld.cards.filter { isJoker(it) })
    }

    /** [meld] complétée par toutes les cartes de [cards], dans l'ordre où elles vont ; null si l'une d'elles ne va pas. */
    fun attachAll(meld: RamiMeld, cards: List<Int>): RamiMeld? {
        var current = meld
        val left = cards.toMutableList()
        while (left.isNotEmpty()) {
            val next = left.firstOrNull { attach(current, it) != null } ?: return null
            current = attach(current, next) ?: return null
            left.remove(next)
        }
        return current
    }

    /**
     * Remplace un joker de [meld] par [card], la carte naturelle qu'il tenait : renvoie la combinaison et le joker repris ; null
     * si [card] ne prend la place d'aucun joker.
     */
    fun swapJoker(meld: RamiMeld, card: Int): Pair<RamiMeld, Int>? {
        if (isJoker(card)) return null
        if (meld.run) {
            if (suit(card) != runSuit(meld)) return null
            val index = meld.cards.indices.firstOrNull { isJoker(meld.cards[it]) && fits(card, meld.key + it) } ?: return null
            val joker = meld.cards[index]
            val cards = meld.cards.toMutableList()
            cards[index] = card
            return meld.copy(cards = cards) to joker
        }
        if (rank(card) != meld.key) return null
        if (meld.cards.any { !isJoker(it) && suit(it) == suit(card) }) return null
        val jokers = meld.cards.filter { isJoker(it) }
        if (jokers.isEmpty()) return null
        val naturals = (meld.cards.filterNot { isJoker(it) } + card).sortedBy { suit(it) }
        return meld.copy(cards = naturals + jokers.drop(1)) to jokers.first()
    }
}

/** Où en est le tour : le joueur pioche, puis il pose et défausse. */
internal enum class RamiPhase { DRAW, PLAY }

/**
 * La table vue par un joueur : tout ce qui est public (combinaisons posées, dernière défausse, taille des mains), plus sa propre
 * main ([hand]). C'est ce que l'organisateur envoie à chaque invité : jamais la main des autres, sauf en fin de manche
 * ([finalHands]).
 */
internal data class RamiTable(
    val playerCount: Int,
    val roundNumber: Int,
    val current: Int,
    /** Vrai quand le joueur dont c'est le tour doit encore piocher. */
    val drawing: Boolean,
    /** La carte du dessus de la défausse ; -1 s'il n'y en a pas. */
    val topDiscard: Int,
    val stockSize: Int,
    val handSizes: List<Int>,
    /** Pour chaque joueur : a-t-il fait sa première pose ? */
    val opened: List<Boolean>,
    val melds: List<RamiMeld>,
    val roundOver: Boolean,
    val winner: Int,
    val settled: Boolean,
    val started: Boolean,
    val points: List<Int>,
    /** Les mains de tous les joueurs, une fois la manche finie ; vide avant. */
    val finalHands: List<List<Int>>,
    val hand: List<Int>,
    val canDrawFromStock: Boolean
) {
    /** Points de la main du joueur. */
    val handPoints: Int get() = RamiScoring.handPoints(hand)
}

/**
 * Une partie de Rami jouée tour par tour sur un seul téléphone que l'on se passe (2 à 6 joueurs).
 *
 * - L'appli distribue 13 cartes à chacun et retourne une carte (la défausse). Le joueur dont c'est le tour voit sa main une fois
 *   qu'il l'a demandée ([reveal]).
 * - À son tour, il pioche ([drawFromStock] ou [takeDiscard]), puis peut poser des combinaisons ([meld]), compléter celles de la
 *   table ([attach]), reprendre un joker ([swapJoker]), et il finit son tour en défaussant une carte ([discard]).
 * - Sa première pose doit valoir [RamiScoring.OPEN_MIN] points ; tant qu'il n'a pas posé, il ne peut pas compléter la table.
 * - La manche s'arrête quand un joueur n'a plus de carte en main : il marque 0 et les autres les points de leur main
 *   ([roundPoints]). [settle] inscrit ces points dans une ligne libre du tableau des scores (le total de chacun, la fin de partie
 *   et le journal viennent de ce tableau, le plus petit total gagnant) ; [nextRound] distribue la manche suivante.
 *
 * [startPlayer] : le joueur qui commence. [startRound] : numéro de la manche. [shuffle] mélange une liste de cartes (remplaçable
 * dans les tests).
 */
internal class RamiRound(
    val playerCount: Int,
    startPlayer: Int = 0,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    private var starter = startPlayer.coerceIn(0, (playerCount - 1).coerceAtLeast(0))

    /** Le joueur qui joue. */
    var current by mutableIntStateOf(starter)
        private set

    /** Les mains de chaque joueur (même ordre que les joueurs). */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** La pioche : la prochaine carte à piocher est la première. */
    var stock by mutableStateOf<List<Int>>(emptyList())
        private set

    /** La défausse : la dernière carte est celle du dessus, la seule qu'on peut reprendre. */
    var discards by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les combinaisons posées sur la table, dans l'ordre où elles ont été posées. */
    var melds by mutableStateOf<List<RamiMeld>>(emptyList())
        private set

    /** Pour chaque joueur : a-t-il fait sa première pose ? */
    var opened by mutableStateOf<List<Boolean>>(emptyList())
        private set

    /** Piocher, puis poser et défausser. */
    var phase by mutableStateOf(RamiPhase.DRAW)
        private set

    /** Vrai quand le joueur dont c'est le tour a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai quand la manche est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Celui qui s'est débarrassé de toutes ses cartes ; -1 tant que la manche n'est pas finie. */
    var winner by mutableIntStateOf(-1)
        private set

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Coups joués (pioches, poses, défausses) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** La main du joueur qui joue. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** La carte du dessus de la défausse ; -1 s'il n'y en a pas. */
    val topDiscard: Int get() = discards.lastOrNull() ?: -1

    /** Vrai s'il reste de quoi piocher, en remélangeant au besoin la défausse (sauf sa carte du dessus). */
    val canDrawFromStock: Boolean get() = stock.isNotEmpty() || discards.size > 1

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** Points de chacun pour la manche : 0 pour celui qui a fini, les points de leur main pour les autres. */
    fun roundPoints(): List<Int> = hands.mapIndexed { player, hand ->
        if (roundOver && player == winner) 0 else RamiScoring.handPoints(hand)
    }

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver) revealed = true
    }

    /** Le joueur pioche la première carte de la pioche (remélangée au besoin) ; faux si ce n'est pas permis. */
    fun drawFromStock(): Boolean {
        if (!revealed || roundOver || phase != RamiPhase.DRAW || !canDrawFromStock) return false
        if (stock.isEmpty()) {
            val refill = discards.dropLast(1).toMutableList()
            shuffle(refill)
            stock = refill
            discards = discards.takeLast(1)
        }
        val card = stock.first()
        stock = stock.drop(1)
        takeCard(card)
        return true
    }

    /** Le joueur reprend la carte du dessus de la défausse ; faux si ce n'est pas permis. */
    fun takeDiscard(): Boolean {
        if (!revealed || roundOver || phase != RamiPhase.DRAW || discards.isEmpty()) return false
        val card = discards.last()
        discards = discards.dropLast(1)
        takeCard(card)
        return true
    }

    private fun takeCard(card: Int) {
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand + card else hand }
        phase = RamiPhase.PLAY
        moves++
    }

    /**
     * Pose [cards] (qui doivent être dans la main du joueur) en une ou plusieurs combinaisons ([RamiScoring.layout]). La première
     * pose d'un joueur doit valoir au moins [RamiScoring.OPEN_MIN] points. Faux, sans rien changer, si ce n'est pas permis.
     */
    fun meld(cards: List<Int>): Boolean {
        if (!canPlay() || !inHand(cards)) return false
        if (RamiScoring.checkMeld(cards, opened[current]) != RamiCheck.OK) return false
        val parts = RamiScoring.layout(cards) ?: return false
        melds = melds + parts
        opened = opened.mapIndexed { index, done -> done || index == current }
        removeFromHand(cards)
        moves++
        finishIfEmpty()
        return true
    }

    /** Ajoute [cards] à la combinaison [meldIndex] de la table (le joueur doit avoir fait sa première pose) ; faux si ce n'est pas permis. */
    fun attach(cards: List<Int>, meldIndex: Int): Boolean {
        if (!canPlay() || !opened[current] || !inHand(cards)) return false
        val target = melds.getOrNull(meldIndex) ?: return false
        val updated = RamiScoring.attachAll(target, cards) ?: return false
        melds = melds.mapIndexed { index, meld -> if (index == meldIndex) updated else meld }
        removeFromHand(cards)
        moves++
        finishIfEmpty()
        return true
    }

    /**
     * Met [card] à la place d'un joker de la combinaison [meldIndex] ; le joker rejoint la main du joueur. Le joueur doit avoir
     * fait sa première pose. Faux si ce n'est pas permis.
     */
    fun swapJoker(card: Int, meldIndex: Int): Boolean {
        if (!canPlay() || !opened[current] || !inHand(listOf(card))) return false
        val target = melds.getOrNull(meldIndex) ?: return false
        val (updated, joker) = RamiScoring.swapJoker(target, card) ?: return false
        melds = melds.mapIndexed { index, meld -> if (index == meldIndex) updated else meld }
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand.filterNot { it == card } + joker else hand }
        moves++
        return true
    }

    /** Défausse [card], ce qui finit le tour (ou la manche, si c'était la dernière carte) ; faux si ce n'est pas permis. */
    fun discard(card: Int): Boolean {
        if (!canPlay() || card !in currentHand) return false
        removeFromHand(listOf(card))
        discards = discards + card
        moves++
        if (!finishIfEmpty()) {
            phase = RamiPhase.DRAW
            current = (current + 1) % playerCount.coerceAtLeast(1)
            revealed = false
        }
        return true
    }

    private fun canPlay(): Boolean = revealed && !roundOver && phase == RamiPhase.PLAY

    private fun inHand(cards: List<Int>): Boolean =
        cards.isNotEmpty() && cards.toSet().size == cards.size && cards.all { it in currentHand }

    private fun removeFromHand(cards: List<Int>) {
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand.filterNot { it in cards } else hand }
    }

    /** Finit la manche si le joueur n'a plus de carte ; renvoie vrai dans ce cas. */
    private fun finishIfEmpty(): Boolean {
        if (currentHand.isNotEmpty()) return false
        roundOver = true
        winner = current
        revealed = false
        return true
    }

    // ---------- Les joueurs d'un autre téléphone ----------
    // Un invité n'a pas à « voir sa main » sur le téléphone de l'organisateur : on la dévoile le temps de son coup.

    /**
     * La table telle que la voit le joueur [viewer] : le public et sa main. [viewer] hors des joueurs (-1) : un spectateur, qui
     * ne voit aucune main avant la fin de la manche.
     */
    fun tableFor(viewer: Int): RamiTable = RamiTable(
        playerCount = playerCount,
        roundNumber = roundNumber,
        current = current,
        drawing = phase == RamiPhase.DRAW,
        topDiscard = topDiscard,
        stockSize = stock.size,
        handSizes = hands.map { it.size },
        opened = opened,
        melds = melds,
        roundOver = roundOver,
        winner = winner,
        settled = settled,
        started = inProgress,
        points = if (roundOver) roundPoints() else hands.map { 0 },
        finalHands = if (roundOver) hands.map { list -> list.sortedBy { RamiScoring.sortKey(it) } } else emptyList(),
        hand = hands.getOrElse(viewer) { emptyList() }.sortedBy { RamiScoring.sortKey(it) },
        canDrawFromStock = canDrawFromStock
    )

    /** [drawFromStock] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun drawFromStockFor(seat: Int): Boolean = asSeat(seat) { drawFromStock() }

    /** [takeDiscard] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun takeDiscardFor(seat: Int): Boolean = asSeat(seat) { takeDiscard() }

    /** [meld] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun meldFor(seat: Int, cards: List<Int>): Boolean = asSeat(seat) { meld(cards) }

    /** [attach] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun attachFor(seat: Int, cards: List<Int>, meldIndex: Int): Boolean = asSeat(seat) { attach(cards, meldIndex) }

    /** [swapJoker] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun swapJokerFor(seat: Int, card: Int, meldIndex: Int): Boolean = asSeat(seat) { swapJoker(card, meldIndex) }

    /** [discard] pour le joueur [seat], qui doit être celui dont c'est le tour. */
    fun discardFor(seat: Int, card: Int): Boolean = asSeat(seat) { discard(card) }

    // La main redevient cachée sur ce téléphone dès le coup joué : une place qui revient à l'organisateur ne dévoile rien.
    private fun asSeat(seat: Int, action: () -> Boolean): Boolean {
        if (seat != current || roundOver) return false
        val before = revealed
        revealed = true
        val done = action()
        // Un tour qui continue (pose, pioche) garde la main cachée sur ce téléphone ; la manche finie ou le tour passé l'ont déjà cachée.
        revealed = before && !roundOver && seat == current
        return done
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

    /** Distribue la manche suivante, commencée par le joueur qui suit celui qui avait commencé ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        roundNumber++
        starter = (starter + 1) % playerCount.coerceAtLeast(1)
        deal()
        return true
    }

    /** Mélange les 108 cartes, distribue les mains et retourne la première carte de la défausse. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val deck = (0 until RamiScoring.DECK).toMutableList()
        shuffle(deck)
        val newHands = List(count) { player ->
            deck.subList(player * RamiScoring.HAND_SIZE, (player + 1) * RamiScoring.HAND_SIZE).toList()
        }
        val rest = deck.drop(count * RamiScoring.HAND_SIZE).toMutableList()
        val first = rest.removeAt(0)
        hands = newHands
        stock = rest
        discards = listOf(first)
        clearTurn()
    }

    private fun clearTurn() {
        melds = emptyList()
        opened = List(playerCount.coerceAtLeast(1)) { false }
        phase = RamiPhase.DRAW
        current = starter
        revealed = false
        roundOver = false
        winner = -1
        settled = false
        moves = 0
    }

    /** Installe une table précise (mains, pioche, défausse, combinaisons déjà posées) : sert aux tests. */
    internal fun load(
        hands: List<List<Int>>,
        stock: List<Int>,
        discards: List<Int>,
        current: Int = 0,
        melds: List<RamiMeld> = emptyList(),
        opened: List<Boolean> = List(playerCount) { false }
    ) {
        require(hands.size == playerCount && discards.isNotEmpty() && current in 0 until playerCount && opened.size == playerCount)
        this.hands = hands
        this.stock = stock
        this.discards = discards
        clearTurn()
        this.current = current
        this.melds = melds
        this.opened = opened
    }
}
