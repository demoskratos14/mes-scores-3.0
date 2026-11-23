package com.aventure.messcores

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GamepadSwapTest {

    @Test
    fun sansManetteNintendoLesBoutonsNeChangentPas() {
        // Un événement sans appareil (ou d'une manette qui n'est pas de Nintendo) garde ses boutons : A reste A, B reste B.
        val a = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)
        val b = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, padKeyCode(a))
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, padKeyCode(b))
    }

    @Test
    fun unAppareilInconnuNEstPasUneManetteNintendo() {
        assertFalse(isNintendoPad(null))
    }
}
