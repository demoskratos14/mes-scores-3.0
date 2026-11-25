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

    /** L'éclair : super-vitesse, invincible aux obstacles, et les virages se prennent tout seuls. */
    BOOST,

    /** Rocher au sol : on le saute. */
    LOW,

    /** Wagon : impossible à sauter, il faut changer de voie. */
    TALL,

    /** Portique à hauteur de tête : on se baisse (on glisse) pour passer dessous. */
    BAR;

    /** Vrai pour ce qu'on ramasse (pièces et bonus), faux pour les obstacles. */
    val isPickup: Boolean get() = ordinal <= BOOST.ordinal
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
    var y: Float,
    /** Hauteur du haut de l'obstacle : on passe dessus si le coureur est plus haut. */
    val top: Float
) {
    /** Ramassé (objet) ou déjà heurté (obstacle) : ne compte plus. */
    var taken: Boolean = false

    /** Position latérale réelle (en mètres) : égale à celle de la voie, sauf pour une pièce que l'aimant fait glisser vers le coureur. */
    var x: Float = lane * Runner.LANE_W

    /** Obstacle défoncé par le coureur en super-vitesse : on ne le dessine plus. */
    var smashed: Boolean = false
}

/** Un modèle d'obstacle et ses dimensions. */
internal class RunnerProp(val model: String, val scale: Float, val yaw: Float, val halfDepth: Float, val top: Float)

/** Réglages de la Course. Les distances sont en mètres, les durées en secondes. */
internal object Runner {
    /** Écart entre deux voies (la voie du milieu est en 0). */
    const val LANE_W = 1.25f

    /** La vitesse part de START_SPEED et grimpe sans cesse (SPEED_PER_M par mètre parcouru) jusqu'à MAX_SPEED. */
    const val START_SPEED = 8f
    const val MAX_SPEED = 28f
    const val SPEED_PER_M = 0.0125f

    /** Distance à laquelle la difficulté (rangées plus serrées, motifs plus durs, virages plus fréquents) est au maximum. */
    const val HARD_AT_M = 1800f

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

    /** Le portique à passer en glissant : il barre tout ce qui est entre ces deux hauteurs (on ne peut pas non plus le sauter). */
    const val BAR_BOTTOM = 0.72f
    const val BAR_TOP = 2.31f

    /** Distance d'avance à laquelle la voie est générée, et distance libre au départ. */
    const val AHEAD_M = 110f
    const val FREE_START_M = 45f

    /** Au plus cet écart latéral entre le coureur et un objet pour le ramasser, ou pour heurter un obstacle. */
    const val PICK_REACH = 0.6f
    const val HIT_REACH = 0.8f

    const val COIN_POINTS = 5
    const val GEM_POINTS = 25
    const val GEM_COINS = 5
    const val START_LIVES = 3
    const val MAX_LIVES = 5

    /** Super-vitesse : vitesse ajoutée, temps pour l'atteindre, et temps d'invulnérabilité laissé après elle. */
    const val BOOST_SPEED = 14f
    const val BOOST_RAMP_S = 0.6f
    const val BOOST_GRACE_S = 1.2f

    /** Vitesse à laquelle l'aimant fait glisser une pièce de côté, et monter ou descendre, vers le coureur (mètres par seconde). */
    const val PULL_SPEED = 22f
    const val PULL_RISE = 9f
    const val INVULN_AFTER_HIT_S = 2f
    const val INVULN_AFTER_SHIELD_S = 1.2f

    /** Écart minimal entre deux rangées d'obstacles. */
    const val MIN_GAP_M = 18f

    /** Temps de réaction laissé entre deux rangées : de GAP_S_EASY au début à GAP_S_HARD à la difficulté maximale. */
    const val GAP_S_EASY = 2.3f
    const val GAP_S_HARD = 1.3f

    /** Hauteur du centre d'une pièce posée au sol, et en l'air (au-dessus d'un rocher). */
    const val COIN_GROUND_Y = 0.6f
    const val COIN_AIR_Y = 1.45f

    // --- La voie ferrée et ses virages ---

    /** Longueur d'une unité de voie (un petit tronçon) : tout le tracé est un nombre entier d'unités. */
    const val UNIT = 1.6f

    /** Demi-largeur de la voie : le carré du virage fait cette largeur de côté. */
    const val JUNCTION_HALF = 1.6f

    /** Début virtuel du premier tronçon : la voie existe déjà derrière le coureur au départ. */
    const val TRACK_START = -9.6f - JUNCTION_HALF

    /** Distance entre le départ et le premier virage, en unités de voie. */
    const val FIRST_CORNER_UNITS = 100

    /** Pas d'obstacle ni de pièce de décor dans cette zone autour d'un virage (avant, après). */
    const val CORNER_CLEAR_BEFORE = 20f
    const val CORNER_CLEAR_AFTER = 20f

    /** Pour tourner, il faut glisser dans le bon sens dans cette distance avant le virage au plus (selon la vitesse). */
    const val TURN_WINDOW_MIN = 9f
    const val TURN_WINDOW_S = 0.8f

    /** Vitesse de rotation du monde après un virage, en degrés par seconde. */
    const val TURN_ANIM_DEG_S = 320f

    /** Le rocher : un tas de charbon, bas, qu'on saute. */
    val LOW = listOf(RunnerProp("rail_coal_pile", 0.8f, 0f, 0.55f, 0.82f))

    /** Le wagon, trop haut pour être sauté : il faut changer de voie. */
    val TALL = listOf(RunnerProp("rail_carriage", 0.75f, 90f, 1.48f, 99f))

    /** Le portique, d'une voie de large, à passer en se baissant. */
    val BAR = RunnerProp("rail_gate", 1f, 0f, 0.3f, BAR_TOP)

    /** Les modèles des objets à ramasser : nom, échelle. */
    fun pickupModel(kind: RunnerKind): Pair<String, Float> = when (kind) {
        RunnerKind.COIN -> "item_coin" to 0.085f
        RunnerKind.GEM -> "item_gem" to 0.095f
        RunnerKind.HEART -> "item_heart" to 0.095f
        RunnerKind.STAR -> "item_star" to 0.095f
        RunnerKind.MAGNET -> "item_orb" to 0.045f
        RunnerKind.BOOST -> "item_lightning" to 0.1f
        RunnerKind.SHIELD -> "item_shield" to 0.1f
        else -> "item_coin" to 0.085f
    }

    /** Le décor de la gare (voir RunnerStage). */
    private val DECOR_MODELS = listOf(
        "rail_track_straight", "rail_track_short", "rail_platform_straight", "rail_tree_round", "rail_bush",
        "rail_telegraph_pole", "rail_station_lamp", "rail_station_building"
    )

    /** Tous les modèles 3D à charger (noms sans extension, dans assets/runner). */
    fun allModels(): List<String> =
        (DECOR_MODELS + LOW.map { it.model } + TALL.map { it.model } + BAR.model +
            RunnerKind.entries.filter { it.isPickup }.map { pickupModel(it).first }).distinct()

    /** Vitesse à la distance [dist] : elle augmente tout le long de la partie, jusqu'au maximum. */
    fun speedAt(dist: Float): Float = min(MAX_SPEED, START_SPEED + dist * SPEED_PER_M)

    /** Difficulté de 0 (le départ) à 1 (à partir de [HARD_AT_M]). */
    fun hardAt(dist: Float): Float = min(1f, max(0f, dist / HARD_AT_M))
}

/**
 * Le tracé de la voie : une ligne droite coupée de virages à angle droit. Le tronçon 0 part du début, chaque virage ouvre le
 * tronçon suivant. Tout est mesuré le long du tracé (la distance parcourue) ; cette classe donne la position réelle, dans le
 * plan, d'un point du tracé (pour dessiner). Cap 0 : vers -Z ; chaque quart de tour à droite (+1) ou à gauche (-1) change de cap.
 */
internal class RunnerPath {
    /** Distance du début de chaque tronçon (celle du virage qui l'ouvre). */
    private val starts = ArrayList<Float>()
    private val turns = ArrayList<Int>()
    private val quarters = ArrayList<Int>()
    private val ox = ArrayList<Double>()
    private val oz = ArrayList<Double>()

    init {
        starts.add(Runner.TRACK_START)
        turns.add(0)
        quarters.add(0)
        ox.add(0.0)
        oz.add(0.0)
    }

    /** Nombre de tronçons (le premier plus un par virage). */
    val size: Int get() = starts.size

    /** Début du tronçon [i] : la distance du virage qui l'ouvre. */
    fun start(i: Int): Float = starts[i]

    /** Sens du virage qui ouvre le tronçon [i] : -1 gauche, 1 droite (0 pour le premier tronçon). */
    fun turn(i: Int): Int = turns[i]

    /** Cap du tronçon [i], en quarts de tour. */
    fun quarter(i: Int): Int = quarters[i]

    val lastStart: Float get() = starts.last()

    /** Ajoute un virage [units] unités de voie après le dernier, dans le sens [dir]. */
    fun addCorner(units: Int, dir: Int) {
        val i = starts.size - 1
        val s = starts[i] + units * Runner.UNIT
        val f = floatArrayOf(0f, 0f)
        forward(quarters[i], f)
        val len = (s - starts[i]).toDouble()
        ox.add(ox[i] + len * f[0])
        oz.add(oz[i] + len * f[1])
        starts.add(s)
        turns.add(dir)
        quarters.add(quarters[i] + dir)
    }

    /** Le tronçon qui contient la distance [s]. */
    fun segmentAt(s: Float): Int {
        var i = starts.size - 1
        while (i > 0 && starts[i] > s) i--
        return i
    }

    /**
     * Position dans le plan (x dans out[0], z dans out[1]) du point à la distance [s] du tronçon [seg], décalé de [lateral] vers
     * la droite. Le tronçon est prolongé tout droit au besoin (le carré d'un virage dépasse le virage de la demi-voie).
     */
    fun position(seg: Int, s: Float, lateral: Float, out: DoubleArray) {
        val q = Math.floorMod(quarters[seg], 4)
        val d = (s - starts[seg]).toDouble()
        out[0] = ox[seg] + d * FWD_X[q] + lateral * RIGHT_X[q]
        out[1] = oz[seg] + d * FWD_Z[q] + lateral * RIGHT_Z[q]
    }

    private fun forward(quarter: Int, out: FloatArray) {
        val q = Math.floorMod(quarter, 4)
        out[0] = FWD_X[q].toFloat()
        out[1] = FWD_Z[q].toFloat()
    }

    companion object {
        // Pour un cap de 0, 1, 2, 3 quarts de tour : la direction d'avance et la droite (dans le plan x, z).
        val FWD_X = doubleArrayOf(0.0, 1.0, 0.0, -1.0)
        val FWD_Z = doubleArrayOf(-1.0, 0.0, 1.0, 0.0)
        val RIGHT_X = doubleArrayOf(1.0, 0.0, -1.0, 0.0)
        val RIGHT_Z = doubleArrayOf(0.0, 1.0, 0.0, -1.0)
    }
}

/**
 * La Course : un coureur qui avance sans cesse sur trois voies. On change de voie, on saute par-dessus les rochers, on se baisse
 * sous les portiques et on évite les wagons. Aux virages à angle droit, il faut glisser dans le bon sens juste avant le coin,
 * sinon on s'écrase contre le mur. On ramasse des pièces et des bonus (bouclier, étoile = points doublés, orbe bleue = aimant, éclair = super-vitesse : invincible, et les virages se prennent seuls, cœur = une vie de plus).
 * La partie s'arrête au premier choc sans vie ni bouclier. La vitesse et la difficulté montent tout le long de la partie. Les
 * niveaux d'amélioration ([levels]) allongent les bonus et étendent l'aimant.
 *
 * Toute la logique est ici, sans rien d'Android : l'écran (RunnerScreen) lit l'état et le dessine. [seed] fixe la voie générée
 * (utile aux tests).
 */
internal class RunnerGame(
    seed: Long = Random.nextLong(),
    startLevels: RunnerLevels = RunnerLevels(),
    startLives: Int = Runner.START_LIVES
) {
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

    var lives = startLives
        private set

    /** Les niveaux d'amélioration, achetés avec les pièces de cette partie (ils repartent de zéro à la partie suivante). */
    var levels = startLevels
        private set
    private var spent = 0

    /** Les pièces attrapées qu'il reste à dépenser (le score, lui, compte toutes les pièces attrapées). */
    val purse: Int get() = coins - spent

    /** Achète le niveau suivant de [upgrade] avec les pièces de la partie : faux s'il est au maximum ou si les pièces manquent. */
    fun buy(upgrade: RunnerUpgrade): Boolean {
        val cost = RunnerShop.cost(levels.of(upgrade)) ?: return false
        if (cost > purse) return false
        spent += cost
        levels = levels.withLevel(upgrade, levels.of(upgrade) + 1)
        return true
    }
    var shield = false
        private set
    var starLeft = 0f
        private set
    var magnetLeft = 0f
        private set

    /** Temps restant de la super-vitesse (éclair). */
    var boostLeft = 0f
        private set
    private var boostBlend = 0f
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

    /** Le tracé de la voie (ses virages sont générés loin devant le coureur). */
    val path = RunnerPath()

    /** Le tronçon du tracé où se trouve le coureur, et le nombre de virages pris. */
    var seg = 0
        private set
    var turnsTaken = 0
        private set

    /** Sens du virage demandé (-1, 0, 1) : il se fait à l'arrivée au coin. */
    private var turnQueued = 0

    /**
     * Rotation restante du monde à l'écran, en degrés : après un virage, elle part de ±90 et revient à 0, ce qui fait tourner
     * la vue sur le nouveau cap au lieu de la faire sauter.
     */
    var turnVisual = 0f
        private set

    val isSliding: Boolean get() = slideLeft > 0f && y <= 0f
    val isAirborne: Boolean get() = y > 0f
    val top: Float get() = y + if (isSliding) Runner.SLIDE_H else Runner.STAND_H

    /** Distance du prochain virage non pris, ou null s'il n'y en a pas devant. */
    val nextCornerZ: Float? get() = if (seg + 1 < path.size) path.start(seg + 1) else null

    /** Sens (-1 gauche, 1 droite) du prochain virage non pris, ou 0. */
    val nextCornerDir: Int get() = if (seg + 1 < path.size) path.turn(seg + 1) else 0

    /** Distance, avant le prochain virage, à partir de laquelle un geste de côté compte comme un virage. */
    val turnWindow: Float get() = min(Runner.CORNER_CLEAR_BEFORE - 2f, max(Runner.TURN_WINDOW_MIN, speed * Runner.TURN_WINDOW_S))

    /** Vrai quand le prochain virage est assez près pour qu'un geste de côté le prenne. */
    val turnIsOpen: Boolean
        get() {
            val c = nextCornerZ ?: return false
            return c - dist <= turnWindow
        }

    init {
        generateAhead()
    }

    fun moveLeft() = sideways(-1)

    fun moveRight() = sideways(1)

    /** Un geste de côté : s'il est dans le sens du virage tout proche, il le prend ; sinon il change de voie. */
    private fun sideways(dir: Int) {
        if (isOver) return
        if (turnIsOpen && nextCornerDir == dir) {
            turnQueued = dir
            return
        }
        lane = max(-1, min(1, lane + dir))
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
        boostBlend = if (boostLeft > 0f) min(1f, boostBlend + dt / Runner.BOOST_RAMP_S) else max(0f, boostBlend - dt / Runner.BOOST_RAMP_S)
        speed = Runner.speedAt(dist) + Runner.BOOST_SPEED * boostBlend
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
        if (boostLeft > 0f) {
            boostLeft = max(0f, boostLeft - dt)
            // À la fin, on est encore protégé un instant : on peut se trouver au milieu d'un obstacle.
            if (boostLeft == 0f) invuln = max(invuln, Runner.BOOST_GRACE_S)
        }
        if (turnVisual != 0f) {
            val spin = Runner.TURN_ANIM_DEG_S * dt
            turnVisual = if (abs(turnVisual) <= spin) 0f else turnVisual - (if (turnVisual > 0f) spin else -spin)
        }

        generateAhead()
        cornerCheck()
        if (!isOver) collide(dt)
        // Ce qui est loin derrière ne sert plus.
        var drop = 0
        while (drop < things.size && things[drop].z < dist - 10f) drop++
        if (drop > 0) things.subList(0, drop).clear()
    }

    private fun multiplier(): Double = if (starLeft > 0f) 2.0 else 1.0

    /** Arrivé au coin : on tourne si le geste a été fait dans le bon sens, sinon on s'écrase contre le mur. */
    private fun cornerCheck() {
        val c = nextCornerZ ?: return
        if (dist < c) return
        val dir = nextCornerDir
        val ok = boostLeft > 0f || turnQueued == dir
        turnQueued = 0
        if (!ok && crashNow()) return
        // Sans vie ni bouclier perdu, on repart quand même dans le bon sens.
        seg++
        turnsTaken++
        turnVisual = -dir * 90f
    }

    private fun collide(dt: Float) {
        val magnet = magnetLeft > 0f
        val reachLat = RunnerShop.magnetLateral(levels.magnet)
        val reachAhead = RunnerShop.magnetAhead(levels.magnet)
        val ghost = boostLeft > 0f
        for (t in things) {
            if (t.taken) continue
            val rel = t.z - dist
            if (t.kind.isPickup) {
                // L'aimant fait glisser les pièces vers le coureur, de loin ; elles sont ramassées quand elles arrivent sur lui.
                if (magnet && (t.kind == RunnerKind.COIN || t.kind == RunnerKind.GEM) && rel < reachAhead && rel > -1f && abs(t.x - x) <= reachLat) {
                    pull(t, dt)
                }
                if (abs(rel) > t.halfDepth + Runner.PLAYER_HALF_DEPTH) continue
                if (abs(x - t.x) > Runner.PICK_REACH) continue
                if (abs(y + 0.5f - t.y) > 0.9f) continue
                take(t)
            } else {
                if (abs(rel) > t.halfDepth + Runner.PLAYER_HALF_DEPTH) continue
                if (abs(x - t.x) > Runner.HIT_REACH) continue
                val hit = when (t.kind) {
                    RunnerKind.BAR -> top > Runner.BAR_BOTTOM && y < Runner.BAR_TOP
                    else -> y < t.top
                }
                if (hit) {
                    t.taken = true
                    // En super-vitesse, on traverse tout : l'obstacle vole en éclats.
                    if (ghost) t.smashed = true else crashNow()
                }
            }
        }
    }

    private fun pull(t: RunnerThing, dt: Float) {
        val dx = x - t.x
        val sx = Runner.PULL_SPEED * dt
        t.x += if (abs(dx) <= sx) dx else if (dx > 0f) sx else -sx
        val dy = y + 0.5f - t.y
        val sy = Runner.PULL_RISE * dt
        t.y += if (abs(dy) <= sy) dy else if (dy > 0f) sy else -sy
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
            RunnerKind.STAR -> starLeft = RunnerShop.starSeconds(levels.star).toFloat()
            RunnerKind.MAGNET -> magnetLeft = RunnerShop.magnetSeconds(levels.magnet).toFloat()
            RunnerKind.BOOST -> boostLeft = RunnerShop.boostSeconds(levels.boost).toFloat()
            RunnerKind.SHIELD -> shield = true
            else -> Unit
        }
    }

    /** Un choc : le bouclier ou une vie de réserve l'absorbe, sinon c'est fini. Renvoie vrai si la partie est finie. */
    private fun crashNow(): Boolean {
        // Juste après un choc, on traverse les obstacles suivants sans rien perdre.
        if (invuln > 0f || boostLeft > 0f) return false
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
        return isOver
    }

    // --- Génération de la voie ---

    private fun generateAhead() {
        val limit = dist + Runner.AHEAD_M
        // Les virages sont décidés loin devant, avant les rangées d'obstacles qui doivent les éviter.
        while (path.lastStart < limit + 60f) addCorner()
        while (nextRowZ < limit) {
            val skipTo = cornerZoneEnd(nextRowZ)
            if (skipTo != null) {
                nextRowZ = skipTo
                continue
            }
            addRow(nextRowZ)
        }
    }

    private fun addCorner() {
        val hard = Runner.hardAt(path.lastStart)
        val units = if (path.size == 1) {
            Runner.FIRST_CORNER_UNITS
        } else {
            // De 88 à 232 m au début, de 60 à 130 m à la difficulté maximale.
            val lo = (55 - 18 * hard).toInt()
            val hi = (145 - 64 * hard).toInt()
            lo + rnd.nextInt(hi - lo + 1)
        }
        path.addCorner(units, if (rnd.nextBoolean()) 1 else -1)
    }

    /** Si [z] est dans la zone d'un virage où l'on ne pose rien, la distance où elle finit. */
    private fun cornerZoneEnd(z: Float): Float? {
        for (i in 1 until path.size) {
            val c = path.start(i)
            if (z > c - Runner.CORNER_CLEAR_BEFORE && z < c + Runner.CORNER_CLEAR_AFTER) return c + Runner.CORNER_CLEAR_AFTER
        }
        return null
    }

    private fun thing(kind: RunnerKind, lane: Int, z: Float, p: RunnerProp, y: Float = 0f) {
        things.add(RunnerThing(kind, lane, z, p.model, p.scale, p.yaw, p.halfDepth, y, p.top))
    }

    private fun low(lane: Int, z: Float) = thing(RunnerKind.LOW, lane, z, Runner.LOW[rnd.nextInt(Runner.LOW.size)])

    private fun tall(lane: Int, z: Float) = thing(RunnerKind.TALL, lane, z, Runner.TALL[rnd.nextInt(Runner.TALL.size)])

    private fun bar(lane: Int, z: Float) = thing(RunnerKind.BAR, lane, z, Runner.BAR, 0f)

    private fun pickup(kind: RunnerKind, lane: Int, z: Float, y: Float) {
        if (cornerZoneEnd(z) != null) return
        val (model, scale) = Runner.pickupModel(kind)
        things.add(RunnerThing(kind, lane, z, model, scale, 0f, 0.4f, y, 0f))
    }

    /** Les sept motifs de rangée : le début n'en connaît que trois simples, les autres arrivent avec la difficulté. */
    private fun pickPattern(hard: Float): Int {
        val w = floatArrayOf(
            0.9f - 0.45f * hard, // 0 : un rocher
            0.7f, // 1 : un wagon
            if (dist > 120f) 0.45f else 0f, // 2 : des portiques sur les trois voies
            0.9f * hard, // 3 : deux wagons côte à côte
            0.5f * hard, // 4 : des rochers sur les trois voies
            0.7f * hard, // 5 : un wagon et un rocher
            0.5f * hard // 6 : deux rochers
        )
        var r = rnd.nextFloat() * w.sum()
        for (i in w.indices) {
            if (r < w[i]) return i
            r -= w[i]
        }
        return 0
    }

    /** Une rangée d'obstacles en [z], puis des pièces (et parfois un bonus) jusqu'à la rangée suivante. */
    private fun addRow(z: Float) {
        val hard = Runner.hardAt(dist)
        val gapSeconds = Runner.GAP_S_EASY + (Runner.GAP_S_HARD - Runner.GAP_S_EASY) * hard
        val gap = max(Runner.MIN_GAP_M, speed * gapSeconds) + rnd.nextFloat() * 6f
        val busy = BooleanArray(3) // voie (-1, 0, 1 → 0, 1, 2) occupée par un obstacle de cette rangée
        fun mark(l: Int) { busy[l + 1] = true }
        val lane = rnd.nextInt(3) - 1
        val other = ((lane + 1 + 1 + rnd.nextInt(2)) % 3) - 1 // l'une des deux autres voies
        var airCoinsLane: Int? = null
        when (pickPattern(hard)) {
            0 -> { low(lane, z); mark(lane); airCoinsLane = lane }
            1 -> { tall(lane, z); mark(lane) }
            2 -> for (l in -1..1) { bar(l, z); mark(l) }
            3 -> for (l in -1..1) if (l != lane) { tall(l, z); mark(l) }
            4 -> for (l in -1..1) { low(l, z); mark(l) }
            5 -> { tall(lane, z); mark(lane); low(other, z); mark(other) }
            else -> { low(lane, z); mark(lane); low(other, z); mark(other) }
        }
        // Des pièces en l'air au-dessus d'un rocher isolé : elles récompensent le saut.
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
            val kind = when (rnd.nextInt(12)) {
                0, 1 -> RunnerKind.HEART
                2, 3, 4 -> RunnerKind.SHIELD
                5, 6 -> RunnerKind.STAR
                7, 8 -> RunnerKind.MAGNET
                9, 10 -> RunnerKind.BOOST
                else -> RunnerKind.GEM
            }
            pickup(kind, coinLane, z + gap / 2f, Runner.COIN_GROUND_Y + 0.1f)
        }
        nextRowZ = z + gap
    }
}
