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
import java.io.File

/** Journal des parties (un fichier par partie) : enregistrement, limite par jeu, import, migration et reprise sur données abîmées. */
@RunWith(RobolectricTestRunner::class)
class GameHistoryRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: GameHistoryRepository

    /** Ancien emplacement du journal, lu seulement pour la migration. */
    private val prefs get() = context.getSharedPreferences("mes_scores_history", Context.MODE_PRIVATE)
    private val journalDir get() = File(context.filesDir, GameHistoryRepository.DIR_NAME)

    private fun game(id: String, savedAt: Long, finished: Boolean = false, gameId: String = "g") = SavedGame(
        id = id,
        savedAt = savedAt,
        gameRules = GameRules(id = gameId, name = "Jeu $gameId"),
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
        journalDir.deleteRecursively()
        prefs.edit().clear().commit()
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
    fun auDelaDeLaLimiteLesPlusAnciennesDuJeuSontSupprimees() {
        val limite = GameHistoryRepository.MAX_SAVED_GAMES
        repeat(limite + 5) { i -> repository.saveGame(game("g$i", i.toLong())) }
        val ids = repository.listGames().map { it.id }
        assertEquals(limite, ids.size)
        assertTrue("g${limite + 4}" in ids)
        assertFalse("g0" in ids)
    }

    @Test
    fun laLimiteEstAppliqueeJeuParJeuSansToucherAuxAutres() {
        val limite = GameHistoryRepository.MAX_SAVED_GAMES
        repeat(limite) { i -> repository.saveGame(game("a$i", i.toLong(), gameId = "yams")) }
        repeat(limite + 3) { i -> repository.saveGame(game("b$i", 1000L + i, gameId = "belote")) }

        val parties = repository.listGames()
        assertEquals(limite, parties.count { it.gameRules.id == "yams" })
        assertEquals(limite, parties.count { it.gameRules.id == "belote" })
        // Les parties de Yams n'ont pas été écartées pour faire de la place à celles de Belote.
        assertTrue("a0" in parties.map { it.id })
        assertFalse("b0" in parties.map { it.id })
    }

    @Test
    fun uneSauvegardeNEcritQueLeFichierDeLaPartie() {
        repository.saveGame(game("a", 1))
        repository.saveGame(game("b", 2))
        val avant = journalDir.listFiles()!!.associate { it.name to it.lastModified() }
        val fichierA = journalDir.listFiles()!!.single { it.name.endsWith("a.json") }
        fichierA.setLastModified(1_000L)

        repository.saveGame(game("b", 3))

        assertEquals(2, journalDir.listFiles()!!.count { it.name.endsWith(".json") })
        assertEquals(1_000L, fichierA.lastModified())
        assertEquals(avant.keys, journalDir.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun unIdentifiantBizarreNeSortJamaisDuDossierDuJournal() {
        repository.saveGame(game("../../evil", 1, gameId = "../x"))
        assertEquals(listOf("../../evil"), repository.listGames().map { it.id })
        assertTrue(journalDir.listFiles()!!.all { it.parentFile == journalDir })
        assertFalse(File(context.filesDir, "evil.json").exists())
        repository.deleteGame("../../evil")
        assertTrue(repository.listGames().isEmpty())
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
    fun unFichierAbimeEstMisDeCoteSansGenerLesAutres() {
        repository.saveGame(game("ok", 1))
        File(journalDir, "g@cassee.json").writeText("pas du json")

        assertEquals(listOf("ok"), repository.listGames().map { it.id })
        assertFalse(File(journalDir, "g@cassee.json").exists())
        assertTrue(File(journalDir, "g@cassee.json.bad").exists())
        // Une fois mis de côté, il n'est plus relu ni recompté.
        assertEquals(listOf("ok"), repository.listGames().map { it.id })
    }

    @Test
    fun desDonneesSainesNeMettentRienDeCote() {
        repository.saveGame(game("a", 1))
        repository.listGames()
        assertTrue(journalDir.listFiles()!!.none { it.name.endsWith(".bad") })
        assertFalse(prefs.contains("saved_games_unreadable_backup"))
    }

    @Test
    fun lAncienJournalEstConvertiEnFichiersAuPremierAcces() {
        val ancien = """[${game("a", 10).toJson()}, ${game("b", 20, finished = true).toJson()}]"""
        prefs.edit().putString("saved_games", ancien).commit()

        assertEquals(listOf("b", "a"), repository.listGames().map { it.id })
        assertEquals(2, journalDir.listFiles()!!.count { it.name.endsWith(".json") })
        // L'ancien texte reste en place comme filet de sécurité.
        assertEquals(ancien, prefs.getString("saved_games", null))
    }

    @Test
    fun laMigrationNeRessuscitePasUnePartieSupprimeeEnsuite() {
        prefs.edit().putString("saved_games", "[${game("a", 10).toJson()}]").commit()
        assertEquals(1, repository.listGames().size)

        repository.deleteGame("a")
        // Nouvelle instance (comme au prochain lancement) : l'ancien texte ne doit pas être relu.
        assertTrue(GameHistoryRepository(context).listGames().isEmpty())
    }

    @Test
    fun supprimerAvantToutAccesNeLaisseJamaisReapparaitreLaPartie() {
        prefs.edit().putString("saved_games", "[${game("a", 10).toJson()}]").commit()
        GameHistoryRepository(context).deleteGame("a")
        assertTrue(GameHistoryRepository(context).listGames().isEmpty())
    }

    @Test
    fun lAncienJournalIllisibleDonneAucunePartieMaisEstMisDeCote() {
        prefs.edit().putString("saved_games", "pas du json").commit()
        assertTrue(repository.listGames().isEmpty())
        assertEquals("pas du json", prefs.getString("saved_games_unreadable_backup", null))
    }

    @Test
    fun uneEntreeAbimeeDeLAncienJournalEstIgnoreeSeuleEtLeTexteBrutEstConserve() {
        val bonne = game("ok", 1).toJson()
        val brut = """[$bonne, {"id": "cassee"}]"""
        prefs.edit().putString("saved_games", brut).commit()

        assertEquals(listOf("ok"), repository.listGames().map { it.id })
        assertEquals(brut, prefs.getString("saved_games_unreadable_backup", null))
    }

    @Test
    fun enregistrerApresUnAncienJournalIllisibleNEcrasePasLaCopieDeSecurite() {
        prefs.edit().putString("saved_games", "pas du json").commit()
        repository.saveGame(game("neuve", 5))

        assertEquals(listOf("neuve"), repository.listGames().map { it.id })
        assertEquals("pas du json", prefs.getString("saved_games_unreadable_backup", null))
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
        journalDir.deleteRecursively()
        assertTrue(repository.listGames().isEmpty())

        val bilan = repository.importJson(texte) { }
        assertEquals(2, bilan?.added)
        assertEquals(listOf("b", "a"), repository.listGames().map { it.id })
    }

    @Test
    fun importerAuDelaDeLaLimiteDUnJeuEcarteLesPlusAnciennesDeCeJeuSeulement() {
        val limite = GameHistoryRepository.MAX_SAVED_GAMES
        repository.saveGame(game("autre", 1, gameId = "belote"))
        val fichier = JournalBackup.export(
            (0 until limite + 2).map { i -> game("y$i", 100L + i, gameId = "yams") },
            exportedAt = 0L
        )

        val bilan = repository.importJson(fichier) { }

        assertEquals(limite + 2, bilan?.added)
        assertEquals(2, bilan?.droppedForSpace)
        val parties = repository.listGames()
        assertEquals(limite, parties.count { it.gameRules.id == "yams" })
        assertTrue("autre" in parties.map { it.id })
        assertFalse("y0" in parties.map { it.id })
    }
}
