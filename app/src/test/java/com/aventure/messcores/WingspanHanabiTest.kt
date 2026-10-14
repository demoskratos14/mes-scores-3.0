package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Wingspan et Hanabi : feuilles de catégories prédéfinies. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class WingspanHanabiTest {

    private fun game(id: String) = GameRepository(org.robolectric.RuntimeEnvironment.getApplication()).builtInGames()
        .first { it.id == id }

    @Test
    fun wingspanEstUneFeuilleDeSeptFamilles() {
        val g = game("builtin_wingspan")
        assertEquals(ScoreSheets.CUSTOM, g.sheet)
        assertEquals(7, ScoreSheets.rows(g)!!.size)
        assertTrue(ScoreSheets.rows(g)!!.all { it.hint != null })
        assertEquals(GameCategory.SPECIAL, g.category())
        assertFalse(g.allowNegativeScores)
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), g) }
        vm.setCell(0, 0, 40, true)
        vm.setCell(1, 0, 7, true)
        vm.setCell(0, 3, 12, true)
        assertEquals(52, vm.totalFor(0))
        assertEquals(7, vm.totalFor(1))
    }

    @Test
    fun hanabiEstUneFeuilleCooperativeAUneColonne() {
        val g = game("builtin_hanabi")
        assertEquals(1, g.minPlayers)
        assertEquals(1, g.maxPlayers)
        val rows = ScoreSheets.rows(g)!!
        assertEquals(6, rows.size)
        assertTrue(rows.all { it.options == listOf(0, 1, 2, 3, 4, 5) && it.hint != null })
        val vm = ScoreViewModel().also { it.initGame(listOf("Équipe"), g) }
        for (row in 0 until 5) vm.setCell(0, row, 5, true)
        assertEquals(25, vm.totalFor(0))
    }
}
