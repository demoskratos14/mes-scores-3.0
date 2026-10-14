package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkatScoringTest {

    private fun round(
        game: Int,
        matadors: Int = 1,
        outcome: Int = SkatOutcome.WON,
        hand: Boolean = false,
        schneiderAnnounced: Boolean = false,
        schwarzAnnounced: Boolean = false,
        open: Boolean = false,
        bid: Int = 0
    ) = computeSkatRound(game, matadors, hand, schneiderAnnounced, schwarzAnnounced, open, outcome, bid)

    @Test
    fun jeuALAtoutGagne() {
        // Trèfle avec 1 : (1 + 1) × 12 = 24, marque 50 + 24.
        val result = round(SkatGame.CLUBS)
        assertEquals(24, result.gameValue)
        assertEquals(74, result.declarerScore)
        assertEquals(0, result.defenderScore)
        assertTrue(result.won)
        // Carreau, 4 matadors : (1 + 4) × 9 = 45.
        assertEquals(45, round(SkatGame.DIAMONDS, matadors = 4).gameValue)
        assertEquals(60, round(SkatGame.SPADES, matadors = 4).gameValue)
        assertEquals(50, round(SkatGame.HEARTS, matadors = 4).gameValue)
    }

    @Test
    fun grandAvecSchneiderEtHand() {
        // Grand avec 2, Hand, gagné Schneider : 1 + 2 + 1 + 1 = 5 × 24 = 120.
        val result = round(SkatGame.GRAND, matadors = 2, outcome = SkatOutcome.WON_SCHNEIDER, hand = true)
        assertEquals(5, result.multiplier)
        assertEquals(120, result.gameValue)
        assertEquals(170, result.declarerScore)
    }

    @Test
    fun jeuPerduVautLeDoubleEtLesAdversairesMarquent() {
        // Grand avec 1 perdu : 2 × 24 = 48 ; −(50 + 96) ; chaque autre joueur +30.
        val result = round(SkatGame.GRAND, outcome = SkatOutcome.LOST)
        assertFalse(result.won)
        assertEquals(-146, result.declarerScore)
        assertEquals(30, result.defenderScore)
    }

    @Test
    fun perduSchneiderCompteDansLaValeur() {
        // Pique avec 1 perdu Schneider : (1 + 1 + 1) × 11 = 33 ; −(50 + 66).
        val result = round(SkatGame.SPADES, outcome = SkatOutcome.LOST_SCHNEIDER)
        assertEquals(33, result.gameValue)
        assertEquals(-116, result.declarerScore)
    }

    @Test
    fun schwarzCompteSchneiderEtSchwarz() {
        // Trèfle avec 1, gagné Schwarz : 1 + 1 + 1 + 1 = 4 × 12 = 48.
        assertEquals(48, round(SkatGame.CLUBS, outcome = SkatOutcome.WON_SCHWARZ).gameValue)
    }

    @Test
    fun ouvertImpliqueHandEtLesAnnonces() {
        // Trèfle avec 1 ouvert gagné Schwarz : 1 + 1 + Hand + Ouvert + Schneider ann. + Schwarz ann. + Schneider + Schwarz = 8 × 12.
        val result = round(SkatGame.CLUBS, outcome = SkatOutcome.WON_SCHWARZ, open = true)
        assertEquals(8, result.multiplier)
        assertEquals(96, result.gameValue)
        assertTrue(result.won)
    }

    @Test
    fun uneAnnonceNonTenueFaitPerdre() {
        val result = round(SkatGame.CLUBS, outcome = SkatOutcome.WON, hand = true, schneiderAnnounced = true)
        assertTrue(result.announceFailed)
        assertFalse(result.won)
        // (1 + 1 + Hand + Schneider annoncé) × 12 = 48 ; −(50 + 96).
        assertEquals(-146, result.declarerScore)
    }

    @Test
    fun uneEnchereTropHautePerdLeJeu() {
        // Carreau avec 1 : 18 ; enchère 23 → perdu, valeur = plus petit multiple de 9 ≥ 23 = 27.
        val result = round(SkatGame.DIAMONDS, bid = 23)
        assertTrue(result.overbid)
        assertFalse(result.won)
        assertEquals(27, result.gameValue)
        assertEquals(-104, result.declarerScore)
        // Une enchère égale à la valeur est tenue.
        assertTrue(round(SkatGame.DIAMONDS, bid = 18).won)
    }

    @Test
    fun valeursDuNull() {
        assertEquals(23, skatNullValue(hand = false, open = false))
        assertEquals(35, skatNullValue(hand = true, open = false))
        assertEquals(46, skatNullValue(hand = false, open = true))
        assertEquals(59, skatNullValue(hand = true, open = true))
        val won = round(SkatGame.NULL)
        assertEquals(73, won.declarerScore)
        val lost = round(SkatGame.NULL, outcome = SkatOutcome.LOST, hand = true, open = true)
        assertEquals(-168, lost.declarerScore)
        assertEquals(30, lost.defenderScore)
    }

    @Test
    fun lesPointsSontRepartisEntreLesJoueurs() {
        val won = round(SkatGame.CLUBS)
        assertEquals(listOf(74, 0, 0), skatDeltas(3, 0, won))
        val lost = round(SkatGame.CLUBS, outcome = SkatOutcome.LOST)
        // À 4 joueurs, tous les autres reçoivent les 30 points.
        assertEquals(listOf(30, 30, -(50 + 48), 30), skatDeltas(4, 2, lost))
    }

    @Test
    fun laSaisieSeRelitSansPerte() {
        val input = SkatInput(1, SkatGame.GRAND, 3, hand = true, schneiderAnnounced = true,
            schwarzAnnounced = false, open = false, outcome = SkatOutcome.WON_SCHNEIDER, bid = 72)
        assertEquals(input, SkatInput.decode(input.encode()))
        assertNull(SkatInput.decode(listOf(1, 2, 3)))
        assertEquals(4, skatMaxMatadors(SkatGame.GRAND))
        assertEquals(11, skatMaxMatadors(SkatGame.CLUBS))
    }
}
