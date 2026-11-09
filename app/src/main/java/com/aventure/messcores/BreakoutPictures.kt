package com.aventure.messcores

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Un dessin du Casse-briques : une émoticône en pixels, 16 × 16 au départ (une lettre par pixel, voir
 * [BreakoutPictures.LETTERS] ; '.' pour le vide). Le dessin est rogné à son contour : [width] et [height] sont ceux
 * de la partie dessinée, pour que l'image occupe tout le terrain quelle que soit sa marge.
 */
internal class BreakoutPicture(val emoji: String, val tier: Int, art: List<String>) {

    // [rangée][colonne] : le rang de la lettre dans BreakoutPictures.LETTERS (0 : vide).
    private val grid: Array<IntArray>
    val width: Int
    val height: Int

    init {
        require(art.size == BreakoutPictures.SIZE && art.all { it.length == BreakoutPictures.SIZE }) { "dessin mal formé : $emoji" }
        val full = art.map { line ->
            IntArray(line.length) { i ->
                BreakoutPictures.LETTERS.indexOf(line[i]).also { require(it >= 0) { "lettre inconnue dans $emoji" } }
            }
        }
        val usedRows = full.indices.filter { j -> full[j].any { it != 0 } }
        val usedCols = (0 until BreakoutPictures.SIZE).filter { i -> full.any { it[i] != 0 } }
        require(usedRows.isNotEmpty() && usedCols.isNotEmpty()) { "dessin vide : $emoji" }
        val top = usedRows.first()
        val left = usedCols.first()
        height = usedRows.last() - top + 1
        width = usedCols.last() - left + 1
        grid = Array(height) { j -> IntArray(width) { i -> full[top + j][left + i] } }
    }

    /** Le rang de la couleur du pixel (col, row) du dessin rogné (0 : vide). */
    fun pixel(col: Int, row: Int): Int = grid[row][col]
}

/**
 * Les 28 dessins des niveaux du Casse-briques, en quatre catégories de sept, de la moins détaillée à la plus
 * détaillée. Les niveaux les prennent dans cet ordre de catégories, dans un ordre tiré au hasard à l'intérieur de
 * chacune (voir [forLevel]) ; une fois les 28 passés, la liste recommence, sur un terrain plus grand qu'au premier tour.
 */
internal object BreakoutPictures {

    const val SIZE = 16
    const val TIERS = 4
    const val TIER_SIZE = 7
    const val COUNT = TIERS * TIER_SIZE

    /** Une lettre par couleur : le rang d'une lettre est celui de sa couleur dans [ARGB]. */
    const val LETTERS = ".ROYAGDLBNCPVWSKMT"

    private val ARGB = intArrayOf(
        0, 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFFFFB300.toInt(), 0xFF43A047.toInt(), 0xFF2E7D32.toInt(), 0xFF9CCC65.toInt(), 0xFF1E88E5.toInt(), 0xFF5C6BC0.toInt(), 0xFF4DD0E1.toInt(), 0xFFF06292.toInt(), 0xFFAB47BC.toInt(), 0xFFF5F5F5.toInt(), 0xFFB0BEC5.toInt(), 0xFF607D8B.toInt(), 0xFF8D6E63.toInt(), 0xFFFFCC80.toInt()
    )

    /**
     * Étirement horizontal d'un dessin : une brique est deux fois plus large que haute, donc un dessin carré à ses
     * proportions naturelles serait minuscule au niveau 1 (4 briques de large). On l'élargit de 60 %.
     */
    private const val STRETCH = 1.6

    /** Part minimale d'une case qui doit être couverte par le dessin pour y mettre une brique. */
    private const val MIN_COVER = 0.45

    private const val MIN_WIDTH = 4
    private const val MIN_HEIGHT = 3

    /** Les dessins, par catégorie (de la moins à la plus détaillée). */
    val all: List<List<BreakoutPicture>> by lazy {
        val list = PICTURES
        check(list.size == COUNT)
        List(TIERS) { tier -> list.filter { it.tier == tier }.also { check(it.size == TIER_SIZE) } }
    }

    /** Le rang du niveau [level] parmi les niveaux à dessin (les niveaux horloge ne comptent pas). */
    fun indexOf(level: Int): Int {
        val before = max(1, level) - 1
        return before - before / Breakout.CLOCK_EVERY
    }

    /**
     * Le dessin du niveau [level] pour l'ordre déterminé par [seed] (gardé dans la sauvegarde), ou null pour un niveau
     * horloge. Les sept premiers niveaux à dessin prennent la catégorie 1, les sept suivants la 2, etc. ; après la
     * catégorie 4 tout recommence, avec un nouvel ordre dans chaque catégorie.
     */
    fun forLevel(level: Int, seed: Int): BreakoutPicture? {
        if (Breakout.isClockLevel(level)) return null
        val index = indexOf(level)
        val lap = index / COUNT
        val position = index % COUNT
        val tier = position / TIER_SIZE
        val order = (0 until TIER_SIZE).shuffled(Random(seed.toLong() * 7919L + (lap * TIERS + tier)))
        return all[tier][order[position % TIER_SIZE]]
    }

    /**
     * Le dessin posé sur un terrain de [cols] × [rows] cases : la couleur (ARGB) de chaque case, rangée par rangée,
     * ou 0 quand elle reste vide. Le dessin est centré en largeur et collé en haut ; il garde ses proportions (à
     * l'étirement [STRETCH] près) et ne dépasse jamais le terrain. Chaque case prend la couleur la plus présente sous
     * elle, si le dessin la couvre assez ([MIN_COVER]).
     */
    fun fit(picture: BreakoutPicture, cols: Int, rows: Int): IntArray {
        val out = IntArray(cols * rows)
        val bw = picture.width
        val bh = picture.height
        val brickH = Breakout.BRICK_H.toDouble()
        val aspect = bw.toDouble() / bh
        val wide = rows * brickH * aspect * STRETCH
        var w = min(cols, max(MIN_WIDTH, wide.roundToInt()))
        var h = rows
        if (wide > cols) h = min(rows, max(MIN_HEIGHT, (cols / (aspect * STRETCH) / brickH).roundToInt()))
        // Même parité que le terrain : le dessin est alors exactement au milieu.
        if ((cols - w) % 2 != 0) w = if (w < cols) w + 1 else w - 1
        val left = (cols - w) / 2
        val weights = DoubleArray(LETTERS.length)
        for (r in 0 until h) {
            val y0 = r * bh.toDouble() / h
            val y1 = (r + 1) * bh.toDouble() / h
            for (c in 0 until w) {
                val x0 = c * bw.toDouble() / w
                val x1 = (c + 1) * bw.toDouble() / w
                weights.fill(0.0)
                var total = 0.0
                for (j in floor(y0).toInt() until min(bh, ceil(y1).toInt())) {
                    val wy = min((j + 1).toDouble(), y1) - max(j.toDouble(), y0)
                    if (wy <= 0.0) continue
                    for (i in floor(x0).toInt() until min(bw, ceil(x1).toInt())) {
                        val wx = min((i + 1).toDouble(), x1) - max(i.toDouble(), x0)
                        if (wx <= 0.0) continue
                        weights[picture.pixel(i, j)] += wx * wy
                        total += wx * wy
                    }
                }
                if (total > 0.0 && total - weights[0] >= MIN_COVER * total) {
                    var best = 1
                    for (k in 2 until weights.size) if (weights[k] > weights[best]) best = k
                    out[r * cols + left + c] = ARGB[best]
                }
            }
        }
        return out
    }

    private fun pic(emoji: String, tier: Int, art: String) =
        BreakoutPicture(emoji, tier, art.trim().lines().map { it.trim() })

    private val PICTURES: List<BreakoutPicture> by lazy {
        listOf(
        // Catégorie 1 : silhouettes pleines, presque sans détail (niveaux 1 à 7).
        pic(
            "❤️", 0,
            """
            ..RRRR....RRRR..
            .RRRRRR..RRRRRR.
            RRRRRRRRRRRRRRRR
            RRRRRRRRRRRRRRRR
            RRRRRRRRRRRRRRRR
            RRRRRRRRRRRRRRRR
            RRRRRRRRRRRRRRRR
            .RRRRRRRRRRRRRR.
            .RRRRRRRRRRRRRR.
            ..RRRRRRRRRRRR..
            ...RRRRRRRRRR...
            ....RRRRRRRR....
            .....RRRRRR.....
            ......RRRR......
            .......RR.......
            .......RR.......
            """
        ),
        pic(
            "⭐", 0,
            """
            .......YY.......
            .......YY.......
            ......YYYY......
            ......YYYY......
            YYYYYYYYYYYYYYYY
            .YYYYYYYYYYYYYY.
            ..YYYYYYYYYYYY..
            ...YYYYYYYYYY...
            ....YYYYYYYY....
            ....YYYYYYYY....
            ...YYYYYYYYYY...
            ...YYYY..YYYY...
            ..YYYY....YYYY..
            ..YYY......YYY..
            .YYY........YYY.
            .YY..........YY.
            """
        ),
        pic(
            "🍄", 0,
            """
            ....RRRRRRRR....
            ..RRRRRWWRRRRR..
            .RRWWRRWWRRWWRR.
            .RRWWRRRRRRWWRR.
            RRRRRRRRRRRRRRRR
            RRRRRRRRRRRRRRRR
            RRRRRRRRRRRRRRRR
            .TTTTTTTTTTTTTT.
            ....TTTTTTTT....
            ....TTTTTTTT....
            ....TTTTTTTT....
            ....TTTTTTTT....
            ....TTTTTTTT....
            ....TTTTTTTT....
            ....TTTTTTTT....
            ....TTTTTTTT....
            """
        ),
        pic(
            "☀️", 0,
            """
            ......OOOO......
            ...O..OOOO..O...
            ..OOO.OOOO.OOO..
            .OOOO......OOOO.
            ..OO.YYYYYY.OO..
            ....YYYYYYYY....
            OOO.YYYYYYYY.OOO
            OOO.YYYYYYYY.OOO
            OOO.YYYYYYYY.OOO
            OOO.YYYYYYYY.OOO
            ....YYYYYYYY....
            ..OO.YYYYYY.OO..
            .OOOO......OOOO.
            ..OOO.OOOO.OOO..
            ...O..OOOO..O...
            ......OOOO......
            """
        ),
        pic(
            "🇨🇵", 0,
            """
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            BBBBBWWWWWWRRRRR
            """
        ),
        pic(
            "🙂", 0,
            """
            .....YYYYYY.....
            ...YYYYYYYYYY...
            ..YYYYYYYYYYYY..
            .YYYYYYYYYYYYYY.
            YYYY...YY...YYYY
            YYYY...YY...YYYY
            YYYY...YY...YYYY
            YYYYYYYYYYYYYYYY
            YY.YYYYYYYYYY.YY
            YYY.YYYYYYYY.YYY
            YYYY........YYYY
            YYYYYYYYYYYYYYYY
            .YYYYYYYYYYYYYY.
            ..YYYYYYYYYYYY..
            ...YYYYYYYYYY...
            .....YYYYYY.....
            """
        ),
        pic(
            "🎄", 0,
            """
            ......YYYY......
            .......GG.......
            ......GGGG......
            .....GGGGGG.....
            ....GGGGGGGG....
            ......GGGG......
            .....GGGGGG.....
            ....GGGGGGGG....
            ...GRGGGGGGRG...
            .....GGGGGG.....
            ....GGGGGGGG....
            ...GGGGGGGGGG...
            ..GGGGRGGRGGGG..
            .GGGGGGGGGGGGGG.
            ......MMMM......
            ......MMMM......
            """
        ),
        // Catégorie 2 : formes simples (niveaux 9 à 15).
        pic(
            "🍀", 1,
            """
            ................
            ...GGG....GGG...
            ..GGGGG..GGGGG..
            .GGGGGG..GGGGGG.
            .GGGGGG..GGGGGG.
            ..GGGGG..GGGGG..
            ..GGGGGGGGGGGG..
            ...GGGGGGGGGG...
            ..GGGGGDDGGGGG..
            .GGGGGGDDGGGGGG.
            .GGGGGGDDGGGGGG.
            ..GGGGGDDGGGGG..
            ...GGG.DD.GGG...
            .......DD.......
            .......DDD......
            .......DDDDD....
            """
        ),
        pic(
            "🚀", 1,
            """
            .......RR.......
            ......RRRR......
            .....RRRRRR.....
            .....RRRRRR.....
            ....WWWWWWWW....
            ....WWWWWWWW....
            ....WWBBBBWW....
            ....WWBBBBWW....
            ....WWWWWWWW....
            ....WWWWWWWW....
            ..RRWWWWWWWWRR..
            .RRRWWWWWWWWRRR.
            .RRRWWWWWWWWRRR.
            ....OOYYYYOO....
            .....OYYYYO.....
            .......YY.......
            """
        ),
        pic(
            "⚓", 1,
            """
            ......BBBB......
            ......B..B......
            ......BBBB......
            .......BB.......
            ..BBBBBBBBBBBB..
            ..BBBBBBBBBBBB..
            .......BB.......
            .......BB.......
            .......BB.......
            .......BB.......
            .......BB.......
            B......BB......B
            BB.....BB.....BB
            .BBB...BB...BBB.
            ..BBBBBBBBBBBB..
            ....BBBBBBBB....
            """
        ),
        pic(
            "🛸", 1,
            """
            .....CCCCCC.....
            ...CCCCCCCCCC...
            ..CCCCCCCCCCCC..
            ..CCCCCCCCCCCC..
            ...SSSSSSSSSS...
            .SSSSSSSSSSSSSS.
            SSSSSSSSSSSSSSSS
            SYSSYSSYYSSYSSYS
            SSSSSSSSSSSSSSSS
            .SSSSSSSSSSSSSS.
            ....SSSSSSSS....
            ......YYYY......
            .....YYYYYY.....
            ....YYYYYYYY....
            ...YYYYYYYYYY...
            ..YYYYYYYYYYYY..
            """
        ),
        pic(
            "🐰", 1,
            """
            ...WWW....WWW...
            ...WPW....WPW...
            ...WPW....WPW...
            ...WPW....WPW...
            ...WPW....WPW...
            ...WWW....WWW...
            ..WWWWWWWWWWWW..
            .WWWWWWWWWWWWWW.
            .WWWWWWWWWWWWWW.
            .WWKWWWWWWWWKWW.
            .WWWWWWWWWWWWWW.
            .WWWWWPPPPWWWWW.
            ..WWWWWWWWWWWW..
            ...WWWWWWWWWW...
            ....WWWWWWWW....
            .....WWWWWW.....
            """
        ),
        pic(
            "👾", 1,
            """
            ...G........G...
            ...G........G...
            ....GG....GG....
            ....GG....GG....
            ...GGGGGGGGGG...
            ...GGGGGGGGGG...
            .GGG..GGGG..GGG.
            .GGG..GGGG..GGG.
            GGGGGGGGGGGGGGGG
            GGGGGGGGGGGGGGGG
            G..GGGGGGGGGG..G
            G..GGGGGGGGGG..G
            G..G........G..G
            G..G........G..G
            ....GGG..GGG....
            ....GGG..GGG....
            """
        ),
        pic(
            "☢️", 1,
            """
            ......YYYY......
            ....YYYYYYYY....
            .....YYYYYY.....
            .....YYYYYY.....
            ......YYYY......
            ......Y..Y......
            .......OO.......
            ......OOOO......
            YYYYY.OOOO.YYYYY
            YYYYYY.OO.YYYYYY
            .YYYYYY..YYYYYY.
            .YYYYY....YYYYY.
            ..YYYY....YYYY..
            ..YYY......YYY..
            ....Y......Y....
            ................
            """
        ),
        // Catégorie 3 : formes moyennes (niveaux 17 à 23).
        pic(
            "👽", 2,
            """
            ................
            ....GGGGGGGG....
            ...GGGGGGGGGG...
            ..GGGGGGGGGGGG..
            .GGGGGGGGGGGGGG.
            .GG.GGGGGGGG.GG.
            .GG..GGGGGG..GG.
            .GG....GG....GG.
            ..GG...GG...GG..
            ..GGGGGGGGGGGG..
            ...GGGGGGGGGG...
            ....GGGGGGGG....
            ....GGGGGGGG....
            .....GGGGGG.....
            .....GGGGGG.....
            .....GGGGGG.....
            """
        ),
        pic(
            "🦀", 2,
            """
            ................
            .R..R......R..R.
            RR.RR......RR.RR
            RRRRRR....RRRRRR
            RRRRRWW..WWRRRRR
            .RRRRWW..WWRRRR.
            ..RR.RRRRRR.RR..
            ...RRRRRRRRRR...
            ...RRRRRRRRRR...
            ..RRRRRRRRRRRR..
            ..RRRRRRRRRRRR..
            .RRRRRRRRRRRRRR.
            RR.RRRRRRRRRR.RR
            R.R.R.RRRR.R.R.R
            .RR.R......R.RR.
            .R.RR......RR.R.
            """
        ),
        pic(
            "🧸", 2,
            """
            ..MM........MM..
            .MMMMM.MM.MMMMM.
            .MTTMMMMMMMMTTM.
            .MTTMMMMMMMMTTM.
            .MMMMMMMMMMMMMM.
            ..MMMKMMMMKMMM..
            ..MMMKMMMMKMMM..
            ..MMMMTKKTMMMM..
            ...MMTTTTTTMM...
            ...MMMTTTTMMM...
            .MMMMMMTTMMMMMM.
            .MMMMMTTTTMMMMM.
            ..MMMMTTTTMMMM..
            ....MMTTTTMM....
            ...MMMTTTTMMM...
            ...MMMMMMMMMM...
            """
        ),
        pic(
            "🌀", 2,
            """
            ......BBBB......
            ....BBBBBBBB....
            ....BBBB...BBB..
            ...BBB.......B..
            ...BB...........
            ..BBB...BBBB....
            ..BBB..CCBBBB...
            ..BBB.CCCCBBB...
            ...BBBCCCC.BBB..
            ...BBBBCC..BBB..
            ....BBBB...BBB..
            ...........BB...
            ..B.......BBB...
            ..BBB...BBBB....
            ....BBBBBBBB....
            ......BBBB......
            """
        ),
        pic(
            "🔱", 2,
            """
            .A.....AA.....A.
            .AA....AA....AA.
            .AA....AA....AA.
            .AA....AA....AA.
            .AA....AA....AA.
            .AA....AA....AA.
            ..AA...AA...AA..
            ...AAAAAAAAAA...
            .......AA.......
            .......AA.......
            .......AA.......
            .......AA.......
            .......AA.......
            .......AA.......
            .......AA.......
            .......AA.......
            """
        ),
        pic(
            "✈️", 2,
            """
            ................
            .......SS.......
            .......SS.......
            .......BB.......
            .......BB.......
            ......SSSS......
            ......SSSS......
            .....SSSSSS.....
            ....SSSSSSSS....
            ..SSSSSSSSSSSS..
            .SSSSSSSSSSSSSS.
            SSSSS..SS..SSSSS
            S......SS......S
            ......SSSS......
            ....SSSSSSSS....
            ....SS.SS.SS....
            """
        ),
        pic(
            "🚢", 2,
            """
            .......RR.......
            .......YY.......
            .......RR.......
            ....WWWWWW......
            ....WWWWWW......
            ....WWWWWW......
            ..WWWWWWWWWWW...
            ..WWBWBWBWBWB...
            ..WWWWWWWWWWW...
            RRRRRRRRRRRRRRRR
            .RRRRRRRRRRRRRR.
            .RRRRRRRRRRRRRR.
            ..RRRRRRRRRRRR..
            ...RRRRRRRRRR...
            BBBBBBBBBBBBBBBB
            ................
            """
        ),
        // Catégorie 4 : les plus détaillées (niveaux 25 à 31).
        pic(
            "🪬", 3,
            """
            .......BB.......
            .......BB.......
            .......BB.......
            ....BB.BB.BB....
            ....BB.BB.BB....
            .BB.BB.BB.BB.BB.
            .BB.BB.BB.BB.BB.
            .BB.BB.BB.BB.BB.
            .BBBBBBBBBBBBBB.
            .BBBWWWWWWWWBBB.
            .BBBWWWKKWWWBBB.
            .BBBBWWWWWWBBBB.
            .BBBBBBBBBBBBBB.
            ..BBBBBBBBBBBB..
            ...BBBBBBBBBB...
            ....BBBBBBBB....
            """
        ),
        pic(
            "🎭", 3,
            """
            ...YYYY.........
            ..YYYYYY........
            .YYYYYYYY.......
            .YY.YY..Y.......
            YY..YY..YY......
            YYYYYYYY..VV....
            YYYYYYY.VVVVVV..
            .Y.YYY.VVVVVVVV.
            .YY....VV.VV..V.
            ..YYYYVV..VV..VV
            ....YYVVVVVVVVVV
            ......VVVVVVVVVV
            .......VVVVVVVV.
            .......VV....VV.
            .........VVVV...
            .........VVVV...
            """
        ),
        pic(
            "🏍️", 3,
            """
            ................
            ................
            ................
            ................
            .........KKKK...
            ..........SSYY..
            .....RRRRRSSYY..
            .....RRRRRRS....
            ..KKKKRRRRRSKK..
            .KKKKKSSSSKSSKK.
            KKK..SSSSSKSSKKK
            KK.SSSSSSSK.S.KK
            KK..SSK..KK...KK
            .SSSKKK..KKKKKK.
            .KKKKK....KKKKK.
            ................
            """
        ),
        pic(
            "🚒", 3,
            """
            ................
            ................
            ............YY..
            SSSSSSSSSS..YY..
            .S.S.S.S.SRRRRRR
            SSSSSSSSSSRRCCCR
            ..........RRCCCR
            RRRRRRRRRRRRCCCR
            RRRRRRRRRRRRRRRR
            YYYYYYYYYYRRRRRR
            RRRRRRRRRRRRRRRR
            RRKKKKRRRRRKKKRR
            RKKSKKRRRRKKSKKR
            .KKSSK....KKSKK.
            ..KKKK....KKKKK.
            ...KK......KK...
            """
        ),
        pic(
            "🚂", 3,
            """
            ................
            ................
            .SSS......KKKKKK
            .KKK......KKKKKK
            .KKK.YY....RRRRR
            .KKK.YYY...RCCCR
            .KKKKYYYKKKRCCCR
            .KKKKKKKKKKRCCCR
            .KKKKKKKKKKRRRRR
            .KKKKKKKKKKRRRRR
            .KKKKKKKKKKRRRRR
            KKRRRRKRRRKRRRRK
            KRR.RRRR.RRRR.R.
            KRR.RRRR.RRRR.R.
            ..RRRR.RRR.RRRR.
            ................
            """
        ),
        pic(
            "🚁", 3,
            """
            ................
            .SSSSSSSSSSSSSS.
            .......S........
            .......S......RR
            ....RRRRRR....RR
            ..RCCCCRRRR...RR
            .RCCCCCRRRRRRRRR
            .RCCCCCRRRRRRR.R
            .RRCCCCRRRRR....
            ..RRRRRRRRRR....
            ...RRRRRRRR.....
            ....KRRR.K......
            ....K....K......
            ..KKKKKKKKKK....
            ................
            ................
            """
        ),
        pic(
            "🎡", 3,
            """
            ....PPPRRCCC....
            ...RPPP..CCCR...
            ..RSR......RSR..
            .YYRS......SRGG.
            .YY..S....S..GG.
            .RY...S..S...GR.
            .RSSSSSYYSSSSSR.
            .RR....YY....RR.
            .GG...SKKS...YY.
            .GG..SK..KS..YY.
            ..RRSK....KSRR..
            ...RRC....PRR...
            ....CCCRRPPP....
            ...KKCCRRPPKK...
            ..KK........KK..
            .KKKKKKKKKKKKKK.
            """
        ),
        )
    }
}

/**
 * Le niveau « heure » : l'heure du téléphone au lancement du niveau, écrite en briques (HH:MM, 24 h). Un chiffre fait
 * 3 colonnes sur 10 rangées (chaque pixel d'une police 3 × 5 vaut une colonne et deux rangées, pour des chiffres
 * hauts) ; il faut donc [MIN_COLS] colonnes, soit le niveau 8 (voir [Breakout.CLOCK_EVERY]).
 */
internal object BreakoutClock {

    const val MIN_COLS = 15
    const val HEIGHT = 10

    private val DIGIT_COLOR = 0xFF4DD0E1.toInt()
    private val COLON_COLOR = 0xFFFDD835.toInt()

    // Les chiffres 0 à 9 : cinq rangées de trois pixels, rangée par rangée.
    private val FONT = listOf(
        "111101101101111", // 0
        "010110010010111", // 1
        "111001111100111", // 2
        "111001111001111", // 3
        "101101111001001", // 4
        "111100111001111", // 5
        "111100111101111", // 6
        "111001001001001", // 7
        "111101111101111", // 8
        "111101111001111"  // 9
    )

    /** Largeur des deux points : 2 colonnes sur le plus grand terrain (le tout est alors centré), 1 sinon. */
    private fun colonWidth(cols: Int) = if (cols >= 16) 2 else 1

    private fun startColumn(cols: Int) = (cols - (14 + colonWidth(cols))) / 2

    /** Les briques de l'heure [minutes] (minutes depuis minuit) sur un terrain de [cols] × [rows] cases : 1 ou 0. */
    fun cells(cols: Int, rows: Int, minutes: Int): IntArray {
        val out = IntArray(cols * rows)
        if (cols < MIN_COLS || rows < HEIGHT) {
            out.fill(1)
            return out
        }
        val m = ((minutes % 1440) + 1440) % 1440
        val digits = intArrayOf(m / 600, (m / 60) % 10, (m % 60) / 10, m % 10)
        val cw = colonWidth(cols)
        val x0 = startColumn(cols)
        val y0 = (rows - HEIGHT) / 2
        val starts = intArrayOf(x0, x0 + 4, x0 + 7 + cw, x0 + 11 + cw)
        for (k in 0 until 4) {
            val glyph = FONT[digits[k]]
            for (gy in 0 until 5) {
                for (gx in 0 until 3) {
                    if (glyph[gy * 3 + gx] != '1') continue
                    for (dy in 0 until 2) out[(y0 + gy * 2 + dy) * cols + starts[k] + gx] = 1
                }
            }
        }
        for (dotRow in intArrayOf(1, 3)) {
            for (dy in 0 until 2) {
                for (dx in 0 until cw) out[(y0 + dotRow * 2 + dy) * cols + x0 + 7 + dx] = 1
            }
        }
        return out
    }

    /** Les couleurs (ARGB) des cases du niveau horloge : les deux points en jaune, les chiffres en bleu clair. */
    fun colors(cols: Int, rows: Int): IntArray {
        val cw = colonWidth(cols)
        val colonStart = startColumn(cols) + 7
        return IntArray(cols * rows) { i ->
            if (i % cols in colonStart until colonStart + cw) COLON_COLOR else DIGIT_COLOR
        }
    }
}
