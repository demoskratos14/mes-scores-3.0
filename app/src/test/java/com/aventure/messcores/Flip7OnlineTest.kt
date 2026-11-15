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
 * Flip 7 chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1). Tout est public : l'invité voit la table entière.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class Flip7OnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = Flip7Online(session)
    }

    private val freeze = Flip7Cards.FREEZE

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == FLIP7_ID }

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

    /** Table vide, pioche [stock], c'est à Ben (place 1) de jouer. */
    private fun fixedTable(host: Phone, stock: List<Int>, current: Int = 1): Flip7Round = host.game.flip7Round().also {
        it.load(stock, current)
        host.online.publish()
    }

    @Test
    fun lInviteRecoitLaTableDesQuIlEstConnecte() {
        val (host, guest) = connected()
        val table = guest.online.table
        assertNotNull(table)
        assertEquals(-1, guest.online.mySeat)
        assertEquals(3, table!!.status.size)
        assertEquals(3, table.numbers.size)
        assertTrue(host.online.remoteSeats.isEmpty())
    }

    @Test
    fun lInviteChoisitSaPlaceEtVoitToutesLesCartes() {
        val (host, guest) = connected()
        val round = host.game.flip7Round()

        guest.online.claim(1)

        assertEquals(setOf(1), host.online.remoteSeats)
        assertEquals(1, guest.online.mySeat)
        // Rien n'est caché : l'invité voit les cartes de tous les joueurs.
        assertEquals(round.numbers, guest.online.table!!.numbers)
        assertEquals(round.stock.size, guest.online.table!!.stockSize)
    }

    @Test
    fun lInvitePiocheQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host, listOf(5, 9, 3))
        guest.online.claim(1)

        guest.online.hit()

        assertEquals(listOf(5), round.numbers[1])
        assertEquals(2, round.current)
        assertEquals(listOf(5), guest.online.table!!.numbers[1])
        assertEquals(2, guest.online.table!!.current)
        assertEquals(2, guest.online.table!!.stockSize)
    }

    @Test
    fun lInviteSArreteEtMarqueSesPoints() {
        val (host, guest) = connected()
        val round = fixedTable(host, listOf(5, 9, 3))
        guest.online.claim(1)
        guest.online.hit()
        // C'est à Chloé, puis à Anna, puis de nouveau à Ben.
        round.stay()
        round.stay()
        assertEquals(1, round.current)

        guest.online.stay()

        assertTrue(round.roundOver)
        assertTrue(round.settled)
        assertEquals(listOf(0, 5, 0), host.game.scores[0].map { it.baseValue })
        val table = guest.online.table!!
        assertTrue(table.roundOver)
        assertEquals(1, table.winner)
        assertEquals(listOf(0, 5, 0), table.points)
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host, listOf(5, 9, 3), current = 0)
        guest.online.claim(1)

        guest.online.hit()
        guest.online.stay()

        assertEquals(0, round.current)
        assertTrue(round.numbers[1].isEmpty())
        assertEquals(Flip7Status.ACTIVE, round.status[1])
        assertEquals(3, round.stock.size)
    }

    @Test
    fun sansPlaceChoisieLInviteNeJoueRien() {
        val (host, guest) = connected()
        val round = fixedTable(host, listOf(5, 9, 3))

        guest.online.hit()
        guest.online.stay()

        assertEquals(1, round.current)
        assertEquals(3, round.stock.size)
        assertEquals(Flip7Status.ACTIVE, round.status[1])
    }

    @Test
    fun lInviteQuiPiocheUneCarteActionChoisitLaCible() {
        val (host, guest) = connected()
        val round = fixedTable(host, listOf(freeze, 4, 4))
        guest.online.claim(1)

        guest.online.hit()

        assertEquals(1, round.pending?.source)
        assertEquals(1, guest.online.table!!.pending?.source)
        assertEquals(listOf(0, 1, 2), guest.online.table!!.pending?.targets)
        // Tant que le choix n'est pas fait, piocher est refusé.
        guest.online.hit()
        assertEquals(3 - 1, round.stock.size)

        guest.online.choose(2)

        assertNull(round.pending)
        assertEquals(Flip7Status.FROZEN, round.status[2])
        assertNull(guest.online.table!!.pending)
        assertEquals(Flip7Status.FROZEN, guest.online.table!!.status[2])
    }

    @Test
    fun uneCarteActionNEstChoisiePasParUnAutreTelephone() {
        val (host, guest) = connected()
        // Anna (place 0, sur le téléphone de l'organisateur) pioche Gel ; Ben ne peut pas choisir à sa place.
        val round = fixedTable(host, listOf(freeze, 4, 4), current = 0)
        guest.online.claim(1)
        round.hit()
        host.online.publish()
        assertEquals(0, round.pending?.source)

        guest.online.choose(2)

        assertNotNull(round.pending)
        assertEquals(Flip7Status.ACTIVE, round.status[2])
    }

    @Test
    fun lInviteLanceLaMancheSuivanteApresLInscription() {
        val (host, guest) = connected()
        val round = fixedTable(host, listOf(5, 9, 3))
        guest.online.claim(1)
        guest.online.hit()
        round.stay()
        round.stay()
        guest.online.stay()
        assertEquals(1, round.roundNumber)

        guest.online.next()

        assertFalse(round.roundOver)
        assertEquals(2, round.roundNumber)
        assertEquals(2, guest.online.table!!.roundNumber)
        assertFalse(guest.online.table!!.roundOver)
    }

    @Test
    fun uneNouvellePlaceChoisieRemplaceLAncienne() {
        val (host, guest) = connected()
        fixedTable(host, listOf(5, 9, 3))
        guest.online.claim(1)

        guest.online.claim(2)

        assertEquals(setOf(2), host.online.remoteSeats)
        assertEquals(2, guest.online.mySeat)
    }

    @Test
    fun uneAnnoncePourUnePlaceInexistanteNeChangeRien() {
        val (host, guest) = connected()
        fixedTable(host, listOf(5, 9, 3))
        guest.online.claim(1)

        guest.online.claim(5)

        assertEquals(setOf(1), host.online.remoteSeats)
        assertEquals(1, guest.online.mySeat)
    }

    @Test
    fun laPlaceDeLInviteSeLibereQuandIlSeDeconnecte() {
        val (host, guest) = connected()
        fixedTable(host, listOf(5, 9, 3))
        guest.online.claim(1)
        assertEquals(setOf(1), host.online.remoteSeats)

        guest.session.disconnect()

        assertTrue(host.online.remoteSeats.isEmpty())
        assertNull(guest.online.table)
    }

    @Test
    fun lInviteQuiQuitteSaPlaceLaLibere() {
        val (host, guest) = connected()
        fixedTable(host, listOf(5, 9, 3))
        guest.online.claim(1)

        guest.online.claim(-1)

        assertTrue(host.online.remoteSeats.isEmpty())
        assertEquals(-1, guest.online.mySeat)
    }

    @Test
    fun leMessageDeTableSeCodeEtSeDecodeSansPerte() {
        val round = Flip7Round(3, dealer = 0) { }
        round.load(listOf(5, freeze, 4), current = 0)
        round.hit()
        round.hit()
        val table = round.tableFor()
        assertNotNull(table.pending)
        val view = SyncMessage.Flip7Msg(Flip7Message.View(table, seat = 1, taken = listOf(0, 2)))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
    }

    @Test
    fun leMessageDeFinDeMancheSeCodeEtSeDecodeSansPerte() {
        val round = Flip7Round(3, dealer = 0) { }
        round.load(listOf(5, 6, 7), current = 0)
        round.hit()
        round.stay()
        round.stay()
        round.stay()
        assertTrue(round.roundOver)
        val view = SyncMessage.Flip7Msg(Flip7Message.View(round.tableFor(), seat = -1, taken = emptyList()))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
    }

    @Test
    fun lesActionsDeLInviteSeCodentEtSeDecodentSansPerte() {
        listOf(
            Flip7Message.Claim(2),
            Flip7Message.Claim(-1),
            Flip7Message.Hit,
            Flip7Message.Stay,
            Flip7Message.Choose(1),
            Flip7Message.Next
        ).forEach { message ->
            val wrapped = SyncMessage.Flip7Msg(message)
            assertEquals(wrapped, SyncCodec.decode(SyncCodec.encode(wrapped)))
        }
    }
}
