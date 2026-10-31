package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant du Huit américain parmi les jeux prédéfinis (rubrique « Cartes »). */
internal const val HUIT_ID = "builtin_huit"

/**
 * Règles du Huit américain : voir la fiche de règles du jeu (« Le tour de jeu » et « Les points »).
 *
 * Une carte est un entier de 0 à 51 : `couleur * 13 + valeur - 1`, la valeur allant de 1 (l'as) à 13 (le roi) et la
 * couleur de 0 à 3 (pique, cœur, carreau, trèfle).
 */
internal object HuitScoring {

    /** Nombre de couleurs du paquet. */
    const val SUITS = 4

    /** Nombre de valeurs par couleur : de l'as (1) au roi (13). */
    const val RANKS = 13

    /** Cartes du paquet (52, sans joker). */
    const val DECK = SUITS * RANKS

    /** Valeur du 8, le joker du jeu. */
    const val EIGHT = 8

    /** Valeur de la carte [card], de 1 (as) à 13 (roi). */
    fun rank(card: Int): Int = card % RANKS + 1

    /** Couleur de la carte [card], de 0 à 3. */
    fun suit(card: Int): Int = card / RANKS

    /** La carte de couleur [suit] et de valeur [rank]. */
    fun card(suit: Int, rank: Int): Int = suit * RANKS + rank - 1

    /** Points d'une carte restée en main : 8 = 50, figures = 10, as = 1, les autres = leur valeur. */
    fun points(card: Int): Int = when (val value = rank(card)) {
        EIGHT -> 50
        in 11..13 -> 10
        else -> value
    }

    /** Points d'une main. */
    fun handPoints(hand: List<Int>): Int = hand.sumOf { points(it) }

    /** Cartes distribuées à chacun : 7 à deux joueurs, 5 sinon. */
    fun handSize(playerCount: Int): Int = if (playerCount == 2) 7 else 5

    /**
     * Vrai si [card] peut être posée sur [top] quand la couleur demandée est [askedSuit] : même couleur que celle demandée
     * (la couleur de [top], ou celle choisie après un 8), même valeur que [top], ou un 8, qui se joue à tout moment.
     */
    fun isPlayable(card: Int, top: Int, askedSuit: Int): Boolean =
        rank(card) == EIGHT || suit(card) == askedSuit || rank(card) == rank(top)
}

/**
 * Une partie de Huit américain jouée tour par tour sur un seul téléphone que l'on se passe.
 *
 * - L'appli distribue les cartes ([HuitScoring.handSize]) et retourne la première carte (jamais un 8). Le joueur dont c'est
 *   le tour voit sa main une fois qu'il l'a demandée ([reveal]) : les autres ne la voient pas.
 * - Il pose ([play]) une carte jouable ([HuitScoring.isPlayable]) ; un 8 se joue à tout moment et fixe la couleur qui suit.
 *   Sans carte jouable, il pioche ([draw]) jusqu'à en avoir une ; quand la pioche se vide, on remélange les cartes posées
 *   (sauf la dernière). S'il ne reste rien à piocher, il passe ([pass]).
 * - La manche finit quand un joueur pose sa dernière carte, ou quand tout le monde passe de suite ([blocked] : celui dont la
 *   main est la plus légère gagne). Le gagnant marque 0, chacun des autres les points de sa main ([roundPoints]).
 * - [settle] inscrit ces points dans une ligne libre du tableau des scores (le total de chacun, la fin de partie à 100 points
 *   et le journal viennent de ce tableau, le plus petit total gagnant) ; [nextRound] distribue la manche suivante, que
 *   commence le joueur qui suit celui qui avait commencé.
 *
 * [startPlayer] : le joueur qui commence. [startRound] : numéro de la manche (une partie reprise continue après les manches
 * déjà inscrites). [shuffle] mélange une liste de cartes (remplaçable dans les tests).
 */
internal class HuitRound(
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

    /** Les cartes posées : la dernière est visible sur la table. */
    var discard by mutableStateOf<List<Int>>(emptyList())
        private set

    /** La couleur demandée : celle de la dernière carte posée, ou celle choisie après un 8. */
    var askedSuit by mutableIntStateOf(0)
        private set

    /** Vrai quand le joueur dont c'est le tour a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var revealed by mutableStateOf(false)
        private set

    /** Vrai quand la manche est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Le gagnant de la manche ; -1 tant qu'elle n'est pas finie. */
    var winner by mutableIntStateOf(-1)
        private set

    /** Vrai si la manche a fini bloquée : tout le monde a passé de suite, sans que personne ne puisse jouer. */
    var blocked by mutableStateOf(false)
        private set

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Joueurs qui ont passé de suite, sans qu'aucune carte ne soit posée entre-temps. */
    private var passes = 0

    /** Coups joués (cartes posées, piochées ou tours passés) depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** La dernière carte posée. */
    val top: Int get() = discard.last()

    /** La main du joueur qui joue. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Vrai si [card] peut être posée maintenant. */
    fun isPlayable(card: Int): Boolean = HuitScoring.isPlayable(card, top, askedSuit)

    /** Vrai si le joueur qui joue a au moins une carte jouable. */
    val hasPlayable: Boolean get() = currentHand.any { isPlayable(it) }

    /** Vrai si le joueur qui joue doit piocher : aucune carte jouable et de quoi piocher (même en remélangeant les cartes posées). */
    val canDraw: Boolean get() = !roundOver && !hasPlayable && (stock.isNotEmpty() || discard.size > 1)

    /** Vrai si le joueur qui joue doit passer : aucune carte jouable et plus rien à piocher. */
    val mustPass: Boolean get() = !roundOver && !hasPlayable && stock.isEmpty() && discard.size <= 1

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** Points de chacun pour la manche : 0 pour le gagnant, les points de sa main pour les autres. */
    fun roundPoints(): List<Int> =
        hands.mapIndexed { player, hand -> if (roundOver && player == winner) 0 else HuitScoring.handPoints(hand) }

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver) revealed = true
    }

    /**
     * Pose [card] (qui doit être dans la main du joueur et jouable). Un 8 demande la couleur [suit] qui suit (de 0 à 3), sauf
     * si c'est la dernière carte de la main. Renvoie faux, sans rien changer, si le coup n'est pas permis.
     */
    fun play(card: Int, suit: Int = -1): Boolean {
        if (!revealed || roundOver || card !in currentHand || !isPlayable(card)) return false
        val isEight = HuitScoring.rank(card) == HuitScoring.EIGHT
        val remaining = currentHand - card
        val validSuit = suit in 0 until HuitScoring.SUITS
        if (isEight && remaining.isNotEmpty() && !validSuit) return false
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) remaining else hand }
        discard = discard + card
        askedSuit = if (isEight && validSuit) suit else HuitScoring.suit(card)
        passes = 0
        moves++
        if (remaining.isEmpty()) finish(player) else advance()
        return true
    }

    /** Le joueur qui joue pioche une carte et la voit dans sa main ; renvoie la carte, ou null s'il ne peut pas piocher. */
    fun draw(): Int? {
        if (!revealed || !canDraw) return null
        if (stock.isEmpty()) refill()
        val card = stock.first()
        stock = stock.drop(1)
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) hand + card else hand }
        moves++
        return card
    }

    /** Le joueur qui joue passe son tour (seulement sans carte jouable ni rien à piocher) ; faux si ce n'est pas permis. */
    fun pass(): Boolean {
        if (!revealed || !mustPass) return false
        passes++
        moves++
        if (passes >= playerCount) finishBlocked() else advance()
        return true
    }

    /** Remélange les cartes posées, sauf la dernière, pour refaire la pioche. */
    private fun refill() {
        val kept = discard.last()
        val pile = discard.dropLast(1).toMutableList()
        shuffle(pile)
        stock = pile
        discard = listOf(kept)
    }

    private fun advance() {
        current = (current + 1) % playerCount.coerceAtLeast(1)
        revealed = false
    }

    private fun finish(player: Int) {
        winner = player
        roundOver = true
        revealed = false
    }

    private fun finishBlocked() {
        blocked = true
        // La main la plus légère gagne ; à égalité, le premier dans l'ordre de la table.
        val lightest = hands.indices.minByOrNull { HuitScoring.handPoints(hands[it]) } ?: 0
        finish(lightest)
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

    /** Mélange, distribue les mains et retourne la première carte (jamais un 8). */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val deck = (0 until HuitScoring.DECK).toMutableList()
        shuffle(deck)
        val size = HuitScoring.handSize(count)
        val newHands = List(count) { player -> deck.subList(player * size, (player + 1) * size).toList() }
        val rest = deck.drop(count * size).toMutableList()
        val startIndex = rest.indexOfFirst { HuitScoring.rank(it) != HuitScoring.EIGHT }.coerceAtLeast(0)
        val start = rest.removeAt(startIndex)
        hands = newHands
        stock = rest
        discard = listOf(start)
        askedSuit = HuitScoring.suit(start)
        current = starter
        revealed = false
        roundOver = false
        winner = -1
        blocked = false
        settled = false
        passes = 0
        moves = 0
    }

    /** Installe une table précise (mains, pioche, cartes posées) : sert aux tests. */
    internal fun load(
        hands: List<List<Int>>,
        stock: List<Int>,
        discard: List<Int>,
        current: Int = 0,
        askedSuit: Int = HuitScoring.suit(discard.last())
    ) {
        require(hands.size == playerCount && discard.isNotEmpty() && current in 0 until playerCount)
        this.hands = hands
        this.stock = stock
        this.discard = discard
        this.askedSuit = askedSuit
        this.current = current
        revealed = false
        roundOver = false
        winner = -1
        blocked = false
        settled = false
        passes = 0
        moves = 0
    }
}
