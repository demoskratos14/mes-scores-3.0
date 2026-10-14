package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Tennis et padel dans la partie en cours : saisie des points, annulation, sauvegarde et reprise. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class RacketViewModelTest {

    private fun rules(id: String) =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == id }

    private fun tennis(vararg names: String) =
        ScoreViewModel().also { it.initGame(names.toList(), rules(RacketConfig.TENNIS_ID)) }

    private fun winGame(vm: ScoreViewModel, side: Int) = repeat(4) { vm.racketPoint(side) }

    private fun winSet(vm: ScoreViewModel, side: Int) = repeat(6) { winGame(vm, side) }

    @Test
    fun tennisEtPadelSontDesJeuxPredefinisAvecLeurPropreEcran() {
        assertEquals(ScoreMode.RACKET, rules("builtin_tennis").scoreMode)
        assertEquals(ScoreMode.RACKET, rules("builtin_padel").scoreMode)
        assertEquals(GameCategory.OTHER, rules("builtin_padel").category())
    }

    @Test
    fun lePadelCommenceAvecLePointDOr() {
        val padel = ScoreViewModel().also { it.initGame(listOf("A", "B", "C", "D"), rules("builtin_padel")) }
        assertTrue(padel.racketConfig.goldenPoint)
        assertFalse(tennis("A", "B").racketConfig.goldenPoint)
    }

    @Test
    fun lesPointsFontAvancerLeScoreEtLesTotauxSontLesSets() {
        val vm = tennis("Anna", "Ben")
        assertFalse(vm.hasProgress())
        winGame(vm, 0)
        assertTrue(vm.hasProgress())
        assertEquals(1, vm.racketState.gamesA)
        assertEquals(listOf(0, 0), vm.totals)
        winSet(vm, 0)
        assertEquals(listOf(1, 0), vm.totals)
        assertEquals(1, vm.rankOf(0))
        assertEquals(2, vm.rankOf(1))
        assertFalse(vm.isGameOver())
    }

    @Test
    fun enDoubleLesDeuxJoueursDUnCampPartagentLeScore() {
        val vm = tennis("Anna", "Ben", "Chloé", "Dan")
        winSet(vm, 1)
        assertEquals(listOf(0, 0, 1, 1), vm.totals)
        assertEquals(listOf(2, 3), vm.racketPlayers(1))
    }

    @Test
    fun lePremierCampQuiGagneLeNombreDeSetsRemporteLeMatch() {
        val vm = tennis("Anna", "Ben")
        winSet(vm, 0)
        winSet(vm, 0)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
        // Plus aucun point n'est accepté une fois le match fini.
        vm.racketPoint(1)
        assertEquals(2, vm.racketState.sets.size)
        assertEquals(0, vm.racketState.gamesB)
    }

    @Test
    fun annulerEtRetablirRejouentLesPoints() {
        val vm = tennis("Anna", "Ben")
        vm.racketPoint(0)
        vm.racketPoint(1)
        assertTrue(vm.canUndo)
        assertFalse(vm.canRedo)
        vm.undo()
        assertEquals(1, vm.racketState.pointsA)
        assertEquals(0, vm.racketState.pointsB)
        assertTrue(vm.canRedo)
        vm.redo()
        assertEquals(1, vm.racketState.pointsB)
        // Un nouveau point vide les points rétablissables.
        vm.undo()
        vm.racketPoint(0)
        assertFalse(vm.canRedo)
    }

    @Test
    fun onPeutAnnulerLeDernierPointDuMatchPourLeRelancer() {
        val vm = tennis("Anna", "Ben")
        winSet(vm, 0)
        winSet(vm, 0)
        assertTrue(vm.isGameOver())
        vm.undo()
        assertFalse(vm.isGameOver())
        assertEquals(1, vm.racketState.sets.size)
    }

    @Test
    fun lesReglagesNeChangentPlusApresLePremierPoint() {
        val vm = tennis("Anna", "Ben")
        assertTrue(vm.setRacketConfig(RacketConfig(setsToWin = 3, goldenPoint = true)))
        assertEquals(3, vm.racketConfig.setsToWin)
        vm.racketPoint(0)
        assertFalse(vm.setRacketConfig(RacketConfig(setsToWin = 1)))
        assertEquals(3, vm.racketConfig.setsToWin)
    }

    @Test
    fun laPartieSEnregistreEtSeReprendDansLeJournal() {
        val vm = tennis("Anna", "Ben")
        vm.setRacketConfig(RacketConfig(setsToWin = 3, goldenPoint = true, finalSet = FinalSetMode.ADVANTAGE))
        winSet(vm, 0)
        winGame(vm, 1)
        vm.racketPoint(0)

        val saved = SavedGame.fromJson(vm.snapshot().toJson())
        assertEquals(listOf(1, 0), saved.totals())
        assertFalse(saved.isFinished)
        assertNull(saved.cellSnapshots)

        val reloaded = ScoreViewModel().also { it.loadFromSaved(saved) }
        assertEquals(vm.racketConfig, reloaded.racketConfig)
        assertEquals(vm.racketState, reloaded.racketState)
        assertEquals(listOf(1, 0), reloaded.totals)
    }

    @Test
    fun uneNouvellePartieRepartDeZero() {
        val vm = tennis("Anna", "Ben")
        winSet(vm, 0)
        vm.initGame(listOf("Anna", "Ben"), rules(RacketConfig.TENNIS_ID))
        assertFalse(vm.hasProgress())
        assertTrue(vm.racketState.sets.isEmpty())
        assertFalse(vm.canUndo)
    }

    @Test
    fun leTexteDePartageContientLeScoreEnChiffres() {
        val vm = tennis("Anna", "Ben")
        winSet(vm, 0)
        winGame(vm, 1)
        val text = vm.shareText(RuntimeEnvironment.getApplication())
        assertTrue(text, text.contains("6-0 0-1"))
    }
}
