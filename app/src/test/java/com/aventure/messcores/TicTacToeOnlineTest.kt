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
 * Le Morpion chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (croix, place 0), l'invité « Ben » (ronds, place 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class TicTacToeOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = TicTacToeOnline(session)
    }

    /** Anna organise, Ben rejoint : la partie est ouverte sur les deux téléphones. */
    private fun table(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingTicTacToe("Anna")
        host.online.openLobby("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    // ---------- Arrivée à la table ----------

    @Test
    fun lInviteRejointLaTableEtLaPartieCommenceAussitot() {
        val (host, guest) = table()

        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(0, host.online.seat)
        assertEquals(1, guest.online.seat)
        assertEquals(1, host.online.mySymbol)
        assertEquals(2, guest.online.mySymbol)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertTrue(guest.online.active)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.MORPION, guest.session.kind)
        assertEquals(TicTacToeMatch(), guest.online.match)
    }

    @Test
    fun avantLArriveeDeLInviteLOrganisateurAttend() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingTicTacToe("Anna")
        host.online.openLobby("Anna")

        assertTrue(host.online.active)
        assertFalse(host.online.ready)
        assertFalse(host.online.myTurn)
        host.online.play(0)
        assertEquals(TicTacToe.EMPTY, host.online.match.board)
    }

    // ---------- Coups ----------

    @Test
    fun chacunJoueSonTourEtLesDeuxTelephonesVoientLaMemeGrille() {
        val (host, guest) = table()

        assertTrue(host.online.myTurn)
        assertFalse(guest.online.myTurn)
        host.online.play(4)
        assertEquals("000010000", host.online.match.board)
        assertEquals("000010000", guest.online.match.board)
        assertFalse(host.online.myTurn)
        assertTrue(guest.online.myTurn)

        guest.online.play(0)
        assertEquals("200010000", host.online.match.board)
        assertEquals("200010000", guest.online.match.board)
        assertTrue(host.online.myTurn)
    }

    @Test
    fun onNeJoueNiHorsDeSonTourNiSurUneCasePrise() {
        val (host, guest) = table()

        // L'invité ne peut pas ouvrir la partie.
        guest.online.play(0)
        assertEquals(TicTacToe.EMPTY, host.online.match.board)

        host.online.play(4)
        // L'organisateur ne peut pas jouer deux fois de suite, l'invité ne peut pas prendre une case occupée.
        host.online.play(0)
        guest.online.play(4)
        assertEquals("000010000", host.online.match.board)
        assertEquals("000010000", guest.online.match.board)
        assertTrue(guest.online.myTurn)
    }

    @Test
    fun unCoupEnvoyeHorsDeSonTourParUnInviteTricheurEstIgnoreParLOrganisateur() {
        val (host, guest) = table()

        // Le téléphone de l'invité n'a pas la main, mais un message forgé arrive quand même.
        guest.session.sendToHost(SyncMessage.Ttt(TttMessage.Move(0)))

        assertEquals(TicTacToe.EMPTY, host.online.match.board)
        assertEquals(TicTacToe.EMPTY, guest.online.match.board)
    }

    // ---------- Manches et score ----------

    private fun playRound(host: Phone, guest: Phone) {
        // Anna aligne la première ligne : 0, 1, 2 ; Ben joue 3 et 4.
        host.online.play(0)
        guest.online.play(3)
        host.online.play(1)
        guest.online.play(4)
        host.online.play(2)
    }

    @Test
    fun uneVictoireEstCompteeEtFigeLaGrille() {
        val (host, guest) = table()
        playRound(host, guest)

        assertEquals(1, host.online.match.winsX)
        assertEquals(1, guest.online.match.winsX)
        assertTrue(guest.online.match.isOver)
        assertFalse(guest.online.myTurn)
        guest.online.play(8)
        assertEquals("111220000", host.online.match.board)
    }

    @Test
    fun lInvitePeutLancerLaMancheSuivanteEtLAutreJoueurCommence() {
        val (host, guest) = table()
        playRound(host, guest)

        guest.online.newRound()

        assertEquals(TicTacToe.EMPTY, host.online.match.board)
        assertEquals(TicTacToe.EMPTY, guest.online.match.board)
        assertEquals(1, host.online.match.starter)
        // Ben commence la deuxième manche ; le score est gardé.
        assertTrue(guest.online.myTurn)
        assertFalse(host.online.myTurn)
        assertEquals(1, guest.online.match.winsX)
    }

    @Test
    fun lOrganisateurPeutLancerLaMancheSuivanteMaisPasEnPleinePartie() {
        val (host, guest) = table()
        host.online.play(0)
        // Manche en cours : personne ne peut la relancer.
        host.online.newRound()
        guest.online.newRound()
        assertEquals("100000000", host.online.match.board)

        // Anna aligne la première ligne.
        guest.online.play(3)
        host.online.play(1)
        guest.online.play(4)
        host.online.play(2)
        assertTrue(host.online.match.isOver)

        host.online.newRound()
        assertEquals(TicTacToe.EMPTY, guest.online.match.board)
        assertEquals(1, guest.online.match.winsX)
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendreUnAutreJoueur() {
        val (host, guest) = table()
        host.online.play(0)

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(host.online.ready)
        assertEquals(TicTacToeNotice.PLAYER_LEFT, host.online.notice)
        assertEquals(TicTacToe.EMPTY, host.online.match.board)
        assertTrue(host.online.active)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = table()

        host.online.leave()

        assertFalse(host.online.active)
        assertTrue(guest.online.active)
        assertEquals(TicTacToeNotice.HOST_LEFT, guest.online.notice)
    }

    @Test
    fun quitterRemetLaTableAZero() {
        val (host, guest) = table()
        host.online.play(0)

        guest.online.leave()

        assertEquals(-1, guest.online.seat)
        assertEquals(emptyList<String>(), guest.online.lobby)
        assertEquals(TicTacToeMatch(), guest.online.match)
        assertEquals(TicTacToeNotice.NONE, guest.online.notice)
    }

    // ---------- Messages ----------

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val match = TicTacToeMatch(board = "120010000", starter = 1, winsX = 2, winsO = 3, draws = 1)
        val messages = listOf(
            TttMessage.Table(listOf("Anna", "Ben"), 1, match, TicTacToeNotice.NONE),
            TttMessage.Table(listOf("Anna"), 1, TicTacToeMatch(), TicTacToeNotice.PLAYER_LEFT),
            TttMessage.Move(7),
            TttMessage.NewRound
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Ttt(message)))
            assertEquals(message.toString(), SyncMessage.Ttt(message), decoded)
        }
    }

    @Test
    fun uneGrilleIllisibleEtUnPremierJoueurAbsurdeSontRamenesAUneValeurPermise() {
        val json = TttMessage.Table(listOf("Anna", "Ben"), 1, TicTacToeMatch(), TicTacToeNotice.NONE).toJson()
            .put("board", "zzz").put("starter", 7).put("wx", -4)

        val decoded = tttMessageFromJson(json) as TttMessage.Table

        assertEquals(TicTacToe.EMPTY, decoded.match.board)
        assertEquals(1, decoded.match.starter)
        assertEquals(0, decoded.match.winsX)
    }

    @Test
    fun unMessageInconnuEstIgnore() {
        assertNull(tttMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(tttMessageFromJson(TttMessage.Move(3).toJson()))
    }
}
