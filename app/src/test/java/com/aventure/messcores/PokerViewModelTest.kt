package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Le poker dans la partie en cours : branchement de [PokerSession] sur le ViewModel (annulation, journal, changement de table). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class PokerViewModelTest {

    private val pokerRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.scoreMode == ScoreMode.POKER }

    private val settings = PokerSettings(
        startingStack = 5000, smallBlind = 50, bigBlind = 100, levelMinutes = 10,
        anteEnabled = false, anteAfterIncreases = 0, firstDealer = 0
    )

    private fun poker(vararg names: String) = ScoreViewModel().also {
        it.initGame(names.toList(), pokerRules)
        it.startPoker(settings)
    }

    private fun ScoreViewModel.finishHand() {
        while (pokerSession.game?.phase == PokerPhase.BETTING) pokerSession.fold()
    }

    @Test
    fun lesJetonsSontLesTotauxEtLActionSAnnuleParLeViewModel() {
        val vm = poker("Anna", "Ben", "Cleo")
        assertEquals(vm.pokerSession.game!!.seats.map { it.stack }, vm.totals)
        assertFalse(vm.canUndo)
        vm.pokerSession.fold()
        assertTrue(vm.canUndo)
        vm.undo()
        assertFalse(vm.canUndo)
        assertTrue(vm.canRedo)
        vm.redo()
        assertTrue(vm.canUndo)
    }

    @Test
    fun laPartieEnregistreeReprendAvecLeMinuteurEnPause() {
        val vm = poker("Anna", "Ben", "Cleo")
        vm.pokerSession.fold()
        val saved = vm.snapshot()
        assertEquals(vm.pokerSession.game!!.copy(clockMs = saved.poker!!.clockMs), saved.poker)

        val reprise = ScoreViewModel()
        reprise.loadFromSaved(saved)
        assertEquals(saved.poker, reprise.pokerSession.game)
        assertFalse(reprise.pokerSession.clockRunning)
        assertFalse(reprise.canUndo)
        assertTrue(reprise.hasProgress())
    }

    @Test
    fun uneNouvellePartieEffaceLePoker() {
        val vm = poker("Anna", "Ben", "Cleo")
        vm.pokerSession.fold()
        vm.initGame(listOf("Anna", "Ben"), pokerRules)
        assertEquals(null, vm.pokerSession.game)
        assertFalse(vm.pokerSession.clockRunning)
        assertFalse(vm.canUndo)
    }

    @Test
    fun unJoueurQuiPartGardeLesNomsEtLesCouleursDesAutres() {
        val vm = poker("Anna", "Ben", "Cleo", "Dan")
        vm.finishHand()
        val couleurs = vm.playerColors

        assertEquals(PokerReseatResult.DONE, vm.pokerRemovePlayers(listOf(1)))

        assertEquals(listOf("Anna", "Cleo", "Dan"), vm.players)
        assertEquals(listOf(couleurs[0], couleurs[2], couleurs[3]), vm.playerColors)
        assertEquals(3, vm.pokerSession.game!!.seats.size)
        assertFalse(vm.canUndo)
    }

    @Test
    fun unArrivantAuMemeNomEstNumeroteEtRecoitUneCouleurLibre() {
        val vm = poker("Anna", "Ben", "Cleo")
        vm.finishHand()

        val result = vm.pokerImportPlayers(listOf(PokerTransferPlayer("Anna", 2000, 0)), Random(1))

        assertEquals(PokerReseatResult.DONE, result)
        assertEquals(4, vm.players.size)
        assertEquals(4, vm.playerColors.size)
        assertEquals(4, vm.players.distinct().size)
        assertEquals(4, vm.playerColors.distinct().size)
        assertTrue(vm.players.containsAll(listOf("Anna", "Ben", "Cleo")))
        assertEquals(2000, vm.totals[vm.players.indexOfFirst { it !in listOf("Anna", "Ben", "Cleo") }])
    }

    @Test
    fun onNeChangePasDeTableEnPleineMainOuSiLaTableEstPleine() {
        val vm = poker("Anna", "Ben", "Cleo")
        assertEquals(PokerReseatResult.NOT_ALLOWED, vm.pokerRemovePlayers(listOf(0)))
        assertEquals(PokerReseatResult.NOT_ALLOWED, vm.pokerImportPlayers(listOf(PokerTransferPlayer("Zoé", 1000, 0))))
        assertEquals(listOf("Anna", "Ben", "Cleo"), vm.players)

        vm.finishHand()
        val trop = List(8) { PokerTransferPlayer("J$it", 1000, 0) }
        assertEquals(PokerReseatResult.TABLE_FULL, vm.pokerImportPlayers(trop))
        assertEquals(3, vm.players.size)
    }
}
