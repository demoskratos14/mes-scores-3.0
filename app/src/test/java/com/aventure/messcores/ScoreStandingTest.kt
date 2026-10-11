package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Classement et fin de partie : fonctions pures de ScoreStanding.kt (sans Android). */
class ScoreStandingTest {

    @Test
    fun lesExAequoPartagentLeMemeRang() {
        assertEquals(listOf(1, 1, 3), computeRanks(listOf(10, 10, 5), lowestWins = false))
        assertEquals(listOf(3, 1, 1), computeRanks(listOf(10, 5, 5), lowestWins = true))
    }

    @Test
    fun auPhase10OnClasseParPhasePuisParScore() {
        // Joueur 1 a la phase la plus avancée : premier, même avec plus de points.
        val ranks = computeRanks(totals = listOf(20, 80, 30), lowestWins = true, phases = listOf(3, 4, 3))
        assertEquals(listOf(2, 1, 3), ranks)
    }

    @Test
    fun leSeuilExigeLEcartMinimal() {
        val condition = EndCondition(
            type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 11, minLead = 2
        )
        assertFalse(thresholdReached(condition, listOf(11, 10)))
        assertTrue(thresholdReached(condition, listOf(12, 10)))
    }

    @Test
    fun leScorePlafondTermineSansEcart() {
        val condition = EndCondition(
            type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 21, minLead = 2, maxScore = 30
        )
        assertTrue(thresholdReached(condition, listOf(30, 29)))
    }

    @Test
    fun uneEgaliteEnTeteEstDetectee() {
        assertTrue(hasTiedLeaders(listOf(5, 9, 9), lowestWins = false))
        assertFalse(hasTiedLeaders(listOf(5, 9, 9), lowestWins = true))
        assertFalse(hasTiedLeaders(emptyList(), lowestWins = false))
    }
}
