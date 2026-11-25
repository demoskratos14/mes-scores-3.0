package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Identifiant du jeu « Poissonnerie » (remplir l'étalage, puis servir les clients) dans la liste des jeux. */
const val FISH_ID = "builtin_fish"

// Les quatre touches du jeu : la croix (gauche, droite) pour choisir un poisson de l'étalage, les boutons A et B pour valider.
internal const val FISH_LEFT = 0
internal const val FISH_RIGHT = 1
internal const val FISH_A = 2
internal const val FISH_B = 3

// Les étapes d'une partie : l'approvisionnement, une courte ouverture du magasin, le service des clients, la fin.
internal const val FISH_STAGE_STOCK = 0
internal const val FISH_STAGE_OPENING = 1
internal const val FISH_STAGE_SERVICE = 2
internal const val FISH_STAGE_OVER = 3

// Les étapes du service d'un client : choisir le poisson, peser, puis le verdict.
internal const val FISH_STEP_PICK = 0
internal const val FISH_STEP_WEIGH = 1
internal const val FISH_STEP_RESULT = 2

// Les verdicts d'une pesée.
internal const val FISH_VERDICT_NONE = 0
internal const val FISH_VERDICT_PERFECT = 1
internal const val FISH_VERDICT_GOOD = 2
internal const val FISH_VERDICT_OFF = 3
internal const val FISH_VERDICT_MISS = 4
internal const val FISH_VERDICT_LEFT = 5 // le client a perdu patience et s'en va

/**
 * Poissonnerie, en deux temps. D'abord l'approvisionnement : des caisses de poisson arrivent sur un tapis, et il faut les ranger
 * avec le bon poisson de l'étalage (flèches pour choisir le poisson, A ou B pour poser la caisse). Ensuite le service : chaque client
 * demande un poisson et un poids ; il faut prendre le bon poisson dans l'étalage, puis arrêter un curseur qui va et vient sur la
 * balance dans la zone du bon poids. Le service puise dans ce qu'on a rangé : un poisson qu'on n'a pas stocké ne sera jamais
 * demandé, et quand il n'y a plus rien à vendre la partie s'arrête. Le score additionne les deux parties.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [FishGame].
 */
internal object Fish {
    /** L'écran est le même que celui de Drive (720 x 405), pour reprendre sa console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    /** Les six poissons de l'étalage : sardine, maquereau, saumon, dorade, thon, crabe (dans cet ordre, voir [FishArt]). */
    const val SPECIES = 6

    /** Durées (millisecondes) de l'approvisionnement, de l'ouverture et du service. */
    const val STOCK_MS = 45_000f
    const val OPENING_MS = 2_600f
    const val SERVICE_MS = 90_000f

    // ---------------------------------------------------------------- l'approvisionnement
    /** Une caisse pèse un kilo ; l'étalage en garde quatre au plus par poisson. */
    const val CRATE_G = 1000
    const val BIN_CAP_G = 4000

    /** Le tapis : les caisses entrent à gauche et tombent à son bout, à droite (position en pixels de l'écran). */
    const val CRATE_START_X = -40f
    const val BELT_END = 650f

    /** Une caisse peut être rangée dès qu'elle est entrée sur l'écran. */
    const val HANDLE_X = 20f

    /** Vitesse du tapis (pixels par seconde), au début puis à la fin de l'approvisionnement. */
    const val BELT_SPEED_START = 95f
    const val BELT_SPEED_END = 165f

    /** Délai entre deux caisses (millisecondes), au début puis à la fin ; la première arrive vite. */
    const val SPAWN_START_MS = 2300f
    const val SPAWN_END_MS = 1150f
    const val FIRST_SPAWN_MS = 600f
    const val MAX_CRATES = 5

    const val POINTS_CRATE = 20
    const val PENALTY_WRONG_BIN = 10
    const val PENALTY_LOST_CRATE = 5

    // ---------------------------------------------------------------- le service
    /** Un client demande entre 200 g et 1,6 kg, par pas de 50 g (et jamais plus que ce qu'il reste du poisson demandé). */
    const val MIN_ORDER_G = 200
    const val MAX_ORDER_G = 1600
    const val ORDER_STEP_G = 50

    /** La balance va de 0 à 2 kg. */
    const val GAUGE_MAX_G = 2000

    /** Patience d'un client (millisecondes), au début puis à la fin du service. */
    const val PATIENCE_START_MS = 17_000f
    const val PATIENCE_END_MS = 10_000f

    /** Un mauvais poisson fait perdre cette part de la patience du client. */
    const val WRONG_FISH_PATIENCE = 0.2f

    /** Vitesse du curseur (parcours complet de la balance par seconde), au début puis à la fin du service. */
    const val GAUGE_SPEED_START = 0.75f
    const val GAUGE_SPEED_END = 1.6f

    /** Tolérance autour du bon poids (grammes) : dans la tolérance, c'est bon ; dans 40 % de la tolérance, c'est parfait ; jusqu'au double, c'est limite. */
    const val TOLERANCE_START_G = 150f
    const val TOLERANCE_END_G = 90f
    const val PERFECT_RATIO = 0.4f
    const val OFF_RATIO = 2f

    const val POINTS_PERFECT = 100
    const val POINTS_GOOD = 60
    const val POINTS_OFF = 20
    const val PENALTY_MISS = 10
    const val PENALTY_LEFT = 10
    const val PENALTY_WRONG_FISH = 10

    /** Prime de rapidité (au plus) pour une pesée bonne ou parfaite, et prime de série : 10 points par pesée réussie de suite après la première, jusqu'à 50. */
    const val PATIENCE_BONUS = 40
    const val STREAK_BONUS = 10
    const val STREAK_BONUS_CAP = 50

    // ---------------------------------------------------------------- les animations (millisecondes)
    const val PLACE_MS = 350f
    const val BLOCKED_MS = 300f
    const val ARRIVE_MS = 500f
    const val RESULT_MS = 1000f

    /** La partie se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f
}

/** Une caisse sur le tapis : son poisson et sa position horizontale sur l'écran. */
internal class FishCrate(val species: Int, var x: Float)

/**
 * Une partie de Poissonnerie. [tick] fait avancer le temps, [press] reçoit les touches ([FISH_LEFT] à [FISH_B]).
 */
internal class FishGame(private val random: Random = Random.Default) {
    var stage = FISH_STAGE_STOCK
        private set

    /** Le temps qui reste dans l'étape en cours (millisecondes). */
    var stageLeftMs = Fish.STOCK_MS
        private set

    /** L'horloge de la partie, pour les animations (millisecondes). */
    var clockMs = 0f
        private set

    /** Le stock de chaque poisson, en grammes. */
    val stock = IntArray(Fish.SPECIES)

    /** Le poisson choisi sur l'étalage (0 à 5). */
    var cursor = 0
        private set

    /** Les caisses sur le tapis. */
    val crates = ArrayList<FishCrate>()
    private var spawnInMs = 0f

    var stockPoints = 0
        private set
    var servicePoints = 0
        private set

    /** Le score : les points de l'approvisionnement et du service, jamais en dessous de zéro. */
    val score: Int get() = max(0, stockPoints + servicePoints)

    // Les compteurs de la partie.
    var stocked = 0
        private set
    var misplaced = 0
        private set
    var lostCrates = 0
        private set
    var served = 0
        private set
    var perfects = 0
        private set
    var walkouts = 0
        private set
    var wrongFish = 0
        private set
    var streak = 0
        private set
    var bestStreak = 0
        private set

    /** Vrai quand la partie s'est arrêtée faute de poisson à vendre. */
    var soldOut = false
        private set

    // Le client en cours.
    var customerSpecies = -1
        private set
    var customerLook = 0
        private set
    var wantG = 0
        private set
    var patienceMs = 0f
        private set
    var patienceMaxMs = 1f
        private set
    var step = FISH_STEP_PICK
        private set

    /** Temps restant avant que le client soit arrivé au comptoir. */
    var arriveMs = 0f
        private set

    /** Le curseur de la balance : 0 (à gauche) à 1 (à droite). */
    var gaugePos = 0f
        private set
    private var gaugeDir = 1f
    var weighedG = 0
        private set
    var verdict = FISH_VERDICT_NONE
        private set
    var resultMs = 0f
        private set

    /** Les points gagnés (ou perdus) avec le dernier client, pour les afficher. */
    var lastPoints = 0
        private set

    // Les animations : caisse rangée, geste refusé, fin de partie.
    var placeMs = 0f
        private set
    var placedBin = -1
        private set
    var blockedMs = 0f
        private set
    var overMs = 0f
        private set

    init {
        reset()
    }

    /** Une partie neuve. */
    fun reset() {
        stage = FISH_STAGE_STOCK
        stageLeftMs = Fish.STOCK_MS
        clockMs = 0f
        stock.fill(0)
        cursor = 0
        crates.clear()
        spawnInMs = Fish.FIRST_SPAWN_MS
        stockPoints = 0
        servicePoints = 0
        stocked = 0
        misplaced = 0
        lostCrates = 0
        served = 0
        perfects = 0
        walkouts = 0
        wrongFish = 0
        streak = 0
        bestStreak = 0
        soldOut = false
        customerSpecies = -1
        customerLook = 0
        wantG = 0
        patienceMs = 0f
        patienceMaxMs = 1f
        step = FISH_STEP_PICK
        arriveMs = 0f
        gaugePos = 0f
        gaugeDir = 1f
        weighedG = 0
        verdict = FISH_VERDICT_NONE
        resultMs = 0f
        lastPoints = 0
        placeMs = 0f
        placedBin = -1
        blockedMs = 0f
        overMs = 0f
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 (début de l'étape) à 1 (dernière seconde) : elle repart de zéro au début du service. */
    val difficulty: Float
        get() = when (stage) {
            FISH_STAGE_STOCK -> 1f - stageLeftMs / Fish.STOCK_MS
            FISH_STAGE_SERVICE -> 1f - stageLeftMs / Fish.SERVICE_MS
            FISH_STAGE_OPENING -> 0f
            else -> 1f
        }.coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    /** La tolérance actuelle autour du bon poids, en grammes : la balance l'affiche, le verdict l'utilise. */
    val toleranceG: Float get() = lerp(Fish.TOLERANCE_START_G, Fish.TOLERANCE_END_G)

    // ---------------------------------------------------------------- état

    val over: Boolean get() = stage == FISH_STAGE_OVER

    /** Le poids que la balance indique en ce moment, en grammes. */
    val currentWeightG: Int get() = (gaugePos * Fish.GAUGE_MAX_G).roundToInt()

    /** Le niveau du stock d'un poisson : 0 (épuisé) à 1 (étalage plein pour ce poisson). */
    fun level(bin: Int): Float = stock[bin].toFloat() / Fish.BIN_CAP_G

    /** La caisse que le joueur peut ranger : la plus avancée sur le tapis, une fois entrée sur l'écran. */
    fun head(): FishCrate? = crates.filter { it.x >= Fish.HANDLE_X }.maxByOrNull { it.x }

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Fish.RESTART_DELAY_MS

    private fun hasOrderableStock(): Boolean = stock.any { it >= Fish.MIN_ORDER_G }

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        placeMs = max(0f, placeMs - dt)
        blockedMs = max(0f, blockedMs - dt)
        when (stage) {
            FISH_STAGE_STOCK -> tickStock(dt)
            FISH_STAGE_OPENING -> {
                stageLeftMs -= dt
                if (stageLeftMs <= 0f) startService()
            }
            FISH_STAGE_SERVICE -> tickService(dt)
            else -> overMs += dt
        }
    }

    private fun tickStock(dt: Float) {
        stageLeftMs -= dt
        if (stageLeftMs <= 0f) {
            endStock()
            return
        }
        val speed = lerp(Fish.BELT_SPEED_START, Fish.BELT_SPEED_END)
        var i = 0
        while (i < crates.size) {
            val crate = crates[i]
            crate.x += speed * dt / 1000f
            if (crate.x >= Fish.BELT_END) {
                // La caisse tombe au bout du tapis : elle est perdue.
                crates.removeAt(i)
                lostCrates++
                stockPoints -= Fish.PENALTY_LOST_CRATE
            } else {
                i++
            }
        }
        spawnInMs -= dt
        if (spawnInMs <= 0f) {
            if (crates.size < Fish.MAX_CRATES) crates.add(FishCrate(random.nextInt(Fish.SPECIES), Fish.CRATE_START_X))
            spawnInMs = lerp(Fish.SPAWN_START_MS, Fish.SPAWN_END_MS)
        }
    }

    /** Fin de l'approvisionnement : le magasin ouvre, sauf s'il n'y a rien à vendre. */
    private fun endStock() {
        crates.clear()
        placedBin = -1
        if (!hasOrderableStock()) {
            finish(soldOut = true)
            return
        }
        stage = FISH_STAGE_OPENING
        stageLeftMs = Fish.OPENING_MS
    }

    private fun startService() {
        stage = FISH_STAGE_SERVICE
        stageLeftMs = Fish.SERVICE_MS
        nextCustomer()
    }

    private fun tickService(dt: Float) {
        stageLeftMs -= dt
        if (stageLeftMs <= 0f) {
            stageLeftMs = 0f
            finish(soldOut = false)
            return
        }
        arriveMs = max(0f, arriveMs - dt)
        when (step) {
            FISH_STEP_PICK -> drainPatience(dt)
            FISH_STEP_WEIGH -> {
                drainPatience(dt)
                if (step == FISH_STEP_WEIGH) moveGauge(dt)
            }
            else -> {
                resultMs -= dt
                if (resultMs <= 0f) nextCustomer()
            }
        }
    }

    private fun drainPatience(dt: Float) {
        patienceMs -= dt
        if (patienceMs <= 0f) {
            patienceMs = 0f
            customerLeaves()
        }
    }

    /** Le client perd patience : il s'en va sans rien acheter (le poisson déjà pris retourne sur l'étalage). */
    private fun customerLeaves() {
        walkouts++
        streak = 0
        verdict = FISH_VERDICT_LEFT
        lastPoints = -Fish.PENALTY_LEFT
        servicePoints += lastPoints
        step = FISH_STEP_RESULT
        resultMs = Fish.RESULT_MS
    }

    private fun moveGauge(dt: Float) {
        val speed = lerp(Fish.GAUGE_SPEED_START, Fish.GAUGE_SPEED_END)
        var p = gaugePos + gaugeDir * speed * dt / 1000f
        if (p >= 1f) {
            p = 1f - (p - 1f)
            gaugeDir = -1f
        } else if (p <= 0f) {
            p = -p
            gaugeDir = 1f
        }
        gaugePos = p.coerceIn(0f, 1f)
    }

    /** Un nouveau client : il demande un poisson dont il reste assez en stock, et un poids que le stock permet. */
    private fun nextCustomer() {
        val options = (0 until Fish.SPECIES).filter { stock[it] >= Fish.MIN_ORDER_G }
        if (options.isEmpty()) {
            finish(soldOut = true)
            return
        }
        val species = options[random.nextInt(options.size)]
        val cap = min(Fish.MAX_ORDER_G, stock[species] / Fish.ORDER_STEP_G * Fish.ORDER_STEP_G)
        val steps = (cap - Fish.MIN_ORDER_G) / Fish.ORDER_STEP_G
        wantG = Fish.MIN_ORDER_G + random.nextInt(steps + 1) * Fish.ORDER_STEP_G
        customerSpecies = species
        customerLook = random.nextInt(4)
        patienceMaxMs = lerp(Fish.PATIENCE_START_MS, Fish.PATIENCE_END_MS)
        patienceMs = patienceMaxMs
        step = FISH_STEP_PICK
        arriveMs = Fish.ARRIVE_MS
        verdict = FISH_VERDICT_NONE
        gaugePos = 0f
        gaugeDir = 1f
        weighedG = 0
        lastPoints = 0
    }

    private fun finish(soldOut: Boolean) {
        this.soldOut = soldOut
        stage = FISH_STAGE_OVER
        overMs = 0f
        crates.clear()
    }

    // ---------------------------------------------------------------- joueur

    /** Une touche ([FISH_LEFT] à [FISH_B]) vient d'être pressée. */
    fun press(key: Int) {
        when (stage) {
            FISH_STAGE_STOCK -> when (key) {
                FISH_LEFT -> moveCursor(-1)
                FISH_RIGHT -> moveCursor(1)
                FISH_A, FISH_B -> placeCrate()
            }
            FISH_STAGE_SERVICE -> when (key) {
                FISH_LEFT -> if (step == FISH_STEP_PICK) moveCursor(-1)
                FISH_RIGHT -> if (step == FISH_STEP_PICK) moveCursor(1)
                FISH_A, FISH_B -> when (step) {
                    FISH_STEP_PICK -> takeFish()
                    FISH_STEP_WEIGH -> stopGauge()
                    else -> {}
                }
            }
            else -> {}
        }
    }

    private fun moveCursor(direction: Int) {
        cursor = (cursor + direction + Fish.SPECIES) % Fish.SPECIES
    }

    /** Range la caisse la plus avancée avec le poisson choisi : il faut que ce soit le même poisson, et qu'il en reste de la place. */
    private fun placeCrate() {
        val crate = head() ?: return
        if (crate.species != cursor) {
            misplaced++
            stockPoints -= Fish.PENALTY_WRONG_BIN
            blockedMs = Fish.BLOCKED_MS
            return
        }
        if (stock[cursor] + Fish.CRATE_G > Fish.BIN_CAP_G) {
            // Plus de place pour ce poisson : la caisse reste sur le tapis.
            blockedMs = Fish.BLOCKED_MS
            return
        }
        stock[cursor] += Fish.CRATE_G
        crates.remove(crate)
        stocked++
        stockPoints += Fish.POINTS_CRATE
        placeMs = Fish.PLACE_MS
        placedBin = cursor
    }

    /** Prend le poisson choisi sur l'étalage : s'il n'est pas le bon, le client perd patience ; sinon la pesée commence. */
    private fun takeFish() {
        if (stock[cursor] <= 0) {
            blockedMs = Fish.BLOCKED_MS
            return
        }
        if (cursor != customerSpecies) {
            wrongFish++
            streak = 0
            servicePoints -= Fish.PENALTY_WRONG_FISH
            blockedMs = Fish.BLOCKED_MS
            patienceMs = max(0f, patienceMs - patienceMaxMs * Fish.WRONG_FISH_PATIENCE)
            if (patienceMs <= 0f) customerLeaves()
            return
        }
        step = FISH_STEP_WEIGH
        gaugePos = random.nextFloat()
        gaugeDir = if (random.nextBoolean()) 1f else -1f
    }

    /** Arrête le curseur : le verdict dépend de l'écart entre le poids pesé et le poids demandé. */
    private fun stopGauge() {
        weighedG = currentWeightG
        val tolerance = toleranceG
        val gap = abs(weighedG - wantG)
        verdict = when {
            gap <= tolerance * Fish.PERFECT_RATIO -> FISH_VERDICT_PERFECT
            gap <= tolerance -> FISH_VERDICT_GOOD
            gap <= tolerance * Fish.OFF_RATIO -> FISH_VERDICT_OFF
            else -> FISH_VERDICT_MISS
        }
        // Le poisson pesé quitte l'étalage, que le client soit content ou non.
        stock[customerSpecies] = max(0, stock[customerSpecies] - weighedG)
        var gained = when (verdict) {
            FISH_VERDICT_PERFECT -> Fish.POINTS_PERFECT
            FISH_VERDICT_GOOD -> Fish.POINTS_GOOD
            FISH_VERDICT_OFF -> Fish.POINTS_OFF
            else -> -Fish.PENALTY_MISS
        }
        if (verdict == FISH_VERDICT_PERFECT || verdict == FISH_VERDICT_GOOD) {
            streak++
            bestStreak = max(bestStreak, streak)
            served++
            if (verdict == FISH_VERDICT_PERFECT) perfects++
            gained += (patienceMs / patienceMaxMs * Fish.PATIENCE_BONUS).roundToInt()
            gained += min(Fish.STREAK_BONUS_CAP, (streak - 1) * Fish.STREAK_BONUS)
        } else {
            streak = 0
            if (verdict == FISH_VERDICT_OFF) served++ else walkouts++
        }
        lastPoints = gained
        servicePoints += gained
        step = FISH_STEP_RESULT
        resultMs = Fish.RESULT_MS
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        put(SAVE_VERSION)
        put(stage); put(stageLeftMs); put(clockMs); put(cursor); put(spawnInMs)
        put(stockPoints); put(servicePoints); put(stocked); put(misplaced); put(lostCrates)
        put(served); put(perfects); put(walkouts); put(wrongFish); put(streak); put(bestStreak); put(soldOut)
        put(customerSpecies); put(customerLook); put(wantG); put(patienceMs); put(patienceMaxMs)
        put(step); put(arriveMs); put(gaugePos); put(gaugeDir); put(weighedG); put(verdict); put(resultMs); put(lastPoints)
        put(placeMs); put(placedBin); put(blockedMs); put(overMs)
        for (grams in stock) put(grams)
        put(crates.size)
        for (crate in crates) {
            put(crate.species)
            put(crate.x)
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
        stage = int().coerceIn(FISH_STAGE_STOCK, FISH_STAGE_OVER); stageLeftMs = float(); clockMs = float()
        cursor = int().coerceIn(0, Fish.SPECIES - 1); spawnInMs = float()
        stockPoints = int(); servicePoints = int(); stocked = int(); misplaced = int(); lostCrates = int()
        served = int(); perfects = int(); walkouts = int(); wrongFish = int(); streak = int(); bestStreak = int(); soldOut = flag()
        customerSpecies = int().coerceIn(-1, Fish.SPECIES - 1); customerLook = int().coerceIn(0, 3); wantG = int()
        patienceMs = float(); patienceMaxMs = max(1f, float())
        step = int().coerceIn(FISH_STEP_PICK, FISH_STEP_RESULT); arriveMs = float(); gaugePos = float().coerceIn(0f, 1f)
        gaugeDir = if (float() >= 0f) 1f else -1f
        weighedG = int(); verdict = int().coerceIn(FISH_VERDICT_NONE, FISH_VERDICT_LEFT); resultMs = float(); lastPoints = int()
        placeMs = float(); placedBin = int().coerceIn(-1, Fish.SPECIES - 1); blockedMs = float(); overMs = float()
        for (bin in 0 until Fish.SPECIES) stock[bin] = int().coerceIn(0, Fish.BIN_CAP_G)
        crates.clear()
        val count = int().coerceIn(0, Fish.MAX_CRATES)
        repeat(count) {
            val species = int().coerceIn(0, Fish.SPECIES - 1)
            crates.add(FishCrate(species, float()))
        }
    }

    companion object {
        private const val SAVE_VERSION = 1

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): FishGame {
            val game = FishGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
