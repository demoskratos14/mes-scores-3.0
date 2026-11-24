package com.aventure.messcores

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

    /** Vitesse de la queue de chaîne quand elle rattrape un trou, et vitesse à laquelle une bille insérée repousse celles qui la précèdent. */
    const val CATCH_UP_SPEED = 6f
    const val SQUEEZE_SPEED = 5f

    /** Au début du niveau, la chaîne entre vite ([RUSH_SPEED]) jusqu'à ce que sa tête ait fait [RUSH_UNTIL] unités. */
    const val RUSH_SPEED = 3f
    const val RUSH_UNTIL = 6f

    /** Écart toléré (en unités) pour dire que deux billes se touchent. */
    const val TOUCH = 0.02f

    /** Une nouvelle bille se colle à la queue si elle y tient à moins de [SPAWN_SNAP] près ; sinon elle sort seule de l'entrée. */
    const val SPAWN_SNAP = 0.1f

    /** Le nombre de couleurs du niveau [level] : 4 au début, 6 à partir du niveau 5. */
    fun colorsFor(level: Int): Int = (3 + (level.coerceAtLeast(1) + 1) / 2).coerceAtMost(PALETTE)

    /** Le nombre de billes de la chaîne au niveau [level]. */
    fun countFor(level: Int): Int = (30 + 5 * level.coerceAtLeast(1)).coerceAtMost(70)

    /** La vitesse de la chaîne au niveau [level], en unités par seconde. */
    fun speedFor(level: Int): Float = (0.55f + 0.05f * (level.coerceAtLeast(1) - 1)).coerceAtMost(1.3f)

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
         * Le chemin de la carte du tower defense [map] : du bord haut du terrain (le départ est [Marble.ENTRY_LEAD] unités avant
         * la première case) jusqu'au centre de la dernière case, où se trouve le trou.
         */
        fun fromTdMap(map: TdMap): MarblePath {
            val n = map.path.size
            val xs = FloatArray(n + 1)
            val ys = FloatArray(n + 1)
            xs[0] = map.path[0] % TdMap.COLS + 0.5f
            ys[0] = 0.5f - Marble.ENTRY_LEAD
            for (i in 0 until n) {
                xs[i + 1] = map.path[i] % TdMap.COLS + 0.5f
                ys[i + 1] = map.path[i] / TdMap.COLS + 0.5f
            }
            return MarblePath(xs, ys)
        }
    }
}

/** Une bille qui vient d'éclater, pour que l'écran l'anime : où elle était (en unités du terrain) et sa couleur. */
internal class MarbleEffect(val x: Float, val y: Float, val color: Int)

/**
 * Une partie de Marble Shooter : une chaîne de billes de couleur roule sur le chemin d'une carte du tower defense, de l'entrée
 * vers le trou, et un lanceur fixe (sur la case libre la plus proche du centre) tire des billes dans toutes les directions.
 * Une bille tirée s'arrête contre la première bille de la chaîne qu'elle touche et s'y insère ; un groupe de trois billes de la
 * même couleur (ou plus) éclate. Si la chaîne atteint le trou, la partie est perdue ; si toutes les billes éclatent, le
 * niveau est gagné (la chaîne suivante est plus longue, plus rapide et a plus de couleurs).
 *
 * La chaîne est poussée par sa queue : seule la dernière série de billes collées avance (à la vitesse de la chaîne, ou très
 * vite pour rattraper un trou) ; les séries qui la précèdent attendent. Quand deux séries se rejoignent et que les deux billes
 * du joint ont la même couleur, le groupe formé éclate si elles sont au moins trois : c'est la réaction en chaîne, qui multiplie
 * les points ([Marble.chainPoints]).
 *
 * Les billes sont rangées de la tête (la plus proche du trou, indice 0) à la queue.
 */
internal class MarbleGame(private val random: Random = Random.Default) {

    private class Item(var s: Float, val color: Int) {
        var x = 0f
        var y = 0f
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

    /** La carte du tower defense dont vient le chemin (pour dessiner le sol et les décors). */
    lateinit var map: TdMap
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
        val built = TdMap.generate(seed)
        map = built
        path = MarblePath.fromTdMap(built)
        // Le lanceur : la case libre la plus proche du centre du terrain.
        var best = Float.MAX_VALUE
        var bx = TdMap.COLS / 2f
        var by = TdMap.ROWS / 2f
        for (row in 0 until TdMap.ROWS) {
            for (col in 0 until TdMap.COLS) {
                if (!built.isBuildable(row, col)) continue
                val dx = col + 0.5f - TdMap.COLS / 2f
                val dy = row + 0.5f - TdMap.ROWS / 2f
                val d = dx * dx + dy * dy
                if (d < best) {
                    best = d
                    bx = col + 0.5f
                    by = row + 0.5f
                }
            }
        }
        shooterX = bx
        shooterY = by
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
        if (current !in present) current = present[random.nextInt(present.size)]
        if (next !in present) next = present[random.nextInt(present.size)]
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
        next = randomPlayableColor()
        return true
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

    private fun outOfField(x: Float, y: Float): Boolean =
        x < -1f || x > TdMap.COLS + 1f || y < -1f || y > TdMap.ROWS + 1f

    /** L'indice de la bille de la chaîne que la bille en [x], [y] touche (la plus proche, parmi celles qu'on voit), ou -1. */
    private fun hitIndex(x: Float, y: Float): Int {
        var best = -1
        var bestD2 = Marble.HIT_DISTANCE * Marble.HIT_DISTANCE
        for (i in items.indices) {
            val m = items[i]
            if (m.y < 0f) continue
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

    /** La bille tirée touche la bille [k] : elle s'insère de son côté de la chaîne, et un groupe éclate peut-être. */
    private fun land(f: Flight, k: Int) {
        flight = null
        shotCount++
        val m = items[k]
        // Du côté du trou de la bille touchée ? Alors elle s'insère devant elle, sinon derrière.
        val ahead = (f.x - m.x) * path.dirX(m.s) + (f.y - m.y) * path.dirY(m.s) > 0f
        val index = if (ahead) k else k + 1
        var s = if (ahead) m.s + Marble.DIAMETER else m.s - Marble.DIAMETER
        // Pas de place derrière : la bille prend celle de la voisine de derrière, et les billes de devant seront repoussées.
        if (index < items.size) s = max(s, items[index].s + Marble.DIAMETER)
        val item = Item(s, f.color)
        sync(item)
        items.add(index, item)
        comboChain = 0
        lastChain = 0
        if (collapseAround(index, 1) > 0) {
            comboChain = 1
            ensurePlayable()
        }
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
        lastEffects = effects
        lastChain = chain
        popCount++
        return count
    }

    /** Début de la dernière série de billes collées (la queue de la chaîne, que rien ne sépare de la dernière bille). */
    private fun rearStart(): Int {
        var a = items.size - 1
        while (a > 0 && touching(a - 1)) a--
        return a
    }

    private fun stepChain(dt: Float) {
        if (items.isEmpty()) return
        val a = rearStart()
        val gap = if (a > 0) items[a - 1].s - items[a].s - Marble.DIAMETER else 0f
        val speed = when {
            a > 0 -> Marble.CATCH_UP_SPEED
            items[0].s < Marble.RUSH_UNTIL -> Marble.RUSH_SPEED
            else -> Marble.speedFor(level)
        }
        var move = speed * dt
        var joined = false
        if (a > 0 && move >= gap) {
            move = max(gap, 0f)
            joined = true
        }
        for (i in a until items.size) items[i].s += move
        squeeze(dt)
        syncAll()
        if (joined) junction(a - 1)
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
        if (items[i].color != items[i + 1].color) return
        if (collapseAround(i, comboChain + 1) > 0) {
            comboChain++
            ensurePlayable()
        }
    }

    /** Fait sortir de l'entrée les billes qui restent, une par une, quand la place se libère. */
    private fun spawn() {
        while (supply > 0 && (items.isEmpty() || items[items.size - 1].s >= Marble.DIAMETER)) {
            val s = if (items.isEmpty()) {
                0f
            } else {
                val room = items[items.size - 1].s - Marble.DIAMETER
                if (room <= Marble.SPAWN_SNAP) room else 0f
            }
            val item = Item(s, nextSpawnColor())
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
     * deux dernières couleurs sorties, la bille en vol s'il y en a une (un drapeau, sa position, sa direction, sa couleur), le
     * nombre de billes, puis chaque bille (sa distance sur le chemin, sa couleur).
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
        add(items.size)
        for (item in items) {
            add(item.s.toBits())
            add(item.color)
        }
    }

    companion object {
        private const val SAVE_VERSION = 1
        private const val SAVE_HEADER = 20
        private const val MAX_SAVED = 200
        private const val MAX_PREVIEW_STEPS = 400
        private const val MAX_ELAPSED_MS = 500L
        private const val STEP = 0.02f

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): MarbleGame? {
            if (data.size < SAVE_HEADER || data[0] != SAVE_VERSION) return null
            val n = data[19]
            if (n < 0 || n > MAX_SAVED || data.size != SAVE_HEADER + 2 * n) return null
            val valid = data[1] >= 1 && data[2] >= 0 && data[3] >= 0 &&
                data[5] in MarbleMode.values().indices && data[7] in 0..Marble.countFor(data[1]) &&
                data[8] in 0 until Marble.PALETTE && data[9] in 0 until Marble.PALETTE && data[10] in 0..1 &&
                data[11] in -1 until Marble.PALETTE && data[12] in -1 until Marble.PALETTE &&
                data[13] in 0..1 && data[18] in 0 until Marble.PALETTE
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
            val positions = FloatArray(n)
            for (i in 0 until n) {
                val s = Float.fromBits(data[SAVE_HEADER + 2 * i])
                val color = data[SAVE_HEADER + 2 * i + 1]
                if (s.isNaN() || s.isInfinite() || s < -10f || s > 1000f) return null
                if (color !in 0 until Marble.PALETTE) return null
                if (i > 0 && s > positions[i - 1]) return null
                positions[i] = s
                colors[i] = color
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
            game.flight = flight
            game.items.clear()
            for (i in 0 until n) {
                val item = Item(positions[i], colors[i])
                game.sync(item)
                game.items.add(item)
            }
            return game
        }
    }
}
