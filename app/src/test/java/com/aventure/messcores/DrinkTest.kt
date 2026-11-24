package com.aventure.messcores

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrinkTest {
    private fun game(seed: Int = 1) = DrinkGame(Random(seed))

    /** Fait avancer la partie jusqu'au premier déséquilibre. */
    private fun untilWobble(g: DrinkGame) {
        var guard = 0
        while (!g.unsteady && guard++ < 1000) g.tick(50f)
        assertTrue(g.unsteady)
    }

    /** La flèche qui redresse le personnage qui penche actuellement. */
    private fun fixKey(g: DrinkGame): Int = if (g.lean > 0f) DRINK_LEFT else DRINK_RIGHT

    @Test
    fun everyGulpIsOneCentilitre() {
        val g = game()
        g.press(DRINK_A)
        g.press(DRINK_B)
        g.press(DRINK_A)
        assertEquals(3, g.cl)
        assertTrue(g.sipMs > 0f)
        assertEquals(1f - 3f / Drink.GLASS_GULPS, g.level, 0.001f)
    }

    @Test
    fun aGlassIsRefilledOnceEmpty() {
        val g = game()
        repeat(Drink.GLASS_GULPS) { g.press(DRINK_A) }
        assertEquals(Drink.GLASS_GULPS, g.cl)
        assertEquals(1, g.glasses)
        assertEquals(1f, g.level, 0.001f)
        assertTrue(g.refillMs > 0f)
        repeat(Drink.GLASS_GULPS) { g.press(DRINK_B) }
        assertEquals(2, g.glasses)
        assertEquals(2 * Drink.GLASS_GULPS, g.cl)
    }

    @Test
    fun noWobbleAtTheStart() {
        val g = game()
        repeat(60) { g.tick(50f) } // 3 secondes
        assertFalse(g.unsteady)
        assertEquals(0f, g.lean, 0f)
    }

    @Test
    fun theCharacterEventuallyStaggers() {
        val g = game()
        untilWobble(g)
        assertEquals(Drink.START_LEAN, abs(g.lean), 0.001f)
    }

    @Test
    fun bothSidesHappen() {
        val sides = HashSet<Boolean>()
        for (seed in 1..40) {
            val g = game(seed)
            untilWobble(g)
            sides.add(g.lean > 0f)
        }
        assertEquals(setOf(true, false), sides)
    }

    @Test
    fun cannotDrinkWhileStaggering() {
        val g = game()
        untilWobble(g)
        val before = g.cl
        g.press(DRINK_A)
        g.press(DRINK_B)
        assertEquals(before, g.cl)
        assertTrue(g.blockedMs > 0f)
    }

    @Test
    fun theRightArrowStraightensHimUp() {
        val g = game()
        untilWobble(g)
        val key = fixKey(g)
        g.press(key)
        assertTrue(g.unsteady)
        g.press(key)
        assertFalse(g.unsteady)
        assertEquals(0f, g.lean, 0f)
        assertEquals(1, g.recoveries)
        // Il peut boire de nouveau.
        g.press(DRINK_A)
        assertEquals(1, g.cl)
    }

    @Test
    fun theWrongArrowMakesHimLeanMore() {
        val g = game()
        untilWobble(g)
        val side = if (g.lean > 0f) 1f else -1f
        val wrong = if (g.lean > 0f) DRINK_RIGHT else DRINK_LEFT
        g.press(wrong)
        assertEquals(side * (Drink.START_LEAN + Drink.WRONG_PUSH), g.lean, 0.001f)
        assertEquals(0, g.recoveries)
    }

    @Test
    fun arrowsDoNothingWhenSteady() {
        val g = game()
        g.press(DRINK_LEFT)
        g.press(DRINK_RIGHT)
        assertFalse(g.unsteady)
        assertEquals(0, g.recoveries)
    }

    @Test
    fun leaningGrowsByItselfUpToTheMaximum() {
        val g = game()
        untilWobble(g)
        val before = abs(g.lean)
        g.tick(1000f)
        assertTrue(abs(g.lean) > before + 0.25f)
        g.tick(10_000f)
        assertEquals(1f, abs(g.lean), 0.0001f)
        assertTrue(g.unsteady)
        // Même penché à fond, quelques bonnes flèches le redressent.
        val key = fixKey(g)
        repeat(8) { g.press(key) }
        assertFalse(g.unsteady)
    }

    @Test
    fun staggersMoreOftenAsTheGameGoesOn() {
        val g = game(7)
        fun run(ms: Int) {
            var left = ms
            while (left > 0) {
                g.tick(50f)
                left -= 50
                // Un joueur parfait redresse le personnage tout de suite.
                var guard = 0
                while (g.unsteady && guard++ < 50) g.press(fixKey(g))
            }
        }
        run(30_000)
        val early = g.recoveries
        run(60_000)
        val middle = g.recoveries
        run(30_000)
        val late = g.recoveries - middle
        assertTrue("début : $early, fin : $late", late > early)
        assertTrue(g.difficulty > 0.9f)
    }

    @Test
    fun theGameEndsAfterTwoMinutes() {
        val g = game()
        repeat(1199) { g.tick(100f) }
        assertFalse(g.over)
        g.tick(100f)
        assertTrue(g.over)
        assertEquals(0f, g.timeLeftMs, 0f)
        assertEquals(0f, g.lean, 0f)
        // Plus rien ne compte une fois la partie finie.
        val cl = g.cl
        g.press(DRINK_A)
        assertEquals(cl, g.cl)
        assertFalse(g.canRestart)
        g.tick(Drink.RESTART_DELAY_MS + 100f)
        assertTrue(g.canRestart)
    }

    @Test
    fun resetStartsANewGame() {
        val g = game()
        repeat(30) { g.press(DRINK_A) }
        untilWobble(g)
        g.reset()
        assertEquals(0, g.cl)
        assertEquals(0, g.glasses)
        assertEquals(0f, g.lean, 0f)
        assertEquals(Drink.DURATION_MS, g.timeLeftMs, 0f)
        assertFalse(g.over)
    }

    @Test
    fun saveAndRestoreKeepTheGame() {
        val g = game()
        repeat(30) { g.press(DRINK_A) }
        untilWobble(g)
        g.tick(400f)
        val copy = DrinkGame.restore(g.save())
        assertEquals(g.cl, copy.cl)
        assertEquals(g.glasses, copy.glasses)
        assertEquals(g.gulpsInGlass, copy.gulpsInGlass)
        assertEquals(g.lean, copy.lean, 0.0001f)
        assertEquals(g.timeLeftMs, copy.timeLeftMs, 0.001f)
        assertEquals(g.recoveries, copy.recoveries)
    }

    @Test
    fun anUnusableSaveGivesANewGame() {
        assertEquals(0, DrinkGame.restore(emptyList()).cl)
        assertEquals(0, DrinkGame.restore(listOf(99.0, 1.0)).cl)
    }

    @Test
    fun clockText() {
        assertEquals("2:00", drinkClockText(120_000f))
        assertEquals("1:59", drinkClockText(119_000f))
        assertEquals("0:01", drinkClockText(300f))
        assertEquals("0:00", drinkClockText(0f))
    }
}
