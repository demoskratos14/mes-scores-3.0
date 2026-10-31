package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Identifiant du Uno parmi les jeux prédéfinis (rubrique « Spéciaux »). */
internal const val UNO_ID = "builtin_uno"

/**
 * Règles du Uno : voir la fiche de règles du jeu.
 *
 * Une carte est un entier de 0 à 107 :
 * - 0 à 99 : les cartes de couleur, `couleur * 25 + rang`, la couleur allant de 0 à 3 (rouge, jaune, vert, bleu) et le rang de 0
 *   à 24 : un 0, deux de chaque chiffre de 1 à 9, deux « passe ton tour », deux « inversion », deux « +2 » ;
 * - 100 à 103 : les quatre jokers ; 104 à 107 : les quatre jokers +4.
 */
internal object UnoScoring {

    /** Nombre de couleurs. */
    const val COLORS = 4

    /** Cartes de chaque couleur : un 0, deux de chaque chiffre de 1 à 9, deux de chaque carte action. */
    const val PER_COLOR = 25

    /** Cartes de couleur du paquet. */
    const val COLORED = COLORS * PER_COLOR

    /** Première carte joker, puis premier joker +4 (quatre de chaque). */
    const val FIRST_WILD = COLORED
    const val FIRST_WILD_FOUR = COLORED + 4

    /** Cartes du paquet (108). */
    const val DECK = COLORED + 8

    /** Cartes distribuées à chacun. */
    const val HAND_SIZE = 7

    // Nature d'une carte : les chiffres valent de 0 à 9, puis les cartes action.
    const val SKIP = 10
    const val REVERSE = 11
    const val DRAW_TWO = 12
    const val WILD = 13
    const val WILD_FOUR = 14

    /** Couleur de la carte [card], de 0 à 3 ; -1 pour un joker. */
    fun color(card: Int): Int = if (card >= COLORED) -1 else card / PER_COLOR

    /** Nature de la carte [card] : un chiffre (0 à 9), [SKIP], [REVERSE], [DRAW_TWO], [WILD] ou [WILD_FOUR]. */
    fun kind(card: Int): Int = when {
        card >= FIRST_WILD_FOUR -> WILD_FOUR
        card >= FIRST_WILD -> WILD
        else -> {
            val rank = card % PER_COLOR
            when {
                rank == 0 -> 0
                rank <= 18 -> (rank - 1) / 2 + 1
                rank <= 20 -> SKIP
                rank <= 22 -> REVERSE
                else -> DRAW_TWO
            }
        }
    }

    /** Vrai pour un joker ou un joker +4. */
    fun isWild(card: Int): Boolean = card >= COLORED

    /** Points d'une carte restée en main : chiffre = sa valeur, +2 / inversion / passe ton tour = 20, jokers = 50. */
    fun points(card: Int): Int = when (val kind = kind(card)) {
        in 0..9 -> kind
        SKIP, REVERSE, DRAW_TWO -> 20
        else -> 50
    }

    /** Points d'une main. */
    fun handPoints(hand: List<Int>): Int = hand.sumOf { points(it) }

    /**
     * Vrai si [card], tirée de la main [hand], peut être posée sur [top] quand la couleur demandée est [askedColor] : même
     * couleur que celle demandée (la couleur de [top], ou celle choisie après un joker), même chiffre ou même symbole que [top],
     * ou un joker. Le joker +4 ne se joue que si l'on n'a aucune carte de la couleur demandée.
     */
    fun isPlayable(card: Int, top: Int, askedColor: Int, hand: List<Int>): Boolean = when (kind(card)) {
        WILD -> true
        WILD_FOUR -> hand.none { color(it) == askedColor }
        else -> color(card) == askedColor || (!isWild(top) && kind(card) == kind(top))
    }
}

/**
 * Ce que vient de produire une carte spéciale : [player] a posé [card] ; [victim] (-1 : personne) passe son tour et a pioché
 * [drawn] cartes ; un joker choisit la couleur, une inversion change le sens (c'est visible sur la table).
 */
internal data class UnoEvent(val player: Int, val card: Int, val victim: Int, val drawn: Int)

/**
 * Une partie de Uno jouée tour par tour sur un seul téléphone que l'on se passe.
 *
 * - L'appli distribue 7 cartes à chacun et retourne la première carte (toujours un chiffre). Le joueur dont c'est le tour voit sa
 *   main une fois qu'il l'a demandée ([reveal]) : les autres ne la voient pas.
 * - Il pose ([play]) une carte jouable ([UnoScoring.isPlayable]). Un joker (et le joker +4) fixe la couleur qui suit. Les cartes
 *   action s'appliquent toutes seules : « passe ton tour » fait sauter le joueur suivant, « inversion » change le sens (à deux
 *   joueurs, elle fait aussi sauter l'autre), « +2 » et « joker +4 » font piocher 2 ou 4 cartes au joueur suivant, qui passe son tour.
 * - Sans carte jouable, il pioche UNE carte ([draw]) : si elle est jouable il peut la poser, sinon (ou s'il préfère la garder)
 *   il passe ([pass]). Quand la pioche se vide, on remélange les cartes posées (sauf la dernière). S'il ne reste rien à piocher et
 *   que rien n'est jouable, il passe aussi.
 * - La manche finit quand un joueur pose sa dernière carte (les cartes à piocher d'un +2 ou d'un +4 final sont d'abord données),
 *   ou quand tout le monde passe de suite ([blocked] : la main la plus légère gagne). Le gagnant marque les points des mains des
 *   autres ([roundPoints]), les autres 0. L'annonce « UNO ! » est faite par l'appli (voir [hasUno]).
 * - [settle] inscrit ces points dans une ligne libre du tableau des scores (le total de chacun, la fin de partie à 500 points
 *   et le journal viennent de ce tableau, le plus grand total gagnant) ; [nextRound] distribue la manche suivante, que
 *   commence le joueur qui suit celui qui avait commencé.
 *
 * [startPlayer] : le joueur qui commence. [startRound] : numéro de la manche (une partie reprise continue après les manches
 * déjà inscrites). [shuffle] mélange une liste de cartes (remplaçable dans les tests).
 */
internal class UnoRound(
    val playerCount: Int,
    startPlayer: Int = 0,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    private var starter = startPlayer.coerceIn(0, (playerCount - 1).coerceAtLeast(0))

    /** Le joueur qui joue. */
    var current by mutableIntStateOf(starter)
        private set

    /** Le sens du jeu : 1 dans l'ordre des joueurs, -1 en sens inverse. */
    var direction by mutableIntStateOf(1)
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

    /** La couleur demandée : celle de la dernière carte posée, ou celle choisie après un joker. */
    var askedColor by mutableIntStateOf(0)
        private set

    /** La carte que le joueur qui joue vient de piocher (-1 : il n'a pas encore pioché ce tour-ci). */
    var drawnCard by mutableIntStateOf(-1)
        private set

    /** Ce qu'a produit la dernière carte spéciale posée ; null après une carte ordinaire. */
    var lastEvent by mutableStateOf<UnoEvent?>(null)
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

    /** Joueurs qui ont passé de suite faute de pouvoir jouer ni piocher, sans qu'aucune carte ne soit posée entre-temps. */
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

    /** Vrai si [card] peut être posée maintenant (après avoir pioché, seule la carte piochée peut l'être). */
    fun isPlayable(card: Int): Boolean =
        (drawnCard < 0 || card == drawnCard) && UnoScoring.isPlayable(card, top, askedColor, currentHand)

    /** Vrai si le joueur qui joue a au moins une carte jouable. */
    val hasPlayable: Boolean get() = currentHand.any { isPlayable(it) }

    /** Vrai si le joueur qui joue doit piocher : rien de jouable, pas encore pioché, et de quoi piocher (même en remélangeant). */
    val canDraw: Boolean
        get() = !roundOver && drawnCard < 0 && !hasPlayable && (stock.isNotEmpty() || discard.size > 1)

    /** Vrai si le joueur qui joue ne peut plus que passer : rien de jouable et rien à piocher (ou il a déjà pioché). */
    val mustPass: Boolean
        get() = !roundOver && !hasPlayable && (drawnCard >= 0 || (stock.isEmpty() && discard.size <= 1))

    /** Vrai si le joueur qui joue vient de piocher une carte jouable : il peut la poser ou la garder et passer. */
    val canKeep: Boolean get() = !roundOver && drawnCard >= 0 && hasPlayable

    /** Vrai si [player] n'a plus qu'une carte : l'appli annonce « UNO ! » à sa place. */
    fun hasUno(player: Int): Boolean = !roundOver && hands.getOrNull(player)?.size == 1

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** Points de chacun pour la manche : les points des mains des autres pour le gagnant, 0 pour les autres. */
    fun roundPoints(): List<Int> {
        if (!roundOver) return hands.map { 0 }
        val total = hands.indices.filter { it != winner }.sumOf { UnoScoring.handPoints(hands[it]) }
        return hands.indices.map { if (it == winner) total else 0 }
    }

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour montre sa main (les autres ne regardent pas). */
    fun reveal() {
        if (!roundOver) revealed = true
    }

    /**
     * Pose [card] (qui doit être dans la main du joueur et jouable). Un joker demande la couleur [color] qui suit (de 0 à 3),
     * sauf si c'est la dernière carte de la main. Renvoie faux, sans rien changer, si le coup n'est pas permis.
     */
    fun play(card: Int, color: Int = -1): Boolean {
        if (!revealed || roundOver || card !in currentHand || !isPlayable(card)) return false
        val wild = UnoScoring.isWild(card)
        val remaining = currentHand - card
        val validColor = color in 0 until UnoScoring.COLORS
        if (wild && remaining.isNotEmpty() && !validColor) return false
        val player = current
        hands = hands.mapIndexed { index, hand -> if (index == player) remaining else hand }
        discard = discard + card
        askedColor = if (wild) (if (validColor) color else askedColor) else UnoScoring.color(card)
        passes = 0
        drawnCard = -1
        moves++

        var steps = 1
        var victim = -1
        var drawn = 0
        when (UnoScoring.kind(card)) {
            UnoScoring.SKIP -> {
                victim = following()
                steps = 2
            }
            UnoScoring.REVERSE -> if (playerCount == 2) {
                // À deux joueurs, l'inversion fait sauter l'autre : on rejoue.
                victim = following()
                steps = 2
            } else {
                direction = -direction
            }
            UnoScoring.DRAW_TWO -> {
                victim = following()
                drawn = give(victim, 2)
                steps = 2
            }
            UnoScoring.WILD_FOUR -> {
                victim = following()
                drawn = give(victim, 4)
                steps = 2
            }
        }
        val special = UnoScoring.kind(card) >= UnoScoring.SKIP
        lastEvent = if (special) UnoEvent(player, card, victim, drawn) else null
        if (remaining.isEmpty()) finish(player) else advance(steps)
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
        drawnCard = card
        moves++
        return card
    }

    /**
     * Le joueur qui joue passe son tour : après avoir pioché (qu'il puisse poser la carte ou non), ou quand il n'a rien de
     * jouable ni à piocher. Faux si ce n'est pas permis.
     */
    fun pass(): Boolean {
        if (!revealed || roundOver || !(drawnCard >= 0 || mustPass)) return false
        // Passer après avoir pioché est un vrai coup ; seul un tour passé faute de cartes à piocher compte pour le blocage.
        if (drawnCard >= 0) passes = 0 else passes++
        moves++
        drawnCard = -1
        if (passes >= playerCount) finishBlocked() else advance(1)
        return true
    }

    /** Le joueur qui suit celui qui joue, dans le sens du jeu. */
    private fun following(): Int = Math.floorMod(current + direction, playerCount.coerceAtLeast(1))

    /** Donne [count] cartes de la pioche à [player] (moins s'il n'y en a plus assez) ; renvoie le nombre donné. */
    private fun give(player: Int, count: Int): Int {
        val taken = mutableListOf<Int>()
        repeat(count) {
            if (stock.isEmpty()) refill()
            if (stock.isNotEmpty()) {
                taken += stock.first()
                stock = stock.drop(1)
            }
        }
        hands = hands.mapIndexed { index, hand -> if (index == player) hand + taken else hand }
        return taken.size
    }

    /** Remélange les cartes posées, sauf la dernière, pour refaire la pioche (rien à faire s'il n'y a pas de cartes à remélanger). */
    private fun refill() {
        if (discard.size <= 1) return
        val kept = discard.last()
        val pile = discard.dropLast(1).toMutableList()
        shuffle(pile)
        stock = pile
        discard = listOf(kept)
    }

    private fun advance(steps: Int) {
        val player = current
        current = Math.floorMod(current + direction * steps, playerCount.coerceAtLeast(1))
        drawnCard = -1
        // Si c'est encore le même joueur (à deux, après un « passe ton tour »), il garde sa main sous les yeux.
        revealed = current == player
    }

    private fun finish(player: Int) {
        winner = player
        roundOver = true
        revealed = false
        drawnCard = -1
    }

    private fun finishBlocked() {
        blocked = true
        lastEvent = null
        // La main la plus légère gagne ; à égalité, le premier dans l'ordre de la table.
        val lightest = hands.indices.minByOrNull { UnoScoring.handPoints(hands[it]) } ?: 0
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

    /** Mélange, distribue les mains et retourne la première carte (toujours un chiffre). */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val deck = (0 until UnoScoring.DECK).toMutableList()
        shuffle(deck)
        val size = UnoScoring.HAND_SIZE
        val newHands = List(count) { player -> deck.subList(player * size, (player + 1) * size).toList() }
        val rest = deck.drop(count * size).toMutableList()
        val startIndex = rest.indexOfFirst { UnoScoring.kind(it) <= 9 }.coerceAtLeast(0)
        val start = rest.removeAt(startIndex)
        hands = newHands
        stock = rest
        discard = listOf(start)
        askedColor = UnoScoring.color(start)
        direction = 1
        current = starter
        drawnCard = -1
        lastEvent = null
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
        askedColor: Int = UnoScoring.color(discard.last()),
        direction: Int = 1
    ) {
        require(hands.size == playerCount && discard.isNotEmpty() && current in 0 until playerCount)
        this.hands = hands
        this.stock = stock
        this.discard = discard
        this.current = current
        this.askedColor = askedColor
        this.direction = direction
        drawnCard = -1
        lastEvent = null
        revealed = false
        roundOver = false
        winner = -1
        blocked = false
        settled = false
        passes = 0
        moves = 0
    }
}
