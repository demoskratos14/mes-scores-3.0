package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Les règles du Rami (combinaisons, ajouts, jokers) et le déroulement d'une manche, sans téléphone ni écran. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class RamiRoundTest {

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val club = FrenchCards.CLUB
    private val joker = 104
    private val joker2 = 105

    /** La carte de couleur [suit] et de valeur [rank], du premier jeu (0) ou du second (1). */
    private fun c(suit: Int, rank: Int, deck: Int = 0): Int = FrenchCards.card(suit, rank) + deck * FrenchCards.DECK

    @Test
    fun uneSuiteEtUnBrelanSontDesCombinaisons() {
        val run = RamiScoring.meldOf(listOf(c(heart, 5), c(heart, 6), c(heart, 7)))
        assertNotNull(run)
        assertTrue(run!!.run)
        assertEquals(5, run.key)
        assertEquals(18, RamiScoring.meldPoints(run))
        val set = RamiScoring.meldOf(listOf(c(spade, 9), c(heart, 9), c(diamond, 9)))
        assertNotNull(set)
        assertFalse(set!!.run)
        assertEquals(27, RamiScoring.meldPoints(set))
    }

    @Test
    fun l_asSePlaceAvantLeDeuxOuApresLeRoiMaisPasLesDeux() {
        assertEquals(1, RamiScoring.meldOf(listOf(c(heart, 1), c(heart, 2), c(heart, 3)))!!.key)
        assertEquals(12, RamiScoring.meldOf(listOf(c(heart, 12), c(heart, 13), c(heart, 1)))!!.key)
        assertNull(RamiScoring.meldOf(listOf(c(heart, 13), c(heart, 1), c(heart, 2))))
    }

    @Test
    fun desCombinationsRefusees() {
        // Même couleur deux fois dans un brelan.
        assertNull(RamiScoring.meldOf(listOf(c(spade, 5), c(heart, 5), c(heart, 5, 1))))
        // Cinq cartes dans un carré.
        assertNull(RamiScoring.meldOf(listOf(c(spade, 5), c(heart, 5), c(diamond, 5), c(club, 5), joker)))
        // Moins de 2 cartes sans joker.
        assertNull(RamiScoring.meldOf(listOf(c(heart, 5), joker, joker2)))
        // Deux cartes seulement.
        assertNull(RamiScoring.meldOf(listOf(c(heart, 5), c(heart, 6))))
    }

    @Test
    fun leJokerRemplaceUneCarteOuProlongeLaSuite() {
        val gap = RamiScoring.meldOf(listOf(c(heart, 5), c(heart, 7), joker))!!
        assertTrue(gap.run)
        assertEquals(5, gap.key)
        assertEquals(joker, gap.cards[1])
        val end = RamiScoring.meldOf(listOf(c(heart, 5), c(heart, 6), joker))!!
        assertEquals(joker, end.cards[2])
    }

    @Test
    fun laPoseEstDecoupeeEnCombinaisons() {
        val cards = listOf(c(heart, 5), c(heart, 6), c(heart, 7), c(spade, 9), c(heart, 9), c(diamond, 9))
        val parts = RamiScoring.layout(cards)
        assertNotNull(parts)
        assertEquals(2, parts!!.size)
        assertEquals(45, parts.sumOf { RamiScoring.meldPoints(it) })
        assertNull(RamiScoring.layout(listOf(c(heart, 5), c(heart, 6), c(diamond, 9))))
    }

    @Test
    fun laPremierePoseDoitValoirAssezDePoints() {
        val small = listOf(c(heart, 2), c(heart, 3), c(heart, 4))
        assertEquals(RamiCheck.TOO_FEW_POINTS, RamiScoring.checkMeld(small, opened = false))
        assertEquals(RamiCheck.OK, RamiScoring.checkMeld(small, opened = true))
        val big = listOf(c(heart, 10), c(heart, 11), c(heart, 12), c(heart, 13))
        assertEquals(RamiCheck.OK, RamiScoring.checkMeld(big, opened = false))
        assertEquals(RamiCheck.INVALID, RamiScoring.checkMeld(listOf(c(heart, 10), c(spade, 2), c(club, 4)), opened = true))
    }

    @Test
    fun onCompleteUneSuiteUnBrelanEtOnReprendUnJoker() {
        val run = RamiScoring.meldOf(listOf(c(heart, 5), c(heart, 6), c(heart, 7)))!!
        assertEquals(5, RamiScoring.attach(run, c(heart, 8))!!.key)
        assertEquals(4, RamiScoring.attach(run, c(heart, 4))!!.key)
        assertNull(RamiScoring.attach(run, c(spade, 8)))
        val set = RamiScoring.meldOf(listOf(c(spade, 5), c(heart, 5), c(diamond, 5)))!!
        assertEquals(4, RamiScoring.attach(set, c(club, 5))!!.cards.size)
        assertNull(RamiScoring.attach(set, c(spade, 5, 1)))
        val withJoker = RamiScoring.meldOf(listOf(c(heart, 5), joker, c(heart, 7)))!!
        val swapped = RamiScoring.swapJoker(withJoker, c(heart, 6))
        assertNotNull(swapped)
        assertEquals(joker, swapped!!.second)
        assertFalse(swapped.first.cards.contains(joker))
        assertNull(RamiScoring.swapJoker(withJoker, c(heart, 8)))
    }

    // Une manche à 2 joueurs, tables connues : Anna (place 0) joue.
    private fun round(hands: List<List<Int>>, stock: List<Int>, discards: List<Int>, opened: List<Boolean> = listOf(false, false)): RamiRound =
        RamiRound(2).also { it.load(hands = hands, stock = stock, discards = discards, current = 0, opened = opened) }

    @Test
    fun laDonneDistribue13CartesEtUneDefausse() {
        val round = RamiRound(3)
        assertTrue(round.hands.all { it.size == RamiScoring.HAND_SIZE })
        assertEquals(1, round.discards.size)
        assertEquals(RamiScoring.DECK, round.hands.sumOf { it.size } + round.stock.size + round.discards.size)
        assertEquals(RamiScoring.DECK, (round.hands.flatten() + round.stock + round.discards).toSet().size)
    }

    @Test
    fun onPiocheAvantDePoserOuDeDefausser() {
        val round = round(
            hands = listOf(listOf(c(heart, 10), c(heart, 11), c(heart, 12), c(spade, 2)), listOf(c(club, 4))),
            stock = listOf(c(heart, 13), c(club, 9)),
            discards = listOf(c(diamond, 3))
        )
        round.reveal()
        assertFalse(round.discard(c(spade, 2)))
        assertFalse(round.meld(listOf(c(heart, 10), c(heart, 11), c(heart, 12))))
        assertTrue(round.drawFromStock())
        assertFalse(round.drawFromStock())
        assertEquals(5, round.currentHand.size)
    }

    @Test
    fun onOuvreAvecAssezDePointsPuisOnDefausse() {
        val round = round(
            hands = listOf(listOf(c(heart, 10), c(heart, 11), c(heart, 12), c(spade, 2)), listOf(c(club, 4))),
            stock = listOf(c(heart, 13), c(club, 9)),
            discards = listOf(c(diamond, 3))
        )
        round.reveal()
        assertTrue(round.drawFromStock())
        assertTrue(round.meld(listOf(c(heart, 10), c(heart, 11), c(heart, 12), c(heart, 13))))
        assertTrue(round.opened[0])
        assertEquals(1, round.melds.size)
        assertTrue(round.discard(c(spade, 2)))
        assertEquals(1, round.current)
        assertEquals(c(spade, 2), round.topDiscard)
        assertEquals(RamiPhase.DRAW, round.phase)
        assertFalse(round.revealed)
    }

    @Test
    fun uneOuvertureTropFaibleEstRefusee() {
        val round = round(
            hands = listOf(listOf(c(heart, 2), c(heart, 3), c(heart, 4), c(spade, 9)), listOf(c(club, 4))),
            stock = listOf(c(club, 9)),
            discards = listOf(c(diamond, 13))
        )
        round.reveal()
        assertTrue(round.takeDiscard())
        assertFalse(round.meld(listOf(c(heart, 2), c(heart, 3), c(heart, 4))))
        assertEquals(5, round.currentHand.size)
    }

    @Test
    fun onNeCompletePasLaTableSansAvoirOuvert() {
        val meld = RamiScoring.meldOf(listOf(c(club, 5), c(club, 6), c(club, 7)))!!
        val round = RamiRound(2)
        round.load(
            hands = listOf(listOf(c(club, 8), c(spade, 2)), listOf(c(heart, 3))),
            stock = listOf(c(heart, 4)),
            discards = listOf(c(diamond, 3)),
            melds = listOf(meld)
        )
        round.reveal()
        round.drawFromStock()
        assertFalse(round.attach(listOf(c(club, 8)), 0))
    }

    @Test
    fun ouvertureAjoutJokerEtFinDeManche() {
        val meld = RamiScoring.meldOf(listOf(c(club, 5), joker, c(club, 7)))!!
        val round = RamiRound(2)
        round.load(
            hands = listOf(listOf(c(club, 6), c(club, 8)), listOf(c(heart, 3), c(heart, 9))),
            stock = listOf(c(spade, 12)),
            discards = listOf(c(diamond, 3)),
            melds = listOf(meld),
            opened = listOf(true, false)
        )
        round.reveal()
        assertTrue(round.takeDiscard())
        // Le 6 de trèfle reprend le joker, qui rejoint la main.
        assertTrue(round.swapJoker(c(club, 6), 0))
        assertTrue(round.currentHand.contains(joker))
        // Le 8 de trèfle se pose au bout de la suite.
        assertTrue(round.attach(listOf(c(club, 8)), 0))
        assertEquals(4, round.melds[0].cards.size)
        // Reste le joker et le 3 de carreau : on défausse le joker, puis la dernière carte.
        assertTrue(round.discard(joker))
        assertFalse(round.roundOver)
    }

    @Test
    fun celuiQuiSeDebarrasseDeSesCartesFinitLaManche() {
        val meld = RamiScoring.meldOf(listOf(c(club, 5), c(club, 6), c(club, 7)))!!
        val round = RamiRound(2)
        round.load(
            hands = listOf(listOf(c(club, 8)), listOf(c(heart, 3), c(heart, 13), joker)),
            stock = listOf(c(spade, 12)),
            discards = listOf(c(diamond, 3)),
            melds = listOf(meld),
            opened = listOf(true, false)
        )
        round.reveal()
        assertTrue(round.takeDiscard())
        assertTrue(round.attach(listOf(c(club, 8)), 0))
        assertFalse(round.roundOver)
        assertTrue(round.discard(c(diamond, 3)))
        assertTrue(round.roundOver)
        assertEquals(0, round.winner)
        assertEquals(listOf(0, 3 + 10 + RamiScoring.JOKER_HAND_POINTS), round.roundPoints())
    }

    @Test
    fun laPiocheVideSeRemelangeAvecLaDefausse() {
        val round = round(
            hands = listOf(listOf(c(heart, 3)), listOf(c(club, 4))),
            stock = emptyList(),
            discards = listOf(c(diamond, 3), c(diamond, 4), c(diamond, 5))
        )
        round.reveal()
        assertTrue(round.canDrawFromStock)
        assertTrue(round.drawFromStock())
        assertEquals(1, round.discards.size)
        assertEquals(c(diamond, 5), round.topDiscard)
        assertEquals(1, round.stock.size)
    }

    @Test
    fun uneVueNeMontrePasLaMainDesAutres() {
        val round = round(
            hands = listOf(listOf(c(heart, 3), c(heart, 4)), listOf(c(club, 4), c(club, 5))),
            stock = listOf(c(spade, 2)),
            discards = listOf(c(diamond, 3))
        )
        assertEquals(listOf(c(club, 4), c(club, 5)), round.tableFor(1).hand.sortedBy { it })
        assertTrue(round.tableFor(-1).hand.isEmpty())
        assertEquals(listOf(2, 2), round.tableFor(0).handSizes)
        assertTrue(round.tableFor(0).finalHands.isEmpty())
    }

    @Test
    fun lesJokersEtLesCartesAutresQueLeurPremierJeuSeLisentBien() {
        assertTrue(RamiScoring.isJoker(104))
        assertFalse(RamiScoring.isJoker(103))
        assertEquals(RamiScoring.base(c(heart, 5, 1)), c(heart, 5))
        assertEquals(RamiScoring.JOKER_HAND_POINTS, RamiScoring.points(joker))
        assertEquals(11, RamiScoring.points(c(club, 1)))
        assertEquals(10, RamiScoring.points(c(club, 12)))
    }
}
