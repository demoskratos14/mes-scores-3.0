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
    fun leaningGrowsByItself() {
        val g = game()
        untilWobble(g)
        val before = abs(g.lean)
        g.tick(1000f)
        assertTrue(abs(g.lean) > before + 0.25f)
        assertFalse(g.fallen)
        // Presque à fond, quelques bonnes flèches le redressent encore.
        g.tick(800f)
        assertTrue(abs(g.lean) > 0.8f && abs(g.lean) < 1f)
        val key = fixKey(g)
        repeat(8) { g.press(key) }
        assertFalse(g.unsteady)
        assertEquals(0, g.falls)
    }

    @Test
    fun heFallsIfNobodyCatchesHim() {
        val g = game()
        untilWobble(g)
        g.tick(10_000f)
        assertTrue(g.fallen)
        assertEquals(1, g.falls)
        assertEquals(1f, abs(g.lean), 0f)
        // Par terre, ni les flèches ni les boutons ne servent à rien.
        val cl = g.cl
        repeat(10) { g.press(fixKey(g)) }
        g.press(DRINK_A)
        assertTrue(g.fallen)
        assertEquals(1f, abs(g.lean), 0f)
        assertEquals(cl, g.cl)
        // Il finit par se relever, d'aplomb, et peut de nouveau boire.
        g.tick(Drink.FALL_MS + 10f)
        assertFalse(g.fallen)
        assertFalse(g.unsteady)
        assertEquals(1, g.falls)
        g.press(DRINK_A)
        assertEquals(cl + 1, g.cl)
    }

    @Test
    fun aFallIsCountedOnlyOnce() {
        val g = game()
        untilWobble(g)
        repeat(100) { g.tick(100f) }
        // Il s'est relevé et a pu rechanceler, mais jamais deux chutes d'un coup.
        assertTrue(g.falls >= 1)
        assertTrue(g.falls <= 3)
    }

    /** Fait avancer la partie jusqu'à l'envie pressante. */
    private fun untilUrge(g: DrinkGame) {
        var guard = 0
        while (g.peePhase == Drink.PEE_NONE && guard++ < 5000) {
            g.tick(50f)
            var fix = 0
            while (g.unsteady && !g.fallen && fix++ < 50) g.press(fixKey(g))
        }
        assertEquals(Drink.PEE_URGE, g.peePhase)
    }

    /** Fait avancer la partie jusqu'à ce que le personnage soit devant l'urinoir. */
    private fun untilUrinal(g: DrinkGame) {
        untilUrge(g)
        var guard = 0
        while (g.peePhase != Drink.PEE_RELIEF && guard++ < 1000) g.tick(50f)
        assertEquals(Drink.PEE_RELIEF, g.peePhase)
    }

    @Test
    fun theUrgeComesAfterAWhile() {
        val g = game()
        repeat(((Drink.PEE_FIRST_MIN - 500f) / 50f).toInt()) {
            g.tick(50f)
            while (g.unsteady && !g.fallen) g.press(fixKey(g))
        }
        assertEquals(Drink.PEE_NONE, g.peePhase)
        untilUrge(g)
        assertTrue(g.peeSide == 1 || g.peeSide == -1)
    }

    @Test
    fun bothUrinalSidesHappen() {
        val sides = HashSet<Int>()
        for (seed in 1..40) {
            val g = game(seed)
            untilUrge(g)
            sides.add(g.peeSide)
        }
        assertEquals(setOf(1, -1), sides)
    }

    @Test
    fun heCanStillDrinkWhileTheUrgeBuilds() {
        val g = game()
        untilUrge(g)
        g.press(DRINK_A)
        assertEquals(1, g.cl)
        assertEquals(0f, g.walk, 0f)
    }

    @Test
    fun heWalksToTheUrinalThenAmashingReliefs() {
        val g = game()
        untilUrge(g)
        g.tick(Drink.URGE_MS + 10f)
        assertEquals(Drink.PEE_GOING, g.peePhase)
        // En chemin : on ne peut plus boire.
        val cl = g.cl
        g.press(DRINK_A)
        assertEquals(cl, g.cl)
        g.tick(Drink.WALK_MS / 2f)
        assertEquals(0.5f, g.walk, 0.1f)
        g.tick(Drink.WALK_MS)
        assertEquals(Drink.PEE_RELIEF, g.peePhase)
        assertEquals(1f, g.walk, 0f)
        // Devant l'urinoir : A et B font avancer le soulagement, pas de gorgée, les flèches ne servent à rien.
        g.press(DRINK_LEFT)
        g.press(DRINK_RIGHT)
        assertEquals(0, g.peeProgress)
        repeat(Drink.PEE_PRESSES - 1) { g.press(if (it % 2 == 0) DRINK_A else DRINK_B) }
        assertEquals(Drink.PEE_RELIEF, g.peePhase)
        assertEquals(cl, g.cl)
        assertEquals(0, g.pees)
        g.press(DRINK_A)
        assertEquals(Drink.PEE_BACK, g.peePhase)
        assertEquals(1, g.pees)
        // Il revient, puis la partie reprend.
        g.tick(Drink.WALK_MS + 10f)
        assertEquals(Drink.PEE_NONE, g.peePhase)
        assertEquals(0f, g.walk, 0f)
        g.press(DRINK_A)
        assertEquals(cl + 1, g.cl)
    }

    @Test
    fun heWaitsAtTheUrinalForever() {
        val g = game()
        untilUrinal(g)
        repeat(200) { g.tick(100f) }
        assertEquals(Drink.PEE_RELIEF, g.peePhase)
        assertEquals(0, g.peeProgress)
    }

    @Test
    fun noWobbleWhileAtTheUrinal() {
        val g = game()
        untilUrinal(g)
        repeat(100) { g.tick(100f) }
        assertFalse(g.unsteady)
        assertEquals(0, g.falls)
    }

    @Test
    fun urgesComeSeveralTimesInAGame() {
        val g = game(3)
        var ms = 0
        while (!g.over && ms < 200_000) {
            g.tick(50f)
            ms += 50
            var guard = 0
            while (g.unsteady && !g.fallen && guard++ < 50) g.press(fixKey(g))
            if (g.peePhase == Drink.PEE_RELIEF) g.press(DRINK_A)
        }
        assertTrue(g.over)
        assertTrue("envies : ${g.pees}", g.pees in 2..6)
    }

    @Test
    fun staggersMoreOftenAsTheGameGoesOn() {
        val g = game(7)
        fun run(ms: Int) {
            var left = ms
            while (left > 0) {
                g.tick(50f)
                left -= 50
                // Un joueur parfait redresse le personnage tout de suite, et se soulage vite.
                var guard = 0
                while (g.unsteady && guard++ < 50) g.press(fixKey(g))
                if (g.peePhase == Drink.PEE_RELIEF) repeat(Drink.PEE_PRESSES) { g.press(DRINK_A) }
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
    fun saveAndRestoreKeepAFallAndAnUrinalTrip() {
        val g = game()
        untilWobble(g)
        g.tick(10_000f)
        g.tick(300f)
        val fell = DrinkGame.restore(g.save())
        assertTrue(fell.fallen)
        assertEquals(g.fallMs, fell.fallMs, 0.001f)
        assertEquals(g.falls, fell.falls)
        assertEquals(g.lean, fell.lean, 0f)

        val h = game(5)
        untilUrinal(h)
        repeat(7) { h.press(DRINK_B) }
        val copy = DrinkGame.restore(h.save())
        assertEquals(Drink.PEE_RELIEF, copy.peePhase)
        assertEquals(7, copy.peeProgress)
        assertEquals(h.peeSide, copy.peeSide)
        assertEquals(h.pees, copy.pees)
        repeat(Drink.PEE_PRESSES - 7) { copy.press(DRINK_A) }
        assertEquals(Drink.PEE_BACK, copy.peePhase)
    }

    @Test
    fun anUnusableSaveGivesANewGame() {
        assertEquals(0, DrinkGame.restore(emptyList()).cl)
        assertEquals(0, DrinkGame.restore(listOf(99.0, 1.0)).cl)
        // Une ancienne sauvegarde (version 1) n'est plus lue.
        assertEquals(0, DrinkGame.restore(listOf(1.0, 5.0, 0.0, 5.0, 0.0, 0.0, 100_000.0)).cl)
    }

    @Test
    fun clockText() {
        assertEquals("2:00", drinkClockText(120_000f))
        assertEquals("1:59", drinkClockText(119_000f))
        assertEquals("0:01", drinkClockText(300f))
        assertEquals("0:00", drinkClockText(0f))
    }
}
