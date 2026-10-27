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
 * Les dames chacun sur son téléphone, testées sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (place 0, blancs à la première partie), l'invité « Ben » (place 1, noirs).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DraughtsOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = DraughtsOnline(session)
    }

    /** Anna organise, Ben rejoint : la partie est ouverte sur les deux téléphones. */
    private fun table(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingDraughts("Anna")
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
        assertEquals(Draughts.WHITE, host.online.myColor)
        assertEquals(Draughts.BLACK, guest.online.myColor)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.DRAUGHTS, guest.session.kind)
        assertEquals(DraughtsMatch(), guest.online.match)
    }

    @Test
    fun avantLArriveeDeLInviteLOrganisateurAttend() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingDraughts("Anna")
        host.online.openLobby("Anna")

        assertTrue(host.online.active)
        assertFalse(host.online.ready)
        assertFalse(host.online.myTurn)
        host.online.play(listOf(65, 54))
        assertEquals(Draughts.START, host.online.match.board)
    }

    // ---------- Coups ----------

    @Test
    fun chacunJoueSonTourEtLesDeuxTelephonesVoientLeMemeDamier() {
        val (host, guest) = table()

        assertTrue(host.online.myTurn)
        assertFalse(guest.online.myTurn)
        host.online.play(listOf(65, 54))
        assertEquals(Draughts.MAN_WHITE, host.online.match.cells[54])
        assertEquals(host.online.match, guest.online.match)
        assertFalse(host.online.myTurn)
        assertTrue(guest.online.myTurn)

        guest.online.play(listOf(34, 45))
        assertEquals(Draughts.MAN_BLACK, host.online.match.cells[45])
        assertEquals(host.online.match, guest.online.match)
        assertEquals(listOf(34, 45), host.online.match.last)
        assertTrue(host.online.myTurn)
    }

    @Test
    fun onNeJoueNiHorsDeSonTourNiContreLesRegles() {
        val (host, guest) = table()

        // L'invité ne peut pas ouvrir la partie.
        guest.online.play(listOf(34, 45))
        assertEquals(Draughts.START, host.online.match.board)

        // Coup contraire aux règles : le pion ne recule pas et ne saute pas.
        host.online.play(listOf(65, 43))
        host.online.play(listOf(65, 54, 43))
        assertEquals(Draughts.START, host.online.match.board)
        assertTrue(host.online.myTurn)

        host.online.play(listOf(65, 54))
        // L'organisateur ne joue pas deux fois de suite.
        host.online.play(listOf(54, 43))
        assertEquals(Draughts.BLACK, host.online.match.turn)
        assertEquals(host.online.match, guest.online.match)
    }

    @Test
    fun unCoupForgeParUnInviteTricheurEstIgnoreParLOrganisateur() {
        val (host, guest) = table()

        // Hors de son tour, puis un coup impossible quand c'est son tour.
        guest.session.sendToHost(SyncMessage.Dr(DrMessage.Move(listOf(34, 45))))
        assertEquals(Draughts.START, host.online.match.board)

        host.online.play(listOf(65, 54))
        guest.session.sendToHost(SyncMessage.Dr(DrMessage.Move(listOf(34, 99))))
        guest.session.sendToHost(SyncMessage.Dr(DrMessage.Move(listOf(65, 54))))
        assertEquals(Draughts.BLACK, host.online.match.turn)
        assertEquals(Draughts.MAN_WHITE, host.online.match.cells[54])
        assertTrue(guest.online.myTurn)
    }

    // ---------- Parties et score ----------

    @Test
    fun abandonnerTermineLaPartieEtCompteLaVictoireDeLAdversaire() {
        val (host, guest) = table()

        guest.online.resign()

        assertTrue(host.online.match.isOver)
        assertEquals(Draughts.WHITE, host.online.match.result)
        assertEquals(1, host.online.match.winsA)
        assertEquals(host.online.match, guest.online.match)
        assertFalse(host.online.myTurn)
        assertFalse(guest.online.myTurn)
        // La partie finie ne se joue plus et ne s'abandonne plus.
        host.online.play(listOf(65, 54))
        guest.online.resign()
        assertEquals(1, host.online.match.winsA)
        assertEquals(0, host.online.match.winsB)
    }

    @Test
    fun lOrganisateurPeutAbandonnerAussi() {
        val (host, guest) = table()

        host.online.resign()

        assertEquals(Draughts.BLACK, guest.online.match.result)
        assertEquals(1, guest.online.match.winsB)
        assertEquals(1, guest.online.match.winnerSeat)
    }

    @Test
    fun lInvitePeutLancerLaPartieSuivanteEtLesCouleursSEchangent() {
        val (host, guest) = table()
        host.online.resign()

        guest.online.newRound()

        assertEquals(Draughts.START, host.online.match.board)
        assertEquals(1, host.online.match.starter)
        assertEquals(Draughts.BLACK, host.online.myColor)
        assertEquals(Draughts.WHITE, guest.online.myColor)
        // Ben a maintenant les blancs et ouvre ; le score est gardé.
        assertTrue(guest.online.myTurn)
        assertFalse(host.online.myTurn)
        assertEquals(1, guest.online.match.winsB)
        guest.online.play(listOf(65, 54))
        assertEquals(Draughts.MAN_WHITE, host.online.match.cells[54])
        assertTrue(host.online.myTurn)
    }

    @Test
    fun personneNePeutRelancerUnePartieEnCours() {
        val (host, guest) = table()
        host.online.play(listOf(65, 54))

        host.online.newRound()
        guest.online.newRound()

        assertEquals(Draughts.MAN_WHITE, host.online.match.cells[54])
        assertEquals(0, host.online.match.starter)
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendreUnAutreJoueur() {
        val (host, guest) = table()
        host.online.play(listOf(65, 54))

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(host.online.ready)
        assertEquals(DraughtsNotice.PLAYER_LEFT, host.online.notice)
        assertEquals(DraughtsMatch(), host.online.match)
        assertTrue(host.online.active)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = table()

        host.online.leave()

        assertFalse(host.online.active)
        assertTrue(guest.online.active)
        assertEquals(DraughtsNotice.HOST_LEFT, guest.online.notice)
    }

    @Test
    fun quitterRemetLaTableAZero() {
        val (host, guest) = table()
        host.online.play(listOf(65, 54))

        guest.online.leave()

        assertEquals(-1, guest.online.seat)
        assertEquals(0, guest.online.myColor)
        assertEquals(emptyList<String>(), guest.online.lobby)
        assertEquals(DraughtsMatch(), guest.online.match)
        assertEquals(DraughtsNotice.NONE, guest.online.notice)
    }

    // ---------- Messages ----------

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val match = DraughtsMatch(
            board = Draughts.START, starter = 1, turn = Draughts.BLACK, quiet = 12, result = Draughts.ONGOING,
            last = listOf(65, 54), winsA = 2, winsB = 3, draws = 1
        )
        val messages = listOf(
            DrMessage.Table(listOf("Anna", "Ben"), 1, match, DraughtsNotice.NONE),
            DrMessage.Table(listOf("Anna"), 1, DraughtsMatch(), DraughtsNotice.PLAYER_LEFT),
            DrMessage.Move(listOf(94, 61, 43)),
            DrMessage.Resign,
            DrMessage.NewRound
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Dr(message)))
            assertEquals(message.toString(), SyncMessage.Dr(message), decoded)
        }
    }

    @Test
    fun unDamierIllisibleEtDesValeursAbsurdesSontRamenesAUneValeurPermise() {
        val json = DrMessage.Table(listOf("Anna", "Ben"), 1, DraughtsMatch(), DraughtsNotice.NONE).toJson()
        json.getJSONObject("match").put("board", "zzz").put("starter", 7).put("turn", 9).put("quiet", 500)
            .put("result", -2).put("wa", -4)

        val decoded = drMessageFromJson(json) as DrMessage.Table

        assertEquals(Draughts.START, decoded.match.board)
        assertEquals(1, decoded.match.starter)
        assertEquals(Draughts.BLACK, decoded.match.turn)
        assertEquals(Draughts.QUIET_LIMIT, decoded.match.quiet)
        assertEquals(Draughts.ONGOING, decoded.match.result)
        assertEquals(0, decoded.match.winsA)
    }

    @Test
    fun unMessageInconnuEstIgnore() {
        assertNull(drMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(drMessageFromJson(DrMessage.Move(listOf(65, 54)).toJson()))
    }
}
