package com.aventure.messcores

import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

/** Identifiant du Snake parmi les jeux prédéfinis (écran à part : voir [SnakeScreen]). */
internal const val SNAKE_ID = "builtin_snake"

/** Les quatre directions, dans le sens horaire : l'opposée d'une direction est celle qui en est éloignée de deux rangs. */
internal enum class SnakeDir(val dx: Int, val dy: Int) {
    UP(0, -1), RIGHT(1, 0), DOWN(0, 1), LEFT(-1, 0);

    val opposite: SnakeDir get() = entries[(ordinal + 2) % 4]
}

/** Réglages et calculs du Snake. */
internal object Snake {

    const val COLS = 12
    const val ROWS = 18

    /** Longueur du serpent au départ (il part de la gauche, vers la droite). */
    const val START_LENGTH = 3

    /** Nombre de souris à manger pour monter d'un niveau. */
    const val MICE_PER_LEVEL = 5

    const val MAX_LEVEL = 15

    /** Temps entre deux cases au niveau 1, facteur appliqué à chaque niveau, et plancher. */
    private const val START_STEP_MS = 240.0
    private const val STEP_FACTOR = 0.9
    const val MIN_STEP_MS = 85L

    /** Points gagnés en remplissant tout le plateau. */
    const val BOARD_BONUS = 500

    /** Niveaux de vitesse gagnés à chaque plateau rempli : la partie repart plus vite que la précédente. */
    const val SPEED_LEVELS_PER_BOARD = 3

    /** Plancher du temps entre deux cases : il baisse de 10 ms à chaque plateau rempli, sans descendre sous 40 ms. */
    private const val MIN_STEP_DROP_MS = 10L
    private const val ABSOLUTE_MIN_STEP_MS = 40L

    /** Nombre de virages gardés en attente : deux virages rapides entre deux cases se suivent au lieu de s'écraser. */
    const val MAX_QUEUED_TURNS = 2

    /** Points d'une souris mangée au niveau [level]. */
    const val MOUSE_POINTS = 10

    /** Le niveau atteint après [eaten] souris mangées. */
    fun levelFor(eaten: Int): Int = (eaten.coerceAtLeast(0) / MICE_PER_LEVEL + 1).coerceAtMost(MAX_LEVEL)

    /**
     * Temps entre deux cases à la vitesse [speedLevel], en millisecondes, après [boardsCleared] plateaux remplis (le plancher
     * baisse un peu à chaque plateau rempli, pour que la vitesse puisse encore augmenter).
     */
    fun stepMs(speedLevel: Int, boardsCleared: Int = 0): Long {
        val floor = max(ABSOLUTE_MIN_STEP_MS, MIN_STEP_MS - MIN_STEP_DROP_MS * boardsCleared.coerceAtLeast(0))
        return max(floor, (START_STEP_MS * STEP_FACTOR.pow(speedLevel.coerceAtLeast(1) - 1)).toLong())
    }

    /** Les cases (colonne, ligne) voisines de [col], [row] dans la direction [dir] : les bords se referment sur eux-mêmes. */
    fun wrap(col: Int, row: Int, dir: SnakeDir): Pair<Int, Int> =
        Math.floorMod(col + dir.dx, COLS) to Math.floorMod(row + dir.dy, ROWS)
}

/**
 * Une partie de Snake : un serpent sur un plateau de [Snake.COLS] × [Snake.ROWS] cases dont les bords se referment
 * (sortir à droite fait revenir à gauche, sortir en haut fait revenir en bas, et inversement). Une souris est posée sur
 * une case libre ; la manger fait grandir le serpent d'une case et fait apparaître une autre souris. La partie s'arrête
 * quand la tête touche le corps. Si le serpent remplit tout le plateau sans se mordre, c'est gagné : il rapporte un bonus, un
 * nouveau plateau commence avec un serpent tout petit (le score et les souris mangées sont gardés) et le serpent va plus vite
 * qu'avant ([boardsCleared] compte les plateaux remplis).
 *
 * Les cases sont numérotées `ligne * COLS + colonne`. Le corps va de la tête ([head]) à la queue.
 */
internal class SnakeGame(private val random: Random = Random.Default) {

    /** Le corps, de la tête (début) à la queue (fin), en numéros de case. */
    private val body = ArrayDeque<Int>()

    /** Les cases occupées par le serpent, pour savoir en un coup d'œil si une case est libre. */
    private val occupied = BooleanArray(Snake.COLS * Snake.ROWS)

    /** Les virages demandés, pas encore pris en compte (le premier sera pris à la prochaine case). */
    private val turns = ArrayDeque<SnakeDir>()

    /** La direction dans laquelle le serpent avance en ce moment. */
    var direction = SnakeDir.RIGHT
        private set

    /** La case de la souris, ou [NONE] quand le serpent remplit tout le plateau. */
    var mouse = NONE
        private set

    var score = 0
        private set

    /** Nombre de souris mangées. */
    var eaten = 0
        private set

    var isOver = false
        private set

    /** Nombre de plateaux entièrement remplis depuis le début de la partie. */
    var boardsCleared = 0
        private set

    /** Souris mangées sur le plateau en cours (elles font monter la vitesse jusqu'au prochain plateau). */
    private var boardEaten = 0

    /** Temps écoulé depuis la dernière case, en millisecondes. */
    private var stepElapsed = 0L

    init {
        reset()
    }

    /** Le niveau, qui fait monter les points d'une souris. */
    val level: Int get() = Snake.levelFor(eaten)

    /** Le niveau de vitesse : il monte avec les souris du plateau en cours, et de plusieurs crans à chaque plateau rempli. */
    val speedLevel: Int get() = boardEaten / Snake.MICE_PER_LEVEL + 1 + boardsCleared * Snake.SPEED_LEVELS_PER_BOARD

    /** Le temps entre deux cases en ce moment, en millisecondes. */
    val stepMs: Long get() = Snake.stepMs(speedLevel, boardsCleared)

    val length: Int get() = body.size

    /** La case de la tête. */
    val head: Int get() = body.first()

    /** Part du temps écoulé vers la prochaine case, de 0 à 1 (pour un dessin fluide). */
    val progress: Float get() = (stepElapsed.toFloat() / stepMs).coerceIn(0f, 1f)

    /** Les cases du serpent, de la tête à la queue. */
    fun bodyCells(): List<Int> = body.toList()

    /** Vrai si le serpent occupe la case [col], [row]. */
    fun isSnake(col: Int, row: Int): Boolean = occupied[row * Snake.COLS + col]

    /** Repart de zéro : un nouveau plateau, sans score ni plateau rempli. */
    fun reset() {
        score = 0
        eaten = 0
        boardsCleared = 0
        isOver = false
        newBoard()
    }

    /** Un nouveau plateau : un serpent de [Snake.START_LENGTH] cases au milieu à gauche, tourné vers la droite, et une souris. */
    private fun newBoard() {
        body.clear()
        occupied.fill(false)
        turns.clear()
        val row = Snake.ROWS / 2
        for (i in 0 until Snake.START_LENGTH) {
            val cell = row * Snake.COLS + (Snake.START_LENGTH - 1 - i)
            body.addLast(cell)
            occupied[cell] = true
        }
        direction = SnakeDir.RIGHT
        boardEaten = 0
        stepElapsed = 0L
        placeMouse()
    }

    /**
     * Demande de tourner vers [dir]. Un demi-tour est refusé (le serpent se mordrait aussitôt), de même que la direction
     * déjà prise. Plusieurs virages peuvent être demandés entre deux cases : ils sont pris un par case, dans l'ordre.
     * Renvoie vrai si le virage est retenu.
     */
    fun turn(dir: SnakeDir): Boolean {
        if (isOver) return false
        val last = turns.lastOrNull() ?: direction
        if (dir == last || dir == last.opposite) return false
        if (turns.size >= Snake.MAX_QUEUED_TURNS) return false
        turns.addLast(dir)
        return true
    }

    /** Fait passer [elapsedMs] millisecondes : le serpent avance d'autant de cases que le temps le permet. */
    fun tick(elapsedMs: Long) {
        if (isOver) return
        stepElapsed += elapsedMs.coerceAtLeast(0L)
        while (!isOver && stepElapsed >= stepMs) {
            stepElapsed -= stepMs
            step()
        }
    }

    /** Avance d'une case : prend le prochain virage demandé, passe d'un bord à l'autre au besoin, mange la souris ou se mord. */
    fun step() {
        if (isOver) return
        turns.removeFirstOrNull()?.let { direction = it }
        val headCell = body.first()
        val (col, row) = Snake.wrap(headCell % Snake.COLS, headCell / Snake.COLS, direction)
        val next = row * Snake.COLS + col
        val eats = next == mouse
        // La queue libère sa case au même moment : la tête peut la prendre, sauf si le serpent grandit (la queue reste alors).
        val tail = body.last()
        if (occupied[next] && !(next == tail && !eats)) {
            isOver = true
            return
        }
        if (!eats) {
            body.removeLast()
            occupied[tail] = false
        }
        body.addFirst(next)
        occupied[next] = true
        if (eats) {
            eaten++
            boardEaten++
            score += Snake.MOUSE_POINTS * level
            placeMouse()
            if (mouse == NONE) {
                // Le plateau est plein : bonus, puis on repart sur un nouveau plateau, plus vite.
                score += Snake.BOARD_BONUS
                boardsCleared++
                newBoard()
            }
        }
    }

    /** Pose la souris sur une case libre tirée au hasard ; [NONE] s'il n'en reste aucune. */
    private fun placeMouse() {
        val free = occupied.indices.filter { !occupied[it] }
        mouse = if (free.isEmpty()) NONE else free[random.nextInt(free.size)]
    }

    /** Pose la souris sur [cell] (pour les tests). */
    internal fun placeMouseAt(cell: Int) {
        require(cell in occupied.indices && !occupied[cell])
        mouse = cell
    }

    /**
     * L'état de la partie en une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]) :
     * version, direction, souris, score, souris mangées, fin, plateaux remplis, souris du plateau en cours, temps écoulé,
     * nombre de virages en attente, longueur, puis les virages et les cases du corps.
     */
    fun save(): List<Int> = buildList {
        add(SAVE_VERSION)
        add(direction.ordinal)
        add(mouse)
        add(score)
        add(eaten)
        add(if (isOver) 1 else 0)
        add(boardsCleared)
        add(boardEaten)
        add(stepElapsed.toInt())
        add(turns.size)
        add(body.size)
        addAll(turns.map { it.ordinal })
        addAll(body)
    }

    companion object {
        /** Pas de souris (le serpent remplit tout le plateau). */
        const val NONE = -1

        private const val SAVE_VERSION = 2
        private const val SAVE_HEADER = 11

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): SnakeGame? {
            if (data.size < SAVE_HEADER || data[0] != SAVE_VERSION) return null
            val cells = Snake.COLS * Snake.ROWS
            val turnCount = data[9]
            val length = data[10]
            if (turnCount !in 0..Snake.MAX_QUEUED_TURNS || length !in 1..cells) return null
            if (data.size != SAVE_HEADER + turnCount + length) return null
            val queued = data.subList(SAVE_HEADER, SAVE_HEADER + turnCount)
            val bodyCells = data.subList(SAVE_HEADER + turnCount, data.size)
            val valid = data[1] in 0..3 && data[2] in NONE until cells && data[3] >= 0 && data[4] >= 0 &&
                data[5] in 0..1 && data[6] >= 0 && data[7] >= 0 && data[8] >= 0 &&
                queued.all { it in 0..3 } && bodyCells.all { it in 0 until cells } &&
                bodyCells.toSet().size == bodyCells.size && data[2] !in bodyCells
            if (!valid) return null
            // Deux cases qui se suivent dans le corps sont voisines (en tenant compte des bords refermés).
            val connected = bodyCells.zipWithNext().all { (a, b) ->
                SnakeDir.entries.any { Snake.wrap(a % Snake.COLS, a / Snake.COLS, it).let { (c, r) -> r * Snake.COLS + c } == b }
            }
            if (!connected) return null
            val game = SnakeGame(random)
            game.body.clear()
            game.occupied.fill(false)
            bodyCells.forEach {
                game.body.addLast(it)
                game.occupied[it] = true
            }
            game.turns.clear()
            queued.forEach { game.turns.addLast(SnakeDir.entries[it]) }
            game.direction = SnakeDir.entries[data[1]]
            game.mouse = data[2]
            game.score = data[3]
            game.eaten = data[4]
            game.isOver = data[5] == 1
            game.boardsCleared = data[6]
            game.boardEaten = data[7]
            game.stepElapsed = data[8].toLong()
            if (game.mouse == NONE && !game.isOver) game.placeMouse()
            return game
        }
    }
}
