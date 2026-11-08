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

    /**
     * Nombre de cases de la grille de briques au niveau [level]. Dès le niveau 2 toutes les cases ne sont pas remplies
     * (voir [layoutFor]) : c'est le nombre maximal de briques, et la taille des sauvegardes.
     */
    fun bricksFor(level: Int): Int = colsFor(level) * rowsFor(level)

    /**
     * Une case de la grille vaut 0 (vide), 1 (brique) ou [CAPSULE_BASE] + le rang d'un [BreakoutBonus] (brique qui cache
     * une capsule de ce bonus, libérée quand on la casse).
     */
    const val CAPSULE_BASE = 10

    /** Part des briques qui cachent une capsule. */
    private const val CAPSULE_CHANCE = 0.16f

    /** Taille d'une capsule (unités) et vitesse de sa chute (unités par seconde). */
    const val CAPSULE_W = 0.8f
    const val CAPSULE_H = 0.4f
    const val CAPSULE_SPEED = 3.5f

    /** La raquette allongée ou raccourcie, par rapport à [PADDLE_W]. */
    const val EXPAND_FACTOR = 1.6f
    const val SHRINK_FACTOR = 0.55f

    /** Durée (millisecondes) de chaque bonus ou malus. */
    const val EXPAND_MS = 15_000
    const val SHRINK_MS = 12_000
    const val PIERCE_MS = 8_000
    const val LASER_MS = 12_000
    const val CATCH_MS = 15_000
    const val FAST_MS = 8_000

    /** Le malus d'accélération : la balle va 1,4 fois plus vite (et le plafond monte d'autant). */
    const val FAST_FACTOR = 1.4f

    /** Délai entre deux tirs de laser et vitesse d'un tir (unités par seconde). */
    const val LASER_INTERVAL_MS = 350
    const val BOLT_SPEED = 16f

    /** Nombre maximal de balles en jeu, et écart d'angle (radians) des balles ajoutées par la multi-balle. */
    const val MAX_BALLS = 9
    const val SPLIT_ANGLE = 0.35f

    /** Part des cases tirées au sort pour porter une brique, dès le niveau 2. */
    private const val FILL_CHANCE = 0.75f

    /** Part minimale de la grille qui doit rester remplie, une fois les briques isolées retirées. */
    private const val MIN_FILL = 0.45f

    /** Nombre d'essais avant de se rabattre sur la grille pleine. */
    private const val LAYOUT_TRIES = 30

    /**
     * La disposition des briques au niveau [level], rangée par rangée (1 : brique, 0 : vide), de taille [bricksFor].
     *
     * Le niveau 1 est la grille pleine. Dès le niveau 2, les briques sont tirées au hasard pour que chaque niveau ait
     * son allure, avec deux garanties :
     * - aucune brique n'est seule dans le vide : chacune touche une autre brique par un côté (gauche, droite, dessus
     *   ou dessous) ;
     * - aucune n'est posée trop bas : elles restent dans la grille, dont le bas est toujours très loin au-dessus de la
     *   raquette (voir [rowsFor], [bricksTopFor] et [paddleTopFor]).
     *
     * À tous les niveaux, quelques briques cachent une capsule de bonus ou de malus (voir [CAPSULE_BASE]).
     */
    fun layoutFor(level: Int, random: Random): IntArray {
        val cols = colsFor(level)
        val rows = rowsFor(level)
        val cells = if (level <= 1) IntArray(cols * rows) { 1 } else randomShape(cols, rows, random)
        hideCapsules(cells, random)
        return cells
    }

    /** Une forme au hasard, sans brique seule ; la grille pleine si les essais donnent trop peu de briques. */
    private fun randomShape(cols: Int, rows: Int, random: Random): IntArray {
        repeat(LAYOUT_TRIES) {
            val cells = IntArray(cols * rows) { if (random.nextFloat() < FILL_CHANCE) 1 else 0 }
            removeLoneBricks(cells, cols, rows)
            if (cells.count { it != 0 } >= MIN_FILL * cells.size) return cells
        }
        return IntArray(cols * rows) { 1 }
    }

    /** Cache une capsule, de bonus tiré au hasard, dans une partie des briques. */
    private fun hideCapsules(cells: IntArray, random: Random) {
        for (i in cells.indices) {
            if (cells[i] != 0 && random.nextFloat() < CAPSULE_CHANCE) cells[i] = CAPSULE_BASE + pickBonus(random).ordinal
        }
    }

    /** Un bonus au hasard : les malus sont moins fréquents que les bonus. */
    internal fun pickBonus(random: Random): BreakoutBonus {
        val all = BreakoutBonus.entries
        var roll = random.nextInt(all.sumOf { it.weight })
        for (bonus in all) {
            roll -= bonus.weight
            if (roll < 0) return bonus
        }
        return all.last()
    }

    /** Retire les briques sans aucune voisine par un côté. Retirer une brique seule n'en isole pas d'autre : un seul passage suffit. */
    internal fun removeLoneBricks(cells: IntArray, cols: Int, rows: Int) {
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                if (cells[row * cols + col] == 0) continue
                val hasNeighbour =
                    (col > 0 && cells[row * cols + col - 1] != 0) ||
                        (col < cols - 1 && cells[row * cols + col + 1] != 0) ||
                        (row > 0 && cells[(row - 1) * cols + col] != 0) ||
                        (row < rows - 1 && cells[(row + 1) * cols + col] != 0)
                if (!hasNeighbour) cells[row * cols + col] = 0
            }
        }
    }

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

/** Les capsules : chacune porte une lettre. Les trois premiers sont des bonus d'armement ou de contrôle, les deux derniers des malus. */
internal enum class BreakoutBonus(val letter: Char, val good: Boolean, val weight: Int) {
    /** Laser : la raquette tire sur les briques. */
    LASER('L', true, 20),

    /** Balle perforante : traverse les briques sans rebondir. */
    PIERCE('P', true, 20),

    /** Agrandissement de la raquette. */
    EXPAND('E', true, 30),

    /** Aimant : la balle reste collée à la raquette. */
    CATCH('C', true, 20),

    /** Multi-balle : chaque balle devient trois. */
    MULTI('M', true, 30),

    /** Malus : raquette raccourcie. */
    SHRINK('R', false, 15),

    /** Malus : balle accélérée. */
    FAST('V', false, 15)
}

/** Une balle. Collée à la raquette ([stuck]), elle la suit à [offset] du centre ; sinon elle vole à (vx, vy). */
internal class BreakoutBall(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var stuck: Boolean = false,
    var offset: Float = 0f
)

/** Une capsule qui tombe, centrée en (x, y). */
internal class BreakoutCapsule(var x: Float, var y: Float, val bonus: BreakoutBonus)

/** Un tir de laser qui monte, en (x, y). */
internal class BreakoutBolt(val x: Float, var y: Float)

/**
 * Une partie de Casse-briques : une raquette en bas, une ou plusieurs balles, et des rangées de briques dont le nombre grandit
 * avec le niveau. La classe est modifiable (les balles bougent à chaque image) : l'écran redessine après chaque appel.
 *
 * Tant que la balle est collée à la raquette ([attached]), elle la suit et attend [launch]. On a [Breakout.LIVES] vies :
 * dernière balle perdue en bas, une vie de moins. Quand toutes les briques sont cassées, le niveau suivant arrive avec une
 * balle plus rapide.
 *
 * Certaines briques cachent une capsule (voir [BreakoutBonus]) : libérée quand on casse la brique, elle tombe, et la
 * raquette qui l'attrape en reçoit l'effet pour un temps. Les effets et les capsules en l'air disparaissent quand une
 * vie est perdue ou qu'un niveau s'achève.
 */
internal class BreakoutGame(private val random: Random = Random.Default) {

    // Une case par brique, rangée par rangée, la rangée 0 étant en haut : 0 vide, 1 brique, sinon brique à capsule (voir
    // [Breakout.CAPSULE_BASE]). Sa taille dépend du niveau.
    private var bricks = IntArray(0)

    /** Les balles en jeu : toujours au moins une (la première est la « principale »). */
    private val balls = mutableListOf<BreakoutBall>()
    private val capsules = mutableListOf<BreakoutCapsule>()
    private val bolts = mutableListOf<BreakoutBolt>()
    private var laserCooldownMs = 0

    /** Le centre de la raquette. */
    var paddleX = Breakout.WIDTH / 2f
        private set

    // Les effets en cours : le temps qu'il leur reste, en millisecondes (0 : pas d'effet).
    var expandMs = 0
        private set
    var shrinkMs = 0
        private set
    var pierceMs = 0
        private set
    var laserMs = 0
        private set
    var catchMs = 0
        private set
    var fastMs = 0
        private set

    val ballX: Float get() = balls[0].x
    val ballY: Float get() = balls[0].y
    val ballVx: Float get() = balls[0].vx
    val ballVy: Float get() = balls[0].vy

    /** Toutes les balles en jeu, de quoi les dessiner. */
    val allBalls: List<BreakoutBall> get() = balls
    val fallingCapsules: List<BreakoutCapsule> get() = capsules
    val laserBolts: List<BreakoutBolt> get() = bolts

    /** Vrai quand toutes les balles sont collées à la raquette, en attente d'être lancées. */
    val attached: Boolean get() = balls.all { it.stuck }

    /** Vrai quand au moins une balle attend sur la raquette. */
    val hasStuckBall: Boolean get() = balls.any { it.stuck }

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

    /** La largeur de la raquette, avec l'effet d'agrandissement ou de rétrécissement en cours. */
    val paddleWidth: Float
        get() = Breakout.PADDLE_W * when {
            expandMs > 0 -> Breakout.EXPAND_FACTOR
            shrinkMs > 0 -> Breakout.SHRINK_FACTOR
            else -> 1f
        }

    /** Vrai quand la balle traverse les briques. */
    val isPiercing: Boolean get() = pierceMs > 0

    /** Vrai quand la raquette tire. */
    val hasLaser: Boolean get() = laserMs > 0

    /** Vrai quand la balle reste collée à la raquette. */
    val hasCatch: Boolean get() = catchMs > 0

    /** Les effets en cours, avec la part du temps qu'il leur reste (entre 0 et 1), pour les afficher. */
    val activeEffects: List<Pair<BreakoutBonus, Float>>
        get() = buildList {
            if (laserMs > 0) add(BreakoutBonus.LASER to laserMs / Breakout.LASER_MS.toFloat())
            if (pierceMs > 0) add(BreakoutBonus.PIERCE to pierceMs / Breakout.PIERCE_MS.toFloat())
            if (expandMs > 0) add(BreakoutBonus.EXPAND to expandMs / Breakout.EXPAND_MS.toFloat())
            if (catchMs > 0) add(BreakoutBonus.CATCH to catchMs / Breakout.CATCH_MS.toFloat())
            if (shrinkMs > 0) add(BreakoutBonus.SHRINK to shrinkMs / Breakout.SHRINK_MS.toFloat())
            if (fastMs > 0) add(BreakoutBonus.FAST to fastMs / Breakout.FAST_MS.toFloat())
        }

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

    /** Déplace la raquette pour que son centre soit en [x] (elle reste dans le terrain) ; les balles collées dessus la suivent. */
    fun movePaddleTo(x: Float) {
        if (isOver) return
        val half = paddleWidth / 2f
        paddleX = x.coerceIn(half, width - half)
        carryStuckBalls()
    }

    /**
     * Lance les balles qui attendent sur la raquette : vers le haut, avec un petit angle au hasard pour une balle posée au
     * centre, et d'autant plus de côté qu'elle est collée loin du centre (on vise en déplaçant la raquette).
     */
    fun launch() {
        if (isOver) return
        val half = paddleWidth / 2f
        val speed = currentSpeed()
        for (b in balls) {
            if (!b.stuck) continue
            b.stuck = false
            val angle = if (abs(b.offset) < 0.0001f) {
                Breakout.serveAngle(random)
            } else {
                Breakout.bounceAngle(b.offset / (half + Breakout.BALL_RADIUS))
            }
            b.vx = speed * sin(angle)
            b.vy = -speed * cos(angle)
            b.offset = 0f
        }
    }

    /** Fait avancer la partie de [elapsedMs] millisecondes (sans effet quand la partie est finie). */
    fun tick(elapsedMs: Long) {
        if (isOver || elapsedMs <= 0L) return
        advanceEffects(elapsedMs.coerceAtMost(60_000L).toInt())
        val seconds = elapsedMs / 1000f
        moveCapsules(seconds)
        if (moveBolts(elapsedMs.coerceAtMost(60_000L).toInt(), seconds)) return
        moveBalls(seconds)
    }

    /** Fait baisser le temps des effets ; un effet qui finit remet la raquette ou la vitesse comme avant. */
    private fun advanceEffects(ms: Int) {
        val hadFast = fastMs > 0
        val hadWidth = expandMs > 0 || shrinkMs > 0
        expandMs = max(0, expandMs - ms)
        shrinkMs = max(0, shrinkMs - ms)
        pierceMs = max(0, pierceMs - ms)
        laserMs = max(0, laserMs - ms)
        catchMs = max(0, catchMs - ms)
        fastMs = max(0, fastMs - ms)
        if (hadFast && fastMs == 0) applySpeed()
        if (hadWidth && expandMs == 0 && shrinkMs == 0) refreshPaddle()
    }

    /** Les capsules tombent ; celle que la raquette touche donne son effet, celle qui passe dessous est perdue. */
    private fun moveCapsules(seconds: Float) {
        if (capsules.isEmpty()) return
        val half = paddleWidth / 2f
        val iterator = capsules.iterator()
        while (iterator.hasNext()) {
            val c = iterator.next()
            c.y += Breakout.CAPSULE_SPEED * seconds
            val caught = c.y + Breakout.CAPSULE_H / 2f >= paddleTop &&
                c.y - Breakout.CAPSULE_H / 2f <= paddleTop + Breakout.PADDLE_H &&
                abs(c.x - paddleX) <= half + Breakout.CAPSULE_W / 2f
            if (caught) {
                iterator.remove()
                activate(c.bonus)
            } else if (c.y - Breakout.CAPSULE_H / 2f > height) {
                iterator.remove()
            }
        }
    }

    /** Le laser tire de lui-même tant qu'il dure ; un tir casse la première brique qu'il touche. Renvoie vrai si le niveau est fini. */
    private fun moveBolts(ms: Int, seconds: Float): Boolean {
        if (laserMs > 0) {
            laserCooldownMs -= ms
            if (laserCooldownMs <= 0) {
                val half = paddleWidth / 2f
                bolts.add(BreakoutBolt(paddleX - half + 0.15f, paddleTop))
                bolts.add(BreakoutBolt(paddleX + half - 0.15f, paddleTop))
                laserCooldownMs = Breakout.LASER_INTERVAL_MS
            }
        }
        if (bolts.isEmpty()) return false
        val distance = Breakout.BOLT_SPEED * seconds
        val steps = max(1, ceil(distance / BOLT_STEP).toInt())
        val d = distance / steps
        for (bolt in bolts.toList()) {
            var alive = true
            var i = 0
            while (i < steps && alive) {
                bolt.y -= d
                if (bolt.y < 0f) {
                    alive = false
                } else {
                    val col = floor(bolt.x / Breakout.BRICK_W).toInt()
                    val row = floor((bolt.y - bricksTop) / Breakout.BRICK_H).toInt()
                    if (hasBrick(col, row)) {
                        alive = false
                        bolts.remove(bolt)
                        if (destroyBrick(col, row)) return true
                    }
                }
                i++
            }
            if (!alive) bolts.remove(bolt)
        }
        return false
    }

    private fun moveBalls(seconds: Float) {
        val flying = balls.filter { !it.stuck }
        if (flying.isEmpty()) return
        val fastest = flying.maxOf { hypot(it.vx, it.vy) }
        val steps = max(1, ceil(fastest * seconds / Breakout.MAX_STEP).toInt())
        val dt = seconds / steps
        var i = 0
        // On s'arrête dès qu'une vie est perdue ou qu'un niveau s'achève : la balle est alors reposée sur la raquette.
        while (i < steps && !isOver && !attached) {
            var restarted = false
            for (b in balls.toList()) {
                if (b.stuck) continue
                if (stepBall(b, dt)) {
                    restarted = true
                    break
                }
            }
            if (restarted) break
            i++
        }
    }

    /** Fait avancer une balle d'un pas. Renvoie vrai quand la partie repart sur la raquette (vie perdue, niveau fini, partie finie). */
    private fun stepBall(b: BreakoutBall, dt: Float): Boolean {
        val r = Breakout.BALL_RADIUS
        b.x += b.vx * dt
        b.y += b.vy * dt

        // Les murs : gauche, droite et plafond.
        if (b.x < r) {
            b.x = r
            b.vx = abs(b.vx)
        } else if (b.x > width - r) {
            b.x = width - r
            b.vx = -abs(b.vx)
        }
        if (b.y < r) {
            b.y = r
            b.vy = abs(b.vy)
        }

        bouncePaddle(b)
        if (b.stuck) return false
        if (breakBricks(b)) return true
        keepBallSteep(b)

        // Balle sortie par le bas : perdue. La dernière balle perdue coûte une vie.
        if (b.y - r > height) {
            if (balls.size > 1) {
                balls.remove(b)
                return false
            }
            lives--
            if (lives <= 0) {
                lives = 0
                isOver = true
            } else {
                serve()
            }
            return true
        }
        return false
    }

    /**
     * La raquette renvoie la balle qui descend vers elle, d'autant plus de côté qu'elle la touche loin du centre. Avec
     * l'aimant, la balle reste collée à l'endroit touché.
     */
    private fun bouncePaddle(b: BreakoutBall) {
        if (b.vy <= 0f) return
        val r = Breakout.BALL_RADIUS
        val half = paddleWidth / 2f
        val onPaddle = b.y + r >= paddleTop &&
            // Une balle déjà passée sous le milieu de la raquette est perdue : elle ne remonte pas.
            b.y <= paddleTop + Breakout.PADDLE_H / 2f &&
            b.x >= paddleX - half - r && b.x <= paddleX + half + r
        if (!onPaddle) return
        b.y = paddleTop - r
        if (catchMs > 0) {
            b.stuck = true
            b.offset = (b.x - paddleX).coerceIn(-half, half)
            b.vx = 0f
            b.vy = 0f
            return
        }
        val speed = hypot(b.vx, b.vy)
        val angle = Breakout.bounceAngle((b.x - paddleX) / (half + r))
        b.vx = speed * sin(angle)
        b.vy = -speed * cos(angle)
    }

    /**
     * Casse la brique que touche la balle, s'il y en a une, et la fait rebondir (de côté si elle l'a prise par le flanc,
     * sinon dans le sens vertical). Une balle perforante casse toutes celles qu'elle touche et ne rebondit pas. Renvoie
     * vrai quand le niveau est fini (la balle revient alors sur la raquette).
     */
    private fun breakBricks(b: BreakoutBall): Boolean {
        val r = Breakout.BALL_RADIUS
        val pierce = pierceMs > 0
        val firstCol = floor((b.x - r) / Breakout.BRICK_W).toInt().coerceAtLeast(0)
        val lastCol = floor((b.x + r) / Breakout.BRICK_W).toInt().coerceAtMost(cols - 1)
        val firstRow = floor((b.y - r - bricksTop) / Breakout.BRICK_H).toInt().coerceAtLeast(0)
        val lastRow = floor((b.y + r - bricksTop) / Breakout.BRICK_H).toInt().coerceAtMost(rows - 1)
        for (row in firstRow..lastRow) {
            for (col in firstCol..lastCol) {
                if (!hasBrick(col, row)) continue
                val left = col * Breakout.BRICK_W
                val right = left + Breakout.BRICK_W
                val top = bricksTop + row * Breakout.BRICK_H
                val bottom = top + Breakout.BRICK_H
                // Le point de la brique le plus proche du centre de la balle : trop loin, pas de contact.
                val nearX = b.x.coerceIn(left, right)
                val nearY = b.y.coerceIn(top, bottom)
                val dx = b.x - nearX
                val dy = b.y - nearY
                if (dx * dx + dy * dy >= r * r) continue

                if (!pierce) {
                    // L'enfoncement le plus faible dit par quel côté la balle est arrivée.
                    val overlapX = min(b.x + r - left, right - (b.x - r))
                    val overlapY = min(b.y + r - top, bottom - (b.y - r))
                    if (overlapX < overlapY) {
                        b.vx = if (b.x < (left + right) / 2f) -abs(b.vx) else abs(b.vx)
                    } else {
                        b.vy = if (b.y < (top + bottom) / 2f) -abs(b.vy) else abs(b.vy)
                    }
                }
                if (destroyBrick(col, row)) return true
                if (!pierce) return false
            }
        }
        return false
    }

    /** Casse la brique (col, row) : points, et capsule libérée si elle en cachait une. Renvoie vrai quand c'était la dernière (niveau suivant). */
    private fun destroyBrick(col: Int, row: Int): Boolean {
        val index = row * cols + col
        val code = bricks[index]
        bricks[index] = 0
        bricksLeft--
        score += Breakout.brickPoints(row, level, rows)
        val bonus = BreakoutBonus.entries.getOrNull(code - Breakout.CAPSULE_BASE)
        if (code >= Breakout.CAPSULE_BASE && bonus != null) {
            capsules.add(BreakoutCapsule(col + 0.5f, bricksTop + (row + 0.5f) * Breakout.BRICK_H, bonus))
        }
        if (bricksLeft == 0) {
            nextLevel()
            return true
        }
        return false
    }

    /** Donne l'effet d'une capsule attrapée. */
    private fun activate(bonus: BreakoutBonus) {
        when (bonus) {
            BreakoutBonus.LASER -> laserMs = Breakout.LASER_MS
            BreakoutBonus.PIERCE -> pierceMs = Breakout.PIERCE_MS
            BreakoutBonus.EXPAND -> {
                expandMs = Breakout.EXPAND_MS
                shrinkMs = 0
                refreshPaddle()
            }
            BreakoutBonus.CATCH -> catchMs = Breakout.CATCH_MS
            BreakoutBonus.MULTI -> split()
            BreakoutBonus.SHRINK -> {
                shrinkMs = Breakout.SHRINK_MS
                expandMs = 0
                refreshPaddle()
            }
            BreakoutBonus.FAST -> {
                fastMs = Breakout.FAST_MS
                applySpeed()
            }
        }
    }

    /** La multi-balle : chaque balle en donne deux autres, partant de part et d'autre (jusqu'à [Breakout.MAX_BALLS]). Les balles collées sont lancées. */
    private fun split() {
        launch()
        for (b in balls.toList()) {
            for (sign in intArrayOf(-1, 1)) {
                if (balls.size >= Breakout.MAX_BALLS) return
                val a = sign * Breakout.SPLIT_ANGLE
                val c = cos(a)
                val s = sin(a)
                balls.add(BreakoutBall(b.x, b.y, b.vx * c - b.vy * s, b.vx * s + b.vy * c))
            }
        }
    }

    /** La vitesse que doivent avoir les balles : celle du niveau, et plus vite encore sous le malus d'accélération. */
    private fun currentSpeed(): Float =
        if (fastMs > 0) min(Breakout.MAX_UNITS_SPEED * Breakout.FAST_FACTOR, Breakout.speedFor(level) * Breakout.FAST_FACTOR)
        else Breakout.speedFor(level)

    /** Remet toutes les balles en vol à la vitesse voulue, sans changer leur direction. */
    private fun applySpeed() {
        val target = currentSpeed()
        for (b in balls) {
            if (b.stuck) continue
            val speed = hypot(b.vx, b.vy)
            if (speed <= 0f) continue
            b.vx *= target / speed
            b.vy *= target / speed
        }
    }

    /** La raquette a changé de largeur : elle reste dans le terrain, et les balles collées dessus aussi. */
    private fun refreshPaddle() {
        val half = paddleWidth / 2f
        paddleX = paddleX.coerceIn(half, width - half)
        for (b in balls) b.offset = b.offset.coerceIn(-half, half)
        carryStuckBalls()
    }

    private fun carryStuckBalls() {
        val r = Breakout.BALL_RADIUS
        for (b in balls) {
            if (!b.stuck) continue
            b.x = (paddleX + b.offset).coerceIn(r, width - r)
            b.y = paddleTop - r
        }
    }

    /** Évite les trajectoires presque horizontales, où la balle mettrait une éternité à redescendre. */
    private fun keepBallSteep(b: BreakoutBall) {
        val speed = hypot(b.vx, b.vy)
        val minVy = Breakout.minVertical(speed)
        if (speed <= 0f || abs(b.vy) >= minVy) return
        val vy = if (b.vy >= 0f) minVy else -minVy
        val vx = sqrt(max(0f, speed * speed - vy * vy))
        b.vy = vy
        b.vx = if (b.vx >= 0f) vx else -vx
    }

    /** Le niveau suivant : un terrain plus large, plus de briques (disposées au hasard) et une balle plus rapide. */
    private fun nextLevel() {
        level++
        paddleX = width / 2f
        fillBricks()
        serve()
    }

    /** Dispose les briques du niveau en cours : la grille pleine au niveau 1, au hasard ensuite (voir [Breakout.layoutFor]). */
    private fun fillBricks() {
        bricks = Breakout.layoutFor(level, random)
        bricksLeft = bricks.count { it != 0 }
    }

    /** Une seule balle revient sur la raquette, immobile ; les effets, les capsules et les tirs en l'air disparaissent. */
    private fun serve() {
        expandMs = 0
        shrinkMs = 0
        pierceMs = 0
        laserMs = 0
        catchMs = 0
        fastMs = 0
        laserCooldownMs = 0
        capsules.clear()
        bolts.clear()
        balls.clear()
        balls.add(BreakoutBall(paddleX, paddleTop - Breakout.BALL_RADIUS, stuck = true))
    }

    /**
     * L'état de la partie sous forme d'une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]) : d'abord
     * l'en-tête (la balle principale), puis les briques, puis les effets, les autres balles et les capsules. Les tirs de
     * laser, éphémères, ne sont pas gardés.
     */
    fun save(): List<Int> = buildList {
        val main = balls[0]
        add(score)
        add(lives)
        add(level)
        add(if (isOver) 1 else 0)
        add(if (main.stuck) 1 else 0)
        add(paddleX.toBits())
        add(main.x.toBits())
        add(main.y.toBits())
        add(main.vx.toBits())
        add(main.vy.toBits())
        addAll(bricks.toList())
        add(expandMs)
        add(shrinkMs)
        add(pierceMs)
        add(laserMs)
        add(catchMs)
        add(fastMs)
        add(main.offset.toBits())
        add(balls.size - 1)
        for (b in balls.drop(1)) {
            add(b.x.toBits())
            add(b.y.toBits())
            add(b.vx.toBits())
            add(b.vy.toBits())
            add(if (b.stuck) 1 else 0)
            add(b.offset.toBits())
        }
        add(capsules.size)
        for (c in capsules) {
            add(c.x.toBits())
            add(c.y.toBits())
            add(c.bonus.ordinal)
        }
    }

    companion object {
        private const val HEADER = 10

        /** Plus court pas d'un tir de laser, pour qu'il ne saute pas par-dessus une brique. */
        private const val BOLT_STEP = 0.2f

        /** Une partie remise dans l'état de [data] (voir [save]), ou null si la liste est abîmée. */
        fun restore(data: List<Int>, random: Random = Random.Default): BreakoutGame? {
            if (data.size < HEADER || data[2] < 1) return null
            // Le nombre de cases dépend du niveau.
            val level = data[2]
            val base = HEADER + Breakout.bricksFor(level)
            if (data.size < base) return null
            val floats = (5 until HEADER).map { Float.fromBits(data[it]) }
            if (floats.any { !it.isFinite() }) return null
            val (px, bx, by, vx, vy) = floats
            // La plus petite demi-largeur possible : celle de la raquette rétrécie.
            val half = Breakout.PADDLE_W * Breakout.SHRINK_FACTOR / 2f
            val width = Breakout.widthFor(level)
            val height = Breakout.heightFor(level)
            val speedLimit = Breakout.MAX_UNITS_SPEED * Breakout.FAST_FACTOR * 2f
            if (px < half || px > width - half) return null
            if (bx < 0f || bx > width || by < 0f || by > height + 1f) return null
            if (abs(vx) > speedLimit || abs(vy) > speedLimit) return null
            if (data[0] < 0 || data[1] !in 0..Breakout.LIVES) return null
            if (data[3] !in 0..1 || data[4] !in 0..1) return null
            val cells = data.subList(HEADER, base)
            val validCodes = Breakout.CAPSULE_BASE until Breakout.CAPSULE_BASE + BreakoutBonus.entries.size
            if (cells.any { it != 0 && it != 1 && it !in validCodes }) return null
            val left = cells.count { it != 0 }
            // Une partie en cours a toujours des briques et au moins une vie ; une partie finie n'a plus de vie.
            if (left == 0 || (data[3] == 0) == (data[1] == 0)) return null

            // La suite (effets, autres balles, capsules) : absente d'une ancienne sauvegarde, qui n'avait que l'en-tête et les briques.
            var cursor = base
            fun next(): Int? = data.getOrNull(cursor++)
            val timers = IntArray(6)
            var mainOffset = 0f
            val extraBalls = mutableListOf<BreakoutBall>()
            val fallen = mutableListOf<BreakoutCapsule>()
            if (data.size > base) {
                val limits = intArrayOf(
                    Breakout.EXPAND_MS, Breakout.SHRINK_MS, Breakout.PIERCE_MS,
                    Breakout.LASER_MS, Breakout.CATCH_MS, Breakout.FAST_MS
                )
                for (i in timers.indices) {
                    val t = next() ?: return null
                    if (t !in 0..limits[i]) return null
                    timers[i] = t
                }
                val maxOffset = Breakout.PADDLE_W * Breakout.EXPAND_FACTOR / 2f
                mainOffset = Float.fromBits(next() ?: return null)
                if (!mainOffset.isFinite() || abs(mainOffset) > maxOffset) return null
                val extraCount = next() ?: return null
                if (extraCount !in 0 until Breakout.MAX_BALLS) return null
                repeat(extraCount) {
                    val values = IntArray(6)
                    for (k in values.indices) values[k] = next() ?: return null
                    val f = floatArrayOf(
                        Float.fromBits(values[0]), Float.fromBits(values[1]), Float.fromBits(values[2]),
                        Float.fromBits(values[3]), Float.fromBits(values[5])
                    )
                    if (f.any { !it.isFinite() }) return null
                    if (f[0] < 0f || f[0] > width || f[1] < 0f || f[1] > height + 1f) return null
                    if (abs(f[2]) > speedLimit || abs(f[3]) > speedLimit || abs(f[4]) > maxOffset) return null
                    if (values[4] !in 0..1) return null
                    extraBalls.add(BreakoutBall(f[0], f[1], f[2], f[3], values[4] == 1, f[4]))
                }
                val capsuleCount = next() ?: return null
                if (capsuleCount !in 0..MAX_SAVED_CAPSULES) return null
                repeat(capsuleCount) {
                    val cx = Float.fromBits(next() ?: return null)
                    val cy = Float.fromBits(next() ?: return null)
                    val kind = next() ?: return null
                    if (!cx.isFinite() || !cy.isFinite() || cx < 0f || cx > width || cy < 0f || cy > height + 1f) return null
                    val bonus = BreakoutBonus.entries.getOrNull(kind) ?: return null
                    fallen.add(BreakoutCapsule(cx, cy, bonus))
                }
                if (cursor != data.size) return null
            }

            return BreakoutGame(random).apply {
                score = data[0]
                lives = data[1]
                this.level = data[2]
                bricks = IntArray(cells.size)
                isOver = data[3] == 1
                paddleX = px
                cells.forEachIndexed { index, value -> bricks[index] = value }
                bricksLeft = left
                expandMs = timers[0]
                shrinkMs = timers[1]
                pierceMs = timers[2]
                laserMs = timers[3]
                catchMs = timers[4]
                fastMs = timers[5]
                balls.clear()
                balls.add(BreakoutBall(bx, by, vx, vy, data[4] == 1, mainOffset))
                balls.addAll(extraBalls)
                capsules.clear()
                capsules.addAll(fallen)
                bolts.clear()
            }
        }

        /** Plus grand nombre de capsules en l'air qu'une sauvegarde peut contenir (bien plus que ce qu'une partie peut en avoir). */
        private const val MAX_SAVED_CAPSULES = 500
    }
}
