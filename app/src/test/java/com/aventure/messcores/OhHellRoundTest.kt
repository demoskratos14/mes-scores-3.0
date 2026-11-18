package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Oh Hell jouable tour par tour : manches, annonces (dont l'annonce interdite au donneur), plis, atout, points, tableau des scores. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class OhHellRoundTest {

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val club = FrenchCards.CLUB

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == OH_HELL_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben", "Cléo")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Le joueur qui agit montre sa main puis annonce [value]. */
    private fun OhHellRound.revealAndBid(value: Int): Boolean {
        reveal()
        return bid(value)
    }

    /** Le joueur qui agit montre sa main puis pose [card]. */
    private fun OhHellRound.revealAndPlay(card: Int): Boolean {
        reveal()
        return play(card)
    }

    // ---------- Les manches ----------

    @Test
    fun leNombreDeCartesMonteJusquAuMaximumPuisRedescend() {
        assertEquals(17, OhHellScoring.maxCards(3))
        assertEquals(12, OhHellScoring.maxCards(4))
        assertEquals(10, OhHellScoring.maxCards(5))
        assertEquals(8, OhHellScoring.maxCards(6))
        assertEquals(7, OhHellScoring.maxCards(7))
        assertEquals(23, OhHellScoring.totalRounds(4))
        assertEquals(13, OhHellScoring.totalRounds(7))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3, 2, 1), (1..13).map { OhHellScoring.cardsFor(it, 7) })
        // Au-delà de la dernière manche, on reste à 1 carte.
        assertEquals(1, OhHellScoring.cardsFor(99, 7))
    }

    @Test
    fun leDonneurChangeAChaqueManche() {
        assertEquals(listOf(0, 1, 2, 0, 1), (1..5).map { OhHellScoring.dealer(it, 3) })
    }

    @Test
    fun laDonneDistribueLesCartesDeLaMancheEtRetourneUnAtoutQuiNEstDansAucuneMain() {
        val round = OhHellRound(4, startRound = 3)
        assertEquals(3, round.cardCount)
        assertEquals(List(4) { 3 }, round.hands.map { it.size })
        val all = round.hands.flatten() + round.trump
        assertEquals(13, all.size)
        assertEquals(13, all.distinct().size)
        assertEquals(OhHellPhase.BIDDING, round.phase)
        assertEquals(List(4) { -1 }, round.bids)
    }

    @Test
    fun leJoueurApresLeDonneurAnnonceLePremier() {
        // Manche 1 à 3 joueurs : le donneur est 0, donc 1 annonce le premier.
        val round = OhHellRound(3) { }
        assertEquals(0, round.dealer)
        assertEquals(1, round.firstPlayer)
        assertEquals(1, round.current)
        // Manche 2 : le donneur est 1, donc 2 annonce le premier.
        val second = OhHellRound(3, startRound = 2) { }
        assertEquals(1, second.dealer)
        assertEquals(2, second.current)
    }

    // ---------- Les annonces ----------

    @Test
    fun lAnnonceInterditeAuDernierEstCelleQuiFeraitEgalerLeTotalEtLeNombreDePlis() {
        assertEquals(0, OhHellScoring.forbiddenBid(listOf(1, 2, -1), 3))
        assertEquals(2, OhHellScoring.forbiddenBid(listOf(1, -1, 0), 3))
        // Pas le dernier à annoncer : aucune annonce interdite.
        assertEquals(-1, OhHellScoring.forbiddenBid(listOf(1, -1, -1), 3))
        // Les autres ont déjà annoncé plus que le nombre de plis : rien n'est interdit.
        assertEquals(-1, OhHellScoring.forbiddenBid(listOf(3, 2, -1), 3))
    }

    @Test
    fun lesAnnoncesSeFontDansLOrdreAvecLeDonneurEnDernierEtLeJeuDesPlisCommence() {
        val round = OhHellRound(3) { }
        // 1 carte chacun : 1, puis 2, puis le donneur 0.
        assertFalse(round.bid(0)) // il faut avoir vu sa main
        assertTrue(round.revealAndBid(1))
        assertEquals(2, round.current)
        assertFalse(round.revealAndBid(2)) // plus que le nombre de plis
        assertTrue(round.revealAndBid(0))
        assertEquals(0, round.current)
        assertEquals(0, round.forbiddenBid) // 1 + 0 + 0 ferait 1 = le nombre de plis
        assertFalse(round.isBidAllowed(0))
        assertFalse(round.revealAndBid(0))
        assertEquals(OhHellPhase.BIDDING, round.phase)
        assertTrue(round.revealAndBid(1))
        assertEquals(listOf(1, 1, 0), round.bids)
        assertEquals(OhHellPhase.PLAYING, round.phase)
        // Celui qui a annoncé le premier ouvre le premier pli.
        assertEquals(1, round.current)
    }

    @Test
    fun uneAnnonceSurUnAutreTelephoneNeSePrendQueAuTourDuJoueur() {
        val round = OhHellRound(3) { }
        assertFalse(round.bidFor(2, 0)) // pas son tour
        assertTrue(round.bidFor(1, 0))
        assertTrue(round.bidFor(2, 1))
        assertEquals(0, round.current)
        // Le donneur : 0 + 1 + x ne doit pas faire 1, donc x ne peut pas être 0.
        assertFalse(round.bidFor(0, 0))
        assertTrue(round.bidFor(0, 1))
        assertEquals(OhHellPhase.PLAYING, round.phase)
    }

    // ---------- Les cartes jouables et les plis ----------

    @Test
    fun ilFautFournirLaCouleurDemandeeSiOnLaEtNImporteQuoiSinon() {
        val trick = listOf(OhHellPlay(0, c(club, 9)))
        val hand = listOf(c(club, 3), c(heart, 5), c(spade, 2))
        assertTrue(OhHellScoring.canPlay(c(club, 3), hand, trick))
        assertFalse(OhHellScoring.canPlay(c(heart, 5), hand, trick))
        assertFalse(OhHellScoring.canPlay(c(spade, 2), hand, trick))
        // Sans trèfle en main, tout est permis (un atout par exemple).
        val noClub = listOf(c(heart, 5), c(spade, 2))
        assertTrue(OhHellScoring.canPlay(c(heart, 5), noClub, trick))
        assertTrue(OhHellScoring.canPlay(c(spade, 2), noClub, trick))
        // On peut ouvrir avec n'importe quelle carte, atout compris, et on ne joue que ce qu'on a.
        assertTrue(OhHellScoring.canPlay(c(heart, 5), hand, emptyList()))
        assertFalse(OhHellScoring.canPlay(c(diamond, 5), hand, emptyList()))
    }

    @Test
    fun leMeilleurAtoutRamasseLePliSinonLaPlusForteCarteDeLaCouleurDemandee() {
        // Atout : cœur. Pas d'atout dans le pli : l'as de trèfle (demandé) bat le roi.
        val noTrump = listOf(OhHellPlay(0, c(club, 13)), OhHellPlay(1, c(club, 1)), OhHellPlay(2, c(spade, 1)))
        assertEquals(1, OhHellScoring.trickWinner(noTrump, heart))
        // Un petit atout bat la couleur demandée, et le plus gros atout bat le petit.
        val withTrump = listOf(OhHellPlay(0, c(club, 1)), OhHellPlay(1, c(heart, 2)), OhHellPlay(2, c(heart, 7)))
        assertEquals(2, OhHellScoring.trickWinner(withTrump, heart))
        // Une carte d'une autre couleur (ni demandée ni atout) ne gagne jamais.
        val discard = listOf(OhHellPlay(0, c(club, 4)), OhHellPlay(1, c(spade, 1)), OhHellPlay(2, c(club, 3)))
        assertEquals(0, OhHellScoring.trickWinner(discard, heart))
    }

    @Test
    fun unPliSeJoueSeRamasseEtCommenceLeSuivantAvecLeGagnant() {
        val round = OhHellRound(3) { }
        round.load(
            hands = listOf(listOf(c(club, 9), c(spade, 4)), listOf(c(club, 12), c(spade, 5)), listOf(c(diamond, 3), c(spade, 6))),
            trump = c(diamond, 2),
            current = 0,
            bids = listOf(1, 0, 1)
        )
        assertEquals(OhHellPhase.PLAYING, round.phase)
        assertEquals(2, round.cardCount)
        assertFalse(round.play(c(club, 9))) // il faut d'abord voir sa main
        assertFalse(round.revealAndPlay(c(spade, 9))) // pas dans sa main
        assertTrue(round.revealAndPlay(c(club, 9)))
        assertEquals(1, round.current)
        assertFalse(round.revealAndPlay(c(spade, 5))) // il a du trèfle
        assertTrue(round.revealAndPlay(c(club, 12)))
        // Le joueur 2 n'a pas de trèfle : son carreau est l'atout, il ramasse.
        assertTrue(round.revealAndPlay(c(diamond, 3)))
        assertTrue(round.trickDone)
        assertEquals(2, round.trickWinner)
        assertFalse(round.isPlayable(c(spade, 6))) // personne ne joue avant que le pli soit ramassé
        assertFalse(round.collectFor(0))
        assertTrue(round.collectFor(2))
        assertEquals(listOf(0, 0, 1), round.tricksWon)
        assertEquals(2, round.current)
        assertFalse(round.roundOver)
    }

    // ---------- Les points et le tableau des scores ----------

    @Test
    fun lesPointsDeLaMancheViennentDeLAnnonceEtDesPlisRealises() {
        val round = OhHellRound(3) { }
        round.load(
            hands = listOf(listOf(c(club, 9)), listOf(c(club, 12)), listOf(c(diamond, 3))),
            trump = c(diamond, 2),
            current = 0,
            bids = listOf(0, 0, 1)
        )
        assertEquals(listOf(0, 0, 0), round.roundPoints())
        round.revealAndPlay(c(club, 9))
        round.revealAndPlay(c(club, 12))
        round.revealAndPlay(c(diamond, 3))
        assertTrue(round.collectTrick())
        assertTrue(round.roundOver)
        // Annonce exacte : 10 + l'annonce ; le joueur 2 a annoncé et fait 1 pli.
        assertEquals(listOf(10, 10, 11), round.roundPoints())
        assertEquals(listOf(10, 10, 11), round.tableFor(0).points)
    }

    @Test
    fun uneAnnonceRateeCouteUnPointParPliDEcart() {
        val round = OhHellRound(3) { }
        round.load(
            hands = listOf(listOf(c(club, 9)), listOf(c(club, 12)), listOf(c(club, 3))),
            trump = c(diamond, 2),
            current = 0,
            bids = listOf(1, 0, 0)
        )
        round.revealAndPlay(c(club, 9))
        round.revealAndPlay(c(club, 12))
        round.revealAndPlay(c(club, 3))
        round.collectTrick()
        // Le joueur 1 ramasse son pli sans l'avoir annoncé ; le joueur 0 l'avait annoncé sans le faire.
        assertEquals(listOf(-1, -1, 10), round.roundPoints())
    }

    @Test
    fun lesPointsSontInscritsUneSeuleFoisPuisLaMancheSuivanteEstDistribuee() {
        val vm = viewModel()
        val round = OhHellRound(3) { }
        round.load(
            hands = listOf(listOf(c(club, 9)), listOf(c(club, 12)), listOf(c(diamond, 3))),
            trump = c(diamond, 2),
            current = 0,
            bids = listOf(0, 0, 1)
        )
        assertFalse(round.settle(vm))
        round.revealAndPlay(c(club, 9))
        round.revealAndPlay(c(club, 12))
        round.revealAndPlay(c(diamond, 3))
        round.collectTrick()
        assertFalse(round.nextRound()) // pas avant l'inscription
        assertTrue(round.settle(vm))
        assertEquals(listOf(10, 10, 11), vm.totals)
        assertFalse(round.settle(vm))
        assertEquals(1, vm.roundsPlayed())
        assertFalse(round.finished)
        assertTrue(round.nextRound())
        assertEquals(2, round.roundNumber)
        assertEquals(2, round.cardCount)
        assertEquals(OhHellPhase.BIDDING, round.phase)
        assertEquals(List(3) { -1 }, round.bids)
    }

    @Test
    fun uneAnnonceRateeSInscritEnNegatifDansLeTableau() {
        val vm = viewModel()
        val round = OhHellRound(3) { }
        round.load(
            hands = listOf(listOf(c(club, 9)), listOf(c(club, 12)), listOf(c(club, 3))),
            trump = c(diamond, 2),
            current = 0,
            bids = listOf(1, 0, 0)
        )
        round.revealAndPlay(c(club, 9))
        round.revealAndPlay(c(club, 12))
        round.revealAndPlay(c(club, 3))
        round.collectTrick()
        assertTrue(round.settle(vm))
        assertEquals(listOf(-1, -1, 10), vm.totals)
    }

    // ---------- La fin de la partie ----------

    @Test
    fun laPartieSArreteApresLaDerniereMancheDuCycle() {
        val vm = viewModel()
        // Dernière manche à 3 joueurs : 1 carte chacune, donneur 2, donc 0 annonce le premier.
        val round = OhHellRound(3, startRound = OhHellScoring.totalRounds(3)) { }
        assertEquals(1, round.cardCount)
        assertFalse(round.finished)
        assertTrue(round.revealAndBid(0))
        assertTrue(round.revealAndBid(0))
        // Le donneur annonce en dernier : 0 + 0 + 1 ferait 1, le nombre de plis, donc 1 lui est interdit.
        assertFalse(round.revealAndBid(1))
        assertTrue(round.revealAndBid(0))
        round.revealAndPlay(round.currentHand.first())
        round.revealAndPlay(round.currentHand.first())
        round.revealAndPlay(round.currentHand.first())
        assertTrue(round.collectTrick())
        assertTrue(round.roundOver)
        assertFalse(round.finished) // pas avant l'inscription
        assertTrue(round.settle(vm))
        assertTrue(round.finished)
        assertFalse(round.nextRound())
    }

    @Test
    fun unePartieRepriseApresToutesSesManchesEstDejaFinie() {
        val round = OhHellRound(3, startRound = OhHellScoring.totalRounds(3) + 1) { }
        assertTrue(round.finished)
        assertEquals(1, round.cardCount)
        assertTrue(round.tableFor(-1).finished)
    }

    // ---------- Ce que voit chaque joueur ----------

    @Test
    fun chaqueJoueurNeVoitQueSaMainTrieeEtLePublic() {
        val round = OhHellRound(3, startRound = 3) { }
        val mine = round.tableFor(1)
        assertEquals(round.hands[1].toSet(), mine.hand.toSet())
        assertEquals(List(3) { 3 }, mine.handSizes)
        assertEquals(round.trump, mine.trump)
        // Un spectateur ne voit aucune main.
        assertTrue(round.tableFor(-1).hand.isEmpty())
        // La main est triée par couleur puis par force.
        val sorted = mine.hand.sortedWith(compareBy({ FrenchCards.suit(it) }, { FrenchCards.strength(it) }))
        assertEquals(sorted, mine.hand)
    }

    // ---------- Jeu jouable ----------

    @Test
    fun ohHellEstUnJeuJouableSurPlusieursTelephonesAvecSaFeuilleDeScores() {
        val game = rules()
        assertTrue(game.isPlayableGame())
        assertTrue(game.isMultiPhone())
        assertTrue(game.hasScoreSheet())
        assertEquals(GameCategory.CARDS, game.category())
        assertEquals(3, game.minPlayers)
        assertEquals(7, game.maxPlayers)
    }

    @Test
    fun laPartieOuverteEstLaMemeJusquAuNouveauDepart() {
        val vm = viewModel()
        val first = vm.ohHellRound()
        assertTrue(first === vm.ohHellRound())
        assertFalse(vm.hasProgress())
        first.revealAndBid(0)
        assertTrue(vm.hasProgress())
        vm.initGame(listOf("Anna", "Ben", "Cléo"), rules())
        assertFalse(first === vm.ohHellRound())
        assertFalse(vm.hasProgress())
    }
}
