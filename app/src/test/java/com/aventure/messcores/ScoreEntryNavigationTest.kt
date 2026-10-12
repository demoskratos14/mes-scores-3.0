package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Navigation entre les joueurs dans la fenêtre de saisie : on saute ceux dont le score est déjà inscrit. */
class ScoreEntryNavigationTest {

    @Test
    fun avanceAuJoueurSuivantVide() {
        assertEquals(1, nextPendingPlayer(listOf(5, null, null), from = 0, step = 1))
    }

    @Test
    fun sauteLesJoueursDejaSaisis() {
        assertEquals(3, nextPendingPlayer(listOf(null, 4, 7, null), from = 0, step = 1))
    }

    @Test
    fun faitLeTourDeLaTable() {
        assertEquals(0, nextPendingPlayer(listOf(null, 4, 7), from = 2, step = 1))
        assertEquals(2, nextPendingPlayer(listOf(null, 4, null), from = 0, step = -1))
    }

    @Test
    fun reculeEnSautantLesJoueursSaisis() {
        assertEquals(0, nextPendingPlayer(listOf(null, 3, 8, null), from = 3, step = -1))
    }

    @Test
    fun neRenvoieJamaisLeJoueurCourant() {
        assertNull(nextPendingPlayer(listOf(null, 1, 2), from = 0, step = 1))
        assertNull(nextPendingPlayer(listOf(null), from = 0, step = 1))
    }

    @Test
    fun aucunJoueurRestantDonneNull() {
        assertNull(nextPendingPlayer(listOf(1, 2, 3), from = 1, step = 1))
        assertNull(nextPendingPlayer(emptyList(), from = 0, step = 1))
    }

    @Test
    fun depuisUneCaseDejaRemplieOnVaAuPremierVide() {
        assertEquals(2, nextPendingPlayer(listOf(1, 2, null, 4), from = 1, step = 1))
        assertEquals(2, nextPendingPlayer(listOf(1, 2, null, 4), from = 3, step = -1))
    }
}
