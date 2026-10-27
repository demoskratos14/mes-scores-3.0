package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GomokuTest {

    /** Grille de test : chaque pierre est donnée par (ligne, colonne) ; 1 pour les noires, 2 pour les blanches. */
    private fun board(black: List<Pair<Int, Int>> = emptyList(), white: List<Pair<Int, Int>> = emptyList()): List<Int> {
        val cells = MutableList(Gomoku.CELLS) { 0 }
        black.forEach { (row, col) -> cells[row * Gomoku.SIZE + col] = 1 }
        white.forEach { (row, col) -> cells[row * Gomoku.SIZE + col] = 2 }
        return cells
    }

    private fun cell(row: Int, col: Int) = row * Gomoku.SIZE + col

    @Test
    fun uneGrilleVideNAPasDeGagnant() {
        val cells = Gomoku.parse(Gomoku.EMPTY)
        assertEquals(Gomoku.CELLS, cells.size)
        assertEquals(0, Gomoku.winner(cells))
        assertNull(Gomoku.winningLine(cells))
        assertFalse(Gomoku.isFull(cells))
    }

    @Test
    fun cinqPierresAligneesGagnentEnLigneEnColonneEtEnDiagonale() {
        val row = board(black = (3..7).map { 5 to it })
        assertEquals(1, Gomoku.winner(row))
        assertEquals((3..7).map { cell(5, it) }, Gomoku.winningLine(row))

        val column = board(white = (2..6).map { it to 9 })
        assertEquals(2, Gomoku.winner(column))

        val down = board(black = (0..4).map { it + 4 to it + 6 })
        assertEquals(1, Gomoku.winner(down))
        assertEquals(listOf(cell(4, 6), cell(5, 7), cell(6, 8), cell(7, 9), cell(8, 10)), Gomoku.winningLine(down))

        val up = board(white = (0..4).map { 10 - it to it + 2 })
        assertEquals(2, Gomoku.winner(up))
    }

    @Test
    fun quatrePierresOuUneLigneInterrompueNeGagnentPas() {
        assertEquals(0, Gomoku.winner(board(black = (3..6).map { 5 to it })))
        // Cinq pierres dont une blanche au milieu.
        assertEquals(0, Gomoku.winner(board(black = listOf(5 to 3, 5 to 4, 5 to 6, 5 to 7), white = listOf(5 to 5))))
    }

    @Test
    fun plusDeCinqPierresAligneesGagnentAussiEtTouteLaSuiteEstRenvoyee() {
        val six = board(black = (4..9).map { 7 to it })
        assertEquals(1, Gomoku.winner(six))
        assertEquals(6, Gomoku.winningLine(six)!!.size)
    }

    @Test
    fun uneLigneNeSeRepliePasSurLeBordDuPlateau() {
        // Quatre pierres au bout d'une ligne et une au début de la suivante : pas de victoire.
        val wrap = board(black = listOf(0 to 11, 0 to 12, 0 to 13, 0 to 14, 1 to 0))
        assertEquals(0, Gomoku.winner(wrap))
        // Une diagonale peut en revanche partir d'un coin.
        assertEquals(1, Gomoku.winner(board(black = (0..4).map { it to it })))
        assertEquals(2, Gomoku.winner(board(white = (0..4).map { it to Gomoku.SIZE - 1 - it })))
    }

    @Test
    fun onNeJoueQueSurUneCaseLibreEtTantQueLaPartieEstOuverte() {
        val start = Gomoku.parse(Gomoku.EMPTY)
        val after = Gomoku.play(start, 112, 1)!!
        assertEquals(1, after[112])
        assertNull(Gomoku.play(after, 112, 2))
        assertNull(Gomoku.play(start, Gomoku.CELLS, 1))
        assertNull(Gomoku.play(start, -1, 1))
        assertNull(Gomoku.play(start, 0, 3))
        // Une fois la partie gagnée, plus aucun coup.
        assertNull(Gomoku.play(board(black = (3..7).map { 5 to it }), 0, 2))
    }

    @Test
    fun lesJoueursSeSuccedentEtLePremierPeutChanger() {
        val empty = Gomoku.parse(Gomoku.EMPTY)
        assertEquals(1, Gomoku.nextPlayer(empty, 1))
        assertEquals(2, Gomoku.nextPlayer(empty, 2))
        val one = Gomoku.play(empty, 0, 1)!!
        assertEquals(2, Gomoku.nextPlayer(one, 1))
        assertEquals(1, Gomoku.nextPlayer(Gomoku.play(empty, 0, 2)!!, 2))
    }

    @Test
    fun unTexteInvalideDonneUneGrilleVide() {
        assertEquals(List(Gomoku.CELLS) { 0 }, Gomoku.parse("abc"))
        assertEquals(List(Gomoku.CELLS) { 0 }, Gomoku.parse("3".repeat(Gomoku.CELLS)))
    }
}

class GomokuMatchTest {

    private fun boardWith(black: List<Int>, white: List<Int>): String {
        val cells = MutableList(Gomoku.CELLS) { 0 }
        black.forEach { cells[it] = 1 }
        white.forEach { cells[it] = 2 }
        return Gomoku.format(cells)
    }

    @Test
    fun seulLeJoueurDontCEstLeTourPeutJouer() {
        val match = GomokuMatch()
        assertNull(match.play(0, 2))
        val after = match.play(0, 1)!!
        assertEquals(0, after.last)
        assertNull(after.play(1, 1))
        val next = after.play(1, 2)!!
        assertEquals(1, next.last)
        assertEquals(2, next.cells[1])
        assertEquals(1, next.current)
    }

    @Test
    fun uneCaseDejaPriseOuHorsDeLaGrilleEstRefusee() {
        val after = GomokuMatch().play(100, 1)!!
        assertNull(after.play(100, 2))
        assertNull(after.play(Gomoku.CELLS, 2))
        assertNull(after.play(-1, 2))
    }

    @Test
    fun uneVictoireOuUneEgaliteSontComptees() {
        // Les noires ont quatre pierres alignées, les blanches jouent ailleurs : la cinquième gagne.
        val almost = GomokuMatch(board = boardWith(black = listOf(0, 1, 2, 3), white = listOf(30, 31, 32, 60)))
        assertEquals(1, almost.current)
        val won = almost.play(4, 1)!!
        assertTrue(won.isOver)
        assertEquals(1, won.winner)
        assertEquals(1, won.winsA)
        assertEquals(0, won.winsB)
        assertNull(won.play(100, 2))

        val almostWhite = GomokuMatch(
            board = boardWith(black = listOf(0, 1, 2, 60, 61, 62), white = listOf(30, 31, 32, 33)),
            starter = 1
        )
        // 10 pierres posées : au tour du joueur qui avait commencé (le deuxième).
        assertEquals(2, almostWhite.current)
        val wonWhite = almostWhite.play(34, 2)!!
        assertEquals(2, wonWhite.winner)
        assertEquals(1, wonWhite.winsB)
    }

    @Test
    fun uneGrillePleineSansLigneEstUneEgalite() {
        // Des paires de pierres décalées d'une ligne à l'autre : jamais cinq de suite, ni en ligne, ni en colonne, ni en diagonale.
        val full = MutableList(Gomoku.CELLS) { index -> 1 + (index % Gomoku.SIZE / 2 + index / Gomoku.SIZE) % 2 }
        assertEquals(0, Gomoku.winner(full))
        assertTrue(Gomoku.isFull(full))

        // Il ne reste que la dernière case, qui revient aux blanches (le deuxième joueur, qui a commencé).
        val last = Gomoku.CELLS - 1
        assertEquals(2, full[last])
        full[last] = 0
        val match = GomokuMatch(board = Gomoku.format(full), starter = 1, winsA = 4)
        assertEquals(2, match.current)
        assertFalse(match.isOver)

        val done = match.play(last, 2)!!
        assertTrue(done.isOver)
        assertEquals(0, done.winner)
        assertEquals(1, done.draws)
        assertEquals(4, done.winsA)
        assertEquals(0, done.winsB)
        assertNotNull(done.newRound())
    }

    @Test
    fun laMancheSuivanteNeSeLancheQueQuandLaMancheEstFinieEtLAutreJoueurCommence() {
        assertNull(GomokuMatch().newRound())
        val over = GomokuMatch(board = boardWith(black = (0..4).toList(), white = listOf(30, 31, 32, 33)), winsA = 1, last = 4)
        val next = over.newRound()!!
        assertEquals(Gomoku.EMPTY, next.board)
        assertEquals(1, next.starter)
        assertEquals(2, next.current)
        assertEquals(1, next.winsA)
        assertEquals(-1, next.last)
        assertNull(next.newRound())
    }

    @Test
    fun laSeanceSurvitAuTexte() {
        val match = GomokuMatch(board = boardWith(listOf(0, 20), listOf(5)), starter = 1, winsA = 2, winsB = 3, draws = 1, last = 20)
        assertEquals(match, GomokuMatch.decode(match.encode()))
        assertEquals(GomokuMatch(), GomokuMatch.decode(GomokuMatch().encode()))
    }

    @Test
    fun unTexteIllisibleEstRefuseEtUnScoreAbsurdeEstRamene() {
        assertNull(GomokuMatch.decode("n'importe quoi"))
        assertNull(GomokuMatch.decode("000;0;0;0;0;-1"))
        assertNull(GomokuMatch.decode("${"3".repeat(Gomoku.CELLS)};0;0;0;0;-1"))
        val fixed = GomokuMatch.decode("${Gomoku.EMPTY};7;-4;x;2;999")
        assertNotNull(fixed)
        assertEquals(1, fixed!!.starter)
        assertEquals(0, fixed.winsA)
        assertEquals(0, fixed.winsB)
        assertEquals(2, fixed.draws)
        assertEquals(Gomoku.CELLS - 1, fixed.last)
    }
}
