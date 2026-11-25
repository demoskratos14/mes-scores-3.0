package com.aventure.messcores

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FishChaseTest {

    /** Avance jusqu'à ce que la mire soit bien sur l'homme (au plus 60 s de jeu), puis lance un poisson et attend son arrivée. */
    private fun aimAndFire(game: FishChaseGame) {
        var waited = 0
        while (!(game.crossOnMan && abs(game.crossX - game.manX) < 0.25f) || !game.canFire || game.stunMs > 0) {
            game.tick(16)
            waited += 16
            assertFalse("la partie ne doit pas finir avant le tir", game.isOver)
            assertTrue("la mire ne passe jamais sur l'homme", waited < 60_000)
        }
        assertTrue(game.fire())
        repeat(FishChase.FLIGHT_MS / 16 + 2) { game.tick(16) }
    }

    @Test
    fun laMireResteDansLeTerrain() {
        val game = FishChaseGame()
        repeat(2_000) {
            game.tick(16)
            assertTrue(game.crossX in 0f..FishChase.WIDTH)
            assertTrue(game.crossY in 0f..FishChase.HEIGHT)
        }
    }

    @Test
    fun lePoissonDoitAttendreLaPauseEntreDeuxLancers() {
        val game = FishChaseGame()
        assertTrue(game.fire())
        assertFalse(game.fire())
        repeat(FishChase.COOLDOWN_MS / 16 + 2) { game.tick(16) }
        assertTrue(game.fire())
        assertEquals(2, game.shots)
    }

    @Test
    fun unPoissonQuiToucheRepousseLHommeEtRapporteDesPoints() {
        val game = FishChaseGame()
        game.tick(16)
        aimAndFire(game)
        assertEquals(1, game.hits)
        assertEquals(1, game.combo)
        assertEquals(FishChase.BASE_POINTS, game.score)
        assertTrue(game.stunMs > 0)
        val before = game.manX
        repeat(60) { game.tick(16) }
        assertTrue("l'homme doit avoir reculé", game.manX < before)
    }

    @Test
    fun unPoissonQuiRateRemetLEnchainementAZero() {
        val game = FishChaseGame()
        game.tick(16)
        aimAndFire(game)
        assertEquals(1, game.combo)
        // On attend une mire loin de l'homme, puis on tire.
        while (abs(game.crossX - game.manX) < 3f || !game.canFire) game.tick(16)
        assertTrue(game.fire())
        repeat(FishChase.FLIGHT_MS / 16 + 2) { game.tick(16) }
        assertEquals(1, game.hits)
        assertEquals(0, game.combo)
    }

    @Test
    fun leNiveauMonteTousLesQuatrePoissons() {
        val game = FishChaseGame()
        game.tick(16)
        repeat(FishChase.HITS_PER_LEVEL) { aimAndFire(game) }
        assertEquals(FishChase.HITS_PER_LEVEL, game.hits)
        assertEquals(2, game.level)
        assertTrue(game.bannerMs > 0)
    }

    @Test
    fun lHommeFinitParRattraperLaFemmeSiOnNeFaitRien() {
        val game = FishChaseGame()
        var elapsed = 0
        while (!game.isOver && elapsed < 120_000) {
            game.tick(16)
            elapsed += 16
        }
        assertTrue(game.isOver)
        assertEquals(FishChase.CATCH_X, game.manX, 0.001f)
    }

    @Test
    fun laPartieSeRestaureApresUneRotation() {
        val game = FishChaseGame()
        game.tick(16)
        aimAndFire(game)
        repeat(30) { game.tick(16) }
        val copy = FishChaseGame.restore(game.save())
        assertEquals(game.score, copy.score)
        assertEquals(game.level, copy.level)
        assertEquals(game.hits, copy.hits)
        assertEquals(game.manX, copy.manX, 0.0001f)
        assertEquals(game.isOver, copy.isOver)
    }

    @Test
    fun uneSauvegardeAbimeeDonneUnePartieNeuve() {
        val copy = FishChaseGame.restore(listOf(1, 2, 3))
        assertEquals(0, copy.score)
        assertEquals(1, copy.level)
    }

    @Test
    fun leJeuEstUnJeuSoloEtMonoTelephone() {
        assertTrue(FISHCHASE_ID in GAME_ICON_IDS)
    }
}
