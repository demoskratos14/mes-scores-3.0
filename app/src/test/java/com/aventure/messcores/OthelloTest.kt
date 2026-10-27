package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OthelloTest {

    private val start = Othello.parse(Othello.START)

    /** Une grille de 64 cases à partir de quelques pions : `case to couleur`. */
    private fun boardOf(vararg pions: Pair<Int, Int>): String {
        val cells = MutableList(Othello.CELLS) { 0 }
        pions.forEach { (index, color) -> cells[index] = color }
        return Othello.format(cells)
    }

    @Test
    fun laPositionDeDepartAQuatrePionsEnDiagonale() {
        assertEquals(64, Othello.START.length)
        assertEquals(2, Othello.count(start, 1))
        assertEquals(2, Othello.count(start, 2))
        // d4 et e5 blancs, e4 et d5 noirs.
        assertEquals(2, start[27])
        assertEquals(1, start[28])
        assertEquals(1, start[35])
        assertEquals(2, start[36])
        assertFalse(Othello.isOver(start))
    }

    @Test
    fun auDepartChaqueCouleurADeuxOuQuatreCoupsPossibles() {
        assertEquals(listOf(19, 26, 37, 44), Othello.validMoves(start, 1))
        assertEquals(listOf(20, 29, 34, 43), Othello.validMoves(start, 2))
    }

    @Test
    fun unCoupRetourneLesPionsEncadres() {
        assertEquals(listOf(27), Othello.flips(start, 19, 1))
        val after = Othello.play(start, 19, 1)!!
        assertEquals(1, after[19])
        assertEquals(1, after[27])
        assertEquals(4, Othello.count(after, 1))
        assertEquals(1, Othello.count(after, 2))
    }

    @Test
    fun unCoupPeutEncadrerDansPlusieursDirectionsEnMemeTemps() {
        // Noir joue en 27 (d4) : à droite 28 blanc puis 29 noir ; en dessous 35 blanc puis 43 noir.
        val cells = Othello.parse(boardOf(28 to 2, 29 to 1, 35 to 2, 43 to 1))
        assertEquals(listOf(28, 35), Othello.flips(cells, 27, 1).sorted())
    }

    @Test
    fun uneLigneAdverseNonFermeeNEstPasRetournee() {
        // Blanc en 1 mais rien après lui : pas d'encadrement ; blanc en 9 suivi d'une case vide : idem.
        val cells = Othello.parse(boardOf(1 to 2, 9 to 2))
        assertTrue(Othello.flips(cells, 0, 1).isEmpty())
        // Un bord n'encadre pas : 0 noir, 1 blanc, 2 blanc... jusqu'au bord sans pion noir.
        val edge = Othello.parse(boardOf(5 to 2, 6 to 2, 7 to 2))
        assertTrue(Othello.flips(edge, 4, 1).isEmpty())
    }

    @Test
    fun unCoupIllegalEstRefuse() {
        assertNull(Othello.play(start, 27, 1)) // case occupée
        assertNull(Othello.play(start, 0, 1)) // rien n'est encadré
        assertNull(Othello.play(start, -1, 1))
        assertNull(Othello.play(start, 64, 1))
        assertNull(Othello.play(start, 19, 3))
    }

    @Test
    fun laManchePrendFinQuandPersonneNePeutJouerEtLePlusDePionsGagne() {
        val oneEach = Othello.parse(boardOf(0 to 1, 63 to 2))
        assertTrue(Othello.isOver(oneEach))
        assertEquals(0, Othello.winner(oneEach))
        assertEquals(1, Othello.winner(Othello.parse(boardOf(0 to 1, 1 to 1, 63 to 2))))
        assertEquals(2, Othello.winner(Othello.parse(boardOf(0 to 1, 62 to 2, 63 to 2))))
    }

    @Test
    fun unTexteInvalideDonneLaPositionDeDepart() {
        assertEquals(start, Othello.parse("abc"))
        assertEquals(start, Othello.parse("3".repeat(64)))
        assertEquals(start, Othello.parse("0".repeat(63)))
    }
}

class OthelloMatchTest {

    private fun boardOf(vararg pions: Pair<Int, Int>): String {
        val cells = MutableList(Othello.CELLS) { 0 }
        pions.forEach { (index, color) -> cells[index] = color }
        return Othello.format(cells)
    }

    @Test
    fun lOrganisateurJoueLesNoirsEtCommence() {
        val match = OthelloMatch()
        assertEquals(1, match.colorOf(0))
        assertEquals(2, match.colorOf(1))
        assertEquals(0, match.currentSeat)
        assertEquals(listOf(19, 26, 37, 44), match.validMoves(0))
        assertTrue(match.validMoves(1).isEmpty())
    }

    @Test
    fun seulLeJoueurDontCEstLeTourPeutJouerEtSurUneCaseQuiEncadre() {
        val match = OthelloMatch()
        assertNull(match.play(19, 1))
        assertNull(match.play(0, 0))
        assertNull(match.play(27, 0))
        val after = match.play(19, 0)!!
        assertEquals(2, after.turn)
        assertEquals(19, after.last)
        assertEquals(0, after.passed)
        assertEquals(1, after.currentSeat)
        assertNull(after.play(20, 0))
        assertNotNull(after.play(20, 1))
    }

    @Test
    fun unJoueurSansCoupPossibleEstSauteEtLAutreRejoue() {
        // Noir joue en 2 (retourne 1) ; blanc n'a plus qu'un pion, bloqué contre le bord, donc passe. Noir a encore 40.
        val match = OthelloMatch(board = boardOf(0 to 1, 1 to 2, 48 to 2, 56 to 1))
        val after = match.play(2, 0)!!
        assertEquals(1, after.turn)
        assertEquals(2, after.passed)
        assertEquals(0, after.currentSeat)
        assertFalse(after.isOver)
        assertEquals(2, after.last)

        // Le coup suivant efface l'annonce et finit la manche : plus aucun pion blanc.
        val end = after.play(40, 0)!!
        assertTrue(end.isOver)
        assertEquals(0, end.passed)
        assertEquals(1, end.winnerColor)
        assertEquals(1, end.wins0)
        assertEquals(0, end.wins1)
    }

    @Test
    fun uneEgaliteEstCompteeEtFigeLaManche() {
        // Noir joue en 2 : 3 pions noirs contre 3 pions blancs isolés sur le bord, personne ne peut plus jouer.
        val match = OthelloMatch(board = boardOf(0 to 1, 1 to 2, 61 to 2, 62 to 2, 63 to 2))
        val draw = match.play(2, 0)!!
        assertTrue(draw.isOver)
        assertEquals(0, draw.winnerColor)
        assertEquals(1, draw.draws)
        assertEquals(0, draw.wins0 + draw.wins1)
        assertTrue(draw.validMoves(0).isEmpty() && draw.validMoves(1).isEmpty())
        assertNull(draw.play(3, 1))
    }

    @Test
    fun laVictoireVaAuJoueurDeLaCouleurGagnanteMemeQuandLesCouleursSontEchangees() {
        // Deuxième manche : la place 1 joue les noirs.
        val match = OthelloMatch(board = boardOf(0 to 1, 1 to 2, 61 to 2), starter = 1)
        assertEquals(1, match.currentSeat)
        assertNull(match.play(2, 0))
        val won = match.play(2, 1)!!
        assertTrue(won.isOver)
        assertEquals(1, won.wins1)
        assertEquals(0, won.wins0)
    }

    @Test
    fun laMancheSuivanteNeSeLancheQueQuandLaMancheEstFinieEtLesCouleursSEchangent() {
        assertNull(OthelloMatch().newRound())
        val over = OthelloMatch(board = boardOf(0 to 1, 1 to 1, 63 to 2), wins0 = 2, draws = 1, last = 1)
        assertTrue(over.isOver)
        val next = over.newRound()!!
        assertEquals(Othello.START, next.board)
        assertEquals(1, next.starter)
        assertEquals(1, next.turn)
        assertEquals(-1, next.last)
        assertEquals(1, next.currentSeat)
        assertEquals(2, next.wins0)
        assertEquals(1, next.draws)
        assertNull(next.newRound())
    }

    @Test
    fun laSeanceSeGardeSousFormeDeTexte() {
        val match = OthelloMatch(board = boardOf(0 to 1, 1 to 2), starter = 1, turn = 2, last = 1, passed = 1, wins0 = 3, wins1 = 2, draws = 4)
        assertEquals(match, OthelloMatch.decode(match.encode()))
        assertEquals(OthelloMatch(), OthelloMatch.decode(OthelloMatch().encode()))
    }

    @Test
    fun unTexteIllisibleDonneUneSeanceNeuve() {
        assertEquals(OthelloMatch(), OthelloMatch.decode("n'importe quoi"))
        assertEquals(OthelloMatch(), OthelloMatch.decode("a,b,c,d,e,f,g,h"))
        val absurd = OthelloMatch.decode(Othello.START + ",9,9,999,9,-4,-4,-4")
        assertEquals(1, absurd.starter)
        assertEquals(2, absurd.turn)
        assertEquals(63, absurd.last)
        assertEquals(2, absurd.passed)
        assertEquals(0, absurd.wins0)
    }
}
