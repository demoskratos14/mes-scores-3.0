package com.aventure.messcores

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Fin de partie avec écart minimum (ping-pong, badminton, volley) : le seuil ne suffit pas. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class MinLeadTest {

    private fun builtIn(id: String) =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == id }

    private fun counterGame(rules: GameRules, vararg names: String) =
        ScoreViewModel().also { it.initGame(names.toList(), rules) }

    private fun ScoreViewModel.points(player: Int, n: Int) = repeat(n) { incrementCounter(player, 1) }

    @Test
    fun lePingPongNeSArretePasA11SiLEcartEstInferieurA2() {
        val vm = counterGame(builtIn("builtin_pingpong"), "Anna", "Ben")
        vm.points(1, 10)
        vm.points(0, 10)
        assertFalse(vm.isGameOver())
        vm.points(0, 1) // 11-10 : seuil atteint mais un seul point d'écart
        assertFalse(vm.isGameOver())
        vm.points(1, 1) // 11-11
        assertFalse(vm.isGameOver())
        vm.points(1, 1) // 11-12
        assertFalse(vm.isGameOver())
        vm.points(1, 1) // 11-13 : écart de 2
        assertTrue(vm.isGameOver())
        assertEquals(listOf(1), vm.winners())
    }

    @Test
    fun lePingPongSArreteDirectementA11AvecUnEcartSuffisant() {
        val vm = counterGame(builtIn("builtin_pingpong"), "Anna", "Ben")
        vm.points(1, 9)
        vm.points(0, 11)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun lesPointsSontRefusesUneFoisLaPartieTerminee() {
        val vm = counterGame(builtIn("builtin_pingpong"), "Anna", "Ben")
        vm.points(0, 11)
        vm.points(1, 3)
        assertEquals(0, vm.totalFor(1))
    }

    @Test
    fun badmintonEtVolleyExigentAussiDeuxPointsDEcart() {
        val badminton = counterGame(builtIn("builtin_badminton"), "Anna", "Ben")
        badminton.points(1, 20)
        badminton.points(0, 21)
        assertFalse(badminton.isGameOver())
        badminton.points(0, 1) // 22-20
        assertTrue(badminton.isGameOver())

        val volley = counterGame(builtIn("builtin_volley"), "Anna", "Ben")
        volley.points(1, 24)
        volley.points(0, 25)
        assertFalse(volley.isGameOver())
        volley.points(0, 1) // 26-24
        assertTrue(volley.isGameOver())
        assertEquals(2, listOf("builtin_badminton", "builtin_volley").count { builtIn(it).endCondition.minLead == 2 })
    }

    @Test
    fun sansEcartMinimumLeSeuilSuffitToujours() {
        val rules = GameRules(
            id = "x", name = "Seuil", scoreMode = ScoreMode.COUNTER,
            endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 5)
        )
        val vm = counterGame(rules, "Anna", "Ben")
        vm.points(1, 4)
        vm.points(0, 5)
        assertTrue(vm.isGameOver()) // 5-4 : fin dès le seuil, comme avant
    }

    @Test
    fun lEcartEstExigeSurChaqueAdversaire() {
        val rules = GameRules(
            id = "x", name = "Trois", scoreMode = ScoreMode.COUNTER,
            endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 5, minLead = 2)
        )
        val vm = counterGame(rules, "Anna", "Ben", "Chloé")
        vm.points(1, 4)
        vm.points(2, 1)
        vm.points(0, 5) // 5 contre 4 et 1 : l'écart sur Ben n'est que de 1
        assertFalse(vm.isGameOver())
        vm.points(0, 1) // 6 contre 4
        assertTrue(vm.isGameOver())
    }

    @Test
    fun leSensEnDessousDuSeuilAppliqueLEcartAuPlusBas() {
        val rules = GameRules(
            id = "x", name = "Bas", scoreMode = ScoreMode.COUNTER, allowNegativeScores = true, lowestWins = true,
            endCondition = EndCondition(
                type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 0,
                thresholdDirection = ThresholdDirection.BELOW, minLead = 2
            )
        )
        val vm = counterGame(rules, "Anna", "Ben")
        vm.incrementCounter(1, 1)
        assertFalse(vm.isGameOver())
        vm.incrementCounter(1, 1) // Ben à 2 : écart de 2
        assertTrue(vm.isGameOver())
    }

    @Test
    fun lEcartSurvitAuAllerRetourJsonEtUnAncienJeuSansEcartSeLit() {
        val rules = builtIn("builtin_pingpong")
        assertEquals(2, GameRules.fromJson(JSONObject(rules.toJson().toString())).endCondition.minLead)
        val old = JSONObject(rules.toJson().toString())
        old.getJSONObject("endCondition").remove("minLead")
        assertEquals(0, GameRules.fromJson(old).endCondition.minLead)
    }

    @Test
    fun badmintonSArreteA30MemeSansEcartDeDeuxPoints() {
        val badminton = counterGame(builtIn("builtin_badminton"), "Anna", "Ben")
        assertEquals(30, builtIn("builtin_badminton").endCondition.maxScore)
        badminton.points(1, 29)
        badminton.points(0, 29)
        assertFalse(badminton.isGameOver()) // 29-29 : le point suivant décide
        badminton.points(0, 1) // 30-29
        assertTrue(badminton.isGameOver())
        assertEquals(listOf(0), badminton.winners())
    }

    @Test
    fun leTopeNeChangeRienAvantDeLAtteindre() {
        val badminton = counterGame(builtIn("builtin_badminton"), "Anna", "Ben")
        badminton.points(1, 24)
        badminton.points(0, 25)
        assertFalse(badminton.isGameOver()) // 25-24 : ni écart de 2, ni plafond
        badminton.points(0, 1) // 26-24
        assertTrue(badminton.isGameOver())
    }

    @Test
    fun lePlafondSeLitEtSEcritEnJson() {
        val rules = builtIn("builtin_badminton")
        assertEquals(30, GameRules.fromJson(JSONObject(rules.toJson().toString())).endCondition.maxScore)
        val old = JSONObject(rules.toJson().toString())
        old.getJSONObject("endCondition").remove("maxScore")
        assertEquals(null, GameRules.fromJson(old).endCondition.maxScore)
    }
}
