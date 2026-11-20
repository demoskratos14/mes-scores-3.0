package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La salle multijoueur, testée sans téléphones : une connexion, puis plusieurs jeux à la suite.
 * L'organisateur est « Anna », l'invité « Ben ».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class SyncRoomTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val ticTacToe = TicTacToeOnline(session)
        val connect4 = Connect4Online(session)
    }

    private fun room(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingRoom("Anna")
        guest.session.startJoiningRoom()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    private fun openTicTacToe(host: Phone) {
        host.session.startRoomGame(SyncKind.MORPION)
        host.ticTacToe.openLobby("Anna")
        host.session.seatRoomPeers()
    }

    private fun openConnect4(host: Phone) {
        host.session.startRoomGame(SyncKind.CONNECT4)
        host.connect4.openLobby("Anna")
        host.session.seatRoomPeers()
    }

    @Test
    fun lesJoueursSeVoientDansLaSalleSansJeuEnCours() {
        val (host, guest) = room()

        assertEquals(listOf("Anna", "Ben"), host.session.roomMembers)
        assertEquals(listOf("Anna", "Ben"), guest.session.roomMembers)
        assertEquals(SyncKind.ROOM, host.session.kind)
        assertEquals(SyncKind.ROOM, guest.session.kind)
        assertTrue(host.session.isRoomHost)
    }

    @Test
    fun lOrganisateurChoisitUnJeuEtLInviteyEstEmmene() {
        val (host, guest) = room()

        openTicTacToe(host)

        assertTrue(guest.ticTacToe.active)
        assertEquals(1, guest.ticTacToe.joined)
        assertEquals(listOf("Anna", "Ben"), guest.ticTacToe.lobby)
        assertEquals(SyncKind.MORPION, guest.session.kind)
    }

    @Test
    fun quandLOrganisateurQuitteUnJeuToutLeMondeRevientDansLaSalleSansSeReconnecter() {
        val (host, guest) = room()
        openTicTacToe(host)

        host.ticTacToe.leave()

        assertTrue(host.session.isConnected)
        assertTrue(guest.session.isConnected)
        assertFalse(guest.ticTacToe.active)
        assertEquals(SyncKind.ROOM, guest.session.kind)
        assertEquals(SyncKind.ROOM, host.session.kind)
        assertEquals(1, guest.session.roomBackCount)

        // Un autre jeu, sur la même connexion.
        openConnect4(host)
        assertTrue(guest.connect4.active)
        assertEquals(1, guest.connect4.joined)
        assertEquals(SyncKind.CONNECT4, guest.session.kind)
    }

    @Test
    fun onPeutRejouerAuMemeJeuApresEtreRevenuDansLaSalle() {
        val (host, guest) = room()
        openTicTacToe(host)
        host.ticTacToe.leave()

        openTicTacToe(host)

        assertTrue(guest.ticTacToe.active)
        assertEquals(2, guest.ticTacToe.joined)
    }

    @Test
    fun unInviteQuiQuitteUnJeuResteDansLaSalle() {
        val (host, guest) = room()
        openTicTacToe(host)

        guest.ticTacToe.leave()

        assertTrue(guest.session.isConnected)
        assertTrue(host.session.isConnected)
        assertTrue(guest.session.roomActive)
        assertFalse(guest.ticTacToe.active)
        assertEquals(listOf("Anna"), host.ticTacToe.lobby)

        // Le jeu suivant le reprend.
        host.ticTacToe.leave()
        openConnect4(host)
        assertTrue(guest.connect4.active)
    }

    @Test
    fun fermerLaSalleCoupeToutesLesConnexions() {
        val (host, guest) = room()

        host.session.closeRoom()

        assertFalse(host.session.roomActive)
        assertFalse(guest.session.isConnected)
        assertFalse(guest.session.roomActive)
    }
}
