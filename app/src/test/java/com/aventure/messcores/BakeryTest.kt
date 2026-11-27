package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BakeryTest {
    private fun game(seed: Int = 1) = BakeryGame(Random(seed))

    /** Choisit le produit [product] avec les flèches. */
    private fun pick(g: BakeryGame, product: Int) {
        var guard = 0
        while (g.productCursor != product && guard++ < Bakery.PRODUCTS) g.press(BAKERY_RIGHT)
        assertEquals(product, g.productCursor)
    }

    /** Choisit le four [oven] avec B. */
    private fun pickOven(g: BakeryGame, oven: Int) {
        var guard = 0
        while (g.ovenCursor != oven && guard++ < Bakery.OVENS) g.press(BAKERY_B)
        assertEquals(oven, g.ovenCursor)
    }

    private fun advance(g: BakeryGame, ms: Int) {
        var left = ms
        while (left > 0 && !g.over) {
            g.tick(50f)
            left -= 50
        }
    }

    @Test
    fun startsWithOneBatchOnTheShelfAndOneTrayOfDough() {
        val g = game()
        assertEquals(0, g.score)
        for (p in 0 until Bakery.PRODUCTS) {
            assertEquals(Bakery.BATCH[p], g.total(p))
            assertEquals(Bakery.START_RAW, g.raw[p])
            assertEquals(Bakery.START_SET, g.setting[p])
        }
        assertTrue(g.ovenProduct.all { it == -1 })
    }

    @Test
    fun qualityFollowsTheBakingTime() {
        val t = Bakery.BAKE_MS[0].toFloat()
        assertEquals(BAKE_RAW, Bakery.qualityAt(0, t * 0.5f))
        assertEquals(BAKE_PALE, Bakery.qualityAt(0, t * 0.8f))
        assertEquals(BAKE_GOLD, Bakery.qualityAt(0, t))
        assertEquals(BAKE_DARK, Bakery.qualityAt(0, t * 1.3f))
        assertEquals(BAKE_BURNT, Bakery.qualityAt(0, t * 1.6f))
    }

    @Test
    fun arrowsChangeTheSelectedProductsTimeWithinLimits() {
        val g = game()
        pick(g, 2)
        g.press(BAKERY_UP)
        g.press(BAKERY_UP)
        assertEquals(Bakery.START_SET + 2, g.setting[2])
        assertEquals(Bakery.START_SET, g.setting[0])
        repeat(100) { g.press(BAKERY_DOWN) }
        assertEquals(Bakery.MIN_SET, g.setting[2])
        repeat(100) { g.press(BAKERY_UP) }
        assertEquals(Bakery.MAX_SET, g.setting[2])
    }

    @Test
    fun loadingUsesATrayOfDoughAndIsRefusedWithoutOne() {
        val g = game()
        pick(g, 0)
        g.press(BAKERY_A)
        assertEquals(0, g.ovenProduct[0])
        assertEquals(0, g.raw[0])
        pickOven(g, 1)
        g.press(BAKERY_A)
        assertEquals(-1, g.ovenProduct[1])
        assertTrue(g.blockedMs > 0f)
    }

    @Test
    fun doughRisesAgainAfterLoading() {
        val g = game()
        pick(g, 0)
        g.press(BAKERY_A)
        advance(g, Bakery.DOUGH_MS.toInt() + 100)
        assertEquals(1, g.raw[0])
        advance(g, Bakery.DOUGH_MS.toInt() * 3)
        assertEquals(Bakery.MAX_RAW, g.raw[0])
    }

    @Test
    fun rawBatchCannotBeTakenOut() {
        val g = game()
        pick(g, 0)
        g.press(BAKERY_A)
        advance(g, 3000)
        g.press(BAKERY_A)
        assertEquals(0, g.ovenProduct[0])
        assertEquals(Bakery.BATCH[0], g.total(0))
    }

    @Test
    fun goldenBatchGoesOnTheShelfAtFullPrice() {
        val g = game()
        pick(g, 0)
        g.press(BAKERY_A)
        advance(g, Bakery.BAKE_MS[0])
        assertEquals(BAKE_GOLD, g.ovenQuality(0))
        // Les clients ont pu se servir pendant la cuisson : on compare à l'étalage juste avant de sortir la fournée.
        val before = g.stock[0][SHELF_GOOD]
        val total = g.total(0)
        g.press(BAKERY_A)
        assertEquals(-1, g.ovenProduct[0])
        assertEquals(minOf(Bakery.SHELF_CAP, total + Bakery.BATCH[0]), g.total(0))
        assertEquals(before + g.total(0) - total, g.stock[0][SHELF_GOOD])
        assertEquals(BAKE_GOLD, g.lastPull[0])
        assertEquals(1, g.goldBatches)
    }

    @Test
    fun paleBatchIsKeptSeparatelyAndSoldAtHalfPrice() {
        val g = game()
        pick(g, 0)
        g.press(BAKERY_A)
        advance(g, (Bakery.BAKE_MS[0] * 0.8f).toInt())
        assertEquals(BAKE_PALE, g.ovenQuality(0))
        g.press(BAKERY_A)
        assertEquals(Bakery.BATCH[0], g.stock[0][SHELF_PALE])
        assertEquals(BAKE_PALE, g.lastPull[0])
        assertEquals(1, Bakery.value(0, SHELF_PALE))
        assertEquals(2, Bakery.value(0, SHELF_GOOD))
    }

    @Test
    fun ovenRingsAtTheTimeSetOnTheWall() {
        val g = game()
        pick(g, 0)
        g.press(BAKERY_A)
        assertFalse(g.ringing(0))
        advance(g, Bakery.START_SET * 1000 + 100)
        assertTrue(g.ringing(0))
        assertTrue(g.dingMs[0] > 0f)
    }

    @Test
    fun overlongBakingBurnsAndTheBatchIsThrownAway() {
        val g = game()
        pick(g, 0)
        g.press(BAKERY_A)
        advance(g, (Bakery.BAKE_MS[0] * 1.6f).toInt())
        assertEquals(BAKE_BURNT, g.ovenQuality(0))
        assertEquals(1, g.burnedBatches)
        assertFalse(g.ringing(0))
        val total = g.total(0)
        g.press(BAKERY_A)
        assertEquals(-1, g.ovenProduct[0])
        assertEquals(total, g.total(0))
        assertEquals(BAKE_BURNT, g.lastPull[0])
    }

    @Test
    fun shelfNeverHoldsMoreThanItsCapacity() {
        val g = game()
        repeat(3) {
            pick(g, 0)
            if (g.raw[0] == 0) advance(g, Bakery.DOUGH_MS.toInt() + 100)
            if (g.ovenProduct[0] >= 0) g.press(BAKERY_B)
            g.press(BAKERY_A)
            val oven = g.ovenCursor
            advance(g, Bakery.BAKE_MS[0])
            g.press(BAKERY_A)
            assertTrue(g.total(0) <= Bakery.SHELF_CAP)
            assertEquals(oven, g.ovenCursor)
        }
        assertTrue(g.lostPieces > 0)
    }

    @Test
    fun customersTakeFromTheShelfAndPay() {
        val g = game()
        advance(g, 30_000)
        assertTrue(g.served + g.misses > 0)
        assertTrue(g.revenue > 0 || g.misses > 0)
    }

    @Test
    fun threeDisappointedCustomersEndTheGame() {
        val g = game()
        var guard = 0
        while (!g.over && guard++ < 20_000) g.tick(50f)
        assertTrue(g.over)
        assertEquals(Bakery.MAX_MISSES, g.misses)
        val score = g.score
        g.tick(5000f)
        g.press(BAKERY_A)
        assertEquals(score, g.score)
        assertTrue(g.canRestart)
    }

    @Test
    fun resetStartsAFreshGame() {
        val g = game()
        advance(g, 200_000)
        g.reset()
        assertFalse(g.over)
        assertEquals(0, g.score)
        assertEquals(0, g.misses)
        assertEquals(Bakery.BATCH[3], g.total(3))
    }

    @Test
    fun savedGameComesBackIdentical() {
        val g = game(7)
        pick(g, 2)
        g.press(BAKERY_UP)
        g.press(BAKERY_A)
        advance(g, 20_000)
        val back = BakeryGame.restore(g.save())
        assertEquals(g.save(), back.save())
        assertEquals(g.score, back.score)
        assertEquals(g.setting.toList(), back.setting.toList())
    }

    @Test
    fun brokenSaveGivesAFreshGame() {
        val g = BakeryGame.restore(listOf(1.0, 2.0))
        assertEquals(0, g.score)
        assertEquals(Bakery.BATCH[0], g.total(0))
    }
}
