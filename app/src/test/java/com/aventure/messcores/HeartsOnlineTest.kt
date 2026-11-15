package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Cœurs chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1) et ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class HeartsOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = HeartsOnline(session)
    }

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private val club = FrenchCards.CLUB
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val spade = FrenchCards.SPADE

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == HEARTS_ID }

    private fun connected(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.game.initGame(listOf("Anna", "Ben", "Chloé"), rules())
        assertTrue(host.session.startHosting("Téléphone d'Anna"))
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Téléphone de Ben")
        host.session.accept()
        return host to guest
    }

    /** Une table connue, sans passe : Anna (place 0) ouvre avec le 2 de trèfle. */
    private fun fixedTable(host: Phone): HeartsRound = host.game.heartsRound().also {
        it.load(
            listOf(
                listOf(HeartsScoring.TWO_OF_CLUBS, c(heart, 5)),
                listOf(c(club, 9), c(spade, 3)),
                listOf(c(diamond, 7), c(heart, 9))
            ),
            current = 0
        )
        host.online.publish()
    }

    @Test
    fun lInviteRecoitLaTableMaisAucuneMainTantQuIlNAPasDePlace() {
        val (host, guest) = connected()
        val table = guest.online.table
        assertNotNull(table)
        assertEquals(-1, guest.online.mySeat)
        assertTrue(table!!.hand.isEmpty())
        assertEquals(3, table.handSizes.size)
        assertTrue(host.online.remoteSeats.isEmpty())
    }

    @Test
    fun lInviteChoisitSaPlaceEtNeRecoitQueSaMain() {
        val (host, guest) = connected()
        val round = fixedTable(host)

        guest.online.claim(1)

        assertEquals(setOf(1), host.online.remoteSeats)
        assertEquals(1, guest.online.mySeat)
        assertEquals(setOf(c(club, 9), c(spade, 3)), guest.online.table!!.hand.toSet())
        assertEquals(round.hands.map { it.size }, guest.online.table!!.handSizes)
    }

    @Test
    fun lInvitePasseSesCartesSansAttendreSonTourEtLaPasseSeTermineQuandTousOntChoisi() {
        val (host, guest) = connected()
        val round = host.game.heartsRound()
        host.online.publish()
        assertEquals(HeartsPhase.PASSING, round.phase)
        guest.online.claim(1)

        guest.online.pass(guest.online.table!!.hand.take(3))

        assertTrue(round.hasPassed(1))
        assertTrue(guest.online.table!!.passed[1])
        assertEquals(HeartsPhase.PASSING, round.phase)

        round.passFor(0, round.hands[0].take(3))
        round.passFor(2, round.hands[2].take(3))
        host.online.publish()

        assertEquals(HeartsPhase.PLAYING, round.phase)
        assertEquals(HeartsPhase.PLAYING, guest.online.table!!.phase)
        assertEquals(round.hands[1].toSet(), guest.online.table!!.hand.toSet())
        assertEquals(round.received[1].toSet(), guest.online.table!!.received.toSet())
    }

    @Test
    fun lInvitePoseSaCarteQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)
        round.playFor(0, HeartsScoring.TWO_OF_CLUBS)
        host.online.publish()
        assertEquals(1, guest.online.table!!.current)
        assertTrue(guest.online.table!!.isPlayable(c(club, 9)))
        assertFalse(guest.online.table!!.isPlayable(c(spade, 3)))

        guest.online.play(c(club, 9))

        assertEquals(2, round.trick.size)
        assertEquals(2, round.current)
        assertEquals(1, guest.online.table!!.hand.size)
        assertEquals(2, guest.online.table!!.current)
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(c(club, 9))

        assertTrue(round.trick.isEmpty())
        assertEquals(0, round.current)
        assertEquals(2, round.hands[1].size)
    }

    @Test
    fun sansPlaceChoisieLInviteNeJoueRien() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        round.playFor(0, HeartsScoring.TWO_OF_CLUBS)

        guest.online.play(c(club, 9))

        assertEquals(1, round.trick.size)
        assertEquals(1, round.current)
    }

    @Test
    fun seulLeGagnantDuPliPeutLEmpocher() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)
        round.playFor(0, HeartsScoring.TWO_OF_CLUBS)
        host.online.publish()
        guest.online.play(c(club, 9))
        round.playFor(2, c(diamond, 7))
        host.online.publish()
        assertTrue(guest.online.table!!.trickDone)
        assertEquals(1, guest.online.table!!.trickWinner)

        guest.online.collect()

        assertTrue(round.trick.isEmpty())
        assertEquals(1, round.current)
        assertEquals(1, round.tricksPlayed)
    }

    @Test
    fun laPlaceDeLInviteSeLibereQuandIlSeDeconnecte() {
        val (host, guest) = connected()
        fixedTable(host)
        guest.online.claim(1)
        assertEquals(setOf(1), host.online.remoteSeats)

        guest.session.disconnect()

        assertTrue(host.online.remoteSeats.isEmpty())
        assertNull(guest.online.table)
    }

    @Test
    fun leMessageDeTableSeCodeEtSeDecodeSansPerte() {
        val round = HeartsRound(4) { }
        round.passFor(2, round.hands[2].take(3))
        val table = round.tableFor(2)
        val message = SyncMessage.Hearts(HeartsMsg.View(table, seat = 2, taken = listOf(0, 3)))

        assertEquals(message, SyncCodec.decode(SyncCodec.encode(message)))
        assertFalse(table.hand.isEmpty())

        val playing = HeartsRound(3, startRound = 3) { }
        val withTrick = SyncMessage.Hearts(HeartsMsg.View(playing.tableFor(0), seat = 0, taken = emptyList()))
        assertEquals(withTrick, SyncCodec.decode(SyncCodec.encode(withTrick)))
        listOf(HeartsMsg.Claim(1), HeartsMsg.Pass(listOf(1, 2, 3)), HeartsMsg.Play(7), HeartsMsg.Collect, HeartsMsg.Next).forEach {
            val simple = SyncMessage.Hearts(it)
            assertEquals(simple, SyncCodec.decode(SyncCodec.encode(simple)))
        }
    }
}
