package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Score du tennis et du padel : jeux, avantage, point d'or, tie-break, super tie-break, sauvegarde. */
class RacketScoringTest {

    private fun game(side: Int): List<Int> = List(4) { side }

    /** Set gagné par [side] 6 jeux à [otherGames] (les jeux de l'adversaire d'abord). */
    private fun set(side: Int, otherGames: Int = 0): List<Int> =
        List(otherGames) { game(1 - side) }.flatten() + List(6) { game(side) }.flatten()

    private fun play(config: RacketConfig, points: List<Int>) = RacketScoring.replay(config, points)

    @Test
    fun unJeuSeGagneEnQuatrePoints() {
        val state = play(RacketConfig(), game(0))
        assertEquals(1, state.gamesA)
        assertEquals(0, state.gamesB)
        assertEquals("0", state.pointText(0))
    }

    @Test
    fun lesPointsSeComptent15_30_40() {
        val config = RacketConfig()
        assertEquals("15", play(config, listOf(0)).pointText(0))
        assertEquals("30", play(config, listOf(0, 0)).pointText(0))
        assertEquals("40", play(config, listOf(0, 0, 0)).pointText(0))
        assertEquals("0", play(config, listOf(0, 0, 0)).pointText(1))
    }

    @Test
    fun aEgaliteIlFautDeuxPointsDEcart() {
        var state = play(RacketConfig(), listOf(0, 0, 0, 1, 1, 1))
        assertTrue(state.isDeuce)
        state = RacketScoring.addPoint(state, 0)
        assertTrue(state.hasAdvantage(0))
        assertFalse(state.hasAdvantage(1))
        assertEquals(0, state.gamesA)
        // L'adversaire égalise : retour à l'égalité, personne n'a l'avantage.
        state = RacketScoring.addPoint(state, 1)
        assertTrue(state.isDeuce)
        assertFalse(state.hasAdvantage(0))
        state = RacketScoring.addPoint(state, 1)
        state = RacketScoring.addPoint(state, 1)
        assertEquals(1, state.gamesB)
        assertEquals(0, state.pointsA)
    }

    @Test
    fun auPointDOrLePointSuivantA40_40DecideDuJeu() {
        val config = RacketConfig(goldenPoint = true)
        val deuce = play(config, listOf(0, 0, 0, 1, 1, 1))
        assertTrue(deuce.isGoldenPoint)
        assertFalse(deuce.isDeuce)
        assertEquals(1, RacketScoring.addPoint(deuce, 0).gamesA)
        assertEquals(1, RacketScoring.addPoint(deuce, 1).gamesB)
    }

    @Test
    fun unSetSeGagneA6JeuxAvecDeuxJeuxDEcart() {
        val state = play(RacketConfig(), set(0, otherGames = 4))
        assertEquals(listOf(SetScore(6, 4)), state.sets)
        assertEquals(1, state.setsWonA)
        assertNull(state.winner)
    }

    @Test
    fun a6_5LeSetContinue() {
        val points = (1..5).flatMap { game(0) + game(1) } + game(0)
        val state = play(RacketConfig(), points)
        assertEquals(6, state.gamesA)
        assertEquals(5, state.gamesB)
        assertTrue(state.sets.isEmpty())
        // 7-5 : set gagné.
        assertEquals(listOf(SetScore(7, 5)), RacketScoring.addPoint(play(RacketConfig(), points + listOf(0, 0, 0)), 0).sets)
    }

    @Test
    fun a6_6UnTieBreakDecideDuSet() {
        val toSixAll = (1..5).flatMap { game(0) + game(1) } + game(0) + game(1)
        var state = play(RacketConfig(), toSixAll)
        assertTrue(state.inTiebreak)
        assertFalse(state.superTiebreak)
        // 7 points d'écart suffisant : 7-0.
        state = play(RacketConfig(), toSixAll + List(7) { 0 })
        assertEquals(listOf(SetScore(7, 6, tiebreakLoser = 0)), state.sets)
        assertFalse(state.inTiebreak)
    }

    @Test
    fun leTieBreakExigeDeuxPointsDEcart() {
        val toSixAll = (1..5).flatMap { game(0) + game(1) } + game(0) + game(1)
        val sixAll = List(6) { listOf(0, 1) }.flatten()
        var state = play(RacketConfig(), toSixAll + sixAll + listOf(0))
        assertTrue(state.inTiebreak)
        assertEquals(7, state.pointsA)
        assertEquals(6, state.pointsB)
        state = RacketScoring.addPoint(state, 0)
        assertEquals(listOf(SetScore(7, 6, tiebreakLoser = 6)), state.sets)
    }

    @Test
    fun leMatchSeGagneEnDeuxSetsMeilleurDeTrois() {
        val state = play(RacketConfig(), set(0) + set(0))
        assertEquals(0, state.winner)
        assertEquals(2, state.sets.size)
    }

    @Test
    fun enMeilleurDeCinqIlFautTroisSets() {
        val config = RacketConfig(setsToWin = 3)
        assertNull(play(config, set(0) + set(0)).winner)
        assertEquals(0, play(config, set(0) + set(0) + set(0)).winner)
    }

    @Test
    fun unSeulSetSuffitSiOnLeDemande() {
        assertEquals(1, play(RacketConfig(setsToWin = 1), set(1)).winner)
    }

    @Test
    fun lesPointsSaisisApresLaFinDuMatchSontIgnores() {
        val state = play(RacketConfig(), set(0) + set(0) + List(20) { 1 })
        assertEquals(0, state.winner)
        assertEquals(2, state.sets.size)
        assertEquals(state, RacketScoring.addPoint(state, 1))
    }

    @Test
    fun unSuperTieBreakRemplaceLeSetDecisif() {
        val config = RacketConfig(finalSet = FinalSetMode.SUPER_TIEBREAK)
        val oneAll = set(0) + set(1)
        var state = play(config, oneAll)
        assertTrue(state.inTiebreak)
        assertTrue(state.superTiebreak)
        assertNull(state.winner)
        // 9-0 : pas fini ; 10-0 : fini.
        assertNull(play(config, oneAll + List(9) { 0 }).winner)
        state = play(config, oneAll + List(10) { 0 })
        assertEquals(0, state.winner)
        assertEquals(SetScore(10, 0, superTiebreak = true), state.sets.last())
    }

    @Test
    fun leSuperTieBreakExigeDeuxPointsDEcart() {
        val config = RacketConfig(finalSet = FinalSetMode.SUPER_TIEBREAK)
        val oneAll = set(0) + set(1)
        assertNull(play(config, oneAll + List(9) { 0 } + List(9) { 1 } + listOf(0)).winner)
        assertEquals(0, play(config, oneAll + List(9) { 0 } + List(9) { 1 } + listOf(0, 0)).winner)
    }

    @Test
    fun sansTieBreakLeSetDecisifContinueApres6_6() {
        val config = RacketConfig(finalSet = FinalSetMode.ADVANTAGE)
        val toSixAll = set(0) + set(1) + (1..5).flatMap { game(0) + game(1) } + game(0) + game(1)
        var state = play(config, toSixAll)
        assertFalse(state.inTiebreak)
        assertEquals(6, state.gamesA)
        assertEquals(6, state.gamesB)
        state = play(config, toSixAll + game(0) + game(0))
        assertEquals(0, state.winner)
        assertEquals(SetScore(8, 6), state.sets.last())
    }

    @Test
    fun lesCampsSeFormentEnPremiereMoitieDeLaListe() {
        assertEquals(listOf(0, 1), (0 until 2).map { RacketScoring.sideOf(it, 2) })
        assertEquals(listOf(0, 0, 1), (0 until 3).map { RacketScoring.sideOf(it, 3) })
        assertEquals(listOf(0, 0, 1, 1), (0 until 4).map { RacketScoring.sideOf(it, 4) })
        assertEquals(listOf(2, 3), RacketScoring.playersOfSide(1, 4))
    }

    @Test
    fun lesSetsSontRepartisSurLesJoueursDUnMemeCamp() {
        val state = play(RacketConfig(), set(0))
        assertEquals(listOf(1, 1, 0, 0), RacketScoring.setsByPlayer(state, 4))
    }

    @Test
    fun leScoreEnChiffresResumeLeMatch() {
        val toSixAll = (1..5).flatMap { game(0) + game(1) } + game(0) + game(1)
        // Un set à 7-6(3) (3 points pour B dans le tie-break), puis 2 jeux à 1 dans le suivant.
        val points = toSixAll + List(3) { 1 } + List(7) { 0 } + game(1) + game(0) + game(0)
        assertEquals("7-6(3) 2-1", RacketScoring.scoreLine(play(RacketConfig(), points)))
        assertEquals("", RacketScoring.scoreLine(play(RacketConfig(), emptyList())))
    }

    @Test
    fun leMatchSEcritEtSeRelitEnJson() {
        val match = RacketMatch(
            RacketConfig(setsToWin = 3, goldenPoint = true, finalSet = FinalSetMode.SUPER_TIEBREAK),
            listOf(0, 1, 1, 0, 0)
        )
        assertEquals(match, RacketMatch.fromJson(match.toJson()))
    }

    @Test
    fun unJsonIncompletDonneLesReglagesParDefaut() {
        val match = RacketMatch.fromJson(org.json.JSONObject("""{"points":[0,1]}"""))
        assertEquals(RacketConfig(), match.config)
        assertEquals(listOf(0, 1), match.points)
    }
}
