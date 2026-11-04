package com.aventure.messcores

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/** Identifiant du Tetris parmi les jeux prédéfinis (écran à part : voir [TetrisScreen]). */
internal const val TETRIS_ID = "builtin_tetris"

/**
 * Les sept pièces. Chacune est décrite par ses quatre cases (colonne, ligne) dans une boîte carrée de [size] cases de
 * côté, à la rotation 0 ; les trois autres rotations s'en déduisent d'un quart de tour dans le sens horaire.
 * Le numéro d'une pièce ([ordinal]) sert d'identifiant partout ailleurs (couleur, case posée : numéro + 1).
 */
internal enum class Tetromino(private val base: List<Pair<Int, Int>>, val size: Int) {
    I(listOf(0 to 1, 1 to 1, 2 to 1, 3 to 1), 4),
    O(listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1), 2),
    T(listOf(1 to 0, 0 to 1, 1 to 1, 2 to 1), 3),
    S(listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1), 3),
    Z(listOf(0 to 0, 1 to 0, 1 to 1, 2 to 1), 3),
    J(listOf(0 to 0, 0 to 1, 1 to 1, 2 to 1), 3),
    L(listOf(2 to 0, 0 to 1, 1 to 1, 2 to 1), 3);

    private val rotations: List<List<Pair<Int, Int>>> =
        (1..3).runningFold(base) { cells, _ -> cells.map { (x, y) -> (size - 1 - y) to x } }

    /** Les quatre cases (colonne, ligne) de la pièce dans sa boîte à la rotation [rotation] (0 à 3, sens horaire). */
    fun cells(rotation: Int): List<Pair<Int, Int>> = rotations[rotation and 3]
}

/** Réglages et calculs du Tetris. */
internal object Tetris {

    const val COLS = 10
    const val ROWS = 20

    /** Nombre de pièces à venir qu'on voit à côté du plateau. */
    const val NEXT_COUNT = 3

    /** Nombre de lignes à faire pour monter d'un niveau. */
    const val LINES_PER_LEVEL = 10

    const val MAX_LEVEL = 20

    /** Temps de chute d'une case au niveau 1, et facteur appliqué à chaque niveau (les pièces tombent un peu plus vite). */
    private const val START_GRAVITY_MS = 800.0
    private const val GRAVITY_FACTOR = 0.86

    /** Temps de chute plancher : au-delà, on ne suivrait plus du doigt. */
    const val MIN_GRAVITY_MS = 70L

    /** Temps laissé à une pièce posée pour glisser encore avant de se figer. */
    const val LOCK_DELAY_MS = 500L

    /** Nombre de fois qu'un déplacement ou une rotation peut relancer ce délai pour une même pièce : sans cela, on la ferait glisser sans fin. */
    const val MAX_LOCK_RESETS = 15

    /** Le niveau atteint après [lines] lignes faites. */
    fun levelFor(lines: Int): Int = min(MAX_LEVEL, lines.coerceAtLeast(0) / LINES_PER_LEVEL + 1)

    /** Temps de chute d'une case au niveau [level], en millisecondes. */
    fun gravityMs(level: Int): Long =
        max(MIN_GRAVITY_MS, (START_GRAVITY_MS * GRAVITY_FACTOR.pow(level.coerceAtLeast(1) - 1)).toLong())

    /** Points d'une pose qui efface [cleared] lignes d'un coup au niveau [level]. */
    fun lineClearPoints(cleared: Int, level: Int): Int {
        val base = when (cleared) {
            1 -> 100
            2 -> 300
            3 -> 500
            4 -> 800
            else -> 0
        }
        return base * level
    }

    /** Un « sac » : les sept pièces dans un ordre tiré au hasard. On les distribue par sac pour qu'aucune ne se fasse trop attendre. */
    fun newBag(random: Random = Random.Default): List<Int> = (0 until Tetromino.entries.size).shuffled(random)
}

/**
 * Une partie de Tetris : un plateau de [Tetris.COLS] × [Tetris.ROWS] cases, la pièce qui tombe, la réserve et les pièces
 * à venir. La classe est modifiable (une partie qui bouge à chaque image) : l'écran redessine après chaque appel.
 *
 * Une case du plateau vaut 0 si elle est vide, sinon le numéro de la pièce qui l'a remplie, plus 1. La ligne 0 est en haut.
 * Les pièces sont repérées par leur numéro dans [Tetromino] ; [NONE] veut dire « pas de pièce ».
 */
internal class TetrisGame(private val random: Random = Random.Default) {

    private val board = IntArray(Tetris.COLS * Tetris.ROWS)
    private val queue = ArrayList<Int>()

    /** La pièce qui tombe, sa rotation (0 à 3) et la position de sa boîte (colonne, ligne du coin en haut à gauche). */
    var current = 0
        private set
    var rotation = 0
        private set
    var x = 0
        private set
    var y = 0
        private set

    /** La pièce mise de côté, ou [NONE] ; et vrai si on l'a déjà utilisée pour la pièce en cours. */
    var held = NONE
        private set
    var holdUsed = false
        private set

    var score = 0
        private set
    var lines = 0
        private set

    /** Vrai quand une nouvelle pièce n'a plus de place : la partie est finie. */
    var isOver = false
        private set

    /** Nombre de lignes effacées par la dernière pièce posée. */
    var lastCleared = 0
        private set

    // Temps écoulé depuis la dernière chute d'une case, depuis que la pièce est posée, et nombre de relances de ce délai.
    private var fall = 0L
    private var lockMs = 0L
    private var lockResets = 0

    val level: Int get() = Tetris.levelFor(lines)

    init {
        reset()
    }

    /** Une nouvelle partie : plateau vide, score à zéro, première pièce. */
    fun reset() {
        board.fill(0)
        queue.clear()
        score = 0
        lines = 0
        held = NONE
        holdUsed = false
        isOver = false
        lastCleared = 0
        fillQueue()
        spawn(takeNext())
    }

    /** Le contenu de la case ([col], [row]) du plateau : 0 si elle est vide, sinon le numéro de la pièce + 1. */
    fun cell(col: Int, row: Int): Int = board[row * Tetris.COLS + col]

    /** Les [count] prochaines pièces, de la plus proche à la plus lointaine. */
    fun next(count: Int = Tetris.NEXT_COUNT): List<Int> = queue.take(count)

    /** Les cases (colonne, ligne) du plateau que la pièce en cours occupe. */
    fun pieceCells(): List<Pair<Int, Int>> = absoluteCells(current, rotation, x, y)

    /** La ligne où la pièce en cours s'arrêterait si on la laissait tomber tout droit. */
    fun ghostY(): Int {
        var ghost = y
        while (fits(current, rotation, x, ghost + 1)) ghost++
        return ghost
    }

    /** Les cases (colonne, ligne) que la pièce en cours occuperait en tombant tout droit : la pièce « fantôme ». */
    fun ghostCells(): List<Pair<Int, Int>> = absoluteCells(current, rotation, x, ghostY())

    fun moveLeft(): Boolean = shift(-1)

    fun moveRight(): Boolean = shift(1)

    /**
     * Fait pivoter la pièce d'un quart de tour (dans le sens horaire, ou l'inverse si [clockwise] est faux). Si la place
     * manque, la pièce est repoussée d'une ou deux cases sur le côté, ou d'une case vers le haut, quand cela permet de
     * tourner (par exemple contre un mur). Le carré ne tourne pas. Renvoie vrai si la pièce a tourné.
     */
    fun rotate(clockwise: Boolean = true): Boolean {
        if (isOver || current == Tetromino.O.ordinal) return false
        val target = (rotation + if (clockwise) 1 else 3) and 3
        for ((dx, dy) in KICKS) {
            if (fits(current, target, x + dx, y + dy)) {
                rotation = target
                x += dx
                y += dy
                touched()
                return true
            }
        }
        return false
    }

    /** Descente rapide : la pièce descend d'une case et rapporte 1 point. Renvoie faux si elle est déjà posée. */
    fun softDrop(): Boolean {
        if (isOver || !fits(current, rotation, x, y + 1)) return false
        y++
        score += 1
        fall = 0L
        return true
    }

    /** Chute directe : la pièce tombe tout de suite jusqu'en bas (2 points par case) et se fige. */
    fun hardDrop() {
        if (isOver) return
        val distance = ghostY() - y
        y += distance
        score += 2 * distance
        lock()
    }

    /**
     * Met la pièce en cours de côté et prend à la place celle qui y était (ou la suivante si la réserve était vide).
     * Une seule fois par pièce. Renvoie vrai si l'échange a eu lieu.
     */
    fun hold(): Boolean {
        if (isOver || holdUsed) return false
        val previous = held
        held = current
        holdUsed = true
        spawn(if (previous == NONE) takeNext() else previous)
        return true
    }

    /**
     * Fait avancer le jeu de [elapsedMs] millisecondes : la pièce tombe d'une case à chaque temps de chute du niveau, et
     * une pièce posée se fige quand son délai ([Tetris.LOCK_DELAY_MS]) est écoulé.
     */
    fun tick(elapsedMs: Long) {
        if (isOver) return
        val interval = Tetris.gravityMs(level)
        fall += elapsedMs
        while (fall >= interval) {
            fall -= interval
            if (fits(current, rotation, x, y + 1)) {
                y++
            } else {
                fall = 0L
                break
            }
        }
        if (isGrounded()) {
            lockMs += elapsedMs
            if (lockMs >= Tetris.LOCK_DELAY_MS) lock()
        } else {
            lockMs = 0L
        }
    }

    /** Pose une pièce [kind] en haut du plateau. Si elle n'a pas la place, la partie est finie. */
    internal fun spawn(kind: Int) {
        current = kind
        rotation = 0
        x = (Tetris.COLS - Tetromino.entries[kind].size) / 2
        y = 0
        fall = 0L
        lockMs = 0L
        lockResets = 0
        if (!fits(current, rotation, x, y)) isOver = true
    }

    /** Remplit la case ([col], [row]) avec la pièce [kind] (pour préparer une situation de jeu dans les tests). */
    internal fun setCell(col: Int, row: Int, kind: Int) {
        board[row * Tetris.COLS + col] = kind + 1
    }

    private fun isGrounded(): Boolean = !fits(current, rotation, x, y + 1)

    private fun shift(dx: Int): Boolean {
        if (isOver || !fits(current, rotation, x + dx, y)) return false
        x += dx
        touched()
        return true
    }

    /** Une pièce posée qu'on déplace ou qu'on fait tourner repart pour un délai complet, [Tetris.MAX_LOCK_RESETS] fois au plus. */
    private fun touched() {
        if (isGrounded() && lockResets < Tetris.MAX_LOCK_RESETS) {
            lockMs = 0L
            lockResets++
        }
    }

    /** Fige la pièce en cours dans le plateau, efface les lignes pleines, compte les points puis envoie la pièce suivante. */
    private fun lock() {
        for ((col, row) in pieceCells()) board[row * Tetris.COLS + col] = current + 1
        val cleared = clearLines()
        lastCleared = cleared
        score += Tetris.lineClearPoints(cleared, level)
        lines += cleared
        holdUsed = false
        spawn(takeNext())
    }

    /** Efface les lignes pleines en faisant descendre celles du dessus. Renvoie le nombre de lignes effacées. */
    private fun clearLines(): Int {
        val cols = Tetris.COLS
        var write = Tetris.ROWS - 1
        var cleared = 0
        for (read in Tetris.ROWS - 1 downTo 0) {
            val full = (0 until cols).all { board[read * cols + it] != 0 }
            if (full) {
                cleared++
            } else {
                if (write != read) board.copyInto(board, write * cols, read * cols, read * cols + cols)
                write--
            }
        }
        for (row in 0..write) board.fill(0, row * cols, row * cols + cols)
        return cleared
    }

    private fun fits(kind: Int, rot: Int, px: Int, py: Int): Boolean =
        absoluteCells(kind, rot, px, py).all { (col, row) ->
            col in 0 until Tetris.COLS && row in 0 until Tetris.ROWS && board[row * Tetris.COLS + col] == 0
        }

    private fun absoluteCells(kind: Int, rot: Int, px: Int, py: Int): List<Pair<Int, Int>> =
        Tetromino.entries[kind].cells(rot).map { (dx, dy) -> (px + dx) to (py + dy) }

    private fun fillQueue() {
        while (queue.size <= Tetris.NEXT_COUNT) queue.addAll(Tetris.newBag(random))
    }

    private fun takeNext(): Int {
        fillQueue()
        val kind = queue.removeAt(0)
        fillQueue()
        return kind
    }

    /**
     * L'état de la partie en une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]). La graine du
     * hasard n'en fait pas partie : les pièces à venir sont gardées, celles d'après seront tirées de nouveau.
     */
    fun save(): List<Int> = buildList {
        add(SAVE_VERSION)
        add(current)
        add(rotation)
        add(x)
        add(y)
        add(held)
        add(if (holdUsed) 1 else 0)
        add(score)
        add(lines)
        add(fall.toInt())
        add(lockMs.toInt())
        add(lockResets)
        add(if (isOver) 1 else 0)
        add(lastCleared)
        add(queue.size)
        addAll(queue)
        addAll(board.toList())
    }

    companion object {
        /** Pas de pièce (réserve vide). */
        const val NONE = -1

        private const val SAVE_VERSION = 1
        private const val SAVE_HEADER = 15

        /** Les décalages (colonnes, lignes) essayés l'un après l'autre pour faire tourner une pièce à qui la place manque. */
        private val KICKS = listOf(0 to 0, -1 to 0, 1 to 0, -2 to 0, 2 to 0, 0 to -1, -1 to -1, 1 to -1)

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): TetrisGame? {
            if (data.size < SAVE_HEADER || data[0] != SAVE_VERSION) return null
            val queueSize = data[14]
            if (queueSize < 0 || data.size != SAVE_HEADER + queueSize + Tetris.COLS * Tetris.ROWS) return null
            val kinds = Tetromino.entries.size
            val queued = data.subList(SAVE_HEADER, SAVE_HEADER + queueSize)
            val cells = data.subList(SAVE_HEADER + queueSize, data.size)
            val valid = data[1] in 0 until kinds && data[2] in 0..3 && data[5] in NONE until kinds &&
                data[6] in 0..1 && data[7] >= 0 && data[8] >= 0 && data[9] >= 0 && data[10] >= 0 &&
                data[11] in 0..Tetris.MAX_LOCK_RESETS && data[12] in 0..1 && data[13] in 0..4 &&
                queued.all { it in 0 until kinds } && cells.all { it in 0..kinds }
            if (!valid) return null
            val game = TetrisGame(random)
            game.board.indices.forEach { game.board[it] = cells[it] }
            game.queue.clear()
            game.queue.addAll(queued)
            game.current = data[1]
            game.rotation = data[2]
            game.x = data[3]
            game.y = data[4]
            game.held = data[5]
            game.holdUsed = data[6] == 1
            game.score = data[7]
            game.lines = data[8]
            game.fall = data[9].toLong()
            game.lockMs = data[10].toLong()
            game.lockResets = data[11]
            game.isOver = data[12] == 1
            game.lastCleared = data[13]
            game.fillQueue()
            // Une pièce qui ne tient pas dans le plateau rendu est un signe de données abîmées.
            if (!game.isOver && !game.fits(game.current, game.rotation, game.x, game.y)) return null
            return game
        }
    }
}
