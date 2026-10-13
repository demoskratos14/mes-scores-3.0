package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownInputTest {

    @Test
    fun unNombreSeulEstEnMinutes() {
        assertEquals(5 * 60, CountdownInput.parseSeconds("5"))
        assertEquals(240 * 60, CountdownInput.parseSeconds("240"))
    }

    @Test
    fun minutesEtSecondes() {
        assertEquals(4 * 60 + 30, CountdownInput.parseSeconds("04:30"))
        assertEquals(4 * 60 + 30, CountdownInput.parseSeconds("4:30"))
        assertEquals(30, CountdownInput.parseSeconds(":30"))
        assertEquals(4 * 60, CountdownInput.parseSeconds("4:"))
    }

    @Test
    fun heuresMinutesEtSecondes() {
        assertEquals(2 * 3600 + 5 * 60 + 30, CountdownInput.parseSeconds("2:05:30"))
        assertEquals(3600, CountdownInput.parseSeconds("1:00:00"))
        assertEquals(2 * 3600 + 30, CountdownInput.parseSeconds("2::30"))
    }

    @Test
    fun pointEtVirgulePassentPourDeuxPoints() {
        assertEquals(4 * 60 + 30, CountdownInput.parseSeconds("4.30"))
        assertEquals(4 * 60 + 30, CountdownInput.parseSeconds("4,30"))
        assertEquals(2 * 3600 + 5 * 60 + 30, CountdownInput.parseSeconds("2.05.30"))
        assertEquals("4:30", CountdownInput.sanitize("4.30"))
    }

    @Test
    fun plusDe180MinutesEstPossible() {
        assertEquals(181 * 60, CountdownInput.parseSeconds("181"))
        assertEquals(99 * 3600 + 59 * 60 + 59, CountdownInput.parseSeconds("99:59:59"))
    }

    @Test
    fun laDureeEstBornee() {
        assertEquals(CountdownInput.MAX_SECONDS, CountdownInput.parseSeconds("6000"))
        assertEquals(CountdownInput.MAX_SECONDS, CountdownInput.parseSeconds("99:99:99"))
        assertEquals(1, CountdownInput.parseSeconds("0"))
        assertEquals(1, CountdownInput.parseSeconds("0:00"))
        assertEquals(1, CountdownInput.parseSeconds("0:00:00"))
    }

    @Test
    fun saisieVideOuInvalide() {
        assertNull(CountdownInput.parseSeconds(""))
        assertNull(CountdownInput.parseSeconds(":"))
        assertNull(CountdownInput.parseSeconds("::"))
        assertNull(CountdownInput.parseSeconds("abc"))
    }

    @Test
    fun deuxSeparateursEtHuitCaracteresAuPlus() {
        // Le 3e « : » est ignoré, les chiffres qui suivent sont conservés.
        assertEquals("4:3:05", CountdownInput.sanitize("4:3:0:5"))
        assertEquals("12:34:56", CountdownInput.sanitize("12:34:56789"))
        assertEquals("1230", CountdownInput.sanitize("1a2b3c0"))
    }

    @Test
    fun affichageSousUneHeure() {
        assertEquals("00:00", formatClock(0))
        assertEquals("04:30", formatClock(270))
        assertEquals("59:59", formatClock(3599))
    }

    @Test
    fun affichageAPartirDUneHeure() {
        assertEquals("1:00:00", formatClock(3600))
        assertEquals("2:05:30", formatClock(2 * 3600 + 5 * 60 + 30))
        assertEquals("12:00:01", formatClock(12 * 3600 + 1))
    }

    @Test
    fun affichageNeDescendJamaisSousZero() {
        assertEquals("00:00", formatClock(-5))
    }
}
