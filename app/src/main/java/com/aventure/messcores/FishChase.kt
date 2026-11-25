package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal const val FISHCHASE_ID = "builtin_fishchase"

/** Les constantes du terrain de Poursuite aux poissons, en unités du terrain (15 de large, 10 de haut). */
internal object FishChase {
    const val WIDTH = 15f
    const val HEIGHT = 10f

    /** Le trottoir : la ligne où les pieds des deux personnages se posent. */
    const val GROUND_Y = 8.9f

    /** La femme court toujours au même endroit : c'est le décor qui défile. */
    const val WOMAN_X = 11.4f
    const val WOMAN_HEIGHT = 3.1f
    const val MAN_HEIGHT = 3.4f

    /** Demi-largeur de la zone où un poisson touche l'homme, et sa hauteur au-dessus du trottoir. */
    const val MAN_HALF_WIDTH = 0.8f
    const val MAN_TOP = GROUND_Y - MAN_HEIGHT

    /** Si l'homme arrive ici, la femme est rattrapée. */
    const val CATCH_X = WOMAN_X - 1.9f
    const val START_X = 4.2f
    const val MIN_X = 0.9f
    const val MAX_X = 8.6f

    const val FISH_RADIUS = 0.35f
    const val FLIGHT_MS = 320
    const val COOLDOWN_MS = 550
    const val FISH_START_Y = HEIGHT + 0.8f
    const val FISH_ARC = 1.6f

    const val KNOCKBACK = 2.2f
    const val KNOCKBACK_SPEED = 9f
    const val STUN_MS = 650
    const val HITS_PER_LEVEL = 4
    const val LEVEL_BANNER_MS = 1400
    const val BASE_POINTS = 100
    const val COMBO_BONUS = 20

    /** Les limites de la mire, qui se promène toute seule. */
    const val CROSS_AMP_X = 6.1f
    const val CROSS_CENTER_Y = 7.0f
    const val CROSS_AMP_Y = 2.0f

    /** La vitesse de l'homme (unités par seconde) au niveau donné. */
    fun manSpeed(level: Int): Float = 0.45f + 0.1f * level.coerceAtMost(14)

    /** La vitesse de la mire (radians par seconde), horizontale puis verticale, au niveau donné. */
    fun crossSpeedX(level: Int): Float = 1.9f + 0.3f * (level - 1).coerceAtMost(10)
    fun crossSpeedY(level: Int): Float = 2.9f + 0.4f * (level - 1).coerceAtMost(10)

    /** La vitesse de défilement du décor, en pixels de l'image par seconde. */
    const val SCROLL_PX_PER_SECOND = 260f
}

/** Un poisson lancé : il part du bas de l'écran vers le point visé et arrive après [FishChase.FLIGHT_MS]. */
internal class FlyingFish(val targetX: Float, val targetY: Float) {
    var ageMs = 0
    var x = FishChase.WIDTH / 2f
    var y = FishChase.FISH_START_Y

    /** L'angle du poisson (0 = tête vers la droite), en degrés. */
    var angle = 0f

    private fun posX(t: Float) = FishChase.WIDTH / 2f + (targetX - FishChase.WIDTH / 2f) * t
    private fun posY(t: Float) =
        FishChase.FISH_START_Y + (targetY - FishChase.FISH_START_Y) * t - 4f * FishChase.FISH_ARC * t * (1f - t)

    fun update() {
        val t = (ageMs.toFloat() / FishChase.FLIGHT_MS).coerceIn(0f, 1f)
        x = posX(t)
        y = posY(t)
        val next = (t + 0.03f).coerceAtMost(1.03f)
        angle = Math.toDegrees(atan2((posY(next) - y).toDouble(), (posX(next) - x).toDouble())).toFloat()
    }
}

/** Une étincelle (ou une écaille) de l'impact d'un poisson. [kind] : 0 pour un impact sur l'homme, 1 pour un poisson qui rate. */
internal class FishSpark(var x: Float, var y: Float, val vx: Float, val vy: Float, val totalMs: Int, val kind: Int) {
    var lifeMs = totalMs
}

/**
 * Une partie de Poursuite aux poissons : un homme court derrière une femme, dans une rue de nuit. Une mire se promène
 * toute seule ; [fire] lance un poisson vers l'endroit où elle se trouve à cet instant. S'il arrive sur l'homme, il le
 * repousse et l'étourdit un moment ; sinon l'homme continue de se rapprocher. Il rattrape la femme : la partie est finie.
 * Tous les [FishChase.HITS_PER_LEVEL] poissons réussis, le niveau monte : l'homme et la mire vont plus vite.
 */
internal class FishChaseGame {
    var score = 0
        private set
    var level = 1
        private set
    var hits = 0
        private set
    var combo = 0
        private set
    var isOver = false
        private set

    var manX = FishChase.START_X
        private set
    var crossX = FishChase.WIDTH / 2f
        private set
    var crossY = FishChase.CROSS_CENTER_Y
        private set

    var clockMs = 0L
        private set

    /** Le temps restant où l'homme est étourdi (il recule, rouge) et celui de la pause avant le prochain lancer. */
    var stunMs = 0
        private set
    var cooldownMs = 0
        private set

    /** Le temps restant d'affichage de l'annonce du niveau. */
    var bannerMs = 0
        private set

    /** Le décor défile : décalage cumulé, en pixels de l'image. */
    var scrollPx = 0f
        private set

    /** Le nombre de lancers et ceux qui ont touché au dernier lancer ([lastShotHit], pour réagir à l'écran). */
    var shots = 0
        private set
    var lastShotHit = false
        private set

    val fish = ArrayList<FlyingFish>()
    val sparks = ArrayList<FishSpark>()

    private var phaseX = 0f
    private var phaseY = 1.3f
    private var knockTarget = FishChase.START_X
    private var sparkSeed = 7

    fun reset() {
        score = 0
        level = 1
        hits = 0
        combo = 0
        isOver = false
        manX = FishChase.START_X
        knockTarget = FishChase.START_X
        crossX = FishChase.WIDTH / 2f
        crossY = FishChase.CROSS_CENTER_Y
        clockMs = 0L
        stunMs = 0
        cooldownMs = 0
        bannerMs = 0
        scrollPx = 0f
        shots = 0
        lastShotHit = false
        phaseX = 0f
        phaseY = 1.3f
        fish.clear()
        sparks.clear()
    }

    /** Vrai si la mire est, à cet instant, sur la zone où un poisson touche l'homme. */
    val crossOnMan: Boolean
        get() = isOnMan(crossX, crossY)

    /** Vrai si le lancer est possible (pas de pause entre deux lancers, partie en cours). */
    val canFire: Boolean
        get() = !isOver && cooldownMs <= 0

    private fun isOnMan(x: Float, y: Float): Boolean =
        abs(x - manX) <= FishChase.MAN_HALF_WIDTH + FishChase.FISH_RADIUS &&
            y >= FishChase.MAN_TOP - FishChase.FISH_RADIUS &&
            y <= FishChase.GROUND_Y + 0.2f

    /** Lance un poisson vers la mire. Rend faux (et ne fait rien) pendant la pause entre deux lancers. */
    fun fire(): Boolean {
        if (!canFire) return false
        fish.add(FlyingFish(crossX, crossY).also { it.update() })
        cooldownMs = FishChase.COOLDOWN_MS
        shots++
        return true
    }

    /** Fait avancer la partie de [elapsedMs] millisecondes. */
    fun tick(elapsedMs: Long) {
        if (isOver || elapsedMs <= 0L) return
        val ms = elapsedMs.toInt()
        val dt = elapsedMs / 1000f
        clockMs += elapsedMs
        scrollPx += FishChase.SCROLL_PX_PER_SECOND * dt

        // La mire : deux oscillations de vitesses différentes, qui lui font parcourir tout l'espace de l'homme.
        phaseX += FishChase.crossSpeedX(level) * dt
        phaseY += FishChase.crossSpeedY(level) * dt
        crossX = FishChase.WIDTH / 2f + FishChase.CROSS_AMP_X * sin(phaseX)
        crossY = FishChase.CROSS_CENTER_Y + FishChase.CROSS_AMP_Y * sin(phaseY)

        if (cooldownMs > 0) cooldownMs = (cooldownMs - ms).coerceAtLeast(0)
        if (bannerMs > 0) bannerMs = (bannerMs - ms).coerceAtLeast(0)

        // L'homme : repoussé après un poisson, sinon il se rapproche de la femme.
        if (stunMs > 0) {
            stunMs = (stunMs - ms).coerceAtLeast(0)
            if (manX > knockTarget) manX = (manX - FishChase.KNOCKBACK_SPEED * dt).coerceAtLeast(knockTarget)
        } else {
            manX += FishChase.manSpeed(level) * dt
        }

        // Les poissons en vol : à l'arrivée, ils touchent l'homme ou non.
        val landed = ArrayList<FlyingFish>()
        for (f in fish) {
            f.ageMs += ms
            f.update()
            if (f.ageMs >= FishChase.FLIGHT_MS) landed.add(f)
        }
        for (f in landed) {
            fish.remove(f)
            resolve(f)
        }

        for (s in sparks) {
            s.lifeMs -= ms
            s.x += s.vx * dt
            s.y += s.vy * dt
        }
        sparks.removeAll { it.lifeMs <= 0 }

        if (manX >= FishChase.CATCH_X) {
            manX = FishChase.CATCH_X
            isOver = true
        }
    }

    private fun resolve(f: FlyingFish) {
        if (isOnMan(f.targetX, f.targetY)) {
            hits++
            combo++
            lastShotHit = true
            score += FishChase.BASE_POINTS * level + (combo - 1) * FishChase.COMBO_BONUS
            knockTarget = (manX - FishChase.KNOCKBACK).coerceAtLeast(FishChase.MIN_X)
            stunMs = FishChase.STUN_MS
            burst(f.targetX, f.targetY, 0)
            if (hits % FishChase.HITS_PER_LEVEL == 0) {
                level++
                bannerMs = FishChase.LEVEL_BANNER_MS
            }
        } else {
            combo = 0
            lastShotHit = false
            burst(f.targetX, f.targetY.coerceAtMost(FishChase.GROUND_Y), 1)
        }
    }

    private fun nextRandom(): Float {
        sparkSeed = (sparkSeed * 1103515245 + 12345) and 0x7fffffff
        return (sparkSeed % 1000) / 1000f
    }

    private fun burst(x: Float, y: Float, kind: Int) {
        val count = if (kind == 0) 9 else 5
        for (i in 0 until count) {
            val angle = (i.toFloat() / count) * 6.2831855f + nextRandom()
            val speed = 1.5f + nextRandom() * 2.5f
            sparks.add(FishSpark(x, y, cos(angle) * speed, sin(angle) * speed - 1f, 450 + (nextRandom() * 250).toInt(), kind))
        }
    }

    /** L'état de la partie rangé dans des entiers, pour la garder quand l'écran tourne (les poissons en vol sont perdus). */
    fun save(): List<Int> = listOf(
        score, level, hits, combo, if (isOver) 1 else 0,
        manX.toRawBits(), knockTarget.toRawBits(), phaseX.toRawBits(), phaseY.toRawBits(),
        clockMs.toInt(), stunMs, cooldownMs, bannerMs, scrollPx.toRawBits(), shots
    )

    companion object {
        fun restore(data: List<Int>): FishChaseGame {
            val game = FishChaseGame()
            if (data.size < 15) return game
            game.score = data[0]
            game.level = data[1].coerceAtLeast(1)
            game.hits = data[2]
            game.combo = data[3]
            game.isOver = data[4] == 1
            game.manX = Float.fromBits(data[5]).coerceIn(FishChase.MIN_X, FishChase.CATCH_X)
            game.knockTarget = Float.fromBits(data[6])
            game.phaseX = Float.fromBits(data[7])
            game.phaseY = Float.fromBits(data[8])
            game.clockMs = data[9].toLong()
            game.stunMs = data[10]
            game.cooldownMs = data[11]
            game.bannerMs = data[12]
            game.scrollPx = Float.fromBits(data[13])
            game.shots = data[14]
            return game
        }
    }
}
