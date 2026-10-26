package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le Puissance 4 chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (rouges, place 0), l'invité « Ben » (jaunes, place 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class Connect4OnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = Connect4Online(session)
    }

    private fun table(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingConnect4("Anna")
        host.online.openLobby("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    @Test
    fun lInviteRejointLaTableEtLaPartieCommenceAussitot() {
        val (host, guest) = table()

        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(1, host.online.myColor)
        assertEquals(2, guest.online.myColor)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.CONNECT4, guest.session.kind)
        assertEquals(Connect4Match(), guest.online.match)
    }

    @Test
    fun avantLArriveeDeLInviteOnNeJouePas() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingConnect4("Anna")
        host.online.openLobby("Anna")

        assertFalse(host.online.ready)
        host.online.play(3)
        assertEquals(Connect4.EMPTY, host.online.match.board)
    }

    @Test
    fun chacunJoueSonTourEtLesDeuxTelephonesVoientLaMemeGrille() {
        val (host, guest) = table()

        assertTrue(host.online.myTurn)
        assertFalse(guest.online.myTurn)
        host.online.play(3)
        assertEquals(1, guest.online.match.cells[Connect4.index(5, 3)])
        assertTrue(guest.online.myTurn)

        guest.online.play(3)
        assertEquals(2, host.online.match.cells[Connect4.index(4, 3)])
        assertEquals(host.online.match, guest.online.match)
        assertTrue(host.online.myTurn)
    }

    @Test
    fun onNeJoueNiHorsDeSonTourNiDansUneColonnePleine() {
        val (host, guest) = table()

        guest.online.play(0)
        assertEquals(Connect4.EMPTY, host.online.match.board)

        host.online.play(0)
        host.online.play(1)
        assertEquals(1, host.online.match.cells.count { it != 0 })

        // Six jetons dans la colonne 0, sans quatre alignés, puis la colonne est pleine.
        guest.online.play(0)
        host.online.play(0)
        guest.online.play(0)
        host.online.play(0)
        guest.online.play(0)
        assertTrue(Connect4.isColumnFull(host.online.match.cells, 0))
        assertTrue(host.online.myTurn)
        host.online.play(0)
        assertEquals(6, host.online.match.cells.count { it != 0 })
    }

    @Test
    fun unCoupForgeHorsDeSonTourEstIgnoreParLOrganisateur() {
        val (host, guest) = table()

        guest.session.sendToHost(SyncMessage.C4(C4Message.Drop(0)))

        assertEquals(Connect4.EMPTY, host.online.match.board)
        assertEquals(Connect4.EMPTY, guest.online.match.board)
    }

    /** Anna aligne quatre jetons sur la rangée du bas ; Ben joue au-dessus. */
    private fun playRound(host: Phone, guest: Phone) {
        host.online.play(0); guest.online.play(0)
        host.online.play(1); guest.online.play(1)
        host.online.play(2); guest.online.play(2)
        host.online.play(3)
    }

    @Test
    fun uneVictoireEstCompteeEtFigeLaGrille() {
        val (host, guest) = table()
        playRound(host, guest)

        assertEquals(1, host.online.match.winsRed)
        assertEquals(1, guest.online.match.winsRed)
        assertTrue(guest.online.match.isOver)
        assertFalse(guest.online.myTurn)
        val board = host.online.match.board
        guest.online.play(5)
        assertEquals(board, host.online.match.board)
    }

    @Test
    fun lInvitePeutLancerLaMancheSuivanteEtLAutreJoueurCommence() {
        val (host, guest) = table()
        host.online.newRound()
        assertEquals(Connect4.EMPTY, host.online.match.board)
        playRound(host, guest)

        guest.online.newRound()

        assertEquals(Connect4.EMPTY, host.online.match.board)
        assertEquals(1, host.online.match.starter)
        assertTrue(guest.online.myTurn)
        assertEquals(1, guest.online.match.winsRed)
    }

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendre() {
        val (host, guest) = table()
        host.online.play(0)

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(host.online.ready)
        assertEquals(Connect4Notice.PLAYER_LEFT, host.online.notice)
        assertEquals(Connect4.EMPTY, host.online.match.board)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = table()

        host.online.leave()

        assertFalse(host.online.active)
        assertEquals(Connect4Notice.HOST_LEFT, guest.online.notice)
    }

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val match = Connect4Match(board = "0".repeat(41) + "1", starter = 1, winsRed = 2, winsYellow = 3, draws = 1)
        val messages = listOf(
            C4Message.Table(listOf("Anna", "Ben"), 1, match, Connect4Notice.NONE),
            C4Message.Table(listOf("Anna"), 1, Connect4Match(), Connect4Notice.PLAYER_LEFT),
            C4Message.Drop(6),
            C4Message.NewRound
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.C4(message)))
            assertEquals(message.toString(), SyncMessage.C4(message), decoded)
        }
    }

    @Test
    fun uneGrilleIllisibleEtUnPremierJoueurAbsurdeSontRamenesAUneValeurPermise() {
        val json = C4Message.Table(listOf("Anna", "Ben"), 1, Connect4Match(), Connect4Notice.NONE).toJson()
            .put("board", "zzz").put("starter", 7).put("wr", -4)

        val decoded = c4MessageFromJson(json) as C4Message.Table

        assertEquals(Connect4.EMPTY, decoded.match.board)
        assertEquals(1, decoded.match.starter)
        assertEquals(0, decoded.match.winsRed)
    }

    @Test
    fun unMessageInconnuEstIgnore() {
        assertNull(c4MessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(c4MessageFromJson(C4Message.Drop(3).toJson()))
    }
}
