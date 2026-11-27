package com.aventure.messcores

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Identifiant du jeu « Boulangerie » (cuire pains et viennoiseries pour garder l'étalage garni) dans la liste des jeux. */
const val BAKERY_ID = "builtin_bakery"

// Les six touches du jeu : la croix (gauche, droite pour choisir le produit ; haut, bas pour régler son temps de cuisson),
// le bouton A (enfourner ou sortir) et le bouton B (changer de four).
internal const val BAKERY_LEFT = 0
internal const val BAKERY_RIGHT = 1
internal const val BAKERY_UP = 2
internal const val BAKERY_DOWN = 3
internal const val BAKERY_A = 4
internal const val BAKERY_B = 5

// L'état de cuisson d'une fournée : cru, pâle, doré (le bon moment), trop cuit, brûlé.
internal const val BAKE_RAW = 0
internal const val BAKE_PALE = 1
internal const val BAKE_GOLD = 2
internal const val BAKE_DARK = 3
internal const val BAKE_BURNT = 4

// Les trois sortes de pièces sur l'étalage : bien cuites (plein tarif), pâles ou trop cuites (demi-tarif).
internal const val SHELF_GOOD = 0
internal const val SHELF_PALE = 1
internal const val SHELF_DARK = 2

// Les étapes d'un client : absent, il arrive, il regarde l'étalage, il paie (content ou non), il repart.
internal const val CUSTOMER_NONE = 0
internal const val CUSTOMER_ARRIVE = 1
internal const val CUSTOMER_THINK = 2
internal const val CUSTOMER_RESULT = 3
internal const val CUSTOMER_LEAVE = 4

/**
 * Boulangerie. Trois fours, six produits (baguette, pain de campagne, croissant, pain au chocolat, brioche, chausson aux pommes).
 * Chaque produit a son propre temps de cuisson, que le joueur ne connaît pas : il règle sur le mur du fond le temps que le four
 * doit sonner (flèches haut et bas), enfourne un plateau de pâtons crus (A), puis sort la fournée au bon moment (A) : trop tôt
 * elle est pâle, trop tard elle fonce, encore plus tard elle brûle. Les clients se servent seuls sur l'étalage ; un client qui ne
 * trouve pas tout ce qu'il voulait repart fâché, et la partie est perdue au troisième. Le score est le chiffre d'affaires.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [BakeryGame].
 */
internal object Bakery {
    /** L'écran est le même que celui de Drive et de la Poissonnerie (720 x 405), pour reprendre leur console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    const val PRODUCTS = 6
    const val OVENS = 3

    /** Le vrai temps de cuisson de chaque produit (millisecondes) : au bout de ce temps, la fournée est dorée à point. */
    val BAKE_MS = intArrayOf(16_000, 28_000, 12_000, 14_000, 22_000, 18_000)

    /** Le nombre de pièces d'une fournée (un plateau de pâtons). */
    val BATCH = intArrayOf(4, 2, 6, 6, 3, 4)

    /** Le prix d'une pièce en euros (toujours pair : une pièce pâle ou trop cuite se vend à moitié prix). */
    val PRICE = intArrayOf(2, 6, 2, 4, 6, 4)

    /** Ce que les clients demandent le plus souvent (poids du tirage au sort). */
    val DEMAND = intArrayOf(30, 14, 22, 18, 8, 12)

    /** L'étalage garde huit pièces au plus par produit ; ce qui dépasse est perdu. */
    const val SHELF_CAP = 8

    // ---------------------------------------------------------------- la pâte
    /** Plateaux de pâtons crus prêts à enfourner au départ, au plus, et temps de pousse d'un nouveau plateau (millisecondes). */
    const val START_RAW = 1
    const val MAX_RAW = 2
    const val DOUGH_MS = 9_000f

    // ---------------------------------------------------------------- la cuisson
    /** Le temps cuit divisé par le vrai temps de cuisson : en dessous de [RAW_LIMIT] c'est cru, puis pâle, doré, trop cuit, brûlé. */
    const val RAW_LIMIT = 0.72f
    const val PALE_LIMIT = 0.90f
    const val GOLD_LIMIT = 1.20f
    const val BURN_LIMIT = 1.45f

    /** Le temps réglé sur le mur, en secondes : au départ il est faux (à toi de le trouver), puis il se règle par pas d'une seconde. */
    const val MIN_SET = 1
    const val MAX_SET = 60
    const val START_SET = 10

    // ---------------------------------------------------------------- les clients
    const val MAX_MISSES = 3

    /** Avant le premier client (millisecondes) ; puis le délai entre deux clients, au début et à la fin ; la difficulté monte pendant [DIFFICULTY_MS]. */
    const val FIRST_CUSTOMER_MS = 5_000f
    const val GAP_START_MS = 6_500f
    const val GAP_END_MS = 1_800f
    const val DIFFICULTY_MS = 300_000f

    /** Les étapes d'un client (millisecondes) : il arrive, il regarde (de moins en moins longtemps), il paie, il repart. */
    const val ARRIVE_MS = 900f
    const val THINK_START_MS = 2_300f
    const val THINK_END_MS = 1_500f
    const val RESULT_MS = 1_000f
    const val LEAVE_MS = 700f

    // ---------------------------------------------------------------- les animations (millisecondes)
    const val BLOCKED_MS = 300f
    const val DING_MS = 700f
    const val PULL_MS = 450f
    const val HAPPY_MS = 700f
    const val LOAD_MS = 350f

    /** La partie se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f

    /** L'état de cuisson d'une fournée du produit [product] cuite pendant [ms] millisecondes. */
    fun qualityAt(product: Int, ms: Float): Int {
        val ratio = ms / BAKE_MS[product]
        return when {
            ratio < RAW_LIMIT -> BAKE_RAW
            ratio < PALE_LIMIT -> BAKE_PALE
            ratio <= GOLD_LIMIT -> BAKE_GOLD
            ratio <= BURN_LIMIT -> BAKE_DARK
            else -> BAKE_BURNT
        }
    }

    /** Ce que rapporte une pièce du produit [product] de la sorte [kind] ([SHELF_GOOD] à [SHELF_DARK]). */
    fun value(product: Int, kind: Int): Int = if (kind == SHELF_GOOD) PRICE[product] else PRICE[product] / 2
}

/**
 * Une partie de Boulangerie. [tick] fait avancer le temps, [press] reçoit les touches ([BAKERY_LEFT] à [BAKERY_B]).
 */
internal class BakeryGame(private val random: Random = Random.Default) {
    /** L'horloge de la partie, pour les animations (millisecondes). */
    var clockMs = 0f
        private set

    /** Le temps réglé sur le mur pour chaque produit, en secondes. */
    val setting = IntArray(Bakery.PRODUCTS)

    /** Les plateaux de pâtons crus qui attendent, et l'avancement (millisecondes) de la pousse du prochain plateau. */
    val raw = IntArray(Bakery.PRODUCTS)
    val doughMs = FloatArray(Bakery.PRODUCTS)

    /** Dans chaque four : le produit (-1 s'il est vide), le temps de cuisson écoulé, et si le four a déjà sonné. */
    val ovenProduct = IntArray(Bakery.OVENS)
    val ovenMs = FloatArray(Bakery.OVENS)
    private val burnSeen = BooleanArray(Bakery.OVENS)

    /** Les animations des fours : la sonnerie, la fournée qu'on vient de sortir, la fournée qu'on vient d'enfourner. */
    val dingMs = FloatArray(Bakery.OVENS)
    val pullMs = FloatArray(Bakery.OVENS)
    val loadMs = FloatArray(Bakery.OVENS)

    /** L'étalage : pour chaque produit, le nombre de pièces bien cuites, pâles et trop cuites. */
    val stock = Array(Bakery.PRODUCTS) { IntArray(3) }

    /** Le dernier état de la fournée sortie pour chaque produit ([BAKE_PALE] à [BAKE_BURNT]), ou -1 : c'est l'indice affiché sur le mur. */
    val lastPull = IntArray(Bakery.PRODUCTS)

    /** Le produit choisi (colonne du mur, de la pâte et de l'étalage) et le four choisi. */
    var productCursor = 0
        private set
    var ovenCursor = 0
        private set

    /** Le chiffre d'affaires, en euros : c'est le score. */
    var revenue = 0
        private set
    val score: Int get() = revenue

    // Les compteurs de la partie.
    var served = 0
        private set
    var misses = 0
        private set
    var goldBatches = 0
        private set
    var bakedBatches = 0
        private set
    var burnedBatches = 0
        private set
    var lostPieces = 0
        private set

    // Le client en cours.
    var customerStage = CUSTOMER_NONE
        private set
    var stageMs = 0f
        private set
    private var gapMs = Bakery.FIRST_CUSTOMER_MS
    var customerLook = 0
        private set

    /** De quel côté le client entre : -1 par la gauche, 1 par la droite. */
    var customerSide = 1
        private set
    var wantLines = 0
        private set
    val wantProduct = IntArray(3)
    val wantQty = IntArray(3)

    /** Ce que le client a pu prendre pour chaque ligne, une fois servi. */
    val gotQty = IntArray(3)

    /** L'humeur du client en partant : 1 content, -1 fâché, 0 pas encore servi. */
    var mood = 0
        private set

    /** Ce que le dernier client a payé, pour l'afficher. */
    var lastPoints = 0
        private set
    private var thinkMs = Bakery.THINK_START_MS

    var blockedMs = 0f
        private set

    /** Le boulanger est content (il saute) tant que ce temps n'est pas écoulé : on vient de sortir une fournée dorée. */
    var happyMs = 0f
        private set
    var over = false
        private set
    var overMs = 0f
        private set

    /** Vrai quand le troisième client fâché est en train de partir : la partie finira à son départ. */
    private var ending = false

    init {
        reset()
    }

    /** Une partie neuve : un plateau de pâtons de chaque produit, et une fournée de chaque sur l'étalage. */
    fun reset() {
        clockMs = 0f
        setting.fill(Bakery.START_SET)
        raw.fill(Bakery.START_RAW)
        doughMs.fill(0f)
        ovenProduct.fill(-1)
        ovenMs.fill(0f)
        burnSeen.fill(false)
        dingMs.fill(0f)
        pullMs.fill(0f)
        loadMs.fill(0f)
        for (p in 0 until Bakery.PRODUCTS) {
            stock[p].fill(0)
            stock[p][SHELF_GOOD] = Bakery.BATCH[p]
        }
        lastPull.fill(-1)
        productCursor = 0
        ovenCursor = 0
        revenue = 0
        served = 0
        misses = 0
        goldBatches = 0
        bakedBatches = 0
        burnedBatches = 0
        lostPieces = 0
        customerStage = CUSTOMER_NONE
        stageMs = 0f
        gapMs = Bakery.FIRST_CUSTOMER_MS
        customerLook = 0
        customerSide = 1
        wantLines = 0
        wantProduct.fill(0)
        wantQty.fill(0)
        gotQty.fill(0)
        mood = 0
        lastPoints = 0
        thinkMs = Bakery.THINK_START_MS
        blockedMs = 0f
        happyMs = 0f
        over = false
        overMs = 0f
        ending = false
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 (début de la journée) à 1 (au bout de cinq minutes) : les clients viennent plus vite et demandent plus. */
    val difficulty: Float get() = (clockMs / Bakery.DIFFICULTY_MS).coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    // ---------------------------------------------------------------- état

    /** Le nombre de pièces sur l'étalage pour le produit [product], toutes sortes confondues. */
    fun total(product: Int): Int = stock[product].sum()

    /** L'état de cuisson de la fournée du four [oven], ou -1 si le four est vide. */
    fun ovenQuality(oven: Int): Int =
        if (ovenProduct[oven] < 0) -1 else Bakery.qualityAt(ovenProduct[oven], ovenMs[oven])

    /** Le temps cuit divisé par le vrai temps de cuisson de la fournée du four [oven] (0 si le four est vide). */
    fun ovenRatio(oven: Int): Float =
        if (ovenProduct[oven] < 0) 0f else ovenMs[oven] / Bakery.BAKE_MS[ovenProduct[oven]]

    /** Vrai quand le four [oven] a atteint le temps réglé sur le mur pour son produit (et que la fournée n'est pas brûlée). */
    fun ringing(oven: Int): Boolean {
        val product = ovenProduct[oven]
        if (product < 0) return false
        return ovenMs[oven] >= setting[product] * 1000f && ovenQuality(oven) != BAKE_BURNT
    }

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Bakery.RESTART_DELAY_MS

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        blockedMs = max(0f, blockedMs - dt)
        happyMs = max(0f, happyMs - dt)
        for (oven in 0 until Bakery.OVENS) {
            dingMs[oven] = max(0f, dingMs[oven] - dt)
            pullMs[oven] = max(0f, pullMs[oven] - dt)
            loadMs[oven] = max(0f, loadMs[oven] - dt)
        }
        if (over) {
            overMs += dt
            return
        }
        tickDough(dt)
        tickOvens(dt)
        tickCustomer(dt)
    }

    private fun tickDough(dt: Float) {
        for (p in 0 until Bakery.PRODUCTS) {
            if (raw[p] >= Bakery.MAX_RAW) {
                doughMs[p] = 0f
                continue
            }
            doughMs[p] += dt
            if (doughMs[p] >= Bakery.DOUGH_MS) {
                raw[p]++
                doughMs[p] = 0f
            }
        }
    }

    private fun tickOvens(dt: Float) {
        for (oven in 0 until Bakery.OVENS) {
            val product = ovenProduct[oven]
            if (product < 0) continue
            val before = ovenMs[oven]
            ovenMs[oven] = before + dt
            val target = setting[product] * 1000f
            if (before < target && ovenMs[oven] >= target) dingMs[oven] = Bakery.DING_MS
            if (!burnSeen[oven] && ovenQuality(oven) == BAKE_BURNT) {
                // La fournée vient de brûler : elle reste dans le four jusqu'à ce qu'on la jette.
                burnSeen[oven] = true
                burnedBatches++
            }
        }
    }

    private fun tickCustomer(dt: Float) {
        when (customerStage) {
            CUSTOMER_NONE -> {
                if (ending) return
                gapMs -= dt
                if (gapMs <= 0f) startCustomer()
            }
            CUSTOMER_ARRIVE -> {
                stageMs -= dt
                if (stageMs <= 0f) {
                    customerStage = CUSTOMER_THINK
                    stageMs = thinkMs
                }
            }
            CUSTOMER_THINK -> {
                stageMs -= dt
                if (stageMs <= 0f) {
                    serveCustomer()
                    customerStage = CUSTOMER_RESULT
                    stageMs = Bakery.RESULT_MS
                }
            }
            CUSTOMER_RESULT -> {
                stageMs -= dt
                if (stageMs <= 0f) {
                    customerStage = CUSTOMER_LEAVE
                    stageMs = Bakery.LEAVE_MS
                }
            }
            else -> {
                stageMs -= dt
                if (stageMs <= 0f) {
                    customerStage = CUSTOMER_NONE
                    stageMs = 0f
                    if (ending) {
                        over = true
                        overMs = 0f
                    } else {
                        gapMs = lerp(Bakery.GAP_START_MS, Bakery.GAP_END_MS)
                    }
                }
            }
        }
    }

    /** Un nouveau client : une à trois lignes (un produit différent chacune), d'une à trois pièces ; il en demande plus avec le temps. */
    private fun startCustomer() {
        val d = difficulty
        var lines = 1
        if (random.nextFloat() < 0.10f + 0.55f * d) lines++
        if (d > 0.4f && random.nextFloat() < 0.35f * d) lines++
        val taken = BooleanArray(Bakery.PRODUCTS)
        for (i in 0 until lines) {
            val product = pickProduct(taken)
            taken[product] = true
            wantProduct[i] = product
            var qty = 1
            if (random.nextFloat() < 0.20f + 0.40f * d) qty++
            if (d > 0.5f && random.nextFloat() < 0.25f * d) qty++
            wantQty[i] = min(qty, min(3, Bakery.BATCH[product]))
            gotQty[i] = 0
        }
        wantLines = lines
        customerLook = random.nextInt(4)
        customerSide = if (random.nextBoolean()) 1 else -1
        thinkMs = lerp(Bakery.THINK_START_MS, Bakery.THINK_END_MS)
        mood = 0
        lastPoints = 0
        customerStage = CUSTOMER_ARRIVE
        stageMs = Bakery.ARRIVE_MS
    }

    /** Tire un produit au sort selon la demande, parmi ceux que ce client n'a pas déjà demandés. */
    private fun pickProduct(taken: BooleanArray): Int {
        var sum = 0
        for (p in 0 until Bakery.PRODUCTS) if (!taken[p]) sum += Bakery.DEMAND[p]
        var roll = random.nextInt(sum)
        for (p in 0 until Bakery.PRODUCTS) {
            if (taken[p]) continue
            roll -= Bakery.DEMAND[p]
            if (roll < 0) return p
        }
        return 0
    }

    /** Le client se sert dans l'étalage (les pièces bien cuites d'abord) et paie ; s'il n'a pas tout trouvé, il repart fâché. */
    private fun serveCustomer() {
        var earned = 0
        var complete = true
        for (i in 0 until wantLines) {
            val product = wantProduct[i]
            var left = wantQty[i]
            for (kind in SHELF_GOOD..SHELF_DARK) {
                val n = min(left, stock[product][kind])
                stock[product][kind] -= n
                left -= n
                earned += n * Bakery.value(product, kind)
            }
            gotQty[i] = wantQty[i] - left
            if (left > 0) complete = false
        }
        revenue += earned
        lastPoints = earned
        if (complete) {
            served++
            mood = 1
        } else {
            misses++
            mood = -1
            if (misses >= Bakery.MAX_MISSES) ending = true
        }
    }

    // ---------------------------------------------------------------- joueur

    /** Une touche ([BAKERY_LEFT] à [BAKERY_B]) vient d'être pressée. */
    fun press(key: Int) {
        if (over) return
        when (key) {
            BAKERY_LEFT -> productCursor = (productCursor + Bakery.PRODUCTS - 1) % Bakery.PRODUCTS
            BAKERY_RIGHT -> productCursor = (productCursor + 1) % Bakery.PRODUCTS
            BAKERY_UP -> adjust(1)
            BAKERY_DOWN -> adjust(-1)
            BAKERY_A -> if (ovenProduct[ovenCursor] < 0) load(ovenCursor) else unload(ovenCursor)
            BAKERY_B -> ovenCursor = (ovenCursor + 1) % Bakery.OVENS
        }
    }

    /** Change le temps réglé sur le mur pour le produit choisi, d'une seconde. */
    private fun adjust(delta: Int) {
        setting[productCursor] = (setting[productCursor] + delta).coerceIn(Bakery.MIN_SET, Bakery.MAX_SET)
    }

    /** Enfourne un plateau de pâtons du produit choisi dans le four [oven] (il faut qu'il en reste de la pâte prête). */
    private fun load(oven: Int) {
        val product = productCursor
        if (raw[product] <= 0) {
            blockedMs = Bakery.BLOCKED_MS
            return
        }
        raw[product]--
        ovenProduct[oven] = product
        ovenMs[oven] = 0f
        burnSeen[oven] = false
        dingMs[oven] = 0f
        loadMs[oven] = Bakery.LOAD_MS
    }

    /**
     * Sort la fournée du four [oven] : crue, on ne peut pas (la porte reste fermée) ; brûlée, elle est jetée ; sinon elle va sur
     * l'étalage, selon sa cuisson (dorée : plein tarif, pâle ou trop cuite : demi-tarif), dans la limite de la place.
     */
    private fun unload(oven: Int) {
        val product = ovenProduct[oven]
        val quality = ovenQuality(oven)
        if (quality == BAKE_RAW) {
            blockedMs = Bakery.BLOCKED_MS
            return
        }
        lastPull[product] = quality
        if (quality != BAKE_BURNT) {
            val kind = when (quality) {
                BAKE_GOLD -> SHELF_GOOD
                BAKE_PALE -> SHELF_PALE
                else -> SHELF_DARK
            }
            val room = max(0, Bakery.SHELF_CAP - total(product))
            val added = min(room, Bakery.BATCH[product])
            stock[product][kind] += added
            lostPieces += Bakery.BATCH[product] - added
            bakedBatches++
            if (quality == BAKE_GOLD) {
                goldBatches++
                happyMs = Bakery.HAPPY_MS
            }
        }
        ovenProduct[oven] = -1
        ovenMs[oven] = 0f
        burnSeen[oven] = false
        dingMs[oven] = 0f
        pullMs[oven] = Bakery.PULL_MS
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        put(SAVE_VERSION)
        put(clockMs); put(productCursor); put(ovenCursor); put(revenue)
        put(served); put(misses); put(goldBatches); put(bakedBatches); put(burnedBatches); put(lostPieces)
        put(over); put(overMs); put(ending); put(blockedMs)
        for (p in 0 until Bakery.PRODUCTS) {
            put(setting[p]); put(raw[p]); put(doughMs[p]); put(lastPull[p])
            for (kind in SHELF_GOOD..SHELF_DARK) put(stock[p][kind])
        }
        for (oven in 0 until Bakery.OVENS) {
            put(ovenProduct[oven]); put(ovenMs[oven]); put(burnSeen[oven])
            put(dingMs[oven]); put(pullMs[oven]); put(loadMs[oven])
        }
        put(customerStage); put(stageMs); put(gapMs); put(customerLook); put(customerSide); put(thinkMs)
        put(wantLines); put(mood); put(lastPoints)
        for (i in 0 until 3) {
            put(wantProduct[i]); put(wantQty[i]); put(gotQty[i])
        }
        return out
    }

    private fun load(values: List<Double>) {
        var at = 0
        fun num(): Double = values[at++]
        fun int(): Int = num().toInt()
        fun float(): Float = num().toFloat()
        fun flag(): Boolean = num() != 0.0
        require(int() == SAVE_VERSION)
        clockMs = float(); productCursor = int().coerceIn(0, Bakery.PRODUCTS - 1); ovenCursor = int().coerceIn(0, Bakery.OVENS - 1)
        revenue = int()
        served = int(); misses = int(); goldBatches = int(); bakedBatches = int(); burnedBatches = int(); lostPieces = int()
        over = flag(); overMs = float(); ending = flag(); blockedMs = float()
        for (p in 0 until Bakery.PRODUCTS) {
            setting[p] = int().coerceIn(Bakery.MIN_SET, Bakery.MAX_SET)
            raw[p] = int().coerceIn(0, Bakery.MAX_RAW)
            doughMs[p] = float()
            lastPull[p] = int().coerceIn(-1, BAKE_BURNT)
            for (kind in SHELF_GOOD..SHELF_DARK) stock[p][kind] = int().coerceIn(0, Bakery.SHELF_CAP)
        }
        for (oven in 0 until Bakery.OVENS) {
            ovenProduct[oven] = int().coerceIn(-1, Bakery.PRODUCTS - 1)
            ovenMs[oven] = float()
            burnSeen[oven] = flag()
            dingMs[oven] = float(); pullMs[oven] = float(); loadMs[oven] = float()
        }
        customerStage = int().coerceIn(CUSTOMER_NONE, CUSTOMER_LEAVE)
        stageMs = float(); gapMs = float(); customerLook = int().coerceIn(0, 3)
        customerSide = if (int() >= 0) 1 else -1
        thinkMs = float()
        wantLines = int().coerceIn(0, 3); mood = int().coerceIn(-1, 1); lastPoints = int()
        for (i in 0 until 3) {
            wantProduct[i] = int().coerceIn(0, Bakery.PRODUCTS - 1)
            wantQty[i] = int().coerceIn(0, 3)
            gotQty[i] = int().coerceIn(0, 3)
        }
    }

    companion object {
        private const val SAVE_VERSION = 1

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): BakeryGame {
            val game = BakeryGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
