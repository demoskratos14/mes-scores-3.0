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

/** Le Bunco jouable tour par tour : points des lancers, tours, fin de manche à 21 points, inscription au tableau. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class BuncoRoundTest {

    /** Une partie dont les dés sortent dans l'ordre donné (trois dés par lancer). */
    private fun game(players: Int, startRound: Int = 1, vararg dice: Int): BuncoRound {
        val queue = ArrayDeque(dice.toList())
        return BuncoRound(players, startRound) { queue.removeFirst() }
    }

    private fun buncoRules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_bunco" }

    // ---------- Points d'un lancer ----------

    @Test
    fun chaqueDeDuNumeroDeLaMancheRapporteUnPoint() {
        assertEquals(BuncoRoll(BuncoKind.MATCHES, 1), BuncoScoring.roll(listOf(3, 5, 6), round = 3))
        assertEquals(BuncoRoll(BuncoKind.MATCHES, 2), BuncoScoring.roll(listOf(3, 5, 3), round = 3))
        assertEquals(BuncoRoll(BuncoKind.NOTHING, 0), BuncoScoring.roll(listOf(1, 2, 4), round = 3))
    }

    @Test
    fun troisDesDuNumeroDeLaMancheSontUnBuncoDe21Points() {
        assertEquals(BuncoRoll(BuncoKind.BUNCO, 21), BuncoScoring.roll(listOf(3, 3, 3), round = 3))
        assertEquals(BuncoRoll(BuncoKind.BUNCO, 21), BuncoScoring.roll(listOf(6, 6, 6), round = 6))
    }

    @Test
    fun troisDesIdentiquesDUnAutreNumeroRapportentCinqPoints() {
        assertEquals(BuncoRoll(BuncoKind.TRIPLE, 5), BuncoScoring.roll(listOf(5, 5, 5), round = 3))
        assertEquals(BuncoRoll(BuncoKind.TRIPLE, 5), BuncoScoring.roll(listOf(1, 1, 1), round = 2))
    }

    // ---------- Un tour ----------

    @Test
    fun tantQueLeJoueurMarqueIlRelanceEtLeTourSArreteAuPremierLancerSansPoint() {
        // Manche 1 : [1,1,2] = 2 points, [1,2,3] = 1 point, [4,5,6] = rien.
        val game = game(2, 1, 1, 1, 2, 1, 2, 3, 4, 5, 6)
        assertFalse(game.roll())
        assertTrue(game.canRoll)
        assertFalse(game.roll())
        assertTrue(game.canRoll)
        assertFalse(game.roll())
        assertEquals(listOf(3, 0), game.points)
        // Le lancer n'a rien rapporté : on ne peut plus lancer, il faut passer la main.
        assertTrue(game.turnOver)
        assertFalse(game.canRoll)
        assertFalse(game.roll())
        assertEquals(0, game.current)

        game.endTurn()
        assertEquals(1, game.current)
        assertFalse(game.turn.hasRolled)
        assertTrue(game.canRoll)
    }

    @Test
    fun onNePasseLaMainQuApresUnLancerSansPoint() {
        val game = game(2, 1, 1, 2, 3)
        game.roll()
        // Le lancer a rapporté un point : le joueur doit relancer, il ne peut pas passer la main.
        game.endTurn()
        assertEquals(0, game.current)
    }

    @Test
    fun apresLeDernierJoueurLaMainRevientAuPremier() {
        val game = game(3, 1, 4, 5, 6, 4, 5, 6, 4, 5, 6)
        repeat(3) {
            game.roll()
            game.endTurn()
        }
        assertEquals(0, game.current)
    }

    // ---------- Fin de manche ----------

    @Test
    fun laMancheSArreteQuandUnJoueurAtteint21PointsEtIlLaGagne() {
        // Joueur 0 : 4 points puis rien ; joueur 1 : un Bunco (21 points).
        val game = game(2, 1, 1, 1, 2, 1, 1, 4, 4, 5, 6, 1, 1, 1)
        assertFalse(game.roll())
        assertFalse(game.roll())
        assertEquals(listOf(4, 0), game.points)
        game.roll()
        game.endTurn()
        assertTrue(game.roll())
        assertEquals(BuncoPhase.DONE, game.phase)
        assertEquals(1, game.winner)
        assertEquals(listOf(4, 21), game.points)
        assertEquals(BuncoKind.BUNCO, game.lastRoll?.kind)
        // La manche est finie : plus de lancer.
        assertFalse(game.canRoll)
    }

    @Test
    fun lesPointsDesLancersSAdditionnentJusqua21() {
        // Manche 2 (le joueur 1 commence) : trois 5 rapportent 5 points ; quatre fois = 20, puis un seul 2 = 21.
        val game = game(2, 2, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 2, 3, 4)
        assertEquals(1, game.current)
        repeat(4) { assertFalse(game.roll()) }
        assertEquals(20, game.points[1])
        assertTrue(game.roll())
        assertEquals(21, game.points[1])
        assertEquals(1, game.winner)
        assertEquals(BuncoKind.MATCHES, game.lastRoll?.kind)
    }

    @Test
    fun lePremierJoueurChangeAChaqueManche() {
        val game = game(3)
        assertEquals(0, game.starterOf(1))
        assertEquals(1, game.starterOf(2))
        assertEquals(2, game.starterOf(3))
        assertEquals(0, game.starterOf(4))
    }

    @Test
    fun laMancheSuivanteRemetLesPointsAZeroEtChangeLePremierJoueur() {
        val game = game(2, 1, 1, 1, 1)
        assertTrue(game.roll())
        game.nextRound()
        assertEquals(2, game.round)
        assertEquals(BuncoPhase.PLAY, game.phase)
        assertEquals(listOf(0, 0), game.points)
        assertEquals(1, game.current)
        assertEquals(-1, game.winner)
        assertFalse(game.turn.hasRolled)
    }

    @Test
    fun apresLaSixiemeMancheLaPartieNeContinuePas() {
        val game = game(2, 6, 6, 6, 6)
        assertTrue(game.isLastRound)
        assertTrue(game.roll())
        game.nextRound()
        assertEquals(6, game.round)
        assertEquals(BuncoPhase.DONE, game.phase)
    }

    // ---------- Sauvegarde et partage ----------

    @Test
    fun lEtatSePartageEtSeRetablitTelQuel() {
        val game = game(3, 2, 2, 2, 5, 4, 5, 6)
        game.roll()
        val copy = BuncoRound(3)
        copy.restoreFrom(game.snapshot())
        assertEquals(game.snapshot(), copy.snapshot())
        assertEquals(game.round, copy.round)
        assertEquals(game.points, copy.points)
        assertEquals(game.turn.values, copy.turn.values)
    }

    @Test
    fun unEtatInvalideRemetLaPartieAuDepart() {
        val game = BuncoRound(3, startRound = 3)
        game.restoreFrom(listOf(0, 3, 9, 0, -1, 0, 0, 0))
        assertEquals(3, game.round)
        assertEquals(BuncoPhase.PLAY, game.phase)
        assertEquals(listOf(0, 0, 0), game.points)
        game.restoreFrom(emptyList())
        assertEquals(3, game.round)
        // Un état d'une partie à un autre nombre de joueurs est refusé.
        game.restoreFrom(BuncoRound(2).snapshot())
        assertEquals(listOf(0, 0, 0), game.points)
    }

    // ---------- Tableau des scores et partie ----------

    @Test
    fun lesPointsDeLaMancheSontInscritsDansLeTableau() {
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), buncoRules()) }
        val game = game(2, 1, 1, 2, 3, 4, 5, 6, 1, 1, 1)
        game.roll()
        game.roll()
        game.endTurn()
        assertTrue(game.roll())
        game.writeTo(vm)
        assertEquals(listOf(1, 21), vm.scores[0].map { it.baseValue })
        assertEquals(listOf(1, 21), vm.totals)
        assertFalse(vm.isGameOver())
    }

    @Test
    fun laPartieSeTermineApresLaSixiemeManche() {
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), buncoRules()) }
        // Chaque manche est gagnée d'un Bunco par son premier joueur : manche N, trois dés N.
        for (round in 1..6) {
            val queue = ArrayDeque(listOf(round, round, round))
            val played = BuncoRound(2, startRound = round) { queue.removeFirst() }
            assertTrue(played.roll())
            played.writeTo(vm)
            if (round < 6) assertFalse(vm.isGameOver())
        }
        assertTrue(vm.isGameOver())
        // Premier joueur : 0, 1, 0, 1, 0, 1 ; chacun a gagné 3 manches de 21 points.
        assertEquals(listOf(63, 63), vm.totals)
        assertEquals(listOf(0, 1), vm.winners())
        assertEquals(6, vm.scores.count { row -> row.any { it.baseValue != null } })
    }

    @Test
    fun uneReprisePartDeLaMancheQuiSuitCellesDejaInscrites() {
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), buncoRules()) }
        vm.setCell(0, 0, 21, false)
        vm.setCell(0, 1, 4, false)
        vm.setCell(1, 0, 3, false)
        vm.setCell(1, 1, 21, false)
        val game = vm.buncoRound()
        assertEquals(3, game.round)
        // Le premier joueur de la manche 3 est le joueur 1 (index 0).
        assertEquals(0, game.current)
    }

    @Test
    fun laPartieDeBuncoEstGardeeQuandOnPasseParLesScoresPuisRevientAuxDes() {
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), buncoRules()) }
        val game = vm.buncoRound()
        // L'écran des dés se recompose (retour des scores) : c'est la même partie.
        assertSame(game, vm.buncoRound())
    }

    @Test
    fun uneNouvellePartieOuLaFermetureEffacentLaPartieDeBunco() {
        val rules = buncoRules()
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), rules) }
        val game = vm.buncoRound()

        vm.initGame(listOf("Anna", "Ben"), rules)
        val fresh = vm.buncoRound()
        assertNotSame(game, fresh)

        vm.closeGame()
        assertNotSame(fresh, vm.buncoRound())
    }
}
