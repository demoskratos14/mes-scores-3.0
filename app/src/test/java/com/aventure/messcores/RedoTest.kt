package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Annuler / rétablir : les saisies annulées se rejouent tant qu'aucune nouvelle saisie n'est faite. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class RedoTest {

    private val names = listOf("Anna", "Ben")

    private fun counterGame() = ScoreViewModel().also {
        it.initGame(names, GameRules(id = "c", name = "C", scoreMode = ScoreMode.COUNTER, allowNegativeScores = true))
    }

    private fun tableGame() = ScoreViewModel().also { it.initGame(names, GameRules(id = "t", name = "T")) }

    @Test
    fun retablirRejoueLaSaisieAnnulee() {
        val vm = counterGame()
        vm.incrementCounter(0, 1)
        vm.incrementCounter(0, 1)
        assertFalse(vm.canRedo)
        vm.undo()
        assertEquals(listOf(1, 0), vm.totals)
        assertTrue(vm.canRedo)
        vm.redo()
        assertEquals(listOf(2, 0), vm.totals)
        assertFalse(vm.canRedo)
        assertTrue(vm.canUndo)
    }

    @Test
    fun plusieursAnnulationsPuisPlusieursRetablissements() {
        val vm = counterGame()
        vm.incrementCounter(0, 1)
        vm.incrementCounter(1, 1)
        vm.incrementCounter(0, -1)
        repeat(3) { vm.undo() }
        assertEquals(listOf(0, 0), vm.totals)
        repeat(3) { vm.redo() }
        assertEquals(listOf(0, 1), vm.totals)
        // Rétablies, les saisies sont de nouveau annulables.
        vm.undo()
        assertEquals(listOf(1, 1), vm.totals)
    }

    @Test
    fun uneNouvelleSaisieVideLesSaisiesRetablissables() {
        val vm = counterGame()
        vm.incrementCounter(0, 1)
        vm.undo()
        assertTrue(vm.canRedo)
        vm.incrementCounter(1, 1)
        assertFalse(vm.canRedo)
        vm.redo() // sans effet
        assertEquals(listOf(0, 1), vm.totals)
    }

    @Test
    fun retablirSansRienARetablirNeChangeRien() {
        val vm = counterGame()
        vm.redo()
        assertEquals(listOf(0, 0), vm.totals)
        assertFalse(vm.canUndo)
    }

    @Test
    fun retablirUneCaseDuTableauRestaureLaValeurEtLaMancheAjoutee() {
        val vm = tableGame()
        val rounds = vm.scores.size
        val last = vm.scores.lastIndex
        vm.setCell(last, 0, 7, false) // remplit la dernière manche : une manche vide est ajoutée
        assertEquals(rounds + 1, vm.scores.size)
        vm.undo()
        assertEquals(rounds, vm.scores.size)
        assertNull(vm.scores[last][0].baseValue)
        assertTrue(vm.canRedo)
        vm.redo()
        assertEquals(rounds + 1, vm.scores.size)
        assertEquals(7, vm.scores[last][0].baseValue)
        // Le rétablissement reste annulable.
        vm.undo()
        assertNull(vm.scores[last][0].baseValue)
    }

    @Test
    fun uneNouvellePartieOuLaFermetureVidentLeRetablissement() {
        val vm = counterGame()
        vm.incrementCounter(0, 1)
        vm.undo()
        assertTrue(vm.canRedo)
        vm.initGame(names, GameRules(id = "c", name = "C", scoreMode = ScoreMode.COUNTER))
        assertFalse(vm.canRedo)
        vm.incrementCounter(0, 1)
        vm.undo()
        vm.closeGame()
        assertFalse(vm.canRedo)
    }
}
