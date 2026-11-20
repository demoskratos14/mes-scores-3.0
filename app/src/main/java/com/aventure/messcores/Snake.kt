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

    /** Le plateau en portrait (sur le téléphone, tenu droit). */
    const val COLS = 12
    const val ROWS = 18

    /**
     * Le plateau du mode TV (voir [TvMode]) : toujours [TV_ROWS] lignes, et autant de colonnes qu'il en faut pour que les
     * cases carrées remplissent l'écran en paysage, entre [TV_MIN_COLS] et [TV_MAX_COLS].
     */
    const val TV_ROWS = 14
    const val TV_MIN_COLS = 20
    const val TV_MAX_COLS = 36

    /** Taille minimale et maximale d'un côté, pour refuser une sauvegarde absurde. */
    const val MIN_SIDE = 6
    const val MAX_SIDE = 60

    /** Le nombre de colonnes du plateau TV pour une zone de jeu dont la largeur vaut [aspect] fois la hauteur. */
    fun tvCols(aspect: Float): Int = Math.round(TV_ROWS * aspect).coerceIn(TV_MIN_COLS, TV_MAX_COLS)

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

    /**
     * La case (colonne, ligne) voisine de [col], [row] dans la direction [dir] sur un plateau de [cols] × [rows] cases :
     * les bords se referment sur eux-mêmes.
     */
    fun wrap(col: Int, row: Int, dir: SnakeDir, cols: Int = COLS, rows: Int = ROWS): Pair<Int, Int> =
        Math.floorMod(col + dir.dx, cols) to Math.floorMod(row + dir.dy, rows)
}

/**
 * Une partie de Snake : un serpent sur un plateau de [cols] × [rows] cases (12 × 18 par défaut, plus large en mode TV) dont les bords se referment
 * (sortir à droite fait revenir à gauche, sortir en haut fait revenir en bas, et inversement). Une souris est posée sur
 * une case libre ; la manger fait grandir le serpent d'une case et fait apparaître une autre souris. La partie s'arrête
 * quand la tête touche le corps. Si le serpent remplit tout le plateau sans se mordre, c'est gagné : il rapporte un bonus, un
 * nouveau plateau commence avec un serpent tout petit (le score et les souris mangées sont gardés) et le serpent va plus vite
 * qu'avant ([boardsCleared] compte les plateaux remplis).
 *
 * Les cases sont numérotées `ligne * cols + colonne`. Le corps va de la tête ([head]) à la queue.
 */
internal class SnakeGame(
    private val random: Random = Random.Default,
    val cols: Int = Snake.COLS,
    val rows: Int = Snake.ROWS
) {

    /** Le corps, de la tête (début) à la queue (fin), en numéros de case. */
    private val body = ArrayDeque<Int>()

    /** Les cases occupées par le serpent, pour savoir en un coup d'œil si une case est libre. */
    private val occupied = BooleanArray(cols * rows)

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
        require(cols in Snake.MIN_SIDE..Snake.MAX_SIDE && rows in Snake.MIN_SIDE..Snake.MAX_SIDE) { "plateau de $cols × $rows" }
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
    fun isSnake(col: Int, row: Int): Boolean = occupied[row * cols + col]

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
        val row = rows / 2
        for (i in 0 until Snake.START_LENGTH) {
            val cell = row * cols + (Snake.START_LENGTH - 1 - i)
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
        val (col, row) = Snake.wrap(headCell % cols, headCell / cols, direction, cols, rows)
        val next = row * cols + col
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
     * version, colonnes, lignes, direction, souris, score, souris mangées, fin, plateaux remplis, souris du plateau en cours, temps écoulé,
     * nombre de virages en attente, longueur, puis les virages et les cases du corps.
     */
    fun save(): List<Int> = buildList {
        add(SAVE_VERSION)
        add(cols)
        add(rows)
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

        /** Version 3 : la taille du plateau est gardée. La version 2 (plateau de 12 × 18, sans cette taille) est encore lue. */
        private const val SAVE_VERSION = 3
        private const val OLD_SAVE_VERSION = 2
        private const val SAVE_HEADER = 11

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): SnakeGame? {
            if (data.isEmpty()) return null
            // On ramène les deux versions à la même forme : colonnes, lignes, puis la liste de la version 2.
            val (cols, rows, d) = when (data[0]) {
                OLD_SAVE_VERSION -> Triple(Snake.COLS, Snake.ROWS, data)
                SAVE_VERSION -> if (data.size < 3) return null else Triple(data[1], data[2], listOf(OLD_SAVE_VERSION) + data.drop(3))
                else -> return null
            }
            if (cols !in Snake.MIN_SIDE..Snake.MAX_SIDE || rows !in Snake.MIN_SIDE..Snake.MAX_SIDE) return null
            if (d.size < SAVE_HEADER) return null
            val cells = cols * rows
            val turnCount = d[9]
            val length = d[10]
            if (turnCount !in 0..Snake.MAX_QUEUED_TURNS || length !in 1..cells) return null
            if (d.size != SAVE_HEADER + turnCount + length) return null
            val queued = d.subList(SAVE_HEADER, SAVE_HEADER + turnCount)
            val bodyCells = d.subList(SAVE_HEADER + turnCount, d.size)
            val valid = d[1] in 0..3 && d[2] in NONE until cells && d[3] >= 0 && d[4] >= 0 &&
                d[5] in 0..1 && d[6] >= 0 && d[7] >= 0 && d[8] >= 0 &&
                queued.all { it in 0..3 } && bodyCells.all { it in 0 until cells } &&
                bodyCells.toSet().size == bodyCells.size && d[2] !in bodyCells
            if (!valid) return null
            // Deux cases qui se suivent dans le corps sont voisines (en tenant compte des bords refermés).
            val connected = bodyCells.zipWithNext().all { (a, b) ->
                SnakeDir.entries.any { Snake.wrap(a % cols, a / cols, it, cols, rows).let { (c, r) -> r * cols + c } == b }
            }
            if (!connected) return null
            val game = SnakeGame(random, cols, rows)
            game.body.clear()
            game.occupied.fill(false)
            bodyCells.forEach {
                game.body.addLast(it)
                game.occupied[it] = true
            }
            game.turns.clear()
            queued.forEach { game.turns.addLast(SnakeDir.entries[it]) }
            game.direction = SnakeDir.entries[d[1]]
            game.mouse = d[2]
            game.score = d[3]
            game.eaten = d[4]
            game.isOver = d[5] == 1
            game.boardsCleared = d[6]
            game.boardEaten = d[7]
            game.stepElapsed = d[8].toLong()
            if (game.mouse == NONE && !game.isOver) game.placeMouse()
            return game
        }
    }
}
