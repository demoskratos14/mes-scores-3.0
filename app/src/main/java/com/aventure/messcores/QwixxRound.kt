package com.aventure.messcores

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.random.Random

/**
 * Où en est le tour du Qwixx : le joueur actif lance les dés ([ROLL]), tous les joueurs choisissent s'ils cochent la somme
 * des dés blancs ([WHITE]), puis le joueur actif choisit s'il coche un dé blanc + un dé de couleur ([COLOR]) ; le tour
 * peut alors se terminer ([END]). La partie finie reste affichée ([OVER]).
 */
internal enum class QwixxStep { ROLL, WHITE, COLOR, END, OVER }

/** Une croix possible : le nombre [number] dans la rangée [row] (0 rouge, 1 jaune, 2 vert, 3 bleu). */
internal data class QwixxMove(val row: Int, val number: Int)

/** Règles de coche et de points du Qwixx : voir la fiche de règles du jeu. */
internal object QwixxScoring {

    /** Rouge, jaune, vert, bleu. */
    const val ROWS = 4

    /** Nombres d'une rangée : de 2 à 12. */
    const val MIN_NUMBER = 2
    const val MAX_NUMBER = 12

    /** Croix (sans le cadenas) qu'il faut déjà avoir pour cocher le dernier nombre d'une rangée et la fermer. */
    const val CROSSES_TO_LOCK = 5

    /** Un joueur qui prend sa quatrième pénalité met fin à la partie. */
    const val MAX_PENALTIES = 4

    const val PENALTY_POINTS = 5

    /** La partie s'arrête quand deux rangées sont fermées. */
    const val LOCKS_TO_END = 2

    /** Choix d'un joueur : pas encore décidé, ou passe (les valeurs 0 à 3 sont la rangée cochée). */
    const val NO_CHOICE = -1
    const val PASS = ROWS

    // Une rangée est un masque de bits : le bit (n − 2) est la croix du nombre n, le bit 11 est le cadenas.
    private const val LOCK_BIT = 11
    private const val NUMBERS_MASK = (1 shl 11) - 1

    /** Les rangées rouge et jaune vont de 2 à 12, les rangées verte et bleue de 12 à 2. */
    fun isAscending(row: Int): Boolean = row < 2

    /** Le dernier nombre de la rangée : celui qui la ferme. */
    fun lastNumber(row: Int): Int = if (isAscending(row)) MAX_NUMBER else MIN_NUMBER

    /** Les nombres de la rangée dans l'ordre où on les coche. */
    fun numbersOf(row: Int): List<Int> = if (isAscending(row)) (MIN_NUMBER..MAX_NUMBER).toList() else (MAX_NUMBER downTo MIN_NUMBER).toList()

    fun isMarked(mask: Int, number: Int): Boolean = number in MIN_NUMBER..MAX_NUMBER && (mask and (1 shl (number - MIN_NUMBER))) != 0

    fun isLocked(mask: Int): Boolean = (mask and (1 shl LOCK_BIT)) != 0

    /** Croix de la rangée, le cadenas compris. */
    fun crosses(mask: Int): Int = Integer.bitCount(mask)

    /** Points d'une rangée : 1 + 2 + … + n pour n croix. */
    fun points(crosses: Int): Int = crosses.coerceIn(0, MAX_NUMBER).let { it * (it + 1) / 2 }

    /**
     * Vrai si l'on peut cocher [number] dans la rangée [row] de masque [mask] : toujours à droite des croix déjà faites
     * (à gauche pour les rangées vertes et bleues), et le dernier nombre seulement avec [CROSSES_TO_LOCK] croix.
     * La rangée fermée pour tous est à vérifier à part.
     */
    fun canMark(mask: Int, row: Int, number: Int): Boolean {
        if (number !in MIN_NUMBER..MAX_NUMBER || isLocked(mask)) return false
        val numbers = mask and NUMBERS_MASK
        if (numbers != 0) {
            if (isAscending(row)) {
                val highest = MIN_NUMBER + (31 - Integer.numberOfLeadingZeros(numbers))
                if (number <= highest) return false
            } else {
                val lowest = MIN_NUMBER + Integer.numberOfTrailingZeros(numbers)
                if (number >= lowest) return false
            }
        }
        return number != lastNumber(row) || Integer.bitCount(numbers) >= CROSSES_TO_LOCK
    }

    /** Le masque après avoir coché [number] ; le dernier nombre ajoute le cadenas. */
    fun mark(mask: Int, row: Int, number: Int): Int {
        val marked = mask or (1 shl (number - MIN_NUMBER))
        return if (number == lastNumber(row)) marked or (1 shl LOCK_BIT) else marked
    }

    /** Le masque après avoir défait la croix [number] (et le cadenas qu'elle avait posé). */
    fun unmark(mask: Int, row: Int, number: Int): Int {
        val cleared = mask and (1 shl (number - MIN_NUMBER)).inv()
        return if (number == lastNumber(row)) cleared and (1 shl LOCK_BIT).inv() else cleared
    }
}

/**
 * Une partie de Qwixx jouée tour par tour, de la même façon pour tous les téléphones.
 *
 * - Le joueur actif lance les 6 dés (2 blancs, puis un rouge, un jaune, un vert, un bleu).
 * - Tous les joueurs peuvent cocher la somme des deux dés blancs dans la rangée de leur choix, ou passer
 *   ([markWhite], [passWhite]) ; un joueur qui n'a aucune croix possible passe tout seul.
 * - Puis le joueur actif peut cocher un dé blanc + le dé d'une couleur, dans cette couleur ([markColor], [passColor]).
 * - Le joueur actif qui n'a rien coché prend une pénalité ([endTurn]). La partie se termine quand deux rangées sont fermées
 *   par un cadenas, ou quand un joueur a quatre pénalités : les points de chacun sont alors inscrits dans le tableau des scores
 *   ([writeTo]).
 *
 * Une rangée fermée par un cadenas pendant un tour reste ouverte aux autres joueurs jusqu'à la fin de ce tour : ceux qui cochent le
 * même nombre au même moment la ferment aussi.
 *
 * [rollDie] tire un dé de 1 à 6 (remplaçable dans les tests).
 */
internal class QwixxRound(
    val playerCount: Int,
    rollDie: () -> Int = { Random.nextInt(1, 7) }
) {
    /** Les six dés du tour : sans en garder, un seul lancer. */
    val turn = DiceTurn(DiceSetups.forGame("builtin_qwixx") ?: DiceSetup(diceCount = 6, allowHold = false), rollDie)

    /** Le joueur qui lance les dés. */
    var current by mutableIntStateOf(0)
        private set

    /** Vrai quand la partie est terminée. */
    var over by mutableStateOf(false)
        private set

    /** Rangées fermées avant ce tour (bit r pour la rangée r). */
    var closedMask by mutableIntStateOf(0)
        private set

    /** Les quatre rangées de chaque joueur, en masques de bits (voir [QwixxScoring]). */
    var boards by mutableStateOf(List(playerCount) { List(QwixxScoring.ROWS) { 0 } })
        private set

    var penalties by mutableStateOf(List(playerCount) { 0 })
        private set

    /** Choix de chaque joueur sur la somme des dés blancs : [QwixxScoring.NO_CHOICE], [QwixxScoring.PASS] ou la rangée cochée. */
    var whiteChoices by mutableStateOf(List(playerCount) { QwixxScoring.NO_CHOICE })
        private set

    /** Choix du joueur actif avec un dé de couleur : comme [whiteChoices]. */
    var colorChoice by mutableIntStateOf(QwixxScoring.NO_CHOICE)
        private set

    /** Nombre coché avec un dé de couleur (quand [colorChoice] est une rangée). */
    var colorNumber by mutableIntStateOf(0)
        private set

    /** Nombre de tours terminés : un tour plus avancé l'emporte en entier quand deux états se rencontrent (voir [mergeFrom]). */
    var turnCount by mutableIntStateOf(0)
        private set

    /**
     * Pour chaque joueur, combien de fois ce qui lui appartient a changé (ses croix, son choix sur les dés blancs, et
     * pour le joueur actif son choix avec un dé de couleur) : à tour égal, c'est la version la plus haute qui l'emporte.
     */
    private var versions by mutableStateOf(List(playerCount) { 0 })

    /** Somme des deux dés blancs ; 0 avant le lancer. */
    val whiteSum: Int get() = QwixxDice.whiteSum(turn.values)

    private fun has(mask: Int, row: Int): Boolean = (mask and (1 shl row)) != 0

    private fun <T> List<T>.with(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }

    /** Ce qui appartient à [player] vient de changer : sa version monte. */
    private fun touch(player: Int) {
        versions = versions.with(player, versions[player] + 1)
    }

    /** Rangées dont un joueur a posé le cadenas (y compris pendant ce tour). */
    private fun lockedRows(): Int {
        var mask = 0
        for (row in 0 until QwixxScoring.ROWS) {
            if (boards.any { QwixxScoring.isLocked(it[row]) }) mask = mask or (1 shl row)
        }
        return mask
    }

    /** Vrai si la rangée est fermée pour tous : le cadenas est posé depuis un tour précédent. */
    fun isRowClosed(row: Int): Boolean = has(closedMask, row)

    // ---------- Ce que chacun peut faire ----------

    /** Rangées où [player] peut cocher la somme des dés blancs. */
    fun whiteOptions(player: Int): List<Int> {
        if (!turn.hasRolled || player !in 0 until playerCount) return emptyList()
        val sum = whiteSum
        return (0 until QwixxScoring.ROWS).filter { row -> !has(closedMask, row) && QwixxScoring.canMark(boards[player][row], row, sum) }
    }

    /** Croix que le joueur actif peut faire avec un dé blanc et un dé de couleur. */
    fun colorOptions(): List<QwixxMove> {
        if (!turn.hasRolled) return emptyList()
        val closed = closedMask or lockedRows()
        val dice = turn.values
        val result = mutableListOf<QwixxMove>()
        for (row in 0 until QwixxScoring.ROWS) {
            if (has(closed, row)) continue
            val color = dice.getOrElse(2 + row) { 0 }
            for (white in 0..1) {
                val number = dice.getOrElse(white) { 0 } + color
                val move = QwixxMove(row, number)
                if (QwixxScoring.canMark(boards[current][row], row, number) && move !in result) result += move
            }
        }
        return result
    }

    private fun whiteDone(player: Int): Boolean = whiteChoices[player] != QwixxScoring.NO_CHOICE || whiteOptions(player).isEmpty()

    private fun colorDone(): Boolean = colorChoice != QwixxScoring.NO_CHOICE || colorOptions().isEmpty()

    val step: QwixxStep
        get() = when {
            over -> QwixxStep.OVER
            !turn.hasRolled -> QwixxStep.ROLL
            (0 until playerCount).any { !whiteDone(it) } -> QwixxStep.WHITE
            !colorDone() -> QwixxStep.COLOR
            else -> QwixxStep.END
        }

    /** Vrai si le joueur actif n'a rien coché à ce tour : il prendra une pénalité en passant la main. */
    val penaltyPending: Boolean
        get() = step == QwixxStep.END &&
            whiteChoices[current] !in 0 until QwixxScoring.ROWS && colorChoice !in 0 until QwixxScoring.ROWS

    // ---------- Actions ----------

    /** Le joueur actif lance les dés. */
    fun roll() {
        if (step != QwixxStep.ROLL || !turn.canRoll) return
        turn.roll()
    }

    /** [player] coche la somme des dés blancs dans la rangée [row]. */
    fun markWhite(player: Int, row: Int) {
        if (step != QwixxStep.WHITE || player !in 0 until playerCount) return
        if (whiteChoices[player] != QwixxScoring.NO_CHOICE || row !in whiteOptions(player)) return
        boards = boards.with(player, boards[player].with(row, QwixxScoring.mark(boards[player][row], row, whiteSum)))
        whiteChoices = whiteChoices.with(player, row)
        touch(player)
    }

    /** [player] ne coche rien avec les dés blancs. */
    fun passWhite(player: Int) {
        if (step != QwixxStep.WHITE || player !in 0 until playerCount) return
        if (whiteChoices[player] != QwixxScoring.NO_CHOICE) return
        whiteChoices = whiteChoices.with(player, QwixxScoring.PASS)
        touch(player)
    }

    /** Défait le choix de [player] sur les dés blancs, tant que le joueur actif n'a pas fait le sien avec un dé de couleur. */
    fun cancelWhite(player: Int) {
        if (over || !turn.hasRolled || colorChoice != QwixxScoring.NO_CHOICE || player !in 0 until playerCount) return
        val choice = whiteChoices[player]
        if (choice == QwixxScoring.NO_CHOICE) return
        if (choice in 0 until QwixxScoring.ROWS) {
            boards = boards.with(player, boards[player].with(choice, QwixxScoring.unmark(boards[player][choice], choice, whiteSum)))
        }
        whiteChoices = whiteChoices.with(player, QwixxScoring.NO_CHOICE)
        touch(player)
    }

    /** Le joueur actif coche [number] dans la rangée [row] avec un dé blanc et le dé de cette couleur. */
    fun markColor(row: Int, number: Int) {
        if (step != QwixxStep.COLOR || QwixxMove(row, number) !in colorOptions()) return
        boards = boards.with(current, boards[current].with(row, QwixxScoring.mark(boards[current][row], row, number)))
        colorChoice = row
        colorNumber = number
        touch(current)
    }

    /** Le joueur actif ne coche rien avec un dé de couleur. */
    fun passColor() {
        if (step != QwixxStep.COLOR) return
        colorChoice = QwixxScoring.PASS
        touch(current)
    }

    /** Défait le choix du joueur actif avec un dé de couleur. */
    fun cancelColor() {
        if (over || colorChoice == QwixxScoring.NO_CHOICE) return
        if (colorChoice in 0 until QwixxScoring.ROWS) {
            boards = boards.with(current, boards[current].with(colorChoice, QwixxScoring.unmark(boards[current][colorChoice], colorChoice, colorNumber)))
        }
        colorChoice = QwixxScoring.NO_CHOICE
        colorNumber = 0
        touch(current)
    }

    /**
     * Fin du tour : pénalité du joueur actif s'il n'a rien coché, rangées fermées, puis joueur suivant. Renvoie vrai si ce tour
     * met fin à la partie (deux rangées fermées, ou quatre pénalités).
     */
    fun endTurn(): Boolean {
        if (step != QwixxStep.END) return false
        if (penaltyPending) penalties = penalties.with(current, penalties[current] + 1)
        touch(current)
        turnCount += 1
        closedMask = closedMask or lockedRows()
        val ended = Integer.bitCount(closedMask) >= QwixxScoring.LOCKS_TO_END || penalties.any { it >= QwixxScoring.MAX_PENALTIES }
        if (ended) {
            over = true
        } else {
            current = (current + 1) % playerCount.coerceAtLeast(1)
            newTurn()
        }
        return ended
    }

    private fun newTurn() {
        turn.reset()
        whiteChoices = List(playerCount) { QwixxScoring.NO_CHOICE }
        colorChoice = QwixxScoring.NO_CHOICE
        colorNumber = 0
    }

    // ---------- Points ----------

    /** Croix de [player] dans la rangée [row], cadenas compris. */
    fun crossesOf(player: Int, row: Int): Int = QwixxScoring.crosses(boards[player][row])

    /** Points de [player] : ses quatre rangées, moins 5 points par pénalité. */
    fun scoreOf(player: Int): Int =
        (0 until QwixxScoring.ROWS).sumOf { QwixxScoring.points(crossesOf(player, it)) } - QwixxScoring.PENALTY_POINTS * penalties[player]

    /** Vrai si une croix ou une pénalité est déjà marquée (il y a quelque chose à perdre en quittant). */
    val hasMarks: Boolean get() = boards.any { rows -> rows.any { it != 0 } } || penalties.any { it > 0 }

    /** Inscrit dans le tableau des scores les points de chaque couleur et les pénalités de chaque joueur. */
    fun writeTo(viewModel: ScoreViewModel) {
        for (player in 0 until playerCount) {
            for (row in 0 until QwixxScoring.ROWS) {
                viewModel.setCell(row, player, QwixxScoring.points(crossesOf(player, row)), false)
            }
            viewModel.setCell(QwixxScoring.ROWS, player, -QwixxScoring.PENALTY_POINTS * penalties[player], false)
        }
    }

    // ---------- Sauvegarde et partage ----------

    private fun resetAll() {
        current = 0
        over = false
        closedMask = 0
        boards = List(playerCount) { List(QwixxScoring.ROWS) { 0 } }
        penalties = List(playerCount) { 0 }
        turnCount = 0
        versions = List(playerCount) { 0 }
        newTurn()
    }

    /** État sous forme de liste d'entiers, pour survivre à une rotation de l'écran et être partagé (voir [restoreFrom]). */
    fun snapshot(): List<Int> = buildList {
        add(if (over) 1 else 0)
        add(playerCount)
        add(current)
        add(closedMask)
        add(colorChoice)
        add(colorNumber)
        for (player in 0 until playerCount) {
            add(penalties[player])
            addAll(boards[player])
            add(whiteChoices[player])
        }
        add(turnCount)
        addAll(versions)
        addAll(turn.snapshot())
    }

    /** Un état lu depuis une liste produite par [snapshot], sans encore l'appliquer. */
    private class Parsed(
        val over: Boolean,
        val current: Int,
        val closedMask: Int,
        val colorChoice: Int,
        val colorNumber: Int,
        val penalties: List<Int>,
        val boards: List<List<Int>>,
        val whiteChoices: List<Int>,
        val turnCount: Int,
        val versions: List<Int>,
        val dice: List<Int>
    ) {
        /** Vrai si les dés ont été lancés (la liste des dés commence par le nombre de lancers puis le nombre de dés). */
        val diceRolled: Boolean get() = (dice.getOrNull(1) ?: 0) > 0

        /** Ce qui appartient à [player] : ses rangées, ses pénalités, son choix, et pour le joueur actif son choix de couleur. */
        fun registerOf(player: Int): List<Int> =
            boards[player] + penalties[player] + whiteChoices[player] +
                if (player == current) listOf(colorChoice, colorNumber) else emptyList()
    }

    /** Lit [data] ; null si la liste n'est pas un état valide pour cette partie. */
    private fun parse(data: List<Int>): Parsed? = try {
        var cursor = 0
        fun next(): Int = data[cursor++]

        val newOver = next() == 1
        require(next() == playerCount)
        val newCurrent = next()
        val newClosed = next()
        val newColorChoice = next()
        val newColorNumber = next()
        require(newCurrent in 0 until playerCount.coerceAtLeast(1))
        require(newClosed in 0 until (1 shl QwixxScoring.ROWS))
        require(newColorChoice in QwixxScoring.NO_CHOICE..QwixxScoring.PASS)
        require(newColorNumber in 0..QwixxScoring.MAX_NUMBER)
        val newPenalties = mutableListOf<Int>()
        val newBoards = mutableListOf<List<Int>>()
        val newWhite = mutableListOf<Int>()
        repeat(playerCount) {
            val penalty = next()
            require(penalty in 0..QwixxScoring.MAX_PENALTIES)
            newPenalties += penalty
            newBoards += List(QwixxScoring.ROWS) {
                val mask = next()
                require(mask in 0 until (1 shl 12))
                mask
            }
            val choice = next()
            require(choice in QwixxScoring.NO_CHOICE..QwixxScoring.PASS)
            newWhite += choice
        }
        val newTurnCount = next()
        require(newTurnCount >= 0)
        val newVersions = List(playerCount) { next().also { v -> require(v >= 0) } }
        Parsed(
            over = newOver,
            current = newCurrent,
            closedMask = newClosed,
            colorChoice = newColorChoice,
            colorNumber = newColorNumber,
            penalties = newPenalties,
            boards = newBoards,
            whiteChoices = newWhite,
            turnCount = newTurnCount,
            versions = newVersions,
            dice = data.drop(cursor)
        )
    } catch (e: Exception) {
        null
    }

    private fun applyState(state: Parsed) {
        over = state.over
        current = state.current
        closedMask = state.closedMask
        colorChoice = state.colorChoice
        colorNumber = state.colorNumber
        penalties = state.penalties
        boards = state.boards
        whiteChoices = state.whiteChoices
        turnCount = state.turnCount
        versions = state.versions
        turn.restoreFrom(state.dice)
    }

    /** Rétablit l'état produit par [snapshot] ; une liste invalide remet la partie au départ. */
    fun restoreFrom(data: List<Int>) {
        resetAll()
        parse(data)?.let { applyState(it) }
    }

    /**
     * Fusionne l'état [data] reçu d'un autre téléphone avec celui-ci, au lieu de le remplacer : deux téléphones qui
     * valident au même instant gardent chacun leur part.
     *
     * - Un tour plus avancé l'emporte en entier (tout ce qu'il contient a été vu par le téléphone qui l'a terminé).
     * - À tour égal, des dés lancés l'emportent sur des dés pas encore lancés ; deux lancers différents au même instant
     *   se départagent de la même façon sur tous les téléphones.
     * - Sinon, joueur par joueur : la version la plus haute de ce qui appartient au joueur l'emporte, donc les croix de
     *   l'un et les croix de l'autre sont toutes les deux gardées.
     *
     * La fusion donne le même résultat quel que soit l'ordre des messages : tous les téléphones finissent avec le même état.
     */
    fun mergeFrom(data: List<Int>) {
        val remote = parse(data) ?: return
        val local = parse(snapshot()) ?: return
        applyState(join(local, remote))
    }

    private fun join(a: Parsed, b: Parsed): Parsed {
        if (a.turnCount != b.turnCount) return if (a.turnCount > b.turnCount) a else b
        if (a.diceRolled != b.diceRolled) return if (a.diceRolled) a else b
        val byDice = compareIntLists(a.dice, b.dice)
        if (byDice != 0) return if (byDice > 0) a else b

        val mergedPenalties = a.penalties.toMutableList()
        val mergedBoards = a.boards.toMutableList()
        val mergedWhite = a.whiteChoices.toMutableList()
        val mergedVersions = a.versions.toMutableList()
        val mergedCurrent = maxOf(a.current, b.current)
        var mergedColorChoice = if (a.current == mergedCurrent) a.colorChoice else b.colorChoice
        var mergedColorNumber = if (a.current == mergedCurrent) a.colorNumber else b.colorNumber
        for (player in 0 until playerCount) {
            val byVersion = b.versions[player].compareTo(a.versions[player])
            val bWins = if (byVersion != 0) byVersion > 0 else compareIntLists(b.registerOf(player), a.registerOf(player)) > 0
            if (bWins) {
                mergedPenalties[player] = b.penalties[player]
                mergedBoards[player] = b.boards[player]
                mergedWhite[player] = b.whiteChoices[player]
                if (player == mergedCurrent) {
                    mergedColorChoice = b.colorChoice
                    mergedColorNumber = b.colorNumber
                }
            }
            mergedVersions[player] = maxOf(a.versions[player], b.versions[player])
        }
        return Parsed(
            over = a.over || b.over,
            current = mergedCurrent,
            closedMask = a.closedMask or b.closedMask,
            colorChoice = mergedColorChoice,
            colorNumber = mergedColorNumber,
            penalties = mergedPenalties,
            boards = mergedBoards,
            whiteChoices = mergedWhite,
            turnCount = a.turnCount,
            versions = mergedVersions,
            dice = a.dice
        )
    }
}

/** Compare deux listes d'entiers terme à terme, puis par taille : le même ordre sur tous les téléphones sert à départager deux états. */
internal fun compareIntLists(a: List<Int>, b: List<Int>): Int {
    for (i in 0 until minOf(a.size, b.size)) {
        val c = a[i].compareTo(b[i])
        if (c != 0) return c
    }
    return a.size.compareTo(b.size)
}
