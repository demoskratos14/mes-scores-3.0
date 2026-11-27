package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Identifiant du jeu « Caisse » (passer les articles des clients, puis les encaisser) dans la liste des jeux. */
const val CASHIER_ID = "builtin_cashier"

// Les quatre touches du jeu : la croix (gauche, droite) pour tourner l'article ou choisir, les boutons A et B pour valider.
internal const val CASHIER_LEFT = 0
internal const val CASHIER_RIGHT = 1
internal const val CASHIER_A = 2
internal const val CASHIER_B = 3

// Les étapes d'un client : il arrive, on passe un article (code-barres), on choisit un article sans code-barres,
// on vérifie l'âge (bouteille de vin), on l'encaisse, puis il repart.
internal const val CASHIER_STEP_ARRIVE = 0
internal const val CASHIER_STEP_SCAN = 1
internal const val CASHIER_STEP_PICK = 2
internal const val CASHIER_STEP_AGE = 3
internal const val CASHIER_STEP_PAY = 4
internal const val CASHIER_STEP_RESULT = 5

// Les messages qui s'affichent un instant sur l'écran (le texte est indexé par ces valeurs, voir [CashierLabels]).
internal const val CASHIER_POP_NONE = 0
internal const val CASHIER_POP_BEEP = 1 // article passé
internal const val CASHIER_POP_FAKE = 2 // étiquette qui n'est pas le bon code-barres
internal const val CASHIER_POP_EMPTY = 3 // rien à scanner sur cette face
internal const val CASHIER_POP_WRONG = 4 // mauvaise image choisie
internal const val CASHIER_POP_AGE_OK = 5 // âge vérifié
internal const val CASHIER_POP_AGE_MISSED = 6 // âge oublié
internal const val CASHIER_POP_OVER = 7 // trop de monnaie rendue
internal const val CASHIER_POP_DECLINED = 8 // carte refusée
internal const val CASHIER_POP_PAID = 9 // client encaissé
internal const val CASHIER_POP_PERFECT = 10 // client encaissé sans une erreur de paiement
internal const val CASHIER_POP_LEFT = 11 // le client perd patience et s'en va

/**
 * Caisse : le joueur tient la caisse d'un supermarché et doit passer le plus de clients possible en deux minutes.
 *
 * Pour chaque client, les articles du chariot arrivent un par un devant le scanner. Un article avec un code-barres se tourne
 * (flèches : un quart de tour) jusqu'à la face qui porte le code-barres, puis A le passe au scanner. Attention aux étiquettes jaunes de
 * promotion et aux codes à l'unité des packs, qui ne se scannent pas. Un fruit ou un pain n'a pas de code : il faut choisir sa
 * bonne image parmi quatre. Une bouteille de vin demande de vérifier l'âge du client (B). Quand le chariot est vide, le client
 * paie : par carte (arrêter le curseur dans la zone verte) ou en espèces (rendre exactement la monnaie avec les pièces).
 * La patience du client baisse, plus vite quand la file s'allonge. Le score récompense la rapidité et les séries sans erreur.
 *
 * Tout ce fichier est du Kotlin pur (sans Android) pour pouvoir être testé : voir [CashierGame].
 */
internal object Cashier {
    /** L'écran est le même que celui de Drive (720 x 405), pour reprendre sa console. */
    const val WIDTH = 720f
    const val HEIGHT = 405f

    /** Durée de la partie (millisecondes). */
    const val GAME_MS = 120_000f

    // ---------------------------------------------------------------- les articles
    /** Les articles : 0 à 7 ont un code-barres (lait, céréales, conserve, eau, confiture, pâtes, vin, pack de six bouteilles) ; 8 à 13 n'en ont pas (pomme, banane, carotte, citron, baguette, croissant). */
    const val KINDS = 14
    const val KIND_WINE = 6
    const val KIND_PACK = 7
    const val FIRST_LOOSE = 8

    /** Un article a quatre faces : 0 est celle de devant (l'image du produit) ; le code-barres est sur l'une des trois autres. */
    const val FACES = 4

    /** Le nombre d'images proposées pour un article sans code-barres. */
    const val PICK_CHOICES = 4

    /** Le prix de base de chaque article, en euros (chaque article en ajoute 0 ou 1). */
    val PRICES = intArrayOf(1, 4, 2, 1, 3, 2, 8, 6, 2, 2, 1, 1, 1, 2)

    /** Les billets que les clients donnent (le plus petit qui couvre la note) et les pièces et billets qu'on rend. */
    val NOTES = intArrayOf(5, 10, 20, 50, 100, 200)
    val DENOMS = intArrayOf(1, 2, 5, 10, 20)

    /** Vrai si l'article porte un code-barres (sinon on choisit son image). */
    fun hasBarcode(kind: Int): Boolean = kind < FIRST_LOOSE

    /** Vrai si l'article se pèse (les fruits et les légumes) : son poids s'affiche sur la balance. */
    fun isWeighed(kind: Int): Boolean = kind in FIRST_LOOSE until FIRST_LOOSE + 4

    /** Le billet donné par le client pour une note de [total] euros : le plus petit billet qui la dépasse. */
    fun noteFor(total: Int): Int = NOTES.firstOrNull { it > total } ?: (total + 1)

    /** Le plus petit nombre de pièces et billets pour rendre [amount] euros. */
    fun minCoins(amount: Int): Int {
        var left = max(0, amount)
        var count = 0
        for (i in DENOMS.indices.reversed()) {
            count += left / DENOMS[i]
            left %= DENOMS[i]
        }
        return count
    }

    // ---------------------------------------------------------------- le client
    /** Patience d'un client (millisecondes), au début puis à la fin de la partie. */
    const val PATIENCE_START_MS = 50_000f
    const val PATIENCE_END_MS = 34_000f

    /** Chaque client en plus dans la file fait baisser la patience de celui de la caisse de cette part plus vite. */
    const val QUEUE_DRAIN = 0.15f
    const val MAX_WAITING = 5

    /** Délai entre deux nouveaux clients dans la file (millisecondes), au début puis à la fin. */
    const val JOIN_START_MS = 14_000f
    const val JOIN_END_MS = 8_000f

    /** Un oubli de vérification d'âge fait perdre cette part de la patience du client. */
    const val AGE_PATIENCE = 0.25f

    // ---------------------------------------------------------------- le service
    /** Durée pendant laquelle il faut vérifier l'âge du client après avoir passé le vin. */
    const val AGE_MS = 2_200f

    /** Vitesse du curseur de la carte (parcours complet de la piste par seconde), au début puis à la fin. */
    const val TERM_SPEED_START = 0.9f
    const val TERM_SPEED_END = 1.7f

    /** Largeur de la zone verte du terminal (part de la piste), au début puis à la fin ; la moitié centrale de ses 35 % est « parfaite ». */
    const val TERM_ZONE_START = 0.24f
    const val TERM_ZONE_END = 0.13f
    const val TERM_PERFECT = 0.35f

    /** Part des clients qui paient par carte (les autres paient en espèces). */
    const val CARD_SHARE = 0.5f

    // ---------------------------------------------------------------- les points
    const val POINTS_SCAN = 10
    const val SPEED_BONUS = 10
    const val SPEED_FAST_MS = 1_500f
    const val SPEED_SLOW_MS = 4_000f
    const val STREAK_STEP = 2
    const val STREAK_CAP = 20
    const val POINTS_ID = 15
    const val POINTS_CUSTOMER = 50
    const val PATIENCE_BONUS = 50
    const val POINTS_PERFECT_PAY = 20

    const val PENALTY_FAKE = 10
    const val PENALTY_EMPTY = 2
    const val PENALTY_WRONG = 10
    const val PENALTY_NO_ID = 20
    const val PENALTY_OVER = 5
    const val PENALTY_CARD = 5
    const val PENALTY_LEFT = 30

    // ---------------------------------------------------------------- les animations (millisecondes)
    const val ARRIVE_MS = 600f
    const val TURN_MS = 120f
    const val SLIDE_MS = 220f
    const val BLOCKED_MS = 300f
    const val POP_MS = 900f
    const val RESULT_MS = 900f

    /** La partie se termine par un message ; ce temps passé, la touche A relance une partie. */
    const val RESTART_DELAY_MS = 900f
}

/** Un article du chariot : sa sorte, la face qui porte le code-barres (-1 s'il n'en a pas), la face du faux code (-1 s'il n'y en a pas), son prix et son poids. */
internal class CashierItem(
    val kind: Int,
    val codeFace: Int,
    val decoyFace: Int,
    val price: Int,
    val weightG: Int
)

/**
 * Une partie de Caisse. [tick] fait avancer le temps, [press] reçoit les touches ([CASHIER_LEFT] à [CASHIER_B]).
 */
internal class CashierGame(private val random: Random = Random.Default) {
    /** Vrai quand le temps est écoulé. */
    var over = false
        private set

    /** Le temps qui reste (millisecondes). */
    var timeLeftMs = Cashier.GAME_MS
        private set

    /** L'horloge de la partie, pour les animations (millisecondes). */
    var clockMs = 0f
        private set

    private var points = 0

    /** Le score : jamais en dessous de zéro. */
    val score: Int get() = max(0, points)

    // Les compteurs de la partie.
    var customersServed = 0
        private set
    var walkouts = 0
        private set
    var itemsScanned = 0
        private set
    var mistakes = 0
        private set
    var perfectPays = 0
        private set
    var streak = 0
        private set
    var bestStreak = 0
        private set

    // Le client en cours.
    val cart = ArrayList<CashierItem>()

    /** Le rang de l'article en cours dans le chariot (le nombre d'articles déjà passés). */
    var index = 0
        private set
    var customerLook = 0
        private set
    var patienceMs = 0f
        private set
    var patienceMaxMs = 1f
        private set

    /** Les clients qui attendent derrière celui de la caisse (au moins un). */
    var waiting = 1
        private set
    private var joinInMs = Cashier.JOIN_START_MS
    var step = CASHIER_STEP_ARRIVE
        private set

    /** Temps restant avant que le client soit arrivé à la caisse. */
    var arriveMs = 0f
        private set

    /** La face de l'article tournée vers le scanner (0 à 3), la précédente, et le temps restant de l'animation du tour. */
    var facing = 0
        private set
    var prevFacing = 0
        private set
    var turnMs = 0f
        private set

    /** Le temps passé sur l'article en cours (pour la prime de rapidité), et le temps restant du glissement du tapis. */
    var itemMs = 0f
        private set
    var slideMs = 0f
        private set

    // Le choix d'un article sans code-barres : les quatre images et celle qui est sélectionnée.
    val choices = IntArray(Cashier.PICK_CHOICES)
    var pickCursor = 0
        private set

    /** Le temps qui reste pour vérifier l'âge. */
    var ageMs = 0f
        private set

    // Le paiement.
    var payCard = false
        private set
    var total = 0
        private set

    /** Le billet donné par le client et la monnaie à rendre (espèces). */
    var given = 0
        private set
    var change = 0
        private set

    /** Le curseur du terminal de carte (0 à 1) et le milieu de sa zone verte. */
    var termPos = 0f
        private set
    private var termDir = 1f
    var termCenter = 0.5f
        private set

    /** Les pièces et billets déjà rendus (dans l'ordre), leur somme et celui qui est sélectionné. */
    val tray = ArrayList<Int>()
    var traySum = 0
        private set
    var denomCursor = 0
        private set

    // Le verdict du client et les messages.
    var resultMs = 0f
        private set
    var resultHappy = false
        private set
    var lastPoints = 0
        private set
    var popCode = CASHIER_POP_NONE
        private set
    var popPoints = 0
        private set
    var popMs = 0f
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
        over = false
        timeLeftMs = Cashier.GAME_MS
        clockMs = 0f
        points = 0
        customersServed = 0
        walkouts = 0
        itemsScanned = 0
        mistakes = 0
        perfectPays = 0
        streak = 0
        bestStreak = 0
        waiting = 1
        joinInMs = Cashier.JOIN_START_MS
        facing = 0
        prevFacing = 0
        turnMs = 0f
        itemMs = 0f
        slideMs = 0f
        popCode = CASHIER_POP_NONE
        popPoints = 0
        popMs = 0f
        blockedMs = 0f
        overMs = 0f
        lastPoints = 0
        resultHappy = false
        resultMs = 0f
        nextCustomer()
    }

    // ---------------------------------------------------------------- difficulté

    /** La difficulté de 0 (début de la partie) à 1 (dernière seconde). */
    val difficulty: Float get() = (1f - timeLeftMs / Cashier.GAME_MS).coerceIn(0f, 1f)

    private fun lerp(from: Float, to: Float): Float = from + (to - from) * difficulty

    /** La largeur actuelle de la zone verte du terminal de carte (part de la piste). */
    val termZoneW: Float get() = lerp(Cashier.TERM_ZONE_START, Cashier.TERM_ZONE_END)

    // ---------------------------------------------------------------- état

    /** L'article en cours, ou null quand le chariot est vide (paiement, départ du client). */
    val currentItem: CashierItem? get() = cart.getOrNull(index)

    /** Vrai quand le message de fin est assez resté à l'écran pour qu'un appui sur A relance une partie. */
    val canRestart: Boolean get() = over && overMs > Cashier.RESTART_DELAY_MS

    // ---------------------------------------------------------------- temps

    /** Fait avancer la partie de [ms] millisecondes. */
    fun tick(ms: Float) {
        val dt = max(0f, ms)
        clockMs += dt
        blockedMs = max(0f, blockedMs - dt)
        popMs = max(0f, popMs - dt)
        turnMs = max(0f, turnMs - dt)
        slideMs = max(0f, slideMs - dt)
        if (over) {
            overMs += dt
            return
        }
        timeLeftMs -= dt
        if (timeLeftMs <= 0f) {
            timeLeftMs = 0f
            over = true
            overMs = 0f
            return
        }
        // La file s'allonge : un client de plus de temps en temps, jusqu'à cinq qui attendent.
        joinInMs -= dt
        if (joinInMs <= 0f) {
            if (waiting < Cashier.MAX_WAITING) waiting++
            joinInMs = lerp(Cashier.JOIN_START_MS, Cashier.JOIN_END_MS)
        }
        when (step) {
            CASHIER_STEP_ARRIVE -> {
                arriveMs -= dt
                if (arriveMs <= 0f) showItem()
            }
            CASHIER_STEP_RESULT -> {
                resultMs -= dt
                if (resultMs <= 0f) nextCustomer()
            }
            else -> {
                itemMs += dt
                drainPatience(dt)
                if (step == CASHIER_STEP_AGE) {
                    ageMs -= dt
                    if (ageMs <= 0f) ageMissed()
                } else if (step == CASHIER_STEP_PAY && payCard) {
                    moveTerminal(dt)
                }
            }
        }
    }

    private fun drainPatience(dt: Float) {
        patienceMs -= dt * (1f + Cashier.QUEUE_DRAIN * (waiting - 1))
        if (patienceMs <= 0f) {
            patienceMs = 0f
            customerLeaves()
        }
    }

    /** Le client perd patience : il s'en va sans payer, ce qui coûte des points. */
    private fun customerLeaves() {
        walkouts++
        streak = 0
        points -= Cashier.PENALTY_LEFT
        lastPoints = -Cashier.PENALTY_LEFT
        pop(CASHIER_POP_LEFT, -Cashier.PENALTY_LEFT)
        step = CASHIER_STEP_RESULT
        resultMs = Cashier.RESULT_MS
        resultHappy = false
    }

    private fun pop(code: Int, gained: Int) {
        popCode = code
        popPoints = gained
        popMs = Cashier.POP_MS
    }

    // ---------------------------------------------------------------- les clients et leurs chariots

    /** Un nouveau client avec son chariot : le suivant de la file, ou un client qui entre quand la file est vide. */
    private fun nextCustomer() {
        waiting = max(1, waiting - 1)
        customerLook = random.nextInt(4)
        patienceMaxMs = lerp(Cashier.PATIENCE_START_MS, Cashier.PATIENCE_END_MS)
        patienceMs = patienceMaxMs
        cart.clear()
        val low = lerp(3f, 5f).roundToInt()
        val high = lerp(4f, 8f).roundToInt()
        val count = low + random.nextInt(max(1, high - low + 1))
        repeat(count) { cart.add(makeItem()) }
        index = 0
        total = 0
        given = 0
        change = 0
        tray.clear()
        traySum = 0
        denomCursor = 0
        step = CASHIER_STEP_ARRIVE
        arriveMs = Cashier.ARRIVE_MS
        itemMs = 0f
    }

    /** Un article au hasard : de plus en plus d'étiquettes pièges, de vin et d'articles sans code quand la partie avance. */
    private fun makeItem(): CashierItem {
        val kind = when {
            random.nextFloat() < lerp(0.18f, 0.30f) -> Cashier.FIRST_LOOSE + random.nextInt(Cashier.KINDS - Cashier.FIRST_LOOSE)
            random.nextFloat() < lerp(0.07f, 0.15f) -> Cashier.KIND_WINE
            random.nextFloat() < 0.10f -> Cashier.KIND_PACK
            else -> random.nextInt(Cashier.KIND_WINE)
        }
        val price = Cashier.PRICES[kind] + random.nextInt(2)
        if (!Cashier.hasBarcode(kind)) {
            val weight = if (Cashier.isWeighed(kind)) 300 + random.nextInt(14) * 50 else 0
            return CashierItem(kind, -1, -1, price, weight)
        }
        val codeFace = 1 + random.nextInt(3)
        // Un pack a toujours un second code (celui d'une bouteille) ; les autres articles ont parfois une étiquette de promotion.
        val decoyChance = if (kind == Cashier.KIND_PACK) 1f else lerp(0.10f, 0.45f)
        val decoyFace = if (random.nextFloat() < decoyChance) {
            val options = (1..3).filter { it != codeFace }
            options[random.nextInt(options.size)]
        } else {
            -1
        }
        return CashierItem(kind, codeFace, decoyFace, price, 0)
    }

    /** Pose l'article en cours devant le scanner : tourné au hasard s'il a un code-barres, ou avec ses quatre images à choisir. */
    private fun showItem() {
        val item = cart[index]
        itemMs = 0f
        if (Cashier.hasBarcode(item.kind)) {
            step = CASHIER_STEP_SCAN
            facing = random.nextInt(Cashier.FACES)
            prevFacing = facing
            turnMs = 0f
        } else {
            step = CASHIER_STEP_PICK
            val others = (Cashier.FIRST_LOOSE until Cashier.KINDS).filter { it != item.kind }.shuffled(random)
            val slot = random.nextInt(Cashier.PICK_CHOICES)
            var next = 0
            for (i in 0 until Cashier.PICK_CHOICES) {
                choices[i] = if (i == slot) item.kind else others[next++]
            }
            pickCursor = 0
        }
    }

    /** L'article est passé : au suivant, ou au paiement quand le chariot est vide. */
    private fun nextItem() {
        index++
        slideMs = Cashier.SLIDE_MS
        if (index >= cart.size) startPay() else showItem()
    }

    // ---------------------------------------------------------------- joueur

    /** Une touche ([CASHIER_LEFT] à [CASHIER_B]) vient d'être pressée. */
    fun press(key: Int) {
        if (over) return
        when (step) {
            CASHIER_STEP_SCAN -> when (key) {
                CASHIER_LEFT -> turn(-1)
                CASHIER_RIGHT -> turn(1)
                CASHIER_A -> scan()
            }
            CASHIER_STEP_PICK -> when (key) {
                CASHIER_LEFT -> movePick(-1)
                CASHIER_RIGHT -> movePick(1)
                CASHIER_A -> validatePick()
            }
            CASHIER_STEP_AGE -> if (key == CASHIER_B) checkId()
            CASHIER_STEP_PAY -> if (payCard) {
                if (key == CASHIER_A || key == CASHIER_B) stopTerminal()
            } else {
                when (key) {
                    CASHIER_LEFT -> moveDenom(-1)
                    CASHIER_RIGHT -> moveDenom(1)
                    CASHIER_A -> addCoin()
                    CASHIER_B -> removeCoin()
                }
            }
            else -> {}
        }
    }

    /** Un quart de tour de l'article : [direction] vaut 1 (face suivante) ou -1 (face précédente). */
    private fun turn(direction: Int) {
        prevFacing = facing
        facing = (facing + direction + Cashier.FACES) % Cashier.FACES
        turnMs = Cashier.TURN_MS
    }

    /** Une erreur : [penalty] points en moins, la série est cassée. */
    private fun fail(code: Int, penalty: Int) {
        mistakes++
        streak = 0
        points -= penalty
        blockedMs = Cashier.BLOCKED_MS
        pop(code, -penalty)
    }

    /** A sur l'article : le bip si le code-barres est sous le scanner, sinon une erreur (faux code, ou rien du tout). */
    private fun scan() {
        val item = cart[index]
        when {
            facing == item.codeFace -> scanned(item)
            facing == item.decoyFace -> fail(CASHIER_POP_FAKE, Cashier.PENALTY_FAKE)
            else -> fail(CASHIER_POP_EMPTY, Cashier.PENALTY_EMPTY)
        }
    }

    /** L'article est enregistré : ses points (plus vite, plus de points) puis, pour le vin, la vérification d'âge. */
    private fun scanned(item: CashierItem) {
        itemsScanned++
        total += item.price
        streak++
        bestStreak = max(bestStreak, streak)
        var gained = Cashier.POINTS_SCAN
        val speed = ((Cashier.SPEED_SLOW_MS - itemMs) / (Cashier.SPEED_SLOW_MS - Cashier.SPEED_FAST_MS)).coerceIn(0f, 1f)
        gained += (speed * Cashier.SPEED_BONUS).roundToInt()
        gained += min(Cashier.STREAK_CAP, (streak - 1) * Cashier.STREAK_STEP)
        points += gained
        pop(CASHIER_POP_BEEP, gained)
        if (item.kind == Cashier.KIND_WINE) {
            step = CASHIER_STEP_AGE
            ageMs = Cashier.AGE_MS
        } else {
            nextItem()
        }
    }

    private fun movePick(direction: Int) {
        pickCursor = (pickCursor + direction + Cashier.PICK_CHOICES) % Cashier.PICK_CHOICES
    }

    private fun validatePick() {
        val item = cart[index]
        if (choices[pickCursor] == item.kind) scanned(item) else fail(CASHIER_POP_WRONG, Cashier.PENALTY_WRONG)
    }

    /** B sur la bouteille de vin : l'âge du client est vérifié à temps. */
    private fun checkId() {
        points += Cashier.POINTS_ID
        pop(CASHIER_POP_AGE_OK, Cashier.POINTS_ID)
        nextItem()
    }

    /** Le temps pour vérifier l'âge est passé : des points en moins, et le client perd de la patience. */
    private fun ageMissed() {
        mistakes++
        streak = 0
        points -= Cashier.PENALTY_NO_ID
        patienceMs = max(0f, patienceMs - patienceMaxMs * Cashier.AGE_PATIENCE)
        blockedMs = Cashier.BLOCKED_MS
        pop(CASHIER_POP_AGE_MISSED, -Cashier.PENALTY_NO_ID)
        nextItem()
    }

    // ---------------------------------------------------------------- le paiement

    /** Le chariot est vide : le client paie par carte ou en espèces. */
    private fun startPay() {
        step = CASHIER_STEP_PAY
        itemMs = 0f
        payCard = random.nextFloat() < Cashier.CARD_SHARE
        if (payCard) {
            termPos = 0f
            termDir = 1f
            termCenter = 0.25f + random.nextFloat() * 0.5f
        } else {
            given = Cashier.noteFor(total)
            change = given - total
            tray.clear()
            traySum = 0
            denomCursor = Cashier.DENOMS.size / 2
        }
    }

    private fun moveTerminal(dt: Float) {
        val speed = lerp(Cashier.TERM_SPEED_START, Cashier.TERM_SPEED_END)
        var p = termPos + termDir * speed * dt / 1000f
        if (p >= 1f) {
            p = 1f - (p - 1f)
            termDir = -1f
        } else if (p <= 0f) {
            p = -p
            termDir = 1f
        }
        termPos = p.coerceIn(0f, 1f)
    }

    /** Arrête le curseur de la carte : dans la zone verte le paiement passe, au milieu c'est parfait ; sinon la carte est refusée et le curseur repart. */
    private fun stopTerminal() {
        val half = termZoneW / 2f
        val gap = abs(termPos - termCenter)
        if (gap <= half) {
            paid(perfect = gap <= half * Cashier.TERM_PERFECT)
        } else {
            fail(CASHIER_POP_DECLINED, Cashier.PENALTY_CARD)
        }
    }

    private fun moveDenom(direction: Int) {
        denomCursor = (denomCursor + direction + Cashier.DENOMS.size) % Cashier.DENOMS.size
    }

    /** Rend la pièce ou le billet choisi : quand la somme est exacte le paiement est fini ; un de trop est refusé. */
    private fun addCoin() {
        val value = Cashier.DENOMS[denomCursor]
        if (traySum + value > change) {
            fail(CASHIER_POP_OVER, Cashier.PENALTY_OVER)
            return
        }
        tray.add(value)
        traySum += value
        if (traySum == change) paid(perfect = tray.size <= Cashier.minCoins(change))
    }

    /** Reprend la dernière pièce rendue. */
    private fun removeCoin() {
        if (tray.isEmpty()) return
        traySum -= tray.removeAt(tray.size - 1)
    }

    /** Le client a payé : les points du client, une prime de patience et une prime de paiement parfait. */
    private fun paid(perfect: Boolean) {
        customersServed++
        var gained = Cashier.POINTS_CUSTOMER
        gained += (patienceMs / patienceMaxMs * Cashier.PATIENCE_BONUS).roundToInt()
        if (perfect) {
            gained += Cashier.POINTS_PERFECT_PAY
            perfectPays++
        }
        points += gained
        lastPoints = gained
        pop(if (perfect) CASHIER_POP_PERFECT else CASHIER_POP_PAID, gained)
        step = CASHIER_STEP_RESULT
        resultMs = Cashier.RESULT_MS
        resultHappy = true
    }

    // ---------------------------------------------------------------- sauvegarde

    /** La partie sous forme de liste de nombres, pour la garder quand l'écran tourne (voir [restore]). */
    fun save(): List<Double> {
        val out = ArrayList<Double>()
        fun put(value: Number) { out.add(value.toDouble()) }
        fun put(value: Boolean) { out.add(if (value) 1.0 else 0.0) }
        put(SAVE_VERSION)
        put(over); put(timeLeftMs); put(clockMs); put(points)
        put(customersServed); put(walkouts); put(itemsScanned); put(mistakes); put(perfectPays); put(streak); put(bestStreak)
        put(index); put(customerLook); put(patienceMs); put(patienceMaxMs); put(waiting); put(joinInMs)
        put(step); put(arriveMs); put(facing); put(prevFacing); put(turnMs); put(itemMs); put(slideMs)
        put(pickCursor); put(ageMs)
        put(payCard); put(total); put(given); put(change); put(termPos); put(termDir); put(termCenter); put(denomCursor)
        put(resultMs); put(resultHappy); put(lastPoints); put(popCode); put(popPoints); put(popMs); put(blockedMs); put(overMs)
        for (choice in choices) put(choice)
        put(tray.size)
        for (coin in tray) put(coin)
        put(cart.size)
        for (item in cart) {
            put(item.kind); put(item.codeFace); put(item.decoyFace); put(item.price); put(item.weightG)
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
        over = flag(); timeLeftMs = float().coerceIn(0f, Cashier.GAME_MS); clockMs = float(); points = int()
        customersServed = int(); walkouts = int(); itemsScanned = int(); mistakes = int(); perfectPays = int(); streak = int(); bestStreak = int()
        index = int(); customerLook = int().coerceIn(0, 3); patienceMs = float(); patienceMaxMs = max(1f, float())
        waiting = int().coerceIn(1, Cashier.MAX_WAITING); joinInMs = float()
        step = int().coerceIn(CASHIER_STEP_ARRIVE, CASHIER_STEP_RESULT); arriveMs = float()
        facing = int().coerceIn(0, Cashier.FACES - 1); prevFacing = int().coerceIn(0, Cashier.FACES - 1); turnMs = float()
        itemMs = float(); slideMs = float()
        pickCursor = int().coerceIn(0, Cashier.PICK_CHOICES - 1); ageMs = float()
        payCard = flag(); total = int(); given = int(); change = int(); termPos = float().coerceIn(0f, 1f)
        termDir = if (float() >= 0f) 1f else -1f
        termCenter = float().coerceIn(0f, 1f); denomCursor = int().coerceIn(0, Cashier.DENOMS.size - 1)
        resultMs = float(); resultHappy = flag(); lastPoints = int()
        popCode = int().coerceIn(CASHIER_POP_NONE, CASHIER_POP_LEFT); popPoints = int(); popMs = float(); blockedMs = float(); overMs = float()
        for (i in 0 until Cashier.PICK_CHOICES) choices[i] = int().coerceIn(0, Cashier.KINDS - 1)
        tray.clear()
        traySum = 0
        repeat(int().coerceIn(0, 64)) {
            val coin = int()
            tray.add(coin)
            traySum += coin
        }
        cart.clear()
        repeat(int().coerceIn(0, 16)) {
            val kind = int().coerceIn(0, Cashier.KINDS - 1)
            val codeFace = int().coerceIn(-1, Cashier.FACES - 1)
            val decoyFace = int().coerceIn(-1, Cashier.FACES - 1)
            val price = int()
            val weight = int()
            cart.add(CashierItem(kind, codeFace, decoyFace, price, weight))
        }
        // Une partie en cours doit avoir un chariot et un article à l'étape où l'on en a besoin.
        require(cart.isNotEmpty())
        require(index in 0..cart.size)
        if (step == CASHIER_STEP_SCAN || step == CASHIER_STEP_PICK || step == CASHIER_STEP_AGE) require(index < cart.size)
    }

    companion object {
        private const val SAVE_VERSION = 1

        /** Retrouve une partie sauvegardée par [save] ; une partie neuve si la liste ne convient pas. */
        fun restore(values: List<Double>): CashierGame {
            val game = CashierGame()
            try {
                game.load(values)
            } catch (_: Exception) {
                game.reset()
            }
            return game
        }
    }
}
