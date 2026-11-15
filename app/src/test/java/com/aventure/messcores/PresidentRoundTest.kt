package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Le Président jouable : force des cartes, poses, valeurs égales, passes, plis, classement, points, échanges de cartes. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class PresidentRoundTest {

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val club = FrenchCards.CLUB

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    /** Une manche dont le paquet n'est pas mélangé : les cartes 0, 1, 2… sont distribuées dans l'ordre. */
    private fun round(players: Int = 3): PresidentRound = PresidentRound(players, shuffle = { })

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == PRESIDENT_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben", "Chloé")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Trois joueurs avec les mains données, le joueur [current] ayant dévoilé la sienne. */
    private fun table(vararg hands: List<Int>, current: Int = 0): PresidentRound =
        round(hands.size).also {
            it.load(hands.toList(), current = current)
            it.reveal()
        }

    // ---------- Règles des cartes ----------

    @Test
    fun leDeuxEstLaCartePlusForteSuivieDeLAsEtLeTroisLaPlusFaible() {
        assertTrue(PresidentScoring.strength(c(spade, 2)) > PresidentScoring.strength(c(heart, 1)))
        assertTrue(PresidentScoring.strength(c(heart, 1)) > PresidentScoring.strength(c(club, 13)))
        assertTrue(PresidentScoring.strength(c(club, 13)) > PresidentScoring.strength(c(diamond, 4)))
        assertTrue(PresidentScoring.strength(c(diamond, 4)) > PresidentScoring.strength(c(spade, 3)))
    }

    @Test
    fun uneVraiePoseEstDeUnAQuatreCartesDeMemeValeur() {
        assertTrue(PresidentScoring.isSet(listOf(c(spade, 7))))
        assertTrue(PresidentScoring.isSet(listOf(c(spade, 7), c(heart, 7), c(club, 7), c(diamond, 7))))
        assertFalse(PresidentScoring.isSet(listOf(c(spade, 7), c(heart, 8))))
        assertFalse(PresidentScoring.isSet(emptyList()))
    }

    @Test
    fun lesPlacesDonnentLesRolesEtLesPoints() {
        fun points(place: Int, players: Int) = PresidentScoring.points(PresidentScoring.role(place, players))
        assertEquals(listOf(2, 0, -2), (0..2).map { points(it, 3) })
        assertEquals(listOf(2, 1, -1, -2), (0..3).map { points(it, 4) })
        assertEquals(listOf(2, 1, 0, -1, -2), (0..4).map { points(it, 5) })
        assertEquals(PresidentRole.NEUTRAL, PresidentScoring.role(1, 3))
    }

    // ---------- La distribution ----------

    @Test
    fun toutLePaquetEstDistribue() {
        val five = round(5)
        assertEquals(listOf(11, 11, 10, 10, 10), five.hands.map { it.size })
        assertEquals(52, five.hands.flatten().distinct().size)
    }

    @Test
    fun laPremiereMancheCommenceParCeluiQuiALeTroisDeTrefle() {
        val four = round(4)
        val holder = four.hands.indexOfFirst { c(club, 3) in it }
        assertEquals(holder, four.current)
    }

    // ---------- Les poses ----------

    @Test
    fun onOuvreLePliAvecUneOuPlusieursCartesDeMemeValeur() {
        val r = table(listOf(c(spade, 5), c(heart, 5), c(club, 9)), listOf(c(spade, 6)), listOf(c(heart, 7)))
        assertEquals(PlayCheck.NOT_A_SET, r.check(listOf(c(spade, 5), c(club, 9))))
        assertEquals(PlayCheck.EMPTY, r.check(emptyList()))
        assertEquals(PlayCheck.NOT_IN_HAND, r.check(listOf(c(heart, 9))))
        assertEquals(PlayCheck.OK, r.play(listOf(c(spade, 5), c(heart, 5))))
        assertEquals(1, r.current)
        assertEquals(listOf(c(club, 9)), r.hands[0])
        assertFalse(r.revealed)
    }

    @Test
    fun onNePoseQueLeMemeNombreDeCartesEtPlusFortes() {
        val r = table(listOf(c(spade, 5), c(club, 9)), listOf(c(spade, 6), c(heart, 4), c(heart, 8)), listOf(c(heart, 7)))
        r.play(listOf(c(spade, 5)))
        r.reveal()
        assertEquals(PlayCheck.TOO_WEAK, r.check(listOf(c(heart, 4))))
        assertEquals(PlayCheck.WRONG_COUNT, r.check(listOf(c(spade, 6), c(heart, 8))))
        assertEquals(PlayCheck.OK, r.play(listOf(c(spade, 6))))
    }

    @Test
    fun uneValeurEgaleFaitSauterLeJoueurSuivant() {
        val r = table(listOf(c(spade, 5), c(heart, 9)), listOf(c(diamond, 5), c(club, 7)), listOf(c(spade, 6), c(spade, 7)))
        r.play(listOf(c(spade, 5)))
        r.reveal()
        r.play(listOf(c(diamond, 5)))
        assertEquals(2, r.skipped)
        assertEquals(0, r.current)
        r.reveal()
        assertTrue(r.pass())
        // Le pli est fini : celui qui avait posé en dernier l'ouvre à nouveau.
        assertEquals(1, r.current)
        assertTrue(r.top.isEmpty())
        assertTrue(r.leading)
    }

    @Test
    fun siTousLesAutresSontSautesOuOntPasseLeMemeJoueurContinue() {
        val r = table(listOf(c(spade, 5), c(heart, 9)), listOf(c(diamond, 5), c(club, 7)), emptyList())
        r.play(listOf(c(spade, 5)))
        r.reveal()
        r.play(listOf(c(diamond, 5)))
        assertEquals(1, r.current)
        assertTrue(r.top.isEmpty())
        // Il garde le téléphone : sa main reste visible.
        assertTrue(r.revealed)
    }

    @Test
    fun onNePeutPasPasserQuandOnOuvreLePli() {
        val r = table(listOf(c(spade, 5)), listOf(c(spade, 6)), listOf(c(spade, 7)))
        assertFalse(r.pass())
    }

    // ---------- Fin de manche et points ----------

    /** Une manche à trois joueurs finie : Anna sort la première, Chloé la deuxième, Ben est le Trou. */
    private fun finishedRound(): PresidentRound {
        val r = table(
            listOf(c(spade, 3)),
            listOf(c(spade, 4), c(spade, 5)),
            listOf(c(spade, 6), c(spade, 7))
        )
        assertEquals(PlayCheck.OK, r.play(listOf(c(spade, 3))))
        r.reveal()
        assertEquals(PlayCheck.OK, r.play(listOf(c(spade, 4))))
        r.reveal()
        assertEquals(PlayCheck.OK, r.play(listOf(c(spade, 6))))
        r.reveal()
        // Ben ne peut pas battre le 6 avec le 5 : il passe, et Chloé rouvre le pli.
        assertEquals(PlayCheck.TOO_WEAK, r.check(listOf(c(spade, 5))))
        assertTrue(r.pass())
        assertEquals(2, r.current)
        r.reveal()
        assertEquals(PlayCheck.OK, r.play(listOf(c(spade, 7))))
        return r
    }

    @Test
    fun laMancheSeTermineQuandUnSeulJoueurAEncoreDesCartes() {
        val r = finishedRound()
        assertTrue(r.roundOver)
        assertEquals(listOf(0, 2, 1), r.order)
        assertEquals(listOf(2, -2, 0), r.roundPoints())
        assertEquals(PresidentRole.TROU, r.roleOf(1))
    }

    @Test
    fun lesPointsNegatifsSontInscritsAuTableauDesScores() {
        val r = finishedRound()
        val vm = viewModel()
        assertTrue(r.settle(vm))
        assertFalse(r.settle(vm))
        assertEquals(listOf(2, -2, 0), vm.totals)
    }

    /** La manche suivante d'une manche finie : Anna (Présidente) et Ben (Trou) font leur échange. */
    private fun nextRoundAfterFinished(): PresidentRound {
        val r = finishedRound()
        assertFalse(r.nextRound())
        r.settle(viewModel())
        assertTrue(r.nextRound())
        return r
    }

    @Test
    fun leTrouDonneSesMeilleuresCartesEtLePresidentChoisitCeQuIlRend() {
        val r = nextRoundAfterFinished()
        assertEquals(2, r.roundNumber)
        assertTrue(r.order.isEmpty())
        // Les échanges d'abord : le Président (Anna) choisit, le Trou (Ben) a déjà donné ses 2 meilleures cartes.
        assertEquals(PresidentPhase.EXCHANGE, r.phase)
        assertEquals(0, r.current)
        val exchange = r.pendingExchange!!
        assertEquals(0, exchange.high)
        assertEquals(1, exchange.low)
        assertEquals(2, exchange.size)
        assertTrue(r.hands[0].containsAll(exchange.toHigh))
        assertEquals(52, r.hands.flatten().distinct().size)
        // Il peut rendre n'importe quelles cartes de sa main, même ses meilleures.
        r.reveal()
        val chosen = r.currentHand.sortedByDescending { PresidentScoring.sortKey(it) }.take(2)
        assertFalse(r.giveBack(chosen.take(1)))
        assertTrue(r.giveBack(chosen))
        assertEquals(chosen.sortedBy { PresidentScoring.sortKey(it) }, r.exchanges.first().toLow)
        assertTrue(r.hands[1].containsAll(chosen))
        assertTrue(r.hands[0].none { it in chosen })
        // Puis le jeu commence, par le Trou.
        assertEquals(PresidentPhase.PLAY, r.phase)
        assertEquals(1, r.current)
        assertEquals(52, r.hands.flatten().distinct().size)
    }

    @Test
    fun onNeJoueQuAvecLesCartesDeSaMainPendantLesEchanges() {
        val r = nextRoundAfterFinished()
        r.reveal()
        assertEquals(PlayCheck.NOT_YOUR_TURN, r.check(listOf(r.currentHand.first())))
        assertFalse(r.giveBack(listOf(c(spade, 3), c(spade, 3))))
        val foreign = r.hands[2].take(2)
        assertFalse(r.giveBack(foreign))
        assertEquals(PresidentPhase.EXCHANGE, r.phase)
    }

    @Test
    fun chaqueJoueurNeVoitQueSaMainEtLesCartesRenduesDesDeuxConcernes() {
        val r = nextRoundAfterFinished()
        val chosen = r.hands[0].sortedBy { PresidentScoring.strength(it) }.take(2)
        assertEquals(PresidentPhase.EXCHANGE, r.phase)
        assertTrue(r.giveBackFor(0, chosen))
        // Le Trou et le Président voient ce qui a été rendu ; Chloé voit seulement qu'un échange a eu lieu.
        assertEquals(chosen.sortedBy { PresidentScoring.sortKey(it) }, r.tableFor(1).exchanges.first().toLow)
        assertEquals(r.tableFor(0).exchanges.first().toLow, r.tableFor(1).exchanges.first().toLow)
        val seenByChloe = r.tableFor(2)
        assertTrue(seenByChloe.exchanges.first().toLow.isEmpty())
        assertTrue(seenByChloe.exchanges.first().done)
        // La main de chacun n'est que la sienne ; un spectateur n'en voit aucune.
        assertEquals(r.hands[2].sortedBy { PresidentScoring.sortKey(it) }, seenByChloe.hand)
        assertTrue(r.tableFor(-1).hand.isEmpty())
        assertEquals(r.hands.map { it.size }, seenByChloe.handSizes)
    }

    @Test
    fun uneActionNeVautQueDeLaPlaceDontCEstLeTour() {
        val r = round(3)
        r.load(listOf(listOf(c(spade, 5)), listOf(c(spade, 6), c(heart, 9)), listOf(c(heart, 7), c(heart, 8))), current = 1)
        assertEquals(PlayCheck.NOT_YOUR_TURN, r.playFor(0, listOf(c(spade, 5))))
        assertFalse(r.passFor(0))
        // Il ne peut pas passer en ouvrant le pli.
        assertFalse(r.passFor(1))
        assertEquals(PlayCheck.OK, r.playFor(1, listOf(c(spade, 6))))
        assertEquals(2, r.current)
        assertEquals(listOf(c(spade, 6)), r.top)
    }
}
