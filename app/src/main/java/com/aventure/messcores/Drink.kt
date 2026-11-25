package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Identifiant du jeu « La descente » (boire le plus possible en deux minutes) dans la liste des jeux. */
const val DRINK_ID = "builtin_drink"

// Les quatre touches du jeu : la croix (gauche, droite) pour rétablir l'équilibre, les boutons A et B pour boire.
internal const val DRINK_LEFT = 0
internal const val DRINK_RIGHT = 1
internal const val DRINK_A = 2
internal const val DRINK_B = 3

/**
 * La descente : le personnage du Drive doit boire le plus possible en deux minutes. Chaque appui sur A ou B est une gorgée
 * (1 cl) ; un verre de 25 cl se vide en 25 gorgées, puis un verre plein arrive tout seul. De temps en temps, le personnage se met à
 * chanceler d'un côté ou de l'autre, de plus en plus fort : tant qu'il n'a pas retrouvé son équilibre avec la flèche du côté
 * opposé, il ne peut plus boire. Appuyer sur la mauvaise flèche le fait pencher davantage. S'il penche à fond sans avoir été rattrapé, il
 * tombe : il reste à terre un moment, sans pouvoir rien faire. De temps en temps, l'envie le prend : il va à l'urinoir, d'un côté
 * de l'écran, et il faut marteler A ou B pour qu'il se soulage, avant qu'il ne revienne. Plus la partie avance, plus il chancelle
 * souvent et vite. Le score est la quantité bue, en centilitres.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [DrinkGame].
 */
internal object Drink {
    /** L'écran est le même que celui de Drive (720 x 405), pour reprendre sa console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    /** La durée d'une partie : deux minutes. */
    const val DURATION_MS = 120_000f

    /** Une gorgée vaut un centilitre ; un verre en contient [GLASS_GULPS]. */
    const val GULP_CL = 1
    const val GLASS_GULPS = 25

    // L'équilibre : -1 (penché à fond à gauche) à 1 (penché à fond à droite), 0 : debout.
    /** Inclinaison du personnage quand il se met à chanceler. */
    const val START_LEAN = 0.35f

    /** Au-dessous de cette inclinaison, après un appui de la bonne flèche, le personnage est de nouveau d'aplomb. */
    const val STEADY = 0.1f

    /** Ce que retire un appui sur la bonne flèche, ce qu'ajoute un appui sur la mauvaise. */
    const val PUSH = 0.14f
    const val WRONG_PUSH = 0.1f

    /** Vitesse à laquelle il penche tout seul (par seconde), au début puis à la fin de la partie. */
    const val GROWTH_START = 0.3f
    const val GROWTH_END = 0.65f

    /** Délai entre la fin d'un déséquilibre et le suivant (min et max, en millisecondes), au début puis à la fin de la partie. */
    const val GAP_MIN_START = 3000f
    const val GAP_MAX_START = 6000f
    const val GAP_MIN_END = 1400f
    const val GAP_MAX_END = 3000f

    /** Avant le premier déséquilibre : un temps pour prendre le rythme (min et max). */
    const val FIRST_GAP_MIN = 3500f
    const val FIRST_GAP_MAX = 5500f

    // Les durées des animations (en millisecondes).
    /** Le verre reste levé à la bouche autant de temps après une gorgée. */
    const val SIP_MS = 220f
    const val REFILL_MS = 450f
    const val BLOCKED_MS = 300f

    /** Le jeu se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f

    /** Le temps passé par terre après une chute (millisecondes). */
    const val FALL_MS = 2200f

    // L'urinoir : les étapes de l'envie pressante.
    /** Pas d'envie. */
    const val PEE_NONE = 0

    /** L'envie se fait sentir : un avertissement, mais il peut encore boire. */
    const val PEE_URGE = 1

    /** Il marche vers l'urinoir. */
    const val PEE_GOING = 2

    /** Il est devant l'urinoir : il faut marteler A ou B. */
    const val PEE_RELIEF = 3

    /** Il revient à sa place. */
    const val PEE_BACK = 4

    /** Le temps de l'avertissement, puis de chaque trajet entre sa place et l'urinoir (millisecondes). */
    const val URGE_MS = 1800f
    const val WALK_MS = 1000f

    /** Le nombre d'appuis sur A ou B pour se soulager. */
    const val PEE_PRESSES = 30

    /** Le délai avant la première envie, puis entre la fin d'une envie et la suivante (min et max, en millisecondes). */
    const val PEE_FIRST_MIN = 20_000f
    const val PEE_FIRST_MAX = 30_000f
    const val PEE_GAP_MIN = 20_000f
    const val PEE_GAP_MAX = 32_000f

    /** La distance entre le centre de l'écran et l'urinoir (le personnage s'arrête un peu avant), en pixels de l'écran. */
    const val URINAL_DISTANCE = 280f
}

/**
 * Une partie de La descente. [tick] fait avancer le temps, [press] reçoit les touches ([DRINK_LEFT] à [DRINK_B]).
 */
internal class DrinkGame(private val random: Random = Random.Default) {
    /** La quantité bue, en centilitres : c'est le score. */
    var cl = 0
        private set

    /** Le nombre de verres vidés en entier. */
    var glasses = 0
        private set

    /** Les gorgées prises dans le verre en cours (0 : plein). */
    var gulpsInGlass = 0
        private set

    /** Combien de fois le personnage a chancelé et retrouvé l'équilibre. */
    var recoveries = 0
        private set

    /** L'inclinaison : -1 (à gauche) à 1 (à droite), 0 quand il est d'aplomb. */
    var lean = 0f
        private set

    /** Le temps qui reste (millisecondes). */
    var timeLeftMs = Drink.DURATION_MS
        private set

    var over = false
        private set
    var overMs = 0f
        private set

    /** L'horloge de la partie, pour les animations (millisecondes). */
    var clockMs = 0f
        private set

    /** Temps restant avant que le verre ne redescende après une gorgée, avant la fin du remplissage, après une gorgée refusée. */
    var sipMs = 0f
        private set
    var refillMs = 0f
        private set
    var blockedMs = 0f
        private set

    /** Le nombre de chutes. */
    var falls = 0
        private set

    /** Temps qu'il lui reste à passer par terre (0 : debout). Quand il est par terre, [lean] vaut -1 ou 1 (le côté où il est tombé). */
    var fallMs = 0f
        private set

    /** Le nombre de fois où il est allé à l'urinoir. */
    var pees = 0
        private set

    /** L'étape de l'envie pressante ([Drink.PEE_NONE] à [Drink.PEE_BACK]). */
    var peePhase = Drink.PEE_NONE
        private set

    /** Le temps qu'il reste à l'étape en cours (avertissement et trajets). */
    var peeMs = 0f
        private set

    /** Les appuis déjà donnés devant l'urinoir. */
    var peeProgress = 0
        private set

    /** Le côté de l'urinoir : -1 à gauche, 1 à droite. */
    var peeSide = 1
        private set

    private var wobbleInMs = 0f
    private var peeInMs = 0f

    init {
        reset()
    }

    /** Une partie neuve. */
    fun reset() {
        cl = 0
        glasses = 0
        gulpsInGlass = 0
        recoveries = 0
        lean = 0f
        timeLeftMs = Drink.DURATION_MS
        over = false
        overMs = 0f
        clockMs = 0f
        sipMs = 0f
        refillMs = 0f
        blockedMs = 0f
        falls = 0
        fallMs = 0f
        pees = 0
        peePhase = Drink.PEE_NONE
        peeMs = 0f
        peeProgress = 0
        peeSide = 1
        wobbleInMs = between(Drink.FIRST_GAP_MIN, Drink.FIRST_GAP_MAX)
        peeInMs = between(Drink.PEE_FIRST_MIN, Drink.PEE_FIRST_MAX)
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 (début) à 1 (dernière seconde). */
    val difficulty: Float
        get() = (1f - timeLeftMs / Drink.DURATION_MS).coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    private fun between(low: Float, high: Float): Float = low + random.nextFloat() * (high - low)

    private fun nextGap(): Float =
        between(lerp(Drink.GAP_MIN_START, Drink.GAP_MIN_END), lerp(Drink.GAP_MAX_START, Drink.GAP_MAX_END))

    // ---------------------------------------------------------------- état

    /** Vrai quand le personnage chancelle (ou qu'il est tombé) : il ne peut pas boire avant d'avoir retrouvé son équilibre. */
    val unsteady: Boolean get() = lean != 0f

    /** Vrai quand il est par terre. */
    val fallen: Boolean get() = fallMs > 0f

    /** Vrai quand il est occupé avec l'urinoir (en route, devant ou au retour) : il ne boit plus. */
    val busy: Boolean get() = peePhase >= Drink.PEE_GOING

    /** Sa place entre son bar (0) et l'urinoir (1), pour le dessin. */
    val walk: Float
        get() = when (peePhase) {
            Drink.PEE_GOING -> 1f - (peeMs / Drink.WALK_MS).coerceIn(0f, 1f)
            Drink.PEE_RELIEF -> 1f
            Drink.PEE_BACK -> (peeMs / Drink.WALK_MS).coerceIn(0f, 1f)
            else -> 0f
        }

    /** Le soulagement, de 0 à 1, pour la jauge. */
    val relief: Float get() = (peeProgress.toFloat() / Drink.PEE_PRESSES).coerceIn(0f, 1f)

    /** Le niveau du verre en cours : 1 plein, 0 vide. */
    val level: Float get() = 1f - gulpsInGlass.toFloat() / Drink.GLASS_GULPS

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Drink.RESTART_DELAY_MS

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        sipMs = max(0f, sipMs - dt)
        refillMs = max(0f, refillMs - dt)
        blockedMs = max(0f, blockedMs - dt)
        if (over) {
            overMs += dt
            return
        }
        timeLeftMs -= dt
        if (timeLeftMs <= 0f) {
            timeLeftMs = 0f
            lean = 0f
            fallMs = 0f
            peePhase = Drink.PEE_NONE
            over = true
            overMs = 0f
            return
        }
        if (fallen) {
            fallMs -= dt
            if (fallMs <= 0f) {
                // Il se relève, d'aplomb.
                fallMs = 0f
                lean = 0f
                wobbleInMs = nextGap()
            }
            return
        }
        if (peePhase != Drink.PEE_NONE) {
            tickPee(dt)
            return
        }
        if (unsteady) {
            val direction = if (lean > 0f) 1f else -1f
            val grown = min(1f, abs(lean) + lerp(Drink.GROWTH_START, Drink.GROWTH_END) * dt / 1000f)
            lean = direction * grown
            if (grown >= 1f) {
                // Penché à fond sans avoir été rattrapé : il tombe.
                falls++
                fallMs = Drink.FALL_MS
            }
        } else {
            peeInMs -= dt
            wobbleInMs -= dt
            if (peeInMs <= 0f) {
                peePhase = Drink.PEE_URGE
                peeMs = Drink.URGE_MS
                peeSide = if (random.nextBoolean()) 1 else -1
            } else if (wobbleInMs <= 0f) {
                startWobble()
            }
        }
    }

    /** Fait avancer l'envie pressante : l'avertissement, le trajet aller, l'attente du soulagement, le trajet retour. */
    private fun tickPee(dt: Float) {
        if (peePhase == Drink.PEE_RELIEF) return
        peeMs -= dt
        if (peeMs > 0f) return
        when (peePhase) {
            Drink.PEE_URGE -> {
                peePhase = Drink.PEE_GOING
                peeMs = Drink.WALK_MS
            }
            Drink.PEE_GOING -> {
                peePhase = Drink.PEE_RELIEF
                peeMs = 0f
                peeProgress = 0
            }
            Drink.PEE_BACK -> {
                peePhase = Drink.PEE_NONE
                peeMs = 0f
                peeInMs = between(Drink.PEE_GAP_MIN, Drink.PEE_GAP_MAX)
                wobbleInMs = max(wobbleInMs, between(Drink.FIRST_GAP_MIN * 0.5f, Drink.FIRST_GAP_MAX * 0.5f))
            }
        }
    }

    private fun startWobble() {
        val side = if (random.nextBoolean()) 1f else -1f
        lean = side * Drink.START_LEAN
    }

    // ---------------------------------------------------------------- joueur

    /** Une touche ([DRINK_LEFT] à [DRINK_B]) vient d'être pressée. */
    fun press(key: Int) {
        if (over) return
        when (key) {
            DRINK_A, DRINK_B -> if (busy) relieve() else drink()
            DRINK_LEFT -> steer(-1)
            DRINK_RIGHT -> steer(1)
        }
    }

    /** Un appui sur A ou B devant l'urinoir : le soulagement avance ; une fois complet, il rentre à sa place. */
    private fun relieve() {
        if (peePhase != Drink.PEE_RELIEF) return
        peeProgress++
        if (peeProgress >= Drink.PEE_PRESSES) {
            pees++
            peePhase = Drink.PEE_BACK
            peeMs = Drink.WALK_MS
        }
    }

    private fun drink() {
        if (unsteady) {
            blockedMs = Drink.BLOCKED_MS
            return
        }
        cl += Drink.GULP_CL
        sipMs = Drink.SIP_MS
        gulpsInGlass++
        if (gulpsInGlass >= Drink.GLASS_GULPS) {
            gulpsInGlass = 0
            glasses++
            refillMs = Drink.REFILL_MS
        }
    }

    /** Un appui sur la flèche de [direction] (-1 gauche, 1 droite) : la bonne redresse, la mauvaise fait pencher davantage. */
    private fun steer(direction: Int) {
        if (!unsteady || fallen) return
        val leansRight = lean > 0f
        // Pencher à droite se corrige vers la gauche, et inversement.
        val correcting = (direction < 0) == leansRight
        val side = if (leansRight) 1f else -1f
        if (correcting) {
            val left = abs(lean) - Drink.PUSH
            if (left <= Drink.STEADY) {
                lean = 0f
                recoveries++
                wobbleInMs = nextGap()
            } else {
                lean = side * left
            }
        } else {
            lean = side * min(1f, abs(lean) + Drink.WRONG_PUSH)
        }
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        put(SAVE_VERSION)
        put(cl); put(glasses); put(gulpsInGlass); put(recoveries); put(lean); put(timeLeftMs)
        put(over); put(overMs); put(clockMs); put(sipMs); put(refillMs); put(blockedMs); put(wobbleInMs)
        put(falls); put(fallMs); put(pees); put(peePhase); put(peeMs); put(peeProgress); put(peeSide); put(peeInMs)
        return out
    }

    private fun load(values: List<Double>) {
        var at = 0
        fun num(): Double = values[at++]
        fun int(): Int = num().toInt()
        fun float(): Float = num().toFloat()
        fun flag(): Boolean = num() != 0.0
        require(int() == SAVE_VERSION)
        cl = int(); glasses = int(); gulpsInGlass = int().coerceIn(0, Drink.GLASS_GULPS - 1)
        recoveries = int(); lean = float().coerceIn(-1f, 1f); timeLeftMs = float()
        over = flag(); overMs = float(); clockMs = float(); sipMs = float(); refillMs = float(); blockedMs = float(); wobbleInMs = float()
        falls = int(); fallMs = float(); pees = int(); peePhase = int().coerceIn(Drink.PEE_NONE, Drink.PEE_BACK)
        peeMs = float(); peeProgress = int().coerceIn(0, Drink.PEE_PRESSES - 1); peeSide = if (int() < 0) -1 else 1; peeInMs = float()
    }

    companion object {
        private const val SAVE_VERSION = 2

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): DrinkGame {
            val game = DrinkGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
