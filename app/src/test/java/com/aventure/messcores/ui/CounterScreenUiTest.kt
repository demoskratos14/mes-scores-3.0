package com.aventure.messcores.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.aventure.messcores.CounterScreen
import com.aventure.messcores.GameHistoryRepository
import com.aventure.messcores.GameRepository
import com.aventure.messcores.MesScoresTheme
import com.aventure.messcores.R
import com.aventure.messcores.ScoreViewModel
import com.aventure.messcores.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Écran de compteurs (ping-pong, badminton…) : +1, annuler, enregistrer au journal. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class CounterScreenUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var viewModel: ScoreViewModel
    private lateinit var history: GameHistoryRepository

    private fun showCounter() {
        val rules = GameRepository(context).builtInGames().first { it.id == "builtin_pingpong" }
        viewModel = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), rules) }
        history = GameHistoryRepository(context)
        composeRule.setContent {
            MesScoresTheme(mode = ThemeMode.SYSTEM) {
                CounterScreen(viewModel = viewModel, historyRepository = history)
            }
        }
    }

    private fun addPoint(playerIndex: Int) {
        composeRule.onAllNodesWithContentDescription(context.getString(R.string.counter_add))[playerIndex].performClick()
    }

    @Test
    fun lePlusUnNeChangeQueLeJoueurConcerne() {
        showCounter()
        addPoint(0)
        addPoint(0)
        addPoint(1)
        assertEquals(listOf(2, 1), viewModel.totals)
    }

    @Test
    fun lePointMarqueApparaitALEcran() {
        showCounter()
        addPoint(1)
        composeRule.onNodeWithText("1").assertIsDisplayed()
    }

    @Test
    fun annulerRetireLaDerniereSaisieEtSeGriseEnSuite() {
        showCounter()
        val undo = hasText(context.getString(R.string.common_cancel), substring = true) and hasClickAction()
        composeRule.onNode(undo).assertIsNotEnabled() // rien à annuler au départ
        addPoint(0)
        composeRule.onNode(undo).assertIsEnabled()
        composeRule.onNode(undo).performClick()
        assertEquals(listOf(0, 0), viewModel.totals)
        composeRule.onNode(undo).assertIsNotEnabled()
    }

    @Test
    fun enregistrerAjouteLaPartieAuJournal() {
        showCounter()
        addPoint(0)
        assertEquals(0, history.listGames().size)
        composeRule.onNodeWithText(context.getString(R.string.actions_save)).performClick()
        assertEquals(1, history.listGames().size)
        composeRule.onNodeWithText(context.getString(R.string.actions_saved)).assertIsDisplayed()
    }
}
