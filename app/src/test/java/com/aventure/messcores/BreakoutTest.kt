package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreakoutTest {

    private val all = Breakout.COLS * Breakout.ROWS

    private fun newGame(seed: Int = 1) = BreakoutGame(Random(seed))

    /** Une partie dans l'état voulu (construite par [BreakoutGame.restore]) : toutes les briques sont là sauf si [bricks] dit autrement. */
    private fun state(
        ballX: Float,
        ballY: Float,
        vx: Float,
        vy: Float,
        paddleX: Float = 4f,
        lives: Int = Breakout.LIVES,
        level: Int = 1,
        attached: Boolean = false,
        bricks: List<Int> = List(Breakout.bricksFor(level)) { 1 }
    ): BreakoutGame {
        val data = listOf(
            0, lives, level, 0, if (attached) 1 else 0,
            paddleX.toBits(), ballX.toBits(), ballY.toBits(), vx.toBits(), vy.toBits()
        ) + bricks
        return BreakoutGame.restore(data, Random(1))!!
    }

    // --- Réglages ---

    @Test
    fun laBalleVaDeplusEnPlusViteEtPlafonne() {
        assertEquals(7f, Breakout.speedFor(1), 0.001f)
        assertTrue(Breakout.speedFor(2) > Breakout.speedFor(1))
        // Sur le plus grand terrain, la vitesse est multipliée par l'échelle du terrain : le plafond y est doublé.
        assertEquals(Breakout.MAX_UNITS_SPEED, Breakout.speedFor(100), 0.001f)
        assertEquals(Breakout.MAX_SPEED, Breakout.speedFor(100) / Breakout.scaleFor(100), 0.001f)
        assertEquals(Breakout.speedFor(1), Breakout.speedFor(0), 0.001f)
    }

    @Test
    fun lesBriquesDuHautValentPlusEtLeNiveauMultiplie() {
        assertEquals(70, Breakout.brickPoints(0, 1))
        assertEquals(10, Breakout.brickPoints(Breakout.ROWS - 1, 1))
        assertEquals(140, Breakout.brickPoints(0, 2))
        // Sur un terrain de 8 rangées, la brique du haut vaut une rangée de plus.
        assertEquals(80, Breakout.brickPoints(0, 1, rows = 8))
    }

    @Test
    fun leRenvoiDeLaRaquetteVaDuCentreAuBord() {
        assertEquals(0f, Breakout.bounceAngle(0f), 0.0001f)
        assertTrue(Breakout.bounceAngle(1f) > 0f)
        assertEquals(-Breakout.bounceAngle(1f), Breakout.bounceAngle(-1f), 0.0001f)
        assertEquals(Breakout.bounceAngle(1f), Breakout.bounceAngle(5f), 0.0001f)
    }

    // --- Début de partie ---

    @Test
    fun uneNouvellePartieALesBriquesLesViesEtLaBalleSurLaRaquette() {
        val game = newGame()
        assertEquals(all, game.bricksLeft)
        for (row in 0 until Breakout.ROWS) for (col in 0 until Breakout.COLS) assertTrue(game.hasBrick(col, row))
        assertFalse(game.hasBrick(-1, 0))
        assertFalse(game.hasBrick(Breakout.COLS, 0))
        assertFalse(game.hasBrick(0, Breakout.ROWS))
        assertEquals(Breakout.LIVES, game.lives)
        assertEquals(1, game.level)
        assertEquals(0, game.score)
        assertTrue(game.attached)
        assertFalse(game.isOver)
        assertEquals(game.paddleX, game.ballX, 0.0001f)
    }

    @Test
    fun laRaquetteResteDansLeTerrain() {
        val game = newGame()
        game.movePaddleTo(-5f)
        assertEquals(Breakout.PADDLE_W / 2f, game.paddleX, 0.0001f)
        game.movePaddleTo(50f)
        assertEquals(Breakout.WIDTH - Breakout.PADDLE_W / 2f, game.paddleX, 0.0001f)
    }

    @Test
    fun laBalleSuitLaRaquetteTantQuOnNeLanceRien() {
        val game = newGame()
        game.movePaddleTo(2.5f)
        assertEquals(2.5f, game.ballX, 0.0001f)
        game.tick(500)
        assertEquals(2.5f, game.ballX, 0.0001f)
        assertTrue(game.attached)
    }

    @Test
    fun lancerFaitMonterLaBalleALaVitesseDuNiveau() {
        val game = newGame()
        game.launch()
        assertFalse(game.attached)
        assertTrue(game.ballVy < 0f)
        assertEquals(Breakout.speedFor(1), hypot(game.ballVx, game.ballVy), 0.01f)
    }

    @Test
    fun onNePeutPasLancerDeuxFoisNiBougerLaBalleALaMain() {
        val game = newGame()
        game.launch()
        val vx = game.ballVx
        game.launch()
        assertEquals(vx, game.ballVx, 0f)
        val x = game.ballX
        game.movePaddleTo(1f)
        assertEquals(x, game.ballX, 0f)
    }

    // --- Rebonds ---

    @Test
    fun laBalleRebondiSurLesMursEtLePlafond() {
        val left = state(ballX = 0.3f, ballY = 8f, vx = -6f, vy = 2f)
        left.tick(100)
        assertTrue(left.ballVx > 0f)
        assertTrue(left.ballX >= Breakout.BALL_RADIUS)

        val right = state(ballX = Breakout.WIDTH - 0.3f, ballY = 8f, vx = 6f, vy = 2f)
        right.tick(100)
        assertTrue(right.ballVx < 0f)
        assertTrue(right.ballX <= Breakout.WIDTH - Breakout.BALL_RADIUS)

        val top = state(ballX = 4f, ballY = 0.3f, vx = 1f, vy = -6f)
        top.tick(100)
        assertTrue(top.ballVy > 0f)
        assertTrue(top.ballY >= Breakout.BALL_RADIUS)
    }

    @Test
    fun laRaquetteRenvoieLaBalleVersLeHaut() {
        val game = state(ballX = 4f, ballY = Breakout.PADDLE_TOP - 0.3f, vx = 0f, vy = 7f, paddleX = 4f)
        game.tick(100)
        assertTrue(game.ballVy < 0f)
        assertEquals(0f, game.ballVx, 0.01f)
        assertEquals(7f, hypot(game.ballVx, game.ballVy), 0.01f)
        assertEquals(Breakout.LIVES, game.lives)
    }

    @Test
    fun laRaquetteRenvoieDeCoteQuandOnTouchePresDuBord() {
        val right = state(ballX = 4.8f, ballY = Breakout.PADDLE_TOP - 0.3f, vx = 0f, vy = 7f, paddleX = 4f)
        right.tick(100)
        assertTrue(right.ballVx > 1f)
        val left = state(ballX = 3.2f, ballY = Breakout.PADDLE_TOP - 0.3f, vx = 0f, vy = 7f, paddleX = 4f)
        left.tick(100)
        assertTrue(left.ballVx < -1f)
    }

    @Test
    fun uneBriqueToucheeParDessousDisparaitEtRapporte() {
        // La brique du bas, colonne 3 : son dessous est à BRICKS_TOP + ROWS × BRICK_H.
        val bottom = Breakout.BRICKS_TOP + Breakout.ROWS * Breakout.BRICK_H
        val game = state(ballX = 3.5f, ballY = bottom + 0.4f, vx = 0f, vy = -7f)
        game.tick(100)
        assertFalse(game.hasBrick(3, Breakout.ROWS - 1))
        assertEquals(all - 1, game.bricksLeft)
        assertEquals(Breakout.brickPoints(Breakout.ROWS - 1, 1), game.score)
        assertTrue("la balle repart vers le bas", game.ballVy > 0f)
    }

    @Test
    fun uneBriqueToucheeParLeCoteFaitRepartirLaBalleDeCote() {
        // Deux briques (la colonne 3 et la colonne 7, rangée 0) pour que le niveau ne s'achève pas ; la balle arrive de la gauche, à la hauteur de la première.
        val bricks = List(all) { if (it == 3 || it == 7) 1 else 0 }
        val y = Breakout.BRICKS_TOP + Breakout.BRICK_H / 2f
        val game = state(ballX = 2.6f, ballY = y, vx = 7f, vy = 1.5f, bricks = bricks)
        game.tick(100)
        assertFalse(game.hasBrick(3, 0))
        assertTrue("la balle repart vers la gauche", game.ballVx < 0f)
    }

    @Test
    fun laBalleNeTraversePasLesBriquesMemeAvecUnGrosTemps() {
        // Un seul gros « tick » : la balle est avancée par petits pas, elle casse la brique au lieu de la traverser.
        val bottom = Breakout.BRICKS_TOP + Breakout.ROWS * Breakout.BRICK_H
        val game = state(ballX = 3.5f, ballY = bottom + 1.0f, vx = 0f, vy = -13f)
        game.tick(100)
        assertEquals(all - 1, game.bricksLeft)
        assertTrue(game.ballY > bottom - 0.01f)
    }

    // --- Vies et niveaux ---

    @Test
    fun uneBalleManqueeCouteUneVieEtRevientSurLaRaquette() {
        val game = state(ballX = 1f, ballY = Breakout.HEIGHT - 0.5f, vx = 0f, vy = 7f, paddleX = 6f)
        game.tick(400)
        assertEquals(Breakout.LIVES - 1, game.lives)
        assertTrue(game.attached)
        assertFalse(game.isOver)
        assertEquals(game.paddleX, game.ballX, 0.0001f)
    }

    @Test
    fun laDerniereVieFinitLaPartieEtLaFigeTout() {
        val game = state(ballX = 1f, ballY = Breakout.HEIGHT - 0.5f, vx = 0f, vy = 7f, paddleX = 6f, lives = 1)
        game.tick(400)
        assertTrue(game.isOver)
        assertEquals(0, game.lives)
        val y = game.ballY
        game.tick(500)
        game.launch()
        game.movePaddleTo(1f)
        assertEquals(y, game.ballY, 0f)
        assertEquals(6f, game.paddleX, 0f)
    }

    @Test
    fun laDerniereBriqueOuvreLeNiveauSuivantPlusRapide() {
        val bottom = Breakout.BRICKS_TOP + Breakout.ROWS * Breakout.BRICK_H
        // Il ne reste que la brique du bas, colonne 3.
        val bricks = List(all) { if (it == (Breakout.ROWS - 1) * Breakout.COLS + 3) 1 else 0 }
        val game = state(ballX = 3.5f, ballY = bottom + 0.4f, vx = 0f, vy = -7f, lives = 2, bricks = bricks)
        game.tick(100)
        assertEquals(2, game.level)
        // Le niveau 2 a sa disposition au hasard : au plus une grille pleine, et de quoi jouer.
        assertTrue(game.bricksLeft in 1..Breakout.bricksFor(2))
        assertEquals(game.bricksLeft, (0 until game.rows).sumOf { r -> (0 until game.cols).count { c -> game.hasBrick(c, r) } })
        assertEquals(9, game.cols)
        assertEquals(game.width / 2f, game.paddleX, 0.0001f)
        assertEquals(2, game.lives)
        assertTrue(game.attached)
        assertEquals(Breakout.brickPoints(Breakout.ROWS - 1, 1), game.score)
        game.launch()
        assertEquals(Breakout.speedFor(2), hypot(game.ballVx, game.ballVy), 0.01f)
    }

    @Test
    fun recommencerRemetToutAZero() {
        val game = state(ballX = 1f, ballY = Breakout.heightFor(3) - 0.5f, vx = 0f, vy = 7f, paddleX = 6f, lives = 1, level = 3)
        game.tick(400)
        assertTrue(game.isOver)
        game.reset()
        assertFalse(game.isOver)
        assertEquals(Breakout.LIVES, game.lives)
        assertEquals(1, game.level)
        assertEquals(0, game.score)
        assertEquals(all, game.bricksLeft)
        assertEquals(8, game.cols)
        assertTrue(game.attached)
    }

    // --- Des niveaux de plus en plus grands ---

    @Test
    fun leNiveauUnGardeLesDimensionsDeBase() {
        assertEquals(8, Breakout.colsFor(1))
        assertEquals(7, Breakout.rowsFor(1))
        assertEquals(all, Breakout.bricksFor(1))
        assertEquals(1f, Breakout.scaleFor(1), 0.0001f)
        assertEquals(Breakout.HEIGHT, Breakout.heightFor(1), 0.0001f)
        assertEquals(Breakout.PADDLE_TOP, Breakout.paddleTopFor(1), 0.0001f)
    }

    @Test
    fun chaqueNiveauAjouteUneColonneEtDesRangeesEnProportion() {
        assertEquals(9, Breakout.colsFor(2))
        assertEquals(8, Breakout.rowsFor(2))
        assertEquals(72, Breakout.bricksFor(2))
        // Le terrain garde ses proportions : la hauteur suit la largeur.
        for (level in 1..12) {
            assertEquals(
                Breakout.WIDTH / Breakout.HEIGHT,
                Breakout.widthFor(level) / Breakout.heightFor(level),
                0.0001f
            )
        }
    }

    @Test
    fun leNombreDeBriquesAugmenteAChaqueNiveauJusquAuPlafondDeColonnes() {
        val capLevel = Breakout.MAX_COLS - Breakout.COLS + 1
        for (level in 1 until capLevel) {
            assertTrue("niveau $level", Breakout.bricksFor(level + 1) > Breakout.bricksFor(level))
        }
        assertEquals(Breakout.MAX_COLS, Breakout.colsFor(capLevel))
        assertEquals(Breakout.MAX_COLS, Breakout.colsFor(capLevel + 20))
        assertEquals(Breakout.bricksFor(capLevel), Breakout.bricksFor(capLevel + 20))
        // La balle accélère quand même une fois le terrain à sa taille maximale.
        assertTrue(Breakout.speedFor(capLevel + 1) > Breakout.speedFor(capLevel))
    }

    @Test
    fun laBalleEtLaRaquetteGardentLeurTailleEnUnitesDoncRapetissentALEcran() {
        // Largeur de la raquette et rayon de la balle sont des constantes : seul le terrain grandit.
        val onScreen1 = Breakout.PADDLE_W / Breakout.widthFor(1)
        val onScreen2 = Breakout.PADDLE_W / Breakout.widthFor(2)
        assertTrue(onScreen2 < onScreen1)
        assertEquals(Breakout.PADDLE_W / Breakout.BRICK_W, 1.8f, 0.0001f)
    }

    @Test
    fun laVitesseALEcranResteCelleDuNiveauMemeSurUnTerrainPlusLarge() {
        // Au niveau 2 : la vitesse en unités par seconde est celle du niveau (7,7) fois l'échelle du terrain (9 / 8).
        assertEquals((7f + 0.7f) * 9f / 8f, Breakout.speedFor(2), 0.001f)
    }

    @Test
    fun laPartieDuNiveauDeuxASesDimensionsEtSeSauvegarde() {
        val game = state(ballX = 3f, ballY = 9f, vx = 2f, vy = -6f, paddleX = 8f, level = 2)
        assertEquals(9, game.cols)
        assertEquals(8, game.rows)
        assertEquals(72, game.bricksLeft)
        assertEquals(9f, game.width, 0f)
        // La raquette peut aller jusqu'au bord de ce terrain plus large.
        game.movePaddleTo(100f)
        assertEquals(9f - Breakout.PADDLE_W / 2f, game.paddleX, 0.0001f)
        val copy = BreakoutGame.restore(game.save())
        assertNotNull(copy)
        assertEquals(game.save(), copy!!.save())
        assertEquals(72, copy.bricksLeft)
        // Une sauvegarde du niveau 2 avec le nombre de briques du niveau 1 est abîmée.
        assertNull(BreakoutGame.restore(game.save().take(10 + all)))
    }

    @Test
    fun uneBriqueDeLaNeuviemeColonneExisteAuNiveauDeuxMaisPasAuNiveauUn() {
        assertTrue(state(4f, 9f, 0f, -6f, level = 2).hasBrick(8, 0))
        assertFalse(state(4f, 9f, 0f, -6f, level = 1).hasBrick(8, 0))
    }

    @Test
    fun laBalleCasseLesBriquesDuNiveauDeuxAvecLeurPropreGrille() {
        // Au niveau 2, la grille commence à 1,5 × 9/8 et la brique du bas (rangée 7) est plus bas que celle du niveau 1.
        val bottom = Breakout.bricksTopFor(2) + 8 * Breakout.BRICK_H
        val game = state(ballX = 8.5f, ballY = bottom + 0.4f, vx = 0f, vy = -7.9f, level = 2)
        game.tick(100)
        assertFalse(game.hasBrick(8, 7))
        assertEquals(Breakout.brickPoints(7, 2, 8), game.score)
    }

    // --- Des niveaux disposés au hasard ---

    private fun neighbours(cells: IntArray, cols: Int, rows: Int, col: Int, row: Int): Int {
        var n = 0
        if (col > 0 && cells[row * cols + col - 1] == 1) n++
        if (col < cols - 1 && cells[row * cols + col + 1] == 1) n++
        if (row > 0 && cells[(row - 1) * cols + col] == 1) n++
        if (row < rows - 1 && cells[(row + 1) * cols + col] == 1) n++
        return n
    }

    @Test
    fun leNiveauUnEstToujoursLaGrillePleine() {
        repeat(20) { seed ->
            assertTrue(Breakout.layoutFor(1, Random(seed)).all { it == 1 })
        }
        assertEquals(all, newGame(5).bricksLeft)
    }

    @Test
    fun auNiveauDeuxEtAuDelaLesBriquesSontDisposeesAuHasard() {
        for (level in 2..17) {
            val layouts = (0 until 10).map { Breakout.layoutFor(level, Random(it)).toList() }.toSet()
            assertTrue("niveau $level : les dispositions diffèrent", layouts.size > 1)
            for (layout in layouts) assertEquals(Breakout.bricksFor(level), layout.size)
        }
    }

    @Test
    fun aucuneBriqueNEstSeuleDansLeVide() {
        for (level in 2..17) for (seed in 0 until 50) {
            val cols = Breakout.colsFor(level)
            val rows = Breakout.rowsFor(level)
            val cells = Breakout.layoutFor(level, Random(seed))
            for (row in 0 until rows) for (col in 0 until cols) {
                if (cells[row * cols + col] == 1) {
                    assertTrue("niveau $level, graine $seed : brique seule en ($col, $row)", neighbours(cells, cols, rows, col, row) >= 1)
                }
            }
        }
    }

    @Test
    fun retirerLesBriquesSeulesLaisseLesPaires() {
        // Une brique seule (0, 0), une paire (2, 0)-(3, 0), une brique seule en diagonale (1, 1)... et (0, 2) seule.
        val cells = intArrayOf(
            1, 0, 1, 1,
            0, 1, 0, 0,
            1, 0, 0, 0
        )
        Breakout.removeLoneBricks(cells, 4, 3)
        assertEquals(listOf(0, 0, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0), cells.toList())
    }

    @Test
    fun lesBriquesNeDescendentJamaisTropBasParRapportALaRaquette() {
        for (level in 1..17) for (seed in 0 until 20) {
            val cols = Breakout.colsFor(level)
            val rows = Breakout.rowsFor(level)
            val cells = Breakout.layoutFor(level, Random(seed))
            val lowestRow = (0 until rows).last { row -> (0 until cols).any { cells[row * cols + it] == 1 } }
            val bottom = Breakout.bricksTopFor(level) + (lowestRow + 1) * Breakout.BRICK_H
            // Il reste toujours plus de la moitié de la hauteur du terrain sous les briques, pour avoir le temps de renvoyer la balle.
            assertTrue("niveau $level", Breakout.paddleTopFor(level) - bottom > Breakout.heightFor(level) / 2f)
        }
    }

    @Test
    fun uneDispositionNeSeVideJamaisAuPointDEtreTropMaigre() {
        for (level in 2..17) for (seed in 0 until 50) {
            val cells = Breakout.layoutFor(level, Random(seed))
            assertTrue(cells.count { it == 1 } >= cells.size * 0.45)
        }
    }

    @Test
    fun lePassageAuNiveauSuivantDisposeLesBriquesAuHasardEtLesSauvegarde() {
        val bottom = Breakout.BRICKS_TOP + Breakout.ROWS * Breakout.BRICK_H
        val bricks = List(all) { if (it == (Breakout.ROWS - 1) * Breakout.COLS + 3) 1 else 0 }
        val game = state(ballX = 3.5f, ballY = bottom + 0.4f, vx = 0f, vy = -7f, bricks = bricks)
        game.tick(100)
        assertEquals(2, game.level)
        val copy = BreakoutGame.restore(game.save())
        assertNotNull(copy)
        assertEquals(game.save(), copy!!.save())
        assertEquals(game.bricksLeft, copy.bricksLeft)
    }

    // --- Une vraie partie ---

    @Test
    fun uneSimulationAvecUnJoueurQuiSuitLaBalleGardeToujoursLaBalleDansLeTerrain() {
        val game = newGame(7)
        game.launch()
        var frames = 0
        while (frames < 60 * 120 && !game.isOver) {
            game.movePaddleTo(game.ballX)
            if (game.attached) game.launch()
            game.tick(16)
            assertTrue(game.ballX in 0f..game.width)
            assertTrue(game.ballY in 0f..game.height + 1f)
            assertTrue(game.ballX.isFinite() && game.ballY.isFinite())
            assertTrue(abs(hypot(game.ballVx, game.ballVy)) <= Breakout.MAX_UNITS_SPEED + 0.01f)
            frames++
        }
        assertTrue("des briques ont été cassées", game.score > 0)
        var counted = 0
        for (row in 0 until game.rows) for (col in 0 until game.cols) if (game.hasBrick(col, row)) counted++
        assertEquals(counted, game.bricksLeft)
    }

    // --- Sauvegarde ---

    @Test
    fun laPartieSeRestaureTelleQuelle() {
        val game = newGame(3)
        game.launch()
        repeat(40) { game.movePaddleTo(game.ballX); game.tick(16) }
        val data = game.save()
        val copy = BreakoutGame.restore(data)
        assertNotNull(copy)
        assertEquals(data, copy!!.save())
        assertEquals(game.score, copy.score)
        assertEquals(game.bricksLeft, copy.bricksLeft)
        assertEquals(game.ballX, copy.ballX, 0f)
        assertEquals(game.attached, copy.attached)
    }

    @Test
    fun uneSauvegardeAbimeeEstRefusee() {
        val good = newGame().save()
        assertNotNull(BreakoutGame.restore(good))
        assertNull(BreakoutGame.restore(emptyList()))
        assertNull(BreakoutGame.restore(good.dropLast(1)))
        assertNull(BreakoutGame.restore(good.toMutableList().also { it[1] = 9 }))
        assertNull(BreakoutGame.restore(good.toMutableList().also { it[2] = 0 }))
        assertNull(BreakoutGame.restore(good.toMutableList().also { it[6] = Float.NaN.toBits() }))
        assertNull(BreakoutGame.restore(good.toMutableList().also { it[5] = (-3f).toBits() }))
        assertNull(BreakoutGame.restore(good.toMutableList().also { it[12] = 7 }))
        // Plus aucune brique : une partie pareille aurait déjà changé de niveau.
        assertNull(BreakoutGame.restore(good.mapIndexed { index, value -> if (index >= 10) 0 else value }))
        // Fini mais avec des vies, ou en cours sans vie : incohérent.
        assertNull(BreakoutGame.restore(good.toMutableList().also { it[3] = 1 }))
        assertNull(BreakoutGame.restore(good.toMutableList().also { it[1] = 0 }))
    }
}
