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
 * Le Président chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1) et ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class PresidentOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = PresidentOnline(session)
    }

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == PRESIDENT_ID }

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

    /** Une table connue : Ben (place 1) doit ouvrir le pli. */
    private fun fixedTable(host: Phone): PresidentRound = host.game.presidentRound().also {
        it.load(
            listOf(listOf(c(spade, 5)), listOf(c(spade, 6), c(heart, 9)), listOf(c(heart, 7), c(heart, 8))),
            current = 1
        )
        host.online.publish()
    }

    @Test
    fun lInviteRecoitLaTableDesQuIlEstConnecteMaisAucuneMain() {
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
        assertEquals(listOf(c(spade, 6), c(heart, 9)), guest.online.table!!.hand)
        // L'organisateur n'envoie jamais les mains des autres : leur taille seulement.
        assertEquals(round.hands.map { it.size }, guest.online.table!!.handSizes)
    }

    @Test
    fun lInvitePoseSesCartesQuandCEstSonTourEtLOrganisateurLeVoit() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(listOf(c(spade, 6)))

        assertEquals(listOf(c(spade, 6)), round.top)
        assertEquals(2, round.current)
        assertEquals(listOf(c(spade, 6)), guest.online.table!!.top)
        assertEquals(1, guest.online.table!!.hand.size)
        assertEquals(2, guest.online.table!!.current)
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        round.load(
            listOf(listOf(c(spade, 5)), listOf(c(spade, 6), c(heart, 9)), listOf(c(heart, 7), c(heart, 8))),
            current = 0
        )
        guest.online.claim(1)

        guest.online.play(listOf(c(spade, 6)))
        guest.online.pass()

        assertTrue(round.top.isEmpty())
        assertEquals(0, round.current)
        assertEquals(2, round.hands[1].size)
    }

    @Test
    fun sansPlaceChoisieLInviteNeJoueRien() {
        val (host, guest) = connected()
        val round = fixedTable(host)

        guest.online.play(listOf(c(spade, 6)))

        assertTrue(round.top.isEmpty())
        assertEquals(1, round.current)
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
        val round = PresidentRound(4, shuffle = { })
        val table = round.tableFor(2)
        val message = SyncMessage.Pres(PresMessage.View(table, seat = 2, taken = listOf(0, 3)))

        val decoded = SyncCodec.decode(SyncCodec.encode(message))

        assertEquals(message, decoded)
        assertFalse(table.hand.isEmpty())
    }
}
