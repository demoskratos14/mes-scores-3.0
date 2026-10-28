package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessClockTest {

    private fun move(text: String): ChessMove = ChessMove.parse(text)!!

    @Test
    fun laPenduleSeChoisitAvantLePremierCoupSeulement() {
        val match = ChessMatch().withClock(300, 3)!!
        assertTrue(match.clockOn)
        assertEquals(300_000L, match.time0Ms)
        assertEquals(300_000L, match.time1Ms)
        val started = match.play(move("e2e4"), 0)!!
        assertNull(started.withClock(60, 0))
    }

    @Test
    fun laPenduleDemarreAuPremierCoupDesBlancs() {
        val match = ChessMatch().withClock(300, 0)!!
        assertFalse(match.clockRunning)
        // Avant le premier coup, le temps n'est pas décompté.
        assertEquals(300_000L, match.remainingMs(0, 90_000L))
        val started = match.play(move("e2e4"), 0, elapsedMs = 90_000L)!!
        assertTrue(started.clockRunning)
        assertEquals(300_000L, started.time0Ms)
    }

    @Test
    fun leTempsEstDecompteEtLIncrementAjoute() {
        var match = ChessMatch().withClock(300, 3)!!
        match = match.play(move("e2e4"), 0, 0L)!!
        assertEquals(303_000L, match.time0Ms)
        match = match.play(move("e7e5"), 1, 10_000L)!!
        assertEquals(293_000L, match.time1Ms)
        // Le temps qui s'écoule pèse sur le joueur dont c'est le tour.
        assertEquals(283_000L, match.remainingMs(0, 20_000L))
        assertEquals(293_000L, match.remainingMs(1, 20_000L))
    }

    @Test
    fun letempsEcouleFaitPerdreLaManche() {
        var match = ChessMatch().withClock(60, 0)!!
        match = match.play(move("e2e4"), 0)!!
        match = match.flag(1)!!
        assertEquals(ChessReason.TIMEOUT, match.result?.reason)
        assertEquals(0, match.winnerSeat)
        assertEquals(1, match.wins0)
        assertTrue(match.isOver)
        assertNull(match.flag(0))
    }

    @Test
    fun unCoupJoueApresLeTempsEstPerdu() {
        var match = ChessMatch().withClock(60, 0)!!
        match = match.play(move("e2e4"), 0)!!
        val late = match.play(move("e7e5"), 1, elapsedMs = 61_000L)!!
        assertEquals(ChessReason.TIMEOUT, late.result?.reason)
        assertEquals(0, late.winnerSeat)
        assertEquals("e2e4", late.moves)
    }

    @Test
    fun sansPenduleRienNeChange() {
        val match = ChessMatch()
        assertFalse(match.clockOn)
        assertNull(match.flag(0))
        val played = match.play(move("e2e4"), 0, elapsedMs = 999_999L)!!
        assertFalse(played.isOver)
    }

    @Test
    fun laMancheSuivanteRemetLesPendules() {
        var match = ChessMatch().withClock(180, 2)!!
        match = match.play(move("e2e4"), 0)!!
        match = match.flag(1)!!
        val next = match.newRound()!!
        assertEquals(180, next.baseSec)
        assertEquals(2, next.incSec)
        assertEquals(180_000L, next.time0Ms)
        assertEquals(180_000L, next.time1Ms)
        assertEquals(-1, next.timedOut)
        assertEquals(1, next.whiteSeat)
    }

    @Test
    fun laPenduleVoyageDansLeMessage() {
        val match = ChessMatch().withClock(300, 3)!!.play(move("e2e4"), 0)!!
        val table = ChessMessage.Table(listOf("Anna", "Ben"), 1, match, ChessNotice.NONE, elapsedMs = 4_200L)
        val back = chessMessageFromJson(table.toJson())
        assertNotNull(back)
        assertEquals(table, back)
    }

    @Test
    fun unVieuxMessageSansPenduleSeLitToujours() {
        val json = ChessMessage.Table(listOf("Anna", "Ben"), 1, ChessMatch(), ChessNotice.NONE).toJson()
        listOf("base", "inc", "t0", "t1", "timedOut", "elapsed").forEach { json.remove(it) }
        val back = chessMessageFromJson(json) as ChessMessage.Table
        assertEquals(ChessMatch(), back.match)
        assertEquals(0L, back.elapsedMs)
    }
}
