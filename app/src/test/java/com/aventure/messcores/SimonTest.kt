package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SimonTest {

    @Test
    fun uneSerieCommenceParUneSeuleCouleurValide() {
        repeat(50) {
            val run = SimonRun.start(Random(it))
            assertEquals(1, run.sequence.size)
            assertTrue(run.sequence.single() in 0 until Simon.PADS)
            assertEquals(0, run.completed)
            assertFalse(run.failed)
        }
    }

    @Test
    fun laBonneToucheFaitAvancer() {
        val run = SimonRun(listOf(2, 0, 3)).tap(2)
        assertEquals(1, run.progress)
        assertEquals(0, run.sequence[run.progress])
        assertEquals(0, run.expected)
        assertFalse(run.failed)
    }

    @Test
    fun laMauvaiseToucheFinitLaSerie() {
        val run = SimonRun(listOf(2, 0, 3)).tap(2).tap(1)
        assertTrue(run.failed)
        assertNull(run.expected)
        assertEquals(0, run.completed)
    }

    @Test
    fun uneSerieRateeNeBougeplus() {
        val run = SimonRun(listOf(1)).tap(3)
        assertEquals(run, run.tap(1))
        assertEquals(run, run.nextRound())
    }

    @Test
    fun laSuiteEstReproduiteQuandToutesLesToucheSontBonnes() {
        var run = SimonRun(listOf(1, 1, 2))
        assertFalse(run.roundComplete)
        run = run.tap(1).tap(1)
        assertFalse(run.roundComplete)
        run = run.tap(2)
        assertTrue(run.roundComplete)
        assertNull(run.expected)
    }

    @Test
    fun uneToucheDeTropApresLaSuiteEstIgnoree() {
        val done = SimonRun(listOf(0)).tap(0)
        assertTrue(done.roundComplete)
        assertEquals(done, done.tap(3))
    }

    @Test
    fun laSuiteSuivanteGardeLAncienneEtAjouteUneCouleur() {
        val run = SimonRun(listOf(3, 1)).tap(3).tap(1).nextRound(Random(5))
        assertEquals(3, run.sequence.size)
        assertEquals(listOf(3, 1), run.sequence.take(2))
        assertTrue(run.sequence.last() in 0 until Simon.PADS)
        assertEquals(0, run.progress)
        assertEquals(1, run.completed)
    }

    @Test
    fun onNePasseAuTourSuivantQuandLaSuiteNEstPasFinie() {
        val run = SimonRun(listOf(3, 1)).tap(3)
        assertEquals(run, run.nextRound())
    }

    @Test
    fun laSerieCompteLesSuitesReproduitesEnEntier() {
        var run = SimonRun(listOf(0))
        run = run.tap(0).nextRound(Random(1))
        val second = run.sequence
        for (pad in second) run = run.tap(pad)
        run = run.nextRound(Random(2))
        assertEquals(2, run.completed)
        assertEquals(3, run.sequence.size)
    }

    @Test
    fun lAllumageVaDePlusEnPlusVitePuisPlafonne() {
        assertEquals(Simon.START_FLASH_MS, Simon.flashMs(1))
        assertTrue(Simon.flashMs(10) < Simon.flashMs(5))
        assertEquals(Simon.MIN_FLASH_MS, Simon.flashMs(500))
        assertEquals(Simon.START_FLASH_MS, Simon.flashMs(0))
    }

    @Test
    fun leMeilleurScoreGagne() {
        val game = SimonGame(players = 3).finishRun(4).finishRun(9).finishRun(2)
        assertTrue(game.isOver)
        assertEquals(9, game.best)
        assertEquals(listOf(1), game.winners)
        assertEquals(listOf(1, 0, 2), game.ranking())
    }

    @Test
    fun alEgaliteLesJoueursSontExAequo() {
        val game = SimonGame(players = 3).finishRun(5).finishRun(3).finishRun(5)
        assertEquals(listOf(0, 2), game.winners)
    }

    @Test
    fun personneNeGagneSiAucuneSuiteNEstReproduite() {
        val game = SimonGame(players = 2).finishRun(0).finishRun(0)
        assertTrue(game.isOver)
        assertTrue(game.winners.isEmpty())
    }

    @Test
    fun pasDeGagnantTantQueLaPartieContinue() {
        val game = SimonGame(players = 2).finishRun(6)
        assertEquals(1, game.current)
        assertTrue(game.winners.isEmpty())
    }

    @Test
    fun unJoueurSeulAUnScorePourSeBattre() {
        val game = SimonGame(players = 1).finishRun(7)
        assertTrue(game.isOver)
        assertEquals(listOf(0), game.winners)
    }

    @Test
    fun unScoreDeTropOuNegatifEstTraiteProprement() {
        assertEquals(listOf(0), SimonGame(players = 1).finishRun(-2).scores)
        assertEquals(listOf(3), SimonGame(players = 1).finishRun(3).finishRun(8).scores)
    }

    @Test
    fun uneNouvellePartieGardeLesJoueursEtEfface() {
        val next = SimonGame(players = 2).finishRun(3).finishRun(4).newGame()
        assertEquals(2, next.players)
        assertTrue(next.scores.isEmpty())
    }

    // ---------- Chacun son tour ----------

    @Test
    fun laPartieCommenceParUneCouleurEtLePremierJoueur() {
        val game = SimonTurns.start(3, Random(3))
        assertEquals(1, game.sequence.size)
        assertEquals(0, game.current)
        assertEquals(listOf(0, 1, 2), game.alive)
        assertFalse(game.isOver)
        assertNull(game.winner)
    }

    @Test
    fun uneSuiteReussieLAllongeEtPasseAuSuivant() {
        val start = SimonTurns(players = 3, sequence = listOf(2))
        val next = start.succeed(Random(9))
        assertEquals(2, next.sequence.size)
        assertEquals(2, next.sequence.first())
        assertEquals(1, next.current)
        assertEquals(1, next.reached)
    }

    @Test
    fun apresLeDernierJoueurOnRevientAuPremier() {
        val game = SimonTurns(players = 3, sequence = listOf(0), current = 2).succeed(Random(1))
        assertEquals(0, game.current)
    }

    @Test
    fun uneErreurEliminePuisLeSuivantRetenteLaMemeSuite() {
        val start = SimonTurns(players = 3, sequence = listOf(1, 2))
        val next = start.fail()
        assertEquals(listOf(0), next.eliminated)
        assertEquals(1, next.current)
        assertEquals(listOf(1, 2), next.sequence)
        assertEquals(listOf(1, 2), next.alive)
    }

    @Test
    fun unJoueurEliminEstSauteAuTourSuivant() {
        var game = SimonTurns(players = 3, sequence = listOf(0))
        game = game.fail() // 0 éliminé, au tour de 1
        game = game.succeed(Random(4)) // 1 réussit, 2 suit
        assertEquals(2, game.current)
        game = game.succeed(Random(5)) // 2 réussit : on saute 0, éliminé
        assertEquals(1, game.current)
    }

    @Test
    fun leDernierEnJeuGagne() {
        var game = SimonTurns(players = 3, sequence = listOf(0))
        game = game.fail() // 0 sort
        assertFalse(game.isOver)
        game = game.fail() // 1 sort
        assertTrue(game.isOver)
        assertEquals(2, game.winner)
        assertEquals(listOf(2, 1, 0), game.ranking())
    }

    @Test
    fun aDeuxJoueursUneErreurFinitLaPartie() {
        val game = SimonTurns(players = 2, sequence = listOf(3)).fail()
        assertTrue(game.isOver)
        assertEquals(1, game.winner)
        assertEquals(listOf(1, 0), game.ranking())
    }

    @Test
    fun uneFoisFiniePlusRienNeChange() {
        val over = SimonTurns(players = 2, sequence = listOf(3)).fail()
        assertEquals(over, over.fail())
        assertEquals(over, over.succeed(Random(1)))
    }

    @Test
    fun uneNouvellePartieRemetToutLeMondeEnJeu() {
        val next = SimonTurns(players = 3, sequence = listOf(1, 2, 3), eliminated = listOf(0, 1), current = 2).newGame(Random(2))
        assertEquals(3, next.players)
        assertTrue(next.eliminated.isEmpty())
        assertEquals(1, next.sequence.size)
        assertEquals(0, next.current)
    }
}
