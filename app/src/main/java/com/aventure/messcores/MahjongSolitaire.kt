package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/** Identifiant du Mahjong solitaire parmi les jeux prédéfinis (écran à part : voir [MahjongScreen]). */
internal const val MAHJONG_ID = "builtin_mahjong"

/**
 * Un emplacement du plateau : sa position en demi-tuiles ([x] vers la droite, [y] vers le bas) et son étage [z]
 * (0 : le sol). Une tuile occupe 2 × 2 demi-tuiles ; en posant les étages à des demi-tuiles d'écart, une tuile
 * peut reposer à cheval sur deux tuiles de l'étage du dessous (c'est le cas de la tuile du sommet de la Tortue).
 */
internal data class MahjongSpot(val x: Int, val y: Int, val z: Int)

/** Les trois niveaux : la forme du plateau et donc le nombre de tuiles (toujours pair). */
internal enum class MahjongLevel(val tileCount: Int) {
    EASY(62),
    MEDIUM(104),
    HARD(144);

    /** Le plateau du niveau, construit à la première demande. */
    val layout: MahjongLayout by lazy { MahjongLayouts.build(this) }
}

/**
 * La forme d'un plateau : ses emplacements, et pour chacun les emplacements qui le recouvrent ou qui touchent ses côtés
 * gauche et droit (calculés une fois, pour que tester si une tuile est libre reste très rapide).
 */
internal class MahjongLayout(val spots: List<MahjongSpot>) {
    val size: Int = spots.size

    /** Largeur et hauteur du plateau en demi-tuiles, et plus haut étage. */
    val spanX: Int = (spots.maxOfOrNull { it.x } ?: 0) + 2
    val spanY: Int = (spots.maxOfOrNull { it.y } ?: 0) + 2
    val maxZ: Int = spots.maxOfOrNull { it.z } ?: 0

    // Un emplacement en recouvre un autre s'il est plus haut et le chevauche (même d'une demi-tuile).
    private val covering: Array<IntArray> = Array(size) { i ->
        val s = spots[i]
        (0 until size).filter { j ->
            val o = spots[j]
            o.z > s.z && abs(o.x - s.x) <= 1 && abs(o.y - s.y) <= 1
        }.toIntArray()
    }

    // Un voisin de côté est sur le même étage, juste à côté (une tuile de large), et le chevauche en hauteur.
    private val leftNeighbors: Array<IntArray> = neighbors(-2)
    private val rightNeighbors: Array<IntArray> = neighbors(2)

    /** L'ordre de dessin : du sol vers le sommet, puis de haut en bas et de gauche à droite (une tuile cache le bord de sa voisine). */
    val drawOrder: IntArray = spots.indices
        .sortedWith(compareBy<Int>({ spots[it].z }, { spots[it].y }, { spots[it].x }))
        .toIntArray()

    private fun neighbors(dx: Int): Array<IntArray> = Array(size) { i ->
        val s = spots[i]
        (0 until size).filter { j ->
            val o = spots[j]
            o.z == s.z && o.x == s.x + dx && abs(o.y - s.y) <= 1
        }.toIntArray()
    }

    /**
     * Vrai si la tuile de l'emplacement [i] est libre dans l'état [alive] (vrai pour les emplacements où une tuile
     * reste) : aucune tuile dessus, et au moins un de ses deux côtés, gauche ou droit, dégagé.
     */
    fun isFree(i: Int, alive: BooleanArray): Boolean {
        if (!alive[i]) return false
        if (anyAlive(covering[i], alive)) return false
        return !(anyAlive(leftNeighbors[i], alive) && anyAlive(rightNeighbors[i], alive))
    }

    private fun anyAlive(list: IntArray, alive: BooleanArray): Boolean {
        for (j in list) if (alive[j]) return true
        return false
    }
}

/** Les trois plateaux du jeu. */
internal object MahjongLayouts {

    /** Un bloc rectangulaire de [cols] × [rows] tuiles à l'étage [z], dont la première est à la colonne [col0], rangée [row0]. */
    private fun block(cols: Int, rows: Int, z: Int, col0: Int, row0: Int): List<MahjongSpot> =
        List(cols * rows) { MahjongSpot(2 * (col0 + it % cols), 2 * (row0 + it / cols), z) }

    fun build(level: MahjongLevel): MahjongLayout = MahjongLayout(
        when (level) {
            MahjongLevel.EASY -> pyramid(8, 5)
            MahjongLevel.MEDIUM -> pyramid(10, 6)
            MahjongLevel.HARD -> turtle()
        }
    )

    /** Une pyramide à trois étages : un rectangle de [cols] × [rows] tuiles, un plus petit dessus, puis une rangée au sommet. */
    private fun pyramid(cols: Int, rows: Int): List<MahjongSpot> = buildList {
        addAll(block(cols, rows, 0, 0, 0))
        addAll(block(cols - 2, rows - 2, 1, 1, 1))
        addAll(block(cols - 4, if (rows >= 6) 2 else 1, 2, 2, 2))
    }

    /** La Tortue, le plateau classique de 144 tuiles : 87 au sol, puis 36, 16, 4 et une seule au sommet. */
    private fun turtle(): List<MahjongSpot> = buildList {
        // Huit rangées au sol (première et dernière colonne de chacune), plus les trois tuiles « de côté ».
        val rows = listOf(1 to 12, 3 to 10, 2 to 11, 1 to 12, 1 to 12, 2 to 11, 3 to 10, 1 to 12)
        rows.forEachIndexed { r, (from, to) ->
            for (c in from..to) add(MahjongSpot(2 * c, 2 * r, 0))
        }
        add(MahjongSpot(0, 7, 0))
        add(MahjongSpot(26, 7, 0))
        add(MahjongSpot(28, 7, 0))
        addAll(block(6, 6, 1, 4, 1))
        addAll(block(4, 4, 2, 5, 2))
        addAll(block(2, 2, 3, 6, 3))
        add(MahjongSpot(13, 7, 4))
    }
}

/**
 * Les 42 sortes de tuiles d'un jeu de 144 : trois familles de neuf (cercles, bambous, caractères), les quatre vents,
 * les trois dragons, puis les quatre fleurs et les quatre saisons. Chaque sorte des 34 premières existe en quatre
 * exemplaires ; les fleurs et les saisons n'en ont qu'un.
 */
internal object MahjongTiles {
    const val KINDS = 42
    const val DOTS = 0
    const val BAMBOO = 9
    const val CHARACTERS = 18
    const val WINDS = 27
    const val DRAGONS = 31
    const val FLOWERS = 34
    const val SEASONS = 38

    /** Le groupe de [kind] pour les paires : les quatre fleurs vont ensemble, les quatre saisons aussi, les autres tuiles par sorte. */
    fun matchGroup(kind: Int): Int = when {
        kind >= SEASONS -> SEASONS
        kind >= FLOWERS -> FLOWERS
        else -> kind
    }

    /** Vrai si les deux sortes de tuiles forment une paire. */
    fun canMatch(a: Int, b: Int): Boolean = matchGroup(a) == matchGroup(b)

    /** Les 72 paires d'un jeu complet : deux par sorte des 34 premières, deux de fleurs et deux de saisons. */
    fun allPairs(): List<Pair<Int, Int>> = buildList {
        for (kind in 0 until FLOWERS) {
            add(kind to kind)
            add(kind to kind)
        }
        add(FLOWERS to FLOWERS + 1)
        add(FLOWERS + 2 to FLOWERS + 3)
        add(SEASONS to SEASONS + 1)
        add(SEASONS + 2 to SEASONS + 3)
    }
}

/**
 * La donne : on pose les paires en jouant la partie à l'envers. On prend deux tuiles libres du plateau plein, on leur
 * donne une paire, on les retire, et ainsi de suite jusqu'au plateau vide. Rejouer ces retraits dans le même ordre
 * gagne la partie : la donne a donc toujours une solution.
 */
internal object MahjongDeal {

    /**
     * Répartit [pairs] sur les emplacements marqués dans [present] (il y en a deux fois plus que de paires) : renvoie la
     * sorte de tuile de chaque emplacement (-1 pour les emplacements vides), ou null si [attempts] essais n'ont pas suffi
     * (une impasse se rencontre quand il ne reste qu'une tuile libre alors que d'autres attendent).
     */
    fun solvable(
        layout: MahjongLayout,
        present: BooleanArray,
        pairs: List<Pair<Int, Int>>,
        random: Random,
        attempts: Int = 200
    ): IntArray? {
        val n = layout.size
        val free = IntArray(n)
        repeat(attempts) {
            val alive = present.copyOf()
            val kinds = IntArray(n) { -1 }
            var complete = true
            for (pair in pairs.shuffled(random)) {
                var count = 0
                for (i in 0 until n) if (layout.isFree(i, alive)) free[count++] = i
                if (count < 2) {
                    complete = false
                    break
                }
                val a = random.nextInt(count)
                var b = random.nextInt(count - 1)
                if (b >= a) b++
                kinds[free[a]] = pair.first
                kinds[free[b]] = pair.second
                alive[free[a]] = false
                alive[free[b]] = false
            }
            if (complete) return kinds
        }
        return null
    }
}

/** Réglages et calculs du Mahjong solitaire. */
internal object Mahjong {

    /** Points par tuile retirée : gagner rapporte au moins ce nombre de points par tuile du plateau. */
    const val POINTS_PER_TILE = 10

    /** Temps de référence par paire, en secondes : on gagne un bonus tant qu'on finit avant. */
    const val PAR_SECONDS_PER_PAIR = 8

    /** Points de bonus par seconde gagnée sur le temps de référence. */
    const val BONUS_PER_SECOND = 2

    /** Points perdus à chaque indice demandé et à chaque mélange des tuiles. */
    const val HINT_COST = 50
    const val SHUFFLE_COST = 100

    /**
     * Le score d'une partie gagnée en [seconds] secondes au niveau [level] : 10 points par tuile, plus 2 points par
     * seconde gagnée sur le temps de référence (8 secondes par paire), moins 50 points par indice et 100 par mélange.
     * Une partie gagnée marque toujours au moins un point par tuile ; une partie perdue ne marque rien.
     */
    fun scoreFor(level: MahjongLevel, seconds: Int, hints: Int, shuffles: Int): Int {
        val par = level.tileCount / 2 * PAR_SECONDS_PER_PAIR
        val bonus = max(0, par - seconds.coerceAtLeast(0)) * BONUS_PER_SECOND
        val penalty = hints.coerceAtLeast(0) * HINT_COST + shuffles.coerceAtLeast(0) * SHUFFLE_COST
        return max(level.tileCount, level.tileCount * POINTS_PER_TILE + bonus - penalty)
    }

    /** L'identifiant sous lequel le record du niveau [level] est gardé (un record par niveau). */
    fun recordKey(level: MahjongLevel): String = "${MAHJONG_ID}_${level.name.lowercase()}"
}

/**
 * Une partie de Mahjong solitaire : on retire les tuiles du plateau deux par deux. Une tuile est libre quand rien ne la
 * recouvre et que l'un de ses côtés, gauche ou droit, est dégagé ; deux tuiles libres identiques forment une paire (les
 * quatre fleurs vont ensemble, les quatre saisons aussi). Le plateau vidé gagne la partie.
 *
 * La donne est toujours résoluble (voir [MahjongDeal]). Quand il ne reste plus de paire à retirer, [shuffle] redistribue
 * les tuiles restantes, encore de façon résoluble ; si même cela est impossible, la partie est perdue.
 * Les emplacements sont numérotés comme [MahjongLayout.spots].
 */
internal class MahjongGame(val level: MahjongLevel, private val random: Random = Random.Default) {

    val layout: MahjongLayout = level.layout
    val size: Int = layout.size

    /** La sorte de tuile de chaque emplacement (voir [MahjongTiles]), et ceux où une tuile reste. */
    private val kinds = IntArray(size)
    private val alive = BooleanArray(size) { true }

    /** Les paires retirées, de la plus ancienne à la plus récente : de quoi annuler. */
    private val history = ArrayList<Pair<Int, Int>>()

    /** L'étape de la partie : [PLAYING], [WON] ou [LOST]. */
    var state = PLAYING
        private set

    /** L'emplacement de la tuile choisie en attente de sa paire, ou -1. */
    var selected = -1
        private set

    /** Nombre d'indices demandés et de mélanges faits : ils coûtent des points. */
    var hints = 0
        private set
    var shuffles = 0
        private set

    /** Temps de jeu écoulé, en millisecondes. */
    var elapsedMs = 0L
        private set

    /** Nombre de tuiles encore sur le plateau. */
    var remaining = size
        private set

    init {
        dealNew()
    }

    val isFinished: Boolean get() = state != PLAYING

    /** Temps écoulé en secondes entières. */
    val seconds: Int get() = (elapsedMs / 1000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** Le score de la partie : celui de [Mahjong.scoreFor] si elle est gagnée, 0 sinon. */
    val score: Int get() = if (state == WON) Mahjong.scoreFor(level, seconds, hints, shuffles) else 0

    /** Vrai s'il reste une paire retirée qu'on peut reprendre. */
    val canUndo: Boolean get() = state == PLAYING && history.isNotEmpty()

    fun isRemoved(i: Int): Boolean = !alive[i]

    /** La sorte de la tuile de l'emplacement [i] (voir [MahjongTiles]). */
    fun kindAt(i: Int): Int = kinds[i]

    /** Vrai si la tuile de l'emplacement [i] est libre. */
    fun isFree(i: Int): Boolean = layout.isFree(i, alive)

    /** Fait avancer le chronomètre de [ms] millisecondes ; sans effet une fois la partie finie. */
    fun tick(ms: Long) {
        if (state == PLAYING && ms > 0) elapsedMs += ms
    }

    /** Recommence une partie du même niveau, avec une nouvelle donne. */
    fun reset() {
        alive.fill(true)
        history.clear()
        state = PLAYING
        selected = -1
        hints = 0
        shuffles = 0
        elapsedMs = 0L
        remaining = size
        dealNew()
    }

    /** Une première paire de tuiles libres identiques (celle qui contient la tuile [prefer] si on peut), ou null s'il n'y en a pas. */
    fun findMove(prefer: Int = -1): Pair<Int, Int>? {
        val free = (0 until size).filter { layout.isFree(it, alive) }
        if (prefer in free) {
            val partner = free.firstOrNull { it != prefer && MahjongTiles.canMatch(kinds[it], kinds[prefer]) }
            if (partner != null) return prefer to partner
        }
        for (a in free.indices) {
            for (b in a + 1 until free.size) {
                if (MahjongTiles.canMatch(kinds[free[a]], kinds[free[b]])) return free[a] to free[b]
            }
        }
        return null
    }

    /** Vrai s'il reste au moins une paire à retirer. */
    fun hasMove(): Boolean = findMove() != null

    /**
     * Un geste sur la tuile de l'emplacement [i] : le résultat est l'une des constantes `TAP_` (voir plus bas). Toucher
     * une tuile libre la choisit ; toucher ensuite une tuile qui lui est identique retire la paire ; toucher une autre
     * tuile libre change de choix ; toucher la tuile choisie annule le choix. Une tuile bloquée ne fait rien.
     */
    fun tap(i: Int): Int {
        if (state != PLAYING || i !in 0 until size || !alive[i]) return TAP_IGNORED
        if (!layout.isFree(i, alive)) return TAP_BLOCKED
        if (i == selected) {
            selected = -1
            return TAP_UNSELECTED
        }
        if (selected >= 0 && MahjongTiles.canMatch(kinds[selected], kinds[i])) {
            remove(selected, i)
            selected = -1
            return TAP_MATCHED
        }
        selected = i
        return TAP_SELECTED
    }

    /** Un indice : une paire qu'on peut retirer (de préférence avec la tuile choisie). Il coûte des points ; null s'il n'y en a pas. */
    fun hint(): Pair<Int, Int>? {
        if (state != PLAYING) return null
        val move = findMove(selected) ?: return null
        hints++
        return move
    }

    /** Reprend la dernière paire retirée. Renvoie vrai si quelque chose a changé. */
    fun undo(): Boolean {
        if (!canUndo) return false
        val (a, b) = history.removeAt(history.lastIndex)
        alive[a] = true
        alive[b] = true
        remaining += 2
        selected = -1
        return true
    }

    /**
     * Redistribue les tuiles restantes sur leurs emplacements, de façon à ce que la partie reste résoluble ; ça coûte des
     * points et on ne peut plus annuler les paires d'avant. Renvoie vrai si les tuiles ont été redistribuées. Quand il n'y
     * a plus de paire à retirer et que la redistribution est impossible (par exemple deux dernières tuiles l'une sur
     * l'autre), la partie est perdue.
     */
    fun shuffle(): Boolean {
        if (state != PLAYING) return false
        val dealt = MahjongDeal.solvable(layout, alive, remainingPairs(), random)
        if (dealt == null) {
            if (findMove() == null) state = LOST
            return false
        }
        for (i in 0 until size) if (alive[i]) kinds[i] = dealt[i]
        selected = -1
        history.clear()
        shuffles++
        return true
    }

    private fun remove(a: Int, b: Int) {
        alive[a] = false
        alive[b] = false
        remaining -= 2
        history.add(a to b)
        if (remaining == 0) state = WON
    }

    /** Les tuiles restantes rangées en paires : chaque groupe en compte un nombre pair, puisqu'on ne retire que des paires. */
    private fun remainingPairs(): List<Pair<Int, Int>> {
        val byGroup = HashMap<Int, ArrayList<Int>>()
        for (i in 0 until size) {
            if (alive[i]) byGroup.getOrPut(MahjongTiles.matchGroup(kinds[i])) { ArrayList() }.add(kinds[i])
        }
        val pairs = ArrayList<Pair<Int, Int>>()
        for (group in byGroup.values) {
            var k = 0
            while (k + 1 < group.size) {
                pairs.add(group[k] to group[k + 1])
                k += 2
            }
        }
        return pairs
    }

    /** Distribue une donne neuve : les paires d'un jeu complet tirées au hasard, posées de façon résoluble. */
    private fun dealNew() {
        val pairs = MahjongTiles.allPairs().shuffled(random).take(size / 2)
        val dealt = MahjongDeal.solvable(layout, alive, pairs, random)
        if (dealt != null) {
            dealt.copyInto(kinds)
        } else {
            // Presque impossible : on mélange simplement, sans la garantie de solution.
            val flat = pairs.flatMap { listOf(it.first, it.second) }.shuffled(random)
            for (i in 0 until size) kinds[i] = flat[i]
        }
    }

    /** L'état de la partie rangé dans une liste d'entiers (en-tête, sortes, emplacements occupés, paires retirées) : voir [restore]. */
    fun save(): List<Int> {
        val out = ArrayList<Int>(HEADER + 2 * size + 1 + 2 * history.size)
        out.add(level.ordinal)
        out.add(state)
        out.add(elapsedMs.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        out.add(hints)
        out.add(shuffles)
        out.add(selected)
        for (k in kinds) out.add(k)
        for (a in alive) out.add(if (a) 1 else 0)
        out.add(history.size)
        for ((a, b) in history) {
            out.add(a)
            out.add(b)
        }
        return out
    }

    /** Remet la partie dans l'état de [data] (rangé par [save] pour le même niveau) ; sans effet si la liste ne convient pas. */
    private fun load(data: List<Int>) {
        if (data.size < HEADER + 2 * size + 1) return
        val savedState = data[1]
        if (savedState !in PLAYING..LOST) return
        val savedKinds = IntArray(size) { data[HEADER + it] }
        val savedAlive = BooleanArray(size) { data[HEADER + size + it] == 1 }
        if (savedKinds.any { it !in 0 until MahjongTiles.KINDS }) return
        if (data.subList(HEADER + size, HEADER + 2 * size).any { it != 0 && it != 1 }) return
        // Chaque groupe de tuiles doit rester en nombre pair : sinon la partie ne pourrait pas se terminer.
        val perGroup = IntArray(MahjongTiles.KINDS)
        for (i in 0 until size) if (savedAlive[i]) perGroup[MahjongTiles.matchGroup(savedKinds[i])]++
        if (perGroup.any { it % 2 != 0 }) return
        val historySize = data[HEADER + 2 * size]
        if (historySize < 0 || data.size != HEADER + 2 * size + 1 + 2 * historySize) return
        val savedHistory = ArrayList<Pair<Int, Int>>(historySize)
        for (h in 0 until historySize) {
            val a = data[HEADER + 2 * size + 1 + 2 * h]
            val b = data[HEADER + 2 * size + 2 + 2 * h]
            if (a !in 0 until size || b !in 0 until size || a == b || savedAlive[a] || savedAlive[b]) return
            savedHistory.add(a to b)
        }
        savedKinds.copyInto(kinds)
        savedAlive.copyInto(alive)
        history.clear()
        history.addAll(savedHistory)
        remaining = savedAlive.count { it }
        state = when {
            remaining == 0 -> WON
            savedState == WON -> PLAYING
            else -> savedState
        }
        elapsedMs = data[2].toLong().coerceAtLeast(0L)
        hints = data[3].coerceAtLeast(0)
        shuffles = data[4].coerceAtLeast(0)
        val savedSelected = data[5]
        selected = if (savedSelected in 0 until size && alive[savedSelected] && layout.isFree(savedSelected, alive)) savedSelected else -1
    }

    companion object {
        /** Les étapes de la partie. */
        const val PLAYING = 0
        const val WON = 1
        const val LOST = 2

        /** Les résultats d'un geste sur une tuile (voir [tap]). */
        const val TAP_IGNORED = 0
        const val TAP_BLOCKED = 1
        const val TAP_SELECTED = 2
        const val TAP_UNSELECTED = 3
        const val TAP_MATCHED = 4

        /** Nombre d'entiers de l'en-tête rangé par [save] : niveau, étape, temps, indices, mélanges, tuile choisie. */
        private const val HEADER = 6

        /** Une partie remise dans l'état rangé par [save] ; une partie neuve du premier niveau si la liste ne convient pas. */
        fun restore(data: List<Int>): MahjongGame {
            val level = MahjongLevel.entries.getOrNull(data.getOrNull(0) ?: -1) ?: MahjongLevel.EASY
            return MahjongGame(level).also { it.load(data) }
        }

        /**
         * Une partie dont les tuiles sont les sortes [kinds] (une par emplacement), pour les tests : la donne n'est pas
         * tirée au hasard, et rien ne garantit qu'elle ait une solution.
         */
        fun withKinds(level: MahjongLevel, kinds: IntArray): MahjongGame {
            require(kinds.size == level.layout.size) { "il faut exactement ${level.layout.size} tuiles" }
            val game = MahjongGame(level)
            kinds.copyInto(game.kinds)
            return game
        }
    }
}
