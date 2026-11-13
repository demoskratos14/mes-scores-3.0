package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val SKYJO_ACTION_ID = "builtin_skyjo_action"

/** Les neuf cartes action du Skyjo Action. */
internal enum class SkyjoAction { EXCHANGE, SWAP_OWN, DRAW_THREE, LOOK, METEOR, DOUBLE_MOVE, PROTECTION, REACTIVATION, STEAL }

/** Une carte de la grille : sa valeur (voir [SkyjoActionScoring]) et si elle est face visible. Une place vide est `null`. */
internal data class SkyjoCell(val card: Int, val up: Boolean)

/** Une carte action gardée devant un joueur ; elle ne peut être jouée qu'à partir du tour numéro [unlockTurn]. */
internal data class SkyjoHeldAction(val action: SkyjoAction, val unlockTurn: Int)

/** Une carte cachée regardée avec la carte « Regarder » : à qui est-elle, à quelle place, et sa valeur. */
internal data class SkyjoPeek(val player: Int, val cell: Int, val card: Int)

internal enum class SkyjoEventKind { ACTION_PLAYED, BLOCKED, LINE_REMOVED, EXTRA_TURN, END_TRIGGERED, STOLE }

/** Ce qui s'est passé pendant un tour, pour l'annoncer au joueur suivant. */
internal data class SkyjoEvent(val kind: SkyjoEventKind, val player: Int, val other: Int = -1, val action: SkyjoAction? = null)

internal enum class SkyjoPhase {
    /** Début de tour : piocher, prendre la défausse, prendre ou jouer une carte action. */
    CHOOSE,

    /** Une carte est en main ([SkyjoActionRound.held]) : l'échanger avec une carte de sa grille (ou la défausser pour retourner une carte). */
    PLACE,

    /** Une étoile vient d'être posée ou retournée : on peut prendre une carte action. */
    STAR_BONUS,

    /** « Piocher trois cartes » : trois cartes sont visibles, on en garde une ou aucune. */
    THREE,

    /** « Échange personnel » : choisir deux cartes de sa grille. */
    SWAP,

    /** « Échange » : choisir une carte dans la grille d'un joueur puis une dans celle d'un autre. */
    EXCHANGE,

    /** « Regarder » : choisir la ligne ou la colonne à regarder. */
    LOOK,

    /** Les cartes regardées sont affichées au joueur seul. */
    PEEK,

    /** « Météores » : le joueur choisit une carte de chaque adversaire à retirer. */
    METEOR,

    /** « Réactivation » : choisir une carte de la défausse action à rejouer. */
    REACTIVATE,

    /** « Voleur » : choisir la carte action d'un adversaire à lui prendre. */
    STEAL,

    ROUND_OVER
}

/** Composition du jeu et règles de calcul du Skyjo Action. */
internal object SkyjoActionScoring {
    /** Valeur de la carte étoile (joker). */
    const val STAR = 13
    const val MIN_VALUE = -2
    const val MAX_VALUE = 12

    /** Exemplaires de chaque valeur de -2 à 12, et d'étoiles (15 × 7 + 15 = 120 cartes). */
    const val COPIES = 7
    const val STARS = 15
    const val ROWS = 3
    const val ACTION_ROW = 4
    const val ACTION_PENALTY = 10
    const val STAR_COLUMN_BONUS = -10
    const val STAR_ROW_BONUS = -15

    /** Les 30 cartes action. */
    val ACTION_COPIES: Map<SkyjoAction, Int> = mapOf(
        SkyjoAction.EXCHANGE to 4,
        SkyjoAction.SWAP_OWN to 4,
        SkyjoAction.DRAW_THREE to 3,
        SkyjoAction.LOOK to 3,
        SkyjoAction.METEOR to 3,
        SkyjoAction.DOUBLE_MOVE to 3,
        SkyjoAction.PROTECTION to 4,
        SkyjoAction.REACTIVATION to 3,
        SkyjoAction.STEAL to 3
    )

    fun isStar(card: Int): Boolean = card == STAR

    /** Points d'une carte : sa valeur, 0 pour l'étoile. */
    fun points(card: Int): Int = if (card == STAR) 0 else card

    fun numberDeck(): MutableList<Int> {
        val deck = mutableListOf<Int>()
        for (value in MIN_VALUE..MAX_VALUE) repeat(COPIES) { deck.add(value) }
        repeat(STARS) { deck.add(STAR) }
        return deck
    }

    fun actionDeck(): MutableList<SkyjoAction> {
        val deck = mutableListOf<SkyjoAction>()
        ACTION_COPIES.forEach { (action, copies) -> repeat(copies) { deck.add(action) } }
        return deck
    }

    /** 12 cartes (4 colonnes de 3) jusqu'à 4 joueurs, 9 cartes (3 colonnes) de 5 à 8. */
    fun columns(players: Int): Int = if (players <= 4) 4 else 3

    fun gridSize(players: Int): Int = ROWS * columns(players)

    fun revealedAtStart(players: Int): Int = if (players <= 4) 2 else 1
}

/**
 * Le Skyjo Action tour par tour. Toutes les cartes de la grille sont connues de tous (cachées pour tout le monde) : seule la
 * carte « Regarder » montre un secret, à un seul joueur ([peek]). Une manche se déroule ainsi :
 * - chaque joueur a une grille de cartes cachées ([grids]) dont 2 (1 à 5 joueurs et plus) sont retournées ; une étoile retournée
 *   donne le droit de prendre une carte action ;
 * - à son tour on pioche (et on garde la carte en l'échangeant, ou on la défausse pour retourner une carte), on prend la dernière
 *   défausse (à échanger), on prend une carte action visible ou de la pioche action, ou on joue une carte action déjà gardée ;
 * - les colonnes de 3 et les lignes complètes de cartes identiques (l'étoile sert de joker) sont retirées ;
 * - quand un joueur n'a plus de carte cachée, les autres jouent un dernier tour (sans carte action), puis on compte : valeur
 *   des cartes, étoiles à 0 (-10 pour une colonne d'étoiles, -15 pour une ligne), 10 points par carte action gardée ; celui
 *   qui a terminé double ses points positifs s'il n'a pas le score strictement le plus bas.
 * [settle] inscrit les points au tableau des scores et [nextRound] distribue la manche suivante, commencée par celui qui a terminé.
 *
 * [startPlayer] : le joueur qui commence ; sans valeur, c'est celui dont les cartes retournées totalisent le plus.
 * [shuffle] mélange une liste (remplaçable dans les tests).
 */
internal class SkyjoActionRound(
    val playerCount: Int,
    startPlayer: Int? = null,
    startRound: Int = 1,
    private val shuffle: (MutableList<*>) -> Unit = { java.util.Collections.shuffle(it) }
) {
    private var nextStarter: Int? = startPlayer

    val columns: Int = SkyjoActionScoring.columns(playerCount)

    var grids by mutableStateOf<List<List<SkyjoCell?>>>(emptyList())
        private set
    var stock by mutableStateOf<List<Int>>(emptyList())
        private set

    /** La défausse ; la dernière carte est celle du dessus. */
    var discard by mutableStateOf<List<Int>>(emptyList())
        private set
    var actionRow by mutableStateOf<List<SkyjoAction>>(emptyList())
        private set
    var actionStock by mutableStateOf<List<SkyjoAction>>(emptyList())
        private set

    /** La défausse action ; la dernière carte est celle du dessus. */
    var actionDiscard by mutableStateOf<List<SkyjoAction>>(emptyList())
        private set
    var hands by mutableStateOf<List<List<SkyjoHeldAction>>>(emptyList())
        private set

    var current by mutableIntStateOf(0)
        private set
    var phase by mutableStateOf(SkyjoPhase.CHOOSE)
        private set

    /** La carte tenue en main (piochée ou prise dans la défausse), le temps de la placer. */
    var held by mutableStateOf<Int?>(null)
        private set
    var heldFromDiscard by mutableStateOf(false)
        private set

    /** Les cartes de « Piocher trois cartes ». */
    var three by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Première carte choisie (joueur, place) d'un échange. */
    var firstPick by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    /** Les adversaires qui doivent encore perdre une carte (Météores) ; le premier est celui qui est en train d'être choisi. */
    var meteorQueue by mutableStateOf<List<Int>>(emptyList())
        private set
    var peek by mutableStateOf<List<SkyjoPeek>>(emptyList())
        private set
    var peekShown by mutableStateOf(false)
        private set

    /** Les joueurs qui ont droit à une carte action grâce à une étoile, dans l'ordre. */
    var bonusQueue by mutableStateOf<List<Int>>(emptyList())
        private set

    /** Le joueur qui a terminé la manche (-1 : personne pour l'instant). */
    var endBy by mutableIntStateOf(-1)
        private set
    var extraTurns by mutableIntStateOf(0)
        private set

    /** Numéro du tour en cours (chaque tour supplémentaire compte). */
    var turnNumber by mutableIntStateOf(1)
        private set
    var roundOver by mutableStateOf(false)
        private set
    var settled by mutableStateOf(false)
        private set
    var roundNumber by mutableIntStateOf(startRound.coerceAtLeast(1))
        private set

    /** Ce qui s'est passé pendant le tour précédent. */
    var events by mutableStateOf<List<SkyjoEvent>>(emptyList())
        private set
    private var pendingEvents = listOf<SkyjoEvent>()
    private var setupBonus = false
    private var moves by mutableIntStateOf(0)

    init {
        deal()
    }

    // ---------- Lecture de la table ----------

    /** Vrai tant qu'une manche est commencée (sert à demander confirmation avant de quitter). */
    val inProgress: Boolean get() = moves > 0 && !settled

    /** Le joueur qui doit agir : celui qui prend une carte action grâce à une étoile, sinon le joueur dont c'est le tour. */
    val actor: Int get() = if (phase == SkyjoPhase.STAR_BONUS && bonusQueue.isNotEmpty()) bonusQueue.first() else current

    /** Vrai pendant le dernier tour de ceux qui n'ont pas terminé la manche : plus de carte action. */
    val finalTurn: Boolean get() = endBy >= 0 && !roundOver

    val canDrawFromStock: Boolean get() = stock.isNotEmpty() || discard.size > 1

    private fun cells(player: Int): List<SkyjoCell?> = grids.getOrElse(player) { emptyList() }

    fun faceDownCount(player: Int): Int = cells(player).count { it != null && !it.up }

    fun holdsProtection(player: Int): Boolean = hands.getOrElse(player) { emptyList() }.any { it.action == SkyjoAction.PROTECTION }

    /** Vrai si la carte action [index] du joueur en cours peut être jouée maintenant. */
    fun canPlayAction(index: Int): Boolean {
        if (phase != SkyjoPhase.CHOOSE || finalTurn) return false
        val card = hands.getOrNull(current)?.getOrNull(index) ?: return false
        return card.unlockTurn <= turnNumber && canResolve(card.action)
    }

    private fun canResolve(action: SkyjoAction): Boolean = when (action) {
        SkyjoAction.SWAP_OWN -> cells(current).count { it != null } >= 2
        SkyjoAction.DRAW_THREE -> canDrawFromStock
        SkyjoAction.LOOK -> grids.indices.any { faceDownCount(it) > 0 }
        SkyjoAction.REACTIVATION -> actionDiscard.any { it != SkyjoAction.REACTIVATION && canResolve(it) }
        SkyjoAction.STEAL -> hands.indices.any { it != current && hands[it].isNotEmpty() }
        else -> true
    }

    /** Les cartes de la défausse action qu'on peut rejouer avec la Réactivation. */
    fun reactivatable(): List<Int> =
        actionDiscard.indices.filter { actionDiscard[it] != SkyjoAction.REACTIVATION && canResolve(actionDiscard[it]) }

    // ---------- Début de tour : piocher, prendre la défausse, prendre ou jouer une action ----------

    fun drawFromStock(): Boolean {
        if (phase != SkyjoPhase.CHOOSE) return false
        val card = drawNumber() ?: return false
        held = card
        heldFromDiscard = false
        phase = SkyjoPhase.PLACE
        return true
    }

    fun takeFromDiscard(): Boolean {
        if (phase != SkyjoPhase.CHOOSE || discard.isEmpty()) return false
        held = discard.last()
        discard = discard.dropLast(1)
        heldFromDiscard = true
        phase = SkyjoPhase.PLACE
        return true
    }

    fun takeActionFromRow(index: Int): Boolean {
        if (phase != SkyjoPhase.CHOOSE || finalTurn) return false
        val action = actionRow.getOrNull(index) ?: return false
        removeFromRow(index)
        gainAction(current, action, turnNumber + 1)
        endTurn()
        return true
    }

    fun takeActionFromDeck(): Boolean {
        if (phase != SkyjoPhase.CHOOSE || finalTurn) return false
        val action = drawAction() ?: return false
        gainAction(current, action, turnNumber + 1)
        endTurn()
        return true
    }

    /** Joue la carte action [index] de sa main : sa carte va à la défausse action et son effet commence. */
    fun playAction(index: Int): Boolean {
        if (!canPlayAction(index)) return false
        val card = hands[current][index]
        hands = hands.mapIndexed { p, list -> if (p == current) list.filterIndexed { i, _ -> i != index } else list }
        actionDiscard = actionDiscard + card.action
        pendingEvents = pendingEvents + SkyjoEvent(SkyjoEventKind.ACTION_PLAYED, current, action = card.action)
        begin(card.action)
        return true
    }

    /** Défausse la carte action [index] sans l'activer (le tour est fini). */
    fun discardAction(index: Int): Boolean {
        if (phase != SkyjoPhase.CHOOSE || finalTurn) return false
        val card = hands.getOrNull(current)?.getOrNull(index) ?: return false
        if (card.unlockTurn > turnNumber) return false
        hands = hands.mapIndexed { p, list -> if (p == current) list.filterIndexed { i, _ -> i != index } else list }
        actionDiscard = actionDiscard + card.action
        endTurn()
        return true
    }

    // ---------- Placer la carte en main ----------

    /** Échange la carte en main avec la carte [cell] de sa grille ; l'ancienne carte va (face visible) sur la défausse. */
    fun placeHeld(cell: Int): Boolean {
        if (phase != SkyjoPhase.PLACE) return false
        val card = held ?: return false
        val old = cells(current).getOrNull(cell) ?: return false
        setCell(current, cell, SkyjoCell(card, true))
        discard = discard + old.card
        held = null
        afterPlacement(SkyjoActionScoring.isStar(card))
        return true
    }

    /** Défausse la carte piochée et retourne la carte cachée [cell] (impossible avec une carte prise dans la défausse). */
    fun discardHeldAndFlip(cell: Int): Boolean {
        if (phase != SkyjoPhase.PLACE || heldFromDiscard) return false
        val card = held ?: return false
        val target = cells(current).getOrNull(cell) ?: return false
        if (target.up) return false
        discard = discard + card
        held = null
        setCell(current, cell, target.copy(up = true))
        afterPlacement(SkyjoActionScoring.isStar(target.card))
        return true
    }

    // ---------- Bonus d'étoile ----------

    fun takeBonusFromRow(index: Int): Boolean {
        if (phase != SkyjoPhase.STAR_BONUS || bonusQueue.isEmpty()) return false
        val action = actionRow.getOrNull(index) ?: return false
        removeFromRow(index)
        gainAction(bonusQueue.first(), action, if (setupBonus) turnNumber else turnNumber + 1)
        finishBonus()
        return true
    }

    fun takeBonusFromDeck(): Boolean {
        if (phase != SkyjoPhase.STAR_BONUS || bonusQueue.isEmpty()) return false
        val action = drawAction() ?: return false
        gainAction(bonusQueue.first(), action, if (setupBonus) turnNumber else turnNumber + 1)
        finishBonus()
        return true
    }

    fun skipBonus(): Boolean {
        if (phase != SkyjoPhase.STAR_BONUS || bonusQueue.isEmpty()) return false
        finishBonus()
        return true
    }

    private fun finishBonus() {
        bonusQueue = bonusQueue.drop(1)
        if (bonusQueue.isNotEmpty()) return
        if (setupBonus) {
            setupBonus = false
            phase = SkyjoPhase.CHOOSE
        } else {
            endTurn()
        }
    }

    private fun afterPlacement(star: Boolean) {
        if (star && (actionRow.isNotEmpty() || actionStock.isNotEmpty() || actionDiscard.isNotEmpty())) {
            bonusQueue = listOf(current)
            phase = SkyjoPhase.STAR_BONUS
        } else {
            endTurn()
        }
    }

    // ---------- Les cartes action ----------

    private fun begin(action: SkyjoAction) {
        when (action) {
            SkyjoAction.SWAP_OWN -> {
                firstPick = null
                phase = SkyjoPhase.SWAP
            }
            SkyjoAction.EXCHANGE -> {
                firstPick = null
                phase = SkyjoPhase.EXCHANGE
            }
            SkyjoAction.DRAW_THREE -> {
                val taken = mutableListOf<Int>()
                repeat(3) { drawNumber()?.let { taken.add(it) } }
                three = taken
                phase = SkyjoPhase.THREE
            }
            SkyjoAction.LOOK -> phase = SkyjoPhase.LOOK
            SkyjoAction.METEOR -> {
                val targets = mutableListOf<Int>()
                for (step in 1 until playerCount) {
                    val victim = (current + step) % playerCount
                    if (cells(victim).none { it != null }) continue
                    if (holdsProtection(victim)) block(victim, SkyjoAction.METEOR) else targets.add(victim)
                }
                meteorQueue = targets
                if (targets.isEmpty()) endTurn() else phase = SkyjoPhase.METEOR
            }
            SkyjoAction.DOUBLE_MOVE -> {
                extraTurns += 2
                endTurn()
            }
            SkyjoAction.PROTECTION -> {
                extraTurns += 1
                endTurn()
            }
            SkyjoAction.REACTIVATION -> phase = SkyjoPhase.REACTIVATE
            SkyjoAction.STEAL -> phase = SkyjoPhase.STEAL
        }
    }

    /** Une protection annule l'attaque et disparaît (elle va à la défausse action). */
    private fun block(victim: Int, action: SkyjoAction) {
        val index = hands[victim].indexOfFirst { it.action == SkyjoAction.PROTECTION }
        if (index < 0) return
        hands = hands.mapIndexed { p, list -> if (p == victim) list.filterIndexed { i, _ -> i != index } else list }
        actionDiscard = actionDiscard + SkyjoAction.PROTECTION
        pendingEvents = pendingEvents + SkyjoEvent(SkyjoEventKind.BLOCKED, victim, action = action)
    }

    /** « Réactivation » : rejoue la carte [index] de la défausse action (elle remonte sur le dessus de la pile). */
    fun reactivate(index: Int): Boolean {
        if (phase != SkyjoPhase.REACTIVATE || index !in reactivatable()) return false
        val action = actionDiscard[index]
        actionDiscard = actionDiscard.filterIndexed { i, _ -> i != index } + action
        pendingEvents = pendingEvents + SkyjoEvent(SkyjoEventKind.ACTION_PLAYED, current, action = action)
        begin(action)
        return true
    }

    /** « Piocher trois cartes » : garde la carte [index] en l'échangeant avec la carte [cell] ; les autres vont à la défausse. */
    fun threeKeep(index: Int, cell: Int): Boolean {
        if (phase != SkyjoPhase.THREE) return false
        val card = three.getOrNull(index) ?: return false
        val old = cells(current).getOrNull(cell) ?: return false
        setCell(current, cell, SkyjoCell(card, true))
        discard = discard + three.filterIndexed { i, _ -> i != index } + old.card
        three = emptyList()
        afterPlacement(SkyjoActionScoring.isStar(card))
        return true
    }

    /** « Piocher trois cartes » : n'en garde aucune, les trois vont à la défausse et on retourne la carte cachée [cell]. */
    fun threeNone(cell: Int): Boolean {
        if (phase != SkyjoPhase.THREE) return false
        val target = cells(current).getOrNull(cell) ?: return false
        if (target.up) return false
        discard = discard + three
        three = emptyList()
        setCell(current, cell, target.copy(up = true))
        afterPlacement(SkyjoActionScoring.isStar(target.card))
        return true
    }

    /** « Échange personnel » : deux cartes de sa grille changent de place (chacune garde sa face). */
    fun swapPick(cell: Int): Boolean {
        if (phase != SkyjoPhase.SWAP) return false
        if (cells(current).getOrNull(cell) == null) return false
        val first = firstPick
        if (first == null) {
            firstPick = current to cell
            return true
        }
        if (first.second == cell) {
            firstPick = null
            return true
        }
        exchangeCells(current, first.second, current, cell)
        firstPick = null
        endTurn()
        return true
    }

    /** « Échange » : une carte dans la grille d'un joueur, puis une dans celle d'un autre joueur. */
    fun exchangePick(player: Int, cell: Int): Boolean {
        if (phase != SkyjoPhase.EXCHANGE) return false
        if (cells(player).getOrNull(cell) == null) return false
        val first = firstPick
        if (first == null || first.first == player) {
            firstPick = if (first == player to cell) null else player to cell
            return true
        }
        val victims = listOf(first.first, player).filter { it != current && holdsProtection(it) }
        if (victims.isEmpty()) {
            exchangeCells(first.first, first.second, player, cell)
        } else {
            victims.forEach { block(it, SkyjoAction.EXCHANGE) }
        }
        firstPick = null
        endTurn()
        return true
    }

    /** « Regarder » : le joueur en cours découvre, seul, les cartes cachées de la ligne ou de la colonne de la carte [cell] de [player]. */
    fun look(player: Int, cell: Int, row: Boolean): Boolean {
        if (phase != SkyjoPhase.LOOK) return false
        val grid = cells(player)
        if (grid.getOrNull(cell) == null) return false
        val line = grid.indices.filter { if (row) it / columns == cell / columns else it % columns == cell % columns }
        val seen = line.mapNotNull { i -> grid[i]?.takeIf { !it.up }?.let { SkyjoPeek(player, i, it.card) } }
        if (seen.isEmpty()) return false
        peek = seen
        peekShown = false
        phase = SkyjoPhase.PEEK
        return true
    }

    fun showPeek(): Boolean {
        if (phase != SkyjoPhase.PEEK) return false
        peekShown = true
        return true
    }

    fun closePeek(): Boolean {
        if (phase != SkyjoPhase.PEEK || !peekShown) return false
        peek = emptyList()
        peekShown = false
        endTurn()
        return true
    }

    /** « Météores » : retire la carte [cell] de [player] (le prochain adversaire de la liste) ; elle est remplacée par une carte de la pioche, face visible. */
    fun meteorPick(player: Int, cell: Int): Boolean {
        if (phase != SkyjoPhase.METEOR || meteorQueue.firstOrNull() != player) return false
        val old = cells(player).getOrNull(cell) ?: return false
        discard = listOf(old.card) + discard
        val replacement = drawNumber()
        replaceCell(player, cell, replacement?.let { SkyjoCell(it, true) })
        meteorQueue = meteorQueue.drop(1)
        if (meteorQueue.isEmpty()) endTurn()
        return true
    }

    /** « Voleur » : prend la carte action [index] de [player] ; on rejoue aussitôt, mais sans pouvoir se servir de cette carte à ce tour. */
    fun steal(player: Int, index: Int): Boolean {
        if (phase != SkyjoPhase.STEAL || player == current) return false
        val card = hands.getOrNull(player)?.getOrNull(index) ?: return false
        if (holdsProtection(player)) {
            block(player, SkyjoAction.STEAL)
            endTurn()
            return true
        }
        hands = hands.mapIndexed { p, list -> if (p == player) list.filterIndexed { i, _ -> i != index } else list }
        gainAction(current, card.action, turnNumber + 2)
        extraTurns += 1
        pendingEvents = pendingEvents + SkyjoEvent(SkyjoEventKind.STOLE, current, player, card.action)
        endTurn()
        return true
    }

    // ---------- Fin de tour et fin de manche ----------

    private fun endTurn() {
        held = null
        three = emptyList()
        firstPick = null
        peek = emptyList()
        meteorQueue = emptyList()
        for (player in 0 until playerCount) removeLines(player)
        moves++
        if (endBy < 0 && (0 until playerCount).any { faceDownCount(it) == 0 }) {
            endBy = current
            extraTurns = 0
            pendingEvents = pendingEvents + SkyjoEvent(SkyjoEventKind.END_TRIGGERED, current)
        }
        if (endBy < 0 && extraTurns > 0) {
            extraTurns--
            pendingEvents = pendingEvents + SkyjoEvent(SkyjoEventKind.EXTRA_TURN, current)
            events = pendingEvents
            pendingEvents = emptyList()
            turnNumber++
            phase = SkyjoPhase.CHOOSE
            return
        }
        val next = (current + 1) % playerCount.coerceAtLeast(1)
        if (endBy >= 0 && next == endBy) {
            finishRound()
            return
        }
        events = pendingEvents
        pendingEvents = emptyList()
        current = next
        turnNumber++
        phase = SkyjoPhase.CHOOSE
    }

    private fun finishRound() {
        grids = grids.map { grid -> grid.map { it?.copy(up = true) } }
        for (player in 0 until playerCount) removeLines(player)
        events = pendingEvents
        pendingEvents = emptyList()
        phase = SkyjoPhase.ROUND_OVER
        roundOver = true
    }

    /** Les points de la grille de [player] : valeur des cartes, -10 par colonne d'étoiles et -15 par ligne d'étoiles. */
    fun gridPoints(player: Int): Int {
        val grid = cells(player)
        var total = grid.filterNotNull().sumOf { SkyjoActionScoring.points(it.card) }
        val used = HashSet<Int>()
        for (r in 0 until SkyjoActionScoring.ROWS) {
            val line = (0 until columns).map { r * columns + it }
            if (line.all { grid.getOrNull(it)?.card == SkyjoActionScoring.STAR }) {
                total += SkyjoActionScoring.STAR_ROW_BONUS
                used.addAll(line)
            }
        }
        for (c in 0 until columns) {
            val line = (0 until SkyjoActionScoring.ROWS).map { it * columns + c }
            if (line.none { it in used } && line.all { grid.getOrNull(it)?.card == SkyjoActionScoring.STAR }) {
                total += SkyjoActionScoring.STAR_COLUMN_BONUS
                used.addAll(line)
            }
        }
        return total
    }

    private fun rawPoints(player: Int): Int =
        gridPoints(player) + SkyjoActionScoring.ACTION_PENALTY * hands.getOrElse(player) { emptyList() }.size

    /** Vrai si celui qui a terminé la manche n'a pas le score strictement le plus bas (et un score positif) : ses points sont doublés. */
    fun enderDoubled(): Boolean {
        if (!roundOver || endBy < 0) return false
        val mine = rawPoints(endBy)
        return mine > 0 && (0 until playerCount).any { it != endBy && rawPoints(it) <= mine }
    }

    /** Les points de la manche de chaque joueur (après le doublement éventuel de celui qui a terminé). */
    fun roundPoints(): List<Int> = (0 until playerCount).map { player ->
        val raw = rawPoints(player)
        if (player == endBy && enderDoubled()) raw * 2 else raw
    }

    /** Inscrit les points de la manche finie au tableau des scores ; vrai si c'est fait à l'instant. */
    fun settle(viewModel: ScoreViewModel): Boolean {
        if (!roundOver || settled) return false
        if (!writeRoundPoints(viewModel, roundPoints())) return false
        settled = true
        return true
    }

    /** Distribue la manche suivante, commencée par celui qui a terminé ; faux tant que celle-ci n'est pas inscrite. */
    fun nextRound(): Boolean {
        if (!settled) return false
        roundNumber++
        nextStarter = endBy.takeIf { it >= 0 }
        deal()
        return true
    }

    // ---------- Distribution ----------

    private fun deal() {
        val count = playerCount.coerceAtLeast(1)
        val size = SkyjoActionScoring.gridSize(count)
        val deck = SkyjoActionScoring.numberDeck()
        shuffle(deck)
        val dealt = List(count) { player ->
            List(size) { SkyjoCell(deck[player * size + it], false) as SkyjoCell? }.toMutableList()
        }
        val rest = deck.drop(count * size).toMutableList()
        for (player in 0 until count) {
            val indices = (0 until size).toMutableList()
            shuffle(indices)
            indices.take(SkyjoActionScoring.revealedAtStart(count)).forEach { dealt[player][it] = dealt[player][it]!!.copy(up = true) }
        }
        grids = dealt.map { it.toList() }
        discard = listOf(rest.removeAt(0))
        stock = rest
        val actions = SkyjoActionScoring.actionDeck()
        shuffle(actions)
        actionRow = actions.take(SkyjoActionScoring.ACTION_ROW)
        actionStock = actions.drop(SkyjoActionScoring.ACTION_ROW)
        actionDiscard = emptyList()
        hands = List(count) { emptyList() }
        val sums = (0 until count).map { p -> cells(p).filterNotNull().filter { it.up }.sumOf { SkyjoActionScoring.points(it.card) } }
        val first = nextStarter?.coerceIn(0, count - 1) ?: sums.indices.maxByOrNull { sums[it] } ?: 0
        nextStarter = null
        current = first
        val queue = mutableListOf<Int>()
        for (step in 0 until count) {
            val p = (first + step) % count
            repeat(cells(p).count { it != null && it.up && it.card == SkyjoActionScoring.STAR }) { queue.add(p) }
        }
        bonusQueue = if (actionRow.isEmpty()) emptyList() else queue
        setupBonus = bonusQueue.isNotEmpty()
        phase = if (setupBonus) SkyjoPhase.STAR_BONUS else SkyjoPhase.CHOOSE
        held = null
        heldFromDiscard = false
        three = emptyList()
        firstPick = null
        meteorQueue = emptyList()
        peek = emptyList()
        peekShown = false
        endBy = -1
        extraTurns = 0
        turnNumber = 1
        roundOver = false
        settled = false
        events = emptyList()
        pendingEvents = emptyList()
        moves = 0
    }

    /** Installe une table précise : sert aux tests. */
    internal fun load(
        grids: List<List<SkyjoCell?>>,
        stock: List<Int> = emptyList(),
        discard: List<Int> = listOf(0),
        current: Int = 0,
        actionRow: List<SkyjoAction> = emptyList(),
        actionStock: List<SkyjoAction> = emptyList(),
        actionDiscard: List<SkyjoAction> = emptyList(),
        hands: List<List<SkyjoHeldAction>> = List(playerCount) { emptyList() }
    ) {
        require(grids.size == playerCount && current in 0 until playerCount)
        this.grids = grids
        this.stock = stock
        this.discard = discard
        this.current = current
        this.actionRow = actionRow
        this.actionStock = actionStock
        this.actionDiscard = actionDiscard
        this.hands = hands
        phase = SkyjoPhase.CHOOSE
        held = null
        three = emptyList()
        firstPick = null
        meteorQueue = emptyList()
        peek = emptyList()
        bonusQueue = emptyList()
        setupBonus = false
        endBy = -1
        extraTurns = 0
        turnNumber = 1
        roundOver = false
        settled = false
        events = emptyList()
        pendingEvents = emptyList()
        moves = 0
    }

    // ---------- Outils ----------

    private fun setCell(player: Int, cell: Int, value: SkyjoCell) = replaceCell(player, cell, value)

    private fun replaceCell(player: Int, cell: Int, value: SkyjoCell?) {
        grids = grids.mapIndexed { p, grid -> if (p == player) grid.mapIndexed { i, c -> if (i == cell) value else c } else grid }
    }

    private fun exchangeCells(p1: Int, c1: Int, p2: Int, c2: Int) {
        val a = cells(p1)[c1]
        val b = cells(p2)[c2]
        replaceCell(p1, c1, b)
        replaceCell(p2, c2, a)
    }

    private fun drawNumber(): Int? {
        if (stock.isEmpty() && discard.size > 1) {
            val top = discard.last()
            val rest = discard.dropLast(1).toMutableList()
            shuffle(rest)
            stock = rest
            discard = listOf(top)
        }
        val card = stock.firstOrNull() ?: return null
        stock = stock.drop(1)
        return card
    }

    private fun drawAction(): SkyjoAction? {
        if (actionStock.isEmpty() && actionDiscard.isNotEmpty()) {
            val pile = actionDiscard.toMutableList()
            shuffle(pile)
            actionStock = pile
            actionDiscard = emptyList()
        }
        val card = actionStock.firstOrNull() ?: return null
        actionStock = actionStock.drop(1)
        return card
    }

    private fun removeFromRow(index: Int) {
        val replacement = drawAction()
        actionRow = if (replacement != null) {
            actionRow.mapIndexed { i, a -> if (i == index) replacement else a }
        } else {
            actionRow.filterIndexed { i, _ -> i != index }
        }
    }

    private fun gainAction(player: Int, action: SkyjoAction, unlockTurn: Int) {
        hands = hands.mapIndexed { p, list -> if (p == player) list + SkyjoHeldAction(action, unlockTurn) else list }
    }

    /** Les cartes d'une ligne ou colonne sont identiques (l'étoile est un joker, mais il faut au moins un nombre) et toutes visibles. */
    private fun lineMatches(line: List<SkyjoCell?>): Boolean {
        if (line.any { it == null || !it.up }) return false
        val numbers = line.mapNotNull { it?.card }.filter { it != SkyjoActionScoring.STAR }
        return numbers.isNotEmpty() && numbers.toSet().size == 1
    }

    /** Retire de la grille de [player] les colonnes de 3 et les lignes complètes de cartes identiques ; elles vont sur la défausse. */
    private fun removeLines(player: Int) {
        var changed = true
        while (changed) {
            changed = false
            val grid = cells(player)
            val lines = (0 until columns).map { c -> (0 until SkyjoActionScoring.ROWS).map { it * columns + c } } +
                (0 until SkyjoActionScoring.ROWS).map { r -> (0 until columns).map { r * columns + it } }
            val line = lines.firstOrNull { idx -> lineMatches(idx.map { grid.getOrNull(it) }) } ?: break
            discard = discard + line.mapNotNull { grid[it]?.card }
            line.forEach { replaceCell(player, it, null) }
            pendingEvents = pendingEvents + SkyjoEvent(SkyjoEventKind.LINE_REMOVED, player)
            changed = true
        }
    }
}
