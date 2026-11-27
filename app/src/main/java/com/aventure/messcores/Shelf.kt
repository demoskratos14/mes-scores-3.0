package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Identifiant du jeu « Mise en rayon » (rattraper les produits secs sur un plateau en équilibre) dans la liste des jeux. */
const val SHELF_ID = "builtin_shelf"

// Les quatre touches du jeu, un bit chacune : la croix (gauche, droite) pour se déplacer, A pour pencher le plateau à gauche,
// B pour le pencher à droite. Ces valeurs servent aussi de masque des touches tenues (voir [ShelfGame.tick]).
internal const val SHELF_LEFT = 1
internal const val SHELF_RIGHT = 2
internal const val SHELF_A = 4
internal const val SHELF_B = 8

// Les produits secs, dans l'ordre des tableaux de [Shelf] : pâtes, riz, farine, conserve, céréales, café.
internal const val SHELF_PASTA = 0
internal const val SHELF_RICE = 1
internal const val SHELF_FLOUR = 2
internal const val SHELF_CAN = 3
internal const val SHELF_CEREAL = 4
internal const val SHELF_COFFEE = 5

// Comment la journée s'est terminée.
internal const val SHELF_END_NONE = 0
internal const val SHELF_END_TIME = 1 // la journée est finie
internal const val SHELF_END_BREAKS = 2 // trop de casse : le patron ferme le rayon

/**
 * Mise en rayon. Des produits secs (pâtes, riz, farine, conserves, céréales, café) tombent du haut de l'écran ; le magasinier les
 * rattrape sur un plateau qu'il tient en équilibre. Croix : se déplacer à gauche et à droite. Boutons : A penche le plateau à
 * gauche, B le penche à droite, A et B ensemble le stabilisent. Un produit lourd ou loin du centre fait basculer le plateau ;
 * dès qu'il est trop penché les produits glissent et tombent (c'est de la casse). Les points ne se gagnent qu'au rayon : il y en a
 * un à chaque bord de l'écran, et il faut y tenir le plateau à plat un instant pour ranger toute la pile. Plus la pile est haute,
 * plus le multiplicateur est grand, et plus elle est dure à garder en équilibre.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [ShelfGame].
 */
internal object Shelf {
    /** L'écran est le même que celui de Drive et de Poissonnerie (720 x 405), pour reprendre leur console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    /** Durée de la journée (millisecondes) et nombre de produits cassés qui la terminent avant l'heure. */
    const val SHIFT_MS = 120_000f
    const val MAX_BREAKS = 5

    /** Le sol, où se tient le magasinier et où se brisent les produits (ordonnée en pixels de l'écran). */
    const val FLOOR_Y = 392f

    // ---------------------------------------------------------------- le magasinier et son plateau
    /** Le plateau pivote autour des mains du magasinier ; il mesure 2 x [HALF] de long. */
    const val PIVOT_Y = 326f
    const val HALF = 80f
    const val TRAY_THICK = 9f

    /** Le magasinier se déplace entre ces deux abscisses, à cette vitesse (pixels par seconde). */
    const val HERO_MIN_X = 60f
    const val HERO_MAX_X = 660f
    const val HERO_SPEED = 215f

    /** Accélération du magasinier (pixels par seconde carrée) : il met un instant à démarrer et à s'arrêter. */
    const val HERO_ACCEL = 1800f

    /** Les rayons : le magasinier est « au rayon » quand il est à moins de [SHELF_LEFT_X] à gauche, ou à plus de [SHELF_RIGHT_X] à droite. */
    const val SHELF_LEFT_X = 112f
    const val SHELF_RIGHT_X = 608f

    // ---------------------------------------------------------------- les produits
    const val ITEMS = 6

    /** Largeur et hauteur de chaque produit (pixels), dans l'ordre pâtes, riz, farine, conserve, céréales, café. */
    val ITEM_W = floatArrayOf(26f, 30f, 36f, 26f, 40f, 30f)
    val ITEM_H = floatArrayOf(40f, 34f, 44f, 26f, 52f, 22f)

    /** Le poids de chaque produit : ce qui fait basculer le plateau. */
    val ITEM_MASS = floatArrayOf(1f, 2f, 3f, 2f, 1f, 1.5f)

    /** La valeur de chaque produit une fois rangé au rayon. */
    val ITEM_POINTS = intArrayOf(10, 15, 20, 15, 20, 15)

    /** Le glissement de chaque produit sur le plateau penché : le sac de farine accroche, la conserve roule. */
    val ITEM_SLIP = floatArrayOf(1f, 0.8f, 0.6f, 1.7f, 1f, 1f)

    /** Poids de tirage de chaque produit au début puis à la fin de la journée (la farine est de plus en plus fréquente). */
    private val DRAW_START = floatArrayOf(3f, 3f, 1.2f, 2f, 2f, 2f)
    private val DRAW_END = floatArrayOf(2f, 2f, 3f, 2.5f, 2f, 2f)

    fun drawWeight(item: Int, difficulty: Float): Float = DRAW_START[item] + (DRAW_END[item] - DRAW_START[item]) * difficulty

    // ---------------------------------------------------------------- la chute
    /** Les produits tombent d'une abscisse tirée entre ces deux bornes, hors de portée d'un magasinier au rayon. */
    const val SPAWN_MIN_X = 200f
    const val SPAWN_MAX_X = 520f
    const val SPAWN_Y = -30f

    /** Aucun produit ne tombe si le magasinier est à plus de cette distance au-delà des bornes de chute : ses allers-retours au rayon sont sans risque. */
    const val SPAWN_REACH = 60f

    /** Vitesse de chute (pixels par seconde), au début puis à la fin de la journée. */
    const val FALL_SPEED_START = 150f
    const val FALL_SPEED_END = 255f

    /** Délai entre deux produits (millisecondes), au début puis à la fin ; le premier arrive vite. */
    const val SPAWN_START_MS = 1900f
    const val SPAWN_END_MS = 950f
    const val FIRST_SPAWN_MS = 700f

    /** Au plus ce nombre de produits en l'air à la fois, et ce nombre de produits sur le plateau (au-delà, le produit se brise). */
    const val MAX_FLYING = 3
    const val MAX_ON_TRAY = 8

    // ---------------------------------------------------------------- l'équilibre
    /** Le plateau penche jusqu'à cet angle (radians, environ 40 degrés) : au-delà il se renverse. */
    const val MAX_TILT = 0.7f

    /** Inertie du plateau à vide, puis effet de la pesanteur sur le poids décentré, puis force des mains pour A et B. */
    const val INERTIA = 12_000f
    const val GRAVITY_TORQUE = 150f
    const val HAND_TORQUE = 30_000f

    /** Les mains ramènent doucement le plateau à plat (rappel) et l'amortissent ; A et B ensemble freinent fort. */
    const val SPRING = 1.2f
    const val DAMPING = 2.0f
    const val BRAKE = 9f

    /** Un appui sur A ou B donne un petit élan, pour qu'un tapotement très bref compte aussi (radians par seconde). */
    const val NUDGE = 0.22f

    /** Poids de la hauteur d'une pile dans l'inertie du plateau, et couple de balancement par « poids x hauteur ». */
    const val PILE_INERTIA = 0.7f
    const val SWAY_TORQUE = 13f

    /** Une pile haute se secoue quand le magasinier démarre ou s'arrête : couple par accélération et par « poids x hauteur ». */
    const val MOVE_TORQUE = 0.04f

    /** Un produit qui atterrit secoue le plateau en proportion de son poids et de sa distance au centre. */
    const val IMPACT = 0.0011f

    /** Angle (radians) en dessous duquel un produit tient sans glisser : posé sur le plateau, puis posé sur un autre produit. */
    const val FRICTION_FLAT = 0.17f
    const val FRICTION_STACKED = 0.10f

    /** Vitesse de glissement (pixels par seconde) par unité de sinus de l'angle au-delà du frottement. */
    const val SLIDE = 1000f

    // ---------------------------------------------------------------- le rangement
    /** Le plateau doit être à plat (angle et vitesse de rotation sous ces seuils) pendant ce temps pour ranger la pile. */
    const val UNLOAD_MS = 700f
    const val UNLOAD_TILT = 0.1f
    const val UNLOAD_SPIN = 0.5f

    /** Chaque produit de la pile après le premier ajoute 25 % au multiplicateur, qui plafonne à 3. */
    const val STACK_BONUS = 0.25f
    const val STACK_BONUS_CAP = 3f

    /** Un rayon montre au plus ce nombre de produits rangés. */
    const val SHELF_SHOW = 12

    // ---------------------------------------------------------------- les animations (millisecondes)
    const val BANK_MS = 1100f
    const val CAPSIZE_MS = 700f
    const val BREAK_MS = 450f
    const val BUMP_MS = 220f

    /** La partie se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f

    /** Le multiplicateur d'une pile de [n] produits. */
    fun multiplier(n: Int): Float = min(STACK_BONUS_CAP, 1f + STACK_BONUS * max(0, n - 1))
}

/** Un produit : en l'air il est en ([x], [y]) sur l'écran (y est le bas du produit) ; sur le plateau, [s] est sa position le long du plateau. */
internal class ShelfItem(val type: Int, var x: Float, var y: Float) {
    var onTray = false

    /** Position le long du plateau depuis les mains (négative à gauche) ; [base] : la hauteur de ce qui est dessous (une pile). */
    var s = 0f
    var base = 0f

    /** Vitesse horizontale d'un produit qui tombe du plateau (pixels par seconde). */
    var vx = 0f

    val width: Float get() = Shelf.ITEM_W[type]
    val height: Float get() = Shelf.ITEM_H[type]
    val mass: Float get() = Shelf.ITEM_MASS[type]
}

/**
 * Une partie de Mise en rayon. [tick] fait avancer le temps avec les touches tenues, [press] reçoit l'appui sur A ou B.
 */
internal class ShelfGame(private val random: Random = Random.Default) {
    /** Le magasinier : son abscisse (le pivot du plateau) et le côté vers lequel il regarde (-1 gauche, 1 droite). */
    var heroX = Shelf.WIDTH / 2f
        private set
    var facing = 1
        private set

    /** Vrai pendant que le magasinier marche (pour l'animation), et sa vitesse (pixels par seconde, positive vers la droite). */
    var walking = false
        private set
    var heroVx = 0f
        private set
    private var heroAx = 0f

    /** L'angle du plateau (radians, positif : le côté droit descend) et sa vitesse de rotation. */
    var tilt = 0f
        private set
    var spin = 0f
        private set

    /** Les produits en l'air et ceux sur le plateau, dans l'ordre où ils sont arrivés. */
    val falling = ArrayList<ShelfItem>()
    val tray = ArrayList<ShelfItem>()
    private var spawnInMs = 0f

    /** Faux : plus aucun produit ne tombe tout seul (pour les tests, qui posent eux-mêmes ce dont ils ont besoin). */
    var autoSpawn = true

    /** Les produits déjà rangés sur chaque rayon (gauche, droite), pour le dessin : au plus [Shelf.SHELF_SHOW]. */
    val leftShelf = ArrayList<Int>()
    val rightShelf = ArrayList<Int>()

    var score = 0
        private set
    var timeLeftMs = Shelf.SHIFT_MS
        private set
    var clockMs = 0f
        private set

    // Les compteurs de la partie.
    var shelved = 0
        private set
    var trips = 0
        private set
    var bestPile = 0
        private set
    var caught = 0
        private set
    var broken = 0
        private set
    var capsizes = 0
        private set

    /** Parmi la casse : les produits qui ont raté le plateau, et ceux qui en sont tombés. */
    var missed = 0
        private set
    var spilled = 0
        private set

    /** Comment la partie s'est terminée ([SHELF_END_NONE] tant qu'elle dure). */
    var endReason = SHELF_END_NONE
        private set

    /** Le rangement en cours : de 0 à 1 quand le plateau est tenu à plat à un rayon avec des produits dessus. */
    var unload = 0f
        private set

    // Les animations : dernier rangement (points, taille de la pile, côté), renversement, casse, choc à l'atterrissage.
    var bankMs = 0f
        private set
    var bankPoints = 0
        private set
    var bankCount = 0
        private set
    var bankSide = 0
        private set
    var capsizeMs = 0f
        private set
    var breakMs = 0f
        private set
    var breakX = 0f
        private set
    var bumpMs = 0f
        private set
    var overMs = 0f
        private set

    init {
        reset()
    }

    /** Une partie neuve. */
    fun reset() {
        heroX = Shelf.WIDTH / 2f
        facing = 1
        walking = false
        heroVx = 0f
        heroAx = 0f
        tilt = 0f
        spin = 0f
        falling.clear()
        tray.clear()
        spawnInMs = Shelf.FIRST_SPAWN_MS
        leftShelf.clear()
        rightShelf.clear()
        score = 0
        timeLeftMs = Shelf.SHIFT_MS
        clockMs = 0f
        shelved = 0
        trips = 0
        bestPile = 0
        caught = 0
        broken = 0
        capsizes = 0
        missed = 0
        spilled = 0
        endReason = SHELF_END_NONE
        unload = 0f
        bankMs = 0f
        bankPoints = 0
        bankCount = 0
        bankSide = 0
        capsizeMs = 0f
        breakMs = 0f
        breakX = 0f
        bumpMs = 0f
        overMs = 0f
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 (début de la journée) à 1 (dernière seconde). */
    val difficulty: Float get() = (1f - timeLeftMs / Shelf.SHIFT_MS).coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    // ---------------------------------------------------------------- état

    val over: Boolean get() = endReason != SHELF_END_NONE

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Shelf.RESTART_DELAY_MS

    /** Le rayon où se trouve le magasinier : -1 (gauche), 1 (droite), 0 (au milieu, là où tombent les produits). */
    val shelfSide: Int
        get() = when {
            heroX <= Shelf.SHELF_LEFT_X -> -1
            heroX >= Shelf.SHELF_RIGHT_X -> 1
            else -> 0
        }

    /** Le poids total sur le plateau. */
    val trayMass: Float get() = tray.sumOf { it.mass.toDouble() }.toFloat()

    /** La valeur brute de la pile qu'on porte (avant multiplicateur), et ce qu'elle rapporterait au rayon. */
    val trayValue: Int get() = tray.sumOf { Shelf.ITEM_POINTS[it.type] }
    val trayWorth: Int get() = (trayValue * Shelf.multiplier(tray.size)).roundToInt()

    /** Le plateau est « à plat » : assez pour ranger. */
    val isLevel: Boolean get() = abs(tilt) <= Shelf.UNLOAD_TILT && abs(spin) <= Shelf.UNLOAD_SPIN

    /** La position d'un point du plateau (distance [s] des mains, [n] au-dessus de sa surface) sur l'écran. */
    fun trayX(s: Float, n: Float = 0f): Float = heroX + s * cos(tilt) + n * sin(tilt)
    fun trayY(s: Float, n: Float = 0f): Float = Shelf.PIVOT_Y + s * sin(tilt) - n * cos(tilt)

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes, avec les touches tenues [held] (masque de [SHELF_LEFT] à [SHELF_B]). */
    fun tick(ms: Float, held: Int = 0) {
        var left = max(0f, ms)
        // Des pas de 16 ms au plus : la physique reste stable même après un à-coup.
        while (left > 0f) {
            val step = min(left, 16f)
            left -= step
            advance(step, held)
        }
    }

    private fun advance(dt: Float, held: Int) {
        clockMs += dt
        bankMs = max(0f, bankMs - dt)
        capsizeMs = max(0f, capsizeMs - dt)
        breakMs = max(0f, breakMs - dt)
        bumpMs = max(0f, bumpMs - dt)
        if (over) {
            overMs += dt
            return
        }
        timeLeftMs -= dt
        if (timeLeftMs <= 0f) {
            timeLeftMs = 0f
            finish(SHELF_END_TIME)
            return
        }
        val seconds = dt / 1000f
        moveHero(seconds, held)
        spawn(dt)
        moveFalling(seconds)
        if (over) return
        updateBases()
        tiltTray(seconds, held)
        slideItems(seconds)
        updateBases()
        unloadAtShelf(dt)
    }

    private fun moveHero(seconds: Float, held: Int) {
        val dir = (if (held and SHELF_RIGHT != 0) 1 else 0) - (if (held and SHELF_LEFT != 0) 1 else 0)
        walking = dir != 0
        if (dir != 0) facing = dir
        // La vitesse monte et descend en une fraction de seconde : les départs et les arrêts secouent la pile (voir [tiltTray]).
        val target = dir * Shelf.HERO_SPEED
        val step = Shelf.HERO_ACCEL * seconds
        val before = heroVx
        heroVx = if (heroVx < target) min(target, heroVx + step) else max(target, heroVx - step)
        heroAx = if (seconds > 0f) (heroVx - before) / seconds else 0f
        val x = heroX + heroVx * seconds
        heroX = x.coerceIn(Shelf.HERO_MIN_X, Shelf.HERO_MAX_X)
        if (heroX != x) heroVx = 0f
    }

    /** Un nouveau produit tombe de temps en temps ; la réserve attend quand le magasinier est trop loin pour les rattraper (au rayon). */
    private fun spawn(dt: Float) {
        if (!autoSpawn) return
        if (heroX < Shelf.SPAWN_MIN_X - Shelf.SPAWN_REACH || heroX > Shelf.SPAWN_MAX_X + Shelf.SPAWN_REACH) return
        spawnInMs -= dt
        if (spawnInMs > 0f) return
        spawnInMs = lerp(Shelf.SPAWN_START_MS, Shelf.SPAWN_END_MS)
        if (falling.size >= Shelf.MAX_FLYING) return
        val x = Shelf.SPAWN_MIN_X + random.nextFloat() * (Shelf.SPAWN_MAX_X - Shelf.SPAWN_MIN_X)
        falling.add(ShelfItem(drawItem(), x, Shelf.SPAWN_Y))
    }

    private fun drawItem(): Int {
        val d = difficulty
        var total = 0f
        for (i in 0 until Shelf.ITEMS) total += Shelf.drawWeight(i, d)
        var pick = random.nextFloat() * total
        for (i in 0 until Shelf.ITEMS) {
            pick -= Shelf.drawWeight(i, d)
            if (pick <= 0f) return i
        }
        return Shelf.ITEMS - 1
    }

    // ---------------------------------------------------------------- la chute et l'atterrissage

    private fun moveFalling(seconds: Float) {
        val speed = lerp(Shelf.FALL_SPEED_START, Shelf.FALL_SPEED_END)
        var i = 0
        while (i < falling.size) {
            val item = falling[i]
            // Un produit tombé du plateau garde un peu de vitesse de côté et accélère ; un produit qui tombe du haut va droit.
            if (item.vx != 0f) {
                item.x += item.vx * seconds
                item.y += (speed + 120f) * seconds
            } else {
                item.y += speed * seconds
            }
            if (item.y >= Shelf.FLOOR_Y) {
                falling.removeAt(i)
                if (item.vx == 0f) missed++ else spilled++
                breakItem(item.x)
                if (over) return
                continue
            }
            if (item.vx == 0f) {
                val landing = tryLand(item)
                if (landing != LAND_NONE) {
                    falling.removeAt(i)
                    if (landing == LAND_BROKEN) {
                        spilled++
                        breakItem(item.x)
                        if (over) return
                    }
                    continue
                }
            }
            i++
        }
    }

    /** Le produit touche-t-il le plateau (ou la pile posée dessus) ? Si oui, il s'y pose ([LAND_ON_TRAY]), ou se brise s'il est plein ([LAND_BROKEN]). */
    private fun tryLand(item: ShelfItem): Int {
        val dx = item.x - heroX
        val dy = item.y - Shelf.PIVOT_Y
        val c = cos(tilt)
        val sn = sin(tilt)
        val s = dx * c + dy * sn
        val n = -dx * sn + dy * c
        if (abs(s) > Shelf.HALF + item.width * 0.3f) return LAND_NONE
        val sc = s.coerceIn(-Shelf.HALF, Shelf.HALF)
        val top = stackTop(sc, item.width, null)
        // Il se pose dès qu'il atteint le dessus de la pile, sauf s'il est déjà passé sous le plateau (tombé de côté).
        if (n < -top || n > Shelf.TRAY_THICK + 6f) return LAND_NONE
        if (tray.size >= Shelf.MAX_ON_TRAY) return LAND_BROKEN
        item.onTray = true
        item.s = sc
        item.base = top
        tray.add(item)
        caught++
        bumpMs = Shelf.BUMP_MS
        spin += Shelf.IMPACT * item.mass * sc
        return LAND_ON_TRAY
    }

    /** La hauteur de ce qui est posé sur le plateau autour de [s] (pour un produit de largeur [width]), sans compter [except]. */
    private fun stackTop(s: Float, width: Float, except: ShelfItem?): Float {
        var top = 0f
        for (other in tray) {
            if (other === except) continue
            if (abs(other.s - s) < (width + other.width) / 2f - 2f) top = max(top, other.base + other.height)
        }
        return top
    }

    /** Recalcule la hauteur de chaque produit posé : il repose sur ceux qui sont dessous, dans l'ordre d'arrivée. */
    private fun updateBases() {
        for (i in tray.indices) {
            val item = tray[i]
            var top = 0f
            for (j in 0 until i) {
                val other = tray[j]
                if (abs(other.s - item.s) < (item.width + other.width) / 2f - 2f) top = max(top, other.base + other.height)
            }
            item.base = top
        }
    }

    private fun breakItem(x: Float) {
        broken++
        breakMs = Shelf.BREAK_MS
        breakX = x
        if (broken >= Shelf.MAX_BREAKS) finish(SHELF_END_BREAKS)
    }

    // ---------------------------------------------------------------- l'équilibre

    private fun tiltTray(seconds: Float, held: Int) {
        val pushA = held and SHELF_A != 0
        val pushB = held and SHELF_B != 0
        // La pesanteur tire sur chaque produit : son bras de levier est sa distance horizontale aux mains, qui grandit avec
        // l'inclinaison du plateau d'autant plus que la pile est haute (une pile haute est un pendule renversé).
        val cosT = cos(tilt)
        val sinT = sin(tilt)
        var load = 0f
        var lever = 0f
        var inertia = Shelf.INERTIA
        for (item in tray) {
            val height = item.base + item.height / 2f
            load += item.mass * (item.s * cosT + height * sinT)
            lever += item.mass * height
            inertia += item.mass * (item.s * item.s + height * height * Shelf.PILE_INERTIA)
        }
        // Le balancement d'une pile : lent, irrégulier (deux ondes), proportionnel à sa hauteur et à son poids.
        val sway = (sin(clockMs * 0.0021f) + sin(clockMs * 0.0037f + 1.3f)) / 2f
        var torque = Shelf.GRAVITY_TORQUE * load - Shelf.MOVE_TORQUE * heroAx * lever + Shelf.SWAY_TORQUE * lever * sway
        if (pushA != pushB) torque += if (pushB) Shelf.HAND_TORQUE else -Shelf.HAND_TORQUE
        var accel = torque / inertia - Shelf.SPRING * tilt - Shelf.DAMPING * spin
        if (pushA && pushB) accel = 0f
        spin += accel * seconds
        if (pushA && pushB) spin *= exp(-Shelf.BRAKE * seconds)
        tilt += spin * seconds
        if (abs(tilt) >= Shelf.MAX_TILT) {
            // Un plateau vide a simplement atteint sa butée ; un plateau chargé se renverse.
            if (tray.isEmpty()) {
                tilt = Shelf.MAX_TILT * if (tilt > 0f) 1f else -1f
                spin = 0f
            } else {
                tilt = Shelf.MAX_TILT * if (tilt > 0f) 1f else -1f
                capsize()
            }
        }
    }

    /** Le plateau se renverse : tout ce qui est dessus tombe, et c'est de la casse. */
    private fun capsize() {
        capsizes++
        capsizeMs = Shelf.CAPSIZE_MS
        val lost = tray.toList()
        tray.clear()
        for (item in lost) {
            item.onTray = false
            item.x = trayX(item.s, item.base)
            item.y = trayY(item.s, item.base)
            item.vx = if (tilt > 0f) 140f else -140f
            falling.add(item)
        }
        tilt = 0f
        spin = 0f
        // La casse compte quand les produits touchent le sol, pas tous d'un coup ici.
    }

    /** Les produits glissent vers le côté bas dès que le plateau est assez penché, et tombent par le bout. */
    private fun slideItems(seconds: Float) {
        if (tray.isEmpty()) return
        val sn = sin(tilt)
        val dir = if (tilt > 0f) 1f else -1f
        val lost = ArrayList<ShelfItem>()
        for (item in tray) {
            val friction = if (item.base > 0f) Shelf.FRICTION_STACKED else Shelf.FRICTION_FLAT
            val excess = abs(sn) - sin(friction)
            if (excess <= 0f) continue
            item.s += dir * Shelf.SLIDE * excess * Shelf.ITEM_SLIP[item.type] * seconds
            if (abs(item.s) > Shelf.HALF + item.width * 0.3f) lost.add(item)
        }
        for (item in lost) {
            tray.remove(item)
            item.onTray = false
            item.x = trayX(item.s, item.base)
            item.y = trayY(item.s, item.base)
            item.vx = dir * 70f
            falling.add(item)
        }
    }

    /** Un appui sur A ou B ([key] : [SHELF_A] ou [SHELF_B]) donne un petit élan au plateau : un tapotement bref compte aussi. */
    fun press(key: Int) {
        if (over) return
        when (key) {
            SHELF_A -> spin -= Shelf.NUDGE
            SHELF_B -> spin += Shelf.NUDGE
        }
    }

    // ---------------------------------------------------------------- le rangement

    private fun unloadAtShelf(dt: Float) {
        val side = shelfSide
        if (side == 0 || tray.isEmpty() || !isLevel) {
            // Le rangement redescend deux fois plus vite qu'il ne monte.
            unload = max(0f, unload - dt * 2f / Shelf.UNLOAD_MS)
            return
        }
        unload += dt / Shelf.UNLOAD_MS
        if (unload < 1f) return
        unload = 0f
        bank(side)
    }

    /** Range toute la pile sur le rayon [side] : les points sont la valeur des produits fois le multiplicateur de la pile. */
    private fun bank(side: Int) {
        val n = tray.size
        val points = trayWorth
        score += points
        shelved += n
        trips++
        bestPile = max(bestPile, n)
        bankPoints = points
        bankCount = n
        bankSide = side
        bankMs = Shelf.BANK_MS
        val shelf = if (side < 0) leftShelf else rightShelf
        for (item in tray) {
            shelf.add(item.type)
            if (shelf.size > Shelf.SHELF_SHOW) shelf.removeAt(0)
        }
        tray.clear()
        tilt = 0f
        spin = 0f
    }

    private fun finish(reason: Int) {
        endReason = reason
        overMs = 0f
        unload = 0f
        falling.clear()
        tray.clear()
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        put(SAVE_VERSION)
        put(heroX); put(facing); put(tilt); put(spin); put(spawnInMs)
        put(score); put(timeLeftMs); put(clockMs)
        put(shelved); put(trips); put(bestPile); put(caught); put(broken); put(capsizes); put(missed); put(spilled); put(endReason)
        put(unload); put(bankMs); put(bankPoints); put(bankCount); put(bankSide)
        put(capsizeMs); put(breakMs); put(breakX); put(bumpMs); put(overMs)
        for (shelf in listOf(leftShelf, rightShelf)) {
            put(shelf.size)
            for (type in shelf) put(type)
        }
        for (list in listOf(falling, tray)) {
            put(list.size)
            for (item in list) {
                put(item.type); put(item.x); put(item.y); put(item.s); put(item.base); put(item.vx)
            }
        }
        return out
    }

    private fun load(values: List<Double>) {
        var at = 0
        fun num(): Double = values[at++]
        fun int(): Int = num().toInt()
        fun float(): Float = num().toFloat()
        require(int() == SAVE_VERSION)
        heroX = float().coerceIn(Shelf.HERO_MIN_X, Shelf.HERO_MAX_X)
        facing = if (int() >= 0) 1 else -1
        tilt = float().coerceIn(-Shelf.MAX_TILT, Shelf.MAX_TILT); spin = float(); spawnInMs = float()
        score = int(); timeLeftMs = float().coerceIn(0f, Shelf.SHIFT_MS); clockMs = float()
        shelved = int(); trips = int(); bestPile = int(); caught = int(); broken = int(); capsizes = int(); missed = int(); spilled = int()
        endReason = int().coerceIn(SHELF_END_NONE, SHELF_END_BREAKS)
        unload = float().coerceIn(0f, 1f); bankMs = float(); bankPoints = int(); bankCount = int(); bankSide = int().coerceIn(-1, 1)
        capsizeMs = float(); breakMs = float(); breakX = float(); bumpMs = float(); overMs = float()
        for (shelf in listOf(leftShelf, rightShelf)) {
            shelf.clear()
            val count = int().coerceIn(0, Shelf.SHELF_SHOW)
            repeat(count) { shelf.add(int().coerceIn(0, Shelf.ITEMS - 1)) }
        }
        falling.clear()
        tray.clear()
        for ((list, onTray) in listOf(falling to false, tray to true)) {
            val count = int().coerceIn(0, 64)
            repeat(count) {
                val item = ShelfItem(int().coerceIn(0, Shelf.ITEMS - 1), float(), float())
                item.s = float(); item.base = float(); item.vx = float()
                item.onTray = onTray
                list.add(item)
            }
        }
    }

    companion object {
        private const val SAVE_VERSION = 1
        private const val LAND_NONE = 0
        private const val LAND_ON_TRAY = 1
        private const val LAND_BROKEN = 2

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): ShelfGame {
            val game = ShelfGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
