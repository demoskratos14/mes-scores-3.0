package com.aventure.messcores

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Identifiant du jeu « Rayon frais » (mise en rayon avec dates limites, retrait, démarque, porte du frigo, contrôle d'hygiène). */
const val FRESH_ID = "builtin_fresh"

// Les quatre touches du jeu : la croix (gauche, droite) pour se déplacer, les boutons A et B pour agir.
internal const val FRESH_LEFT = 0
internal const val FRESH_RIGHT = 1
internal const val FRESH_A = 2
internal const val FRESH_B = 3

// Les étapes d'une partie : une courte ouverture du magasin, la journée, la fin.
internal const val FRESH_STAGE_OPENING = 0
internal const val FRESH_STAGE_PLAY = 1
internal const val FRESH_STAGE_OVER = 2

// Pourquoi la partie s'est arrêtée : la journée est finie, ou le joueur est licencié (trois fautes graves).
internal const val FRESH_END_NONE = 0
internal const val FRESH_END_TIME = 1
internal const val FRESH_END_FIRED = 2

// Les étapes du contrôle d'hygiène : rien, l'inspecteur arrive (compte à rebours), puis le verdict.
internal const val FRESH_INSPECT_NONE = 0
internal const val FRESH_INSPECT_WARN = 1
internal const val FRESH_INSPECT_VERDICT = 2

// Le dernier fait marquant de la partie, affiché au centre de l'écran avec ses points (voir [FreshGame.lastEvent]).
internal const val FRESH_EVENT_NONE = 0
internal const val FRESH_EVENT_STOCK_OK = 1 // paquet rangé dans l'ordre des dates
internal const val FRESH_EVENT_STOCK_LATE = 2 // paquet rangé dans le désordre
internal const val FRESH_EVENT_SALE = 3
internal const val FRESH_EVENT_SALE_MARKED = 4
internal const val FRESH_EVENT_REMOVED = 5 // un paquet périmé retiré
internal const val FRESH_EVENT_WASTE = 6 // un paquet encore bon jeté
internal const val FRESH_EVENT_MARKED = 7 // un paquet démarqué
internal const val FRESH_EVENT_SICK = 8 // un client a pris un paquet périmé (faute)
internal const val FRESH_EVENT_STOCKOUT = 9 // un client n'a rien trouvé
internal const val FRESH_EVENT_WRONG = 10 // un paquet posé au mauvais endroit
internal const val FRESH_EVENT_DOOR_CLOSED = 11
internal const val FRESH_EVENT_DOOR_FAULT = 12 // la porte est restée ouverte trop longtemps (faute)
internal const val FRESH_EVENT_INSPECT_OK = 13
internal const val FRESH_EVENT_INSPECT_KO = 14 // contrôle raté (faute)
internal const val FRESH_EVENT_COUNT = 15

/**
 * Rayon frais : le personnage du Drive tient le rayon frais d'un supermarché. Cinq produits (yaourt, jambon, fromage, salade, poulet)
 * ont chacun leur colonne dans le meuble réfrigéré, qui garde trois paquets au plus : le paquet du bas est « devant » (celui que les
 * clients prennent), celui du haut est « au fond ». Chaque paquet porte un compteur : le temps qu'il lui reste avant sa date limite.
 *
 * Le joueur se déplace avec les flèches le long du rayon : tout à gauche le chariot (A ou B prend le paquet du dessus ; avec un paquet
 * en main, ça le remet), puis les cinq colonnes, puis, tout à droite, la porte du frigo. Dans une colonne, avec un paquet en main, A le
 * range au fond et B le range devant (le bon ordre : le paquet qui expire le premier devant) ; sans paquet en main, A retire le paquet
 * visé (le premier à expirer) et B le démarque (−30 % : le prochain client de la colonne le prendra en priorité). Un paquet périmé
 * retiré rapporte ; jeter un paquet encore bon fait perdre des points.
 *
 * Les clients arrivent, demandent un produit et prennent le paquet démarqué ou, sinon, celui de devant. Un client qui prend un paquet
 * périmé est une faute grave. De temps en temps, la porte du frigo reste ouverte : les compteurs du meuble tournent trois fois plus
 * vite, il faut aller la fermer (A ou B devant elle) avant qu'il ne soit trop tard. Un contrôleur d'hygiène arrive aussi après un
 * compte à rebours : s'il trouve un paquet périmé dans le meuble ou la porte ouverte, c'est une faute grave ; sinon, une prime.
 * Trois fautes graves et le joueur est licencié ; sinon la journée dure deux minutes et demie.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [FreshGame].
 */
internal object Fresh {
    /** L'écran est le même que celui de Drive (720 x 405), pour reprendre sa console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    /** Les cinq produits : yaourt, jambon, fromage, salade, poulet (dans cet ordre, voir [FreshArt]) ; une colonne chacun. */
    const val PRODUCTS = 5

    /** Les positions du joueur, de gauche à droite : le chariot, les cinq colonnes, la porte du frigo. */
    const val POSITIONS = 7
    const val CART_POS = 0
    const val DOOR_POS = 6

    /** Un meuble garde trois paquets au plus par colonne ; le chariot en porte six au plus (le paquet en main compte). */
    const val SLOT_CAP = 3
    const val CART_CAP = 6

    /** Le chariot est plein au départ. */
    const val START_CART = 6

    const val MAX_ERRORS = 3

    /** Durées (millisecondes) de l'ouverture et de la journée. */
    const val OPENING_MS = 2_400f
    const val DAY_MS = 150_000f

    /** La durée de vie (millisecondes) d'un paquet neuf de chaque produit, avant l'effet de la difficulté. */
    private val LIFE_MS = floatArrayOf(42_000f, 32_000f, 56_000f, 24_000f, 28_000f)

    fun lifeMs(product: Int): Float = LIFE_MS[product.coerceIn(0, PRODUCTS - 1)]

    /** À la fin de la journée, les paquets vivent ce rapport de leur durée de vie du début. */
    const val LIFE_END_SCALE = 0.75f

    /** Part des paquets livrés à date courte (un tiers à la moitié de leur vie : ils vont devant), au début puis à la fin. */
    const val SHORT_CHANCE_START = 0.15f
    const val SHORT_CHANCE_END = 0.3f

    // ---------------------------------------------------------------- les livraisons
    const val DELIVERY_MIN = 3
    const val DELIVERY_MAX = 4
    const val FIRST_DELIVERY_MS = 9_000f
    const val DELIVERY_GAP_START = 8_500f
    const val DELIVERY_GAP_END = 6_500f

    // ---------------------------------------------------------------- les clients
    const val FIRST_CUSTOMER_MS = 6_500f
    const val CUSTOMER_GAP_START = 3_800f
    const val CUSTOMER_GAP_END = 2_300f
    const val MAX_CUSTOMERS = 3
    const val WALK_MS = 1_300f
    const val LEAVE_MS = 900f

    // ---------------------------------------------------------------- la porte du frigo
    const val DOOR_AGE_MULT = 3f
    const val FIRST_DOOR_MS = 26_000f
    const val DOOR_GAP_START = 34_000f
    const val DOOR_GAP_END = 22_000f

    /** Une porte restée ouverte aussi longtemps rompt la chaîne du froid : faute grave. */
    const val DOOR_FAULT_MS = 16_000f

    // ---------------------------------------------------------------- le contrôle d'hygiène
    const val FIRST_INSPECT_MS = 42_000f
    const val INSPECT_GAP_START = 45_000f
    const val INSPECT_GAP_END = 36_000f
    const val INSPECT_WARN_MS = 7_000f
    const val INSPECT_VERDICT_MS = 2_400f

    // ---------------------------------------------------------------- les points
    const val POINTS_STOCK = 12
    const val POINTS_STOCK_LATE = 4
    const val PENALTY_WRONG = 8
    const val POINTS_SALE = 30
    const val POINTS_SALE_MARKED = 18
    const val STREAK_BONUS = 5
    const val STREAK_BONUS_STEPS = 6
    const val POINTS_REMOVE = 25
    const val WASTE_BASE = 6
    const val WASTE_SPAN = 24
    const val POINTS_MARK = 4
    const val PENALTY_SICK = 60
    const val PENALTY_STOCKOUT = 8
    const val POINTS_DOOR = 15
    const val PENALTY_DOOR = 40
    const val INSPECT_BONUS = 120
    const val INSPECT_PENALTY = 60

    /** Un compteur est « bas » sous ce rapport de la durée de vie, et « moyen » sous le suivant. */
    const val LOW_RATIO = 0.25f
    const val MID_RATIO = 0.5f

    // ---------------------------------------------------------------- les animations (millisecondes)
    const val BLOCKED_MS = 300f
    const val PLACE_MS = 350f
    const val MOVE_MS = 220f
    const val EVENT_MS = 1_300f
    const val FAULT_MS = 700f

    /** La partie se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f
}

/** Un paquet : son produit, le temps qu'il lui reste avant sa date limite, sa durée de vie de départ et sa démarque. */
internal class FreshPack(val product: Int, var lifeMs: Float, val maxMs: Float, var marked: Boolean = false) {
    /** Vrai quand la date limite est passée. */
    val expired: Boolean get() = lifeMs <= 0f

    /** Ce qu'il reste de la vie du paquet : 1 (neuf) à 0 (périmé). */
    val ratio: Float get() = if (maxMs <= 0f) 0f else (lifeMs / maxMs).coerceIn(0f, 1f)
}

/** Un client qui vient au meuble : il marche vers la colonne de son produit, le prend, puis repart du côté d'où il est venu. */
internal class FreshCustomer(
    val product: Int,
    val look: Int,
    val fromLeft: Boolean,
    var ms: Float = 0f,
    var done: Boolean = false,
    var mood: Int = 0
)

/**
 * Une partie de Rayon frais. [tick] fait avancer le temps, [press] reçoit les touches ([FRESH_LEFT] à [FRESH_B]).
 */
internal class FreshGame(private val random: Random = Random.Default) {
    var stage = FRESH_STAGE_OPENING
        private set
    var endReason = FRESH_END_NONE
        private set

    /** Le temps qui reste dans l'étape en cours : l'ouverture, puis la journée (millisecondes). */
    var stageLeftMs = Fresh.OPENING_MS
        private set

    /** L'horloge de la partie, pour les animations (millisecondes). */
    var clockMs = 0f
        private set

    /** Où se tient le joueur (0 le chariot, 1 à 5 les colonnes, 6 la porte), de quel côté il regarde, et le temps de marche restant. */
    var pos = Fresh.CART_POS
        private set
    var facing = 1
        private set
    var moveMs = 0f
        private set

    /** Le paquet que le joueur tient en main, s'il en a un. */
    var held: FreshPack? = null
        private set

    /** Le meuble : une colonne par produit, le paquet de devant en premier. */
    val slots: List<ArrayList<FreshPack>> = List(Fresh.PRODUCTS) { ArrayList<FreshPack>() }

    /** Le chariot : le paquet du dessus (celui qu'on prend) en premier. */
    val cart = ArrayList<FreshPack>()

    /** Les clients qui sont au meuble en ce moment. */
    val customers = ArrayList<FreshCustomer>()

    var points = 0
        private set

    /** Le score : les points de la partie, jamais en dessous de zéro. */
    val score: Int get() = max(0, points)

    /** Les fautes graves (trois, et c'est fini). */
    var errors = 0
        private set

    // Les compteurs de la partie.
    var stocked = 0
        private set
    var misordered = 0
        private set
    var wrongSlots = 0
        private set
    var sold = 0
        private set
    var removedExpired = 0
        private set
    var wasted = 0
        private set
    var markdowns = 0
        private set
    var sickCustomers = 0
        private set
    var stockouts = 0
        private set
    var doorsClosed = 0
        private set
    var doorFaults = 0
        private set
    var inspectOk = 0
        private set
    var inspectFails = 0
        private set
    var streak = 0
        private set
    var bestStreak = 0
        private set

    // La porte du frigo.
    var doorOpen = false
        private set
    var doorOpenMs = 0f
        private set
    private var nextDoorMs = Fresh.FIRST_DOOR_MS

    // Le contrôle d'hygiène.
    var inspect = FRESH_INSPECT_NONE
        private set
    var inspectMs = 0f
        private set
    var inspectPass = false
        private set
    private var nextInspectMs = Fresh.FIRST_INSPECT_MS

    private var deliverInMs = Fresh.FIRST_DELIVERY_MS
    private var spawnCustomerMs = Fresh.FIRST_CUSTOMER_MS

    // Le dernier fait marquant, et les animations : geste refusé, paquet rangé, faute, fin de partie.
    var lastEvent = FRESH_EVENT_NONE
        private set
    var lastPoints = 0
        private set
    var eventMs = 0f
        private set
    var blockedMs = 0f
        private set
    var placeSlot = -1
        private set
    var placeMs = 0f
        private set
    var faultMs = 0f
        private set
    var overMs = 0f
        private set

    init {
        reset()
    }

    /** Une partie neuve. */
    fun reset() {
        stage = FRESH_STAGE_OPENING
        endReason = FRESH_END_NONE
        stageLeftMs = Fresh.OPENING_MS
        clockMs = 0f
        pos = Fresh.CART_POS
        facing = 1
        moveMs = 0f
        held = null
        for (queue in slots) queue.clear()
        cart.clear()
        customers.clear()
        points = 0
        errors = 0
        stocked = 0
        misordered = 0
        wrongSlots = 0
        sold = 0
        removedExpired = 0
        wasted = 0
        markdowns = 0
        sickCustomers = 0
        stockouts = 0
        doorsClosed = 0
        doorFaults = 0
        inspectOk = 0
        inspectFails = 0
        streak = 0
        bestStreak = 0
        doorOpen = false
        doorOpenMs = 0f
        nextDoorMs = Fresh.FIRST_DOOR_MS
        inspect = FRESH_INSPECT_NONE
        inspectMs = 0f
        inspectPass = false
        nextInspectMs = Fresh.FIRST_INSPECT_MS
        deliverInMs = Fresh.FIRST_DELIVERY_MS
        spawnCustomerMs = Fresh.FIRST_CUSTOMER_MS
        lastEvent = FRESH_EVENT_NONE
        lastPoints = 0
        eventMs = 0f
        blockedMs = 0f
        placeSlot = -1
        placeMs = 0f
        faultMs = 0f
        overMs = 0f
        repeat(Fresh.START_CART) { cart.add(newPack()) }
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 (début de la journée) à 1 (dernière seconde). */
    val difficulty: Float
        get() = when (stage) {
            FRESH_STAGE_PLAY -> 1f - stageLeftMs / Fresh.DAY_MS
            FRESH_STAGE_OPENING -> 0f
            else -> 1f
        }.coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    // ---------------------------------------------------------------- état

    val over: Boolean get() = stage == FRESH_STAGE_OVER

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Fresh.RESTART_DELAY_MS

    /**
     * Le paquet visé dans la colonne [slot] : un paquet périmé d'abord (le plus proche de l'avant), sinon celui qui expire le premier
     * parmi ceux qui ne sont pas démarqués, sinon celui qui expire le premier. Rien si la colonne est vide.
     */
    fun target(slot: Int): FreshPack? {
        if (slot < 0 || slot >= Fresh.PRODUCTS) return null
        val queue = slots[slot]
        val expired = queue.firstOrNull { it.expired }
        if (expired != null) return expired
        return queue.filter { !it.marked }.minByOrNull { it.lifeMs } ?: queue.minByOrNull { it.lifeMs }
    }

    /** Le nombre de paquets périmés dans le meuble. */
    fun expiredCount(): Int {
        var count = 0
        for (queue in slots) for (pack in queue) if (pack.expired) count++
        return count
    }

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        moveMs = max(0f, moveMs - dt)
        eventMs = max(0f, eventMs - dt)
        blockedMs = max(0f, blockedMs - dt)
        placeMs = max(0f, placeMs - dt)
        faultMs = max(0f, faultMs - dt)
        when (stage) {
            FRESH_STAGE_OPENING -> {
                stageLeftMs -= dt
                if (stageLeftMs <= 0f) {
                    stage = FRESH_STAGE_PLAY
                    stageLeftMs = Fresh.DAY_MS
                }
            }
            FRESH_STAGE_PLAY -> tickPlay(dt)
            else -> overMs += dt
        }
    }

    private fun tickPlay(dt: Float) {
        stageLeftMs -= dt
        // Les paquets du meuble vieillissent, trois fois plus vite quand la porte est ouverte (ceux du chariot ne vieillissent pas).
        val speed = if (doorOpen) Fresh.DOOR_AGE_MULT else 1f
        for (queue in slots) for (pack in queue) pack.lifeMs = max(0f, pack.lifeMs - dt * speed)

        deliverInMs -= dt
        if (deliverInMs <= 0f) {
            deliver()
            deliverInMs += lerp(Fresh.DELIVERY_GAP_START, Fresh.DELIVERY_GAP_END)
        }

        spawnCustomerMs -= dt
        if (spawnCustomerMs <= 0f && customers.size < Fresh.MAX_CUSTOMERS) {
            customers.add(FreshCustomer(random.nextInt(Fresh.PRODUCTS), random.nextInt(4), random.nextBoolean()))
            spawnCustomerMs = lerp(Fresh.CUSTOMER_GAP_START, Fresh.CUSTOMER_GAP_END) * (0.75f + 0.5f * random.nextFloat())
        }
        val iterator = customers.iterator()
        while (iterator.hasNext()) {
            val customer = iterator.next()
            customer.ms += dt
            if (!customer.done && customer.ms >= Fresh.WALK_MS) {
                serve(customer)
                if (stage != FRESH_STAGE_PLAY) return
            }
            if (customer.ms >= Fresh.WALK_MS + Fresh.LEAVE_MS) iterator.remove()
        }

        tickDoor(dt)
        if (stage != FRESH_STAGE_PLAY) return
        tickInspector(dt)
        if (stage != FRESH_STAGE_PLAY) return

        if (stageLeftMs <= 0f) {
            stageLeftMs = 0f
            finish(FRESH_END_TIME)
        }
    }

    private fun tickDoor(dt: Float) {
        if (doorOpen) {
            doorOpenMs += dt
            if (doorOpenMs >= Fresh.DOOR_FAULT_MS) {
                closeDoor()
                doorFaults++
                streak = 0
                award(-Fresh.PENALTY_DOOR, FRESH_EVENT_DOOR_FAULT)
                fault()
            }
        } else {
            nextDoorMs -= dt
            if (nextDoorMs <= 0f) {
                if (inspect == FRESH_INSPECT_WARN) {
                    // Pas de porte ouverte juste au moment où l'inspecteur arrive : on attend un peu.
                    nextDoorMs = 3_000f
                } else {
                    doorOpen = true
                    doorOpenMs = 0f
                }
            }
        }
    }

    private fun closeDoor() {
        doorOpen = false
        doorOpenMs = 0f
        nextDoorMs = lerp(Fresh.DOOR_GAP_START, Fresh.DOOR_GAP_END) * (0.8f + 0.4f * random.nextFloat())
    }

    private fun tickInspector(dt: Float) {
        when (inspect) {
            FRESH_INSPECT_NONE -> {
                nextInspectMs -= dt
                // Pas de contrôle dans les dernières secondes de la journée.
                if (nextInspectMs <= 0f && stageLeftMs > Fresh.INSPECT_WARN_MS + 4_000f) {
                    inspect = FRESH_INSPECT_WARN
                    inspectMs = Fresh.INSPECT_WARN_MS
                }
            }
            FRESH_INSPECT_WARN -> {
                inspectMs -= dt
                if (inspectMs <= 0f) checkInspection()
            }
            else -> {
                inspectMs -= dt
                if (inspectMs <= 0f) {
                    inspect = FRESH_INSPECT_NONE
                    inspectMs = 0f
                    nextInspectMs = lerp(Fresh.INSPECT_GAP_START, Fresh.INSPECT_GAP_END) * (0.9f + 0.2f * random.nextFloat())
                }
            }
        }
    }

    /** Le contrôle : un paquet périmé dans le meuble ou la porte ouverte, c'est une faute grave ; sinon, une prime. */
    private fun checkInspection() {
        inspectPass = expiredCount() == 0 && !doorOpen
        inspect = FRESH_INSPECT_VERDICT
        inspectMs = Fresh.INSPECT_VERDICT_MS
        if (inspectPass) {
            inspectOk++
            award(Fresh.INSPECT_BONUS, FRESH_EVENT_INSPECT_OK)
        } else {
            inspectFails++
            streak = 0
            award(-Fresh.INSPECT_PENALTY, FRESH_EVENT_INSPECT_KO)
            fault()
        }
    }

    // ---------------------------------------------------------------- les paquets et les clients

    private fun newPack(): FreshPack {
        val product = random.nextInt(Fresh.PRODUCTS)
        val short = random.nextFloat() < lerp(Fresh.SHORT_CHANCE_START, Fresh.SHORT_CHANCE_END)
        val factor = if (short) 0.35f + 0.2f * random.nextFloat() else 0.85f + 0.15f * random.nextFloat()
        val full = Fresh.lifeMs(product) * lerp(1f, Fresh.LIFE_END_SCALE)
        return FreshPack(product, full * factor, full)
    }

    /** Une livraison : trois ou quatre paquets sur le chariot, dans la limite de sa place. */
    private fun deliver() {
        val room = Fresh.CART_CAP - cart.size - (if (held != null) 1 else 0)
        val count = min(room, Fresh.DELIVERY_MIN + random.nextInt(Fresh.DELIVERY_MAX - Fresh.DELIVERY_MIN + 1))
        repeat(max(0, count)) { cart.add(newPack()) }
    }

    /** Le client arrivé devant sa colonne prend le paquet démarqué, sinon celui de devant. */
    private fun serve(customer: FreshCustomer) {
        customer.done = true
        val queue = slots[customer.product]
        if (queue.isEmpty()) {
            stockouts++
            streak = 0
            customer.mood = -1
            award(-Fresh.PENALTY_STOCKOUT, FRESH_EVENT_STOCKOUT)
            return
        }
        val pick = queue.firstOrNull { it.marked } ?: queue[0]
        queue.remove(pick)
        if (pick.expired) {
            sickCustomers++
            streak = 0
            customer.mood = -1
            award(-Fresh.PENALTY_SICK, FRESH_EVENT_SICK)
            fault()
            return
        }
        customer.mood = 1
        sold++
        streak++
        bestStreak = max(bestStreak, streak)
        val base = if (pick.marked) Fresh.POINTS_SALE_MARKED else Fresh.POINTS_SALE
        val gain = base + Fresh.STREAK_BONUS * min(streak - 1, Fresh.STREAK_BONUS_STEPS)
        award(gain, if (pick.marked) FRESH_EVENT_SALE_MARKED else FRESH_EVENT_SALE)
    }

    // ---------------------------------------------------------------- les gestes du joueur

    /** Une touche pressée. */
    fun press(key: Int) {
        if (stage != FRESH_STAGE_PLAY) return
        when (key) {
            FRESH_LEFT -> move(-1)
            FRESH_RIGHT -> move(1)
            FRESH_A, FRESH_B -> act(key == FRESH_A)
        }
    }

    private fun move(direction: Int) {
        facing = direction
        pos = (pos + direction).coerceIn(0, Fresh.POSITIONS - 1)
        moveMs = Fresh.MOVE_MS
    }

    private fun act(isA: Boolean) {
        when (pos) {
            Fresh.CART_POS -> actCart()
            Fresh.DOOR_POS -> actDoor()
            else -> actSlot(pos - 1, isA)
        }
    }

    /** Au chariot : prendre le paquet du dessus, ou, si on en tient un, le remettre. */
    private fun actCart() {
        val pack = held
        if (pack == null) {
            if (cart.isEmpty()) {
                blockedMs = Fresh.BLOCKED_MS
                return
            }
            held = cart.removeAt(0)
        } else {
            cart.add(0, pack)
            held = null
        }
    }

    /** À la porte : la fermer si elle est ouverte (sinon, rien). */
    private fun actDoor() {
        if (!doorOpen) return
        closeDoor()
        doorsClosed++
        award(Fresh.POINTS_DOOR, FRESH_EVENT_DOOR_CLOSED)
    }

    private fun actSlot(slot: Int, isA: Boolean) {
        val queue = slots[slot]
        val pack = held
        if (pack != null) {
            // Un paquet en main : le ranger au fond (A) ou devant (B), dans la colonne de son produit.
            if (pack.product != slot) {
                wrongSlots++
                streak = 0
                blockedMs = Fresh.BLOCKED_MS
                award(-Fresh.PENALTY_WRONG, FRESH_EVENT_WRONG)
                return
            }
            if (queue.size >= Fresh.SLOT_CAP) {
                blockedMs = Fresh.BLOCKED_MS
                return
            }
            // Le bon ordre : le paquet qui expire le premier devant. Au fond, il faut expirer après le dernier ; devant, avant le premier.
            val inOrder = when {
                queue.isEmpty() -> true
                isA -> pack.lifeMs >= queue[queue.size - 1].lifeMs
                else -> pack.lifeMs <= queue[0].lifeMs
            }
            if (isA) queue.add(pack) else queue.add(0, pack)
            held = null
            stocked++
            placeSlot = slot
            placeMs = Fresh.PLACE_MS
            if (inOrder) {
                award(Fresh.POINTS_STOCK, FRESH_EVENT_STOCK_OK)
            } else {
                misordered++
                award(Fresh.POINTS_STOCK_LATE, FRESH_EVENT_STOCK_LATE)
            }
            return
        }
        // Les mains libres : retirer (A) ou démarquer (B) le paquet visé.
        val aim = target(slot)
        if (aim == null) {
            blockedMs = Fresh.BLOCKED_MS
            return
        }
        if (isA) {
            queue.remove(aim)
            if (aim.expired) {
                removedExpired++
                streak++
                bestStreak = max(bestStreak, streak)
                award(Fresh.POINTS_REMOVE, FRESH_EVENT_REMOVED)
            } else {
                // Jeter un paquet encore bon : d'autant plus cher qu'il lui restait de vie.
                wasted++
                streak = 0
                award(-(Fresh.WASTE_BASE + (Fresh.WASTE_SPAN * aim.ratio).roundToInt()), FRESH_EVENT_WASTE)
            }
        } else {
            if (aim.expired || aim.marked) {
                blockedMs = Fresh.BLOCKED_MS
                return
            }
            aim.marked = true
            markdowns++
            award(Fresh.POINTS_MARK, FRESH_EVENT_MARKED)
        }
    }

    // ---------------------------------------------------------------- points, fautes, fin

    private fun award(amount: Int, event: Int) {
        points += amount
        lastPoints = amount
        lastEvent = event
        eventMs = Fresh.EVENT_MS
    }

    /** Une faute grave : à la troisième, le joueur est licencié. */
    private fun fault() {
        errors++
        faultMs = Fresh.FAULT_MS
        if (errors >= Fresh.MAX_ERRORS) finish(FRESH_END_FIRED)
    }

    private fun finish(reason: Int) {
        if (stage == FRESH_STAGE_OVER) return
        stage = FRESH_STAGE_OVER
        endReason = reason
        overMs = 0f
    }

    // ---------------------------------------------------------------- sauvegarde

    /** L'état de la partie en une liste de nombres, pour qu'elle survive à une rotation de l'écran. */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        fun put(pack: FreshPack) {
            put(pack.product)
            put(pack.lifeMs)
            put(pack.maxMs)
            put(pack.marked)
        }
        put(SAVE_VERSION)
        put(stage); put(endReason); put(stageLeftMs); put(clockMs)
        put(pos); put(facing); put(moveMs)
        put(points); put(errors)
        put(stocked); put(misordered); put(wrongSlots); put(sold); put(removedExpired); put(wasted); put(markdowns)
        put(sickCustomers); put(stockouts); put(doorsClosed); put(doorFaults); put(inspectOk); put(inspectFails)
        put(streak); put(bestStreak)
        put(doorOpen); put(doorOpenMs); put(nextDoorMs)
        put(inspect); put(inspectMs); put(inspectPass); put(nextInspectMs)
        put(deliverInMs); put(spawnCustomerMs)
        put(lastEvent); put(lastPoints); put(eventMs); put(blockedMs); put(placeSlot); put(placeMs); put(faultMs); put(overMs)
        val inHand = held
        put(inHand != null)
        if (inHand != null) put(inHand)
        for (queue in slots) {
            put(queue.size)
            for (pack in queue) put(pack)
        }
        put(cart.size)
        for (pack in cart) put(pack)
        put(customers.size)
        for (customer in customers) {
            put(customer.product)
            put(customer.look)
            put(customer.fromLeft)
            put(customer.ms)
            put(customer.done)
            put(customer.mood)
        }
        return out
    }

    private fun load(values: List<Double>) {
        var at = 0
        fun num(): Double = values[at++]
        fun int(): Int = num().toInt()
        fun float(): Float = num().toFloat()
        fun flag(): Boolean = num() != 0.0
        fun pack(): FreshPack {
            val product = int().coerceIn(0, Fresh.PRODUCTS - 1)
            val life = float()
            val full = max(1f, float())
            return FreshPack(product, life, full, flag())
        }
        require(int() == SAVE_VERSION)
        stage = int().coerceIn(FRESH_STAGE_OPENING, FRESH_STAGE_OVER); endReason = int().coerceIn(FRESH_END_NONE, FRESH_END_FIRED)
        stageLeftMs = float(); clockMs = float()
        pos = int().coerceIn(0, Fresh.POSITIONS - 1); facing = if (int() >= 0) 1 else -1; moveMs = float()
        points = int(); errors = int().coerceIn(0, Fresh.MAX_ERRORS)
        stocked = int(); misordered = int(); wrongSlots = int(); sold = int(); removedExpired = int(); wasted = int(); markdowns = int()
        sickCustomers = int(); stockouts = int(); doorsClosed = int(); doorFaults = int(); inspectOk = int(); inspectFails = int()
        streak = int(); bestStreak = int()
        doorOpen = flag(); doorOpenMs = float(); nextDoorMs = float()
        inspect = int().coerceIn(FRESH_INSPECT_NONE, FRESH_INSPECT_VERDICT); inspectMs = float(); inspectPass = flag(); nextInspectMs = float()
        deliverInMs = float(); spawnCustomerMs = float()
        lastEvent = int().coerceIn(FRESH_EVENT_NONE, FRESH_EVENT_COUNT - 1); lastPoints = int(); eventMs = float(); blockedMs = float()
        placeSlot = int().coerceIn(-1, Fresh.PRODUCTS - 1); placeMs = float(); faultMs = float(); overMs = float()
        held = if (flag()) pack() else null
        for (queue in slots) {
            queue.clear()
            val count = int().coerceIn(0, Fresh.SLOT_CAP)
            repeat(count) { queue.add(pack()) }
        }
        cart.clear()
        val cartCount = int().coerceIn(0, Fresh.CART_CAP)
        repeat(cartCount) { cart.add(pack()) }
        customers.clear()
        val customerCount = int().coerceIn(0, Fresh.MAX_CUSTOMERS)
        repeat(customerCount) {
            val product = int().coerceIn(0, Fresh.PRODUCTS - 1)
            val look = int().coerceIn(0, 3)
            val fromLeft = flag()
            val ms = float()
            val done = flag()
            customers.add(FreshCustomer(product, look, fromLeft, ms, done, int().coerceIn(-1, 1)))
        }
    }

    companion object {
        private const val SAVE_VERSION = 1

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): FreshGame {
            val game = FreshGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
