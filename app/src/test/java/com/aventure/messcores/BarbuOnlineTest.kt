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
 * Barbu chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue une place et ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class BarbuOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = BarbuOnline(session)
    }

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == BARBU_ID }

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

    @Test
    fun lInviteRecoitLaTableMaisAucuneMainTantQuIlNAPasDePlace() {
        val (host, guest) = connected()
        val table = guest.online.table
        assertNotNull(table)
        assertEquals(-1, guest.online.mySeat)
        assertTrue(table!!.hand.isEmpty())
        assertEquals(3, table.handSizes.size)
        assertEquals(BarbuPhase.CHOOSING, table.phase)
        assertTrue(host.online.remoteSeats.isEmpty())
    }

    @Test
    fun lInviteChoisitSaPlaceEtNeRecoitQueSaMain() {
        val (host, guest) = connected()
        val round = host.game.barbuRound()

        guest.online.claim(1)

        assertEquals(setOf(1), host.online.remoteSeats)
        assertEquals(1, guest.online.mySeat)
        assertEquals(round.hands[1].toSet(), guest.online.table!!.hand.toSet())
        assertEquals(round.hands.map { it.size }, guest.online.table!!.handSizes)
    }

    @Test
    fun leDonneurInviteAnnonceSonContratEtJoueSaCarte() {
        val (host, guest) = connected()
        val round = host.game.barbuRound()
        // Anna est la donneuse de la première donne : l'invité prend sa place.
        guest.online.claim(0)
        assertEquals(0, round.declarer)

        guest.online.choose(BarbuContract.NO_HEARTS)

        assertEquals(BarbuContract.NO_HEARTS, round.contract)
        assertEquals(BarbuContract.NO_HEARTS, guest.online.table!!.contract)
        assertEquals(BarbuPhase.PLAYING, guest.online.table!!.phase)
        assertEquals(0, guest.online.table!!.current)

        val table = guest.online.table!!
        val card = table.hand.first { table.isPlayable(it) }
        guest.online.play(card)

        assertEquals(listOf(BarbuPlay(0, card)), round.trick)
        assertEquals(listOf(BarbuPlay(0, card)), guest.online.table!!.trick)
        assertEquals(1, round.current)
    }

    @Test
    fun unInviteQuiNEstPasDonneurNePeutPasAnnoncer() {
        val (host, guest) = connected()
        val round = host.game.barbuRound()
        guest.online.claim(1)

        guest.online.choose(BarbuContract.SALAD)

        assertNull(round.contract)
        assertEquals(BarbuPhase.CHOOSING, round.phase)
    }

    @Test
    fun unInviteNePeutPasJouerHorsDeSonTour() {
        val (host, guest) = connected()
        val round = host.game.barbuRound()
        round.chooseFor(0, BarbuContract.NO_TRICKS)
        host.online.publish()
        guest.online.claim(2)

        val card = guest.online.table!!.hand.first()
        guest.online.play(card)

        assertTrue(round.trick.isEmpty())
        assertEquals(card in round.hands[2], true)
    }

    @Test
    fun laPlaceDunAutreTelephoneNeSePrendPas() {
        val (host, guest) = connected()
        guest.online.claim(1)
        assertEquals(setOf(1), host.online.remoteSeats)
        guest.online.claim(-1)
        assertTrue(host.online.remoteSeats.isEmpty())
        assertEquals(-1, guest.online.mySeat)
    }

    @Test
    fun lesMessagesSeLisentApresUnAllerRetourEnJson() {
        val round = BarbuRound(3) { }
        round.load(
            BarbuContract.DOMINO,
            listOf(listOf(c(FrenchCards.SPADE, 7)), listOf(c(FrenchCards.HEART, 7)), listOf(c(FrenchCards.CLUB, 9))),
            current = 0
        )
        round.playFor(0, c(FrenchCards.SPADE, 7))
        val messages = listOf(
            BarbuMsg.View(round.tableFor(1), 1, listOf(1)),
            BarbuMsg.Claim(2),
            BarbuMsg.Choose(BarbuContract.SALAD.ordinal),
            BarbuMsg.Play(c(FrenchCards.CLUB, 4)),
            BarbuMsg.Collect,
            BarbuMsg.Next
        )
        messages.forEach { message ->
            assertEquals(message, barbuMsgFromJson(message.toJson()))
        }
        // Le message voyage aussi sous l'enveloppe de la connexion.
        val wrapped = SyncMessage.Barbu(BarbuMsg.Claim(1))
        assertFalse(wrapped.barbu is BarbuMsg.View)
    }
}
