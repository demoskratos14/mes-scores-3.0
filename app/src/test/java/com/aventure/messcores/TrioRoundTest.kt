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

/** Trio jouable tour par tour : la distribution, les cartes retournées, les trios, les trois façons de gagner et le tableau. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class TrioRoundTest {

    /** Une manche dont le paquet n'est pas mélangé : 1, 1, 1, 2, 2, 2… sont distribués dans l'ordre. */
    private fun round(players: Int = 3): TrioRound = TrioRound(players) { }

    private fun rules(): GameRules =
        GameRepository(RuntimeEnvironment.getApplication()).builtInGames().first { it.id == TRIO_ID }

    private fun viewModel(names: List<String> = listOf("Anna", "Ben", "Chloé")) =
        ScoreViewModel().also { it.initGame(names, rules()) }

    /** Table précise ; [current] joue. */
    private fun table(
        hands: List<List<Int>>,
        center: List<Int> = emptyList(),
        trios: List<List<Int>> = List(hands.size) { emptyList() },
        current: Int = 0
    ): TrioRound = round(hands.size).also { it.load(hands, center, trios, current) }

    // ---------- Les cartes et leurs liens ----------

    @Test
    fun lesNumerosLiesSontCeuxDesCartes() {
        assertEquals(listOf(9, 8), TrioScoring.linksOf(1))
        assertEquals(listOf(5, 6), TrioScoring.linksOf(2))
        assertEquals(listOf(4, 10), TrioScoring.linksOf(3))
        assertEquals(listOf(11, 3), TrioScoring.linksOf(4))
        assertEquals(listOf(2, 12), TrioScoring.linksOf(5))
        assertTrue(TrioScoring.linksOf(7).isEmpty())
        // Le lien marche dans les deux sens.
        for (a in 1..12) for (b in 1..12) assertEquals("$a-$b", TrioScoring.areLinked(a, b), TrioScoring.areLinked(b, a))
        assertTrue(TrioScoring.areLinked(1, 8))
        assertTrue(TrioScoring.areLinked(12, 5))
        assertFalse(TrioScoring.areLinked(1, 2))
    }

    @Test
    fun laDistributionDependDuNombreDeJoueurs() {
        assertEquals(9, TrioScoring.handSize(3))
        assertEquals(7, TrioScoring.handSize(4))
        assertEquals(6, TrioScoring.handSize(5))
        assertEquals(5, TrioScoring.handSize(6))
        for (n in 3..6) {
            val r = round(n)
            assertEquals(n, r.hands.size)
            assertTrue(r.hands.all { it.size == TrioScoring.handSize(n) })
            assertEquals(TrioScoring.DECK, r.hands.sumOf { it.size } + r.center.size)
            assertEquals(TrioScoring.centerSize(n), r.center.size)
        }
    }

    @Test
    fun leJeuA36CartesEtLesMainsSontRangees() {
        val r = TrioRound(4)
        val all = r.hands.flatten() + r.center
        assertEquals((1..12).flatMap { n -> List(3) { n } }, all.sorted())
        r.hands.forEach { assertEquals(it.sorted(), it) }
    }

    @Test
    fun lePremierJoueurChangeAChaqueManche() {
        assertEquals(0, TrioRound(3, startRound = 1).current)
        assertEquals(1, TrioRound(3, startRound = 2).current)
        assertEquals(2, TrioRound(3, startRound = 3).current)
        assertEquals(0, TrioRound(3, startRound = 4).current)
    }

    // ---------- Retourner une carte ----------

    @Test
    fun onRetourneLaPlusPetiteOuLaPlusGrandeCarteDUneMain() {
        val r = table(listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)))
        assertTrue(r.flipHand(1, high = false))
        assertEquals(listOf(TrioReveal(TrioZone.HAND, 1, 0, 2)), r.reveals)
        // La plus petite carte cachée est maintenant la suivante : on ne retourne jamais deux fois la même carte.
        assertEquals(1, r.lowIndex(1))
        assertEquals(2, r.highIndex(1))
    }

    @Test
    fun uneCarteDuMilieuSeRetourneNImporteLaquelle() {
        val r = table(listOf(listOf(3), listOf(4), listOf(5)), center = listOf(7, 8, 9))
        assertTrue(r.flipCenter(1))
        assertEquals(TrioReveal(TrioZone.CENTER, -1, 1, 8), r.reveals.single())
        assertFalse(r.flipCenter(1))
        assertFalse(r.flipCenter(5))
        assertFalse(r.flipCenter(-1))
    }

    @Test
    fun onPeutRetournerSaPropreMain() {
        val r = table(listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)), current = 0)
        assertTrue(r.flipHand(0, high = true))
        assertEquals(9, r.reveals.single().value)
    }

    @Test
    fun uneMainSansCarteCacheeNeSeRetournePas() {
        val r = table(listOf(emptyList(), listOf(4), listOf(5)))
        assertFalse(r.canFlipHand(0, high = false))
        assertFalse(r.flipHand(0, high = true))
        assertFalse(r.flipHand(7, high = true))
        assertTrue(r.reveals.isEmpty())
    }

    @Test
    fun uneCarteDifferenteTermineLeTourEtToutEstRecache() {
        val r = table(listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)))
        r.flipHand(1, high = false) // 2
        r.flipHand(2, high = false) // 1
        assertTrue(r.mismatch)
        assertEquals(2, r.reveals.size)
        // Tant que le tour n'est pas terminé, on ne retourne plus rien.
        assertFalse(r.flipHand(0, high = false))
        assertFalse(r.flipCenter(0))
        assertEquals(0, r.current)
        assertTrue(r.endTurn())
        assertTrue(r.reveals.isEmpty())
        assertFalse(r.mismatch)
        assertEquals(1, r.current)
        // Les cartes sont restées en place.
        assertEquals(listOf(2, 4, 8), r.hands[1])
    }

    @Test
    fun onNeTermineUnTourQuApresUneErreur() {
        val r = table(listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)))
        assertFalse(r.endTurn())
        r.flipHand(1, high = false)
        assertFalse(r.endTurn())
        assertEquals(0, r.current)
    }

    // ---------- Les trios ----------

    @Test
    fun troisCartesIdentiquesFontUnTrioEtLeJoueurRejoue() {
        val r = table(
            listOf(listOf(3, 5, 9), listOf(3, 4, 8), listOf(1, 6, 7)),
            center = listOf(3, 10, 11),
            current = 0
        )
        assertTrue(r.flipHand(0, high = false)) // 3 (la sienne)
        assertTrue(r.flipHand(1, high = false)) // 3
        assertFalse(r.mismatch)
        assertTrue(r.flipCenter(0)) // 3
        assertEquals(listOf(listOf(3), emptyList<Int>(), emptyList<Int>()), r.trios)
        assertEquals(listOf(5, 9), r.hands[0])
        assertEquals(listOf(4, 8), r.hands[1])
        assertEquals(listOf(10, 11), r.center)
        assertTrue(r.reveals.isEmpty())
        assertEquals(3, r.lastTrio)
        // Il rejoue.
        assertEquals(0, r.current)
        assertFalse(r.roundOver)
        assertTrue(r.flipCenter(0))
        assertEquals(0, r.lastTrio)
    }

    @Test
    fun leTrioRetireLesBonnesCartesQuandPlusieursViennentDeLaMemeMain() {
        val r = table(listOf(listOf(4, 4, 4, 9), listOf(2, 6), listOf(1, 8)), current = 1)
        assertTrue(r.flipHand(0, high = false))
        assertTrue(r.flipHand(0, high = false))
        assertTrue(r.flipHand(0, high = false))
        assertEquals(listOf(9), r.hands[0])
        assertEquals(listOf(emptyList<Int>(), listOf(4), emptyList<Int>()), r.trios)
    }

    @Test
    fun uneCarteDejaRetourneeNeSeRetournePasDeuxFois() {
        val r = table(listOf(listOf(5), listOf(5), listOf(5)))
        assertTrue(r.flipHand(1, high = false))
        assertFalse(r.flipHand(1, high = true))
        assertFalse(r.flipHand(1, high = false))
        assertEquals(1, r.reveals.size)
    }

    // ---------- Gagner la manche ----------

    @Test
    fun troisTriosFontGagnerLaManche() {
        // 2, 3 et 12 : aucun des trois n'est lié à un autre, seul le nombre de trios compte.
        val r = table(
            listOf(listOf(12), listOf(12), listOf(12)),
            trios = listOf(listOf(2, 3), emptyList(), emptyList()),
            current = 0
        )
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        assertTrue(r.roundOver)
        assertEquals(0, r.winner)
        assertEquals(TrioWin.THREE_TRIOS, r.winReason)
        assertEquals(listOf(1, 0, 0), r.roundPoints())
    }

    @Test
    fun deuxTriosLiesFontGagnerLaManche() {
        // Le joueur a déjà le trio de 1 ; il gagne le trio de 8, lié au 1.
        val r = table(
            listOf(listOf(8), listOf(8), listOf(8)),
            trios = listOf(emptyList(), listOf(1), emptyList()),
            current = 1
        )
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        assertTrue(r.roundOver)
        assertEquals(1, r.winner)
        assertEquals(TrioWin.LINKED, r.winReason)
        assertEquals(1 to 8, TrioScoring.linkedPair(r.trios[1]))
    }

    @Test
    fun deuxTriosNonLiesNeFontPasGagner() {
        val r = table(
            listOf(listOf(8, 8), listOf(8), listOf(2)),
            trios = listOf(emptyList(), listOf(2), emptyList()),
            current = 1
        )
        r.flipHand(0, high = false)
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        // 2 et 8 ne sont pas liés : la manche continue.
        assertFalse(r.roundOver)
        assertEquals(listOf(2, 8), r.trios[1])
        assertEquals(1, r.current)
    }

    @Test
    fun leTrioDeSeptFaitGagnerToutDeSuite() {
        val r = table(listOf(listOf(7, 12), listOf(7, 11), listOf(7, 10)), current = 2)
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        assertTrue(r.roundOver)
        assertEquals(2, r.winner)
        assertEquals(TrioWin.SEVENS, r.winReason)
        // Une fois la manche finie, plus rien ne se retourne.
        assertFalse(r.flipHand(0, high = true))
        assertFalse(r.endTurn())
    }

    @Test
    fun siTousLesTriosSontPrisLePlusFourniGagne() {
        val r = table(
            listOf(listOf(12), listOf(12), listOf(12)),
            trios = listOf(listOf(1), listOf(2), listOf(4)),
            current = 1
        )
        // 12 n'est lié qu'au 5 : le joueur 1 a 2 puis 12, sans lien (2 est lié à 5 et 6), et il en a le plus.
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        assertTrue(r.roundOver)
        assertEquals(TrioWin.MOST, r.winReason)
        assertEquals(1, r.winner)
        assertEquals(listOf(0, 1, 0), r.roundPoints())
    }

    @Test
    fun siTousLesTriosSontPrisALegalitePersonneNeGagne() {
        val r = table(
            listOf(listOf(12), listOf(12), listOf(12)),
            trios = listOf(listOf(1, 10), listOf(2), listOf(4)),
            current = 1
        )
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        assertTrue(r.roundOver)
        assertEquals(TrioWin.NONE, r.winReason)
        assertEquals(-1, r.winner)
        assertEquals(listOf(0, 0, 0), r.roundPoints())
    }

    @Test
    fun winReasonLitLesTrois() {
        assertNull(TrioScoring.winReason(listOf(1, 2)))
        assertEquals(TrioWin.THREE_TRIOS, TrioScoring.winReason(listOf(1, 3, 12)))
        assertEquals(TrioWin.LINKED, TrioScoring.winReason(listOf(3, 10)))
        assertEquals(TrioWin.SEVENS, TrioScoring.winReason(listOf(7)))
    }

    // ---------- Manches et scores ----------

    @Test
    fun lePointDuGagnantEstInscritAuTableauUneSeuleFois() {
        val vm = viewModel()
        val r = table(listOf(listOf(7), listOf(7), listOf(7)))
        assertFalse(r.settle(vm))
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        assertTrue(r.settle(vm))
        assertTrue(r.settled)
        assertEquals(listOf(1, 0, 0), vm.totals)
        assertFalse(r.settle(vm))
        assertEquals(listOf(1, 0, 0), vm.totals)
        assertEquals(1, vm.roundsPlayed())
    }

    @Test
    fun laMancheSuivanteEstDistribueeApresInscription() {
        val vm = viewModel()
        val r = table(listOf(listOf(7), listOf(7), listOf(7)))
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        assertFalse(r.nextRound())
        r.settle(vm)
        assertTrue(r.nextRound())
        assertEquals(2, r.roundNumber)
        assertFalse(r.roundOver)
        assertFalse(r.settled)
        assertEquals(1, r.current)
        assertTrue(r.trios.all { it.isEmpty() })
        assertEquals(TrioScoring.DECK, r.hands.sumOf { it.size } + r.center.size)
        assertFalse(r.inProgress)
    }

    @Test
    fun laPartieSeTermineApresTroisManchesGagnees() {
        val vm = viewModel()
        repeat(3) {
            val r = table(listOf(listOf(7), listOf(7), listOf(7)))
            r.flipHand(0, high = false)
            r.flipHand(1, high = false)
            r.flipHand(2, high = false)
            assertFalse(vm.isGameOver())
            r.settle(vm)
        }
        assertTrue(vm.isGameOver())
        assertEquals(listOf(0), vm.winners())
    }

    @Test
    fun lesJeuxJouablesEtLaCategorieIncluentLeTrio() {
        val trio = rules()
        assertTrue(trio.isPlayableGame())
        assertTrue(trio.isMultiPhone())
        assertEquals(GameCategory.SPECIAL, trio.category())
        assertEquals(3, trio.minPlayers)
        assertEquals(6, trio.maxPlayers)
        assertTrue(TRIO_ID in GAME_ICON_IDS)
    }

    // ---------- Chacun sur son téléphone ----------

    @Test
    fun laTableEnvoyeeNeMontreQueSaMainEtLesCartesRetournees() {
        val r = table(
            listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)),
            center = listOf(10, 11, 12)
        )
        r.flipHand(1, high = true)
        val view = r.tableFor(2)
        assertEquals(listOf(1, 6, 7), view.hands[2])
        assertTrue(view.hands[0].all { it == 0 })
        assertEquals(3, view.hands[0].size)
        assertTrue(view.hands[1].all { it == 0 })
        assertTrue(view.center.all { it == 0 })
        assertEquals(3, view.center.size)
        // La carte retournée, elle, est connue de tous.
        assertEquals(8, view.reveals.single().value)
    }

    @Test
    fun uneFoisLaMancheFinieToutLeMondeVoitTout() {
        val r = table(listOf(listOf(7, 12), listOf(7, 11), listOf(7, 10)), center = listOf(1, 2, 3))
        r.flipHand(0, high = false)
        r.flipHand(1, high = false)
        r.flipHand(2, high = false)
        val view = r.tableFor(-1)
        assertEquals(listOf(listOf(12), listOf(11), listOf(10)), view.hands)
        assertEquals(listOf(1, 2, 3), view.center)
    }

    @Test
    fun performAppliqueLesActions() {
        val r = table(listOf(listOf(3, 5, 9), listOf(2, 4, 8), listOf(1, 6, 7)), center = listOf(2, 2, 2))
        assertTrue(r.perform(TrioOp.LOW, 1))
        assertTrue(r.perform(TrioOp.CENTER, 0))
        assertTrue(r.perform(TrioOp.CENTER, 1))
        assertEquals(listOf(listOf(2), emptyList<Int>(), emptyList<Int>()), r.trios)
        assertFalse(r.perform(TrioOp.HIGH, 9))
        assertFalse(r.perform(TrioOp.END, -1))
        assertFalse(r.perform(TrioOp.NEXT, -1))
    }
}
