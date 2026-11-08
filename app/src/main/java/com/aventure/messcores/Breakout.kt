package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Identifiant du Casse-briques parmi les jeux prédéfinis (écran à part : voir [BreakoutScreen]). */
internal const val BREAKOUT_ID = "builtin_breakout"

/**
 * Réglages et calculs du Casse-briques. Le terrain est en « unités » : une brique fait une unité de large, donc le
 * terrain en fait autant que de colonnes ; l'écran n'a qu'à mettre ce terrain à l'échelle. Le haut du terrain est en y = 0.
 *
 * Le terrain grandit avec le niveau : une colonne de plus à chaque niveau (jusqu'à [MAX_COLS]) et, dans la même
 * proportion, plus de rangées et plus de hauteur. La raquette, la balle et les briques gardent leur taille en unités :
 * affichés dans le même cadre, ils rapetissent à l'écran, et le nombre de briques à casser augmente vite d'un niveau
 * à l'autre. [COLS], [ROWS], [WIDTH], [HEIGHT], [BRICKS_TOP] et [PADDLE_TOP] sont les valeurs du niveau 1 ; pour un
 * autre niveau, voir [colsFor], [rowsFor], [widthFor], [heightFor], [bricksTopFor] et [paddleTopFor].
 */
internal object Breakout {

    const val COLS = 8
    const val ROWS = 7

    const val WIDTH = COLS.toFloat()
    const val HEIGHT = 13f

    /** Nombre maximal de colonnes : au-delà, les briques seraient trop fines pour le doigt, et seule la vitesse monte encore. */
    const val MAX_COLS = 16

    const val BRICK_W = 1f
    const val BRICK_H = 0.5f

    /** Hauteur où commence la première rangée de briques. */
    const val BRICKS_TOP = 1.5f

    const val PADDLE_W = 1.8f
    const val PADDLE_H = 0.36f

    /** Hauteur du dessus de la raquette. */
    const val PADDLE_TOP = 11.8f

    const val BALL_RADIUS = 0.18f

    const val LIVES = 3

    /**
     * Vitesse de la balle au niveau 1 (unités par seconde), ce qu'on y ajoute à chaque niveau, et le plafond. Ces vitesses
     * sont celles qu'on voit à l'écran : sur un terrain plus large, elles sont multipliées par son échelle (voir [speedFor]).
     */
    private const val START_SPEED = 7f
    private const val SPEED_PER_LEVEL = 0.7f
    const val MAX_SPEED = 13f

    /** La plus grande vitesse possible en unités par seconde : le plafond sur le plus grand terrain. */
    const val MAX_UNITS_SPEED = MAX_SPEED * MAX_COLS / COLS

    /** Plus long pas de déplacement de la balle : plus court qu'une brique, pour qu'elle ne traverse rien. */
    const val MAX_STEP = 0.1f

    /** Angle maximal de renvoi par la raquette, depuis la verticale (au bord de la raquette). */
    private const val MAX_BOUNCE_DEG = 60.0

    /** Angle maximal de la balle à la mise en jeu, depuis la verticale. */
    private const val MAX_SERVE_DEG = 25.0

    /** Part minimale de la vitesse qui doit rester verticale : sans cela, la balle pourrait rebondir à l'infini à l'horizontale. */
    private const val MIN_VERTICAL_SHARE = 0.2f

    /** Nombre de colonnes de briques au niveau [level] : 8 au niveau 1, une de plus à chaque niveau, jusqu'à [MAX_COLS]. */
    fun colsFor(level: Int): Int = min(MAX_COLS, COLS + level.coerceAtLeast(1) - 1)

    /** Le terrain du niveau [level] par rapport à celui du niveau 1 (1 au niveau 1, 2 au plus). */
    fun scaleFor(level: Int): Float = colsFor(level).toFloat() / COLS

    /** Nombre de rangées de briques au niveau [level] : elles suivent l'échelle du terrain, pour garder la même allure. */
    fun rowsFor(level: Int): Int = Math.round(ROWS * scaleFor(level))

    /** Nombre de briques à casser au niveau [level]. */
    fun bricksFor(level: Int): Int = colsFor(level) * rowsFor(level)

    fun widthFor(level: Int): Float = colsFor(level).toFloat()
    fun heightFor(level: Int): Float = HEIGHT * scaleFor(level)
    fun bricksTopFor(level: Int): Float = BRICKS_TOP * scaleFor(level)
    fun paddleTopFor(level: Int): Float = PADDLE_TOP * scaleFor(level)

    /**
     * Vitesse de la balle au niveau [level], en unités par seconde. Elle monte à chaque niveau, et suit l'échelle du
     * terrain : à l'écran, la balle met le même temps à le traverser, quelle que soit sa largeur.
     */
    fun speedFor(level: Int): Float =
        min(MAX_SPEED, START_SPEED + SPEED_PER_LEVEL * (level.coerceAtLeast(1) - 1)) * scaleFor(level)

    /**
     * Points d'une brique de la rangée [row] (0 = tout en haut) au niveau [level], sur un terrain de [rows] rangées :
     * plus la brique est haute, plus elle vaut.
     */
    fun brickPoints(row: Int, level: Int, rows: Int = ROWS): Int =
        (rows - row.coerceIn(0, rows - 1)) * 10 * level.coerceAtLeast(1)

    /** Angle de renvoi (radians, depuis la verticale) quand la balle touche la raquette à [offset] (-1 : bord gauche, 1 : bord droit). */
    fun bounceAngle(offset: Float): Float = (offset.coerceIn(-1f, 1f) * Math.toRadians(MAX_BOUNCE_DEG)).toFloat()

    internal fun serveAngle(random: Random): Float =
        (random.nextDouble(-MAX_SERVE_DEG, MAX_SERVE_DEG) * PI / 180.0).toFloat()

    internal fun minVertical(speed: Float): Float = speed * MIN_VERTICAL_SHARE
}

/**
 * Une partie de Casse-briques : une raquette en bas, une balle, et des rangées de briques dont le nombre grandit avec le niveau.
 * La classe est modifiable (la balle bouge à chaque image) : l'écran redessine après chaque appel.
 *
 * Tant que la balle est [attached], elle suit la raquette et attend [launch]. On a [Breakout.LIVES] vies : balle perdue
 * en bas, une vie de moins. Quand toutes les briques sont cassées, le niveau suivant arrive avec une balle plus rapide.
 */
internal class BreakoutGame(private val random: Random = Random.Default) {

    // 1 si la brique est encore là, sinon 0 ; rangée par rangée, la rangée 0 étant en haut. Sa taille dépend du niveau.
    private var bricks = IntArray(0)

    /** Le centre de la raquette. */
    var paddleX = Breakout.WIDTH / 2f
        private set

    var ballX = Breakout.WIDTH / 2f
        private set
    var ballY = Breakout.PADDLE_TOP - Breakout.BALL_RADIUS
        private set
    var ballVx = 0f
        private set
    var ballVy = 0f
        private set

    /** Vrai quand la balle est posée sur la raquette, en attente d'être lancée. */
    var attached = true
        private set

    var score = 0
        private set
    var lives = Breakout.LIVES
        private set
    var level = 1
        private set

    /** Vrai quand la dernière vie est perdue. */
    var isOver = false
        private set

    /** Nombre de briques encore debout. */
    var bricksLeft = 0
        private set

    // Les dimensions du terrain du niveau en cours (voir [Breakout.colsFor]).
    val cols: Int get() = Breakout.colsFor(level)
    val rows: Int get() = Breakout.rowsFor(level)
    val width: Float get() = Breakout.widthFor(level)
    val height: Float get() = Breakout.heightFor(level)
    val bricksTop: Float get() = Breakout.bricksTopFor(level)
    val paddleTop: Float get() = Breakout.paddleTopFor(level)

    init {
        reset()
    }

    /** Une nouvelle partie. */
    fun reset() {
        score = 0
        lives = Breakout.LIVES
        level = 1
        isOver = false
        paddleX = width / 2f
        fillBricks()
        serve()
    }

    /** Vrai s'il y a encore une brique à la colonne [col], rangée [row]. */
    fun hasBrick(col: Int, row: Int): Boolean =
        col in 0 until cols && row in 0 until rows && bricks[row * cols + col] != 0

    /** Déplace la raquette pour que son centre soit en [x] (elle reste dans le terrain) ; la balle posée dessus la suit. */
    fun movePaddleTo(x: Float) {
        if (isOver) return
        val half = Breakout.PADDLE_W / 2f
        paddleX = x.coerceIn(half, width - half)
        if (attached) placeBallOnPaddle()
    }

    /** Lance la balle (si elle attend sur la raquette) : vers le haut, avec un petit angle au hasard. */
    fun launch() {
        if (isOver || !attached) return
        attached = false
        val speed = Breakout.speedFor(level)
        val angle = Breakout.serveAngle(random)
        ballVx = speed * sin(angle)
        ballVy = -speed * cos(angle)
    }

    /** Fait avancer la partie de [elapsedMs] millisecondes (sans effet tant que la balle attend ou que la partie est finie). */
    fun tick(elapsedMs: Long) {
        if (isOver || attached || elapsedMs <= 0L) return
        val seconds = elapsedMs / 1000f
        val distance = hypot(ballVx, ballVy) * seconds
        val steps = max(1, ceil(distance / Breakout.MAX_STEP).toInt())
        val dt = seconds / steps
        var i = 0
        // On s'arrête dès que la balle est perdue ou qu'un niveau s'achève : elle est alors reposée sur la raquette.
        while (i < steps && !isOver && !attached) {
            step(dt)
            i++
        }
    }

    private fun step(dt: Float) {
        val r = Breakout.BALL_RADIUS
        ballX += ballVx * dt
        ballY += ballVy * dt

        // Les murs : gauche, droite et plafond.
        if (ballX < r) {
            ballX = r
            ballVx = abs(ballVx)
        } else if (ballX > width - r) {
            ballX = width - r
            ballVx = -abs(ballVx)
        }
        if (ballY < r) {
            ballY = r
            ballVy = abs(ballVy)
        }

        bouncePaddle()
        if (breakBrick()) return
        keepBallSteep()

        // Balle sortie par le bas : une vie de moins.
        if (ballY - r > height) {
            lives--
            if (lives <= 0) {
                lives = 0
                isOver = true
            } else {
                serve()
            }
        }
    }

    /** La raquette renvoie la balle qui descend vers elle, d'autant plus de côté qu'elle la touche loin du centre. */
    private fun bouncePaddle() {
        if (ballVy <= 0f) return
        val r = Breakout.BALL_RADIUS
        val half = Breakout.PADDLE_W / 2f
        val onPaddle = ballY + r >= paddleTop &&
            // Une balle déjà passée sous le milieu de la raquette est perdue : elle ne remonte pas.
            ballY <= paddleTop + Breakout.PADDLE_H / 2f &&
            ballX >= paddleX - half - r && ballX <= paddleX + half + r
        if (!onPaddle) return
        val speed = hypot(ballVx, ballVy)
        val angle = Breakout.bounceAngle((ballX - paddleX) / (half + r))
        ballVx = speed * sin(angle)
        ballVy = -speed * cos(angle)
        ballY = paddleTop - r
    }

    /**
     * Casse la brique que touche la balle, s'il y en a une, et la fait rebondir (de côté si elle l'a prise par le flanc,
     * sinon dans le sens vertical). Renvoie vrai quand le niveau est fini (la balle revient alors sur la raquette).
     */
    private fun breakBrick(): Boolean {
        val r = Breakout.BALL_RADIUS
        val firstCol = floor((ballX - r) / Breakout.BRICK_W).toInt().coerceAtLeast(0)
        val lastCol = floor((ballX + r) / Breakout.BRICK_W).toInt().coerceAtMost(cols - 1)
        val firstRow = floor((ballY - r - bricksTop) / Breakout.BRICK_H).toInt().coerceAtLeast(0)
        val lastRow = floor((ballY + r - bricksTop) / Breakout.BRICK_H).toInt().coerceAtMost(rows - 1)
        for (row in firstRow..lastRow) {
            for (col in firstCol..lastCol) {
                if (!hasBrick(col, row)) continue
                val left = col * Breakout.BRICK_W
                val right = left + Breakout.BRICK_W
                val top = bricksTop + row * Breakout.BRICK_H
                val bottom = top + Breakout.BRICK_H
                // Le point de la brique le plus proche du centre de la balle : trop loin, pas de contact.
                val nearX = ballX.coerceIn(left, right)
                val nearY = ballY.coerceIn(top, bottom)
                val dx = ballX - nearX
                val dy = ballY - nearY
                if (dx * dx + dy * dy >= r * r) continue

                bricks[row * cols + col] = 0
                bricksLeft--
                score += Breakout.brickPoints(row, level, rows)

                // L'enfoncement le plus faible dit par quel côté la balle est arrivée.
                val overlapX = min(ballX + r - left, right - (ballX - r))
                val overlapY = min(ballY + r - top, bottom - (ballY - r))
                if (overlapX < overlapY) {
                    ballVx = if (ballX < (left + right) / 2f) -abs(ballVx) else abs(ballVx)
                } else {
                    ballVy = if (ballY < (top + bottom) / 2f) -abs(ballVy) else abs(ballVy)
                }
                if (bricksLeft == 0) {
                    nextLevel()
                    return true
                }
                return false
            }
        }
        return false
    }

    /** Évite les trajectoires presque horizontales, où la balle mettrait une éternité à redescendre. */
    private fun keepBallSteep() {
        val speed = hypot(ballVx, ballVy)
        val minVy = Breakout.minVertical(speed)
        if (speed <= 0f || abs(ballVy) >= minVy) return
        val vy = if (ballVy >= 0f) minVy else -minVy
        val vx = sqrt(max(0f, speed * speed - vy * vy))
        ballVy = vy
        ballVx = if (ballVx >= 0f) vx else -vx
    }

    /** Le niveau suivant : un terrain plus large, plus de briques et une balle plus rapide. */
    private fun nextLevel() {
        level++
        paddleX = width / 2f
        fillBricks()
        serve()
    }

    /** Remplit le terrain du niveau en cours de briques. */
    private fun fillBricks() {
        bricks = IntArray(cols * rows) { 1 }
        bricksLeft = bricks.size
    }

    /** La balle revient sur la raquette, immobile. */
    private fun serve() {
        attached = true
        ballVx = 0f
        ballVy = 0f
        placeBallOnPaddle()
    }

    private fun placeBallOnPaddle() {
        ballX = paddleX
        ballY = paddleTop - Breakout.BALL_RADIUS
    }

    /** L'état de la partie sous forme d'une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Int> = buildList {
        add(score)
        add(lives)
        add(level)
        add(if (isOver) 1 else 0)
        add(if (attached) 1 else 0)
        add(paddleX.toBits())
        add(ballX.toBits())
        add(ballY.toBits())
        add(ballVx.toBits())
        add(ballVy.toBits())
        addAll(bricks.toList())
    }

    companion object {
        private const val HEADER = 10

        /** Une partie remise dans l'état de [data] (voir [save]), ou null si la liste est abîmée. */
        fun restore(data: List<Int>, random: Random = Random.Default): BreakoutGame? {
            if (data.size < HEADER || data[2] < 1) return null
            // Le nombre de briques dépend du niveau.
            val level = data[2]
            if (data.size != HEADER + Breakout.bricksFor(level)) return null
            val floats = (5 until HEADER).map { Float.fromBits(data[it]) }
            if (floats.any { !it.isFinite() }) return null
            val (px, bx, by, vx, vy) = floats
            val half = Breakout.PADDLE_W / 2f
            val width = Breakout.widthFor(level)
            if (px < half || px > width - half) return null
            if (bx < 0f || bx > width || by < 0f || by > Breakout.heightFor(level) + 1f) return null
            if (abs(vx) > Breakout.MAX_UNITS_SPEED * 2f || abs(vy) > Breakout.MAX_UNITS_SPEED * 2f) return null
            if (data[0] < 0 || data[1] !in 0..Breakout.LIVES) return null
            if (data[3] !in 0..1 || data[4] !in 0..1) return null
            val cells = data.subList(HEADER, data.size)
            if (cells.any { it != 0 && it != 1 }) return null
            val left = cells.count { it == 1 }
            // Une partie en cours a toujours des briques et au moins une vie ; une partie finie n'a plus de vie.
            if (left == 0 || (data[3] == 0) == (data[1] == 0)) return null

            return BreakoutGame(random).apply {
                score = data[0]
                lives = data[1]
                this.level = data[2]
                bricks = IntArray(cells.size)
                isOver = data[3] == 1
                attached = data[4] == 1
                paddleX = px
                ballX = bx
                ballY = by
                ballVx = vx
                ballVy = vy
                cells.forEachIndexed { index, value -> bricks[index] = value }
                bricksLeft = left
            }
        }
    }
}
