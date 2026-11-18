package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.random.Random

/** Barbu jouable tour par tour : les sept contrats, les cartes jouables, les plis, le Domino, les points et le tableau des scores. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class BarbuRoundTest {

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val club = FrenchCards.CLUB

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == BARBU_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben", "Cléo")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    // ---------- Les points ----------

    @Test
    fun chaqueContratCompteSesPenalites() {
        val taken = listOf(BarbuScoring.KING_OF_HEARTS, c(spade, 12), c(diamond, 12), c(heart, 3), c(club, 4))
        assertEquals(-4, BarbuScoring.tricksPoints(BarbuContract.NO_TRICKS, taken, 2, true))
        assertEquals(-10, BarbuScoring.tricksPoints(BarbuContract.NO_HEARTS, taken, 2, true))
        assertEquals(-20, BarbuScoring.tricksPoints(BarbuContract.NO_QUEENS, taken, 2, true))
        assertEquals(-20, BarbuScoring.tricksPoints(BarbuContract.NO_KING, taken, 2, true))
        assertEquals(-40, BarbuScoring.tricksPoints(BarbuContract.NO_LAST, taken, 2, true))
        assertEquals(0, BarbuScoring.tricksPoints(BarbuContract.NO_LAST, taken, 2, false))
        assertEquals(0, BarbuScoring.tricksPoints(BarbuContract.NO_KING, listOf(c(heart, 12)), 1, false))
        // La Salade additionne tout : −4 −10 −20 −20 −40.
        assertEquals(-94, BarbuScoring.tricksPoints(BarbuContract.SALAD, taken, 2, true))
    }

    @Test
    fun leDominoDonne40AuPremierEtMoins40AuDernier() {
        for (players in 3..6) {
            assertEquals(40, BarbuScoring.dominoPoints(players, 0))
            assertEquals(-40, BarbuScoring.dominoPoints(players, players - 1))
            assertEquals(0, (0 until players).sumOf { BarbuScoring.dominoPoints(players, it) })
        }
    }

    // ---------- Le contrat ----------

    @Test
    fun leDonneurVoitSaMainPuisAnnonceUnContratUneSeuleFois() {
        val round = BarbuRound(4) { }
        assertEquals(BarbuPhase.CHOOSING, round.phase)
        assertEquals(0, round.declarer)
        assertEquals(0, round.current)
        // Il doit d'abord voir sa main.
        assertFalse(round.chooseContract(BarbuContract.NO_HEARTS))
        round.reveal()
        assertTrue(round.chooseContract(BarbuContract.NO_HEARTS))
        assertEquals(BarbuContract.NO_HEARTS, round.contract)
        assertEquals(BarbuPhase.PLAYING, round.phase)
        assertEquals(0, round.current)
        assertTrue(round.hasUsed(0, BarbuContract.NO_HEARTS))
        assertFalse(round.hasUsed(1, BarbuContract.NO_HEARTS))
        assertEquals(6, round.available(0).size)
        assertFalse(BarbuContract.NO_HEARTS in round.available(0))
        // Pas deux contrats dans la même donne.
        round.reveal()
        assertFalse(round.chooseContract(BarbuContract.SALAD))
    }

    @Test
    fun seulLeDonneurPeutAnnoncerSurSonTelephone() {
        val round = BarbuRound(3) { }
        assertFalse(round.chooseFor(1, BarbuContract.NO_TRICKS))
        assertTrue(round.chooseFor(0, BarbuContract.NO_TRICKS))
    }

    // ---------- Les plis ----------

    private fun trickRound(vararg hands: List<Int>, contract: BarbuContract, current: Int = 0): BarbuRound =
        BarbuRound(hands.size) { }.also { it.load(contract, hands.toList(), current) }

    @Test
    fun onFournitLaCouleurDemandeeEtLaPlusForteRamasseLePli() {
        val round = trickRound(
            listOf(c(spade, 5), c(heart, 2)),
            listOf(c(spade, 9), c(club, 3)),
            listOf(c(heart, 4), c(club, 7)),
            contract = BarbuContract.NO_TRICKS
        )
        assertTrue(round.playFor(0, c(spade, 5)))
        // Ben a un pique : il ne peut pas défausser son trèfle.
        assertFalse(round.playFor(1, c(club, 3)))
        assertTrue(round.playFor(1, c(spade, 9)))
        // Cléo n'a pas de pique : elle défausse ce qu'elle veut.
        assertTrue(round.playFor(2, c(club, 7)))
        assertTrue(round.trickDone)
        assertEquals(1, round.trickWinner)
        // Personne ne joue avant que le pli soit ramassé, et seul son gagnant le ramasse.
        assertFalse(round.collectFor(0))
        assertTrue(round.collectFor(1))
        assertEquals(listOf(0, 1, 0), round.tricksWon)
        assertEquals(1, round.current)
    }

    @Test
    fun lePasDePlisCompteMoinsDeuxParPliEtLaDonneFinitApresLeDernier() {
        val round = trickRound(
            listOf(c(spade, 5)),
            listOf(c(spade, 9)),
            listOf(c(spade, 3)),
            contract = BarbuContract.NO_TRICKS
        )
        round.playFor(0, c(spade, 5))
        round.playFor(1, c(spade, 9))
        round.playFor(2, c(spade, 3))
        assertEquals(1, round.trickWinner)
        assertEquals(1, round.lastTrickWinner)
        assertTrue(round.collectFor(1))
        assertTrue(round.roundOver)
        assertEquals(listOf(0, -2, 0), round.roundPoints())
    }

    @Test
    fun leDernierPliCoute40AuJoueurQuiLeRamasse() {
        val round = trickRound(
            listOf(c(heart, 5)),
            listOf(c(heart, 13)),
            listOf(c(heart, 3)),
            contract = BarbuContract.NO_LAST
        )
        round.playFor(0, c(heart, 5))
        round.playFor(1, c(heart, 13))
        round.playFor(2, c(heart, 3))
        round.collectFor(1)
        assertEquals(listOf(0, -40, 0), round.roundPoints())
    }

    @Test
    fun leRoiDeCoeurCoute20AuJoueurQuiLeRamasse() {
        val round = trickRound(
            listOf(c(heart, 5), c(spade, 2)),
            listOf(c(heart, 13), c(spade, 3)),
            listOf(c(heart, 1), c(spade, 4)),
            contract = BarbuContract.NO_KING
        )
        round.playFor(0, c(heart, 5))
        round.playFor(1, c(heart, 13))
        // L'as est la carte la plus forte : Cléo ramasse le pli, roi de cœur compris.
        round.playFor(2, c(heart, 1))
        assertEquals(2, round.trickWinner)
        round.collectFor(2)
        assertEquals(listOf(0, 0, -20), round.roundPoints())
    }

    // ---------- Le Domino ----------

    @Test
    fun auDominoOnProlongeUneCouleurOuvreAvecLaValeurDeDepartEtOnPasse() {
        val round = trickRound(
            listOf(c(spade, 7), c(heart, 7)),
            listOf(c(spade, 9), c(spade, 10)),
            listOf(c(spade, 8), c(heart, 8)),
            contract = BarbuContract.DOMINO
        )
        assertTrue(round.playFor(0, c(spade, 7)))
        assertEquals(7, round.dominoBase)
        // Ben ne peut rien poser (il lui faut le 8 de pique) : son tour est passé.
        assertEquals(listOf(1), round.skipped)
        assertEquals(2, round.current)
        // Le 8 de cœur ne peut pas ouvrir les cœurs : seule la valeur de départ le peut.
        assertFalse(round.playFor(2, c(heart, 8)))
        assertTrue(round.playFor(2, c(spade, 8)))
        assertEquals(0, round.current)
        assertTrue(round.playFor(0, c(heart, 7)))
        // Anna s'est vidée la première.
        assertEquals(listOf(0), round.finishOrder)
        assertTrue(round.playFor(1, c(spade, 9)))
        assertTrue(round.playFor(2, c(heart, 8)))
        // Il ne reste que Ben : il finit dernier et la donne est finie.
        assertTrue(round.roundOver)
        assertEquals(listOf(0, 2, 1), round.finishOrder)
        assertEquals(listOf(40, -40, 0), round.roundPoints())
    }

    @Test
    fun auDominoOnNOuvrePasAvecUneValeurDontUneCouleurManque() {
        // À 3 joueurs le 2 de carreau est retiré : aucune couleur ne pourrait s'ouvrir avec un 2.
        assertFalse(BarbuScoring.canStartDomino(c(spade, 2), 3))
        assertTrue(BarbuScoring.canStartDomino(c(spade, 3), 3))
        assertTrue(BarbuScoring.canStartDomino(c(spade, 2), 4))
        assertFalse(BarbuScoring.canStartDomino(c(club, 3), 6))
        val round = trickRound(
            listOf(c(spade, 2), c(spade, 6)),
            listOf(c(heart, 6), c(heart, 9)),
            listOf(c(club, 6), c(club, 9)),
            contract = BarbuContract.DOMINO
        )
        assertFalse(round.playFor(0, c(spade, 2)))
        assertTrue(round.playFor(0, c(spade, 6)))
    }

    @Test
    fun lAsSePoseAuDessusDuRoiMaisPasEnDessousDuDeux() {
        val low = listOf(2, 0, 0, 0)
        assertTrue(BarbuScoring.dominoPlayable(c(spade, 1), low, listOf(13, 0, 0, 0), 2))
        assertFalse(BarbuScoring.dominoPlayable(c(spade, 1), low, listOf(12, 0, 0, 0), 2))
        assertTrue(BarbuScoring.dominoPlayable(c(spade, 3), listOf(4, 0, 0, 0), listOf(4, 0, 0, 0), 4))
    }

    // ---------- Une partie entière ----------

    @Test
    fun uneDonneSInscritDansLeTableauAvecSesPointsNegatifs() {
        val game = viewModel()
        val round = game.barbuRound()
        // Anna choisit « Pas de dames » puis tout le monde joue sans respecter autre chose que les règles.
        round.reveal()
        assertTrue(round.chooseContract(BarbuContract.NO_QUEENS))
        val random = Random(7)
        while (!round.roundOver) {
            if (round.trickDone) {
                assertTrue(round.collectFor(round.trickWinner))
                continue
            }
            val player = round.current
            val card = round.hands[player].filter { round.isPlayable(it) }.let { it[random.nextInt(it.size)] }
            assertTrue(round.playFor(player, card))
        }
        val points = round.roundPoints()
        assertEquals(-40, points.sum())
        assertTrue(round.settle(game))
        assertFalse(round.settle(game))
        assertEquals(points, game.totals)
        assertTrue(round.nextRound())
        assertEquals(2, round.roundNumber)
        // Le donneur change à chaque donne.
        assertEquals(1, round.declarer)
        assertEquals(BarbuPhase.CHOOSING, round.phase)
    }

    @Test
    fun chaqueJoueurAnnonceSesSeptContratsEtLaPartieSeTermine() {
        for (players in 3..6) {
            val game = viewModel((1..players).map { "J$it" })
            val round = game.barbuRound()
            val random = Random(players)
            val declared = IntArray(players)
            while (!round.finished) {
                val declarer = round.declarer
                val options = round.available(declarer)
                assertTrue(round.chooseFor(declarer, options[random.nextInt(options.size)]))
                declared[declarer]++
                while (!round.roundOver) {
                    if (round.trickDone) {
                        assertTrue(round.collectFor(round.trickWinner))
                        continue
                    }
                    val player = round.current
                    // Il y a toujours une carte jouable pour celui dont c'est le tour.
                    val playable = round.hands[player].filter { round.isPlayable(it) }
                    assertTrue(playable.isNotEmpty())
                    assertTrue(round.playFor(player, playable[random.nextInt(playable.size)]))
                }
                assertTrue(round.settle(game))
                if (!round.finished) assertTrue(round.nextRound())
            }
            assertTrue(declared.all { it == BarbuScoring.CONTRACT_COUNT })
            assertEquals(BarbuScoring.totalDeals(players), game.roundsPlayed())
            assertFalse(round.nextRound())
        }
    }

    @Test
    fun uneGardePartieReprisePeutDejaEtreFinie() {
        assertTrue(BarbuRound(4, startRound = 29).finished)
        assertFalse(BarbuRound(4, startRound = 28).finished)
    }

    @Test
    fun leBarbuEstJouableSurUnOuPlusieursTelephones() {
        val barbu = rules()
        assertTrue(barbu.isPlayableGame())
        assertTrue(barbu.isMultiPhone())
    }
}
