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
 * Le partage entre deux téléphones, testé sans téléphones : deux parties, deux sessions, et un faux lien radio
 * qui relie les deux de façon instantanée.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class SyncSessionTest {

    /** Un des deux bouts du faux lien : ce qu'envoie l'un arrive tout de suite à l'autre. */
    private class FakeTransport(private val selfId: String) : SyncTransport {
        override var listener: TransportListener? = null
        lateinit var peer: FakeTransport
        private var hostName: String? = null
        private var discovering = false
        private var accepted = false
        private var linked = false
        private var connected = false

        override fun startHosting(deviceName: String) {
            hostName = deviceName
            if (peer.discovering) peer.listener?.onEndpointFound(selfId, deviceName)
        }

        override fun startDiscovery() {
            discovering = true
            peer.hostName?.let { listener?.onEndpointFound(peer.selfId, it) }
        }

        override fun requestConnection(deviceName: String, endpointId: String) {
            linked = true
            peer.linked = true
            listener?.onConnectionInitiated(peer.selfId, peer.hostName ?: "?", DIGITS)
            peer.listener?.onConnectionInitiated(selfId, deviceName, DIGITS)
        }

        override fun acceptConnection(endpointId: String) {
            accepted = true
            if (peer.accepted && !connected) {
                connected = true
                peer.connected = true
                // L'organisateur est prévenu en premier : il envoie la partie dès qu'il est connecté.
                val host = if (hostName != null) this else peer
                val guest = if (host === this) peer else this
                host.listener?.onConnected(guest.selfId)
                guest.listener?.onConnected(host.selfId)
            }
        }

        override fun rejectConnection(endpointId: String) {
            linked = false
            peer.linked = false
            listener?.onConnectionFailed(peer.selfId, rejected = true)
            peer.listener?.onConnectionFailed(selfId, rejected = true)
        }

        override fun send(endpointIds: Collection<String>, bytes: ByteArray) {
            if (connected || peer.connected) peer.listener?.onMessage(selfId, bytes)
        }

        override fun stopAll() {
            hostName = null
            discovering = false
            accepted = false
            if (connected) {
                connected = false
                peer.connected = false
                peer.accepted = false
                peer.listener?.onDisconnected(selfId)
            }
        }

        private companion object {
            const val DIGITS = "4821"
        }
    }

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
    }

    private val tableRules = GameRules(id = "t", name = "T")
    private val counterRules = GameRules(id = "c", name = "C", scoreMode = ScoreMode.COUNTER)

    private fun phones(rules: GameRules): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.game.initGame(listOf("Anna", "Ben"), rules)
        return host to guest
    }

    /** L'organisateur annonce, l'invité cherche et se connecte, l'organisateur accepte. */
    private fun connect(host: Phone, guest: Phone) {
        assertTrue(host.session.startHosting("Téléphone d'Anna"))
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Téléphone de Ben")
        host.session.accept()
    }

    // ---------- Connexion ----------

    @Test
    fun lInviteRecoitLaPartieDesLaConnexion() {
        val (host, guest) = phones(tableRules)
        host.game.setCell(0, 0, 15, false)

        connect(host, guest)

        assertTrue(host.session.isConnected)
        assertTrue(guest.session.isGuest)
        assertEquals(1, guest.session.receivedStates)
        assertEquals(listOf("Anna", "Ben"), guest.game.players)
        assertEquals(15, guest.game.scores[0][0].baseValue)
        assertEquals("Téléphone d'Anna", guest.session.connected.single().name)
        assertEquals("Téléphone de Ben", host.session.connected.single().name)
    }

    @Test
    fun lesDeuxTelephonesVoientLeMemeCodeAVerifier() {
        val (host, guest) = phones(tableRules)
        host.session.startHosting("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")

        assertEquals("4821", host.session.pending!!.digits)
        assertEquals("Ben", host.session.pending!!.name)
        assertEquals("4821", guest.session.pending!!.digits)
        assertFalse(host.session.isConnected)
    }

    @Test
    fun uneConnexionRefuseeEstSignaleeALInvite() {
        val (host, guest) = phones(tableRules)
        host.session.startHosting("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")

        host.session.refuse()

        assertEquals(SyncProblem.REFUSED, guest.session.problem)
        assertFalse(guest.session.isConnected)
        assertNull(host.session.pending)
        assertNull(guest.session.pending)
    }

    @Test
    fun onNePeutOrganiserQueUneVraiePartieAuTableauAuCompteurOuParManches() {
        val noGame = Phone("host")
        assertFalse(noGame.session.startHosting("Anna"))
        assertEquals(SyncRole.NONE, noGame.session.role)

        for (mode in listOf(ScoreMode.POKER, ScoreMode.RACKET)) {
            val (host, _) = phones(GameRules(id = "x", name = "X", scoreMode = mode))
            assertFalse(host.session.canShareCurrentGame())
            assertFalse(host.session.startHosting("Anna"))
        }
        for (mode in listOf(ScoreMode.TABLE, ScoreMode.COUNTER, ScoreMode.VARIABLE_TEAMS)) {
            val (host, _) = phones(GameRules(id = "x", name = "X", scoreMode = mode))
            assertTrue(host.session.canShareCurrentGame())
        }
    }

    // ---------- Saisies de l'invité ----------

    @Test
    fun uneSaisieDeLInviteVaChezLOrganisateurPuisRevientChezLui() {
        val (host, guest) = phones(tableRules)
        connect(host, guest)

        guest.game.setCell(0, 1, 7, false)

        // L'organisateur applique la saisie ; l'invité n'applique rien lui-même.
        assertEquals(7, host.game.scores[0][1].baseValue)
        assertNull(guest.game.scores[0][1].baseValue)

        host.session.broadcastState()
        assertEquals(7, guest.game.scores[0][1].baseValue)
        assertEquals(2, guest.session.receivedStates)
        assertEquals(host.game.totals, guest.game.totals)
    }

    @Test
    fun laSaisieDeLOrganisateurArriveChezLInvite() {
        val (host, guest) = phones(tableRules)
        connect(host, guest)

        host.game.setCell(1, 0, 30, false)
        host.session.broadcastState()

        assertEquals(30, guest.game.scores[1][0].baseValue)
    }

    @Test
    fun lesCompteursSePartagentAussi() {
        val (host, guest) = phones(counterRules)
        connect(host, guest)

        guest.game.incrementCounter(0, 3)
        host.game.incrementCounter(1, 2)
        host.session.broadcastState()

        assertEquals(listOf(3, 2), host.game.counters)
        assertEquals(listOf(3, 2), guest.game.counters)
    }

    @Test
    fun lesMancheDeTarotSePartagentAussi() {
        val teamsRules = GameRules(id = "v", name = "V", scoreMode = ScoreMode.VARIABLE_TEAMS)
        val (host, guest) = phones(teamsRules)
        connect(host, guest)

        guest.game.appendTeamRound(TeamRound("Équipe A", setOf(0), 25, deltas = listOf(25, -25)))
        host.session.broadcastState()

        assertEquals(1, host.game.teamRounds.size)
        assertEquals(1, guest.game.teamRounds.size)
        assertEquals(host.game.totals, guest.game.totals)
    }

    @Test
    fun annulerEtRetablirViennentDeLOrganisateur() {
        val (host, guest) = phones(tableRules)
        connect(host, guest)
        assertFalse(guest.game.canUndo)

        guest.game.setCell(0, 0, 9, false)
        host.session.broadcastState()
        assertTrue(guest.game.canUndo)
        assertFalse(guest.game.canRedo)

        // L'invité annule : l'annulation se fait chez l'organisateur.
        guest.game.undo()
        assertNull(host.game.scores[0][0].baseValue)
        host.session.broadcastState()
        assertNull(guest.game.scores[0][0].baseValue)
        assertFalse(guest.game.canUndo)
        assertTrue(guest.game.canRedo)

        guest.game.redo()
        host.session.broadcastState()
        assertEquals(9, guest.game.scores[0][0].baseValue)
    }

    // ---------- Fin de la connexion ----------

    @Test
    fun siLOrganisateurSeDeconnecteLInviteGardeLaPartieEtJoueSeul() {
        val (host, guest) = phones(tableRules)
        connect(host, guest)
        host.game.setCell(0, 0, 4, false)
        host.session.broadcastState()

        host.session.disconnect()

        assertEquals(SyncProblem.HOST_LOST, guest.session.problem)
        assertEquals(SyncRole.NONE, guest.session.role)
        assertFalse(guest.session.isGuest)
        // La partie est toujours là, et les saisies s'appliquent de nouveau directement.
        assertEquals(4, guest.game.scores[0][0].baseValue)
        guest.game.setCell(0, 1, 9, false)
        assertEquals(9, guest.game.scores[0][1].baseValue)
        assertNull(host.game.scores[0][1].baseValue)
    }

    @Test
    fun sansInviteConnecteRienNEstEnvoye() {
        val (host, _) = phones(tableRules)
        host.session.startHosting("Anna")
        host.game.setCell(0, 0, 1, false)
        // Pas d'invité : l'envoi ne fait rien et ne plante pas.
        host.session.broadcastState()
        assertTrue(host.session.connected.isEmpty())
    }

    @Test
    fun unInviteDeconnecteNEnvoiePasSesSaisies() {
        val (host, guest) = phones(tableRules)
        guest.game.initGame(listOf("Anna", "Ben"), tableRules)
        guest.session.startJoining()
        guest.game.setCell(0, 0, 3, false)
        assertEquals(3, guest.game.scores[0][0].baseValue)
        assertNull(host.game.scores[0][0].baseValue)
    }
}
