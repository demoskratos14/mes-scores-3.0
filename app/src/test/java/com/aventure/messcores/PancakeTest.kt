package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PancakeTest {

    private fun newGame(seed: Int = 1) = PancakeGame(Random(seed))

    private val plates = listOf(
        Pancake.RIGHT_PLATE to PANCAKE_RIGHT,
        Pancake.CENTER_PLATE to PANCAKE_CENTER,
        Pancake.LEFT_PLATE to PANCAKE_LEFT
    )

    /** Un joueur sans faute : il se place dans la voie de la prochaine assiette de la crêpe la plus pressée. */
    private fun PancakeGame.playPerfectly(durationMs: Long, stepMs: Long = 20) {
        var t = 0L
        while (t < durationMs && !isOver) {
            val lane = flights.filter { !it.splat }
                .flatMap { f -> plates.filter { (u, _) -> f.age < u + 1.2 }.map { (u, lane) -> (u - f.age) to lane } }
                .minByOrNull { it.first }?.second
            if (lane != null) {
                if (waiter < lane) moveRight() else if (waiter > lane) moveLeft()
            }
            tick(stepMs)
            t += stepMs
        }
    }

    // --- Le départ ---

    @Test
    fun leServeurPartAuCentreSansCrepeNiScore() {
        val game = newGame()
        assertEquals(PANCAKE_CENTER, game.waiter)
        assertEquals(0, game.score)
        assertEquals(0, game.misses)
        assertFalse(game.isOver)
        assertTrue(game.flights.isEmpty())
    }

    @Test
    fun laPremiereCrepePartApresDeuxCyclesDuCuisinier() {
        val game = newGame()
        game.tick(2500)
        assertTrue(game.flights.isEmpty())
        game.tick(300)
        assertEquals(1, game.flights.size)
    }

    // --- Le serveur ---

    @Test
    fun leServeurSauteUneVoieALaFoisEtResteDansLesBornes() {
        val game = newGame()
        game.moveLeft()
        assertEquals(PANCAKE_LEFT, game.waiter)
        game.moveLeft()
        assertEquals(PANCAKE_LEFT, game.waiter)
        game.moveRight()
        assertEquals(PANCAKE_CENTER, game.waiter)
        game.moveRight()
        game.moveRight()
        assertEquals(PANCAKE_RIGHT, game.waiter)
    }

    // --- La trajectoire ---

    @Test
    fun laCrepeSuitLaTrajectoireRelevee() {
        assertNull(Pancake.spriteAt(-1.0))
        assertEquals(Pancake.Sprite.Step(0), Pancake.spriteAt(0.0))
        assertEquals(Pancake.Sprite.Step(1), Pancake.spriteAt(3.0))
        assertEquals(Pancake.Sprite.OnPlate(PANCAKE_RIGHT), Pancake.spriteAt(21.0))
        assertEquals(Pancake.Sprite.OnPlate(PANCAKE_RIGHT), Pancake.spriteAt(22.5))
        assertEquals(Pancake.Sprite.Step(7), Pancake.spriteAt(23.0))
        assertEquals(Pancake.Sprite.OnPlate(PANCAKE_CENTER), Pancake.spriteAt(47.5))
        assertEquals(Pancake.Sprite.OnPlate(PANCAKE_LEFT), Pancake.spriteAt(67.5))
        assertEquals(Pancake.Sprite.Step(Pancake.stepCount - 1), Pancake.spriteAt(70.0))
        assertEquals(Pancake.Sprite.Diner, Pancake.spriteAt(76.0))
        assertNull(Pancake.spriteAt(73.0))
        assertNull(Pancake.spriteAt(81.0))
    }

    @Test
    fun leCuisinierRepeteSaBoucleDeSixUnites() {
        assertEquals(0, Pancake.chefPhaseAt(0.0))
        assertEquals(1, Pancake.chefPhaseAt(1.0))
        assertEquals(2, Pancake.chefPhaseAt(2.0))
        assertEquals(3, Pancake.chefPhaseAt(3.0))
        assertEquals(4, Pancake.chefPhaseAt(3.5))
        assertEquals(5, Pancake.chefPhaseAt(5.0))
        assertEquals(0, Pancake.chefPhaseAt(6.0))
        assertEquals(3, Pancake.chefPhaseAt(15.0))
    }

    // --- Les crêpes attrapées et tombées ---

    @Test
    fun uneCrepeAttrapeeToutLeLongRapporteUnPoint() {
        val game = newGame()
        game.playPerfectly(30_000)
        assertEquals(1, game.score)
        assertEquals(0, game.misses)
    }

    @Test
    fun unJoueurSansFauteNeLaisseTomberAucuneCrepeMemeQuandElleSontPlusieurs() {
        val game = newGame()
        game.playPerfectly(300_000)
        assertFalse(game.isOver)
        assertEquals(0, game.misses)
        assertTrue(game.score > 20)
    }

    @Test
    fun uneCrepeTombeeFigeLeJeuUnInstantEtCompteUnRate() {
        val game = newGame()
        // Le serveur reste au centre : la première crêpe arrive à droite et tombe.
        game.moveRight()
        game.moveLeft()
        var guard = 0
        while (game.misses == 0 && guard++ < 10_000) game.tick(20)
        assertEquals(1, game.misses)
        assertEquals(PANCAKE_RIGHT, game.splatLane)
        val clock = game.clock
        game.tick(400)
        assertEquals(clock, game.clock, 0.0)
        game.tick(500)
        assertEquals(-1, game.splatLane)
        game.tick(100)
        assertTrue(game.clock > clock)
    }

    @Test
    fun laPartieSArreteALaCinquiemeCrepeTombee() {
        val game = newGame()
        var guard = 0
        while (!game.isOver && guard++ < 100_000) game.tick(16)
        assertTrue(game.isOver)
        assertEquals(Pancake.MAX_MISSES, game.misses)
        val clock = game.clock
        game.tick(5000)
        assertEquals(clock, game.clock, 0.0)
    }

    @Test
    fun resetRemetLaPartieAZero() {
        val game = newGame()
        game.playPerfectly(30_000)
        game.reset()
        assertEquals(0, game.score)
        assertEquals(0, game.misses)
        assertTrue(game.flights.isEmpty())
        assertEquals(PANCAKE_CENTER, game.waiter)
        assertEquals(0.0, game.clock, 0.0)
    }

    // --- La sauvegarde ---

    @Test
    fun laPartieSeRestaureTelleQuelle() {
        val game = newGame()
        game.playPerfectly(45_000)
        game.moveLeft()
        val copy = PancakeGame.restore(game.save(), Random(1))
        assertNotNull(copy)
        copy!!
        assertEquals(game.score, copy.score)
        assertEquals(game.misses, copy.misses)
        assertEquals(game.waiter, copy.waiter)
        assertEquals(game.clock, copy.clock, 1e-3)
        assertEquals(game.flights.size, copy.flights.size)
        assertEquals(game.sprites(), copy.sprites())
    }

    @Test
    fun uneSauvegardeIncorrecteEstRefusee() {
        assertNull(PancakeGame.restore(emptyList()))
        assertNull(PancakeGame.restore(List(5) { 0 }))
    }
}
