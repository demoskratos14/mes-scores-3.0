package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class BridgeSkatGamesTest {

    private fun game(id: String) = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == id }

    @Test
    fun leBridgeEtLeSkatSontDesJeuxDeCartes() {
        for (id in listOf("builtin_bridge", "builtin_skat")) {
            assertEquals(id, GameCategory.CARDS, game(id).category())
            assertEquals(id, ScoreMode.VARIABLE_TEAMS, game(id).scoreMode)
        }
        assertTrue(game("builtin_bridge").usesBridgeScoring)
        assertTrue(game("builtin_skat").usesSkatScoring)
        assertEquals(2, game("builtin_bridge").minPlayers)
        assertEquals(2, game("builtin_bridge").maxPlayers)
        assertEquals(3, game("builtin_skat").minPlayers)
        assertEquals(4, game("builtin_skat").maxPlayers)
    }

    @Test
    fun uneDonneDeBridgeAjouteSesPointsAuBonCamp() {
        val vm = ScoreViewModel().also { it.initGame(listOf("Nous", "Eux"), game("builtin_bridge")) }
        val made = computeBridgeContract(3, BridgeSuit.NO_TRUMP, 0, false, 9)
        vm.appendTeamRound(
            TeamRound(
                teamALabel = "Nous · 3SA · rempli", teamAPlayers = setOf(0), value = made.score,
                deltas = bridgeDeltas(2, 0, made),
                contractInput = BridgeInput(0, 3, BridgeSuit.NO_TRUMP, 0, false, 9).encode()
            )
        )
        val down = computeBridgeContract(4, BridgeSuit.SPADES, 0, false, 8)
        vm.appendTeamRound(
            TeamRound(
                teamALabel = "Nous · 4♠ · chuté", teamAPlayers = setOf(0), value = down.score,
                deltas = bridgeDeltas(2, 0, down),
                contractInput = BridgeInput(0, 4, BridgeSuit.SPADES, 0, false, 8).encode()
            )
        )
        // 400 pour Nous, puis 100 de pénalité pour Eux.
        assertEquals(listOf(400, 100), vm.totals)
        // Sans fin de partie automatique, le classement donne quand même la tête : Nous devant Eux.
        assertEquals(1, vm.ranks[0])
        assertEquals(2, vm.ranks[1])
    }

    @Test
    fun uneDonneDeSkatMarqueLeDeclarantEtLesAdversaires() {
        val vm = ScoreViewModel().also { it.initGame(listOf("Ana", "Ben", "Cléo"), game("builtin_skat")) }
        val lost = computeSkatRound(SkatGame.GRAND, 1, false, false, false, false, SkatOutcome.LOST, 0)
        vm.appendTeamRound(
            TeamRound(
                teamALabel = "Ana · Grand · perdu", teamAPlayers = setOf(0), value = lost.declarerScore,
                deltas = skatDeltas(3, 0, lost)
            )
        )
        assertEquals(listOf(-146, 30, 30), vm.totals)
    }

    @Test
    fun laSaisieDUneDonneSurvitALEnregistrement() {
        val input = BridgeInput(1, 4, BridgeSuit.SPADES, 1, true, 11).encode()
        val rules = game("builtin_bridge")
        val saved = SavedGame(
            id = "test", savedAt = 1L, gameRules = rules, players = listOf("Nous", "Eux"),
            playerColorsArgb = emptyList(),
            teamRounds = listOf(TeamRound("Eux · 4♠ X · rempli", setOf(1), 590, deltas = listOf(0, 590), contractInput = input)),
            isFinished = false
        )
        val reloaded = SavedGame.fromJson(saved.toJson())
        val round = reloaded.teamRounds!!.single()
        assertEquals(input, round.contractInput)
        assertNotNull(BridgeInput.decode(round.contractInput!!))
        assertEquals(listOf(0, 590), round.deltas)
    }
}
