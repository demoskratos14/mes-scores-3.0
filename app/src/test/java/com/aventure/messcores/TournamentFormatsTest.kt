package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Poules (chacun contre chacun) et tableau suivant l'ordre de la liste. */
@RunWith(RobolectricTestRunner::class)
// Les textes sont en français : on fixe la langue pour que les pluriels suivent les règles françaises (0 → singulier).
@Config(qualifiers = "fr")
class TournamentFormatsTest {

    private fun names(n: Int) = List(n) { "J${it + 1}" }

    private fun poules(n: Int) = TournamentEngine.startRoundRobin(names(n))

    private fun pairOf(match: TournamentMatch) = setOf(match.playerAIndex!!, match.playerBIndex!!)

    /** Donne la victoire à [winner] dans le match qui oppose [a] et [b]. */
    private fun decide(state: TournamentState, a: Int, b: Int, winner: Int): TournamentState {
        state.rounds.forEachIndexed { r, round ->
            round.forEachIndexed { m, match ->
                if (pairOf(match) == setOf(a, b)) return TournamentEngine.setWinner(state, r, m, winner)
            }
        }
        error("Match $a-$b introuvable")
    }

    /** Joue tous les matchs de poules : le participant au plus petit numéro gagne toujours. */
    private fun playAllPoules(initial: TournamentState): TournamentState {
        var state = initial
        initial.rounds.forEach { round ->
            round.forEach { match ->
                val a = match.playerAIndex!!
                val b = match.playerBIndex!!
                state = decide(state, a, b, minOf(a, b))
            }
        }
        return state
    }

    // ---- Poules : calendrier ----

    @Test
    fun poules_moinsDeDeuxParticipants_pasDeChampionnat() {
        assertFalse(poules(0).hasTournament)
        assertFalse(poules(1).hasTournament)
    }

    @Test
    fun poules_chaquePaireSeRencontreUneSeuleFois() {
        for (n in 2..12) {
            val pairs = poules(n).rounds.flatten().map { pairOf(it) }
            assertEquals("n=$n", n * (n - 1) / 2, pairs.size)
            assertEquals("n=$n", pairs.size, pairs.toSet().size)
        }
    }

    @Test
    fun poules_personneNeJoueDeuxFoisLaMemeJournee() {
        for (n in 2..12) {
            poules(n).rounds.forEach { round ->
                val players = round.flatMap { listOf(it.playerAIndex!!, it.playerBIndex!!) }
                assertEquals("n=$n", players.size, players.toSet().size)
            }
        }
    }

    @Test
    fun poules_nombrePair_toutLeMondeJoueChaqueJournee() {
        for (n in listOf(2, 4, 6, 8)) {
            val state = poules(n)
            assertEquals("n=$n", n - 1, state.rounds.size)
            state.rounds.forEach { round ->
                assertEquals("n=$n", (0 until n).toSet(), round.flatMap { listOf(it.playerAIndex!!, it.playerBIndex!!) }.toSet())
            }
        }
    }

    @Test
    fun poules_nombreImpair_unRepoParJourneeEtChacunSeReposeUneFois() {
        for (n in listOf(3, 5, 7, 9)) {
            val state = poules(n)
            assertEquals("n=$n", n, state.rounds.size)
            val resting = state.rounds.map { round ->
                val playing = round.flatMap { listOf(it.playerAIndex!!, it.playerBIndex!!) }.toSet()
                (0 until n).filter { it !in playing }
            }
            assertTrue("n=$n", resting.all { it.size == 1 })
            assertEquals("n=$n", (0 until n).toList(), resting.map { it.single() }.sorted())
        }
    }

    @Test
    fun poules_lesMatchsNeSontJamaisDesByes() {
        assertTrue(poules(5).rounds.flatten().none { it.isBye })
    }

    // ---- Poules : résultats et classement ----

    @Test
    fun poules_avantLePremierMatch_toutLeMondeALegalite() {
        val rows = poules(4).standings()
        assertEquals(listOf(1, 1, 1, 1), rows.map { it.rank })
        assertTrue(rows.all { it.played == 0 && it.wins == 0 })
    }

    @Test
    fun poules_terminees_seulementQuandTousLesMatchsSontJoues() {
        var state = poules(4)
        val all = state.rounds.flatten()
        all.dropLast(1).forEach { match ->
            state = decide(state, match.playerAIndex!!, match.playerBIndex!!, match.playerAIndex!!)
            assertFalse(state.finished)
        }
        val last = all.last()
        state = decide(state, last.playerAIndex!!, last.playerBIndex!!, last.playerAIndex!!)
        assertTrue(state.finished)
    }

    @Test
    fun poules_classementAuxVictoires() {
        val state = playAllPoules(poules(4))
        assertTrue(state.finished)
        val rows = state.standings()
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.playerIndex })
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.rank })
        assertEquals(listOf(3, 2, 1, 0), rows.map { it.wins })
        assertTrue(rows.all { it.played == 3 })
        assertEquals(0, state.championIndex())
        assertEquals(listOf(0, 1, 2, 3), state.podium())
    }

    @Test
    fun poules_podiumLimiteAQuatrePlaces() {
        val state = playAllPoules(poules(6))
        assertEquals(listOf(0, 1, 2, 3), state.podium())
        assertEquals(6, state.standings().size)
    }

    @Test
    fun poules_egaliteADeux_departageeParLeFaceAFace() {
        // J1 et J2 finissent à 2 victoires ; J1 a battu J2. J3 et J4 finissent à 1 victoire ; J3 a battu J4.
        var state = poules(4)
        state = decide(state, 0, 1, 0)
        state = decide(state, 0, 2, 0)
        state = decide(state, 3, 0, 3)
        state = decide(state, 1, 2, 1)
        state = decide(state, 1, 3, 1)
        state = decide(state, 2, 3, 2)
        assertTrue(state.finished)
        val rows = state.standings()
        assertEquals(listOf(0, 1, 2, 3), rows.map { it.playerIndex })
        assertEquals(listOf(1, 2, 3, 4), rows.map { it.rank })
        assertEquals(0, state.championIndex())
    }

    @Test
    fun poules_egaliteParfaite_memeRangEtPasDeChampion() {
        // J1 bat J2, J2 bat J3, J3 bat J1 : chacun a une victoire et une défaite.
        var state = poules(3)
        state = decide(state, 0, 1, 0)
        state = decide(state, 1, 2, 1)
        state = decide(state, 2, 0, 2)
        assertTrue(state.finished)
        assertEquals(listOf(1, 1, 1), state.standings().map { it.rank })
        assertNull(state.championIndex())
        assertEquals(3, state.podium().size)
    }

    @Test
    fun poules_corrigerUnMatch_nEnSupprimeAucunAutre() {
        val done = playAllPoules(poules(4))
        val roundCount = done.rounds.size
        val decidedBefore = done.rounds.flatten().count { it.winnerIndex != null }
        assertFalse(done.hasPlayedAfter(0))

        val reset = TournamentEngine.resetMatch(done, 0, 0)
        assertFalse(reset.finished)
        assertEquals(roundCount, reset.rounds.size)
        assertNull(reset.rounds[0][0].winnerIndex)
        assertEquals(decidedBefore - 1, reset.rounds.flatten().count { it.winnerIndex != null })

        // On peut désigner l'autre vainqueur, et les poules se terminent de nouveau.
        val match = reset.rounds[0][0]
        val redone = TournamentEngine.setWinner(reset, 0, 0, match.playerBIndex!!)
        assertTrue(redone.finished)
    }

    @Test
    fun poules_saisiesInvalides_laissentLEtatIntact() {
        val state = poules(4)
        val match = state.rounds[0][0]
        val outsider = (0 until 4).first { it != match.playerAIndex && it != match.playerBIndex }
        assertSame(state, TournamentEngine.setWinner(state, 0, 0, outsider))
        assertSame(state, TournamentEngine.setWinner(state, 9, 0, 0))
        assertSame(state, TournamentEngine.resetMatch(state, 0, 0)) // pas encore joué
        val decided = TournamentEngine.setWinner(state, 0, 0, match.playerAIndex!!)
        assertSame(decided, TournamentEngine.setWinner(decided, 0, 0, match.playerBIndex!!)) // déjà joué
    }

    @Test
    fun eliminationDirecte_inchangee_etSansClassementDePoules() {
        val state = TournamentEngine.start(names(4))
        assertEquals(TournamentFormat.KNOCKOUT, state.format)
        assertTrue(state.standings().isEmpty())
    }

    // ---- Tableau suivant l'ordre de la liste ----

    @Test
    fun tableauOrdonne_lesParticipantsSAffrontentDansLOrdreDeLaListe() {
        val first = TournamentEngine.start(names(8), ordered = true).rounds.first()
        assertEquals(listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7), first.map { it.playerAIndex to it.playerBIndex })
    }

    @Test
    fun tableauOrdonne_resultatIdentiqueQuelQueSoitLeTirage() {
        val a = TournamentEngine.start(names(8), kotlin.random.Random(1), ordered = true)
        val b = TournamentEngine.start(names(8), kotlin.random.Random(99), ordered = true)
        assertEquals(a, b)
    }

    @Test
    fun tableauOrdonne_lesDerniersDeLaListeSontQualifiesDOffice() {
        val first = TournamentEngine.start(names(6), ordered = true).rounds.first()
        assertEquals(
            listOf(0 to 1, 2 to 3, 4 to null, 5 to null),
            first.map { it.playerAIndex to it.playerBIndex }
        )
        assertEquals(listOf(null, null, 4, 5), first.map { it.winnerIndex })
    }

    @Test
    fun tableauOrdonne_chaqueParticipantUneSeuleFois_etBonNombreDeByes() {
        for (n in 2..32) {
            val state = TournamentEngine.start(names(n), ordered = true)
            val first = state.rounds.first()
            val players = first.flatMap { listOfNotNull(it.playerAIndex, it.playerBIndex) }
            assertEquals("n=$n", (0 until n).toList(), players.sorted())
            var size = 1
            while (size < n) size *= 2
            assertEquals("n=$n", size - n, first.count { it.isBye })
            assertEquals("n=$n", size / 2, first.size)
        }
    }

    @Test
    fun tableauOrdonne_sejouejusquaLaFinale() {
        for (n in 2..16) {
            var state = TournamentEngine.start(names(n), ordered = true)
            var guard = 0
            while (!state.finished && guard++ < 200) {
                val r = state.rounds.lastIndex
                val m = state.rounds[r].indexOfFirst { it.winnerIndex == null }
                state = TournamentEngine.setWinner(state, r, m, state.rounds[r][m].playerAIndex!!)
            }
            assertTrue("n=$n", state.finished)
            assertEquals("n=$n", TournamentFormat.KNOCKOUT, state.format)
        }
    }

    // ---- Partage ----

    @Test
    fun partageDesPoules_listeLesRangsEtLesVictoires() {
        val text = ResultText.poules(RuntimeEnvironment.getApplication(), 
            listOf("Anna", "Ben", "Cléo"),
            listOf(PouleRow(0, 1, 2, 2), PouleRow(1, 2, 2, 1), PouleRow(2, 3, 2, 0))
        )
        assertEquals(
            "Championnat (poules) — classement final\n" +
                "🥇 Anna : 2 victoires sur 2 matchs\n" +
                "🥈 Ben : 1 victoire sur 2 matchs\n" +
                "🥉 Cléo : 0 victoire sur 2 matchs\n" +
                "\n" +
                "— Mes Scores",
            text
        )
    }
}
