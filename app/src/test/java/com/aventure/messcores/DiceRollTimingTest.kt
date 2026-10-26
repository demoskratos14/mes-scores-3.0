package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiceRollTimingTest {

    @Test
    fun lesDesSArretentUnAUnDeGaucheADroite() {
        assertEquals(DiceRollTiming.FIRST_STOP_MS, DiceRollTiming.stopAt(0))
        for (position in 1..5) {
            assertEquals(DiceRollTiming.STOP_STEP_MS, DiceRollTiming.stopAt(position) - DiceRollTiming.stopAt(position - 1))
        }
    }

    @Test
    fun lAnimationDureAssezPourVoirTournerLesDesMaisResteCourte() {
        // Cinq dés : le dernier s'arrête en moins de deux secondes et demie.
        val last = DiceRollTiming.stopAt(4)
        assertTrue(last > DiceRollTiming.FIRST_STOP_MS)
        assertTrue(last < 2_500L)
        // Les faces changent plusieurs fois avant l'arrêt du premier dé.
        assertTrue(DiceRollTiming.FIRST_STOP_MS / DiceRollTiming.FACE_CHANGE_MS >= 5)
    }
}
