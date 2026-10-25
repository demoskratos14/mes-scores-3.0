package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TicTacToeTest {

    private fun board(text: String) = TicTacToe.parse(text)

    @Test
    fun uneGrilleVideNAPasDeGagnant() {
        val cells = board(TicTacToe.EMPTY)
        assertEquals(0, TicTacToe.winner(cells))
        assertNull(TicTacToe.winningLine(cells))
        assertFalse(TicTacToe.isFull(cells))
    }

    @Test
    fun trois_symboles_alignes_gagnent_en_ligne_en_colonne_et_en_diagonale() {
        assertEquals(1, TicTacToe.winner(board("111220000")))
        assertEquals(1, TicTacToe.winner(board("100100100")))
        assertEquals(2, TicTacToe.winner(board("020020020")))
        assertEquals(1, TicTacToe.winner(board("120210001")))
        assertEquals(2, TicTacToe.winner(board("102020201")))
        assertEquals(listOf(0, 4, 8), TicTacToe.winningLine(board("120210001")))
    }

    @Test
    fun uneGrillePleineSansLigneEstUneEgalite() {
        val cells = board("121221112")
        assertEquals(0, TicTacToe.winner(cells))
        assertTrue(TicTacToe.isFull(cells))
    }

    @Test
    fun onNeJoueQueSurUneCaseLibreEtTantQueLaPartieEstOuverte() {
        val start = board(TicTacToe.EMPTY)
        val after = TicTacToe.play(start, 4, 1)
        assertEquals("000010000", TicTacToe.format(after!!))
        assertNull(TicTacToe.play(after, 4, 2))
        assertNull(TicTacToe.play(start, 9, 1))
        assertNull(TicTacToe.play(start, -1, 1))
        assertNull(TicTacToe.play(start, 0, 3))
        // Une fois la partie gagnée, plus aucun coup.
        assertNull(TicTacToe.play(board("111220000"), 8, 2))
    }

    @Test
    fun lesJoueursSeSuccedentEtLePremierPeutChanger() {
        assertEquals(1, TicTacToe.nextPlayer(board(TicTacToe.EMPTY), 1))
        assertEquals(2, TicTacToe.nextPlayer(board("100000000"), 1))
        assertEquals(2, TicTacToe.nextPlayer(board(TicTacToe.EMPTY), 2))
        assertEquals(1, TicTacToe.nextPlayer(board("020000000"), 2))
    }

    @Test
    fun unTexteInvalideDonneUneGrilleVide() {
        assertEquals(List(9) { 0 }, TicTacToe.parse("abc"))
        assertEquals(List(9) { 0 }, TicTacToe.parse("333333333"))
    }
}
