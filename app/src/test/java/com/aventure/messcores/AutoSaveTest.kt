package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Auto-sauvegarde dans le journal ([ScoreViewModel.autoSave]) : n'écrit que s'il y a une partie
 * commencée et du nouveau, et reste compatible avec « Quitter sans enregistrer ».
 */
@RunWith(RobolectricTestRunner::class)
class AutoSaveTest {

    private lateinit var repository: GameHistoryRepository

    @Before
    fun setUp() {
        repository = GameHistoryRepository(RuntimeEnvironment.getApplication())
    }

    private fun newGame(): ScoreViewModel =
        ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), GameRules(id = "test", name = "Test")) }

    private fun ScoreViewModel.enter(round: Int, player: Int, value: Int) {
        scores[round][player].baseValue = value
        notifyCellChanged(round)
    }

    @Test
    fun sansScoreSaisiRienNEstEcrit() {
        val vm = newGame()
        assertFalse(vm.autoSave(repository))
        assertFalse(vm.autoSave(repository, force = true))
        assertTrue(repository.listGames().isEmpty())
    }

    @Test
    fun laPremiereSaisieEstEcriteEtLaSuivanteSeulementSiQuelqueChoseChange() {
        val vm = newGame()
        vm.enter(0, 0, 5)
        assertTrue(vm.autoSave(repository))
        assertEquals(1, repository.listGames().size)

        // Rien de nouveau : le journal n'est pas réécrit.
        assertFalse(vm.autoSave(repository))

        vm.enter(0, 1, 3)
        assertTrue(vm.autoSave(repository))
        // Toujours la même entrée du journal, pas une nouvelle à chaque fois.
        assertEquals(1, repository.listGames().size)
    }

    @Test
    fun forceEcritMemeSansChangement() {
        val vm = newGame()
        vm.enter(0, 0, 5)
        assertTrue(vm.autoSave(repository))
        assertTrue(vm.autoSave(repository, force = true))
    }

    @Test
    fun quitterSansEnregistrerEffaceLaPartieAutoSauvegardeeJamaisEnregistree() {
        val vm = newGame()
        vm.enter(0, 0, 5)
        vm.autoSave(repository)
        assertEquals(1, repository.listGames().size)

        vm.discardToJournal(repository)
        vm.closeGame()
        assertTrue(repository.listGames().isEmpty())
        // Une fois la partie fermée, une auto-sauvegarde tardive ne la ressuscite pas.
        assertFalse(vm.autoSave(repository, force = true))
        assertTrue(repository.listGames().isEmpty())
    }

    @Test
    fun quitterSansEnregistrerRemetLaPartieDansLEtatValideParLUtilisateur() {
        val first = newGame()
        first.enter(0, 0, 5)
        first.saveToJournal(repository)
        val validated = repository.listGames().single()

        // Reprise de la partie, nouvelle saisie, auto-sauvegarde, puis abandon des modifications.
        val vm = ScoreViewModel()
        vm.loadFromSaved(validated)
        vm.enter(0, 1, 9)
        assertTrue(vm.autoSave(repository))
        assertEquals(9, repository.listGames().single().cellSnapshots!![0][1].baseValue)

        vm.discardToJournal(repository)
        vm.closeGame()
        val restored = repository.listGames().single()
        assertEquals(validated.savedAt, restored.savedAt)
        assertEquals(null, restored.cellSnapshots!![0][1].baseValue)
    }

    @Test
    fun uneSauvegardeVouluePuisAucunChangementNeReecritRien() {
        val vm = newGame()
        vm.enter(0, 0, 5)
        vm.saveToJournal(repository)
        assertFalse(vm.autoSave(repository))
    }
}
