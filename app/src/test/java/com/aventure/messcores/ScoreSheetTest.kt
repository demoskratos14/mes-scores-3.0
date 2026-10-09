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

    @Test
    fun laFeuilleQwixxAQuatreCouleursEtLesPenalitesSansBonus() {
        val rows = ScoreSheets.rows(ScoreSheets.QWIXX)!!
        assertEquals(5, rows.size)
        // 1 + 2 + … + n pour n croix, jusqu'à 78 points avec 12 croix.
        assertEquals(listOf(0, 1, 3, 6, 10, 15, 21, 28, 36, 45, 55, 66, 78), rows.first().options)
        assertEquals(listOf(0, -5, -10, -15, -20), rows.last().options)
        assertFalse(ScoreSheets.hasBonus(ScoreSheets.QWIXX))
        assertTrue(ScoreSheets.hasBonus(ScoreSheets.YAMS))
        assertEquals(0, ScoreSheets.bonus(ScoreSheets.QWIXX, listOf(78, 78, 78, 78, 0)))
    }

    @Test
    fun leTotalQwixxSoustraitLesPenalites() {
        val qwixx = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_qwixx" }
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), qwixx) }
        assertEquals(5, vm.scores.size)
        vm.setCell(0, 0, 21, false)
        vm.setCell(1, 0, 10, false)
        vm.setCell(2, 0, 6, false)
        vm.setCell(3, 0, 3, false)
        vm.setCell(4, 0, -10, false)
        assertEquals(30, vm.totalFor(0))
        assertEquals(0, vm.sheetBonusFor(0))
        assertFalse(vm.sheetHasBonus)
        assertFalse(vm.isGameOver())
    }

    @Test
    fun lesAutresJeuxDeDesPredefinisOntLesBonnesRegles() {
        val games = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().associateBy { it.id }
        assertEquals(10000, games.getValue("builtin_dixmille").endCondition.scoreThreshold)
        assertEquals(100, games.getValue("builtin_cochon").endCondition.scoreThreshold)
        assertTrue(games.getValue("builtin_zombie").endCondition.tieBreakOnEqualLeaders)
        assertTrue(games.getValue("builtin_shutbox").lowestWins)
        assertEquals(ScoreMode.COUNTER, games.getValue("builtin_kot").scoreMode)
        assertEquals(ScoreMode.COUNTER, games.getValue("builtin_mexicain").scoreMode)
        assertTrue(games.getValue("builtin_mexicain").lowestWins)
        assertEquals(ScoreSheets.QWIXX, games.getValue("builtin_qwixx").sheet)
        // Chaque identifiant est unique.
        assertEquals(games.size, GameRepository(RuntimeEnvironment.getApplication()).builtInGames().size)
    }

    @Test
    fun kingOfTokyoSArreteA20PointsDeVictoire() {
        val kot = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_kot" }
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), kot) }
        repeat(19) { vm.incrementCounter(0, 1) }
        assertFalse(vm.isGameOver())
        vm.incrementCounter(0, 1)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun zombieDiceDepartageLesEgalitesApres13Cerveaux() {
        val zombie = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_zombie" }
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), zombie) }
        vm.setCell(0, 0, 13, false)
        vm.setCell(0, 1, 13, false)
        // Égalité en tête : on ne s'arrête pas, une manche décisive est nécessaire.
        assertFalse(vm.isGameOver())
        vm.setCell(1, 0, 2, false)
        vm.setCell(1, 1, 1, false)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun lesJeuxDeCartesAjoutesOntLesBonnesRegles() {
        val games = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().associateBy { it.id }

        val coinche = games.getValue("builtin_coinche")
        assertEquals(listOf(1, 2, 4), coinche.multipliers.map { it.factor })
        assertEquals(1000, coinche.endCondition.scoreThreshold)

        val coeurs = games.getValue("builtin_coeurs")
        assertTrue(coeurs.lowestWins)
        assertEquals(100, coeurs.endCondition.scoreThreshold)

        assertEquals(500, games.getValue("builtin_pique").endCondition.scoreThreshold)
        assertEquals(2, games.getValue("builtin_gin").maxPlayers)
        assertTrue(games.getValue("builtin_huit").lowestWins)
        assertTrue(games.getValue("builtin_president").allowNegativeScores)
        assertTrue(games.getValue("builtin_cribbage").endCondition.stopImmediately)
    }

    @Test
    fun leCribbageSArreteDesQuUnJoueurAtteint121() {
        val cribbage = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_cribbage" }
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), cribbage) }
        vm.setCell(0, 0, 100, false)
        assertFalse(vm.isGameOver())
        vm.setCell(1, 0, 21, false)
        assertTrue(vm.isGameOver())
    }

    @Test
    fun coeursSArreteA100EtLePlusPetitScoreGagne() {
        val coeurs = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_coeurs" }
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben", "Chloé"), coeurs) }
        for (player in 0..2) vm.setCell(0, player, listOf(100, 20, 30)[player], false)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(1), vm.winners())
    }
}
