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
 * Le Uno chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1) et ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class UnoOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = UnoOnline(session)
    }

    private val red = 0
    private val blue = 3

    private fun n(color: Int, number: Int): Int = color * UnoScoring.PER_COLOR + if (number == 0) 0 else 1 + (number - 1) * 2

    private val wild = UnoScoring.FIRST_WILD

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == UNO_ID }

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

    /** Une table connue : Ben (place 1) doit jouer sur un 5 rouge ; il a un 7 rouge, un 2 bleu et un joker. */
    private fun fixedTable(host: Phone): UnoRound = host.game.unoRound().also {
        it.load(
            hands = listOf(listOf(n(red, 1), n(red, 2)), listOf(n(red, 7), n(blue, 2), wild), listOf(n(blue, 4), n(blue, 5))),
            stock = listOf(n(blue, 9), n(blue, 8), n(blue, 6)),
            discard = listOf(n(red, 5)),
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
        assertEquals(round.hands[1].sortedBy { UnoScoring.sortKey(it) }, guest.online.table!!.hand)
        // L'organisateur n'envoie jamais les mains des autres : leur taille seulement.
        assertEquals(round.hands.map { it.size }, guest.online.table!!.handSizes)
    }

    @Test
    fun lInvitePoseUneCarteQuandCEstSonTourEtLOrganisateurLeVoit() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(n(red, 7))

        assertEquals(n(red, 7), round.top)
        assertEquals(2, round.current)
        assertEquals(n(red, 7), guest.online.table!!.top)
        assertEquals(2, guest.online.table!!.hand.size)
        assertEquals(2, guest.online.table!!.current)
    }

    @Test
    fun unJokerPoseParLInviteChoisitLaCouleur() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(wild, blue)

        assertEquals(blue, round.askedColor)
        assertEquals(blue, guest.online.table!!.askedColor)
        assertEquals(2, round.current)
    }

    @Test
    fun unJokerSansCouleurValideEstRefuse() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.play(wild, -1)

        assertEquals(n(red, 5), round.top)
        assertEquals(1, round.current)
        assertEquals(3, round.hands[1].size)
    }

    @Test
    fun uneCarteInjouableOuQuiNEstPasDansSaMainEstRefusee() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        // Le 2 bleu ne va pas sur le 5 rouge ; le 1 rouge est dans la main d'Anna.
        guest.online.play(n(blue, 2))
        guest.online.play(n(red, 1))

        assertEquals(n(red, 5), round.top)
        assertEquals(1, round.current)
        assertEquals(3, round.hands[1].size)
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        round.load(
            hands = listOf(listOf(n(red, 1)), listOf(n(red, 7), n(blue, 2)), listOf(n(blue, 4))),
            stock = listOf(n(blue, 9)),
            discard = listOf(n(red, 5)),
            current = 0
        )
        guest.online.claim(1)

        guest.online.play(n(red, 7))
        guest.online.draw()
        guest.online.pass()

        assertEquals(n(red, 5), round.top)
        assertEquals(0, round.current)
        assertEquals(2, round.hands[1].size)
        assertEquals(1, round.stock.size)
    }

    @Test
    fun sansPlaceChoisieLInviteNeJoueRien() {
        val (host, guest) = connected()
        val round = fixedTable(host)

        guest.online.play(n(red, 7))
        guest.online.draw()

        assertEquals(n(red, 5), round.top)
        assertEquals(1, round.current)
        assertEquals(3, round.stock.size)
    }

    @Test
    fun lInviteSansCarteJouablePiocheUneCartePuisPasse() {
        val (host, guest) = connected()
        val round = host.game.unoRound()
        round.load(
            hands = listOf(listOf(n(red, 1)), listOf(n(blue, 2), n(blue, 3)), listOf(n(blue, 4))),
            stock = listOf(n(blue, 9), n(blue, 8)),
            discard = listOf(n(red, 5)),
            current = 1
        )
        host.online.publish()
        guest.online.claim(1)
        assertTrue(guest.online.table!!.canDraw)

        guest.online.draw()

        assertEquals(3, round.hands[1].size)
        assertEquals(1, round.current)
        assertEquals(n(blue, 9), guest.online.table!!.drawnCard)
        assertTrue(guest.online.table!!.hand.contains(n(blue, 9)))
        assertFalse(guest.online.table!!.canDraw)

        guest.online.pass()

        assertEquals(2, round.current)
        assertEquals(-1, guest.online.table!!.drawnCard)
    }

    @Test
    fun lInviteQuiPoseSaDerniereCarteFaitFinirLaMancheEtLesPointsSontInscrits() {
        val (host, guest) = connected()
        val round = host.game.unoRound()
        round.load(
            hands = listOf(listOf(n(blue, 9)), listOf(n(red, 7)), listOf(n(blue, 4))),
            stock = emptyList(),
            discard = listOf(n(red, 5)),
            current = 1
        )
        host.online.publish()
        guest.online.claim(1)

        guest.online.play(n(red, 7))

        assertTrue(round.roundOver)
        assertTrue(round.settled)
        assertEquals(1, round.winner)
        val table = guest.online.table!!
        assertTrue(table.roundOver)
        assertEquals(1, table.winner)
        assertEquals(13, table.points[1])
        assertEquals(13, host.game.scores[0][1].baseValue)
    }

    @Test
    fun lInviteLanceLaMancheSuivanteApresLInscription() {
        val (host, guest) = connected()
        val round = host.game.unoRound()
        round.load(
            hands = listOf(listOf(n(blue, 9)), listOf(n(red, 7)), listOf(n(blue, 4))),
            stock = emptyList(),
            discard = listOf(n(red, 5)),
            current = 1
        )
        host.online.publish()
        guest.online.claim(1)
        guest.online.play(n(red, 7))
        assertEquals(1, round.roundNumber)

        guest.online.next()

        assertFalse(round.roundOver)
        assertEquals(2, round.roundNumber)
        assertEquals(2, guest.online.table!!.roundNumber)
        assertEquals(7, guest.online.table!!.hand.size)
    }

    @Test
    fun uneNouvellePlaceChoisieRemplaceLAncienne() {
        val (host, guest) = connected()
        fixedTable(host)
        guest.online.claim(1)

        guest.online.claim(2)

        assertEquals(setOf(2), host.online.remoteSeats)
        assertEquals(2, guest.online.mySeat)
    }

    @Test
    fun uneCartePasseeParUneAutrePlaceQueLaSienneNEstPasJouee() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(2)

        // Ben (place 1) a la main, mais ce téléphone joue Chloé (place 2) : rien ne se passe.
        guest.online.play(n(blue, 4))

        assertEquals(n(red, 5), round.top)
        assertEquals(1, round.current)
    }

    @Test
    fun uneAnnoncePourUnePlaceInexistanteNeChangeRien() {
        val (host, guest) = connected()
        fixedTable(host)
        guest.online.claim(1)

        guest.online.claim(5)

        assertEquals(setOf(1), host.online.remoteSeats)
        assertEquals(1, guest.online.mySeat)
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
    fun lInviteQuiQuitteSaPlaceLaLibere() {
        val (host, guest) = connected()
        fixedTable(host)
        guest.online.claim(1)

        guest.online.claim(-1)

        assertTrue(host.online.remoteSeats.isEmpty())
        assertEquals(-1, guest.online.mySeat)
        assertTrue(guest.online.table!!.hand.isEmpty())
    }

    @Test
    fun laVueDUnSpectateurNeMontreAucuneMain() {
        val round = UnoRound(3) { }
        val table = round.tableFor(-1)

        assertTrue(table.hand.isEmpty())
        assertEquals(-1, table.drawnCard)
        assertFalse(table.canDraw)
        assertFalse(table.mustPass)
        assertEquals(List(3) { UnoScoring.HAND_SIZE }, table.handSizes)
    }

    @Test
    fun leMessageDeTableSeCodeEtSeDecodeSansPerte() {
        val round = UnoRound(4) { }
        round.load(
            hands = listOf(listOf(n(red, 1)), listOf(n(red, 7), wild), listOf(n(blue, 4)), listOf(n(blue, 5))),
            stock = listOf(n(blue, 9)),
            discard = listOf(n(blue, 2), n(red, 3), UnoScoring.FIRST_WILD_FOUR),
            current = 1,
            askedColor = red
        )
        round.reveal()
        val table = round.tableFor(1)
        val view = SyncMessage.UnoMsg(UnoMessage.View(table, seat = 1, taken = listOf(0, 3)))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
        assertFalse(table.hand.isEmpty())
    }

    @Test
    fun leMessageDUneCarteSpecialeGardeSonEvenement() {
        val round = UnoRound(3) { }
        round.load(
            hands = listOf(listOf(n(red, 1), UnoScoring.FIRST_WILD_FOUR), listOf(n(blue, 2)), listOf(n(blue, 4))),
            stock = List(6) { n(blue, 9) },
            discard = listOf(n(blue, 5)),
            current = 0
        )
        round.reveal()
        assertTrue(round.play(UnoScoring.FIRST_WILD_FOUR, red))
        val table = round.tableFor(2)
        assertNotNull(table.lastEvent)
        val view = SyncMessage.UnoMsg(UnoMessage.View(table, seat = 2, taken = emptyList()))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
    }

    @Test
    fun lesActionsDeLInviteSeCodentEtSeDecodentSansPerte() {
        listOf(
            UnoMessage.Claim(2),
            UnoMessage.Claim(-1),
            UnoMessage.Play(n(red, 7), -1),
            UnoMessage.Play(wild, blue),
            UnoMessage.Draw,
            UnoMessage.Pass,
            UnoMessage.Next
        ).forEach { message ->
            val wrapped = SyncMessage.UnoMsg(message)
            assertEquals(wrapped, SyncCodec.decode(SyncCodec.encode(wrapped)))
        }
    }
}
