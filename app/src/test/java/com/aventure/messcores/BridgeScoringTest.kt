package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeScoringTest {

    private fun score(level: Int, suit: Int, tricks: Int, doubling: Int = 0, vulnerable: Boolean = false) =
        computeBridgeContract(level, suit, doubling, vulnerable, tricks).score

    @Test
    fun plisNecessairesSelonLeNiveau() {
        assertEquals(7, bridgeTricksNeeded(1))
        assertEquals(13, bridgeTricksNeeded(7))
    }

    @Test
    fun partielleEtManche() {
        // 1 trèfle fait : 20 + 50 de partielle.
        assertEquals(70, score(1, BridgeSuit.CLUBS, 7))
        // 2 cœurs fait : 60 + 50.
        assertEquals(110, score(2, BridgeSuit.HEARTS, 8))
        // 3 SA fait : 100 + 300 de manche (non vulnérable), 100 + 500 vulnérable.
        assertEquals(400, score(3, BridgeSuit.NO_TRUMP, 9))
        assertEquals(600, score(3, BridgeSuit.NO_TRUMP, 9, vulnerable = true))
        // 4 piques fait : 120 + 300.
        assertEquals(420, score(4, BridgeSuit.SPADES, 10))
        // 5 carreaux fait : 100 + 300.
        assertEquals(400, score(5, BridgeSuit.DIAMONDS, 11))
    }

    @Test
    fun pliEnPlus() {
        // 3 SA +1 : 100 + 30 + 300.
        assertEquals(430, score(3, BridgeSuit.NO_TRUMP, 10))
        // 1 trèfle +2 : 20 + 2 × 20 + 50.
        assertEquals(110, score(1, BridgeSuit.CLUBS, 9))
    }

    @Test
    fun chelems() {
        // 6 piques : 180 + 300 + 500.
        assertEquals(980, score(6, BridgeSuit.SPADES, 12))
        // 6 piques vulnérable : 180 + 500 + 750.
        assertEquals(1430, score(6, BridgeSuit.SPADES, 12, vulnerable = true))
        // 7 SA vulnérable : 220 + 500 + 1500.
        assertEquals(2220, score(7, BridgeSuit.NO_TRUMP, 13, vulnerable = true))
    }

    @Test
    fun contratContreEtSurcontre() {
        // 4 piques contrés faits : 240 + 300 + 50.
        assertEquals(590, score(4, BridgeSuit.SPADES, 10, doubling = 1))
        // 2 piques contrés fait : 120 (≥ 100 donc manche) + 300 + 50.
        assertEquals(470, score(2, BridgeSuit.SPADES, 8, doubling = 1))
        // 1 pique surcontré +1 vulnérable : 120 + 400 + 100 + 500 de manche.
        assertEquals(1120, score(1, BridgeSuit.SPADES, 8, doubling = 2, vulnerable = true))
    }

    @Test
    fun chuteNonContree() {
        val result = computeBridgeContract(4, BridgeSuit.SPADES, 0, false, 8)
        assertFalse(result.made)
        assertEquals(-2, result.difference)
        assertEquals(100, result.penalty)
        assertEquals(-100, result.score)
        // Vulnérable : 100 le pli.
        assertEquals(-200, score(4, BridgeSuit.SPADES, 8, vulnerable = true))
    }

    @Test
    fun chuteContreeNonVulnerable() {
        assertEquals(-100, score(4, BridgeSuit.SPADES, 9, doubling = 1))
        assertEquals(-300, score(4, BridgeSuit.SPADES, 8, doubling = 1))
        assertEquals(-500, score(4, BridgeSuit.SPADES, 7, doubling = 1))
        assertEquals(-800, score(4, BridgeSuit.SPADES, 6, doubling = 1))
        assertEquals(-1000, score(4, BridgeSuit.SPADES, 7, doubling = 2))
    }

    @Test
    fun chuteContreeVulnerable() {
        assertEquals(-200, score(4, BridgeSuit.SPADES, 9, doubling = 1, vulnerable = true))
        assertEquals(-500, score(4, BridgeSuit.SPADES, 8, doubling = 1, vulnerable = true))
        assertEquals(-800, score(4, BridgeSuit.SPADES, 7, doubling = 1, vulnerable = true))
    }

    @Test
    fun lesPointsRevientAuBonCamp() {
        val made = computeBridgeContract(3, BridgeSuit.NO_TRUMP, 0, false, 9)
        assertEquals(listOf(400, 0), bridgeDeltas(2, 0, made))
        assertEquals(listOf(0, 400), bridgeDeltas(2, 1, made))
        val down = computeBridgeContract(4, BridgeSuit.SPADES, 0, false, 8)
        // Le contrat chute : ce sont les défenseurs qui marquent la pénalité.
        assertEquals(listOf(0, 100), bridgeDeltas(2, 0, down))
        assertEquals(listOf(100, 0), bridgeDeltas(2, 1, down))
    }

    @Test
    fun laSaisieSeRelitSansPerte() {
        val input = BridgeInput(declarer = 1, level = 4, suit = BridgeSuit.SPADES, doubling = 1, vulnerable = true, tricks = 11)
        assertEquals(input, BridgeInput.decode(input.encode()))
        assertNull(BridgeInput.decode(listOf(1, 2)))
        assertTrue(input.encode().size == 6)
    }
}
