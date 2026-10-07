package com.aventure.messcores

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Sauvegarde du championnat : il doit survivre à la fermeture de l'appli, et une sauvegarde abîmée ne doit pas planter. */
@RunWith(RobolectricTestRunner::class)
class TournamentViewModelTest {

    private lateinit var app: Application

    private val prefs get() = app.getSharedPreferences("mes_scores_tournament", Context.MODE_PRIVATE)

    private val joueurs = listOf("Anna", "Ben", "Chloé", "David")

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
    }

    /** Simule la réouverture de l'appli : un nouveau ViewModel relit ce qui est sauvegardé. */
    private fun rouvrir() = TournamentViewModel(app)

    @Test
    fun sansSauvegardeIlNYAPasDeChampionnat() {
        assertFalse(TournamentViewModel(app).hasTournament)
    }

    @Test
    fun unChampionnatDemarreEstRetrouveApresRedemarrage() {
        val vm = TournamentViewModel(app)
        vm.startTournament(joueurs, ordered = true)

        val relu = rouvrir()
        assertTrue(relu.hasTournament)
        assertEquals(vm.participants, relu.participants)
        assertEquals(TournamentFormat.KNOCKOUT, relu.format)
        assertEquals(vm.rounds.size, relu.rounds.size)
    }

    @Test
    fun unResultatSaisiEstRetrouveApresRedemarrage() {
        val vm = TournamentViewModel(app)
        vm.startTournament(joueurs, ordered = true)
        val matchIndex = vm.rounds[0].indexOfFirst { it.playerAIndex != null && it.playerBIndex != null }
        val vainqueur = vm.rounds[0][matchIndex].playerAIndex!!
        vm.setWinner(0, matchIndex, vainqueur)

        val relu = rouvrir()
        assertEquals(vainqueur, relu.rounds[0][matchIndex].winnerIndex)
    }

    @Test
    fun annulerUnResultatEstAussiSauvegarde() {
        val vm = TournamentViewModel(app)
        vm.startTournament(joueurs, ordered = true)
        val matchIndex = vm.rounds[0].indexOfFirst { it.playerAIndex != null && it.playerBIndex != null }
        vm.setWinner(0, matchIndex, vm.rounds[0][matchIndex].playerAIndex!!)
        vm.resetMatch(0, matchIndex)

        assertNull(rouvrir().rounds[0][matchIndex].winnerIndex)
    }

    @Test
    fun leFormatEnPoulesEstRetrouveApresRedemarrage() {
        val vm = TournamentViewModel(app)
        vm.startTournament(joueurs, format = TournamentFormat.ROUND_ROBIN)

        val relu = rouvrir()
        assertEquals(TournamentFormat.ROUND_ROBIN, relu.format)
        assertEquals(vm.rounds.size, relu.rounds.size)
    }

    @Test
    fun uneAncienneSauvegardeSansFormatEstLueEnEliminationDirecte() {
        val ancienne = """{"version":1,"participants":["A","B"],"finished":false,
            "rounds":[[{"round":0,"a":0,"b":1}]]}"""
        prefs.edit().putString("tournament", ancienne).commit()

        val vm = TournamentViewModel(app)
        assertTrue(vm.hasTournament)
        assertEquals(TournamentFormat.KNOCKOUT, vm.format)
        assertEquals(listOf("A", "B"), vm.participants)
    }

    @Test
    fun uneSauvegardeIllisibleNePlantePasEtEstMiseDeCote() {
        prefs.edit().putString("tournament", "pas du json").commit()

        val vm = TournamentViewModel(app)
        assertFalse(vm.hasTournament)
        assertEquals("pas du json", prefs.getString("tournament_unreadable_backup", null))
    }

    @Test
    fun demarrerUnChampionnatApresCorruptionNEcrasePasLaCopieDeSecurite() {
        prefs.edit().putString("tournament", "pas du json").commit()
        val vm = TournamentViewModel(app)
        vm.startTournament(joueurs, ordered = true)

        assertTrue(rouvrir().hasTournament)
        assertEquals("pas du json", prefs.getString("tournament_unreadable_backup", null))
    }

    @Test
    fun uneSauvegardeSaineNeCreeAucuneCopieDeSecurite() {
        TournamentViewModel(app).startTournament(joueurs, ordered = true)
        rouvrir()
        assertFalse(prefs.contains("tournament_unreadable_backup"))
    }
}
