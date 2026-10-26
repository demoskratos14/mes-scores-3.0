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
 * Pierre-feuille-ciseaux chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien
 * radio. L'organisateur est « Anna » (joueur 1, place 0), l'invité « Ben » (joueur 2, place 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class RockPaperScissorsOnlineTest {

    private val rock = RockPaperScissors.ROCK
    private val paper = RockPaperScissors.PAPER
    private val scissors = RockPaperScissors.SCISSORS
    private val hidden = RockPaperScissors.HIDDEN

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = RockPaperScissorsOnline(session)
    }

    /** Anna organise (avec [target] manches à gagner), Ben rejoint : la partie est ouverte sur les deux téléphones. */
    private fun table(target: Int = RockPaperScissors.DEFAULT_TARGET): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.online.chooseTarget(target)
        host.session.startHostingRockPaperScissors("Anna")
        host.online.openLobby("Anna")
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Ben")
        host.session.accept()
        return host to guest
    }

    // ---------- Arrivée à la table ----------

    @Test
    fun lInviteRejointLaTableEtLaPartieCommenceAussitot() {
        val (host, guest) = table(target = 2)

        assertEquals(listOf("Anna", "Ben"), host.online.lobby)
        assertEquals(listOf("Anna", "Ben"), guest.online.lobby)
        assertEquals(1, host.online.me)
        assertEquals(2, guest.online.me)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.PFC, guest.session.kind)
        // Le nombre de manches choisi par l'organisateur arrive chez l'invité.
        assertEquals(RpsMatch(target = 2), guest.online.match)
    }

    @Test
    fun avantLArriveeDeLInviteOnNeChoisitPas() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingRockPaperScissors("Anna")
        host.online.openLobby("Anna")

        assertTrue(host.online.active)
        assertFalse(host.online.ready)
        assertFalse(host.online.canPick)
        host.online.pick(rock)
        assertEquals(0, host.online.match.pickA)
    }

    @Test
    fun leNombreDeManchesProposeEstSeulAcceptable() {
        val phone = Phone("a")
        phone.online.chooseTarget(5)
        assertEquals(5, phone.online.target)
        phone.online.chooseTarget(4)
        assertEquals(5, phone.online.target)
    }

    // ---------- Choix secrets ----------

    @Test
    fun leChoixDeLOrganisateurNArrivePasEnClairChezLInvite() {
        val (host, guest) = table()

        host.online.pick(rock)

        assertEquals(rock, host.online.match.pickA)
        // L'invité sait seulement que l'organisateur a choisi.
        assertEquals(hidden, guest.online.match.pickA)
        assertEquals(hidden, guest.online.view.pickA)
        assertFalse(guest.online.match.revealed)
        assertTrue(guest.online.match.hasPicked(1))
        assertTrue(guest.online.canPick)
        assertFalse(host.online.canPick)
    }

    @Test
    fun leChoixDeLInviteResteCacheSurLeTelephoneDeLOrganisateurJusquALaRevelation() {
        val (host, guest) = table()

        guest.online.pick(scissors)

        // L'organisateur le détient, mais l'affichage ne le montre pas.
        assertEquals(scissors, host.online.match.pickB)
        assertEquals(hidden, host.online.view.pickB)
        assertFalse(host.online.view.revealed)
        assertEquals(scissors, guest.online.view.pickB)
        assertFalse(guest.online.canPick)
        assertTrue(host.online.canPick)
    }

    @Test
    fun lesDeuxChoixSontReveleesEnMemeTempsSurLesDeuxTelephones() {
        val (host, guest) = table()

        host.online.pick(rock)
        guest.online.pick(scissors)

        for (phone in listOf(host, guest)) {
            val view = phone.online.view
            assertTrue(view.revealed)
            assertEquals(rock, view.pickA)
            assertEquals(scissors, view.pickB)
            assertEquals(1, view.roundWinner)
            assertEquals(1, view.winsA)
            assertEquals(0, view.winsB)
        }
    }

    @Test
    fun l_invite_peut_choisir_le_premier() {
        val (host, guest) = table()

        guest.online.pick(paper)
        assertFalse(host.online.view.revealed)
        host.online.pick(rock)

        assertTrue(guest.online.view.revealed)
        assertEquals(2, guest.online.view.roundWinner)
        assertEquals(1, host.online.view.winsB)
    }

    @Test
    fun onNeChoisitPasDeuxFoisDansLaMemeManche() {
        val (host, guest) = table()

        host.online.pick(rock)
        host.online.pick(paper)
        assertEquals(rock, host.online.match.pickA)

        guest.online.pick(scissors)
        // Un message forgé de l'invité, après la révélation, est ignoré par l'organisateur.
        guest.session.sendToHost(SyncMessage.Rps(RpsMessage.Pick(rock)))
        assertEquals(scissors, host.online.match.pickB)
        assertEquals(1, host.online.match.winsA)
        assertEquals(0, host.online.match.winsB)
    }

    @Test
    fun unChoixInvalideEstIgnore() {
        val (host, guest) = table()

        guest.session.sendToHost(SyncMessage.Rps(RpsMessage.Pick(0)))
        guest.session.sendToHost(SyncMessage.Rps(RpsMessage.Pick(RockPaperScissors.HIDDEN)))
        guest.online.pick(7)

        assertEquals(0, host.online.match.pickB)
        assertEquals(0, guest.online.match.pickB)
    }

    // ---------- Manches et parties ----------

    private fun playRound(host: Phone, guest: Phone, hostPick: Int, guestPick: Int) {
        host.online.pick(hostPick)
        guest.online.pick(guestPick)
    }

    @Test
    fun lInvitePeutLancerLaMancheSuivanteEtLeScoreEstGarde() {
        val (host, guest) = table()
        playRound(host, guest, rock, scissors)

        guest.online.next()

        for (phone in listOf(host, guest)) {
            assertEquals(0, phone.online.match.pickA)
            assertEquals(0, phone.online.match.pickB)
            assertEquals(1, phone.online.match.winsA)
            assertTrue(phone.online.canPick)
        }
    }

    @Test
    fun onNePasseALaMancheSuivanteQuUneFoisLaMancheRevelee() {
        val (host, guest) = table()
        host.online.pick(rock)

        host.online.next()
        guest.online.next()

        assertEquals(rock, host.online.match.pickA)
        assertEquals(hidden, guest.online.match.pickA)
    }

    @Test
    fun laPartieSeTermineQuandUnJoueurAGagneAssezDeManches() {
        val (host, guest) = table(target = 2)

        playRound(host, guest, rock, scissors)
        host.online.next()
        playRound(host, guest, paper, rock)

        for (phone in listOf(host, guest)) {
            assertTrue(phone.online.match.isOver)
            assertEquals(1, phone.online.match.winner)
            assertFalse(phone.online.canPick)
        }
        // La partie finie, plus de choix ; « next » relance une partie neuve avec le même nombre de manches.
        guest.online.pick(rock)
        assertEquals(rock, host.online.match.pickB)
        guest.online.next()
        assertEquals(RpsMatch(target = 2), host.online.match)
        assertEquals(RpsMatch(target = 2), guest.online.match)
    }

    @Test
    fun uneEgaliteSeRejoueSansPoint() {
        val (host, guest) = table()
        playRound(host, guest, paper, paper)

        assertEquals(1, host.online.match.draws)
        host.online.next()
        assertEquals(1, guest.online.match.draws)
        assertEquals(0, guest.online.match.winsA + guest.online.match.winsB)
        assertTrue(guest.online.canPick)
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartLaisseLOrganisateurAttendreUnAutreJoueur() {
        val (host, guest) = table(target = 5)
        host.online.pick(rock)

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertFalse(host.online.ready)
        assertEquals(RpsNotice.PLAYER_LEFT, host.online.notice)
        // La partie repart de zéro, avec le même nombre de manches.
        assertEquals(RpsMatch(target = 5), host.online.match)
    }

    @Test
    fun lOrganisateurQuiPartTermineLaPartieDeLInvite() {
        val (host, guest) = table()

        host.online.leave()

        assertFalse(host.online.active)
        assertTrue(guest.online.active)
        assertEquals(RpsNotice.HOST_LEFT, guest.online.notice)
    }

    @Test
    fun quitterRemetLaTableAZero() {
        val (host, guest) = table()
        host.online.pick(rock)

        guest.online.leave()

        assertEquals(-1, guest.online.seat)
        assertEquals(emptyList<String>(), guest.online.lobby)
        assertEquals(RpsMatch(), guest.online.match)
        assertEquals(RpsNotice.NONE, guest.online.notice)
    }

    // ---------- Messages ----------

    @Test
    fun chaqueMessageSurvitALEnvoi() {
        val match = RpsMatch(target = 5, winsA = 2, winsB = 3, draws = 1, pickA = hidden, pickB = scissors)
        val messages = listOf(
            RpsMessage.Table(listOf("Anna", "Ben"), 1, match, RpsNotice.NONE),
            RpsMessage.Table(listOf("Anna"), 1, RpsMatch(), RpsNotice.PLAYER_LEFT),
            RpsMessage.Pick(2),
            RpsMessage.Next
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Rps(message)))
            assertEquals(message.toString(), SyncMessage.Rps(message), decoded)
        }
    }

    @Test
    fun unMessageAbsurdeEstRameneAUneValeurPermise() {
        val json = RpsMessage.Table(listOf("Anna", "Ben"), 1, RpsMatch(), RpsNotice.NONE).toJson()
            .put("m", "99,-1,-1,-1,42,-9").put("seat", 8).put("notice", "bizarre")

        val decoded = rpsMessageFromJson(json) as RpsMessage.Table

        assertEquals(RpsMatch(), decoded.match)
        assertEquals(1, decoded.seat)
        assertEquals(RpsNotice.NONE, decoded.notice)
    }

    @Test
    fun unMessageInconnuEstIgnore() {
        assertNull(rpsMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
        assertNotNull(rpsMessageFromJson(RpsMessage.Pick(3).toJson()))
    }
}
