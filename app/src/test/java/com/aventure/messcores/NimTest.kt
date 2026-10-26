package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NimTest {

    @Test
    fun onRetireUnADeuxOuTroisAllumettesSansDepasserLeTas() {
        assertFalse(Nim.isLegal(21, 0))
        assertTrue(Nim.isLegal(21, 1))
        assertTrue(Nim.isLegal(21, 2))
        assertTrue(Nim.isLegal(21, 3))
        assertFalse(Nim.isLegal(21, 4))
        assertFalse(Nim.isLegal(21, -1))
        // Avec deux allumettes, on ne peut pas en retirer trois.
        assertTrue(Nim.isLegal(2, 2))
        assertFalse(Nim.isLegal(2, 3))
        assertFalse(Nim.isLegal(0, 1))
    }

    @Test
    fun leMeilleurCoupLaisseUnNombreDeLaForme4kPlus1() {
        for (remaining in 1..Nim.START) {
            val take = Nim.bestTake(remaining)
            assertTrue("$remaining : coup $take permis", Nim.isLegal(remaining, take))
            if (remaining % 4 != 1) assertEquals("$remaining : on laisse 4k + 1", 1, (remaining - take) % 4)
        }
        assertEquals(0, Nim.bestTake(0))
        // Position perdante face à un adversaire parfait : on prend une seule allumette.
        assertEquals(1, Nim.bestTake(21))
        assertEquals(1, Nim.bestTake(5))
        assertEquals(3, Nim.bestTake(4))
        assertEquals(1, Nim.bestTake(2))
    }
}

class NimMatchTest {

    @Test
    fun uneNouvelleSeanceCommenceAvecLeTasCompletEtLePremierJoueur() {
        val match = NimMatch()
        assertEquals(21, match.remaining)
        assertEquals(1, match.turn)
        assertFalse(match.isOver)
        assertEquals(0, match.winner)
    }

    @Test
    fun seulLeJoueurDontCEstLeTourPeutJouerEtLeTourPasseALAutre() {
        val match = NimMatch()
        assertNull(match.play(1, 2))
        val after = match.play(2, 1)!!
        assertEquals(19, after.remaining)
        assertEquals(2, after.turn)
        assertNull(after.play(1, 1))
        val next = after.play(3, 2)!!
        assertEquals(16, next.remaining)
        assertEquals(1, next.turn)
    }

    @Test
    fun uneQuantiteInterditeEstRefusee() {
        val match = NimMatch()
        assertNull(match.play(0, 1))
        assertNull(match.play(4, 1))
        assertNull(match.play(-2, 1))
        assertNull(NimMatch(remaining = 2).play(3, 1))
        assertNotNull(NimMatch(remaining = 2).play(2, 1))
    }

    @Test
    fun celuiQuiPrendLaDerniereAllumettePerdEtLAutreMarque() {
        val player1Takes = NimMatch(remaining = 1, turn = 1).play(1, 1)!!
        assertTrue(player1Takes.isOver)
        assertEquals(0, player1Takes.remaining)
        assertEquals(1, player1Takes.loser)
        assertEquals(2, player1Takes.winner)
        assertEquals(0, player1Takes.wins1)
        assertEquals(1, player1Takes.wins2)

        val player2Takes = NimMatch(remaining = 3, turn = 2, wins1 = 4).play(3, 2)!!
        assertEquals(2, player2Takes.loser)
        assertEquals(1, player2Takes.winner)
        assertEquals(5, player2Takes.wins1)
        assertEquals(0, player2Takes.wins2)
    }

    @Test
    fun laissantUneSeuleAllumetteOnGagne() {
        // Avec 4 allumettes, on en prend 3 : l'adversaire doit prendre la dernière.
        val after = NimMatch(remaining = 4, turn = 1).play(3, 1)!!
        assertFalse(after.isOver)
        val end = after.play(1, 2)!!
        assertEquals(1, end.winner)
    }

    @Test
    fun uneFoisLaMancheFinieAucunCoupNEstPermis() {
        val over = NimMatch(remaining = 1).play(1, 1)!!
        assertNull(over.play(1, 2))
        assertNull(over.play(1, 1))
    }

    @Test
    fun laMancheSuivanteNeSeLancheQueQuandLaMancheEstFinieEtLAutreJoueurCommence() {
        assertNull(NimMatch().newRound())
        val over = NimMatch(remaining = 1, turn = 1, wins2 = 2).play(1, 1)!!
        val next = over.newRound()!!
        assertEquals(Nim.START, next.remaining)
        assertEquals(0, next.loser)
        assertEquals(1, next.starter)
        assertEquals(2, next.turn)
        // Le score est gardé.
        assertEquals(3, next.wins2)
        assertNull(next.newRound())

        // Après une deuxième manche, le premier joueur recommence.
        val over2 = NimMatch(remaining = 1, turn = 2, starter = 1).play(1, 2)!!
        val third = over2.newRound()!!
        assertEquals(0, third.starter)
        assertEquals(1, third.turn)
    }

    @Test
    fun uneManchePeutSeJouerJusquAuBout() {
        // Les deux joueurs jouent le meilleur coup : le premier, en position perdante (21 = 4 × 5 + 1), finit par perdre.
        var match = NimMatch()
        while (!match.isOver) match = match.play(Nim.bestTake(match.remaining), match.turn)!!
        assertEquals(2, match.winner)
        assertEquals(1, match.wins2)
    }
}
