package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le Skyjo Action dans l'appli : jeu jouable, feuille de scores, inscription des manches. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class SkyjoActionGameTest {

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == SKYJO_ACTION_ID }

    private fun viewModel() = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), rules()) }

    private fun grid(up: Set<Int> = emptySet()): List<SkyjoCell?> = (1..12).map { SkyjoCell(it, (it - 1) in up) }

    @Test
    fun leSkyjoActionEstUnJeuJouableSurUnSeulTelephoneAvecSaFeuilleDeScores() {
        val game = rules()
        assertTrue(game.isPlayableGame())
        assertFalse(game.isMultiPhone())
        assertTrue(game.hasScoreSheet())
        assertEquals(GameCategory.SPECIAL, game.category())
        assertEquals(2, game.minPlayers)
        assertEquals(8, game.maxPlayers)
        assertTrue(game.lowestWins)
        assertTrue(SKYJO_ACTION_ID in GAME_ICON_IDS)
    }

    @Test
    fun lesPointsDeLaMancheSontInscritsUneSeuleFoisPuisLaMancheSuivanteCommenceParCeluiQuiATermine() {
        val vm = viewModel()
        val r = vm.skyjoActionRound()
        r.load(listOf(grid(up = (0..10).toSet()), grid()), stock = listOf(1, 1))
        r.drawFromStock(); r.discardHeldAndFlip(11)
        r.drawFromStock(); r.placeHeld(0)
        assertTrue(r.roundOver)
        assertTrue(vm.hasProgress())
        assertFalse(r.nextRound())
        assertTrue(r.settle(vm))
        assertFalse(r.settle(vm))
        assertEquals(listOf(156, 78), vm.totals)
        assertTrue(r.nextRound())
        assertEquals(2, r.roundNumber)
        assertEquals(0, r.current)
    }

    @Test
    fun uneNouvellePartieEffaceLaMancheEnCours() {
        val vm = viewModel()
        val first = vm.skyjoActionRound()
        first.load(listOf(grid(), grid()), stock = listOf(1))
        first.drawFromStock(); first.placeHeld(0)
        assertTrue(vm.hasProgress())
        vm.initGame(listOf("Anna", "Ben"), rules())
        assertFalse(first === vm.skyjoActionRound())
        assertFalse(vm.hasProgress())
    }
}
