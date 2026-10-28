package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/** Valeur des dés du Dix mille : voir la fiche de règles du jeu (« Les points »). */
internal object DixMilleScoring {

    /** Nombre de dés lancés. */
    const val DICE = 6

    /** Points à atteindre pour gagner. */
    const val TARGET = 10000

    /** Points qu'il faut réunir dans un même tour pour ouvrir son score (tant qu'on n'a encore rien marqué). */
    const val OPENING = 500

    /** Suite de 1 à 6 (les six dés). */
    const val STRAIGHT_POINTS = 1500

    /** Trois paires (les six dés). */
    const val THREE_PAIRS_POINTS = 1500

    /** Brelan de la valeur [face] : 100 fois la valeur, mille pour les 1. */
    private fun triple(face: Int): Int = if (face == 1) 1000 else face * 100

    private fun counts(dice: List<Int>): IntArray = IntArray(7).also { counts -> dice.forEach { if (it in 1..6) counts[it]++ } }

    private fun isStraight(counts: IntArray, size: Int): Boolean = size == DICE && (1..6).all { counts[it] == 1 }

    private fun isThreePairs(counts: IntArray, size: Int): Boolean = size == DICE && (1..6).count { counts[it] == 2 } == 3

    /**
     * Les points que rapportent les dés [dice] mis de côté, ou null si l'un d'eux ne rapporte rien (ou s'il n'y en a aucun) :
     * un 1 vaut 100, un 5 vaut 50 ; trois dés identiques valent 100 fois leur valeur (1 000 pour les 1), et chaque dé identique
     * en plus double le total ; la suite de 1 à 6 et les trois paires (six dés) valent 1 500.
     */
    fun score(dice: List<Int>): Int? {
        if (dice.isEmpty() || dice.any { it !in 1..6 }) return null
        val counts = counts(dice)
        if (isStraight(counts, dice.size)) return STRAIGHT_POINTS
        if (isThreePairs(counts, dice.size)) return THREE_PAIRS_POINTS
        var total = 0
        for (face in 1..6) {
            val count = counts[face]
            if (count == 0) continue
            total += when {
                count >= 3 -> triple(face) * (1 shl (count - 3))
                face == 1 -> 100 * count
                face == 5 -> 50 * count
                // Un dé qui ne rapporte rien ne peut pas être mis de côté.
                else -> return null
            }
        }
        return total
    }

    /** Vrai si au moins un dé de [dice] (les dés encore en jeu) rapporte des points : sinon c'est un Farkle. */
    fun canScore(dice: List<Int>): Boolean {
        val counts = counts(dice)
        return counts[1] > 0 || counts[5] > 0 || counts.any { it >= 3 } ||
            isStraight(counts, dice.size) || isThreePairs(counts, dice.size)
    }
}

/**
 * Une partie de Dix mille jouée tour par tour, de la même façon pour tous les téléphones.
 *
 * - Le joueur qui joue lance les 6 dés, puis choisit ([toggle]) les dés qui rapportent des points (tous les dés choisis
 *   doivent rapporter, voir [DixMilleScoring.score]). Il relance ensuite les autres dés ([roll]) ou garde les points du tour
 *   ([keep]) ; tant qu'il n'a rien marqué, il doit réunir [DixMilleScoring.OPENING] points dans un même tour pour pouvoir garder.
 * - Si le lancer ne rapporte rien ([farkle]), les points du tour sont perdus : le joueur n'a plus qu'à passer la main ([pass]).
 * - Quand les six dés ont rapporté, ils sont tous relancés ([hotDice]).
 * - Les points gardés (0 après un Farkle) sont inscrits dans la prochaine case libre du joueur dans le tableau des scores :
 *   le total de chacun, la fin de partie (10 000 points, avec dernier tour pour ceux qui n'ont pas encore joué dans la manche)
 *   et le journal viennent de ce tableau, comme pour une partie notée à la main.
 *
 * [startPlayer] : le joueur qui lance en premier (une partie reprise continue avec le joueur qui n'a pas encore joué dans la manche).
 * [rollDie] tire un dé de 1 à 6 (remplaçable dans les tests).
 */
internal class DixMilleRound(
    val playerCount: Int,
    startPlayer: Int = 0,
    private val rollDie: () -> Int = { Random.nextInt(1, 7) }
) {
    private val first = startPlayer.coerceIn(0, (playerCount - 1).coerceAtLeast(0))

    /** Les dés du joueur qui joue : ceux qui sont gardés ([DiceTurn.held]) sont les dés mis de côté ou choisis, jamais relancés. */
    val turn = DiceTurn(DiceSetup(diceCount = DixMilleScoring.DICE), rollDie)

    /** Le joueur qui joue. */
    var current by mutableIntStateOf(first)
        private set

    /** Points des dés déjà mis de côté pendant ce tour (sans ceux qui sont choisis sur le lancer en cours). */
    var turnPoints by mutableIntStateOf(0)
        private set

    /** Index des dés déjà mis de côté aux lancers précédents : ils ne se choisissent plus et ne se relancent pas. */
    var locked by mutableStateOf<Set<Int>>(emptySet())
        private set

    /** Vrai quand les six dés ont rapporté et viennent d'être tous relancés. */
    var hotDice by mutableStateOf(false)
        private set

    /** Index des dés choisis sur le lancer en cours. */
    val selected: Set<Int> get() = turn.held - locked

    /** Les points des dés choisis ; null si aucun dé n'est choisi ou si l'un d'eux ne rapporte rien. */
    val selectionPoints: Int?
        get() = if (turn.hasRolled) DixMilleScoring.score(selected.sorted().map { turn.values[it] }) else null

    /** Les points du tour si le joueur garde maintenant : ceux déjà mis de côté et ceux des dés choisis. */
    val pending: Int get() = turnPoints + (selectionPoints ?: 0)

    /** Vrai quand aucun dé encore en jeu ne rapporte : le tour est perdu. */
    val farkle: Boolean
        get() = turn.hasRolled && !DixMilleScoring.canScore(turn.values.filterIndexed { index, _ -> index !in locked })

    /** Vrai si le joueur peut lancer les dés : au début du tour, ou une fois ses dés choisis (s'il n'a pas fait de Farkle). */
    val canRoll: Boolean get() = !farkle && (!turn.hasRolled || selectionPoints != null)

    /** Vrai si le joueur peut garder ses points ; [opened] : il a déjà marqué des points dans la partie. */
    fun canKeep(opened: Boolean): Boolean = !farkle && selectionPoints != null && (opened || pending >= DixMilleScoring.OPENING)

    /** Choisit le dé [index] parmi ceux du lancer (ou le remet en jeu s'il était choisi) ; sans effet sur un dé déjà mis de côté. */
    fun toggle(index: Int) {
        if (!turn.hasRolled || farkle || index in locked || index !in 0 until DixMilleScoring.DICE) return
        turn.toggleHold(index)
    }

    /**
     * Lance les dés : tous au début du tour ; ensuite les dés choisis sont mis de côté et les autres sont relancés
     * (les six si tous ont rapporté).
     */
    fun roll() {
        if (!canRoll) return
        if (turn.hasRolled) {
            turnPoints += selectionPoints ?: 0
            locked = turn.held
            hotDice = locked.size == DixMilleScoring.DICE
            if (hotDice) {
                // Les six dés ont rapporté : on les relance tous.
                turn.held.toList().forEach { turn.toggleHold(it) }
                locked = emptySet()
            }
        }
        turn.roll()
    }

    /** Inscrit [points] dans la prochaine case libre de [player] au tableau des scores. */
    private fun write(viewModel: ScoreViewModel, player: Int, points: Int) {
        val row = viewModel.scores.indexOfFirst { it.getOrNull(player)?.baseValue == null }
        if (row >= 0) viewModel.setCell(row, player, points, false)
    }

    /**
     * Le joueur garde les points du tour ([pending]) : ils sont inscrits au tableau et la main passe au joueur suivant.
     * Renvoie les points gardés, ou -1 si le joueur ne peut pas garder (voir [canKeep]).
     */
    fun keep(viewModel: ScoreViewModel, opened: Boolean): Int {
        if (!canKeep(opened)) return -1
        val points = pending
        write(viewModel, current, points)
        nextPlayer()
        return points
    }

    /** Farkle : le tour est perdu, 0 est inscrit au tableau et la main passe au joueur suivant. */
    fun pass(viewModel: ScoreViewModel) {
        if (!farkle) return
        write(viewModel, current, 0)
        nextPlayer()
    }

    private fun nextPlayer() {
        current = (current + 1) % playerCount.coerceAtLeast(1)
        resetTurn()
    }

    private fun resetTurn() {
        turn.reset()
        turnPoints = 0
        locked = emptySet()
        hotDice = false
    }

    // ---------- Sauvegarde et partage ----------

    private fun resetAll() {
        current = first
        resetTurn()
    }

    /** État sous forme de liste d'entiers, pour survivre à une rotation de l'écran et être partagé (voir [restoreFrom]). */
    fun snapshot(): List<Int> = buildList {
        add(playerCount)
        add(current)
        add(turnPoints)
        add(if (hotDice) 1 else 0)
        add(locked.sumOf { 1 shl it })
        addAll(turn.snapshot())
    }

    /** Rétablit l'état produit par [snapshot] ; une liste invalide remet la partie au départ. */
    fun restoreFrom(data: List<Int>) {
        resetAll()
        try {
            var cursor = 0
            fun next(): Int = data[cursor++]

            require(next() == playerCount)
            val newCurrent = next()
            val newPoints = next()
            val newHot = next()
            val mask = next()
            require(newCurrent in 0 until playerCount && newPoints >= 0 && newHot in 0..1 && mask in 0 until (1 shl DixMilleScoring.DICE))
            turn.restoreFrom(data.drop(cursor))
            val newLocked = (0 until DixMilleScoring.DICE).filter { mask and (1 shl it) != 0 }.toSet()
            // Les dés mis de côté sont forcément des dés gardés d'un tour déjà lancé.
            require(newLocked.isEmpty() || (turn.hasRolled && newLocked.all { it in turn.held }))

            current = newCurrent
            turnPoints = newPoints
            hotDice = newHot == 1
            locked = newLocked
        } catch (e: Exception) {
            resetAll()
        }
    }
}
