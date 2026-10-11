package com.aventure.messcores

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jeux personnalisés : le formulaire peut reprendre les mécaniques des jeux prédéfinis. */
class CustomGameDraftTest {

    private val contracts = listOf(
        GameRules.NORMAL_MULTIPLIER.copy(label = "Petite"),
        ScoreMultiplier("tarot_garde", "Garde", 2),
        ScoreMultiplier("tarot_garde_sans", "Garde sans", 4),
        ScoreMultiplier("tarot_garde_contre", "Garde contre", 6)
    )

    private fun draft(
        mode: ScoreMode = ScoreMode.TABLE,
        variant: GameVariant = GameVariant.NONE,
        block: CustomGameDraft.() -> CustomGameDraft = { this }
    ) = CustomGameDraft(name = "Mon jeu", scoreMode = mode, variant = variant, tarotContracts = contracts).block()

    @Test
    fun unFormulaireSansMecaniqueDonneUnJeuClassiqueSansBornesParticulieres() {
        val rules = draft().toRules("id", "Sans nom")
        assertEquals("Mon jeu", rules.name)
        assertEquals(GameRules.DEFAULT_MIN_PLAYERS, rules.minPlayers)
        assertEquals(GameRules.DEFAULT_MAX_PLAYERS, rules.maxPlayers)
        assertNull(rules.sheet)
        assertNull(rules.bidScoring)
        assertNull(rules.phases)
        assertFalse(rules.tarotScoring)
        assertEquals(listOf(GameRules.NORMAL_MULTIPLIER), rules.multipliers)
    }

    @Test
    fun unNomVidePrendLeNomParDefaut() {
        assertEquals("Sans nom", draft { copy(name = "  ") }.toRules("id", "Sans nom").name)
    }

    @Test
    fun leNombreDeJoueursEstBorneEtCoherent() {
        val rules = draft { copy(minPlayers = 4, maxPlayers = 2) }.toRules("id", "x")
        assertEquals(4, rules.minPlayers)
        assertEquals(4, rules.maxPlayers) // le maximum ne passe jamais sous le minimum
        assertEquals(1, draft { copy(minPlayers = 0) }.toRules("id", "x").minPlayers)
    }

    @Test
    fun uneFeuilleImposeSesPropresReglages() {
        val rules = draft(variant = GameVariant.SHEET) {
            copy(sheet = ScoreSheets.QWIXX, allowNegative = true, customMultipliers = listOf(ScoreMultiplier("c", "Double", 2)),
                endCondition = EndCondition(EndConditionType.ROUND_COUNT, roundCount = 3))
        }.toRules("id", "x")
        assertEquals(ScoreSheets.QWIXX, rules.sheet)
        assertFalse(rules.allowNegativeScores)
        assertEquals(listOf(GameRules.NORMAL_MULTIPLIER), rules.multipliers)
        assertEquals(EndConditionType.NONE, rules.endCondition.type)
    }

    @Test
    fun uneAnnonceEtDesPlisDonneLeScoreDuJeuPredefini() {
        val rules = draft(variant = GameVariant.BID) { copy(bidScoring = BidScoring.OH_HELL) }.toRules("id", "x")
        assertEquals(BidScoring.OH_HELL, rules.bidScoring)
        assertTrue(rules.allowNegativeScores)
    }

    @Test
    fun leScoreExactReprendLeMolkky() {
        val rules = draft(variant = GameVariant.EXACT_TARGET) {
            copy(exactTarget = 50, exactReset = 25, lowestWins = true)
        }.toRules("id", "x")
        assertEquals(50, rules.exactTarget)
        assertEquals(25, rules.exactReset)
        assertFalse(rules.lowestWins)
        assertEquals(EndConditionType.SCORE_THRESHOLD, rules.endCondition.type)
        assertEquals(50, rules.endCondition.scoreThreshold)
        assertTrue(rules.endCondition.stopImmediately)
        // Le calcul du total suit : 45 + 8 dépasse 50, on retombe à 25.
        assertEquals(25, rules.tableTotal(listOf(45, 8)))
        assertEquals(50, rules.tableTotal(listOf(45, 5)))
    }

    @Test
    fun leDecompteReprendLesFlechettes() {
        val rules = draft(variant = GameVariant.COUNTDOWN) { copy(countdownFrom = 301, lowestWins = false) }.toRules("id", "x")
        assertEquals(301, rules.countdownFrom)
        assertTrue(rules.lowestWins)
        assertEquals(EndConditionType.NONE, rules.endCondition.type)
        assertEquals(201, rules.tableTotal(listOf(60, 40)))
    }

    @Test
    fun unDecompteOuUnScoreExactSansValeurRetombeSurLeJeuClassique() {
        val countdown = draft(variant = GameVariant.COUNTDOWN) { copy(countdownFrom = null) }.toRules("id", "x")
        assertNull(countdown.countdownFrom)
        val exact = draft(variant = GameVariant.EXACT_TARGET) { copy(exactTarget = 0) }.toRules("id", "x")
        assertNull(exact.exactTarget)
        assertNull(exact.exactReset)
    }

    @Test
    fun lesPhasesNeConcernentQueLeTableauLibre() {
        val libre = draft { copy(phases = 10, lowestWins = true, endCondition = EndCondition(EndConditionType.ROUND_COUNT, roundCount = 5)) }
            .toRules("id", "x")
        assertEquals(10, libre.phases)
        assertEquals(EndConditionType.NONE, libre.endCondition.type) // la fin vient de la dernière phase
        assertNull(draft(variant = GameVariant.SHEET) { copy(phases = 10) }.toRules("id", "x").phases)
        assertNull(draft(mode = ScoreMode.COUNTER) { copy(phases = 10) }.toRules("id", "x").phases)
        assertNull(draft { copy(phases = 0) }.toRules("id", "x").phases)
    }

    @Test
    fun leTarotPrendSesQuatreContratsEtJoueDeTroisACinq() {
        val rules = draft(mode = ScoreMode.VARIABLE_TEAMS, variant = GameVariant.TAROT) {
            copy(minPlayers = 2, maxPlayers = 9, customMultipliers = listOf(ScoreMultiplier("c", "Autre", 3)))
        }.toRules("id", "x")
        assertTrue(rules.tarotScoring)
        assertTrue(rules.usesTarotScoring)
        assertEquals(contracts, rules.multipliers)
        assertEquals(3, rules.minPlayers)
        assertEquals(5, rules.maxPlayers)
        assertTrue(rules.allowNegativeScores)
    }

    @Test
    fun uneMecaniqueIncompatibleAvecLeModeEstIgnoree() {
        assertFalse(draft(mode = ScoreMode.COUNTER, variant = GameVariant.TAROT).toRules("id", "x").tarotScoring)
        assertNull(draft(mode = ScoreMode.VARIABLE_TEAMS, variant = GameVariant.SHEET).toRules("id", "x").sheet)
        assertFalse(draft(variant = GameVariant.TAROT).toRules("id", "x").tarotScoring)
    }

    @Test
    fun lesMecaniquesProposeesDependentDuMode() {
        assertEquals(listOf(GameVariant.NONE), variantsFor(ScoreMode.COUNTER))
        assertTrue(GameVariant.TAROT in variantsFor(ScoreMode.VARIABLE_TEAMS))
        assertFalse(GameVariant.TAROT in variantsFor(ScoreMode.TABLE))
        assertTrue(GameVariant.SHEET in variantsFor(ScoreMode.TABLE))
    }

    @Test
    fun lesReglesSurviventALaSauvegardeEtOnRetrouveLaMecanique() {
        val variants = listOf(
            draft(variant = GameVariant.SHEET) { copy(sheet = ScoreSheets.BOWLING) },
            draft(variant = GameVariant.BID),
            draft(variant = GameVariant.EXACT_TARGET) { copy(exactTarget = 50, exactReset = 25) },
            draft(variant = GameVariant.COUNTDOWN) { copy(countdownFrom = 501) },
            draft(mode = ScoreMode.VARIABLE_TEAMS, variant = GameVariant.TAROT),
            draft { copy(phases = 8) }
        )
        variants.forEach { d ->
            val rules = d.toRules("id", "x")
            val relu = GameRules.fromJson(JSONObject(rules.toJson().toString()))
            assertEquals(rules, relu)
            assertEquals(d.effectiveVariant(), relu.mechanic())
        }
    }

    @Test
    fun uneAncienneSauvegardeSansTarotRestePossibleEtLeTarotPredefiniGardeSonEcran() {
        val ancien = GameRules.fromJson(JSONObject(GameRules(id = "a", name = "A").toJson().toString()).apply { remove("tarotScoring") })
        assertFalse(ancien.tarotScoring)
        assertTrue(GameRules(id = "builtin_tarot", name = "Tarot").usesTarotScoring)
    }
}
