package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Données de la courbe d'évolution des scores. */
class ScoreProgressTest {

    private val players = listOf("Anna", "Ben")

    private fun tableGame(rounds: List<List<Int?>>, rules: GameRules = GameRules(id = "t", name = "T")) = SavedGame(
        id = "x",
        savedAt = 0L,
        gameRules = rules,
        players = players,
        playerColorsArgb = emptyList(),
        cellSnapshots = rounds.map { round -> round.map { CellSnapshot(it, false, GameRules.NORMAL_MULTIPLIER_ID) } },
        isFinished = false
    )

    @Test
    fun tableauLeTotalEstCumuleMancheApresManche() {
        val series = ScoreProgress.series(tableGame(listOf(listOf(3, 1), listOf(2, 4), listOf(null, null))))
        assertEquals(listOf(listOf(3, 5), listOf(1, 5)), series)
    }

    @Test
    fun tableauLaMancheVideALaFinNeCompteBienPas() {
        val series = ScoreProgress.series(tableGame(listOf(listOf(1, 1), listOf(1, 1), listOf(null, null), listOf(null, null))))
        assertEquals(2, series!!.first().size)
    }

    @Test
    fun uneSeuleMancheNeFaitPasUneCourbe() {
        assertNull(ScoreProgress.series(tableGame(listOf(listOf(3, 1), listOf(null, null)))))
    }

    @Test
    fun unTableauVideNeFaitPasUneCourbe() {
        assertNull(ScoreProgress.series(tableGame(listOf(listOf(null, null), listOf(null, null)))))
    }

    @Test
    fun lesCompteursNOntPasDHistorique() {
        val game = SavedGame(
            id = "c", savedAt = 0L,
            gameRules = GameRules(id = "c", name = "C", scoreMode = ScoreMode.COUNTER),
            players = players, playerColorsArgb = emptyList(), counters = listOf(5, 3), isFinished = false
        )
        assertNull(ScoreProgress.series(game))
    }

    @Test
    fun equipesVariablesLesPointsSontCumulesEtOpposesPourLesAutres() {
        val game = SavedGame(
            id = "tarot", savedAt = 0L,
            gameRules = GameRules(id = "tarot", name = "Tarot", scoreMode = ScoreMode.VARIABLE_TEAMS),
            players = players, playerColorsArgb = emptyList(),
            teamRounds = listOf(
                TeamRound("A", setOf(0), 10),
                TeamRound("A", setOf(1), 4)
            ),
            isFinished = false
        )
        assertEquals(listOf(listOf(10, 6), listOf(-10, -6)), ScoreProgress.series(game))
    }

    @Test
    fun lesDeltasExactsDuTarotPrennentLePasSurLaRegleSimple() {
        val game = SavedGame(
            id = "tarot", savedAt = 0L,
            gameRules = GameRules(id = "tarot", name = "Tarot", scoreMode = ScoreMode.VARIABLE_TEAMS),
            players = players, playerColorsArgb = emptyList(),
            teamRounds = listOf(
                TeamRound("A", setOf(0), 10, deltas = listOf(30, -30)),
                TeamRound("A", setOf(0), 10, deltas = listOf(-20, 20))
            ),
            isFinished = false
        )
        assertEquals(listOf(listOf(30, 10), listOf(-30, -10)), ScoreProgress.series(game))
    }

    @Test
    fun lesMultiplicateursEtLeSigneSontAppliques() {
        val rules = GameRules(
            id = "m", name = "M",
            multipliers = listOf(GameRules.NORMAL_MULTIPLIER, ScoreMultiplier("double", "Double", 2))
        )
        val game = tableGame(listOf(listOf(3, 1), listOf(2, 4)), rules).let { g ->
            g.copy(
                cellSnapshots = listOf(
                    listOf(CellSnapshot(3, false, "double"), CellSnapshot(1, true, GameRules.NORMAL_MULTIPLIER_ID)),
                    listOf(CellSnapshot(2, false, GameRules.NORMAL_MULTIPLIER_ID), CellSnapshot(4, false, GameRules.NORMAL_MULTIPLIER_ID))
                )
            )
        }
        assertEquals(listOf(listOf(6, 8), listOf(-1, 3)), ScoreProgress.series(game))
    }
}
