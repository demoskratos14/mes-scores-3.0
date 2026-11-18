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
 * Oh Hell chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1) et ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class OhHellOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = OhHellOnline(session)
    }

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private val club = FrenchCards.CLUB
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val spade = FrenchCards.SPADE

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == OH_HELL_ID }

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

    /**
     * Une table connue, annonces faites (Anna 1, Ben 0, Chloé 1) : Anna (place 0) ouvre le premier pli. L'atout est pique (la carte
     * retournée est le 2 de pique) : au premier pli, personne n'en joue, c'est la couleur demandée qui décide.
     */
    private fun fixedTable(host: Phone): OhHellRound = host.game.ohHellRound().also {
        it.load(
            hands = listOf(
                listOf(c(club, 9), c(spade, 3)),
                listOf(c(club, 12), c(spade, 5)),
                listOf(c(diamond, 7), c(heart, 9))
            ),
            trump = c(spade, 2),
            current = 0,
            bids = listOf(1, 0, 1)
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
        assertEquals(setOf(c(club, 12), c(spade, 5)), guest.online.table!!.hand.toSet())
        assertEquals(round.hands.map { it.size }, guest.online.table!!.handSizes)
        assertEquals(c(spade, 2), guest.online.table!!.trump)
    }

    @Test
    fun lInviteAnnonceQuandCEstSonTourEtLaPlaceDesAutresEstIgnoree() {
        val (host, guest) = connected()
        // Manche 1 : le donneur est Anna, donc Ben (place 1) annonce le premier.
        val round = host.game.ohHellRound()
        host.online.publish()
        guest.online.claim(1)
        assertEquals(1, guest.online.table!!.current)
        assertEquals(OhHellPhase.BIDDING, guest.online.table!!.phase)

        guest.online.bid(1)

        assertEquals(1, round.bids[1])
        assertEquals(2, round.current)
        assertEquals(1, guest.online.table!!.bids[1])
        // Ce n'est plus son tour : une autre annonce est ignorée.
        guest.online.bid(0)
        assertEquals(1, round.bids[1])
        assertEquals(2, round.current)
    }

    @Test
    fun lInviteNePeutPasFaireUneAnnonceHorsDeLaCarteDeLaManche() {
        val (host, guest) = connected()
        val round = host.game.ohHellRound()
        host.online.publish()
        guest.online.claim(1)

        // 1 carte chacun : on ne peut pas annoncer 2 plis.
        guest.online.bid(2)

        assertEquals(-1, round.bids[1])
        assertEquals(1, round.current)
    }

    @Test
    fun lInvitePoseSaCarteQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)
        round.playFor(0, c(club, 9))
        host.online.publish()
        assertEquals(1, guest.online.table!!.current)
        assertTrue(guest.online.table!!.isPlayable(c(club, 12)))
        assertFalse(guest.online.table!!.isPlayable(c(spade, 5)))

        guest.online.play(c(club, 12))

        assertEquals(2, round.trick.size)
        assertEquals(2, round.current)
        assertEquals(1, guest.online.table!!.hand.size)
        assertEquals(2, guest.online.table!!.current)
    }

    @Test
    fun uneCarteHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(c(club, 12))

        assertTrue(round.trick.isEmpty())
        assertEquals(0, round.current)
        assertEquals(2, round.hands[1].size)
    }

    @Test
    fun sansPlaceChoisieLInviteNeJoueRien() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        round.playFor(0, c(club, 9))

        guest.online.play(c(club, 12))

        assertEquals(1, round.trick.size)
        assertEquals(1, round.current)
    }

    @Test
    fun seulLeGagnantDuPliPeutLEmpocher() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)
        round.playFor(0, c(club, 9))
        host.online.publish()
        guest.online.play(c(club, 12))
        round.playFor(2, c(diamond, 7))
        host.online.publish()
        assertTrue(guest.online.table!!.trickDone)
        assertEquals(1, guest.online.table!!.trickWinner)

        guest.online.collect()

        assertTrue(round.trick.isEmpty())
        assertEquals(1, round.current)
        assertEquals(1, round.tricksPlayed)
        assertEquals(listOf(0, 1, 0), round.tricksWon)
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
        val round = OhHellRound(4) { }
        round.bidFor(1, 0)
        val table = round.tableFor(2)
        val message = SyncMessage.OhHell(OhHellMsg.View(table, seat = 2, taken = listOf(0, 3)))

        assertEquals(message, SyncCodec.decode(SyncCodec.encode(message)))
        assertFalse(table.hand.isEmpty())

        val playing = OhHellRound(3) { }
        playing.load(
            hands = listOf(listOf(c(club, 9), c(spade, 3)), listOf(c(club, 12), c(spade, 5)), listOf(c(diamond, 7), c(heart, 9))),
            trump = c(heart, 2),
            current = 1,
            bids = listOf(1, 0, 1),
            trick = listOf(OhHellPlay(0, c(club, 9)))
        )
        val withTrick = SyncMessage.OhHell(OhHellMsg.View(playing.tableFor(1), seat = 1, taken = emptyList()))
        assertEquals(withTrick, SyncCodec.decode(SyncCodec.encode(withTrick)))
        listOf(OhHellMsg.Claim(1), OhHellMsg.Bid(2), OhHellMsg.Play(7), OhHellMsg.Collect, OhHellMsg.Next).forEach {
            val simple = SyncMessage.OhHell(it)
            assertEquals(simple, SyncCodec.decode(SyncCodec.encode(simple)))
        }
    }
}
