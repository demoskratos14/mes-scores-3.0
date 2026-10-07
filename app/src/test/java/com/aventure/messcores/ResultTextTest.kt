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
// Les textes sont en français : on fixe la langue pour que les pluriels suivent les règles françaises (0 → singulier).
@Config(qualifiers = "fr")
class ResultTextTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun partieTermineeAvecMedaillesEtVainqueur() {
        val text = ResultText.game(context, 
            gameName = "Skyjo",
            players = listOf("Anna", "Ben", "Chloé", "Dan"),
            totals = listOf(52, 45, 60, 70),
            ranks = listOf(2, 1, 3, 4),
            finished = true,
            winners = listOf(1)
        )
        val lines = text.lines()
        assertEquals("Skyjo — partie terminée", lines[0])
        assertEquals("🥇 Ben : 45", lines[1])
        assertEquals("🥈 Anna : 52", lines[2])
        assertEquals("🥉 Chloé : 60", lines[3])
        assertEquals("4. Dan : 70", lines[4])
        assertTrue(text.contains("Vainqueur : Ben"))
    }

    @Test
    fun lesExAequoPartagentLaMedailleEtLesVainqueursSontListes() {
        val text = ResultText.game(context, "Uno", listOf("A", "B", "C"), listOf(10, 10, 5), listOf(1, 1, 3), true, listOf(0, 1))
        assertTrue(text.contains("🥇 A : 10"))
        assertTrue(text.contains("🥇 B : 10"))
        assertTrue(text.contains("🥉 C : 5"))
        assertTrue(text.contains("Vainqueurs : A et B"))
    }

    @Test
    fun partieEnCoursSansVainqueur() {
        val text = ResultText.game(context, "Uno", listOf("A", "B"), listOf(3, 4), listOf(2, 1), false, emptyList())
        assertTrue(text.startsWith("Uno — classement en cours"))
        assertFalse(text.contains("Vainqueur"))
    }

    @Test
    fun classementDeChampionnat() {
        val text = ResultText.tournament(context, listOf("A", "B", "C", "D"), listOf(2, 0, 3, 1))
        val lines = text.lines()
        assertEquals("Championnat — classement final", lines[0])
        assertEquals("🥇 C", lines[1])
        assertEquals("🥈 A", lines[2])
        assertEquals("🥉 D", lines[3])
        assertEquals("4. B", lines[4])
    }
}
