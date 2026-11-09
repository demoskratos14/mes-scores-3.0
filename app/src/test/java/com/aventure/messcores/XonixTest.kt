package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XonixTest {

    private fun newGame(seed: Int = 1) = XonixGame(Random(seed))

    /** Fige les billes (deux, du côté droit du plateau) : les parcours des tests ne dépendent plus du hasard. */
    private fun stillBalls(game: XonixGame, x: Float = 20f) {
        game.balls.forEachIndexed { index, ball ->
            ball.x = x + index * 0.5f
            ball.y = 20f + index * 0.5f
            ball.vx = 0f
            ball.vy = 0f
        }
    }

    /** Avance de [steps] pas du joueur (un pas par [Xonix.MOVE_MS]). */
    private fun walk(game: XonixGame, direction: Int, steps: Int) {
        game.setDirection(direction)
        repeat(steps) { game.tick(Xonix.MOVE_MS) }
    }

    /** Un parcours qui recoupe son propre tracé : le joueur perd une vie. */
    private fun crossOwnTrail(game: XonixGame) {
        walk(game, Xonix.DOWN, 3)
        walk(game, Xonix.RIGHT, 2)
        walk(game, Xonix.UP, 2)
        walk(game, Xonix.LEFT, 3)
    }

    /** Une partie identique à [game] mais avec une mine en plus (la partie est reconstruite par [XonixGame.restore]). */
    private fun withSoldier(game: XonixGame, x: Int, y: Int, dx: Int, dy: Int): XonixGame {
        val base = game.save()
        val ballCount = base[10]
        val header = base.subList(0, 14).toMutableList().also {
            it[1] = 3
            it[11] = 1
        }
        val balls = base.subList(14, 14 + ballCount * 4)
        val board = base.takeLast(Xonix.COLS * Xonix.ROWS)
        return XonixGame.restore(header + balls + listOf(x, y, dx, dy) + board)!!
    }

    @Test
    fun laPartieCommenceSurLaBordureAvecDeuxBillesEtTroisVies() {
        val game = newGame()
        assertEquals(1, game.level)
        assertEquals(Xonix.LIVES, game.lives)
        assertEquals(0, game.score)
        assertEquals(Xonix.START_COL, game.px)
        assertEquals(0, game.py)
        assertEquals(Xonix.NONE, game.dir)
        assertEquals(2, game.balls.size)
        assertEquals(0, game.soldiers.size)
        assertEquals(0, game.percent)
        assertEquals(Xonix.LAND, game.cell(0, 0))
        assertEquals(Xonix.LAND, game.cell(Xonix.COLS - 1, Xonix.ROWS - 1))
        assertEquals(Xonix.SEA, game.cell(1, 1))
        assertTrue(game.isOnLand)
    }

    @Test
    fun lesBillesCommencentDansLaMerEtYRestent() {
        val game = newGame(3)
        repeat(2000) { game.tick(16) }
        game.balls.forEach { ball ->
            assertEquals(Xonix.SEA, game.cell(ball.x.toInt(), ball.y.toInt()))
        }
    }

    @Test
    fun unTraceQuiTraverseLePlateauConquiertLeCoteSansBille() {
        val game = newGame()
        stillBalls(game)
        walk(game, Xonix.DOWN, 33)
        // Le tracé (32 cases) et toute la mer à gauche de lui (11 colonnes de 32 cases) deviennent de la terre.
        assertEquals(32 + 11 * 32, game.claimed)
        assertEquals(54, game.percent)
        assertEquals(Xonix.fillPoints(game.claimed, 1), game.score)
        assertEquals(Xonix.LAND, game.cell(5, 10))
        assertEquals(Xonix.LAND, game.cell(Xonix.START_COL, 10))
        assertEquals(Xonix.SEA, game.cell(18, 10))
        assertEquals(Xonix.NONE, game.dir)
        assertFalse(game.isLevelClearing)
        assertEquals(Xonix.LIVES, game.lives)
    }

    @Test
    fun surSonTraceLeJoueurNePeutPasFaireDemiTour() {
        val game = newGame()
        stillBalls(game)
        walk(game, Xonix.DOWN, 2)
        game.setDirection(Xonix.UP)
        assertEquals(Xonix.DOWN, game.dir)
        game.setDirection(Xonix.LEFT)
        assertEquals(Xonix.LEFT, game.dir)
    }

    @Test
    fun surLaTerreOnPeutSArreterMaisPasEnMer() {
        val game = newGame()
        stillBalls(game)
        game.setDirection(Xonix.LEFT)
        game.stop()
        assertEquals(Xonix.NONE, game.dir)
        walk(game, Xonix.DOWN, 2)
        game.stop()
        assertEquals(Xonix.DOWN, game.dir)
    }

    @Test
    fun recouperSonPropreTraceFaitPerdreUneVieEtLeJoueurRevientAuDepart() {
        val game = newGame()
        stillBalls(game)
        crossOwnTrail(game)
        assertTrue(game.isHurt)
        assertEquals(Xonix.LIVES - 1, game.lives)
        game.tick(Xonix.HURT_MS)
        assertFalse(game.isHurt)
        assertEquals(Xonix.START_COL, game.px)
        assertEquals(0, game.py)
        assertEquals(Xonix.NONE, game.dir)
        // Le tracé s'est effacé.
        assertEquals(Xonix.SEA, game.cell(Xonix.START_COL, 1))
        assertEquals(0, game.claimed)
    }

    @Test
    fun uneBilleQuiToucheLeTraceFaitPerdreUneVie() {
        val game = newGame()
        stillBalls(game)
        walk(game, Xonix.DOWN, 5)
        game.balls[0].x = 13.5f
        game.balls[0].y = 3.5f
        game.balls[0].vx = -10f
        game.tick(100)
        assertTrue(game.isHurt)
        assertEquals(Xonix.LIVES - 1, game.lives)
    }

    @Test
    fun entrerDansLaCaseOuSeTrouveUneBilleFaitPerdreUneVie() {
        val game = newGame()
        stillBalls(game)
        game.balls[0].x = Xonix.START_COL + 0.5f
        game.balls[0].y = 1.5f
        game.setDirection(Xonix.DOWN)
        game.tick(Xonix.MOVE_MS)
        assertTrue(game.isHurt)
        assertEquals(Xonix.LIVES - 1, game.lives)
    }

    @Test
    fun laPartieSArreteALaDerniereVie() {
        val game = newGame()
        stillBalls(game)
        repeat(Xonix.LIVES) {
            crossOwnTrail(game)
            assertTrue(game.isHurt)
            game.tick(Xonix.HURT_MS)
        }
        assertEquals(0, game.lives)
        assertTrue(game.isOver)
        // Une fois finie, la partie ne bouge plus.
        val score = game.score
        val row = game.py
        game.setDirection(Xonix.DOWN)
        game.tick(Xonix.MOVE_MS)
        assertEquals(score, game.score)
        assertEquals(row, game.py)
    }

    @Test
    fun conquerirAssezDeMerFaitPasserAuNiveauSuivant() {
        val game = newGame()
        stillBalls(game, x = 2f)
        // Premier tracé en bas : toute la mer à droite devient de la terre (50 %).
        walk(game, Xonix.DOWN, 33)
        assertEquals(50, game.percent)
        // Puis le long de la bordure du bas, et un second tracé vers le haut : encore 192 cases (77 % en tout).
        walk(game, Xonix.LEFT, 6)
        walk(game, Xonix.UP, 33)
        assertEquals(77, game.percent)
        assertTrue(game.isLevelClearing)
        val scoreBefore = game.score
        assertTrue(scoreBefore >= Xonix.levelBonus(1, Xonix.LIVES))
        game.tick(Xonix.LEVEL_CLEAR_MS)
        assertFalse(game.isLevelClearing)
        assertEquals(2, game.level)
        assertEquals(0, game.claimed)
        assertEquals(Xonix.ballsFor(2), game.balls.size)
        assertEquals(Xonix.LIVES, game.lives)
        assertEquals(scoreBefore, game.score)
        assertEquals(Xonix.START_COL, game.px)
    }

    @Test
    fun uneMineSurLaTerreFaitPerdreUneVieSiElleToucheLeJoueur() {
        val game = newGame()
        stillBalls(game)
        val withSoldier = withSoldier(game, Xonix.START_COL - 1, 0, 1, 1)
        withSoldier.tick(Xonix.SOLDIER_MS)
        assertTrue(withSoldier.isHurt)
        assertEquals(Xonix.LIVES - 1, withSoldier.lives)
    }

    @Test
    fun uneMineResteSurLaTerreFerme() {
        val game = newGame()
        stillBalls(game)
        val withSoldier = withSoldier(game, 5, Xonix.ROWS - 1, 1, -1)
        var steps = 0
        while (!withSoldier.isHurt && steps < 400) {
            withSoldier.tick(Xonix.SOLDIER_MS)
            val soldier = withSoldier.soldiers.single()
            assertEquals(Xonix.LAND, withSoldier.cell(soldier.x, soldier.y))
            steps++
        }
        assertTrue(steps > 30)
    }

    @Test
    fun leNombreDeBillesEtDeMinesGrandiAvecLeNiveau() {
        assertEquals(2, Xonix.ballsFor(1))
        assertEquals(3, Xonix.ballsFor(2))
        assertEquals(Xonix.MAX_BALLS, Xonix.ballsFor(50))
        assertEquals(0, Xonix.soldiersFor(1))
        assertEquals(0, Xonix.soldiersFor(2))
        assertEquals(1, Xonix.soldiersFor(3))
        assertEquals(1, Xonix.soldiersFor(5))
        assertEquals(2, Xonix.soldiersFor(6))
        assertEquals(Xonix.MAX_SOLDIERS, Xonix.soldiersFor(99))
        assertTrue(Xonix.ballSpeed(5) > Xonix.ballSpeed(1))
        assertTrue(Xonix.ballSpeed(99) <= 14f)
    }

    @Test
    fun lesPointsDependentDuNombreDeCasesEtDuNiveau() {
        assertEquals(50, Xonix.fillPoints(5, 1))
        assertEquals(200, Xonix.fillPoints(10, 1))
        assertEquals(30 * 10 * 2 * 3, Xonix.fillPoints(30, 2))
        assertEquals(60 * 10 * 5, Xonix.fillPoints(60, 1))
        assertEquals(0, Xonix.fillPoints(0, 3))
        assertEquals(500 * 2 + 200 * 3, Xonix.levelBonus(2, 3))
    }

    @Test
    fun lePourcentageSeCalculeSurLaMerHorsBordure() {
        assertEquals(0, Xonix.percentOf(0))
        assertEquals(100, Xonix.percentOf(Xonix.INTERIOR))
        assertEquals(100, Xonix.percentOf(Xonix.INTERIOR + 50))
        assertEquals(50, Xonix.percentOf(Xonix.INTERIOR / 2))
    }

    @Test
    fun saveEtRestoreRendentLaMemePartie() {
        val game = newGame(5)
        stillBalls(game)
        walk(game, Xonix.DOWN, 4)
        walk(game, Xonix.RIGHT, 3)
        val data = game.save()
        val restored = XonixGame.restore(data)
        assertNotNull(restored)
        assertEquals(data, restored!!.save())
        assertEquals(game.px, restored.px)
        assertEquals(game.py, restored.py)
        assertEquals(game.dir, restored.dir)
        assertEquals(game.balls.size, restored.balls.size)
        assertEquals(Xonix.TRAIL, restored.cell(game.px, game.py))
    }

    @Test
    fun restoreRefuseDesDonneesAbimees() {
        val data = newGame().save()
        assertNull(XonixGame.restore(emptyList()))
        assertNull(XonixGame.restore(listOf(1, 2, 3)))
        assertNull(XonixGame.restore(data.dropLast(1)))
        // Une version inconnue.
        assertNull(XonixGame.restore(data.toMutableList().also { it[0] = 99 }))
        // Un coin de la bordure qui serait de la mer.
        val firstCell = data.size - Xonix.COLS * Xonix.ROWS
        assertNull(XonixGame.restore(data.toMutableList().also { it[firstCell] = Xonix.SEA }))
        // Un joueur en pleine mer.
        assertNull(
            XonixGame.restore(
                data.toMutableList().also {
                    it[4] = 5
                    it[5] = 5
                }
            )
        )
        // Une bille dans la terre.
        assertNull(
            XonixGame.restore(
                data.toMutableList().also {
                    it[14] = 0
                    it[15] = 0
                }
            )
        )
    }
}
