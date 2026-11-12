package com.aventure.messcores

import kotlin.random.Random

/** Identifiant du jeu Pancake parmi les jeux prédéfinis (écran à part : voir [PancakeScreen]). */
internal const val PANCAKE_ID = "builtin_pancake"

/** Les trois voies du jeu, de gauche à droite : le serveur se tient dans l'une d'elles. */
internal const val PANCAKE_LEFT = 0
internal const val PANCAKE_CENTER = 1
internal const val PANCAKE_RIGHT = 2

/**
 * Réglages du jeu Pancake, refait d'après un jeu LCD de 1981 (VTech / Time Fun). Toutes les durées ont été mesurées sur
 * une vidéo du jeu d'origine. Le temps du jeu se compte en « unités » de [UNIT_MS] ; un pas de crêpe en vaut 3.
 */
internal object Pancake {

    /** Une unité de temps : un tiers de pas (7/32 de seconde). */
    const val UNIT_MS = 218.75

    /** Un pas de crêpe : 3 unités (0,656 s). */
    const val TICK_UNITS = 3

    /** Un cycle du cuisinier : 2 pas. Un nouveau lancer ne peut partir qu'au début d'un cycle. */
    const val CYCLE_UNITS = 6

    /** Au départ, la première crêpe est lancée après 2 cycles. */
    const val FIRST_LAUNCH_CYCLES = 2

    /** Nombre de crêpes ratées qui finit la partie (les cinq marqueurs en haut à droite). */
    const val MAX_MISSES = 5

    /** Durée de la crêpe écrasée au sol, pendant laquelle tout le jeu attend. */
    const val SPLAT_MS = 800.0

    /** Nombre de crêpes que le jeu accepte en l'air en même temps. */
    const val MAX_IN_FLIGHT = 4

    /** À partir de ce score, des crêpes peuvent être lancées avant que la précédente soit servie. */
    const val BUSY_SCORE = 5

    /** Chance, à chaque cycle, qu'une crêpe de plus soit lancée quand le jeu est « chargé » (mesurée : environ 17 %). */
    const val LAUNCH_CHANCE = 0.17

    /** Le serveur doit être dans la voie au plus tard une unité après l'arrivée de la crêpe sur l'assiette. */
    const val CATCH_GRACE_UNITS = 1.1

    // Dates, en unités depuis le lancer, où la crêpe arrive sur l'assiette de chaque voie (côté droit, puis centre, puis gauche).
    const val RIGHT_PLATE = 21
    const val CENTER_PLATE = 47
    const val LEFT_PLATE = 67

    /** Durée de la crêpe posée sur l'assiette quand elle est attrapée : 2 unités. */
    const val CATCH_UNITS = 2

    /** Dernière position de la crêpe avant le client, puis le client reçoit, puis le score monte. */
    const val LAST_STEP = 69
    const val DINER_AT = 75
    const val SCORE_AT = 81

    /** Les dates (en unités) de chaque position de la trajectoire hors assiettes : crêpe visible de [start] à [start] + 3. */
    private val STEPS = listOf(
        0, 3, 6, 9, 12, 15, 18, // lancer, arc vers la droite, puis la crêpe au-dessus de l'assiette droite
        23, 26, 29, 32, 35, 38, 41, 44, // remonte, sommet, redescend vers l'assiette centrale
        49, 52, 55, 58, 61, 64, // remonte, arc vers la gauche, au-dessus de l'assiette gauche
        69 // dernière position avant le client
    )

    /** Dates où chaque position de [STEPS] commence : position numéro [i] du dessin. */
    fun stepStart(i: Int): Int = STEPS[i]

    val stepCount: Int get() = STEPS.size

    /** Dessin de la crêpe, selon le temps écoulé depuis son lancer. */
    sealed interface Sprite {
        /** Position numéro [index] de la trajectoire (0 à 21), les trois positions « au-dessus de l'assiette » comprises. */
        data class Step(val index: Int) : Sprite

        /** La crêpe posée sur l'assiette du serveur dans la voie [lane] (attrapée) ou juste avant de tomber. */
        data class OnPlate(val lane: Int) : Sprite

        /** Les crêpes livrées sur la table du client. */
        data object Diner : Sprite
    }

    /** Voie de l'assiette atteinte à la date [unit] (en unités depuis le lancer), ou `null`. */
    fun plateLaneAt(unit: Int): Int? = when (unit) {
        RIGHT_PLATE -> PANCAKE_RIGHT
        CENTER_PLATE -> PANCAKE_CENTER
        LEFT_PLATE -> PANCAKE_LEFT
        else -> null
    }

    /** Le dessin visible à la date [unit] depuis le lancer pour une crêpe en vol (ni ratée ni servie), ou `null` s'il n'y en a pas. */
    fun spriteAt(unit: Double): Sprite? {
        val u = unit
        return when {
            u < 0.0 -> null
            u >= RIGHT_PLATE && u < RIGHT_PLATE + CATCH_UNITS -> Sprite.OnPlate(PANCAKE_RIGHT)
            u >= CENTER_PLATE && u < CENTER_PLATE + CATCH_UNITS -> Sprite.OnPlate(PANCAKE_CENTER)
            u >= LEFT_PLATE && u < LEFT_PLATE + CATCH_UNITS -> Sprite.OnPlate(PANCAKE_LEFT)
            u >= DINER_AT && u < DINER_AT + TICK_UNITS -> Sprite.Diner
            u >= SCORE_AT -> null
            else -> {
                val i = STEPS.indexOfLast { it <= u }
                if (i >= 0 && u < STEPS[i] + TICK_UNITS) Sprite.Step(i) else null
            }
        }
    }

    /** Les cinq positions du cuisinier : durée de chacune en unités, dans l'ordre de la boucle (6 unités en tout). */
    val CHEF_PHASES: List<Pair<Double, Int>> = listOf(
        0.0 to 0, 0.9 to 1, 1.1 to 2, 2.9 to 3, 3.1 to 4, 3.9 to 5
    )

    /** Position du cuisinier (0 à 5) à la date [unit] du jeu. */
    fun chefPhaseAt(unit: Double): Int {
        val inCycle = unit - Math.floor(unit / CYCLE_UNITS) * CYCLE_UNITS
        return CHEF_PHASES.last { it.first <= inCycle }.second
    }
}

/** Une crêpe en vol : [age] est le temps écoulé depuis son lancer, en unités ; [splat] vaut vrai une fois tombée au sol. */
internal class PancakeFlight(var age: Double, var splat: Boolean = false, var lane: Int = PANCAKE_RIGHT, var decided: Int = 0)

/**
 * Une partie de Pancake. Le cuisinier lance des crêpes qui décrivent un grand arc de la droite vers la gauche en passant par
 * trois assiettes. Le serveur ne se tient que dans une voie à la fois (gauche, centre, droite) et saute d'une voie à la voisine : s'il est
 * sous la crêpe quand elle arrive sur son assiette, il l'attrape et la renvoie ; sinon elle s'écrase au sol, tout s'arrête un instant
 * et un marqueur s'allume. La crêpe renvoyée trois fois arrive chez le client : un point. Cinq crêpes tombées finissent la partie.
 *
 * Le jeu avance par [tick] : le temps y est compté en millisecondes, mais les durées sont celles du jeu d'origine
 * ([Pancake]). [random] décide des lancers.
 */
internal class PancakeGame(private val random: Random = Random.Default) {

    /** Les crêpes en vol, de la plus ancienne à la plus récente. */
    val flights = mutableListOf<PancakeFlight>()

    var waiter = PANCAKE_CENTER
        private set

    var score = 0
        private set

    var misses = 0
        private set

    var isOver = false
        private set

    /** Temps du jeu écoulé depuis le début, en unités (il s'arrête pendant la crêpe écrasée). */
    var clock = 0.0
        private set

    /** Temps restant à attendre pendant la crêpe écrasée, en millisecondes (0 quand le jeu avance). */
    var freezeMs = 0.0
        private set

    /** Voie où la crêpe écrasée est visible, ou -1. */
    var splatLane = -1
        private set

    /** Prochain début de cycle du cuisinier, en unités du temps du jeu. */
    private var nextLaunchCheck = Pancake.FIRST_LAUNCH_CYCLES.toDouble() * Pancake.CYCLE_UNITS

    init {
        reset()
    }

    /** Repart de zéro. */
    fun reset() {
        flights.clear()
        waiter = PANCAKE_CENTER
        score = 0
        misses = 0
        isOver = false
        clock = 0.0
        freezeMs = 0.0
        splatLane = -1
        nextLaunchCheck = Pancake.FIRST_LAUNCH_CYCLES.toDouble() * Pancake.CYCLE_UNITS
    }

    /** Le serveur saute dans la voie de gauche (sans effet s'il y est déjà, ou en pause). */
    fun moveLeft() {
        if (!isOver && waiter > PANCAKE_LEFT) waiter--
    }

    /** Le serveur saute dans la voie de droite. */
    fun moveRight() {
        if (!isOver && waiter < PANCAKE_RIGHT) waiter++
    }

    /** Fait passer [elapsedMs] millisecondes. */
    fun tick(elapsedMs: Long) {
        var left = elapsedMs.coerceAtLeast(0L).toDouble()
        // Le jeu avance par petits pas (au plus un quart d'unité) pour ne jamais sauter une décision.
        val maxStep = Pancake.UNIT_MS / 4
        while (left > 0.0 && !isOver) {
            val dt = minOf(left, maxStep)
            left -= dt
            advance(dt)
        }
    }

    private fun advance(ms: Double) {
        if (freezeMs > 0.0) {
            freezeMs -= ms
            if (freezeMs <= 0.0) {
                freezeMs = 0.0
                splatLane = -1
                flights.removeAll { it.splat }
            }
            return
        }
        val units = ms / Pancake.UNIT_MS
        clock += units
        for (f in flights) f.age += units
        resolveFlights()
        launchIfDue()
    }

    private fun resolveFlights() {
        val iter = flights.iterator()
        while (iter.hasNext()) {
            val f = iter.next()
            if (f.splat) continue
            // Décision à l'arrivée sur chaque assiette : le serveur est-il dans la voie ?
            val plates = listOf(
                Triple(Pancake.RIGHT_PLATE, PANCAKE_RIGHT, 1),
                Triple(Pancake.CENTER_PLATE, PANCAKE_CENTER, 2),
                Triple(Pancake.LEFT_PLATE, PANCAKE_LEFT, 3)
            )
            for ((start, lane, bit) in plates) {
                if (f.decided and bit != 0) continue
                if (f.age < start + Pancake.CATCH_GRACE_UNITS) continue
                f.decided = f.decided or bit
                if (waiter != lane) {
                    f.splat = true
                    f.lane = lane
                    misses++
                    splatLane = lane
                    freezeMs = Pancake.SPLAT_MS
                    if (misses >= Pancake.MAX_MISSES) isOver = true
                    return
                }
            }
            if (f.age >= Pancake.SCORE_AT) {
                score++
                iter.remove()
            }
        }
    }

    private fun launchIfDue() {
        while (clock >= nextLaunchCheck) {
            val inFlight = flights.count { !it.splat }
            val launch = when {
                inFlight == 0 -> true
                score >= Pancake.BUSY_SCORE && inFlight < Pancake.MAX_IN_FLIGHT -> random.nextDouble() < Pancake.LAUNCH_CHANCE
                else -> false
            }
            if (launch) flights.add(PancakeFlight(age = clock - nextLaunchCheck))
            nextLaunchCheck += Pancake.CYCLE_UNITS
        }
    }

    /** Les cinq positions du cuisinier ; voir [Pancake.chefPhaseAt]. */
    val chefPhase: Int get() = Pancake.chefPhaseAt(clock)

    /** Le dessin de chaque crêpe en vol à cet instant (les crêpes écrasées n'y sont pas : voir [splatLane]). */
    fun sprites(): List<Pancake.Sprite> = flights.filter { !it.splat }.mapNotNull { Pancake.spriteAt(it.age) }

    /** L'état de la partie en une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Int> = buildList {
        add(SAVE_VERSION)
        add(waiter)
        add(score)
        add(misses)
        add(if (isOver) 1 else 0)
        add((clock * 1000).toInt())
        add(freezeMs.toInt())
        add(splatLane)
        add((nextLaunchCheck * 1000).toInt())
        add(flights.size)
        for (f in flights) {
            add((f.age * 1000).toInt())
            add(if (f.splat) 1 else 0)
            add(f.lane)
            add(f.decided)
        }
    }

    companion object {
        private const val SAVE_VERSION = 1
        private const val SAVE_HEADER = 10

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): PancakeGame? {
            if (data.size < SAVE_HEADER || data[0] != SAVE_VERSION) return null
            val count = data[9]
            if (count !in 0..16 || data.size != SAVE_HEADER + count * 4) return null
            if (data[1] !in PANCAKE_LEFT..PANCAKE_RIGHT || data[2] < 0 || data[3] !in 0..Pancake.MAX_MISSES) return null
            if (data[4] !in 0..1 || data[5] < 0 || data[6] < 0 || data[7] !in -1..PANCAKE_RIGHT || data[8] < 0) return null
            val game = PancakeGame(random)
            game.waiter = data[1]
            game.score = data[2]
            game.misses = data[3]
            game.isOver = data[4] == 1
            game.clock = data[5] / 1000.0
            game.freezeMs = data[6].toDouble()
            game.splatLane = data[7]
            game.nextLaunchCheck = data[8] / 1000.0
            game.flights.clear()
            for (i in 0 until count) {
                val at = SAVE_HEADER + i * 4
                val lane = data[at + 2]
                if (data[at] < 0 || data[at + 1] !in 0..1 || lane !in PANCAKE_LEFT..PANCAKE_RIGHT || data[at + 3] !in 0..7) return null
                game.flights.add(PancakeFlight(data[at] / 1000.0, data[at + 1] == 1, lane, data[at + 3]))
            }
            return game
        }
    }
}
