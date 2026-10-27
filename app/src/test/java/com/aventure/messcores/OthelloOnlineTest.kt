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
 * L'Othello chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (place 0, noirs à la première manche), l'invité « Ben » (place 1, blancs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class OthelloOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = OthelloOnline(session)
    }

    /** Anna organise, Ben rejoint : la partie est ouverte sur les deux téléphones. */
    private fun table(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingOthello("Anna")
        host.online.openLobby("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    /** La grille après des coups successifs (couleur, case), calculée avec les règles pures. */
    private fun after(vararg moves: Pair<Int, Int>): String {
        var cells = Othello.parse(Othello.START)
        for ((color, index) in moves) cells = Othello.play(cells, index, color)!!
        return Othello.format(cells)
    }

    // ---------- Arrivée à la table ----------

    @Test
    fun lInviteRejointLaTableEtLaPartieCommenceAussitot() {
        val (host, guest) = table()

        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(0, host.online.seat)
        assertEquals(1, guest.online.seat)
        assertEquals(1, host.online.myColor)
        assertEquals(2, guest.online.myColor)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertTrue(guest.online.active)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.OTHELLO, guest.session.kind)
        assertEquals(OthelloMatch(), guest.online.match)
    }

    @Test
    fun avantLArriveeDeLInviteLOrganisateurAttend() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingOthello("Anna")
        host.online.openLobby("Anna")

        assertTrue(host.online.active)
        assertFalse(host.online.ready)
        assertFalse(host.online.myTurn)
        host.online.play(19)
        assertEquals(Othello.START, host.online.match.board)
    }

    // ---------- Coups ----------

    @Test
    fun chacunJoueSonTourEtLesDeuxTelephonesVoientLaMemeGrille() {
        val (host, guest) = table()

        assertTrue(host.online.myTurn)
        assertFalse(guest.online.myTurn)
        host.online.play(19)
        assertEquals(after(1 to 19), host.online.match.board)
        assertEquals(after(1 to 19), guest.online.match.board)
        assertEquals(19, guest.online.match.last)
        assertFalse(host.online.myTurn)
        assertTrue(guest.online.myTurn)

        guest.online.play(20)
        assertEquals(after(1 to 19, 2 to 20), host.online.match.board)
        assertEquals(after(1 to 19, 2 to 20), guest.online.match.board)
        assertTrue(host.online.myTurn)
    }

    @Test
    fun onNeJoueNiHorsDeSonTourNiSurUneCaseQuiNEncadreRien() {
        val (host, guest) = table()

        // L'invité ne peut pas ouvrir la partie.
        guest.online.play(20)
        assertEquals(Othello.START, host.online.match.board)

        // Une case qui n'encadre rien, une case occupée : refusées.
        host.online.play(0)
        host.online.play(27)
        assertEquals(Othello.START, host.online.match.board)
        assertTrue(host.online.myTurn)

        host.online.play(19)
        // L'organisateur ne peut pas jouer deux fois de suite.
        host.online.play(26)
        assertEquals(after(1 to 19), guest.online.match.board)
        assertTrue(guest.online.myTurn)
    }

    @Test
    fun unCoupEnvoyeHorsDeSonTourParUnInviteTricheurEstIgnoreParLOrganisateur() {
        val (host, guest) = table()

        // Le téléphone de l'invité n'a pas la main, mais un message forgé arrive quand même.
        guest.session.sendToHost(SyncMessage.Oth(OthMessage.Move(20)))

        assertEquals(Othello.START, host.online.match.board)
        assertEquals(Othello.START, guest.online.match.board)
    }

    @Test
    fun unCoupIllegalEnvoyeParLInviteALonSonTourEstIgnore() {
        val (host, guest) = table()
        host.online.play(19)

        guest.session.sendToHost(SyncMessage.Oth(OthMessage.Move(0)))

        assertEquals(after(1 to 19), host.online.match.board)
        assertTrue(guest.online.myTurn)
    }

    @Test
    fun laMancheSuivanteNeSeLanceQuUneFoisLaMancheFinie() {
        val (host, guest) = table()
        host.online.play(19)

        host.online.newRound()
        guest.online.newRound()

        assertEquals(after(1 to 19), host.online.match.board)
        assertEquals(after(1 to 19), guest.online.match.board)
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendreUnAutreJoueur() {
        val (host, guest) = table()
        host.online.play(19)

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(host.online.ready)
        assertEquals(OthelloNotice.PLAYER_LEFT, host.online.notice)
        assertEquals(Othello.START, host.online.match.board)
        assertTrue(host.online.active)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = table()

        host.online.leave()

        assertFalse(host.online.active)
        assertTrue(guest.online.active)
        assertEquals(OthelloNotice.HOST_LEFT, guest.online.notice)
    }

    @Test
    fun quitterRemetLaTableAZero() {
        val (host, guest) = table()
        host.online.play(19)

        guest.online.leave()

        assertEquals(-1, guest.online.seat)
        assertEquals(emptyList<String>(), guest.online.lobby)
        assertEquals(OthelloMatch(), guest.online.match)
        assertEquals(OthelloNotice.NONE, guest.online.notice)
    }

    // ---------- Messages ----------

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val match = OthelloMatch(
            board = after(1 to 19), starter = 1, turn = 2, last = 19, passed = 1, wins0 = 2, wins1 = 3, draws = 1
        )
        val messages = listOf(
            OthMessage.Table(listOf("Anna", "Ben"), 1, match, OthelloNotice.NONE),
            OthMessage.Table(listOf("Anna"), 1, OthelloMatch(), OthelloNotice.PLAYER_LEFT),
            OthMessage.Move(37),
            OthMessage.NewRound
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Oth(message)))
            assertEquals(message.toString(), SyncMessage.Oth(message), decoded)
        }
    }

    @Test
    fun uneGrilleIllisibleEtDesValeursAbsurdesSontRamenees()  {
        val json = OthMessage.Table(listOf("Anna", "Ben"), 1, OthelloMatch(), OthelloNotice.NONE).toJson()
            .put("board", "zzz").put("starter", 7).put("turn", 0).put("last", 500).put("w0", -4)

        val decoded = othMessageFromJson(json) as OthMessage.Table

        assertEquals(Othello.START, decoded.match.board)
        assertEquals(1, decoded.match.starter)
        assertEquals(1, decoded.match.turn)
        assertEquals(63, decoded.match.last)
        assertEquals(0, decoded.match.wins0)
    }

    @Test
    fun unMessageInconnuEstIgnore() {
        assertNull(othMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(othMessageFromJson(OthMessage.Move(3).toJson()))
    }
}
