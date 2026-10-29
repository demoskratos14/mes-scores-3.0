package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le Mexicain jouable tour par tour : classement des jeux, manche à deux dés, vies perdues et éliminations. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class MexicainTest {

    /** Une manche de Mexicain dont les dés sortent dans l'ordre donné. */
    private fun round(players: Int, vararg dice: Int): Round421 {
        val queue = ArrayDeque(dice.toList())
        return Round421(players, 3, MexicainRanking) { queue.removeFirst() }
    }

    /** Le joueur qui joue lance ses dés (un lancer) puis garde son jeu ; renvoie vrai si la manche est finie. */
    private fun playOnce(round: Round421): Boolean {
        round.turn.roll()
        return round.keepHand()
    }

    // ---------- Force des jeux ----------

    @Test
    fun lesJeuxSontClassesDuMeilleurAuMoinsBon() {
        val ranked = listOf(
            listOf(2, 1), listOf(6, 6), listOf(3, 3), listOf(1, 1),
            listOf(6, 5), listOf(6, 4), listOf(5, 4), listOf(4, 3), listOf(3, 2), listOf(3, 1)
        )
        ranked.zipWithNext().forEach { (better, worse) ->
            assertTrue("$better doit battre $worse", DiceMexicain.strength(better) > DiceMexicain.strength(worse))
        }
        // L'ordre des dés ne change rien.
        assertEquals(DiceMexicain.strength(listOf(2, 1)), DiceMexicain.strength(listOf(1, 2)))
        assertEquals(DiceMexicain.strength(listOf(6, 4)), DiceMexicain.strength(listOf(4, 6)))
        assertTrue(DiceMexicain.isMexico(listOf(1, 2)))
        assertTrue(DiceMexicain.isDouble(listOf(4, 4)))
        assertFalse(DiceMexicain.isDouble(listOf(4, 5)))
    }

    // ---------- Vies ----------

    @Test
    fun lesViesRestantesEtLesJoueursEnJeuSuiventLesViesPerdues() {
        assertEquals(listOf(5, 3, 0), DiceMexicain.livesLeft(listOf(0, 2, 5)))
        assertEquals(listOf(0, 1), DiceMexicain.activePlayers(listOf(4, 0, 5)))
        assertNull(DiceMexicain.winner(listOf(4, 0, 5)))
        assertEquals(1, DiceMexicain.winner(listOf(5, 3, 5)))
        assertNull(DiceMexicain.winner(listOf(0, 0, 0)))
    }

    // ---------- Une manche ----------

    @Test
    fun lesJoueursJouentDeuxDesEtLePlusMauvaisJeuPerd() {
        // Anna : 6-5 ; Ben : 3-1 (le pire) ; Chloé : 4-4.
        val round = round(3, 6, 5, 3, 1, 4, 4)
        round.chooseStarter(0)
        round.begin(listOf(0, 1, 2))
        assertEquals(2, round.turn.setup.diceCount)
        assertFalse(playOnce(round))
        assertFalse(playOnce(round))
        assertTrue(playOnce(round))
        val result = round.result()!!
        assertEquals(2, result.winner)
        assertEquals(1, result.loser)
        assertFalse(result.draw)
    }

    @Test
    fun leMexicoBatToutLeReste() {
        val round = round(2, 1, 2, 6, 6)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        playOnce(round)
        playOnce(round)
        val result = round.result()!!
        assertEquals(0, result.winner)
        assertEquals(1, result.loser)
    }

    @Test
    fun auxJeuxEgauxLeMoinsDeLancersEstMeilleur() {
        // Anna garde 5-3 au deuxième lancer, Ben au premier : Anna perd.
        val round = round(2, 1, 1, 5, 3, 5, 3)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        round.turn.roll()
        round.turn.roll()
        round.keepHand()
        playOnce(round)
        val result = round.result()!!
        assertEquals(1, result.winner)
        assertEquals(0, result.loser)
        assertFalse(result.draw)
    }

    @Test
    fun uneEgalitePerfecteRendLaMancheNulle() {
        val round = round(2, 5, 3, 3, 5)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        playOnce(round)
        playOnce(round)
        assertTrue(round.result()!!.draw)
    }

    @Test
    fun lePremierJoueurFixeLaLimiteDeLancers() {
        val round = round(2, 4, 2, 6, 1, 2, 5, 3, 3)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        round.turn.roll()
        round.turn.roll()
        round.keepHand()
        assertEquals(2, round.limit)
        round.turn.roll()
        round.turn.roll()
        assertFalse(round.turn.canRoll)
    }

    @Test
    fun uneMancheSuivanteCommenceChezLeSuivantQuandLePerdantEstElimine() {
        val round = round(3, 6, 5, 3, 1, 4, 4)
        round.chooseStarter(0)
        round.begin(listOf(0, 1, 2))
        repeat(3) { playOnce(round) }
        // Ben (joueur 1) perd la manche mais n'a plus de vie : la manche suivante se joue à deux, et c'est Chloé qui ouvre.
        round.nextRound(listOf(0, 2))
        assertEquals(listOf(2, 0), round.order)
        assertEquals(2, round.currentPlayer)
    }

    // ---------- Sauvegarde et partage ----------

    @Test
    fun laMancheSeRestaureAvecSesDeuxDes() {
        val round = round(3, 6, 5, 3, 1)
        round.chooseStarter(0)
        round.begin(listOf(0, 1, 2))
        playOnce(round)
        round.turn.roll()
        val copy = Round421(3, 3, MexicainRanking)
        copy.restoreFrom(round.snapshot())
        assertEquals(round.snapshot(), copy.snapshot())
        assertEquals(listOf(6, 5), copy.hands[0])
        // Un état du 421 (trois dés) n'est pas accepté par une manche de Mexicain : on revient au tirage.
        val other = Round421(3, 3, MexicainRanking)
        val round421 = Round421(3, 3, Dice421Ranking) { 4 }
        round421.chooseStarter(0)
        round421.begin(listOf(0, 1, 2))
        round421.turn.roll()
        round421.keepHand()
        other.restoreFrom(round421.snapshot())
        assertEquals(Phase421.DRAW, other.phase)
    }

    // ---------- Dans l'appli ----------

    @Test
    fun leMexicainSeJoueSurLaPageDesDes() {
        val mexicain = GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == "builtin_mexicain" }
        assertTrue(mexicain.isPlayableGame())
        assertTrue(mexicain.isDiceOnly())
        assertEquals(DiceSetup(diceCount = 2, maxRolls = 3), DiceSetups.forGame("builtin_mexicain"))
    }
}
