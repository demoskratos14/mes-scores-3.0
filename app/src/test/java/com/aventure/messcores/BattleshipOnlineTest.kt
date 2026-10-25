package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le touché coulé chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (place 0), l'invité « Ben » (place 1). Anna tire la première.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class BattleshipOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = BattleshipOnline(session, chooseFirst = { 0 })
    }

    private val annaFleet = listOf(
        listOf(0, 1, 2, 3, 4), listOf(10, 11, 12, 13), listOf(20, 21, 22), listOf(30, 31, 32), listOf(40, 41)
    )
    private val benFleet = listOf(
        listOf(5, 15, 25, 35, 45), listOf(6, 16, 26, 36), listOf(7, 17, 27), listOf(8, 18, 28), listOf(9, 19)
    )

    private fun lobby(settings: BattleshipSettings = BattleshipSettings()): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingBattleship("Anna")
        host.online.openLobby("Anna", settings)
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    private fun playing(settings: BattleshipSettings = BattleshipSettings()): Pair<Phone, Phone> {
        val (host, guest) = lobby(settings)
        host.online.startGame()
        host.online.submitFleet(annaFleet)
        guest.online.submitFleet(benFleet)
        return host to guest
    }

    @Test
    fun laSalleDAttenteReunitLesDeuxJoueurs() {
        val (host, guest) = lobby()
        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(0, host.online.seat)
        assertEquals(1, guest.online.seat)
        assertEquals(SyncKind.BATTLESHIP, guest.session.kind)
        assertEquals(1, guest.online.joined)
        assertEquals("Ben", host.online.opponentName)
    }

    @Test
    fun lesReglagesArriventChezLInvite() {
        val (_, guest) = lobby(BattleshipSettings(shootAgainOnHit = true))
        assertTrue(guest.online.settings.shootAgainOnHit)
    }

    @Test
    fun laPartieCommenceQuandLesDeuxFlottesSontPlacees() {
        val (host, guest) = lobby()
        host.online.startGame()
        assertEquals(BattleshipPhase.PLACEMENT, host.online.phase)
        assertEquals(BattleshipPhase.PLACEMENT, guest.online.phase)
        host.online.submitFleet(annaFleet)
        assertEquals(BattleshipPhase.PLACEMENT, host.online.phase)
        assertTrue(guest.online.opponentReady)
        guest.online.submitFleet(benFleet)
        assertEquals(BattleshipPhase.PLAYING, host.online.phase)
        assertEquals(BattleshipPhase.PLAYING, guest.online.phase)
        assertTrue(host.online.myTurn)
        assertFalse(guest.online.myTurn)
    }

    @Test
    fun uneFlotteInvalideEstRefusee() {
        val (host, _) = lobby()
        host.online.startGame()
        host.online.submitFleet(annaFleet.dropLast(1))
        assertNull(host.online.myFleet)
    }

    @Test
    fun lAdversaireRepondALEauEtLaMainPasse() {
        val (host, guest) = playing()
        host.online.fire(99)
        assertEquals(ShotOutcome.MISS, host.online.myShots.single().outcome)
        assertEquals(99, guest.online.shotsAtMe.single().cell)
        assertTrue(guest.online.myTurn)
        assertFalse(host.online.myTurn)
        assertNull(host.online.pendingShot)
    }

    @Test
    fun onNeTirePasQuandCEstLeTourDeLAutre() {
        val (host, guest) = playing()
        guest.online.fire(99)
        assertTrue(guest.online.myShots.isEmpty())
        assertTrue(host.online.shotsAtMe.isEmpty())
        host.online.fire(99)
        // Case déjà visée : refusé.
        guest.online.fire(98)
        host.online.fire(99)
        assertEquals(1, host.online.myShots.size)
    }

    @Test
    fun unTouchePuisUnCouleSontAnnonces() {
        val (host, guest) = playing()
        // Le torpilleur de Ben occupe les cases 9 et 19.
        host.online.fire(9)
        assertEquals(ShotOutcome.HIT, host.online.myShots.last().outcome)
        guest.online.fire(99)
        host.online.fire(19)
        val sunk = host.online.myShots.last()
        assertEquals(ShotOutcome.SUNK, sunk.outcome)
        assertEquals(listOf(9, 19), sunk.sunk)
    }

    @Test
    fun avecLeTirSupplementaireUnTouchePermetDeRejouer() {
        val (host, guest) = playing(BattleshipSettings(shootAgainOnHit = true))
        host.online.fire(5)
        assertTrue(host.online.myTurn)
        host.online.fire(99)
        assertTrue(guest.online.myTurn)
    }

    @Test
    fun laPartieSeTermineEtLesFlottesSontDevoilees() {
        val (host, guest) = playing(BattleshipSettings(shootAgainOnHit = true))
        for (cell in benFleet.flatten()) host.online.fire(cell)
        assertEquals(BattleshipPhase.FINISHED, host.online.phase)
        assertEquals(BattleshipPhase.FINISHED, guest.online.phase)
        assertEquals(0, host.online.winner)
        assertEquals(0, guest.online.winner)
        assertEquals(benFleet, host.online.opponentFleet)
        assertEquals(annaFleet, guest.online.opponentFleet)
        assertEquals(true, host.online.opponentHonest)
        assertEquals(true, guest.online.opponentHonest)
    }

    @Test
    fun unAdversaireQuiMentEstDemasque() {
        val (host, _) = playing()
        // Il a répondu « à l'eau » en 99, mais dévoile ensuite une flotte qui y a un bateau.
        host.online.fire(99)
        val liar = annaFleet.dropLast(1) + listOf(listOf(98, 99))
        assertFalse(Battleship.isConsistent(liar, host.online.myShots))
    }

    @Test
    fun unJoueurQuiPartEnPleinePartieInterrompt() {
        val (host, guest) = playing()
        guest.session.disconnect()
        assertEquals(BattleshipNotice.PLAYER_LEFT, host.online.notice)
        assertFalse(host.online.started)
        assertEquals(listOf("Anna"), host.online.lobby)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = playing()
        host.online.leave()
        assertEquals(BattleshipNotice.HOST_LEFT, guest.online.notice)
        assertFalse(guest.online.started)
    }

    @Test
    fun rejouerRamenePartoutALaSalleDAttente() {
        val (host, guest) = playing(BattleshipSettings(shootAgainOnHit = true))
        for (cell in benFleet.flatten()) host.online.fire(cell)
        host.online.replay()
        assertFalse(host.online.started)
        assertFalse(guest.online.started)
        assertNull(guest.online.myFleet)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        host.online.startGame()
        assertEquals(BattleshipPhase.PLACEMENT, guest.online.phase)
    }
}
