package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant de Yaniv parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val YANIV_ID = "builtin_yaniv"

/**
 * Règles des cartes de Yaniv : voir la fiche de règles du jeu.
 *
 * Le paquet a 54 cartes : les 52 de [FrenchCards] (0 à 51) et deux jokers (52 et 53).
 */
internal object YanivScoring {

    /** Cartes du paquet : 52 cartes et 2 jokers. */
    const val DECK = FrenchCards.DECK + 2

    /** Cartes distribuées à chacun. */
    const val HAND_SIZE = 5

    /** Points maximum d'une main pour pouvoir annoncer « Yaniv ». */
    const val YANIV_MAX = 5

    /** Points ajoutés à celui qui annonce « Yaniv » et se fait battre (« Assaf »). */
    const val ASSAF_PENALTY = 30

    /** Vrai pour un joker. */
    fun isJoker(card: Int): Boolean = card >= FrenchCards.DECK

    /** Points d'une carte restée en main : joker = 0, as = 1, figures = 10, les autres = leur valeur. */
    fun points(card: Int): Int = if (isJoker(card)) 0 else minOf(FrenchCards.rank(card), 10)

    /** Points d'une main. */
    fun handPoints(hand: List<Int>): Int = hand.sumOf { points(it) }

    /** Vrai pour une paire, un brelan ou un carré : au moins 2 cartes de même valeur, le joker remplaçant n'importe laquelle. */
    fun isSet(cards: List<Int>): Boolean =
        cards.size >= 2 && cards.filterNot { isJoker(it) }.map { FrenchCards.rank(it) }.toSet().size <= 1

    /**
     * Une suite d'au moins 3 cartes de la même couleur, rangées dans l'ordre de la suite (l'as ne vaut que 1 : pas de suite
     * qui passe du roi à l'as). Le joker remplace une carte manquante ou prolonge la suite. Null si ce n'est pas une suite.
     */
    fun orderedRun(cards: List<Int>): List<Int>? {
        if (cards.size < 3 || cards.size > FrenchCards.RANKS) return null
        val jokers = cards.filter { isJoker(it) }
        val naturals = cards.filterNot { isJoker(it) }
        if (naturals.isEmpty()) return cards
        val suit = FrenchCards.suit(naturals.first())
        if (naturals.any { FrenchCards.suit(it) != suit }) return null
        val sorted = naturals.sortedBy { FrenchCards.rank(it) }
        if (sorted.map { FrenchCards.rank(it) }.toSet().size != sorted.size) return null
        val gaps = sorted.zipWithNext { a, b -> FrenchCards.rank(b) - FrenchCards.rank(a) - 1 }.sum()
        if (gaps > jokers.size) return null
        val spare = jokers.toMutableList()
        val result = mutableListOf<Int>()
        sorted.forEachIndexed { index, card ->
            if (index > 0) repeat(FrenchCards.rank(card) - FrenchCards.rank(sorted[index - 1]) - 1) { result += spare.removeAt(0) }
            result += card
        }
        // Les jokers qui restent prolongent la suite : vers le roi tant qu'il y a de la place, puis vers l'as.
        var lastRank = FrenchCards.rank(sorted.last())
        while (spare.isNotEmpty()) {
            if (lastRank < FrenchCards.RANKS) {
                result += spare.removeAt(0)
                lastRank++
            } else {
                result.add(0, spare.removeAt(0))
            }
        }
        return result
    }

    /** Vrai si [cards] (sans doublon) est une défausse permise : une carte seule, une paire / un brelan / un carré, ou une suite. */
    fun isValidDiscard(cards: List<Int>): Boolean =
        cards.isNotEmpty() && cards.toSet().size == cards.size && (cards.size == 1 || isSet(cards) || orderedRun(cards) != null)

    /** Les cartes de la défausse [cards] dans l'ordre où on les pose (une suite est rangée dans l'ordre de la suite). */
    fun arrange(cards: List<Int>): List<Int> =
        if (cards.size >= 3 && !isSet(cards)) orderedRun(cards) ?: cards else cards.sortedBy { sortKey(it) }

    /**
     * Les cartes qu'on peut reprendre dans la dernière défausse [last] (rangée par [arrange]) : n'importe laquelle pour une carte
     * seule ou une paire, un brelan ou un carré ; seulement la première ou la dernière pour une suite.
     */
    fun drawable(last: List<Int>): List<Int> =
        if (last.size >= 3 && !isSet(last)) listOf(last.first(), last.last()) else last

    /** Clé de tri d'une main : par couleur puis par valeur, les jokers à la fin. */
    fun sortKey(card: Int): Int = if (isJoker(card)) card else FrenchCards.suit(card) * FrenchCards.RANKS + FrenchCards.rank(card)
}

/** Où en est le tour : le joueur défausse, puis il pioche. */
internal enum class YanivPhase { DISCARD, DRAW }

/**
 * Une partie de Yaniv jouée tour par tour sur un seul téléphone que l'on se passe (2 à 8 joueurs).
 *
 * - L'appli distribue 5 cartes à chacun et retourne une carte (la défausse). Le joueur dont c'est le tour voit sa main une
 *   fois qu'il l'a demandée ([reveal]).
 * - Au début de son tour, avec [YanivScoring.YANIV_MAX] points ou moins, il peut annoncer « Yaniv » ([callYaniv]). Sinon il
 *   défausse ([discard]) une carte, une paire, un brelan ou une suite, puis pioche ([drawFromStock]) ou reprend une carte de la
 *   défausse du joueur précédent ([takeFromDiscard] : n'importe laquelle, ou la première ou la dernière d'une suite).
 * - Après « Yaniv », chacun compte sa main. Si personne n'a autant de points ou moins que celui qui l'a annoncé, il marque 0
 *   et les autres leur main. Sinon c'est « Assaf » : celui qui l'a annoncé marque sa main plus
 *   [YanivScoring.ASSAF_PENALTY], et celui qui a le moins ([assafBy]) marque 0 ([roundPoints]).
 * - [settle] inscrit ces points dans une ligne libre du tableau des scores (le total de chacun, la fin de partie et le
 *   journal viennent de ce tableau, le plus petit total gagnant) ; [nextRound] distribue la manche suivante.
 *
 * [startPlayer] : le joueur qui commence. [startRound] : numéro de la manche. [shuffle] mélange une liste de cartes
 * (remplaçable dans les tests).
 */
internal class YanivRound(
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

    /** La dernière défausse, visible sur la table (rangée par [YanivScoring.arrange]). */
    var lastDiscard by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les défausses plus anciennes : elles refont la pioche quand elle est vide. */
    var pile by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les cartes que le joueur vient de défausser et qui ne sont pas encore sur la table (il doit encore piocher). */
    var pending by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Défausser, puis piocher. */
    var phase by mutableStateOf(YanivPhase.DISCARD)
        private set

    /** Vrai quand le joueur dont c'est le tour a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai quand la manche est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Celui qui a annoncé « Yaniv » ; -1 tant que personne ne l'a fait. */
    var caller by mutableIntStateOf(-1)
        private set

    /** Celui qui a battu l'annonce (« Assaf ») ; -1 si l'annonce a réussi ou tant qu'elle n'est pas faite. */
    var assafBy by mutableIntStateOf(-1)
        private set

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Coups joués (défausses, pioches) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** La main du joueur qui joue (sans la défausse en attente). */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Points de la main du joueur qui joue. */
    val currentPoints: Int get() = YanivScoring.handPoints(currentHand)

    /** Vrai si le joueur qui joue peut annoncer « Yaniv » : début de tour et assez peu de points en main. */
    val canCallYaniv: Boolean
        get() = revealed && !roundOver && phase == YanivPhase.DISCARD && currentPoints <= YanivScoring.YANIV_MAX

    /** Les cartes qu'on peut reprendre dans la dernière défausse. */
    val drawable: List<Int> get() = YanivScoring.drawable(lastDiscard)

    /** Vrai s'il reste de quoi piocher, en remélangeant au besoin les anciennes défausses. */
    val canDrawFromStock: Boolean get() = stock.isNotEmpty() || pile.isNotEmpty()

    /** Celui qui a gagné la manche : celui qui a battu l'annonce, sinon celui qui l'a faite ; -1 tant qu'elle n'est pas finie. */
    val winner: Int get() = if (!roundOver) -1 else if (assafBy >= 0) assafBy else caller

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** Points de chacun pour la manche (voir la description de la classe). */
    fun roundPoints(): List<Int> = hands.mapIndexed { player, hand ->
        val points = YanivScoring.handPoints(hand)
        when {
            !roundOver -> points
            player == caller -> if (assafBy >= 0) points + YanivScoring.ASSAF_PENALTY else 0
            player == assafBy -> 0
            else -> points
        }
    }

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver) revealed = true
    }

    /**
     * Défausse [cards] (qui doivent être dans la main du joueur et former une défausse permise : [YanivScoring.isValidDiscard]).
     * Le joueur doit ensuite piocher. Renvoie faux, sans rien changer, si le coup n'est pas permis.
     */
    fun discard(cards: List<Int>): Boolean {
        if (!revealed || roundOver || phase != YanivPhase.DISCARD) return false
        if (cards.any { it !in currentHand } || !YanivScoring.isValidDiscard(cards)) return false
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand.filterNot { it in cards } else hand }
        pending = YanivScoring.arrange(cards)
        phase = YanivPhase.DRAW
        moves++
        return true
    }

    /** Après sa défausse, le joueur pioche la première carte de la pioche (remélangée au besoin) ; faux si ce n'est pas permis. */
    fun drawFromStock(): Boolean {
        if (!revealed || roundOver || phase != YanivPhase.DRAW || !canDrawFromStock) return false
        var newPile = pile
        if (stock.isEmpty()) {
            val refill = newPile.toMutableList()
            shuffle(refill)
            stock = refill
            newPile = emptyList()
        }
        val card = stock.first()
        stock = stock.drop(1)
        pile = newPile + lastDiscard
        finishTurn(card)
        return true
    }

    /** Après sa défausse, le joueur reprend [card] dans la dernière défausse (voir [drawable]) ; faux si ce n'est pas permis. */
    fun takeFromDiscard(card: Int): Boolean {
        if (!revealed || roundOver || phase != YanivPhase.DRAW || card !in drawable) return false
        pile = pile + lastDiscard.filter { it != card }
        finishTurn(card)
        return true
    }

    /** Annonce « Yaniv » : chacun compte sa main et la manche est finie ; faux si l'annonce n'est pas permise ([canCallYaniv]). */
    fun callYaniv(): Boolean {
        if (!canCallYaniv) return false
        val callerPoints = currentPoints
        caller = current
        // Celui qui bat l'annonce : le moins de points parmi ceux qui en ont autant ou moins ; à égalité, le premier après celui qui annonce.
        val others = (1 until playerCount).map { (current + it) % playerCount }
        assafBy = others
            .filter { YanivScoring.handPoints(hands[it]) <= callerPoints }
            .minByOrNull { YanivScoring.handPoints(hands[it]) } ?: -1
        roundOver = true
        revealed = false
        moves++
        return true
    }

    /** La carte [card] rejoint la main du joueur, sa défausse devient celle de la table et le tour passe au suivant. */
    private fun finishTurn(card: Int) {
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand + card else hand }
        lastDiscard = pending
        pending = emptyList()
        phase = YanivPhase.DISCARD
        current = (current + 1) % playerCount.coerceAtLeast(1)
        revealed = false
        moves++
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

    /** Mélange les 54 cartes, distribue les mains et retourne la première carte de la défausse. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val deck = (0 until YanivScoring.DECK).toMutableList()
        shuffle(deck)
        val newHands = List(count) { player ->
            deck.subList(player * YanivScoring.HAND_SIZE, (player + 1) * YanivScoring.HAND_SIZE).toList()
        }
        val rest = deck.drop(count * YanivScoring.HAND_SIZE).toMutableList()
        val first = rest.removeAt(0)
        hands = newHands
        stock = rest
        lastDiscard = listOf(first)
        pile = emptyList()
        clearTurn()
    }

    private fun clearTurn() {
        pending = emptyList()
        phase = YanivPhase.DISCARD
        current = starter
        revealed = false
        roundOver = false
        caller = -1
        assafBy = -1
        settled = false
        moves = 0
    }

    /** Installe une table précise (mains, pioche, dernière défausse, anciennes défausses) : sert aux tests. */
    internal fun load(
        hands: List<List<Int>>,
        stock: List<Int>,
        lastDiscard: List<Int>,
        current: Int = 0,
        pile: List<Int> = emptyList()
    ) {
        require(hands.size == playerCount && lastDiscard.isNotEmpty() && current in 0 until playerCount)
        this.hands = hands
        this.stock = stock
        this.lastDiscard = YanivScoring.arrange(lastDiscard)
        this.pile = pile
        clearTurn()
        this.current = current
    }
}
