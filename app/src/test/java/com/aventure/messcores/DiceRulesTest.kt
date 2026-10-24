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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class DiceRulesTest {

    /** Un tour dont les dés sortent dans l'ordre donné : on voit ainsi quels dés ont été relancés. */
    private fun turn(setup: DiceSetup, vararg rolls: Int): DiceTurn {
        val queue = ArrayDeque(rolls.toList())
        return DiceTurn(setup) { queue.removeFirst() }
    }

    // ---------- Yams ----------

    @Test
    fun lesLignesDuHautRapportentLeNombreDeDesParLeurValeur() {
        val dice = listOf(1, 1, 1, 2, 2)
        assertEquals(3, YamsScoring.score("aces", dice))
        assertEquals(4, YamsScoring.score("twos", dice))
        assertEquals(0, YamsScoring.score("threes", dice))
        assertEquals(30, YamsScoring.score("sixes", listOf(6, 6, 6, 6, 6)))
    }

    @Test
    fun brelanCarreEtChanceFontLaSommeDesDes() {
        val dice = listOf(4, 4, 4, 4, 2)
        assertEquals(18, YamsScoring.score("three_kind", dice))
        assertEquals(18, YamsScoring.score("four_kind", dice))
        assertEquals(18, YamsScoring.score("chance", dice))
        assertEquals(0, YamsScoring.score("four_kind", listOf(4, 4, 4, 1, 2)))
        assertEquals(0, YamsScoring.score("three_kind", listOf(1, 2, 3, 4, 6)))
    }

    @Test
    fun fullSuitesEtYamsRapportentLeursPointsFixes() {
        assertEquals(25, YamsScoring.score("full_house", listOf(3, 3, 5, 5, 5)))
        assertEquals(0, YamsScoring.score("full_house", listOf(3, 3, 3, 3, 3)))
        assertEquals(0, YamsScoring.score("full_house", listOf(3, 3, 3, 3, 5)))
        assertEquals(30, YamsScoring.score("small_straight", listOf(1, 2, 3, 4, 6)))
        assertEquals(30, YamsScoring.score("small_straight", listOf(1, 3, 4, 5, 6)))
        assertEquals(0, YamsScoring.score("small_straight", listOf(1, 2, 3, 5, 6)))
        assertEquals(40, YamsScoring.score("large_straight", listOf(2, 3, 4, 5, 6)))
        assertEquals(40, YamsScoring.score("large_straight", listOf(5, 4, 3, 2, 1)))
        assertEquals(0, YamsScoring.score("large_straight", listOf(1, 2, 3, 4, 6)))
        assertEquals(50, YamsScoring.score("yams", listOf(2, 2, 2, 2, 2)))
        assertEquals(0, YamsScoring.score("yams", listOf(2, 2, 2, 2, 3)))
    }

    @Test
    fun chaqueLigneDeLaFeuilleDuYamsASonCalculDeScore() {
        val rows = ScoreSheets.rows(ScoreSheets.YAMS)!!
        val dice = listOf(1, 2, 3, 4, 5)
        for (row in rows) {
            val points = YamsScoring.score(row.id, dice)
            // Le score proposé est toujours une valeur que la ligne accepte : un bouton, ou la saisie libre.
            row.options?.let { assertTrue("${row.id} : $points", points in it) }
        }
    }

    // ---------- 421 ----------

    @Test
    fun lesJeuxDu421ValentLeNombreDeJetonsDeLaFicheDeRegles() {
        assertEquals(10, Dice421.combo(listOf(1, 4, 2)).points)
        assertEquals(R.string.dice_421_name_421, Dice421.combo(listOf(2, 4, 1)).label)
        assertEquals(7, Dice421.combo(listOf(1, 1, 1)).points)
        assertEquals(6, Dice421.combo(listOf(1, 6, 1)).points)
        assertEquals(R.string.dice_421_name_aces, Dice421.combo(listOf(1, 6, 1)).label)
        assertEquals(6, Dice421.combo(listOf(6, 6, 6)).points)
        assertEquals(R.string.dice_421_name_triple, Dice421.combo(listOf(5, 5, 5)).label)
        assertEquals(2, Dice421.combo(listOf(3, 4, 5)).points)
        assertEquals(R.string.dice_421_name_straight, Dice421.combo(listOf(5, 3, 4)).label)
        assertEquals(1, Dice421.combo(listOf(2, 2, 4)).points)
        assertEquals(R.string.dice_421_name_other, Dice421.combo(listOf(2, 2, 4)).label)
    }

    // ---------- Qwixx ----------

    @Test
    fun auQwixxChaqueCouleurSeCombineAvecLesDeuxDesBlancs() {
        val dice = listOf(2, 3, 4, 5, 6, 1)
        assertEquals(5, QwixxDice.whiteSum(dice))
        assertEquals(6 to 7, QwixxDice.colorOptions(dice, 2))
        assertEquals(3 to 4, QwixxDice.colorOptions(dice, 5))
    }

    // ---------- Tour de dés ----------

    @Test
    fun lePremierLancerLanceTousLesDesPuisSeulementLesNonGardes() {
        val turn = turn(DiceSetup(diceCount = 5, maxRolls = 3), 1, 2, 3, 4, 5, 6, 6, 6)
        assertFalse(turn.hasRolled)
        turn.roll()
        assertEquals(listOf(1, 2, 3, 4, 5), turn.values)
        turn.toggleHold(0)
        turn.toggleHold(4)
        turn.roll()
        assertEquals(listOf(1, 6, 6, 6, 5), turn.values)
        assertEquals(1, turn.rollsLeft)
    }

    @Test
    fun onNeGardeRienAvantLePremierLancer() {
        val turn = turn(DiceSetup(diceCount = 3, maxRolls = 3), 1, 2, 3)
        turn.toggleHold(1)
        assertTrue(turn.held.isEmpty())
        turn.roll()
        turn.toggleHold(1)
        assertEquals(setOf(1), turn.held)
        // Un deuxième appui remet le dé en jeu.
        turn.toggleHold(1)
        assertTrue(turn.held.isEmpty())
    }

    @Test
    fun apresLeDernierLancerPlusRienNeSeLanceNiNeSeGarde() {
        val turn = turn(DiceSetup(diceCount = 2, maxRolls = 2), 1, 2, 3, 4)
        turn.roll()
        turn.roll()
        assertEquals(0, turn.rollsLeft)
        assertFalse(turn.canRoll)
        assertFalse(turn.canHold)
        turn.roll()
        assertEquals(listOf(3, 4), turn.values)
        assertEquals(2, turn.rollsUsed)
    }

    @Test
    fun siTousLesDesSontGardesIlNYADonRienARelancer() {
        val turn = turn(DiceSetup(diceCount = 2, maxRolls = 3), 1, 2)
        turn.roll()
        turn.toggleHold(0)
        turn.toggleHold(1)
        assertFalse(turn.canRoll)
    }

    @Test
    fun avecUnSeulDeOuSansGardeOnNePeutRienGarder() {
        val single = turn(DiceSetup(diceCount = 1), 4)
        single.roll()
        assertFalse(single.canHold)
        val qwixx = turn(DiceSetup(diceCount = 3, allowHold = false), 1, 2, 3)
        qwixx.roll()
        assertFalse(qwixx.canHold)
        assertTrue(qwixx.canRoll)
        assertNull(qwixx.rollsLeft)
    }

    @Test
    fun nouveauTourRemetToutAZero() {
        val turn = turn(DiceSetup(diceCount = 2, maxRolls = 3), 1, 2)
        turn.roll()
        turn.toggleHold(0)
        turn.reset()
        assertFalse(turn.hasRolled)
        assertTrue(turn.held.isEmpty())
        assertEquals(3, turn.rollsLeft)
    }

    @Test
    fun lEtatSurvitAUneRotationDeLEcran() {
        val setup = DiceSetup(diceCount = 3, maxRolls = 3)
        val turn = turn(setup, 2, 5, 6)
        turn.roll()
        turn.toggleHold(2)

        val restored = DiceTurn(setup)
        restored.restoreFrom(turn.snapshot())
        assertEquals(listOf(2, 5, 6), restored.values)
        assertEquals(setOf(2), restored.held)
        assertEquals(1, restored.rollsUsed)

        // Données invalides : le tour repart de zéro, sans planter.
        restored.restoreFrom(listOf(1, 7, 3))
        assertFalse(restored.hasRolled)
        restored.restoreFrom(emptyList())
        assertFalse(restored.hasRolled)
    }

    @Test
    fun lesDesTirentToujoursUneValeurDe1A6() {
        val turn = DiceTurn(DiceSetup(diceCount = 6))
        repeat(200) {
            turn.roll()
            assertTrue(turn.values.all { it in 1..6 })
        }
    }

    // ---------- Dés par jeu ----------

    @Test
    fun chaqueJeuDeDesALesBonsDes() {
        val dice = GameRepository(RuntimeEnvironment.getApplication()).builtInGames()
            .filter { it.category() == GameCategory.DICE }
        // Zombie Dice a ses propres dés (cerveaux, pas, coups de fusil) : rien à lancer ici.
        for (game in dice) {
            if (game.id == "builtin_zombie") assertNull(game.id, DiceSetups.forGame(game.id))
            else assertNotNull(game.id, DiceSetups.forGame(game.id))
        }
        assertEquals(DiceSetup(diceCount = 5, maxRolls = 3), DiceSetups.forGame("builtin_yams"))
        assertEquals(1, DiceSetups.forGame("builtin_cochon")!!.diceCount)
        assertEquals(3, DiceSetups.forGame("builtin_421")!!.diceCount)
        val qwixx = DiceSetups.forGame("builtin_qwixx")!!
        assertEquals(6, qwixx.tints.size)
        assertFalse(qwixx.allowHold)
        assertNull(DiceSetups.forGame("builtin_skyjo"))
    }

    @Test
    fun lesPointsDUneFaceSontAuNombreDeLaFace() {
        for (value in 1..6) assertEquals(value, pipsOf(value).size)
        assertTrue(pipsOf(0).isEmpty())
    }
}
