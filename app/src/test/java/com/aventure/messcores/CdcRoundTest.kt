package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le Cul de chouette jouable tour par tour : combinaisons, tours, qui marque ou perd, inscription au tableau. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class CdcRoundTest {

    /** Une partie dont les dés sortent dans l'ordre donné (trois dés par lancer). */
    private fun game(players: Int, vararg dice: Int, startPlayer: Int = 0): CdcRound {
        val queue = ArrayDeque(dice.toList())
        return CdcRound(players, startPlayer) { queue.removeFirst() }
    }

    private fun cdcRules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_cdc" }

    private fun open(vararg names: String): ScoreViewModel =
        ScoreViewModel().also { it.initGame(names.toList(), cdcRules()) }

    // ---------- Combinaisons ----------

    @Test
    fun uneChouetteVautLeCarreDeSesDes() {
        assertEquals(CdcRoll(CdcKind.CHOUETTE, 25, 5), CdcScoring.roll(listOf(5, 2, 5)))
        assertEquals(CdcRoll(CdcKind.CHOUETTE, 1, 1), CdcScoring.roll(listOf(1, 1, 3)))
    }

    @Test
    fun uneVeluteVautDeuxFoisLeCarreDuPlusGrandDe() {
        assertEquals(CdcRoll(CdcKind.VELUTE, 50, 5), CdcScoring.roll(listOf(2, 3, 5)))
        assertEquals(CdcRoll(CdcKind.VELUTE, 50, 5), CdcScoring.roll(listOf(5, 3, 2)))
    }

    @Test
    fun uneChouetteQuiEstAussiUneVeluteEstUneChouetteVelute() {
        assertEquals(CdcRoll(CdcKind.CHOUETTE_VELUTE, 8, 2), CdcScoring.roll(listOf(1, 2, 1)))
        assertEquals(CdcRoll(CdcKind.CHOUETTE_VELUTE, 72, 6), CdcScoring.roll(listOf(3, 3, 6)))
    }

    @Test
    fun troisDesIdentiquesSontUnCulDeChouette() {
        assertEquals(CdcRoll(CdcKind.CUL_DE_CHOUETTE, 50, 1), CdcScoring.roll(listOf(1, 1, 1)))
        assertEquals(CdcRoll(CdcKind.CUL_DE_CHOUETTE, 80, 4), CdcScoring.roll(listOf(4, 4, 4)))
        assertEquals(CdcRoll(CdcKind.CUL_DE_CHOUETTE, 100, 6), CdcScoring.roll(listOf(6, 6, 6)))
    }

    @Test
    fun troisDesQuiSeSuiventSontUneSuite() {
        assertEquals(CdcRoll(CdcKind.SUITE, 10, 3), CdcScoring.roll(listOf(5, 3, 4)))
        assertEquals(CdcRoll(CdcKind.SUITE, 10, 2), CdcScoring.roll(listOf(2, 3, 4)))
    }

    @Test
    fun unDeuxTroisEstUneVeluteEtNonUneSuite() {
        assertEquals(CdcRoll(CdcKind.VELUTE, 18, 3), CdcScoring.roll(listOf(1, 2, 3)))
    }

    @Test
    fun sansCombinaisonCEstUnNeant() {
        assertEquals(CdcKind.NEANT, CdcScoring.roll(listOf(1, 3, 6)).kind)
        assertEquals(CdcKind.NEANT, CdcScoring.roll(listOf(2, 4, 5)).kind)
        assertEquals(CdcKind.NEANT, CdcScoring.roll(emptyList()).kind)
    }

    // ---------- Un tour ----------

    @Test
    fun unJoueurNeLanceQuUneFoisPuisPasseLaMain() {
        val round = game(2, 5, 5, 2)
        assertTrue(round.canRoll)
        assertTrue(round.roll())
        assertFalse(round.canRoll)
        assertTrue(round.turnOver)

        round.endTurn()
        assertEquals(1, round.current)
        assertTrue(round.canRoll)
        assertFalse(round.turn.hasRolled)
    }

    @Test
    fun laMainRevientAuPremierJoueurApresLeDernier() {
        val round = game(2, 1, 3, 6, 1, 3, 6)
        round.roll()
        round.endTurn()
        round.roll()
        round.endTurn()
        assertEquals(0, round.current)
    }

    @Test
    fun onNePasseLaMainQuApresLeLancer() {
        val round = game(2, 5, 5, 2)
        round.endTurn()
        assertEquals(0, round.current)
        round.roll()
        round.endTurn()
        assertEquals(1, round.current)
    }

    // ---------- Inscription au tableau ----------

    @Test
    fun unLancerQuiRapporteEstInscritAuJoueurQuiJoue() {
        val vm = open("Anna", "Ben")
        val round = game(2, 5, 2, 5)

        assertTrue(round.roll())
        round.settle(vm)

        assertEquals(listOf(25, 0), vm.totals)
    }

    @Test
    fun unNeantInscritZeroEtLaLigneEstConsommee() {
        val vm = open("Anna", "Ben")
        val round = game(2, 1, 3, 6, 4, 4, 2)

        round.roll()
        round.settle(vm)
        assertEquals(1, vm.roundsPlayed())
        round.endTurn()
        round.roll()
        round.settle(vm)

        assertEquals(listOf(0, 16), vm.totals)
        assertEquals(2, vm.roundsPlayed())
    }

    @Test
    fun uneChouetteVeluteAttendQuiAcrieLePremier() {
        val vm = open("Anna", "Ben", "Chloé")
        val round = game(3, 1, 1, 2)

        assertFalse(round.roll())
        assertEquals(CdcPhase.CLAIM, round.phase)
        assertTrue(round.needsClaim)
        assertFalse(round.canRoll)
        assertFalse(round.turnOver)
        assertEquals(listOf(0, 0, 0), vm.totals)

        round.settle(vm, claimant = 2)

        assertEquals(listOf(0, 0, 8), vm.totals)
        assertEquals(CdcPhase.PLAY, round.phase)
        assertTrue(round.turnOver)
    }

    @Test
    fun uneSuiteFaitPerdreDesPointsAuDernierARealgir() {
        val vm = open("Anna", "Ben")
        vm.setCell(0, 1, 30, false)
        val round = game(2, 3, 4, 5)

        assertFalse(round.roll())
        round.settle(vm, claimant = 1)

        assertEquals(listOf(0, 20), vm.totals)
    }

    @Test
    fun personneNeMarqueNiNePerdEtLaLigneEstConsommee() {
        val vm = open("Anna", "Ben")
        val round = game(2, 3, 4, 5)

        round.roll()
        round.settle(vm, claimant = null)

        assertEquals(listOf(0, 0), vm.totals)
        assertEquals(1, vm.roundsPlayed())
        assertEquals(CdcPhase.PLAY, round.phase)
    }

    @Test
    fun unJoueurInconnuNeChangeRien() {
        val vm = open("Anna", "Ben")
        val round = game(2, 3, 4, 5)

        round.roll()
        round.settle(vm, claimant = 7)

        assertEquals(CdcPhase.CLAIM, round.phase)
        assertEquals(0, vm.roundsPlayed())
    }

    @Test
    fun lesPointsPeuventDevenirNegatifs() {
        val vm = open("Anna", "Ben")
        val round = game(2, 2, 3, 4)

        round.roll()
        round.settle(vm, claimant = 0)

        assertEquals(listOf(-10, 0), vm.totals)
    }

    @Test
    fun laPartieSArreteA343Points() {
        val vm = open("Anna", "Ben")
        vm.setCell(0, 0, 300, false)
        val round = game(2, 4, 4, 4)

        assertFalse(vm.isGameOver())
        round.roll()
        round.settle(vm)

        assertEquals(380, vm.totals[0])
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    // ---------- Partie du ViewModel ----------

    @Test
    fun laPartieReprendAvecLeJoueurQuiSuitLeDernierLancer() {
        val vm = open("Anna", "Ben", "Chloé")
        // Deux lancers déjà inscrits au tableau : c'est au troisième joueur de lancer.
        vm.setCell(0, 0, 4, false)
        vm.setCell(1, 1, 0, false)

        assertEquals(2, vm.cdcRound().current)
    }

    @Test
    fun uneNouvellePartieCommenceParLePremierJoueur() {
        assertEquals(0, open("Anna", "Ben", "Chloé").cdcRound().current)
    }

    @Test
    fun laPartieEstGardeeParLeViewModelPuisRemiseAZero() {
        val rules = cdcRules()
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), rules) }
        val first = vm.cdcRound()
        assertSame(first, vm.cdcRound())

        vm.initGame(listOf("Anna", "Ben"), rules)
        assertNotSame(first, vm.cdcRound())
    }

    // ---------- Sauvegarde et partage ----------

    @Test
    fun lEtatSurvitAuPartage() {
        val round = game(3, 3, 4, 5)
        round.roll()
        val copy = CdcRound(3)
        copy.restoreFrom(round.snapshot())

        assertEquals(round.snapshot(), copy.snapshot())
        assertEquals(CdcPhase.CLAIM, copy.phase)
        assertEquals(round.turn.values, copy.turn.values)
    }

    @Test
    fun unEtatInvalideRemetLaPartieAuDepart() {
        val round = game(2, 3, 4, 5)
        round.roll()

        round.restoreFrom(listOf(0, 3, 0, 0, 0))
        assertEquals(CdcRound(2).snapshot(), round.snapshot())

        round.restoreFrom(emptyList())
        assertEquals(CdcRound(2).snapshot(), round.snapshot())
    }
}
