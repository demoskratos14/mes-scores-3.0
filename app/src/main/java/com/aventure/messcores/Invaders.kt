package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal const val INVADERS_ID = "builtin_invaders"

/**
 * Réglages et calculs des Envahisseurs. Le terrain est en « unités » (9 de large, 14 de haut, le haut en y = 0). Les envahisseurs
 * forment une grille de [ROWS] rangées de [COLS] colonnes qui va d'un bord à l'autre et descend d'un cran à chaque bord touché,
 * de plus en plus vite quand il en reste peu. Le vaisseau du joueur glisse en bas, derrière quatre abris faits de petites cases
 * que les tirs, des deux camps, grignotent.
 */
internal object Invaders {
    const val WIDTH = 9f
    const val HEIGHT = 14f

    // La grille d'envahisseurs : [SPACING_X] et [SPACING_Y] séparent les centres, [SPRITE] est le côté de l'image dessinée et
    // [RADIUS] le rayon qui compte pour les tirs.
    const val COLS = 6
    const val ROWS = 5
    const val SPACING_X = 1.1f
    const val SPACING_Y = 0.95f
    const val SPRITE = 0.8f
    const val RADIUS = 0.34f

    /** Distance que la grille garde avec les bords du terrain avant de faire demi-tour. */
    const val MARGIN = 0.35f

    /** De combien la grille descend à chaque demi-tour. */
    const val DROP = 0.45f
    const val START_Y = 2f

    // Le vaisseau du joueur.
    const val PLAYER_Y = 12.6f
    const val PLAYER_SPRITE = 1f
    const val PLAYER_RADIUS = 0.32f
    const val PLAYER_SPEED = 6.5f
    const val PLAYER_MARGIN = 0.55f
    const val LIVES = 3
    const val MAX_LIVES = 9

    /** Une vie de plus tous les 2 000 points. */
    const val EXTRA_LIFE_EVERY = 2000
    const val INVULNERABLE_MS = 2000
    const val RESPAWN_DELAY_MS = 1200
    const val GAME_OVER_DELAY_MS = 1500
    const val LEVEL_DELAY_MS = 1500

    // Les tirs.
    const val SHOT_SPEED = 11f
    const val SHOT_RADIUS = 0.07f
    const val FIRE_COOLDOWN_MS = 350
    const val MAX_PLAYER_SHOTS = 2

    // Les abris : [BUNKERS] abris de [BUNKER_COLS] x [BUNKER_ROWS] cases de côté [BUNKER_CELL], le haut à [BUNKER_Y].
    const val BUNKERS = 4
    const val BUNKER_COLS = 6
    const val BUNKER_ROWS = 4
    const val BUNKER_CELL = 0.2f
    const val BUNKER_Y = 10.4f

    // Le vaisseau bonus qui traverse le haut du terrain de temps en temps.
    const val BONUS_Y = 1f
    const val BONUS_SPEED = 2.4f
    const val BONUS_RADIUS = 0.4f
    const val BONUS_SIZE = 0.9f

    /** Le numéro de son image dans la planche des ennemis d'Astéroïdes (voir [Asteroids.ATLAS_COLS]). */
    const val BONUS_TYPE = 10
    const val BONUS_MIN_DELAY_MS = 14_000
    const val BONUS_EXTRA_DELAY_MS = 10_000

    /** Il n'y a de bonus que s'il reste au moins ce nombre d'envahisseurs. */
    const val BONUS_MIN_INVADERS = 8
    val BONUS_POINTS = intArrayOf(50, 100, 150, 300)

    /** Plus long pas de simulation : un tir avance de moins d'une case d'abri par pas, il ne la saute donc jamais. */
    const val STEP_MS = 16

    /** Les points d'une rangée, de la plus haute à la plus basse : les envahisseurs du haut sont les plus lointains. */
    val ROW_POINTS = intArrayOf(30, 20, 20, 10, 10)

    /**
     * Les images des rangées (du haut vers le bas) pour chaque vague, des numéros de la planche des ennemis d'Astéroïdes
     * (voir [Asteroids.ENEMIES]) : le niveau n utilise la vague (n - 1) modulo le nombre de vagues, et chaque vague change de têtes.
     */
    val WAVES: List<IntArray> = listOf(
        intArrayOf(11, 4, 3, 1, 0),
        intArrayOf(14, 5, 6, 2, 1),
        intArrayOf(13, 7, 8, 3, 2),
        intArrayOf(12, 9, 10, 4, 0),
        intArrayOf(15, 14, 5, 6, 1),
        intArrayOf(16, 13, 10, 7, 3)
    )

    /** L'image de la rangée [row] au niveau [level] (un numéro de la planche des ennemis). */
    fun typeAt(level: Int, row: Int): Int {
        val wave = WAVES[(level.coerceAtLeast(1) - 1) % WAVES.size]
        return wave[row.coerceIn(0, ROWS - 1)]
    }

    /** Le x du centre de la colonne [col] quand la grille est centrée au niveau de départ. */
    fun centeredOriginX(): Float = WIDTH / 2f - (COLS - 1) * SPACING_X / 2f

    /** Le y du centre de la première rangée au niveau [level] : la grille commence un peu plus bas à chaque niveau (5 crans au plus). */
    fun startY(level: Int): Float = START_Y + 0.35f * (level.coerceAtLeast(1) - 1).coerceAtMost(5)

    /** Facteur de vitesse du niveau : +10 % par niveau, doublée au plus. */
    fun speedFactor(level: Int): Float = (1f + 0.1f * (level.coerceAtLeast(1) - 1)).coerceAtMost(2f)

    /** La vitesse de la grille avec [alive] envahisseurs au niveau [level] : elle accélère à mesure qu'ils tombent. */
    fun formationSpeed(level: Int, alive: Int): Float {
        val ratio = 1f - alive.coerceIn(0, ROWS * COLS).toFloat() / (ROWS * COLS)
        return 0.9f * speedFactor(level) * (1f + 2.6f * ratio)
    }

    /** Le temps minimal entre deux tirs des envahisseurs au niveau [level] ; il s'y ajoute un peu de hasard. */
    fun shotDelayMs(level: Int): Int = (900 - 60 * (level.coerceAtLeast(1) - 1)).coerceAtLeast(350)

    /** Le nombre de tirs ennemis en l'air au plus au niveau [level]. */
    fun maxEnemyShots(level: Int): Int = (2 + level.coerceAtLeast(1) / 3).coerceAtMost(5)

    fun enemyShotSpeed(level: Int): Float = (4.6f + 0.2f * (level.coerceAtLeast(1) - 1)).coerceAtMost(7f)

    /** Le x du bord gauche de l'abri numéro [bunker]. */
    fun bunkerLeft(bunker: Int): Float = WIDTH / BUNKERS * (bunker + 0.5f) - BUNKER_COLS * BUNKER_CELL / 2f

    /** Vrai si la case ([row], [col]) d'un abri neuf existe : un abri a les coins du haut coupés et une arche en bas. */
    fun bunkerShape(row: Int, col: Int): Boolean {
        if (row == 0 && (col == 0 || col == BUNKER_COLS - 1)) return false
        if (row == BUNKER_ROWS - 1 && col in 2..3) return false
        return true
    }

    /** L'indice de la case ([row], [col]) de l'abri [bunker] dans la liste des cases. */
    fun cellIndex(bunker: Int, row: Int, col: Int): Int = (bunker * BUNKER_ROWS + row) * BUNKER_COLS + col

    fun bonusDelay(random: Random): Int = BONUS_MIN_DELAY_MS + random.nextInt(BONUS_EXTRA_DELAY_MS)
}

/** Un tir : celui du joueur monte ([vy] négatif), celui d'un envahisseur ([hostile]) descend. Il disparaît en sortant du terrain. */
internal class InvaderShot(
    var x: Float,
    var y: Float,
    val vy: Float,
    val hostile: Boolean
)

/** Le vaisseau bonus : il traverse le haut du terrain à la vitesse [vx] et vaut [points] quand on le touche. */
internal class InvaderBonus(
    var x: Float,
    val vx: Float,
    val points: Int
)

/** Une étincelle d'explosion (décor, non gardé par [InvadersGame.save]) ; [kind] : 0 envahisseur, 1 vaisseau, 2 bonus. */
internal class InvaderSpark(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var lifeMs: Int,
    val totalMs: Int,
    val kind: Int
)

/**
 * Une partie d'Envahisseurs : on déplace son vaisseau (commandes [moveLeft], [moveRight]) et on tire ([fire], un tir toutes les
 * [Invaders.FIRE_COOLDOWN_MS], deux au plus en l'air) pour abattre toute la grille avant qu'elle n'arrive en bas. Les envahisseurs
 * tirent à leur tour, de préférence sur la colonne du joueur. Trois vies ; vaisseau touché, le suivant revient au centre,
 * protégé un moment, et la grille attend. Une grille vidée amène la suivante, plus basse et plus rapide, avec des abris neufs.
 */
internal class InvadersGame(private val random: Random = Random.Default) {

    private val aliveFlags = BooleanArray(Invaders.ROWS * Invaders.COLS)
    private val bunkerCells = BooleanArray(Invaders.BUNKERS * Invaders.BUNKER_ROWS * Invaders.BUNKER_COLS)
    private val shotList = mutableListOf<InvaderShot>()
    private val sparkList = mutableListOf<InvaderSpark>()

    val shots: List<InvaderShot> get() = shotList
    val sparks: List<InvaderSpark> get() = sparkList

    /** Le vaisseau bonus en vol, s'il y en a un. */
    var bonus: InvaderBonus? = null
        private set

    // Les commandes, tenues par l'écran.
    var moveLeft = false
    var moveRight = false
    var fire = false

    var playerX = Invaders.WIDTH / 2f
        private set

    /** Faux entre la destruction du vaisseau et son retour. */
    var playerAlive = true
        private set

    /** Temps de protection restant après un retour, en millisecondes. */
    var invulnerableMs = 0
        private set

    /** Le x du centre de la première colonne et le y du centre de la première rangée : la grille se déplace d'un bloc. */
    var originX = 0f
        private set
    var originY = 0f
        private set

    /** Le sens de la grille : 1 vers la droite, -1 vers la gauche. */
    var direction = 1
        private set

    /** Le nombre d'envahisseurs encore en vie. */
    var aliveCount = 0
        private set

    var score = 0
        private set
    var lives = Invaders.LIVES
        private set
    var level = 1
        private set

    /** Vrai quand la dernière vie est perdue et que l'explosion a eu le temps de se voir. */
    var isOver = false
        private set

    /** Temps restant avant la grille suivante (0 : la grille est en cours). */
    var levelDelayMs = 0
        private set

    /** Horloge du décor (animation des images), en millisecondes ; elle repart de zéro après une heure. */
    var clockMs = 0
        private set

    private var respawnMs = 0
    private var overMs = 0
    private var shotTimerMs = 0
    private var bonusTimerMs = 0
    private var fireCooldownMs = 0
    private var nextExtraLife = Invaders.EXTRA_LIFE_EVERY

    init {
        reset()
    }

    /** Vrai entre deux grilles : l'écran peut annoncer la suivante. */
    val isBetweenLevels: Boolean get() = levelDelayMs > 0

    /** Vrai si l'envahisseur de la rangée [row] et de la colonne [col] est en vie. */
    fun isAlive(row: Int, col: Int): Boolean = aliveFlags[row * Invaders.COLS + col]

    /** Le x du centre de la colonne [col] (la grille bouge : il dépend de [originX]). */
    fun invaderX(col: Int): Float = originX + col * Invaders.SPACING_X

    /** Le y du centre de la rangée [row]. */
    fun invaderY(row: Int): Float = originY + row * Invaders.SPACING_Y

    /** Vrai si la case ([row], [col]) de l'abri [bunker] est encore là. */
    fun isBunkerCell(bunker: Int, row: Int, col: Int): Boolean = bunkerCells[Invaders.cellIndex(bunker, row, col)]

    /** Repart de zéro : score nul, trois vies, première grille. */
    fun reset() {
        score = 0
        lives = Invaders.LIVES
        level = 1
        isOver = false
        levelDelayMs = 0
        respawnMs = 0
        overMs = 0
        fireCooldownMs = 0
        clockMs = 0
        nextExtraLife = Invaders.EXTRA_LIFE_EVERY
        shotList.clear()
        sparkList.clear()
        bonus = null
        moveLeft = false
        moveRight = false
        fire = false
        placePlayer()
        newWave()
    }

    /** Fait avancer la partie de [elapsedMs] millisecondes, par pas de [Invaders.STEP_MS] au plus. */
    fun tick(elapsedMs: Long) {
        var left = elapsedMs.coerceAtLeast(0L)
        while (left > 0L && !isOver) {
            val step = minOf(left, Invaders.STEP_MS.toLong()).toInt()
            left -= step
            advance(step)
        }
    }

    private fun advance(ms: Int) {
        val dt = ms / 1000f
        clockMs = (clockMs + ms) % 3_600_000
        moveSparks(ms, dt)
        moveShots(dt)
        movePlayer(ms, dt)
        // La grille attend que le vaisseau soit de retour, et entre deux grilles.
        if (playerAlive && levelDelayMs == 0) {
            moveFormation(dt)
            enemyFire(ms)
        }
        moveBonus(ms, dt)
        collide()
        checkInvasion()
        progressLevel(ms)
    }

    // --- Mouvements ---

    private fun moveSparks(ms: Int, dt: Float) {
        for (spark in sparkList) {
            spark.x += spark.vx * dt
            spark.y += spark.vy * dt
            spark.lifeMs -= ms
        }
        sparkList.removeAll { it.lifeMs <= 0 }
    }

    private fun moveShots(dt: Float) {
        for (shot in shotList) shot.y += shot.vy * dt
        shotList.removeAll { it.y < -0.3f || it.y > Invaders.HEIGHT + 0.3f }
    }

    private fun movePlayer(ms: Int, dt: Float) {
        if (playerAlive) {
            var move = 0f
            if (moveLeft) move -= 1f
            if (moveRight) move += 1f
            playerX = (playerX + move * Invaders.PLAYER_SPEED * dt).coerceIn(Invaders.PLAYER_MARGIN, Invaders.WIDTH - Invaders.PLAYER_MARGIN)
            invulnerableMs = maxOf(0, invulnerableMs - ms)
            fireCooldownMs = maxOf(0, fireCooldownMs - ms)
            if (fire && fireCooldownMs == 0 && shotList.count { !it.hostile } < Invaders.MAX_PLAYER_SHOTS) {
                shotList.add(InvaderShot(playerX, Invaders.PLAYER_Y - Invaders.PLAYER_SPRITE * 0.45f, -Invaders.SHOT_SPEED, false))
                fireCooldownMs = Invaders.FIRE_COOLDOWN_MS
            }
        } else if (lives > 0) {
            respawnMs = maxOf(0, respawnMs - ms)
            if (respawnMs == 0) placePlayer()
        } else {
            overMs = maxOf(0, overMs - ms)
            if (overMs == 0) isOver = true
        }
    }

    private fun moveFormation(dt: Float) {
        if (aliveCount == 0) return
        originX += direction * Invaders.formationSpeed(level, aliveCount) * dt
        var minCol = Invaders.COLS
        var maxCol = -1
        for (i in aliveFlags.indices) {
            if (!aliveFlags[i]) continue
            val col = i % Invaders.COLS
            if (col < minCol) minCol = col
            if (col > maxCol) maxCol = col
        }
        val left = originX + minCol * Invaders.SPACING_X - Invaders.SPRITE / 2f
        val right = originX + maxCol * Invaders.SPACING_X + Invaders.SPRITE / 2f
        if (direction > 0 && right >= Invaders.WIDTH - Invaders.MARGIN) {
            originX -= right - (Invaders.WIDTH - Invaders.MARGIN)
            direction = -1
            originY += Invaders.DROP
        } else if (direction < 0 && left <= Invaders.MARGIN) {
            originX += Invaders.MARGIN - left
            direction = 1
            originY += Invaders.DROP
        }
    }

    private fun moveBonus(ms: Int, dt: Float) {
        val saucer = bonus
        if (saucer == null) {
            if (playerAlive && levelDelayMs == 0 && aliveCount >= Invaders.BONUS_MIN_INVADERS) {
                bonusTimerMs -= ms
                if (bonusTimerMs <= 0) spawnBonus()
            }
            return
        }
        saucer.x += saucer.vx * dt
        if (saucer.x < -Invaders.BONUS_SIZE || saucer.x > Invaders.WIDTH + Invaders.BONUS_SIZE) bonus = null
    }

    // --- Naissances ---

    private fun placePlayer() {
        playerX = Invaders.WIDTH / 2f
        playerAlive = true
        invulnerableMs = Invaders.INVULNERABLE_MS
        fireCooldownMs = 0
    }

    /** Une grille neuve : tous les envahisseurs, des abris neufs, au sommet (un peu plus bas à chaque niveau). */
    private fun newWave() {
        aliveFlags.fill(true)
        aliveCount = aliveFlags.size
        originX = Invaders.centeredOriginX()
        originY = Invaders.startY(level)
        direction = 1
        for (b in 0 until Invaders.BUNKERS) {
            for (row in 0 until Invaders.BUNKER_ROWS) {
                for (col in 0 until Invaders.BUNKER_COLS) {
                    bunkerCells[Invaders.cellIndex(b, row, col)] = Invaders.bunkerShape(row, col)
                }
            }
        }
        shotList.clear()
        bonus = null
        shotTimerMs = Invaders.shotDelayMs(level)
        bonusTimerMs = Invaders.bonusDelay(random)
    }

    private fun spawnBonus() {
        val fromLeft = random.nextBoolean()
        bonus = InvaderBonus(
            x = if (fromLeft) -Invaders.BONUS_RADIUS else Invaders.WIDTH + Invaders.BONUS_RADIUS,
            vx = if (fromLeft) Invaders.BONUS_SPEED else -Invaders.BONUS_SPEED,
            points = Invaders.BONUS_POINTS[random.nextInt(Invaders.BONUS_POINTS.size)]
        )
        bonusTimerMs = Invaders.bonusDelay(random)
    }

    /** Un envahisseur tire : le plus bas de sa colonne, qui est celle du joueur quatre fois sur dix, une au hasard sinon. */
    private fun enemyFire(ms: Int) {
        shotTimerMs -= ms
        if (shotTimerMs > 0) return
        shotTimerMs = Invaders.shotDelayMs(level) + random.nextInt(400)
        if (aliveCount == 0 || shotList.count { it.hostile } >= Invaders.maxEnemyShots(level)) return
        val columns = (0 until Invaders.COLS).filter { col -> (0 until Invaders.ROWS).any { row -> isAlive(row, col) } }
        if (columns.isEmpty()) return
        val col = if (random.nextFloat() < 0.4f) {
            columns.minByOrNull { abs(invaderX(it) - playerX) } ?: columns[0]
        } else {
            columns[random.nextInt(columns.size)]
        }
        val row = (Invaders.ROWS - 1 downTo 0).first { isAlive(it, col) }
        shotList.add(InvaderShot(invaderX(col), invaderY(row) + Invaders.SPRITE / 2f, Invaders.enemyShotSpeed(level), true))
    }

    private fun burst(x: Float, y: Float, count: Int, kind: Int, speed: Float) {
        repeat(count) {
            val angle = random.nextFloat() * 6.2831855f
            val v = speed * (0.3f + random.nextFloat() * 0.7f)
            val life = 350 + random.nextInt(450)
            sparkList.add(InvaderSpark(x, y, sin(angle) * v, -cos(angle) * v, life, life, kind))
        }
        // Les étincelles les plus anciennes disparaissent d'abord : jamais plus de MAX_SPARKS à la fois.
        while (sparkList.size > MAX_SPARKS) sparkList.removeAt(0)
    }

    // --- Collisions ---

    /** Vrai si les deux points sont à moins de [reach] l'un de l'autre. */
    private fun near(ax: Float, ay: Float, bx: Float, by: Float, reach: Float): Boolean {
        val dx = ax - bx
        val dy = ay - by
        return dx * dx + dy * dy <= reach * reach
    }

    /**
     * Si le point ([x], [y]) est dans une case d'abri encore là, la détruit, avec celle qui la suit dans le sens du tir
     * (le tir creuse), et renvoie vrai : le tir est arrêté.
     */
    private fun hitBunker(x: Float, y: Float, movingDown: Boolean): Boolean {
        val height = Invaders.BUNKER_ROWS * Invaders.BUNKER_CELL
        if (y < Invaders.BUNKER_Y || y >= Invaders.BUNKER_Y + height) return false
        val row = ((y - Invaders.BUNKER_Y) / Invaders.BUNKER_CELL).toInt().coerceIn(0, Invaders.BUNKER_ROWS - 1)
        for (b in 0 until Invaders.BUNKERS) {
            val left = Invaders.bunkerLeft(b)
            if (x < left || x >= left + Invaders.BUNKER_COLS * Invaders.BUNKER_CELL) continue
            val col = ((x - left) / Invaders.BUNKER_CELL).toInt().coerceIn(0, Invaders.BUNKER_COLS - 1)
            val index = Invaders.cellIndex(b, row, col)
            if (!bunkerCells[index]) return false
            bunkerCells[index] = false
            val deeper = row + if (movingDown) 1 else -1
            if (deeper in 0 until Invaders.BUNKER_ROWS) bunkerCells[Invaders.cellIndex(b, deeper, col)] = false
            return true
        }
        return false
    }

    /** Détruit les cases d'abri que le rectangle ([x0], [y0]) - ([x1], [y1]) recouvre : les envahisseurs écrasent les abris. */
    private fun eraseBunkers(x0: Float, y0: Float, x1: Float, y1: Float) {
        val top = Invaders.BUNKER_Y
        if (y1 < top || y0 >= top + Invaders.BUNKER_ROWS * Invaders.BUNKER_CELL) return
        for (b in 0 until Invaders.BUNKERS) {
            val left = Invaders.bunkerLeft(b)
            for (row in 0 until Invaders.BUNKER_ROWS) {
                val cellTop = top + row * Invaders.BUNKER_CELL
                if (cellTop + Invaders.BUNKER_CELL <= y0 || cellTop >= y1) continue
                for (col in 0 until Invaders.BUNKER_COLS) {
                    val cellLeft = left + col * Invaders.BUNKER_CELL
                    if (cellLeft + Invaders.BUNKER_CELL <= x0 || cellLeft >= x1) continue
                    bunkerCells[Invaders.cellIndex(b, row, col)] = false
                }
            }
        }
    }

    /** L'indice du premier envahisseur en vie touché par un tir en ([x], [y]), ou -1. */
    private fun invaderAt(x: Float, y: Float): Int {
        for (i in aliveFlags.indices) {
            if (!aliveFlags[i]) continue
            val row = i / Invaders.COLS
            val col = i % Invaders.COLS
            if (near(x, y, invaderX(col), invaderY(row), Invaders.RADIUS + Invaders.SHOT_RADIUS)) return i
        }
        return -1
    }

    private fun collide() {
        var hitPlayer = false
        val iterator = shotList.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            if (shot.hostile) {
                if (hitBunker(shot.x, shot.y, true)) {
                    iterator.remove()
                } else if (playerAlive && invulnerableMs == 0 &&
                    near(shot.x, shot.y, playerX, Invaders.PLAYER_Y, Invaders.PLAYER_RADIUS + Invaders.SHOT_RADIUS)
                ) {
                    iterator.remove()
                    hitPlayer = true
                }
                continue
            }
            if (hitBunker(shot.x, shot.y, false)) {
                iterator.remove()
                continue
            }
            val index = invaderAt(shot.x, shot.y)
            if (index >= 0) {
                iterator.remove()
                killInvader(index)
                continue
            }
            val saucer = bonus
            if (saucer != null && near(shot.x, shot.y, saucer.x, Invaders.BONUS_Y, Invaders.BONUS_RADIUS + Invaders.SHOT_RADIUS)) {
                iterator.remove()
                bonus = null
                addScore(saucer.points)
                burst(saucer.x, Invaders.BONUS_Y, 16, 2, 4f)
            }
        }
        if (hitPlayer) destroyPlayer(fatal = false)
    }

    private fun killInvader(index: Int) {
        aliveFlags[index] = false
        aliveCount--
        val row = index / Invaders.COLS
        addScore(Invaders.ROW_POINTS[row])
        burst(invaderX(index % Invaders.COLS), invaderY(row), 9, 0, 3f)
    }

    /** La grille écrase les abris qu'elle recouvre, et la partie est perdue si elle arrive au vaisseau. */
    private fun checkInvasion() {
        if (aliveCount == 0 || !playerAlive) return
        val half = Invaders.SPRITE / 2f
        var lowest = 0
        for (i in aliveFlags.indices) {
            if (!aliveFlags[i]) continue
            val row = i / Invaders.COLS
            if (row > lowest) lowest = row
            val x = invaderX(i % Invaders.COLS)
            val y = invaderY(row)
            eraseBunkers(x - half, y - half, x + half, y + half)
        }
        if (invaderY(lowest) + half >= Invaders.PLAYER_Y - Invaders.PLAYER_SPRITE / 2f) destroyPlayer(fatal = true)
    }

    /** Le vaisseau est détruit : une vie de moins (toutes si [fatal], quand la grille arrive), et les tirs ennemis disparaissent. */
    private fun destroyPlayer(fatal: Boolean) {
        playerAlive = false
        lives = if (fatal) 0 else lives - 1
        burst(playerX, Invaders.PLAYER_Y, 24, 1, 5f)
        shotList.removeAll { it.hostile }
        if (lives > 0) respawnMs = Invaders.RESPAWN_DELAY_MS else overMs = Invaders.GAME_OVER_DELAY_MS
    }

    private fun addScore(points: Int) {
        score += points
        while (score >= nextExtraLife) {
            nextExtraLife += Invaders.EXTRA_LIFE_EVERY
            // Pas de vie rendue au vaisseau déjà perdu : un tir encore en l'air ne doit pas ressusciter la partie.
            if (lives in 1 until Invaders.MAX_LIVES) lives++
        }
    }

    private fun progressLevel(ms: Int) {
        if (aliveCount > 0) return
        if (levelDelayMs <= 0) {
            // Dernier envahisseur abattu : le bonus et les tirs ennemis s'en vont, la grille suivante se prépare.
            levelDelayMs = Invaders.LEVEL_DELAY_MS
            bonus = null
            shotList.removeAll { it.hostile }
        } else {
            levelDelayMs -= ms
            if (levelDelayMs <= 0) {
                levelDelayMs = 0
                level++
                newWave()
            }
        }
    }

    // --- Sauvegarde ---

    private fun packFlags(flags: BooleanArray, from: Int, count: Int): Int {
        var mask = 0
        for (i in 0 until count) if (flags[from + i]) mask = mask or (1 shl i)
        return mask
    }

    /**
     * L'état de la partie sous forme d'une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]) :
     * l'en-tête (score, vies, niveau, vaisseau, grille, minuteurs, envahisseurs et cases d'abris en masques de bits), puis les
     * tirs et le bonus. Les étincelles, purement décoratives, ne sont pas gardées, ni les commandes.
     */
    fun save(): List<Int> = buildList {
        add(score)
        add(lives)
        add(level)
        add(if (isOver) 1 else 0)
        add(if (playerAlive) 1 else 0)
        add(playerX.toBits())
        add(invulnerableMs)
        add(respawnMs)
        add(levelDelayMs)
        add(overMs)
        add(shotTimerMs)
        add(bonusTimerMs)
        add(if (direction > 0) 1 else 0)
        add(originX.toBits())
        add(originY.toBits())
        add(packFlags(aliveFlags, 0, aliveFlags.size))
        for (word in 0 until BUNKER_WORDS) {
            val from = word * 32
            add(packFlags(bunkerCells, from, minOf(32, bunkerCells.size - from)))
        }
        add(shotList.size)
        for (s in shotList) {
            add(s.x.toBits())
            add(s.y.toBits())
            add(s.vy.toBits())
            add(if (s.hostile) 1 else 0)
        }
        val saucer = bonus
        add(if (saucer == null) 0 else 1)
        if (saucer != null) {
            add(saucer.x.toBits())
            add(saucer.vx.toBits())
            add(saucer.points)
        }
    }

    companion object {
        /** Jamais plus d'étincelles à la fois. */
        private const val MAX_SPARKS = 160

        private const val MAX_SHOTS_SAVED = 40

        /** Le nombre d'entiers qui gardent les cases d'abris (32 cases par entier). */
        private const val BUNKER_WORDS = (Invaders.BUNKERS * Invaders.BUNKER_ROWS * Invaders.BUNKER_COLS + 31) / 32

        /** Une partie remise dans l'état de [data] (voir [save]), ou null si la liste est abîmée ou incohérente. */
        fun restore(data: List<Int>, random: Random = Random.Default): InvadersGame? {
            var cursor = 0
            var bad = false
            fun int(): Int {
                if (cursor >= data.size) {
                    bad = true
                    return 0
                }
                return data[cursor++]
            }
            fun float(): Float {
                val value = Float.fromBits(int())
                if (!value.isFinite()) bad = true
                return value
            }

            val score = int()
            val lives = int()
            val level = int()
            val over = int()
            val alive = int()
            val playerX = float()
            val invulnerable = int()
            val respawn = int()
            val levelDelay = int()
            val overDelay = int()
            val shotTimer = int()
            val bonusTimer = int()
            val dir = int()
            val originX = float()
            val originY = float()
            val aliveMask = int()
            val bunkerMasks = IntArray(BUNKER_WORDS) { int() }
            if (bad) return null
            if (score < 0 || lives !in 0..Invaders.MAX_LIVES || level < 1) return null
            if (over !in 0..1 || alive !in 0..1 || dir !in 0..1) return null
            // Une partie finie n'a plus de vie, et un vaisseau en vol en a au moins une.
            if (over == 1 && lives != 0) return null
            if (alive == 1 && lives == 0) return null
            if (playerX < 0f || playerX > Invaders.WIDTH) return null
            if (invulnerable !in 0..Invaders.INVULNERABLE_MS || respawn !in 0..Invaders.RESPAWN_DELAY_MS) return null
            if (levelDelay !in 0..Invaders.LEVEL_DELAY_MS || overDelay !in 0..Invaders.GAME_OVER_DELAY_MS) return null
            if (shotTimer !in -Invaders.STEP_MS..2000) return null
            if (bonusTimer !in -Invaders.STEP_MS..(Invaders.BONUS_MIN_DELAY_MS + Invaders.BONUS_EXTRA_DELAY_MS)) return null
            if (originX < -Invaders.WIDTH || originX > Invaders.WIDTH * 2f || originY < 0f || originY > Invaders.HEIGHT * 2f) return null
            if (aliveMask < 0 || aliveMask >= (1 shl (Invaders.ROWS * Invaders.COLS))) return null

            val shots = mutableListOf<InvaderShot>()
            val shotCount = int()
            if (bad || shotCount !in 0..MAX_SHOTS_SAVED) return null
            repeat(shotCount) {
                val shot = InvaderShot(x = float(), y = float(), vy = float(), hostile = int() == 1)
                if (bad) return null
                val inField = shot.x in 0f..Invaders.WIDTH && shot.y in -0.3f..(Invaders.HEIGHT + 0.3f)
                if (!inField || abs(shot.vy) > 30f) return null
                shots.add(shot)
            }
            var saucer: InvaderBonus? = null
            val hasBonus = int()
            if (bad || hasBonus !in 0..1) return null
            if (hasBonus == 1) {
                val bx = float()
                val bvx = float()
                val points = int()
                if (bad || points !in 1..1000 || abs(bvx) > 20f) return null
                if (bx < -Invaders.BONUS_SIZE * 2f || bx > Invaders.WIDTH + Invaders.BONUS_SIZE * 2f) return null
                saucer = InvaderBonus(bx, bvx, points)
            }
            if (bad) return null

            val game = InvadersGame(random)
            game.score = score
            game.lives = lives
            game.level = level
            game.isOver = over == 1
            game.playerAlive = alive == 1
            game.playerX = playerX
            game.invulnerableMs = invulnerable
            game.respawnMs = respawn
            game.levelDelayMs = levelDelay
            game.overMs = overDelay
            game.shotTimerMs = shotTimer
            game.bonusTimerMs = bonusTimer
            game.direction = if (dir == 1) 1 else -1
            game.originX = originX
            game.originY = originY
            var count = 0
            for (i in game.aliveFlags.indices) {
                game.aliveFlags[i] = ((aliveMask ushr i) and 1) == 1
                if (game.aliveFlags[i]) count++
            }
            game.aliveCount = count
            for (i in game.bunkerCells.indices) game.bunkerCells[i] = ((bunkerMasks[i / 32] ushr (i % 32)) and 1) == 1
            game.nextExtraLife = (score / Invaders.EXTRA_LIFE_EVERY + 1) * Invaders.EXTRA_LIFE_EVERY
            game.shotList.clear()
            game.shotList.addAll(shots)
            game.bonus = saucer
            game.sparkList.clear()
            game.fireCooldownMs = 0
            game.clockMs = 0
            return game
        }
    }
}
