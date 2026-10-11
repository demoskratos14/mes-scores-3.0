package com.aventure.messcores

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Feuilles de catégories personnalisées : lecture du texte, règles, saisie et sauvegarde. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class CustomSheetTest {

    private val wingspanRows = listOf(
        CustomSheetRow("Oiseaux"),
        CustomSheetRow("Bonus", listOf(0, 3, 5)),
        CustomSheetRow("Œufs")
    )

    private fun rules(rows: List<CustomSheetRow> = wingspanRows) = GameRules(
        id = "custom_1",
        name = "Mon jeu",
        scoreMode = ScoreMode.TABLE,
        sheet = ScoreSheets.CUSTOM,
        sheetRows = rows
    )

    private fun draft(block: CustomGameDraft.() -> CustomGameDraft = { this }) = CustomGameDraft(
        name = "Mon jeu",
        scoreMode = ScoreMode.TABLE,
        variant = GameVariant.SHEET,
        sheet = ScoreSheets.CUSTOM,
        sheetRows = wingspanRows
    ).block()

    // ---------- Lecture du texte ----------

    @Test
    fun uneLigneSansDeuxPointsEstUneCategorieAScoreLibre() {
        val rows = CustomSheetText.parse("Oiseaux\nŒufs")
        assertEquals(listOf(CustomSheetRow("Oiseaux"), CustomSheetRow("Œufs")), rows)
        assertNull(rows[0].options)
    }

    @Test
    fun lesValeursApresLesDeuxPointsDeviennentDesBoutons() {
        val rows = CustomSheetText.parse("Pièces : 0, 1, 2, 3")
        assertEquals(listOf(CustomSheetRow("Pièces", listOf(0, 1, 2, 3))), rows)
    }

    @Test
    fun lesValeursPeuventEtreSepareesParDesVirgulesDesPointsVirgulesOuDesEspaces() {
        assertEquals(listOf(0, 5, 10), CustomSheetText.parse("A: 0;5;10")[0].options)
        assertEquals(listOf(0, 5, 10), CustomSheetText.parse("A: 0 5 10")[0].options)
        assertEquals(listOf(0, 5, 10), CustomSheetText.parse("A:0,5 ; 10")[0].options)
    }

    @Test
    fun lesValeursNegativesSontAcceptees() {
        assertEquals(listOf(-1, 0, 1), CustomSheetText.parse("Pénalité : -1, 0, 1")[0].options)
        // Le vrai signe moins (U+2212) est reconnu, comme dans l'affichage des scores.
        assertEquals(listOf(-2, 0), CustomSheetText.parse("Pénalité : −2, 0")[0].options)
    }

    @Test
    fun desValeursIllisiblesOuAbsentesRamenentAUnScoreLibre() {
        assertNull(CustomSheetText.parse("Oiseaux :")[0].options)
        assertNull(CustomSheetText.parse("Oiseaux : abc, !")[0].options)
        assertEquals(listOf(2, 4), CustomSheetText.parse("Oiseaux : 2, abc, 4, 2")[0].options)
    }

    @Test
    fun lesLignesSansNomEtLesEspacesSontIgnores() {
        val rows = CustomSheetText.parse("\n  Oiseaux  \n   \n : 1, 2\n  Bonus : 3  \n")
        assertEquals(listOf("Oiseaux", "Bonus"), rows.map { it.name })
        assertEquals(listOf(3), rows[1].options)
    }

    @Test
    fun lesLimitesDeLignesEtDeLongueurSontRespectees() {
        val many = (1..60).joinToString("\n") { "Cat $it" }
        assertEquals(CustomSheetText.MAX_ROWS, CustomSheetText.parse(many).size)
        val longName = "x".repeat(100)
        assertEquals(CustomSheetText.MAX_NAME_LENGTH, CustomSheetText.parse(longName)[0].name.length)
        val manyValues = "A : " + (1..100).joinToString(",")
        assertEquals(CustomSheetText.MAX_OPTIONS, CustomSheetText.parse(manyValues)[0].options!!.size)
    }

    @Test
    fun formaterPuisLireRetrouveLesMemesCategories() {
        val text = CustomSheetText.format(wingspanRows)
        assertEquals("Oiseaux\nBonus : 0, 3, 5\nŒufs", text)
        assertEquals(wingspanRows, CustomSheetText.parse(text))
    }

    // ---------- Règles du jeu ----------

    @Test
    fun lesLignesDeLaFeuillePersonnaliseeViennentDuJeu() {
        val rows = ScoreSheets.rows(rules())!!
        assertEquals(listOf("Oiseaux", "Bonus", "Œufs"), rows.map { it.labelText })
        assertEquals(listOf(null, listOf(0, 3, 5), null), rows.map { it.options })
        assertEquals(3, rows.map { it.id }.toSet().size)
        // Pas de partie haute : aucun bonus.
        assertFalse(rows.any { it.upper })
        assertFalse(ScoreSheets.hasBonus(ScoreSheets.CUSTOM))
    }

    @Test
    fun lesFeuillesIntegreesNeSontPasTouchees() {
        assertEquals(ScoreSheets.rows(ScoreSheets.YAMS), ScoreSheets.rows(GameRules(id = "y", name = "Yams", sheet = ScoreSheets.YAMS)))
        assertNull(ScoreSheets.rows(GameRules(id = "g", name = "Libre")))
    }

    @Test
    fun leFormulaireDonneUneFeuillePersonnalisee() {
        val r = draft().toRules("id", "Sans nom")
        assertEquals(ScoreSheets.CUSTOM, r.sheet)
        assertEquals(wingspanRows, r.sheetRows)
        assertEquals(GameVariant.SHEET, r.mechanic())
        assertEquals(listOf(GameRules.NORMAL_MULTIPLIER), r.multipliers)
        assertEquals(EndCondition(), r.endCondition)
        assertFalse(r.allowNegativeScores)
    }

    @Test
    fun uneFeuillePersonnaliseeSansCategorieRetombeSurUnTableauClassique() {
        val r = draft { copy(sheetRows = emptyList()) }.toRules("id", "Sans nom")
        assertNull(r.sheet)
        assertTrue(r.sheetRows.isEmpty())
        assertEquals(GameVariant.NONE, r.mechanic())
    }

    @Test
    fun lesScoresNegatifsSontUnReglageDeLaFeuillePersonnalisee() {
        assertTrue(draft { copy(allowNegative = true) }.toRules("id", "x").allowNegativeScores)
        // Les feuilles intégrées gardent des scores positifs.
        assertFalse(draft { copy(sheet = ScoreSheets.YAMS, allowNegative = true) }.toRules("id", "x").allowNegativeScores)
    }

    @Test
    fun lesCategoriesSontOubliesSiLaFeuilleChoisieEstUneFeuilleIntegree() {
        val r = draft { copy(sheet = ScoreSheets.QWIXX) }.toRules("id", "x")
        assertEquals(ScoreSheets.QWIXX, r.sheet)
        assertTrue(r.sheetRows.isEmpty())
    }

    // ---------- Sauvegarde ----------

    @Test
    fun lesCategoriesSurviventAuAllerRetourJson() {
        val reloaded = GameRules.fromJson(JSONObject(rules().toJson().toString()))
        assertEquals(ScoreSheets.CUSTOM, reloaded.sheet)
        assertEquals(wingspanRows, reloaded.sheetRows)
        assertEquals(rules(), reloaded)
    }

    @Test
    fun unAncienJeuSansCategoriesSeLitToujours() {
        val old = GameRules(id = "y", name = "Yams", sheet = ScoreSheets.YAMS).toJson()
        assertFalse(old.has("sheetRows"))
        val reloaded = GameRules.fromJson(old)
        assertTrue(reloaded.sheetRows.isEmpty())
        assertEquals(ScoreSheets.YAMS, reloaded.sheet)
    }

    // ---------- Partie ----------

    private fun newGame(vararg names: String) = ScoreViewModel().also { it.initGame(names.toList(), rules()) }

    @Test
    fun laPartieAUneLigneParCategorieEtSeTermineQuandToutEstRempli() {
        val vm = newGame("Anna", "Ben")
        assertEquals(3, vm.scores.size)
        assertEquals(3, vm.sheetRows!!.size)
        assertFalse(vm.sheetHasBonus)
        for (row in 0..2) {
            for (player in 0..1) {
                assertFalse(vm.isGameOver())
                vm.setCell(row, player, row + player, false)
            }
        }
        assertTrue(vm.isGameOver())
        assertEquals(3, vm.scores.size)
    }

    @Test
    fun leTotalEstLaSommeDesCategoriesSansBonus() {
        val vm = newGame("Anna", "Ben")
        vm.setCell(0, 0, 40, false)
        vm.setCell(1, 0, 5, false)
        vm.setCell(2, 0, 7, false)
        vm.setCell(0, 1, 100, false)
        assertEquals(52, vm.totalFor(0))
        assertEquals(100, vm.totalFor(1))
        assertEquals(0, vm.sheetBonusFor(0))
    }

    @Test
    fun lePlusBasGagneSAppliqueAUneFeuillePersonnalisee() {
        val low = rules().copy(lowestWins = true)
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), low) }
        // Toutes les cases remplies (un 0 compte) : la partie est terminée, Ben a le total le plus bas.
        for (row in 0..2) {
            vm.setCell(row, 0, if (row == 0) 10 else 0, false)
            vm.setCell(row, 1, if (row == 0) 3 else 0, false)
        }
        assertEquals(listOf(1), vm.winners())
    }

    @Test
    fun laPartieEnregistreeConserveLesCategories() {
        val vm = newGame("Anna", "Ben")
        vm.setCell(0, 0, 12, false)
        vm.setCell(1, 0, 5, false)
        val saved = vm.snapshot()
        val reloaded = SavedGame.fromJson(JSONObject(saved.toJson().toString()))
        assertEquals(wingspanRows, reloaded.gameRules.sheetRows)
        val other = ScoreViewModel().also { it.loadFromSaved(reloaded) }
        assertEquals(listOf("Oiseaux", "Bonus", "Œufs"), other.sheetRows!!.map { it.labelText })
        assertEquals(vm.totals, other.totals)
    }
}
