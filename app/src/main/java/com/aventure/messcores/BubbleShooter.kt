package com.aventure.messcores

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Identifiant du Bubble Shooter parmi les jeux prédéfinis (écran à part : voir [BubbleShooterScreen]). */
internal const val BUBBLE_ID = "builtin_bubble"

/**
 * Les façons de jouer au Bubble Shooter. [CLASSIC] : le plafond descend quand on rate trop de tirs. [ZEN] : il ne descend jamais, on
 * prend son temps. [DAILY] : le tableau de départ est le même pour tout le monde, un jour donné (il vient de la date).
 */
internal enum class BubbleMode {
    CLASSIC, ZEN, DAILY;

    companion object {
        /** Le mode de numéro [index] (celui qui sert de clé dans les préférences et la sauvegarde) ; le classique si le numéro n'existe pas. */
        fun fromIndex(index: Int): BubbleMode = values().getOrElse(index) { CLASSIC }
    }
}

/**
 * Réglages et calculs du Bubble Shooter.
 *
 * Le terrain est en unités : une bulle mesure une unité de diamètre. Les rangées sont décalées une sur deux d'une demi-bulle
 * (une grille hexagonale) ; chaque rangée a [COLS] bulles, le terrain fait donc [WIDTH] = [COLS] + 0,5 unité de large.
 * Le plafond est en haut (y = 0), le lanceur en bas.
 *
 * Les couleurs sont des numéros de 0 à [PALETTE] - 1. Les bulles spéciales ([BOMB], [RAINBOW], [LIGHTNING]) ont les numéros
 * suivants : elles n'existent que dans le lanceur et disparaissent en se posant, jamais dans la grille.
 */
internal object Bubble {

    const val COLS = 10

    /** Nombre de rangées que la grille peut contenir (la partie est finie bien avant qu'elle soit pleine). */
    const val MAX_ROWS = 14

    /** Une bulle posée sur cette rangée (ou plus bas) fait perdre la partie : c'est la ligne rouge. */
    const val LOSE_ROW = 12

    /** Nombre de couleurs possibles, au total. */
    const val PALETTE = 6

    /** Les bulles spéciales : la bombe fait éclater ses voisines, l'arc-en-ciel prend la couleur qui fait le plus gros groupe, l'éclair vide la rangée où il se pose. */
    const val BOMB = 6
    const val RAINBOW = 7
    const val LIGHTNING = 8

    /** Nombre de numéros possibles pour une bulle du lanceur : les couleurs et les bulles spéciales. */
    const val KINDS = 9

    const val RADIUS = 0.5f

    /** Hauteur entre deux rangées : √3 / 2, deux rangées voisines s'emboîtent. */
    const val ROW_H = 0.8660254f

    const val WIDTH = COLS + 0.5f

    /** Hauteur de la ligne rouge (le haut de la rangée [LOSE_ROW]). */
    const val DEADLINE_Y = LOSE_ROW * ROW_H

    /** Position du lanceur, et hauteur totale du terrain. */
    const val SHOOTER_X = WIDTH / 2f
    const val SHOOTER_Y = DEADLINE_Y + 1.6f
    const val HEIGHT = SHOOTER_Y + 1f

    /** Vitesse d'une bulle tirée, en unités par seconde, et longueur d'un petit pas de calcul. */
    const val SPEED = 20f
    const val SUBSTEP = 0.1f

    /** Distance entre deux centres en dessous de laquelle une bulle tirée s'arrête contre une autre (un peu moins qu'un diamètre : on pardonne). */
    const val STICK_DISTANCE = 0.9f

    /** Angle le plus plat qu'on puisse viser, par rapport à l'horizontale (10°), et angle de départ (tout droit). */
    const val MIN_ANGLE = 0.17453293f
    const val MAX_ANGLE = 2.9670596f
    const val START_ANGLE = 1.5707964f

    /** Nombre de bulles de même couleur qui font éclater un groupe. */
    const val MIN_CLUSTER = 3

    const val POP_POINTS = 10

    /** Points de la première bulle qui tombe d'un tir : chacune des suivantes en vaut le double (voir [fallPoints]). */
    const val FALL_POINTS = 20

    /** Valeur la plus haute d'une bulle qui tombe : le doublement s'arrête là (20 × 64), pour que le score reste raisonnable. */
    const val FALL_POINTS_MAX = FALL_POINTS * 64

    /** Points gagnés en vidant le tableau. */
    const val LEVEL_BONUS = 500

    /** « Charge » à atteindre pour qu'une bulle spéciale arrive : chaque bulle éclatée en donne 1, chaque bulle tombée 2. */
    const val SPECIAL_THRESHOLD = 24

    /** Échanges permis au départ et au plus : un de plus à chaque tir qui fait partir des bulles. */
    const val SWAPS_START = 1
    const val SWAPS_MAX = 3

    /**
     * Les premiers niveaux sont dessinés à la main : une rangée par texte, un chiffre par bulle (la couleur), un point pour une case
     * vide. Chaque bulle doit tenir au plafond, directement ou par d'autres bulles, et n'utiliser que des couleurs du niveau (voir
     * [colorsFor]) : le test `lesNiveauxDessinesSontValides` le vérifie. Après le dernier, les tableaux sont tirés au hasard.
     */
    val HANDMADE: List<List<String>> = listOf(
        // 1 : des bandes, pour apprendre.
        listOf("0000011111", "0000011111", "2222233333", "2222233333", "0000011111"),
        // 2 : un triangle pointe en bas.
        listOf("0011223300", ".01122330.", "..112233..", "...1223...", "....22...."),
        // 3 : des blocs par deux.
        listOf("0011223344", "0011223344", "4433221100", "4433221100", "0011223344", "0011223344"),
        // 4 : un sablier.
        listOf("0000000000", ".11111111.", "..222222..", "...3333...", "....44....", "...3333...", "..222222.."),
        // 5 : des couleurs en spirale.
        listOf("0123450123", "5012345012", "4501234501", "3450123450", "2345012345"),
        // 6 : des colonnes aérées.
        listOf("0.1.2.3.4.", "0.1.2.3.4.", "0505050505", "1.2.3.4.5.", "1.2.3.4.5.", "3434343434"),
        // 7 : un cœur.
        listOf(
            "0000000000", ".03.14.25.", "0033114422", ".033155222",
            ".03315522.", "..034522..", "...0452...", "....15...."
        ),
        // 8 : un damier.
        listOf("0101012345", "1010105432", "2323230101", "3232321010", "4545452323", "5454543232")
    )

    /** Le dessin du niveau [level], ou `null` s'il est tiré au hasard. */
    fun handmade(level: Int): List<String>? = HANDMADE.getOrNull(level - 1)

    /** Nombre de couleurs au niveau [level] : 4 au départ, une de plus tous les deux niveaux, 6 au plus. */
    fun colorsFor(level: Int): Int = (3 + (level.coerceAtLeast(1) + 1) / 2).coerceAtMost(PALETTE)

    /** Nombre de rangées au départ du niveau [level] quand il est tiré au hasard. */
    fun rowsFor(level: Int): Int = (4 + level.coerceAtLeast(1)).coerceAtMost(9)

    /** Nombre de tirs manqués (sans bulle éclatée) après lesquels le plafond descend d'une rangée au niveau [level]. */
    fun shotsPerDrop(level: Int): Int = (7 - (level.coerceAtLeast(1) - 1) / 2).coerceAtLeast(4)

    /** Les bulles spéciales qui peuvent arriver au niveau [level] : la bombe dès le 2, l'arc-en-ciel dès le 3, l'éclair dès le 4. */
    fun unlockedSpecials(level: Int): List<Int> = when {
        level >= 4 -> listOf(BOMB, RAINBOW, LIGHTNING)
        level >= 3 -> listOf(BOMB, RAINBOW)
        level >= 2 -> listOf(BOMB)
        else -> emptyList()
    }

    /** Vrai si le numéro [kind] est celui d'une bulle spéciale. */
    fun isSpecial(kind: Int): Boolean = kind >= PALETTE

    /** La graine du défi du jour : la même pour tout le monde, un jour donné (par exemple 20261114). */
    fun dailySeed(year: Int, month: Int, day: Int): Int = year * 10000 + month * 100 + day

    /**
     * Points de [count] bulles tombées par un seul tir : la première vaut [FALL_POINTS], la deuxième le double, la troisième le
     * double encore… jusqu'à [FALL_POINTS_MAX]. Faire tomber une grappe d'un coup rapporte donc bien plus que des chutes séparées
     * (1 bulle : 20, 2 : 60, 3 : 140, 4 : 300…).
     */
    fun fallPoints(count: Int): Int {
        var total = 0
        var value = FALL_POINTS
        repeat(count.coerceAtLeast(0)) {
            total += value
            if (value < FALL_POINTS_MAX) value *= 2
        }
        return total
    }

    fun clampAngle(angle: Float): Float = angle.coerceIn(MIN_ANGLE, MAX_ANGLE)

    /** L'angle de visée (de l'horizontale, vers le haut) pour aller du lanceur vers un point à [dx] vers la droite et [dy] vers le haut. */
    fun angleTo(dx: Float, dy: Float): Float =
        if (dy <= 0f) (if (dx >= 0f) MIN_ANGLE else MAX_ANGLE) else clampAngle(atan2(dy, dx))
}

/**
 * Une bulle qui vient de quitter le tableau, pour que l'écran l'anime : où elle était (en unités du terrain), sa couleur, et
 * [falling] vrai si elle tombe (elle ne tenait plus au plafond), faux si elle éclate. Pour l'éclat d'une bombe, [color] vaut
 * [Bubble.BOMB].
 */
internal class BubbleEffect(val x: Float, val y: Float, val color: Int, val falling: Boolean)

/**
 * Une partie de Bubble Shooter : des bulles de couleur accrochées au plafond, et un lanceur en bas qui en tire une à la fois.
 * La bulle tirée rebondit sur les murs et s'arrête contre le plafond ou une autre bulle. Un groupe de trois bulles de la même
 * couleur (ou plus) éclate ; celles qui ne tiennent plus au plafond tombent. Quand un tir ne fait rien éclater, un compteur
 * avance : à la fin, le plafond descend d'une rangée (sauf en mode [BubbleMode.ZEN]). Une bulle qui touche la ligne rouge
 * ([Bubble.LOSE_ROW]) finit la partie ; vider tout le tableau rapporte un bonus et lance le niveau suivant (plus de couleurs,
 * plus de rangées, descente plus rapide). Les premiers niveaux sont dessinés à la main ([Bubble.HANDMADE]).
 *
 * Les gros coups chargent une jauge : quand elle est pleine, une bulle spéciale arrive au lanceur (bombe, arc-en-ciel, éclair).
 * Échanger la bulle du lanceur avec la suivante coûte un « échange », qu'on regagne en faisant partir des bulles.
 *
 * Les couleurs sont des numéros de 0 à [Bubble.PALETTE] - 1 ; [EMPTY] marque une case vide.
 */
internal class BubbleGame(private val baseRandom: Random = Random.Default) {

    /** Le hasard de la partie : celui qu'on a donné, ou une suite fixe tirée de la date en mode [BubbleMode.DAILY]. */
    private var random: Random = baseRandom

    /** La grille, rangée par rangée du plafond vers le bas. */
    private val grid = Array(Bubble.MAX_ROWS) { IntArray(Bubble.COLS) { EMPTY } }

    /**
     * Parité du décalage des rangées : la rangée `r` est décalée d'une demi-bulle quand `(r + parity)` est impair. Elle change à
     * chaque descente du plafond, pour que les bulles déjà posées gardent leur place quand on ajoute une rangée au-dessus.
     */
    private var parity = 0

    /** Une bulle en vol : sa position et sa direction (un vecteur de longueur 1). */
    private class Flight(var x: Float, var y: Float, var dx: Float, var dy: Float)

    private var flight: Flight? = null

    /** La façon de jouer, et la graine du défi du jour. */
    var mode = BubbleMode.CLASSIC
        private set
    private var dailySeed = 0

    /** La bulle du lanceur, et la suivante : une couleur, ou une bulle spéciale. */
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

    /** Tirs manqués depuis la dernière descente du plafond. */
    var shotsSinceDrop = 0
        private set

    /** Échanges encore permis, et jauge qui monte vers la prochaine bulle spéciale. */
    var swapCharges = Bubble.SWAPS_START
        private set
    private var specialCharge = 0

    /** Les deux dernières couleurs données au lanceur (-1 : aucune) : on évite d'en donner trois de suite la même. */
    private var recent1 = -1
    private var recent2 = -1

    /** Les bulles parties du tableau au dernier tir, et le nombre de tirs posés depuis le début (il ne fait que croître : l'écran s'en sert pour repérer un nouveau tir). */
    var lastEffects: List<BubbleEffect> = emptyList()
        private set
    var shotCount = 0
        private set

    /** Nombre de bulles éclatées et de bulles tombées par le dernier tir. */
    var lastPopped = 0
        private set
    var lastFallen = 0
        private set

    init {
        reset()
    }

    val isFlying: Boolean get() = flight != null

    /** Position de la bulle en vol (au lanceur quand aucune ne vole). */
    val flightX: Float get() = flight?.x ?: Bubble.SHOOTER_X
    val flightY: Float get() = flight?.y ?: Bubble.SHOOTER_Y

    /** Tirs manqués encore permis avant que le plafond descende. */
    val shotsUntilDrop: Int get() = (Bubble.shotsPerDrop(level) - shotsSinceDrop).coerceAtLeast(1)

    /** Nombre de bulles accrochées. */
    val bubbleCount: Int get() = grid.sumOf { row -> row.count { it != EMPTY } }

    /** Lance une partie en [mode] ; [seed] est la graine du défi du jour (voir [Bubble.dailySeed]), sans effet dans les autres modes. */
    fun start(mode: BubbleMode, seed: Int = 0) {
        this.mode = mode
        dailySeed = seed
        reset()
    }

    /** Repart de zéro, dans le même mode : niveau 1, sans score. */
    fun reset() {
        random = if (mode == BubbleMode.DAILY) Random(dailySeed) else baseRandom
        level = 1
        score = 0
        isOver = false
        shotsSinceDrop = 0
        swapCharges = Bubble.SWAPS_START
        specialCharge = 0
        recent1 = -1
        recent2 = -1
        lastPopped = 0
        lastFallen = 0
        lastEffects = emptyList()
        flight = null
        fillLevel()
        current = randomPlayableColor()
        next = randomPlayableColor()
    }

    // --- La grille ---

    /** La couleur de la case ([row], [col]), ou [EMPTY]. */
    fun colorAt(row: Int, col: Int): Int = grid[row][col]

    /** Vrai si la rangée [row] est décalée d'une demi-bulle vers la droite. */
    fun isShifted(row: Int): Boolean = (row + parity) % 2 == 1

    fun centerX(row: Int, col: Int): Float = col + Bubble.RADIUS + if (isShifted(row)) Bubble.RADIUS else 0f

    fun centerY(row: Int): Float = Bubble.RADIUS + row * Bubble.ROW_H

    /** Appelle [action] pour chaque case voisine de ([row], [col]) : jusqu'à six, selon le décalage de la rangée et les bords. */
    private fun forEachNeighbor(row: Int, col: Int, action: (Int, Int) -> Unit) {
        fun visit(r: Int, c: Int) {
            if (r in 0 until Bubble.MAX_ROWS && c in 0 until Bubble.COLS) action(r, c)
        }
        visit(row, col - 1)
        visit(row, col + 1)
        // Une rangée décalée touche, au-dessus et en dessous, les cases de même colonne et de la colonne suivante ;
        // une rangée non décalée, celles de même colonne et de la colonne précédente.
        val other = if (isShifted(row)) col + 1 else col - 1
        visit(row - 1, col)
        visit(row - 1, other)
        visit(row + 1, col)
        visit(row + 1, other)
    }

    private fun isBoardEmpty(): Boolean = grid.all { row -> row.all { it == EMPTY } }

    private fun reachedLoseLine(): Boolean =
        (Bubble.LOSE_ROW until Bubble.MAX_ROWS).any { r -> grid[r].any { it != EMPTY } }

    /** Pose le tableau du niveau en cours : le dessin du niveau s'il en a un, sinon des rangées tirées au hasard parmi les couleurs du niveau. */
    private fun fillLevel() {
        grid.forEach { it.fill(EMPTY) }
        parity = 0
        val pattern = Bubble.handmade(level)
        if (pattern != null) {
            for ((r, row) in pattern.withIndex()) {
                for (c in 0 until Bubble.COLS) {
                    val ch = row.getOrElse(c) { '.' }
                    if (ch in '0'..'5') grid[r][c] = ch - '0'
                }
            }
            return
        }
        val colors = Bubble.colorsFor(level)
        for (r in 0 until Bubble.rowsFor(level)) {
            for (c in 0 until Bubble.COLS) grid[r][c] = random.nextInt(colors)
        }
    }

    /** Les couleurs qui restent sur le tableau (un tir d'une autre couleur ne servirait qu'à gêner). */
    private fun presentColors(): List<Int> {
        val seen = BooleanArray(Bubble.PALETTE)
        grid.forEach { row -> row.forEach { if (it != EMPTY) seen[it] = true } }
        return (0 until Bubble.PALETTE).filter { seen[it] }
    }

    /** Les couleurs des bulles « en surface » : celles qui ont au moins une case vide à côté, donc que le tir peut atteindre. */
    private fun exposedColors(): List<Int> {
        val seen = BooleanArray(Bubble.PALETTE)
        for (r in 0 until Bubble.MAX_ROWS) {
            for (c in 0 until Bubble.COLS) {
                val color = grid[r][c]
                if (color == EMPTY || seen[color]) continue
                var open = false
                forEachNeighbor(r, c) { nr, nc -> if (grid[nr][nc] == EMPTY) open = true }
                if (open) seen[color] = true
            }
        }
        return (0 until Bubble.PALETTE).filter { seen[it] }
    }

    /**
     * Une couleur pour le lanceur : parmi celles qu'on peut atteindre (en surface), jamais une couleur absente du tableau, et pas
     * trois fois de suite la même.
     */
    private fun randomPlayableColor(): Int {
        val present = presentColors()
        if (present.isEmpty()) return random.nextInt(Bubble.colorsFor(level))
        var pool = exposedColors().ifEmpty { present }
        if (recent1 >= 0 && recent1 == recent2 && pool.size > 1) pool = pool.filter { it != recent1 }.ifEmpty { pool }
        val pick = pool[random.nextInt(pool.size)]
        recent2 = recent1
        recent1 = pick
        return pick
    }

    /** La prochaine bulle du lanceur : une bulle spéciale si la jauge est pleine (et que la bulle du lanceur n'en est pas une), sinon une couleur. */
    private fun nextQueueBubble(): Int {
        val unlocked = Bubble.unlockedSpecials(level)
        if (unlocked.isNotEmpty() && specialCharge >= Bubble.SPECIAL_THRESHOLD && !Bubble.isSpecial(current)) {
            specialCharge -= Bubble.SPECIAL_THRESHOLD
            return unlocked[random.nextInt(unlocked.size)]
        }
        return randomPlayableColor()
    }

    // --- Les tirs ---

    /** Échange la bulle du lanceur avec la suivante, ce qui coûte un échange. Sans effet pendant un tir ou sans échange. */
    fun swap(): Boolean {
        if (isOver || isFlying || swapCharges <= 0) return false
        swapCharges--
        val keep = current
        current = next
        next = keep
        return true
    }

    /** Tire la bulle du lanceur selon [angle] (de l'horizontale, vers le haut). Renvoie faux si un tir est déjà en vol ou la partie finie. */
    fun shoot(angle: Float): Boolean {
        if (isOver || isFlying) return false
        val a = Bubble.clampAngle(angle)
        flight = Flight(Bubble.SHOOTER_X, Bubble.SHOOTER_Y, cos(a), -sin(a))
        return true
    }

    /** Fait passer [elapsedMs] millisecondes : la bulle en vol avance, et se pose si elle touche le plafond ou une bulle. */
    fun tick(elapsedMs: Long) {
        val f = flight ?: return
        if (advance(f, Bubble.SPEED * elapsedMs.coerceAtLeast(0L) / 1000f)) land(f)
    }

    /**
     * Fait avancer [f] de [distance] unités, par petits pas, en la faisant rebondir sur les murs. Renvoie vrai quand elle s'arrête
     * (plafond ou bulle touchés) ; [onBounce] est appelé à chaque rebond.
     */
    private fun advance(f: Flight, distance: Float, onBounce: () -> Unit = {}): Boolean {
        var left = distance
        val maxX = Bubble.WIDTH - Bubble.RADIUS
        while (left > 0f) {
            val d = min(Bubble.SUBSTEP, left)
            left -= d
            f.x += f.dx * d
            f.y += f.dy * d
            if (f.x < Bubble.RADIUS) {
                f.x = 2 * Bubble.RADIUS - f.x
                f.dx = -f.dx
                onBounce()
            } else if (f.x > maxX) {
                f.x = 2 * maxX - f.x
                f.dx = -f.dx
                onBounce()
            }
            if (f.y <= Bubble.RADIUS) {
                f.y = Bubble.RADIUS
                return true
            }
            if (touchesBubble(f.x, f.y)) return true
        }
        return false
    }

    private fun touchesBubble(x: Float, y: Float): Boolean {
        val first = ((y - 1f - Bubble.RADIUS) / Bubble.ROW_H).toInt().coerceAtLeast(0)
        val last = ((y + 1f - Bubble.RADIUS) / Bubble.ROW_H).toInt().coerceAtMost(Bubble.MAX_ROWS - 1)
        val limit = Bubble.STICK_DISTANCE * Bubble.STICK_DISTANCE
        for (r in first..last) {
            val cy = centerY(r)
            for (c in 0 until Bubble.COLS) {
                if (grid[r][c] == EMPTY) continue
                val dx = x - centerX(r, c)
                val dy = y - cy
                if (dx * dx + dy * dy < limit) return true
            }
        }
        return false
    }

    /**
     * Le chemin que suivrait un tir d'angle [angle] : le lanceur, chaque rebond sur un mur (jusqu'à [maxBounces]), puis l'endroit
     * où la bulle s'arrêterait. Sert à dessiner le guide de visée.
     */
    fun trajectory(angle: Float, maxBounces: Int = 2): List<Pair<Float, Float>> {
        val a = Bubble.clampAngle(angle)
        val f = Flight(Bubble.SHOOTER_X, Bubble.SHOOTER_Y, cos(a), -sin(a))
        val points = mutableListOf(f.x to f.y)
        var bounces = 0
        var stopped = false
        var guard = 0
        while (!stopped && bounces <= maxBounces && guard++ < MAX_PREVIEW_STEPS) {
            stopped = advance(f, Bubble.SUBSTEP) { points.add(f.x to f.y); bounces++ }
        }
        points.add(f.x to f.y)
        return points
    }

    /** La bulle en vol s'arrête : elle prend la case libre accrochée la plus proche. */
    private fun land(f: Flight) {
        flight = null
        val cell = snap(f.x, f.y)
        if (cell < 0) {
            isOver = true
            return
        }
        settle(cell / Bubble.COLS, cell % Bubble.COLS, current)
    }

    /** La case libre, accrochée au plafond ou à une bulle, la plus proche du point ([x], [y]) ; -1 s'il n'y en a aucune. */
    private fun snap(x: Float, y: Float): Int {
        val near = ((y - Bubble.RADIUS) / Bubble.ROW_H).roundToInt().coerceIn(0, Bubble.MAX_ROWS - 1)
        var best = -1
        var bestDistance = Float.MAX_VALUE
        fun scan(rows: IntRange) {
            for (r in rows) {
                for (c in 0 until Bubble.COLS) {
                    if (grid[r][c] != EMPTY || !isAttached(r, c)) continue
                    val dx = x - centerX(r, c)
                    val dy = y - centerY(r)
                    val distance = dx * dx + dy * dy
                    if (distance < bestDistance) {
                        bestDistance = distance
                        best = r * Bubble.COLS + c
                    }
                }
            }
        }
        scan((near - 1).coerceAtLeast(0)..(near + 1).coerceAtMost(Bubble.MAX_ROWS - 1))
        if (best < 0) scan(0 until Bubble.MAX_ROWS)
        return best
    }

    private fun isAttached(row: Int, col: Int): Boolean {
        if (row == 0) return true
        var attached = false
        forEachNeighbor(row, col) { r, c -> if (grid[r][c] != EMPTY) attached = true }
        return attached
    }

    /**
     * Pose une bulle de couleur (ou spéciale) [color] en ([row], [col]) et en tire les conséquences : un groupe de la même couleur
     * éclate s'il compte assez de bulles (une bombe fait éclater ses voisines, un éclair toute sa rangée, un arc-en-ciel prend la
     * couleur qui fait le plus gros groupe), celles qui ne tiennent plus tombent, le plafond descend après assez de tirs manqués,
     * le tableau vidé donne le niveau suivant, une bulle sur la ligne rouge finit la partie. Puis la bulle suivante passe au lanceur.
     */
    internal fun settle(row: Int, col: Int, color: Int) {
        var popped = 0
        val effects = mutableListOf<BubbleEffect>()
        when (color) {
            Bubble.BOMB -> popped = explodeAround(row, col, effects)
            Bubble.LIGHTNING -> popped = clearRow(row, effects)
            else -> {
                grid[row][col] = if (color == Bubble.RAINBOW) rainbowColor(row, col) else color
                val group = cluster(row, col)
                if (group.size >= Bubble.MIN_CLUSTER) popped = removeCells(group, effects)
            }
        }
        val fallen = if (popped > 0) dropFloating(effects) else 0
        lastPopped = popped
        lastFallen = fallen
        lastEffects = effects
        shotCount++
        score += Bubble.POP_POINTS * popped + Bubble.fallPoints(fallen)
        specialCharge += popped + 2 * fallen
        if (popped > 0) {
            swapCharges = min(Bubble.SWAPS_MAX, swapCharges + 1)
        } else if (mode != BubbleMode.ZEN) {
            shotsSinceDrop++
            if (shotsSinceDrop >= Bubble.shotsPerDrop(level)) {
                shotsSinceDrop = 0
                descend()
            }
        }
        if (isBoardEmpty()) {
            level++
            score += Bubble.LEVEL_BONUS
            shotsSinceDrop = 0
            fillLevel()
        } else if (reachedLoseLine()) {
            isOver = true
        }
        current = next
        next = nextQueueBubble()
        if (!Bubble.isSpecial(current) && current !in presentColors()) current = randomPlayableColor()
    }

    /** Retire les cases [cells] (numéros `rangée * COLS + colonne`) en notant l'éclatement de chacune, et renvoie combien il y en avait. */
    private fun removeCells(cells: List<Int>, effects: MutableList<BubbleEffect>): Int {
        for (index in cells) {
            val r = index / Bubble.COLS
            val c = index % Bubble.COLS
            effects.add(BubbleEffect(centerX(r, c), centerY(r), grid[r][c], falling = false))
            grid[r][c] = EMPTY
        }
        return cells.size
    }

    /** La bombe : fait éclater toutes les bulles voisines de ([row], [col]). Renvoie combien ont éclaté. */
    private fun explodeAround(row: Int, col: Int, effects: MutableList<BubbleEffect>): Int {
        val cells = mutableListOf<Int>()
        forEachNeighbor(row, col) { r, c -> if (grid[r][c] != EMPTY) cells.add(r * Bubble.COLS + c) }
        if (cells.isNotEmpty()) effects.add(BubbleEffect(centerX(row, col), centerY(row), Bubble.BOMB, falling = false))
        return removeCells(cells, effects)
    }

    /** L'éclair : fait éclater toutes les bulles de la rangée [row]. Renvoie combien ont éclaté. */
    private fun clearRow(row: Int, effects: MutableList<BubbleEffect>): Int =
        removeCells((0 until Bubble.COLS).filter { grid[row][it] != EMPTY }.map { row * Bubble.COLS + it }, effects)

    /**
     * L'arc-en-ciel posé en ([row], [col]) : la couleur d'une bulle voisine qui ferait le plus gros groupe (la plus petite si
     * deux font pareil) ; sans voisine, une couleur du tableau au hasard. La case reste vide : l'appelant y pose la couleur.
     */
    private fun rainbowColor(row: Int, col: Int): Int {
        val candidates = sortedSetOf<Int>()
        forEachNeighbor(row, col) { r, c -> if (grid[r][c] != EMPTY) candidates.add(grid[r][c]) }
        if (candidates.isEmpty()) return randomPlayableColor()
        var best = candidates.first()
        var bestSize = -1
        for (candidate in candidates) {
            grid[row][col] = candidate
            val size = cluster(row, col).size
            if (size > bestSize) {
                best = candidate
                bestSize = size
            }
        }
        grid[row][col] = EMPTY
        return best
    }

    /** Les numéros de case (`rangée * COLS + colonne`) du groupe de même couleur qui contient ([row], [col]). */
    private fun cluster(row: Int, col: Int): List<Int> {
        val color = grid[row][col]
        if (color == EMPTY) return emptyList()
        val seen = BooleanArray(Bubble.MAX_ROWS * Bubble.COLS)
        val queue = ArrayDeque<Int>()
        val start = row * Bubble.COLS + col
        seen[start] = true
        queue.addLast(start)
        val group = mutableListOf<Int>()
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            group.add(index)
            forEachNeighbor(index / Bubble.COLS, index % Bubble.COLS) { r, c ->
                val n = r * Bubble.COLS + c
                if (!seen[n] && grid[r][c] == color) {
                    seen[n] = true
                    queue.addLast(n)
                }
            }
        }
        return group
    }

    /**
     * Fait tomber les bulles qui ne tiennent plus au plafond (même par d'autres bulles), et renvoie combien sont tombées. Si
     * [effects] est donné, chaque bulle tombée y est ajoutée (pour l'animer).
     */
    internal fun dropFloating(effects: MutableList<BubbleEffect>? = null): Int {
        val seen = BooleanArray(Bubble.MAX_ROWS * Bubble.COLS)
        val queue = ArrayDeque<Int>()
        for (c in 0 until Bubble.COLS) {
            if (grid[0][c] != EMPTY) {
                seen[c] = true
                queue.addLast(c)
            }
        }
        while (queue.isNotEmpty()) {
            val index = queue.removeFirst()
            forEachNeighbor(index / Bubble.COLS, index % Bubble.COLS) { r, c ->
                val n = r * Bubble.COLS + c
                if (grid[r][c] != EMPTY && !seen[n]) {
                    seen[n] = true
                    queue.addLast(n)
                }
            }
        }
        var fallen = 0
        for (r in 0 until Bubble.MAX_ROWS) {
            for (c in 0 until Bubble.COLS) {
                if (grid[r][c] != EMPTY && !seen[r * Bubble.COLS + c]) {
                    effects?.add(BubbleEffect(centerX(r, c), centerY(r), grid[r][c], falling = true))
                    grid[r][c] = EMPTY
                    fallen++
                }
            }
        }
        return fallen
    }

    /** Le plafond descend : tout glisse d'une rangée vers le bas, et une rangée neuve apparaît en haut. */
    private fun descend() {
        for (r in Bubble.MAX_ROWS - 1 downTo 1) grid[r - 1].copyInto(grid[r])
        val colors = Bubble.colorsFor(level)
        for (c in 0 until Bubble.COLS) grid[0][c] = random.nextInt(colors)
        parity = 1 - parity
    }

    // --- Pour les tests ---

    /** Pose une bulle sans rien résoudre. */
    internal fun place(row: Int, col: Int, color: Int) {
        grid[row][col] = color
    }

    /** Vide tout le tableau. */
    internal fun clearBoard() {
        grid.forEach { it.fill(EMPTY) }
    }

    /** Fixe la bulle du lanceur et la suivante. */
    internal fun setQueue(current: Int, next: Int) {
        this.current = current
        this.next = next
    }

    /** Pose le tableau de départ du niveau [number], comme s'il venait d'être atteint. */
    internal fun loadLevel(number: Int) {
        level = number
        fillLevel()
    }

    /** Remplit la jauge des bulles spéciales à [charge]. */
    internal fun setSpecialCharge(charge: Int) {
        specialCharge = charge
    }

    // --- La sauvegarde ---

    /**
     * L'état de la partie en une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]) : version, niveau, score,
     * tirs manqués, parité, fin, bulle du lanceur, bulle suivante, mode, graine, échanges, jauge, puis la bulle en vol s'il y en a
     * une (un drapeau, sa position et sa direction), puis la grille rangée par rangée.
     */
    fun save(): List<Int> = buildList {
        add(SAVE_VERSION)
        add(level)
        add(score)
        add(shotsSinceDrop)
        add(parity)
        add(if (isOver) 1 else 0)
        add(current)
        add(next)
        add(mode.ordinal)
        add(dailySeed)
        add(swapCharges)
        add(specialCharge)
        val f = flight
        add(if (f != null) 1 else 0)
        add((f?.x ?: 0f).toBits())
        add((f?.y ?: 0f).toBits())
        add((f?.dx ?: 0f).toBits())
        add((f?.dy ?: 0f).toBits())
        grid.forEach { row -> addAll(row.toList()) }
    }

    companion object {
        /** Case vide. */
        const val EMPTY = -1

        private const val SAVE_VERSION = 2
        private const val SAVE_HEADER = 17
        private const val MAX_PREVIEW_STEPS = 1000

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): BubbleGame? {
            if (data.size != SAVE_HEADER + Bubble.MAX_ROWS * Bubble.COLS || data[0] != SAVE_VERSION) return null
            val cells = data.subList(SAVE_HEADER, data.size)
            val valid = data[1] >= 1 && data[2] >= 0 && data[3] >= 0 && data[4] in 0..1 && data[5] in 0..1 &&
                data[6] in 0 until Bubble.KINDS && data[7] in 0 until Bubble.KINDS &&
                data[8] in BubbleMode.values().indices && data[10] in 0..Bubble.SWAPS_MAX && data[11] >= 0 &&
                data[12] in 0..1 && cells.all { it in EMPTY until Bubble.PALETTE }
            if (!valid) return null
            var flight: Flight? = null
            if (data[12] == 1) {
                val x = Float.fromBits(data[13])
                val y = Float.fromBits(data[14])
                val dx = Float.fromBits(data[15])
                val dy = Float.fromBits(data[16])
                val length = dx * dx + dy * dy
                val sane = x in 0f..Bubble.WIDTH && y in 0f..Bubble.HEIGHT && dy < 0f && length > 0.99f && length < 1.01f
                if (!sane) return null
                flight = Flight(x, y, dx, dy)
            }
            val game = BubbleGame(random)
            game.level = data[1]
            game.score = data[2]
            game.shotsSinceDrop = data[3]
            game.parity = data[4]
            game.isOver = data[5] == 1
            game.current = data[6]
            game.next = data[7]
            game.mode = BubbleMode.fromIndex(data[8])
            game.dailySeed = data[9]
            game.swapCharges = data[10]
            game.specialCharge = data[11]
            game.flight = flight
            for (r in 0 until Bubble.MAX_ROWS) {
                for (c in 0 until Bubble.COLS) game.grid[r][c] = cells[r * Bubble.COLS + c]
            }
            return game
        }
    }
}
