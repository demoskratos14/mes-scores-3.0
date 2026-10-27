package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraughtsTest {

    /** Grille avec seulement les pièces données (case, pièce). */
    private fun position(vararg pieces: Pair<Int, Int>): List<Int> {
        val cells = MutableList(Draughts.CELLS) { 0 }
        pieces.forEach { (index, piece) -> cells[index] = piece }
        return cells
    }

    private fun paths(cells: List<Int>, color: Int) = Draughts.legalMoves(cells, color).map { it.path }.toSet()

    private fun perft(cells: List<Int>, color: Int, depth: Int): Int {
        if (depth == 0) return 1
        val moves = Draughts.legalMoves(cells, color)
        if (depth == 1) return moves.size
        return moves.sumOf { perft(Draughts.afterMove(cells, it), 3 - color, depth - 1) }
    }

    // ---------- Damier ----------

    @Test
    fun laPositionDeDepartALVingtPionsParCampSurLesCasesSombres() {
        val cells = Draughts.parse(Draughts.START)
        assertEquals(100, cells.size)
        assertEquals(20, cells.count { it == Draughts.MAN_WHITE })
        assertEquals(20, cells.count { it == Draughts.MAN_BLACK })
        assertTrue(cells.indices.filter { cells[it] != 0 }.all { Draughts.isDark(it) })
        // Les noirs en haut (rangées 0 à 3), les blancs en bas (rangées 6 à 9).
        assertTrue((0 until 40).all { cells[it] != Draughts.MAN_WHITE })
        assertTrue((60 until 100).all { cells[it] != Draughts.MAN_BLACK })
    }

    @Test
    fun lesCasesSontNumeroteesDe1A50() {
        assertEquals(1, Draughts.number(1))
        assertEquals(5, Draughts.number(9))
        assertEquals(6, Draughts.number(10))
        assertEquals(46, Draughts.number(90))
        assertEquals(50, Draughts.number(98))
        val numbers = (0 until Draughts.CELLS).filter { Draughts.isDark(it) }.map { Draughts.number(it) }
        assertEquals((1..50).toList(), numbers)
    }

    @Test
    fun uneGrilleIllisibleOuAvecUnePieceSurUneCaseClaireDonneLeDepart() {
        val start = Draughts.parse(Draughts.START)
        assertEquals(start, Draughts.parse("n'importe quoi"))
        assertEquals(start, Draughts.parse("1" + "0".repeat(99)))
        assertEquals(start, Draughts.parse("0".repeat(99) + "9"))
        assertEquals(Draughts.START, Draughts.format(start))
    }

    // ---------- Coups : valeurs de référence (perft du jeu de dames à 10 × 10) ----------

    @Test
    fun lesCoupsPossiblesDepuisLeDepartCorrespondentAuxValeursDeReference() {
        val start = Draughts.parse(Draughts.START)
        assertEquals(9, perft(start, Draughts.WHITE, 1))
        assertEquals(81, perft(start, Draughts.WHITE, 2))
        assertEquals(658, perft(start, Draughts.WHITE, 3))
        assertEquals(4265, perft(start, Draughts.WHITE, 4))
        assertEquals(27117, perft(start, Draughts.WHITE, 5))
    }

    // ---------- Pions ----------

    @Test
    fun unPionAvanceEnDiagonaleVersLAvantSeulement() {
        // Pion blanc en 74 (rangée 7) : il monte en 63 ou 65, jamais en 83 ou 85.
        assertEquals(setOf(listOf(74, 63), listOf(74, 65)), paths(position(74 to 1), Draughts.WHITE))
        // Pion noir en 43 : il descend en 52 ou 54.
        assertEquals(setOf(listOf(43, 52), listOf(43, 54)), paths(position(43 to 2), Draughts.BLACK))
    }

    @Test
    fun unPionPrendEnAvantEtEnArriere() {
        assertEquals(setOf(listOf(54, 32)), paths(position(54 to 1, 43 to 2), Draughts.WHITE))
        assertEquals(setOf(listOf(32, 54)), paths(position(32 to 1, 43 to 2), Draughts.WHITE))
        assertEquals(setOf(listOf(43, 65)), paths(position(43 to 2, 54 to 1), Draughts.BLACK))
    }

    @Test
    fun laPriseEstObligatoire() {
        // Le pion blanc en 54 peut avancer, mais doit prendre le pion noir en 43.
        val moves = Draughts.legalMoves(position(54 to 1, 43 to 2), Draughts.WHITE)
        assertTrue(moves.all { it.isCapture })
        assertEquals(listOf(43), moves.single().captured)
    }

    @Test
    fun onNePeutPasPrendreSiLaCaseDerriereEstOccupee() {
        // Le pion noir en 43 est protégé par un autre pion noir en 32.
        val moves = Draughts.legalMoves(position(54 to 1, 43 to 2, 32 to 2), Draughts.WHITE)
        assertTrue(moves.none { it.isCapture })
    }

    @Test
    fun ilFautPrendreLeMaximumDePieces() {
        // Prendre le pion en 63 (une pièce) est possible, mais la rafle 65 puis 45 (deux pièces) est seule permise.
        val moves = Draughts.legalMoves(position(74 to 1, 63 to 2, 65 to 2, 45 to 2), Draughts.WHITE)
        assertEquals(1, moves.size)
        assertEquals(listOf(74, 56, 34), moves.single().path)
        assertEquals(listOf(65, 45), moves.single().captured)
    }

    @Test
    fun unPionPeutRepasserParSaCaseDeDepartSansPrendreDeuxFoisLaMemePiece() {
        val found = paths(position(74 to 1, 63 to 2, 41 to 2, 43 to 2, 65 to 2, 45 to 2), Draughts.WHITE)
        assertEquals(
            setOf(
                listOf(74, 52, 34, 56, 74),
                listOf(74, 56, 34, 52, 30),
                listOf(74, 56, 34, 52, 74)
            ),
            found
        )
    }

    @Test
    fun unPionAQuiPlusRienNEstPermisEstBloque() {
        assertTrue(Draughts.legalMoves(position(81 to 2, 90 to 1, 92 to 1), Draughts.BLACK).isEmpty())
    }

    // ---------- Promotion ----------

    @Test
    fun unPionQuiFinitSurLaDerniereRangeeDevientDame() {
        val cells = position(14 to 1)
        val move = Draughts.legalMoves(cells, Draughts.WHITE).first { it.to == 3 }
        assertEquals(Draughts.KING_WHITE, Draughts.afterMove(cells, move)[3])

        val black = position(85 to 2)
        val blackMove = Draughts.legalMoves(black, Draughts.BLACK).first { it.to == 94 }
        assertEquals(Draughts.KING_BLACK, Draughts.afterMove(black, blackMove)[94])
    }

    @Test
    fun unPionQuiPasseParLaDerniereRangeeEnContinuantDePrendreResteUnPion() {
        val cells = position(25 to 1, 14 to 2, 12 to 2)
        val move = Draughts.legalMoves(cells, Draughts.WHITE).single()
        assertEquals(listOf(25, 3, 21), move.path)
        val after = Draughts.afterMove(cells, move)
        assertEquals(Draughts.MAN_WHITE, after[21])
        assertEquals(0, after[14])
        assertEquals(0, after[12])
        // S'il s'arrête sur la dernière rangée, en revanche, il est couronné.
        val stop = position(25 to 1, 14 to 2)
        val stopMove = Draughts.legalMoves(stop, Draughts.WHITE).single()
        assertEquals(listOf(25, 3), stopMove.path)
        assertEquals(Draughts.KING_WHITE, Draughts.afterMove(stop, stopMove)[3])
    }

    // ---------- Dames ----------

    @Test
    fun uneDameSeDeplaceDeLoinEnDiagonale() {
        val moves = paths(position(94 to 3), Draughts.WHITE)
        assertTrue(listOf(94, 50) in moves)
        assertTrue(listOf(94, 49) in moves)
        assertTrue(listOf(94, 83) in moves)
        assertEquals(9, moves.size)
    }

    @Test
    fun uneDamePrendDeLoinEtDoitContinuerSiElleLePeut() {
        // La dame saute le pion en 72 et doit atterrir en 61 pour continuer sur le pion en 52 : seules ces rafles sont permises.
        val found = paths(position(94 to 3, 72 to 2, 52 to 2), Draughts.WHITE)
        assertEquals(
            setOf(listOf(94, 61, 43), listOf(94, 61, 34), listOf(94, 61, 25), listOf(94, 61, 16), listOf(94, 61, 7)),
            found
        )
    }

    @Test
    fun uneDameNePeutPasSauterDeuxPiecesCollees() {
        // Deux pions noirs côte à côte sur sa diagonale : impossible de les prendre.
        val moves = Draughts.legalMoves(position(94 to 3, 83 to 2, 72 to 2), Draughts.WHITE)
        assertTrue(moves.none { it.isCapture })
    }

    // ---------- Séance ----------

    @Test
    fun lesBlancsCommencentEtOnJoueChacunSonTour() {
        val match = DraughtsMatch()
        assertEquals(0, match.currentSeat)
        assertEquals(Draughts.WHITE, match.colorOf(0))
        assertEquals(Draughts.BLACK, match.colorOf(1))

        // Le deuxième joueur ne peut pas ouvrir la partie.
        assertNull(match.play(listOf(34, 45), 1))
        val next = match.play(listOf(65, 54), 0)!!
        assertEquals(Draughts.BLACK, next.turn)
        assertEquals(1, next.currentSeat)
        assertEquals(listOf(65, 54), next.last)
        assertEquals(Draughts.MAN_WHITE, next.cells[54])
        assertEquals(0, next.cells[65])
        // Le premier joueur ne peut pas jouer deux fois de suite.
        assertNull(next.play(listOf(54, 43), 0))
    }

    @Test
    fun unCoupContraireAuxReglesEstRefuse() {
        val match = DraughtsMatch()
        assertNull(match.play(listOf(65, 43), 0))   // pas une diagonale d'une case
        assertNull(match.play(listOf(65, 54, 43), 0)) // coup plus long que permis
        assertNull(match.play(listOf(54, 43), 0))   // pas de pièce en 54
        assertNull(match.play(listOf(34, 45), 0))   // pièce adverse
        assertNull(match.play(listOf(92, 81), 0))   // case d'arrivée occupée par un pion blanc
        assertNull(match.play(emptyList(), 0))
    }

    @Test
    fun lePionAdverseCaptureEstRetireEtLaVictoireEstCompteeQuandIlNeResteRien() {
        val match = DraughtsMatch(board = Draughts.format(position(32 to 1, 21 to 2)))
        val won = match.play(listOf(32, 10), 0)!!
        assertEquals(Draughts.WHITE, won.result)
        assertTrue(won.isOver)
        assertEquals(0, won.winnerSeat)
        assertEquals(1, won.winsA)
        assertEquals(0, won.winsB)
        assertEquals(0, won.cells[21])
        // La partie finie ne se joue plus.
        assertTrue(won.legalMoves.isEmpty())
        assertNull(won.play(listOf(10, 1), 1))
    }

    @Test
    fun celuiQuiNePeutPlusJouerPerdMemeAvecDesPieces() {
        // Le pion noir en 81 est enfermé par cinq pions blancs : aucune prise ni aucun pas pour lui.
        val board = Draughts.format(position(81 to 2, 90 to 1, 92 to 1, 70 to 1, 72 to 1, 63 to 1, 45 to 1))
        val won = DraughtsMatch(board = board).play(listOf(45, 34), 0)!!
        assertEquals(Draughts.WHITE, won.result)
        assertEquals(1, won.winsA)
        assertEquals(Draughts.MAN_BLACK, won.cells[81])
    }

    @Test
    fun vingtCinqCoupsDeDamesParCampSansPriseRendentLaPartieNulle() {
        val board = Draughts.format(position(94 to 3, 7 to 4))
        val almost = DraughtsMatch(board = board, quiet = Draughts.QUIET_LIMIT - 1)
        val drawn = almost.play(listOf(94, 83), 0)!!
        assertEquals(Draughts.DRAW, drawn.result)
        assertEquals(1, drawn.draws)
        assertEquals(-1, drawn.winnerSeat)
    }

    @Test
    fun unMouvementDePionRemetLeCompteurAZero() {
        val board = Draughts.format(position(74 to 1, 7 to 4))
        val match = DraughtsMatch(board = board, quiet = Draughts.QUIET_LIMIT - 1)
        val next = match.play(listOf(74, 63), 0)!!
        assertEquals(0, next.quiet)
        assertFalse(next.isOver)
    }

    @Test
    fun abandonnerFaitGagnerLAdversaire() {
        val match = DraughtsMatch()
        val resigned = match.resign(0)!!
        assertEquals(Draughts.BLACK, resigned.result)
        assertEquals(1, resigned.winnerSeat)
        assertEquals(1, resigned.winsB)
        assertNull(resigned.resign(1))
    }

    @Test
    fun laPartieSuivanteEchangeLesCouleursEtGardeLeScore() {
        val first = DraughtsMatch().resign(0)!!
        assertNull(DraughtsMatch().newRound())

        val second = first.newRound()!!
        assertEquals(Draughts.START, second.board)
        assertEquals(1, second.starter)
        assertEquals(Draughts.WHITE, second.turn)
        assertEquals(Draughts.ONGOING, second.result)
        assertEquals(emptyList<Int>(), second.last)
        assertEquals(1, second.winsB)
        // Le deuxième joueur a maintenant les blancs et ouvre la partie.
        assertEquals(Draughts.WHITE, second.colorOf(1))
        assertEquals(1, second.currentSeat)
        assertNull(second.play(listOf(65, 54), 0))
        assertNotNull(second.play(listOf(65, 54), 1))

        // Abandon du premier joueur, qui a les noirs : les blancs (le deuxième joueur) gagnent.
        val resigned = second.resign(0)!!
        assertEquals(Draughts.WHITE, resigned.result)
        assertEquals(1, resigned.winnerSeat)
        assertEquals(2, resigned.winsB)
    }
}
