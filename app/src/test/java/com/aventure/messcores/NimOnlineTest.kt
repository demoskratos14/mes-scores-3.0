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
 * Le jeu des allumettes chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (premier joueur, place 0), l'invité « Ben » (deuxième joueur, place 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class NimOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = NimOnline(session)
    }

    /** Anna organise, Ben rejoint : la partie est ouverte sur les deux téléphones. */
    private fun table(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingNim("Anna")
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
        assertEquals(1, host.online.myNumber)
        assertEquals(2, guest.online.myNumber)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertTrue(guest.online.active)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.NIM, guest.session.kind)
        assertEquals(NimMatch(), guest.online.match)
    }

    @Test
    fun avantLArriveeDeLInviteLOrganisateurAttend() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingNim("Anna")
        host.online.openLobby("Anna")

        assertTrue(host.online.active)
        assertFalse(host.online.ready)
        assertFalse(host.online.myTurn)
        host.online.take(2)
        assertEquals(Nim.START, host.online.match.remaining)
    }

    // ---------- Coups ----------

    @Test
    fun chacunJoueSonTourEtLesDeuxTelephonesVoientLeMemeTas() {
        val (host, guest) = table()

        assertTrue(host.online.myTurn)
        assertFalse(guest.online.myTurn)
        host.online.take(2)
        assertEquals(19, host.online.match.remaining)
        assertEquals(19, guest.online.match.remaining)
        assertFalse(host.online.myTurn)
        assertTrue(guest.online.myTurn)

        guest.online.take(3)
        assertEquals(16, host.online.match.remaining)
        assertEquals(16, guest.online.match.remaining)
        assertTrue(host.online.myTurn)
    }

    @Test
    fun onNeJoueNiHorsDeSonTourNiAvecUneQuantiteInterdite() {
        val (host, guest) = table()

        // L'invité ne peut pas ouvrir la partie.
        guest.online.take(1)
        assertEquals(Nim.START, host.online.match.remaining)

        // Ni 0 ni 4 allumettes.
        host.online.take(0)
        host.online.take(4)
        assertEquals(Nim.START, host.online.match.remaining)

        host.online.take(1)
        // L'organisateur ne peut pas jouer deux fois de suite.
        host.online.take(1)
        assertEquals(20, host.online.match.remaining)
        assertEquals(20, guest.online.match.remaining)
        assertTrue(guest.online.myTurn)
    }

    @Test
    fun unCoupEnvoyeHorsDeSonTourParUnInviteTricheurEstIgnoreParLOrganisateur() {
        val (host, guest) = table()

        // Le téléphone de l'invité n'a pas la main, mais un message forgé arrive quand même.
        guest.session.sendToHost(SyncMessage.Nim(NimMessage.Take(3)))
        assertEquals(Nim.START, host.online.match.remaining)
        assertEquals(Nim.START, guest.online.match.remaining)

        // Une quantité absurde à son tour est refusée aussi.
        host.online.take(1)
        guest.session.sendToHost(SyncMessage.Nim(NimMessage.Take(9)))
        assertEquals(20, host.online.match.remaining)
        assertTrue(guest.online.myTurn)
    }

    // ---------- Manches et score ----------

    /** Anna prend 3, 3, 3, puis 2 ; Ben prend 3, 3, puis 1 : il prend la dernière allumette et perd. */
    private fun playRound(host: Phone, guest: Phone) {
        host.online.take(3)
        guest.online.take(3)
        host.online.take(3)
        guest.online.take(3)
        host.online.take(3)
        guest.online.take(3)
        host.online.take(2)
        guest.online.take(1)
    }

    @Test
    fun laDerniereAllumetteFaitPerdreEtLeVainqueurMarque() {
        val (host, guest) = table()
        playRound(host, guest)

        for (phone in listOf(host, guest)) {
            assertEquals(0, phone.online.match.remaining)
            assertTrue(phone.online.match.isOver)
            assertEquals(2, phone.online.match.loser)
            assertEquals(1, phone.online.match.winner)
            assertEquals(1, phone.online.match.wins1)
            assertEquals(0, phone.online.match.wins2)
            assertFalse(phone.online.myTurn)
        }
        host.online.take(1)
        guest.online.take(1)
        assertEquals(0, host.online.match.remaining)
    }

    @Test
    fun lInvitePeutLancerLaMancheSuivanteEtLAutreJoueurCommence() {
        val (host, guest) = table()
        playRound(host, guest)

        guest.online.newRound()

        assertEquals(Nim.START, host.online.match.remaining)
        assertEquals(Nim.START, guest.online.match.remaining)
        assertEquals(1, host.online.match.starter)
        // Ben commence la deuxième manche ; le score est gardé.
        assertTrue(guest.online.myTurn)
        assertFalse(host.online.myTurn)
        assertEquals(1, guest.online.match.wins1)
    }

    @Test
    fun lOrganisateurPeutLancerLaMancheSuivanteMaisPasEnPleinePartie() {
        val (host, guest) = table()
        host.online.take(1)
        // Manche en cours : personne ne peut la relancer.
        host.online.newRound()
        guest.online.newRound()
        assertEquals(20, host.online.match.remaining)

        // La manche se joue jusqu'au bout : Anna se retrouve à prendre la dernière allumette.
        guest.online.take(3)
        host.online.take(3)
        guest.online.take(3)
        host.online.take(3)
        guest.online.take(3)
        host.online.take(3)
        guest.online.take(1)
        assertFalse(host.online.match.isOver)
        host.online.take(1)
        assertTrue(host.online.match.isOver)
        assertEquals(2, host.online.match.winner)

        host.online.newRound()
        assertEquals(Nim.START, guest.online.match.remaining)
        assertEquals(1, guest.online.match.wins2)
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendreUnAutreJoueur() {
        val (host, guest) = table()
        host.online.take(1)

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(host.online.ready)
        assertEquals(NimNotice.PLAYER_LEFT, host.online.notice)
        assertEquals(Nim.START, host.online.match.remaining)
        assertTrue(host.online.active)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = table()

        host.online.leave()

        assertFalse(host.online.active)
        assertTrue(guest.online.active)
        assertEquals(NimNotice.HOST_LEFT, guest.online.notice)
    }

    @Test
    fun quitterRemetLaTableAZero() {
        val (host, guest) = table()
        host.online.take(1)

        guest.online.leave()

        assertEquals(-1, guest.online.seat)
        assertEquals(emptyList<String>(), guest.online.lobby)
        assertEquals(NimMatch(), guest.online.match)
        assertEquals(NimNotice.NONE, guest.online.notice)
    }

    // ---------- Messages ----------

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val match = NimMatch(remaining = 7, turn = 2, starter = 1, wins1 = 2, wins2 = 3)
        val over = NimMatch(remaining = 0, turn = 1, loser = 1, wins2 = 1)
        val messages = listOf(
            NimMessage.Table(listOf("Anna", "Ben"), 1, match, NimNotice.NONE),
            NimMessage.Table(listOf("Anna", "Ben"), 1, over, NimNotice.NONE),
            NimMessage.Table(listOf("Anna"), 1, NimMatch(), NimNotice.PLAYER_LEFT),
            NimMessage.Take(3),
            NimMessage.NewRound
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Nim(message)))
            assertEquals(message.toString(), SyncMessage.Nim(message), decoded)
        }
    }

    @Test
    fun unTasUnJoueurOuUnScoreAbsurdesSontRamenesAUneValeurPermise() {
        val json = NimMessage.Table(listOf("Anna", "Ben"), 1, NimMatch(), NimNotice.NONE).toJson()
            .put("left", 500).put("turn", 9).put("starter", 7).put("loser", 2).put("w1", -4)

        val decoded = nimMessageFromJson(json) as NimMessage.Table

        assertEquals(Nim.START, decoded.match.remaining)
        assertEquals(2, decoded.match.turn)
        assertEquals(1, decoded.match.starter)
        // Un perdant n'existe que si le tas est vide.
        assertEquals(0, decoded.match.loser)
        assertEquals(0, decoded.match.wins1)
    }

    @Test
    fun unMessageInconnuEstIgnore() {
        assertNull(nimMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(nimMessageFromJson(NimMessage.Take(2).toJson()))
    }
}
