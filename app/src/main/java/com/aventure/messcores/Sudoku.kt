package com.aventure.messcores

import kotlin.math.max
import kotlin.random.Random

/** Identifiant du Sudoku parmi les jeux prédéfinis (écran à part : voir [SudokuScreen]). */
internal const val SUDOKU_ID = "builtin_sudoku"

/**
 * Les niveaux : nombre de chiffres donnés au départ (moins il y en a, plus la grille est difficile), points de base d'une
 * grille réussie et temps de référence en secondes (on gagne un bonus tant qu'on finit avant).
 */
internal enum class SudokuLevel(val clues: Int, val basePoints: Int, val parSeconds: Int) {
    EASY(38, 500, 600),
    MEDIUM(32, 1000, 1080),
    HARD(26, 1500, 1800)
}

/** Réglages et calculs du Sudoku. Les cases sont numérotées `ligne * 9 + colonne`, de 0 à 80. */
internal object Sudoku {

    const val SIZE = 9
    const val CELLS = SIZE * SIZE

    /** Points de bonus par seconde gagnée sur le temps de référence du niveau. */
    const val BONUS_PER_SECOND = 2

    /** Le score d'une grille réussie en [seconds] secondes : les points du niveau, plus le bonus de rapidité. */
    fun scoreFor(level: SudokuLevel, seconds: Int): Int =
        level.basePoints + max(0, level.parSeconds - seconds.coerceAtLeast(0)) * BONUS_PER_SECOND

    /** L'identifiant sous lequel le record du niveau [level] est gardé (un record par niveau). */
    fun recordKey(level: SudokuLevel): String = "${SUDOKU_ID}_${level.name.lowercase()}"

    fun rowOf(index: Int): Int = index / SIZE

    fun colOf(index: Int): Int = index % SIZE

    /** Le bloc de 3 × 3 de la case, numéroté de 0 à 8 (ligne de blocs * 3 + colonne de blocs). */
    fun boxOf(index: Int): Int = (index / 27) * 3 + (index % SIZE) / 3

    /** Vrai si les deux cases se « voient » : même ligne, même colonne ou même bloc. */
    fun sees(a: Int, b: Int): Boolean = a != b && (rowOf(a) == rowOf(b) || colOf(a) == colOf(b) || boxOf(a) == boxOf(b))
}

/** Une grille à résoudre ([grid], 0 pour une case vide) et sa solution complète. */
internal class SudokuPuzzle(val grid: IntArray, val solution: IntArray)

/** Le générateur et le solveur de grilles. Tout est calculé dans l'appli, sans grille toute faite ni connexion. */
internal object SudokuEngine {

    /** Les chiffres 1 à 9 sous forme de bits (le bit n vaut le chiffre n). */
    private const val ALL = 0x3FE

    private val DIGITS = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)

    /**
     * Crée une grille du niveau [level] : on remplit d'abord une grille complète au hasard, puis on retire des chiffres un par
     * un, dans un ordre aléatoire, en ne gardant un retrait que si la grille garde une solution unique. On s'arrête dès que le
     * nombre de chiffres donnés du niveau est atteint (ou quand plus aucun retrait n'est possible).
     */
    fun generate(level: SudokuLevel, random: Random = Random.Default): SudokuPuzzle {
        val solution = solve(IntArray(Sudoku.CELLS), random) ?: error("une grille vide a toujours une solution")
        val grid = solution.copyOf()
        var clues = Sudoku.CELLS
        for (index in (0 until Sudoku.CELLS).shuffled(random)) {
            if (clues <= level.clues) break
            val saved = grid[index]
            grid[index] = 0
            if (countSolutions(grid, 2) == 1) clues-- else grid[index] = saved
        }
        return SudokuPuzzle(grid, solution)
    }

    /** Une solution de [grid] (chiffres au hasard si [random] est donné), ou null si la grille est impossible. */
    fun solve(grid: IntArray, random: Random? = null): IntArray? {
        val search = Search(grid, random)
        search.run(1)
        return search.firstSolution
    }

    /** Le nombre de solutions de [grid], compté jusqu'à [limit] : 0 (impossible), 1 (unique) ou plus. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int = Search(grid, null).run(limit)

    fun hasUniqueSolution(grid: IntArray): Boolean = countSolutions(grid, 2) == 1

    /** Une recherche par retour en arrière : on essaie d'abord la case qui a le moins de chiffres possibles. */
    private class Search(grid: IntArray, private val random: Random?) {
        private val cells = grid.copyOf()
        private val rows = IntArray(Sudoku.SIZE)
        private val cols = IntArray(Sudoku.SIZE)
        private val boxes = IntArray(Sudoku.SIZE)
        private var invalid = grid.size != Sudoku.CELLS
        private var limit = 1
        private var found = 0

        var firstSolution: IntArray? = null
            private set

        init {
            if (!invalid) {
                for (i in 0 until Sudoku.CELLS) {
                    val v = cells[i]
                    if (v == 0) continue
                    if (v !in 1..9) {
                        invalid = true
                        break
                    }
                    val bit = 1 shl v
                    val r = Sudoku.rowOf(i)
                    val c = Sudoku.colOf(i)
                    val b = Sudoku.boxOf(i)
                    if ((rows[r] or cols[c] or boxes[b]) and bit != 0) {
                        invalid = true
                        break
                    }
                    rows[r] = rows[r] or bit
                    cols[c] = cols[c] or bit
                    boxes[b] = boxes[b] or bit
                }
            }
        }

        fun run(limit: Int): Int {
            if (invalid) return 0
            this.limit = limit
            recurse()
            return found
        }

        /** Renvoie vrai quand la recherche peut s'arrêter (assez de solutions trouvées). */
        private fun recurse(): Boolean {
            var best = -1
            var bestMask = 0
            var bestCount = 10
            for (i in 0 until Sudoku.CELLS) {
                if (cells[i] != 0) continue
                val used = rows[Sudoku.rowOf(i)] or cols[Sudoku.colOf(i)] or boxes[Sudoku.boxOf(i)]
                val mask = ALL and used.inv()
                val count = Integer.bitCount(mask)
                if (count < bestCount) {
                    best = i
                    bestMask = mask
                    bestCount = count
                    if (count <= 1) break
                }
            }
            if (best == -1) {
                found++
                if (firstSolution == null) firstSolution = cells.copyOf()
                return found >= limit
            }
            if (bestCount == 0) return false
            val r = Sudoku.rowOf(best)
            val c = Sudoku.colOf(best)
            val b = Sudoku.boxOf(best)
            val order = if (random != null) DIGITS.toList().shuffled(random).toIntArray() else DIGITS
            for (d in order) {
                val bit = 1 shl d
                if (bestMask and bit == 0) continue
                cells[best] = d
                rows[r] = rows[r] or bit
                cols[c] = cols[c] or bit
                boxes[b] = boxes[b] or bit
                if (recurse()) return true
                cells[best] = 0
                rows[r] = rows[r] and bit.inv()
                cols[c] = cols[c] and bit.inv()
                boxes[b] = boxes[b] and bit.inv()
            }
            return false
        }
    }
}

/**
 * Une partie de Sudoku : une grille de 9 × 9 cases à remplir pour que chaque ligne, chaque colonne et chaque bloc de 3 × 3
 * contienne une fois les chiffres de 1 à 9. Les chiffres donnés ([isGiven]) ne changent pas. Dans une case libre, on pose un
 * chiffre ([enterDigit]) ou on note des possibilités ([toggleNote]) : un chiffre posé n'est pas forcément juste, une note n'est
 * qu'un pense-bête, rangée par chiffre (le bit n de [noteMask] est le chiffre n).
 */
internal class SudokuGame(val level: SudokuLevel, private val puzzle: IntArray, private val solution: IntArray) {

    private val values = puzzle.copyOf()
    private val notes = IntArray(Sudoku.CELLS)

    /** L'étape de la partie : [PLAYING] ou [WON]. */
    var state = PLAYING
        private set

    /** Temps de jeu écoulé, en millisecondes. */
    var elapsedMs = 0L
        private set

    val isFinished: Boolean get() = state == WON

    /** Temps écoulé en secondes entières. */
    val seconds: Int get() = (elapsedMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Le score de la partie : celui de [Sudoku.scoreFor] si elle est gagnée, 0 sinon. */
    val score: Int get() = if (state == WON) Sudoku.scoreFor(level, seconds) else 0

    fun isGiven(index: Int): Boolean = puzzle[index] != 0

    /** Le chiffre de la case (donné ou posé), 0 si elle est vide. */
    fun value(index: Int): Int = values[index]

    /** Les possibilités notées dans la case : le bit n est levé si le chiffre n est noté. */
    fun noteMask(index: Int): Int = notes[index]

    fun hasNote(index: Int, digit: Int): Boolean = notes[index] and (1 shl digit) != 0

    /** Vrai si le chiffre de la case est en double sur sa ligne, sa colonne ou son bloc. */
    fun hasConflict(index: Int): Boolean {
        val v = values[index]
        if (v == 0) return false
        for (j in 0 until Sudoku.CELLS) {
            if (values[j] == v && Sudoku.sees(index, j)) return true
        }
        return false
    }

    /** Fait avancer le chronomètre de [ms] millisecondes ; sans effet une fois la partie gagnée. */
    fun tick(ms: Long) {
        if (state == PLAYING && ms > 0) elapsedMs += ms
    }

    /**
     * Pose le chiffre [digit] dans la case : les notes de la case disparaissent. Poser le chiffre qui y est déjà l'efface.
     * Renvoie vrai si la grille a changé (jamais sur un chiffre donné ni une fois la partie gagnée).
     */
    fun enterDigit(index: Int, digit: Int): Boolean {
        if (isFinished || isGiven(index) || digit !in 1..9) return false
        notes[index] = 0
        values[index] = if (values[index] == digit) 0 else digit
        checkWon()
        return true
    }

    /**
     * Ajoute ou retire [digit] des possibilités notées dans la case. Si la case contenait un chiffre posé, il est effacé :
     * une case montre soit un chiffre, soit des notes. Renvoie vrai si la grille a changé.
     */
    fun toggleNote(index: Int, digit: Int): Boolean {
        if (isFinished || isGiven(index) || digit !in 1..9) return false
        if (values[index] != 0) {
            values[index] = 0
            notes[index] = 0
        }
        notes[index] = notes[index] xor (1 shl digit)
        return true
    }

    private fun checkWon() {
        if (values.contentEquals(solution)) state = WON
    }

    /** L'état de la partie sous forme de liste d'entiers (voir [restore]), pour la garder quand l'écran tourne. */
    fun save(): List<Int> = buildList {
        add(level.ordinal)
        add(state)
        add(elapsedMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        puzzle.forEach { add(it) }
        solution.forEach { add(it) }
        values.forEach { add(it) }
        notes.forEach { add(it) }
    }

    companion object {
        const val PLAYING = 0
        const val WON = 1

        private const val HEADER = 3
        private const val ALL_NOTES = 0x3FE

        /** Reconstruit une partie sauvegardée par [save] ; null si la liste est abîmée. */
        fun restore(data: List<Int>): SudokuGame? {
            val n = Sudoku.CELLS
            if (data.size != HEADER + 4 * n) return null
            val level = SudokuLevel.entries.getOrNull(data[0]) ?: return null
            val state = data[1]
            if (state != PLAYING && state != WON) return null
            val puzzle = IntArray(n) { data[HEADER + it] }
            val solution = IntArray(n) { data[HEADER + n + it] }
            val values = IntArray(n) { data[HEADER + 2 * n + it] }
            val notes = IntArray(n) { data[HEADER + 3 * n + it] }
            for (i in 0 until n) {
                if (puzzle[i] !in 0..9 || solution[i] !in 1..9 || values[i] !in 0..9) return null
                if (puzzle[i] != 0 && (puzzle[i] != solution[i] || values[i] != puzzle[i])) return null
                if (notes[i] and ALL_NOTES.inv() != 0) return null
            }
            val game = SudokuGame(level, puzzle, solution)
            values.copyInto(game.values)
            notes.copyInto(game.notes)
            game.state = state
            game.elapsedMs = data[2].coerceAtLeast(0).toLong()
            return game
        }
    }
}
