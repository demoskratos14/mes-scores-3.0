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
class ScoreSheetTest {

    private val yams = GameRules(id = "builtin_yams", name = "Yams", scoreMode = ScoreMode.TABLE, sheet = ScoreSheets.YAMS)
    private val rowCount = ScoreSheets.rows(ScoreSheets.YAMS)!!.size

    private fun newGame(vararg names: String) = ScoreViewModel().also { it.initGame(names.toList(), yams) }

    private fun ScoreViewModel.enter(row: Int, player: Int, value: Int) {
        setCell(row, player, value, isNegative = false)
    }

    @Test
    fun laFeuilleDuYamsAtreizeCategoriesDontSixEnPartieHaute() {
        val rows = ScoreSheets.rows(ScoreSheets.YAMS)!!
        assertEquals(13, rows.size)
        assertEquals(6, rows.count { it.upper })
        assertEquals(null, ScoreSheets.rows(null))
    }

    @Test
    fun laPartieCommenceAvecToutesLesLignesEtNenAjouteAucune() {
        val vm = newGame("Anna", "Ben")
        assertEquals(rowCount, vm.scores.size)
        vm.enter(rowCount - 1, 0, 20)
        vm.enter(0, 1, 3)
        assertEquals(rowCount, vm.scores.size)
    }

    @Test
    fun leBonusDe35EstAjouteDesQueLaPartieHauteAtteint63() {
        val vm = newGame("Anna", "Ben")
        // Anna : 3 de chaque chiffre = 3 × (1+2+3+4+5+6) = 63 -> bonus.
        for (row in 0..5) vm.enter(row, 0, (row + 1) * 3)
        // Ben : 62 (un dé de moins dans « Six »)
        for (row in 0..4) vm.enter(row, 1, (row + 1) * 3)
        vm.enter(5, 1, 17)
        assertEquals(63, vm.sheetUpperTotalFor(0))
        assertEquals(35, vm.sheetBonusFor(0))
        assertEquals(63 + 35, vm.totalFor(0))
        assertEquals(0, vm.sheetBonusFor(1))
        assertEquals(62, vm.totalFor(1))
    }

    @Test
    fun laPartieNeSeTermineQuandToutesLesCasesSontRemplies() {
        val vm = newGame("Anna", "Ben")
        for (row in 0 until rowCount) {
            for (player in 0..1) {
                assertFalse(vm.isGameOver())
                vm.enter(row, player, 0)
            }
        }
        assertTrue(vm.isGameOver())
        // Un 0 (case barrée) compte comme une case remplie.
        assertEquals(0, vm.totalFor(0))
    }

    @Test
    fun laPartieEnregistreeConserveLaFeuilleEtLeBonus() {
        val vm = newGame("Anna", "Ben")
        for (row in 0..5) vm.enter(row, 0, (row + 1) * 3)
        vm.enter(9, 0, 30)
        val saved = vm.snapshot()
        assertEquals(ScoreSheets.YAMS, saved.gameRules.sheet)
        assertEquals(vm.totals, saved.totals())
        // Aller-retour JSON (journal, sauvegarde) : la feuille est toujours reconnue.
        val reloaded = SavedGame.fromJson(saved.toJson())
        assertEquals(ScoreSheets.YAMS, reloaded.gameRules.sheet)
        assertEquals(vm.totals, reloaded.totals())
        val other = ScoreViewModel().also { it.loadFromSaved(reloaded) }
        assertEquals(rowCount, other.scores.size)
        assertEquals(vm.totals, other.totals)
    }

    @Test
    fun lesJeuxDeDesPredefinisSontDisponibles() {
        val games = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().associateBy { it.id }

        val y = games.getValue("builtin_yams")
        assertEquals(ScoreSheets.YAMS, y.sheet)
        assertEquals(ScoreMode.TABLE, y.scoreMode)

        val quatre = games.getValue("builtin_421")
        assertEquals(ScoreMode.COUNTER, quatre.scoreMode)
        assertTrue(quatre.lowestWins)

        val cdc = games.getValue("builtin_cdc")
        assertEquals(343, cdc.endCondition.scoreThreshold)
        assertTrue(cdc.allowNegativeScores)
    }

    @Test
    fun leCulDeChouetteSArreteAuSeuilDe343() {
        val cdc = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_cdc" }
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), cdc) }
        vm.setCell(0, 0, 300, false)
        assertFalse(vm.isGameOver())
        vm.setCell(1, 0, 43, false)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }
}
