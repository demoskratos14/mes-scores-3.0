package com.aventure.messcores

import kotlin.math.hypot
import kotlin.math.max
import kotlin.random.Random

/** Identifiant du jeu des Ronds parmi les jeux prédéfinis (écran à part : voir [CirclesScreen]). */
internal const val CIRCLES_ID = "builtin_circles"

/** Record du niveau difficile : à part de celui du niveau normal, car les deux séries ne se valent pas. */
internal const val CIRCLES_HARD_ID = "builtin_circles_hard"

/** Le niveau de la séance : au niveau [HARD], des têtes de mort piègent l'écran en plus des ronds. */
internal enum class CirclesLevel {
    NORMAL,
    HARD;

    /** L'identifiant sous lequel le record du niveau est gardé. */
    val recordId: String get() = if (this == HARD) CIRCLES_HARD_ID else CIRCLES_ID

    companion object {
        /** Le niveau de numéro [index] ; le niveau normal si le numéro ne désigne rien. */
        fun fromIndex(index: Int): CirclesLevel = entries.getOrElse(index) { NORMAL }
    }
}

/** Un point de l'écran de jeu, en pixels (indépendant de Compose pour pouvoir être testé). */
internal data class CirclePoint(val x: Float, val y: Float)

/** Réglages et calculs du jeu des Ronds. */
internal object Circles {

    /** Un téléphone qu'on se passe : seul ou jusqu'à huit joueurs. */
    const val MIN_PLAYERS = 1
    const val MAX_PLAYERS = 8

    /** Durée de vie du premier rond, du moment où il apparaît à celui où il disparaît. */
    const val START_LIFETIME_MS = 2_000L

    /** Durée de vie plancher : un rond ne devient jamais plus rapide que cela. */
    const val MIN_LIFETIME_MS = 600L

    /** Temps retiré à la durée de vie du rond suivant chaque fois qu'un rond est touché. */
    const val LIFETIME_STEP_MS = 40L

    /**
     * Part du rayon de départ qui reste toujours touchable, même quand le rond est devenu minuscule :
     * sans cela, les derniers instants d'un rond seraient impossibles à toucher avec un doigt.
     */
    const val MIN_HIT_FRACTION = 0.4f

    /** Distance minimale (en rayons de départ) entre deux ronds qui se suivent, pour qu'on doive bouger le doigt. */
    private const val MIN_SPREAD_RADII = 2.5f

    /** Nombre d'essais pour trouver un emplacement assez éloigné du rond précédent. */
    private const val SPAWN_TRIES = 12

    /** Rayon d'une tête de mort, en part du rayon de départ d'un rond : elle ne rétrécit pas, on la voit toujours en entier. */
    const val SKULL_RADIUS_FRACTION = 0.8f

    /** Série à partir de laquelle les têtes de mort apparaissent (niveau difficile) : le temps de se mettre dans le jeu. */
    const val SKULLS_FROM_STREAK = 2

    /** Une tête de mort de plus tous les [SKULLS_STEP] ronds touchés. */
    const val SKULLS_STEP = 6

    /** Nombre maximal de têtes de mort en même temps : au-delà, l'écran serait trop encombré pour jouer. */
    const val MAX_SKULLS = 3

    /** Espace minimal laissé entre une tête de mort et le rond ou une autre tête de mort, en part du rayon de départ d'un rond. */
    private const val SKULL_GAP_RADII = 0.3f

    /** Nombre d'essais pour placer une tête de mort sans qu'elle touche le rond ou une autre tête de mort. */
    private const val SKULL_TRIES = 20

    /**
     * Nombre de têtes de mort qui accompagnent le rond qu'on voit apparaître après [streak] ronds touchés : aucune au
     * niveau normal ou tant que la série est courte, puis une de plus tous les [SKULLS_STEP] ronds, jusqu'à [MAX_SKULLS].
     */
    fun skullCount(level: CirclesLevel, streak: Int): Int {
        if (level != CirclesLevel.HARD || streak < SKULLS_FROM_STREAK) return 0
        return (1 + (streak - SKULLS_FROM_STREAK) / SKULLS_STEP).coerceAtMost(MAX_SKULLS)
    }

    /** Vrai si le toucher en ([tapX], [tapY]) atteint la tête de mort [skull] de rayon [skullRadius] : pas de marge, on touche ce qu'on voit. */
    fun isSkullHit(tapX: Float, tapY: Float, skull: CirclePoint, skullRadius: Float): Boolean =
        hypot(tapX - skull.x, tapY - skull.y) <= skullRadius

    /**
     * Tire au hasard l'emplacement de [count] têtes de mort de rayon [skullRadius] dans une zone de [width] × [height]
     * pixels. Chacune reste entièrement dans la zone et loin du rond (de centre [circle], à son rayon de départ
     * [maxRadius]) comme des autres têtes de mort : on ne peut jamais toucher l'une en visant l'autre. Si la zone est
     * trop petite pour en placer autant, on en renvoie moins (voire aucune).
     */
    fun randomSkulls(
        width: Float,
        height: Float,
        circle: CirclePoint,
        maxRadius: Float,
        skullRadius: Float,
        count: Int,
        random: Random = Random.Default
    ): List<CirclePoint> {
        val minX = skullRadius
        val maxX = width - skullRadius
        val minY = skullRadius
        val maxY = height - skullRadius
        if (count <= 0 || maxX <= minX || maxY <= minY) return emptyList()
        val gap = maxRadius * SKULL_GAP_RADII
        val placed = mutableListOf<CirclePoint>()
        for (skull in 0 until count) {
            for (attempt in 0 until SKULL_TRIES) {
                val candidate = CirclePoint(random.nextFloat() * (maxX - minX) + minX, random.nextFloat() * (maxY - minY) + minY)
                val farFromCircle = hypot(candidate.x - circle.x, candidate.y - circle.y) >= maxRadius + skullRadius + gap
                val farFromSkulls = placed.all { hypot(candidate.x - it.x, candidate.y - it.y) >= 2f * skullRadius + gap }
                if (farFromCircle && farFromSkulls) {
                    placed += candidate
                    break
                }
            }
        }
        return placed
    }

    /** Durée de vie du rond qu'on voit apparaître après [streak] ronds touchés. */
    fun lifetimeMs(streak: Int): Long =
        max(MIN_LIFETIME_MS, START_LIFETIME_MS - streak.coerceAtLeast(0) * LIFETIME_STEP_MS)

    /** Rayon du rond [elapsedMs] millisecondes après son apparition : de [maxRadius] à 0, en ligne droite. */
    fun radiusAt(elapsedMs: Long, lifetimeMs: Long, maxRadius: Float): Float {
        if (lifetimeMs <= 0L) return 0f
        val left = 1f - elapsedMs.coerceAtLeast(0L).toFloat() / lifetimeMs.toFloat()
        return maxRadius * left.coerceIn(0f, 1f)
    }

    /** Vrai tant que le rond est encore là : son temps n'est pas écoulé. */
    fun isAlive(elapsedMs: Long, lifetimeMs: Long): Boolean = elapsedMs < lifetimeMs

    /**
     * Vrai si le toucher en ([tapX], [tapY]) atteint le rond de centre ([centerX], [centerY]) et de rayon [radius].
     * Le rayon pris en compte ne descend jamais sous [MIN_HIT_FRACTION] du rayon de départ [maxRadius].
     */
    fun isHit(tapX: Float, tapY: Float, centerX: Float, centerY: Float, radius: Float, maxRadius: Float): Boolean {
        val reach = max(radius, maxRadius * MIN_HIT_FRACTION)
        return hypot(tapX - centerX, tapY - centerY) <= reach
    }

    /**
     * Tire au hasard le centre du prochain rond dans une zone de [width] × [height] pixels : le rond, à son rayon de
     * départ [maxRadius], reste entièrement dans la zone. Si [previous] est donné, on cherche un emplacement assez
     * éloigné de lui. Une zone trop petite pour le rond donne son centre.
     */
    fun randomCenter(
        width: Float,
        height: Float,
        maxRadius: Float,
        previous: CirclePoint? = null,
        random: Random = Random.Default
    ): CirclePoint {
        val minX = maxRadius
        val maxX = width - maxRadius
        val minY = maxRadius
        val maxY = height - maxRadius
        if (maxX <= minX || maxY <= minY) return CirclePoint(width / 2f, height / 2f)
        var best = CirclePoint(random.nextFloat() * (maxX - minX) + minX, random.nextFloat() * (maxY - minY) + minY)
        if (previous == null) return best
        var bestDistance = hypot(best.x - previous.x, best.y - previous.y)
        repeat(SPAWN_TRIES) {
            if (bestDistance >= maxRadius * MIN_SPREAD_RADII) return best
            val candidate = CirclePoint(random.nextFloat() * (maxX - minX) + minX, random.nextFloat() * (maxY - minY) + minY)
            val distance = hypot(candidate.x - previous.x, candidate.y - previous.y)
            if (distance > bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }
}

/**
 * La séance des Ronds sur un seul téléphone qu'on se passe : chaque joueur joue une série à son tour (le nombre de
 * ronds touchés d'affilée) et la plus longue série gagne.
 *
 * Les joueurs sont repérés par leur place (0, 1, ...) dans l'ordre de jeu. [streaks] contient les séries des joueurs
 * qui ont déjà joué, dans l'ordre : le joueur courant est donc le suivant.
 */
internal data class CirclesGame(
    val players: Int,
    val streaks: List<Int> = emptyList()
) {
    /** La place du joueur qui doit jouer (ou [players] quand tout le monde a joué). */
    val current: Int get() = streaks.size

    val isOver: Boolean get() = streaks.size >= players

    /** La plus longue série jouée jusqu'ici (0 si personne n'a encore joué ou touché un rond). */
    val best: Int get() = streaks.maxOrNull() ?: 0

    /** Les places gagnantes une fois la partie finie : celles qui ont la plus longue série. Vide si personne n'a touché un rond. */
    val winners: List<Int>
        get() = if (!isOver || best <= 0) emptyList() else streaks.indices.filter { streaks[it] == best }

    /** La série de la place [seat], ou `null` si elle n'a pas encore joué. */
    fun streakOf(seat: Int): Int? = streaks.getOrNull(seat)

    /** Les places classées de la plus longue série à la plus courte (à égalité, dans l'ordre de jeu). */
    fun ranking(): List<Int> = streaks.indices.sortedByDescending { streaks[it] }

    /** Le joueur courant a fini sa série de [streak] ronds : au suivant. Sans effet quand la partie est finie. */
    fun finishRun(streak: Int): CirclesGame =
        if (isOver) this else copy(streaks = streaks + streak.coerceAtLeast(0))

    /** Une nouvelle partie avec les mêmes joueurs. */
    fun newGame(): CirclesGame = CirclesGame(players)
}
