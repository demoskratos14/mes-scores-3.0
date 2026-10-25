package com.aventure.messcores.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.aventure.messcores.MesScoresTheme
import com.aventure.messcores.R
import com.aventure.messcores.ScoreApp
import com.aventure.messcores.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Navigation de bout en bout depuis l'écran d'accueil, avec l'application complète. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class AppFlowUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = RuntimeEnvironment.getApplication()

    /** Ouvre l'appli, choisit un côté de l'accueil, saisit deux joueurs puis appuie sur Continuer. */
    private fun openFromHome(homeChoice: Int) {
        composeRule.setContent {
            MesScoresTheme(mode = ThemeMode.SYSTEM) { ScoreApp() }
        }
        composeRule.onNodeWithText(context.getString(homeChoice)).performClick()
        composeRule.waitForIdle()
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[1].performTextInput("Anna")
        fields[2].performTextInput("Ben")
        composeRule.onNodeWithText(context.getString(R.string.common_continue)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun deLAccueilOnArriveAuChoixDuJeuCoteFeuillesDeScores() {
        openFromHome(R.string.home_scores)
        composeRule.onNodeWithText(context.getString(R.string.choose_title)).assertIsDisplayed()
    }

    @Test
    fun deLAccueilOnArriveAuChoixDuJeuCoteJeuxJouables() {
        openFromHome(R.string.home_playable)
        composeRule.onNodeWithText(context.getString(R.string.choose_title)).assertIsDisplayed()
    }
}
