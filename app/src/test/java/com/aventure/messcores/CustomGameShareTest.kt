package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Partage d'un jeu personnalisé par fichier : export, lecture, fusion sans écrasement. */
class CustomGameShareTest {

    private fun game(id: String, name: String = "Mon jeu", max: Int = 4) =
        GameRules(
            id = id, name = name, minPlayers = 2, maxPlayers = max, scoreMode = ScoreMode.TABLE,
            sheet = ScoreSheets.CUSTOM, sheetRows = listOf(CustomSheetRow("Oiseaux"), CustomSheetRow("Bonus", listOf(0, 3, 5)))
        )

    private fun ids(): () -> String {
        var n = 0
        return { "new_${++n}" }
    }

    @Test
    fun exportPuisLectureRendLesMemesJeux() {
        val games = listOf(game("custom_1"), game("custom_2", "Autre", 6))
        val parsed = CustomGameShare.parse(CustomGameShare.export(games, 123L)) as CustomGameShare.ParseResult.Ok
        assertEquals(0, parsed.invalid)
        assertEquals(games.map { it.toJson().toString() }, parsed.games.map { it.toJson().toString() })
        assertEquals(listOf("Oiseaux", "Bonus"), parsed.games[0].sheetRows.map { it.name })
        assertEquals(listOf(0, 3, 5), parsed.games[0].sheetRows[1].options)
    }

    @Test
    fun unFichierQuiNEstPasUnJeuEstRefuse() {
        assertEquals(
            CustomGameShare.ParseResult.Error(CustomGameShare.ImportError.NOT_A_GAME_FILE),
            CustomGameShare.parse("pas du json")
        )
        // Une sauvegarde du journal n'est pas un jeu partagé.
        assertEquals(
            CustomGameShare.ParseResult.Error(CustomGameShare.ImportError.NOT_A_GAME_FILE),
            CustomGameShare.parse(JournalBackup.export(emptyList(), 1L))
        )
    }

    @Test
    fun unFichierPlusRecentEstRefuse() {
        val newer = CustomGameShare.export(listOf(game("custom_1")), 1L).replace("\"version\": 1", "\"version\": 99")
        assertEquals(
            CustomGameShare.ParseResult.Error(CustomGameShare.ImportError.NEWER_VERSION),
            CustomGameShare.parse(newer)
        )
    }

    @Test
    fun unJeuIllisibleEstCompteMaisLesAutresPassent() {
        val text = CustomGameShare.export(listOf(game("custom_1")), 1L)
            .replace("\"games\": [", "\"games\": [ {\"nonsense\": true},")
        val parsed = CustomGameShare.parse(text) as CustomGameShare.ParseResult.Ok
        assertEquals(1, parsed.games.size)
        assertEquals(1, parsed.invalid)
    }

    @Test
    fun importerDeuxFoisLeMemeJeuNeLeDupliquePas() {
        val incoming = listOf(game("custom_1"))
        val first = CustomGameShare.merge(emptyList(), incoming, ids())
        assertEquals(1, first.added)
        val second = CustomGameShare.merge(first.games, incoming, ids())
        assertEquals(0, second.added)
        assertEquals(1, second.unchanged)
        assertEquals(1, second.games.size)
    }

    @Test
    fun unJeuModifieDeMemeIdEstAjouteEnCopieSansEcraserLOriginal() {
        val mine = game("custom_1", "Mon jeu")
        val theirs = game("custom_1", "Mon jeu", max = 6)
        val outcome = CustomGameShare.merge(listOf(mine), listOf(theirs), ids())
        assertEquals(1, outcome.added)
        assertEquals(2, outcome.games.size)
        assertEquals(mine, outcome.games[0])
        assertEquals("new_1", outcome.games[1].id)
        assertEquals(6, outcome.games[1].maxPlayers)
    }

    @Test
    fun unIdDeJeuPredefiniDonneUnNouvelId() {
        val outcome = CustomGameShare.merge(emptyList(), listOf(game("builtin_yams")), ids())
        assertEquals("new_1", outcome.games.single().id)
    }

    @Test
    fun nomDeFichierSansCaracteresInterdits() {
        assertEquals("Mon_jeu_de_cartes.json", CustomGameShare.fileName(game("c", "Mon jeu de cartes!")))
        assertEquals("jeu.json", CustomGameShare.fileName(game("c", "???")))
        assertTrue(CustomGameShare.fileName(game("c", "a/b\\c")).none { it == '/' || it == '\\' })
    }
}
