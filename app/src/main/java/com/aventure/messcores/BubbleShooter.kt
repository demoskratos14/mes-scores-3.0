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
 * Réglages et calculs du Bubble Shooter.
 *
 * Le terrain est en unités : une bulle mesure une unité de diamètre. Les rangées sont décalées une sur deux d'une demi-bulle
 * (une grille hexagonale) ; chaque rangée a [COLS] bulles, le terrain fait donc [WIDTH] = [COLS] + 0,5 unité de large.
 * Le plafond est en haut (y = 0), le lanceur en bas.
 */
internal object Bubble {

    const val COLS = 10

    /** Nombre de rangées que la grille peut contenir (la partie est finie bien avant qu'elle soit pleine). */
    const val MAX_ROWS = 14

    /** Une bulle posée sur cette rangée (ou plus bas) fait perdre la partie : c'est la ligne rouge. */
    const val LOSE_ROW = 12

    /** Nombre de couleurs possibles, au total. */
    const val PALETTE = 6

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

    /** Nombre de couleurs au niveau [level] : 4 au départ, une de plus tous les deux niveaux, 6 au plus. */
    fun colorsFor(level: Int): Int = (3 + (level.coerceAtLeast(1) + 1) / 2).coerceAtMost(PALETTE)

    /** Nombre de rangées au départ du niveau [level]. */
    fun rowsFor(level: Int): Int = (4 + level.coerceAtLeast(1)).coerceAtMost(9)

    /** Nombre de tirs manqués (sans bulle éclatée) après lesquels le plafond descend d'une rangée au niveau [level]. */
    fun shotsPerDrop(level: Int): Int = (7 - (level.coerceAtLeast(1) - 1) / 2).coerceAtLeast(4)

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
 * [falling] vrai si elle tombe (elle ne tenait plus au plafond), faux si elle éclate (elle faisait partie du groupe de même couleur).
 */
internal class BubbleEffect(val x: Float, val y: Float, val color: Int, val falling: Boolean)

/**
 * Une partie de Bubble Shooter : des bulles de couleur accrochées au plafond, et un lanceur en bas qui en tire une à la fois.
 * La bulle tirée rebondit sur les murs et s'arrête contre le plafond ou une autre bulle. Un groupe de trois bulles de la même
 * couleur (ou plus) éclate ; celles qui ne tiennent plus au plafond tombent. Quand un tir ne fait rien éclater, un compteur
 * avance : à la fin, le plafond descend d'une rangée. Une bulle qui touche la ligne rouge ([Bubble.LOSE_ROW]) finit la partie ;
 * vider tout le tableau rapporte un bonus et lance le niveau suivant (plus de couleurs, plus de rangées, descente plus rapide).
 *
 * Les couleurs sont des numéros de 0 à [Bubble.PALETTE] - 1 ; [EMPTY] marque une case vide.
 */
internal class BubbleGame(private val random: Random = Random.Default) {

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

    /** La couleur de la bulle du lanceur, et celle de la suivante. */
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

    /** Repart de zéro : niveau 1, sans score. */
    fun reset() {
        level = 1
        score = 0
        isOver = false
        shotsSinceDrop = 0
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

    /** Pose un nouveau tableau : [Bubble.rowsFor] rangées de bulles tirées au hasard parmi les couleurs du niveau. */
    private fun fillLevel() {
        grid.forEach { it.fill(EMPTY) }
        parity = 0
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

    private fun randomPlayableColor(): Int {
        val present = presentColors()
        return if (present.isEmpty()) random.nextInt(Bubble.colorsFor(level)) else present[random.nextInt(present.size)]
    }

    // --- Les tirs ---

    /** Échange la bulle du lanceur avec la suivante. Sans effet pendant un tir. */
    fun swap(): Boolean {
        if (isOver || isFlying) return false
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
     * Pose une bulle de couleur [color] en ([row], [col]) et en tire les conséquences : le groupe de la même couleur éclate s'il
     * compte assez de bulles, celles qui ne tiennent plus tombent, le plafond descend après assez de tirs manqués, le tableau
     * vidé donne le niveau suivant, une bulle sur la ligne rouge finit la partie. Puis la bulle suivante passe au lanceur.
     */
    internal fun settle(row: Int, col: Int, color: Int) {
        grid[row][col] = color
        var popped = 0
        var fallen = 0
        val effects = mutableListOf<BubbleEffect>()
        val group = cluster(row, col)
        if (group.size >= Bubble.MIN_CLUSTER) {
            group.forEach {
                val r = it / Bubble.COLS
                val c = it % Bubble.COLS
                effects.add(BubbleEffect(centerX(r, c), centerY(r), grid[r][c], falling = false))
                grid[r][c] = EMPTY
            }
            popped = group.size
            fallen = dropFloating(effects)
        }
        lastPopped = popped
        lastFallen = fallen
        lastEffects = effects
        shotCount++
        score += Bubble.POP_POINTS * popped + Bubble.fallPoints(fallen)
        if (popped == 0) {
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
        next = randomPlayableColor()
        if (current !in presentColors()) current = randomPlayableColor()
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

    // --- La sauvegarde ---

    /**
     * L'état de la partie en une liste d'entiers, pour la garder quand l'écran tourne (voir [restore]) : version, niveau, score,
     * tirs manqués, parité, fin, bulle du lanceur, bulle suivante, puis la grille rangée par rangée. Une bulle en vol n'est pas
     * gardée : le tir est à refaire (la bulle du lanceur n'a pas bougé de la file tant qu'elle n'est pas posée).
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
        grid.forEach { row -> addAll(row.toList()) }
    }

    companion object {
        /** Case vide. */
        const val EMPTY = -1

        private const val SAVE_VERSION = 1
        private const val SAVE_HEADER = 8
        private const val MAX_PREVIEW_STEPS = 1000

        /** Rebâtit une partie depuis [data] (voir [save]), ou renvoie `null` si ces données ne sont pas celles d'une partie. */
        fun restore(data: List<Int>, random: Random = Random.Default): BubbleGame? {
            if (data.size != SAVE_HEADER + Bubble.MAX_ROWS * Bubble.COLS || data[0] != SAVE_VERSION) return null
            val cells = data.subList(SAVE_HEADER, data.size)
            val valid = data[1] >= 1 && data[2] >= 0 && data[3] >= 0 && data[4] in 0..1 && data[5] in 0..1 &&
                data[6] in 0 until Bubble.PALETTE && data[7] in 0 until Bubble.PALETTE &&
                cells.all { it in EMPTY until Bubble.PALETTE }
            if (!valid) return null
            val game = BubbleGame(random)
            game.level = data[1]
            game.score = data[2]
            game.shotsSinceDrop = data[3]
            game.parity = data[4]
            game.isOver = data[5] == 1
            game.current = data[6]
            game.next = data[7]
            for (r in 0 until Bubble.MAX_ROWS) {
                for (c in 0 until Bubble.COLS) game.grid[r][c] = cells[r * Bubble.COLS + c]
            }
            return game
        }
    }
}
