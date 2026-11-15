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
 * Domino chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1). Chacun ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DominoOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = DominoOnline(session)
    }

    private fun t(a: Int, b: Int): Int = DominoScoring.tileOf(a, b)

    private fun placed(a: Int, b: Int): PlacedDomino = PlacedDomino(t(a, b), a, b)

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == DOMINOS_ID }

    private fun connected(): Pair<Phone, Phone> {
        val host = Phone("host")
        val guest = Phone("guest")
        host.transport.peer = guest.transport
        guest.transport.peer = host.transport
        host.game.initGame(listOf("Anna", "Ben"), rules())
        assertTrue(host.session.startHosting("Téléphone d'Anna"))
        guest.session.startJoining()
        guest.session.connectTo(guest.session.endpoints.single(), "Téléphone de Ben")
        host.session.accept()
        return host to guest
    }

    /** Chaîne 4-2 (bouts 4 et 2) ; Anna a 3-3 et 1-1, Ben a 2-5 et 6-6 ; c'est à [current] de jouer. */
    private fun fixedTable(
        host: Phone,
        current: Int = 1,
        annaHand: List<Int> = listOf(t(3, 3), t(1, 1)),
        benHand: List<Int> = listOf(t(2, 5), t(6, 6)),
        stock: List<Int> = emptyList()
    ): DominoRound = host.game.dominoRound().also {
        it.load(listOf(annaHand, benHand), stock, listOf(placed(4, 2)), current)
        host.online.publish()
    }

    @Test
    fun lInviteRecoitLaTableDesQuIlEstConnecte() {
        val (host, guest) = connected()
        val mirror = guest.online.mirror
        assertNotNull(mirror)
        assertEquals(2, mirror!!.playerCount)
        assertEquals(-1, guest.online.mySeat)
        assertTrue(host.online.remoteSeats.isEmpty())
    }

    @Test
    fun lInviteChoisitSaPlaceEtPuisLaRend() {
        val (host, guest) = connected()

        guest.online.claim(1)
        assertEquals(setOf(1), host.online.remoteSeats)
        assertEquals(1, guest.online.mySeat)

        guest.online.claim(-1)
        assertTrue(host.online.remoteSeats.isEmpty())
        assertEquals(-1, guest.online.mySeat)
    }

    @Test
    fun lInviteNeVoitQueSaMainEtLaTailleDesAutres() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        val shown = guest.online.mirror!!
        assertEquals(round.hands[1], shown.hands[1])
        // La main d'Anna : deux dominos, mais sans valeur.
        assertEquals(2, shown.hands[0].size)
        assertTrue(shown.hands[0].all { it == 0 })
        // La pioche aussi n'est connue que par sa taille.
        assertEquals(round.stock.size, shown.stock.size)
        assertEquals(round.chain, shown.chain)
    }

    @Test
    fun sansPlaceUnInviteNeVoitAucuneMain() {
        val (host, guest) = connected()
        fixedTable(host)

        val shown = guest.online.mirror!!
        assertTrue(shown.hands.flatten().all { it == 0 })
    }

    @Test
    fun lInvitePoseUnDominoQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)
        guest.online.claim(1)

        guest.online.send(DominoOp.PLAY, t(2, 5), atLeft = false)

        assertEquals(2, round.chain.size)
        assertEquals(5, round.rightEnd)
        assertEquals(listOf(t(6, 6)), round.hands[1])
        assertEquals(0, round.current)
        assertEquals(2, guest.online.mirror!!.chain.size)
        assertEquals(0, guest.online.mirror!!.current)
        assertEquals(listOf(t(6, 6)), guest.online.mirror!!.hands[1])
    }

    @Test
    fun unDominoQuiNeSePosePasEstRefuse() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)
        guest.online.claim(1)

        // 6-6 ne touche ni le 4 ni le 2.
        guest.online.send(DominoOp.PLAY, t(6, 6), atLeft = true)

        assertEquals(1, round.chain.size)
        assertEquals(1, round.current)
        assertEquals(2, round.hands[1].size)
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 0)
        guest.online.claim(1)

        guest.online.send(DominoOp.PLAY, t(2, 5), atLeft = false)
        guest.online.send(DominoOp.PASS)

        assertEquals(1, round.chain.size)
        assertEquals(0, round.current)
    }

    @Test
    fun lInvitePiocheQuandIlNaRienAPoser() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1, benHand = listOf(t(6, 6), t(0, 0)), stock = listOf(t(2, 3), t(5, 5)))
        guest.online.claim(1)

        guest.online.send(DominoOp.DRAW)

        assertEquals(listOf(t(6, 6), t(0, 0), t(2, 3)), round.hands[1])
        assertEquals(1, round.current)
        // Il voit le domino pioché ; la pioche n'a plus qu'un domino, dont il ne connaît pas la valeur.
        assertEquals(listOf(t(6, 6), t(0, 0), t(2, 3)), guest.online.mirror!!.hands[1])
        assertEquals(1, guest.online.mirror!!.stock.size)
        assertEquals(0, guest.online.mirror!!.stock.first())
    }

    @Test
    fun leDernierDominoFinitLaMancheEtInscritLesPoints() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1, benHand = listOf(t(2, 5)), annaHand = listOf(t(3, 3)))
        guest.online.claim(1)

        guest.online.send(DominoOp.PLAY, t(2, 5), atLeft = false)

        assertTrue(round.roundOver)
        assertTrue(round.settled)
        // Ben l'emporte et marque les points restés chez Anna (3-3 : 6 points).
        assertEquals(listOf(0, 6), host.game.scores[0].map { it.baseValue })
        val shown = guest.online.mirror!!
        assertTrue(shown.roundOver)
        assertTrue(shown.settled)
        // La manche finie, les mains restantes sont montrées à tous.
        assertEquals(listOf(t(3, 3)), shown.hands[0])
    }

    @Test
    fun laMainDevoileeSeRecouvreQuandLInviteSeDeconnecte() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)
        guest.online.claim(1)
        round.reveal()
        assertTrue(round.revealed)

        guest.transport.disconnect("host")

        assertTrue(host.online.remoteSeats.isEmpty())
        assertFalse(round.revealed)
    }

    @Test
    fun leMessageDeTableSeCodeEtSeDecodeSansPerte() {
        val round = DominoRound(2) { }
        round.load(listOf(listOf(t(3, 3), t(1, 1)), listOf(t(2, 5))), listOf(t(0, 0)), listOf(placed(4, 2), placed(2, 6)), current = 1)
        val view = SyncMessage.DominoMsg(DominoMessage.View(round.tableFor(1), seat = 1, taken = listOf(0)))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
    }

    @Test
    fun lesActionsDeLInviteSeCodentEtSeDecodentSansPerte() {
        val messages = listOf<DominoMessage>(DominoMessage.Claim(1), DominoMessage.Claim(-1)) +
            DominoOp.entries.map { DominoMessage.Do(it, tile = 12, atLeft = it == DominoOp.PLAY) } +
            DominoMessage.Do(DominoOp.PASS)
        messages.forEach { message ->
            val wrapped = SyncMessage.DominoMsg(message)
            assertEquals(wrapped, SyncCodec.decode(SyncCodec.encode(wrapped)))
        }
    }
}
