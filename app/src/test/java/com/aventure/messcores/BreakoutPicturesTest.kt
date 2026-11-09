package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Les niveaux en dessins : les 28 émoticônes, leur ordre, leur adaptation au terrain, et le niveau « heure ». */
class BreakoutPicturesTest {

    /** Les niveaux à dessin d'un premier tour, par catégorie : les niveaux 8, 16, 24 et 32 sont des niveaux horloge. */
    private val lap = listOf(1..7, 9..15, 17..23, 25..31)

    private fun picture(emoji: String) = BreakoutPictures.all.flatten().first { it.emoji.startsWith(emoji) }

    private fun shape(colors: IntArray) = colors.map { it != 0 }

    // --- Le catalogue ---

    @Test
    fun ilYAVingtHuitDessinsEnQuatreCategoriesDeSept() {
        assertEquals(4, BreakoutPictures.all.size)
        for ((tier, pictures) in BreakoutPictures.all.withIndex()) {
            assertEquals(7, pictures.size)
            assertTrue(pictures.all { it.tier == tier })
        }
        assertEquals(28, BreakoutPictures.all.flatten().map { it.emoji }.toSet().size)
    }

    @Test
    fun chaqueDessinAUnContourEtDesCouleurs() {
        for (picture in BreakoutPictures.all.flatten()) {
            assertTrue(picture.emoji, picture.width in 6..BreakoutPictures.SIZE)
            assertTrue(picture.emoji, picture.height in 6..BreakoutPictures.SIZE)
            val used = (0 until picture.height).flatMap { r -> (0 until picture.width).map { c -> picture.pixel(c, r) } }
            assertTrue(picture.emoji, used.any { it != 0 })
            // Le dessin est rogné : chaque bord touche au moins un pixel.
            assertTrue((0 until picture.width).any { picture.pixel(it, 0) != 0 })
            assertTrue((0 until picture.width).any { picture.pixel(it, picture.height - 1) != 0 })
            assertTrue((0 until picture.height).any { picture.pixel(0, it) != 0 })
            assertTrue((0 until picture.height).any { picture.pixel(picture.width - 1, it) != 0 })
        }
    }

    // --- L'ordre des niveaux ---

    @Test
    fun lesNiveauxVontDesDessinsLesMoinsDetaillesAuxPlusDetailles() {
        for (seed in 0 until 10) {
            for ((tier, levels) in lap.withIndex()) {
                for (level in levels) assertEquals("niveau $level", tier, BreakoutPictures.forLevel(level, seed)!!.tier)
            }
        }
    }

    @Test
    fun chaqueDessinNeServQuUneFoisParTour() {
        for (seed in 0 until 10) {
            val used = lap.flatMap { it.toList() }.map { BreakoutPictures.forLevel(it, seed)!!.emoji }
            assertEquals(28, used.size)
            assertEquals(28, used.toSet().size)
        }
    }

    @Test
    fun lOrdreDansUneCategorieDependDeLaGraine() {
        val orders = (0 until 20).map { seed -> (1..7).map { BreakoutPictures.forLevel(it, seed)!!.emoji } }.toSet()
        assertTrue(orders.size > 1)
        // La même graine redonne toujours le même ordre.
        assertEquals(
            (1..40).map { BreakoutPictures.forLevel(it, 12)?.emoji },
            (1..40).map { BreakoutPictures.forLevel(it, 12)?.emoji }
        )
    }

    @Test
    fun laListeRecommenceQuandTousLesDessinsOntServi() {
        // Le niveau 32 est un niveau horloge ; le 33e reprend la première catégorie, avec un terrain plus grand qu'au niveau 1.
        for (seed in 0 until 10) {
            assertEquals(0, BreakoutPictures.forLevel(33, seed)!!.tier)
            assertEquals(3, BreakoutPictures.forLevel(31, seed)!!.tier)
            for (level in 33..39) assertEquals(0, BreakoutPictures.forLevel(level, seed)!!.tier)
            val secondLap = (33..39).map { BreakoutPictures.forLevel(it, seed)!!.emoji }
            assertEquals(7, secondLap.toSet().size)
        }
        assertTrue(Breakout.bricksFor(33) > Breakout.bricksFor(1))
        assertEquals(28, BreakoutPictures.indexOf(33))
    }

    @Test
    fun unNiveauSurHuitEstUnNiveauHorloge() {
        for (level in 1..80) {
            assertEquals("niveau $level", level % 8 == 0, Breakout.isClockLevel(level))
            assertEquals("niveau $level", level % 8 == 0, BreakoutPictures.forLevel(level, 3) == null)
        }
        assertFalse(Breakout.isClockLevel(0))
    }

    // --- L'adaptation au terrain ---

    @Test
    fun unDessinResteDansLeTerrainEtSeVoit() {
        for (level in 1..40) {
            val cols = Breakout.colsFor(level)
            val rows = Breakout.rowsFor(level)
            for (picture in BreakoutPictures.all.flatten()) {
                val colors = BreakoutPictures.fit(picture, cols, rows)
                assertEquals(cols * rows, colors.size)
                assertTrue("${picture.emoji} niveau $level", colors.count { it != 0 } >= 6)
            }
        }
    }

    @Test
    fun unDessinSymetriqueResteSymetriqueEtCentre() {
        for (emoji in listOf("❤", "⭐", "🙂", "🍄", "🎄")) {
            for (level in 1..40) {
                val cols = Breakout.colsFor(level)
                val rows = Breakout.rowsFor(level)
                val shape = shape(BreakoutPictures.fit(picture(emoji), cols, rows))
                for (r in 0 until rows) for (c in 0 until cols) {
                    assertEquals("$emoji niveau $level", shape[r * cols + c], shape[r * cols + cols - 1 - c])
                }
            }
        }
    }

    @Test
    fun leDessinGrandiAvecLeTerrain() {
        val heart = picture("❤")
        val small = BreakoutPictures.fit(heart, Breakout.colsFor(1), Breakout.rowsFor(1)).count { it != 0 }
        val large = BreakoutPictures.fit(heart, Breakout.colsFor(9), Breakout.rowsFor(9)).count { it != 0 }
        assertTrue(large > small * 2)
    }

    @Test
    fun leDrapeauAsesTroisBandesBleueBlancheRouge() {
        val flag = picture("🇨🇵")
        val cols = Breakout.colsFor(9)
        val rows = Breakout.rowsFor(9)
        val colors = BreakoutPictures.fit(flag, cols, rows)
        val row = (0 until cols).map { colors[cols + it] }.filter { it != 0 }
        assertTrue(row.size >= 10)
        assertEquals(0xFF1E88E5.toInt(), row.first())
        assertEquals(0xFFE53935.toInt(), row.last())
        assertTrue(0xFFF5F5F5.toInt() in row)
    }

    @Test
    fun chaqueBriqueDuNiveauACouleurDeDessin() {
        for (level in 1..40) for (seed in 0 until 8) {
            val cells = Breakout.layoutFor(level, Random(seed), seed = seed, minutes = seed * 90)
            val colors = Breakout.colorsFor(level, seed)
            assertEquals(cells.size, colors.size)
            for (i in cells.indices) if (cells[i] != 0) assertTrue("niveau $level, case $i", colors[i] != 0)
        }
    }

    // --- Le niveau horloge ---

    private fun rowsOf(game: BreakoutGame) =
        (0 until game.rows).map { r -> (0 until game.cols).joinToString("") { c -> if (game.hasBrick(c, r)) "#" else "." } }

    private fun rowsOf(cells: IntArray, cols: Int, rows: Int) =
        (0 until rows).map { r -> (0 until cols).joinToString("") { c -> if (cells[r * cols + c] != 0) "#" else "." } }

    private val clock1234On15 = listOf(
        "...............",
        ".#..###.###.#.#",
        ".#..###.###.#.#",
        "##....##..#.#.#",
        "##....##..#.#.#",
        ".#..###.###.###",
        ".#..###.###.###",
        ".#..#..#..#...#",
        ".#..#..#..#...#",
        "###.###.###...#",
        "###.###.###...#",
        "...............",
        "..............."
    )

    private val clock1234On16 = listOf(
        "................",
        "................",
        ".#..###..###.#.#",
        ".#..###..###.#.#",
        "##....###..#.#.#",
        "##....###..#.#.#",
        ".#..###..###.###",
        ".#..###..###.###",
        ".#..#..##..#...#",
        ".#..#..##..#...#",
        "###.###..###...#",
        "###.###..###...#",
        "................",
        "................"
    )

    @Test
    fun leNiveauHorlogeEcritLHeureEnBriques() {
        val at15 = Breakout.layoutFor(8, Random(1), minutes = 12 * 60 + 34)
        assertEquals(clock1234On15, rowsOf(at15, 15, 13))
        val at16 = Breakout.layoutFor(16, Random(1), minutes = 12 * 60 + 34)
        assertEquals(clock1234On16, rowsOf(at16, 16, 14))
    }

    @Test
    fun leNiveauHorlogeSaitEcrireToutesLesHeures() {
        for (minutes in 0 until 1440 step 7) {
            val cells = Breakout.layoutFor(24, Random(1), minutes = minutes)
            assertTrue(cells.count { it != 0 } >= 15)
        }
        // Minuit et midi pile, et une heure hors d'un jour est ramenée dans le jour.
        assertEquals(
            rowsOf(Breakout.layoutFor(16, Random(1), minutes = 0), 16, 14),
            rowsOf(Breakout.layoutFor(16, Random(1), minutes = 1440), 16, 14)
        )
    }

    @Test
    fun leNiveauHorlogeSeLanceAvecLHeureDuMomentEtLaGardeEnSauvegarde() {
        // Une partie au niveau 7 avec une seule brique, en bas : la casser ouvre le niveau 8, le premier niveau horloge.
        val level = 7
        val cols = Breakout.colsFor(level)
        val rows = Breakout.rowsFor(level)
        val bottom = Breakout.bricksTopFor(level) + rows * Breakout.BRICK_H
        val bricks = List(cols * rows) { if (it == (rows - 1) * cols + 3) 1 else 0 }
        val data = listOf(
            0, 3, level, 0, 0,
            4f.toBits(), 3.5f.toBits(), (bottom + 0.4f).toBits(), 0f.toBits(), (-7f).toBits()
        ) + bricks
        var now = 12 * 60 + 34
        val game = BreakoutGame.restore(data, Random(1), clock = { now })!!
        game.tick(100)
        assertEquals(8, game.level)
        assertEquals(clock1234On15, rowsOf(game))
        // L'heure est figée au lancement du niveau : la sauvegarde garde les briques, pas l'horloge.
        now = 23 * 60 + 59
        val copy = BreakoutGame.restore(game.save(), Random(1), clock = { now })!!
        assertEquals(clock1234On15, rowsOf(copy))
        assertEquals(game.save(), copy.save())
        for (r in 0 until game.rows) for (c in 0 until game.cols) assertEquals(game.brickColor(c, r), copy.brickColor(c, r))
    }

    // --- Couleurs et sauvegarde ---

    @Test
    fun laPartieGardeLaGraineDeSesDessinsEtSesCouleurs() {
        val game = BreakoutGame(Random(1), pictureSeed = 5)
        assertEquals(5, game.save().last())
        val copy = BreakoutGame.restore(game.save(), Random(2))!!
        assertEquals(game.save(), copy.save())
        var colored = 0
        for (r in 0 until game.rows) for (c in 0 until game.cols) {
            assertEquals(game.brickColor(c, r), copy.brickColor(c, r))
            if (game.hasBrick(c, r)) {
                assertTrue(game.brickColor(c, r) != 0)
                colored++
            }
        }
        assertEquals(game.bricksLeft, colored)
        assertEquals(0, game.brickColor(-1, 0))
        assertEquals(0, game.brickColor(0, game.rows))
    }

    @Test
    fun uneAncienneSauvegardeSansGraineGardeLesCouleursDesRangees() {
        val data = listOf(0, 3, 1, 0, 0, 4f.toBits(), 4f.toBits(), 8f.toBits(), 0f.toBits(), (-5f).toBits()) +
            List(Breakout.bricksFor(1)) { 1 }
        val game = BreakoutGame.restore(data)
        assertNotNull(game)
        for (r in 0 until game!!.rows) for (c in 0 until game.cols) assertEquals(0, game.brickColor(c, r))
        // La partie reprise a désormais une graine : elle se sauvegarde et se restaure.
        val again = BreakoutGame.restore(game.save())
        assertNotNull(again)
        assertEquals(game.save(), again!!.save())
    }

    @Test
    fun recommencerChoisitUnNouvelOrdreDeDessins() {
        val game = BreakoutGame(Random(1), pictureSeed = 5)
        game.reset()
        assertTrue(game.save().last() != 5)
        assertNull(BreakoutGame.restore(game.save() + listOf(1)))
    }
}
