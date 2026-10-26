package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Le 421 jouable tour par tour : manche de départ, limite de lancers, jetons comptés seuls. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class Round421Test {

    /** Une manche dont les dés sortent dans l'ordre donné . */
    private fun round(players: Int, vararg dice: Int): Round421 {
        val queue = ArrayDeque(dice.toList())
        return Round421(players) { queue.removeFirst() }
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
            listOf(4, 2, 1), listOf(1, 1, 1), listOf(1, 6, 1), listOf(1, 1, 2),
            listOf(6, 6, 6), listOf(2, 2, 2), listOf(4, 5, 6), listOf(1, 2, 3),
            listOf(6, 5, 3), listOf(6, 5, 2), listOf(5, 4, 2), listOf(2, 2, 1)
        )
        ranked.zipWithNext().forEach { (better, worse) ->
            assertTrue("$better doit battre $worse", Dice421.strength(better) > Dice421.strength(worse))
        }
        // L'ordre des dés ne change rien.
        assertEquals(Dice421.strength(listOf(4, 2, 1)), Dice421.strength(listOf(1, 4, 2)))
    }

    // ---------- Jetons : charge et décharge ----------

    @Test
    fun pendantLaChargeLePerdantPrendAuMilieuSansDepasserCeQuiReste() {
        assertEquals(21, Dice421.pot(listOf(0, 0, 0)))
        assertEquals(Settlement421(10, fromPot = true), Dice421.settle(10, winner = 0, holdings = listOf(0, 0, 0)))
        // Il ne reste que 3 jetons au milieu : le perdant ne peut en prendre que 3.
        assertEquals(Settlement421(3, fromPot = true), Dice421.settle(10, winner = 0, holdings = listOf(9, 5, 4)))
    }

    @Test
    fun pendantLaDechargeLePerdantPrendAuGagnantSansDepasserSesJetons() {
        val holdings = listOf(12, 6, 3)
        assertEquals(0, Dice421.pot(holdings))
        assertEquals(Settlement421(6, fromPot = false), Dice421.settle(6, winner = 1, holdings = holdings))
        assertEquals(Settlement421(3, fromPot = false), Dice421.settle(10, winner = 2, holdings = holdings))
    }

    @Test
    fun pendantLaDechargeSeulsLesJoueursAvecDesJetonsJouent() {
        assertEquals(listOf(0, 1, 2), Dice421.activePlayers(listOf(0, 0, 0)))
        assertEquals(listOf(0, 1, 2), Dice421.activePlayers(listOf(5, 5, 5)))
        assertEquals(listOf(0, 2), Dice421.activePlayers(listOf(15, 0, 6)))
    }

    @Test
    fun laPartieSeTermineQuandUnJoueurALesVingtEtUnJetons() {
        assertNull(Dice421.gameLoser(listOf(0, 0, 0)))
        assertNull(Dice421.gameLoser(listOf(15, 0, 6)))
        // Les 21 jetons chez un seul joueur : il perd la partie.
        assertEquals(2, Dice421.gameLoser(listOf(0, 0, 21)))
        // Pendant la charge, un seul joueur qui a des jetons ne termine pas la partie.
        assertNull(Dice421.gameLoser(listOf(0, 8, 0)))
    }

    // ---------- Manche de départ ----------

    @Test
    fun laMancheDeDepartDesigneLeMeilleurJeuCommePremierJoueur() {
        // Le joueur 1 fait 421 : il commence la première vraie manche, sans jetons en jeu pendant la manche de départ.
        val round = round(3, 5, 3, 2, 4, 2, 1, 6, 6, 6)
        round.startOpening(listOf(0, 1, 2))
        assertTrue(round.opening)
        assertEquals(Phase421.PLAY, round.phase)
        assertEquals(listOf(0, 1, 2), round.order)
        repeat(3) { playOnce(round) }
        assertEquals(Phase421.DONE, round.phase)
        assertEquals(1, round.result()?.winner)

        round.finishOpening(listOf(0, 1, 2))
        assertFalse(round.opening)
        assertEquals(Phase421.PLAY, round.phase)
        assertEquals(listOf(1, 2, 0), round.order)
        assertTrue(round.hands.all { it.isEmpty() })
    }

    @Test
    fun uneEgaliteParfaiteALaMancheDeDepartLaFaitRejouer() {
        val round = round(2, 5, 3, 2, 5, 3, 2)
        round.startOpening(listOf(0, 1))
        repeat(2) { playOnce(round) }
        assertTrue(round.result()?.draw == true)

        round.finishOpening(listOf(0, 1))
        assertTrue(round.opening)
        assertEquals(Phase421.PLAY, round.phase)
        assertEquals(listOf(0, 1), round.order)
        assertTrue(round.hands.all { it.isEmpty() })
    }

    @Test
    fun onNePeutPasLancerLaMancheDeDepartApresAvoirChoisiLePremierJoueur() {
        val round = round(3)
        round.chooseStarter(1)
        round.startOpening(listOf(0, 1, 2))
        assertFalse(round.opening)
        assertEquals(Phase421.DRAW, round.phase)
    }

    @Test
    fun onPeutChoisirLePremierJoueurSansMancheDeDepart() {
        val round = round(3)
        round.chooseStarter(1)
        assertEquals(1, round.starter)
        round.begin(listOf(0, 1, 2))
        assertEquals(Phase421.PLAY, round.phase)
        assertEquals(listOf(1, 2, 0), round.order)
    }

    @Test
    fun laManchePeutSeCommencerSeulementApresLeChoixDuPremierJoueur() {
        val round = round(3)
        round.begin(listOf(0, 1, 2))
        assertEquals(Phase421.DRAW, round.phase)
    }

    // ---------- Une manche ----------

    @Test
    fun lePremierJoueurFixeLeNombreDeLancersDesAutres() {
        // Le joueur 2 commence : un seul lancer, donc un seul lancer pour les autres.
        val round = round(3, 4, 2, 1, 6, 6, 6, 5, 3, 2)
        round.chooseStarter(2)
        round.begin(listOf(0, 1, 2))
        assertEquals(listOf(2, 0, 1), round.order)
        assertTrue(round.isStarterTurn)
        assertEquals(3, round.turn.rollsLeft)
        assertFalse(round.canKeep)

        assertFalse(playOnce(round))
        assertEquals(1, round.limit)
        assertEquals(0, round.currentPlayer)
        assertFalse(round.isStarterTurn)
        // Le joueur suivant n'a qu'un lancer, et ses dés sont remis à zéro.
        assertFalse(round.turn.hasRolled)
        assertEquals(1, round.turn.rollsLeft)
        round.turn.roll()
        assertEquals(0, round.turn.rollsLeft)
        assertFalse(round.turn.canRoll)
        assertTrue(round.canKeep)
        assertFalse(round.keepHand())

        assertEquals(1, round.currentPlayer)
        assertTrue(playOnce(round))
        assertEquals(Phase421.DONE, round.phase)
        // 421 gagne ; entre 666 et 532, le moins bon est 532 (joueur 1).
        assertEquals(Result421(winner = 2, loser = 1, points = 10), round.result())
        assertEquals(1, round.currentPlayer)
    }

    @Test
    fun onNeGardePasUnJeuAvantDAvoirLance() {
        val round = round(2)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        assertFalse(round.keepHand())
        assertEquals(0, round.position)
    }

    @Test
    fun lePremierJoueurPeutFaireTroisLancersEtLesAutresAussi() {
        val round = round(2, 1, 1, 2, 2, 2, 3, 5, 6, 2, 3, 4, 1, 2, 2)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        repeat(3) { round.turn.roll() }
        assertEquals(0, round.turn.rollsLeft)
        round.keepHand()
        assertEquals(3, round.limit)
        assertEquals(3, round.turn.rollsLeft)
    }

    @Test
    fun aJeuEgalCeluiQuiALanceLeMoinsDeFoisEstMeilleur() {
        // Le joueur 1 fait deux lancers pour 532 ; le joueur 2 fait 532 du premier coup et gagne donc l'égalité.
        val round = round(2, 1, 1, 1, 5, 3, 2, 5, 3, 2)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        round.turn.roll()
        round.turn.roll()
        round.keepHand()
        assertEquals(2, round.limit)
        playOnce(round)
        assertEquals(Result421(winner = 1, loser = 0, points = 1), round.result())
    }

    @Test
    fun aJeuEtLancersEgauxLePremierAJouerEstMeilleur() {
        val round = round(2, 5, 3, 2, 5, 3, 2)
        round.chooseStarter(0)
        round.begin(listOf(0, 1))
        playOnce(round)
        assertTrue(playOnce(round))
        // Même jeu, même nombre de lancers : le premier à jouer gagne, mais la manche est nulle.
        assertEquals(Result421(winner = 0, loser = 1, points = 1, draw = true), round.result())
    }

    @Test
    fun uneMancheNulleSeRejoueAvecLeMemePremierJoueur() {
        val round = round(3, 5, 3, 2, 5, 3, 2, 5, 3, 2)
        round.chooseStarter(1)
        round.begin(listOf(0, 1, 2))
        repeat(3) { playOnce(round) }
        assertTrue(round.result()?.draw == true)
        round.nextRound(listOf(0, 1, 2))
        assertFalse(round.opening)
        assertEquals(listOf(1, 2, 0), round.order)
    }

    @Test
    fun lePerdantCommenceLaMancheSuivanteAvecLesJoueursActifs() {
        val round = round(3, 4, 2, 1, 6, 6, 6, 5, 3, 2)
        round.chooseStarter(2)
        round.begin(listOf(0, 1, 2))
        repeat(3) { playOnce(round) }
        assertEquals(Phase421.DONE, round.phase)
        round.recordTokens(10, fromPot = true)
        assertEquals(10, round.tokens)

        round.nextRound(listOf(0, 1, 2))
        assertEquals(Phase421.PLAY, round.phase)
        // Le joueur 1 avait le moins bon jeu : il ouvre la manche suivante, qui repart de zéro.
        assertEquals(listOf(1, 2, 0), round.order)
        assertEquals(3, round.limit)
        assertEquals(0, round.tokens)
        assertTrue(round.hands.all { it.isEmpty() })
    }

    @Test
    fun pendantLaDechargeUnJoueurSansJetonNeJouePlus() {
        val round = round(3, 4, 2, 1, 6, 6, 6, 5, 3, 2)
        round.chooseStarter(0)
        round.begin(listOf(0, 1, 2))
        repeat(3) { playOnce(round) }
        // Le joueur 1 n'a plus de jetons : la manche suivante se joue à deux.
        round.nextRound(Dice421.activePlayers(listOf(15, 0, 6)))
        assertEquals(2, round.order.size)
        assertFalse(1 in round.order)
    }

    @Test
    fun rejouerLaMancheRepartDuMemePremierJoueur() {
        val round = round(3, 4, 2, 1, 6, 6, 6, 5, 3, 2)
        round.chooseStarter(2)
        round.begin(listOf(0, 1, 2))
        repeat(3) { playOnce(round) }
        round.replayRound()
        assertEquals(Phase421.PLAY, round.phase)
        assertEquals(listOf(2, 0, 1), round.order)
        assertTrue(round.hands.all { it.isEmpty() })
        assertEquals(3, round.limit)
    }

    // ---------- Rotation de l'écran et partage ----------

    @Test
    fun laMancheSurvitAUneRotationDeLEcranEtSePartage() {
        // Le premier joueur fait deux lancers : le suivant en a deux aussi et peut garder un dé après le premier.
        val round = round(3, 1, 1, 1, 4, 2, 1, 6, 6, 6)
        round.chooseStarter(2)
        round.begin(listOf(0, 1, 2))
        round.turn.roll()
        round.turn.roll()
        round.keepHand()
        round.turn.roll()
        round.turn.toggleHold(1)
        assertEquals(setOf(1), round.turn.held)

        val copy = Round421(3)
        copy.restoreFrom(round.snapshot())
        assertEquals(round.phase, copy.phase)
        assertEquals(round.order, copy.order)
        assertEquals(round.position, copy.position)
        assertEquals(round.limit, copy.limit)
        assertEquals(round.hands, copy.hands)
        assertEquals(round.rolls, copy.rolls)
        assertEquals(round.turn.values, copy.turn.values)
        assertEquals(round.turn.held, copy.turn.held)
        assertEquals(round.currentPlayer, copy.currentPlayer)
        assertEquals(2, copy.turn.rollLimit)
        assertEquals(round.snapshot(), copy.snapshot())
    }

    @Test
    fun laMancheDeDepartSurvitAUnePartage() {
        val round = round(3, 5, 3, 2)
        round.startOpening(listOf(0, 1, 2))
        playOnce(round)
        val copy = Round421(3)
        copy.restoreFrom(round.snapshot())
        assertTrue(copy.opening)
        assertEquals(Phase421.PLAY, copy.phase)
        assertEquals(round.hands, copy.hands)
        assertEquals(round.snapshot(), copy.snapshot())
    }

    @Test
    fun uneListeInvalideRemetLaPartieAuTirage() {
        val round = round(3)
        round.chooseStarter(1)
        round.begin(listOf(0, 1, 2))
        round.restoreFrom(listOf(9, 9, 9))
        assertEquals(Phase421.DRAW, round.phase)
        assertEquals(-1, round.starter)
        round.restoreFrom(emptyList())
        assertEquals(Phase421.DRAW, round.phase)
        // Une manche pour un autre nombre de joueurs est refusée.
        val other = Round421(4)
        other.restoreFrom(Round421(3).snapshot())
        assertEquals(Phase421.DRAW, other.phase)
    }
}
