package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Identifiant du Démineur parmi les jeux prédéfinis (écran à part : voir [MinesweeperScreen]). */
internal const val MINESWEEPER_ID = "builtin_minesweeper"

/** Les trois niveaux : taille du plateau (colonnes × lignes) et nombre de mines. */
internal enum class MineLevel(val cols: Int, val rows: Int, val mines: Int) {
    EASY(9, 9, 10),
    MEDIUM(9, 14, 22),
    HARD(10, 16, 34);

    val cellCount: Int get() = cols * rows
}

/** Réglages et calculs du Démineur. */
internal object Minesweeper {

    /** Points par mine du niveau : gagner rapporte au moins ce nombre de points par mine. */
    const val POINTS_PER_MINE = 100

    /** Temps de référence par mine, en secondes : on gagne un bonus tant qu'on finit avant. */
    const val PAR_SECONDS_PER_MINE = 15

    /** Points de bonus par seconde gagnée sur le temps de référence. */
    const val BONUS_PER_SECOND = 10

    /**
     * Le score d'une partie gagnée en [seconds] secondes au niveau [level] : 100 points par mine, plus un bonus de
     * 10 points par seconde gagnée sur le temps de référence (15 secondes par mine). Une partie perdue ne marque rien.
     */
    fun scoreFor(level: MineLevel, seconds: Int): Int {
        val par = level.mines * PAR_SECONDS_PER_MINE
        return level.mines * POINTS_PER_MINE + max(0, par - seconds.coerceAtLeast(0)) * BONUS_PER_SECOND
    }

    /** L'identifiant sous lequel le record du niveau [level] est gardé (un record par niveau). */
    fun recordKey(level: MineLevel): String = "${MINESWEEPER_ID}_${level.name.lowercase()}"
}

/**
 * Une partie de Démineur : un plateau de [MineLevel.cols] × [MineLevel.rows] cases, dont [MineLevel.mines] cachent une
 * mine. Dévoiler une case montre combien de mines touchent ses huit voisines ; une case sans mine voisine dévoile
 * toutes ses voisines, de proche en proche. On pose un drapeau sur les cases qu'on croit minées. Dévoiler une mine
 * perd la partie ; dévoiler toutes les cases sans mine la gagne.
 *
 * Les mines ne sont posées qu'au premier dévoilement : la première case et ses voisines n'en ont jamais, la partie
 * ne se perd donc jamais au premier geste. Les cases sont numérotées `ligne * cols + colonne`.
 */
internal class MinesweeperGame(val level: MineLevel, private val random: Random = Random.Default) {

    val cols: Int get() = level.cols
    val rows: Int get() = level.rows

    /** Les drapeaux de chaque case (voir [MINE], [OPEN], [FLAG] et [BLAST]). */
    private val cells = IntArray(level.cellCount)

    /** Le nombre de mines voisines de chaque case ; juste dès que les mines sont posées. */
    private val counts = IntArray(level.cellCount)

    /** L'étape de la partie : [READY], [PLAYING], [WON] ou [LOST]. */
    var state = READY
        private set

    /** Temps de jeu écoulé depuis le premier dévoilement, en millisecondes. */
    var elapsedMs = 0L
        private set

    /** Nombre de drapeaux posés. */
    var flags = 0
        private set

    /** Nombre de cases sans mine déjà dévoilées. */
    var opened = 0
        private set

    val isFinished: Boolean get() = state == WON || state == LOST

    /** Temps écoulé en secondes entières. */
    val seconds: Int get() = (elapsedMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Mines moins drapeaux : peut être négatif quand on a posé trop de drapeaux. */
    val minesLeft: Int get() = level.mines - flags

    /** Le score de la partie : celui de [Minesweeper.scoreFor] si elle est gagnée, 0 sinon. */
    val score: Int get() = if (state == WON) Minesweeper.scoreFor(level, seconds) else 0

    fun index(col: Int, row: Int): Int = row * cols + col

    fun inBounds(col: Int, row: Int): Boolean = col in 0 until cols && row in 0 until rows

    fun isOpen(col: Int, row: Int): Boolean = cells[index(col, row)] and OPEN != 0

    fun isFlagged(col: Int, row: Int): Boolean = cells[index(col, row)] and FLAG != 0

    /** Vrai si la case cache une mine : à ne montrer qu'une fois la partie finie. */
    fun isMine(col: Int, row: Int): Boolean = cells[index(col, row)] and MINE != 0

    /** Vrai pour la mine sur laquelle la partie a été perdue. */
    fun isBlast(col: Int, row: Int): Boolean = cells[index(col, row)] and BLAST != 0

    /** Le nombre de mines qui touchent la case ; sans sens tant que les mines ne sont pas posées. */
    fun adjacentMines(col: Int, row: Int): Int = counts[index(col, row)]

    /** Le nombre de drapeaux posés sur les voisines de la case. */
    fun adjacentFlags(col: Int, row: Int): Int {
        var n = 0
        forNeighbors(index(col, row)) { if (cells[it] and FLAG != 0) n++ }
        return n
    }

    /** Fait avancer le chronomètre de [ms] millisecondes ; sans effet tant que la partie n'est pas en cours. */
    fun tick(ms: Long) {
        if (state == PLAYING && ms > 0) elapsedMs += ms
    }

    /** Recommence une partie du même niveau, sur un plateau vide dont les mines ne sont pas encore posées. */
    fun reset() {
        cells.fill(0)
        counts.fill(0)
        state = READY
        elapsedMs = 0L
        flags = 0
        opened = 0
    }

    /**
     * Dévoile la case : une case fermée s'ouvre ; une case déjà ouverte dont le nombre est atteint par les drapeaux
     * voisins ouvre toutes ses voisines non marquées ([chord]). Renvoie vrai si le plateau a changé.
     */
    fun reveal(col: Int, row: Int): Boolean {
        if (isFinished || !inBounds(col, row)) return false
        val i = index(col, row)
        val cell = cells[i]
        if (cell and FLAG != 0) return false
        if (cell and OPEN != 0) return chord(col, row)
        if (state == READY) {
            placeMines(avoid = i)
            state = PLAYING
        }
        openFrom(i)
        checkWin()
        return true
    }

    /** Pose ou retire le drapeau d'une case fermée. Renvoie vrai si le plateau a changé. */
    fun toggleFlag(col: Int, row: Int): Boolean {
        if (isFinished || !inBounds(col, row)) return false
        val i = index(col, row)
        val cell = cells[i]
        if (cell and OPEN != 0) return false
        if (cell and FLAG != 0) {
            cells[i] = cell and FLAG.inv()
            flags--
        } else {
            cells[i] = cell or FLAG
            flags++
        }
        return true
    }

    /**
     * Sur une case ouverte qui porte un nombre : si autant de drapeaux que ce nombre l'entourent, ouvre toutes ses
     * autres voisines d'un coup (un drapeau mal placé fait donc perdre la partie). Renvoie vrai si le plateau a changé.
     */
    fun chord(col: Int, row: Int): Boolean {
        if (isFinished || !inBounds(col, row)) return false
        val i = index(col, row)
        if (cells[i] and OPEN == 0 || counts[i] == 0) return false
        if (adjacentFlags(col, row) != counts[i]) return false
        var changed = false
        forNeighbors(i) { n ->
            if (state == PLAYING && cells[n] and (OPEN or FLAG) == 0) {
                openFrom(n)
                changed = true
            }
        }
        checkWin()
        return changed
    }

    /** Pose les mines au hasard, hors de la case [avoid] et de ses voisines, puis compte les voisines minées. */
    private fun placeMines(avoid: Int) {
        val ac = avoid % cols
        val ar = avoid / cols
        val candidates = ArrayList<Int>(cells.size)
        for (i in cells.indices) {
            if (abs(i % cols - ac) <= 1 && abs(i / cols - ar) <= 1) continue
            candidates.add(i)
        }
        candidates.shuffle(random)
        for (k in 0 until level.mines) cells[candidates[k]] = cells[candidates[k]] or MINE
        computeCounts()
    }

    private fun computeCounts() {
        for (i in cells.indices) {
            var n = 0
            forNeighbors(i) { if (cells[it] and MINE != 0) n++ }
            counts[i] = n
        }
    }

    /** Ouvre la case [start] ; si elle n'a aucune mine voisine, ouvre aussi ses voisines, de proche en proche. */
    private fun openFrom(start: Int) {
        if (cells[start] and MINE != 0) {
            cells[start] = cells[start] or BLAST
            state = LOST
            return
        }
        val stack = ArrayDeque<Int>()
        stack.add(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            if (cells[i] and (OPEN or FLAG) != 0) continue
            cells[i] = cells[i] or OPEN
            opened++
            if (counts[i] == 0) forNeighbors(i) { n -> if (cells[n] and (OPEN or FLAG) == 0) stack.add(n) }
        }
    }

    /** La partie est gagnée quand il ne reste que des mines à dévoiler : elles reçoivent alors toutes un drapeau. */
    private fun checkWin() {
        if (state != PLAYING || opened != cells.size - level.mines) return
        state = WON
        for (i in cells.indices) {
            if (cells[i] and MINE != 0 && cells[i] and FLAG == 0) cells[i] = cells[i] or FLAG
        }
        flags = level.mines
    }

    /** Appelle [action] pour chacune des (jusqu'à huit) cases qui touchent [index]. */
    private inline fun forNeighbors(index: Int, action: (Int) -> Unit) {
        val c = index % cols
        val r = index / cols
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nc = c + dc
                val nr = r + dr
                if (nc in 0 until cols && nr in 0 until rows) action(nr * cols + nc)
            }
        }
    }

    /** L'état de la partie rangé dans une liste d'entiers (niveau, étape, temps, puis chaque case) : voir [restore]. */
    fun save(): List<Int> {
        val out = ArrayList<Int>(3 + cells.size)
        out.add(level.ordinal)
        out.add(state)
        out.add(elapsedMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        for (cell in cells) out.add(cell)
        return out
    }

    /** Remet la partie dans l'état de [data] (rangé par [save] pour le même niveau) ; sans effet si la liste ne convient pas. */
    private fun load(data: List<Int>) {
        if (data.size != 3 + cells.size) return
        val savedState = data[1]
        if (savedState !in READY..LOST) return
        for (i in cells.indices) cells[i] = data[3 + i] and (MINE or OPEN or FLAG or BLAST)
        state = savedState
        elapsedMs = data[2].toLong().coerceAtLeast(0L)
        counts.fill(0)
        if (state != READY) computeCounts()
        flags = cells.count { it and FLAG != 0 }
        opened = cells.count { it and OPEN != 0 }
    }

    companion object {
        /** Les étapes de la partie. */
        const val READY = 0
        const val PLAYING = 1
        const val WON = 2
        const val LOST = 3

        // Les drapeaux d'une case.
        private const val MINE = 1
        private const val OPEN = 2
        private const val FLAG = 4
        private const val BLAST = 8

        /** Une partie remise dans l'état rangé par [save] ; une partie neuve du premier niveau si la liste ne convient pas. */
        fun restore(data: List<Int>): MinesweeperGame {
            val level = MineLevel.entries.getOrNull(data.getOrNull(0) ?: -1) ?: MineLevel.EASY
            return MinesweeperGame(level).also { it.load(data) }
        }

        /**
         * Une partie en cours dont les mines sont aux cases [mines] (numérotées `ligne * cols + colonne`), pour les tests :
         * le plateau n'attend plus le premier dévoilement pour poser ses mines.
         */
        fun withMines(level: MineLevel, mines: Collection<Int>): MinesweeperGame {
            require(mines.size == level.mines) { "il faut exactement ${level.mines} mines" }
            val game = MinesweeperGame(level)
            for (m in mines) game.cells[m] = game.cells[m] or MINE
            game.computeCounts()
            game.state = PLAYING
            return game
        }
    }
}
