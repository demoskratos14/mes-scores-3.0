package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Identifiant du Xonix parmi les jeux prédéfinis (écran à part : voir [XonixScreen]). */
internal const val XONIX_ID = "builtin_xonix"

/** Réglages et calculs du Xonix. */
internal object Xonix {

    const val COLS = 24
    const val ROWS = 34

    // Les trois sortes de cases : la mer (à conquérir), la terre ferme (déjà conquise, bordure comprise) et le tracé en cours.
    const val SEA = 0
    const val LAND = 1
    const val TRAIL = 2

    // Les directions : le joueur avance seul dans celle qu'on lui donne.
    const val NONE = 0
    const val UP = 1
    const val DOWN = 2
    const val LEFT = 3
    const val RIGHT = 4

    const val LIVES = 3

    /** Part de la mer (hors bordure) à conquérir pour finir un niveau, en pour cent. */
    const val TARGET_PERCENT = 75

    /** Nombre de cases de mer à l'intérieur de la bordure. */
    const val INTERIOR = (COLS - 2) * (ROWS - 2)

    /** Colonne de départ du joueur, sur la bordure du haut. */
    const val START_COL = COLS / 2

    /** Temps entre deux pas du joueur, et entre deux pas d'une mine. */
    const val MOVE_MS = 55L
    const val SOLDIER_MS = 150L

    /** Arrêt de la partie après une vie perdue, puis après la fin d'un niveau (le temps de le voir). */
    const val HURT_MS = 700L
    const val LEVEL_CLEAR_MS = 1400L

    const val MAX_BALLS = 8
    const val MAX_SOLDIERS = 3

    /** Une bille avance par pas de ce nombre de cases au plus : plus court qu'une case, pour ne rien traverser. */
    const val MAX_STEP = 0.25f

    /** Nombre de billes dans la mer au niveau [level] : deux au premier, une de plus à chaque niveau. */
    fun ballsFor(level: Int): Int = min(MAX_BALLS, level.coerceAtLeast(1) + 1)

    /** Nombre de mines sur la terre ferme : aucune avant le niveau 3, puis une de plus tous les trois niveaux. */
    fun soldiersFor(level: Int): Int = if (level < 3) 0 else min(MAX_SOLDIERS, (level - 3) / 3 + 1)

    /** Vitesse des billes au niveau [level], en cases par seconde. */
    fun ballSpeed(level: Int): Float = min(14f, 7f + 0.6f * (level.coerceAtLeast(1) - 1))

    /** Les cases qu'on avance (colonne, ligne) pour une direction. */
    fun dx(dir: Int): Int = when (dir) {
        LEFT -> -1
        RIGHT -> 1
        else -> 0
    }

    fun dy(dir: Int): Int = when (dir) {
        UP -> -1
        DOWN -> 1
        else -> 0
    }

    fun opposite(dir: Int): Int = when (dir) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
        else -> NONE
    }

    /** Part de la mer conquise, en pour cent (arrondie vers le bas), quand [claimed] cases sont prises. */
    fun percentOf(claimed: Int): Int = claimed.coerceIn(0, INTERIOR) * 100 / INTERIOR

    /**
     * Points d'une conquête de [cells] cases d'un coup au niveau [level] : 10 par case, multipliés par le niveau, et par
     * 2, 3 ou 5 quand on en prend beaucoup d'un coup (10, 30 ou 60 cases).
     */
    fun fillPoints(cells: Int, level: Int): Int {
        val bonus = when {
            cells >= 60 -> 5
            cells >= 30 -> 3
            cells >= 10 -> 2
            else -> 1
        }
        return cells.coerceAtLeast(0) * 10 * level.coerceAtLeast(1) * bonus
    }

    /** Bonus de fin de niveau : 500 par niveau, et 200 par vie restante. */
    fun levelBonus(level: Int, lives: Int): Int = 500 * level.coerceAtLeast(1) + 200 * lives.coerceAtLeast(0)
}

/** Une bille de la mer : sa position (en cases, au centre) et sa vitesse (en cases par seconde). */
internal class XonixBall(var x: Float, var y: Float, var vx: Float, var vy: Float)

/** Une mine de la terre ferme : sa case et sa direction en diagonale (−1 ou 1 sur chaque axe). */
internal class XonixSoldier(var x: Int, var y: Int, var dx: Int, var dy: Int)

/**
 * Une partie de Xonix : un plateau de [Xonix.COLS] × [Xonix.ROWS] cases dont le tour est de la terre ferme et l'intérieur
 * de la mer. Le joueur part de la terre, trace un chemin dans la mer et revient sur la terre : la partie de la mer qui
 * ne contient aucune bille devient de la terre. Une bille qui touche le tracé, ou le joueur qui recoupe son propre tracé
 * ou croise une mine, fait perdre une vie. La classe est modifiable (une partie qui bouge à chaque image) : l'écran
 * redessine après chaque appel. La ligne 0 est en haut.
 */
internal class XonixGame(private val random: Random = Random.Default) {

    private val cells = IntArray(Xonix.COLS * Xonix.ROWS)
    private val ballList = ArrayList<XonixBall>()
    private val soldierList = ArrayList<XonixSoldier>()

    /** Les billes de la mer et les mines de la terre (à ne pas modifier : l'écran les dessine). */
    val balls: List<XonixBall> get() = ballList
    val soldiers: List<XonixSoldier> get() = soldierList

    var level = 1
        private set
    var score = 0
        private set
    var lives = Xonix.LIVES
        private set

    /** La case du joueur et sa direction ([Xonix.NONE] : il ne bouge pas). */
    var px = Xonix.START_COL
        private set
    var py = 0
        private set
    var dir = Xonix.NONE
        private set

    /** Vrai quand la dernière vie est perdue : la partie est finie. */
    var isOver = false
        private set

    /** Temps restant de l'arrêt après une vie perdue, et de celui après un niveau fini (0 : le jeu tourne). */
    var hurtMs = 0L
        private set
    var levelClearMs = 0L
        private set

    /** Nombre de cases de mer conquises depuis le début du niveau. */
    var claimed = 0
        private set

    private var trailCount = 0
    private var moveAcc = 0L
    private var soldierAcc = 0L

    val percent: Int get() = Xonix.percentOf(claimed)
    val isHurt: Boolean get() = hurtMs > 0L
    val isLevelClearing: Boolean get() = levelClearMs > 0L

    init {
        startLevel(1)
    }

    /** Recommence une partie : premier niveau, trois vies, score à zéro. */
    fun reset() {
        score = 0
        lives = Xonix.LIVES
        isOver = false
        startLevel(1)
    }

    /** Le contenu de la case ([Xonix.SEA], [Xonix.LAND] ou [Xonix.TRAIL]) ; la terre ferme hors du plateau. */
    fun cell(x: Int, y: Int): Int =
        if (x in 0 until Xonix.COLS && y in 0 until Xonix.ROWS) cells[y * Xonix.COLS + x] else Xonix.LAND

    /** Vrai si le joueur est sur la terre ferme (et non sur son tracé). */
    val isOnLand: Boolean get() = cell(px, py) == Xonix.LAND

    private fun cellAt(x: Float, y: Float): Int = cell(floor(x).toInt(), floor(y).toInt())

    /** Donne une direction au joueur. Sur son tracé, il ne peut pas faire demi-tour. Sans effet entre deux phases de jeu. */
    fun setDirection(newDir: Int) {
        if (isOver || isHurt || isLevelClearing || newDir !in Xonix.UP..Xonix.RIGHT) return
        if (!isOnLand && newDir == Xonix.opposite(dir)) return
        dir = newDir
    }

    /** Arrête le joueur, mais seulement sur la terre ferme : en mer, il faut continuer. */
    fun stop() {
        if (isOnLand) dir = Xonix.NONE
    }

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Long) {
        if (isOver || ms <= 0L) return
        if (levelClearMs > 0L) {
            levelClearMs -= ms
            if (levelClearMs <= 0L) startLevel(level + 1)
            return
        }
        if (hurtMs > 0L) {
            hurtMs -= ms
            if (hurtMs <= 0L) {
                hurtMs = 0L
                if (lives <= 0) isOver = true else respawn()
            }
            return
        }
        moveAcc += ms
        while (moveAcc >= Xonix.MOVE_MS && !isHurt && !isLevelClearing) {
            moveAcc -= Xonix.MOVE_MS
            stepPlayer()
        }
        if (isHurt || isLevelClearing) {
            moveAcc = 0L
            return
        }
        moveBalls(ms)
        if (isHurt) return
        soldierAcc += ms
        while (soldierAcc >= Xonix.SOLDIER_MS && !isHurt) {
            soldierAcc -= Xonix.SOLDIER_MS
            stepSoldiers()
        }
    }

    private fun startLevel(newLevel: Int) {
        level = newLevel
        for (y in 0 until Xonix.ROWS) {
            for (x in 0 until Xonix.COLS) {
                val border = x == 0 || y == 0 || x == Xonix.COLS - 1 || y == Xonix.ROWS - 1
                cells[y * Xonix.COLS + x] = if (border) Xonix.LAND else Xonix.SEA
            }
        }
        claimed = 0
        trailCount = 0
        px = Xonix.START_COL
        py = 0
        dir = Xonix.NONE
        hurtMs = 0L
        levelClearMs = 0L
        moveAcc = 0L
        soldierAcc = 0L
        ballList.clear()
        soldierList.clear()
        val speed = Xonix.ballSpeed(newLevel)
        repeat(Xonix.ballsFor(newLevel)) {
            // Une direction en diagonale (entre 25° et 65°) : une bille ne rase jamais un bord sans le quitter.
            val angle = (25.0 + random.nextDouble() * 40.0) * PI / 180.0
            val signX = if (random.nextBoolean()) 1f else -1f
            val signY = if (random.nextBoolean()) 1f else -1f
            ballList.add(
                XonixBall(
                    x = 2f + random.nextFloat() * (Xonix.COLS - 4),
                    y = 2f + random.nextFloat() * (Xonix.ROWS - 4),
                    vx = (cos(angle) * speed).toFloat() * signX,
                    vy = (sin(angle) * speed).toFloat() * signY
                )
            )
        }
        repeat(Xonix.soldiersFor(newLevel)) {
            val onBottom = random.nextBoolean()
            val sx = if (onBottom) random.nextInt(1, Xonix.COLS - 1) else if (random.nextBoolean()) 0 else Xonix.COLS - 1
            val sy = if (onBottom) Xonix.ROWS - 1 else random.nextInt(Xonix.ROWS / 2, Xonix.ROWS - 1)
            soldierList.add(
                XonixSoldier(sx, sy, if (random.nextBoolean()) 1 else -1, if (random.nextBoolean()) 1 else -1)
            )
        }
    }

    /** Après une vie perdue : le tracé s'efface, le joueur revient au départ, et les mines trop proches s'éloignent. */
    private fun respawn() {
        for (i in cells.indices) if (cells[i] == Xonix.TRAIL) cells[i] = Xonix.SEA
        trailCount = 0
        px = Xonix.START_COL
        py = 0
        dir = Xonix.NONE
        moveAcc = 0L
        soldierAcc = 0L
        soldierList.forEach { soldier ->
            if (abs(soldier.x - px) + abs(soldier.y - py) < 5) {
                soldier.x = Xonix.COLS / 2
                soldier.y = Xonix.ROWS - 1
            }
        }
    }

    private fun lose() {
        if (isHurt) return
        lives--
        hurtMs = Xonix.HURT_MS
    }

    private fun stepPlayer() {
        if (dir == Xonix.NONE) return
        val nx = px + Xonix.dx(dir)
        val ny = py + Xonix.dy(dir)
        if (nx !in 0 until Xonix.COLS || ny !in 0 until Xonix.ROWS) {
            // Au bord du plateau, sur la terre : on s'arrête.
            dir = Xonix.NONE
            return
        }
        val target = cells[ny * Xonix.COLS + nx]
        if (target == Xonix.TRAIL) {
            // On recoupe son propre tracé.
            lose()
            return
        }
        if (target == Xonix.SEA && ballList.any { floor(it.x).toInt() == nx && floor(it.y).toInt() == ny }) {
            lose()
            return
        }
        val wasOnLand = isOnLand
        px = nx
        py = ny
        if (target == Xonix.SEA) {
            cells[ny * Xonix.COLS + nx] = Xonix.TRAIL
            trailCount++
        } else if (!wasOnLand) {
            // De la mer, on revient sur la terre : le tracé devient de la terre et on prend la mer qu'il enferme.
            closeTrail()
        }
        if (!isLevelClearing && soldierList.any { it.x == px && it.y == py }) lose()
    }

    /** Le tracé devient de la terre, puis toute la mer sans bille à portée devient de la terre aussi. */
    private fun closeTrail() {
        var gained = 0
        for (i in cells.indices) {
            if (cells[i] == Xonix.TRAIL) {
                cells[i] = Xonix.LAND
                gained++
            }
        }
        trailCount = 0
        val reached = BooleanArray(cells.size)
        val stack = IntArray(cells.size)
        var top = 0
        fun visit(index: Int) {
            if (cells[index] == Xonix.SEA && !reached[index]) {
                reached[index] = true
                stack[top++] = index
            }
        }
        ballList.forEach { ball ->
            val bx = floor(ball.x).toInt()
            val by = floor(ball.y).toInt()
            if (bx in 0 until Xonix.COLS && by in 0 until Xonix.ROWS) visit(by * Xonix.COLS + bx)
        }
        while (top > 0) {
            val index = stack[--top]
            val x = index % Xonix.COLS
            val y = index / Xonix.COLS
            if (x > 0) visit(index - 1)
            if (x < Xonix.COLS - 1) visit(index + 1)
            if (y > 0) visit(index - Xonix.COLS)
            if (y < Xonix.ROWS - 1) visit(index + Xonix.COLS)
        }
        for (i in cells.indices) {
            if (cells[i] == Xonix.SEA && !reached[i]) {
                cells[i] = Xonix.LAND
                gained++
            }
        }
        claimed += gained
        score += Xonix.fillPoints(gained, level)
        dir = Xonix.NONE
        if (percent >= Xonix.TARGET_PERCENT) {
            levelClearMs = Xonix.LEVEL_CLEAR_MS
            score += Xonix.levelBonus(level, lives)
        }
    }

    private fun moveBalls(ms: Long) {
        val seconds = ms / 1000f
        for (ball in ballList) {
            val speed = hypot(ball.vx, ball.vy)
            val steps = max(1, ceil(speed * seconds / Xonix.MAX_STEP).toInt())
            val h = seconds / steps
            for (step in 0 until steps) {
                val nx = ball.x + ball.vx * h
                when (cellAt(nx, ball.y)) {
                    Xonix.LAND -> ball.vx = -ball.vx
                    Xonix.TRAIL -> {
                        lose()
                        return
                    }
                    else -> ball.x = nx
                }
                val ny = ball.y + ball.vy * h
                when (cellAt(ball.x, ny)) {
                    Xonix.LAND -> ball.vy = -ball.vy
                    Xonix.TRAIL -> {
                        lose()
                        return
                    }
                    else -> ball.y = ny
                }
            }
        }
    }

    private fun isLand(x: Int, y: Int): Boolean = x in 0 until Xonix.COLS && y in 0 until Xonix.ROWS && cells[y * Xonix.COLS + x] == Xonix.LAND

    /** Une mine avance d'une case en diagonale sur la terre, rebondit sur la mer et glisse le long des bords. */
    private fun stepSoldiers() {
        for (s in soldierList) {
            if (isLand(s.x + s.dx, s.y + s.dy)) {
                s.x += s.dx
                s.y += s.dy
            } else {
                val horizontal = isLand(s.x + s.dx, s.y)
                val vertical = isLand(s.x, s.y + s.dy)
                if (horizontal && !vertical) {
                    // Une bordure horizontale : la mine glisse le long d'elle et rebondit sur la mer.
                    s.dy = -s.dy
                    s.x += s.dx
                } else if (!horizontal && vertical) {
                    // Une bordure verticale : même chose en tournant d'un quart de tour.
                    s.dx = -s.dx
                    s.y += s.dy
                } else {
                    // Un coin ou un cul-de-sac : demi-tour, puis un pas si la voie est libre.
                    s.dx = -s.dx
                    s.dy = -s.dy
                    if (isLand(s.x + s.dx, s.y + s.dy)) {
                        s.x += s.dx
                        s.y += s.dy
                    } else if (isLand(s.x + s.dx, s.y)) {
                        s.x += s.dx
                    } else if (isLand(s.x, s.y + s.dy)) {
                        s.y += s.dy
                    }
                }
            }
            if (s.x == px && s.y == py) {
                lose()
                return
            }
        }
    }

    /**
     * L'état de la partie sous forme d'une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]). Les
     * positions et vitesses des billes sont gardées au millième de case près ; le hasard n'en fait pas partie.
     */
    fun save(): List<Int> = buildList {
        add(SAVE_VERSION)
        add(level)
        add(score)
        add(lives)
        add(px)
        add(py)
        add(dir)
        add(hurtMs.toInt())
        add(levelClearMs.toInt())
        add(if (isOver) 1 else 0)
        add(ballList.size)
        add(soldierList.size)
        add(moveAcc.toInt())
        add(soldierAcc.toInt())
        ballList.forEach { ball ->
            add((ball.x * 1000f).roundToInt())
            add((ball.y * 1000f).roundToInt())
            add((ball.vx * 1000f).roundToInt())
            add((ball.vy * 1000f).roundToInt())
        }
        soldierList.forEach { soldier ->
            add(soldier.x)
            add(soldier.y)
            add(soldier.dx)
            add(soldier.dy)
        }
        addAll(cells.toList())
    }

    companion object {
        private const val SAVE_VERSION = 1
        private const val SAVE_HEADER = 14

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): XonixGame? {
            if (data.size < SAVE_HEADER || data[0] != SAVE_VERSION) return null
            val ballCount = data[10]
            val soldierCount = data[11]
            if (ballCount !in 0..Xonix.MAX_BALLS || soldierCount !in 0..Xonix.MAX_SOLDIERS) return null
            val boardSize = Xonix.COLS * Xonix.ROWS
            if (data.size != SAVE_HEADER + ballCount * 4 + soldierCount * 4 + boardSize) return null
            val validHeader = data[1] >= 1 && data[2] >= 0 && data[3] in 0..Xonix.LIVES &&
                data[4] in 0 until Xonix.COLS && data[5] in 0 until Xonix.ROWS && data[6] in Xonix.NONE..Xonix.RIGHT &&
                data[7] in 0..Xonix.HURT_MS.toInt() && data[8] in 0..Xonix.LEVEL_CLEAR_MS.toInt() && data[9] in 0..1 &&
                data[12] in 0 until Xonix.MOVE_MS.toInt() && data[13] in 0 until Xonix.SOLDIER_MS.toInt()
            if (!validHeader) return null
            val ballsStart = SAVE_HEADER
            val soldiersStart = ballsStart + ballCount * 4
            val cellsStart = soldiersStart + soldierCount * 4
            val saved = data.subList(cellsStart, data.size)
            if (saved.any { it !in Xonix.SEA..Xonix.TRAIL }) return null
            val borderIntact = (0 until boardSize).all { index ->
                val x = index % Xonix.COLS
                val y = index / Xonix.COLS
                val border = x == 0 || y == 0 || x == Xonix.COLS - 1 || y == Xonix.ROWS - 1
                !border || saved[index] == Xonix.LAND
            }
            if (!borderIntact) return null
            val game = XonixGame(random)
            saved.forEachIndexed { index, value -> game.cells[index] = value }
            game.ballList.clear()
            for (i in 0 until ballCount) {
                val at = ballsStart + i * 4
                val ball = XonixBall(data[at] / 1000f, data[at + 1] / 1000f, data[at + 2] / 1000f, data[at + 3] / 1000f)
                // Une bille hors de la mer est un signe de données abîmées.
                if (game.cellAt(ball.x, ball.y) != Xonix.SEA) return null
                game.ballList.add(ball)
            }
            game.soldierList.clear()
            for (i in 0 until soldierCount) {
                val at = soldiersStart + i * 4
                val soldier = XonixSoldier(data[at], data[at + 1], data[at + 2], data[at + 3])
                val valid = soldier.x in 0 until Xonix.COLS && soldier.y in 0 until Xonix.ROWS &&
                    abs(soldier.dx) == 1 && abs(soldier.dy) == 1
                if (!valid) return null
                game.soldierList.add(soldier)
            }
            game.level = data[1]
            game.score = data[2]
            game.lives = data[3]
            game.px = data[4]
            game.py = data[5]
            game.dir = data[6]
            game.hurtMs = data[7].toLong()
            game.levelClearMs = data[8].toLong()
            game.isOver = data[9] == 1
            game.moveAcc = data[12].toLong()
            game.soldierAcc = data[13].toLong()
            // Le joueur est sur la terre ou sur son tracé, jamais en pleine mer.
            if (game.cell(game.px, game.py) == Xonix.SEA) return null
            game.trailCount = saved.count { it == Xonix.TRAIL }
            game.claimed = (0 until boardSize).count { index ->
                val x = index % Xonix.COLS
                val y = index / Xonix.COLS
                x in 1 until Xonix.COLS - 1 && y in 1 until Xonix.ROWS - 1 && saved[index] == Xonix.LAND
            }
            return game
        }
    }
}
