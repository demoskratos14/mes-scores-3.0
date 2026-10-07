package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Les textes sont en français : on fixe la langue pour que les pluriels suivent les règles françaises (0 → singulier).
@Config(qualifiers = "fr")
class ScoreViewModelTest {

    private val names = listOf("Anna", "Ben", "Chloé")

    private fun tableGame(rules: GameRules): ScoreViewModel =
        ScoreViewModel().also { it.initGame(names, rules) }

    /** Saisit un score dans la case (manche, joueur) comme le fait l'écran de score. */
    private fun ScoreViewModel.enter(round: Int, player: Int, value: Int) {
        scores[round][player].baseValue = value
        notifyCellChanged(round)
    }

    private fun threshold(
        value: Int,
        direction: ThresholdDirection = ThresholdDirection.ABOVE,
        stopImmediately: Boolean = true,
        tieBreak: Boolean = false,
        lowestWins: Boolean = false
    ) = GameRules(
        id = "test",
        name = "Test",
        lowestWins = lowestWins,
        endCondition = EndCondition(
            type = EndConditionType.SCORE_THRESHOLD,
            scoreThreshold = value,
            thresholdDirection = direction,
            stopImmediately = stopImmediately,
            tieBreakOnEqualLeaders = tieBreak
        )
    )

    // ---------- Totaux et classement ----------

    @Test
    fun totauxEtRangsDuMeilleurAuMoinsBon() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        vm.enter(0, 0, 10)
        vm.enter(0, 1, 30)
        vm.enter(0, 2, 20)
        vm.enter(1, 0, 5)
        assertEquals(listOf(15, 30, 20), vm.totals)
        assertEquals(listOf(3, 1, 2), vm.ranks)
        assertEquals(listOf(1, 2, 0), vm.rankingOrder())
    }

    @Test
    fun lesExAequoPartagentLeMemeRang() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        vm.enter(0, 0, 10)
        vm.enter(0, 1, 10)
        vm.enter(0, 2, 5)
        assertEquals(listOf(1, 1, 3), vm.ranks)
    }

    @Test
    fun leScoreLePlusBasGagneQuandLesReglesLeDemandent() {
        val vm = tableGame(GameRules(id = "t", name = "T", lowestWins = true))
        vm.enter(0, 0, 10)
        vm.enter(0, 1, 30)
        vm.enter(0, 2, 20)
        assertEquals(listOf(1, 3, 2), vm.ranks)
    }

    @Test
    fun personneNEstEnTeteQuandToutLeMondeEstAEgalite() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        assertFalse(vm.isLeader(0))
        vm.enter(0, 0, 10)
        assertTrue(vm.isLeader(0))
        assertFalse(vm.isLeader(1))
    }

    @Test
    fun scoreNegatifEtMultiplicateur() {
        val rules = GameRules(
            id = "t", name = "T",
            multipliers = listOf(GameRules.NORMAL_MULTIPLIER, ScoreMultiplier("x2", "Double", factor = 2, bonus = 10))
        )
        val vm = tableGame(rules)
        vm.scores[0][0].baseValue = 5
        vm.scores[0][0].multiplierId = "x2"   // 5 × 2 + 10 = 20
        vm.scores[0][1].baseValue = 7
        vm.scores[0][1].isNegative = true      // −7
        assertEquals(20, vm.totalFor(0))
        assertEquals(-7, vm.totalFor(1))
        assertEquals(0, vm.totalFor(2))
    }

    // ---------- Mode compteur ----------

    @Test
    fun modeCompteur() {
        val vm = ScoreViewModel()
        vm.initGame(names, GameRules(id = "c", name = "C", scoreMode = ScoreMode.COUNTER))
        vm.incrementCounter(1, +1)
        vm.incrementCounter(1, +1)
        vm.incrementCounter(2, -1)
        assertEquals(listOf(0, 2, -1), vm.totals)
    }

    // ---------- Fin de partie ----------

    @Test
    fun pasDeFinDePartieSansCondition() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        vm.enter(0, 0, 100000)
        assertFalse(vm.isGameOver())
    }

    @Test
    fun finApresUnNombreDeManches() {
        val rules = GameRules(
            id = "t", name = "T",
            endCondition = EndCondition(type = EndConditionType.ROUND_COUNT, roundCount = 2)
        )
        val vm = tableGame(rules)
        vm.enter(0, 0, 1)
        assertEquals(1, vm.roundsPlayed())
        assertFalse(vm.isGameOver())
        vm.enter(1, 1, 1)
        assertEquals(2, vm.roundsPlayed())
        assertTrue(vm.isGameOver())
    }

    @Test
    fun seuilAtteintArretImmediat() {
        val vm = tableGame(threshold(100))
        vm.enter(0, 0, 99)
        assertFalse(vm.isGameOver())
        vm.enter(0, 0, 100)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
        assertEquals(0, vm.winner())
    }

    @Test
    fun seuilVersLeBas() {
        val vm = tableGame(threshold(-50, direction = ThresholdDirection.BELOW, lowestWins = true))
        vm.enter(0, 0, -49)
        assertFalse(vm.isGameOver())
        vm.enter(0, 0, -50)
        assertTrue(vm.isGameOver())
    }

    @Test
    fun seuilAtteintMaisOnTermineLaManche() {
        val vm = tableGame(threshold(100, stopImmediately = false))
        vm.enter(0, 0, 120)               // seuil franchi, mais Ben et Chloé n'ont pas joué
        assertFalse(vm.isGameOver())
        vm.enter(0, 1, 10)
        assertFalse(vm.isGameOver())
        vm.enter(0, 2, 10)                // manche complète
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun egaliteEnTetePrologeLaPartieSiDemande() {
        val vm = tableGame(threshold(100, tieBreak = true))
        vm.enter(0, 0, 100)
        vm.enter(0, 1, 100)               // Anna et Ben à égalité en tête
        assertFalse(vm.isGameOver())
        vm.enter(1, 1, 5)                 // Ben passe devant
        assertTrue(vm.isGameOver())
        assertEquals(listOf(1), vm.winners())
    }

    @Test
    fun egaliteEnTeteSansDepartageDonnePlusieursGagnants() {
        val vm = tableGame(threshold(100))
        vm.enter(0, 0, 100)
        vm.enter(0, 1, 100)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0, 1), vm.winners())
    }

    @Test
    fun plusDeNouvelleMancheUneFoisLaPartieTerminee() {
        val vm = tableGame(threshold(100))
        val before = vm.scores.size
        vm.enter(vm.scores.lastIndex, 0, 100)
        assertEquals(before, vm.scores.size)
    }

    @Test
    fun uneNouvelleMancheVideApparaitQuandLaDerniereEstEntamee() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        val before = vm.scores.size
        vm.enter(vm.scores.lastIndex, 0, 1)
        assertEquals(before + 1, vm.scores.size)
    }

    // ---------- Annulation ----------

    private fun counterGame(): ScoreViewModel =
        ScoreViewModel().also { it.initGame(names, GameRules(id = "c", name = "C", scoreMode = ScoreMode.COUNTER)) }

    @Test
    fun annulerUnAppuiSurUnCompteurLeRemetAvant() {
        val vm = counterGame()
        assertFalse(vm.canUndo)
        vm.incrementCounter(1, 1)
        vm.incrementCounter(1, 1)
        vm.incrementCounter(0, -1)
        assertEquals(listOf(-1, 2, 0), vm.totals)
        vm.undo()
        assertEquals(listOf(0, 2, 0), vm.totals)
        vm.undo()
        vm.undo()
        assertEquals(listOf(0, 0, 0), vm.totals)
        assertFalse(vm.canUndo)
        vm.undo() // sans effet
        assertEquals(listOf(0, 0, 0), vm.totals)
    }

    @Test
    fun annulerUneSaisieDeCaseRestaureLAncienneValeur() {
        val vm = tableGame(GameRules(id = "t", name = "T", allowNegativeScores = true))
        vm.setCell(0, 0, 10, false)
        vm.setCell(0, 0, 25, true)
        assertEquals(listOf(-25, 0, 0), vm.totals)
        vm.undo()
        assertEquals(listOf(10, 0, 0), vm.totals)
        vm.undo()
        assertEquals(listOf(0, 0, 0), vm.totals)
        assertEquals(null, vm.scores[0][0].baseValue)
    }

    @Test
    fun annulerLaSaisieRetireLaMancheAjouteeAutomatiquement() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        val before = vm.scores.size
        vm.setCell(vm.scores.lastIndex, 0, 7, false)
        assertEquals(before + 1, vm.scores.size)
        vm.undo()
        assertEquals(before, vm.scores.size)
    }

    @Test
    fun annulerNeRetirePasUneMancheDejaRemplie() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        val last = vm.scores.lastIndex
        vm.setCell(last, 0, 7, false)           // ajoute une manche vide
        vm.setCell(last + 1, 1, 3, false)       // la remplit en partie, ce qui en ajoute une autre
        val size = vm.scores.size
        vm.undo()                               // annule la 2e saisie seulement
        assertEquals(size - 1, vm.scores.size)
        assertEquals(7, vm.scores[last][0].baseValue)
        assertEquals(null, vm.scores[last + 1][1].baseValue)
    }

    @Test
    fun uneSaisieIdentiqueNeCreePasDAnnulation() {
        val vm = tableGame(GameRules(id = "t", name = "T"))
        vm.setCell(0, 0, 10, false)
        vm.undo()
        vm.setCell(0, 0, null, false)           // la case était déjà vide
        assertFalse(vm.canUndo)
    }

    @Test
    fun annulerUnChangementDeRegleDeMultiplication() {
        val double = ScoreMultiplier(id = "x2", label = "Double", factor = 2)
        val rules = GameRules(id = "t", name = "T", multipliers = listOf(GameRules.NORMAL_MULTIPLIER, double))
        val vm = tableGame(rules)
        vm.setCell(0, 0, 10, false)
        vm.setMultiplier(0, 0, "x2")
        assertEquals(20, vm.totals[0])
        vm.undo()
        assertEquals(10, vm.totals[0])
    }

    @Test
    fun uneNouvellePartieVideLHistoriqueDAnnulation() {
        val vm = counterGame()
        vm.incrementCounter(0, 1)
        assertTrue(vm.canUndo)
        vm.initGame(names, GameRules(id = "c", name = "C", scoreMode = ScoreMode.COUNTER))
        assertFalse(vm.canUndo)
    }

    // ---------- Partage ----------

    @Test
    fun leTexteDePartageListeLeClassementEtLeVainqueur() {
        val vm = tableGame(
            GameRules(
                id = "t", name = "Mon jeu",
                endCondition = EndCondition(type = EndConditionType.SCORE_THRESHOLD, scoreThreshold = 30)
            )
        )
        vm.enter(0, 0, 10)
        vm.enter(0, 1, 40)
        vm.enter(0, 2, 20)
        val text = vm.shareText(RuntimeEnvironment.getApplication())
        assertTrue(text, text.startsWith("Mon jeu — partie terminée"))
        assertTrue(text, text.indexOf("Ben : 40") < text.indexOf("Chloé : 20"))
        assertTrue(text, text.indexOf("Chloé : 20") < text.indexOf("Anna : 10"))
        assertTrue(text, text.contains("Vainqueur : Ben"))
    }

    // ---------- Équipes variables (Tarot) ----------

    @Test
    fun totauxDesManchesDeTarotAvecEtSansDeltas() {
        val vm = ScoreViewModel()
        vm.initGame(names, GameRules(id = "tarot", name = "Tarot", scoreMode = ScoreMode.VARIABLE_TEAMS))
        vm.appendTeamRound(TeamRound("A", setOf(0), value = 25, deltas = listOf(50, -25, -25)))
        vm.appendTeamRound(TeamRound("B", setOf(1), value = 10))   // règle simple : +10 à Ben, −10 aux autres
        // Anna : +50 puis −10 ; Ben : −25 puis +10 ; Chloé : −25 puis −10.
        assertEquals(listOf(40, -15, -35), vm.totals)
    }
}
