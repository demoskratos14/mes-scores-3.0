package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Les échecs chacun sur son téléphone, testés sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (place 0, blancs à la première manche), l'invité « Ben » (place 1, noirs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class ChessOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = ChessOnline(session)
    }

    /** Anna organise, Ben rejoint : la partie est ouverte sur les deux téléphones. */
    private fun table(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingChess("Anna")
        host.online.openLobby("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    private fun move(text: String): ChessMove = ChessMove.parse(text)!!

    // ---------- Arrivée à la table ----------

    @Test
    fun lInviteRejointLaTableEtLaPartieCommenceAussitot() {
        val (host, guest) = table()

        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(0, host.online.seat)
        assertEquals(1, guest.online.seat)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertTrue(guest.online.active)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.CHESS, guest.session.kind)
        assertEquals(ChessMatch(), guest.online.match)
        // Anna a les blancs et la main ; Ben les noirs.
        assertTrue(host.online.playsWhite)
        assertFalse(guest.online.playsWhite)
        assertTrue(host.online.myTurn)
        assertFalse(guest.online.myTurn)
    }

    @Test
    fun avantLArriveeDeLInviteLOrganisateurAttend() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingChess("Anna")
        host.online.openLobby("Anna")

        assertTrue(host.online.active)
        assertFalse(host.online.ready)
        assertFalse(host.online.myTurn)
        host.online.play(move("e2e4"))
        assertEquals("", host.online.match.moves)
    }

    // ---------- Coups ----------

    @Test
    fun chacunJoueSonTourEtLesDeuxTelephonesVoientLaMemePartie() {
        val (host, guest) = table()

        host.online.play(move("e2e4"))
        assertEquals("e2e4", host.online.match.moves)
        assertEquals("e2e4", guest.online.match.moves)
        assertFalse(host.online.myTurn)
        assertTrue(guest.online.myTurn)

        guest.online.play(move("e7e5"))
        assertEquals("e2e4 e7e5", host.online.match.moves)
        assertEquals("e2e4 e7e5", guest.online.match.moves)
        assertTrue(host.online.myTurn)
    }

    @Test
    fun onNeJoueNiHorsDeSonTourNiUnCoupInterdit() {
        val (host, guest) = table()

        // L'invité ne peut pas ouvrir la partie.
        guest.online.play(move("e7e5"))
        assertEquals("", host.online.match.moves)

        // Un coup interdit (le pion ne recule pas, le cavalier ne va pas en ligne) est refusé.
        host.online.play(move("e2e5"))
        host.online.play(move("b1b3"))
        assertEquals("", host.online.match.moves)

        host.online.play(move("e2e4"))
        // L'organisateur ne peut pas jouer deux fois de suite.
        host.online.play(move("d2d4"))
        assertEquals("e2e4", guest.online.match.moves)
    }

    @Test
    fun unCoupForgeParUnInviteTricheurEstIgnoreParLOrganisateur() {
        val (host, guest) = table()

        // Le téléphone de l'invité n'a pas la main, mais un message forgé arrive quand même.
        guest.session.sendToHost(SyncMessage.Chess(ChessMessage.Move("e7e5")))
        assertEquals("", host.online.match.moves)

        host.online.play(move("e2e4"))
        // Au tour de l'invité, un coup interdit est ignoré lui aussi.
        guest.session.sendToHost(SyncMessage.Chess(ChessMessage.Move("e7e3")))
        guest.session.sendToHost(SyncMessage.Chess(ChessMessage.Move("n'importe quoi")))
        assertEquals("e2e4", host.online.match.moves)
        assertEquals("e2e4", guest.online.match.moves)
    }

    @Test
    fun lInviteChoisitLaPieceDeLaPromotion() {
        val (host, guest) = table()
        // Une promotion au bout d'une partie courte : h2h4 g7g5 h4g5 h7h6 g5h6 f7f6 h6h7 e7e6 h7g8n.
        for ((index, text) in "h2h4 g7g5 h4g5 h7h6 g5h6 f7f6 h6h7 e7e6".split(" ").withIndex()) {
            (if (index % 2 == 0) host else guest).online.play(move(text))
        }
        assertEquals(8, host.online.match.game.moves.size)

        // Sans pièce choisie, le coup n'est pas permis.
        host.online.play(move("h7g8"))
        assertEquals(8, host.online.match.game.moves.size)

        host.online.play(move("h7g8n"))
        assertEquals(9, guest.online.match.game.moves.size)
        assertEquals('N', guest.online.match.game.position.board[Chess.squareOf("g8")!!])
    }

    // ---------- Manches et score ----------

    private fun foolsMate(host: Phone, guest: Phone) {
        host.online.play(move("f2f3"))
        guest.online.play(move("e7e5"))
        host.online.play(move("g2g4"))
        guest.online.play(move("d8h4"))
    }

    @Test
    fun unMatEstCompteEtFigeLaPartie() {
        val (host, guest) = table()
        foolsMate(host, guest)

        assertTrue(host.online.match.isOver)
        assertEquals(1, host.online.match.wins1)
        assertEquals(1, guest.online.match.wins1)
        assertEquals(ChessReason.CHECKMATE, guest.online.match.result?.reason)
        assertEquals(1, guest.online.match.winnerSeat)
        assertFalse(host.online.myTurn)
        assertFalse(guest.online.myTurn)
    }

    @Test
    fun lInvitePeutLancerLaMancheSuivanteEtLesCouleursSEchangent() {
        val (host, guest) = table()
        foolsMate(host, guest)

        guest.online.newRound()

        assertEquals("", host.online.match.moves)
        assertEquals("", guest.online.match.moves)
        // Ben a maintenant les blancs et la main ; le score est gardé.
        assertTrue(guest.online.playsWhite)
        assertTrue(guest.online.myTurn)
        assertFalse(host.online.myTurn)
        assertEquals(1, guest.online.match.wins1)
    }

    @Test
    fun personneNePeutRelancerUneMancheEnCours() {
        val (host, guest) = table()
        host.online.play(move("e2e4"))

        host.online.newRound()
        guest.online.newRound()

        assertEquals("e2e4", host.online.match.moves)
        assertEquals("e2e4", guest.online.match.moves)
    }

    // ---------- Abandon et nulle ----------

    @Test
    fun lInviteAbandonneEtLOrganisateurGagne() {
        val (host, guest) = table()
        host.online.play(move("e2e4"))

        guest.online.resign()

        assertEquals(ChessReason.RESIGNATION, host.online.match.result?.reason)
        assertEquals(0, host.online.match.winnerSeat)
        assertEquals(1, guest.online.match.wins0)
        assertTrue(guest.online.match.isOver)
    }

    @Test
    fun lOrganisateurAbandonneEtLInviteGagne() {
        val (host, guest) = table()

        host.online.resign()

        assertEquals(1, guest.online.match.winnerSeat)
        assertEquals(1, guest.online.match.wins1)
    }

    @Test
    fun uneNulleProposeeParLInviteEstAccepteeParLOrganisateur() {
        val (host, guest) = table()

        guest.online.offerDraw()
        assertEquals(1, host.online.match.drawOffer)
        assertEquals(1, guest.online.match.drawOffer)
        // L'offreur ne peut pas répondre à sa propre proposition.
        guest.online.answerDraw(true)
        assertFalse(host.online.match.isOver)

        host.online.answerDraw(true)
        assertEquals(ChessReason.AGREEMENT, guest.online.match.result?.reason)
        assertEquals(1, host.online.match.draws)
        assertEquals(1, guest.online.match.draws)
    }

    @Test
    fun uneNulleProposeeParLOrganisateurEstRefuseeParLInvite() {
        val (host, guest) = table()

        host.online.offerDraw()
        assertEquals(0, guest.online.match.drawOffer)

        guest.online.answerDraw(false)

        assertEquals(-1, host.online.match.drawOffer)
        assertFalse(host.online.match.isOver)
        assertEquals(0, host.online.match.draws)
    }

    @Test
    fun uneNulleNonRepondueEstRefuseeQuandOnJoue() {
        val (host, guest) = table()
        guest.online.offerDraw()

        // Anna joue sans répondre : la proposition de Ben est refusée.
        host.online.play(move("e2e4"))

        assertEquals(-1, guest.online.match.drawOffer)
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendreUnAutreJoueur() {
        val (host, guest) = table()
        host.online.play(move("e2e4"))

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(host.online.ready)
        assertEquals(ChessNotice.PLAYER_LEFT, host.online.notice)
        assertEquals("", host.online.match.moves)
        assertTrue(host.online.active)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = table()

        host.online.leave()

        assertFalse(host.online.active)
        assertTrue(guest.online.active)
        assertEquals(ChessNotice.HOST_LEFT, guest.online.notice)
    }

    @Test
    fun quitterRemetLaTableAZero() {
        val (host, guest) = table()
        host.online.play(move("e2e4"))

        guest.online.leave()

        assertEquals(-1, guest.online.seat)
        assertEquals(emptyList<String>(), guest.online.lobby)
        assertEquals(ChessMatch(), guest.online.match)
        assertEquals(ChessNotice.NONE, guest.online.notice)
    }
}
