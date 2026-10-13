package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class TournamentEngineTest {

    private fun names(n: Int) = List(n) { "J${it + 1}" }

    private fun start(n: Int, seed: Int = 1) = TournamentEngine.start(names(n), Random(seed))

    /** Joue tous les matchs en donnant toujours la victoire au joueur A (ou au seul joueur d'un bye). */
    private fun playAll(initial: TournamentState): TournamentState {
        var state = initial
        var guard = 0
        while (!state.finished && guard++ < 200) {
            val roundIndex = state.rounds.lastIndex
            val matchIndex = state.rounds[roundIndex].indexOfFirst { it.winnerIndex == null }
            val match = state.rounds[roundIndex][matchIndex]
            state = TournamentEngine.setWinner(state, roundIndex, matchIndex, match.playerAIndex!!)
        }
        return state
    }

    // ---- Tirage ----

    @Test
    fun moinsDeDeuxParticipants_pasDeChampionnat() {
        assertFalse(start(0).hasTournament)
        assertFalse(start(1).hasTournament)
    }

    @Test
    fun premierTour_chaqueParticipantApparaitUneSeuleFois() {
        for (n in 2..32) {
            val first = start(n, seed = n).rounds.first()
            val players = first.flatMap { listOfNotNull(it.playerAIndex, it.playerBIndex) }
            assertEquals("n=$n", (0 until n).toList(), players.sorted())
        }
    }

    @Test
    fun byes_unParMatchAuMaximum_etSeulementAuPremierTour() {
        for (n in 2..32) {
            val state = start(n, seed = n)
            var size = 1
            while (size < n) size *= 2
            assertEquals("n=$n", size - n, state.rounds.first().count { it.isBye })
            // Aucun match avec deux places vides.
            assertTrue(state.rounds.first().all { it.playerAIndex != null || it.playerBIndex != null })
            // Les byes sont déjà qualifiés d'office.
            assertTrue(state.rounds.first().filter { it.isBye }.all { it.winnerIndex != null })
        }
    }

    // ---- Déroulement complet ----

    @Test
    fun deuxParticipants_unSeulMatchEstLaFinale() {
        val state = start(2)
        assertEquals(1, state.rounds.size)
        assertFalse(state.finished)
        val done = playAll(state)
        assertTrue(done.finished)
        assertEquals(2, done.podium().size)
        assertEquals(done.championIndex(), done.podium().first())
    }

    @Test
    fun troisParticipants_podiumDeTrois_sansPetiteFinale() {
        val done = playAll(start(3))
        assertTrue(done.finished)
        assertNull(done.thirdPlaceIndex())
        val podium = done.podium()
        assertEquals(3, podium.size)
        assertEquals(3, podium.toSet().size)
    }

    @Test
    fun quatreParticipants_demiFinalesPuisFinaleEtPetiteFinale() {
        var state = start(4)
        assertEquals(2, state.rounds.first().size)
        val semis = state.rounds.first()
        // A gagne chaque demi-finale.
        state = TournamentEngine.setWinner(state, 0, 0, semis[0].playerAIndex!!)
        assertEquals(1, state.rounds.size) // pas encore de tour suivant
        state = TournamentEngine.setWinner(state, 0, 1, semis[1].playerAIndex!!)
        assertEquals(2, state.rounds.size)
        val last = state.rounds.last()
        assertEquals(listOf("Finale", "Petite finale"), last.map { it.label })
        assertEquals(setOf(semis[0].playerAIndex, semis[1].playerAIndex), setOf(last[0].playerAIndex, last[0].playerBIndex))
        assertEquals(setOf(semis[0].playerBIndex, semis[1].playerBIndex), setOf(last[1].playerAIndex, last[1].playerBIndex))
        assertFalse(state.finished)

        state = TournamentEngine.setWinner(state, 1, 0, last[0].playerAIndex!!)
        assertFalse(state.finished) // la petite finale reste à jouer
        state = TournamentEngine.setWinner(state, 1, 1, last[1].playerBIndex!!)
        assertTrue(state.finished)
        assertEquals(
            listOf(last[0].playerAIndex, last[0].playerBIndex, last[1].playerBIndex, last[1].playerAIndex),
            state.podium()
        )
        assertEquals(last[0].playerAIndex, state.championIndex())
        assertEquals(last[1].playerBIndex, state.thirdPlaceIndex())
    }

    @Test
    fun deDeuxATrenteDeux_tousLesChampionnatsSeTerminent_avecLeBonNombreDeMatchs() {
        for (n in 2..32) {
            val done = playAll(start(n, seed = n))
            assertTrue("n=$n terminé", done.finished)
            val realMatches = done.rounds.flatten().count { !it.isBye }
            // Élimination directe : n-1 matchs, plus la petite finale dès 4 participants.
            assertEquals("n=$n matchs", if (n >= 4) n else n - 1, realMatches)
            val expectedPodium = minOf(n, 4)
            val podium = done.podium()
            assertEquals("n=$n podium", expectedPodium, podium.size)
            assertEquals("n=$n podium distinct", expectedPodium, podium.toSet().size)
            assertTrue(done.rounds.flatten().all { it.winnerIndex != null })
        }
    }

    // ---- Saisies refusées ----

    @Test
    fun setWinner_ignoreUnJoueurQuiNeJoueraitPasCeMatch() {
        val state = start(4)
        val match = state.rounds[0][0]
        val outsider = (0 until 4).first { it != match.playerAIndex && it != match.playerBIndex }
        assertSame(state, TournamentEngine.setWinner(state, 0, 0, outsider))
        assertSame(state, TournamentEngine.setWinner(state, 5, 0, 0)) // tour inexistant
        assertSame(state, TournamentEngine.setWinner(state, 0, 9, 0)) // match inexistant
    }

    @Test
    fun setWinner_refuseDeChangerUnResultatSansPasserParLaCorrection() {
        val state = start(4)
        val m = state.rounds[0][0]
        val first = TournamentEngine.setWinner(state, 0, 0, m.playerAIndex!!)
        assertSame(first, TournamentEngine.setWinner(first, 0, 0, m.playerBIndex!!))
    }

    // ---- Correction d'un résultat ----

    @Test
    fun resetMatch_avantQueLeTourSoitComplet_vireSimplementLeResultat() {
        val state = start(4)
        val m = state.rounds[0][0]
        val decided = TournamentEngine.setWinner(state, 0, 0, m.playerAIndex!!)
        val reset = TournamentEngine.resetMatch(decided, 0, 0)
        assertNull(reset.rounds[0][0].winnerIndex)
        assertEquals(1, reset.rounds.size)
        assertFalse(decided.hasPlayedAfter(0))
    }

    @Test
    fun resetMatch_demiFinale_supprimeFinaleEtPetiteFinale_puisPermetDeChangerLeVainqueur() {
        var state = start(4)
        val semis = state.rounds.first()
        state = TournamentEngine.setWinner(state, 0, 0, semis[0].playerAIndex!!)
        state = TournamentEngine.setWinner(state, 0, 1, semis[1].playerAIndex!!)
        assertEquals(2, state.rounds.size)

        val reset = TournamentEngine.resetMatch(state, 0, 0)
        assertEquals(1, reset.rounds.size)
        assertNull(reset.rounds[0][0].winnerIndex)
        assertEquals(semis[1].playerAIndex, reset.rounds[0][1].winnerIndex) // l'autre demi est conservée

        // Autre vainqueur : la finale se régénère avec le nouveau finaliste.
        val redone = TournamentEngine.setWinner(reset, 0, 0, semis[0].playerBIndex!!)
        val finalists = setOf(redone.rounds[1][0].playerAIndex, redone.rounds[1][0].playerBIndex)
        assertEquals(setOf(semis[0].playerBIndex, semis[1].playerAIndex), finalists)
    }

    @Test
    fun resetMatch_aprèsLaFin_rouvreLeChampionnat() {
        val done = playAll(start(4))
        assertTrue(done.finished)
        val finalIndex = done.rounds.last().indexOfFirst { it.label == "Finale" }
        val reset = TournamentEngine.resetMatch(done, done.rounds.lastIndex, finalIndex)
        assertFalse(reset.finished)
        assertTrue(reset.podium().isEmpty())
        assertNull(reset.championIndex())
        // On peut le terminer à nouveau.
        val last = reset.rounds.last()[finalIndex]
        val again = TournamentEngine.setWinner(reset, reset.rounds.lastIndex, finalIndex, last.playerBIndex!!)
        assertTrue(again.finished)
        assertEquals(last.playerBIndex, again.championIndex())
    }

    @Test
    fun resetMatch_ignoreUnByeOuUnMatchSansResultat() {
        val state = start(3)
        val byeIndex = state.rounds[0].indexOfFirst { it.isBye }
        assertSame(state, TournamentEngine.resetMatch(state, 0, byeIndex))
        val realIndex = state.rounds[0].indexOfFirst { !it.isBye }
        assertSame(state, TournamentEngine.resetMatch(state, 0, realIndex)) // pas encore joué
        assertSame(state, TournamentEngine.resetMatch(state, 3, 0)) // inexistant
    }

    @Test
    fun hasPlayedAfter_comptePasLesByesDesToursSuivants() {
        // 3 participants : le tour 2 (finale) n'a de résultat qu'une fois joué.
        var state = start(3)
        val realIndex = state.rounds[0].indexOfFirst { !it.isBye }
        state = TournamentEngine.setWinner(state, 0, realIndex, state.rounds[0][realIndex].playerAIndex!!)
        assertEquals(2, state.rounds.size)
        assertFalse(state.hasPlayedAfter(0))
        state = TournamentEngine.setWinner(state, 1, 0, state.rounds[1][0].playerAIndex!!)
        assertTrue(state.hasPlayedAfter(0))
        assertTrue(state.finished)
    }

    // ---- Tirage au sort reproductible ----

    @Test
    fun memeGraine_memeTableau() {
        assertEquals(start(8, seed = 42), start(8, seed = 42))
    }
}
