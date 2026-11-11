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
    const val FIRE_EVERY_MS = 150L
    const val BULLET_SPEED = 14f
    const val BULLET_HALF_W = 0.06f
    const val BULLET_HALF_H = 0.25f
    const val ENEMY_BULLET_HIT = 0.12f

    /** Durée pendant laquelle le vaisseau ne peut pas être touché après un impact. */
    const val INVULN_MS = 1600L

    /** Vitesse du défilement du fond (et du sol, où les tourelles sont posées), en unités par seconde. */
    const val SCROLL_SPEED = 3f

    /** Plus long pas de calcul : après un à-coup, rien ne doit traverser un autre objet d'un coup. */
    const val MAX_STEP_MS = 50L

    const val LEVEL_EVERY_MS = 25_000L
    const val MAX_SPARKS = 200

    fun levelAt(timeMs: Long): Int = 1 + (timeMs / LEVEL_EVERY_MS).toInt()

    /** Délai entre deux arrivées d'ennemis : de plus en plus court, jusqu'à un plancher. */
    fun spawnEveryMs(level: Int): Long = max(600L, 1500L - (level - 1) * 110L)

    /** Les types d'ennemis qui peuvent arriver au niveau [level]. */
    fun poolAt(level: Int): List<EnemyType> = EnemyType.values().filter { it.minLevel <= level }
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
    ROLL
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
 * [lane] et [holdMs] règlent le mouvement, [fireEveryMs] et [bulletSpeed] le tir. Un type n'arrive qu'à partir du niveau
 * [minLevel] ; [weight] est sa chance relative d'être tiré et [group] le nombre d'ennemis qui arrivent ensemble.
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
    val minLevel: Int = 1,
    val weight: Int = 1,
    val group: Int = 1
) {
    /** Essaim en V de 4, ondule sans tirer. */
    WASP(h = 1.5f, ratio = 0.561f, hp = 1, points = 50, move = Move.SINE, fire = Fire.NONE, speed = 3.0f, amp = 1.4f, minLevel = 1, weight = 4, group = 4),
    /** Descend droit en tirant deux salves parallèles. */
    K24(h = 1.4f, ratio = 1.025f, hp = 2, points = 80, move = Move.STRAIGHT, fire = Fire.TWIN, speed = 2.0f, fireEveryMs = 1500L, bulletSpeed = 5.0f, minLevel = 1, weight = 3),
    /** Zigzag sec, tir visé. */
    D14(h = 1.8f, ratio = 0.703f, hp = 2, points = 100, move = Move.ZIGZAG, fire = Fire.AIMED, speed = 2.4f, amp = 1.6f, fireEveryMs = 1800L, bulletSpeed = 5.5f, minLevel = 2, weight = 3),
    /** Char au sol : roule de gauche à droite, canon vers le bas. */
    TANK(h = 2.6f, ratio = 0.301f, hp = 4, points = 120, move = Move.ROLL, fire = Fire.STREAM, amp = 1.8f, fireEveryMs = 1300L, bulletSpeed = 5.5f, minLevel = 2, weight = 2),
    /** Par deux : ralentit, vise votre position puis fonce dessus. */
    L9G(h = 1.6f, ratio = 0.482f, hp = 2, points = 100, move = Move.DIVE, fire = Fire.NONE, speed = 2.2f, minLevel = 2, weight = 3, group = 2),
    /** Tourelle au sol, tir visé rapide. */
    FALL_TURRET(h = 1.5f, ratio = 0.841f, hp = 3, points = 110, move = Move.GROUND, fire = Fire.AIMED, fireEveryMs = 1700L, bulletSpeed = 6.0f, minLevel = 3, weight = 2),
    /** Entre par le côté en diagonale, tir visé. */
    D2(h = 1.9f, ratio = 0.641f, hp = 3, points = 130, move = Move.DIAGONAL, fire = Fire.AIMED, speed = 2.6f, fireEveryMs = 1600L, bulletSpeed = 5.5f, minLevel = 3, weight = 3),
    /** Descend puis traverse l’écran en arrosant vers le bas. */
    K23(h = 1.9f, ratio = 0.707f, hp = 4, points = 150, move = Move.STRAFE, fire = Fire.STREAM, speed = 3.0f, lane = 2.6f, fireEveryMs = 600L, bulletSpeed = 5.5f, minLevel = 3, weight = 2),
    /** Plonge en arc puis remonte, tir en éventail de 3. */
    D18(h = 1.9f, ratio = 0.743f, hp = 4, points = 160, move = Move.SWOOP, fire = Fire.TRIPLE, amp = 8.5f, fireEveryMs = 1400L, bulletSpeed = 5.0f, minLevel = 4, weight = 2),
    /** Tourne en spirale en descendant, tir visé très rapide. */
    K27(h = 1.9f, ratio = 0.629f, hp = 5, points = 180, move = Move.CIRCLE, fire = Fire.AIMED, speed = 2.4f, amp = 1.5f, fireEveryMs = 1300L, bulletSpeed = 7.0f, minLevel = 4, weight = 2),
    /** Tourelle au sol, tir en éventail de 3. */
    SHERMAN_TURRET(h = 2.3f, ratio = 0.505f, hp = 6, points = 200, move = Move.GROUND, fire = Fire.TRIPLE, fireEveryMs = 1900L, bulletSpeed = 5.0f, minLevel = 4, weight = 2),
    /** S’installe en haut 6 s et mitraille en éventail de 3. */
    D9(h = 2.3f, ratio = 0.693f, hp = 8, points = 250, move = Move.HOVER, fire = Fire.TRIPLE, speed = 2.4f, amp = 0.8f, lane = 2.6f, holdMs = 6000L, fireEveryMs = 1100L, bulletSpeed = 5.0f, minLevel = 5, weight = 2),
    /** Grande vague lente, anneau de 8 tirs. */
    D20(h = 2.5f, ratio = 0.668f, hp = 7, points = 250, move = Move.SINE, fire = Fire.RADIAL8, speed = 1.5f, amp = 2.2f, fireEveryMs = 2200L, bulletSpeed = 4.0f, minLevel = 5, weight = 2),
    /** Grosse tourelle au sol, salve de missiles en éventail de 5. */
    REDFOR_TURRET(h = 2.2f, ratio = 0.892f, hp = 10, points = 300, move = Move.GROUND, fire = Fire.SPREAD5, fireEveryMs = 2100L, bulletSpeed = 5.0f, minLevel = 6, weight = 2),
    /** Zigzag lent, tir en spirale continue. */
    D4(h = 2.5f, ratio = 0.623f, hp = 8, points = 300, move = Move.ZIGZAG, fire = Fire.SPIRAL, speed = 1.6f, amp = 2.0f, fireEveryMs = 220L, bulletSpeed = 4.5f, minLevel = 6, weight = 2),
    /** Grand arc de plongée, éventail de 5. */
    D16(h = 2.5f, ratio = 0.527f, hp = 9, points = 350, move = Move.SWOOP, fire = Fire.SPREAD5, amp = 9.5f, fireEveryMs = 1500L, bulletSpeed = 5.0f, minLevel = 7, weight = 1),
    /** Très lente et très résistante, mitrailleuse vers le bas. */
    CORVETTE(h = 4.2f, ratio = 0.272f, hp = 20, points = 600, move = Move.STRAIGHT, fire = Fire.STREAM, speed = 1.0f, fireEveryMs = 350L, bulletSpeed = 6.0f, minLevel = 7, weight = 1),
    /** Mini-boss : reste 9 s en haut, anneaux de 12 tirs. */
    CONSTELLATION(h = 3.6f, ratio = 0.565f, hp = 28, points = 1000, move = Move.HOVER, fire = Fire.RADIAL12, speed = 1.6f, amp = 0.6f, lane = 3.4f, holdMs = 9000L, fireEveryMs = 1400L, bulletSpeed = 3.8f, minLevel = 8, weight = 1),
    /** Nacelle ronde : s’installe au milieu du terrain 5 s et lâche un anneau de 8 tirs. */
    SPACE_TANK(h = 1.8f, ratio = 1f, hp = 9, points = 280, move = Move.HOVER, fire = Fire.RADIAL8, speed = 1.8f, amp = 0.5f, lane = 4.2f, holdMs = 5000L, fireEveryMs = 1900L, bulletSpeed = 3.8f, minLevel = 6, weight = 2),
    /** Chasseur en piqué : repère votre position, fonce dessus en mitraillant tout droit. */
    STARSHIP(h = 1.7f, ratio = 0.9f, hp = 3, points = 140, move = Move.DIVE, fire = Fire.STREAM, speed = 2.6f, fireEveryMs = 450L, bulletSpeed = 6.5f, minLevel = 3, weight = 2),
    /** Char au sol qui roule de gauche à droite, tir en éventail de 3. */
    TANK_PROFILE(h = 2.4f, ratio = 0.479f, hp = 7, points = 220, move = Move.ROLL, fire = Fire.TRIPLE, amp = 2.4f, fireEveryMs = 1800L, bulletSpeed = 5f, minLevel = 5, weight = 2),
    /** Gros char au sol qui patrouille largement, tir visé rapide. */
    TANK_TOP(h = 2.6f, ratio = 0.703f, hp = 9, points = 260, move = Move.ROLL, fire = Fire.AIMED, amp = 2.8f, fireEveryMs = 1500L, bulletSpeed = 6.5f, minLevel = 4, weight = 2),
    /** Navette qui entre en diagonale, tir en éventail de 5. */
    SHUTTLE(h = 2.4f, ratio = 0.848f, hp = 7, points = 240, move = Move.DIAGONAL, fire = Fire.SPREAD5, speed = 2.2f, fireEveryMs = 1700L, bulletSpeed = 5f, minLevel = 6, weight = 2),
    /** Tourelle lourde au sol, 14 points de vie, spirale de tirs continue. */
    TURRET_HEAVY(h = 2.6f, ratio = 0.93f, hp = 14, points = 450, move = Move.GROUND, fire = Fire.SPIRAL, fireEveryMs = 240L, bulletSpeed = 4.5f, minLevel = 7, weight = 1);

    /** Largeur dessinée, en unités du terrain. */
    val w: Float get() = h * ratio

    /** Demi-côtés de la zone qui compte pour les touches (plus petite que le dessin). */
    val hitW: Float get() = w * 0.36f
    val hitH: Float get() = h * 0.30f

    /** Posé sur le sol : ne blesse pas le joueur au contact et se dessine sur une plateforme. */
    val ground: Boolean get() = move == Move.GROUND || move == Move.ROLL
}

/**
 * Une partie de Shoot'em up : le vaisseau tire tout seul, des ennemis de [EnemyType] différents arrivent avec leurs
 * mouvements et leurs tirs. Trois vies ; la partie est finie ([isOver]) quand la dernière est perdue. Le moteur n'utilise
 * pas Compose : l'écran appelle [tick] à chaque image et lit les listes pour dessiner.
 */
internal class ShmupGame(private val random: Random = Random.Default) {

    class Bullet(var x: Float, var y: Float, val vx: Float, val vy: Float)

    /**
     * Un ennemi. Sa position ne dépend que de son point d'arrivée ([spawnX], [spawnY]), de son sens [dir] (1 ou -1), de sa
     * [phase] et de son âge [ageMs] : elle se recalcule avec [place], sans rien intégrer image par image. [aux] sert au
     * type DIVE pour retenir où était le joueur (négatif tant qu'il ne l'a pas repéré) ; [shots] compte ses salves.
     */
    class Enemy(
        val type: EnemyType,
        val spawnX: Float,
        val spawnY: Float,
        val dir: Float,
        val phase: Float,
        var hp: Int,
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
            }
        }

        /** Vrai quand l'ennemi est sorti du terrain pour de bon (en bas, sur un côté ou remonté par le haut). */
        fun isGone(): Boolean =
            y > Shmup.HEIGHT + type.h || x < -3f || x > Shmup.WIDTH + 3f || (y < -type.h - 2f && ageMs > 1500L)

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

    /** Distance de défilement du fond depuis le début, en unités (ne revient jamais à zéro). */
    var scroll = 0f
        private set

    val bullets = mutableListOf<Bullet>()
    val enemyBullets = mutableListOf<Bullet>()
    val enemies = mutableListOf<Enemy>()
    val sparks = mutableListOf<Spark>()

    private var fireInMs = 0L
    private var spawnInMs = 1000L

    val level: Int get() = Shmup.levelAt(timeMs)
    val isOver: Boolean get() = lives <= 0

    /** Remet la partie à neuf : trois vies, plus de score, plus aucun objet sur le terrain. */
    fun reset() {
        playerX = Shmup.WIDTH / 2f
        playerY = Shmup.START_Y
        score = 0
        lives = Shmup.LIVES
        timeMs = 0L
        invulnMs = 0L
        scroll = 0f
        bullets.clear()
        enemyBullets.clear()
        enemies.clear()
        sparks.clear()
        fireInMs = 0L
        spawnInMs = 1000L
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
        scroll += Shmup.SCROLL_SPEED * dt
        invulnMs = max(0L, invulnMs - ms)

        // Tir automatique : deux tirs côte à côte.
        fireInMs -= ms
        if (fireInMs <= 0L) {
            fireInMs += Shmup.FIRE_EVERY_MS
            val muzzleY = playerY - Shmup.SHIP_H / 2f
            bullets += Bullet(playerX - 0.3f, muzzleY, 0f, -Shmup.BULLET_SPEED)
            bullets += Bullet(playerX + 0.3f, muzzleY, 0f, -Shmup.BULLET_SPEED)
        }

        // Arrivée des ennemis.
        spawnInMs -= ms
        if (spawnInMs <= 0L) {
            spawnInMs += Shmup.spawnEveryMs(level)
            spawnWave()
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
                    val jitter = if (e.type.fireEveryMs >= 800L) random.nextInt(300) else 0
                    e.shootInMs = e.type.fireEveryMs + jitter
                    // Il ne tire que s'il est à l'écran et pas trop près du bas.
                    if (e.y > 0.4f && e.y < Shmup.HEIGHT * 0.78f) fireFrom(e)
                }
            }
        }
        for (s in sparks) {
            s.x += s.vx * dt
            s.y += s.vy * dt
            s.lifeMs -= ms
        }

        // Les tirs du joueur touchent les ennemis.
        val shots = bullets.iterator()
        while (shots.hasNext()) {
            val b = shots.next()
            val target = enemies.firstOrNull {
                it.hp > 0 &&
                    abs(b.x - it.x) < Shmup.BULLET_HALF_W + it.type.hitW &&
                    abs(b.y - it.y) < Shmup.BULLET_HALF_H + it.type.hitH
            } ?: continue
            target.hp--
            if (target.hp <= 0) {
                score += target.type.points * level
                burst(target.x, target.y, 10 + (target.type.h * 6f).toInt())
            } else {
                burst(b.x, b.y, 3)
            }
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
                // Un petit ennemi se détruit en percutant ; un gros encaisse trois tirs d'un coup et continue.
                rammer.hp -= 3
                if (rammer.hp <= 0) burst(rammer.x, rammer.y, 14)
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

        // Nettoyage de ce qui est mort ou sorti du terrain.
        enemies.removeAll { it.hp <= 0 || it.isGone() }
        bullets.removeAll { it.y < -1f }
        enemyBullets.removeAll { it.y < -1f || it.y > Shmup.HEIGHT + 1f || it.x < -1f || it.x > Shmup.WIDTH + 1f }
        sparks.removeAll { it.lifeMs <= 0L }
    }

    /** Fait arriver un type d'ennemi tiré parmi ceux du niveau (avec son escorte s'il vient en groupe). */
    private fun spawnWave() {
        val lvl = level
        val pool = Shmup.poolAt(lvl)
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
        val phase = random.nextFloat() * TWO_PI
        val hp = type.hp + max(0, lvl - type.minLevel) / 3
        val n = type.group
        for (i in 0 until n) {
            val off = i - (n - 1) / 2f
            val x = if (n == 1) baseX else (baseX + off * (type.w + 0.5f)).coerceIn(0.8f, Shmup.WIDTH - 0.8f)
            val y = -type.h / 2f - abs(off) * 0.9f
            val delay = type.fireEveryMs / 2 + random.nextInt(600)
            enemies += Enemy(type, x, y, dir, phase, hp, shootInMs = delay)
        }
    }

    private fun fireFrom(e: Enemy) {
        val t = e.type
        val speed = t.bulletSpeed
        val x = e.x
        val muzzleY = e.y + t.h * 0.25f
        when (t.fire) {
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

    private fun hurt() {
        lives--
        invulnMs = Shmup.INVULN_MS
        burst(playerX, playerY, 24)
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
        out += scroll.toRawBits()
        out += fireInMs.toInt()
        out += spawnInMs.toInt()
        for (list in listOf(bullets, enemyBullets)) {
            out += list.size
            for (b in list) {
                out += b.x.toRawBits()
                out += b.y.toRawBits()
                out += b.vx.toRawBits()
                out += b.vy.toRawBits()
            }
        }
        out += enemies.size
        for (e in enemies) {
            out += e.type.ordinal
            out += e.spawnX.toRawBits()
            out += e.spawnY.toRawBits()
            out += e.dir.toRawBits()
            out += e.phase.toRawBits()
            out += e.hp
            out += e.ageMs.toInt()
            out += e.shootInMs.toInt()
            out += e.aux.toRawBits()
            out += e.shots
        }
        return out
    }

    companion object {
        private const val SAVE_VERSION = 2
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
            game.scroll = float()
            game.fireInMs = int().toLong()
            game.spawnInMs = int().toLong()
            for (list in listOf(game.bullets, game.enemyBullets)) {
                repeat(count()) { list += Bullet(float(), float(), float(), float()) }
            }
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
