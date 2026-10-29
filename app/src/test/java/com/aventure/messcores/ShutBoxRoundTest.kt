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

/** Le Shut the Box jouable tour par tour : combinaisons de clapets, un seul dé, boîte fermée, inscription au tableau, manches. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class ShutBoxRoundTest {

    /** Une partie dont les dés sortent dans l'ordre donné (deux dés à chaque lancer, de gauche à droite). */
    private fun game(players: Int, startPlayer: Int = 0, vararg dice: Int): ShutBoxRound {
        val queue = ArrayDeque(dice.toList())
        return ShutBoxRound(players, startPlayer) { queue.removeFirst() }
    }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_shutbox" }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Lance les dés puis rabat les clapets [flaps] (leur somme doit égaler le total). */
    private fun move(round: ShutBoxRound, vararg flaps: Int) {
        round.roll()
        flaps.forEach { round.toggle(it) }
        assertTrue(round.confirm())
    }

    /** Dés d'un tour qui finit bloqué avec le seul clapet 8 ouvert (8 points) : 12, 12, 6, 7, puis 6 qui ne rabat rien. */
    private val stuckOnEight = intArrayOf(6, 6, 6, 6, 3, 3, 3, 4, 2, 4)

    /** Joue le tour qui laisse le clapet 8 ouvert, jusqu'à l'inscription au tableau ; renvoie les points inscrits. */
    private fun playStuckTurn(round: ShutBoxRound, vm: ScoreViewModel): Int {
        move(round, 3, 4, 5)
        move(round, 1, 2, 9)
        move(round, 6)
        move(round, 7)
        round.roll()
        assertTrue(round.stuck)
        return round.endTurn(vm)
    }

    // ---------- Règles des clapets ----------

    @Test
    fun leScoreEstLaSommeDesClapetsRestesOuverts() {
        assertEquals(45, ShutBoxScoring.score(ShutBoxScoring.ALL_OPEN))
        assertEquals(0, ShutBoxScoring.score(0))
        assertEquals(3, ShutBoxScoring.score(ShutBoxScoring.bit(1) or ShutBoxScoring.bit(2)))
        assertEquals(listOf(2, 9), ShutBoxScoring.flaps(ShutBoxScoring.bit(2) or ShutBoxScoring.bit(9)))
    }

    @Test
    fun unSeulDeNEstPermisQueQuandLes7Et8Et9SontRabattus() {
        assertFalse(ShutBoxScoring.canRollOneDie(ShutBoxScoring.ALL_OPEN))
        val sixFirst = (1..6).fold(0) { mask, flap -> mask or ShutBoxScoring.bit(flap) }
        assertTrue(ShutBoxScoring.canRollOneDie(sixFirst))
        // Il suffit qu'un seul des trois soit encore ouvert.
        assertFalse(ShutBoxScoring.canRollOneDie(sixFirst or ShutBoxScoring.bit(8)))
        assertTrue(ShutBoxScoring.canRollOneDie(0))
    }

    @Test
    fun uneCombinaisonExisteSiDesClapetsOuvertsFontLeTotal() {
        val all = ShutBoxScoring.ALL_OPEN
        assertTrue(ShutBoxScoring.canMake(all, 12))
        assertTrue(ShutBoxScoring.canMake(all, 2))
        val nine = ShutBoxScoring.bit(9)
        assertFalse(ShutBoxScoring.canMake(nine, 12))
        assertTrue(ShutBoxScoring.canMake(nine, 9))
        val oneTwo = ShutBoxScoring.bit(1) or ShutBoxScoring.bit(2)
        assertTrue(ShutBoxScoring.canMake(oneTwo, 3))
        assertFalse(ShutBoxScoring.canMake(oneTwo, 4))
        assertFalse(ShutBoxScoring.canMake(0, 2))
        assertFalse(ShutBoxScoring.canMake(all, 0))
    }

    @Test
    fun uneSelectionDoitEtreOuverteNonVideEtFaireLeTotal() {
        val openMask = ShutBoxScoring.ALL_OPEN and ShutBoxScoring.bit(5).inv()
        val threeFour = ShutBoxScoring.bit(3) or ShutBoxScoring.bit(4)
        assertTrue(ShutBoxScoring.isValid(openMask, threeFour, 7))
        assertFalse(ShutBoxScoring.isValid(openMask, threeFour, 8))
        assertFalse(ShutBoxScoring.isValid(openMask, 0, 0))
        // Le clapet 5 est déjà rabattu.
        assertFalse(ShutBoxScoring.isValid(openMask, ShutBoxScoring.bit(5), 5))
    }

    // ---------- Un tour ----------

    @Test
    fun onRabatUnClapetDontLeNumeroEstLeTotalDesDes() {
        val round = game(2, 0, 3, 4)
        assertFalse(round.turn.hasRolled)
        round.roll()
        assertEquals(7, round.total)
        round.toggle(7)
        assertEquals(ShutBoxScoring.bit(7), round.selected)
        assertTrue(round.canConfirm)
        assertTrue(round.confirm())
        assertEquals(ShutBoxScoring.ALL_OPEN and ShutBoxScoring.bit(7).inv(), round.openMask)
        assertEquals(0, round.selected)
        // Les dés sont à relancer, pour le même joueur.
        assertFalse(round.turn.hasRolled)
        assertTrue(round.canRoll)
        assertEquals(0, round.current)
    }

    @Test
    fun onPeutRabattreSeveralClapetsDontLaSommeFaitLeTotal() {
        val round = game(2, 0, 3, 4)
        round.roll()
        round.toggle(3)
        assertFalse(round.canConfirm)
        round.toggle(4)
        assertEquals(7, round.selectionSum)
        assertTrue(round.confirm())
        assertEquals(listOf(1, 2, 5, 6, 7, 8, 9), ShutBoxScoring.flaps(round.openMask))
    }

    @Test
    fun uneSommeQuiNeFaitPasLeTotalEstRefusee() {
        val round = game(2, 0, 3, 4)
        round.roll()
        round.toggle(3)
        round.toggle(5)
        assertEquals(8, round.selectionSum)
        assertFalse(round.canConfirm)
        assertFalse(round.confirm())
        assertEquals(ShutBoxScoring.ALL_OPEN, round.openMask)
        assertTrue(round.turn.hasRolled)
    }

    @Test
    fun toucherUnClapetUneSecondeFoisLeRemetEnJeu() {
        val round = game(2, 0, 3, 4)
        round.roll()
        round.toggle(7)
        round.toggle(7)
        assertEquals(0, round.selected)
        assertFalse(round.canConfirm)
    }

    @Test
    fun onNeChoisitNiAvantLeLancerNiUnClapetDejaRabattu() {
        val round = game(2, 0, 3, 4, 2, 5)
        round.toggle(7)
        assertEquals(0, round.selected)
        move(round, 7)
        round.roll()
        round.toggle(7)
        assertEquals(0, round.selected)
        round.toggle(0)
        round.toggle(10)
        assertEquals(0, round.selected)
    }

    @Test
    fun onNeRelancePasAvantDAvoirRabattuLeLancerEnCours() {
        val round = game(2, 0, 3, 4, 1, 1)
        round.roll()
        assertFalse(round.canRoll)
        val values = round.turn.values
        round.roll()
        assertEquals(values, round.turn.values)
    }

    // ---------- Tour bloqué, boîte fermée ----------

    @Test
    fun sansCombinaisonPossibleLeTourEstFiniEtOnMarqueLesClapetsOuverts() {
        val vm = viewModel()
        val round = game(2, 0, *stuckOnEight)
        assertEquals(8, playStuckTurn(round, vm))
        assertEquals(listOf(8, null), vm.scores[0].map { it.baseValue })
        assertEquals(listOf(8, 0), vm.totals)
        // La main passe au joueur suivant, avec une boîte neuve.
        assertEquals(1, round.current)
        assertEquals(ShutBoxScoring.ALL_OPEN, round.openMask)
        assertFalse(round.turn.hasRolled)
    }

    @Test
    fun unTourBloqueNePermetPlusDeChoisirDeClapet() {
        val vm = viewModel()
        val round = game(2, 0, *stuckOnEight)
        move(round, 3, 4, 5)
        move(round, 1, 2, 9)
        move(round, 6)
        move(round, 7)
        round.roll()
        assertTrue(round.stuck)
        assertTrue(round.turnOver)
        round.toggle(8)
        assertEquals(0, round.selected)
        assertFalse(round.confirm())
        assertEquals(8, round.score)
        assertEquals(8, round.endTurn(vm))
    }

    @Test
    fun onNePeutPasTerminerUnTourQuiPeutContinuer() {
        val vm = viewModel()
        val round = game(2, 0, 3, 4)
        assertEquals(-1, round.endTurn(vm))
        round.roll()
        assertFalse(round.turnOver)
        assertEquals(-1, round.endTurn(vm))
        assertEquals(listOf(null, null), vm.scores[0].map { it.baseValue })
        assertEquals(0, round.current)
    }

    @Test
    fun uneBoiteFermeeRapporteZero() {
        val vm = viewModel()
        // 9, 8, 7 avec deux dés, puis 6 à 1 avec un seul dé (le second dé tiré est ignoré).
        val round = game(2, 0, 4, 5, 4, 4, 3, 4, 6, 6, 5, 6, 4, 6, 3, 6, 2, 6, 1, 6)
        move(round, 9)
        move(round, 8)
        move(round, 7)
        assertTrue(round.canChooseOneDie)
        for (flap in 6 downTo 1) {
            round.setOneDie(true)
            move(round, flap)
        }
        assertTrue(round.boxShut)
        assertTrue(round.turnOver)
        assertFalse(round.canRoll)
        assertEquals(0, round.score)
        assertEquals(0, round.endTurn(vm))
        assertEquals(listOf(0, null), vm.scores[0].map { it.baseValue })
        assertEquals(1, round.current)
    }

    // ---------- Un seul dé ----------

    @Test
    fun unSeulDeEstRefuseTantQueLes7Et8Et9NeSontPasRabattus() {
        val round = game(2, 0, 3, 4)
        assertFalse(round.canChooseOneDie)
        round.setOneDie(true)
        assertFalse(round.oneDie)
    }

    @Test
    fun avecUnSeulDeLeTotalEstLeSeulPremierDe() {
        val round = game(2, 0, 4, 5, 4, 4, 3, 4, 5, 2)
        move(round, 9)
        move(round, 8)
        move(round, 7)
        assertTrue(round.canChooseOneDie)
        round.setOneDie(true)
        round.roll()
        assertEquals(5, round.total)
        assertTrue(round.oneDie)
        // Le choix du nombre de dés ne change plus une fois les dés lancés.
        round.setOneDie(false)
        assertTrue(round.oneDie)
        round.toggle(5)
        assertTrue(round.confirm())
        assertEquals(listOf(1, 2, 3, 4, 6), ShutBoxScoring.flaps(round.openMask))
        // On peut de nouveau choisir deux dés avant le lancer suivant.
        round.setOneDie(false)
        assertFalse(round.oneDie)
    }

    // ---------- Tableau des scores et partie ----------

    @Test
    fun chaqueJoueurInscritSaMancheDansSaPropreLigne() {
        val vm = viewModel()
        val round = game(2, 0, *stuckOnEight, *stuckOnEight)
        playStuckTurn(round, vm)
        playStuckTurn(round, vm)
        assertEquals(listOf(8, 8), vm.scores[0].map { it.baseValue })
        assertEquals(listOf(null, null), vm.scores[1].map { it.baseValue })
        assertEquals(0, round.current)
        assertEquals(1, vm.roundsPlayed())
    }

    @Test
    fun laPartieSArreteApresLaDerniereMancheDuDernierJoueur() {
        val vm = viewModel()
        val round = game(2, 0, *IntArray(100) { stuckOnEight[it % 10] })
        repeat(9) { playStuckTurn(round, vm) }
        // Le premier joueur a fini sa cinquième manche, le second pas encore.
        assertEquals(5, vm.roundsPlayed())
        assertFalse(vm.isGameOver())
        playStuckTurn(round, vm)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(40, 40), vm.totals)
        // Égalité : les deux joueurs gagnent.
        assertEquals(listOf(0, 1), vm.winners())
    }

    @Test
    fun lePlusPetitTotalGagne() {
        val vm = viewModel()
        repeat(5) { row ->
            vm.setCell(row, 0, 8, false)
            vm.setCell(row, 1, if (row == 0) 0 else 8, false)
        }
        assertTrue(vm.isGameOver())
        assertEquals(listOf(1), vm.winners())
    }

    @Test
    fun uneBoiteSeuleUnJoueurSeTermineApresCinqManches() {
        val vm = viewModel(listOf("Anna"))
        val round = game(1, 0, *IntArray(50) { stuckOnEight[it % 10] })
        repeat(4) { playStuckTurn(round, vm) }
        assertFalse(vm.isGameOver())
        playStuckTurn(round, vm)
        assertTrue(vm.isGameOver())
    }

    @Test
    fun lesReglesDuJeuFixentLesCinqManchesEtLAttenteDuDernierJoueur() {
        val condition = rules().endCondition
        assertEquals(EndConditionType.ROUND_COUNT, condition.type)
        assertEquals(ShutBoxScoring.ROUNDS, condition.roundCount)
        assertFalse(condition.stopImmediately)
        assertTrue(rules().lowestWins)
        assertTrue(rules().isDiceOnly())
        assertEquals(2, DiceSetups.forGame("builtin_shutbox")!!.diceCount)
    }

    @Test
    fun uneFinParManchesSansAttenteResteImmediateEnDehorsDuShutTheBox() {
        // Le comportement des autres jeux (stopImmediately par défaut) ne change pas.
        val rules = GameRules(
            id = "t", name = "T",
            endCondition = EndCondition(type = EndConditionType.ROUND_COUNT, roundCount = 2)
        )
        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben"), rules) }
        vm.setCell(0, 0, 1, false)
        assertFalse(vm.isGameOver())
        vm.setCell(1, 0, 1, false)
        assertTrue(vm.isGameOver())
    }

    // ---------- Reprise, rotation d'écran et partage ----------

    @Test
    fun uneNouvellePartieCommenceAvecLePremierJoueur() {
        val vm = viewModel(listOf("Anna", "Ben", "Cléo"))
        assertEquals(0, vm.shutBoxRound().current)
    }

    @Test
    fun unePartieRepriseContinueAvecLeJoueurQuiNAPasEncoreJoueDansLaManche() {
        val vm = viewModel(listOf("Anna", "Ben", "Cléo"))
        vm.setCell(0, 0, 5, false)
        vm.setCell(0, 1, 9, false)
        val resumed = vm.shutBoxRound()
        assertEquals(2, resumed.current)

        val fullRound = viewModel(listOf("Anna", "Ben"))
        fullRound.setCell(0, 0, 5, false)
        fullRound.setCell(0, 1, 9, false)
        assertEquals(0, fullRound.shutBoxRound().current)
    }

    @Test
    fun laPartieEstLaMemeQuandOnRevientDesScoresEtRepartDeZeroPourUneNouvelle() {
        val vm = viewModel()
        val game = vm.shutBoxRound()
        assertSame(game, vm.shutBoxRound())
        vm.initGame(listOf("Anna", "Ben"), rules())
        val fresh = vm.shutBoxRound()
        assertNotSame(game, fresh)
        vm.closeGame()
        assertNotSame(fresh, vm.shutBoxRound())
    }

    @Test
    fun lEtatSeRetabliTelQuelApresUneRotationOuUnPartage() {
        val round = game(3, 1, 3, 4, 2, 5)
        move(round, 7)
        round.roll()
        round.toggle(2)
        val copy = ShutBoxRound(3)
        copy.restoreFrom(round.snapshot())
        assertEquals(round.current, copy.current)
        assertEquals(round.openMask, copy.openMask)
        assertEquals(round.selected, copy.selected)
        assertEquals(round.oneDie, copy.oneDie)
        assertEquals(round.turn.values, copy.turn.values)
        assertEquals(round.total, copy.total)
        assertEquals(round.snapshot(), copy.snapshot())
    }

    @Test
    fun lEtatUnSeulDeSeRetabliAussi() {
        val round = game(2, 0, 4, 5, 4, 4, 3, 4, 5, 2)
        move(round, 9)
        move(round, 8)
        move(round, 7)
        round.setOneDie(true)
        round.roll()
        val copy = ShutBoxRound(2)
        copy.restoreFrom(round.snapshot())
        assertTrue(copy.oneDie)
        assertEquals(5, copy.total)
    }

    @Test
    fun unEtatInvalideRemetLaPartieAuDepart() {
        val game = game(3, 1)
        // Mauvais nombre de joueurs.
        game.restoreFrom(listOf(2, 0, ShutBoxScoring.ALL_OPEN, 0, 0, 0, 0))
        assertEquals(1, game.current)
        assertEquals(ShutBoxScoring.ALL_OPEN, game.openMask)
        // Clapets choisis alors qu'ils sont déjà rabattus.
        game.restoreFrom(listOf(3, 2, 0, ShutBoxScoring.bit(1), 0, 0, 0))
        assertEquals(1, game.current)
        assertEquals(ShutBoxScoring.ALL_OPEN, game.openMask)
        assertEquals(0, game.selected)
        // Un seul dé alors que les clapets 7, 8 et 9 sont ouverts.
        game.restoreFrom(listOf(3, 2, ShutBoxScoring.ALL_OPEN, 0, 1, 0, 0))
        assertFalse(game.oneDie)
        // Joueur qui joue hors de la table, liste vide.
        game.restoreFrom(listOf(3, 7, ShutBoxScoring.ALL_OPEN, 0, 0, 0, 0))
        assertEquals(1, game.current)
        game.restoreFrom(emptyList())
        assertEquals(1, game.current)
        assertNull(game.total)
        game.restoreFrom(ShutBoxRound(3).snapshot())
        assertEquals(0, game.current)
    }
}
