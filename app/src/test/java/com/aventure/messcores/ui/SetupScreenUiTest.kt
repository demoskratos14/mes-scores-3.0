package com.aventure.messcores.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertCountEquals
import com.aventure.messcores.MesScoresTheme
import com.aventure.messcores.PlayerNames
import com.aventure.messcores.GameRules
import com.aventure.messcores.R
import com.aventure.messcores.SavedGame
import com.aventure.messcores.ScoreMode
import com.aventure.messcores.SetupScreen
import com.aventure.messcores.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Écran d'accueil : saisie des joueurs puis « Continuer ». */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class SetupScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()
    private var received: List<String>? = null

    private var resumed = false

    private fun showSetup(unfinishedGame: SavedGame? = null) {
        composeRule.setContent {
            MesScoresTheme(mode = ThemeMode.SYSTEM) {
                SetupScreen(
                    onNext = { received = it },
                    onOpenJournal = {},
                    onOpenStats = {},
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    onOpenTournament = {},
                    unfinishedGame = unfinishedGame,
                    onResumeGame = { resumed = true }
                )
            }
        }
    }

    private fun clickContinue() {
        composeRule.onNodeWithText(context.getString(R.string.common_continue)).performScrollTo().performClick()
    }

    @Test
    fun lesNomsSaisisSontTransmisAuClicSurContinuer() {
        showSetup()
        // Champs modifiables : d'abord le nombre de joueurs, puis un champ par joueur.
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[1].performTextInput("Anna")
        fields[2].performTextInput("Ben")
        clickContinue()
        assertEquals(listOf("Anna", "Ben"), received)
    }

    @Test
    fun lesNomsVidesPrennentLeNomParDefaut() {
        showSetup()
        clickContinue()
        val prefix = context.getString(R.string.player_default)
        assertEquals(PlayerNames.resolve(listOf("", ""), prefix), received)
    }

    @Test
    fun changerLeNombreDeJoueursAjouteUnChampParJoueur() {
        showSetup()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(3) // nombre + 2 joueurs
        composeRule.onAllNodes(hasSetTextAction())[0].performTextReplacement("4")
        composeRule.waitForIdle()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(5) // nombre + 4 joueurs
    }

    private fun unfinished() = SavedGame(
        id = "g1",
        savedAt = 1L,
        gameRules = GameRules(id = "c", name = "Ping-pong", scoreMode = ScoreMode.COUNTER),
        players = listOf("Anna", "Ben"),
        playerColorsArgb = emptyList(),
        counters = listOf(3, 1),
        isFinished = false
    )

    @Test
    fun laPartieEnCoursEstProposeeEtSeReprendEnUnAppui() {
        showSetup(unfinished())
        composeRule.onNodeWithText(context.getString(R.string.resume_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.resume_summary, "Ping-pong", "Anna, Ben")).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_resume)).performClick()
        assertTrue(resumed)
    }

    @Test
    fun ignorerCacheLaPartieEnCours() {
        showSetup(unfinished())
        composeRule.onNodeWithText(context.getString(R.string.resume_dismiss)).performClick()
        composeRule.onAllNodesWithText(context.getString(R.string.resume_title)).assertCountEquals(0)
    }

    @Test
    fun sansPartieEnCoursAucuneProposition() {
        showSetup(null)
        composeRule.onAllNodesWithText(context.getString(R.string.resume_title)).assertCountEquals(0)
    }

    @Test
    fun rienNEstTransmisSansClicSurContinuer() {
        showSetup()
        assertNull(received)
    }
}
