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

/** Le Huit américain jouable tour par tour : points des cartes, cartes jouables, 8, pioche, passe, fin de manche, tableau des scores. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class HuitRoundTest {

    private val spade = 0
    private val heart = 1
    private val diamond = 2
    private val club = 3

    private fun c(suit: Int, rank: Int): Int = HuitScoring.card(suit, rank)

    /** Une manche à [players] joueurs dont le paquet n'est pas mélangé : les cartes 0, 1, 2… sont distribuées dans l'ordre. */
    private fun round(players: Int = 2, startPlayer: Int = 0): HuitRound = HuitRound(players, startPlayer) { }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == HUIT_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Table à deux joueurs : le joueur 0 a [first], le joueur 1 [second], la dernière carte posée est [top]. */
    private fun table(
        first: List<Int>,
        second: List<Int>,
        top: Int,
        stock: List<Int> = emptyList(),
        discard: List<Int> = listOf(top)
    ): HuitRound = round().also { it.load(listOf(first, second), stock, discard) }

    // ---------- Règles des cartes ----------

    @Test
    fun lesCartesValentLeursPointsEnMain() {
        assertEquals(50, HuitScoring.points(c(spade, 8)))
        assertEquals(10, HuitScoring.points(c(heart, 11)))
        assertEquals(10, HuitScoring.points(c(diamond, 12)))
        assertEquals(10, HuitScoring.points(c(club, 13)))
        assertEquals(1, HuitScoring.points(c(spade, 1)))
        assertEquals(7, HuitScoring.points(c(heart, 7)))
        assertEquals(10, HuitScoring.points(c(spade, 10)))
        assertEquals(50 + 10 + 1 + 5, HuitScoring.handPoints(listOf(c(spade, 8), c(heart, 13), c(diamond, 1), c(club, 5))))
        assertEquals(0, HuitScoring.handPoints(emptyList()))
    }

    @Test
    fun laCarteEtSaCouleurSeRetrouvent() {
        for (card in 0 until HuitScoring.DECK) {
            assertEquals(card, HuitScoring.card(HuitScoring.suit(card), HuitScoring.rank(card)))
        }
        assertEquals(1, HuitScoring.rank(0))
        assertEquals(13, HuitScoring.rank(HuitScoring.DECK - 1))
        assertEquals(club, HuitScoring.suit(HuitScoring.DECK - 1))
    }

    @Test
    fun onDistribueSeptCartesADeuxJoueursEtCinqSinon() {
        assertEquals(7, HuitScoring.handSize(2))
        assertEquals(5, HuitScoring.handSize(3))
        assertEquals(5, HuitScoring.handSize(7))
    }

    @Test
    fun uneCarteSePoseSurLaMemeCouleurLaMemeValeurOuUnHuit() {
        val top = c(heart, 2)
        assertTrue(HuitScoring.isPlayable(c(heart, 9), top, heart))
        assertTrue(HuitScoring.isPlayable(c(spade, 2), top, heart))
        assertTrue(HuitScoring.isPlayable(c(club, 8), top, heart))
        assertFalse(HuitScoring.isPlayable(c(spade, 9), top, heart))
        // Après un 8, c'est la couleur choisie qui compte, pas celle du 8.
        val eight = c(spade, 8)
        assertTrue(HuitScoring.isPlayable(c(diamond, 4), eight, diamond))
        assertFalse(HuitScoring.isPlayable(c(spade, 4), eight, diamond))
    }

    // ---------- Distribution ----------

    @Test
    fun laDistributionDonneLesBonnesMainsEtRetourneUneCarte() {
        val round = round()
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), round.hands[0])
        assertEquals(listOf(7, 8, 9, 10, 11, 12, 13), round.hands[1])
        // Le paquet n'est pas mélangé : la première carte restante est le 2 de cœur.
        assertEquals(c(heart, 2), round.top)
        assertEquals(heart, round.askedSuit)
        assertEquals(HuitScoring.DECK - 14 - 1, round.stock.size)
        assertEquals(0, round.current)
        assertFalse(round.revealed)
        assertFalse(round.roundOver)
    }

    @Test
    fun laPremiereCarteRetourneeNEstJamaisUnHuit() {
        // Le 8 de cœur (carte 20) est mis en tête de la pioche : il reste dans la pioche, la carte suivante est retournée.
        val round = HuitRound(2, 0) { deck ->
            deck.remove(20)
            deck.add(14, 20)
        }
        assertEquals(c(heart, 2), round.top)
        assertTrue(20 in round.stock)
        assertEquals(HuitScoring.DECK - 14 - 1, round.stock.size)
    }

    @Test
    fun aTroisJoueursChacunRecoitCinqCartes() {
        val round = round(players = 3)
        assertEquals(listOf(5, 5, 5), round.hands.map { it.size })
        assertEquals((0 until 15).toList(), round.hands.flatten())
    }

    // ---------- Jouer une carte ----------

    @Test
    fun onNeJoueQuApresAvoirMontreSaMain() {
        val round = round()
        assertFalse(round.play(c(spade, 2)))
        assertNull(round.draw())
        round.reveal()
        assertTrue(round.revealed)
        assertTrue(round.play(c(spade, 2)))
    }

    @Test
    fun uneCarteJouableEstPoseeEtLaMainPasseAuJoueurSuivant() {
        val round = round()
        round.reveal()
        // Le 2 de pique (carte 1) a la même valeur que le 2 de cœur retourné.
        assertTrue(round.play(c(spade, 2)))
        assertEquals(c(spade, 2), round.top)
        assertEquals(spade, round.askedSuit)
        assertEquals(6, round.hands[0].size)
        assertEquals(1, round.current)
        assertFalse(round.revealed)
        assertEquals(listOf(c(heart, 2), c(spade, 2)), round.discard)
    }

    @Test
    fun uneCarteInjouableOuQuiNEstPasEnMainEstRefusee() {
        val round = round()
        round.reveal()
        // L'as de pique n'est ni un cœur, ni un 2, ni un 8.
        assertFalse(round.play(c(spade, 1)))
        // Une carte de l'autre joueur.
        assertFalse(round.play(c(spade, 9)))
        assertEquals(0, round.current)
        assertEquals(7, round.hands[0].size)
        assertEquals(c(heart, 2), round.top)
    }

    @Test
    fun unHuitSeJoueAToutMomentEtDemandeUneCouleur() {
        val round = table(
            first = listOf(c(spade, 8), c(spade, 3)),
            second = listOf(c(club, 4), c(diamond, 6)),
            top = c(heart, 2)
        )
        round.reveal()
        // Sans couleur choisie, le 8 est refusé.
        assertFalse(round.play(c(spade, 8)))
        assertFalse(round.play(c(spade, 8), 9))
        assertTrue(round.play(c(spade, 8), diamond))
        assertEquals(c(spade, 8), round.top)
        assertEquals(diamond, round.askedSuit)
        // Le joueur suivant doit suivre la couleur choisie : le 6 de carreau passe, le 4 de trèfle non.
        round.reveal()
        assertFalse(round.isPlayable(c(club, 4)))
        assertTrue(round.isPlayable(c(diamond, 6)))
    }

    @Test
    fun laDerniereCarteEstUnHuitSansChoisirDeCouleur() {
        val round = table(
            first = listOf(c(spade, 8)),
            second = listOf(c(club, 4)),
            top = c(heart, 2)
        )
        round.reveal()
        assertTrue(round.play(c(spade, 8)))
        assertTrue(round.roundOver)
        assertEquals(0, round.winner)
    }

    // ---------- Piocher et passer ----------

    @Test
    fun onNePiochePasQuandOnPeutJouer() {
        val round = round()
        round.reveal()
        assertTrue(round.hasPlayable)
        assertFalse(round.canDraw)
        assertNull(round.draw())
        assertEquals(7, round.hands[0].size)
    }

    @Test
    fun sansCarteJouableOnPiocheJusquaEnAvoirUne() {
        val round = table(
            first = listOf(c(spade, 3), c(spade, 4)),
            second = listOf(c(club, 4)),
            top = c(heart, 2),
            stock = listOf(c(spade, 11), c(heart, 5), c(diamond, 9))
        )
        round.reveal()
        assertFalse(round.hasPlayable)
        assertTrue(round.canDraw)
        assertFalse(round.mustPass)
        // Le valet de pique ne va pas : on pioche encore.
        assertEquals(c(spade, 11), round.draw())
        assertTrue(round.canDraw)
        // Le 5 de cœur va sur le 2 de cœur : fini de piocher.
        assertEquals(c(heart, 5), round.draw())
        assertTrue(round.hasPlayable)
        assertFalse(round.canDraw)
        assertNull(round.draw())
        assertEquals(4, round.hands[0].size)
        assertEquals(listOf(c(diamond, 9)), round.stock)
        assertEquals(0, round.current)
        assertTrue(round.play(c(heart, 5)))
        assertEquals(1, round.current)
    }

    @Test
    fun quandLaPiocheEstVideOnRemelangeLesCartesPoseesSaufLaDerniere() {
        val round = table(
            first = listOf(c(spade, 3)),
            second = listOf(c(club, 4)),
            top = c(heart, 2),
            stock = emptyList(),
            discard = listOf(c(club, 9), c(diamond, 9), c(spade, 7), c(heart, 2))
        )
        round.reveal()
        assertTrue(round.canDraw)
        // Le paquet de remplacement n'est pas mélangé : on pioche dans l'ordre des cartes posées.
        assertEquals(c(club, 9), round.draw())
        assertEquals(listOf(c(heart, 2)), round.discard)
        assertEquals(listOf(c(diamond, 9), c(spade, 7)), round.stock)
        assertEquals(c(heart, 2), round.top)
    }

    @Test
    fun sansRienAPiocherOnPasseSonTour() {
        val round = table(
            first = listOf(c(spade, 3)),
            second = listOf(c(club, 4)),
            top = c(heart, 2)
        )
        round.reveal()
        assertFalse(round.hasPlayable)
        assertFalse(round.canDraw)
        assertTrue(round.mustPass)
        assertTrue(round.pass())
        assertEquals(1, round.current)
        assertFalse(round.revealed)
        assertFalse(round.roundOver)
    }

    @Test
    fun onNePassePasQuandOnPeutJouerOuPiocher() {
        val round = round()
        round.reveal()
        assertFalse(round.pass())
        assertEquals(0, round.current)
        val drawing = table(
            first = listOf(c(spade, 3)),
            second = listOf(c(club, 4)),
            top = c(heart, 2),
            stock = listOf(c(diamond, 9))
        )
        drawing.reveal()
        assertFalse(drawing.pass())
    }

    @Test
    fun siTousPassentDeSuiteLaMancheEstBloqueeEtLaMainLaPlusLegereGagne() {
        val round = table(
            first = listOf(c(spade, 13), c(spade, 3)),
            second = listOf(c(club, 4), c(club, 5)),
            top = c(heart, 2)
        )
        round.reveal()
        assertTrue(round.pass())
        round.reveal()
        assertTrue(round.pass())
        assertTrue(round.roundOver)
        assertTrue(round.blocked)
        // 4 + 5 = 9 points contre 10 + 3 = 13 : le joueur 1 gagne la manche.
        assertEquals(1, round.winner)
        assertEquals(listOf(13, 0), round.roundPoints())
    }

    // ---------- Fin de manche et scores ----------

    @Test
    fun laMancheFinitQuandUnJoueurPoseSaDerniereCarte() {
        val round = table(
            first = listOf(c(heart, 9)),
            second = listOf(c(club, 8), c(spade, 13), c(diamond, 1)),
            top = c(heart, 2)
        )
        round.reveal()
        assertTrue(round.play(c(heart, 9)))
        assertTrue(round.roundOver)
        assertFalse(round.blocked)
        assertEquals(0, round.winner)
        assertEquals(listOf(0, 50 + 10 + 1), round.roundPoints())
        // Plus personne ne joue.
        round.reveal()
        assertFalse(round.revealed)
        assertFalse(round.play(c(club, 8), heart))
    }

    @Test
    fun lesPointsDeLaMancheSontInscritsAuTableauUneSeuleFois() {
        val vm = viewModel()
        val round = table(
            first = listOf(c(heart, 9)),
            second = listOf(c(club, 8), c(spade, 13)),
            top = c(heart, 2)
        )
        assertFalse(round.settle(vm))
        round.reveal()
        round.play(c(heart, 9))
        assertTrue(round.settle(vm))
        assertTrue(round.settled)
        assertEquals(listOf(0, 60), vm.totals)
        assertEquals(listOf(0, 60), vm.scores[0].map { it.baseValue })
        // Une seconde inscription ne change rien.
        assertFalse(round.settle(vm))
        assertEquals(listOf(0, 60), vm.totals)
        assertEquals(1, vm.roundsPlayed())
    }

    @Test
    fun laMancheSuivanteEstDistribueeEtCommenceeParLeJoueurSuivant() {
        val vm = viewModel()
        val round = table(
            first = listOf(c(heart, 9)),
            second = listOf(c(club, 8)),
            top = c(heart, 2)
        )
        // Tant que la manche n'est pas inscrite, on ne passe pas à la suivante.
        assertFalse(round.nextRound())
        round.reveal()
        round.play(c(heart, 9))
        assertFalse(round.nextRound())
        round.settle(vm)
        assertTrue(round.nextRound())
        assertEquals(2, round.roundNumber)
        assertFalse(round.roundOver)
        assertFalse(round.settled)
        assertEquals(1, round.current)
        assertEquals(7, round.hands[0].size)
        assertEquals(7, round.hands[1].size)
        assertFalse(round.revealed)
    }

    @Test
    fun lesManchesSuivantesVontDansLesLignesSuivantesDuTableau() {
        val vm = viewModel()
        val round = round()
        round.load(listOf(listOf(c(heart, 9)), listOf(c(club, 8))), emptyList(), listOf(c(heart, 2)))
        round.reveal()
        round.play(c(heart, 9))
        round.settle(vm)
        assertEquals(listOf(0, 50), vm.totals)
        assertTrue(round.nextRound())
        // Deuxième manche : le joueur 0 pose le 3 de pique sur le 5 de pique et gagne ; le joueur 1 garde 3 + 4 points.
        round.load(listOf(listOf(c(spade, 3)), listOf(c(club, 3), c(club, 4))), emptyList(), listOf(c(spade, 5)))
        round.reveal()
        assertTrue(round.play(c(spade, 3)))
        assertTrue(round.settle(vm))
        assertEquals(2, vm.roundsPlayed())
        assertEquals(listOf(0, 57), vm.totals)
        assertEquals(listOf(0, 7), vm.scores[1].map { it.baseValue })
    }

    @Test
    fun laPartieSeTermineAuSeuilEtLePlusPetitTotalGagne() {
        val vm = viewModel()
        // Ben a déjà 95 points : 10 de plus et il atteint 100.
        vm.setCell(0, 1, 95, false)
        vm.setCell(0, 0, 20, false)
        assertFalse(vm.isGameOver())
        val round = table(
            first = listOf(c(heart, 9)),
            second = listOf(c(spade, 13)),
            top = c(heart, 2)
        )
        round.reveal()
        round.play(c(heart, 9))
        assertTrue(round.settle(vm))
        assertEquals(listOf(20, 105), vm.totals)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
        assertEquals(2, vm.roundsPlayed())
    }

    // ---------- Progression et reprise ----------

    @Test
    fun laMancheEstEnCoursDesQuOnJoueUnCoup() {
        val round = round()
        assertFalse(round.inProgress)
        round.reveal()
        assertFalse(round.inProgress)
        round.play(c(spade, 2))
        assertTrue(round.inProgress)
    }

    @Test
    fun laPartieOuverteGardeLaMemeMancheTantQuOnNeLaFermePas() {
        val vm = viewModel(listOf("Anna", "Ben", "Chloé"))
        val first = vm.huitRound()
        assertTrue(first === vm.huitRound())
        assertEquals(1, first.roundNumber)
        assertEquals(0, first.current)
    }

    @Test
    fun unePartieRepriseContinueApresLesManchesDejaInscrites() {
        val vm = viewModel(listOf("Anna", "Ben", "Chloé"))
        // Deux manches déjà inscrites au tableau : la troisième est distribuée, commencée par le troisième joueur.
        vm.setCell(0, 0, 0, false)
        vm.setCell(0, 1, 5, false)
        vm.setCell(0, 2, 12, false)
        vm.setCell(1, 0, 8, false)
        vm.setCell(1, 1, 0, false)
        vm.setCell(1, 2, 3, false)
        val resumed = vm.huitRound()
        assertEquals(3, resumed.roundNumber)
        assertEquals(2, resumed.current)
    }

    @Test
    fun uneNouvellePartieEffaceLaMancheEnCours() {
        val vm = viewModel()
        val first = vm.huitRound()
        assertFalse(vm.hasProgress())
        first.load(listOf(listOf(c(heart, 9), c(heart, 3)), listOf(c(club, 8))), emptyList(), listOf(c(heart, 2)))
        first.reveal()
        assertTrue(first.play(c(heart, 9)))
        // Une manche entamée, même sans score inscrit, fait demander confirmation en quittant.
        assertTrue(vm.hasProgress())
        vm.initGame(listOf("Anna", "Ben"), rules())
        assertFalse(first === vm.huitRound())
        assertFalse(vm.hasProgress())
    }

    // ---------- Jeu jouable ----------

    @Test
    fun leHuitAmericainEstUnJeuJouableSurUnSeulTelephoneAvecSaFeuilleDeScores() {
        val huit = rules()
        assertTrue(huit.isPlayableGame())
        assertFalse(huit.isMultiPhone())
        assertTrue(huit.hasScoreSheet())
        assertFalse(huit.isDiceOnly())
        assertEquals(GameCategory.CARDS, huit.category())
        assertEquals(2, huit.minPlayers)
        assertEquals(7, huit.maxPlayers)
    }

    @Test
    fun septJoueursRecoiventCinqCartesChacunEtLaPiocheRestante() {
        val round = round(players = 7)
        assertEquals(List(7) { 5 }, round.hands.map { it.size })
        // 52 - 35 cartes distribuées - 1 carte retournée.
        assertEquals(16, round.stock.size)
        assertFalse(HuitScoring.rank(round.top) == HuitScoring.EIGHT)
    }
}
