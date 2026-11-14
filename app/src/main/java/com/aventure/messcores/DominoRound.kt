package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant du Domino parmi les jeux prédéfinis (rubrique « Spéciaux »), le même que celui de sa feuille de scores. */
internal const val DOMINOS_ID = "builtin_dominos"

/**
 * Un domino posé dans la chaîne : [tile] (voir [DominoScoring]) et les points qu'on voit à sa gauche ([left]) et à sa droite
 * ([right]) une fois posé. Deux dominos voisins se touchent par des moitiés de même valeur.
 */
internal data class PlacedDomino(val tile: Int, val left: Int, val right: Int)

/**
 * Règles des dominos : le jeu « double-six » de 28 dominos, de 0-0 à 6-6.
 *
 * Un domino est un numéro de 0 à 27, dans l'ordre 0-0, 0-1 … 0-6, 1-1 … 6-6 (le plus petit côté d'abord) : c'est aussi l'ordre
 * des images de `res/drawable-nodpi/domino_*.webp`.
 */
internal object DominoScoring {

    /** Dominos du jeu. */
    const val TILES = 28

    /** Points maximum d'une moitié de domino. */
    const val MAX_SIDE = 6

    private val sides: List<Pair<Int, Int>> = buildList {
        for (a in 0..MAX_SIDE) for (b in a..MAX_SIDE) add(a to b)
    }

    /** Le petit côté du domino [tile]. */
    fun low(tile: Int): Int = sides[tile].first

    /** Le grand côté du domino [tile]. */
    fun high(tile: Int): Int = sides[tile].second

    /** Le numéro du domino qui a les côtés [a] et [b], dans n'importe quel ordre. */
    fun tileOf(a: Int, b: Int): Int = sides.indexOf(minOf(a, b) to maxOf(a, b))

    /** Vrai pour un double (les deux moitiés égales). */
    fun isDouble(tile: Int): Boolean = low(tile) == high(tile)

    /** Points d'un domino resté en main : la somme de ses deux moitiés. */
    fun points(tile: Int): Int = low(tile) + high(tile)

    /** Points d'une main. */
    fun handPoints(hand: List<Int>): Int = hand.sumOf { points(it) }

    /** Vrai si le domino [tile] a un côté de valeur [end]. */
    fun matches(tile: Int, end: Int): Boolean = low(tile) == end || high(tile) == end

    /** Dominos distribués à chacun : 7 à deux joueurs, 5 à trois ou quatre. */
    fun handSize(playerCount: Int): Int = if (playerCount <= 2) 7 else 5

    /**
     * Valeur d'un domino pour ouvrir la partie : le plus fort double, sinon le domino qui a le plus de points (à égalité, celui
     * qui a le plus grand côté).
     */
    fun openingKey(tile: Int): Int = (if (isDouble(tile)) 1000 else 0) + points(tile) * 10 + high(tile)
}

/**
 * Une partie de dominos jouée tour par tour sur un seul téléphone que l'on se passe (2 à 4 joueurs).
 *
 * - L'appli distribue 7 dominos à chacun (5 à trois ou quatre joueurs) ; les autres forment la pioche. Celui qui a le plus fort
 *   double ouvre : son domino est posé tout de suite, sinon c'est celui qui a le domino le plus lourd ([DominoScoring.openingKey]).
 * - Le joueur dont c'est le tour voit sa main une fois qu'il l'a demandée ([reveal]). Il pose un domino dont une moitié
 *   correspond au bout gauche ou au bout droit de la chaîne ([play]). S'il n'en a aucun, il pioche ([draw]) jusqu'à en avoir
 *   un ; quand la pioche est vide, il passe son tour ([pass]).
 * - La manche s'arrête quand un joueur a posé tous ses dominos, ou quand plus personne ne peut jouer (pioche vide, tout le monde
 *   passe : la manche est « bloquée »).
 * - Celui qui a posé son dernier domino marque les points des dominos restés chez les autres. Si la manche est bloquée, celui qui a
 *   le moins de points marque la différence avec chacun des autres ; à égalité pour le moins, personne ne marque ([roundPoints]).
 * - [settle] inscrit ces points dans une ligne libre du tableau des scores (le total de chacun, la fin de partie à 100 points et
 *   le journal viennent de ce tableau, le plus grand total gagnant) ; [nextRound] distribue la manche suivante.
 *
 * [startRound] : numéro de la manche. [shuffle] mélange une liste de dominos (remplaçable dans les tests).
 */
internal class DominoRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    /** Le joueur qui joue. */
    var current by mutableIntStateOf(0)
        private set

    /** Les mains de chaque joueur (même ordre que les joueurs). */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** La pioche : le prochain domino à piocher est le premier. */
    var stock by mutableStateOf<List<Int>>(emptyList())
        private set

    /** La chaîne posée sur la table, de gauche à droite. */
    var chain by mutableStateOf<List<PlacedDomino>>(emptyList())
        private set

    /** Celui qui a ouvert la manche ; -1 tant que rien n'est posé. */
    var opener by mutableIntStateOf(-1)
        private set

    /** Le domino qui a ouvert la manche ; -1 tant que rien n'est posé. */
    var openingTile by mutableIntStateOf(-1)
        private set

    /** Vrai quand le joueur dont c'est le tour a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai quand la manche est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Vrai si la manche s'est arrêtée parce que plus personne ne pouvait jouer. */
    var blocked by mutableStateOf(false)
        private set

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Joueurs qui viennent de passer leur tour l'un après l'autre. */
    private var passes = 0

    /** Coups joués (dominos posés, piochés, tours passés) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** La main du joueur qui joue. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Points de la main du joueur qui joue. */
    val currentPoints: Int get() = DominoScoring.handPoints(currentHand)

    /** Le bout gauche de la chaîne ; -1 si rien n'est posé. */
    val leftEnd: Int get() = chain.firstOrNull()?.left ?: -1

    /** Le bout droit de la chaîne ; -1 si rien n'est posé. */
    val rightEnd: Int get() = chain.lastOrNull()?.right ?: -1

    /** Vrai si [tile] peut se poser au bout gauche de la chaîne (n'importe où si rien n'est posé). */
    fun fitsLeft(tile: Int): Boolean = chain.isEmpty() || DominoScoring.matches(tile, leftEnd)

    /** Vrai si [tile] peut se poser au bout droit de la chaîne (n'importe où si rien n'est posé). */
    fun fitsRight(tile: Int): Boolean = chain.isEmpty() || DominoScoring.matches(tile, rightEnd)

    /** Vrai si le joueur qui joue a au moins un domino à poser. */
    val canPlayAny: Boolean get() = currentHand.any { fitsLeft(it) || fitsRight(it) }

    /** Vrai s'il reste des dominos à piocher. */
    val canDrawFromStock: Boolean get() = stock.isNotEmpty()

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /**
     * Celui qui a gagné la manche : celui qui a posé son dernier domino ou, si elle est bloquée, celui qui a le moins de points
     * (-1 à égalité) ; -1 tant que la manche n'est pas finie.
     */
    val winner: Int
        get() {
            if (!roundOver) return -1
            if (!blocked) return hands.indexOfFirst { it.isEmpty() }
            val points = hands.map { DominoScoring.handPoints(it) }
            val least = points.minOrNull() ?: return -1
            val leaders = points.indices.filter { points[it] == least }
            return if (leaders.size == 1) leaders.first() else -1
        }

    /** Points de chacun pour la manche (voir la description de la classe) ; tant qu'elle n'est pas finie, les points de sa main. */
    fun roundPoints(): List<Int> {
        val handPoints = hands.map { DominoScoring.handPoints(it) }
        if (!roundOver) return handPoints
        val winner = winner
        if (winner < 0) return List(playerCount) { 0 }
        val others = handPoints.filterIndexed { index, _ -> index != winner }
        val score = if (blocked) others.sumOf { it - handPoints[winner] } else others.sum()
        return List(playerCount) { if (it == winner) score else 0 }
    }

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver) revealed = true
    }

    /**
     * Pose le domino [tile] de la main du joueur au bout gauche ([atLeft]) ou au bout droit de la chaîne, une moitié contre la
     * moitié de même valeur. Renvoie faux, sans rien changer, si le domino n'est pas dans sa main ou ne se pose pas à ce bout.
     * Le tour passe au joueur suivant, sauf si c'était son dernier domino : la manche est alors finie.
     */
    fun play(tile: Int, atLeft: Boolean): Boolean {
        if (!revealed || roundOver || tile !in currentHand) return false
        if (!(if (atLeft) fitsLeft(tile) else fitsRight(tile))) return false
        val low = DominoScoring.low(tile)
        val high = DominoScoring.high(tile)
        val placed = when {
            chain.isEmpty() -> PlacedDomino(tile, low, high)
            // À gauche, la moitié qui touche la chaîne est à droite du nouveau domino ; à droite, c'est la moitié de gauche.
            atLeft -> if (low == leftEnd) PlacedDomino(tile, high, low) else PlacedDomino(tile, low, high)
            else -> if (low == rightEnd) PlacedDomino(tile, low, high) else PlacedDomino(tile, high, low)
        }
        val player = current
        chain = if (atLeft) listOf(placed) + chain else chain + placed
        hands = hands.mapIndexed { index, hand -> if (index == player) hand - tile else hand }
        moves++
        passes = 0
        if (hands[player].isEmpty()) {
            roundOver = true
            revealed = false
        } else {
            nextPlayer()
        }
        return true
    }

    /**
     * Le joueur qui n'a aucun domino à poser en pioche un (le premier de la pioche) et reste à jouer. Renvoie faux, sans rien
     * changer, s'il a un domino à poser ou si la pioche est vide.
     */
    fun draw(): Boolean {
        if (!revealed || roundOver || !canDrawFromStock || canPlayAny) return false
        val tile = stock.first()
        stock = stock.drop(1)
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand + tile else hand }
        moves++
        return true
    }

    /**
     * Le joueur passe son tour : permis seulement s'il n'a aucun domino à poser et que la pioche est vide. Si tous les joueurs
     * passent l'un après l'autre, la manche est bloquée. Renvoie faux, sans rien changer, si ce n'est pas permis.
     */
    fun pass(): Boolean {
        if (!revealed || roundOver || canDrawFromStock || canPlayAny) return false
        moves++
        passes++
        if (passes >= playerCount) {
            blocked = true
            roundOver = true
            revealed = false
        } else {
            nextPlayer()
        }
        return true
    }

    private fun nextPlayer() {
        current = (current + 1) % playerCount.coerceAtLeast(1)
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

    /** Distribue la manche suivante ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        roundNumber++
        deal()
        return true
    }

    /** Mélange les 28 dominos, distribue les mains, puis pose le domino d'ouverture de celui qui doit ouvrir. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val deck = (0 until DominoScoring.TILES).toMutableList()
        shuffle(deck)
        val size = DominoScoring.handSize(count)
        val newHands = List(count) { player -> deck.subList(player * size, (player + 1) * size).toList() }
        hands = newHands
        stock = deck.drop(count * size)
        chain = emptyList()
        clearTurn()
        open()
    }

    /** Pose le domino d'ouverture : le plus fort double, sinon le domino le plus lourd, parmi toutes les mains. */
    private fun open() {
        var starter = 0
        var tile = hands[0].first()
        hands.forEachIndexed { player, hand ->
            hand.forEach { candidate ->
                if (DominoScoring.openingKey(candidate) > DominoScoring.openingKey(tile)) {
                    starter = player
                    tile = candidate
                }
            }
        }
        hands = hands.mapIndexed { index, hand -> if (index == starter) hand - tile else hand }
        chain = listOf(PlacedDomino(tile, DominoScoring.low(tile), DominoScoring.high(tile)))
        opener = starter
        openingTile = tile
        current = (starter + 1) % playerCount.coerceAtLeast(1)
    }

    private fun clearTurn() {
        current = 0
        revealed = false
        roundOver = false
        blocked = false
        settled = false
        opener = -1
        openingTile = -1
        passes = 0
        moves = 0
    }

    /**
     * Installe une table précise (mains, pioche, chaîne déjà posée de gauche à droite) : sert aux tests. Rien n'est ouvert
     * automatiquement ; [opener] vaut -1 et [current] joue.
     */
    internal fun load(
        hands: List<List<Int>>,
        stock: List<Int>,
        chain: List<PlacedDomino>,
        current: Int = 0
    ) {
        require(hands.size == playerCount && current in 0 until playerCount)
        this.hands = hands
        this.stock = stock
        this.chain = chain
        clearTurn()
        this.current = current
    }
}
