package com.aventure.messcores

import kotlin.random.Random
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
 * Le Duel de réflexes chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur est « Anna » (premier joueur, place 0), l'invité « Ben » (deuxième joueur, place 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class ReflexOnlineTest {

    private class Phone(selfId: String) {
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport)
        val online = ReflexOnline(session, Random(7))
    }

    /** Anna organise, Ben rejoint : la partie est ouverte sur les deux téléphones. */
    private fun table(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.session.startHostingReflex("Anna")
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
        assertEquals(1, host.online.myNumber)
        assertEquals(2, guest.online.myNumber)
        assertTrue(host.online.isHost)
        assertFalse(guest.online.isHost)
        assertTrue(host.online.ready)
        assertTrue(guest.online.ready)
        assertEquals(1, guest.online.joined)
        assertEquals(SyncKind.REFLEX, guest.session.kind)
        assertEquals(ReflexMatch(), guest.online.match)
        assertFalse(guest.online.running)
    }

    @Test
    fun avantLArriveeDeLInviteOnNeLancePasDeManche() {
        val host = Phone("host")
        host.transport.peer = Phone("guest").transport
        host.session.startHostingReflex("Anna")
        host.online.openLobby("Anna")

        assertFalse(host.online.ready)
        host.online.startRound()
        assertFalse(host.online.running)
    }

    // ---------- Une manche ----------

    @Test
    fun lOrganisateurLanceLaMancheEtLInviteRecoitLeMemeDelai() {
        val (host, guest) = table()

        host.online.startRound()

        assertTrue(host.online.running)
        assertTrue(guest.online.running)
        assertEquals(1, host.online.round)
        assertEquals(1, guest.online.round)
        assertEquals(host.online.delayMs, guest.online.delayMs)
        assertTrue(host.online.delayMs in Reflex.MIN_DELAY_MS..Reflex.MAX_DELAY_MS)
        assertFalse(host.online.reported)
        assertFalse(guest.online.reported)
    }

    @Test
    fun lInvitePeutLancerLaManche() {
        val (host, guest) = table()

        guest.online.startRound()

        assertTrue(host.online.running)
        assertTrue(guest.online.running)
        assertEquals(1, guest.online.round)
    }

    @Test
    fun lePlusRapideGagneEtLesDeuxTelephonesVoientLeMemeResultat() {
        val (host, guest) = table()
        host.online.startRound()

        host.online.report(310)
        // Tant que l'invité n'a rien donné, la manche continue.
        assertTrue(host.online.running)
        assertTrue(host.online.reported)

        guest.online.report(240)

        for (phone in listOf(host, guest)) {
            assertFalse(phone.online.running)
            assertEquals(2, phone.online.match.lastWinner)
            assertEquals(240, phone.online.match.lastReactionMs)
            assertEquals(310, phone.online.match.lastLoserReactionMs)
            assertEquals(1, phone.online.match.wins2)
        }
    }

    @Test
    fun lInviteQuiRepondEnPremierEstAttendu() {
        val (host, guest) = table()
        host.online.startRound()

        guest.online.report(200)
        assertTrue(guest.online.reported)
        assertTrue(host.online.running)

        host.online.report(250)
        assertFalse(guest.online.running)
        assertEquals(2, guest.online.match.lastWinner)
    }

    @Test
    fun unFauxDepartDonneLaMancheAL_autre() {
        val (host, guest) = table()
        host.online.startRound()

        host.online.report(Reflex.EARLY)
        guest.online.report(500)

        assertEquals(2, host.online.match.lastWinner)
        assertTrue(guest.online.match.falseStart)
    }

    @Test
    fun uneMancheNulleSeRejoueSansPoint() {
        val (host, guest) = table()
        host.online.startRound()
        host.online.report(Reflex.MISS)
        guest.online.report(Reflex.MISS)

        assertFalse(host.online.running)
        assertTrue(guest.online.match.lastDraw)
        assertEquals(0, guest.online.match.wins1 + guest.online.match.wins2)

        // On peut relancer : c'est une nouvelle manche.
        guest.online.startRound()
        assertEquals(2, host.online.round)
        assertTrue(host.online.running)
        assertFalse(guest.online.reported)
    }

    @Test
    fun unSeulResultatParManche() {
        val (host, guest) = table()
        host.online.startRound()

        host.online.report(400)
        host.online.report(100)
        guest.online.report(300)
        guest.online.report(50)

        assertEquals(2, host.online.match.lastWinner)
        assertEquals(300, host.online.match.lastReactionMs)
    }

    @Test
    fun onNeDonnePasDeResultatHorsManche() {
        val (host, guest) = table()

        host.online.report(100)
        guest.online.report(100)

        assertEquals(ReflexMatch(), host.online.match)
        assertFalse(host.online.reported)
        assertFalse(guest.online.reported)
    }

    @Test
    fun unResultatDUneAncienneMancheEstIgnore() {
        val (host, guest) = table()
        host.online.startRound()
        host.online.report(300)
        guest.online.report(200)

        // Un message en retard de la manche 1 arrive pendant la manche 2.
        host.online.startRound()
        guest.session.sendToHost(SyncMessage.Reflex(ReflexMessage.Result(1, 10)))
        host.online.report(280)

        assertTrue(host.online.running)
        guest.online.report(250)
        assertEquals(2, host.online.round)
        assertEquals(250, host.online.match.lastReactionMs)
    }

    @Test
    fun onNeLanceNiNeRelanceUneMancheEnCours() {
        val (host, guest) = table()
        host.online.startRound()
        val delay = host.online.delayMs

        host.online.startRound()
        guest.online.startRound()

        assertEquals(1, host.online.round)
        assertEquals(delay, host.online.delayMs)
    }

    // ---------- La partie ----------

    @Test
    fun lePremierAuxManchesCiblesGagneLaPartieEtOnPeutRecommencer() {
        val (host, guest) = table()

        repeat(Reflex.TARGET) {
            host.online.startRound()
            host.online.report(200)
            guest.online.report(300)
        }

        assertTrue(host.online.match.isOver)
        assertTrue(guest.online.match.isOver)
        assertEquals(1, guest.online.match.winner)

        // Plus de manche une fois la partie finie.
        guest.online.startRound()
        host.online.startRound()
        assertFalse(host.online.running)

        // Chacun des deux joueurs peut lancer une nouvelle partie.
        guest.online.newMatch()
        assertEquals(ReflexMatch(), host.online.match)
        assertEquals(ReflexMatch(), guest.online.match)
    }

    @Test
    fun onNeRelancePasUnePartieEnCours() {
        val (host, guest) = table()
        host.online.startRound()
        host.online.report(200)
        guest.online.report(300)

        guest.online.newMatch()
        host.online.newMatch()

        assertEquals(1, host.online.match.wins1)
    }

    // ---------- Départs ----------

    @Test
    fun lInviteQuiPartRemetLaTableAZero() {
        val (host, guest) = table()
        host.online.startRound()
        host.online.report(200)
        guest.online.report(300)
        host.online.startRound()

        guest.online.leave()

        assertFalse(guest.online.active)
        assertEquals(listOf("Anna"), host.online.lobby)
        assertEquals(ReflexMatch(), host.online.match)
        assertFalse(host.online.running)
        assertEquals(ReflexNotice.PLAYER_LEFT, host.online.notice)
        assertFalse(host.online.ready)
    }

    // ---------- Messages ----------

    @Test
    fun lesMessagesSontCodesPuisDecodesSansPerte() {
        val played = ReflexMatch(wins1 = 2, wins2 = 1, lastWinner = 1, lastReactionMs = 187, lastLoserReactionMs = 264)
        val messages = listOf(
            ReflexMessage.Table(listOf("Anna", "Ben"), 1, ReflexMatch(), ReflexNotice.NONE, 0, 0L, false),
            ReflexMessage.Table(listOf("Anna", "Ben"), 1, played, ReflexNotice.NONE, 4, 2_345L, true),
            ReflexMessage.Table(listOf("Anna", "Ben"), 1, ReflexMatch(lastWinner = 2, falseStart = true), ReflexNotice.NONE, 2, 1_800L, false),
            ReflexMessage.Table(listOf("Anna", "Ben"), 1, ReflexMatch(lastDraw = true), ReflexNotice.NONE, 3, 3_000L, false),
            ReflexMessage.Table(listOf("Anna"), 1, ReflexMatch(), ReflexNotice.PLAYER_LEFT, 0, 0L, false),
            ReflexMessage.Result(3, 245),
            ReflexMessage.Result(3, Reflex.EARLY),
            ReflexMessage.Result(3, Reflex.MISS),
            ReflexMessage.Start,
            ReflexMessage.NewMatch
        )
        for (message in messages) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Reflex(message)))
            assertEquals(message.toString(), SyncMessage.Reflex(message), decoded)
        }
    }

    @Test
    fun unMessageAbsurdeEstRamenePuisUnInconnuEstRefuse() {
        val json = ReflexMessage.Table(listOf("Anna", "Ben"), 1, ReflexMatch(), ReflexNotice.NONE, 1, 2_000L, true).toJson()
        json.put("w1", 99).put("last", 9).put("ms", 123_456).put("lms", 654_321).put("delay", 1_000_000L).put("round", -5)

        val decoded = reflexMessageFromJson(json) as ReflexMessage.Table
        assertEquals(Reflex.TARGET, decoded.match.wins1)
        assertEquals(2, decoded.match.lastWinner)
        assertEquals(9_999, decoded.match.lastReactionMs)
        assertEquals(9_999, decoded.match.lastLoserReactionMs)
        assertEquals(Reflex.MAX_DELAY_MS, decoded.delayMs)
        assertEquals(0, decoded.round)

        assertNotNull(reflexMessageFromJson(ReflexMessage.Start.toJson()))
        assertNull(reflexMessageFromJson(org.json.JSONObject().put("k", "inconnu")))
    }
}
