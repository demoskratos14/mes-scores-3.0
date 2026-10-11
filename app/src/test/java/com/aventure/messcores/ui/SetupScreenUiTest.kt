package com.aventure.messcores.ui

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertCountEquals
import com.aventure.messcores.MesScoresTheme
import com.aventure.messcores.PlayerNames
import com.aventure.messcores.R
import com.aventure.messcores.SetupScreen
import com.aventure.messcores.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun showSetup() {
        composeRule.setContent {
            MesScoresTheme(mode = ThemeMode.SYSTEM) {
                SetupScreen(
                    onNext = { received = it },
                    onOpenJournal = {},
                    onOpenStats = {},
                    themeMode = ThemeMode.SYSTEM,
                    onThemeModeChange = {},
                    onOpenTournament = {}
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

    @Test
    fun rienNEstTransmisSansClicSurContinuer() {
        showSetup()
        assertNull(received)
    }
}
