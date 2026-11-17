package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Le Huit américain chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1) et ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class HuitOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = HuitOnline(session)
    }

    private val spade = 0
    private val heart = 1
    private val diamond = 2
    private val club = 3

    private fun c(suit: Int, rank: Int): Int = HuitScoring.card(suit, rank)

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == HUIT_ID }

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

    /** Une table connue : Ben (place 1) doit jouer sur un 5 de cœur ; il a un 7 de cœur, un 2 de trèfle et un 8 de pique. */
    private fun fixedTable(host: Phone): HuitRound = host.game.huitRound().also {
        it.load(
            hands = listOf(
                listOf(c(heart, 1), c(heart, 2)),
                listOf(c(heart, 7), c(club, 2), c(spade, 8)),
                listOf(c(diamond, 4), c(diamond, 5))
            ),
            stock = listOf(c(diamond, 9), c(diamond, 10), c(diamond, 6)),
            discard = listOf(c(heart, 5)),
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
        assertEquals(round.tableFor(1).hand, guest.online.table!!.hand)
        // L'organisateur n'envoie jamais les mains des autres : leur taille seulement.
        assertEquals(round.hands.map { it.size }, guest.online.table!!.handSizes)
    }

    @Test
    fun lInvitePoseUneCarteQuandCEstSonTourEtLOrganisateurLeVoit() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(c(heart, 7))

        assertEquals(c(heart, 7), round.top)
        assertEquals(2, round.current)
        assertEquals(c(heart, 7), guest.online.table!!.top)
        assertEquals(2, guest.online.table!!.hand.size)
        assertEquals(2, guest.online.table!!.current)
    }

    @Test
    fun unHuitPoseParLInviteChoisitLaCouleur() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(c(spade, 8), club)

        assertEquals(club, round.askedSuit)
        assertEquals(club, guest.online.table!!.askedSuit)
        assertEquals(2, round.current)
    }

    @Test
    fun unHuitSansCouleurValideEstRefuse() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(c(spade, 8), -1)

        assertEquals(c(heart, 5), round.top)
        assertEquals(1, round.current)
        assertEquals(3, guest.online.table!!.hand.size)
    }

    @Test
    fun lInviteNeJoueQueQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        round.load(round.hands, round.stock, round.discard, current = 0)
        host.online.publish()
        guest.online.claim(1)

        guest.online.play(c(heart, 7))

        assertEquals(c(heart, 5), round.top)
        assertEquals(0, round.current)
        assertEquals(3, round.hands[1].size)
    }

    @Test
    fun lInviteSansPlaceNeJouePas() {
        val (host, guest) = connected()
        val round = fixedTable(host)

        guest.online.play(c(heart, 7))
        guest.online.draw()

        assertEquals(c(heart, 5), round.top)
        assertEquals(1, round.current)
        assertEquals(3, round.hands[1].size)
    }

    @Test
    fun lInvitePiocheQuandIlNaAucuneCarteJouableEtPasAvant() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        // Il a un 7 de cœur jouable : la pioche est refusée.
        guest.online.draw()
        assertEquals(3, round.hands[1].size)

        // Sans carte jouable, elle est acceptée.
        round.load(
            hands = listOf(round.hands[0], listOf(c(club, 2), c(club, 3)), round.hands[2]),
            stock = listOf(c(diamond, 9)),
            discard = listOf(c(heart, 5)),
            current = 1
        )
        host.online.publish()
        guest.online.draw()
        assertEquals(3, round.hands[1].size)
        assertEquals(3, guest.online.table!!.hand.size)
        assertEquals(1, round.current)
    }

    @Test
    fun lInvitePasseQuandIlNePeutNiJouerNiPiocher() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)
        round.load(
            hands = listOf(round.hands[0], listOf(c(club, 2)), round.hands[2]),
            stock = emptyList(),
            discard = listOf(c(heart, 5)),
            current = 1
        )
        host.online.publish()
        assertTrue(guest.online.table!!.mustPass)

        guest.online.pass()

        assertEquals(2, round.current)
    }

    @Test
    fun uneMancheFinieParLInviteEstInscriteEtLaSuivanteSeDistribue() {
        val (host, guest) = connected()
        val round = host.game.huitRound().also {
            it.load(
                hands = listOf(listOf(c(heart, 1), c(heart, 2)), listOf(c(heart, 7)), listOf(c(diamond, 4), c(diamond, 5))),
                stock = listOf(c(diamond, 9)),
                discard = listOf(c(heart, 5)),
                current = 1
            )
            host.online.publish()
        }
        guest.online.claim(1)

        guest.online.play(c(heart, 7))

        assertTrue(round.roundOver)
        assertEquals(1, round.winner)
        assertTrue(round.settled)
        assertTrue(guest.online.table!!.roundOver)
        assertEquals(1, guest.online.table!!.winner)

        val number = round.roundNumber
        guest.online.next()
        assertEquals(number + 1, round.roundNumber)
        assertFalse(round.roundOver)
    }

    @Test
    fun lInviteQuiQuitteSaPlaceLaLibere() {
        val (host, guest) = connected()
        fixedTable(host)
        guest.online.claim(1)
        assertEquals(setOf(1), host.online.remoteSeats)

        guest.online.claim(-1)

        assertTrue(host.online.remoteSeats.isEmpty())
        assertEquals(-1, guest.online.mySeat)
    }
}
