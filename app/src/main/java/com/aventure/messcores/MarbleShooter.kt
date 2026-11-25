package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Identifiant du Marble Shooter parmi les jeux prédéfinis (écran à part : à venir, voir [MarbleGame]). */
internal const val MARBLE_ID = "builtin_marble"

/**
 * Les façons de jouer au Marble Shooter. [CLASSIC] : les cartes et les billes viennent du hasard. [DAILY] : la carte et la suite
 * de billes de chaque niveau sont les mêmes pour tout le monde, un jour donné (elles viennent de la date).
 */
internal enum class MarbleMode {
    CLASSIC, DAILY;

    companion object {
        /** Le mode de numéro [index] (celui qui sert de clé dans les préférences et la sauvegarde) ; le classique si le numéro n'existe pas. */
        fun fromIndex(index: Int): MarbleMode = values().getOrElse(index) { CLASSIC }
    }
}

/**
 * Réglages et calculs du Marble Shooter.
 *
 * Le terrain est celui du tower defense ([TdMap]) : des cases d'une unité. Une bille mesure [DIAMETER] unité ; sa position est
 * une distance `s` le long du chemin (voir [MarblePath]), de l'entrée vers le trou. Les couleurs sont des numéros de 0 à
 * [PALETTE] - 1.
 */
internal object Marble {

    /** Nombre de couleurs possibles, au total. */
    const val PALETTE = 6

    /** Les billes spéciales : la bombe fait éclater les billes autour de l'endroit touché, l'arc-en-ciel prend la couleur qui fait le plus gros groupe, l'éclair fait éclater toutes les billes de la couleur touchée. */
    const val BOMB = 6
    const val RAINBOW = 7
    const val LIGHTNING = 8

    /** Nombre de numéros possibles pour une bille du lanceur : les couleurs et les billes spéciales. */
    const val KINDS = 9

    /** « Charge » à atteindre pour qu'une bille spéciale arrive : chaque bille éclatée en donne 1. */
    const val SPECIAL_THRESHOLD = 18

    /** Rayon de l'explosion d'une bombe, en cases : deux billes de chaque côté de la bille touchée. */
    const val BOMB_RADIUS = 1.2f

    const val DIAMETER = 0.55f
    const val RADIUS = DIAMETER / 2f

    /** Nombre de billes de même couleur qui font éclater un groupe. */
    const val MIN_RUN = 3

    const val POP_POINTS = 10

    /** Points en plus pour chaque bille au-delà de [MIN_RUN] dans un même groupe. */
    const val RUN_BONUS = 10

    /** Points gagnés en vidant la chaîne. */
    const val LEVEL_BONUS = 500

    /** Longueur du chemin ajoutée avant la première case : la chaîne sort de l'écran par là. */
    const val ENTRY_LEAD = 1.5f

    /** Vitesse d'une bille tirée, en unités par seconde, et longueur d'un petit pas de calcul. */
    const val SHOT_SPEED = 16f
    const val SUBSTEP = 0.1f

    /** Distance entre deux centres en dessous de laquelle une bille tirée touche une bille de la chaîne (un peu moins qu'un diamètre : on pardonne). */
    const val HIT_DISTANCE = DIAMETER * 0.9f

    /** Vitesse à laquelle le train de devant recule vers la queue quand un groupe a éclaté (voir [MarbleGame]). */
    const val RETREAT_SPEED = 5f

    /** Vitesse d'une bille sortie seule de l'entrée (la queue avait éclaté) qui fonce rejoindre la chaîne, et vitesse à laquelle une bille insérée repousse celles qui la précèdent. */
    const val CATCH_UP_SPEED = 6f
    const val SQUEEZE_SPEED = 5f

    /** Au début du niveau, la chaîne entre vite ([RUSH_SPEED]) jusqu'à ce que sa tête ait fait [RUSH_UNTIL] unités. */
    const val RUSH_SPEED = 3f
    const val RUSH_UNTIL = 6f

    /** Écart toléré (en unités) pour dire que deux billes se touchent. */
    const val TOUCH = 0.02f

    /** Une nouvelle bille se colle à la queue si elle y tient à moins de [SPAWN_SNAP] près ; sinon elle sort seule de l'entrée. */
    const val SPAWN_SNAP = 0.15f

    /** Le nombre de couleurs du niveau [level] : 4 au début, 6 à partir du niveau 5. */
    fun colorsFor(level: Int): Int = (3 + (level.coerceAtLeast(1) + 1) / 2).coerceAtMost(PALETTE)

    /** Le nombre de billes de la chaîne au niveau [level]. */
    fun countFor(level: Int): Int = (30 + 5 * level.coerceAtLeast(1)).coerceAtMost(70)

    /** La vitesse de la chaîne au niveau [level], en unités par seconde. */
    fun speedFor(level: Int): Float = (0.55f + 0.05f * (level.coerceAtLeast(1) - 1)).coerceAtMost(1.3f)

    /** Les billes spéciales qui peuvent arriver au niveau [level] : la bombe dès le 2, l'arc-en-ciel dès le 3, l'éclair dès le 4. */
    fun unlockedSpecials(level: Int): List<Int> = when {
        level >= 4 -> listOf(BOMB, RAINBOW, LIGHTNING)
        level >= 3 -> listOf(BOMB, RAINBOW)
        level >= 2 -> listOf(BOMB)
        else -> emptyList()
    }

    /** Vrai si le numéro [kind] est celui d'une bille spéciale. */
    fun isSpecial(kind: Int): Boolean = kind >= PALETTE

    fun dailySeed(year: Int, month: Int, day: Int): Int = year * 10000 + month * 100 + day

    /**
     * Les points d'un groupe de [count] billes qui éclate à la [chain]-ième réaction d'affilée : 10 par bille, 10 de plus pour
     * chaque bille au-delà de trois, le tout multiplié par le rang de la réaction (1 pour le tir, 2 quand le trou refermé fait
     * éclater un autre groupe, etc.).
     */
    fun chainPoints(count: Int, chain: Int): Int =
        (count * POP_POINTS + max(0, count - MIN_RUN) * RUN_BONUS) * max(1, chain)
}

/**
 * La carte d'un niveau : un chemin sur la grille du tower defense ([COLS] × [ROWS] cases, une case = une unité), et des décors.
 * Le chemin entre par un bord du terrain (de là, la chaîne arrive du dehors) et finit n'importe où, au trou. Il ne se croise ni
 * ne se frôle : deux cases du chemin qui ne se suivent pas ne se touchent jamais par un côté, il reste donc toujours une case
 * libre entre deux morceaux. Le lanceur est au centre, sur la case ([SHOOTER_ROW], [SHOOTER_COL]), que le chemin évite avec ses
 * voisines. La carte ne dépend que de la graine.
 */
internal class MarbleMap private constructor(
    val seed: Int,
    /** Les cases du chemin, de l'entrée au trou (numéro = rangée × [COLS] + colonne). */
    val path: IntArray,
    /** Par case : -1 (rien) ou le rang du décor dans l'image des décors. Une case avec décor est bloquée. */
    val decor: IntArray,
    /** Le pas de la case d'entrée vers l'extérieur du terrain : c'est par là que la chaîne arrive. */
    val entryDx: Int,
    val entryDy: Int
) {
    /** Le thème de la carte (marais ou été, voir [TdMap.theme]) : il ne change que les images. Une graine donne toujours le même. */
    val theme: Int = TdMap.themeOf(seed)

    private val onPath = BooleanArray(COLS * ROWS).also { grid -> path.forEach { grid[it] = true } }

    val entryRow: Int get() = path.first() / COLS
    val entryCol: Int get() = path.first() % COLS
    val holeRow: Int get() = path.last() / COLS
    val holeCol: Int get() = path.last() % COLS

    fun isPath(row: Int, col: Int): Boolean = row in 0 until ROWS && col in 0 until COLS && onPath[row * COLS + col]

    /** Vrai si on peut y poser un décor ou un lanceur : ni chemin ni décor. */
    fun isBuildable(row: Int, col: Int): Boolean =
        row in 0 until ROWS && col in 0 until COLS && !onPath[row * COLS + col] && decor[row * COLS + col] < 0

    /** Comme [isPath], mais le chemin se prolonge d'une case hors du terrain, à l'entrée. */
    private fun pathLike(row: Int, col: Int): Boolean {
        if (row in 0 until ROWS && col in 0 until COLS) return onPath[row * COLS + col]
        return row == entryRow + entryDy && col == entryCol + entryDx
    }

    /** La signature de la tuile à poser sur la case (voir [TdMap.tileSignature]) : neuf bits, à 1 pour la terre claire du chemin. */
    fun tileSignature(row: Int, col: Int): Int {
        if (!isPath(row, col)) return 0
        val up = pathLike(row - 1, col)
        val down = pathLike(row + 1, col)
        val left = pathLike(row, col - 1)
        val right = pathLike(row, col + 1)
        val bits = booleanArrayOf(
            up && left && pathLike(row - 1, col - 1), up, up && right && pathLike(row - 1, col + 1),
            left, true, right,
            down && left && pathLike(row + 1, col - 1), down, down && right && pathLike(row + 1, col + 1)
        )
        var sig = 0
        for (k in bits.indices) if (bits[k]) sig = sig or (1 shl k)
        return sig
    }

    /** Le rang de la tuile du sol de la case dans l'image des tuiles du tower defense. */
    fun tileIndex(row: Int, col: Int): Int {
        if (!isPath(row, col)) return TdTiles.SWAMP[(row + col) and 1]
        return TdTiles.best(tileSignature(row, col), theme)
    }

    companion object {
        const val COLS = TdMap.COLS
        const val ROWS = TdMap.ROWS

        /** La case du lanceur, au centre du terrain. */
        const val SHOOTER_ROW = 7
        const val SHOOTER_COL = 4

        /** Nombre de cases du chemin : tiré au hasard entre les deux. */
        const val MIN_CELLS = 30
        const val MAX_CELLS = 50

        private const val ATTEMPTS = 40
        private const val NODE_BUDGET = 3000
        private const val STRAIGHT_PERCENT = 65
        private const val SCENERY_PERCENT = 14

        // Haut, droite, bas, gauche.
        private val DR = intArrayOf(-1, 0, 1, 0)
        private val DC = intArrayOf(0, 1, 0, -1)

        /** Vrai pour la case du lanceur et ses huit voisines : le chemin n'y passe jamais. */
        fun isReserved(row: Int, col: Int): Boolean = abs(row - SHOOTER_ROW) <= 1 && abs(col - SHOOTER_COL) <= 1

        fun generate(seed: Int): MarbleMap {
            val random = Random(seed)
            var cells: IntArray? = null
            var attempt = 0
            while (cells == null && attempt < ATTEMPTS) {
                cells = walk(random)
                attempt++
            }
            // Le chemin tracé à la main ne sert que si le hasard n'a rien donné, ce qui n'arrive en pratique jamais.
            return build(seed, cells ?: serpentine(random.nextBoolean()), random)
        }

        /** Un chemin tracé au hasard en partant d'un bord, ou `null` si l'essai s'est perdu dans une impasse. */
        private fun walk(random: Random): IntArray? {
            val starts = ArrayList<Int>()
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val edge = row == 0 || row == ROWS - 1 || col == 0 || col == COLS - 1
                    if (edge && !isReserved(row, col)) starts.add(row * COLS + col)
                }
            }
            val target = MIN_CELLS + random.nextInt(MAX_CELLS - MIN_CELLS + 1)
            val path = IntArray(target)
            val onPath = BooleanArray(COLS * ROWS)
            // À chaque case posée, les directions à essayer dans l'ordre, et la prochaine à essayer.
            val order = Array(target) { IntArray(4) }
            val next = IntArray(target)
            path[0] = starts[random.nextInt(starts.size)]
            onPath[path[0]] = true
            order[0] = shuffledDirections(random)
            var depth = 0
            var budget = NODE_BUDGET
            while (depth < target - 1 && budget-- > 0) {
                if (next[depth] >= 4) {
                    // Plus rien à essayer ici : on reprend la case d'avant.
                    onPath[path[depth]] = false
                    if (depth == 0) return null
                    depth--
                    continue
                }
                val dir = order[depth][next[depth]++]
                val from = path[depth]
                val row = from / COLS + DR[dir]
                val col = from % COLS + DC[dir]
                if (!canStep(row, col, from, onPath)) continue
                depth++
                path[depth] = row * COLS + col
                onPath[path[depth]] = true
                order[depth] = directionsFor(dir, random)
                next[depth] = 0
            }
            return if (depth == target - 1) path else null
        }

        /** On peut aller en ([row], [col]) depuis [from] : c'est libre, hors du lanceur, et ça ne touche aucune autre case du chemin que [from]. */
        private fun canStep(row: Int, col: Int, from: Int, onPath: BooleanArray): Boolean {
            if (row !in 0 until ROWS || col !in 0 until COLS || isReserved(row, col)) return false
            if (onPath[row * COLS + col]) return false
            for (d in 0 until 4) {
                val r = row + DR[d]
                val c = col + DC[d]
                if (r !in 0 until ROWS || c !in 0 until COLS) continue
                val neighbor = r * COLS + c
                if (neighbor != from && onPath[neighbor]) return false
            }
            return true
        }

        private fun shuffledDirections(random: Random): IntArray {
            val dirs = intArrayOf(0, 1, 2, 3)
            for (i in 3 downTo 1) {
                val j = random.nextInt(i + 1)
                val keep = dirs[i]
                dirs[i] = dirs[j]
                dirs[j] = keep
            }
            return dirs
        }

        /** Les directions à essayer après avoir avancé dans la direction [last] : le plus souvent tout droit d'abord, pour de longues lignes. */
        private fun directionsFor(last: Int, random: Random): IntArray {
            val dirs = shuffledDirections(random)
            if (random.nextInt(100) >= STRAIGHT_PERCENT) return dirs
            return intArrayOf(last) + dirs.filter { it != last }
        }

        /** Un chemin en lacets, sûr, qui évite le centre : trois rangées en haut, le long du bord droit, puis deux rangées en bas. */
        private fun serpentine(mirror: Boolean): IntArray {
            val cells = ArrayList<Int>()
            fun add(row: Int, col: Int) = cells.add(row * COLS + if (mirror) COLS - 1 - col else col)
            for (col in 0 until COLS) add(0, col)
            add(1, COLS - 1)
            for (col in COLS - 1 downTo 0) add(2, col)
            add(3, 0)
            for (col in 0 until COLS) add(4, col)
            for (row in 5..9) add(row, COLS - 1)
            for (col in COLS - 1 downTo 0) add(10, col)
            add(11, 0)
            for (col in 0 until COLS) add(12, col)
            return cells.toIntArray()
        }

        private fun build(seed: Int, path: IntArray, random: Random): MarbleMap {
            val onPath = BooleanArray(COLS * ROWS).also { grid -> path.forEach { grid[it] = true } }
            val first = path.first()
            // Les côtés par où la case d'entrée donne sur l'extérieur : en coin, il y en a deux, on en tire un.
            val outward = ArrayList<Pair<Int, Int>>()
            if (first / COLS == 0) outward.add(0 to -1)
            if (first / COLS == ROWS - 1) outward.add(0 to 1)
            if (first % COLS == 0) outward.add(-1 to 0)
            if (first % COLS == COLS - 1) outward.add(1 to 0)
            val (dx, dy) = outward[random.nextInt(outward.size)]
            val decor = IntArray(COLS * ROWS) { -1 }
            // Un repère à côté de l'entrée : la première case libre autour d'elle.
            for (d in 0 until 4) {
                val r = first / COLS + DR[d]
                val c = first % COLS + DC[d]
                if (r !in 0 until ROWS || c !in 0 until COLS || onPath[r * COLS + c] || isReserved(r, c)) continue
                decor[r * COLS + c] = TdMap.DECOR_CAVE
                break
            }
            // Des décors au hasard, jamais collés au chemin ni au lanceur.
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val cell = row * COLS + col
                    if (onPath[cell] || decor[cell] >= 0 || isReserved(row, col)) continue
                    val nearPath = (-1..1).any { dr ->
                        (-1..1).any { dc ->
                            val r = row + dr
                            val c = col + dc
                            r in 0 until ROWS && c in 0 until COLS && onPath[r * COLS + c]
                        }
                    }
                    if (!nearPath && random.nextInt(100) < SCENERY_PERCENT) decor[cell] = random.nextInt(TdMap.SCENERY_COUNT)
                }
            }
            return MarbleMap(seed, path, decor, dx, dy)
        }
    }
}

/**
 * Le chemin des billes : une ligne brisée, avec la position et la direction d'un point donné par sa distance `s` depuis le
 * départ. Le départ est hors du terrain, l'arrivée est le trou.
 */
internal class MarblePath(private val xs: FloatArray, private val ys: FloatArray) {

    private val cum = FloatArray(xs.size)

    init {
        require(xs.size >= 2 && xs.size == ys.size) { "un chemin a au moins deux points" }
        for (i in 1 until xs.size) {
            val dx = xs[i] - xs[i - 1]
            val dy = ys[i] - ys[i - 1]
            cum[i] = cum[i - 1] + sqrt(dx * dx + dy * dy)
        }
    }

    /** La longueur du chemin : la distance `s` du trou. */
    val length: Float get() = cum[cum.size - 1]

    val holeX: Float get() = xs[xs.size - 1]
    val holeY: Float get() = ys[ys.size - 1]

    private fun segment(s: Float): Int {
        var i = 0
        val last = cum.size - 2
        while (i < last && cum[i + 1] < s) i++
        return i
    }

    private fun fraction(i: Int, s: Float): Float {
        val len = cum[i + 1] - cum[i]
        return if (len > 0f) (s - cum[i]) / len else 0f
    }

    /** La position du point à la distance [s] (au départ ou au trou si [s] sort du chemin). */
    fun x(s: Float): Float {
        val c = s.coerceIn(0f, length)
        val i = segment(c)
        return xs[i] + (xs[i + 1] - xs[i]) * fraction(i, c)
    }

    fun y(s: Float): Float {
        val c = s.coerceIn(0f, length)
        val i = segment(c)
        return ys[i] + (ys[i + 1] - ys[i]) * fraction(i, c)
    }

    /** La direction du chemin au point [s] (un vecteur de longueur 1, vers le trou). */
    fun dirX(s: Float): Float {
        val i = segment(s.coerceIn(0f, length))
        val len = cum[i + 1] - cum[i]
        return if (len > 0f) (xs[i + 1] - xs[i]) / len else 0f
    }

    fun dirY(s: Float): Float {
        val i = segment(s.coerceIn(0f, length))
        val len = cum[i + 1] - cum[i]
        return if (len > 0f) (ys[i + 1] - ys[i]) / len else 0f
    }

    companion object {
        /**
         * Le chemin de la carte [map] : le départ est [Marble.ENTRY_LEAD] unités avant la case d'entrée, hors du terrain (dans le
         * sens de [MarbleMap.entryDx], [MarbleMap.entryDy]), puis le chemin passe par le centre de chaque case jusqu'au trou, au
         * centre de la dernière.
         */
        fun fromMap(map: MarbleMap): MarblePath {
            val n = map.path.size
            val xs = FloatArray(n + 1)
            val ys = FloatArray(n + 1)
            xs[0] = map.entryCol + 0.5f + map.entryDx * Marble.ENTRY_LEAD
            ys[0] = map.entryRow + 0.5f + map.entryDy * Marble.ENTRY_LEAD
            for (i in 0 until n) {
                xs[i + 1] = map.path[i] % MarbleMap.COLS + 0.5f
                ys[i + 1] = map.path[i] / MarbleMap.COLS + 0.5f
            }
            return MarblePath(xs, ys)
        }
    }
}

/**
 * Une bille qui vient d'éclater, pour que l'écran l'anime : où elle était (en unités du terrain) et sa couleur. Quand une bombe
 * ou un éclair a fait éclater des billes, un dernier repère de couleur [Marble.BOMB] ou [Marble.LIGHTNING] marque l'endroit touché.
 */
internal class MarbleEffect(val x: Float, val y: Float, val color: Int)

/**
 * Une partie de Marble Shooter : une chaîne de billes de couleur roule sur le chemin d'une carte du tower defense, de l'entrée
 * vers le trou, et un lanceur fixe (sur la case libre la plus proche du centre) tire des billes dans toutes les directions.
 * Une bille tirée s'arrête contre la première bille de la chaîne qu'elle touche et s'y insère ; un groupe de trois billes de la
 * même couleur (ou plus) éclate. Si la chaîne atteint le trou, la partie est perdue ; si toutes les billes éclatent, le
 * niveau est gagné (la chaîne suivante est plus longue, plus rapide et a plus de couleurs).
 *
 * La chaîne est poussée par sa queue : seule la dernière série de billes collées avance, à la vitesse du niveau. Quand un groupe
 * éclate, tout le train de devant (toutes les billes entre le trou et la brèche) recule ensemble, vite, jusqu'à toucher la queue.
 * Quand deux séries se rejoignent ainsi et que les deux billes du joint ont la même couleur, le groupe formé éclate si elles sont
 * au moins trois, et le train de devant recule de nouveau : c'est la réaction en chaîne, qui multiplie les points
 * ([Marble.chainPoints]). Une seule exception : si la queue entière a éclaté, les billes qui sortent ensuite de l'entrée foncent
 * vers l'avant rejoindre la chaîne, qui les attend.
 *
 * Les billes sont rangées de la tête (la plus proche du trou, indice 0) à la queue.
 */
internal class MarbleGame(private val random: Random = Random.Default) {

    private class Item(var s: Float, val color: Int) {
        var x = 0f
        var y = 0f

        /** Vrai pour une bille sortie seule de l'entrée, loin de la chaîne : elle fonce l'attraper au lieu d'attendre qu'on la rejoigne. */
        var rush = false
    }

    private class Flight(var x: Float, var y: Float, val dx: Float, val dy: Float, val color: Int)

    private val items = ArrayList<Item>()
    private var flight: Flight? = null

    /** La façon de jouer, et la graine du défi du jour. */
    var mode = MarbleMode.CLASSIC
        private set
    private var dailySeed = 0

    /** La graine de la carte du niveau en cours. */
    private var mapSeed = 0

    /** La carte du niveau (pour dessiner le sol et les décors). */
    lateinit var map: MarbleMap
        private set

    lateinit var path: MarblePath
        private set

    /** La position du lanceur (le centre d'une case libre). */
    var shooterX = 0f
        private set
    var shooterY = 0f
        private set

    /** La bille du lanceur et la suivante. */
    var current = 0
        private set
    var next = 0
        private set

    var score = 0
        private set
    var level = 1
        private set
    var isOver = false
        private set

    /** Nombre de billes du niveau qui ne sont pas encore sorties de l'entrée. */
    var supply = 0
        private set

    /** Les billes éclatées chargent une jauge : quand elle est pleine, une bille spéciale arrive au lanceur. */
    private var specialCharge = 0

    /** Rang de la réaction en chaîne en cours (0 : aucun groupe n'a éclaté depuis le dernier tir). */
    private var comboChain = 0

    /** Les deux dernières couleurs sorties de l'entrée (-1 : aucune) : on n'en sort jamais trois de suite la même. */
    private var recent1 = -1
    private var recent2 = -1

    /** Les billes parties au dernier éclatement, le rang de sa réaction, et le nombre d'éclatements depuis le début (l'écran s'en sert pour repérer un nouveau). */
    var lastEffects: List<MarbleEffect> = emptyList()
        private set
    var lastChain = 0
        private set
    var popCount = 0
        private set

    /** Le nombre de tirs posés ou perdus depuis le début (il ne fait que croître). */
    var shotCount = 0
        private set

    init {
        reset()
    }

    val isFlying: Boolean get() = flight != null

    /** Où en est la jauge des billes spéciales : 0 à vide, 1 quand la prochaine est prête (0 tant qu'aucune n'est débloquée). */
    val specialProgress: Float
        get() = if (Marble.unlockedSpecials(level).isEmpty()) 0f else (specialCharge.toFloat() / Marble.SPECIAL_THRESHOLD).coerceIn(0f, 1f)

    /** Position et couleur de la bille en vol (au lanceur quand aucune ne vole). */
    val flightX: Float get() = flight?.x ?: shooterX
    val flightY: Float get() = flight?.y ?: shooterY
    val flightColor: Int get() = flight?.color ?: current

    /** Nombre de billes sur le chemin. */
    val marbleCount: Int get() = items.size

    /** Où en est la tête de la chaîne sur le chemin : 0 à l'entrée, 1 au trou. */
    val progress: Float get() = if (items.isEmpty()) 0f else (items[0].s / path.length).coerceIn(0f, 1f)

    fun marbleColor(i: Int): Int = items[i].color
    fun marbleS(i: Int): Float = items[i].s
    fun marbleX(i: Int): Float = items[i].x
    fun marbleY(i: Int): Float = items[i].y

    /** Lance une partie en [mode] ; [seed] est la graine du défi du jour (voir [Marble.dailySeed]), sans effet dans le mode classique. */
    fun start(mode: MarbleMode, seed: Int = 0) {
        this.mode = mode
        dailySeed = seed
        reset()
    }

    /** Repart de zéro, dans le même mode : niveau 1, sans score. */
    fun reset() {
        level = 1
        score = 0
        isOver = false
        comboChain = 0
        lastChain = 0
        specialCharge = 0
        lastEffects = emptyList()
        beginLevel()
        current = randomPlayableColor()
        next = randomPlayableColor()
    }

    // --- Le niveau ---

    private fun beginLevel() {
        mapSeed = if (mode == MarbleMode.DAILY) dailySeed * 31 + level else random.nextInt()
        buildMap(mapSeed)
        items.clear()
        flight = null
        supply = Marble.countFor(level)
        recent1 = -1
        recent2 = -1
    }

    private fun buildMap(seed: Int) {
        val built = MarbleMap.generate(seed)
        map = built
        path = MarblePath.fromMap(built)
        shooterX = MarbleMap.SHOOTER_COL + 0.5f
        shooterY = MarbleMap.SHOOTER_ROW + 0.5f
    }

    private fun nextLevel() {
        level++
        score += Marble.LEVEL_BONUS
        beginLevel()
    }

    private fun checkEnd() {
        if (items.isNotEmpty() && items[0].s >= path.length) {
            isOver = true
            return
        }
        if (items.isEmpty() && supply == 0) nextLevel()
    }

    // --- Les couleurs ---

    private fun presentColors(): List<Int> = items.map { it.color }.distinct()

    /** Les couleurs qu'on peut tirer : celles qui restent sur le chemin, ou toutes celles du niveau quand il est vide. */
    private fun playableColors(): List<Int> {
        val present = presentColors()
        return if (present.isNotEmpty()) present else (0 until Marble.colorsFor(level)).toList()
    }

    private fun randomPlayableColor(): Int {
        val colors = playableColors()
        return colors[random.nextInt(colors.size)]
    }

    /** Remplace la bille du lanceur ou la suivante si plus aucune bille de sa couleur ne reste sur le chemin. */
    private fun ensurePlayable() {
        val present = presentColors()
        if (present.isEmpty()) return
        if (!Marble.isSpecial(current) && current !in present) current = present[random.nextInt(present.size)]
        if (!Marble.isSpecial(next) && next !in present) next = present[random.nextInt(present.size)]
    }

    /**
     * La couleur de la bille qui sort de l'entrée. En mode [MarbleMode.DAILY] elle ne dépend que de la graine de la carte et du
     * rang de la bille dans le niveau, pour que tout le monde ait la même suite.
     */
    private fun nextSpawnColor(): Int {
        val colors = Marble.colorsFor(level)
        val index = Marble.countFor(level) - supply
        var color = 0
        for (attempt in 0 until 8) {
            val r = if (mode == MarbleMode.DAILY) Random(mapSeed * 131 + index * 7 + attempt) else random
            color = r.nextInt(colors)
            if (!(recent1 == color && recent2 == color)) break
        }
        recent2 = recent1
        recent1 = color
        return color
    }

    // --- Le lanceur ---

    /** Échange la bille du lanceur avec la suivante (sans limite). */
    fun swap(): Boolean {
        if (isOver) return false
        val keep = current
        current = next
        next = keep
        return true
    }

    /**
     * Tire la bille du lanceur selon [angle] (en radians, de l'horizontale vers la droite, tourné vers le bas de l'écran :
     * 0 tire vers la droite, -π/2 vers le haut). Renvoie faux si un tir est déjà en vol ou la partie finie.
     */
    fun shoot(angle: Float): Boolean {
        if (isOver || flight != null) return false
        flight = Flight(shooterX, shooterY, cos(angle), sin(angle), current)
        current = next
        next = nextQueueMarble()
        return true
    }

    /** La prochaine bille du lanceur : une bille spéciale si la jauge est pleine (et que la bille du lanceur n'en est pas une), sinon une couleur. */
    private fun nextQueueMarble(): Int {
        val unlocked = Marble.unlockedSpecials(level)
        if (unlocked.isNotEmpty() && specialCharge >= Marble.SPECIAL_THRESHOLD && !Marble.isSpecial(current)) {
            specialCharge -= Marble.SPECIAL_THRESHOLD
            return unlocked[random.nextInt(unlocked.size)]
        }
        return randomPlayableColor()
    }

    /** Le guide de visée : le lanceur, puis l'endroit où la bille s'arrêterait (contre une bille, ou en sortant du terrain). */
    fun trajectory(angle: Float): List<Pair<Float, Float>> {
        val dx = cos(angle)
        val dy = sin(angle)
        var x = shooterX
        var y = shooterY
        var guard = 0
        while (guard++ < MAX_PREVIEW_STEPS) {
            x += dx * Marble.SUBSTEP
            y += dy * Marble.SUBSTEP
            if (hitIndex(x, y) >= 0 || outOfField(x, y)) break
        }
        return listOf(shooterX to shooterY, x to y)
    }

    /** Une bille qui n'est pas encore entrée sur le terrain ne peut pas être touchée. */
    private fun isVisible(m: Item): Boolean = m.x >= 0f && m.x <= MarbleMap.COLS && m.y >= 0f && m.y <= MarbleMap.ROWS

    /** Vrai si la bille d'indice [i] est sur le terrain (sinon elle arrive encore du dehors). */
    fun isMarbleVisible(i: Int): Boolean = isVisible(items[i])

    private fun outOfField(x: Float, y: Float): Boolean =
        x < -1f || x > TdMap.COLS + 1f || y < -1f || y > TdMap.ROWS + 1f

    /** L'indice de la bille de la chaîne que la bille en [x], [y] touche (la plus proche, parmi celles qu'on voit), ou -1. */
    private fun hitIndex(x: Float, y: Float): Int {
        var best = -1
        var bestD2 = Marble.HIT_DISTANCE * Marble.HIT_DISTANCE
        for (i in items.indices) {
            val m = items[i]
            if (!isVisible(m)) continue
            val dx = x - m.x
            val dy = y - m.y
            val d2 = dx * dx + dy * dy
            if (d2 < bestD2) {
                bestD2 = d2
                best = i
            }
        }
        return best
    }

    // --- Le temps qui passe ---

    /** Fait passer [elapsedMs] millisecondes : la bille en vol avance, la chaîne roule, de nouvelles billes sortent de l'entrée. */
    fun tick(elapsedMs: Long) {
        var left = elapsedMs.coerceIn(0L, MAX_ELAPSED_MS) / 1000f
        while (left > 0f && !isOver) {
            val dt = min(left, STEP)
            left -= dt
            stepShot(dt)
            stepChain(dt)
            spawn()
            checkEnd()
        }
    }

    private fun stepShot(dt: Float) {
        val f = flight ?: return
        var left = Marble.SHOT_SPEED * dt
        while (left > 0f) {
            val d = min(Marble.SUBSTEP, left)
            left -= d
            f.x += f.dx * d
            f.y += f.dy * d
            val hit = hitIndex(f.x, f.y)
            if (hit >= 0) {
                land(f, hit)
                return
            }
            if (outOfField(f.x, f.y)) {
                // Un tir perdu ne coûte rien.
                flight = null
                shotCount++
                comboChain = 0
                lastChain = 0
                return
            }
        }
    }

    /**
     * La bille tirée touche la bille [k]. Une couleur s'insère de son côté de la chaîne, et un groupe éclate peut-être ; une bombe
     * fait éclater les billes autour de la bille touchée, un éclair toutes les billes de sa couleur, un arc-en-ciel s'insère en
     * prenant la couleur qui fait le plus gros groupe.
     */
    private fun land(f: Flight, k: Int) {
        flight = null
        shotCount++
        comboChain = 0
        lastChain = 0
        val popped = when (f.color) {
            Marble.BOMB -> explode(k)
            Marble.LIGHTNING -> strike(k)
            else -> insert(f, k)
        }
        if (popped > 0) {
            comboChain = 1
            ensurePlayable()
        }
    }

    /** Insère la bille tirée [f] contre la bille [k] et renvoie le nombre de billes parties. */
    private fun insert(f: Flight, k: Int): Int {
        val m = items[k]
        // Du côté du trou de la bille touchée ? Alors elle s'insère devant elle, sinon derrière.
        val ahead = (f.x - m.x) * path.dirX(m.s) + (f.y - m.y) * path.dirY(m.s) > 0f
        val index = if (ahead) k else k + 1
        var s = if (ahead) m.s + Marble.DIAMETER else m.s - Marble.DIAMETER
        // Pas de place derrière : la bille prend celle de la voisine de derrière, et les billes de devant seront repoussées.
        if (index < items.size) s = max(s, items[index].s + Marble.DIAMETER)
        val color = if (f.color == Marble.RAINBOW) rainbowColor(index) else f.color
        val item = Item(s, color)
        sync(item)
        items.add(index, item)
        return collapseAround(index, 1)
    }

    /** La bombe : fait éclater toutes les billes à moins de [Marble.BOMB_RADIUS] de la bille [k]. Renvoie combien sont parties. */
    private fun explode(k: Int): Int {
        val cx = items[k].x
        val cy = items[k].y
        val limit = Marble.BOMB_RADIUS * Marble.BOMB_RADIUS
        val hit = items.indices.filter { i ->
            val dx = items[i].x - cx
            val dy = items[i].y - cy
            dx * dx + dy * dy <= limit
        }
        return removeSet(hit, 1, MarbleEffect(cx, cy, Marble.BOMB))
    }

    /** L'éclair : fait éclater toutes les billes de la couleur de la bille [k], sur tout le chemin. Renvoie combien sont parties. */
    private fun strike(k: Int): Int {
        val color = items[k].color
        return removeSet(items.indices.filter { items[it].color == color }, 1, MarbleEffect(items[k].x, items[k].y, Marble.LIGHTNING))
    }

    /**
     * L'arc-en-ciel qui va s'insérer à l'indice [index] : la couleur d'une des deux billes voisines qui fait le plus gros groupe
     * (la plus petite si deux font pareil) ; sans voisine, une couleur du chemin au hasard.
     */
    private fun rainbowColor(index: Int): Int {
        val candidates = sortedSetOf<Int>()
        if (index - 1 >= 0) candidates.add(items[index - 1].color)
        if (index < items.size) candidates.add(items[index].color)
        if (candidates.isEmpty()) return randomPlayableColor()
        var best = candidates.first()
        var bestSize = -1
        for (candidate in candidates) {
            val size = runIfInserted(index, candidate)
            if (size > bestSize) {
                best = candidate
                bestSize = size
            }
        }
        return best
    }

    /** La taille qu'aurait le groupe d'une bille de couleur [color] insérée à l'indice [index], billes collées seulement. */
    private fun runIfInserted(index: Int, color: Int): Int {
        var size = 1
        var j = index - 1
        while (j >= 0 && items[j].color == color) {
            size++
            if (j == 0 || !touching(j - 1)) break
            j--
        }
        var i = index
        while (i < items.size && items[i].color == color) {
            size++
            if (i == items.size - 1 || !touching(i)) break
            i++
        }
        return size
    }

    private fun touching(i: Int): Boolean = items[i].s - items[i + 1].s <= Marble.DIAMETER + Marble.TOUCH

    /** Fait éclater le groupe de même couleur qui contient la bille [index], s'il compte trois billes ou plus. Renvoie le nombre de billes parties. */
    private fun collapseAround(index: Int, chain: Int): Int {
        val color = items[index].color
        var lo = index
        while (lo > 0 && items[lo - 1].color == color && touching(lo - 1)) lo--
        var hi = index
        while (hi < items.size - 1 && items[hi + 1].color == color && touching(hi)) hi++
        if (hi - lo + 1 < Marble.MIN_RUN) return 0
        return removeRun(lo, hi, chain)
    }

    private fun removeRun(lo: Int, hi: Int, chain: Int): Int {
        val count = hi - lo + 1
        val effects = ArrayList<MarbleEffect>(count)
        for (i in lo..hi) effects.add(MarbleEffect(items[i].x, items[i].y, items[i].color))
        items.subList(lo, hi + 1).clear()
        score += Marble.chainPoints(count, chain)
        specialCharge += count
        lastEffects = effects
        lastChain = chain
        popCount++
        return count
    }

    /**
     * Retire les billes d'indices [indices] (rangés du plus petit au plus grand), qui ne se touchent pas forcément, et note leur
     * éclatement suivi de [marker] (le repère de la bombe ou de l'éclair, que l'écran dessine en grand). Renvoie leur nombre.
     */
    private fun removeSet(indices: List<Int>, chain: Int, marker: MarbleEffect): Int {
        val effects = ArrayList<MarbleEffect>(indices.size + 1)
        for (i in indices) effects.add(MarbleEffect(items[i].x, items[i].y, items[i].color))
        effects.add(marker)
        for (i in indices.indices.reversed()) items.removeAt(indices[i])
        score += Marble.chainPoints(indices.size, chain)
        specialCharge += indices.size
        lastEffects = effects
        lastChain = chain
        popCount++
        return indices.size
    }

    /**
     * La chaîne est faite de séries de billes collées. La dernière (la queue, poussée par l'entrée) avance à la vitesse du niveau ;
     * chaque série qui en est séparée par un trou recule, à [Marble.RETREAT_SPEED] : tout le train de devant recule donc d'un bloc,
     * et se colle à la série de derrière en la touchant. Seule exception : une bille sortie seule de l'entrée ([Item.rush]) fonce
     * vers la chaîne, qui l'attend. Deux séries qui se rejoignent sont recollées, et leur joint peut faire éclater un groupe.
     */
    private fun stepChain(dt: Float) {
        if (items.isEmpty()) return
        val starts = ArrayList<Int>()
        starts.add(0)
        for (i in 1 until items.size) if (!touching(i - 1)) starts.add(i)
        val count = starts.size
        // L'écart de chaque série avec celle de devant, avant le pas : on repère celles qui viennent de se joindre.
        val oldGap = FloatArray(count)
        for (k in 1 until count) oldGap[k] = items[starts[k] - 1].s - items[starts[k]].s - Marble.DIAMETER
        val velocity = FloatArray(count)
        for (k in 0 until count) {
            val head = items[starts[k]]
            // Rien devant elle : elle n'a personne à rattraper.
            if (k == 0) head.rush = false
            velocity[k] = when {
                head.rush -> Marble.CATCH_UP_SPEED
                k == count - 1 -> if (count == 1 && head.s < Marble.RUSH_UNTIL) Marble.RUSH_SPEED else Marble.speedFor(level)
                items[starts[k + 1]].rush -> 0f
                else -> -Marble.RETREAT_SPEED
            }
        }
        val joins = ArrayList<Int>()
        var aheadTail = 0f
        for (k in 0 until count) {
            val first = starts[k]
            val last = if (k + 1 < count) starts[k + 1] - 1 else items.size - 1
            var move = velocity[k] * dt
            if (move < 0f) {
                // Elle recule : au plus jusqu'à toucher la série de derrière (là où elle était avant ce pas).
                val room = if (k + 1 < count) max(items[last].s - items[last + 1].s - Marble.DIAMETER, 0f) else 0f
                move = max(move, -room)
            } else if (k > 0) {
                // Elle avance : au plus jusqu'à toucher la série de devant (là où elle est maintenant).
                move = min(move, max(aheadTail - Marble.DIAMETER - items[first].s, 0f))
            }
            for (i in first..last) items[i].s += move
            if (k > 0 && oldGap[k] > Marble.TOUCH && aheadTail - items[first].s - Marble.DIAMETER <= Marble.TOUCH) {
                items[first].rush = false
                joins.add(first - 1)
            }
            aheadTail = items[last].s
        }
        squeeze(dt)
        syncAll()
        // De l'arrière vers l'avant : un groupe qui éclate ne décale pas les indices de ceux de devant.
        for (i in joins.indices.reversed()) junction(joins[i])
    }

    /** Les billes qui se chevauchent (une bille vient de s'insérer) : celles de devant sont repoussées vers le trou. */
    private fun squeeze(dt: Float) {
        val maxPush = Marble.SQUEEZE_SPEED * dt
        for (i in items.size - 2 downTo 0) {
            val need = items[i + 1].s + Marble.DIAMETER
            val m = items[i]
            if (m.s < need) m.s = min(need, m.s + maxPush)
        }
    }

    /** Deux séries viennent de se rejoindre entre [i] et [i] + 1 : si les billes du joint ont la même couleur, le groupe éclate à partir de trois. */
    private fun junction(i: Int) {
        if (i < 0 || i + 1 >= items.size) return
        if (items[i].color != items[i + 1].color || !touching(i)) return
        if (collapseAround(i, comboChain + 1) > 0) {
            comboChain++
            ensurePlayable()
        }
    }

    /** Fait sortir de l'entrée les billes qui restent, une par une, quand la place se libère. */
    private fun spawn() {
        while (supply > 0 && (items.isEmpty() || items[items.size - 1].s >= Marble.DIAMETER)) {
            var alone = false
            val s = if (items.isEmpty()) {
                0f
            } else {
                val room = items[items.size - 1].s - Marble.DIAMETER
                if (room <= Marble.SPAWN_SNAP) {
                    room
                } else {
                    alone = true
                    0f
                }
            }
            val item = Item(s, nextSpawnColor())
            item.rush = alone
            sync(item)
            items.add(item)
            supply--
        }
    }

    private fun sync(item: Item) {
        item.x = path.x(item.s)
        item.y = path.y(item.s)
    }

    private fun syncAll() {
        for (item in items) sync(item)
    }

    // --- Pour les tests ---

    /** Remplace le terrain par [path] et le lanceur par ([x], [y]) ; la chaîne est vidée. */
    internal fun loadField(path: MarblePath, x: Float, y: Float) {
        this.path = path
        shooterX = x
        shooterY = y
        items.clear()
        flight = null
    }

    /** Pose une chaîne collée de billes de couleurs [colors], la tête à la distance [leaderS]. */
    internal fun setMarbles(colors: List<Int>, leaderS: Float) {
        items.clear()
        for ((i, c) in colors.withIndex()) {
            val item = Item(leaderS - i * Marble.DIAMETER, c)
            sync(item)
            items.add(item)
        }
    }

    /** Pose des billes à des distances quelconques : des paires (distance, couleur), de la tête à la queue, trous compris. */
    internal fun setChain(marbles: List<Pair<Float, Int>>) {
        items.clear()
        for ((s, c) in marbles) {
            val item = Item(s, c)
            sync(item)
            items.add(item)
        }
    }

    internal fun setLevel(value: Int) {
        level = value
    }

    internal fun setSpecialCharge(charge: Int) {
        specialCharge = charge
    }

    internal fun setSupply(count: Int) {
        supply = count
    }

    internal fun setQueue(current: Int, next: Int) {
        this.current = current
        this.next = next
    }

    // --- La sauvegarde ---

    /**
     * L'état de la partie en une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]) : version, niveau, score,
     * rang de la réaction, graine de la carte, mode, graine du jour, billes à sortir, bille du lanceur, bille suivante, fin,
     * deux dernières couleurs sorties, la bille en vol s'il y en a une (un drapeau, sa position, sa direction, sa couleur), la
     * jauge des billes spéciales, le nombre de billes, puis chaque bille (sa distance sur le chemin, sa couleur plus 16 si elle fonce rejoindre la chaîne).
     */
    fun save(): List<Int> = buildList {
        add(SAVE_VERSION)
        add(level)
        add(score)
        add(comboChain)
        add(mapSeed)
        add(mode.ordinal)
        add(dailySeed)
        add(supply)
        add(current)
        add(next)
        add(if (isOver) 1 else 0)
        add(recent1)
        add(recent2)
        val f = flight
        add(if (f != null) 1 else 0)
        add((f?.x ?: 0f).toBits())
        add((f?.y ?: 0f).toBits())
        add((f?.dx ?: 0f).toBits())
        add((f?.dy ?: 0f).toBits())
        add(f?.color ?: 0)
        add(specialCharge)
        add(items.size)
        for (item in items) {
            add(item.s.toBits())
            add(item.color + if (item.rush) RUSH_BIT else 0)
        }
    }

    companion object {
        private const val SAVE_VERSION = 3
        private const val RUSH_BIT = 16
        private const val SAVE_HEADER = 21
        private const val MAX_SAVED = 200
        private const val MAX_CHARGE = 100_000
        private const val MAX_PREVIEW_STEPS = 400
        private const val MAX_ELAPSED_MS = 500L
        private const val STEP = 0.02f

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): MarbleGame? {
            if (data.size < SAVE_HEADER || data[0] != SAVE_VERSION) return null
            val n = data[20]
            if (n < 0 || n > MAX_SAVED || data.size != SAVE_HEADER + 2 * n) return null
            val valid = data[1] >= 1 && data[2] >= 0 && data[3] >= 0 &&
                data[5] in MarbleMode.values().indices && data[7] in 0..Marble.countFor(data[1]) &&
                data[8] in 0 until Marble.KINDS && data[9] in 0 until Marble.KINDS && data[10] in 0..1 &&
                data[11] in -1 until Marble.PALETTE && data[12] in -1 until Marble.PALETTE &&
                data[13] in 0..1 && data[18] in 0 until Marble.KINDS && data[19] in 0..MAX_CHARGE
            if (!valid) return null
            var flight: Flight? = null
            if (data[13] == 1) {
                val x = Float.fromBits(data[14])
                val y = Float.fromBits(data[15])
                val dx = Float.fromBits(data[16])
                val dy = Float.fromBits(data[17])
                val length = dx * dx + dy * dy
                val sane = x in -2f..(TdMap.COLS + 2f) && y in -2f..(TdMap.ROWS + 2f) && length > 0.99f && length < 1.01f
                if (!sane) return null
                flight = Flight(x, y, dx, dy, data[18])
            }
            val colors = IntArray(n)
            val rushes = BooleanArray(n)
            val positions = FloatArray(n)
            for (i in 0 until n) {
                val s = Float.fromBits(data[SAVE_HEADER + 2 * i])
                val raw = data[SAVE_HEADER + 2 * i + 1]
                val color = raw and (RUSH_BIT - 1)
                if (raw < 0 || raw >= 2 * RUSH_BIT) return null
                if (s.isNaN() || s.isInfinite() || s < -10f || s > 1000f) return null
                if (color !in 0 until Marble.PALETTE) return null
                if (i > 0 && s > positions[i - 1]) return null
                positions[i] = s
                colors[i] = color
                rushes[i] = (raw and RUSH_BIT) != 0
            }
            val game = MarbleGame(random)
            game.mode = MarbleMode.fromIndex(data[5])
            game.dailySeed = data[6]
            game.level = data[1]
            game.score = data[2]
            game.comboChain = data[3]
            game.mapSeed = data[4]
            game.buildMap(data[4])
            game.supply = data[7]
            game.current = data[8]
            game.next = data[9]
            game.isOver = data[10] == 1
            game.recent1 = data[11]
            game.recent2 = data[12]
            game.specialCharge = data[19]
            game.flight = flight
            game.items.clear()
            for (i in 0 until n) {
                val item = Item(positions[i], colors[i])
                item.rush = rushes[i]
                game.sync(item)
                game.items.add(item)
            }
            return game
        }
    }
}
