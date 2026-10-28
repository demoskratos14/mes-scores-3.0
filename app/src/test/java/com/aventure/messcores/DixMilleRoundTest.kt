package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le Dix mille jouable tour par tour : points des dés, Farkle, ouverture, six dés qui marquent, inscription au tableau. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DixMilleRoundTest {

    /** Une partie dont les dés sortent dans l'ordre donné (les dés relancés seulement, de gauche à droite). */
    private fun game(players: Int, startPlayer: Int = 0, vararg dice: Int): DixMilleRound {
        val queue = ArrayDeque(dice.toList())
        return DixMilleRound(players, startPlayer) { queue.removeFirst() }
    }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_dixmille" }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    // ---------- Points des dés ----------

    @Test
    fun unUnValentCentEtUnCinqValentCinquante() {
        assertEquals(100, DixMilleScoring.score(listOf(1)))
        assertEquals(50, DixMilleScoring.score(listOf(5)))
        assertEquals(200, DixMilleScoring.score(listOf(1, 1)))
        assertEquals(100, DixMilleScoring.score(listOf(5, 5)))
        assertEquals(150, DixMilleScoring.score(listOf(1, 5)))
    }

    @Test
    fun unBrelanVautCentFoisSaValeurEtMilleSiCEstDesUns() {
        assertEquals(400, DixMilleScoring.score(listOf(4, 4, 4)))
        assertEquals(200, DixMilleScoring.score(listOf(2, 2, 2)))
        assertEquals(600, DixMilleScoring.score(listOf(6, 6, 6)))
        assertEquals(500, DixMilleScoring.score(listOf(5, 5, 5)))
        assertEquals(1000, DixMilleScoring.score(listOf(1, 1, 1)))
    }

    @Test
    fun chaqueDeIdentiqueEnPlusDoubleLeTotal() {
        assertEquals(800, DixMilleScoring.score(listOf(4, 4, 4, 4)))
        assertEquals(1600, DixMilleScoring.score(listOf(4, 4, 4, 4, 4)))
        assertEquals(3200, DixMilleScoring.score(listOf(4, 4, 4, 4, 4, 4)))
        assertEquals(2000, DixMilleScoring.score(listOf(1, 1, 1, 1)))
        assertEquals(1000, DixMilleScoring.score(listOf(5, 5, 5, 5)))
    }

    @Test
    fun lesPointsDeDesDifferentsSAdditionnent() {
        assertEquals(1100, DixMilleScoring.score(listOf(1, 1, 1, 5, 5)))
        assertEquals(400, DixMilleScoring.score(listOf(3, 3, 3, 5, 5)))
        assertEquals(1100, DixMilleScoring.score(listOf(5, 5, 5, 5, 1)))
        assertEquals(900, DixMilleScoring.score(listOf(5, 5, 5, 4, 4, 4)))
    }

    @Test
    fun laSuiteEtLesTroisPairesValentMilleCinqCents() {
        assertEquals(1500, DixMilleScoring.score(listOf(3, 1, 2, 6, 5, 4)))
        assertEquals(1500, DixMilleScoring.score(listOf(2, 2, 6, 6, 3, 3)))
        // Les deux ne comptent qu'avec les six dés : 1-2-3-4-5 ne rapporte que son 1 et son 5, qui ne sont pas seuls.
        assertNull(DixMilleScoring.score(listOf(1, 2, 3, 4, 5)))
        // Quatre dés identiques et une paire ne sont pas trois paires.
        assertNull(DixMilleScoring.score(listOf(2, 2, 2, 2, 3, 3)))
    }

    @Test
    fun unDeQuiNeRapporteRienRendLaSelectionInvalide() {
        assertNull(DixMilleScoring.score(listOf(2)))
        assertNull(DixMilleScoring.score(listOf(1, 3)))
        assertNull(DixMilleScoring.score(listOf(4, 4, 1)))
        assertNull(DixMilleScoring.score(emptyList()))
    }

    @Test
    fun unLancerSansUnSeulDeQuiRapporteEstUnFarkle() {
        assertFalse(DixMilleScoring.canScore(listOf(2, 3, 4, 6, 6, 2)))
        assertFalse(DixMilleScoring.canScore(listOf(2, 3, 4)))
        assertTrue(DixMilleScoring.canScore(listOf(2, 3, 4, 6, 6, 1)))
        assertTrue(DixMilleScoring.canScore(listOf(2, 3, 4, 6, 6, 5)))
        assertTrue(DixMilleScoring.canScore(listOf(2, 2, 2, 3, 4, 6)))
        assertTrue(DixMilleScoring.canScore(listOf(2, 2, 3, 3, 4, 4)))
        assertTrue(DixMilleScoring.canScore(listOf(1, 2, 3, 4, 5, 6)))
        // Sur moins de six dés, ni suite ni trois paires.
        assertFalse(DixMilleScoring.canScore(listOf(2, 2, 3, 3)))
    }

    // ---------- Un tour ----------

    @Test
    fun onChoisitLesDesQuiRapportentPuisOnGardeLesPoints() {
        val vm = viewModel()
        // Lancer : 1 5 2 3 4 6. On choisit le 1 et le 5.
        val game = game(2, 0, 1, 5, 2, 3, 4, 6)
        game.roll()
        assertFalse(game.farkle)
        assertNull(game.selectionPoints)
        game.toggle(0)
        game.toggle(1)
        assertEquals(150, game.selectionPoints)
        assertEquals(150, game.pending)
        // 150 points, mais le joueur n'a pas encore ouvert : il ne peut pas garder.
        assertFalse(game.canKeep(opened = false))
        assertTrue(game.canKeep(opened = true))
        assertEquals(-1, game.keep(vm, opened = false))
        assertEquals(150, game.keep(vm, opened = true))
        assertEquals(listOf(150, null), vm.scores[0].map { it.baseValue })
        assertEquals(1, game.current)
        assertFalse(game.turn.hasRolled)
        assertEquals(0, game.turnPoints)
    }

    @Test
    fun unDeQuiNeRapporteRienNePeutPasEtreChoisiNiRelance() {
        // Lancer : 1 2 3 4 6 6.
        val game = game(2, 0, 1, 2, 3, 4, 6, 6)
        game.roll()
        game.toggle(0)
        game.toggle(1)
        assertNull(game.selectionPoints)
        assertFalse(game.canRoll)
        // Le choix invalide ne se relance pas.
        game.roll()
        assertEquals(1, game.turn.rollsUsed)
        // On retire le 2 : le choix redevient valable.
        game.toggle(1)
        assertEquals(100, game.selectionPoints)
        assertTrue(game.canRoll)
    }

    @Test
    fun onDoitChoisirAuMoinsUnDePourRelancer() {
        val game = game(2, 0, 1, 2, 3, 4, 6, 6)
        game.roll()
        assertFalse(game.canRoll)
    }

    @Test
    fun lesDesChoisisSontMisDeCoteEtSeulsLesAutresSontRelances() {
        // Lancer 1 : 1 5 2 3 4 6 ; on garde le 1 ; relance des 5 autres : 4 4 4 2 2.
        val game = game(2, 0, 1, 5, 2, 3, 4, 6, 4, 4, 4, 2, 2)
        game.roll()
        game.toggle(0)
        game.roll()
        assertEquals(100, game.turnPoints)
        assertEquals(setOf(0), game.locked)
        assertEquals(listOf(1, 4, 4, 4, 2, 2), game.turn.values)
        // Le dé mis de côté ne se choisit plus.
        game.toggle(0)
        assertEquals(setOf(0), game.turn.held)
        // Les trois 4 : 400 points, 500 au total.
        game.toggle(1)
        game.toggle(2)
        game.toggle(3)
        assertEquals(400, game.selectionPoints)
        assertEquals(500, game.pending)
        assertTrue(game.canKeep(opened = false))
    }

    @Test
    fun unLancerSansPointEstUnFarkleEtLesPointsDuTourSontPerdus() {
        val vm = viewModel()
        // Lancer 1 : 5 1 2 3 4 6 ; on garde 1 et 5 ; relance des 4 autres : 2 3 4 6 (aucun point).
        val game = game(2, 0, 5, 1, 2, 3, 4, 6, 2, 3, 4, 6)
        game.roll()
        game.toggle(0)
        game.toggle(1)
        game.roll()
        assertEquals(150, game.turnPoints)
        assertTrue(game.farkle)
        assertFalse(game.canRoll)
        assertEquals(-1, game.keep(vm, opened = true))
        game.pass(vm)
        assertEquals(listOf(0, null), vm.scores[0].map { it.baseValue })
        assertEquals(1, game.current)
        assertEquals(0, game.turnPoints)
        assertTrue(game.locked.isEmpty())
    }

    @Test
    fun onNePeutPasPasserSansFarkle() {
        val vm = viewModel()
        val game = game(2, 0, 1, 2, 3, 4, 6, 6)
        game.roll()
        game.pass(vm)
        assertEquals(0, game.current)
        assertEquals(0, vm.roundsPlayed())
    }

    @Test
    fun sixDesQuiRapportentSontTousRelances() {
        // Lancer 1 : 1 1 1 5 5 5 (1 000 + 500) ; tous choisis ; six nouveaux dés : 2 3 4 6 6 1.
        val game = game(2, 0, 1, 1, 1, 5, 5, 5, 2, 3, 4, 6, 6, 1)
        game.roll()
        (0..5).forEach { game.toggle(it) }
        assertEquals(1500, game.selectionPoints)
        game.roll()
        assertTrue(game.hotDice)
        assertEquals(1500, game.turnPoints)
        assertTrue(game.locked.isEmpty())
        assertTrue(game.turn.held.isEmpty())
        assertEquals(listOf(2, 3, 4, 6, 6, 1), game.turn.values)
        assertFalse(game.farkle)
        // Les points du tour se gardent avec le 1 de ce lancer.
        game.toggle(5)
        assertEquals(1600, game.pending)
        assertTrue(game.canKeep(opened = false))
    }

    @Test
    fun surLesSixDesQuiRapportentOnPeutAussiGarderSansRelancer() {
        val vm = viewModel()
        val game = game(2, 0, 1, 1, 1, 5, 5, 5)
        game.roll()
        (0..5).forEach { game.toggle(it) }
        assertEquals(1500, game.keep(vm, opened = false))
        assertEquals(listOf(1500, null), vm.scores[0].map { it.baseValue })
    }

    @Test
    fun apresUnDeMisDeCoteLesAutresDesNeFontPlusDeSuite() {
        // Un 1 est mis de côté : les cinq autres dés (2 3 4 6 6) ne rapportent rien, et une suite exigerait les six dés.
        val game = game(2, 0, 1, 5, 2, 3, 4, 6, 2, 3, 4, 6, 6)
        game.roll()
        game.toggle(0)
        game.roll()
        assertTrue(game.farkle)
    }

    @Test
    fun apresLeDernierJoueurLaMainRevientAuPremier() {
        val vm = viewModel(listOf("Anna", "Ben", "Cléo"))
        val game = game(3, 0, 1, 1, 1, 5, 5, 5, 1, 1, 1, 5, 5, 5, 1, 1, 1, 5, 5, 5)
        repeat(3) {
            game.roll()
            (0..5).forEach { game.toggle(it) }
            game.keep(vm, opened = true)
        }
        assertEquals(0, game.current)
    }

    // ---------- Sauvegarde et partage ----------

    @Test
    fun lEtatSePartageEtSeRetablitTelQuel() {
        val game = game(3, 1, 1, 5, 2, 3, 4, 6, 4, 4, 4, 2, 2)
        game.roll()
        game.toggle(0)
        game.roll()
        game.toggle(1)
        val copy = DixMilleRound(3)
        copy.restoreFrom(game.snapshot())
        assertEquals(game.snapshot(), copy.snapshot())
        assertEquals(game.current, copy.current)
        assertEquals(game.turnPoints, copy.turnPoints)
        assertEquals(game.locked, copy.locked)
        assertEquals(game.turn.values, copy.turn.values)
        assertEquals(game.selected, copy.selected)
    }

    @Test
    fun unEtatInvalideRemetLaPartieAuDepart() {
        val game = DixMilleRound(3, startPlayer = 2)
        // Des dés mis de côté sans lancer : refusé.
        game.restoreFrom(listOf(3, 1, 500, 0, 1, 0, 0))
        assertEquals(2, game.current)
        assertEquals(0, game.turnPoints)
        game.restoreFrom(emptyList())
        assertEquals(2, game.current)
        // Un état d'une partie à un autre nombre de joueurs est refusé.
        game.restoreFrom(DixMilleRound(2).snapshot())
        assertEquals(2, game.current)
    }

    // ---------- Tableau des scores et partie ----------

    @Test
    fun laPartieAUnObjectifDeDixMillePointsEtUnDernierTourPourLesAutres() {
        val rules = rules()
        assertEquals(10000, rules.endCondition.scoreThreshold)
        assertFalse(rules.endCondition.stopImmediately)
        val vm = viewModel(listOf("Anna", "Ben", "Cléo"))
        vm.setCell(0, 0, 10000, false)
        // Anna a atteint l'objectif, mais Ben et Cléo n'ont pas encore joué la manche.
        assertFalse(vm.isGameOver())
        vm.setCell(0, 1, 600, false)
        assertFalse(vm.isGameOver())
        vm.setCell(0, 2, 0, false)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun lesPointsGardesVontDansLaProchaineCaseLibreDuJoueur() {
        val vm = viewModel()
        val game = game(2, 0, 1, 1, 1, 5, 5, 5, 1, 1, 1, 5, 5, 5, 1, 1, 1, 5, 5, 5)
        repeat(3) {
            game.roll()
            (0..5).forEach { game.toggle(it) }
            game.keep(vm, opened = true)
        }
        assertEquals(listOf(1500, 1500, null), vm.scores.take(3).map { it[0].baseValue })
        assertEquals(listOf(1500, null, null), vm.scores.take(3).map { it[1].baseValue })
        assertEquals(listOf(3000, 1500), vm.totals)
    }

    @Test
    fun uneReprisePartDuPremierJoueurQuiNAPasEncoreJoueDansLaManche() {
        val vm = viewModel(listOf("Anna", "Ben", "Cléo"))
        assertEquals(0, vm.dixMilleRound().current)

        val resumed = viewModel(listOf("Anna", "Ben", "Cléo"))
        resumed.setCell(0, 0, 500, false)
        resumed.setCell(0, 1, 0, false)
        assertEquals(2, resumed.dixMilleRound().current)

        val fullRound = viewModel(listOf("Anna", "Ben"))
        fullRound.setCell(0, 0, 500, false)
        fullRound.setCell(0, 1, 0, false)
        assertEquals(0, fullRound.dixMilleRound().current)
    }

    @Test
    fun laPartieEstGardeeQuandOnPasseParLesScoresPuisRevientAuxDes() {
        val vm = viewModel()
        val game = vm.dixMilleRound()
        // L'écran des dés se recompose (retour des scores) : c'est la même partie.
        assertSame(game, vm.dixMilleRound())
    }

    @Test
    fun uneNouvellePartieOuLaFermetureEffacentLaPartieDeDixMille() {
        val rules = rules()
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), rules) }
        val game = vm.dixMilleRound()

        vm.initGame(listOf("Anna", "Ben"), rules)
        val fresh = vm.dixMilleRound()
        assertNotSame(game, fresh)

        vm.closeGame()
        assertNotSame(fresh, vm.dixMilleRound())
    }

    @Test
    fun leDixMilleSeJoueSurLaPageDesDesSansFeuilleDeScore() {
        assertTrue(rules().isDiceOnly())
    }
}
