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
 * Trio chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1). Chacun ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class TrioOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = TrioOnline(session)
    }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == TRIO_ID }

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

    /** Anna 3-5-9, Ben 2-4-8, Chloé 1-6-7, milieu 2-2-10 ; c'est à [current] de jouer. */
    private fun fixedTable(host: Phone, current: Int = 1): TrioRound = host.game.trioRound().also {
        it.load(listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)), listOf(2, 2, 10), current = current)
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
        assertEquals(3, shown.hands[0].size)
        assertTrue(shown.hands[0].all { it == 0 })
        assertTrue(shown.hands[2].all { it == 0 })
        assertEquals(round.center.size, shown.center.size)
        assertTrue(shown.center.all { it == 0 })
    }

    @Test
    fun sansPlaceUnInviteNeVoitAucuneMain() {
        val (host, guest) = connected()
        fixedTable(host)

        val shown = guest.online.mirror!!
        assertTrue(shown.hands.flatten().all { it == 0 })
    }

    @Test
    fun lInviteRetourneUneCarteQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)
        guest.online.claim(1)

        guest.online.send(TrioOp.HIGH, 2) // la plus grande carte de Chloé : 7

        assertEquals(listOf(TrioReveal(TrioZone.HAND, 2, 2, 7)), round.reveals)
        // Tout le monde voit la carte retournée, y compris l'invité.
        assertEquals(7, guest.online.mirror!!.reveals.single().value)
        // Mais pas le reste de la main de Chloé.
        assertTrue(guest.online.mirror!!.hands[2].all { it == 0 })
    }

    @Test
    fun lInviteGagneUnTrioEtRejoue() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)
        guest.online.claim(1)

        guest.online.send(TrioOp.LOW, 1) // 2 (sa propre carte)
        guest.online.send(TrioOp.CENTER, 0) // 2
        guest.online.send(TrioOp.CENTER, 1) // 2

        assertEquals(listOf(2), round.trios[1])
        assertEquals(1, round.current)
        assertEquals(listOf(2), guest.online.mirror!!.trios[1])
        assertEquals(2, guest.online.mirror!!.lastTrio)
    }

    @Test
    fun uneErreurEstTermineeParLInviteEtLeTourPasse() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)
        guest.online.claim(1)

        guest.online.send(TrioOp.LOW, 1) // 2
        guest.online.send(TrioOp.LOW, 0) // 3 : différente
        assertTrue(round.mismatch)
        assertTrue(guest.online.mirror!!.mismatch)

        guest.online.send(TrioOp.END)
        assertFalse(round.mismatch)
        assertEquals(2, round.current)
        assertEquals(2, guest.online.mirror!!.current)
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 0)
        guest.online.claim(1)

        guest.online.send(TrioOp.LOW, 0)

        assertTrue(round.reveals.isEmpty())
        assertEquals(0, round.current)
    }

    @Test
    fun sansPlaceLesActionsDeLInviteSontIgnorees() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)

        guest.online.send(TrioOp.LOW, 0)

        assertTrue(round.reveals.isEmpty())
    }

    @Test
    fun uneMancheFinieEstInscriteEtLaSuivanteSeLanceDepuisLInvite() {
        val (host, guest) = connected()
        val round = host.game.trioRound()
        round.load(listOf(listOf(7), listOf(7), listOf(7)), emptyList(), current = 1)
        host.online.publish()
        guest.online.claim(1)

        guest.online.send(TrioOp.LOW, 0)
        guest.online.send(TrioOp.LOW, 1)
        guest.online.send(TrioOp.LOW, 2)

        assertTrue(round.roundOver)
        assertTrue(round.settled)
        assertEquals(listOf(0, 1, 0), host.game.totals)
        assertTrue(guest.online.mirror!!.roundOver)
        assertEquals(1, guest.online.mirror!!.winner)

        guest.online.send(TrioOp.NEXT)
        assertEquals(2, round.roundNumber)
        assertFalse(round.roundOver)
    }

    @Test
    fun laMainDevoileeSeRecouvreQuandLInviteSeDeconnecte() {
        val (host, guest) = connected()
        val round = fixedTable(host, current = 1)
        guest.online.claim(1)
        round.show()
        assertTrue(round.shown)

        guest.transport.disconnect("host")

        assertTrue(host.online.remoteSeats.isEmpty())
        assertFalse(round.shown)
    }

    @Test
    fun leMessageDeTableSeCodeEtSeDecodeSansPerte() {
        val round = TrioRound(3) { }
        round.load(
            listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)),
            listOf(2, 2, 10),
            trios = listOf(listOf(12), emptyList(), listOf(11)),
            current = 1
        )
        round.flipHand(1, high = false)
        round.flipCenter(2)
        val view = SyncMessage.TrioMsg(TrioMessage.View(round.tableFor(1), seat = 1, taken = listOf(0)))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
    }

    @Test
    fun lesActionsDeLInviteSeCodentEtSeDecodentSansPerte() {
        val messages = listOf<TrioMessage>(TrioMessage.Claim(1), TrioMessage.Claim(-1)) +
            TrioOp.entries.map { TrioMessage.Do(it, target = 4) } +
            TrioMessage.Do(TrioOp.END)
        messages.forEach { message ->
            val wrapped = SyncMessage.TrioMsg(message)
            assertEquals(wrapped, SyncCodec.decode(SyncCodec.encode(wrapped)))
        }
    }
}
