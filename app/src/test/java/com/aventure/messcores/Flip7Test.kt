package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class Flip7Test {

    private fun newGame(vararg names: String) = ScoreViewModel().also { vm ->
        val rules = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_flip7" }
        vm.initGame(names.toList(), rules)
    }

    @Test
    fun flip7SeJoueDe3A12JoueursEnTableauJusqua200() {
        val rules = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_flip7" }
        assertEquals(ScoreMode.TABLE, rules.scoreMode)
        assertEquals(3, rules.minPlayers)
        assertEquals(12, rules.maxPlayers)
        assertEquals(200, rules.endCondition.scoreThreshold)
        assertEquals(GameCategory.SPECIAL, rules.category())
    }

    @Test
    fun laPartieNeSArretePasAvantLaFinDeLaMancheOu200EstAtteint() {
        val vm = newGame("Anna", "Ben", "Cléo")
        vm.setCell(0, 0, 120, false)
        vm.setCell(0, 1, 90, false)
        vm.setCell(0, 2, 80, false)
        assertFalse(vm.isGameOver())

        // Anna passe 200, mais la manche n'est pas complète : on continue.
        vm.setCell(1, 0, 85, false)
        assertEquals(205, vm.totalFor(0))
        assertFalse(vm.isGameOver())

        vm.setCell(1, 1, 100, false)
        assertFalse(vm.isGameOver())
        vm.setCell(1, 2, 60, false)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun uneEgaliteEnTeteApres200SeJoueEnMancheSupplementaire() {
        val vm = newGame("Anna", "Ben", "Cléo")
        vm.setCell(0, 0, 200, false)
        vm.setCell(0, 1, 200, false)
        vm.setCell(0, 2, 0, false)
        assertFalse(vm.isGameOver())

        vm.setCell(1, 0, 10, false)
        vm.setCell(1, 1, 0, false)
        vm.setCell(1, 2, 0, false)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }
}
