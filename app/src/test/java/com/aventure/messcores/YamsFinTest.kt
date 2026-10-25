package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Une partie de Yams sur un seul téléphone est terminée dès que toutes les cases de tous les joueurs sont remplies. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class YamsFinTest {

    private val yams = GameRules(
        id = "builtin_yams",
        name = "Yams",
        minPlayers = 1,
        maxPlayers = 8,
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.YAMS
    )

    @Test
    fun laPartieEstTermineeQuandToutesLesCasesSontRemplies() {
        val game = ScoreViewModel()
        game.initGame(listOf("Anna", "Ben"), yams)
        val rowCount = game.sheetRows!!.size
        assertEquals(rowCount, game.scores.size)

        for (row in 0 until rowCount) {
            for (player in 0..1) {
                assertFalse("avant la case $row/$player", game.isGameOver())
                // Une case sur deux est barrée (0), les autres valent 5.
                game.setCell(row, player, if ((row + player) % 2 == 0) 0 else 5, false)
            }
        }

        assertTrue(game.isGameOver())
        assertTrue(game.winners().isNotEmpty())
    }

    @Test
    fun uneCaseEffaceeRendLaPartieNonTerminee() {
        val game = ScoreViewModel()
        game.initGame(listOf("Anna"), yams)
        for (row in 0 until game.sheetRows!!.size) game.setCell(row, 0, 10, false)
        assertTrue(game.isGameOver())

        game.undo()

        assertFalse(game.isGameOver())
    }
}
