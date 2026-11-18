package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot

/** Identifiant du Trio parmi les jeux prédéfinis (rubrique « Spéciaux »), le même que celui de sa feuille de scores. */
internal const val TRIO_ID = "builtin_trio"

/** Où se trouve une carte retournée : dans la main d'un joueur ou dans le milieu de la table. */
internal enum class TrioZone { HAND, CENTER }

/**
 * Une carte retournée pendant le tour : sa [zone], le joueur à qui appartient la main ([player], -1 pour le milieu), sa place
 * [index] dans cette main (ou dans le milieu) et son numéro [value].
 */
internal data class TrioReveal(val zone: TrioZone, val player: Int, val index: Int, val value: Int)

/** Comment une manche a été gagnée. */
internal enum class TrioWin {
    /** Le trio de 7 : victoire immédiate. */
    SEVENS,

    /** Deux trios liés (voir [TrioScoring.areLinked]). */
    LINKED,

    /** Trois trios. */
    THREE_TRIOS,

    /** Tous les trios sont pris sans que personne ait gagné : celui qui en a le plus l'emporte. */
    MOST,

    /** Tous les trios sont pris et deux joueurs sont à égalité : personne ne marque. */
    NONE
}

/**
 * Règles du Trio : 36 cartes numérotées de 1 à 12, en trois exemplaires chacune.
 *
 * Les petits numéros dessinés dans les coins d'une carte donnent les trios qui lui sont « liés » (lus sur les cartes) : 1 avec
 * 6 et 8, 2 avec 5 et 9, 3 avec 4 et 10, 4 avec 11, 5 avec 12. Le 7 n'est lié à aucun numéro : son trio fait gagner à lui seul.
 */
internal object TrioScoring {

    /** Numéros du jeu : de 1 à 12. */
    const val NUMBERS = 12

    /** Cartes identiques qui font un trio. */
    const val COPIES = 3

    /** Le numéro dont le trio fait gagner tout de suite. */
    const val SEVEN = 7

    /** Trios qui font gagner la manche. */
    const val TRIOS_TO_WIN = 3

    /** Cartes du jeu. */
    const val DECK = NUMBERS * COPIES

    // Les numéros liés, dans l'ordre où leurs pastilles se lisent sur la carte.
    private val links: Map<Int, List<Int>> = mapOf(
        1 to listOf(6, 8),
        2 to listOf(5, 9),
        3 to listOf(4, 10),
        4 to listOf(11, 3),
        5 to listOf(2, 12),
        6 to listOf(1),
        7 to emptyList(),
        8 to listOf(1),
        9 to listOf(2),
        10 to listOf(3),
        11 to listOf(4),
        12 to listOf(5)
    )

    /** Les numéros liés à [number] (0, 1 ou 2 numéros). */
    fun linksOf(number: Int): List<Int> = links[number].orEmpty()

    /** Vrai si les trios de [a] et de [b] sont liés. */
    fun areLinked(a: Int, b: Int): Boolean = b in linksOf(a)

    /** Cartes distribuées à chacun : 9 à trois joueurs (ou moins), 7 à quatre, 6 à cinq, 5 à six. */
    fun handSize(playerCount: Int): Int = when {
        playerCount <= 3 -> 9
        playerCount == 4 -> 7
        playerCount == 5 -> 6
        else -> 5
    }

    /** Cartes laissées au milieu de la table, face cachée. */
    fun centerSize(playerCount: Int): Int = DECK - playerCount.coerceAtLeast(1) * handSize(playerCount)

    /** Les deux numéros liés parmi les trios [trios], ou null s'il n'y en a pas. */
    fun linkedPair(trios: List<Int>): Pair<Int, Int>? {
        for (a in trios) for (b in trios) if (a < b && areLinked(a, b)) return a to b
        return null
    }

    /** Comment un joueur qui a gagné les trios [trios] gagne la manche, ou null s'il n'a pas encore gagné. */
    fun winReason(trios: List<Int>): TrioWin? = when {
        SEVEN in trios -> TrioWin.SEVENS
        linkedPair(trios) != null -> TrioWin.LINKED
        trios.size >= TRIOS_TO_WIN -> TrioWin.THREE_TRIOS
        else -> null
    }
}

/**
 * (Voir [TrioRound.flipHand] pour les cartes que l'on peut demander.)
 *
 * Une manche de Trio jouée tour par tour (3 à 6 joueurs), sur un seul téléphone que l'on se passe ou chacun sur le sien.
 *
 * - L'appli distribue les cartes (9 chacun à trois joueurs, 7 à quatre, 6 à cinq, 5 à six) ; les autres forment le milieu de la
 *   table, face cachée. Chaque main est rangée de la plus petite carte à la plus grande, et chacun connaît sa main.
 * - À son tour, le joueur demande une carte : la plus petite ou la plus grande carte encore cachée de la main d'un joueur (la
 *   sienne comprise : [flipHand]), ou n'importe quelle carte du milieu ([flipCenter]). Il peut continuer tant que les cartes retournées sont identiques ; à la première carte différente, le tour s'arrête
 *   ([mismatch]) et tout est recaché ([endTurn]). C'est à chacun de se souvenir de ce qui a été retourné.
 * - Trois cartes identiques retournées dans le même tour font un trio : le joueur le garde devant lui et le tour passe au suivant.
 * - La manche est gagnée avec 3 trios, avec 2 trios liés ([TrioScoring.areLinked]) ou, tout de suite, avec le trio de 7. Si tous
 *   les trios sont pris sans vainqueur, celui qui en a le plus gagne (à égalité, personne).
 * - Le gagnant marque 1 point ([roundPoints]) ; [settle] l'inscrit dans une ligne libre du tableau des scores (le total de chacun,
 *   la fin de partie et le journal viennent de ce tableau) ; [nextRound] distribue la manche suivante.
 *
 * [startRound] : numéro de la manche (le premier joueur change à chaque manche). [shuffle] mélange le paquet (remplaçable dans les tests).
 */
internal class TrioRound(
    val playerCount: Int,
    startRound: Int = 1,
    private val shuffle: (MutableList<Int>) -> Unit = { it.shuffle() }
) {
    /** Le joueur qui joue. */
    var current by mutableIntStateOf(0)
        private set

    /** Les mains de chaque joueur (même ordre que les joueurs), de la plus petite carte à la plus grande. */
    var hands by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Les cartes du milieu de la table, face cachée. */
    var center by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Les trios gagnés par chaque joueur (le numéro de chaque trio). */
    var trios by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** Les cartes retournées pendant le tour en cours, dans l'ordre. */
    var reveals by mutableStateOf<List<TrioReveal>>(emptyList())
        private set

    /** Vrai quand la dernière carte retournée est différente des autres : le tour est perdu, il reste à le terminer ([endTurn]). */
    var mismatch by mutableStateOf(false)
        private set

    /** Le numéro du trio qui vient d'être gagné par le joueur précédent (0 : aucun). */
    var lastTrio by mutableIntStateOf(0)
        private set

    /** Vrai quand le joueur dont c'est le tour a demandé à voir sa main (faux tant qu'on se passe le téléphone). */
    var shown by mutableStateOf(false)
        private set

    /** Vrai quand la manche est finie. */
    var roundOver by mutableStateOf(false)
        private set

    /** Le gagnant de la manche ; -1 tant qu'elle n'est pas finie ou si personne ne gagne. */
    var winner by mutableIntStateOf(-1)
        private set

    /** Comment la manche a été gagnée ; [TrioWin.NONE] tant qu'elle n'est pas finie. */
    var winReason by mutableStateOf(TrioWin.NONE)
        private set

    /** Vrai quand les points de la manche finie sont inscrits au tableau des scores (voir [settle]). */
    var settled by mutableStateOf(false)
        private set

    /** Numéro de la manche en cours. */
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Cartes retournées depuis la dernière distribution. */
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** La main du joueur qui joue. */
    val currentHand: List<Int> get() = hands.getOrElse(current) { emptyList() }

    /** Vrai quand la manche en cours a déjà commencé : quitter la partie fait alors perdre quelque chose. */
    val inProgress: Boolean get() = moves > 0

    /** Vrai si la carte [index] de la main de [player] est retournée pendant ce tour. */
    fun isRevealed(player: Int, index: Int): Boolean =
        reveals.any { it.zone == TrioZone.HAND && it.player == player && it.index == index }

    /** Vrai si la carte [index] du milieu est retournée pendant ce tour. */
    fun isCenterRevealed(index: Int): Boolean = reveals.any { it.zone == TrioZone.CENTER && it.index == index }

    /** La carte retournée qui est à cette place de la main de [player], ou null. */
    fun revealAt(player: Int, index: Int): TrioReveal? =
        reveals.firstOrNull { it.zone == TrioZone.HAND && it.player == player && it.index == index }

    /** La carte retournée qui est à cette place du milieu, ou null. */
    fun centerRevealAt(index: Int): TrioReveal? = reveals.firstOrNull { it.zone == TrioZone.CENTER && it.index == index }

    /** Les places encore cachées de la main de [player], de la plus petite carte à la plus grande. */
    fun hiddenIndices(player: Int): List<Int> =
        hands.getOrElse(player) { emptyList() }.indices.filter { !isRevealed(player, it) }

    /** La place de la plus petite carte encore cachée de la main de [player] (-1 : plus aucune). */
    fun lowIndex(player: Int): Int = hiddenIndices(player).firstOrNull() ?: -1

    /** La place de la plus grande carte encore cachée de la main de [player] (-1 : plus aucune). */
    fun highIndex(player: Int): Int = hiddenIndices(player).lastOrNull() ?: -1

    /** Vrai si le joueur qui joue peut retourner la plus grande ([high]) ou la plus petite carte cachée de la main de [player]. */
    fun canFlipHand(player: Int, high: Boolean): Boolean =
        canFlip() && player in 0 until playerCount && (if (high) highIndex(player) else lowIndex(player)) >= 0

    /** Vrai si le joueur qui joue peut retourner la carte [index] du milieu. */
    fun canFlipCenter(index: Int): Boolean = canFlip() && index in center.indices && !isCenterRevealed(index)

    private fun canFlip(): Boolean = !roundOver && !mismatch && reveals.size < TrioScoring.COPIES

    /** Points de chacun pour la manche : 1 pour le gagnant, 0 pour les autres (et pour tous tant que la manche n'est pas finie). */
    fun roundPoints(): List<Int> = List(playerCount) { if (roundOver && it == winner) 1 else 0 }

    // ---------- Le tour ----------

    /** Le joueur dont c'est le tour montre sa main (les autres ne regardent pas). */
    fun show() {
        if (!roundOver) shown = true
    }

    /** Recouvre la main du joueur qui joue (sa place n'est plus jouée sur ce téléphone). */
    fun hide() {
        shown = false
    }

    /**
     * Retourne la plus grande ([high]) ou la plus petite carte encore cachée de la main de [player] : demandée plusieurs fois,
     * la plus petite carte donne chaque fois la suivante. Renvoie faux, sans rien changer, si ce n'est pas permis (manche
     * finie, tour déjà perdu, main sans carte cachée).
     */
    fun flipHand(player: Int, high: Boolean): Boolean {
        if (!canFlipHand(player, high)) return false
        val index = if (high) highIndex(player) else lowIndex(player)
        return addReveal(TrioReveal(TrioZone.HAND, player, index, hands[player][index]))
    }

    /** Retourne la carte [index] du milieu. Renvoie faux, sans rien changer, si ce n'est pas permis. */
    fun flipCenter(index: Int): Boolean {
        if (!canFlipCenter(index)) return false
        return addReveal(TrioReveal(TrioZone.CENTER, -1, index, center[index]))
    }

    private fun addReveal(card: TrioReveal): Boolean {
        val cards = reveals + card
        moves++
        lastTrio = 0
        if (cards.size >= 2 && card.value != cards.first().value) {
            // Une carte différente : le tour est perdu, les cartes restent visibles le temps que chacun les voie.
            reveals = cards
            mismatch = true
        } else if (cards.size < TrioScoring.COPIES) {
            reveals = cards
        } else {
            claim(cards)
        }
        return true
    }

    /** Trois cartes identiques : le joueur gagne le trio (les cartes quittent le jeu) et le tour passe au suivant, sauf s'il a gagné la manche. */
    private fun claim(cards: List<TrioReveal>) {
        val player = current
        val value = cards.first().value
        hands = hands.mapIndexed { owner, hand ->
            val gone = cards.filter { it.zone == TrioZone.HAND && it.player == owner }.map { it.index }.toSet()
            if (gone.isEmpty()) hand else hand.filterIndexed { index, _ -> index !in gone }
        }
        val goneCenter = cards.filter { it.zone == TrioZone.CENTER }.map { it.index }.toSet()
        if (goneCenter.isNotEmpty()) center = center.filterIndexed { index, _ -> index !in goneCenter }
        trios = trios.mapIndexed { owner, list -> if (owner == player) list + value else list }
        reveals = emptyList()
        lastTrio = value
        val reason = TrioScoring.winReason(trios[player])
        if (reason != null) {
            finish(player, reason)
        } else if (hands.all { it.isEmpty() } && center.isEmpty()) {
            // Tous les trios sont pris : le plus fourni l'emporte, à égalité personne.
            val counts = trios.map { it.size }
            val most = counts.maxOrNull() ?: 0
            val leaders = counts.indices.filter { counts[it] == most }
            if (leaders.size == 1) finish(leaders.first(), TrioWin.MOST) else finish(-1, TrioWin.NONE)
        } else {
            current = (current + 1) % playerCount.coerceAtLeast(1)
            shown = false
        }
    }

    private fun finish(player: Int, reason: TrioWin) {
        roundOver = true
        winner = player
        winReason = reason
        shown = false
    }

    /** Termine un tour perdu : les cartes retournées sont recachées et le tour passe au joueur suivant. Faux si le tour n'est pas perdu. */
    fun endTurn(): Boolean {
        if (!mismatch || roundOver) return false
        reveals = emptyList()
        mismatch = false
        lastTrio = 0
        current = (current + 1) % playerCount.coerceAtLeast(1)
        shown = false
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

    /** Distribue la manche suivante ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        roundNumber++
        deal()
        return true
    }

    /** Mélange les 36 cartes, distribue les mains (rangées par ordre croissant) et laisse le reste au milieu. */
    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val deck = (1..TrioScoring.NUMBERS).flatMap { number -> List(TrioScoring.COPIES) { number } }.toMutableList()
        shuffle(deck)
        val size = TrioScoring.handSize(count)
        hands = List(count) { player -> deck.subList(player * size, (player + 1) * size).sorted() }
        center = deck.drop(count * size)
        clearTurn()
        // Le joueur qui commence change à chaque manche.
        current = (roundNumber - 1) % count
    }

    private fun clearTurn() {
        trios = List(playerCount.coerceAtLeast(1)) { emptyList() }
        current = 0
        reveals = emptyList()
        mismatch = false
        lastTrio = 0
        shown = false
        roundOver = false
        winner = -1
        winReason = TrioWin.NONE
        settled = false
        moves = 0
    }

    // ---------- Chacun sur son téléphone ----------

    /**
     * La table envoyée au téléphone de la place [viewer] (-1 : une place sans téléphone) : les trios, les cartes retournées, les tailles
     * des mains et du milieu, et sa propre main. Les autres mains et le milieu ne partent jamais, sauf une fois la manche finie.
     */
    fun tableFor(viewer: Int): TrioView = TrioView(
        playerCount = playerCount,
        roundNumber = roundNumber,
        current = current,
        hands = hands.mapIndexed { player, hand -> if (roundOver || player == viewer) hand else List(hand.size) { 0 } },
        center = if (roundOver) center else List(center.size) { 0 },
        trios = trios,
        reveals = reveals,
        mismatch = mismatch,
        lastTrio = lastTrio,
        roundOver = roundOver,
        winner = winner,
        winReason = winReason,
        settled = settled
    )

    /**
     * Sur un téléphone invité : recopie la table reçue de l'organisateur dans cette manche, qui ne sert alors qu'à l'affichage (les
     * actions de l'invité partent vers l'organisateur). Les cartes des autres et du milieu sont des cartes sans valeur, de la bonne taille.
     */
    fun applyTable(view: TrioView) {
        Snapshot.withMutableSnapshot {
            roundNumber = view.roundNumber
            current = view.current
            hands = view.hands
            center = view.center
            trios = view.trios
            reveals = view.reveals
            mismatch = view.mismatch
            lastTrio = view.lastTrio
            shown = false
            roundOver = view.roundOver
            winner = view.winner
            winReason = view.winReason
            settled = view.settled
        }
    }

    /**
     * Installe une table précise (mains, milieu, trios déjà gagnés) : sert aux tests. Les mains sont rangées par ordre croissant ;
     * [current] joue.
     */
    internal fun load(
        hands: List<List<Int>>,
        center: List<Int>,
        trios: List<List<Int>> = List(playerCount) { emptyList() },
        current: Int = 0
    ) {
        require(hands.size == playerCount && trios.size == playerCount && current in 0 until playerCount)
        clearTurn()
        this.hands = hands.map { it.sorted() }
        this.center = center
        this.trios = trios
        this.current = current
    }
}
