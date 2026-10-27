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
 * Le Gomoku et la Pipopipette chacun sur son téléphone, testés sans téléphones : deux sessions reliées par un faux
 * lien radio. L'organisateur est « Anna » (premier joueur, place 0), l'invité « Ben » (deuxième joueur, place 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DuoOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val gomoku = DuoOnline(session, SyncKind.GOMOKU, { GomokuMatch() }, { GomokuMatch.decode(it) })
        val dots = DuoOnline(session, SyncKind.DOTS, { DotsMatch() }, { DotsMatch.decode(it) })
    }

    private fun link(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        return host to guest
    }

    /** Anna organise un Gomoku, Ben le rejoint : la partie est ouverte sur les deux téléphones. */
    private fun gomokuTable(): Pair<Phone, Phone> {
        val (host, guest) = link()
        host.session.startHostingDuo(SyncKind.GOMOKU, "Anna")
        host.gomoku.openLobby("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    private fun dotsTable(): Pair<Phone, Phone> {
        val (host, guest) = link()
        host.session.startHostingDuo(SyncKind.DOTS, "Anna")
        host.dots.openLobby("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    // ---------- Arrivée à la table ----------

    @Test
    fun lInviteRejointLaTableEtLaPartieCommenceAussitot() {
        val (host, guest) = gomokuTable()

        assertEquals(listOf("Anna", "Ben"), host.gomoku.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.gomoku.lobby)
        assertEquals(1, host.gomoku.myPlayer)
        assertEquals(2, guest.gomoku.myPlayer)
        assertTrue(host.gomoku.isHost)
        assertFalse(guest.gomoku.isHost)
        assertTrue(host.gomoku.ready)
        assertTrue(guest.gomoku.ready)
        assertEquals(1, guest.gomoku.joined)
        assertEquals(SyncKind.GOMOKU, guest.session.kind)
        assertEquals(GomokuMatch(), guest.gomoku.match)
        // L'autre jeu n'est pas concerné par cette table.
        assertFalse(guest.dots.active)
        assertEquals(0, guest.dots.joined)
    }

    @Test
    fun lInviteRejointUnePipopipetteSansToucherAuGomoku() {
        val (host, guest) = dotsTable()

        assertTrue(guest.dots.ready)
        assertEquals(SyncKind.DOTS, guest.session.kind)
        assertEquals(1, guest.dots.joined)
        assertFalse(guest.gomoku.active)
        assertFalse(host.gomoku.active)
    }

    @Test
    fun avantLArriveeDeLInviteLOrganisateurAttend() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingDuo(SyncKind.GOMOKU, "Anna")
        host.gomoku.openLobby("Anna")

        assertTrue(host.gomoku.active)
        assertFalse(host.gomoku.ready)
        assertFalse(host.gomoku.myTurn)
        host.gomoku.play(0)
        assertEquals(Gomoku.EMPTY, host.gomoku.match.board)
    }

    // ---------- Gomoku ----------

    @Test
    fun auGomokuChacunJoueSonTourEtLesDeuxTelephonesVoientLaMemeGrille() {
        val (host, guest) = gomokuTable()

        assertTrue(host.gomoku.myTurn)
        assertFalse(guest.gomoku.myTurn)
        host.gomoku.play(112)
        assertEquals(1, host.gomoku.match.cells[112])
        assertEquals(host.gomoku.match, guest.gomoku.match)
        assertFalse(host.gomoku.myTurn)
        assertTrue(guest.gomoku.myTurn)

        guest.gomoku.play(113)
        assertEquals(2, host.gomoku.match.cells[113])
        assertEquals(host.gomoku.match, guest.gomoku.match)
        assertEquals(113, guest.gomoku.match.last)
        assertTrue(host.gomoku.myTurn)
    }

    @Test
    fun onNeJoueNiHorsDeSonTourNiSurUneCasePrise() {
        val (host, guest) = gomokuTable()

        // L'invité ne peut pas ouvrir la partie.
        guest.gomoku.play(0)
        assertEquals(Gomoku.EMPTY, host.gomoku.match.board)

        host.gomoku.play(112)
        // L'organisateur ne joue pas deux fois de suite ; l'invité ne prend pas une case occupée.
        host.gomoku.play(0)
        guest.gomoku.play(112)
        assertEquals(1, host.gomoku.match.cells.count { it != 0 })
        assertEquals(1, guest.gomoku.match.cells.count { it != 0 })
        assertTrue(guest.gomoku.myTurn)
    }

    @Test
    fun unCoupEnvoyeHorsDeSonTourParUnInviteTricheurEstIgnoreParLOrganisateur() {
        val (host, guest) = gomokuTable()

        guest.session.sendToHost(SyncMessage.Duo(SyncKind.GOMOKU, DuoMessage.Move(0)))

        assertEquals(Gomoku.EMPTY, host.gomoku.match.board)
        assertEquals(Gomoku.EMPTY, guest.gomoku.match.board)
    }

    /** Anna aligne cinq pierres sur la première ligne ; Ben joue sur la deuxième. */
    private fun gomokuRound(host: Phone, guest: Phone) {
        for (col in 0 until 4) {
            host.gomoku.play(col)
            guest.gomoku.play(Gomoku.SIZE + col)
        }
        host.gomoku.play(4)
    }

    @Test
    fun uneVictoireAuGomokuEstCompteeEtFigeLaGrille() {
        val (host, guest) = gomokuTable()
        gomokuRound(host, guest)

        assertEquals(1, host.gomoku.match.winsA)
        assertEquals(1, guest.gomoku.match.winsA)
        assertTrue(guest.gomoku.match.isOver)
        assertFalse(guest.gomoku.myTurn)
        val board = host.gomoku.match.board
        guest.gomoku.play(200)
        assertEquals(board, host.gomoku.match.board)
    }

    @Test
    fun lInvitePeutLancerLaMancheSuivanteEtLAutreJoueurCommence() {
        val (host, guest) = gomokuTable()
        gomokuRound(host, guest)

        guest.gomoku.newRound()

        assertEquals(Gomoku.EMPTY, host.gomoku.match.board)
        assertEquals(Gomoku.EMPTY, guest.gomoku.match.board)
        assertTrue(guest.gomoku.myTurn)
        assertFalse(host.gomoku.myTurn)
        assertEquals(1, guest.gomoku.match.winsA)
    }

    @Test
    fun personnePeutRelancerUneMancheEnCours() {
        val (host, guest) = gomokuTable()
        host.gomoku.play(0)

        host.gomoku.newRound()
        guest.gomoku.newRound()

        assertEquals(1, host.gomoku.match.cells.count { it != 0 })
    }

    // ---------- Pipopipette ----------

    @Test
    fun aLaPipopipetteFermerUnCarreFaitRejouerEtLesDeuxTelephonesSontSynchronises() {
        val (host, guest) = dotsTable()

        // Anna et Ben tracent les quatre côtés du carré 0 ; Ben ferme le dernier et rejoue.
        host.dots.play(Dots.horizontal(0, 0))
        guest.dots.play(Dots.horizontal(1, 0))
        host.dots.play(Dots.vertical(0, 0))
        assertTrue(guest.dots.myTurn)
        guest.dots.play(Dots.vertical(0, 1))

        assertEquals('2', host.dots.match.boxes[0])
        assertEquals(host.dots.match, guest.dots.match)
        assertTrue(guest.dots.myTurn)
        assertFalse(host.dots.myTurn)

        // Ben rejoue puis la main revient à Anna.
        guest.dots.play(Dots.horizontal(0, 1))
        assertTrue(host.dots.myTurn)
        assertFalse(guest.dots.myTurn)
    }

    @Test
    fun aLaPipopipetteUnTraitDejaTraceOuHorsDeSonTourEstIgnore() {
        val (host, guest) = dotsTable()

        guest.dots.play(0)
        assertEquals(Dots.EMPTY_EDGES, host.dots.match.edges)

        host.dots.play(0)
        guest.dots.play(0)
        host.dots.play(1)
        assertEquals(1, host.dots.match.edges.count { it != '0' })
        assertEquals(1, guest.dots.match.edges.count { it != '0' })
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendreUnAutreJoueur() {
        val (host, guest) = gomokuTable()
        host.gomoku.play(0)

        guest.gomoku.leave()

        assertFalse(guest.gomoku.active)
        assertEquals(listOf("Anna"), host.gomoku.lobby)
        assertFalse(host.gomoku.ready)
        assertEquals(DuoNotice.PLAYER_LEFT, host.gomoku.notice)
        assertEquals(Gomoku.EMPTY, host.gomoku.match.board)
        assertTrue(host.gomoku.active)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = dotsTable()

        host.dots.leave()

        assertFalse(host.dots.active)
        assertTrue(guest.dots.active)
        assertEquals(DuoNotice.HOST_LEFT, guest.dots.notice)
    }

    @Test
    fun quitterRemetLaTableAZero() {
        val (host, guest) = gomokuTable()
        host.gomoku.play(0)

        guest.gomoku.leave()

        assertEquals(-1, guest.gomoku.seat)
        assertEquals(emptyList<String>(), guest.gomoku.lobby)
        assertEquals(GomokuMatch(), guest.gomoku.match)
        assertEquals(DuoNotice.NONE, guest.gomoku.notice)
    }

    // ---------- Messages ----------

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val gomoku = GomokuMatch(board = Gomoku.EMPTY, starter = 1, winsA = 2, winsB = 3, draws = 1, last = 7).encode()
        val dots = DotsMatch(starter = 1, winsA = 1).encode()
        val messages = listOf(
            SyncMessage.Duo(SyncKind.GOMOKU, DuoMessage.Table(listOf("Anna", "Ben"), 1, gomoku, DuoNotice.NONE)),
            SyncMessage.Duo(SyncKind.DOTS, DuoMessage.Table(listOf("Anna"), 1, dots, DuoNotice.PLAYER_LEFT)),
            SyncMessage.Duo(SyncKind.GOMOKU, DuoMessage.Move(7)),
            SyncMessage.Duo(SyncKind.DOTS, DuoMessage.NewRound)
        )
        for (message in messages) {
            assertEquals(message.toString(), message, SyncCodec.decode(SyncCodec.encode(message)))
        }
    }

    @Test
    fun unMessageDunAutreJeuOuInconnuEstIgnore() {
        assertNull(duoMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(duoMessageFromJson(DuoMessage.Move(3).toJson()))
        // Un message « duo » qui prétend venir du Morpion n'est pas lu.
        val forged = SyncCodec.encode(SyncMessage.Duo(SyncKind.MORPION, DuoMessage.NewRound))
        assertNull(SyncCodec.decode(forged))
    }

    @Test
    fun uneSeanceIllisibleRecueParLInviteEstIgnoree() {
        val (host, guest) = gomokuTable()
        host.gomoku.play(0)
        val before = guest.gomoku.match

        host.session.sendTo(
            listOf("guest"),
            SyncMessage.Duo(SyncKind.GOMOKU, DuoMessage.Table(listOf("Anna", "Ben"), 1, "n'importe quoi", DuoNotice.NONE))
        )

        assertEquals(before, guest.gomoku.match)
    }
}
