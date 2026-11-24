package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Identifiant de la Course (style Temple Run) parmi les jeux prédéfinis (écran à part : voir [RunnerScreen]). */
internal const val RUNNER_ID = "builtin_runner"

/** Ce qu'on peut trouver sur la voie : des objets à ramasser, et trois sortes d'obstacles. */
internal enum class RunnerKind {
    COIN, GEM, HEART, STAR, MAGNET, SHIELD,

    /** Obstacle bas : on le saute. */
    LOW,

    /** Obstacle haut (un wagon) : impossible à sauter, il faut changer de voie. */
    TALL,

    /** Barre à hauteur de poitrine : on se baisse (on glisse) pour passer dessous. */
    BAR
}

/** Un objet posé sur la voie, à la distance [z] du départ (en mètres), sur la voie [lane] (-1, 0 ou 1). */
internal class RunnerThing(
    val kind: RunnerKind,
    val lane: Int,
    val z: Float,
    /** Nom du modèle 3D (sans extension, dans assets/runner). */
    val model: String,
    val scale: Float,
    /** Lacet du modèle, en degrés (90 : un wagon, modélisé en long sur X, se met en long sur la voie). */
    val yaw: Float,
    /** Demi-profondeur (le long de la voie) utilisée pour les contacts. */
    val halfDepth: Float,
    /** Hauteur où le modèle est posé : le centre d'un objet à ramasser, le bas d'un obstacle. */
    val y: Float,
    /** Hauteur du haut de l'obstacle : on passe dessus si le coureur est plus haut. */
    val top: Float
) {
    /** Ramassé (objet) ou déjà heurté (obstacle) : ne compte plus. */
    var taken: Boolean = false
}

/** Un modèle d'obstacle et ses dimensions. */
internal class RunnerProp(val model: String, val scale: Float, val yaw: Float, val halfDepth: Float, val top: Float)

/** Réglages de la Course. Les distances sont en mètres, les durées en secondes. */
internal object Runner {
    /** Écart entre deux voies (la voie du milieu est en 0). */
    const val LANE_W = 1.25f

    const val START_SPEED = 8f
    const val MAX_SPEED = 17f
    const val SPEED_PER_M = 0.011f

    /** Vitesse du coureur d'une voie à l'autre. */
    const val LATERAL_SPEED = 9f

    const val GRAVITY = 26f
    const val JUMP_V = 9.5f

    /** Vitesse de chute quand on demande à se baisser en l'air. */
    const val FAST_FALL = 16f

    const val SLIDE_S = 0.75f
    const val STAND_H = 1.0f
    const val SLIDE_H = 0.45f
    const val PLAYER_HALF_DEPTH = 0.25f

    /** La barre à passer en glissant : elle est entre ces deux hauteurs. */
    const val BAR_BOTTOM = 0.62f
    const val BAR_TOP = 1.12f

    /** Distance d'avance à laquelle la voie est générée, et distance libre au départ. */
    const val AHEAD_M = 100f
    const val FREE_START_M = 45f

    /** Au plus cet écart latéral entre le coureur et un objet pour le ramasser, ou pour heurter un obstacle. */
    const val PICK_REACH = 0.6f
    const val MAGNET_REACH = 2.6f
    const val HIT_REACH = 0.8f

    const val COIN_POINTS = 5
    const val GEM_POINTS = 25
    const val GEM_COINS = 5
    const val MAX_LIVES = 3

    const val STAR_S = 10f
    const val MAGNET_S = 8f
    const val INVULN_AFTER_HIT_S = 2f
    const val INVULN_AFTER_SHIELD_S = 1.2f

    /** Écart minimal entre deux rangées d'obstacles. */
    const val MIN_GAP_M = 18f

    /** Hauteur du centre d'une pièce posée au sol, et en l'air (au-dessus d'un obstacle bas). */
    const val COIN_GROUND_Y = 0.6f
    const val COIN_AIR_Y = 1.45f

    val LOW = listOf(
        RunnerProp("rail_crate_stack", 1.0f, 0f, 0.5f, 0.95f),
        RunnerProp("rail_luggage_stack", 1.0f, 0f, 0.5f, 1.0f),
        RunnerProp("rail_mail_bags", 1.3f, 0f, 0.5f, 0.8f),
        RunnerProp("rail_coal_pile", 0.8f, 0f, 0.55f, 0.8f),
        RunnerProp("rail_cat", 2.2f, 0f, 0.35f, 0.85f),
        RunnerProp("rail_milk_churn", 1.4f, 0f, 0.3f, 1.05f)
    )

    val TALL = listOf(
        RunnerProp("rail_carriage", 0.75f, 90f, 1.48f, 99f),
        RunnerProp("rail_locomotive", 0.75f, 90f, 1.28f, 99f)
    )

    /** La barre : une rambarde posée à hauteur de poitrine, d'une voie de large. */
    val BAR = RunnerProp("rail_fence_rail", 0.47f, 0f, 0.25f, BAR_TOP)

    /** Les modèles des objets à ramasser : nom, échelle. */
    fun pickupModel(kind: RunnerKind): Pair<String, Float> = when (kind) {
        RunnerKind.COIN -> "item_coin" to 0.085f
        RunnerKind.GEM -> "item_gem" to 0.095f
        RunnerKind.HEART -> "item_heart" to 0.095f
        RunnerKind.STAR -> "item_star" to 0.095f
        RunnerKind.MAGNET -> "item_lightning" to 0.1f
        RunnerKind.SHIELD -> "item_shield" to 0.1f
        else -> "item_coin" to 0.085f
    }

    /** Le décor de la gare (voir RunnerStage) : les modèles du décor, les obstacles, la barre et les objets à ramasser. */
    private val DECOR_MODELS = listOf(
        "rail_track_straight", "rail_track_short", "rail_platform_straight", "rail_tree_round", "rail_bush",
        "rail_telegraph_pole", "rail_station_lamp", "rail_station_building"
    )

    /** Tous les modèles 3D à charger (noms sans extension, dans assets/runner). */
    fun allModels(): List<String> =
        (DECOR_MODELS + LOW.map { it.model } + TALL.map { it.model } + BAR.model +
            RunnerKind.entries.filter { it.ordinal <= RunnerKind.SHIELD.ordinal }.map { pickupModel(it).first }).distinct()

    /** Vitesse à la distance [dist]. */
    fun speedAt(dist: Float): Float = min(MAX_SPEED, START_SPEED + dist * SPEED_PER_M)
}

/**
 * La Course : un coureur qui avance sans cesse sur trois voies (ligne droite). On change de voie, on saute par-dessus les
 * obstacles bas, on se baisse sous les barres et on évite les wagons. On ramasse des pièces et des bonus (bouclier, étoile
 * = points doublés, éclair = aimant à pièces, cœur = une vie de plus). La partie s'arrête au premier choc sans vie ni bouclier.
 *
 * Toute la logique est ici, sans rien d'Android : l'écran (RunnerScreen) lit l'état et le dessine. [seed] fixe la voie générée
 * (utile aux tests).
 */
internal class RunnerGame(seed: Long = Random.nextLong()) {
    private val rnd = Random(seed)

    /** Distance parcourue. */
    var dist = 0f
        private set
    var speed = Runner.START_SPEED
        private set

    /** Voie visée (-1, 0, 1) et position latérale réelle (en mètres : lisse d'une voie à l'autre). */
    var lane = 0
        private set
    var x = 0f
        private set

    /** Hauteur des pieds et vitesse verticale. */
    var y = 0f
        private set
    private var vy = 0f
    private var slideLeft = 0f
    private var slideQueued = false

    var lives = 1
        private set
    var shield = false
        private set
    var starLeft = 0f
        private set
    var magnetLeft = 0f
        private set
    var invuln = 0f
        private set

    /** Temps restant de l'animation « touché » (le coureur a perdu une vie sans mourir). */
    var hurtLeft = 0f
        private set
    var coins = 0
        private set
    var gems = 0
        private set

    /** Nombre de chocs subis (pour déclencher un effet à chaque hausse). */
    var hits = 0
        private set
    private var scoreD = 0.0
    val score: Int get() = scoreD.toInt()
    var isOver = false
        private set

    /** Temps de jeu écoulé : l'écran s'en sert pour animer. */
    var clock = 0f
        private set

    /** Les objets de la voie, de ceux qui viennent d'être passés à ceux qui sont loin devant. */
    val things = ArrayList<RunnerThing>()
    private var nextRowZ = Runner.FREE_START_M

    val isSliding: Boolean get() = slideLeft > 0f && y <= 0f
    val isAirborne: Boolean get() = y > 0f
    val top: Float get() = y + if (isSliding) Runner.SLIDE_H else Runner.STAND_H

    init {
        generateAhead()
    }

    fun moveLeft() {
        if (!isOver) lane = max(-1, lane - 1)
    }

    fun moveRight() {
        if (!isOver) lane = min(1, lane + 1)
    }

    fun jump() {
        if (isOver || y > 0f) return
        vy = Runner.JUMP_V
        y = 0.0001f
        slideLeft = 0f
        slideQueued = false
    }

    /** Se baisse. En l'air, on retombe plus vite et on glisse dès l'atterrissage. */
    fun slide() {
        if (isOver) return
        if (y > 0f) {
            vy = min(vy, -Runner.FAST_FALL)
            slideQueued = true
        } else {
            slideLeft = Runner.SLIDE_S
        }
    }

    /** Fait avancer la partie de [ms] millisecondes (au plus 100 : après un à-coup, on ne saute pas plusieurs mètres d'un coup). */
    fun tick(ms: Long) {
        if (isOver) return
        var left = (ms / 1000f).coerceIn(0f, 0.1f)
        while (left > 1e-6f && !isOver) {
            val dt = min(left, 0.02f)
            step(dt)
            left -= dt
        }
    }

    private fun step(dt: Float) {
        clock += dt
        speed = Runner.speedAt(dist)
        val dd = speed * dt
        dist += dd
        scoreD += dd * multiplier()

        val target = lane * Runner.LANE_W
        val dx = target - x
        val move = Runner.LATERAL_SPEED * dt
        x = if (abs(dx) <= move) target else x + (if (dx > 0f) move else -move)

        if (y > 0f || vy > 0f) {
            vy -= Runner.GRAVITY * dt
            y += vy * dt
            if (y <= 0f) {
                y = 0f
                vy = 0f
                if (slideQueued) {
                    slideLeft = Runner.SLIDE_S
                    slideQueued = false
                }
            }
        }
        if (slideLeft > 0f) slideLeft = max(0f, slideLeft - dt)
        if (starLeft > 0f) starLeft = max(0f, starLeft - dt)
        if (magnetLeft > 0f) magnetLeft = max(0f, magnetLeft - dt)
        if (invuln > 0f) invuln = max(0f, invuln - dt)
        if (hurtLeft > 0f) hurtLeft = max(0f, hurtLeft - dt)

        generateAhead()
        collide()
        // Ce qui est loin derrière ne sert plus.
        var drop = 0
        while (drop < things.size && things[drop].z < dist - 10f) drop++
        if (drop > 0) things.subList(0, drop).clear()
    }

    private fun multiplier(): Double = if (starLeft > 0f) 2.0 else 1.0

    private fun collide() {
        val magnet = magnetLeft > 0f
        for (t in things) {
            if (t.taken) continue
            val pickup = t.kind.ordinal <= RunnerKind.SHIELD.ordinal
            val pulled = magnet && (t.kind == RunnerKind.COIN || t.kind == RunnerKind.GEM)
            val window = t.halfDepth + Runner.PLAYER_HALF_DEPTH + if (pulled) 2.5f else 0f
            if (abs(t.z - dist) > window) continue
            val side = abs(x - t.lane * Runner.LANE_W)
            if (pickup) {
                if (side > (if (pulled) Runner.MAGNET_REACH else Runner.PICK_REACH)) continue
                if (!pulled && abs(y + 0.5f - t.y) > 0.9f) continue
                take(t)
            } else {
                if (side > Runner.HIT_REACH) continue
                val hit = when (t.kind) {
                    RunnerKind.BAR -> top > Runner.BAR_BOTTOM && y < Runner.BAR_TOP
                    else -> y < t.top
                }
                if (hit) crash(t)
            }
        }
    }

    private fun take(t: RunnerThing) {
        t.taken = true
        when (t.kind) {
            RunnerKind.COIN -> {
                coins++
                scoreD += Runner.COIN_POINTS * multiplier()
            }
            RunnerKind.GEM -> {
                gems++
                coins += Runner.GEM_COINS
                scoreD += Runner.GEM_POINTS * multiplier()
            }
            RunnerKind.HEART -> lives = min(Runner.MAX_LIVES, lives + 1)
            RunnerKind.STAR -> starLeft = Runner.STAR_S
            RunnerKind.MAGNET -> magnetLeft = Runner.MAGNET_S
            RunnerKind.SHIELD -> shield = true
            else -> Unit
        }
    }

    private fun crash(t: RunnerThing) {
        t.taken = true
        // Juste après un choc, on traverse les obstacles suivants sans rien perdre.
        if (invuln > 0f) return
        hits++
        when {
            shield -> {
                shield = false
                invuln = Runner.INVULN_AFTER_SHIELD_S
            }
            lives > 1 -> {
                lives--
                invuln = Runner.INVULN_AFTER_HIT_S
                hurtLeft = 0.6f
            }
            else -> isOver = true
        }
    }

    private fun generateAhead() {
        while (nextRowZ < dist + Runner.AHEAD_M) addRow(nextRowZ)
    }

    private fun thing(kind: RunnerKind, lane: Int, z: Float, p: RunnerProp, y: Float = 0f) {
        things.add(RunnerThing(kind, lane, z, p.model, p.scale, p.yaw, p.halfDepth, y, p.top))
    }

    private fun low(lane: Int, z: Float) = thing(RunnerKind.LOW, lane, z, Runner.LOW[rnd.nextInt(Runner.LOW.size)])

    private fun tall(lane: Int, z: Float) = thing(RunnerKind.TALL, lane, z, Runner.TALL[rnd.nextInt(Runner.TALL.size)])

    private fun bar(lane: Int, z: Float) = thing(RunnerKind.BAR, lane, z, Runner.BAR, Runner.BAR_BOTTOM)

    private fun pickup(kind: RunnerKind, lane: Int, z: Float, y: Float) {
        val (model, scale) = Runner.pickupModel(kind)
        things.add(RunnerThing(kind, lane, z, model, scale, 0f, 0.4f, y, 0f))
    }

    /** Une rangée d'obstacles en [z], puis des pièces (et parfois un bonus) jusqu'à la rangée suivante. */
    private fun addRow(z: Float) {
        val hard = min(1f, dist / 1500f)
        val gap = max(Runner.MIN_GAP_M, speed * 2.1f) + rnd.nextFloat() * 6f
        val busy = BooleanArray(3) // voie (-1, 0, 1 → 0, 1, 2) occupée par un obstacle de cette rangée
        fun mark(l: Int) { busy[l + 1] = true }
        val lane = rnd.nextInt(3) - 1
        val other = ((lane + 1 + 1 + rnd.nextInt(2)) % 3) - 1 // l'une des deux autres voies
        var airCoinsLane: Int? = null
        val r = rnd.nextFloat()
        when {
            r < 0.30f -> {
                low(lane, z); mark(lane)
                if (hard > 0.4f && rnd.nextFloat() < hard) { low(other, z); mark(other) } else airCoinsLane = lane
            }
            r < 0.55f -> {
                tall(lane, z); mark(lane)
                if (hard > 0.5f && rnd.nextFloat() < hard) { low(other, z); mark(other) }
            }
            r < 0.70f -> for (l in -1..1) { bar(l, z); mark(l) }
            r < 0.85f -> for (l in -1..1) if (l != lane) { tall(l, z); mark(l) }
            else -> for (l in -1..1) { low(l, z); mark(l) }
        }
        // Des pièces en l'air au-dessus d'un obstacle bas isolé : elles récompensent le saut.
        airCoinsLane?.let { l ->
            for (k in -1..1) pickup(RunnerKind.COIN, l, z + k * 1.7f, Runner.COIN_AIR_Y)
        }
        val free = (-1..1).filter { !busy[it + 1] }
        val coinLane = if (free.isEmpty()) lane else free[rnd.nextInt(free.size)]
        var cz = z + 4.5f
        while (cz < z + gap - 4f) {
            pickup(RunnerKind.COIN, coinLane, cz, Runner.COIN_GROUND_Y)
            cz += 1.7f
        }
        // Un bonus au milieu de la ligne de pièces, de temps en temps.
        if (rnd.nextFloat() < 0.22f) {
            val kind = when (rnd.nextInt(10)) {
                0, 1 -> RunnerKind.HEART
                2, 3, 4 -> RunnerKind.SHIELD
                5, 6 -> RunnerKind.STAR
                7, 8 -> RunnerKind.MAGNET
                else -> RunnerKind.GEM
            }
            pickup(kind, coinLane, z + gap / 2f, Runner.COIN_GROUND_Y + 0.1f)
        }
        nextRowZ = z + gap
    }
}
