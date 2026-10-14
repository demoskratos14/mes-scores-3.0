package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PokerEngineTest {

    private fun settings(stack: Int = 5000, small: Int = 50, big: Int = 100, ante: Boolean = false, anteAfter: Int = 0) =
        PokerSettings(stack, small, big, levelMinutes = 10, anteEnabled = ante, anteAfterIncreases = anteAfter, firstDealer = 0)

    /** Partie dont les tapis de départ sont donnés joueur par joueur ; le bouton est au joueur [dealer]. */
    private fun game(vararg stacks: Int, s: PokerSettings = settings(), dealer: Int = 0): PokerGame {
        val base = PokerEngine.newGame(s, stacks.size)
        return PokerEngine.startHand(
            base.copy(
                seats = stacks.map { PokerSeat(stack = it) },
                dealer = dealer,
                handNumber = 0,
                phase = PokerPhase.HAND_OVER
            ),
            level = 1,
            keepDealer = true
        )
    }

    @Test
    fun blindsEtPremierJoueurAParler() {
        val g = game(5000, 5000, 5000, 5000, 5000)
        assertEquals(0, g.dealer)
        assertEquals(1, g.smallBlindSeat)
        assertEquals(2, g.bigBlindSeat)
        assertEquals(3, g.toAct)
        assertEquals(150, g.pot)
    }

    @Test
    fun enTeteATeteLeBoutonEstPetiteBlindEtParleEnPremier() {
        val g = game(1000, 1000)
        assertEquals(0, g.smallBlindSeat)
        assertEquals(1, g.bigBlindSeat)
        assertEquals(0, g.toAct)
        // Après le flop, la grosse blind parle en premier.
        val flop = PokerEngine.check(PokerEngine.call(g))
        assertEquals(PokerStreet.FLOP, flop.street)
        assertEquals(1, flop.toAct)
    }

    @Test
    fun scenarioDuTourQuiRevientAuJoueurQuiAvaitSimplementSuivi() {
        var g = game(5000, 5000, 5000, 5000, 5000)
        g = PokerEngine.call(g)          // joueur 3 suit
        g = PokerEngine.fold(g)          // joueur 4 passe
        g = PokerEngine.raiseTo(g, 1100) // joueur 0 relance de 1000
        g = PokerEngine.call(g)          // joueur 1 suit
        g = PokerEngine.allIn(g)         // joueur 2 fait tapis
        assertEquals(3, g.toAct)         // le tour revient à celui qui avait simplement suivi
        g = PokerEngine.call(g)
        assertEquals(0, g.toAct)
        g = PokerEngine.call(g)
        assertEquals(1, g.toAct)
        g = PokerEngine.fold(g)
        assertEquals(PokerPhase.SHOWDOWN, g.phase)
    }

    @Test
    fun laRelanceMinimumEstImposee() {
        var g = game(5000, 5000, 5000)
        g = PokerEngine.raiseTo(g, 150)  // trop petit : remonté à 200
        assertEquals(200, g.currentBet)
        g = PokerEngine.raiseTo(g, 700)
        assertEquals(500, g.minRaise)
        g = PokerEngine.raiseTo(g, 800)  // minimum 1200
        assertEquals(1200, g.currentBet)
    }

    @Test
    fun unTapisCourtNeRouvrePasLesRelances() {
        var g = game(5000, 5000, 350)
        g = PokerEngine.call(g)
        g = PokerEngine.call(g)
        g = PokerEngine.check(g)         // flop, le joueur 1 parle
        g = PokerEngine.raiseTo(g, 200)  // joueur 1 mise 200
        g = PokerEngine.allIn(g)         // joueur 2 : tapis à 250, relance incomplète
        assertEquals(0, g.toAct)
        assertTrue(g.seats[1].raiseLocked)
        g = PokerEngine.call(g)          // joueur 0 suit
        assertEquals(1, g.toAct)
        val unchanged = PokerEngine.raiseTo(g, 1000)
        assertEquals(g, unchanged)       // le joueur 1 ne peut plus relancer
    }

    @Test
    fun potsAnnexesAvecTroisTapisDifferents() {
        var g = game(1000, 300, 600, 5000, dealer = 3)
        repeat(4) { g = PokerEngine.allIn(g) }
        assertEquals(PokerPhase.SHOWDOWN, g.phase)
        val pots = PokerEngine.pots(g)
        assertEquals(listOf(1200, 900, 800, 4000), pots.map { it.amount })
        assertTrue(PokerEngine.isAutomatic(pots.last()))
    }

    @Test
    fun partagerUnPotEtEliminer() {
        var g = game(1000, 1000)
        g = PokerEngine.allIn(g)
        g = PokerEngine.allIn(g)
        assertEquals(PokerPhase.SHOWDOWN, g.phase)
        g = PokerEngine.resolveShowdown(g, listOf(listOf(0)))
        assertEquals(PokerPhase.HAND_OVER, g.phase)
        assertEquals(2000, g.seats[0].stack)
        assertTrue(g.seats[1].out)
        assertEquals(0, g.champion)
        assertEquals(listOf(1, 2), PokerEngine.ranks(g))
    }

    @Test
    fun egaliteLePotEstPartage() {
        var g = game(1000, 1000)
        g = PokerEngine.allIn(PokerEngine.allIn(g))
        g = PokerEngine.resolveShowdown(g, listOf(listOf(0, 1)))
        assertEquals(1000, g.seats[0].stack)
        assertEquals(1000, g.seats[1].stack)
        assertNull(g.champion)
    }

    @Test
    fun toutLeMondeSeCoucheLeDernierRemporteLePot() {
        var g = game(1000, 1000, 1000)
        g = PokerEngine.fold(g)
        g = PokerEngine.fold(g)
        assertEquals(PokerPhase.HAND_OVER, g.phase)
        assertEquals(1050, g.seats[2].stack)
        assertEquals(150, g.winnings.sum())
    }

    @Test
    fun antesEtStructureDesBlinds() {
        val s = settings(ante = true, anteAfter = 2)
        assertEquals(0, PokerBlinds.anteAt(s, 1))
        assertEquals(0, PokerBlinds.anteAt(s, 2))
        assertTrue(PokerBlinds.anteAt(s, 3) > 0)
        assertEquals(120, PokerBlinds.bigBlindAt(s, 2))
        assertEquals(60, PokerBlinds.smallBlindAt(s, 2))
        assertEquals(1, PokerBlinds.levelAt(s, 0))
        assertEquals(2, PokerBlinds.levelAt(s, 10 * 60_000L))
        val g = game(1000, 1000, 1000, s = settings(ante = true, anteAfter = 0))
        assertEquals(150 + 30, g.pot)
        assertFalse(g.seats.any { it.stack < 0 })
    }

    @Test
    fun sauvegardeJsonRoundTrip() {
        var g = game(1000, 1000, 1000)
        g = PokerEngine.raiseTo(g, 300)
        assertEquals(g, PokerGame.fromJson(g.toJson()))
    }
}
