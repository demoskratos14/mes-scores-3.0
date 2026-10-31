package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Cœurs jouable tour par tour : distribution, passe, cartes jouables, plis, points, grand chelem, tableau des scores. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class HeartsRoundTest {

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val club = FrenchCards.CLUB

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private val queenOfSpades = HeartsScoring.QUEEN_OF_SPADES
    private val twoOfClubs = HeartsScoring.TWO_OF_CLUBS

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == HEARTS_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben", "Cléo")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Une table de jeu des plis (sans passe) : un joueur par main, le joueur [current] a dévoilé sa main. */
    private fun table(
        vararg hands: List<Int>,
        current: Int = 0,
        trick: List<HeartsPlay> = emptyList(),
        tricksPlayed: Int = 1,
        heartsBroken: Boolean = false,
        taken: List<List<Int>> = hands.map { emptyList() }
    ): HeartsRound = HeartsRound(hands.size) { }.also {
        it.load(hands.toList(), current, trick, tricksPlayed, heartsBroken, taken)
        it.reveal()
    }

    // ---------- Les cartes et les points ----------

    @Test
    fun lesCoeursValentUnPointEtLaDameDePique13() {
        assertEquals(c(spade, 12), queenOfSpades)
        assertEquals(c(club, 2), twoOfClubs)
        assertEquals(1, HeartsScoring.points(c(heart, 1)))
        assertEquals(1, HeartsScoring.points(c(heart, 13)))
        assertEquals(13, HeartsScoring.points(queenOfSpades))
        assertEquals(0, HeartsScoring.points(c(spade, 13)))
        assertEquals(0, HeartsScoring.points(c(diamond, 12)))
        assertEquals(26, (0 until FrenchCards.DECK).sumOf { HeartsScoring.points(it) })
    }

    @Test
    fun lAsEstLaPlusForte() {
        assertEquals(14, FrenchCards.strength(c(spade, 1)))
        assertEquals(13, FrenchCards.strength(c(spade, 13)))
        assertEquals(2, FrenchCards.strength(c(spade, 2)))
    }

    @Test
    fun leNombreDeCartesParJoueurDependDuNombreDeJoueurs() {
        assertEquals(17, HeartsScoring.handSize(3))
        assertEquals(13, HeartsScoring.handSize(4))
        assertEquals(10, HeartsScoring.handSize(5))
        assertEquals(8, HeartsScoring.handSize(6))
        for (players in 3..6) {
            val round = HeartsRound(players) { }
            assertEquals(List(players) { HeartsScoring.handSize(players) }, round.hands.map { it.size })
            val all = round.hands.flatten()
            assertEquals(all.size, all.toSet().size)
            assertEquals(FrenchCards.DECK - HeartsScoring.removedCards(players).size, all.size)
            // Le 2 de trèfle, la dame de pique et les 13 cœurs restent toujours dans le paquet.
            assertTrue(twoOfClubs in all)
            assertTrue(queenOfSpades in all)
            assertEquals(13, all.count { FrenchCards.suit(it) == heart })
        }
    }

    @Test
    fun leSensDeLaPasseChangeChaqueMancheAvecUneMancheSansPasse() {
        assertEquals(listOf(1, -1, 2, 0, 1), (1..5).map { HeartsScoring.passOffset(it, 4) })
        assertEquals(listOf(1, -1, 0, 1), (1..4).map { HeartsScoring.passOffset(it, 3) })
        assertEquals(listOf(1, -1, 0, 1), (1..4).map { HeartsScoring.passOffset(it, 5) })
        assertEquals(listOf(1, -1, 3, 0), (1..4).map { HeartsScoring.passOffset(it, 6) })
    }

    // ---------- La passe ----------

    @Test
    fun chacunChoisitTroisCartesPuisLesCartesChangentDeMain() {
        val round = HeartsRound(4) { }
        assertEquals(HeartsPhase.PASSING, round.phase)
        assertEquals(0, round.current)
        // Pas avant d'avoir vu sa main, ni autre chose que 3 cartes différentes de sa main.
        assertFalse(round.confirmPass(round.currentHand.take(3)))
        round.reveal()
        assertFalse(round.confirmPass(round.currentHand.take(2)))
        assertFalse(round.confirmPass(listOf(0, 0, 1)))
        assertFalse(round.confirmPass(listOf(0, 1, 40)))
        for (player in 0 until 4) {
            assertEquals(player, round.current)
            if (!round.revealed) round.reveal()
            assertTrue(round.confirmPass(round.currentHand.take(3)))
        }
        assertEquals(HeartsPhase.PLAYING, round.phase)
        assertEquals(List(4) { 13 }, round.hands.map { it.size })
        // Le joueur 1 reçoit les trois premières cartes du joueur 0 (passe au joueur suivant).
        assertEquals(setOf(0, 1, 2), round.received[1])
        assertTrue(round.hands[1].containsAll(listOf(0, 1, 2)))
        assertFalse(round.hands[0].any { it in 0..2 })
        // Le joueur 3 passait le 2 de trèfle (le 2e de ses cartes) au joueur 0 : c'est lui qui commence.
        assertTrue(twoOfClubs in round.hands[0])
        assertEquals(0, round.current)
        assertFalse(round.revealed)
    }

    @Test
    fun uneMancheSansPasseCommenceDirectParLe2DeTrefle() {
        val round = HeartsRound(4, startRound = 4) { }
        assertEquals(0, round.passOffset)
        assertEquals(HeartsPhase.PLAYING, round.phase)
        assertEquals(3, round.current)
        assertTrue(twoOfClubs in round.hands[3])
    }

    @Test
    fun laPasseEnFaceEchangeAvecLeJoueurOppose() {
        val round = HeartsRound(4, startRound = 3) { }
        assertEquals(2, round.passOffset)
        assertEquals(2, round.passTarget(0))
        for (player in 0 until 4) {
            round.reveal()
            assertTrue(round.confirmPass(round.currentHand.take(3)))
        }
        assertEquals(setOf(0, 1, 2), round.received[2])
        assertEquals(setOf(26, 27, 28), round.received[0])
    }

    // ---------- Cartes jouables ----------

    @Test
    fun auPremierPliOnCommenceParLe2DeTrefle() {
        val round = table(
            listOf(twoOfClubs, c(diamond, 5), c(heart, 3)), listOf(c(club, 9)), listOf(c(club, 4)),
            tricksPlayed = 0
        )
        assertTrue(round.isPlayable(twoOfClubs))
        assertFalse(round.isPlayable(c(diamond, 5)))
        assertFalse(round.isPlayable(c(heart, 3)))
        assertTrue(round.play(twoOfClubs))
        assertEquals(1, round.current)
        assertFalse(round.revealed)
    }

    @Test
    fun onFournitLaCouleurDemandeeSiOnLaPossede() {
        val lead = listOf(HeartsPlay(0, twoOfClubs))
        val round = table(
            listOf(c(diamond, 5)), listOf(c(club, 9), c(heart, 4), c(diamond, 2)), listOf(c(club, 4)),
            current = 1, trick = lead, tricksPlayed = 0
        )
        assertTrue(round.isPlayable(c(club, 9)))
        assertFalse(round.isPlayable(c(heart, 4)))
        assertFalse(round.isPlayable(c(diamond, 2)))
    }

    @Test
    fun auPremierPliOnNeDonnePasDePointsSaufSiOnNAQueCa() {
        val lead = listOf(HeartsPlay(0, twoOfClubs))
        val other = table(
            listOf(c(diamond, 5)), listOf(c(heart, 4), queenOfSpades, c(diamond, 7)), listOf(c(club, 4)),
            current = 1, trick = lead, tricksPlayed = 0
        )
        assertFalse(other.isPlayable(c(heart, 4)))
        assertFalse(other.isPlayable(queenOfSpades))
        assertTrue(other.isPlayable(c(diamond, 7)))

        val onlyPoints = table(
            listOf(c(diamond, 5)), listOf(c(heart, 4), queenOfSpades), listOf(c(club, 4)),
            current = 1, trick = lead, tricksPlayed = 0
        )
        assertTrue(onlyPoints.isPlayable(c(heart, 4)))
        assertTrue(onlyPoints.isPlayable(queenOfSpades))
    }

    @Test
    fun sansLaCouleurDemandeeApresLePremierPliOnDefausseCeQueLOnVeut() {
        val lead = listOf(HeartsPlay(0, c(spade, 5)))
        val round = table(
            listOf(c(spade, 9)), listOf(c(heart, 4), c(diamond, 7)), listOf(c(club, 4)),
            current = 1, trick = lead
        )
        assertTrue(round.isPlayable(c(heart, 4)))
        assertTrue(round.isPlayable(c(diamond, 7)))
    }

    @Test
    fun onNeCommencePasUnPliParUnCoeurTantQueLesCoeursNeSontPasOuverts() {
        val closed = table(listOf(c(heart, 4), c(spade, 5)), listOf(c(club, 9)), listOf(c(club, 4)))
        assertFalse(closed.isPlayable(c(heart, 4)))
        assertTrue(closed.isPlayable(c(spade, 5)))

        val broken = table(listOf(c(heart, 4), c(spade, 5)), listOf(c(club, 9)), listOf(c(club, 4)), heartsBroken = true)
        assertTrue(broken.isPlayable(c(heart, 4)))

        val onlyHearts = table(listOf(c(heart, 4), c(heart, 6)), listOf(c(club, 9)), listOf(c(club, 4)))
        assertTrue(onlyHearts.isPlayable(c(heart, 4)))
    }

    // ---------- Les plis ----------

    @Test
    fun laPlusFortePlusDeLaCouleurDemandeeRamasseLePliEtLAsEstHaut() {
        val round = table(
            listOf(c(spade, 3), c(club, 9)), listOf(c(spade, 1), c(club, 3)), listOf(c(spade, 13), c(club, 5))
        )
        assertTrue(round.play(c(spade, 3)))
        round.reveal()
        assertTrue(round.play(c(spade, 1)))
        assertFalse(round.trickDone)
        round.reveal()
        assertTrue(round.play(c(spade, 13)))
        assertTrue(round.trickDone)
        assertEquals(1, round.trickWinner)
        assertEquals(0, round.trickPoints())
        // Personne ne joue avant que le pli soit ramassé.
        round.reveal()
        assertFalse(round.revealed)
        assertTrue(round.collectTrick())
        assertEquals(1, round.current)
        assertEquals(2, round.tricksPlayed)
        assertTrue(round.trick.isEmpty())
        assertEquals(listOf(c(spade, 3), c(spade, 1), c(spade, 13)).toSet(), round.taken[1].toSet())
        assertFalse(round.collectTrick())
    }

    @Test
    fun uneCarteDhorsCouleurNeRamassePasLePliEtUnCoeurOuvreLesCoeurs() {
        val round = table(
            listOf(c(spade, 3), c(club, 9)), listOf(c(spade, 5), c(club, 3)), listOf(c(heart, 2), c(club, 5))
        )
        assertFalse(round.heartsBroken)
        round.play(c(spade, 3))
        round.reveal()
        round.play(c(spade, 5))
        round.reveal()
        assertTrue(round.play(c(heart, 2)))
        assertTrue(round.heartsBroken)
        assertEquals(1, round.trickWinner)
        assertEquals(1, round.trickPoints())
        round.collectTrick()
        assertEquals(1, round.taken[1].count { it == c(heart, 2) })
    }

    @Test
    fun onNePeutPasJouerHorsDeSonTourNiUneCarteQueLOnNAPas() {
        val round = table(listOf(c(spade, 3), c(club, 9)), listOf(c(spade, 5), c(club, 3)), listOf(c(club, 5)))
        assertFalse(round.play(c(spade, 5)))
        assertTrue(round.play(c(spade, 3)))
        // Main refermée pour le joueur suivant : rien ne se joue avant de la dévoiler.
        assertFalse(round.play(c(spade, 5)))
    }

    // ---------- Fin de manche ----------

    @Test
    fun laMancheFinitApresLeDernierPliEtLesPointsSontCeuxDesCartesRamassees() {
        val round = table(
            listOf(c(club, 5)), listOf(c(club, 9)), listOf(c(club, 13)),
            taken = listOf(listOf(c(heart, 2), c(heart, 3), c(heart, 4)), listOf(queenOfSpades), emptyList())
        )
        round.play(c(club, 5))
        round.reveal()
        round.play(c(club, 9))
        round.reveal()
        round.play(c(club, 13))
        assertFalse(round.roundOver)
        assertTrue(round.collectTrick())
        assertTrue(round.roundOver)
        assertEquals(-1, round.moonShooter)
        assertEquals(listOf(3, 13, 0), round.roundPoints())
    }

    @Test
    fun ramasserTousLesPointsEstLeGrandChelem() {
        val allPoints = (0 until FrenchCards.DECK).filter { HeartsScoring.isPoints(it) }
        val round = table(
            listOf(c(spade, 1)), listOf(c(spade, 2)), listOf(c(spade, 3)),
            taken = listOf(allPoints, emptyList(), emptyList())
        )
        round.play(c(spade, 1))
        round.reveal()
        round.play(c(spade, 2))
        round.reveal()
        round.play(c(spade, 3))
        round.collectTrick()
        assertTrue(round.roundOver)
        assertEquals(0, round.moonShooter)
        assertEquals(listOf(0, 26, 26), round.roundPoints())
    }

    // ---------- Tableau des scores ----------

    @Test
    fun lesPointsSontInscritsUneSeuleFoisPuisLaPasseDeLaMancheSuivanteChange() {
        val vm = viewModel()
        val round = table(
            listOf(c(club, 5)), listOf(c(club, 9)), listOf(c(club, 13)),
            taken = listOf(listOf(c(heart, 2), c(heart, 3), c(heart, 4)), listOf(queenOfSpades), emptyList())
        )
        assertFalse(round.settle(vm))
        round.play(c(club, 5))
        round.reveal()
        round.play(c(club, 9))
        round.reveal()
        round.play(c(club, 13))
        round.collectTrick()
        assertFalse(round.nextRound())
        assertTrue(round.settle(vm))
        assertEquals(listOf(3, 13, 0), vm.totals)
        assertFalse(round.settle(vm))
        assertEquals(1, vm.roundsPlayed())
        assertFalse(vm.isGameOver())
        assertTrue(round.nextRound())
        assertEquals(2, round.roundNumber)
        assertEquals(HeartsPhase.PASSING, round.phase)
        assertEquals(-1, round.passOffset)
        assertEquals(List(3) { 17 }, round.hands.map { it.size })
    }

    @Test
    fun laPartieSArreteA100PointsEtLePlusPetitTotalGagne() {
        val vm = viewModel()
        vm.setCell(0, 0, 95, false)
        vm.setCell(0, 1, 40, false)
        vm.setCell(0, 2, 10, false)
        assertFalse(vm.isGameOver())
        val hearts = (2..7).map { c(heart, it) }
        val round = table(
            listOf(c(club, 5)), listOf(c(club, 9)), listOf(c(club, 13)),
            taken = listOf(hearts, emptyList(), emptyList())
        )
        round.play(c(club, 5))
        round.reveal()
        round.play(c(club, 9))
        round.reveal()
        round.play(c(club, 13))
        round.collectTrick()
        assertTrue(round.settle(vm))
        assertEquals(101, vm.totals[0])
        assertTrue(vm.isGameOver())
        assertEquals(listOf(2), vm.winners())
    }

    // ---------- Progression et reprise ----------

    @Test
    fun uneManchePartielleReprendApresLesManchesDejaInscrites() {
        val vm = viewModel()
        for (round in 0..1) for (player in 0..2) vm.setCell(round, player, 0, false)
        val resumed = vm.heartsRound()
        assertEquals(3, resumed.roundNumber)
        assertTrue(resumed === vm.heartsRound())
    }

    @Test
    fun uneNouvellePartieEffaceLaMancheEnCours() {
        val vm = viewModel()
        val first = vm.heartsRound()
        assertFalse(vm.hasProgress())
        first.reveal()
        assertTrue(first.confirmPass(first.currentHand.take(3)))
        assertTrue(vm.hasProgress())
        vm.initGame(listOf("Anna", "Ben", "Cléo"), rules())
        assertFalse(first === vm.heartsRound())
        assertFalse(vm.hasProgress())
    }

    // ---------- Jeu jouable ----------

    @Test
    fun coeursEstUnJeuJouableSurUnSeulTelephoneAvecSaFeuilleDeScores() {
        val game = rules()
        assertTrue(game.isPlayableGame())
        assertFalse(game.isMultiPhone())
        assertTrue(game.hasScoreSheet())
        assertEquals(GameCategory.CARDS, game.category())
        assertEquals(3, game.minPlayers)
        assertEquals(6, game.maxPlayers)
    }
}
