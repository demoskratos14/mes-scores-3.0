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

/** Flip 7 jouable tour par tour : paquet, points, doublons, Seconde chance, Gel, Pioche trois, Flip 7 et tableau des scores. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class Flip7RoundTest {

    private val freeze = Flip7Cards.FREEZE
    private val flipThree = Flip7Cards.FLIP_THREE
    private val secondChance = Flip7Cards.SECOND_CHANCE

    /** Une manche à [players] joueurs, paquet non mélangé. */
    private fun round(players: Int = 3): Flip7Round = Flip7Round(players, dealer = 0) { }

    /** Table vide à trois joueurs, pioche [stock] (la première carte sort d'abord), c'est à [current] de jouer. */
    private fun table(stock: List<Int>, current: Int = 0): Flip7Round = round().also { it.load(stock, current) }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == FLIP7_ID }

    private fun viewModel() = ScoreViewModel().also { it.initGame(listOf("Anna", "Ben", "Chloé"), rules()) }

    // ---------- Le paquet et les points ----------

    @Test
    fun lePaquetA94CartesCommeLeJeu() {
        val deck = Flip7Cards.deck()
        assertEquals(94, deck.size)
        assertEquals(1, deck.count { it == 0 })
        for (value in 1..12) assertEquals(value, deck.count { it == value })
        assertEquals(3, deck.count { it == freeze })
        assertEquals(3, deck.count { it == flipThree })
        assertEquals(3, deck.count { it == secondChance })
        for (bonus in Flip7Cards.PLUS_2..Flip7Cards.TIMES_2) assertEquals(1, deck.count { it == bonus })
    }

    @Test
    fun lesPointsAdditionnentLesNombresPuisLesBonus() {
        assertEquals(11, Flip7Cards.score(listOf(3, 8), emptyList(), flip7 = false))
        assertEquals(15, Flip7Cards.score(listOf(3, 8), listOf(Flip7Cards.PLUS_4), flip7 = false))
        // ×2 double seulement les nombres, les +N s'ajoutent après.
        assertEquals(24, Flip7Cards.score(listOf(3, 8), listOf(Flip7Cards.TIMES_2, Flip7Cards.PLUS_2), flip7 = false))
        assertEquals(11 + 15, Flip7Cards.score(listOf(3, 8), emptyList(), flip7 = true))
        assertEquals(2 + 4 + 6 + 8 + 10, listOf(Flip7Cards.PLUS_2, Flip7Cards.PLUS_4, Flip7Cards.PLUS_6, Flip7Cards.PLUS_8, Flip7Cards.PLUS_10).sumOf { Flip7Cards.bonusValue(it) })
    }

    @Test
    fun laDonneDonneUneCarteAChacunEtLeJoueurApresLeDonneurCommence() {
        val r = round()
        // Paquet non mélangé : 0, 1, 2 sont donnés dans l'ordre aux joueurs 1, 2 puis 0.
        assertEquals(listOf(listOf(2), listOf(0), listOf(1)), r.numbers)
        assertEquals(1, r.current)
        assertEquals(1, r.firstPlayer)
        assertFalse(r.roundOver)
        assertFalse(r.inProgress)
    }

    // ---------- Piocher, s'arrêter, sauter ----------

    @Test
    fun piocherAjouteLaCarteEtPasseLeTour() {
        val r = table(listOf(5, 7, 9))
        assertTrue(r.hit())
        assertEquals(listOf(5), r.numbers[0])
        assertEquals(1, r.current)
        assertTrue(r.inProgress)
        assertEquals(5, r.pointsOf(0))
    }

    @Test
    fun unDoublonFaitSauterEtMarque0() {
        val r = table(listOf(5, 7, 5))
        r.hit() // joueur 0 : 5
        r.hit() // joueur 1 : 7
        r.stay() // joueur 2 s'arrête sans carte
        r.hit() // joueur 0 : 5 encore
        assertEquals(Flip7Status.BUSTED, r.status[0])
        assertEquals(0, r.pointsOf(0))
        assertEquals(Flip7EventKind.BUST, r.lastEvent?.kind)
        // Le tour passe au joueur 1.
        assertEquals(1, r.current)
    }

    @Test
    fun laSecondeChanceAnnuleLeDoublonEtPartAvecLui() {
        val r = table(listOf(secondChance, 7, 4, 5, 6, 6))
        r.hit() // joueur 0 : Seconde chance
        assertTrue(secondChance in r.extras[0])
        r.hit() // joueur 1 : 7
        r.hit() // joueur 2 : 4
        r.hit() // joueur 0 : 5
        r.stay() // joueur 1 s'arrête
        r.stay() // joueur 2 s'arrête
        r.hit() // joueur 0 : 6
        assertEquals(Flip7Status.ACTIVE, r.status[0])
        // Joueur 0 pioche un second 6 : sauvé par la Seconde chance.
        r.hit()
        assertEquals(Flip7Status.ACTIVE, r.status[0])
        assertFalse(secondChance in r.extras[0])
        assertEquals(listOf(5, 6), r.numbers[0])
        assertEquals(Flip7EventKind.SAVED, r.lastEvent?.kind)
        assertTrue(6 in r.discard && secondChance in r.discard)
    }

    @Test
    fun sArreterMarqueLesPointsEtSortDeLaManche() {
        val r = table(listOf(9, 10, 11))
        r.hit()
        r.hit()
        r.hit()
        assertTrue(r.stay()) // joueur 0 avec 9
        assertEquals(Flip7Status.STAYED, r.status[0])
        assertEquals(9, r.pointsOf(0))
        assertEquals(1, r.current)
        // Un joueur arrêté ne rejoue plus : le tour saute du joueur 2 au joueur 1.
        r.stay() // joueur 1
        assertEquals(2, r.current)
        r.stay() // joueur 2
        assertTrue(r.roundOver)
        assertEquals(listOf(9, 10, 11), r.roundPoints())
        assertEquals(2, r.winner)
        assertFalse(r.hit())
    }

    // ---------- Les cartes Action ----------

    @Test
    fun gelDemandeUneCibleQuiMarqueSesPointsEtSort() {
        val r = table(listOf(8, freeze, 3))
        r.hit() // joueur 0 : 8
        r.hit() // joueur 1 : Gel -> choix
        val pending = r.pending
        assertNotNull(pending)
        assertEquals(Flip7Choice.FREEZE, pending!!.choice)
        assertEquals(1, pending.source)
        assertEquals(listOf(0, 1, 2), pending.targets)
        assertFalse(r.canAct)
        assertFalse(r.hit())
        assertTrue(r.chooseTarget(0))
        assertEquals(Flip7Status.FROZEN, r.status[0])
        assertEquals(8, r.pointsOf(0))
        assertTrue(freeze in r.extras[0])
        assertNull(r.pending)
        // Joueur 1 avait la main, le tour passe au suivant en jeu : le joueur 2.
        assertEquals(2, r.current)
    }

    @Test
    fun onPeutSeGelerSoiMeme() {
        val r = table(listOf(freeze, 4))
        r.hit()
        assertTrue(r.chooseTarget(0))
        assertEquals(Flip7Status.FROZEN, r.status[0])
        assertEquals(1, r.current)
    }

    @Test
    fun uneCibleHorsDeLaListeEstRefusee() {
        val r = table(listOf(freeze, 4))
        r.hit()
        assertFalse(r.chooseTarget(7))
        assertNotNull(r.pending)
    }

    @Test
    fun piocheTroisFaitPiocherTroisCartesALaCible() {
        val r = table(listOf(flipThree, 4, 5, 6, 9))
        r.hit() // joueur 0 : Pioche trois
        assertEquals(Flip7Choice.FLIP_THREE, r.pending?.choice)
        r.chooseTarget(1)
        assertEquals(listOf(4, 5, 6), r.numbers[1])
        assertTrue(flipThree in r.extras[1])
        assertEquals(1, r.current)
        assertEquals(1, r.stock.size)
    }

    @Test
    fun piocheTroisSArreteSurUnDoublon() {
        val r = table(listOf(5, flipThree, 5, 9, 9))
        r.hit() // joueur 0 : 5
        r.stay() // joueur 1
        r.stay() // joueur 2
        r.hit() // joueur 0 : Pioche trois
        r.chooseTarget(0) // sur lui-même : 5 (doublon, il saute), les deux autres cartes ne sont pas piochées
        assertEquals(Flip7Status.BUSTED, r.status[0])
        assertEquals(2, r.stock.size)
        assertTrue(r.roundOver)
    }

    @Test
    fun lesGelEtPiocheTroisPiochesPendantPiocheTroisSeJouentApresLaTroisiemeCarte() {
        val r = table(listOf(flipThree, 4, freeze, 6, 9))
        r.hit() // joueur 0 : Pioche trois
        r.chooseTarget(0) // sur lui-même : 4, Gel (en attente), 6
        assertEquals(listOf(4, 6), r.numbers[0])
        val pending = r.pending
        assertNotNull(pending)
        assertEquals(Flip7Choice.FREEZE, pending!!.choice)
        assertEquals(0, pending.source)
        r.chooseTarget(1)
        assertEquals(Flip7Status.FROZEN, r.status[1])
        assertNull(r.pending)
        assertEquals(2, r.current)
    }

    @Test
    fun uneDeuxiemeSecondeChanceSeDonneAUnAutreJoueur() {
        val r = table(listOf(secondChance, 7, 4, secondChance, 3))
        r.hit() // joueur 0 : Seconde chance (gardée)
        r.hit() // joueur 1 : 7
        r.hit() // joueur 2 : 4
        r.hit() // joueur 0 : une deuxième Seconde chance
        val pending = r.pending
        assertEquals(Flip7Choice.GIVE_SECOND_CHANCE, pending?.choice)
        assertEquals(listOf(1, 2), pending?.targets)
        r.chooseTarget(2)
        assertEquals(listOf(secondChance), r.extras[2])
        assertEquals(listOf(secondChance), r.extras[0])
    }

    @Test
    fun sansPersonneAQuiLaDonnerUneSecondeChanceEnTropEstDefaussee() {
        val r = table(listOf(secondChance, 7, 4, 5, secondChance))
        r.hit() // joueur 0 : Seconde chance
        r.hit() // joueur 1 : 7
        r.hit() // joueur 2 : 4
        r.hit() // joueur 0 : 5
        r.stay() // joueur 1
        r.stay() // joueur 2
        // Seul le joueur 0 est encore en jeu : la deuxième Seconde chance n'a pas preneur.
        r.hit()
        assertNull(r.pending)
        assertEquals(Flip7EventKind.SECOND_CHANCE_LOST, r.lastEvent?.kind)
        assertTrue(secondChance in r.discard)
    }

    // ---------- Flip 7 ----------

    @Test
    fun septNombresDifferentsFontUnFlip7QuiFinitLaManche() {
        val r = table(listOf(1, 2, 3, 4, 5, 6, 7), current = 1)
        r.stay() // joueur 1
        r.stay() // joueur 2
        repeat(7) { assertTrue(r.hit()) } // le joueur 0 reste seul en jeu
        assertTrue(r.roundOver)
        assertEquals(0, r.flip7Player)
        assertEquals(28 + 15, r.pointsOf(0))
        assertEquals(0, r.winner)
        assertEquals(Flip7EventKind.FLIP7, r.lastEvent?.kind)
        assertEquals(-1, r.current)
    }

    @Test
    fun unJoueurEncoreEnJeuMarqueSesCartesQuandUnAutreFaitUnFlip7() {
        val r = table(listOf(1, 10, 2, 11, 3, 12, 4, 9, 5, 8, 6, 0, 7), current = 0)
        r.hit() // joueur 0 : 1
        r.hit() // joueur 1 : 10
        r.stay() // joueur 2
        repeat(5) {
            r.hit() // joueur 0
            r.hit() // joueur 1
        }
        r.hit() // joueur 0 : son septième nombre différent
        assertTrue(r.roundOver)
        assertEquals(0, r.flip7Player)
        assertEquals(Flip7Status.ACTIVE, r.status[1])
        assertEquals(10 + 11 + 12 + 9 + 8 + 0, r.pointsOf(1))
        assertEquals(28 + 15, r.pointsOf(0))
        assertEquals(0, r.pointsOf(2))
    }

    // ---------- Manches, scores et paquet ----------

    @Test
    fun laMancheFinieSInscritUneFoisAuTableauEtLaSuivanteRepart() {
        val vm = viewModel()
        val r = Flip7Round(3, dealer = 0, startRound = 1) { }
        r.load(listOf(9, 10, 11), current = 0)
        r.hit()
        r.hit()
        r.hit()
        r.stay()
        r.stay()
        r.stay()
        assertTrue(r.roundOver)
        assertFalse(r.nextRound())
        assertTrue(r.settle(vm))
        assertFalse(r.settle(vm))
        assertEquals(9, vm.totalFor(0))
        assertEquals(10, vm.totalFor(1))
        assertEquals(11, vm.totalFor(2))
        assertTrue(r.nextRound())
        assertEquals(2, r.roundNumber)
        assertFalse(r.roundOver)
        // Chaque joueur a de nouveau une seule carte devant lui ; les cartes de la manche d'avant sont défaussées.
        assertTrue(r.numbers.all { it.size <= 1 })
        assertTrue(listOf(9, 10, 11).all { it in r.discard || it in r.numbers.flatten() })
    }

    @Test
    fun laPartieSArreteALaFinDeLaMancheOu200EstAtteint() {
        val vm = viewModel()
        vm.setCell(0, 0, 195, false)
        vm.setCell(0, 1, 100, false)
        vm.setCell(0, 2, 50, false)
        assertFalse(vm.isGameOver())
        val r = Flip7Round(3, dealer = 0, startRound = 2) { }
        r.load(listOf(12, 3, 4), current = 0)
        r.hit()
        r.hit()
        r.hit()
        r.stay()
        r.stay()
        r.stay()
        r.settle(vm)
        assertEquals(207, vm.totalFor(0))
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun laPiocheVideSeRemelangeAvecLaDefausse() {
        val r = round()
        r.load(listOf(5), current = 0, discard = listOf(6, 7))
        r.hit() // joueur 0 : 5, la pioche est vide
        r.hit() // joueur 1 : la défausse est remélangée
        assertEquals(2, r.numbers[1].size + r.stock.size)
        assertTrue(r.discard.isEmpty())
        assertEquals(1, r.numbers[1].size)
    }

    @Test
    fun sansAucuneCarteLaMancheSArrete() {
        val r = round()
        r.load(emptyList(), current = 0)
        r.hit()
        assertTrue(r.roundOver)
        assertEquals(Flip7EventKind.DECK_EMPTY, r.lastEvent?.kind)
    }

    @Test
    fun flip7EstUnJeuJouableSurUnSeulTelephone() {
        val rules = rules()
        assertTrue(rules.isPlayableGame())
        assertFalse(rules.isMultiPhone())
        assertTrue(rules.hasScoreSheet())
    }
}
