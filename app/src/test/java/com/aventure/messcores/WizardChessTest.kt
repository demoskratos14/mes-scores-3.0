package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardChessTest {

    private fun square(name: String): Int = Chess.squareOf(name)!!

    private fun detect(moves: String, next: String): MoveFx? {
        val before = ChessGame.fromMoves(moves).position.board
        return WizardFx.detect(before, ChessMove.parse(next)!!)
    }

    @Test
    fun unCoupSansPriseEstUnSimpleDeplacement() {
        val fx = detect("", "e2e4")!!
        assertFalse(fx.isCapture)
        assertEquals('P', fx.mover)
        assertEquals(square("e2"), fx.from)
        assertEquals(square("e4"), fx.to)
    }

    @Test
    fun uneRepriseDeLaPieceSurLaCaseDArrivee() {
        val fx = detect("e2e4 d7d5", "e4d5")!!
        assertTrue(fx.isCapture)
        assertEquals('P', fx.mover)
        assertEquals('p', fx.captured)
        assertEquals(square("d5"), fx.capturedSquare)
    }

    @Test
    fun laPriseEnPassantCasseLePionQuiEstSurLaRangeeDeDepart() {
        val fx = detect("e2e4 a7a6 e4e5 d7d5", "e5d6")!!
        assertTrue(fx.isCapture)
        assertEquals('p', fx.captured)
        assertEquals(square("d5"), fx.capturedSquare)
        assertEquals(square("d6"), fx.to)
    }

    @Test
    fun leRoqueNEstPasUnePrise() {
        val fx = detect("e2e4 e7e5 g1f3 b8c6 f1c4 g8f6", "e1g1")!!
        assertFalse(fx.isCapture)
        assertEquals('K', fx.mover)
    }

    @Test
    fun uneCaseDeDepartVideOuHorsDeLEchiquierNeDonneRien() {
        val board = ChessGame.fromMoves("").position.board
        assertNull(WizardFx.detect(board, ChessMove(square("e4"), square("e5"))))
        assertNull(WizardFx.detect(board, ChessMove(-1, 3)))
        assertNull(WizardFx.detect(board, ChessMove(3, 64)))
        assertNotNull(WizardFx.detect(board, ChessMove(square("e2"), square("e4"))))
    }

    @Test
    fun unePriseDureePlusLongtempsQuUnDeplacement() {
        val quiet = detect("", "e2e4")!!
        val capture = detect("e2e4 d7d5", "e4d5")!!
        assertTrue(wizardMoveDuration(capture) > wizardMoveDuration(quiet))
    }

    @Test
    fun lesPiecesDeProfilSeTournentVersLeurVictime() {
        // Par défaut les blanches regardent à gauche et les noires à droite (retournées).
        assertFalse(wizardFlip('P'))
        assertTrue(wizardFlip('p'))
        // Pour frapper, une pièce de profil se tourne du côté voulu, quelle que soit sa couleur.
        assertTrue(wizardFlip('N', faceRight = true))
        assertFalse(wizardFlip('n', faceRight = false))
        // Le fou, la dame et le roi ne se retournent jamais.
        assertFalse(wizardFlip('K', faceRight = true))
        assertFalse(wizardFlip('q', faceRight = true))
    }

    @Test
    fun seulesLesPiecesArmeesOntUneAttache() {
        for (piece in "RBNK") assertNotNull(wizardRig(piece))
        for (piece in "PQ") assertNull(wizardRig(piece))
    }
}
