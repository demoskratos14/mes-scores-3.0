package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Identifiant du Shoot'em up parmi les jeux prédéfinis (écran à part : voir [ShmupScreen]). */
internal const val SHMUP_ID = "builtin_shmup"

private const val HALF_PI = 1.5707964f
private const val TWO_PI = 6.2831855f

/**
 * Réglages et calculs du Shoot'em up à défilement vertical. Le terrain est en « unités » (9 de large sur 16 de haut) ;
 * l'écran n'a qu'à le mettre à l'échelle. Le haut du terrain est en y = 0, le vaisseau du joueur est en bas.
 *
 * Le jeu compte [STAGES] plateaux (espace, chemin de terre, base aérienne, désert). Un plateau dure [STAGE_MS], coupé en
 * [SUBLEVELS] paliers où arrivent de nouveaux ennemis ; puis un boss arrive, le défilement s'arrête, et une fois le boss
 * détruit un saut en vitesse lumière mène au plateau suivant. Après le dernier plateau, tout recommence au premier, en
 * plus difficile (un « tour » de plus).
 */
internal object Shmup {
    const val WIDTH = 9f
    const val HEIGHT = 16f
    const val LIVES = 3

    /** Taille dessinée du vaisseau (le sprite fait 96 × 112 pixels) et demi-côté de la zone qui compte pour les touches. */
    const val SHIP_W = 1.2f
    const val SHIP_H = 1.4f
    const val SHIP_HIT = 0.25f

    const val START_Y = HEIGHT - 2.2f

    // L'arme a 4 niveaux : 1 missile, 2 missiles, 3 missiles en éventail, laser + 2 missiles (le premier laser, niveau final).
    // Au laser, le bonus suivant remet l'arme du vaisseau au niveau 1 et ajoute un satellite (3 au maximum) ; quand ils sont
    // tous là, les bonus montent l'arme du vaisseau jusqu'au laser, puis celle des satellites, jusqu'au laser aussi.
    const val MAX_WEAPON = 4
    const val MAX_SATELLITES = 3
    const val FIRE_EVERY_MS = 200L
    const val MISSILE_SPEED = 14f
    const val MISSILE_HALF_W = 0.07f
    const val MISSILE_HALF_H = 0.25f
    const val MISSILE_DAMAGE = 2
    const val SPREAD_ANGLE = 0.2f
    const val LASER_EVERY_MS = 60L
    const val LASER_HALF_W = 0.12f

    /** Les satellites : distance au vaisseau, vitesse de rotation (rad/s), taille dessinée, écart des missiles et laser plus fin. */
    const val ORBIT_RADIUS = 1.1f
    const val ORBIT_SPEED = 2.4f
    const val SAT_W = 0.5f
    const val SAT_H = 0.58f
    const val SAT_SPREAD = 0.15f
    const val SAT_LASER_HALF_W = 0.08f

    const val ENEMY_BULLET_HIT = 0.12f

    /** Durée pendant laquelle le vaisseau ne peut pas être touché après un impact. */
    const val INVULN_MS = 1600L

    /** Vitesse du défilement du fond (et du sol, où les tourelles sont posées), en unités par seconde. */
    const val SCROLL_SPEED = 3f

    /** Vitesse du défilement pendant le saut en vitesse lumière. */
    const val TRANSITION_SCROLL = 14f

    /** Plus long pas de calcul : après un à-coup, rien ne doit traverser un autre objet d'un coup. */
    const val MAX_STEP_MS = 50L

    const val STAGES = 4
    const val SUBLEVELS = 3
    const val SUBLEVEL_MS = 25_000L
    const val STAGE_MS = SUBLEVEL_MS * SUBLEVELS

    /** Temps maximal laissé aux ennemis restants pour quitter le terrain avant l'arrivée du boss. */
    const val CLEAR_WAIT_MS = 10_000L
    const val BOSS_INTRO_MS = 2500L
    const val TRANSITION_MS = 4000L

    const val PICKUP_SPEED = 2.2f
    const val PICKUP_HALF = 0.3f
    const val MAX_SPARKS = 200

    /** Délai entre deux arrivées d'ennemis : plus court de palier en palier, de plateau en plateau et de tour en tour. */
    fun spawnEveryMs(stage: Int, sublevel: Int, loop: Int): Long =
        max(350L, 1250L - 150L * (sublevel - 1) - 110L * stage - 200L * loop)

    /** Chance qu'une seconde vague arrive en même temps que la première : de plus en plus fréquent, de plateau en plateau et de tour en tour. */
    fun extraWaveChance(stage: Int, loop: Int): Float = min(0.6f, 0.12f * stage + 0.25f * loop)

    /** Les types d'ennemis (sans les boss) qui peuvent arriver à ce plateau et à ce palier. */
    fun poolAt(stage: Int, sublevel: Int): List<EnemyType> =
        EnemyType.values().filter { !it.boss && it.availableAt(stage, sublevel) }

    /** Le boss du plateau [stage]. */
    fun bossOf(stage: Int): EnemyType = EnemyType.values().first { it.boss && it.stage == stage }
}

/** Où en est la partie dans un plateau. */
internal enum class ShmupPhase {
    /** Les ennemis arrivent. */
    NORMAL,

    /** Le temps du plateau est écoulé : on attend que les ennemis restants soient partis, puis le boss arrive. */
    CLEARING,

    /** Le combat contre le boss : le défilement est arrêté. */
    BOSS,

    /** Le saut en vitesse lumière vers le plateau suivant. */
    TRANSITION
}

/** Comment un ennemi se déplace. La position est calculée d'après son âge (voir [ShmupGame.Enemy.place]). */
internal enum class Move {
    /** Descend tout droit. */
    STRAIGHT,

    /** Descend en ondulant de gauche à droite. */
    SINE,

    /** Descend en zigzag aux virages secs. */
    ZIGZAG,

    /** Ralentit, repère la position du joueur puis fonce dessus. */
    DIVE,

    /** Entre par un côté et traverse l'écran en diagonale. */
    DIAGONAL,

    /** Descend jusqu'à une ligne puis traverse l'écran d'un bord à l'autre. */
    STRAFE,

    /** Descend jusqu'à une ligne, y reste un moment puis repart vers le haut. */
    HOVER,

    /** Tourne en cercle tout en descendant. */
    CIRCLE,

    /** Plonge en arc depuis un côté, puis remonte et sort. */
    SWOOP,

    /** Posé sur le sol : il défile avec le fond. */
    GROUND,

    /** Posé sur le sol et roule de gauche à droite pendant que le sol défile. */
    ROLL,

    /** Un boss : descend jusqu'à sa ligne puis se balance de gauche à droite, sans jamais partir. */
    BOSS
}

/** Comment un ennemi tire. */
internal enum class Fire {
    NONE,

    /** Un tir tout droit vers le bas. */
    STREAM,

    /** Deux tirs parallèles vers le bas. */
    TWIN,

    /** Un tir visé sur le joueur. */
    AIMED,

    /** Trois tirs en éventail serré, centré sur le joueur. */
    TRIPLE,

    /** Cinq tirs en éventail vers le bas. */
    SPREAD5,

    /** Un anneau de 8 tirs, qui tourne un peu à chaque salve. */
    RADIAL8,

    /** Un anneau de 12 tirs, qui tourne un peu à chaque salve. */
    RADIAL12,

    /** Deux tirs opposés qui tournent à chaque salve : une spirale. */
    SPIRAL
}

/**
 * Les ennemis du jeu. Chacun a son sprite (voir [ShmupScreen]), sa taille, sa résistance, son mouvement et son tir.
 * [h] est la hauteur dessinée en unités du terrain et [ratio] le rapport largeur / hauteur du sprite. [speed], [amp],
 * [lane] et [holdMs] règlent le mouvement, [fireEveryMs] et [bulletSpeed] le tir. Un type n'arrive qu'à partir du plateau
 * [stage] (0 pour le premier) et du palier [tier] de ce plateau ; [weight] est sa chance relative d'être tiré et [group] le
 * nombre d'ennemis qui arrivent ensemble. Un [boss] arrive seul à la fin du plateau [stage] et passe à son second tir
 * ([fire2], toutes les [fire2EveryMs]) quand il lui reste moins de la moitié de sa vie.
 */
internal enum class EnemyType(
    val h: Float,
    val ratio: Float,
    val hp: Int,
    val points: Int,
    val move: Move,
    val fire: Fire,
    val speed: Float = 0f,
    val amp: Float = 0f,
    val lane: Float = 2.5f,
    val holdMs: Long = 0L,
    val fireEveryMs: Long = 0L,
    val bulletSpeed: Float = 5f,
    val stage: Int = 0,
    val tier: Int = 1,
    val weight: Int = 1,
    val group: Int = 1,
    val fire2: Fire = Fire.NONE,
    val fire2EveryMs: Long = 0L,
    val boss: Boolean = false
) {
    /** Essaim en V de 4, ondule sans tirer. */
    WASP(h = 1.5f, ratio = 0.561f, hp = 1, points = 50, move = Move.SINE, fire = Fire.NONE, speed = 3.0f, amp = 1.4f, weight = 4, group = 4),
    /** Descend droit en tirant deux salves parallèles. */
    K24(h = 1.4f, ratio = 1.025f, hp = 2, points = 80, move = Move.STRAIGHT, fire = Fire.TWIN, speed = 2.0f, fireEveryMs = 1500L, bulletSpeed = 5.0f, weight = 3),
    /** Zigzag sec, tir visé. */
    D14(h = 1.8f, ratio = 0.703f, hp = 2, points = 100, move = Move.ZIGZAG, fire = Fire.AIMED, speed = 2.4f, amp = 1.6f, fireEveryMs = 1800L, bulletSpeed = 5.5f, weight = 3),
    /** Par deux : ralentit, vise votre position puis fonce dessus. */
    L9G(h = 1.6f, ratio = 0.482f, hp = 2, points = 100, move = Move.DIVE, fire = Fire.NONE, speed = 2.2f, tier = 2, weight = 3, group = 2),
    /** Entre par le côté en diagonale, tir visé. */
    D2(h = 1.9f, ratio = 0.641f, hp = 3, points = 130, move = Move.DIAGONAL, fire = Fire.AIMED, speed = 2.6f, fireEveryMs = 1600L, bulletSpeed = 5.5f, tier = 2, weight = 3),
    /** Chasseur en piqué : repère votre position, fonce dessus en mitraillant tout droit. */
    STARSHIP(h = 1.7f, ratio = 0.9f, hp = 3, points = 140, move = Move.DIVE, fire = Fire.STREAM, speed = 2.6f, fireEveryMs = 450L, bulletSpeed = 6.5f, tier = 2, weight = 2),
    /** Descend puis traverse l’écran en arrosant vers le bas. */
    K23(h = 1.9f, ratio = 0.707f, hp = 4, points = 150, move = Move.STRAFE, fire = Fire.STREAM, speed = 3.0f, lane = 2.6f, fireEveryMs = 600L, bulletSpeed = 5.5f, tier = 3, weight = 2),
    /** Plonge en arc puis remonte, tir en éventail de 3. */
    D18(h = 1.9f, ratio = 0.743f, hp = 4, points = 160, move = Move.SWOOP, fire = Fire.TRIPLE, amp = 8.5f, fireEveryMs = 1400L, bulletSpeed = 5.0f, tier = 3, weight = 2),
    /** Tourne en spirale en descendant, tir visé très rapide. */
    K27(h = 1.9f, ratio = 0.629f, hp = 5, points = 180, move = Move.CIRCLE, fire = Fire.AIMED, speed = 2.4f, amp = 1.5f, fireEveryMs = 1300L, bulletSpeed = 7.0f, tier = 3, weight = 2),
    /** S’installe en haut 6 s et mitraille en éventail de 3. */
    D9(h = 2.3f, ratio = 0.693f, hp = 8, points = 250, move = Move.HOVER, fire = Fire.TRIPLE, speed = 2.4f, amp = 0.8f, lane = 2.6f, holdMs = 6000L, fireEveryMs = 1100L, bulletSpeed = 5.0f, stage = 1, weight = 2),
    /** Grande vague lente, anneau de 8 tirs. */
    D20(h = 2.5f, ratio = 0.668f, hp = 7, points = 250, move = Move.SINE, fire = Fire.RADIAL8, speed = 1.5f, amp = 2.2f, fireEveryMs = 2200L, bulletSpeed = 4.0f, stage = 1, weight = 2),
    /** Char au sol : roule de gauche à droite, canon vers le bas. */
    TANK(h = 2.0f, ratio = 0.301f, hp = 4, points = 120, move = Move.ROLL, fire = Fire.STREAM, amp = 1.8f, fireEveryMs = 1300L, bulletSpeed = 5.5f, stage = 1, weight = 2),
    /** Char au sol qui patrouille largement, tir visé rapide. */
    TANK_TOP(h = 1.9f, ratio = 0.707f, hp = 9, points = 260, move = Move.ROLL, fire = Fire.AIMED, amp = 2.8f, fireEveryMs = 1500L, bulletSpeed = 6.5f, stage = 1, tier = 2, weight = 2),
    /** Zigzag lent, tir en spirale continue. */
    D4(h = 2.5f, ratio = 0.623f, hp = 8, points = 300, move = Move.ZIGZAG, fire = Fire.SPIRAL, speed = 1.6f, amp = 2.0f, fireEveryMs = 220L, bulletSpeed = 4.5f, stage = 1, tier = 3, weight = 2),
    /** Grand arc de plongée, éventail de 5. */
    D16(h = 2.5f, ratio = 0.527f, hp = 9, points = 350, move = Move.SWOOP, fire = Fire.SPREAD5, amp = 9.5f, fireEveryMs = 1500L, bulletSpeed = 5.0f, stage = 2, weight = 1),
    /** Tourelle au sol, tir visé rapide. */
    FALL_TURRET(h = 1.5f, ratio = 0.841f, hp = 3, points = 110, move = Move.GROUND, fire = Fire.AIMED, fireEveryMs = 1700L, bulletSpeed = 6.0f, stage = 2, weight = 2),
    /** Tourelle au sol, tir en éventail de 3. */
    SHERMAN_TURRET(h = 2.3f, ratio = 0.505f, hp = 6, points = 200, move = Move.GROUND, fire = Fire.TRIPLE, fireEveryMs = 1900L, bulletSpeed = 5.0f, stage = 2, tier = 2, weight = 2),
    /** Très lente et très résistante, mitrailleuse vers le bas. */
    CORVETTE(h = 4.2f, ratio = 0.272f, hp = 20, points = 600, move = Move.STRAIGHT, fire = Fire.STREAM, speed = 1.0f, fireEveryMs = 350L, bulletSpeed = 6.0f, stage = 2, tier = 3, weight = 1),
    /** Reste 9 s en haut, anneaux de 12 tirs. */
    CONSTELLATION(h = 3.6f, ratio = 0.565f, hp = 28, points = 1000, move = Move.HOVER, fire = Fire.RADIAL12, speed = 1.6f, amp = 0.6f, lane = 3.4f, holdMs = 9000L, fireEveryMs = 1400L, bulletSpeed = 3.8f, stage = 3, weight = 1),
    /** Tourelle lourde au sol, spirale de tirs continue. */
    TURRET_HEAVY(h = 2.6f, ratio = 0.93f, hp = 14, points = 450, move = Move.GROUND, fire = Fire.SPIRAL, fireEveryMs = 240L, bulletSpeed = 4.5f, stage = 3, weight = 1),
    /** Lance-missiles au sol, salve en éventail de 5. */
    REDFOR_TURRET(h = 1.5f, ratio = 0.894f, hp = 10, points = 300, move = Move.GROUND, fire = Fire.SPREAD5, fireEveryMs = 2100L, bulletSpeed = 5.0f, stage = 3, tier = 2, weight = 2),
    /** Boss : la soucoupe : anneaux de 12 tirs, puis spirale quand elle est à moitié détruite. */
    BOSS_SAUCER(h = 3.6f, ratio = 1.0f, hp = 300, points = 5000, move = Move.BOSS, fire = Fire.RADIAL12, speed = 2f, amp = 2.4f, lane = 3.6f, fireEveryMs = 1500L, bulletSpeed = 3.8f, fire2 = Fire.SPIRAL, fire2EveryMs = 200L, boss = true),
    /** Boss : le gros char : tir en éventail de 3, puis de 5 quand il est à moitié détruit. */
    BOSS_TANK(h = 4.0f, ratio = 0.705f, hp = 400, points = 6000, move = Move.BOSS, fire = Fire.TRIPLE, speed = 2f, amp = 2.6f, lane = 3.8f, fireEveryMs = 1100L, bulletSpeed = 5.5f, fire2 = Fire.SPREAD5, fire2EveryMs = 800L, boss = true, stage = 1),
    /** Boss : la navette : éventail de 5, puis anneaux de 12 quand elle est à moitié détruite. */
    BOSS_SHUTTLE(h = 4.4f, ratio = 0.85f, hp = 500, points = 8000, move = Move.BOSS, fire = Fire.SPREAD5, speed = 2f, amp = 2.2f, lane = 3.8f, fireEveryMs = 1300L, bulletSpeed = 5.0f, fire2 = Fire.RADIAL12, fire2EveryMs = 900L, boss = true, stage = 2),
    /** Boss : le gros lance-missiles : salves en éventail de 5, puis anneaux de 12 quand il est à moitié détruit. */
    BOSS_LAUNCHER(h = 3.8f, ratio = 0.892f, hp = 650, points = 10000, move = Move.BOSS, fire = Fire.SPREAD5, speed = 2f, amp = 1.8f, lane = 3.6f, fireEveryMs = 900L, bulletSpeed = 4.8f, fire2 = Fire.RADIAL12, fire2EveryMs = 600L, boss = true, stage = 3);

    /** Largeur dessinée, en unités du terrain. */
    val w: Float get() = h * ratio

    /** Demi-côtés de la zone qui compte pour les touches (plus petite que le dessin). */
    val hitW: Float get() = w * 0.36f
    val hitH: Float get() = h * 0.30f

    /** Posé sur le sol : ne blesse pas le joueur au contact et se dessine avec une ombre portée. */
    val ground: Boolean get() = move == Move.GROUND || move == Move.ROLL

    /** Vrai si ce type peut arriver au palier [sublevel] du plateau [stageIndex] (un type déjà débloqué reste là). */
    fun availableAt(stageIndex: Int, sublevel: Int): Boolean =
        stageIndex > stage || (stageIndex == stage && sublevel >= tier)
}

/**
 * Une partie de Shoot'em up : le vaisseau tire tout seul avec une arme à [Shmup.MAX_WEAPON] niveaux, que des petits vaisseaux
 * satellites viennent renforcer (le tout se change avec les bonus et malus qui tombent), des ennemis de [EnemyType] différents arrivent avec leurs mouvements et leurs tirs, et chaque
 * plateau se termine par un boss. Trois vies ; la partie est finie ([isOver]) quand la dernière est perdue. Le moteur
 * n'utilise pas Compose : l'écran appelle [tick] à chaque image et lit les listes pour dessiner.
 */
internal class ShmupGame(private val random: Random = Random.Default) {

    /** Un tir. [dmg] : les points de vie qu'il retire ; [lvl] : le niveau d'arme qui l'a tiré (0 pour un tir ennemi). */
    class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float, val dmg: Int = 1, val lvl: Int = 0)

    /** Une pastille qui tombe : un bonus (l'arme gagne un niveau) ou un malus (elle en perd un). */
    class Pickup(var x: Float, var y: Float, val bonus: Boolean)

    /**
     * Un ennemi. Sa position ne dépend que de son point d'arrivée ([spawnX], [spawnY]), de son sens [dir] (1 ou -1), de sa
     * [phase] et de son âge [ageMs] : elle se recalcule avec [place], sans rien intégrer image par image. [maxHp] est sa vie
     * au départ ; [aux] sert au type DIVE pour retenir où était le joueur (négatif tant qu'il ne l'a pas repéré) ; [shots]
     * compte ses salves.
     */
    class Enemy(
        val type: EnemyType,
        val spawnX: Float,
        val spawnY: Float,
        val dir: Float,
        val phase: Float,
        var hp: Int,
        val maxHp: Int = hp,
        var ageMs: Long = 0L,
        var shootInMs: Long = 0L,
        var aux: Float = -1f,
        var shots: Int = 0
    ) {
        var x = spawnX
            private set
        var y = spawnY
            private set

        init {
            place()
        }

        /** Recalcule [x] et [y] d'après l'âge. */
        fun place() {
            val t = ageMs / 1000f
            val v = type.speed
            val lane = type.lane
            when (type.move) {
                Move.STRAIGHT -> {
                    x = spawnX
                    y = spawnY + v * t
                }
                Move.SINE -> {
                    x = spawnX + sin(t * 2f + phase) * type.amp
                    y = spawnY + v * t
                }
                Move.ZIGZAG -> {
                    x = spawnX + triangle(t * 1.4f + phase) * type.amp
                    y = spawnY + v * t
                }
                Move.DIVE -> {
                    val lock = DIVE_LOCK_MS / 1000f
                    if (t < lock) {
                        x = spawnX
                        y = spawnY + v * 0.5f * t
                    } else {
                        val k = ((t - lock) / 1.2f).coerceIn(0f, 1f)
                        val target = if (aux < 0f) spawnX else aux
                        x = spawnX + (target - spawnX) * (k * k * (3f - 2f * k))
                        y = spawnY + v * 0.5f * lock + v * 2.4f * (t - lock)
                    }
                }
                Move.DIAGONAL -> {
                    x = spawnX + dir * v * 0.8f * t
                    y = spawnY + v * 0.9f * t
                }
                Move.STRAFE -> {
                    val down = (lane - spawnY) / v
                    if (t < down) {
                        x = spawnX
                        y = spawnY + v * t
                    } else {
                        x = spawnX + dir * 2.6f * (t - down)
                        y = lane
                    }
                }
                Move.HOVER -> {
                    val down = (lane - spawnY) / v
                    val hold = type.holdMs / 1000f
                    y = when {
                        t < down -> spawnY + v * t
                        t < down + hold -> lane + sin((t - down) * 1.5f) * 0.15f
                        else -> lane - v * 1.6f * (t - down - hold)
                    }
                    x = spawnX + sin(t * 1.2f + phase) * type.amp
                }
                Move.CIRCLE -> {
                    val a = t * 2.2f + phase
                    x = spawnX + cos(a) * type.amp
                    y = spawnY + v * 0.55f * t + sin(a) * type.amp * 0.8f
                }
                Move.SWOOP -> {
                    val w = 0.85f
                    y = spawnY + type.amp * sin(w * t)
                    x = spawnX + dir * type.amp * 0.45f * (1f - cos(w * t))
                }
                Move.GROUND -> {
                    x = spawnX
                    y = spawnY + Shmup.SCROLL_SPEED * t
                }
                Move.ROLL -> {
                    x = spawnX + sin(t * 0.7f + phase) * type.amp
                    y = spawnY + Shmup.SCROLL_SPEED * t
                }
                Move.BOSS -> {
                    val down = (lane - spawnY) / v
                    y = if (t < down) spawnY + v * t else lane + sin((t - down) * 0.8f) * 0.25f
                    x = spawnX + (if (t < down) 0f else sin((t - down) * 0.55f + phase) * type.amp)
                }
            }
        }

        /** Vrai quand l'ennemi est sorti du terrain pour de bon (en bas, sur un côté ou remonté par le haut). Un boss reste. */
        fun isGone(): Boolean = !type.boss &&
            (y > Shmup.HEIGHT + type.h || x < -3f || x > Shmup.WIDTH + 3f || (y < -type.h - 2f && ageMs > 1500L))

        companion object {
            /** Moment (en ms) où un DIVE repère le joueur. */
            const val DIVE_LOCK_MS = 1500L

            /** Onde en triangle : entre -1 et 1, de période 1. */
            fun triangle(x: Float): Float {
                val f = x - floor(x)
                return if (f < 0.5f) 4f * f - 1f else 3f - 4f * f
            }
        }
    }

    class Spark(var x: Float, var y: Float, val vx: Float, val vy: Float, var lifeMs: Long)

    var playerX = Shmup.WIDTH / 2f
        private set
    var playerY = Shmup.START_Y
        private set
    var score = 0
        private set
    var lives = Shmup.LIVES
        private set
    var timeMs = 0L
        private set
    var invulnMs = 0L
        private set

    /** Niveau de l'arme du vaisseau, de 1 à [Shmup.MAX_WEAPON]. */
    var weapon = 1
        private set

    /** Nombre de satellites (de 0 à [Shmup.MAX_SATELLITES]), niveau d'arme qu'ils ont en commun, et leur angle de rotation. */
    var satellites = 0
        private set
    var satWeapon = 1
        private set
    var orbitAngle = 0f
        private set

    /** Plateau en cours (0 à 3) et nombre de tours déjà finis (0 au premier tour). */
    var stage = 0
        private set
    var loop = 0
        private set
    var phase = ShmupPhase.NORMAL
        private set

    /** Temps passé dans le plateau avant le boss, temps écoulé du saut en vitesse lumière et de l'annonce du boss. */
    var stageMs = 0L
        private set
    var transitionMs = 0L
        private set
    var bossIntroMs = 0L
        private set

    /** Distance de défilement du fond depuis le début, en unités (ne revient jamais à zéro), et sa vitesse du moment. */
    var scroll = 0f
        private set
    var scrollSpeed = Shmup.SCROLL_SPEED
        private set

    val bullets = mutableListOf<Bullet>()
    val enemyBullets = mutableListOf<Bullet>()
    val enemies = mutableListOf<Enemy>()
    val pickups = mutableListOf<Pickup>()
    val sparks = mutableListOf<Spark>()

    private var fireInMs = 0L
    private var spawnInMs = 1000L
    private var laserInMs = 0L
    private var clearWaitMs = 0L
    private var stageSwitched = false

    /** Le numéro de niveau montré au joueur : il monte d'un à chaque plateau et ne redescend pas au nouveau tour. */
    val level: Int get() = loop * Shmup.STAGES + stage + 1

    /** Le palier du plateau, de 1 à [Shmup.SUBLEVELS]. */
    val sublevel: Int get() = min(Shmup.SUBLEVELS, 1 + (stageMs / Shmup.SUBLEVEL_MS).toInt())

    val isOver: Boolean get() = lives <= 0

    /** Vrai quand le laser du vaisseau est allumé (arme de niveau 4 ou plus). */
    val laserOn: Boolean get() = weapon >= Shmup.MAX_WEAPON && !isOver

    /** Vrai quand les satellites tirent au laser (ils ont atteint le niveau final). */
    val satelliteLaserOn: Boolean get() = satellites > 0 && satWeapon >= Shmup.MAX_WEAPON && !isOver

    /** Position du satellite numéro [index] (de 0 à satellites - 1) : sur un cercle autour du vaisseau, gardée dans le terrain. */
    fun satelliteX(index: Int): Float =
        (playerX + cos(orbitAngle + index * TWO_PI / satellites) * Shmup.ORBIT_RADIUS).coerceIn(0.3f, Shmup.WIDTH - 0.3f)

    fun satelliteY(index: Int): Float =
        (playerY + sin(orbitAngle + index * TWO_PI / satellites) * Shmup.ORBIT_RADIUS).coerceIn(0.3f, Shmup.HEIGHT - 0.3f)

    /** Le boss en jeu, s'il y en a un. */
    val boss: Enemy? get() = enemies.firstOrNull { it.type.boss }

    /** Avancement du saut en vitesse lumière, de 0 à 1 (0 en dehors du saut). */
    val transitionProgress: Float
        get() = if (phase == ShmupPhase.TRANSITION) (transitionMs.toFloat() / Shmup.TRANSITION_MS).coerceIn(0f, 1f) else 0f

    /** Remet la partie à neuf : trois vies, plus de score, premier plateau, arme de niveau 1, plus aucun objet sur le terrain. */
    fun reset() {
        playerX = Shmup.WIDTH / 2f
        playerY = Shmup.START_Y
        score = 0
        lives = Shmup.LIVES
        timeMs = 0L
        invulnMs = 0L
        weapon = 1
        satellites = 0
        satWeapon = 1
        orbitAngle = 0f
        stage = 0
        loop = 0
        phase = ShmupPhase.NORMAL
        stageMs = 0L
        transitionMs = 0L
        bossIntroMs = 0L
        scroll = 0f
        scrollSpeed = Shmup.SCROLL_SPEED
        bullets.clear()
        enemyBullets.clear()
        enemies.clear()
        pickups.clear()
        sparks.clear()
        fireInMs = 0L
        spawnInMs = 1000L
        laserInMs = 0L
        clearWaitMs = 0L
        stageSwitched = false
    }

    /** Place le vaisseau en (x, y), sans le laisser sortir du terrain. */
    fun movePlayerTo(x: Float, y: Float) {
        playerX = x.coerceIn(Shmup.SHIP_W / 2f, Shmup.WIDTH - Shmup.SHIP_W / 2f)
        playerY = y.coerceIn(Shmup.SHIP_H / 2f, Shmup.HEIGHT - Shmup.SHIP_H / 2f)
    }

    /** Fait avancer la partie de [elapsedMs] millisecondes, par pas de [Shmup.MAX_STEP_MS] au plus. */
    fun tick(elapsedMs: Long) {
        var left = elapsedMs
        while (left > 0L && !isOver) {
            val step = min(left, Shmup.MAX_STEP_MS)
            advance(step)
            left -= step
        }
    }

    private fun advance(ms: Long) {
        val dt = ms / 1000f
        timeMs += ms
        invulnMs = max(0L, invulnMs - ms)

        // Défilement : il s'arrête pendant le combat contre le boss et s'emballe pendant le saut en vitesse lumière.
        val targetSpeed = when (phase) {
            ShmupPhase.BOSS -> 0f
            ShmupPhase.TRANSITION -> Shmup.TRANSITION_SCROLL
            else -> Shmup.SCROLL_SPEED
        }
        val change = 6f * dt
        scrollSpeed += (targetSpeed - scrollSpeed).coerceIn(-change, change)
        scroll += scrollSpeed * dt

        // Les satellites tournent autour du vaisseau.
        orbitAngle = (orbitAngle + Shmup.ORBIT_SPEED * dt) % TWO_PI

        // Tir automatique : les missiles du vaisseau et de chaque satellite, plus les lasers du dernier niveau.
        fireInMs -= ms
        if (fireInMs <= 0L) {
            fireInMs += Shmup.FIRE_EVERY_MS
            fireMissiles()
        }
        if (laserOn || satelliteLaserOn) {
            laserInMs -= ms
            if (laserInMs <= 0L) {
                laserInMs += Shmup.LASER_EVERY_MS
                laserHit()
            }
        }

        // Le déroulement du plateau.
        when (phase) {
            ShmupPhase.NORMAL -> {
                stageMs += ms
                spawnInMs -= ms
                if (spawnInMs <= 0L) {
                    spawnInMs += Shmup.spawnEveryMs(stage, sublevel, loop)
                    spawnWave()
                    if (random.nextFloat() < Shmup.extraWaveChance(stage, loop)) spawnWave()
                }
                if (stageMs >= Shmup.STAGE_MS) {
                    phase = ShmupPhase.CLEARING
                    clearWaitMs = 0L
                }
            }
            ShmupPhase.CLEARING -> {
                clearWaitMs += ms
                if (enemies.isEmpty() || clearWaitMs >= Shmup.CLEAR_WAIT_MS) spawnBoss()
            }
            ShmupPhase.BOSS -> bossIntroMs = max(0L, bossIntroMs - ms)
            ShmupPhase.TRANSITION -> {
                transitionMs += ms
                if (!stageSwitched && transitionMs >= Shmup.TRANSITION_MS / 2) switchStage()
                if (transitionMs >= Shmup.TRANSITION_MS) {
                    phase = ShmupPhase.NORMAL
                    spawnInMs = 1200L
                }
            }
        }

        // Mouvements.
        for (b in bullets) {
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        for (b in enemyBullets) {
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        for (e in enemies) {
            e.ageMs += ms
            if (e.type.move == Move.DIVE && e.aux < 0f && e.ageMs >= Enemy.DIVE_LOCK_MS) e.aux = playerX
            e.place()
            if (e.type.fire != Fire.NONE) {
                e.shootInMs -= ms
                if (e.shootInMs <= 0L) {
                    val second = e.type.fire2 != Fire.NONE && e.hp * 2 < e.maxHp
                    val pattern = if (second) e.type.fire2 else e.type.fire
                    val every = if (second) e.type.fire2EveryMs else e.type.fireEveryMs
                    val jitter = if (every >= 800L) random.nextInt(300) else 0
                    e.shootInMs = (every * max(0.6f, 1f - 0.08f * loop)).toLong() + jitter
                    // Il ne tire que s'il est à l'écran et pas trop près du bas.
                    if (e.y > 0.4f && e.y < Shmup.HEIGHT * 0.78f) fireFrom(e, pattern)
                }
            }
        }
        for (s in sparks) {
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.lifeMs -= ms
        }

        // Les pastilles tombent et le vaisseau les ramasse.
        val falling = pickups.iterator()
        while (falling.hasNext()) {
            val p = falling.next()
            p.y += Shmup.PICKUP_SPEED * dt
            if (abs(p.x - playerX) < Shmup.PICKUP_HALF + Shmup.SHIP_W / 2f &&
                abs(p.y - playerY) < Shmup.PICKUP_HALF + Shmup.SHIP_H / 2f
            ) {
                collect(p)
                falling.remove()
            } else if (p.y > Shmup.HEIGHT + 1f) {
                falling.remove()
            }
        }

        // Les missiles du joueur touchent les ennemis.
        val shots = bullets.iterator()
        while (shots.hasNext()) {
            val b = shots.next()
            val target = enemies.firstOrNull {
                it.hp > 0 &&
                    abs(b.x - it.x) < Shmup.MISSILE_HALF_W + it.type.hitW &&
                    abs(b.y - it.y) < Shmup.MISSILE_HALF_H + it.type.hitH
            } ?: continue
            hit(target, b.dmg, b.x, b.y)
            shots.remove()
        }

        // Le vaisseau est touché par un ennemi (sauf ceux posés au sol) ou par un tir ennemi, sauf juste après un impact.
        if (invulnMs <= 0L) {
            val rammer = enemies.firstOrNull {
                it.hp > 0 && !it.type.ground &&
                    abs(it.x - playerX) < it.type.hitW + Shmup.SHIP_HIT &&
                    abs(it.y - playerY) < it.type.hitH + Shmup.SHIP_HIT
            }
            if (rammer != null) {
                // Un petit ennemi se détruit en percutant ; un gros encaisse un peu plus et continue.
                hit(rammer, 3, rammer.x, rammer.y)
                hurt()
            }
        }
        val incoming = enemyBullets.iterator()
        while (incoming.hasNext()) {
            val b = incoming.next()
            if (invulnMs > 0L || isOver) break
            if (abs(b.x - playerX) < Shmup.ENEMY_BULLET_HIT + Shmup.SHIP_HIT &&
                abs(b.y - playerY) < Shmup.ENEMY_BULLET_HIT + Shmup.SHIP_HIT
            ) {
                incoming.remove()
                hurt()
            }
        }

        // Le boss est détruit : on saute vers le plateau suivant.
        if (phase == ShmupPhase.BOSS && enemies.none { it.type.boss && it.hp > 0 }) startTransition()

        // Nettoyage de ce qui est mort ou sorti du terrain.
        enemies.removeAll { it.hp <= 0 || it.isGone() }
        bullets.removeAll { it.y < -1f || it.y > Shmup.HEIGHT + 1f || it.x < -1f || it.x > Shmup.WIDTH + 1f }
        enemyBullets.removeAll { it.y < -1f || it.y > Shmup.HEIGHT + 1f || it.x < -1f || it.x > Shmup.WIDTH + 1f }
        sparks.removeAll { it.lifeMs <= 0L }
    }

    /** Les missiles du vaisseau, puis ceux de chaque satellite (le laser du dernier niveau s'y ajoute, voir [laserHit]). */
    private fun fireMissiles() {
        volley(playerX, playerY - Shmup.SHIP_H / 2f, weapon, 0.3f)
        for (i in 0 until satellites) volley(satelliteX(i), satelliteY(i) - Shmup.SAT_H / 2f, satWeapon, Shmup.SAT_SPREAD)
    }

    /** Une salve de missiles tirée de (x, y) au niveau [level] : 1, 2, 3 en éventail, ou 2 avec le laser (niveau 4). */
    private fun volley(x: Float, y: Float, level: Int, spread: Float) {
        val speed = Shmup.MISSILE_SPEED
        fun missile(mx: Float, angle: Float) {
            bullets += Bullet(mx, y, sin(angle) * speed, -cos(angle) * speed, Shmup.MISSILE_DAMAGE, level)
        }
        when (level) {
            1 -> missile(x, 0f)
            2, 4 -> {
                missile(x - spread, 0f)
                missile(x + spread, 0f)
            }
            else -> {
                missile(x, 0f)
                missile(x - spread * 1.2f, -Shmup.SPREAD_ANGLE)
                missile(x + spread * 1.2f, Shmup.SPREAD_ANGLE)
            }
        }
    }

    /** Un coup de laser : les lasers allumés (vaisseau et satellites) retirent un point de vie aux ennemis de leur colonne. */
    private fun laserHit() {
        if (laserOn) beam(playerX, playerY - Shmup.SHIP_H / 2f, Shmup.LASER_HALF_W)
        if (satelliteLaserOn) {
            for (i in 0 until satellites) beam(satelliteX(i), satelliteY(i) - Shmup.SAT_H / 2f, Shmup.SAT_LASER_HALF_W)
        }
    }

    /** Un laser tiré de la colonne [x], au-dessus de [nose] : tous les ennemis dans cette colonne perdent un point de vie. */
    private fun beam(x: Float, nose: Float, half: Float) {
        for (e in enemies) {
            if (e.hp > 0 && e.y - e.type.hitH < nose && e.y + e.type.hitH > 0f && abs(e.x - x) < half + e.type.hitW) {
                hit(e, 1, e.x, e.y + e.type.hitH)
            }
        }
    }

    /** Retire [dmg] points de vie à [e] ; s'il est détruit, il rapporte ses points et peut laisser tomber une pastille. */
    private fun hit(e: Enemy, dmg: Int, sparkX: Float, sparkY: Float) {
        e.hp -= dmg
        if (e.hp > 0) {
            burst(sparkX, sparkY, 2)
            return
        }
        score += e.type.points * level
        burst(e.x, e.y, 10 + (e.type.h * 6f).toInt())
        if (e.type.boss) {
            pickups += Pickup(e.x, e.y, true)
            return
        }
        // Les pastilles sont rares : un peu plus souvent sur les gros ennemis, et un malus sur deux quand l'arme n'est pas au minimum.
        val chance = if (e.type.hp >= 9) 0.12f else 0.05f
        if (random.nextFloat() < chance) {
            val bonus = weapon <= 1 || random.nextFloat() < 0.5f
            pickups += Pickup(e.x.coerceIn(0.5f, Shmup.WIDTH - 0.5f), e.y, bonus)
        }
    }

    /**
     * Le vaisseau ramasse une pastille. Un bonus fait avancer l'armement : d'abord l'arme du vaisseau monte jusqu'au laser ;
     * au laser, le bonus suivant la remet au niveau 1 et ajoute un satellite (jusqu'à 3) ; quand les 3 sont là et l'arme du
     * vaisseau au laser, les bonus montent l'arme des satellites jusqu'au laser (le maximum : les bonus rapportent alors des
     * points). Un malus ne baisse que l'arme du vaisseau, d'un niveau.
     */
    private fun collect(p: Pickup) {
        if (p.bonus) {
            when {
                weapon < Shmup.MAX_WEAPON -> weapon++
                satellites < Shmup.MAX_SATELLITES -> {
                    weapon = 1
                    satellites++
                }
                satWeapon < Shmup.MAX_WEAPON -> satWeapon++
                else -> score += 200
            }
        } else {
            weapon = max(1, weapon - 1)
        }
        burst(p.x, p.y, 6)
    }

    /** Fait arriver un type d'ennemi tiré parmi ceux du plateau (avec son escorte s'il vient en groupe). */
    private fun spawnWave() {
        val pool = Shmup.poolAt(stage, sublevel)
        var pick = random.nextInt(pool.sumOf { it.weight })
        val type = pool.first {
            pick -= it.weight
            pick < 0
        }
        val dir = if (random.nextBoolean()) 1f else -1f
        val fromSide = type.move == Move.DIAGONAL || type.move == Move.STRAFE || type.move == Move.SWOOP
        val reach = when (type.move) {
            Move.SINE, Move.ZIGZAG, Move.CIRCLE, Move.ROLL, Move.HOVER -> type.amp
            else -> 0f
        }
        val margin = min(Shmup.WIDTH / 2f - 0.1f, max(0.9f, type.w / 2f + reach))
        val baseX = when {
            fromSide -> if (dir > 0f) 0.8f else Shmup.WIDTH - 0.8f
            else -> margin + random.nextFloat() * (Shmup.WIDTH - 2f * margin)
        }
        val wobble = random.nextFloat() * TWO_PI
        // Un type plus ancien que le plateau et chaque tour de plus rendent l'ennemi plus résistant.
        val hp = type.hp + max(0, stage - type.stage) / 2 + loop * (1 + type.hp / 4)
        val n = type.group
        for (i in 0 until n) {
            val off = i - (n - 1) / 2f
            val x = if (n == 1) baseX else (baseX + off * (type.w + 0.5f)).coerceIn(0.8f, Shmup.WIDTH - 0.8f)
            val y = -type.h / 2f - abs(off) * 0.9f
            val delay = type.fireEveryMs / 2 + random.nextInt(600)
            enemies += Enemy(type, x, y, dir, wobble, hp, shootInMs = delay)
        }
    }

    /** Fait arriver le boss du plateau, plus résistant à chaque tour. */
    private fun spawnBoss() {
        val type = Shmup.bossOf(stage)
        val hp = (type.hp * (1f + 0.6f * loop)).toInt()
        enemies += Enemy(type, Shmup.WIDTH / 2f, -type.h / 2f, 1f, 0f, hp, shootInMs = Shmup.BOSS_INTRO_MS + 500L)
        phase = ShmupPhase.BOSS
        bossIntroMs = Shmup.BOSS_INTRO_MS
    }

    private fun startTransition() {
        phase = ShmupPhase.TRANSITION
        transitionMs = 0L
        stageSwitched = false
        enemyBullets.clear()
    }

    /** Au milieu du saut en vitesse lumière : le plateau suivant (ou le premier, en plus difficile, après le dernier). */
    private fun switchStage() {
        stage++
        if (stage >= Shmup.STAGES) {
            stage = 0
            loop++
        }
        stageMs = 0L
        stageSwitched = true
    }

    private fun fireFrom(e: Enemy, pattern: Fire) {
        val t = e.type
        val speed = t.bulletSpeed * (1f + 0.12f * loop)
        val x = e.x
        val muzzleY = e.y + t.h * 0.25f
        when (pattern) {
            Fire.NONE -> Unit
            Fire.STREAM -> shoot(x, muzzleY, HALF_PI, speed)
            Fire.TWIN -> {
                shoot(x - t.w * 0.22f, muzzleY, HALF_PI, speed)
                shoot(x + t.w * 0.22f, muzzleY, HALF_PI, speed)
            }
            Fire.AIMED -> shoot(x, muzzleY, aimAngle(x, muzzleY), speed)
            Fire.TRIPLE -> {
                val a = aimAngle(x, muzzleY)
                for (k in -1..1) shoot(x, muzzleY, a + k * 0.28f, speed)
            }
            Fire.SPREAD5 -> for (k in -2..2) shoot(x, muzzleY, HALF_PI + k * 0.28f, speed)
            Fire.RADIAL8 -> ring(x, e.y, 8, e.shots * 0.2f, speed)
            Fire.RADIAL12 -> ring(x, e.y, 12, e.shots * 0.13f, speed)
            Fire.SPIRAL -> {
                val a = e.shots * 0.5f
                shoot(x, e.y, a, speed)
                shoot(x, e.y, a + TWO_PI / 2f, speed)
            }
        }
        e.shots++
    }

    /** L'angle (en radians, y vers le bas) qui mène de (x, y) au vaisseau du joueur. */
    private fun aimAngle(x: Float, y: Float): Float {
        val dx = playerX - x
        val dy = playerY - y
        return if (hypot(dx, dy) < 0.001f) HALF_PI else atan2(dy, dx)
    }

    private fun shoot(x: Float, y: Float, angle: Float, speed: Float) {
        enemyBullets += Bullet(x, y, cos(angle) * speed, sin(angle) * speed)
    }

    private fun ring(x: Float, y: Float, count: Int, offset: Float, speed: Float) {
        for (k in 0 until count) shoot(x, y, offset + k * TWO_PI / count, speed)
    }

    /** Le vaisseau est touché : il perd un satellite s'il en a, sinon une vie. Dans les deux cas il est protégé un moment. */
    private fun hurt() {
        invulnMs = Shmup.INVULN_MS
        if (satellites > 0) {
            burst(satelliteX(satellites - 1), satelliteY(satellites - 1), 16)
            satellites--
        } else {
            lives--
            burst(playerX, playerY, 24)
        }
    }

    private fun burst(x: Float, y: Float, count: Int) {
        repeat(count) {
            if (sparks.size >= Shmup.MAX_SPARKS) return
            val angle = random.nextFloat() * TWO_PI
            val speed = 1f + random.nextFloat() * 4f
            sparks += Spark(x, y, cos(angle) * speed, sin(angle) * speed, 200L + random.nextInt(400))
        }
    }

    /**
     * L'état de la partie sous forme d'une liste d'entiers (les décimaux sont rangés par leurs bits), pour la garder quand
     * l'écran tourne. Les étincelles ne sont pas gardées.
     */
    fun save(): List<Int> {
        val out = ArrayList<Int>()
        out += SAVE_VERSION
        out += playerX.toRawBits()
        out += playerY.toRawBits()
        out += score
        out += lives
        out += timeMs.toInt()
        out += invulnMs.toInt()
        out += weapon
        out += stage
        out += loop
        out += phase.ordinal
        out += stageMs.toInt()
        out += transitionMs.toInt()
        out += bossIntroMs.toInt()
        out += clearWaitMs.toInt()
        out += if (stageSwitched) 1 else 0
        out += scroll.toRawBits()
        out += scrollSpeed.toRawBits()
        out += fireInMs.toInt()
        out += spawnInMs.toInt()
        out += satellites
        out += satWeapon
        out += orbitAngle.toRawBits()
        for (list in listOf(bullets, enemyBullets)) {
            out += list.size
            for (b in list) {
                out += b.x.toRawBits()
                out += b.y.toRawBits()
                out += b.vx.toRawBits()
                out += b.vy.toRawBits()
                out += b.dmg
                out += b.lvl
            }
        }
        out += pickups.size
        for (p in pickups) {
            out += p.x.toRawBits()
            out += p.y.toRawBits()
            out += if (p.bonus) 1 else 0
        }
        out += enemies.size
        for (e in enemies) {
            out += e.type.ordinal
            out += e.spawnX.toRawBits()
            out += e.spawnY.toRawBits()
            out += e.dir.toRawBits()
            out += e.phase.toRawBits()
            out += e.hp
            out += e.maxHp
            out += e.ageMs.toInt()
            out += e.shootInMs.toInt()
            out += e.aux.toRawBits()
            out += e.shots
        }
        return out
    }

    companion object {
        private const val SAVE_VERSION = 4
        private const val MAX_SAVED = 500

        /** Une partie remise dans l'état de [data] (voir [save]), ou null si la liste est abîmée. */
        fun restore(data: List<Int>, random: Random = Random.Default): ShmupGame? = try {
            var i = 0
            fun int(): Int = data[i++]
            fun float(): Float = Float.fromBits(int())
            fun count(): Int = int().also { require(it in 0..MAX_SAVED) }

            require(int() == SAVE_VERSION)
            val game = ShmupGame(random)
            game.playerX = float()
            game.playerY = float()
            game.score = int()
            game.lives = int()
            require(game.lives in 0..Shmup.LIVES)
            game.timeMs = int().toLong()
            game.invulnMs = int().toLong()
            game.weapon = int()
            require(game.weapon in 1..Shmup.MAX_WEAPON)
            game.stage = int()
            require(game.stage in 0 until Shmup.STAGES)
            game.loop = int()
            require(game.loop >= 0)
            game.phase = ShmupPhase.values()[int()]
            game.stageMs = int().toLong()
            game.transitionMs = int().toLong()
            game.bossIntroMs = int().toLong()
            game.clearWaitMs = int().toLong()
            game.stageSwitched = int() == 1
            game.scroll = float()
            game.scrollSpeed = float()
            game.fireInMs = int().toLong()
            game.spawnInMs = int().toLong()
            game.satellites = int()
            require(game.satellites in 0..Shmup.MAX_SATELLITES)
            game.satWeapon = int()
            require(game.satWeapon in 1..Shmup.MAX_WEAPON)
            game.orbitAngle = float()
            for (list in listOf(game.bullets, game.enemyBullets)) {
                repeat(count()) { list += Bullet(float(), float(), float(), float(), int(), int()) }
            }
            repeat(count()) { game.pickups += Pickup(float(), float(), int() == 1) }
            val types = EnemyType.values()
            repeat(count()) {
                val type = types[int()]
                game.enemies += Enemy(
                    type = type,
                    spawnX = float(),
                    spawnY = float(),
                    dir = float(),
                    phase = float(),
                    hp = int(),
                    maxHp = int(),
                    ageMs = int().toLong(),
                    shootInMs = int().toLong(),
                    aux = float(),
                    shots = int()
                )
            }
            require(i == data.size)
            game
        } catch (e: IndexOutOfBoundsException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
