package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

internal const val ASTEROIDS_ID = "builtin_asteroids"

/**
 * Réglages et calculs d'Astéroïdes. Le terrain est en « unités » (9 de large, 14 de haut, le haut en y = 0) et il se
 * referme sur lui-même : ce qui sort d'un côté rentre par le côté opposé (voir [wrap] et [wrapDelta]). Le vaisseau
 * pointe vers le haut quand son angle vaut 0 et tourne dans le sens des aiguilles d'une montre quand l'angle augmente.
 */
internal object Asteroids {
    const val WIDTH = 9f
    const val HEIGHT = 14f
    const val TWO_PI = 6.2831855f

    // Les vies.
    const val LIVES = 3
    const val MAX_LIVES = 9

    /** Une vie de plus tous les 10 000 points. */
    const val EXTRA_LIFE_EVERY = 10_000

    // Le vaisseau : [SHIP_RADIUS] est son rayon de collision, [SHIP_SPRITE] le côté de l'image carrée qui le dessine.
    const val SHIP_RADIUS = 0.34f
    const val SHIP_SPRITE = 1.2f
    const val ROTATE_SPEED = 4.4f // radians par seconde
    const val THRUST = 7.5f // unités par seconde, par seconde
    const val MAX_SPEED = 8f
    const val DRAG = 0.45f // part de vitesse perdue par seconde (frottement très léger)
    const val INVULNERABLE_MS = 2500
    const val RESPAWN_DELAY_MS = 1400
    const val GAME_OVER_DELAY_MS = 1800

    /** Distance que les astéroïdes, les soucoupes et leurs tirs doivent laisser libre autour du centre pour que le vaisseau y revienne. */
    const val SAFE_RADIUS = 2.2f

    /** Distance que les astéroïdes d'un nouveau niveau laissent libre autour du vaisseau. */
    const val SPAWN_CLEARANCE = 3f

    // Les tirs du vaisseau.
    const val SHOT_SPEED = 12f
    const val SHOT_LIFE_MS = 950
    const val MAX_SHOTS = 4
    const val FIRE_COOLDOWN_MS = 200
    const val SHOT_RADIUS = 0.07f

    // Les astéroïdes : trois tailles.
    const val LARGE = 3
    const val MEDIUM = 2
    const val SMALL = 1

    /** Nombre de formes d'astéroïdes et de sommets par forme (voir [shapeRadii]). */
    const val SHAPE_COUNT = 6
    const val SHAPE_POINTS = 12

    /** Temps entre la dernière roche cassée et le niveau suivant. */
    const val LEVEL_DELAY_MS = 1600

    // Les soucoupes.
    const val UFO_MIN_DELAY_MS = 14_000
    const val UFO_EXTRA_DELAY_MS = 10_000
    const val UFO_SHOT_SPEED = 5f
    const val UFO_SHOT_LIFE_MS = 1700
    const val UFO_LARGE_POINTS = 200
    const val UFO_SMALL_POINTS = 1000

    // Plus long pas de simulation : un tir (12 unités par seconde) avance de moins de 0,2 unité par pas, moins que le plus petit astéroïde.
    const val STEP_MS = 16

    private val SHAPES: List<FloatArray> = Random(77).let { random ->
        List(SHAPE_COUNT) { FloatArray(SHAPE_POINTS) { 0.78f + random.nextFloat() * 0.34f } }
    }

    /** Les rayons (en part du rayon de l'astéroïde) des sommets de la forme [shape], de quoi dessiner un caillou irrégulier. */
    fun shapeRadii(shape: Int): FloatArray = SHAPES[shape.coerceIn(0, SHAPE_COUNT - 1)]

    /** Le rayon d'un astéroïde de taille [size] (3 gros, 2 moyen, 1 petit). */
    fun radiusOf(size: Int): Float = when (size) {
        LARGE -> 0.9f
        MEDIUM -> 0.5f
        else -> 0.27f
    }

    /** Points d'un astéroïde cassé : les petits valent le plus, ils sont les plus durs à toucher. */
    fun pointsFor(size: Int): Int = when (size) {
        LARGE -> 20
        MEDIUM -> 50
        else -> 100
    }

    /** Nombre de gros astéroïdes au début du niveau [level] : 4 au niveau 1, un de plus par niveau, 11 au plus. */
    fun rocksFor(level: Int): Int = (3 + level.coerceAtLeast(1)).coerceAtMost(11)

    /** Facteur de vitesse du niveau : +8 % par niveau, doublée au plus. */
    fun speedFactor(level: Int): Float = (1f + 0.08f * (level.coerceAtLeast(1) - 1)).coerceAtMost(2f)

    /** La vitesse d'un astéroïde de taille [size] au niveau [level] ; [roll] (de 0 à 1) la place entre la plus lente et la plus rapide. */
    fun rockSpeed(size: Int, roll: Float, level: Int): Float {
        val low = when (size) {
            LARGE -> 0.8f
            MEDIUM -> 1.3f
            else -> 1.9f
        }
        val high = when (size) {
            LARGE -> 1.6f
            MEDIUM -> 2.3f
            else -> 3.1f
        }
        return (low + (high - low) * roll.coerceIn(0f, 1f)) * speedFactor(level)
    }

    fun ufoRadius(small: Boolean): Float = if (small) 0.26f else 0.42f

    fun ufoPoints(small: Boolean): Int = if (small) UFO_SMALL_POINTS else UFO_LARGE_POINTS

    /** Le temps avant la prochaine soucoupe, en millisecondes. */
    fun ufoDelay(random: Random): Int = UFO_MIN_DELAY_MS + random.nextInt(UFO_EXTRA_DELAY_MS)

    /** Chance que la soucoupe soit la petite (qui vise bien) : nulle jusqu'à 2 000 points, puis croissante, 80 % au plus. */
    fun smallUfoChance(score: Int): Float = ((score - 2000) / 12000f).coerceIn(0f, 0.8f)

    /** [v] ramenée dans [0, [size][ : le terrain se referme sur lui-même. */
    fun wrap(v: Float, size: Float): Float {
        val m = v % size
        return if (m < 0f) m + size else m
    }

    /** L'écart [d] ramené entre -[size]/2 et [size]/2 : le plus court chemin sur un terrain qui se referme. */
    fun wrapDelta(d: Float, size: Float): Float {
        val m = d % size
        return when {
            m > size / 2f -> m - size
            m < -size / 2f -> m + size
            else -> m
        }
    }
}

/** Un astéroïde : sa taille (voir [Asteroids.LARGE]), sa forme, et son angle de rotation sur lui-même (sans effet sur les collisions). */
internal class AsteroidRock(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Int,
    val shape: Int,
    var angle: Float,
    val spin: Float
)

/** Un tir, du vaisseau ou d'une soucoupe ([hostile]) ; il disparaît quand [lifeMs] tombe à 0. */
internal class AsteroidShot(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var lifeMs: Int,
    val hostile: Boolean
)

/** Une soucoupe : elle traverse le terrain sans le refermer, change de cap toutes les [dirMs] et tire toutes les [shotMs]. */
internal class AsteroidUfo(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val small: Boolean,
    var dirMs: Int,
    var shotMs: Int
)

/** Une étincelle d'explosion (décor, non gardé par [AsteroidsGame.save]) ; [kind] : 0 roche, 1 vaisseau, 2 soucoupe. */
internal class AsteroidSpark(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    var lifeMs: Int,
    val totalMs: Int,
    val kind: Int
)

/**
 * Une partie d'Astéroïdes : un vaisseau, des roches qui se brisent en plus petites quand on les touche, et de temps en
 * temps une soucoupe qui tire. Les commandes ([turnLeft], [turnRight], [thrust], [fire]) sont des états : tant qu'elles
 * sont vraies, le vaisseau tourne, pousse ou tire (un tir toutes les [Asteroids.FIRE_COOLDOWN_MS], quatre au plus en
 * l'air). Trois vies ; vaisseau détruit, le suivant revient au centre dès que la place est libre, protégé un moment.
 * Un niveau est fini quand il n'y a plus de roche ; le suivant en a plus et elles vont plus vite.
 */
internal class AsteroidsGame(private val random: Random = Random.Default) {

    private val rockList = mutableListOf<AsteroidRock>()
    private val shotList = mutableListOf<AsteroidShot>()
    private val sparkList = mutableListOf<AsteroidSpark>()

    val rocks: List<AsteroidRock> get() = rockList
    val shots: List<AsteroidShot> get() = shotList
    val sparks: List<AsteroidSpark> get() = sparkList

    /** La soucoupe en vol, s'il y en a une. */
    var ufo: AsteroidUfo? = null
        private set

    // Les commandes, tenues par l'écran.
    var turnLeft = false
    var turnRight = false
    var thrust = false
    var fire = false

    var shipX = Asteroids.WIDTH / 2f
        private set
    var shipY = Asteroids.HEIGHT / 2f
        private set
    var shipVx = 0f
        private set
    var shipVy = 0f
        private set

    /** L'angle du vaisseau en radians : 0 vers le haut, positif dans le sens des aiguilles d'une montre. */
    var shipAngle = 0f
        private set

    /** Faux entre la destruction du vaisseau et son retour. */
    var shipAlive = true
        private set

    /** Vrai quand le moteur pousse (la flamme se dessine). */
    var thrusting = false
        private set

    /** Temps de protection restant après un retour, en millisecondes. */
    var invulnerableMs = 0
        private set

    var score = 0
        private set
    var lives = Asteroids.LIVES
        private set
    var level = 1
        private set

    /** Vrai quand la dernière vie est perdue et que l'explosion a eu le temps de se voir. */
    var isOver = false
        private set

    /** Temps restant avant le niveau suivant (0 : le niveau est en cours). */
    var levelDelayMs = 0
        private set

    private var respawnMs = 0
    private var overMs = 0
    private var ufoTimerMs = 0
    private var fireCooldownMs = 0
    private var nextExtraLife = Asteroids.EXTRA_LIFE_EVERY

    init {
        reset()
    }

    /** Vrai entre deux niveaux : le décor attend, l'écran peut annoncer le suivant. */
    val isBetweenLevels: Boolean get() = levelDelayMs > 0

    /** Repart de zéro : score nul, trois vies, niveau 1. */
    fun reset() {
        score = 0
        lives = Asteroids.LIVES
        level = 1
        isOver = false
        levelDelayMs = 0
        respawnMs = 0
        overMs = 0
        fireCooldownMs = 0
        nextExtraLife = Asteroids.EXTRA_LIFE_EVERY
        rockList.clear()
        shotList.clear()
        sparkList.clear()
        ufo = null
        ufoTimerMs = Asteroids.ufoDelay(random)
        turnLeft = false
        turnRight = false
        thrust = false
        fire = false
        placeShip()
        spawnRocks()
    }

    /** Fait avancer la partie de [elapsedMs] millisecondes, par pas de [Asteroids.STEP_MS] au plus. */
    fun tick(elapsedMs: Long) {
        var left = elapsedMs.coerceAtLeast(0L)
        while (left > 0L && !isOver) {
            val step = minOf(left, Asteroids.STEP_MS.toLong()).toInt()
            left -= step
            advance(step)
        }
    }

    private fun advance(ms: Int) {
        val dt = ms / 1000f
        moveSparks(ms, dt)
        moveRocks(dt)
        moveShots(ms, dt)
        moveUfo(ms, dt)
        moveShip(ms, dt)
        collide()
        progressLevel(ms)
    }

    // --- Mouvements ---

    private fun moveRocks(dt: Float) {
        for (rock in rockList) {
            rock.x = Asteroids.wrap(rock.x + rock.vx * dt, Asteroids.WIDTH)
            rock.y = Asteroids.wrap(rock.y + rock.vy * dt, Asteroids.HEIGHT)
            rock.angle += rock.spin * dt
        }
    }

    private fun moveShots(ms: Int, dt: Float) {
        for (shot in shotList) {
            shot.x = Asteroids.wrap(shot.x + shot.vx * dt, Asteroids.WIDTH)
            shot.y = Asteroids.wrap(shot.y + shot.vy * dt, Asteroids.HEIGHT)
            shot.lifeMs -= ms
        }
        shotList.removeAll { it.lifeMs <= 0 }
    }

    private fun moveSparks(ms: Int, dt: Float) {
        for (spark in sparkList) {
            spark.x += spark.vx * dt
            spark.y += spark.vy * dt
            spark.lifeMs -= ms
        }
        sparkList.removeAll { it.lifeMs <= 0 }
    }

    private fun moveUfo(ms: Int, dt: Float) {
        val saucer = ufo
        if (saucer == null) {
            // Le compte à rebours ne court que pendant une partie « normale » : vaisseau en vol, roches en jeu.
            if (shipAlive && rockList.isNotEmpty() && levelDelayMs == 0) {
                ufoTimerMs -= ms
                if (ufoTimerMs <= 0) spawnUfo()
            }
            return
        }
        saucer.x += saucer.vx * dt
        saucer.y = Asteroids.wrap(saucer.y + saucer.vy * dt, Asteroids.HEIGHT)
        saucer.dirMs -= ms
        if (saucer.dirMs <= 0) {
            saucer.dirMs = 700 + random.nextInt(900)
            saucer.vy = (random.nextInt(3) - 1) * abs(saucer.vx) * 0.6f
        }
        saucer.shotMs -= ms
        if (saucer.shotMs <= 0) {
            saucer.shotMs = if (saucer.small) 1000 else 1400
            if (shipAlive) ufoFire(saucer)
        }
        val reach = Asteroids.ufoRadius(saucer.small) * 2f
        if (saucer.x < -reach || saucer.x > Asteroids.WIDTH + reach) ufo = null
    }

    private fun moveShip(ms: Int, dt: Float) {
        if (shipAlive) {
            if (turnLeft) shipAngle -= Asteroids.ROTATE_SPEED * dt
            if (turnRight) shipAngle += Asteroids.ROTATE_SPEED * dt
            shipAngle = Asteroids.wrap(shipAngle, Asteroids.TWO_PI)
            thrusting = thrust
            if (thrust) {
                shipVx += sin(shipAngle) * Asteroids.THRUST * dt
                shipVy -= cos(shipAngle) * Asteroids.THRUST * dt
            }
            val keep = exp(-Asteroids.DRAG * dt)
            shipVx *= keep
            shipVy *= keep
            val speed = hypot(shipVx, shipVy)
            if (speed > Asteroids.MAX_SPEED) {
                shipVx *= Asteroids.MAX_SPEED / speed
                shipVy *= Asteroids.MAX_SPEED / speed
            }
            shipX = Asteroids.wrap(shipX + shipVx * dt, Asteroids.WIDTH)
            shipY = Asteroids.wrap(shipY + shipVy * dt, Asteroids.HEIGHT)
            invulnerableMs = maxOf(0, invulnerableMs - ms)
            fireCooldownMs = maxOf(0, fireCooldownMs - ms)
            if (fire && fireCooldownMs == 0 && shotList.count { !it.hostile } < Asteroids.MAX_SHOTS) fireShot()
        } else {
            thrusting = false
            if (lives > 0) {
                respawnMs = maxOf(0, respawnMs - ms)
                if (respawnMs == 0 && isCenterClear()) placeShip()
            } else {
                overMs = maxOf(0, overMs - ms)
                if (overMs == 0) isOver = true
            }
        }
    }

    // --- Naissances ---

    private fun placeShip() {
        shipX = Asteroids.WIDTH / 2f
        shipY = Asteroids.HEIGHT / 2f
        shipVx = 0f
        shipVy = 0f
        shipAngle = 0f
        shipAlive = true
        thrusting = false
        invulnerableMs = Asteroids.INVULNERABLE_MS
        fireCooldownMs = 0
    }

    private fun fireShot() {
        val dirX = sin(shipAngle)
        val dirY = -cos(shipAngle)
        val nose = Asteroids.SHIP_SPRITE * 0.45f
        shotList.add(
            AsteroidShot(
                x = Asteroids.wrap(shipX + dirX * nose, Asteroids.WIDTH),
                y = Asteroids.wrap(shipY + dirY * nose, Asteroids.HEIGHT),
                vx = shipVx + dirX * Asteroids.SHOT_SPEED,
                vy = shipVy + dirY * Asteroids.SHOT_SPEED,
                lifeMs = Asteroids.SHOT_LIFE_MS,
                hostile = false
            )
        )
        fireCooldownMs = Asteroids.FIRE_COOLDOWN_MS
    }

    private fun newRock(x: Float, y: Float, size: Int, parentVx: Float, parentVy: Float): AsteroidRock {
        val speed = Asteroids.rockSpeed(size, random.nextFloat(), level)
        val dir = random.nextFloat() * Asteroids.TWO_PI
        return AsteroidRock(
            x = x,
            y = y,
            vx = sin(dir) * speed + parentVx * 0.5f,
            vy = -cos(dir) * speed + parentVy * 0.5f,
            size = size,
            shape = random.nextInt(Asteroids.SHAPE_COUNT),
            angle = random.nextFloat() * Asteroids.TWO_PI,
            spin = (random.nextFloat() - 0.5f) * 2f
        )
    }

    private fun spawnRocks() {
        repeat(Asteroids.rocksFor(level)) {
            var x = 0f
            var y = 0f
            var tries = 0
            do {
                x = random.nextFloat() * Asteroids.WIDTH
                y = random.nextFloat() * Asteroids.HEIGHT
                tries++
            } while (tries < 30 && near(x, y, shipX, shipY, Asteroids.SPAWN_CLEARANCE))
            rockList.add(newRock(x, y, Asteroids.LARGE, 0f, 0f))
        }
    }

    private fun spawnUfo() {
        val small = random.nextFloat() < Asteroids.smallUfoChance(score)
        val fromLeft = random.nextBoolean()
        val speed = if (small) 2.9f else 2.1f
        val r = Asteroids.ufoRadius(small)
        ufo = AsteroidUfo(
            x = if (fromLeft) -r else Asteroids.WIDTH + r,
            y = random.nextFloat() * Asteroids.HEIGHT,
            vx = if (fromLeft) speed else -speed,
            vy = 0f,
            small = small,
            dirMs = 800,
            shotMs = 900
        )
        ufoTimerMs = Asteroids.ufoDelay(random)
    }

    private fun ufoFire(saucer: AsteroidUfo) {
        val angle = if (saucer.small) {
            val dx = Asteroids.wrapDelta(shipX - saucer.x, Asteroids.WIDTH)
            val dy = Asteroids.wrapDelta(shipY - saucer.y, Asteroids.HEIGHT)
            atan2(dx, -dy) + (random.nextFloat() - 0.5f) * 0.24f
        } else {
            random.nextFloat() * Asteroids.TWO_PI
        }
        shotList.add(
            AsteroidShot(
                x = saucer.x,
                y = saucer.y,
                vx = sin(angle) * Asteroids.UFO_SHOT_SPEED,
                vy = -cos(angle) * Asteroids.UFO_SHOT_SPEED,
                lifeMs = Asteroids.UFO_SHOT_LIFE_MS,
                hostile = true
            )
        )
    }

    private fun burst(x: Float, y: Float, count: Int, kind: Int, speed: Float) {
        repeat(count) {
            val dir = random.nextFloat() * Asteroids.TWO_PI
            val v = speed * (0.3f + random.nextFloat() * 0.7f)
            val life = 350 + random.nextInt(500)
            sparkList.add(AsteroidSpark(x, y, sin(dir) * v, -cos(dir) * v, life, life, kind))
        }
        // Les étincelles les plus anciennes disparaissent d'abord : jamais plus de MAX_SPARKS à la fois.
        while (sparkList.size > MAX_SPARKS) sparkList.removeAt(0)
    }

    // --- Collisions ---

    /** Vrai si les deux points sont à moins de [reach] l'un de l'autre, en tenant compte d'un terrain qui se referme. */
    private fun near(ax: Float, ay: Float, bx: Float, by: Float, reach: Float): Boolean {
        val dx = Asteroids.wrapDelta(ax - bx, Asteroids.WIDTH)
        val dy = Asteroids.wrapDelta(ay - by, Asteroids.HEIGHT)
        return dx * dx + dy * dy <= reach * reach
    }

    private fun collide() {
        // Les tirs du vaisseau contre les roches, puis contre la soucoupe.
        val iterator = shotList.iterator()
        while (iterator.hasNext()) {
            val shot = iterator.next()
            if (shot.hostile) continue
            val rock = rockList.firstOrNull {
                near(shot.x, shot.y, it.x, it.y, Asteroids.radiusOf(it.size) + Asteroids.SHOT_RADIUS)
            }
            if (rock != null) {
                iterator.remove()
                breakRock(rock)
                continue
            }
            val saucer = ufo
            if (saucer != null && near(shot.x, shot.y, saucer.x, saucer.y, Asteroids.ufoRadius(saucer.small) + Asteroids.SHOT_RADIUS)) {
                iterator.remove()
                destroyUfo(saucer, award = true)
            }
        }
        if (!shipAlive || invulnerableMs > 0) return
        // Le vaisseau contre les tirs ennemis, les roches et la soucoupe.
        val hostile = shotList.firstOrNull {
            it.hostile && near(shipX, shipY, it.x, it.y, Asteroids.SHIP_RADIUS + Asteroids.SHOT_RADIUS)
        }
        if (hostile != null) {
            shotList.remove(hostile)
            destroyShip()
            return
        }
        if (rockList.any { near(shipX, shipY, it.x, it.y, Asteroids.SHIP_RADIUS + Asteroids.radiusOf(it.size) * 0.9f) }) {
            destroyShip()
            return
        }
        val saucer = ufo
        if (saucer != null && near(shipX, shipY, saucer.x, saucer.y, Asteroids.SHIP_RADIUS + Asteroids.ufoRadius(saucer.small))) {
            destroyUfo(saucer, award = false)
            destroyShip()
        }
    }

    /** Casse [rock] : les points, puis deux roches plus petites à sa place (aucune pour un petit). */
    private fun breakRock(rock: AsteroidRock) {
        rockList.remove(rock)
        addScore(Asteroids.pointsFor(rock.size))
        burst(rock.x, rock.y, 5 + rock.size * 3, 0, 2.5f + rock.size)
        if (rock.size > Asteroids.SMALL) {
            repeat(2) { rockList.add(newRock(rock.x, rock.y, rock.size - 1, rock.vx, rock.vy)) }
        }
    }

    private fun destroyUfo(saucer: AsteroidUfo, award: Boolean) {
        ufo = null
        if (award) addScore(Asteroids.ufoPoints(saucer.small))
        burst(saucer.x, saucer.y, 14, 2, 4f)
    }

    private fun destroyShip() {
        shipAlive = false
        thrusting = false
        lives--
        burst(shipX, shipY, 26, 1, 5f)
        if (lives > 0) respawnMs = Asteroids.RESPAWN_DELAY_MS else overMs = Asteroids.GAME_OVER_DELAY_MS
    }

    private fun addScore(points: Int) {
        score += points
        while (score >= nextExtraLife) {
            nextExtraLife += Asteroids.EXTRA_LIFE_EVERY
            // Pas de vie rendue au vaisseau déjà perdu : un tir encore en l'air ne doit pas ressusciter la partie.
            if (lives in 1 until Asteroids.MAX_LIVES) lives++
        }
    }

    /** Vrai si rien ne menace le centre du terrain : le vaisseau peut y revenir. */
    private fun isCenterClear(): Boolean {
        val cx = Asteroids.WIDTH / 2f
        val cy = Asteroids.HEIGHT / 2f
        if (rockList.any { near(cx, cy, it.x, it.y, Asteroids.SAFE_RADIUS + Asteroids.radiusOf(it.size)) }) return false
        val saucer = ufo
        if (saucer != null && near(cx, cy, saucer.x, saucer.y, Asteroids.SAFE_RADIUS)) return false
        return shotList.none { it.hostile && near(cx, cy, it.x, it.y, Asteroids.SAFE_RADIUS) }
    }

    private fun progressLevel(ms: Int) {
        if (rockList.isNotEmpty()) return
        if (levelDelayMs <= 0) {
            // Dernière roche cassée : la soucoupe et ses tirs s'en vont, le niveau suivant se prépare.
            levelDelayMs = Asteroids.LEVEL_DELAY_MS
            ufo = null
            shotList.removeAll { it.hostile }
        } else {
            levelDelayMs -= ms
            if (levelDelayMs <= 0) {
                levelDelayMs = 0
                level++
                ufoTimerMs = Asteroids.ufoDelay(random)
                spawnRocks()
            }
        }
    }

    // --- Sauvegarde ---

    /**
     * L'état de la partie sous forme d'une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]) :
     * l'en-tête (score, vies, niveau, vaisseau, minuteurs), puis les roches, les tirs et la soucoupe. Les étincelles,
     * purement décoratives, ne sont pas gardées, ni les commandes.
     */
    fun save(): List<Int> = buildList {
        add(score)
        add(lives)
        add(level)
        add(if (isOver) 1 else 0)
        add(if (shipAlive) 1 else 0)
        add(shipX.toBits())
        add(shipY.toBits())
        add(shipVx.toBits())
        add(shipVy.toBits())
        add(shipAngle.toBits())
        add(invulnerableMs)
        add(respawnMs)
        add(levelDelayMs)
        add(ufoTimerMs)
        add(overMs)
        add(rockList.size)
        for (r in rockList) {
            add(r.x.toBits())
            add(r.y.toBits())
            add(r.vx.toBits())
            add(r.vy.toBits())
            add(r.size)
            add(r.shape)
            add(r.angle.toBits())
            add(r.spin.toBits())
        }
        add(shotList.size)
        for (s in shotList) {
            add(s.x.toBits())
            add(s.y.toBits())
            add(s.vx.toBits())
            add(s.vy.toBits())
            add(s.lifeMs)
            add(if (s.hostile) 1 else 0)
        }
        val saucer = ufo
        add(if (saucer == null) 0 else 1)
        if (saucer != null) {
            add(saucer.x.toBits())
            add(saucer.y.toBits())
            add(saucer.vx.toBits())
            add(saucer.vy.toBits())
            add(if (saucer.small) 1 else 0)
            add(saucer.dirMs)
            add(saucer.shotMs)
        }
    }

    companion object {
        /** Jamais plus d'étincelles à la fois. */
        private const val MAX_SPARKS = 160

        private const val MAX_ROCKS = 160
        private const val MAX_SHOTS_SAVED = 40

        /** Une partie remise dans l'état de [data] (voir [save]), ou null si la liste est abîmée ou incohérente. */
        fun restore(data: List<Int>, random: Random = Random.Default): AsteroidsGame? {
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
            val shipX = float()
            val shipY = float()
            val shipVx = float()
            val shipVy = float()
            val shipAngle = float()
            val invulnerable = int()
            val respawn = int()
            val levelDelay = int()
            val ufoTimer = int()
            val overDelay = int()
            if (bad) return null
            if (score < 0 || lives !in 0..Asteroids.MAX_LIVES || level < 1) return null
            if (over !in 0..1 || alive !in 0..1) return null
            // Une partie finie n'a plus de vie, et un vaisseau en vol en a au moins une.
            if (over == 1 && lives != 0) return null
            if (alive == 1 && lives == 0) return null
            if (shipX < 0f || shipX > Asteroids.WIDTH || shipY < 0f || shipY > Asteroids.HEIGHT) return null
            if (hypot(shipVx, shipVy) > Asteroids.MAX_SPEED * 2f) return null
            if (invulnerable !in 0..Asteroids.INVULNERABLE_MS) return null
            if (respawn !in 0..Asteroids.RESPAWN_DELAY_MS) return null
            if (levelDelay !in 0..Asteroids.LEVEL_DELAY_MS) return null
            if (ufoTimer !in -Asteroids.STEP_MS..(Asteroids.UFO_MIN_DELAY_MS + Asteroids.UFO_EXTRA_DELAY_MS)) return null
            if (overDelay !in 0..Asteroids.GAME_OVER_DELAY_MS) return null

            val rocks = mutableListOf<AsteroidRock>()
            val rockCount = int()
            if (rockCount !in 0..MAX_ROCKS) return null
            repeat(rockCount) {
                val rock = AsteroidRock(
                    x = float(), y = float(), vx = float(), vy = float(),
                    size = int(), shape = int(), angle = float(), spin = float()
                )
                if (bad) return null
                val inField = rock.x in 0f..Asteroids.WIDTH && rock.y in 0f..Asteroids.HEIGHT
                if (!inField || rock.size !in Asteroids.SMALL..Asteroids.LARGE) return null
                if (rock.shape !in 0 until Asteroids.SHAPE_COUNT || hypot(rock.vx, rock.vy) > 40f) return null
                rocks.add(rock)
            }
            val shots = mutableListOf<AsteroidShot>()
            val shotCount = int()
            if (bad || shotCount !in 0..MAX_SHOTS_SAVED) return null
            repeat(shotCount) {
                val shot = AsteroidShot(x = float(), y = float(), vx = float(), vy = float(), lifeMs = int(), hostile = int() == 1)
                if (bad) return null
                val inField = shot.x in 0f..Asteroids.WIDTH && shot.y in 0f..Asteroids.HEIGHT
                if (!inField || shot.lifeMs !in 1..Asteroids.UFO_SHOT_LIFE_MS || hypot(shot.vx, shot.vy) > 40f) return null
                shots.add(shot)
            }
            var saucer: AsteroidUfo? = null
            val hasSaucer = int()
            if (bad || hasSaucer !in 0..1) return null
            if (hasSaucer == 1) {
                saucer = AsteroidUfo(x = float(), y = float(), vx = float(), vy = float(), small = int() == 1, dirMs = int(), shotMs = int())
                if (bad) return null
                val reach = Asteroids.ufoRadius(false) * 2f
                if (saucer.x < -reach || saucer.x > Asteroids.WIDTH + reach) return null
                if (saucer.y < 0f || saucer.y > Asteroids.HEIGHT || hypot(saucer.vx, saucer.vy) > 40f) return null
            }
            if (bad) return null

            val game = AsteroidsGame(random)
            game.score = score
            game.lives = lives
            game.level = level
            game.isOver = over == 1
            game.shipAlive = alive == 1
            game.shipX = shipX
            game.shipY = shipY
            game.shipVx = shipVx
            game.shipVy = shipVy
            game.shipAngle = Asteroids.wrap(shipAngle, Asteroids.TWO_PI)
            game.invulnerableMs = invulnerable
            game.respawnMs = respawn
            game.levelDelayMs = levelDelay
            game.ufoTimerMs = ufoTimer
            game.overMs = overDelay
            game.nextExtraLife = (score / Asteroids.EXTRA_LIFE_EVERY + 1) * Asteroids.EXTRA_LIFE_EVERY
            game.rockList.clear()
            game.rockList.addAll(rocks)
            game.shotList.clear()
            game.shotList.addAll(shots)
            game.ufo = saucer
            game.sparkList.clear()
            game.fireCooldownMs = 0
            game.thrusting = false
            return game
        }
    }
}

/** Les degrés d'un angle en radians (pour tourner l'image du vaisseau à l'écran). */
internal fun Float.radiansToDegrees(): Float = this * (180f / PI.toFloat())
