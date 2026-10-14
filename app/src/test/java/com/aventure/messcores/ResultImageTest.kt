package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Contenu de l'image du classement : même ordre et mêmes textes que le classement partagé en texte. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class ResultImageTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun lesLignesSontRangeesDuPremierAuDernierAvecLesExAequo() {
        val content = ResultImage.content(
            context, "Skyjo", listOf("Anna", "Ben", "Chloé", "Dan"),
            totals = listOf(52, 45, 45, 70), ranks = listOf(3, 1, 1, 4), finished = true, winners = listOf(1, 2)
        )
        assertEquals("Skyjo — partie terminée", content.title)
        assertEquals(
            listOf(ResultImage.Row(1, "Ben", 45), ResultImage.Row(1, "Chloé", 45), ResultImage.Row(3, "Anna", 52), ResultImage.Row(4, "Dan", 70)),
            content.rows
        )
        assertEquals("Vainqueurs : Ben et Chloé", content.winnerLine)
        assertEquals("— Mes Scores", content.footer)
    }

    @Test
    fun partieEnCoursSansVainqueur() {
        val content = ResultImage.content(context, "Uno", listOf("A", "B"), listOf(10, 5), listOf(2, 1), false, emptyList())
        assertEquals("Uno — classement en cours", content.title)
        assertNull(content.winnerLine)
        assertEquals("B", content.rows.first().name)
    }
}
