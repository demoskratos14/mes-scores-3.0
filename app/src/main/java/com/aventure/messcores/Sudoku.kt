package com.aventure.messcores

import kotlin.math.max
import kotlin.random.Random

/** Identifiant du Sudoku parmi les jeux prédéfinis (écran à part : voir [SudokuScreen]). */
internal const val SUDOKU_ID = "builtin_sudoku"

/**
 * Les niveaux. Chacun fixe le nombre de chiffres donnés au départ (moins il y en a, plus la grille est difficile : pour l'expert,
 * 17 est le plancher, on en retire tant que la grille garde une solution unique), les points de base d'une grille réussie et le
 * temps de référence en secondes (on gagne un bonus tant qu'on finit avant). Il fixe aussi les techniques de résolution que la
 * grille doit demander, de [minTechnique] à [maxTechnique] (voir [SudokuLogic]) : toute grille se résout sans jamais deviner.
 */
internal enum class SudokuLevel(
    val clues: Int,
    val basePoints: Int,
    val parSeconds: Int,
    val minTechnique: Int,
    val maxTechnique: Int
) {
    EASY(38, 500, 600, 0, SudokuLogic.HIDDEN_SINGLE),
    MEDIUM(32, 1000, 1080, 0, SudokuLogic.LOCKED_CANDIDATES),
    HARD(26, 1500, 1800, 0, SudokuLogic.PAIR),
    EXPERT(17, 2500, 2700, SudokuLogic.TRIPLE, SudokuLogic.ANY);

    /** L'écart entre la grille notée [rating] et ce que demande le niveau : 0 si elle convient. */
    fun gap(rating: SudokuRating): Int = when {
        !rating.solved -> UNSOLVED_GAP
        rating.hardest < minTechnique -> minTechnique - rating.hardest
        rating.hardest > maxTechnique -> rating.hardest - maxTechnique
        else -> 0
    }

    private companion object {
        /** Écart d'une grille qu'on ne résout pas par logique : pire que toute grille trop facile ou trop dure. */
        const val UNSOLVED_GAP = 1000
    }
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

    /** Les 27 groupes de neuf cases : les lignes (0 à 8), les colonnes (9 à 17) puis les blocs (18 à 26). */
    val UNITS: Array<IntArray> = Array(27) { u ->
        when {
            u < 9 -> IntArray(SIZE) { u * SIZE + it }
            u < 18 -> IntArray(SIZE) { it * SIZE + (u - 9) }
            else -> {
                val b = u - 18
                IntArray(SIZE) { ((b / 3) * 3 + it / 3) * SIZE + (b % 3) * 3 + it % 3 }
            }
        }
    }

    /** Pour chaque case, les 20 cases qu'elle voit (même ligne, colonne ou bloc). */
    val PEERS: Array<IntArray> = Array(CELLS) { i -> (0 until CELLS).filter { sees(i, it) }.toIntArray() }
}

/** Une grille à résoudre ([grid], 0 pour une case vide) et sa solution complète. */
internal class SudokuPuzzle(val grid: IntArray, val solution: IntArray)

/** Le générateur et le solveur de grilles. Tout est calculé dans l'appli, sans grille toute faite ni connexion. */
internal object SudokuEngine {

    /** Les chiffres 1 à 9 sous forme de bits (le bit n vaut le chiffre n). */
    private const val ALL = 0x3FE

    private val DIGITS = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9)

    /** Nombre maximal de grilles essayées pour un niveau, et temps maximal (en nanosecondes) avant de prendre la meilleure. */
    private const val MAX_ATTEMPTS = 500
    private const val TIME_BUDGET_NS = 10_000_000_000L

    /**
     * Crée une grille du niveau [level]. On en fabrique ([carve]) puis on la note avec [SudokuLogic.rate] : on garde la première
     * dont les techniques demandées conviennent au niveau (les grilles trop faciles, trop dures ou qui obligeraient à deviner sont
     * jetées). Si aucune ne convient avant la fin du temps ou des essais, on garde la plus proche, qui se résout toujours sans
     * deviner quand c'est possible.
     */
    fun generate(level: SudokuLevel, random: Random = Random.Default): SudokuPuzzle {
        val deadline = System.nanoTime() + TIME_BUDGET_NS
        var best: SudokuPuzzle? = null
        var bestGap = Int.MAX_VALUE
        for (attempt in 1..MAX_ATTEMPTS) {
            val puzzle = carve(level, random)
            val gap = level.gap(SudokuLogic.rate(puzzle.grid))
            if (gap == 0) return puzzle
            if (gap < bestGap) {
                best = puzzle
                bestGap = gap
            }
            if (System.nanoTime() > deadline) break
        }
        return best!!
    }

    /**
     * Fabrique une grille à solution unique : on remplit d'abord une grille complète au hasard, puis on retire des chiffres un par
     * un, dans un ordre aléatoire, en ne gardant un retrait que si la grille garde une solution unique. On s'arrête dès que le
     * nombre de chiffres donnés du niveau est atteint (ou quand plus aucun retrait n'est possible).
     */
    fun carve(level: SudokuLevel, random: Random = Random.Default): SudokuPuzzle {
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

/** Le résultat de [SudokuLogic.rate] : [solved] si la grille se résout sans deviner, [hardest] la technique la plus difficile utilisée (0 si aucune). */
internal class SudokuRating(val solved: Boolean, val hardest: Int, val grid: IntArray)

/**
 * Le solveur « humain » : il résout une grille comme le ferait un joueur, avec des techniques de plus en plus difficiles, en
 * n'employant la suivante que quand la précédente ne trouve plus rien. Il ne devine jamais. Le numéro de la technique la plus
 * difficile dont il a eu besoin donne la difficulté de la grille.
 */
internal object SudokuLogic {

    /** Chiffre seul possible dans une case. */
    const val NAKED_SINGLE = 1

    /** Chiffre qui ne peut aller que dans une case d'une ligne, d'une colonne ou d'un bloc. */
    const val HIDDEN_SINGLE = 2

    /** Chiffre d'un bloc limité à une ligne (ou colonne), qu'on retire du reste de la ligne ; et l'inverse. */
    const val LOCKED_CANDIDATES = 3

    /** Deux chiffres enfermés dans deux cases d'un groupe (paire nue ou cachée). */
    const val PAIR = 4

    /** Trois chiffres enfermés dans trois cases d'un groupe (triplet nu ou caché). */
    const val TRIPLE = 5

    /** Un chiffre limité aux mêmes deux colonnes dans deux lignes (ou l'inverse). */
    const val X_WING = 6

    /** Une case à deux possibilités et deux « ailes » qui la voient : le chiffre commun des ailes est retiré des cases qui voient les deux. */
    const val XY_WING = 7

    /** Comme le X-Wing, avec trois lignes et trois colonnes. */
    const val SWORDFISH = 8

    /** Aucune limite de technique. */
    const val ANY = Int.MAX_VALUE

    private const val ALL = 0x3FE

    /** Note la grille [grid] (0 pour une case vide) : la résout par logique et donne la technique la plus difficile utilisée. */
    fun rate(grid: IntArray): SudokuRating = Solver(grid).run()

    private class Solver(given: IntArray) {
        private val v = given.copyOf()

        /** Les chiffres encore possibles de chaque case vide (le bit n est le chiffre n). */
        private val cand = IntArray(Sudoku.CELLS)
        private var hardest = 0

        init {
            for (i in 0 until Sudoku.CELLS) {
                if (v[i] != 0) continue
                var mask = ALL
                for (j in Sudoku.PEERS[i]) if (given[j] in 1..9) mask = mask and (1 shl given[j]).inv()
                cand[i] = mask
            }
        }

        fun run(): SudokuRating {
            while (true) {
                if (v.all { it != 0 }) return SudokuRating(true, hardest, v)
                if (!step()) return SudokuRating(false, hardest, v)
            }
        }

        private fun step(): Boolean =
            nakedSingle() || hiddenSingle() || lockedCandidates() || subsets(2, PAIR) || subsets(3, TRIPLE) ||
                fish(2, X_WING) || xyWing() || fish(3, SWORDFISH)

        private fun note(rank: Int) {
            if (rank > hardest) hardest = rank
        }

        private fun place(index: Int, digit: Int) {
            v[index] = digit
            cand[index] = 0
            for (j in Sudoku.PEERS[index]) cand[j] = cand[j] and (1 shl digit).inv()
        }

        /** Retire les chiffres de [mask] des possibilités des [cells] qui répondent à [applies] ; vrai si quelque chose a changé. */
        private fun elim(cells: IntArray, mask: Int, applies: (Int) -> Boolean = { true }): Boolean {
            var changed = false
            for (i in cells) {
                if (cand[i] and mask != 0 && applies(i)) {
                    cand[i] = cand[i] and mask.inv()
                    changed = true
                }
            }
            return changed
        }

        /** Essaie toutes les combinaisons de [k] éléments de [items] ; s'arrête dès que [block] répond vrai. */
        private fun combos(items: IntArray, k: Int, block: (IntArray) -> Boolean): Boolean {
            val pick = IntArray(k)
            fun go(start: Int, depth: Int): Boolean {
                if (depth == k) return block(pick)
                for (idx in start until items.size) {
                    pick[depth] = items[idx]
                    if (go(idx + 1, depth + 1)) return true
                }
                return false
            }
            return go(0, 0)
        }

        private fun nakedSingle(): Boolean {
            for (i in 0 until Sudoku.CELLS) {
                if (v[i] == 0 && Integer.bitCount(cand[i]) == 1) {
                    place(i, Integer.numberOfTrailingZeros(cand[i]))
                    note(NAKED_SINGLE)
                    return true
                }
            }
            return false
        }

        private fun hiddenSingle(): Boolean {
            for (unit in Sudoku.UNITS) {
                for (d in 1..9) {
                    val bit = 1 shl d
                    var count = 0
                    var where = -1
                    for (i in unit) {
                        if (v[i] == 0 && cand[i] and bit != 0) {
                            count++
                            where = i
                        }
                    }
                    if (count == 1) {
                        place(where, d)
                        note(HIDDEN_SINGLE)
                        return true
                    }
                }
            }
            return false
        }

        private fun lockedCandidates(): Boolean {
            for (d in 1..9) {
                val bit = 1 shl d
                // Un chiffre d'un bloc limité à une ligne ou une colonne : on le retire du reste de cette ligne ou colonne.
                for (box in 0 until 9) {
                    val cells = Sudoku.UNITS[18 + box].filter { cand[it] and bit != 0 }
                    if (cells.size < 2) continue
                    val row = Sudoku.rowOf(cells[0])
                    if (cells.all { Sudoku.rowOf(it) == row } && elim(Sudoku.UNITS[row], bit) { Sudoku.boxOf(it) != box }) {
                        note(LOCKED_CANDIDATES)
                        return true
                    }
                    val col = Sudoku.colOf(cells[0])
                    if (cells.all { Sudoku.colOf(it) == col } && elim(Sudoku.UNITS[9 + col], bit) { Sudoku.boxOf(it) != box }) {
                        note(LOCKED_CANDIDATES)
                        return true
                    }
                }
                // Un chiffre d'une ligne ou d'une colonne limité à un bloc : on le retire du reste de ce bloc.
                for (u in 0 until 18) {
                    val cells = Sudoku.UNITS[u].filter { cand[it] and bit != 0 }
                    if (cells.size < 2) continue
                    val box = Sudoku.boxOf(cells[0])
                    if (cells.all { Sudoku.boxOf(it) == box } &&
                        elim(Sudoku.UNITS[18 + box], bit) { if (u < 9) Sudoku.rowOf(it) != u else Sudoku.colOf(it) != u - 9 }
                    ) {
                        note(LOCKED_CANDIDATES)
                        return true
                    }
                }
            }
            return false
        }

        /** Paires ([k] = 2) et triplets ([k] = 3), nus puis cachés, dans chaque groupe. */
        private fun subsets(k: Int, rank: Int): Boolean {
            for (unit in Sudoku.UNITS) {
                val free = unit.filter { v[it] == 0 }.toIntArray()
                if (free.size <= k) continue
                // Nu : k cases qui, à elles toutes, n'ont que k chiffres possibles : ces chiffres quittent les autres cases.
                val naked = combos(free, k) { combo ->
                    var union = 0
                    for (i in combo) union = union or cand[i]
                    Integer.bitCount(union) == k && elim(free, union) { it !in combo }
                }
                if (naked) {
                    note(rank)
                    return true
                }
                // Caché : k chiffres qui ne tiennent que dans k cases : ces cases perdent tous leurs autres chiffres.
                val digits = (1..9).filter { d -> free.any { cand[it] and (1 shl d) != 0 } }.toIntArray()
                val hidden = combos(digits, k) { combo ->
                    var mask = 0
                    for (d in combo) mask = mask or (1 shl d)
                    val cells = free.filter { cand[it] and mask != 0 }
                    var changed = false
                    if (cells.size == k) {
                        for (i in cells) {
                            if (cand[i] and mask.inv() != 0) {
                                cand[i] = cand[i] and mask
                                changed = true
                            }
                        }
                    }
                    changed
                }
                if (hidden) {
                    note(rank)
                    return true
                }
            }
            return false
        }

        /** X-Wing ([k] = 2) et Swordfish ([k] = 3), sur les lignes puis sur les colonnes. */
        private fun fish(k: Int, rank: Int): Boolean {
            for (d in 1..9) {
                val bit = 1 shl d
                for (rows in booleanArrayOf(true, false)) {
                    // Pour chaque ligne (ou colonne), les cases où le chiffre est encore possible.
                    val lines = Array(9) { l ->
                        Sudoku.UNITS[if (rows) l else 9 + l].filter { cand[it] and bit != 0 }.toIntArray()
                    }
                    val usable = (0 until 9).filter { lines[it].size in 2..k }.toIntArray()
                    val found = combos(usable, k) { combo ->
                        var positions = 0
                        for (l in combo) for (i in lines[l]) positions = positions or (1 shl (if (rows) Sudoku.colOf(i) else Sudoku.rowOf(i)))
                        var changed = false
                        if (Integer.bitCount(positions) == k) {
                            // Le chiffre est dans k lignes et k colonnes seulement : il quitte le reste de ces colonnes.
                            for (p in 0 until 9) {
                                if (positions and (1 shl p) == 0) continue
                                val cross = Sudoku.UNITS[if (rows) 9 + p else p]
                                if (elim(cross, bit) { (if (rows) Sudoku.rowOf(it) else Sudoku.colOf(it)) !in combo }) changed = true
                            }
                        }
                        changed
                    }
                    if (found) {
                        note(rank)
                        return true
                    }
                }
            }
            return false
        }

        private fun xyWing(): Boolean {
            for (p in 0 until Sudoku.CELLS) {
                if (v[p] != 0 || Integer.bitCount(cand[p]) != 2) continue
                val x = Integer.numberOfTrailingZeros(cand[p])
                val y = 31 - Integer.numberOfLeadingZeros(cand[p])
                val wings = Sudoku.PEERS[p].filter { v[it] == 0 && Integer.bitCount(cand[it]) == 2 }
                for (w1 in wings) {
                    // Première aile : x et un troisième chiffre z (pas y).
                    if (cand[w1] and (1 shl x) == 0 || cand[w1] and (1 shl y) != 0) continue
                    val z = cand[w1] and (1 shl x).inv()
                    for (w2 in wings) {
                        // Seconde aile : y et z.
                        if (w2 == w1 || cand[w2] != ((1 shl y) or z)) continue
                        val targets = Sudoku.PEERS[w1].filter { it != p && v[it] == 0 && Sudoku.sees(w2, it) }.toIntArray()
                        if (elim(targets, z)) {
                            note(XY_WING)
                            return true
                        }
                    }
                }
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
