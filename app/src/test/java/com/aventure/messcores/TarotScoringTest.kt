package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TarotScoringTest {

    /** Raccourci : tous les paramètres ont une valeur neutre sauf ceux qu'on précise. */
    private fun round(
        playerCount: Int = 4,
        taker: Int? = 0,
        partner: Int? = null,
        factor: Int = 1,
        bouts: Int = 1,
        points: Int = 51,
        petitAuBout: Int = 0,
        handful: Int = 0,
        slamAnnounced: Boolean = false,
        defenseSlam: Boolean = false
    ) = computeTarotRound(
        playerCount, taker, partner, factor, bouts, points, petitAuBout, handful, slamAnnounced, defenseSlam
    )

    @Test
    fun pointsRequisSelonLesBouts() {
        assertEquals(56, tarotRequiredPoints(0))
        assertEquals(51, tarotRequiredPoints(1))
        assertEquals(41, tarotRequiredPoints(2))
        assertEquals(36, tarotRequiredPoints(3))
    }

    @Test
    fun contratRempliJusteALaLimite() {
        val r = round(bouts = 1, points = 51) // 51 requis, écart 0
        assertTrue(r.success)
        assertEquals(0, r.difference)
        assertEquals(25, r.unit)
        // 4 joueurs, preneur seul : il reçoit 3 × 25, chaque défenseur perd 25.
        assertEquals(listOf(75, -25, -25, -25), r.deltas)
    }

    @Test
    fun contratRempliAvecEcartEtCoefficient() {
        // 3 joueurs, garde ×2, 2 bouts (41 requis), 50 points : (25 + 9) × 2 = 68.
        val r = round(playerCount = 3, factor = 2, bouts = 2, points = 50)
        assertEquals(68, r.unit)
        assertEquals(listOf(136, -68, -68), r.deltas)
    }

    @Test
    fun contratChute() {
        // 4 joueurs, garde ×2, 0 bout (56 requis), 50 points : −(25 + 6) × 2 = −62.
        val r = round(factor = 2, bouts = 0, points = 50)
        assertFalse(r.success)
        assertEquals(-6, r.difference)
        assertEquals(-62, r.unit)
        assertEquals(listOf(-186, 62, 62, 62), r.deltas)
    }

    @Test
    fun gardeSansACinqJoueursPreneurSeul() {
        // Garde sans ×4, 3 bouts (36 requis), 40 points : (25 + 4) × 4 = 116.
        val r = round(playerCount = 5, factor = 4, bouts = 3, points = 40)
        assertEquals(116, r.unit)
        assertEquals(listOf(464, -116, -116, -116, -116), r.deltas)
    }

    @Test
    fun aCinqJoueursAvecJoueurAppele() {
        // Preneur 0, appelé 2, garde ×2, 2 bouts, 41 points : unité 50.
        val r = round(playerCount = 5, partner = 2, factor = 2, bouts = 2, points = 41)
        assertEquals(50, r.unit)
        assertEquals(listOf(100, -50, 50, -50, -50), r.deltas)
    }

    @Test
    fun petitAuBoutEtPoigneeSAjoutentAuContrat() {
        // Garde sans ×4, 1 bout, 51 points : 100 ; petit au bout du preneur +10 × 4 ; poignée double +30.
        val r = round(factor = 4, points = 51, petitAuBout = 1, handful = 2)
        assertEquals(100 + 40 + 30, r.unit)
    }

    @Test
    fun petitAuBoutDeLaDefenseEtPoigneeSInversentQuandLeContratChute() {
        // Garde ×2, 0 bout, 50 points : −62 ; petit au bout de la défense −20 ; poignée simple −20.
        val r = round(factor = 2, bouts = 0, points = 50, petitAuBout = 2, handful = 1)
        assertEquals(-62 - 20 - 20, r.unit)
    }

    @Test
    fun chelemAnnonceReussi() {
        val r = round(points = 91, slamAnnounced = true) // contrat 25 + 40, chelem +400
        assertTrue(r.slamSucceeded)
        assertEquals(65 + 400, r.unit)
    }

    @Test
    fun chelemNonAnnonceReussi() {
        val r = round(points = 91)
        assertTrue(r.slamSucceeded)
        assertEquals(65 + 200, r.unit)
    }

    @Test
    fun chelemAnnonceMaisRate() {
        val r = round(bouts = 0, points = 60, slamAnnounced = true) // contrat 25 + 4, chelem −200
        assertFalse(r.slamSucceeded)
        assertEquals(29 - 200, r.unit)
    }

    @Test
    fun chelemDeLaDefenseSeulementSiLeContratChute() {
        val chute = round(bouts = 0, points = 0, defenseSlam = true) // −(25 + 56) puis −200
        assertTrue(chute.defenseSlamActive)
        assertEquals(-81 - DEFENSE_SLAM_PRIZE, chute.unit)

        val rempli = round(bouts = 0, points = 60, defenseSlam = true) // ignoré : le contrat est rempli
        assertFalse(rempli.defenseSlamActive)
        assertEquals(29, rempli.unit)
    }

    @Test
    fun sansPreneurPasDeRepartition() {
        val r = round(taker = null)
        assertNull(r.deltas)
        assertEquals(25, r.unit)
    }

    @Test
    fun laSommeDesScoresEstToujoursNulle() {
        for (playerCount in 3..5) {
            for (taker in 0 until playerCount) {
                val partners: List<Int?> =
                    if (playerCount == 5) listOf<Int?>(null) + (0 until 5).filter { it != taker } else listOf(null)
                for (partner in partners) {
                    for (factor in listOf(1, 2, 4, 6)) {
                        for (bouts in 0..3) {
                            for (points in listOf(0, 20, 36, 41, 51, 56, 60, 90, 91)) {
                                for (petit in 0..2) {
                                    for (handful in 0..3) {
                                        for (slam in listOf(false, true)) {
                                            for (defense in listOf(false, true)) {
                                                val deltas = round(
                                                    playerCount, taker, partner, factor, bouts, points,
                                                    petit, handful, slam, defense
                                                ).deltas
                                                assertNotNull(deltas)
                                                assertEquals(playerCount, deltas!!.size)
                                                assertEquals(0, deltas.sum())
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
