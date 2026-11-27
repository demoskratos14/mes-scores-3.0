package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Identifiant du jeu « Boucherie » (une journée de boucher : découper, servir, rester propre) dans la liste des jeux. */
const val BUTCHER_ID = "builtin_butcher"

// Les quatre touches du jeu : la croix (gauche, droite) et les boutons A et B.
internal const val BUTCHER_LEFT = 0
internal const val BUTCHER_RIGHT = 1
internal const val BUTCHER_A = 2
internal const val BUTCHER_B = 3

// Les étapes d'une journée : la découpe du matin, une courte ouverture, le comptoir, le bonus saucisses, la fin.
internal const val BUTCHER_STAGE_CUT = 0
internal const val BUTCHER_STAGE_OPENING = 1
internal const val BUTCHER_STAGE_SERVICE = 2
internal const val BUTCHER_STAGE_BONUS = 3
internal const val BUTCHER_STAGE_OVER = 4

// Les étapes du service d'un client : choisir la pièce, trancher, puis le verdict.
internal const val BUTCHER_STEP_PICK = 0
internal const val BUTCHER_STEP_SLICE = 1
internal const val BUTCHER_STEP_RESULT = 2

// Les verdicts (d'une tranche, d'une coupe, d'une saucisse). Plus le nombre est grand, plus c'est mauvais.
internal const val BUTCHER_VERDICT_NONE = 0
internal const val BUTCHER_VERDICT_PERFECT = 1
internal const val BUTCHER_VERDICT_GOOD = 2
internal const val BUTCHER_VERDICT_OFF = 3
internal const val BUTCHER_VERDICT_MISS = 4
internal const val BUTCHER_VERDICT_LEFT = 5 // le client a perdu patience et s'en va

// Les quatre viandes (dans cet ordre, voir [ButcherArt]).
internal const val BUTCHER_BEEF = 0
internal const val BUTCHER_PORK = 1
internal const val BUTCHER_LAMB = 2
internal const val BUTCHER_POULTRY = 3

// Pourquoi un geste est refusé (le message affiché).
internal const val BUTCHER_BLOCK_NONE = 0
internal const val BUTCHER_BLOCK_WRONG = 1 // mauvaise viande, case vide, ou couteau dans l'os
internal const val BUTCHER_BLOCK_SMALL = 2 // la pièce est trop petite pour la commande
internal const val BUTCHER_BLOCK_BUSY = 3 // le plan est en cours de nettoyage

// Les raisons de la fin de la journée.
internal const val BUTCHER_END_CLOSING = 0 // l'heure de la fermeture
internal const val BUTCHER_END_SOLDOUT = 1 // plus rien à vendre
internal const val BUTCHER_END_HYGIENE = 2 // fermeture sanitaire : trois fautes d'hygiène

// L'inspecteur sanitaire.
internal const val BUTCHER_INSPECT_NONE = 0
internal const val BUTCHER_INSPECT_WARN = 1

/**
 * Boucherie, une journée de boucher en deux temps (et un bonus). Le matin, trois carcasses se succèdent : il faut déplacer le
 * couteau (flèches) et couper sur les pointillés (A) ; chaque coupe nette donne une pièce, rangée dans la chambre froide.
 * Ensuite le comptoir : chaque client demande une viande, un nombre de tranches et un poids par tranche. On choisit une pièce
 * dans la chambre froide (flèches, A), puis on tient A pour avancer la viande contre la lame de la trancheuse et on relâche
 * au bon poids. Les pièces vieillissent : celles qui deviennent rouges sont jetées, donc il faut vendre les plus anciennes
 * d'abord. La volaille salit le plan : avant de toucher une autre viande, il faut le nettoyer (B), sinon c'est une faute
 * d'hygiène ; l'inspecteur sanitaire passe aussi à l'improviste. Trois fautes, et la boucherie ferme. Si le score atteint
 * [Butcher.BONUS_SCORE] avant la fin de la journée, un atelier de saucisses s'ouvre entre deux clients : A remplit le boyau,
 * B ficelle à la bonne longueur.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [ButcherGame].
 */
internal object Butcher {
    /** L'écran est le même que celui de Drive et de Poissonnerie (720 x 405), pour reprendre leur console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    const val MEATS = 4

    /** Durées (millisecondes) de la découpe, de l'ouverture et du service. */
    const val CUT_MS = 50_000f
    const val OPENING_MS = 2_600f
    const val SERVICE_MS = 90_000f

    // ---------------------------------------------------------------- la découpe
    const val CARCASSES = 3
    const val CUTS_PER_CARCASS = 4

    /** La chambre froide garde une pièce par coupe : tout ce qu'on découpe y entre. */
    const val SLOTS = CARCASSES * CUTS_PER_CARCASS

    /** La carcasse occupe cette largeur de l'écran ; ses pointillés s'écartent d'au plus [LINE_JITTER] de leur place régulière. */
    const val CARCASS_X0 = 100f
    const val CARCASS_X1 = 620f
    const val LINE_JITTER = 20f

    /** Le couteau : sa place au départ, ses limites et sa vitesse (pixels de l'écran par seconde). */
    const val KNIFE_START = 360f
    const val KNIFE_MIN = 70f
    const val KNIFE_MAX = 650f
    const val KNIFE_SPEED = 290f

    /** La carcasse se balance (pixels), de plus en plus fort ; une période d'environ 1,6 s. */
    const val SWAY_MAX = 24f
    const val SWAY_RATE_MS = 260f

    /** Tolérance autour d'un pointillé (pixels) : dans la tolérance, la coupe est bonne ; dans 40 % de la tolérance, elle est parfaite. */
    const val CUT_TOL_START = 24f
    const val CUT_TOL_END = 15f
    const val PERFECT_RATIO = 0.4f
    const val OFF_RATIO = 2f

    const val POINTS_CUT_PERFECT = 15
    const val POINTS_CUT_GOOD = 10
    const val PENALTY_BONE = 8

    /** Reste de temps en fin de découpe (si les trois carcasses sont faites) : points par seconde. */
    const val EARLY_BONUS_PER_S = 2

    // ---------------------------------------------------------------- les pièces
    /** Le poids d'une pièce de chaque viande, en grammes (bœuf, porc, agneau, volaille). */
    fun pieceWeightG(meat: Int): Int = when (meat) {
        BUTCHER_BEEF -> 1200
        BUTCHER_PORK -> 1000
        BUTCHER_LAMB -> 900
        else -> 800
    }

    /** La fraîcheur perdue par milliseconde : de 1 (neuve) à 0 (à jeter). La volaille vieillit le plus vite. */
    fun freshLossPerMs(meat: Int): Float = when (meat) {
        BUTCHER_BEEF -> 1f / 150_000f
        BUTCHER_PORK -> 1f / 120_000f
        BUTCHER_LAMB -> 1f / 100_000f
        else -> 1f / 80_000f
    }

    /** Une pièce vendue avant cette fraîcheur est « sauvée » : prime anti-gaspillage. */
    const val SAVE_FRESH = 0.4f
    const val POINTS_SAVED = 12
    const val PENALTY_SPOILED = 10

    // ---------------------------------------------------------------- le service
    /** Un client demande de une à trois tranches, de 100 g à 400 g chacune, par pas de 25 g. */
    const val MAX_SLICES = 3
    const val MIN_SLICE_G = 100
    const val MAX_SLICE_G = 400
    const val SLICE_STEP_G = 25

    /** La trancheuse : la viande avance tant qu'on tient A (grammes par seconde, au début puis à la fin du service). */
    const val SLICE_SPEED_START = 150f
    const val SLICE_SPEED_END = 230f
    const val SLICE_MAX_G = 600f

    /** Une tranche plus légère que cela n'est pas coupée au relâchement (un appui trop court). */
    const val SLICE_MIN_HOLD_G = 15f

    /** Tolérance autour du bon poids (grammes), comme à la balance de la Poissonnerie. */
    const val SLICE_TOL_START_G = 28f
    const val SLICE_TOL_END_G = 20f

    /** Patience d'un client (millisecondes), au début puis à la fin du service. */
    const val PATIENCE_START_MS = 19_000f
    const val PATIENCE_END_MS = 12_000f
    const val WRONG_MEAT_PATIENCE = 0.2f

    const val POINTS_SLICE_PERFECT = 35
    const val POINTS_SLICE_GOOD = 22
    const val POINTS_SLICE_OFF = 8
    const val PENALTY_SLICE_MISS = 8
    const val PENALTY_WRONG = 10
    const val PENALTY_LEFT = 10

    /** Pour un client servi (toutes ses tranches bonnes) : prime de rapidité (au plus) et prime de série. */
    const val PATIENCE_BONUS = 30
    const val STREAK_BONUS = 10
    const val STREAK_BONUS_CAP = 50

    // ---------------------------------------------------------------- l'hygiène
    const val CLEAN_MS = 1_100f
    const val MAX_FAULTS = 3
    const val PENALTY_FAULT = 20

    /** L'inspecteur : un avertissement de 3,2 s, puis le contrôle ; une visite toutes les 30 à 40 s environ. */
    const val INSPECT_FIRST_MIN_MS = 25_000f
    const val INSPECT_FIRST_MAX_MS = 35_000f
    const val INSPECT_NEXT_MIN_MS = 30_000f
    const val INSPECT_NEXT_MAX_MS = 40_000f
    const val INSPECT_WARN_MS = 3_200f
    const val INSPECT_MIN_LEFT_MS = 6_000f
    const val POINTS_INSPECT_OK = 30
    const val PENALTY_INSPECT = 40

    // ---------------------------------------------------------------- le bonus saucisses
    /** Le score à atteindre pendant le service pour ouvrir l'atelier, s'il reste au moins [BONUS_MIN_LEFT_MS] de service. */
    const val BONUS_SCORE = 500
    const val BONUS_MIN_LEFT_MS = 12_000f
    const val BONUS_INTRO_MS = 1_600f
    const val BONUS_MS = 18_000f

    /** Une saucisse : longueur voulue (millimètres) entre 40 et 90 par pas de 5 ; le boyau se remplit à [FILL_SPEED] mm/s. */
    const val FILL_SPEED = 60f
    const val TARGET_MIN_MM = 40
    const val TARGET_MAX_MM = 90
    const val TARGET_STEP_MM = 5
    const val BONUS_TOL_MM = 9f
    const val BONUS_PERFECT_RATIO = 0.5f
    const val BURST_OVER_MM = 26f
    const val MIN_LINK_MM = 6f
    const val POINTS_LINK_PERFECT = 30
    const val POINTS_LINK_GOOD = 15
    const val PENALTY_LINK_BAD = 5
    const val PENALTY_BURST = 10
    const val COMBO_BONUS = 5
    const val COMBO_BONUS_CAP = 25

    // ---------------------------------------------------------------- les animations (millisecondes)
    const val BLOCKED_MS = 300f
    const val STUCK_MS = 400f
    const val ARRIVE_MS = 500f
    const val RESULT_MS = 900f
    const val SLICE_FLASH_MS = 450f
    const val CUT_FLASH_MS = 450f
    const val CARCASS_ARRIVE_MS = 500f
    const val CARCASS_LEAVE_MS = 500f
    const val FAULT_MS = 900f
    const val INSPECT_SHOW_MS = 1_400f
    const val BURST_MS = 600f
    const val LINK_FLASH_MS = 500f
    const val SPOIL_MS = 700f

    /** Une flèche tenue répète le pas dans la chambre froide : première répétition après 260 ms, puis toutes les 140 ms. */
    const val REPEAT_FIRST_MS = 260f
    const val REPEAT_MS = 140f

    /** La partie se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f
}

/** Une pièce de viande dans la chambre froide : sa viande, son poids restant (grammes) et sa fraîcheur (1 neuve, 0 à jeter). */
internal class ButcherPiece(val meat: Int, var weightG: Int, var fresh: Float)

/**
 * Une partie de Boucherie. [tick] fait avancer le temps, [press] et [release] reçoivent les touches ([BUTCHER_LEFT] à
 * [BUTCHER_B]) : certaines se tiennent (les flèches pour le couteau, A pour la trancheuse et le boyau).
 */
internal class ButcherGame(private val random: Random = Random.Default) {
    var stage = BUTCHER_STAGE_CUT
        private set

    /** Le temps qui reste dans l'étape en cours (millisecondes) ; pendant le bonus, celui du service est suspendu. */
    var stageLeftMs = Butcher.CUT_MS
        private set

    /** L'horloge de la partie, pour les animations (millisecondes). */
    var clockMs = 0f
        private set

    var points = 0
        private set

    /** Le score : les points de la journée, jamais en dessous de zéro. */
    val score: Int get() = max(0, points)

    // Les compteurs de la partie.
    var cuts = 0
        private set
    var perfectCuts = 0
        private set
    var boneHits = 0
        private set
    var served = 0
        private set
    var perfects = 0
        private set
    var walkouts = 0
        private set
    var wrongMeat = 0
        private set
    var spoiled = 0
        private set
    var saved = 0
        private set
    var faults = 0
        private set
    var streak = 0
        private set
    var bestStreak = 0
        private set
    var inspections = 0
        private set
    var inspectionsPassed = 0
        private set
    var sausages = 0
        private set
    var bursts = 0
        private set

    /** Pourquoi la partie s'est terminée (voir BUTCHER_END_*). */
    var endReason = BUTCHER_END_CLOSING
        private set

    // Le couteau et la carcasse.
    var knifeX = Butcher.KNIFE_START
        private set
    private var heldLeft = false
    private var heldRight = false
    private var repeatInMs = 0f
    var stuckMs = 0f
        private set
    var cutFlashMs = 0f
        private set
    var cutFlashX = 0f
        private set
    var cutFlashVerdict = BUTCHER_VERDICT_NONE
        private set
    var carcassMeat = BUTCHER_BEEF
        private set
    var carcassNumber = 0
        private set
    var carcassesDone = 0
        private set
    var carcassArriveMs = 0f
        private set
    var carcassLeaveMs = 0f
        private set
    val carcassLines = FloatArray(Butcher.CUTS_PER_CARCASS)
    val carcassCut = BooleanArray(Butcher.CUTS_PER_CARCASS)

    /** La chambre froide : une case par pièce (vide quand la pièce est vendue ou jetée). */
    val pieces = arrayOfNulls<ButcherPiece>(Butcher.SLOTS)

    /** La case choisie dans la chambre froide pendant le service. */
    var cursor = 0
        private set

    // L'hygiène : le plan est sale après la volaille ; il se nettoie avec B.
    var dirty = false
        private set
    var cleanMs = 0f
        private set
    var faultMs = 0f
        private set

    // Le client en cours.
    var customerMeat = -1
        private set
    var customerLook = 0
        private set
    var slicesTotal = 0
        private set
    var slicesLeft = 0
        private set
    var sliceWantG = 0
        private set
    var patienceMs = 0f
        private set
    var patienceMaxMs = 1f
        private set
    var step = BUTCHER_STEP_PICK
        private set

    /** Temps restant avant que le client soit arrivé au comptoir. */
    var arriveMs = 0f
        private set

    /** La case de la pièce en train d'être tranchée (-1 si aucune), et sa fraîcheur quand on l'a prise. */
    var pickedSlot = -1
        private set
    private var pickedFresh = 1f

    /** Le poids que la trancheuse a déjà coupé de la tranche en cours (grammes), et vrai tant qu'on tient A. */
    var sliceG = 0f
        private set
    var holdA = false
        private set
    private var holdMs = 0f
    var sliceFlashMs = 0f
        private set
    var sliceVerdict = BUTCHER_VERDICT_NONE
        private set
    var weighedG = 0
        private set
    private var worstVerdict = BUTCHER_VERDICT_NONE
    var verdict = BUTCHER_VERDICT_NONE
        private set
    var resultMs = 0f
        private set

    /** Les points gagnés (ou perdus) avec le dernier client, pour les afficher. */
    var lastPoints = 0
        private set

    // Les animations : geste refusé, pièce jetée, fin de partie.
    var blockedMs = 0f
        private set
    var blockedKind = BUTCHER_BLOCK_NONE
        private set
    var spoilMs = 0f
        private set
    var spoilSlot = -1
        private set
    var overMs = 0f
        private set

    // L'inspecteur sanitaire.
    var inspectorState = BUTCHER_INSPECT_NONE
        private set
    private var inspectorInMs = 0f
    var inspectorWarnMs = 0f
        private set

    /** Le résultat du dernier contrôle : 1 conforme, -1 amende, 0 aucun ; affiché pendant [inspectShowMs]. */
    var inspectResult = 0
        private set
    var inspectShowMs = 0f
        private set

    // Le bonus saucisses.
    var bonusDone = false
        private set
    var bonusLeftMs = 0f
        private set
    var bonusIntroMs = 0f
        private set

    /** La saucisse en cours : sa longueur (mm) et la longueur voulue. */
    var linkLen = 0f
        private set
    var linkTarget = 60
        private set
    var combo = 0
        private set
    var linkFlashMs = 0f
        private set
    var linkFlashVerdict = BUTCHER_VERDICT_NONE
        private set
    var burstMs = 0f
        private set

    /** Les dernières saucisses finies (longueur en mm et verdict), de la plus ancienne à la plus récente : le décor seulement. */
    val chainLen = ArrayList<Float>()
    val chainVerdict = ArrayList<Int>()

    init {
        reset()
    }

    /** Une partie neuve. */
    fun reset() {
        stage = BUTCHER_STAGE_CUT
        stageLeftMs = Butcher.CUT_MS
        clockMs = 0f
        points = 0
        cuts = 0; perfectCuts = 0; boneHits = 0; served = 0; perfects = 0; walkouts = 0; wrongMeat = 0
        spoiled = 0; saved = 0; faults = 0; streak = 0; bestStreak = 0; inspections = 0; inspectionsPassed = 0
        sausages = 0; bursts = 0
        endReason = BUTCHER_END_CLOSING
        knifeX = Butcher.KNIFE_START
        heldLeft = false; heldRight = false; repeatInMs = 0f
        stuckMs = 0f; cutFlashMs = 0f; cutFlashX = 0f; cutFlashVerdict = BUTCHER_VERDICT_NONE
        carcassMeat = BUTCHER_BEEF
        carcassNumber = 0
        carcassesDone = 0
        carcassArriveMs = 0f
        carcassLeaveMs = 0f
        carcassLines.fill(0f)
        carcassCut.fill(false)
        pieces.fill(null)
        cursor = 0
        dirty = false; cleanMs = 0f; faultMs = 0f
        customerMeat = -1; customerLook = 0; slicesTotal = 0; slicesLeft = 0; sliceWantG = 0
        patienceMs = 0f; patienceMaxMs = 1f
        step = BUTCHER_STEP_PICK
        arriveMs = 0f
        pickedSlot = -1; pickedFresh = 1f
        sliceG = 0f; holdA = false; holdMs = 0f; sliceFlashMs = 0f; sliceVerdict = BUTCHER_VERDICT_NONE; weighedG = 0
        worstVerdict = BUTCHER_VERDICT_NONE
        verdict = BUTCHER_VERDICT_NONE
        resultMs = 0f
        lastPoints = 0
        blockedMs = 0f; blockedKind = BUTCHER_BLOCK_NONE; spoilMs = 0f; spoilSlot = -1; overMs = 0f
        inspectorState = BUTCHER_INSPECT_NONE
        inspectorInMs = 0f
        inspectorWarnMs = 0f
        inspectResult = 0
        inspectShowMs = 0f
        bonusDone = false; bonusLeftMs = 0f; bonusIntroMs = 0f
        linkLen = 0f; linkTarget = 60; combo = 0; linkFlashMs = 0f; linkFlashVerdict = BUTCHER_VERDICT_NONE; burstMs = 0f
        chainLen.clear()
        chainVerdict.clear()
        newCarcass()
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 (début de l'étape) à 1 (dernière seconde) : elle repart de zéro au début du service. */
    val difficulty: Float
        get() = when (stage) {
            BUTCHER_STAGE_CUT -> 1f - stageLeftMs / Butcher.CUT_MS
            BUTCHER_STAGE_SERVICE, BUTCHER_STAGE_BONUS -> 1f - stageLeftMs / Butcher.SERVICE_MS
            BUTCHER_STAGE_OPENING -> 0f
            else -> 1f
        }.coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    /** La tolérance actuelle autour d'un pointillé, en pixels de l'écran. */
    val cutTolerance: Float get() = lerp(Butcher.CUT_TOL_START, Butcher.CUT_TOL_END)

    /** La tolérance actuelle autour du bon poids, en grammes. */
    val sliceTolG: Float get() = lerp(Butcher.SLICE_TOL_START_G, Butcher.SLICE_TOL_END_G)

    // ---------------------------------------------------------------- état

    val over: Boolean get() = stage == BUTCHER_STAGE_OVER

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Butcher.RESTART_DELAY_MS

    /** Le temps à afficher : celui de l'étape, ou celui du bonus pendant l'atelier. */
    val displayLeftMs: Float
        get() = when (stage) {
            BUTCHER_STAGE_OPENING -> Butcher.SERVICE_MS
            BUTCHER_STAGE_BONUS -> bonusLeftMs
            BUTCHER_STAGE_OVER -> 0f
            else -> stageLeftMs
        }

    /** Le balancement actuel de la carcasse, en pixels (il est nul hors de la découpe). */
    val sway: Float
        get() = if (stage == BUTCHER_STAGE_CUT) sin(clockMs / Butcher.SWAY_RATE_MS) * lerp(0f, Butcher.SWAY_MAX) else 0f

    /** La place d'un pointillé de la carcasse sur l'écran, balancement compris. */
    fun lineX(index: Int): Float = carcassLines[index] + sway

    /** Le pointillé encore à couper le plus proche du couteau, ou -1 s'il n'en reste pas. */
    fun nearestLine(): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until Butcher.CUTS_PER_CARCASS) {
            if (carcassCut[i]) continue
            val d = abs(knifeX - lineX(i))
            if (d < bestDistance) {
                bestDistance = d
                best = i
            }
        }
        return best
    }

    /** Le nombre de pièces dans la chambre froide. */
    val pieceCount: Int get() = pieces.count { it != null }

    /** Le poids que la trancheuse indique pour la tranche en cours, en grammes. */
    val currentWeightG: Int get() = sliceG.roundToInt()

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        blockedMs = max(0f, blockedMs - dt)
        stuckMs = max(0f, stuckMs - dt)
        cutFlashMs = max(0f, cutFlashMs - dt)
        sliceFlashMs = max(0f, sliceFlashMs - dt)
        faultMs = max(0f, faultMs - dt)
        inspectShowMs = max(0f, inspectShowMs - dt)
        burstMs = max(0f, burstMs - dt)
        linkFlashMs = max(0f, linkFlashMs - dt)
        spoilMs = max(0f, spoilMs - dt)
        if (cleanMs > 0f) {
            cleanMs -= dt
            if (cleanMs <= 0f) {
                cleanMs = 0f
                dirty = false
            }
        }
        when (stage) {
            BUTCHER_STAGE_CUT -> {
                decay(dt)
                tickCut(dt)
            }
            BUTCHER_STAGE_OPENING -> {
                decay(dt)
                stageLeftMs -= dt
                if (stageLeftMs <= 0f) startService()
            }
            BUTCHER_STAGE_SERVICE -> {
                decay(dt)
                tickService(dt)
            }
            BUTCHER_STAGE_BONUS -> tickBonus(dt)
            else -> overMs += dt
        }
    }

    /** Les pièces vieillissent ; une pièce à zéro est jetée. La pièce en cours de tranchage ne vieillit pas. */
    private fun decay(dt: Float) {
        for (i in 0 until Butcher.SLOTS) {
            val piece = pieces[i] ?: continue
            if (stage == BUTCHER_STAGE_SERVICE && step == BUTCHER_STEP_SLICE && i == pickedSlot) continue
            piece.fresh -= Butcher.freshLossPerMs(piece.meat) * dt
            if (piece.fresh <= 0f) {
                pieces[i] = null
                spoiled++
                points -= Butcher.PENALTY_SPOILED
                spoilSlot = i
                spoilMs = Butcher.SPOIL_MS
            }
        }
    }

    // ---------------------------------------------------------------- la découpe

    private fun tickCut(dt: Float) {
        val direction = (if (heldRight) 1 else 0) - (if (heldLeft) 1 else 0)
        if (direction != 0 && stuckMs <= 0f) {
            knifeX = (knifeX + direction * Butcher.KNIFE_SPEED * dt / 1000f).coerceIn(Butcher.KNIFE_MIN, Butcher.KNIFE_MAX)
        }
        stageLeftMs -= dt
        if (stageLeftMs <= 0f) {
            stageLeftMs = 0f
            endCut()
            return
        }
        if (carcassLeaveMs > 0f) {
            carcassLeaveMs -= dt
            if (carcassLeaveMs <= 0f) {
                carcassLeaveMs = 0f
                if (carcassesDone >= Butcher.CARCASSES) endCut() else newCarcass()
            }
            return
        }
        carcassArriveMs = max(0f, carcassArriveMs - dt)
    }

    /** Une nouvelle carcasse : une viande différente de la précédente, quatre pointillés à peu près régulièrement espacés. */
    private fun newCarcass() {
        carcassNumber++
        var meat = random.nextInt(Butcher.MEATS)
        if (carcassNumber > 1 && meat == carcassMeat) meat = (meat + 1 + random.nextInt(Butcher.MEATS - 1)) % Butcher.MEATS
        carcassMeat = meat
        val spacing = (Butcher.CARCASS_X1 - Butcher.CARCASS_X0) / (Butcher.CUTS_PER_CARCASS + 1)
        for (i in 0 until Butcher.CUTS_PER_CARCASS) {
            carcassLines[i] = Butcher.CARCASS_X0 + spacing * (i + 1) + (random.nextFloat() * 2f - 1f) * Butcher.LINE_JITTER
            carcassCut[i] = false
        }
        carcassArriveMs = Butcher.CARCASS_ARRIVE_MS
        carcassLeaveMs = 0f
    }

    /** Fin de la découpe : le magasin ouvre, sauf s'il n'y a rien à vendre. */
    private fun endCut() {
        carcassLeaveMs = 0f
        if (pieceCount == 0) {
            finish(BUTCHER_END_SOLDOUT)
            return
        }
        stage = BUTCHER_STAGE_OPENING
        stageLeftMs = Butcher.OPENING_MS
    }

    /** Un coup de couteau (A) : coupe nette sur un pointillé, ou lame dans l'os. */
    private fun cut() {
        if (carcassLeaveMs > 0f || carcassArriveMs > 0f) return
        if (cleanMs > 0f || stuckMs > 0f) {
            block(BUTCHER_BLOCK_BUSY)
            return
        }
        val index = nearestLine()
        if (index < 0) return
        val distance = abs(knifeX - lineX(index))
        val tolerance = cutTolerance
        when {
            distance <= tolerance * Butcher.PERFECT_RATIO -> cutLine(index, BUTCHER_VERDICT_PERFECT)
            distance <= tolerance -> cutLine(index, BUTCHER_VERDICT_GOOD)
            else -> {
                boneHits++
                points -= Butcher.PENALTY_BONE
                stuckMs = Butcher.STUCK_MS
                block(BUTCHER_BLOCK_WRONG)
            }
        }
    }

    private fun cutLine(index: Int, quality: Int) {
        if (carcassMeat != BUTCHER_POULTRY && dirty) {
            addFault()
            if (stage == BUTCHER_STAGE_OVER) return
        }
        carcassCut[index] = true
        val slot = pieces.indexOfFirst { it == null }
        if (slot >= 0) pieces[slot] = ButcherPiece(carcassMeat, Butcher.pieceWeightG(carcassMeat), 1f)
        cuts++
        if (quality == BUTCHER_VERDICT_PERFECT) perfectCuts++
        points += if (quality == BUTCHER_VERDICT_PERFECT) Butcher.POINTS_CUT_PERFECT else Butcher.POINTS_CUT_GOOD
        if (carcassMeat == BUTCHER_POULTRY) dirty = true
        cutFlashMs = Butcher.CUT_FLASH_MS
        cutFlashX = knifeX
        cutFlashVerdict = quality
        if (carcassCut.all { it }) {
            carcassesDone++
            if (carcassesDone >= Butcher.CARCASSES) points += (stageLeftMs / 1000f).toInt() * Butcher.EARLY_BONUS_PER_S
            carcassLeaveMs = Butcher.CARCASS_LEAVE_MS
        }
    }

    // ---------------------------------------------------------------- le service

    private fun startService() {
        stage = BUTCHER_STAGE_SERVICE
        stageLeftMs = Butcher.SERVICE_MS
        inspectorState = BUTCHER_INSPECT_NONE
        inspectorInMs = randomBetween(Butcher.INSPECT_FIRST_MIN_MS, Butcher.INSPECT_FIRST_MAX_MS)
        if (pieces[cursor] == null) moveCursor(1)
        nextCustomer()
    }

    private fun randomBetween(from: Float, to: Float): Float = from + random.nextFloat() * (to - from)

    private fun tickService(dt: Float) {
        stageLeftMs -= dt
        if (stageLeftMs <= 0f) {
            stageLeftMs = 0f
            finish(BUTCHER_END_CLOSING)
            return
        }
        tickInspector(dt)
        if (stage != BUTCHER_STAGE_SERVICE) return
        arriveMs = max(0f, arriveMs - dt)
        when (step) {
            BUTCHER_STEP_PICK -> {
                tickCursorRepeat(dt)
                drainPatience(dt)
            }
            BUTCHER_STEP_SLICE -> {
                drainPatience(dt)
                if (step == BUTCHER_STEP_SLICE) advanceSlice(dt)
            }
            else -> {
                resultMs -= dt
                if (resultMs <= 0f) nextCustomer()
            }
        }
    }

    /** Une flèche tenue fait défiler le choix dans la chambre froide. */
    private fun tickCursorRepeat(dt: Float) {
        val direction = (if (heldRight) 1 else 0) - (if (heldLeft) 1 else 0)
        if (direction == 0) return
        repeatInMs -= dt
        while (repeatInMs <= 0f) {
            moveCursor(direction)
            repeatInMs += Butcher.REPEAT_MS
        }
    }

    private fun drainPatience(dt: Float) {
        patienceMs -= dt
        if (patienceMs <= 0f) {
            patienceMs = 0f
            customerLeaves()
        }
    }

    /** Le client perd patience : il s'en va ; ce qui est déjà tranché est perdu, la pièce reste dans la chambre froide. */
    private fun customerLeaves() {
        walkouts++
        streak = 0
        verdict = BUTCHER_VERDICT_LEFT
        lastPoints -= Butcher.PENALTY_LEFT
        points -= Butcher.PENALTY_LEFT
        endSlicing()
        step = BUTCHER_STEP_RESULT
        resultMs = Butcher.RESULT_MS
    }

    private fun endSlicing() {
        holdA = false
        holdMs = 0f
        sliceG = 0f
        pickedSlot = -1
    }

    private fun advanceSlice(dt: Float) {
        if (!holdA) return
        holdMs += dt
        val speed = lerp(Butcher.SLICE_SPEED_START, Butcher.SLICE_SPEED_END) * min(1f, 0.35f + holdMs / 500f)
        sliceG += speed * dt / 1000f
        if (sliceG >= Butcher.SLICE_MAX_G) {
            sliceG = Butcher.SLICE_MAX_G
            cutSlice()
        }
    }

    /** Un nouveau client : une viande dont il reste assez, un nombre de tranches et un poids que la plus grosse pièce permet. */
    private fun nextCustomer() {
        if (!bonusDone && score >= Butcher.BONUS_SCORE && stageLeftMs >= Butcher.BONUS_MIN_LEFT_MS) {
            startBonus()
            return
        }
        val options = (0 until Butcher.MEATS).filter { meat ->
            pieces.any { it != null && it.meat == meat && it.weightG >= Butcher.MIN_SLICE_G }
        }
        if (options.isEmpty()) {
            finish(BUTCHER_END_SOLDOUT)
            return
        }
        val meat = options[random.nextInt(options.size)]
        val biggest = pieces.filter { it != null && it.meat == meat }.maxOf { it!!.weightG }
        var count = 1 + random.nextInt(Butcher.MAX_SLICES)
        while (count > 1 && perSliceCap(biggest, count) < Butcher.MIN_SLICE_G) count--
        val cap = perSliceCap(biggest, count)
        val steps = (cap - Butcher.MIN_SLICE_G) / Butcher.SLICE_STEP_G
        sliceWantG = Butcher.MIN_SLICE_G + random.nextInt(steps + 1) * Butcher.SLICE_STEP_G
        slicesTotal = count
        slicesLeft = count
        customerMeat = meat
        customerLook = random.nextInt(4)
        patienceMaxMs = lerp(Butcher.PATIENCE_START_MS, Butcher.PATIENCE_END_MS)
        patienceMs = patienceMaxMs
        step = BUTCHER_STEP_PICK
        arriveMs = Butcher.ARRIVE_MS
        verdict = BUTCHER_VERDICT_NONE
        worstVerdict = BUTCHER_VERDICT_NONE
        sliceVerdict = BUTCHER_VERDICT_NONE
        weighedG = 0
        lastPoints = 0
        endSlicing()
        if (pieces[cursor] == null) moveCursor(1)
    }

    /** Le plus gros poids par tranche que permet une pièce de [weight] grammes pour [count] tranches. */
    private fun perSliceCap(weight: Int, count: Int): Int =
        min(Butcher.MAX_SLICE_G, weight / count / Butcher.SLICE_STEP_G * Butcher.SLICE_STEP_G)

    private fun finish(reason: Int) {
        endReason = reason
        stage = BUTCHER_STAGE_OVER
        overMs = 0f
        holdA = false
        heldLeft = false
        heldRight = false
    }

    // ---------------------------------------------------------------- l'hygiène

    /** Une faute d'hygiène : des points en moins, la série perdue ; le plan est « nettoyé » par la force des choses. Trois fautes ferment la boucherie. */
    private fun addFault(penalty: Int = Butcher.PENALTY_FAULT) {
        faults++
        points -= penalty
        streak = 0
        dirty = false
        cleanMs = 0f
        faultMs = Butcher.FAULT_MS
        if (faults >= Butcher.MAX_FAULTS) finish(BUTCHER_END_HYGIENE)
    }

    private fun tickInspector(dt: Float) {
        if (inspectorState == BUTCHER_INSPECT_WARN) {
            inspectorWarnMs -= dt
            if (inspectorWarnMs > 0f) return
            inspections++
            inspectorState = BUTCHER_INSPECT_NONE
            inspectorInMs = randomBetween(Butcher.INSPECT_NEXT_MIN_MS, Butcher.INSPECT_NEXT_MAX_MS)
            inspectShowMs = Butcher.INSPECT_SHOW_MS
            if (dirty) {
                inspectResult = -1
                addFault(Butcher.PENALTY_INSPECT)
            } else {
                inspectResult = 1
                inspectionsPassed++
                points += Butcher.POINTS_INSPECT_OK
            }
        } else {
            inspectorInMs -= dt
            if (inspectorInMs <= 0f && stageLeftMs > Butcher.INSPECT_MIN_LEFT_MS) {
                inspectorState = BUTCHER_INSPECT_WARN
                inspectorWarnMs = Butcher.INSPECT_WARN_MS
            }
        }
    }

    private fun startClean() {
        if (dirty && cleanMs <= 0f) cleanMs = Butcher.CLEAN_MS
    }

    // ---------------------------------------------------------------- joueur

    /** Une touche ([BUTCHER_LEFT] à [BUTCHER_B]) vient d'être pressée. */
    fun press(key: Int) {
        when (key) {
            BUTCHER_LEFT -> {
                heldLeft = true
                repeatInMs = Butcher.REPEAT_FIRST_MS
                if (stage == BUTCHER_STAGE_SERVICE && step == BUTCHER_STEP_PICK) moveCursor(-1)
            }
            BUTCHER_RIGHT -> {
                heldRight = true
                repeatInMs = Butcher.REPEAT_FIRST_MS
                if (stage == BUTCHER_STAGE_SERVICE && step == BUTCHER_STEP_PICK) moveCursor(1)
            }
            BUTCHER_A -> pressA()
            BUTCHER_B -> pressB()
        }
    }

    /** Une touche vient d'être relâchée : les flèches s'arrêtent, A tenu coupe la tranche (ou arrête le remplissage du boyau). */
    fun release(key: Int) {
        when (key) {
            BUTCHER_LEFT -> heldLeft = false
            BUTCHER_RIGHT -> heldRight = false
            BUTCHER_A -> releaseA()
        }
    }

    /** Toutes les touches sont lâchées d'un coup (pause, retour au jeu) : rien n'est coupé. */
    fun releaseAll() {
        heldLeft = false
        heldRight = false
        if (holdA) {
            holdA = false
            holdMs = 0f
            sliceG = 0f
        }
    }

    private fun pressA() {
        when (stage) {
            BUTCHER_STAGE_CUT -> cut()
            BUTCHER_STAGE_SERVICE -> when (step) {
                BUTCHER_STEP_PICK -> takePiece()
                BUTCHER_STEP_SLICE -> if (!holdA) {
                    holdA = true
                    holdMs = 0f
                }
                else -> {}
            }
            BUTCHER_STAGE_BONUS -> if (bonusIntroMs <= 0f) holdA = true
            else -> {}
        }
    }

    private fun releaseA() {
        if (!holdA) return
        holdA = false
        if (stage == BUTCHER_STAGE_SERVICE && step == BUTCHER_STEP_SLICE) {
            if (sliceG >= Butcher.SLICE_MIN_HOLD_G) {
                cutSlice()
            } else {
                sliceG = 0f
                holdMs = 0f
            }
        }
    }

    private fun pressB() {
        when (stage) {
            BUTCHER_STAGE_CUT -> startClean()
            BUTCHER_STAGE_SERVICE -> if (step != BUTCHER_STEP_SLICE) startClean()
            BUTCHER_STAGE_BONUS -> pinch()
            else -> {}
        }
    }

    private fun block(kind: Int) {
        blockedMs = Butcher.BLOCKED_MS
        blockedKind = kind
    }

    /** Déplace le choix vers la prochaine pièce dans le sens [direction] (-1 ou 1), en sautant les cases vides. */
    private fun moveCursor(direction: Int) {
        var i = cursor
        repeat(Butcher.SLOTS) {
            i = (i + direction + Butcher.SLOTS) % Butcher.SLOTS
            if (pieces[i] != null) {
                cursor = i
                return
            }
        }
    }

    /**
     * Prend la pièce choisie : ce doit être la bonne viande et une pièce assez lourde pour toutes les tranches. Prendre de la
     * volaille salit le plan ; prendre une autre viande avec un plan sale est une faute d'hygiène.
     */
    private fun takePiece() {
        if (cleanMs > 0f) {
            block(BUTCHER_BLOCK_BUSY)
            return
        }
        val piece = pieces[cursor]
        if (piece == null) {
            block(BUTCHER_BLOCK_WRONG)
            return
        }
        if (piece.meat != customerMeat) {
            wrongMeat++
            streak = 0
            points -= Butcher.PENALTY_WRONG
            block(BUTCHER_BLOCK_WRONG)
            patienceMs = max(0f, patienceMs - patienceMaxMs * Butcher.WRONG_MEAT_PATIENCE)
            if (patienceMs <= 0f) customerLeaves()
            return
        }
        if (piece.weightG < slicesLeft * sliceWantG) {
            block(BUTCHER_BLOCK_SMALL)
            return
        }
        if (piece.meat != BUTCHER_POULTRY && dirty) {
            addFault()
            if (stage == BUTCHER_STAGE_OVER) return
        }
        if (piece.meat == BUTCHER_POULTRY) dirty = true
        pickedSlot = cursor
        pickedFresh = piece.fresh
        step = BUTCHER_STEP_SLICE
        sliceG = 0f
        holdA = false
        holdMs = 0f
    }

    /** La tranche est coupée : le verdict dépend de l'écart entre le poids coupé et le poids demandé. */
    private fun cutSlice() {
        val piece = pieces.getOrNull(pickedSlot)
        if (piece == null) {
            finishCustomer()
            return
        }
        val weighed = sliceG.roundToInt()
        val tolerance = sliceTolG
        val gap = abs(weighed - sliceWantG)
        val v = when {
            gap <= tolerance * Butcher.PERFECT_RATIO -> BUTCHER_VERDICT_PERFECT
            gap <= tolerance -> BUTCHER_VERDICT_GOOD
            gap <= tolerance * Butcher.OFF_RATIO -> BUTCHER_VERDICT_OFF
            else -> BUTCHER_VERDICT_MISS
        }
        piece.weightG = max(0, piece.weightG - weighed)
        val gained = when (v) {
            BUTCHER_VERDICT_PERFECT -> Butcher.POINTS_SLICE_PERFECT
            BUTCHER_VERDICT_GOOD -> Butcher.POINTS_SLICE_GOOD
            BUTCHER_VERDICT_OFF -> Butcher.POINTS_SLICE_OFF
            else -> -Butcher.PENALTY_SLICE_MISS
        }
        points += gained
        lastPoints += gained
        if (v == BUTCHER_VERDICT_PERFECT) perfects++
        worstVerdict = max(worstVerdict, v)
        sliceVerdict = v
        sliceFlashMs = Butcher.SLICE_FLASH_MS
        weighedG = weighed
        holdA = false
        holdMs = 0f
        sliceG = 0f
        slicesLeft--
        // Un reste trop petit pour une tranche est jeté (sans pénalité) : la pièce est épuisée.
        val exhausted = piece.weightG < Butcher.MIN_SLICE_G
        if (exhausted) pieces[pickedSlot] = null
        if (slicesLeft <= 0 || exhausted) finishCustomer()
    }

    /** Le client a eu toutes ses tranches (ou la pièce est épuisée) : primes si tout est bon, puis le verdict. */
    private fun finishCustomer() {
        verdict = worstVerdict
        if (worstVerdict == BUTCHER_VERDICT_PERFECT || worstVerdict == BUTCHER_VERDICT_GOOD) {
            streak++
            bestStreak = max(bestStreak, streak)
            served++
            var bonus = (patienceMs / patienceMaxMs * Butcher.PATIENCE_BONUS).roundToInt()
            bonus += min(Butcher.STREAK_BONUS_CAP, (streak - 1) * Butcher.STREAK_BONUS)
            if (pickedFresh < Butcher.SAVE_FRESH) {
                saved++
                bonus += Butcher.POINTS_SAVED
            }
            points += bonus
            lastPoints += bonus
        } else {
            streak = 0
            if (worstVerdict == BUTCHER_VERDICT_OFF) served++ else walkouts++
        }
        endSlicing()
        step = BUTCHER_STEP_RESULT
        resultMs = Butcher.RESULT_MS
    }

    // ---------------------------------------------------------------- le bonus saucisses

    private fun startBonus() {
        bonusDone = true
        stage = BUTCHER_STAGE_BONUS
        bonusLeftMs = Butcher.BONUS_MS
        bonusIntroMs = Butcher.BONUS_INTRO_MS
        linkLen = 0f
        linkTarget = randomTarget()
        combo = 0
        holdA = false
        chainLen.clear()
        chainVerdict.clear()
        endSlicing()
    }

    private fun randomTarget(): Int {
        val steps = (Butcher.TARGET_MAX_MM - Butcher.TARGET_MIN_MM) / Butcher.TARGET_STEP_MM
        return Butcher.TARGET_MIN_MM + random.nextInt(steps + 1) * Butcher.TARGET_STEP_MM
    }

    private fun tickBonus(dt: Float) {
        if (bonusIntroMs > 0f) {
            bonusIntroMs = max(0f, bonusIntroMs - dt)
            return
        }
        bonusLeftMs -= dt
        if (bonusLeftMs <= 0f) {
            bonusLeftMs = 0f
            endBonus()
            return
        }
        if (holdA) {
            linkLen += Butcher.FILL_SPEED * dt / 1000f
            if (linkLen > linkTarget + Butcher.BURST_OVER_MM) burst()
        }
    }

    /** Le boyau éclate : la saucisse en cours est perdue et il faut relâcher puis presser de nouveau A. */
    private fun burst() {
        bursts++
        combo = 0
        points -= Butcher.PENALTY_BURST
        burstMs = Butcher.BURST_MS
        holdA = false
        linkLen = 0f
        linkTarget = randomTarget()
    }

    /** Ficelle la saucisse en cours (B) : le verdict dépend de l'écart avec la longueur voulue. */
    private fun pinch() {
        if (bonusIntroMs > 0f || linkLen < Butcher.MIN_LINK_MM) return
        val gap = abs(linkLen - linkTarget)
        val v = when {
            gap <= Butcher.BONUS_TOL_MM * Butcher.BONUS_PERFECT_RATIO -> BUTCHER_VERDICT_PERFECT
            gap <= Butcher.BONUS_TOL_MM -> BUTCHER_VERDICT_GOOD
            else -> BUTCHER_VERDICT_MISS
        }
        var gained: Int
        if (v == BUTCHER_VERDICT_MISS) {
            combo = 0
            gained = -Butcher.PENALTY_LINK_BAD
        } else {
            combo++
            sausages++
            gained = if (v == BUTCHER_VERDICT_PERFECT) Butcher.POINTS_LINK_PERFECT else Butcher.POINTS_LINK_GOOD
            gained += min(Butcher.COMBO_BONUS_CAP, (combo - 1) * Butcher.COMBO_BONUS)
        }
        points += gained
        chainLen.add(linkLen)
        chainVerdict.add(v)
        if (chainLen.size > 8) {
            chainLen.removeAt(0)
            chainVerdict.removeAt(0)
        }
        linkFlashMs = Butcher.LINK_FLASH_MS
        linkFlashVerdict = v
        linkLen = 0f
        linkTarget = randomTarget()
    }

    /** Fin du bonus : le service reprend là où il s'était arrêté, avec un nouveau client. */
    private fun endBonus() {
        stage = BUTCHER_STAGE_SERVICE
        holdA = false
        linkLen = 0f
        nextCustomer()
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        put(SAVE_VERSION)
        put(stage); put(stageLeftMs); put(clockMs); put(points)
        put(cuts); put(perfectCuts); put(boneHits); put(served); put(perfects); put(walkouts); put(wrongMeat)
        put(spoiled); put(saved); put(faults); put(streak); put(bestStreak); put(inspections); put(inspectionsPassed)
        put(sausages); put(bursts); put(endReason)
        put(knifeX); put(stuckMs); put(cutFlashMs); put(cutFlashX); put(cutFlashVerdict)
        put(carcassMeat); put(carcassNumber); put(carcassesDone); put(carcassArriveMs); put(carcassLeaveMs)
        for (x in carcassLines) put(x)
        for (c in carcassCut) put(c)
        put(cursor); put(dirty); put(cleanMs); put(faultMs)
        put(customerMeat); put(customerLook); put(slicesTotal); put(slicesLeft); put(sliceWantG); put(patienceMs); put(patienceMaxMs)
        put(step); put(arriveMs); put(pickedSlot); put(pickedFresh); put(sliceG); put(holdA); put(holdMs)
        put(sliceFlashMs); put(sliceVerdict); put(weighedG); put(worstVerdict); put(verdict); put(resultMs); put(lastPoints)
        put(blockedMs); put(blockedKind); put(spoilMs); put(spoilSlot); put(overMs)
        put(inspectorState); put(inspectorInMs); put(inspectorWarnMs); put(inspectResult); put(inspectShowMs)
        put(bonusDone); put(bonusLeftMs); put(bonusIntroMs); put(linkLen); put(linkTarget); put(combo)
        put(linkFlashMs); put(linkFlashVerdict); put(burstMs)
        for (piece in pieces) {
            if (piece == null) {
                put(false)
            } else {
                put(true)
                put(piece.meat); put(piece.weightG); put(piece.fresh)
            }
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
        stage = int().coerceIn(BUTCHER_STAGE_CUT, BUTCHER_STAGE_OVER); stageLeftMs = float(); clockMs = float(); points = int()
        cuts = int(); perfectCuts = int(); boneHits = int(); served = int(); perfects = int(); walkouts = int(); wrongMeat = int()
        spoiled = int(); saved = int(); faults = int(); streak = int(); bestStreak = int(); inspections = int(); inspectionsPassed = int()
        sausages = int(); bursts = int(); endReason = int().coerceIn(BUTCHER_END_CLOSING, BUTCHER_END_HYGIENE)
        knifeX = float().coerceIn(Butcher.KNIFE_MIN, Butcher.KNIFE_MAX); stuckMs = float(); cutFlashMs = float(); cutFlashX = float()
        cutFlashVerdict = int().coerceIn(BUTCHER_VERDICT_NONE, BUTCHER_VERDICT_LEFT)
        carcassMeat = int().coerceIn(0, Butcher.MEATS - 1); carcassNumber = int(); carcassesDone = int()
        carcassArriveMs = float(); carcassLeaveMs = float()
        for (i in 0 until Butcher.CUTS_PER_CARCASS) carcassLines[i] = float()
        for (i in 0 until Butcher.CUTS_PER_CARCASS) carcassCut[i] = flag()
        cursor = int().coerceIn(0, Butcher.SLOTS - 1); dirty = flag(); cleanMs = float(); faultMs = float()
        customerMeat = int().coerceIn(-1, Butcher.MEATS - 1); customerLook = int().coerceIn(0, 3)
        slicesTotal = int(); slicesLeft = int(); sliceWantG = int(); patienceMs = float(); patienceMaxMs = max(1f, float())
        step = int().coerceIn(BUTCHER_STEP_PICK, BUTCHER_STEP_RESULT); arriveMs = float()
        pickedSlot = int().coerceIn(-1, Butcher.SLOTS - 1); pickedFresh = float(); sliceG = float(); holdA = flag(); holdMs = float()
        sliceFlashMs = float(); sliceVerdict = int().coerceIn(BUTCHER_VERDICT_NONE, BUTCHER_VERDICT_LEFT); weighedG = int()
        worstVerdict = int().coerceIn(BUTCHER_VERDICT_NONE, BUTCHER_VERDICT_LEFT)
        verdict = int().coerceIn(BUTCHER_VERDICT_NONE, BUTCHER_VERDICT_LEFT); resultMs = float(); lastPoints = int()
        blockedMs = float(); blockedKind = int().coerceIn(BUTCHER_BLOCK_NONE, BUTCHER_BLOCK_BUSY)
        spoilMs = float(); spoilSlot = int().coerceIn(-1, Butcher.SLOTS - 1); overMs = float()
        inspectorState = int().coerceIn(BUTCHER_INSPECT_NONE, BUTCHER_INSPECT_WARN); inspectorInMs = float()
        inspectorWarnMs = float(); inspectResult = int().coerceIn(-1, 1); inspectShowMs = float()
        bonusDone = flag(); bonusLeftMs = float(); bonusIntroMs = float(); linkLen = float(); linkTarget = int(); combo = int()
        linkFlashMs = float(); linkFlashVerdict = int().coerceIn(BUTCHER_VERDICT_NONE, BUTCHER_VERDICT_LEFT); burstMs = float()
        for (i in 0 until Butcher.SLOTS) {
            pieces[i] = if (flag()) {
                val meat = int().coerceIn(0, Butcher.MEATS - 1)
                ButcherPiece(meat, int().coerceIn(0, 5000), float().coerceIn(0f, 1f))
            } else {
                null
            }
        }
        heldLeft = false
        heldRight = false
        repeatInMs = 0f
        chainLen.clear()
        chainVerdict.clear()
    }

    companion object {
        private const val SAVE_VERSION = 1

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): ButcherGame {
            val game = ButcherGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
