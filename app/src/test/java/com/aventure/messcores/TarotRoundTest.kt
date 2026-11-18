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

/** Tarot jouable : cartes, cartes jouables, plis, enchères, chien et écart, comptage, Excuse, tableau des scores et messages. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class TarotRoundTest {

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART

    private fun c(suit: Int, rank: Int): Int = TarotCards.card(suit, rank)

    /** L'atout [number] (1 à 21). */
    private fun t(number: Int): Int = TarotCards.SUIT_CARDS + number - 1

    private val excuse = TarotCards.EXCUSE

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == TAROT_ID }

    private fun plays(vararg pairs: Pair<Int, Int>): List<TarotPlay> = pairs.map { TarotPlay(it.first, it.second) }

    // ---------- Les cartes ----------

    @Test
    fun leJeuVaut91PointsEtALeTroisBouts() {
        assertEquals(182, (0 until TarotCards.DECK).sumOf { TarotCards.halfPoints(it) })
        assertEquals(3, (0 until TarotCards.DECK).count { TarotCards.isOudler(it) })
        assertEquals(9, TarotCards.halfPoints(c(spade, 14)))
        assertEquals(7, TarotCards.halfPoints(c(spade, 13)))
        assertEquals(5, TarotCards.halfPoints(c(spade, 12)))
        assertEquals(3, TarotCards.halfPoints(c(spade, 11)))
        assertEquals(1, TarotCards.halfPoints(c(spade, 10)))
        assertEquals(1, TarotCards.halfPoints(t(20)))
        assertEquals(9, TarotCards.halfPoints(t(21)))
        assertEquals(9, TarotCards.halfPoints(t(1)))
    }

    @Test
    fun laDonneDistribueToutLeJeu() {
        for (count in 3..5) {
            val round = TarotRound(count) { }
            val table = round.tableFor(0)
            assertEquals(TarotRules.handSize(count), table.hand.size)
            assertEquals(TarotCards.DECK, (0 until count).sumOf { round.tableFor(it).hand.size } + TarotRules.dogSize(count))
            // Un joueur ne voit que sa main.
            assertTrue(round.tableFor(-1).hand.isEmpty())
        }
        assertEquals(24, TarotRules.handSize(3))
        assertEquals(18, TarotRules.handSize(4))
        assertEquals(15, TarotRules.handSize(5))
    }

    @Test
    fun unPetitSecFaitRefaireLaDonne() {
        assertTrue(TarotRules.isPetitSec(listOf(c(spade, 3), t(1), c(heart, 2))))
        assertFalse(TarotRules.isPetitSec(listOf(t(1), t(5))))
        assertFalse(TarotRules.isPetitSec(listOf(t(7))))
    }

    // ---------- Les cartes jouables et le gagnant ----------

    @Test
    fun onFournitLaCouleurDemandee() {
        val hand = listOf(c(spade, 5), c(heart, 9), t(10))
        val trick = plays(0 to c(spade, 3))
        assertTrue(TarotRules.canPlay(c(spade, 5), hand, trick))
        assertFalse(TarotRules.canPlay(c(heart, 9), hand, trick))
        assertFalse(TarotRules.canPlay(t(10), hand, trick))
    }

    @Test
    fun sansLaCouleurOnCoupeEtOnMonte() {
        val trick = plays(0 to c(spade, 3), 1 to t(12))
        // Pas de pique : il faut un atout, et plus fort que le 12 quand on le peut.
        val handHigh = listOf(c(heart, 9), t(5), t(15))
        assertFalse(TarotRules.canPlay(c(heart, 9), handHigh, trick))
        assertFalse(TarotRules.canPlay(t(5), handHigh, trick))
        assertTrue(TarotRules.canPlay(t(15), handHigh, trick))
        // Aucun atout plus fort : n'importe quel atout.
        val handLow = listOf(c(heart, 9), t(3), t(5))
        assertTrue(TarotRules.canPlay(t(3), handLow, trick))
        assertFalse(TarotRules.canPlay(c(heart, 9), handLow, trick))
        // Sans atout : on se défausse de ce qu'on veut.
        val noTrump = listOf(c(heart, 9), c(heart, 2))
        assertTrue(TarotRules.canPlay(c(heart, 2), noTrump, trick))
    }

    @Test
    fun aLAtoutOnMonteAussi() {
        val trick = plays(0 to t(8))
        val hand = listOf(c(spade, 5), t(4), t(9))
        assertFalse(TarotRules.canPlay(c(spade, 5), hand, trick))
        assertFalse(TarotRules.canPlay(t(4), hand, trick))
        assertTrue(TarotRules.canPlay(t(9), hand, trick))
        // Sans atout, on joue ce qu'on veut.
        assertTrue(TarotRules.canPlay(c(spade, 5), listOf(c(spade, 5), c(heart, 1)), trick))
    }

    @Test
    fun lExcuseSeJoueToujoursEtLaCarteSuivanteDonneLaCouleur() {
        val hand = listOf(excuse, c(spade, 5), c(heart, 2))
        assertTrue(TarotRules.canPlay(excuse, hand, plays(0 to c(spade, 3))))
        // L'Excuse ouvre : le joueur suivant n'est pas tenu à une couleur.
        assertTrue(TarotRules.canPlay(c(heart, 2), hand, plays(0 to excuse)))
        // Mais le suivant du suivant doit fournir la couleur de la première vraie carte.
        assertFalse(TarotRules.canPlay(c(heart, 2), hand, plays(0 to excuse, 1 to c(spade, 8))))
        assertTrue(TarotRules.canPlay(c(spade, 5), hand, plays(0 to excuse, 1 to c(spade, 8))))
    }

    @Test
    fun leGagnantDuPli() {
        // Le plus gros atout.
        assertEquals(2, TarotRules.trickWinner(plays(0 to c(spade, 14), 1 to t(3), 2 to t(9), 3 to c(spade, 2))))
        // Sans atout : la plus forte carte de la couleur demandée.
        assertEquals(3, TarotRules.trickWinner(plays(0 to c(spade, 4), 1 to c(heart, 14), 2 to c(spade, 13), 3 to c(spade, 14))))
        // L'Excuse ne gagne jamais.
        assertEquals(1, TarotRules.trickWinner(plays(0 to excuse, 1 to c(heart, 2), 2 to c(spade, 14))))
        assertEquals(2, TarotRules.trickWinner(plays(0 to c(heart, 5), 1 to excuse, 2 to t(1))))
    }

    // ---------- Les poignées ----------

    @Test
    fun lesSeuilsDesPoignees() {
        assertEquals(0, TarotRules.handfulLevel(4, 9))
        assertEquals(1, TarotRules.handfulLevel(4, 10))
        assertEquals(2, TarotRules.handfulLevel(4, 13))
        assertEquals(3, TarotRules.handfulLevel(4, 15))
        assertEquals(1, TarotRules.handfulLevel(5, 8))
        assertEquals(1, TarotRules.handfulLevel(3, 13))
        assertEquals(20, TarotRules.handfulPoints(1))
        assertEquals(40, TarotRules.handfulPoints(3))
    }

    // ---------- Les enchères, le chien et l'écart ----------

    @Test
    fun lesEncheresSeSuiventEtLeChienEstMontre() {
        // Mélange neutre : le joueur 3 a 16 atouts, le chien est 72 à 77 (dont le 21 et l'Excuse).
        val round = TarotRound(4) { }
        assertEquals(1, round.current)
        assertEquals(TarotPhase.BIDDING, round.phase)

        assertFalse(round.bidFor(2, TarotBid.PETITE)) // pas son tour
        assertTrue(round.bidFor(1, TarotBid.PASS))
        assertTrue(round.bidFor(2, TarotBid.PETITE))
        assertFalse(round.bidFor(3, TarotBid.PETITE)) // il faut monter
        assertTrue(round.bidFor(3, TarotBid.GARDE))
        assertTrue(round.bidFor(0, TarotBid.PASS))

        assertEquals(3, round.taker)
        assertEquals(TarotBid.GARDE, round.contract)
        assertEquals(TarotPhase.DISCARD, round.phase)
        assertEquals(3, round.current)
        val table = round.tableFor(3)
        assertEquals(6, table.dog.size)
        assertEquals(24, table.hand.size)
        // Les autres voient le chien mais pas la main du preneur.
        assertEquals(6, round.tableFor(0).dog.size)
        assertTrue(round.tableFor(0).hand.size == 18)
    }

    @Test
    fun toutLeMondePasseEtLeDonneurChange() {
        val round = TarotRound(4) { }
        for (seat in listOf(1, 2, 3, 0)) assertTrue(round.bidFor(seat, TarotBid.PASS))
        assertTrue(round.passedOut)
        assertEquals(TarotPhase.BIDDING, round.phase)
        assertEquals(1, round.dealer)
        assertEquals(2, round.current)
        assertEquals(1, round.roundNumber)
    }

    @Test
    fun lEcartRefuseLesRoisLesBoutsEtLesAtoutsInutiles() {
        val round = TarotRound(4) { }
        round.bidFor(1, TarotBid.PASS)
        round.bidFor(2, TarotBid.PASS)
        round.bidFor(3, TarotBid.GARDE)
        round.bidFor(0, TarotBid.PASS)
        val hand = round.tableFor(3).hand
        // Cette main n'a qu'une carte ordinaire non roi (la dame de trèfle) : un écart d'atouts doit l'inclure.
        val queenOfClubs = c(FrenchCards.CLUB, 13)
        val king = c(FrenchCards.CLUB, 14)
        assertTrue(queenOfClubs in hand && king in hand)
        assertFalse(round.discardFor(3, listOf(king, t(2), t(3), t(4), t(5), t(6)), false))
        assertFalse(round.discardFor(3, listOf(queenOfClubs, t(1), t(3), t(4), t(5), t(6)), false)) // le petit est un bout
        assertFalse(round.discardFor(3, listOf(t(2), t(3), t(4), t(5), t(6), t(7)), false)) // la dame de trèfle reste
        assertFalse(round.discardFor(3, listOf(queenOfClubs, t(2)), false)) // pas le bon nombre
        assertEquals(TarotPhase.DISCARD, round.phase)

        assertTrue(round.discardFor(3, listOf(queenOfClubs, t(2), t(3), t(4), t(5), t(6)), false))
        assertEquals(TarotPhase.PLAYING, round.phase)
        assertEquals(18, round.tableFor(3).hand.size)
        // Les atouts écartés sont montrés à tous.
        assertEquals(listOf(t(2), t(3), t(4), t(5), t(6)), round.tableFor(0).discardedTrumps)
        // Sans chelem, le joueur après le donneur ouvre.
        assertEquals(1, round.current)
    }

    @Test
    fun unChelemAnnonceFaitOuvrirLePreneur() {
        val round = TarotRound(4) { }
        round.bidFor(1, TarotBid.PASS)
        round.bidFor(2, TarotBid.PASS)
        round.bidFor(3, TarotBid.GARDE)
        round.bidFor(0, TarotBid.PASS)
        assertTrue(round.discardFor(3, listOf(c(FrenchCards.CLUB, 13), t(2), t(3), t(4), t(5), t(6)), true))
        assertTrue(round.slamAnnounced)
        assertEquals(3, round.current)
    }

    @Test
    fun aCinqLePreneurAppelleUnRoi() {
        val round = TarotRound(5) { }
        for (seat in listOf(1, 2, 3, 4)) round.bidFor(seat, TarotBid.PASS)
        assertTrue(round.bidFor(0, TarotBid.PETITE))
        assertEquals(TarotPhase.CALLING, round.phase)
        assertEquals(0, round.taker)
        val options = round.callOptions()
        assertEquals(4, options.size)
        assertTrue(options.all { TarotCards.isKing(it) })
        assertFalse(round.callFor(0, t(5)))
        assertTrue(round.callFor(0, options[0]))
        assertEquals(TarotPhase.DISCARD, round.phase)
        // À 5, le chien n'a que 3 cartes.
        assertEquals(3, round.tableFor(0).dog.size)
        assertEquals(-1, round.partner)
    }

    // ---------- Une donne jouée jusqu'au bout ----------

    /** 3 joueurs à 2 cartes (donne d'essai) : le joueur 1 prend en garde sans le chien ; il ouvre. */
    private fun miniRound(h0: List<Int>, h1: List<Int>, h2: List<Int>): TarotRound {
        val round = TarotRound(3) { }
        round.load(listOf(h0, h1, h2), dog = emptyList(), dealer = 0)
        assertTrue(round.bidFor(1, TarotBid.GARDE_SANS))
        assertTrue(round.bidFor(2, TarotBid.PASS))
        assertTrue(round.bidFor(0, TarotBid.PASS))
        assertEquals(TarotPhase.DISCARD, round.phase)
        assertEquals(0, round.tableFor(1).dog.size)
        assertTrue(round.discardFor(1, emptyList(), false))
        assertEquals(1, round.current)
        return round
    }

    @Test
    fun unContratChuteSeCompteEtSInscritAuTableau() {
        val round = miniRound(
            h0 = listOf(c(spade, 3), t(5)),
            h1 = listOf(c(spade, 14), t(21)),
            h2 = listOf(c(spade, 2), t(1))
        )
        // Pli 1 : le roi de pique l'emporte.
        assertTrue(round.playFor(1, c(spade, 14)))
        assertFalse(round.playFor(0, c(spade, 3))) // pas son tour
        assertTrue(round.playFor(2, c(spade, 2)))
        assertTrue(round.playFor(0, c(spade, 3)))
        assertTrue(round.trickDone)
        assertEquals(1, round.trickWinner)
        assertFalse(round.collectFor(0))
        assertTrue(round.collectFor(1))
        // Pli 2 : le 21 l'emporte sur le petit ; le petit n'est pas « au bout » (joué par la défense).
        assertTrue(round.playFor(1, t(21)))
        assertTrue(round.playFor(2, t(1)))
        assertTrue(round.playFor(0, t(5)))
        assertTrue(round.collect())
        assertTrue(round.roundOver)

        val outcome = round.outcome!!
        // Le preneur a tout ramassé : le roi (4,5), deux petites (1), le 21 (4,5), le petit (4,5) et le 5 (0,5) = 15 points, 2 bouts.
        assertEquals(30, outcome.attackHalf)
        assertEquals(15, outcome.points)
        assertEquals(2, outcome.bouts)
        assertEquals(41, outcome.required)
        assertFalse(outcome.success)
        assertEquals(-26, outcome.difference)
        assertEquals(0, outcome.petitAuBout)
        assertFalse(outcome.defenseSlam)
        // (25 + 26) × 4 de garde sans, payé par les deux défenseurs au preneur.
        assertEquals(-204, outcome.unit)
        assertEquals(listOf(204, -408, 204), outcome.deltas)

        val vm = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben", "Cléo"), rules()) }
        assertTrue(round.settle(vm))
        assertFalse(round.settle(vm))
        assertEquals(1, vm.teamRounds.size)
        assertEquals(listOf(204, -408, 204), vm.totals)
        assertEquals(2, vm.teamRounds[0].tarotInput?.multiplierIndex)
        assertEquals(15, vm.teamRounds[0].tarotInput?.points)

        // La donne suivante change de donneur.
        assertTrue(round.nextRound())
        assertEquals(2, round.roundNumber)
        assertEquals(1, round.dealer)
        assertFalse(round.roundOver)
        assertEquals(TarotPhase.BIDDING, round.phase)
    }

    @Test
    fun lExcuseJoueeDansUnPliAdverseEstEchangeeContreUneCarteBasse() {
        val round = miniRound(
            h0 = listOf(c(spade, 3), t(5), c(spade, 6)),
            h1 = listOf(c(spade, 2), t(21), c(spade, 4)),
            h2 = listOf(c(spade, 9), excuse, c(spade, 5))
        )
        // Pli 1 : la défense (joueur 2, avec le 9) le gagne.
        assertTrue(round.playFor(1, c(spade, 2)))
        assertTrue(round.playFor(2, c(spade, 9)))
        assertTrue(round.playFor(0, c(spade, 3)))
        assertEquals(2, round.trickWinner)
        assertTrue(round.collectFor(2))
        // Pli 2 : la défense ouvre par l'Excuse ; le 21 du preneur l'emporte.
        assertTrue(round.playFor(2, excuse))
        assertTrue(round.playFor(0, t(5)))
        assertTrue(round.playFor(1, t(21)))
        assertEquals(1, round.trickWinner)
        assertTrue(round.collect())
        // Pli 3 : la défense (joueur 0, avec le 6) le gagne.
        assertTrue(round.playFor(1, c(spade, 4)))
        assertTrue(round.playFor(2, c(spade, 5)))
        assertTrue(round.playFor(0, c(spade, 6)))
        assertEquals(0, round.trickWinner)
        assertTrue(round.collect())

        val outcome = round.outcome!!
        // L'Excuse retourne à la défense, qui rend une carte à 0,5 point au preneur : le 21 (4,5), le 5 (0,5) et cette carte = 11 demi-points.
        assertEquals(11, outcome.attackHalf)
        assertEquals(1, outcome.bouts)
    }

    @Test
    fun uneExcuseSansRienADonnerResteAuCampAdverse() {
        val round = miniRound(
            h0 = listOf(c(spade, 3), t(5)),
            h1 = listOf(c(spade, 14), t(21)),
            h2 = listOf(c(spade, 2), excuse)
        )
        assertTrue(round.playFor(1, c(spade, 14)))
        assertTrue(round.playFor(2, excuse))
        assertTrue(round.playFor(0, c(spade, 3)))
        assertTrue(round.collectFor(1))
        assertTrue(round.playFor(1, t(21)))
        assertTrue(round.playFor(2, c(spade, 2)))
        assertTrue(round.playFor(0, t(5)))
        assertTrue(round.collect())
        val outcome = round.outcome!!
        // La défense n'a aucun pli, donc rien à échanger : le preneur garde l'Excuse (2 bouts).
        assertEquals(2, outcome.bouts)
        assertEquals(30, outcome.attackHalf)
    }

    // ---------- Les messages entre téléphones ----------

    @Test
    fun laTableVoyageSansPerteEntreLesTelephones() {
        val round = TarotRound(4) { }
        round.bidFor(1, TarotBid.PETITE)
        val table = round.tableFor(2)
        val message = TarotMsg.View(table, 2, listOf(0, 3))
        assertEquals(message, tarotMsgFromJson(message.toJson()))

        for (action in listOf(
            TarotMsg.Claim(1), TarotMsg.Bid(3), TarotMsg.Call(13), TarotMsg.Discard(listOf(1, 2, 3), true),
            TarotMsg.Handful, TarotMsg.Play(40), TarotMsg.Collect, TarotMsg.Next
        )) {
            assertEquals(action, tarotMsgFromJson(action.toJson()))
        }
    }

    @Test
    fun uneDonneFinieVoyageAvecSonResultat() {
        val round = miniRound(
            h0 = listOf(c(spade, 3), t(5)),
            h1 = listOf(c(spade, 14), t(21)),
            h2 = listOf(c(spade, 2), t(1))
        )
        round.playFor(1, c(spade, 14)); round.playFor(2, c(spade, 2)); round.playFor(0, c(spade, 3)); round.collect()
        round.playFor(1, t(21)); round.playFor(2, t(1)); round.playFor(0, t(5)); round.collect()
        val table = round.tableFor(-1)
        assertNotNull(table.outcome)
        val message = TarotMsg.View(table, -1, emptyList())
        assertEquals(message, tarotMsgFromJson(message.toJson()))
        assertNull(tarotMsgFromJson(org.json.JSONObject().put("k", "inconnu")))
    }
}
