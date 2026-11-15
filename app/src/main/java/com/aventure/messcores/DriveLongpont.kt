package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Identifiant du jeu « Drive inter Longpont » (préparation des commandes d'un drive) dans la liste des jeux. */
const val DRIVE_ID = "builtin_drive"

// Les cinq touches du jeu : la croix (gauche, droite, haut) et les deux boutons A et B.
internal const val DRIVE_LEFT = 0
internal const val DRIVE_RIGHT = 1
internal const val DRIVE_UP = 2
internal const val DRIVE_A = 3
internal const val DRIVE_B = 4

// Les produits qui circulent sur les tapis et que demandent les commandes.
internal const val DRIVE_MILK = 0
internal const val DRIVE_BREAD = 1
internal const val DRIVE_APPLE = 2
internal const val DRIVE_CAN = 3
internal const val DRIVE_BOTTLE = 4
internal const val DRIVE_CHEESE = 5

// Les étapes d'une voiture à sa piste de livraison.
internal const val CAR_IDLE = 0 // pas de voiture
internal const val CAR_WARN = 1 // le panneau « ! » clignote : elle va surgir
internal const val CAR_WAIT = 2 // elle est là et attend sa commande
internal const val CAR_LEAVE = 3 // elle repart vers le fond en rapetissant

// Les étapes du téléphone du bureau (une réclamation de client).
internal const val PHONE_IDLE = 0
internal const val PHONE_RING = 1
internal const val PHONE_CALL = 2

// Les étapes de la souris qui traverse le bureau.
internal const val MOUSE_NONE = 0
internal const val MOUSE_SIT = 1
internal const val MOUSE_RUN = 2

/**
 * Drive inter Longpont : l'écran est divisé en douze zones, « adaddadadada » (a : zone d'action, personnage de face ; d : zone de
 * déplacement, personnage de profil). À gauche, dehors, deux pistes de livraison sous une coupole « DRIVE » ; à droite, le bureau
 * (la porte est entre la quatrième et la cinquième zone) avec trois lignes de bacs et quatre tapis roulants. Trois commandes sont
 * affichées au fond, une par ligne de bacs. Les produits arrivent sur les tapis ; devant un bac, la touche A ou B (la lettre du tapis
 * voisin) fait tomber le produit au bout du tapis dans le bac. Une commande complète donne 5 points et rejoint le stock des
 * commandes prêtes (A ou B). Dehors, une voiture surgit sur une piste après un avertissement et demande A ou B : la bonne commande
 * donne 10 points. Une souris traverse le bureau (on saute par-dessus) et le téléphone sonne (réclamation d'un client : décrocher
 * dans la dernière zone, puis la touche demandée). Trois erreurs et le joueur est licencié.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [DriveGame].
 */
internal object Drive {
    const val WIDTH = 720f
    const val HEIGHT = 405f

    /** Les douze zones, de gauche à droite : a = action, d = déplacement. */
    const val ZONES = "adaddadadada"
    const val ZONE_COUNT = 12
    const val ZONE_W = 60f

    /** x du centre de la zone [i], dans l'écran de 720 x 405. */
    fun zoneX(i: Int): Float = ZONE_W * i + ZONE_W / 2f

    fun isAction(i: Int): Boolean = ZONES[i] == 'a'

    /** x de la porte, entre la quatrième et la cinquième zone. */
    const val DOOR_X = ZONE_W * 4f

    /** Bord droit du bureau, et coin où la souris se tient. */
    const val ROOM_RIGHT = WIDTH - 4f
    const val MOUSE_START_X = ROOM_RIGHT - 16f

    /** Les trois lignes de bacs (zones d'action du bureau où l'on se tient), les quatre tapis et les deux pistes. */
    val LANE_ZONES = intArrayOf(5, 7, 9)
    val BELT_ZONES = intArrayOf(4, 6, 8, 10)
    val BAY_ZONES = intArrayOf(1, 3)

    /** La dernière zone : le téléphone est au mur de droite du bureau. */
    const val PHONE_ZONE = ZONE_COUNT - 1

    const val PRODUCT_COUNT = 6
    const val ORDER_SIZE = 3
    const val MAX_ERRORS = 3
    const val MAX_STOCK = 5

    // Les profondeurs (0 devant, 1 au fond) : où les tapis finissent, où les produits apparaissent, la fenêtre pour les faire tomber.
    const val S_NEAR = 0.14f
    const val S_FAR = 0.93f
    const val DROP_WINDOW = 0.10f
    const val S_PARK = 0.03f
    const val S_CAR_FAR = 0.9f

    // Les durées (en millisecondes).
    const val JUMP_MS = 600f
    const val JUMP_PX = 32f
    const val HOP_MS = 160f
    const val HURT_MS = 600f
    const val CAR_LEAVE_MS = 2000f
    const val ORDER_READY_MS = 1400f

    /** Le jeu se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f

    /** La souris : elle blesse sous cette distance, à moins que le joueur ne soit assez haut. */
    const val MOUSE_REACH = 18f
    const val MOUSE_CLEARANCE = 10f

    /** La lettre du tapis de la zone [zone] : 0 pour A, 1 pour B (B, A, B, A en partant de la droite). */
    fun beltLabel(zone: Int): Int = if (((ZONE_COUNT - 2 - zone) / 2) % 2 == 0) 1 else 0

    /** Vrai si la zone [zone] est le bureau (à droite de la porte). */
    fun inOffice(zone: Int): Boolean = zone >= 4
}

/** Une commande affichée au fond au-dessus d'une ligne de bacs : trois produits et une lettre (A ou B). */
internal class DriveLane(val zone: Int) {
    var tag = 0
    val need = IntArray(Drive.ORDER_SIZE)
    val got = BooleanArray(Drive.ORDER_SIZE)
    var done = false
    var readyMs = 0f

    val x: Float get() = Drive.zoneX(zone)
    val missingCount: Int get() = got.count { !it }
}

/** Un produit sur un tapis : [depth] va de 0 (bout du tapis, devant) à 1 (le fond). */
internal class DriveItem(val product: Int, var depth: Float, var passed: Boolean = false)

/** Un tapis roulant (entre deux zones d'action) et les produits qui le parcourent, le plus proche en premier. */
internal class DriveBelt(val zone: Int) {
    val label: Int = Drive.beltLabel(zone)
    val items = ArrayList<DriveItem>()
    var nextMs = 0f

    val x: Float get() = Drive.zoneX(zone)
}

/** Un produit qui tombe (dans un bac, ou par terre au bout du tapis) : seulement pour l'animation. */
internal class DriveFall(
    val product: Int,
    val lost: Boolean,
    val fromX: Float,
    val toX: Float,
    val fromDepth: Float,
    val toDepth: Float,
    var elapsedMs: Float,
    val durationMs: Float
)

/** Une piste de livraison (zone de déplacement du dehors) et la voiture qui s'y gare. */
internal class DriveBay(val zone: Int) {
    var mode = CAR_IDLE
    var elapsedMs = 0f
    var depth = Drive.S_PARK
    var want = 0
    var limitMs = 0f
    var warnMs = 0f

    val x: Float get() = Drive.zoneX(zone)
}

internal class DrivePhone {
    var mode = PHONE_IDLE
    var elapsedMs = 0f
    var ringBeatMs = 0f
    var ringAgeMs = 0f

    /** La touche à presser une fois décroché : 0 = A, 1 = B, 2 = haut. */
    var want = 0
    var limitMs = 0f
}

internal class DriveMouse {
    var mode = MOUSE_NONE
    var x = 0f
    var sitMs = 0f
    var hit = false
    var speed = 0f
}

/**
 * La partie de Drive inter Longpont, sans rien d'Android : [tick] fait avancer le temps, [press] reçoit les touches. La difficulté
 * va de 0 à 1 avec le travail accompli (commandes préparées + 2 par livraison, 24 pour le maximum) ; rien n'accélère tant que la
 * première voiture n'est pas annoncée (le rodage). [random] est injectable pour les tests.
 */
internal class DriveGame(private val random: Random = Random.Default) {
    var over = false
        private set
    var overMs = 0f
        private set
    var pos = 0
        private set

    /** Le sens du dernier déplacement : 1 vers la droite, -1 vers la gauche. */
    var facing = 1
        private set
    var points = 0
        private set
    var errors = 0
        private set

    /** Les commandes préparées, et les livraisons réussies. */
    var orders = 0
        private set
    var deliveries = 0
        private set

    /** Vrai une fois la première voiture annoncée : la difficulté se met alors à monter. */
    var started = false
        private set
    var neededPasses = 0
        private set

    // Les effets : l'éclair d'une erreur (de 1 à 0), le clignotement après un choc (millisecondes), le pas et le saut (de 1 à 0).
    var flash = 0f
        private set
    var hurtMs = 0f
        private set
    private var hop = 0f
    private var jump = 0f

    /** Le temps écoulé en jeu, pour les animations (tapis, souris, sonnerie). */
    var clockMs = 0f
        private set

    /** Les commandes prêtes à livrer : [0] pour A, [1] pour B. */
    val stock = IntArray(2)
    val lanes = Array(Drive.LANE_ZONES.size) { DriveLane(Drive.LANE_ZONES[it]) }
    val belts = Array(Drive.BELT_ZONES.size) { DriveBelt(Drive.BELT_ZONES[it]) }
    val bays = Array(Drive.BAY_ZONES.size) { DriveBay(Drive.BAY_ZONES[it]) }
    val falls = ArrayList<DriveFall>()
    val phone = DrivePhone()
    val mouse = DriveMouse()

    // Le chef d'orchestre : le temps avant la prochaine voiture, souris et sonnerie (millisecondes).
    private var graceMs = GRACE_MS
    private var carMs = 0f
    private var mouseMs = 0f
    private var phoneMs = 0f

    init {
        reset()
    }

    /** Une partie neuve. */
    fun reset() {
        over = false
        overMs = 0f
        pos = 0
        facing = 1
        points = 0
        errors = 0
        orders = 0
        deliveries = 0
        started = false
        neededPasses = 0
        flash = 0f
        hurtMs = 0f
        hop = 0f
        jump = 0f
        clockMs = 0f
        stock[0] = 0
        stock[1] = 0
        falls.clear()
        lanes.forEachIndexed { i, lane ->
            lane.tag = i % 2
            newOrder(lane)
        }
        for (belt in belts) {
            belt.items.clear()
            belt.nextMs = 2500f + random.nextFloat() * 3000f
        }
        for (bay in bays) {
            bay.mode = CAR_IDLE
            bay.elapsedMs = 0f
            bay.depth = Drive.S_PARK
        }
        phone.mode = PHONE_IDLE
        mouse.mode = MOUSE_NONE
        graceMs = GRACE_MS
        carMs = 0f
        mouseMs = 0f
        phoneMs = 0f
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 à 1 ; elle reste à 0 pendant le rodage. */
    val difficulty: Float
        get() = if (!started) 0f else min(1f, (orders + 2 * deliveries) / DIFFICULTY_UNITS)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    private fun between(low: Float, high: Float): Float = low + random.nextFloat() * (high - low)

    /** La vitesse des tapis, en profondeur par seconde : 17 s du fond jusqu'aux bacs au début, 6 s à la fin. */
    val beltSpeed: Float get() = lerp(0.045f, 0.13f)

    /** Combien de produits arrivent, la part de produits utiles, les délais des voitures, des appels et des souris. */
    private val itemGapMs: Float get() = lerp(6000f, 2600f) * between(0.65f, 1.35f)
    private val usefulShare: Float get() = lerp(0.8f, 0.36f)
    private val carGapMs: Float get() = lerp(34000f, 9000f) * between(0.85f, 1.15f)
    private val mouseGapMs: Float get() = lerp(45000f, 11000f) * between(0.8f, 1.2f)
    private val phoneGapMs: Float get() = lerp(65000f, 20000f) * between(0.8f, 1.2f)
    private val carWarnMs: Float get() = lerp(3200f, 2000f)
    private val carPatienceMs: Float get() = lerp(18000f, 6000f)
    private val phoneRingMaxMs: Float get() = lerp(14000f, 8000f)
    private val phoneAnswerMs: Float get() = lerp(3600f, 1200f)
    private val mouseSitMs: Float get() = lerp(2200f, 1100f) + random.nextFloat() * lerp(1400f, 700f)
    private val mouseSpeed: Float get() = lerp(145f, 180f)

    /** Le malus d'un produit utile qui s'échappe : rien au début, 1 point à partir de 30 %, 2 à partir de 65 %. */
    private val missPenalty: Int
        get() = if (difficulty < 0.3f) 0 else if (difficulty < 0.65f) 1 else 2

    // ---------------------------------------------------------------- commandes

    /** La lettre de la prochaine commande : celle qu'on a le moins (en stock et en cours), pour que A et B restent équilibrées. */
    private fun pickTag(lane: DriveLane): Int {
        val count = intArrayOf(stock[0], stock[1])
        for (other in lanes) if (other !== lane && !other.done) count[other.tag]++
        return if (count[0] < count[1]) 0 else if (count[1] < count[0]) 1 else random.nextInt(2)
    }

    private fun newOrder(lane: DriveLane) {
        lane.tag = pickTag(lane)
        val products = (0 until Drive.PRODUCT_COUNT).shuffled(random).take(Drive.ORDER_SIZE)
        for (k in 0 until Drive.ORDER_SIZE) {
            lane.need[k] = products[k]
            lane.got[k] = false
        }
        lane.done = false
        lane.readyMs = 0f
    }

    /** Vrai si un produit [product] sur le tapis [belt] manque à une des commandes voisines. */
    private fun isNeeded(belt: DriveBelt, product: Int): Boolean = lanes.any { lane ->
        !lane.done && abs(lane.zone - belt.zone) == 1 && (0 until Drive.ORDER_SIZE).any { lane.need[it] == product && !lane.got[it] }
    }

    private fun spawnItem(belt: DriveBelt) {
        val missing = ArrayList<Int>()
        for (lane in lanes) {
            if (lane.done || abs(lane.zone - belt.zone) != 1) continue
            for (k in 0 until Drive.ORDER_SIZE) if (!lane.got[k]) missing.add(lane.need[k])
        }
        val product = if (missing.isNotEmpty() && random.nextFloat() < usefulShare) {
            missing[random.nextInt(missing.size)]
        } else {
            random.nextInt(Drive.PRODUCT_COUNT)
        }
        belt.items.add(DriveItem(product, Drive.S_FAR))
    }

    private fun updateBelts(dt: Float) {
        for (belt in belts) {
            belt.nextMs -= dt
            val last = belt.items.lastOrNull()?.depth ?: 0f
            if (belt.nextMs <= 0f) {
                if (last < Drive.S_FAR - 0.2f) {
                    spawnItem(belt)
                    belt.nextMs = itemGapMs
                } else {
                    belt.nextMs = 300f
                }
            }
            for (item in belt.items) {
                item.depth -= beltSpeed * dt / 1000f
                if (!item.passed && item.depth <= Drive.S_NEAR + 0.02f) {
                    item.passed = true
                    if (isNeeded(belt, item.product)) neededPasses++
                }
            }
            while (belt.items.isNotEmpty() && belt.items[0].depth < Drive.S_NEAR - 0.025f) {
                val item = belt.items.removeAt(0)
                // Un produit utile qui s'échappe coûte des points une fois le rodage passé.
                if (isNeeded(belt, item.product)) points = max(0, points - missPenalty)
                falls.add(DriveFall(item.product, true, belt.x, belt.x, Drive.S_NEAR, Drive.S_NEAR - 0.06f, 0f, 380f))
            }
        }
        for (fall in falls) fall.elapsedMs += dt
        falls.removeAll { it.elapsedMs >= it.durationMs }
        for (lane in lanes) {
            if (!lane.done) continue
            lane.readyMs -= dt
            if (lane.readyMs <= 0f) newOrder(lane)
        }
    }

    private fun completeOrder(lane: DriveLane) {
        orders++
        points += 5
        stock[lane.tag] = min(Drive.MAX_STOCK, stock[lane.tag] + 1)
        lane.done = true
        lane.readyMs = Drive.ORDER_READY_MS
    }

    /**
     * Devant une ligne de bacs, A ou B : le produit au bout du tapis de cette lettre tombe dans le bac où l'on se tient. Renvoie faux
     * si l'on n'est pas devant un bac ou si aucun tapis voisin ne porte cette lettre (la touche sert alors à la livraison).
     */
    private fun dropItem(key: Int): Boolean {
        val lane = lanes.firstOrNull { it.zone == pos } ?: return false
        if (lane.done) return false
        val label = if (key == DRIVE_A) 0 else 1
        val belt = belts.firstOrNull { abs(it.zone - pos) == 1 && it.label == label } ?: return false
        val item = belt.items.firstOrNull()
        if (item == null || item.depth > Drive.S_NEAR + Drive.DROP_WINDOW) return true // rien au bout du tapis
        belt.items.removeAt(0)
        falls.add(DriveFall(item.product, false, belt.x, lane.x, Drive.S_NEAR + 0.01f, 0.22f, 0f, 320f))
        val index = (0 until Drive.ORDER_SIZE).firstOrNull { lane.need[it] == item.product && !lane.got[it] }
        if (index != null) {
            lane.got[index] = true
            if (lane.got.all { it }) completeOrder(lane)
        } else {
            points = max(0, points - WRONG_PRODUCT_PENALTY)
            flash = 0.6f
        }
        return true
    }

    // ---------------------------------------------------------------- voitures

    /** Vrai si l'on peut envoyer une voiture sans qu'elle ne puisse jamais être servie : une commande prête ou une ligne à moitié finie. */
    val carCanBeServed: Boolean
        get() = stock[0] + stock[1] > 0 || lanes.any { !it.done && it.got.count { got -> got } >= 2 }

    val activeCars: Int get() = bays.count { it.mode != CAR_IDLE }

    private fun launchCar(): Boolean {
        val free = bays.filter { it.mode == CAR_IDLE }
        if (free.isEmpty()) return false
        val bay = free[random.nextInt(free.size)]
        val held = (0..1).filter { stock[it] > 0 }
        // La voiture demande le plus souvent une commande qu'on a en stock, et de moins en moins avec la difficulté.
        bay.want = if (held.isNotEmpty() && random.nextFloat() < lerp(0.95f, 0.5f)) held[random.nextInt(held.size)] else random.nextInt(2)
        bay.mode = CAR_WARN
        bay.elapsedMs = 0f
        bay.depth = Drive.S_PARK
        bay.warnMs = carWarnMs
        return true
    }

    private fun updateCars(dt: Float) {
        for (bay in bays) {
            when (bay.mode) {
                CAR_WARN -> {
                    bay.elapsedMs += dt
                    if (bay.elapsedMs >= bay.warnMs) { // la voiture surgit d'un coup sur sa zone
                        bay.mode = CAR_WAIT
                        bay.elapsedMs = 0f
                        bay.depth = Drive.S_PARK
                        bay.limitMs = carPatienceMs
                        if (pos == bay.zone) { // le joueur s'y tenait
                            hurtMs = Drive.HURT_MS
                            error()
                        }
                    }
                }
                CAR_WAIT -> {
                    bay.elapsedMs += dt
                    if (bay.elapsedMs >= bay.limitMs) { // aucune commande remise à temps
                        error()
                        bay.mode = CAR_LEAVE
                        bay.elapsedMs = 0f
                    }
                }
                CAR_LEAVE -> {
                    bay.elapsedMs += dt
                    val u = min(1f, bay.elapsedMs / Drive.CAR_LEAVE_MS)
                    bay.depth = Drive.S_PARK + (Drive.S_CAR_FAR - Drive.S_PARK) * u * u
                    if (u >= 1f) bay.mode = CAR_IDLE
                }
            }
        }
    }

    /** La voiture à la droite de la zone où l'on se tient (zone d'action du dehors), si elle attend sa commande. */
    private fun waitingCar(): DriveBay? = bays.firstOrNull { it.zone == pos + 1 && it.mode == CAR_WAIT }

    /** A ou B dehors : on remet à la voiture voisine la commande de cette lettre. Rien en stock pour cette lettre : rien ne se passe. */
    private fun deliver(key: Int) {
        val bay = waitingCar() ?: return
        val tag = if (key == DRIVE_A) 0 else 1
        if (stock[tag] <= 0) return
        stock[tag]--
        if (tag == bay.want) {
            points += 10
            deliveries++
            bay.mode = CAR_LEAVE
            bay.elapsedMs = 0f
        } else {
            points = max(0, points - WRONG_ORDER_PENALTY)
            flash = 0.6f
        }
    }

    // ---------------------------------------------------------------- téléphone et souris

    private fun hangUp() {
        phone.mode = PHONE_IDLE
        phoneMs = phoneGapMs
    }

    private fun ringPhone() {
        phone.mode = PHONE_RING
        phone.ringBeatMs = 0f
        phone.ringAgeMs = 0f
    }

    private fun pickUp() {
        phone.mode = PHONE_CALL
        phone.elapsedMs = 0f
        phone.want = random.nextInt(3)
        phone.limitMs = phoneAnswerMs
    }

    private fun answer(key: Int) {
        val pressed = when (key) {
            DRIVE_A -> 0
            DRIVE_B -> 1
            else -> 2
        }
        if (pressed == phone.want) points++ else error()
        hangUp()
    }

    private fun spawnMouse() {
        mouse.mode = MOUSE_SIT
        mouse.x = Drive.MOUSE_START_X
        mouse.sitMs = mouseSitMs
        mouse.hit = false
        mouse.speed = mouseSpeed
    }

    // ---------------------------------------------------------------- chef d'orchestre

    private fun director(dt: Float) {
        if (!started) {
            // Le rodage : seulement les tapis, jusqu'à ce que chaque article d'une commande soit passé environ 3 fois et qu'une commande soit prête.
            if (neededPasses >= WARM_PASSES && stock[0] + stock[1] > 0) {
                graceMs -= dt
                if (graceMs <= 0f && launchCar()) {
                    started = true
                    carMs = carGapMs
                    mouseMs = 25000f
                    phoneMs = 50000f
                }
            }
            return
        }
        // Au début, un seul genre d'ennui à la fois : voiture, souris ou appel.
        val quiet = difficulty < 0.5f
        val busyPhone = phone.mode != PHONE_IDLE
        val busyMouse = mouse.mode != MOUSE_NONE
        carMs -= dt
        if (carMs <= 0f) {
            carMs = if (carCanBeServed && !(quiet && (busyPhone || busyMouse)) && launchCar()) carGapMs else 1500f
        }
        mouseMs -= dt
        if (mouseMs <= 0f) {
            if (!busyMouse && !(quiet && (activeCars > 0 || busyPhone))) {
                spawnMouse()
                mouseMs = mouseGapMs
            } else {
                mouseMs = 1500f
            }
        }
        phoneMs -= dt
        if (phoneMs <= 0f) {
            if (!busyPhone && !(quiet && (activeCars > 0 || busyMouse))) {
                ringPhone()
                phoneMs = Float.MAX_VALUE
            } else {
                phoneMs = 1500f
            }
        }
    }

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        flash = max(0f, flash - dt / 350f)
        hurtMs = max(0f, hurtMs - dt)
        hop = max(0f, hop - dt / Drive.HOP_MS)
        jump = max(0f, jump - dt / Drive.JUMP_MS)
        if (over) {
            overMs += dt
            return
        }
        updateBelts(dt)
        updateCars(dt)
        director(dt)
        when (phone.mode) {
            PHONE_RING -> {
                phone.ringAgeMs += dt
                phone.ringBeatMs -= dt
                if (phone.ringBeatMs <= 0f) phone.ringBeatMs = 1300f
                if (phone.ringAgeMs > phoneRingMaxMs) { // personne n'a décroché
                    error()
                    hangUp()
                }
            }
            PHONE_CALL -> {
                phone.elapsedMs += dt
                if (phone.elapsedMs > phone.limitMs) { // la réponse est arrivée trop tard
                    error()
                    hangUp()
                }
            }
        }
        when (mouse.mode) {
            MOUSE_SIT -> {
                mouse.sitMs -= dt
                if (mouse.sitMs <= 0f) mouse.mode = MOUSE_RUN
            }
            MOUSE_RUN -> {
                mouse.x -= mouse.speed * dt / 1000f
                if (!mouse.hit && abs(mouse.x - Drive.zoneX(pos)) < Drive.MOUSE_REACH && lift < Drive.MOUSE_CLEARANCE) {
                    mouse.hit = true
                    hurtMs = Drive.HURT_MS
                    error()
                }
                if (mouse.x <= Drive.DOOR_X) mouse.mode = MOUSE_NONE
            }
        }
    }

    // ---------------------------------------------------------------- joueur

    /** La hauteur du personnage au-dessus du sol, en pixels de l'écran : un petit pas, ou un saut. */
    val lift: Float
        get() {
            val step = if (hop > 0f) sin(min(1f, 1f - hop) * PI.toFloat()) * 6f else 0f
            val leap = if (jump > 0f) sin((1f - jump) * PI.toFloat()) * Drive.JUMP_PX else 0f
            return step + leap
        }

    /** Vrai pendant un saut. */
    val jumping: Boolean get() = jump > 0f

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Drive.RESTART_DELAY_MS

    /** Les trois quarts du jeu tiennent dans cette méthode : une touche ([DRIVE_LEFT] à [DRIVE_B]) vient d'être pressée. */
    fun press(key: Int) {
        if (over) return
        when {
            key == DRIVE_LEFT -> move(-1)
            key == DRIVE_RIGHT -> move(1)
            phone.mode == PHONE_CALL && (key == DRIVE_A || key == DRIVE_B || key == DRIVE_UP) -> answer(key)
            key == DRIVE_UP -> jump()
            key == DRIVE_A && phone.mode == PHONE_RING && pos == Drive.PHONE_ZONE -> pickUp()
            key == DRIVE_A || key == DRIVE_B -> if (!dropItem(key)) deliver(key)
        }
    }

    private fun move(direction: Int) {
        val next = pos + direction
        if (next < 0 || next >= Drive.ZONE_COUNT) return
        pos = next
        facing = direction
        hop = 1f
    }

    private fun jump() {
        if (jump > 0f) return
        jump = 1f
    }

    private fun error() {
        if (over) return
        errors++
        flash = 1f
        if (errors >= Drive.MAX_ERRORS) {
            over = true
            overMs = 0f
        }
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        put(SAVE_VERSION)
        put(over); put(overMs); put(pos); put(facing); put(points); put(errors); put(orders); put(deliveries)
        put(started); put(neededPasses); put(flash); put(hurtMs); put(hop); put(jump); put(clockMs)
        put(stock[0]); put(stock[1])
        for (lane in lanes) {
            put(lane.tag)
            for (k in 0 until Drive.ORDER_SIZE) put(lane.need[k])
            for (k in 0 until Drive.ORDER_SIZE) put(lane.got[k])
            put(lane.done); put(lane.readyMs)
        }
        for (belt in belts) {
            put(belt.nextMs)
            put(belt.items.size)
            for (item in belt.items) {
                put(item.product); put(item.depth); put(item.passed)
            }
        }
        for (bay in bays) {
            put(bay.mode); put(bay.elapsedMs); put(bay.depth); put(bay.want); put(bay.limitMs); put(bay.warnMs)
        }
        put(phone.mode); put(phone.elapsedMs); put(phone.ringBeatMs); put(phone.ringAgeMs); put(phone.want); put(phone.limitMs)
        put(mouse.mode); put(mouse.x); put(mouse.sitMs); put(mouse.hit); put(mouse.speed)
        put(graceMs); put(carMs); put(mouseMs); put(phoneMs)
        return out
    }

    private fun load(values: List<Double>) {
        var at = 0
        fun num(): Double = values[at++]
        fun int(): Int = num().toInt()
        fun float(): Float = num().toFloat()
        fun flag(): Boolean = num() != 0.0
        require(int() == SAVE_VERSION)
        over = flag(); overMs = float(); pos = int().coerceIn(0, Drive.ZONE_COUNT - 1); facing = if (int() < 0) -1 else 1
        points = int(); errors = int(); orders = int(); deliveries = int()
        started = flag(); neededPasses = int(); flash = float(); hurtMs = float(); hop = float(); jump = float(); clockMs = float()
        stock[0] = int(); stock[1] = int()
        for (lane in lanes) {
            lane.tag = int().coerceIn(0, 1)
            for (k in 0 until Drive.ORDER_SIZE) lane.need[k] = int().coerceIn(0, Drive.PRODUCT_COUNT - 1)
            for (k in 0 until Drive.ORDER_SIZE) lane.got[k] = flag()
            lane.done = flag(); lane.readyMs = float()
        }
        for (belt in belts) {
            belt.nextMs = float()
            belt.items.clear()
            repeat(int()) {
                belt.items.add(DriveItem(int().coerceIn(0, Drive.PRODUCT_COUNT - 1), float(), flag()))
            }
        }
        for (bay in bays) {
            bay.mode = int(); bay.elapsedMs = float(); bay.depth = float(); bay.want = int().coerceIn(0, 1); bay.limitMs = float(); bay.warnMs = float()
        }
        phone.mode = int(); phone.elapsedMs = float(); phone.ringBeatMs = float(); phone.ringAgeMs = float(); phone.want = int().coerceIn(0, 2); phone.limitMs = float()
        mouse.mode = int(); mouse.x = float(); mouse.sitMs = float(); mouse.hit = flag(); mouse.speed = float()
        graceMs = float(); carMs = float(); mouseMs = float(); phoneMs = float()
        falls.clear()
    }

    companion object {
        private const val SAVE_VERSION = 1
        private const val GRACE_MS = 4000f
        private const val DIFFICULTY_UNITS = 24f

        /** Nombre de produits utiles passés devant les bacs avant la première voiture : 3 passages pour chacun des 3 articles d'une commande. */
        const val WARM_PASSES = 9

        /** Les pénalités : un mauvais produit dans une commande, une mauvaise commande remise à une voiture. */
        const val WRONG_PRODUCT_PENALTY = 2
        const val WRONG_ORDER_PENALTY = 5

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): DriveGame {
            val game = DriveGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
