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
 * Le Yaniv chacun sur son téléphone, testé sans téléphones : deux sessions reliées par un faux lien radio.
 * L'organisateur tient la partie ; l'invité joue « Ben » (place 1) et ne reçoit que sa propre main.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class YanivOnlineTest {

    private class Phone(selfId: String) {
        val game = ScoreViewModel()
        val transport = FakeTransport(selfId)
        val session = SyncSession(transport).also { it.attach(game) }
        val online = YanivOnline(session)
    }

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val club = FrenchCards.CLUB
    private val jokerA = 52

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == YANIV_ID }

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

    /** Une table connue : Ben (place 1) doit jouer ; il a une paire de 7, un 3 et deux figures ; la défausse est un 9 de trèfle. */
    private fun fixedTable(host: Phone): YanivRound = host.game.yanivRound().also {
        it.load(
            hands = listOf(
                listOf(c(spade, 1), c(spade, 2)),
                listOf(c(heart, 7), c(spade, 7), c(club, 3), c(diamond, 12), c(club, 13)),
                listOf(c(club, 5), c(club, 6))
            ),
            stock = listOf(c(diamond, 2), c(diamond, 3), c(diamond, 4)),
            lastDiscard = listOf(c(club, 9)),
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
        assertEquals(round.hands[1].sortedBy { YanivScoring.sortKey(it) }, guest.online.table!!.hand)
        // L'organisateur n'envoie jamais les mains des autres : leur taille seulement.
        assertEquals(round.hands.map { it.size }, guest.online.table!!.handSizes)
        assertTrue(guest.online.table!!.finalHands.isEmpty())
    }

    @Test
    fun lInviteDefausseUnePaireEtPiocheQuandCEstSonTour() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        guest.online.discard(listOf(c(heart, 7), c(spade, 7)))

        assertEquals(YanivPhase.DRAW, round.phase)
        assertTrue(guest.online.table!!.drawing)
        assertEquals(2, guest.online.table!!.pending.size)
        assertEquals(3, guest.online.table!!.hand.size)

        guest.online.drawFromStock()

        assertEquals(2, round.current)
        assertEquals(4, round.hands[1].size)
        assertTrue(round.hands[1].contains(c(diamond, 2)))
        assertEquals(2, guest.online.table!!.current)
        assertEquals(4, guest.online.table!!.hand.size)
        assertEquals(2, guest.online.table!!.lastDiscard.size)
        // La carte piochée n'est connue que de sa main : les autres ne voient que la taille.
        assertEquals(2, guest.online.table!!.stockSize)
    }

    @Test
    fun lInviteReprendUneCarteDeLaDerniereDefausse() {
        val (host, guest) = connected()
        val round = host.game.yanivRound()
        round.load(
            hands = listOf(listOf(c(spade, 1)), listOf(c(heart, 7), c(club, 3)), listOf(c(club, 5))),
            stock = listOf(c(diamond, 2)),
            lastDiscard = listOf(c(club, 4), c(club, 5), c(club, 6)),
            current = 1
        )
        host.online.publish()
        guest.online.claim(1)
        guest.online.discard(listOf(c(heart, 7)))

        // Au milieu d'une suite, la carte ne se reprend pas ; au bout, oui.
        guest.online.takeFromDiscard(c(club, 5))
        assertEquals(1, round.current)
        guest.online.takeFromDiscard(c(club, 4))

        assertEquals(2, round.current)
        assertTrue(round.hands[1].contains(c(club, 4)))
        assertEquals(listOf(c(heart, 7)), round.lastDiscard)
    }

    @Test
    fun uneDefausseRefuseeParLOrganisateurNeChangeRien() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)

        // Un 7 et un 3 ne vont pas ensemble ; une carte de la main d'Anna n'est pas à Ben.
        guest.online.discard(listOf(c(heart, 7), c(club, 3)))
        guest.online.discard(listOf(c(spade, 1)))

        assertEquals(YanivPhase.DISCARD, round.phase)
        assertEquals(1, round.current)
        assertEquals(5, round.hands[1].size)
    }

    @Test
    fun uneActionHorsDeSonTourEstIgnoree() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        round.load(
            hands = listOf(listOf(c(spade, 1)), listOf(c(heart, 7), c(club, 3)), listOf(c(club, 5))),
            stock = listOf(c(diamond, 2)),
            lastDiscard = listOf(c(club, 9)),
            current = 0
        )
        host.online.publish()
        guest.online.claim(1)

        guest.online.discard(listOf(c(heart, 7)))
        guest.online.drawFromStock()
        guest.online.callYaniv()

        assertEquals(0, round.current)
        assertEquals(2, round.hands[1].size)
        assertFalse(round.roundOver)
        assertEquals(1, round.stock.size)
    }

    @Test
    fun sansPlaceChoisieLInviteNeJoueRien() {
        val (host, guest) = connected()
        val round = fixedTable(host)

        guest.online.discard(listOf(c(heart, 7), c(spade, 7)))
        guest.online.callYaniv()

        assertEquals(YanivPhase.DISCARD, round.phase)
        assertEquals(1, round.current)
        assertEquals(5, round.hands[1].size)
    }

    @Test
    fun lInviteQuiAnnonceYanivAvecPeuDePointsFaitFinirLaMancheEtLesPointsSontInscrits() {
        val (host, guest) = connected()
        val round = host.game.yanivRound()
        round.load(
            hands = listOf(listOf(c(spade, 9)), listOf(c(heart, 2), jokerA, c(club, 1)), listOf(c(club, 13))),
            stock = listOf(c(diamond, 2)),
            lastDiscard = listOf(c(club, 9)),
            current = 1
        )
        host.online.publish()
        guest.online.claim(1)
        assertTrue(guest.online.table!!.canCallYaniv)

        guest.online.callYaniv()

        assertTrue(round.roundOver)
        assertTrue(round.settled)
        assertEquals(1, round.winner)
        val table = guest.online.table!!
        assertTrue(table.roundOver)
        assertEquals(1, table.winner)
        assertEquals(listOf(9, 0, 10), table.points)
        // En fin de manche, toutes les mains sont montrées.
        assertEquals(3, table.finalHands.size)
        assertEquals(listOf(c(spade, 9)), table.finalHands[0])
        assertEquals(listOf(9, 0, 10), host.game.scores[0].map { it.baseValue })
    }

    @Test
    fun yanivAnnonceAvecTropDePointsEstRefuse() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(1)
        assertFalse(guest.online.table!!.canCallYaniv)

        guest.online.callYaniv()

        assertFalse(round.roundOver)
        assertEquals(1, round.current)
    }

    @Test
    fun lInviteLanceLaMancheSuivanteApresLInscription() {
        val (host, guest) = connected()
        val round = host.game.yanivRound()
        round.load(
            hands = listOf(listOf(c(spade, 9)), listOf(c(heart, 2)), listOf(c(club, 13))),
            stock = listOf(c(diamond, 2)),
            lastDiscard = listOf(c(club, 9)),
            current = 1
        )
        host.online.publish()
        guest.online.claim(1)
        guest.online.callYaniv()
        assertEquals(1, round.roundNumber)

        guest.online.next()

        assertFalse(round.roundOver)
        assertEquals(2, round.roundNumber)
        assertEquals(2, guest.online.table!!.roundNumber)
        assertEquals(YanivScoring.HAND_SIZE, guest.online.table!!.hand.size)
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
    fun uneActionPasseeParUneAutrePlaceQueLaSienneNEstPasJouee() {
        val (host, guest) = connected()
        val round = fixedTable(host)
        guest.online.claim(2)

        // Ben (place 1) a la main, mais ce téléphone joue Chloé (place 2) : rien ne se passe.
        guest.online.discard(listOf(c(heart, 7), c(spade, 7)))

        assertEquals(YanivPhase.DISCARD, round.phase)
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
    fun leMessageDeTableSeCodeEtSeDecodeSansPerte() {
        val round = YanivRound(3) { }
        round.load(
            hands = listOf(listOf(c(spade, 1)), listOf(c(heart, 7), c(spade, 7), jokerA), listOf(c(club, 5))),
            stock = listOf(c(diamond, 2)),
            lastDiscard = listOf(c(club, 4), c(club, 5), c(club, 6)),
            current = 1
        )
        round.reveal()
        round.discard(listOf(c(heart, 7), c(spade, 7)))
        val table = round.tableFor(1)
        val view = SyncMessage.YanivMsg(YanivMessage.View(table, seat = 1, taken = listOf(0, 2)))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
        assertEquals(2, table.pending.size)
    }

    @Test
    fun leMessageDeFinDeMancheGardeLesMainsDeTous() {
        val round = YanivRound(3) { }
        round.load(
            hands = listOf(listOf(c(spade, 9)), listOf(c(heart, 2)), listOf(c(club, 13), jokerA)),
            stock = listOf(c(diamond, 2)),
            lastDiscard = listOf(c(club, 9)),
            current = 1
        )
        round.reveal()
        round.callYaniv()
        val table = round.tableFor(0)
        val view = SyncMessage.YanivMsg(YanivMessage.View(table, seat = 0, taken = emptyList()))

        assertEquals(view, SyncCodec.decode(SyncCodec.encode(view)))
        assertEquals(3, table.finalHands.size)
    }

    @Test
    fun lesActionsDeLInviteSeCodentEtSeDecodentSansPerte() {
        listOf(
            YanivMessage.Claim(2),
            YanivMessage.Claim(-1),
            YanivMessage.Discard(listOf(c(heart, 7), c(spade, 7))),
            YanivMessage.Discard(listOf(jokerA)),
            YanivMessage.DrawStock,
            YanivMessage.TakeDiscard(c(club, 4)),
            YanivMessage.CallYaniv,
            YanivMessage.Next
        ).forEach { message ->
            val wrapped = SyncMessage.YanivMsg(message)
            assertEquals(wrapped, SyncCodec.decode(SyncCodec.encode(wrapped)))
        }
    }
}
