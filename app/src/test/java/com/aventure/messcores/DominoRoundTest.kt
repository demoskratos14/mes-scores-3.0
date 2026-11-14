package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Domino jouable tour par tour : les 28 dominos, l'ouverture, les dominos posés, la pioche, le blocage, les points et le tableau. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DominoRoundTest {

    private fun t(a: Int, b: Int): Int = DominoScoring.tileOf(a, b)

    private fun placed(a: Int, b: Int): PlacedDomino = PlacedDomino(t(a, b), a, b)

    /** Une manche dont le jeu n'est pas mélangé : les dominos 0, 1, 2… sont distribués dans l'ordre. */
    private fun round(players: Int = 2): DominoRound = DominoRound(players) { }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == DOMINOS_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Table précise : mains, pioche et chaîne déjà posée ; [current] joue. */
    private fun table(
        hands: List<List<Int>>,
        chain: List<PlacedDomino> = listOf(placed(2, 3)),
        stock: List<Int> = emptyList(),
        current: Int = 0
    ): DominoRound = round(hands.size).also { it.load(hands, stock, chain, current) }

    // ---------- Les dominos ----------

    @Test
    fun lesVingtHuitDominosSontTousDifferents() {
        val sides = (0 until DominoScoring.TILES).map { DominoScoring.low(it) to DominoScoring.high(it) }
        assertEquals(28, sides.toSet().size)
        assertTrue(sides.all { (low, high) -> low in 0..6 && high in 0..6 && low <= high })
        assertEquals(0 to 0, sides.first())
        assertEquals(6 to 6, sides.last())
    }

    @Test
    fun unDominoSeRetrouveDansLesDeuxSens() {
        assertEquals(t(1, 3), t(3, 1))
        assertEquals(1, DominoScoring.low(t(3, 1)))
        assertEquals(3, DominoScoring.high(t(3, 1)))
        assertTrue(DominoScoring.isDouble(t(4, 4)))
        assertFalse(DominoScoring.isDouble(t(4, 5)))
        assertEquals(9, DominoScoring.points(t(4, 5)))
        assertEquals(0, DominoScoring.points(t(0, 0)))
        assertEquals(12, DominoScoring.points(t(6, 6)))
        assertEquals(9 + 3, DominoScoring.handPoints(listOf(t(4, 5), t(1, 2))))
        assertTrue(DominoScoring.matches(t(2, 5), 5))
        assertFalse(DominoScoring.matches(t(2, 5), 3))
    }

    @Test
    fun uneImageExistePourChaqueDomino() {
        val images = (0 until DominoScoring.TILES).map { dominoImage(it) }
        assertEquals(28, images.toSet().size)
    }

    @Test
    fun lesMainsDependentDuNombreDeJoueurs() {
        assertEquals(7, DominoScoring.handSize(2))
        assertEquals(5, DominoScoring.handSize(3))
        assertEquals(5, DominoScoring.handSize(4))
    }

    // ---------- Distribution et ouverture ----------

    @Test
    fun laDistributionDonneSeptDominosAChacunADeuxJoueurs() {
        val r = round(2)
        // Le plus fort double distribué est 2-2 (dans la main de Ben) : il est posé tout de suite.
        assertEquals(1, r.opener)
        assertEquals(t(2, 2), r.openingTile)
        assertEquals(listOf(7, 6), r.hands.map { it.size })
        assertEquals(14, r.stock.size)
        assertEquals(listOf(placed(2, 2)), r.chain)
        assertEquals(2, r.leftEnd)
        assertEquals(2, r.rightEnd)
        // C'est ensuite au joueur suivant, main cachée.
        assertEquals(0, r.current)
        assertFalse(r.revealed)
        assertFalse(r.roundOver)
        assertFalse(r.inProgress)
    }

    @Test
    fun laDistributionDonneCinqDominosAChacunATroisOuQuatreJoueurs() {
        val three = round(3)
        assertEquals(3, three.hands.size)
        assertEquals(13, three.stock.size)
        assertEquals(15 - 1, three.hands.sumOf { it.size })
        val four = round(4)
        assertEquals(8, four.stock.size)
        assertEquals(20 - 1, four.hands.sumOf { it.size })
        // Aucun domino n'est perdu ni en double.
        val all = four.hands.flatten() + four.stock + four.chain.map { it.tile }
        assertEquals(28, all.toSet().size)
        assertEquals(28, all.size)
    }

    @Test
    fun lePlusFortDoubleOuvreSinonLeDominoLePlusLourd() {
        assertTrue(DominoScoring.openingKey(t(0, 0)) > DominoScoring.openingKey(t(5, 6)))
        assertTrue(DominoScoring.openingKey(t(3, 3)) > DominoScoring.openingKey(t(2, 2)))
        assertTrue(DominoScoring.openingKey(t(5, 6)) > DominoScoring.openingKey(t(4, 6)))
        assertTrue(DominoScoring.openingKey(t(4, 6)) > DominoScoring.openingKey(t(5, 4)))
        assertTrue(DominoScoring.openingKey(t(5, 6)) > DominoScoring.openingKey(t(3, 6)))
    }

    // ---------- Poser un domino ----------

    @Test
    fun onNePoseRienAvantDeVoirSaMainNiUnDominoQuOnNAPas() {
        val r = table(listOf(listOf(t(3, 5), t(0, 1)), listOf(t(6, 6))))
        assertFalse(r.play(t(3, 5), atLeft = false))
        r.reveal()
        assertFalse(r.play(t(6, 6), atLeft = false))
        assertEquals(listOf(placed(2, 3)), r.chain)
    }

    @Test
    fun unDominoQuiNeCorrespondPasEstRefuseSansRienChanger() {
        val r = table(listOf(listOf(t(4, 5), t(0, 1)), listOf(t(6, 6))))
        r.reveal()
        assertFalse(r.play(t(4, 5), atLeft = true))
        assertFalse(r.play(t(4, 5), atLeft = false))
        assertEquals(listOf(placed(2, 3)), r.chain)
        assertEquals(listOf(t(4, 5), t(0, 1)), r.hands[0])
        assertEquals(0, r.current)
        assertTrue(r.revealed)
        assertFalse(r.inProgress)
    }

    @Test
    fun onPoseADroiteEtLeTourPasseAuSuivant() {
        val r = table(listOf(listOf(t(3, 5), t(0, 1)), listOf(t(6, 6))))
        r.reveal()
        assertTrue(r.play(t(3, 5), atLeft = false))
        // La moitié 3 touche le bout droit 3 : le nouveau bout droit est 5.
        assertEquals(listOf(placed(2, 3), placed(3, 5)), r.chain)
        assertEquals(2, r.leftEnd)
        assertEquals(5, r.rightEnd)
        assertEquals(listOf(t(0, 1)), r.hands[0])
        assertEquals(1, r.current)
        assertFalse(r.revealed)
        assertTrue(r.inProgress)
    }

    @Test
    fun onPoseAGaucheEnRetournantLeDominoSiBesoin() {
        val r = table(listOf(listOf(t(2, 4), t(0, 1)), listOf(t(6, 6))))
        r.reveal()
        assertTrue(r.play(t(2, 4), atLeft = true))
        // La moitié 2 touche le bout gauche 2 : le 4 se retrouve à gauche.
        assertEquals(listOf(placed(4, 2), placed(2, 3)), r.chain)
        assertEquals(4, r.leftEnd)
        assertEquals(3, r.rightEnd)
    }

    @Test
    fun unDominoQuiSePoseAuxDeuxBoutsChoisitSonBout() {
        val r = table(listOf(listOf(t(2, 3), t(0, 1)), listOf(t(6, 6))), chain = listOf(placed(2, 3)))
        r.reveal()
        assertTrue(r.fitsLeft(t(2, 3)))
        assertTrue(r.fitsRight(t(2, 3)))
        assertTrue(r.play(t(2, 3), atLeft = true))
        assertEquals(listOf(placed(3, 2), placed(2, 3)), r.chain)
    }

    @Test
    fun unDoubleSePoseDansLesDeuxSens() {
        val r = table(listOf(listOf(t(3, 3), t(0, 1)), listOf(t(6, 6))))
        r.reveal()
        assertTrue(r.play(t(3, 3), atLeft = false))
        assertEquals(placed(3, 3), r.chain.last())
        assertEquals(3, r.rightEnd)
    }

    // ---------- Piocher et passer ----------

    @Test
    fun onNePiochePasQuandOnAUnDominoAPoser() {
        val r = table(listOf(listOf(t(3, 5)), listOf(t(6, 6))), stock = listOf(t(0, 0)))
        r.reveal()
        assertTrue(r.canPlayAny)
        assertFalse(r.draw())
        assertEquals(listOf(t(0, 0)), r.stock)
    }

    @Test
    fun sansDominoAPoserOnPiocheEtOnResteALaMain() {
        val r = table(listOf(listOf(t(0, 1)), listOf(t(6, 6))), stock = listOf(t(4, 5), t(2, 6)))
        r.reveal()
        assertFalse(r.canPlayAny)
        assertTrue(r.draw())
        assertEquals(listOf(t(0, 1), t(4, 5)), r.hands[0])
        assertEquals(listOf(t(2, 6)), r.stock)
        assertEquals(0, r.current)
        assertTrue(r.revealed)
        // Le domino pioché ne va pas non plus : on pioche encore, puis le suivant se pose.
        assertTrue(r.draw())
        assertTrue(r.canPlayAny)
        assertTrue(r.stock.isEmpty())
        assertFalse(r.draw())
        assertTrue(r.play(t(2, 6), atLeft = true))
        assertEquals(1, r.current)
    }

    @Test
    fun onNePassePasTantQuIlResteDeLaPiocheOuUnDominoAPoser() {
        val withStock = table(listOf(listOf(t(0, 1)), listOf(t(6, 6))), stock = listOf(t(4, 5)))
        withStock.reveal()
        assertFalse(withStock.pass())
        val withTile = table(listOf(listOf(t(3, 5)), listOf(t(6, 6))))
        withTile.reveal()
        assertFalse(withTile.pass())
        assertEquals(0, withTile.current)
    }

    @Test
    fun onPasseQuandOnNePeutRienFaireEtQueLaPiocheEstVide() {
        val r = table(listOf(listOf(t(0, 1)), listOf(t(6, 6))))
        r.reveal()
        assertTrue(r.pass())
        assertEquals(1, r.current)
        assertFalse(r.revealed)
        assertFalse(r.roundOver)
    }

    // ---------- Fin de manche et points ----------

    @Test
    fun celuiQuiPoseSonDernierDominoMarqueLesPointsDesAutres() {
        val r = table(listOf(listOf(t(2, 4)), listOf(t(5, 6), t(0, 1))))
        r.reveal()
        assertTrue(r.play(t(2, 4), atLeft = true))
        assertTrue(r.roundOver)
        assertFalse(r.blocked)
        assertFalse(r.revealed)
        assertEquals(0, r.winner)
        assertEquals(listOf(11 + 1, 0), r.roundPoints())
        // Plus aucun coup une fois la manche finie.
        r.reveal()
        assertFalse(r.pass())
        assertFalse(r.draw())
    }

    @Test
    fun avecTroisJoueursLeGagnantMarqueLaSommeDesDeuxAutres() {
        val r = table(listOf(listOf(t(2, 4)), listOf(t(5, 6)), listOf(t(1, 1), t(0, 3))))
        r.reveal()
        r.play(t(2, 4), atLeft = true)
        assertEquals(listOf(11 + 2 + 3, 0, 0), r.roundPoints())
    }

    @Test
    fun laMancheBloqueeDonneLaDifferenceAuPlusLeger() {
        val r = table(
            hands = listOf(listOf(t(1, 2)), listOf(t(3, 4), t(5, 5))),
            chain = listOf(placed(0, 0))
        )
        r.reveal()
        assertTrue(r.pass())
        r.reveal()
        assertFalse(r.roundOver)
        assertTrue(r.pass())
        assertTrue(r.roundOver)
        assertTrue(r.blocked)
        assertEquals(0, r.winner)
        // Anna a 3 points, Ben 17 : Anna marque la différence, 14.
        assertEquals(listOf(14, 0), r.roundPoints())
    }

    @Test
    fun laMancheBloqueeATroisJoueursCompteLaDifferenceAvecChacun() {
        val r = table(
            hands = listOf(listOf(t(1, 2)), listOf(t(3, 5)), listOf(t(4, 6))),
            chain = listOf(placed(0, 0)),
            current = 1
        )
        repeat(3) {
            r.reveal()
            assertTrue(r.pass())
        }
        assertTrue(r.blocked)
        assertEquals(0, r.winner)
        // 3 points contre 8 et 10 : (8 - 3) + (10 - 3).
        assertEquals(listOf(12, 0, 0), r.roundPoints())
    }

    @Test
    fun uneManchePasseeNeSeBloquePasTantQuUnJoueurPeutJouer() {
        val r = table(
            hands = listOf(listOf(t(1, 2)), listOf(t(0, 3)), listOf(t(4, 5))),
            chain = listOf(placed(0, 0)),
            current = 0
        )
        r.reveal()
        assertTrue(r.pass())
        r.reveal()
        // Ben peut poser son 0-3 : il ne peut pas passer.
        assertFalse(r.pass())
        assertTrue(r.play(t(0, 3), atLeft = false))
        assertTrue(r.roundOver)
        assertFalse(r.blocked)
    }

    @Test
    fun uneEgaliteAuMoinsDeMancheBloqueeNeDonneRienADesPersonne() {
        val r = table(
            hands = listOf(listOf(t(0, 3)), listOf(t(1, 2))),
            chain = listOf(placed(5, 5))
        )
        r.reveal()
        r.pass()
        r.reveal()
        r.pass()
        assertTrue(r.blocked)
        assertEquals(-1, r.winner)
        assertEquals(listOf(0, 0), r.roundPoints())
    }

    // ---------- Manches et scores ----------

    @Test
    fun lesPointsDeLaMancheSontInscritsAuTableauUneSeuleFois() {
        val vm = viewModel()
        val r = table(listOf(listOf(t(2, 4)), listOf(t(5, 6), t(0, 1))))
        assertFalse(r.settle(vm))
        r.reveal()
        r.play(t(2, 4), atLeft = true)
        assertTrue(r.settle(vm))
        assertTrue(r.settled)
        assertEquals(listOf(12, 0), vm.totals)
        assertEquals(listOf(12, 0), vm.scores[0].map { it.baseValue })
        assertFalse(r.settle(vm))
        assertEquals(listOf(12, 0), vm.totals)
        assertEquals(1, vm.roundsPlayed())
    }

    @Test
    fun laMancheSuivanteEstDistribueeApresInscription() {
        val vm = viewModel()
        val r = table(listOf(listOf(t(2, 4)), listOf(t(5, 6), t(0, 1))))
        r.reveal()
        r.play(t(2, 4), atLeft = true)
        assertFalse(r.nextRound())
        r.settle(vm)
        assertTrue(r.nextRound())
        assertEquals(2, r.roundNumber)
        assertFalse(r.roundOver)
        assertFalse(r.settled)
        assertFalse(r.blocked)
        assertEquals(1, r.chain.size)
        assertEquals(listOf(7, 6).sum(), r.hands.sumOf { it.size })
        assertEquals(14, r.stock.size)
        assertFalse(r.revealed)
        assertFalse(r.inProgress)
    }

    @Test
    fun laPartieSeTermineAuSeuilEtLePlusGrandTotalGagne() {
        val vm = viewModel()
        vm.setCell(0, 0, 95, false)
        vm.setCell(0, 1, 40, false)
        assertFalse(vm.isGameOver())
        val r = table(listOf(listOf(t(2, 4)), listOf(t(5, 6), t(0, 1))))
        r.reveal()
        r.play(t(2, 4), atLeft = true)
        assertTrue(r.settle(vm))
        assertEquals(listOf(107, 40), vm.totals)
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    // ---------- Progression et reprise ----------

    @Test
    fun laManchePasseEnCoursDesQuOnJoueUnCoup() {
        val r = table(listOf(listOf(t(3, 5), t(0, 1)), listOf(t(6, 6))))
        assertFalse(r.inProgress)
        r.reveal()
        assertFalse(r.inProgress)
        r.play(t(3, 5), atLeft = false)
        assertTrue(r.inProgress)
    }

    @Test
    fun laPartieEnCoursVitDansLeViewModel() {
        val vm = viewModel()
        val first = vm.dominoRound()
        assertTrue(first === vm.dominoRound())
        assertFalse(vm.hasProgress())
        first.reveal()
        first.load(listOf(listOf(t(3, 5), t(0, 1)), listOf(t(6, 6))), emptyList(), listOf(placed(2, 3)))
        first.reveal()
        first.play(t(3, 5), atLeft = false)
        assertTrue(vm.hasProgress())
        vm.initGame(listOf("Anna", "Ben"), rules())
        assertFalse(vm.dominoRound() === first)
    }

    @Test
    fun leDominoEstUnJeuJouableSurUnSeulTelephone() {
        val rules = rules()
        assertTrue(rules.isPlayableGame())
        assertTrue(rules.hasScoreSheet())
        assertFalse(rules.isMultiPhone())
        assertTrue(DOMINOS_ID in GAME_ICON_IDS)
    }
}
