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
 * Skyjo Action chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1). Les cartes cachées ne partent jamais, et les secrets
 * (carte en main, cartes regardées) ne vont qu'à celui qui joue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class SkyjoActionOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = SkyjoActionOnline(session)
    }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == SKYJO_ACTION_ID }

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

    /** Une grille de 12 cartes toutes différentes (1 à 12), dont les places de [up] sont retournées. */
    private fun grid(up: Set<Int> = emptySet()): List<SkyjoCell?> = List(12) { SkyjoCell(it + 1, it in up) }

    /** Table à trois joueurs : c'est à [current] de jouer, la pioche est [stock]. */
    private fun fixedTable(
        host: Phone,
        current: Int = 1,
        stock: List<Int> = listOf(7, 8, 9),
        hands: List<List<SkyjoHeldAction>> = List(3) { emptyList() }
    ): SkyjoActionRound = host.game.skyjoActionRound().also {
        it.load(listOf(grid(setOf(0)), grid(setOf(0)), grid(setOf(0))), stock = stock, current = current, hands = hands)
        host.online.publish()
    }

    @Test
    fun lInviteRecoitLaTableDesQuIlEstConnecte() {
        val (host, guest) = connected()
        val mirror = guest.online.mirror
        assertNotNull(mirror)
        assertEquals(3, mirror!!.playerCount)
        assertEquals(-1, guest.online.mySeat)
        assertTrue(host.online.remoteSeats.isEmpty())
    }

    @Test
    fun lInviteChoisitSaPlaceEtUnAutreNePeutPasLaPrendre() {
        val (host, guest) = connected()

        guest.online.claim(1)

        assertEquals(setOf(1), host.online.remoteSeats)
        assertEquals(1, guest.online.mySeat)
        assertTrue(host.online.isRemote(1))
        assertFalse(host.online.isRemote(0))

        guest.online.claim(-1)

        assertTrue(host.online.remoteSeats.isEmpty())
        assertEquals(-1, guest.online.mySeat)
    }

    @Test
    fun lesCartesCacheesNePartentJamais() {
        val (host, guest) = connected()
        val round = fixedTable(host)

        val shown = guest.online.mirror!!
        // La carte retournée est vue de tous ; les onze autres n'ont pas de valeur chez l'invité.
        assertEquals(1, shown.grids[0][0]!!.card)
        assertTrue(shown.grids[0][0]!!.up)
        for (player in 0 until 3) {
            for (cell in 1 until 12) {
                assertFalse(shown.grids[player][cell]!!.up)
                assertEquals(0, shown.grids[player][cell]!!.card)
            }
        }
        // Les pioches ne sont connues que par leur taille.
        assertEquals(round.stock.size, shown.stock.size)
        assertTrue(shown.stock.all { it == 0 })
        assertEquals(round.discard.last(), shown.discard.last())
        assertEquals(round.discard.size, shown.discard.size)
    }

    @Test
    fun lInvitePiocheEtPlaceSaCarteQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1, stock = listOf(7, 8, 9))
        guest.online.claim(1)

        guest.online.send(SkyjoOp.DRAW_STOCK)

        assertEquals(SkyjoPhase.PLACE, round.phase)
        assertEquals(7, round.held)
        // Il voit sa carte en main ; la pioche, elle, a perdu une carte chez lui aussi.
        assertEquals(SkyjoPhase.PLACE, guest.online.mirror!!.phase)
        assertEquals(7, guest.online.mirror!!.held)
        assertEquals(2, guest.online.mirror!!.stock.size)

        guest.online.send(SkyjoOp.PLACE, 4)

        assertEquals(SkyjoCell(7, true), round.grids[1][4])
        assertEquals(2, round.current)
        assertEquals(5, round.discard.last())
        assertEquals(2, guest.online.mirror!!.current)
        assertNull(guest.online.mirror!!.held)
        assertEquals(SkyjoCell(7, true), guest.online.mirror!!.grids[1][4])
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 0)
        guest.online.claim(1)

        guest.online.send(SkyjoOp.DRAW_STOCK)
        guest.online.send(SkyjoOp.TAKE_DISCARD)

        assertEquals(0, round.current)
        assertEquals(SkyjoPhase.CHOOSE, round.phase)
        assertNull(round.held)
    }

    @Test
    fun sansPlaceUnInviteNeJoueRien() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)

        guest.online.send(SkyjoOp.DRAW_STOCK)

        assertEquals(SkyjoPhase.CHOOSE, round.phase)
        assertNull(round.held)
    }

    @Test
    fun lesSecretsNePartentQuVersCeluiQuiJoue() {
        val (host, _) = connected()
        val round = fixedTable(host, current = 1, stock = listOf(7, 8, 9))
        assertTrue(round.drawFromStock())

        assertEquals(7, round.tableFor(1).held)
        assertNull(round.tableFor(0).held)
        assertNull(round.tableFor(2).held)
        assertNull(round.tableFor(-1).held)
    }

    @Test
    fun laCarteRegarderNeMontreLesCartesQuALInvite() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1, hands = listOf(emptyList(), listOf(SkyjoHeldAction(SkyjoAction.LOOK, 1)), emptyList()))
        guest.online.claim(1)

        guest.online.send(SkyjoOp.PLAY_ACTION, 0)
        assertEquals(SkyjoPhase.LOOK, round.phase)

        // Il regarde la première ligne de la grille d'Anna (4 colonnes) : sa première carte est déjà visible, il découvre les trois autres.
        guest.online.send(SkyjoOp.LOOK, 0, 0, true)

        assertEquals(SkyjoPhase.PEEK, round.phase)
        val seen = guest.online.mirror!!.peek
        assertEquals(listOf(2, 3, 4), seen.map { it.card })
        assertTrue(guest.online.mirror!!.peekShown)
        // Personne d'autre ne les reçoit.
        assertTrue(round.tableFor(0).peek.isEmpty())
        assertTrue(round.tableFor(2).peek.isEmpty())

        guest.online.send(SkyjoOp.CLOSE_PEEK)

        assertEquals(2, round.current)
        assertTrue(guest.online.mirror!!.peek.isEmpty())
    }

    @Test
    fun laPlaceDUnInviteQuiSeDeconnecteEstRendue() {
        val (host, guest) = connected()
        fixedTable(host)
        guest.online.claim(1)
        assertEquals(setOf(1), host.online.remoteSeats)

        guest.transport.disconnect("host")

        assertTrue(host.online.remoteSeats.isEmpty())
    }

    @Test
    fun leMessageDeTableSeCodeEtSeDecodeSansPerte() {
        val round = SkyjoActionRound(3, startPlayer = 0) { }
        round.load(
            listOf(grid(setOf(0, 5)), grid(setOf(0)), grid(setOf(0))),
            stock = listOf(7, 8),
            discard = listOf(3, -2),
            current = 1,
            actionRow = listOf(SkyjoAction.STEAL, SkyjoAction.PROTECTION),
            actionStock = listOf(SkyjoAction.LOOK),
            actionDiscard = listOf(SkyjoAction.METEOR),
            hands = listOf(emptyList(), listOf(SkyjoHeldAction(SkyjoAction.DRAW_THREE, 4)), emptyList())
        )
        assertTrue(round.drawFromStock())
        val table = round.tableFor(1)
        assertEquals(7, table.held)
        val view = SyncMessage.SkyjoMsg(SkyjoMessage.View(table, seat = 1, taken = listOf(0, 2)))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
    }

    @Test
    fun uneTableAvecDesPlacesVidesSeCodeEtSeDecodeSansPerte() {
        val round = SkyjoActionRound(2, startPlayer = 0) { }
        // Des places vides (colonnes déjà retirées) et des cartes de toutes valeurs, de -2 à l'étoile.
        val partial = List(12) { if (it % 3 == 0) null else SkyjoCell(it - 2, true) }
        val star = List(12) { SkyjoCell(if (it == 0) SkyjoActionScoring.STAR else -2, it % 2 == 0) }
        round.load(listOf(partial, star), stock = listOf(1, 2, 3), current = 1)
        val view = SyncMessage.SkyjoMsg(SkyjoMessage.View(round.tableFor(-1), seat = -1, taken = emptyList()))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
    }

    @Test
    fun lesActionsDeLInviteSeCodentEtSeDecodentSansPerte() {
        val messages = listOf<SkyjoMessage>(SkyjoMessage.Claim(2), SkyjoMessage.Claim(-1)) +
            SkyjoOp.entries.map { SkyjoMessage.Do(it, a = 3, b = 7, flag = it == SkyjoOp.LOOK) } +
            SkyjoMessage.Do(SkyjoOp.DRAW_STOCK)
        messages.forEach { message ->
            val wrapped = SyncMessage.SkyjoMsg(message)
            assertEquals(wrapped, SyncCodec.decode(SyncCodec.encode(wrapped)))
        }
    }
}
