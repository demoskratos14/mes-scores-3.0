package com.aventure.messcores

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class FishTest {
    private fun game(seed: Int = 1) = FishGame(Random(seed))

    /** Fait avancer la partie jusqu'à ce qu'une caisse puisse être rangée. */
    private fun untilCrate(g: FishGame): FishCrate {
        var guard = 0
        while (g.head() == null && guard++ < 2000) g.tick(50f)
        return checkNotNull(g.head())
    }

    /** Déplace le choix sur [bin] avec la flèche de droite. */
    private fun pickBin(g: FishGame, bin: Int) {
        var guard = 0
        while (g.cursor != bin && guard++ < Fish.SPECIES) g.press(FISH_RIGHT)
        assertEquals(bin, g.cursor)
    }

    /** Joue toute la phase d'approvisionnement sans erreur : chaque caisse va dans son bac. */
    private fun stockEverything(g: FishGame) {
        var guard = 0
        while (g.stage == FISH_STAGE_STOCK && guard++ < 4000) {
            g.tick(50f)
            val crate = g.head() ?: continue
            pickBin(g, crate.species)
            g.press(FISH_A)
        }
        assertTrue(g.stage != FISH_STAGE_STOCK)
    }

    /** Une partie en plein service, avec un client au comptoir qui a fini d'arriver. */
    private fun serving(seed: Int = 1): FishGame {
        val g = game(seed)
        stockEverything(g)
        var guard = 0
        while (g.stage == FISH_STAGE_OPENING && guard++ < 1000) g.tick(50f)
        assertEquals(FISH_STAGE_SERVICE, g.stage)
        g.tick(Fish.ARRIVE_MS)
        return g
    }

    @Test
    fun startsByStockingTheDisplay() {
        val g = game()
        assertEquals(FISH_STAGE_STOCK, g.stage)
        assertEquals(0, g.score)
        assertEquals(0f, g.difficulty, 0.001f)
        assertTrue(g.stock.all { it == 0 })
    }

    @Test
    fun theChoiceOfBinWrapsAround() {
        val g = game()
        g.press(FISH_LEFT)
        assertEquals(Fish.SPECIES - 1, g.cursor)
        g.press(FISH_RIGHT)
        assertEquals(0, g.cursor)
    }

    @Test
    fun aCrateInTheRightBinFillsIt() {
        val g = game()
        val crate = untilCrate(g)
        val species = crate.species
        pickBin(g, species)
        g.press(FISH_A)
        assertEquals(Fish.CRATE_G, g.stock[species])
        assertEquals(1, g.stocked)
        assertEquals(Fish.POINTS_CRATE, g.score)
        assertFalse(g.crates.contains(crate))
        assertTrue(g.placeMs > 0f)
    }

    @Test
    fun aCrateInTheWrongBinCostsPointsAndStaysOnTheBelt() {
        val g = game()
        val crate = untilCrate(g)
        pickBin(g, (crate.species + 1) % Fish.SPECIES)
        g.press(FISH_B)
        assertTrue(g.stock.all { it == 0 })
        assertEquals(1, g.misplaced)
        assertEquals(-Fish.PENALTY_WRONG_BIN, g.stockPoints)
        assertEquals(0, g.score) // le score ne descend jamais sous zéro
        assertTrue(g.crates.contains(crate))
    }

    @Test
    fun aCrateThatReachesTheEndOfTheBeltIsLost() {
        val g = game()
        var guard = 0
        while (g.lostCrates == 0 && guard++ < 1000) g.tick(100f)
        assertTrue(g.lostCrates >= 1)
        assertTrue(g.stockPoints < 0)
    }

    @Test
    fun nothingStockedMeansNothingToSell() {
        val g = game()
        var guard = 0
        while (!g.over && guard++ < 1000) g.tick(100f)
        assertTrue(g.over)
        assertTrue(g.soldOut)
    }

    @Test
    fun theShopOpensAfterTheStockingStage() {
        val g = game()
        stockEverything(g)
        assertEquals(FISH_STAGE_OPENING, g.stage)
        g.tick(Fish.OPENING_MS)
        assertEquals(FISH_STAGE_SERVICE, g.stage)
        assertEquals(0f, g.difficulty, 0.01f)
        assertEquals(Fish.TOLERANCE_START_G, g.toleranceG, 1f)
    }

    @Test
    fun aCustomerOnlyAsksForWhatIsInStock() {
        repeat(5) { seed ->
            val g = serving(seed + 1)
            val species = g.customerSpecies
            assertTrue(species in 0 until Fish.SPECIES)
            assertTrue(g.wantG in Fish.MIN_ORDER_G..Fish.MAX_ORDER_G)
            assertEquals(0, g.wantG % Fish.ORDER_STEP_G)
            assertTrue(g.stock[species] >= g.wantG)
        }
    }

    @Test
    fun theRightFishStartsTheWeighing() {
        val g = serving()
        pickBin(g, g.customerSpecies)
        g.press(FISH_A)
        assertEquals(FISH_STEP_WEIGH, g.step)
    }

    @Test
    fun aWrongFishCostsPointsAndPatience() {
        val g = serving()
        val wrong = (0 until Fish.SPECIES).firstOrNull { it != g.customerSpecies && g.stock[it] > 0 }
        assumeTrue(wrong != null)
        pickBin(g, wrong!!)
        val before = g.patienceMs
        g.press(FISH_A)
        assertEquals(1, g.wrongFish)
        assertEquals(FISH_STEP_PICK, g.step)
        assertEquals(-Fish.PENALTY_WRONG_FISH, g.servicePoints)
        assertTrue(g.patienceMs < before)
    }

    @Test
    fun anEmptyBinGivesNothing() {
        val g = serving()
        val empty = (0 until Fish.SPECIES).firstOrNull { g.stock[it] == 0 }
        assumeTrue(empty != null)
        pickBin(g, empty!!)
        g.press(FISH_A)
        assertEquals(0, g.wrongFish)
        assertEquals(FISH_STEP_PICK, g.step)
        assertEquals(0, g.servicePoints)
    }

    /** Pèse avec le curseur arrêté à moins de [within] grammes du poids demandé. */
    private fun weighWithin(g: FishGame, within: Float) {
        pickBin(g, g.customerSpecies)
        g.press(FISH_A)
        assertEquals(FISH_STEP_WEIGH, g.step)
        var guard = 0
        while (abs(g.currentWeightG - g.wantG) > within && guard++ < 4000) g.tick(5f)
        assertTrue(abs(g.currentWeightG - g.wantG) <= within)
        g.press(FISH_A)
    }

    @Test
    fun stoppingTheCursorOnTheWeightIsPerfect() {
        val g = serving()
        val species = g.customerSpecies
        val before = g.stock[species]
        weighWithin(g, g.toleranceG * Fish.PERFECT_RATIO * 0.8f)
        assertEquals(FISH_STEP_RESULT, g.step)
        assertEquals(FISH_VERDICT_PERFECT, g.verdict)
        assertEquals(1, g.perfects)
        assertEquals(1, g.served)
        assertTrue(g.lastPoints >= Fish.POINTS_PERFECT)
        assertEquals(g.lastPoints, g.servicePoints)
        // Le poisson pesé a quitté le bac.
        assertEquals((before - g.weighedG).coerceAtLeast(0), g.stock[species])
    }

    @Test
    fun aLessPreciseStopIsOnlyGood() {
        val g = serving()
        pickBin(g, g.customerSpecies)
        g.press(FISH_A)
        var guard = 0
        // Entre 50 % et 90 % de la tolérance : bon, mais pas parfait.
        while (guard++ < 4000) {
            val gap = abs(g.currentWeightG - g.wantG)
            if (gap > g.toleranceG * 0.55f && gap < g.toleranceG * 0.85f) break
            g.tick(5f)
        }
        g.press(FISH_B)
        assertEquals(FISH_VERDICT_GOOD, g.verdict)
        assertEquals(0, g.perfects)
        assertEquals(1, g.served)
    }

    @Test
    fun stoppingFarFromTheWeightIsAMiss() {
        val g = serving()
        pickBin(g, g.customerSpecies)
        g.press(FISH_A)
        var guard = 0
        while (abs(g.currentWeightG - g.wantG) <= g.toleranceG * Fish.OFF_RATIO * 1.2f && guard++ < 4000) g.tick(5f)
        g.press(FISH_A)
        assertEquals(FISH_VERDICT_MISS, g.verdict)
        assertEquals(-Fish.PENALTY_MISS, g.lastPoints)
        assertEquals(1, g.walkouts)
        assertEquals(0, g.streak)
    }

    @Test
    fun successiveGoodWeighingsBuildAStreak() {
        val g = serving()
        weighWithin(g, g.toleranceG * Fish.PERFECT_RATIO * 0.8f)
        g.tick(Fish.RESULT_MS)
        assertEquals(FISH_STEP_PICK, g.step) // le client suivant est au comptoir
        g.tick(Fish.ARRIVE_MS)
        weighWithin(g, g.toleranceG * Fish.PERFECT_RATIO * 0.8f)
        assertEquals(2, g.streak)
        assertTrue(g.lastPoints >= Fish.POINTS_PERFECT + Fish.STREAK_BONUS)
        assertEquals(2, g.bestStreak)
    }

    @Test
    fun aCustomerWhoWaitsTooLongLeaves() {
        val g = serving()
        var guard = 0
        while (g.step != FISH_STEP_RESULT && guard++ < 1000) g.tick(100f)
        assertEquals(FISH_VERDICT_LEFT, g.verdict)
        assertEquals(1, g.walkouts)
        assertEquals(-Fish.PENALTY_LEFT, g.lastPoints)
    }

    @Test
    fun theGaugeStaysInsideTheScale() {
        val g = serving()
        pickBin(g, g.customerSpecies)
        g.press(FISH_A)
        repeat(300) {
            g.tick(20f)
            assertTrue(g.gaugePos in 0f..1f)
        }
    }

    @Test
    fun theGameEndsWhenTheServiceTimeIsUp() {
        val g = serving()
        var guard = 0
        while (!g.over && guard++ < 4000) g.tick(100f)
        assertTrue(g.over)
        assertFalse(g.canRestart)
        g.tick(Fish.RESTART_DELAY_MS + 1f)
        assertTrue(g.canRestart)
    }

    @Test
    fun theDifficultyGrowsDuringTheService() {
        val g = serving()
        val start = g.toleranceG
        var guard = 0
        while (g.difficulty < 0.9f && !g.over && guard++ < 4000) g.tick(100f)
        assertTrue(g.toleranceG < start)
    }

    @Test
    fun savingAndRestoringKeepsTheGame() {
        val g = serving()
        pickBin(g, g.customerSpecies)
        g.press(FISH_A)
        g.tick(300f)
        val copy = FishGame.restore(g.save())
        assertEquals(g.stage, copy.stage)
        assertEquals(g.step, copy.step)
        assertEquals(g.score, copy.score)
        assertEquals(g.cursor, copy.cursor)
        assertEquals(g.customerSpecies, copy.customerSpecies)
        assertEquals(g.wantG, copy.wantG)
        assertEquals(g.gaugePos, copy.gaugePos, 0.0001f)
        assertEquals(g.patienceMs, copy.patienceMs, 0.01f)
        assertEquals(g.stock.toList(), copy.stock.toList())
    }

    @Test
    fun savingAndRestoringKeepsTheCratesOnTheBelt() {
        val g = game()
        repeat(60) { g.tick(50f) }
        assertTrue(g.crates.isNotEmpty())
        val copy = FishGame.restore(g.save())
        assertEquals(g.crates.map { it.species }, copy.crates.map { it.species })
        assertEquals(g.crates.map { it.x }, copy.crates.map { it.x })
    }

    @Test
    fun anUnreadableSaveGivesAFreshGame() {
        val copy = FishGame.restore(listOf(99.0, 1.0))
        assertEquals(FISH_STAGE_STOCK, copy.stage)
        assertEquals(0, copy.score)
    }

    @Test
    fun resetStartsAgain() {
        val g = serving()
        weighWithin(g, g.toleranceG * Fish.PERFECT_RATIO * 0.8f)
        g.reset()
        assertEquals(FISH_STAGE_STOCK, g.stage)
        assertEquals(0, g.score)
        assertEquals(0, g.served)
        assertTrue(g.stock.all { it == 0 })
        assertTrue(g.crates.isEmpty())
    }
}
