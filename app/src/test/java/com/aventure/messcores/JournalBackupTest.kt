package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalBackupTest {

    private fun game(id: String, savedAt: Long, finished: Boolean = false, score: Int = 10) = SavedGame(
        id = id,
        savedAt = savedAt,
        gameRules = GameRules(id = "g", name = "Jeu"),
        players = listOf("Anna", "Ben"),
        playerColorsArgb = listOf(1, 2),
        cellSnapshots = listOf(
            listOf(
                CellSnapshot(score, false, GameRules.NORMAL_MULTIPLIER_ID),
                CellSnapshot(3, false, GameRules.NORMAL_MULTIPLIER_ID)
            )
        ),
        isFinished = finished
    )

    @Test
    fun exportPuisImportRendLesMemesParties() {
        val original = listOf(game("a", 100, score = 12), game("b", 200, finished = true, score = 40))
        val parsed = JournalBackup.parse(JournalBackup.export(original, exportedAt = 999))
        assertTrue(parsed is JournalBackup.ParseResult.Ok)
        parsed as JournalBackup.ParseResult.Ok
        assertEquals(0, parsed.invalid)
        assertEquals(listOf("a", "b"), parsed.games.map { it.id })
        assertEquals(listOf(100L, 200L), parsed.games.map { it.savedAt })
        assertEquals(listOf(12, 3), parsed.games[0].totals())
        assertEquals(listOf(40, 3), parsed.games[1].totals())
        assertEquals(listOf(false, true), parsed.games.map { it.isFinished })
        assertEquals(listOf("Anna", "Ben"), parsed.games[0].players)
    }

    @Test
    fun unFichierQuiNEstPasUneSauvegardeEstRefuse() {
        assertTrue(JournalBackup.parse("pas du json") is JournalBackup.ParseResult.Error)
        assertTrue(JournalBackup.parse("""{"autre": 1}""") is JournalBackup.ParseResult.Error)
        assertTrue(JournalBackup.parse("""{"format": "mes-scores-journal"}""") is JournalBackup.ParseResult.Error)
    }

    @Test
    fun uneSauvegardeDUneVersionPlusRecenteEstRefusee() {
        val text = """{"format": "mes-scores-journal", "version": 99, "games": []}"""
        assertTrue(JournalBackup.parse(text) is JournalBackup.ParseResult.Error)
    }

    @Test
    fun uneMauvaisePartieEstIgnoreeSansBloquerLesAutres() {
        val good = game("ok", 1).toJson()
        val text = """{"format": "mes-scores-journal", "version": 1, "games": [$good, {"id": "cassée"}]}"""
        val parsed = JournalBackup.parse(text) as JournalBackup.ParseResult.Ok
        assertEquals(listOf("ok"), parsed.games.map { it.id })
        assertEquals(1, parsed.invalid)
    }

    @Test
    fun lImportAjouteSansRienEffacer() {
        val merged = JournalBackup.merge(listOf(game("a", 100)), listOf(game("b", 50)))
        assertEquals(setOf("a", "b"), merged.games.map { it.id }.toSet())
        assertEquals(1, merged.added)
        assertEquals(0, merged.updated)
    }

    @Test
    fun uneVersionPlusRecenteRemplaceUneVersionPlusAncienne() {
        val merged = JournalBackup.merge(listOf(game("a", 100, score = 1)), listOf(game("a", 200, score = 99)))
        assertEquals(1, merged.updated)
        assertEquals(99, merged.games.single().totals()[0])
    }

    @Test
    fun uneVersionPlusAncienneOuIdentiqueNeRemplacePas() {
        val merged = JournalBackup.merge(
            listOf(game("a", 100, score = 1), game("b", 100, score = 2)),
            listOf(game("a", 50, score = 99), game("b", 100, score = 99))
        )
        assertEquals(2, merged.unchanged)
        assertEquals(listOf(1, 2), merged.games.map { it.totals()[0] })
    }

    @Test
    fun laLimiteSupprimeLesTermineesAnciennesAvantLesEnCours() {
        val games = listOf(
            game("enCoursVieux", 1),
            game("finiVieux", 2, finished = true),
            game("finiRecent", 3, finished = true),
            game("enCoursRecent", 4)
        )
        val kept = JournalBackup.trimToLimit(games, limit = 3).map { it.id }
        assertEquals(listOf("enCoursVieux", "finiRecent", "enCoursRecent"), kept)
    }

    @Test
    fun laPartieProtegeeNEstJamaisSupprimee() {
        val games = listOf(game("a", 1, finished = true), game("b", 2, finished = true))
        val kept = JournalBackup.trimToLimit(games, limit = 1, keepId = "a").map { it.id }
        assertEquals(listOf("a"), kept)
    }
}
