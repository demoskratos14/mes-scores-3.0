package com.aventure.messcores

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Identifiant du jeu « Comptabilité » (choisir le bon signe d'une opération, vérifier des factures) dans la liste des jeux. */
const val ACCOUNTING_ID = "builtin_accounting"

// Les six touches du jeu : les quatre flèches de la croix (chacune est un signe) et les boutons A (vrai) et B (faux).
internal const val ACC_UP = 0
internal const val ACC_DOWN = 1
internal const val ACC_LEFT = 2
internal const val ACC_RIGHT = 3
internal const val ACC_A = 4
internal const val ACC_B = 5

// Les quatre signes : chacun porte le numéro de la flèche qui le désigne (haut +, bas −, gauche ÷, droite ×).
internal const val ACC_OP_ADD = ACC_UP
internal const val ACC_OP_SUB = ACC_DOWN
internal const val ACC_OP_DIV = ACC_LEFT
internal const val ACC_OP_MUL = ACC_RIGHT

// Les étapes d'une partie : le temps normal, l'annonce du temps bonus, le temps bonus (opérations en chaîne), la fin.
internal const val ACC_STAGE_MAIN = 0
internal const val ACC_STAGE_BONUS_INTRO = 1
internal const val ACC_STAGE_BONUS = 2
internal const val ACC_STAGE_OVER = 3

// Ce que le client apporte : une opération dont il faut trouver le signe, une facture à valider, une opération en chaîne.
internal const val ACC_KIND_SIGN = 0
internal const val ACC_KIND_CHECK = 1
internal const val ACC_KIND_CHAIN = 2

// Les étapes du traitement d'un client : on attend la réponse, puis le verdict.
internal const val ACC_STEP_ASK = 0
internal const val ACC_STEP_RESULT = 1

// Les verdicts.
internal const val ACC_VERDICT_NONE = 0
internal const val ACC_VERDICT_RIGHT = 1
internal const val ACC_VERDICT_WRONG = 2
internal const val ACC_VERDICT_LEFT = 3 // le client a perdu patience et s'en va

/**
 * Les calculs du jeu, en Kotlin pur : une opération est valable quand elle tombe juste en nombres entiers (une division doit
 * être exacte, une soustraction ne descend pas sous zéro). Une chaîne de trois nombres suit les priorités habituelles :
 * la multiplication et la division avant l'addition et la soustraction.
 */
internal object AccountingMath {
    /** Vrai pour la multiplication et la division, qui passent avant l'addition et la soustraction. */
    private fun strong(op: Int): Boolean = op == ACC_OP_MUL || op == ACC_OP_DIV

    /** [a] [op] [b], ou null si l'opération n'est pas valable (division inexacte ou par zéro, résultat négatif). */
    fun apply(a: Int, op: Int, b: Int): Int? = when (op) {
        ACC_OP_ADD -> a + b
        ACC_OP_SUB -> if (a >= b) a - b else null
        ACC_OP_MUL -> a * b
        ACC_OP_DIV -> if (b != 0 && a % b == 0) a / b else null
        else -> null
    }

    /** Le résultat de [numbers] reliés par [ops] (un signe de moins que de nombres, deux nombres ou trois), ou null si une étape n'est pas valable. */
    fun evaluate(numbers: IntArray, ops: IntArray): Int? {
        if (numbers.size == 2 && ops.size == 1) return apply(numbers[0], ops[0], numbers[1])
        if (numbers.size == 3 && ops.size == 2) {
            if (strong(ops[1]) && !strong(ops[0])) {
                val tail = apply(numbers[1], ops[1], numbers[2]) ?: return null
                return apply(numbers[0], ops[0], tail)
            }
            val head = apply(numbers[0], ops[0], numbers[1]) ?: return null
            return apply(head, ops[1], numbers[2])
        }
        return null
    }

    /** Toutes les façons de choisir les signes pour que [numbers] donne [result]. */
    fun solutions(numbers: IntArray, result: Int): List<IntArray> {
        val out = ArrayList<IntArray>()
        for (first in 0..3) {
            if (numbers.size == 2) {
                if (evaluate(numbers, intArrayOf(first)) == result) out.add(intArrayOf(first))
            } else {
                for (second in 0..3) {
                    val ops = intArrayOf(first, second)
                    if (evaluate(numbers, ops) == result) out.add(ops)
                }
            }
        }
        return out
    }
}

/** Une opération à trous : les [numbers], les signes à trouver ([solution]) et le [result] affiché après le signe « = ». */
internal class AccEquation(val numbers: IntArray, val solution: IntArray, val result: Int)

/** Une facture : les montants de ses [lines], et le total [shownTotal] écrit en bas (juste ou non). */
internal class AccInvoice(val lines: IntArray, val shownTotal: Int) {
    val trueTotal: Int get() = lines.sum()
    val isTrue: Boolean get() = shownTotal == trueTotal
}

internal object Accounting {
    /** L'écran est le même que celui de Drive (720 x 405), pour reprendre sa console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    /** Durée du temps normal, de l'annonce du temps bonus et du temps bonus (millisecondes). */
    const val MAIN_MS = 90_000f
    const val BONUS_INTRO_MS = 2_600f
    const val BONUS_MS = 30_000f

    /** Le temps bonus est accordé quand le score atteint ce seuil à la fin du temps normal. */
    const val BONUS_SCORE = 1_000

    /** Patience d'un client (millisecondes), au début puis à la fin de l'étape : plus longue pour une facture, encore plus pour une chaîne. */
    const val PATIENCE_SIGN_START_MS = 9_000f
    const val PATIENCE_SIGN_END_MS = 5_000f
    const val PATIENCE_CHECK_START_MS = 11_000f
    const val PATIENCE_CHECK_END_MS = 7_000f
    const val PATIENCE_CHAIN_START_MS = 15_000f
    const val PATIENCE_CHAIN_END_MS = 10_000f

    /** Une réponse fausse à une opération fait perdre cette part de la patience du client. */
    const val WRONG_PATIENCE = 0.25f

    // Les points.
    const val POINTS_SIGN = 50
    const val POINTS_CHECK = 60
    const val POINTS_CHAIN = 150
    const val SPEED_BONUS = 30
    const val SPEED_BONUS_CHAIN = 60
    const val STREAK_BONUS = 10
    const val STREAK_BONUS_CAP = 50
    const val PENALTY_WRONG_SIGN = 20
    const val PENALTY_WRONG_CHECK = 30
    const val PENALTY_LEFT = 20

    /** Part des clients qui apportent une facture à valider (un peu moins juste après une facture). */
    const val CHECK_SHARE = 0.3f
    const val CHECK_SHARE_AFTER_CHECK = 0.1f

    // Les animations (millisecondes).
    const val ARRIVE_MS = 450f
    const val RESULT_MS = 750f
    const val BLOCKED_MS = 300f
    const val STAMP_MS = 220f

    /** La partie se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f

    /** Nombre maximal d'essais pour trouver une opération à réponse unique (la première réussit presque toujours). */
    private const val MAX_TRIES = 400

    private fun lerp(from: Int, to: Int, t: Float): Int = (from + (to - from) * t.coerceIn(0f, 1f)).roundToInt()

    /** Un nombre au hasard de [low] à [high] compris. */
    private fun Random.between(low: Int, high: Int): Int = low + nextInt(max(1, high - low + 1))

    /**
     * Une opération à un signe, à réponse unique : un seul des quatre signes donne le résultat. Les nombres grossissent avec la
     * [difficulty] (0 au début, 1 à la fin).
     */
    fun sign(random: Random, difficulty: Float): AccEquation {
        val big = lerp(9, 60, difficulty)
        val table = lerp(6, 12, difficulty)
        repeat(MAX_TRIES) {
            val op = random.nextInt(4)
            val a: Int
            val b: Int
            when (op) {
                ACC_OP_ADD -> { a = random.between(2, big); b = random.between(1, big) }
                ACC_OP_SUB -> { a = random.between(3, big + 2); b = random.between(1, a - 1) }
                ACC_OP_MUL -> { a = random.between(2, table); b = random.between(2, table) }
                else -> { b = random.between(2, table); a = b * random.between(2, table) }
            }
            val result = AccountingMath.apply(a, op, b) ?: return@repeat
            val numbers = intArrayOf(a, b)
            if (AccountingMath.solutions(numbers, result).size == 1) return AccEquation(numbers, intArrayOf(op), result)
        }
        return AccEquation(intArrayOf(7, 5), intArrayOf(ACC_OP_ADD), 12)
    }

    /**
     * Une opération en chaîne à deux signes, à réponse unique : un seul des seize couples de signes donne le résultat (avec les
     * priorités habituelles). Au moins un des deux signes est une multiplication ou une division, pour que les priorités comptent.
     */
    fun chain(random: Random, difficulty: Float): AccEquation {
        val high = lerp(6, 12, difficulty)
        repeat(MAX_TRIES) {
            val numbers = intArrayOf(random.between(2, high), random.between(2, high), random.between(2, high))
            val ops = intArrayOf(random.nextInt(4), random.nextInt(4))
            if (ops.none { it == ACC_OP_MUL || it == ACC_OP_DIV }) return@repeat
            val result = AccountingMath.evaluate(numbers, ops) ?: return@repeat
            if (result > 200) return@repeat
            if (AccountingMath.solutions(numbers, result).size == 1) return AccEquation(numbers, ops, result)
        }
        return AccEquation(intArrayOf(2, 3, 4), intArrayOf(ACC_OP_ADD, ACC_OP_MUL), 14)
    }

    /**
     * Une facture à valider : de deux à quatre montants selon la [difficulty], et un total qui est juste une fois sur deux. Un total
     * faux s'écarte du vrai d'un petit écart (grand au début, plus subtil ensuite) ou a ses deux derniers chiffres inversés.
     */
    fun invoice(random: Random, difficulty: Float): AccInvoice {
        val count = when {
            difficulty < 0.34f -> 2
            difficulty < 0.67f -> 3
            else -> 4
        }
        val high = lerp(30, 99, difficulty)
        val lines = IntArray(count) { random.between(5, high) }
        val total = lines.sum()
        if (random.nextBoolean()) return AccInvoice(lines, total)
        val gaps = if (difficulty < 0.5f) listOf(10, -10, 5, -5, 20, -20) else listOf(1, -1, 2, -2, 9, -9, 10, -10, 11, -11)
        val candidates = ArrayList<Int>()
        for (gap in gaps) candidates.add(total + gap)
        if (difficulty >= 0.5f) candidates.add(total / 100 * 100 + total % 10 * 10 + total / 10 % 10)
        val wrong = candidates.filter { it > 0 && it != total }
        return AccInvoice(lines, wrong[random.nextInt(wrong.size)])
    }
}

/**
 * Une partie de Comptabilité. Pendant 1 min 30, des clients se succèdent au comptoir : chacun apporte soit une opération dont il faut
 * trouver le signe (haut +, bas −, gauche ÷, droite ×), soit une facture dont il faut dire si le total est juste (A : vrai, B : faux).
 * Il n'y a qu'un signe possible par opération. Une patience baisse en continu ; une série de bonnes réponses rapporte des primes.
 * Si le score atteint [Accounting.BONUS_SCORE] à la fin du temps normal, un temps bonus de 30 secondes est accordé, avec des
 * opérations en chaîne à deux signes. Le score additionne le tout.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [AccountingGame].
 */
internal class AccountingGame(private val random: Random = Random.Default) {
    var stage = ACC_STAGE_MAIN
        private set

    /** Le temps qui reste dans l'étape en cours (millisecondes). */
    var stageLeftMs = Accounting.MAIN_MS
        private set

    /** L'horloge de la partie, pour les animations (millisecondes). */
    var clockMs = 0f
        private set

    var mainPoints = 0
        private set
    var bonusPoints = 0
        private set

    /** Le score : les points du temps normal et du temps bonus, jamais en dessous de zéro. */
    val score: Int get() = max(0, mainPoints + bonusPoints)

    // Les compteurs de la partie.
    var solved = 0
        private set
    var checked = 0
        private set
    var chains = 0
        private set
    var mistakes = 0
        private set
    var walkouts = 0
        private set
    var streak = 0
        private set
    var bestStreak = 0
        private set

    /** Vrai quand le temps bonus a été accordé. */
    var bonusGranted = false
        private set

    // Le client en cours.
    var kind = ACC_KIND_SIGN
        private set
    var customerLook = 0
        private set
    var numbers = intArrayOf(0, 0)
        private set

    /** Les signes à trouver, et ceux déjà trouvés (les [found] premiers). */
    var solution = intArrayOf(ACC_OP_ADD)
        private set
    var result = 0
        private set
    var invoiceLines = IntArray(0)
        private set
    var shownTotal = 0
        private set
    var found = 0
        private set
    var patienceMs = 0f
        private set
    var patienceMaxMs = 1f
        private set
    var arriveMs = 0f
        private set
    var step = ACC_STEP_ASK
        private set
    var verdict = ACC_VERDICT_NONE
        private set
    var resultMs = 0f
        private set

    /** Les points gagnés (ou perdus) avec le dernier client, pour les afficher. */
    var lastPoints = 0
        private set
    private var clients = 0
    private var lastKind = ACC_KIND_SIGN

    // Les animations : geste refusé, dernier tampon, fin de partie.
    var blockedMs = 0f
        private set
    var stampMs = 0f
        private set
    var stampKey = -1
        private set
    var overMs = 0f
        private set

    init {
        reset()
    }

    /** Une partie neuve. */
    fun reset() {
        stage = ACC_STAGE_MAIN
        stageLeftMs = Accounting.MAIN_MS
        clockMs = 0f
        mainPoints = 0
        bonusPoints = 0
        solved = 0
        checked = 0
        chains = 0
        mistakes = 0
        walkouts = 0
        streak = 0
        bestStreak = 0
        bonusGranted = false
        clients = 0
        lastKind = ACC_KIND_SIGN
        blockedMs = 0f
        stampMs = 0f
        stampKey = -1
        overMs = 0f
        nextClient()
    }

    // ---------------------------------------------------------------- état

    val over: Boolean get() = stage == ACC_STAGE_OVER

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Accounting.RESTART_DELAY_MS

    /** Vrai quand un client attend une réponse. */
    val asking: Boolean get() = playing && step == ACC_STEP_ASK

    /** Vrai pendant le temps normal et le temps bonus (pas pendant l'annonce du bonus ni à la fin). */
    val playing: Boolean get() = stage == ACC_STAGE_MAIN || stage == ACC_STAGE_BONUS

    /** La facture du client en cours (seulement pour [ACC_KIND_CHECK]). */
    fun invoice(): AccInvoice = AccInvoice(invoiceLines, shownTotal)

    /** La difficulté de 0 (début de l'étape) à 1 (dernière seconde) : elle repart de zéro au début du temps bonus. */
    val difficulty: Float
        get() = when (stage) {
            ACC_STAGE_MAIN -> 1f - stageLeftMs / Accounting.MAIN_MS
            ACC_STAGE_BONUS -> 1f - stageLeftMs / Accounting.BONUS_MS
            ACC_STAGE_BONUS_INTRO -> 0f
            else -> 1f
        }.coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        blockedMs = max(0f, blockedMs - dt)
        stampMs = max(0f, stampMs - dt)
        when (stage) {
            ACC_STAGE_MAIN -> tickPlay(dt)
            ACC_STAGE_BONUS_INTRO -> {
                stageLeftMs -= dt
                if (stageLeftMs <= 0f) {
                    stage = ACC_STAGE_BONUS
                    stageLeftMs = Accounting.BONUS_MS
                    nextClient()
                }
            }
            ACC_STAGE_BONUS -> tickPlay(dt)
            else -> overMs += dt
        }
    }

    private fun tickPlay(dt: Float) {
        stageLeftMs -= dt
        if (stageLeftMs <= 0f) {
            stageLeftMs = 0f
            endOfStage()
            return
        }
        arriveMs = max(0f, arriveMs - dt)
        if (step == ACC_STEP_ASK) {
            patienceMs -= dt
            if (patienceMs <= 0f) {
                patienceMs = 0f
                customerLeaves()
            }
        } else {
            resultMs -= dt
            if (resultMs <= 0f) nextClient()
        }
    }

    /** Fin du temps normal : le temps bonus est accordé si le score a atteint le seuil ; fin du temps bonus : la partie est finie. */
    private fun endOfStage() {
        if (stage == ACC_STAGE_MAIN && score >= Accounting.BONUS_SCORE) {
            bonusGranted = true
            stage = ACC_STAGE_BONUS_INTRO
            stageLeftMs = Accounting.BONUS_INTRO_MS
            step = ACC_STEP_RESULT
            resultMs = 0f
            return
        }
        stage = ACC_STAGE_OVER
        overMs = 0f
    }

    /** Le client perd patience : il s'en va sans rien payer. */
    private fun customerLeaves() {
        walkouts++
        streak = 0
        verdict = ACC_VERDICT_LEFT
        lastPoints = -Accounting.PENALTY_LEFT
        award(lastPoints)
        step = ACC_STEP_RESULT
        resultMs = Accounting.RESULT_MS
    }

    private fun award(points: Int) {
        if (stage == ACC_STAGE_BONUS) bonusPoints += points else mainPoints += points
    }

    /** Un nouveau client : une opération, une facture ou (pendant le temps bonus) une chaîne. */
    private fun nextClient() {
        val share = if (lastKind == ACC_KIND_CHECK) Accounting.CHECK_SHARE_AFTER_CHECK else Accounting.CHECK_SHARE
        kind = when {
            stage == ACC_STAGE_BONUS -> ACC_KIND_CHAIN
            clients > 0 && random.nextFloat() < share -> ACC_KIND_CHECK
            else -> ACC_KIND_SIGN
        }
        lastKind = kind
        clients++
        val d = difficulty
        when (kind) {
            ACC_KIND_CHECK -> {
                val invoice = Accounting.invoice(random, d)
                invoiceLines = invoice.lines
                shownTotal = invoice.shownTotal
                numbers = IntArray(0)
                solution = IntArray(0)
                result = 0
                patienceMaxMs = lerp(Accounting.PATIENCE_CHECK_START_MS, Accounting.PATIENCE_CHECK_END_MS)
            }
            ACC_KIND_CHAIN -> {
                val equation = Accounting.chain(random, 0.25f + 0.75f * d)
                numbers = equation.numbers
                solution = equation.solution
                result = equation.result
                invoiceLines = IntArray(0)
                shownTotal = 0
                patienceMaxMs = lerp(Accounting.PATIENCE_CHAIN_START_MS, Accounting.PATIENCE_CHAIN_END_MS)
            }
            else -> {
                val equation = Accounting.sign(random, d)
                numbers = equation.numbers
                solution = equation.solution
                result = equation.result
                invoiceLines = IntArray(0)
                shownTotal = 0
                patienceMaxMs = lerp(Accounting.PATIENCE_SIGN_START_MS, Accounting.PATIENCE_SIGN_END_MS)
            }
        }
        customerLook = random.nextInt(4)
        patienceMs = patienceMaxMs
        found = 0
        step = ACC_STEP_ASK
        arriveMs = Accounting.ARRIVE_MS
        verdict = ACC_VERDICT_NONE
        resultMs = 0f
        lastPoints = 0
    }

    // ---------------------------------------------------------------- joueur

    /** Une touche ([ACC_UP] à [ACC_B]) vient d'être pressée. */
    fun press(key: Int) {
        if (!asking) return
        when (kind) {
            ACC_KIND_CHECK -> if (key == ACC_A || key == ACC_B) answerCheck(key == ACC_A)
            else -> if (key in ACC_UP..ACC_RIGHT) answerSign(key)
        }
    }

    /** Un signe choisi : le bon rapproche du but (le client est servi quand tous les signes sont trouvés), un mauvais coûte des points et de la patience. */
    private fun answerSign(op: Int) {
        stampKey = op
        stampMs = Accounting.STAMP_MS
        if (op != solution[found]) {
            mistakes++
            streak = 0
            award(-Accounting.PENALTY_WRONG_SIGN)
            blockedMs = Accounting.BLOCKED_MS
            patienceMs = max(0f, patienceMs - patienceMaxMs * Accounting.WRONG_PATIENCE)
            if (patienceMs <= 0f) customerLeaves()
            return
        }
        found++
        if (found < solution.size) return
        val chain = kind == ACC_KIND_CHAIN
        if (chain) chains++ else solved++
        succeed(
            base = if (chain) Accounting.POINTS_CHAIN else Accounting.POINTS_SIGN,
            speed = if (chain) Accounting.SPEED_BONUS_CHAIN else Accounting.SPEED_BONUS
        )
    }

    /** Vrai ou faux pour une facture : la bonne réponse rapporte des points, la mauvaise termine la facture avec une pénalité. */
    private fun answerCheck(saysTrue: Boolean) {
        stampKey = if (saysTrue) ACC_A else ACC_B
        stampMs = Accounting.STAMP_MS
        if (saysTrue == invoice().isTrue) {
            checked++
            succeed(base = Accounting.POINTS_CHECK, speed = Accounting.SPEED_BONUS)
            return
        }
        mistakes++
        streak = 0
        verdict = ACC_VERDICT_WRONG
        lastPoints = -Accounting.PENALTY_WRONG_CHECK
        award(lastPoints)
        blockedMs = Accounting.BLOCKED_MS
        step = ACC_STEP_RESULT
        resultMs = Accounting.RESULT_MS
    }

    private fun succeed(base: Int, speed: Int) {
        streak++
        bestStreak = max(bestStreak, streak)
        val rush = (patienceMs / patienceMaxMs * speed).roundToInt()
        val run = minOf(Accounting.STREAK_BONUS_CAP, (streak - 1) * Accounting.STREAK_BONUS)
        lastPoints = base + rush + run
        award(lastPoints)
        verdict = ACC_VERDICT_RIGHT
        step = ACC_STEP_RESULT
        resultMs = Accounting.RESULT_MS
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        fun put(values: IntArray) {
            put(values.size)
            for (value in values) put(value)
        }
        put(SAVE_VERSION)
        put(stage); put(stageLeftMs); put(clockMs); put(mainPoints); put(bonusPoints)
        put(solved); put(checked); put(chains); put(mistakes); put(walkouts); put(streak); put(bestStreak); put(bonusGranted)
        put(kind); put(customerLook); put(result); put(shownTotal); put(found)
        put(patienceMs); put(patienceMaxMs); put(arriveMs); put(step); put(verdict); put(resultMs); put(lastPoints)
        put(clients); put(lastKind); put(blockedMs); put(stampMs); put(stampKey); put(overMs)
        put(numbers); put(solution); put(invoiceLines)
        return out
    }

    private fun load(values: List<Double>) {
        var at = 0
        fun num(): Double = values[at++]
        fun int(): Int = num().toInt()
        fun float(): Float = num().toFloat()
        fun flag(): Boolean = num() != 0.0
        fun ints(maxSize: Int): IntArray {
            val size = int()
            require(size in 0..maxSize)
            return IntArray(size) { int() }
        }
        require(int() == SAVE_VERSION)
        stage = int().coerceIn(ACC_STAGE_MAIN, ACC_STAGE_OVER); stageLeftMs = float(); clockMs = float()
        mainPoints = int(); bonusPoints = int()
        solved = int(); checked = int(); chains = int(); mistakes = int(); walkouts = int(); streak = int(); bestStreak = int()
        bonusGranted = flag()
        kind = int().coerceIn(ACC_KIND_SIGN, ACC_KIND_CHAIN); customerLook = int().coerceIn(0, 3); result = int(); shownTotal = int()
        found = int().coerceAtLeast(0)
        patienceMs = float(); patienceMaxMs = max(1f, float()); arriveMs = float()
        step = int().coerceIn(ACC_STEP_ASK, ACC_STEP_RESULT); verdict = int().coerceIn(ACC_VERDICT_NONE, ACC_VERDICT_LEFT)
        resultMs = float(); lastPoints = int()
        clients = int(); lastKind = int().coerceIn(ACC_KIND_SIGN, ACC_KIND_CHAIN)
        blockedMs = float(); stampMs = float(); stampKey = int().coerceIn(-1, ACC_B); overMs = float()
        numbers = ints(3); solution = ints(2); invoiceLines = ints(4)
        // Une partie en cours doit avoir de quoi répondre : des signes pour une opération, des montants pour une facture.
        if (stage == ACC_STAGE_MAIN || stage == ACC_STAGE_BONUS) {
            require(
                if (kind == ACC_KIND_CHECK) invoiceLines.isNotEmpty()
                else numbers.size == solution.size + 1 && solution.isNotEmpty()
            )
        }
        found = found.coerceIn(0, solution.size)
    }

    companion object {
        private const val SAVE_VERSION = 1

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): AccountingGame {
            val game = AccountingGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
