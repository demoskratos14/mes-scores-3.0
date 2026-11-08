package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Yaniv jouable tour par tour : points des cartes, défausses permises, pioche, « Yaniv » et « Assaf », tableau des scores. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class YanivRoundTest {

    private val spade = FrenchCards.SPADE
    private val heart = FrenchCards.HEART
    private val diamond = FrenchCards.DIAMOND
    private val club = FrenchCards.CLUB
    private val jokerA = 52
    private val jokerB = 53

    private fun c(suit: Int, rank: Int): Int = FrenchCards.card(suit, rank)

    /** Une manche à [players] joueurs dont le paquet n'est pas mélangé : les cartes 0, 1, 2… sont distribuées dans l'ordre. */
    private fun round(players: Int = 3, startPlayer: Int = 0): YanivRound = YanivRound(players, startPlayer) { }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == YANIV_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben", "Chloé")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Table à trois joueurs : mains [a], [b] et [c], dernière défausse [top], c'est à [current] de jouer. */
    private fun table(
        a: List<Int>,
        b: List<Int>,
        cc: List<Int>,
        top: List<Int> = listOf(c(club, 9)),
        stock: List<Int> = listOf(c(diamond, 2), c(diamond, 3), c(diamond, 4)),
        current: Int = 0,
        pile: List<Int> = emptyList()
    ): YanivRound = round().also { it.load(listOf(a, b, cc), stock, top, current, pile) }

    // ---------- Règles des cartes ----------

    @Test
    fun lesCartesValentLeursPointsEnMain() {
        assertEquals(0, YanivScoring.points(jokerA))
        assertEquals(0, YanivScoring.points(jokerB))
        assertEquals(1, YanivScoring.points(c(spade, 1)))
        assertEquals(7, YanivScoring.points(c(heart, 7)))
        assertEquals(10, YanivScoring.points(c(diamond, 10)))
        assertEquals(10, YanivScoring.points(c(club, 11)))
        assertEquals(10, YanivScoring.points(c(spade, 12)))
        assertEquals(10, YanivScoring.points(c(heart, 13)))
        assertEquals(1 + 7 + 0 + 10, YanivScoring.handPoints(listOf(c(spade, 1), c(heart, 7), jokerA, c(club, 13))))
        assertEquals(0, YanivScoring.handPoints(emptyList()))
        assertTrue(YanivScoring.isJoker(jokerA))
        assertFalse(YanivScoring.isJoker(c(club, 13)))
    }

    @Test
    fun uneCarteSeuleEstToujoursUneDefausseValide() {
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 5))))
        assertTrue(YanivScoring.isValidDiscard(listOf(jokerA)))
        assertFalse(YanivScoring.isValidDiscard(emptyList()))
    }

    @Test
    fun unePaireUnBrelanOuUnCarreSontDesCartesDeMemeValeur() {
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(heart, 7))))
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(heart, 7), c(club, 7))))
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(heart, 7), c(club, 7), c(diamond, 7))))
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(heart, 8))))
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(heart, 7), c(club, 8))))
    }

    @Test
    fun leJokerRemplaceUneCarteDansUneBrelanOuUnePaire() {
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 7), jokerA)))
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(heart, 7), jokerA)))
        assertTrue(YanivScoring.isValidDiscard(listOf(jokerA, jokerB)))
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(heart, 8), jokerA)))
    }

    @Test
    fun uneSuiteAuMoinsTroisCartesDeLaMemeCouleur() {
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 4), c(spade, 5), c(spade, 6))))
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 6), c(spade, 4), c(spade, 5))))
        // Deux cartes qui se suivent ne font pas une suite.
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 4), c(spade, 5))))
        // Il manque le 6.
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 4), c(spade, 5), c(spade, 7))))
        // Deux couleurs.
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 4), c(heart, 5), c(spade, 6))))
        // L'as ne vaut que 1 : pas de suite qui passe du roi à l'as.
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 13), c(spade, 1), c(spade, 2))))
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 1), c(spade, 2), c(spade, 3))))
        assertTrue(YanivScoring.isValidDiscard(listOf(c(spade, 11), c(spade, 12), c(spade, 13))))
    }

    @Test
    fun leJokerBoucheUnTrouOuProlongeLaSuite() {
        assertEquals(
            listOf(c(spade, 4), jokerA, c(spade, 6)),
            YanivScoring.orderedRun(listOf(c(spade, 6), jokerA, c(spade, 4)))
        )
        assertEquals(
            listOf(c(spade, 4), c(spade, 5), jokerA),
            YanivScoring.orderedRun(listOf(jokerA, c(spade, 5), c(spade, 4)))
        )
        // Au roi, le joker prolonge la suite du côté de l'as.
        assertEquals(
            listOf(jokerA, c(spade, 12), c(spade, 13)),
            YanivScoring.orderedRun(listOf(c(spade, 13), c(spade, 12), jokerA))
        )
        assertNull(YanivScoring.orderedRun(listOf(c(spade, 2), c(spade, 9), jokerA)))
    }

    @Test
    fun deuxCartesIdentiquesNeSontPasUneDefausse() {
        assertFalse(YanivScoring.isValidDiscard(listOf(c(spade, 7), c(spade, 7))))
    }

    @Test
    fun surUneSuiteOnNeReprendQueLesExtremitesSurUnBrelanN_importeLaquelle() {
        val run = YanivScoring.arrange(listOf(c(spade, 5), c(spade, 4), c(spade, 6)))
        assertEquals(listOf(c(spade, 4), c(spade, 5), c(spade, 6)), run)
        assertEquals(listOf(c(spade, 4), c(spade, 6)), YanivScoring.drawable(run))
        val set = YanivScoring.arrange(listOf(c(heart, 7), c(spade, 7), c(club, 7)))
        assertEquals(set, YanivScoring.drawable(set))
        val single = listOf(c(club, 9))
        assertEquals(single, YanivScoring.drawable(single))
        val pair = YanivScoring.arrange(listOf(c(heart, 7), c(spade, 7)))
        assertEquals(pair, YanivScoring.drawable(pair))
    }

    @Test
    fun lesDeuxJokersOntChacunLeurImageEtLesAutresCartesGardentLaLeur() {
        assertEquals(R.drawable.fr_card_joker_black, yanivCardImage(jokerA))
        assertEquals(R.drawable.fr_card_joker_red, yanivCardImage(jokerB))
        assertEquals(frenchCardImage(c(heart, 7)), yanivCardImage(c(heart, 7)))
        assertEquals(frenchCardImage(0), yanivCardImage(0))
    }

    // ---------- Distribution ----------

    @Test
    fun lesCinquanteQuatreCartesSontDistribueesCinqChacunEtUneRetournee() {
        val round = round(players = 3)
        assertEquals(List(3) { 5 }, round.hands.map { it.size })
        assertEquals(listOf(0, 1, 2, 3, 4), round.hands[0])
        assertEquals(listOf(15), round.lastDiscard)
        // 54 - 15 cartes distribuées - 1 carte retournée.
        assertEquals(38, round.stock.size)
        val all = round.hands.flatten() + round.stock + round.lastDiscard
        assertEquals(YanivScoring.DECK, all.toSet().size)
        assertEquals(YanivScoring.DECK, all.size)
        assertEquals(0, round.current)
        assertFalse(round.revealed)
        assertEquals(YanivPhase.DISCARD, round.phase)
    }

    @Test
    fun huitJoueursPeuventJouerAvecLePaquet() {
        val round = round(players = 8)
        assertEquals(List(8) { 5 }, round.hands.map { it.size })
        assertEquals(54 - 40 - 1, round.stock.size)
    }

    // ---------- Le tour ----------

    @Test
    fun onNeJoueQuApresAvoirVuSaMain() {
        val round = table(listOf(c(spade, 7), c(heart, 7), c(club, 13)), listOf(c(club, 1)), listOf(c(club, 2)))
        assertFalse(round.discard(listOf(c(spade, 7))))
        assertFalse(round.drawFromStock())
        round.reveal()
        assertTrue(round.discard(listOf(c(spade, 7))))
    }

    @Test
    fun uneDefausseRefuseeNeChangeRien() {
        val hand = listOf(c(spade, 7), c(heart, 8), c(club, 13))
        val round = table(hand, listOf(c(club, 1)), listOf(c(club, 2)))
        round.reveal()
        // Deux cartes qui ne vont pas ensemble.
        assertFalse(round.discard(listOf(c(spade, 7), c(heart, 8))))
        // Une carte qu'il n'a pas.
        assertFalse(round.discard(listOf(c(diamond, 7))))
        assertEquals(hand, round.hands[0])
        assertEquals(YanivPhase.DISCARD, round.phase)
        assertFalse(round.inProgress)
    }

    @Test
    fun apresSaDefausseLeJoueurPiocheSurLaPiocheEtLeTourPasseAuSuivant() {
        val round = table(
            a = listOf(c(spade, 7), c(heart, 7), c(club, 13)),
            b = listOf(c(club, 1)),
            cc = listOf(c(club, 2))
        )
        round.reveal()
        assertTrue(round.discard(listOf(c(heart, 7), c(spade, 7))))
        assertEquals(listOf(c(club, 13)), round.hands[0])
        assertEquals(YanivPhase.DRAW, round.phase)
        assertEquals(0, round.current)
        assertTrue(round.inProgress)
        // Une seconde défausse n'est pas permise : il faut piocher.
        assertFalse(round.discard(listOf(c(club, 13))))
        assertFalse(round.callYaniv())

        assertTrue(round.drawFromStock())
        assertEquals(listOf(c(club, 13), c(diamond, 2)), round.hands[0])
        assertEquals(listOf(c(spade, 7), c(heart, 7)), round.lastDiscard)
        // L'ancienne défausse (le 9 de trèfle) rejoint les défausses plus anciennes.
        assertEquals(listOf(c(club, 9)), round.pile)
        assertEquals(listOf(c(diamond, 3), c(diamond, 4)), round.stock)
        assertEquals(1, round.current)
        assertFalse(round.revealed)
        assertEquals(YanivPhase.DISCARD, round.phase)
    }

    @Test
    fun onNePiochePasAvantDeDefausser() {
        val round = table(listOf(c(spade, 7), c(club, 13)), listOf(c(club, 1)), listOf(c(club, 2)))
        round.reveal()
        assertFalse(round.drawFromStock())
        assertFalse(round.takeFromDiscard(c(club, 9)))
    }

    @Test
    fun onPeutReprendreUneCarteDeLaDefausseDuJoueurPrecedent() {
        val round = table(
            a = listOf(c(spade, 7), c(club, 13)),
            b = listOf(c(club, 1)),
            cc = listOf(c(club, 2)),
            top = listOf(c(heart, 4), c(heart, 5), c(heart, 6))
        )
        round.reveal()
        round.discard(listOf(c(spade, 7)))
        // Au milieu d'une suite : refusé, rien ne change.
        assertFalse(round.takeFromDiscard(c(heart, 5)))
        assertEquals(YanivPhase.DRAW, round.phase)
        assertEquals(listOf(c(club, 13)), round.hands[0])
        // Au bout de la suite : permis.
        assertTrue(round.takeFromDiscard(c(heart, 4)))
        assertEquals(listOf(c(club, 13), c(heart, 4)), round.hands[0])
        assertEquals(listOf(c(spade, 7)), round.lastDiscard)
        // Les autres cartes de la suite rejoignent les anciennes défausses ; la pioche n'a pas bougé.
        assertEquals(listOf(c(heart, 5), c(heart, 6)), round.pile)
        assertEquals(3, round.stock.size)
        assertEquals(1, round.current)
    }

    @Test
    fun surUnePaireOnPeutReprendreN_importeLaquelle() {
        val round = table(
            a = listOf(c(spade, 7), c(club, 13)),
            b = listOf(c(club, 1)),
            cc = listOf(c(club, 2)),
            top = listOf(c(heart, 4), c(club, 4))
        )
        round.reveal()
        round.discard(listOf(c(spade, 7)))
        assertTrue(round.takeFromDiscard(c(club, 4)))
        assertEquals(listOf(c(heart, 4)), round.pile)
    }

    @Test
    fun laPiocheVideEstRemelangeeAvecLesAnciennesDefausses() {
        val round = table(
            a = listOf(c(spade, 7), c(club, 13)),
            b = listOf(c(club, 1)),
            cc = listOf(c(club, 2)),
            top = listOf(c(heart, 9)),
            stock = emptyList(),
            pile = listOf(c(diamond, 5), c(diamond, 6), c(diamond, 8))
        )
        round.reveal()
        round.discard(listOf(c(spade, 7)))
        assertTrue(round.canDrawFromStock)
        assertTrue(round.drawFromStock())
        assertEquals(listOf(c(club, 13), c(diamond, 5)), round.hands[0])
        assertEquals(listOf(c(diamond, 6), c(diamond, 8)), round.stock)
        // La défausse retirée de la table est la seule ancienne défausse.
        assertEquals(listOf(c(heart, 9)), round.pile)
        assertEquals(listOf(c(spade, 7)), round.lastDiscard)
    }

    @Test
    fun sansRienAPiocherOnReprendUneCarteDeLaDefausse() {
        val round = table(
            a = listOf(c(spade, 7), c(club, 13)),
            b = listOf(c(club, 1)),
            cc = listOf(c(club, 2)),
            stock = emptyList()
        )
        round.reveal()
        round.discard(listOf(c(spade, 7)))
        assertFalse(round.canDrawFromStock)
        assertFalse(round.drawFromStock())
        assertEquals(YanivPhase.DRAW, round.phase)
        assertTrue(round.takeFromDiscard(c(club, 9)))
        assertEquals(1, round.current)
    }

    // ---------- « Yaniv » et « Assaf » ----------

    @Test
    fun onNeAnnonceYanivQueAvecCinqPointsOuMoinsEnDebutDeTour() {
        val round = table(listOf(c(spade, 13)), listOf(c(club, 1)), listOf(c(club, 2)))
        assertFalse(round.canCallYaniv)
        round.reveal()
        assertFalse(round.canCallYaniv)
        assertFalse(round.callYaniv())
        assertFalse(round.roundOver)

        val ok = table(listOf(c(spade, 2), c(heart, 3)), listOf(c(club, 1)), listOf(c(club, 2)))
        assertFalse(ok.callYaniv())
        ok.reveal()
        assertEquals(5, ok.currentPoints)
        assertTrue(ok.canCallYaniv)
    }

    @Test
    fun unYanivReussiFaitMarquerZeroAuJoueurEtSaMainAuxAutres() {
        val round = table(
            a = listOf(c(spade, 2), c(heart, 3)),
            b = listOf(c(diamond, 6)),
            cc = listOf(c(club, 13))
        )
        round.reveal()
        assertTrue(round.callYaniv())
        assertTrue(round.roundOver)
        assertEquals(0, round.caller)
        assertEquals(-1, round.assafBy)
        assertEquals(0, round.winner)
        assertEquals(listOf(0, 6, 10), round.roundPoints())
        // La manche est finie : plus de coup possible.
        round.reveal()
        assertFalse(round.discard(listOf(c(diamond, 6))))
        assertFalse(round.callYaniv())
    }

    @Test
    fun unAssafFaitPrendreTrenteDePenaliteAuJoueurEtZeroAuMeilleur() {
        val round = table(
            a = listOf(c(spade, 2), c(heart, 3)),
            b = listOf(c(diamond, 4), jokerA),
            cc = listOf(c(club, 1))
        )
        round.reveal()
        assertTrue(round.callYaniv())
        // Ben a 4 points, Chloé 1 : c'est elle qui a le moins.
        assertEquals(2, round.assafBy)
        assertEquals(2, round.winner)
        assertEquals(listOf(5 + 30, 4, 0), round.roundPoints())
    }

    @Test
    fun avecAutantDePointsQueCeluiQuiAnnonceOnEstAssaf() {
        val round = table(
            a = listOf(c(spade, 3)),
            b = listOf(c(diamond, 3)),
            cc = listOf(c(club, 9))
        )
        round.reveal()
        round.callYaniv()
        assertEquals(1, round.assafBy)
        assertEquals(listOf(3 + 30, 0, 9), round.roundPoints())
    }

    @Test
    fun aEgaliteLePremierApresCeluiQuiAnnonceBatSonAnnonce() {
        // Anna annonce 5 ; Ben et Chloé ont 5 aussi : Ben est le premier après Anna.
        val round = table(
            a = listOf(c(spade, 2), c(heart, 3)),
            b = listOf(c(diamond, 5)),
            cc = listOf(c(club, 2), c(club, 3))
        )
        round.reveal()
        round.callYaniv()
        assertEquals(1, round.assafBy)
        assertEquals(listOf(35, 0, 5), round.roundPoints())

        // Si c'est Ben qui annonce, le premier après lui est Chloé, puis Anna.
        val second = table(
            a = listOf(c(spade, 2), c(heart, 3)),
            b = listOf(c(diamond, 5)),
            cc = listOf(c(club, 2), c(club, 3)),
            current = 1
        )
        second.reveal()
        second.callYaniv()
        assertEquals(2, second.assafBy)
    }

    @Test
    fun lesPointsDeLaMancheEnCoursSontCeuxDesMains() {
        val round = table(listOf(c(spade, 2)), listOf(c(diamond, 4)), listOf(c(club, 9)))
        assertEquals(listOf(2, 4, 9), round.roundPoints())
        assertEquals(-1, round.winner)
    }

    // ---------- Manches et scores ----------

    @Test
    fun lesPointsDeLaMancheSontInscritsAuTableauUneSeuleFois() {
        val vm = viewModel()
        val round = table(
            a = listOf(c(spade, 2), c(heart, 3)),
            b = listOf(c(diamond, 6)),
            cc = listOf(c(club, 13))
        )
        assertFalse(round.settle(vm))
        round.reveal()
        round.callYaniv()
        assertTrue(round.settle(vm))
        assertTrue(round.settled)
        assertEquals(listOf(0, 6, 10), vm.totals)
        assertEquals(listOf(0, 6, 10), vm.scores[0].map { it.baseValue })
        // Une seconde inscription ne change rien.
        assertFalse(round.settle(vm))
        assertEquals(listOf(0, 6, 10), vm.totals)
        assertEquals(1, vm.roundsPlayed())
    }

    @Test
    fun laMancheSuivanteEstDistribueeEtCommenceeParLeJoueurSuivant() {
        val vm = viewModel()
        val round = table(listOf(c(spade, 2)), listOf(c(diamond, 6)), listOf(c(club, 13)))
        // Tant que la manche n'est pas inscrite, on ne passe pas à la suivante.
        assertFalse(round.nextRound())
        round.reveal()
        round.callYaniv()
        assertFalse(round.nextRound())
        round.settle(vm)
        assertTrue(round.nextRound())
        assertEquals(2, round.roundNumber)
        assertFalse(round.roundOver)
        assertFalse(round.settled)
        assertEquals(-1, round.caller)
        assertEquals(-1, round.assafBy)
        assertEquals(1, round.current)
        assertEquals(List(3) { 5 }, round.hands.map { it.size })
        assertEquals(38, round.stock.size)
        assertFalse(round.revealed)
        assertFalse(round.inProgress)
    }

    @Test
    fun laPartieSeTermineAuSeuilEtLePlusPetitTotalGagne() {
        val vm = viewModel()
        // Ben a déjà 195 points : 6 de plus et il atteint 201.
        vm.setCell(0, 0, 10, false)
        vm.setCell(0, 1, 195, false)
        vm.setCell(0, 2, 20, false)
        assertFalse(vm.isGameOver())
        val round = table(
            a = listOf(c(spade, 2), c(heart, 3)),
            b = listOf(c(diamond, 6)),
            cc = listOf(c(club, 13))
        )
        round.reveal()
        round.callYaniv()
        assertTrue(round.settle(vm))
        assertEquals(listOf(10, 201, 30), vm.totals)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
        assertEquals(2, vm.roundsPlayed())
    }

    // ---------- Progression et reprise ----------

    @Test
    fun laMancheEstEnCoursDesQuOnJoueUnCoup() {
        val round = table(listOf(c(spade, 7), c(club, 13)), listOf(c(club, 1)), listOf(c(club, 2)))
        assertFalse(round.inProgress)
        round.reveal()
        assertFalse(round.inProgress)
        round.discard(listOf(c(spade, 7)))
        assertTrue(round.inProgress)
    }

    @Test
    fun laPartieOuverteGardeLaMemeMancheTantQuOnNeLaFermePas() {
        val vm = viewModel()
        val first = vm.yanivRound()
        assertTrue(first === vm.yanivRound())
        assertEquals(1, first.roundNumber)
        assertEquals(0, first.current)
    }

    @Test
    fun unePartieRepriseContinueApresLesManchesDejaInscrites() {
        val vm = viewModel()
        // Deux manches déjà inscrites au tableau : la troisième est distribuée, commencée par le troisième joueur.
        vm.setCell(0, 0, 0, false)
        vm.setCell(0, 1, 5, false)
        vm.setCell(0, 2, 12, false)
        vm.setCell(1, 0, 8, false)
        vm.setCell(1, 1, 0, false)
        vm.setCell(1, 2, 3, false)
        val resumed = vm.yanivRound()
        assertEquals(3, resumed.roundNumber)
        assertEquals(2, resumed.current)
    }

    @Test
    fun uneNouvellePartieEffaceLaMancheEnCours() {
        val vm = viewModel()
        val first = vm.yanivRound()
        assertFalse(vm.hasProgress())
        first.load(
            listOf(listOf(c(spade, 7), c(club, 13)), listOf(c(club, 1)), listOf(c(club, 2))),
            listOf(c(diamond, 2)),
            listOf(c(club, 9))
        )
        first.reveal()
        assertTrue(first.discard(listOf(c(spade, 7))))
        // Une manche entamée, même sans score inscrit, fait demander confirmation en quittant.
        assertTrue(vm.hasProgress())
        vm.initGame(listOf("Anna", "Ben"), rules())
        assertFalse(first === vm.yanivRound())
        assertFalse(vm.hasProgress())
    }

    // ---------- Jeu jouable ----------

    @Test
    fun yanivEstUnJeuJouableSurUnSeulTelephoneAvecSaFeuilleDeScores() {
        val yaniv = rules()
        assertTrue(yaniv.isPlayableGame())
        assertFalse(yaniv.isMultiPhone())
        assertTrue(yaniv.hasScoreSheet())
        assertFalse(yaniv.isDiceOnly())
        assertEquals(GameCategory.CARDS, yaniv.category())
        assertEquals(2, yaniv.minPlayers)
        assertEquals(8, yaniv.maxPlayers)
    }
}
