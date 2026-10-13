package com.aventure.messcores

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

/** Journal des parties : enregistrement, limite de 50 parties, import et reprise sur données abîmées. */
@RunWith(RobolectricTestRunner::class)
class GameHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: GameHistoryRepository

    private val prefs get() = context.getSharedPreferences("mes_scores_history", Context.MODE_PRIVATE)

    private fun game(id: String, savedAt: Long, finished: Boolean = false) = SavedGame(
        id = id,
        savedAt = savedAt,
        gameRules = GameRules(id = "g", name = "Jeu"),
        players = listOf("Anna", "Ben"),
        playerColorsArgb = listOf(1, 2),
        cellSnapshots = listOf(
            listOf(
                CellSnapshot(5, false, GameRules.NORMAL_MULTIPLIER_ID),
                CellSnapshot(3, false, GameRules.NORMAL_MULTIPLIER_ID)
            )
        ),
        isFinished = finished
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        repository = GameHistoryRepository(context)
    }

    @Test
    fun lesPartiesSontRenduesLaPlusRecenteEnPremier() {
        repository.saveGame(game("vieille", 100))
        repository.saveGame(game("recente", 300))
        repository.saveGame(game("milieu", 200))
        assertEquals(listOf("recente", "milieu", "vieille"), repository.listGames().map { it.id })
    }

    @Test
    fun enregistrerUnePartieDeMemeIdLaRemplace() {
        repository.saveGame(game("a", 100, finished = false))
        repository.saveGame(game("a", 200, finished = true))
        val parties = repository.listGames()
        assertEquals(1, parties.size)
        assertEquals(200L, parties[0].savedAt)
        assertTrue(parties[0].isFinished)
    }

    @Test
    fun auDelaDeLaLimiteLesPlusAnciennesSontSupprimees() {
        val limite = GameHistoryRepository.MAX_SAVED_GAMES
        repeat(limite + 5) { i -> repository.saveGame(game("g$i", i.toLong())) }
        val ids = repository.listGames().map { it.id }
        assertEquals(limite, ids.size)
        assertTrue("g${limite + 4}" in ids)
        assertFalse("g0" in ids)
    }

    @Test
    fun supprimerLesPartiesTermineesGardeCellesEnCours() {
        repository.saveGame(game("finie", 1, finished = true))
        repository.saveGame(game("encours", 2, finished = false))
        repository.deleteFinishedGames()
        assertEquals(listOf("encours"), repository.listGames().map { it.id })
    }

    @Test
    fun supprimerUnePartieLaisseLesAutres() {
        repository.saveGame(game("a", 1))
        repository.saveGame(game("b", 2))
        repository.deleteGame("a")
        assertEquals(listOf("b"), repository.listGames().map { it.id })
    }

    @Test
    fun unJournalEntierementIllisibleDonneAucunePartieMaisEstMisDeCote() {
        prefs.edit().putString("saved_games", "pas du json").commit()
        assertTrue(repository.listGames().isEmpty())
        assertEquals("pas du json", prefs.getString("saved_games_unreadable_backup", null))
    }

    @Test
    fun uneEntreeAbimeeEstIgnoreeSeuleEtLeTexteBrutEstConserve() {
        val bonne = game("ok", 1).toJson()
        val brut = """[$bonne, {"id": "cassee"}]"""
        prefs.edit().putString("saved_games", brut).commit()

        assertEquals(listOf("ok"), repository.listGames().map { it.id })
        assertEquals(brut, prefs.getString("saved_games_unreadable_backup", null))
    }

    @Test
    fun enregistrerApresCorruptionNEcrasePasLaCopieDeSecurite() {
        prefs.edit().putString("saved_games", "pas du json").commit()
        repository.saveGame(game("neuve", 5))

        assertEquals(listOf("neuve"), repository.listGames().map { it.id })
        assertEquals("pas du json", prefs.getString("saved_games_unreadable_backup", null))
    }

    @Test
    fun desDonneesSainesNeCreentAucuneCopieDeSecurite() {
        repository.saveGame(game("a", 1))
        repository.listGames()
        assertFalse(prefs.contains("saved_games_unreadable_backup"))
        assertNull(prefs.getString("saved_games_unreadable_backup", null))
    }

    @Test
    fun unImportInvalideLaisseLeJournalIntactEtSignaleLErreur() {
        repository.saveGame(game("a", 1))
        var raison: JournalBackup.ImportError? = null
        val bilan = repository.importJson("n'importe quoi") { raison = it }

        assertNull(bilan)
        assertEquals(JournalBackup.ImportError.NOT_A_BACKUP, raison)
        assertEquals(listOf("a"), repository.listGames().map { it.id })
    }

    @Test
    fun exporterPuisImporterDansUnJournalVideRetrouveLesParties() {
        repository.saveGame(game("a", 10))
        repository.saveGame(game("b", 20, finished = true))
        val texte = repository.exportJson()

        // Autre appareil : le journal est vide avant l'import.
        prefs.edit().clear().commit()
        assertTrue(repository.listGames().isEmpty())

        val bilan = repository.importJson(texte) { }
        assertEquals(2, bilan?.added)
        assertEquals(listOf("b", "a"), repository.listGames().map { it.id })
    }
}
