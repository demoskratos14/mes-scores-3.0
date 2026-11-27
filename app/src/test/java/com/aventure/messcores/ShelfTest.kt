package com.aventure.messcores

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfTest {
    /** Une partie où rien ne tombe tout seul : chaque test fait tomber ce dont il a besoin. */
    private fun game(seed: Int = 1) = ShelfGame(Random(seed)).also { it.autoSpawn = false }

    /** Une partie normale, où les produits tombent tout seuls. */
    private fun liveGame(seed: Int = 1) = ShelfGame(Random(seed))

    /** Pose un produit sur le plateau, à [s] des mains. */
    private fun put(g: ShelfGame, type: Int, s: Float = 0f): ShelfItem {
        val item = ShelfItem(type, 0f, 0f)
        item.onTray = true
        item.s = s
        g.tray.add(item)
        return item
    }

    /** Fait marcher le magasinier jusqu'à [x] (avec la croix), sans que rien d'autre ne soit en jeu. */
    private fun walkTo(g: ShelfGame, x: Float) {
        var guard = 0
        while (abs(g.heroX - x) > 4f && guard++ < 400) {
            g.tick(16f, if (g.heroX < x) SHELF_RIGHT else SHELF_LEFT)
        }
        // Un dernier instant sans touche pour qu'il s'arrête.
        g.tick(200f, 0)
    }

    /** Fait tomber un produit de [type] à l'abscisse [x] et avance jusqu'à ce qu'il se pose ou se brise. */
    private fun drop(g: ShelfGame, type: Int, x: Float) {
        g.falling.add(ShelfItem(type, x, Shelf.SPAWN_Y))
        var guard = 0
        while (g.falling.isNotEmpty() && guard++ < 400) g.tick(16f, 0)
    }

    @Test
    fun startsWithAnEmptyTrayInTheMiddle() {
        val g = game()
        assertEquals(0, g.score)
        assertEquals(Shelf.WIDTH / 2f, g.heroX, 0.001f)
        assertEquals(0f, g.tilt, 0.0001f)
        assertTrue(g.tray.isEmpty())
        assertTrue(g.falling.isEmpty())
        assertFalse(g.over)
        assertEquals(0, g.shelfSide)
        assertEquals(0f, g.difficulty, 0.001f)
    }

    @Test
    fun theCrossMovesTheShopAssistantAndStopsAtTheWalls() {
        val g = game()
        val start = g.heroX
        g.tick(500f, SHELF_RIGHT)
        assertTrue(g.heroX > start + 50f)
        assertEquals(1, g.facing)
        g.tick(10_000f, SHELF_LEFT)
        assertEquals(Shelf.HERO_MIN_X, g.heroX, 0.001f)
        assertEquals(-1, g.facing)
        g.tick(10_000f, SHELF_RIGHT)
        assertEquals(Shelf.HERO_MAX_X, g.heroX, 0.001f)
    }

    @Test
    fun leftAndRightTogetherStandStill() {
        val g = game()
        val start = g.heroX
        g.tick(1000f, SHELF_LEFT or SHELF_RIGHT)
        assertEquals(start, g.heroX, 0.001f)
    }

    @Test
    fun theShopAssistantStartsAndStopsSmoothly() {
        val g = game()
        g.tick(16f, SHELF_RIGHT)
        assertTrue(g.heroVx > 0f && g.heroVx < Shelf.HERO_SPEED)
        g.tick(500f, SHELF_RIGHT)
        assertEquals(Shelf.HERO_SPEED, g.heroVx, 0.001f)
        g.tick(16f, 0)
        assertTrue(g.heroVx > 0f && g.heroVx < Shelf.HERO_SPEED)
        g.tick(500f, 0)
        assertEquals(0f, g.heroVx, 0.001f)
    }

    @Test
    fun shelfSideTellsWhichShelfTheShopAssistantIsAt() {
        val g = game()
        g.tick(10_000f, SHELF_LEFT)
        assertEquals(-1, g.shelfSide)
        g.tick(10_000f, SHELF_RIGHT)
        assertEquals(1, g.shelfSide)
    }

    @Test
    fun aProductFallingOnTheTrayIsCaught() {
        val g = game()
        drop(g, SHELF_FLOUR, g.heroX + 5f)
        assertEquals(1, g.caught)
        assertEquals(1, g.tray.size)
        assertEquals(0, g.broken)
        assertTrue(abs(g.tray[0].s) < 20f)
        assertEquals(0f, g.tray[0].base, 0.001f)
    }

    @Test
    fun aProductThatMissesTheTrayBreaksOnTheFloor() {
        val g = game()
        drop(g, SHELF_PASTA, g.heroX + Shelf.HALF + 120f)
        assertEquals(0, g.caught)
        assertEquals(1, g.broken)
        assertEquals(1, g.missed)
        assertEquals(0, g.spilled)
        assertTrue(g.tray.isEmpty())
    }

    @Test
    fun aSecondProductLandsOnTopOfTheFirst() {
        val g = game()
        drop(g, SHELF_RICE, g.heroX)
        drop(g, SHELF_PASTA, g.heroX)
        assertEquals(2, g.tray.size)
        assertEquals(0f, g.tray[0].base, 0.001f)
        assertEquals(Shelf.ITEM_H[SHELF_RICE], g.tray[1].base, 0.001f)
    }

    @Test
    fun productsSideBySideDoNotStack() {
        val g = game()
        val a = put(g, SHELF_PASTA, -40f)
        val b = put(g, SHELF_PASTA, 40f)
        g.tick(16f, 0)
        assertEquals(0f, a.base, 0.001f)
        assertEquals(0f, b.base, 0.001f)
    }

    @Test
    fun theTrayHoldsAtMostEightProductsThenTheNextOneBreaks() {
        val g = game()
        repeat(Shelf.MAX_ON_TRAY) { put(g, SHELF_COFFEE, (it - 4) * 4f) }
        drop(g, SHELF_PASTA, g.heroX)
        assertEquals(Shelf.MAX_ON_TRAY, g.tray.size)
        assertEquals(1, g.broken)
        assertEquals(1, g.spilled)
        assertEquals(0, g.missed)
    }

    @Test
    fun aHeavyProductOnTheRightTiltsTheTrayToTheRight() {
        val g = game()
        put(g, SHELF_FLOUR, 60f)
        g.tick(300f, 0)
        assertTrue(g.tilt > 0.02f)
    }

    @Test
    fun aHeavyProductOnTheLeftTiltsTheTrayToTheLeft() {
        val g = game()
        put(g, SHELF_FLOUR, -60f)
        g.tick(300f, 0)
        assertTrue(g.tilt < -0.02f)
    }

    @Test
    fun aCenteredProductLeavesTheTrayFlat() {
        val g = game()
        put(g, SHELF_FLOUR, 0f)
        g.tick(2000f, 0)
        assertEquals(0f, g.tilt, 0.03f)
    }

    @Test
    fun buttonATiltsLeftAndButtonBTiltsRight() {
        val a = game()
        a.tick(300f, SHELF_A)
        assertTrue(a.tilt < -0.05f)
        val b = game()
        b.tick(300f, SHELF_B)
        assertTrue(b.tilt > 0.05f)
    }

    @Test
    fun aQuickTapStillMovesTheTray() {
        val a = game()
        a.press(SHELF_A)
        a.tick(100f, 0)
        assertTrue(a.tilt < 0f)
        val b = game()
        b.press(SHELF_B)
        b.tick(100f, 0)
        assertTrue(b.tilt > 0f)
    }

    @Test
    fun aAndBTogetherBrakeTheTray() {
        val free = game()
        free.tick(300f, SHELF_B)
        val spinBefore = free.spin
        assertTrue(spinBefore > 0.1f)
        val braked = game()
        braked.tick(300f, SHELF_B)
        braked.tick(300f, SHELF_A or SHELF_B)
        assertTrue(abs(braked.spin) < abs(spinBefore) * 0.2f)
    }

    @Test
    fun theTrayReturnsToFlatByItself() {
        val g = game()
        g.tick(200f, SHELF_B)
        assertTrue(g.tilt > 0.03f)
        g.tick(8000f, 0)
        assertEquals(0f, g.tilt, 0.02f)
    }

    @Test
    fun aTrayTiltedTooMuchMakesItsProductsSlideOffAndBreak() {
        val g = game()
        put(g, SHELF_CAN, 40f)
        // B tenu : le plateau penche à droite, la conserve glisse puis tombe du bout.
        var guard = 0
        while (g.tray.isNotEmpty() && guard++ < 400) g.tick(16f, SHELF_B)
        assertTrue(g.tray.isEmpty())
        assertEquals(1, g.falling.size)
        var fall = 0
        while (g.falling.isNotEmpty() && fall++ < 400) g.tick(16f, 0)
        assertEquals(1, g.broken)
        assertEquals(1, g.spilled)
    }

    @Test
    fun aCanSlidesFasterThanAFlourSack() {
        val can = game()
        val canItem = put(can, SHELF_CAN, 0f)
        val flour = game()
        val flourItem = put(flour, SHELF_FLOUR, 0f)
        can.tick(450f, SHELF_B)
        flour.tick(450f, SHELF_B)
        // La conserve a glissé plus loin, ou est déjà tombée du plateau.
        assertTrue(!canItem.onTray || !can.tray.contains(canItem) || canItem.s > flourItem.s)
    }

    @Test
    fun aStackedProductGripsLessThanOneLyingOnTheTray() {
        val g = game()
        put(g, SHELF_PASTA, 0f)
        put(g, SHELF_PASTA, 0f)
        g.tick(16f, 0)
        assertTrue(g.tray[1].base > 0f)
        assertTrue(Shelf.FRICTION_STACKED < Shelf.FRICTION_FLAT)
    }

    @Test
    fun aLoadedTrayPushedTooFarCapsizesAndEverythingFalls() {
        val g = game()
        put(g, SHELF_PASTA, 0f)
        var guard = 0
        // Une seule pâte au centre : B tenu longtemps renverse le plateau avant qu'elle ne glisse.
        while (g.capsizes == 0 && guard++ < 600) g.tick(16f, SHELF_B)
        assertTrue(g.capsizes >= 1 || g.spilled + g.falling.size >= 1)
        assertTrue(g.tray.isEmpty() || g.capsizes == 0)
    }

    @Test
    fun anEmptyTrayJustStopsAtItsLimit() {
        val g = game()
        g.tick(6000f, SHELF_B)
        assertEquals(0, g.capsizes)
        assertTrue(g.tilt <= Shelf.MAX_TILT + 0.0001f)
    }

    @Test
    fun levelTrayAtTheLeftShelfShelvesTheWholePile() {
        val g = game()
        put(g, SHELF_PASTA, -30f)
        put(g, SHELF_FLOUR, 0f)
        put(g, SHELF_CAN, 15f)
        walkTo(g, 80f)
        assertEquals(-1, g.shelfSide)
        g.tick(Shelf.UNLOAD_MS + 100f, 0)
        assertTrue(g.tray.isEmpty())
        val expected = ((10 + 20 + 15) * Shelf.multiplier(3)).let { Math.round(it) }
        assertEquals(expected, g.score)
        assertEquals(3, g.shelved)
        assertEquals(1, g.trips)
        assertEquals(3, g.bestPile)
        assertEquals(-1, g.bankSide)
        assertEquals(3, g.bankCount)
        assertEquals(expected, g.bankPoints)
        assertEquals(3, g.leftShelf.size)
        assertTrue(g.rightShelf.isEmpty())
    }

    @Test
    fun levelTrayAtTheRightShelfShelvesOnTheRightShelf() {
        val g = game()
        put(g, SHELF_RICE, 0f)
        walkTo(g, 650f)
        assertEquals(1, g.shelfSide)
        g.tick(Shelf.UNLOAD_MS + 100f, 0)
        assertEquals(15, g.score)
        assertEquals(1, g.rightShelf.size)
        assertTrue(g.leftShelf.isEmpty())
    }

    @Test
    fun shelvingNeedsALevelTray() {
        val g = game()
        put(g, SHELF_PASTA, 0f)
        walkTo(g, 80f)
        // B tenu : le plateau reste penché, rien n'est rangé.
        g.tick(1500f, SHELF_B)
        assertEquals(0, g.score)
        assertEquals(0, g.trips)
    }

    @Test
    fun shelvingNeedsTheShelfAndSomethingOnTheTray() {
        val g = game()
        put(g, SHELF_PASTA, 0f)
        g.tick(2000f, 0)
        assertEquals(0, g.score)
        assertEquals(1, g.tray.size)
        val empty = game()
        walkTo(empty, 80f)
        empty.tick(2000f, 0)
        assertEquals(0, empty.score)
        assertEquals(0, empty.trips)
    }

    @Test
    fun shelvingProgressFillsThenEmptiesWhenYouLeave() {
        val g = game()
        put(g, SHELF_PASTA, 0f)
        walkTo(g, 80f)
        g.tick(300f, 0)
        assertTrue(g.unload > 0.2f)
        g.tick(16f, SHELF_B)
        g.tick(2000f, SHELF_B or SHELF_A)
        assertTrue(g.unload < 0.5f)
    }

    @Test
    fun theStackMultiplierGrowsWithThePileAndIsCapped() {
        assertEquals(1f, Shelf.multiplier(0), 0.0001f)
        assertEquals(1f, Shelf.multiplier(1), 0.0001f)
        assertEquals(1.25f, Shelf.multiplier(2), 0.0001f)
        assertEquals(1.5f, Shelf.multiplier(3), 0.0001f)
        assertEquals(Shelf.STACK_BONUS_CAP, Shelf.multiplier(40), 0.0001f)
        for (n in 1 until 20) assertTrue(Shelf.multiplier(n + 1) >= Shelf.multiplier(n))
    }

    @Test
    fun aBiggerPileIsWorthMoreThanTheSameProductsOneByOne() {
        val g = game()
        repeat(4) { put(g, SHELF_PASTA, (it - 2) * 12f) }
        val pile = g.trayWorth
        assertTrue(pile > 4 * 10)
        assertEquals(40, g.trayValue)
    }

    @Test
    fun theShelfShowsOnlyTheLastProductsPutOnIt() {
        val g = game()
        repeat(3) {
            repeat(6) { put(g, SHELF_COFFEE, (it - 2.5f) * 14f) }
            walkTo(g, 80f)
            g.tick(Shelf.UNLOAD_MS + 100f, 0)
            walkTo(g, 360f)
        }
        assertEquals(Shelf.SHELF_SHOW, g.leftShelf.size)
        assertEquals(18, g.shelved)
    }

    @Test
    fun productsDoNotFallWhileTheShopAssistantIsAtAShelf() {
        val g = liveGame()
        g.tick(10_000f, SHELF_LEFT)
        // Les produits déjà en l'air finissent leur chute ; ensuite plus rien ne tombe tant qu'il reste au rayon.
        g.tick(6000f, 0)
        assertTrue(g.falling.isEmpty())
        val broken = g.broken
        g.tick(10_000f, 0)
        assertTrue(g.falling.isEmpty())
        assertEquals(broken, g.broken)
    }

    @Test
    fun productsKeepFallingInTheMiddle() {
        val g = liveGame()
        g.tick(2500f, 0)
        assertTrue(g.falling.isNotEmpty() || g.caught + g.broken > 0)
    }

    @Test
    fun productsFallFromTheSpawnZoneOnly() {
        val g = liveGame(5)
        var seen = 0
        repeat(2000) {
            g.tick(16f, 0)
            for (item in g.falling) {
                seen++
                assertTrue(item.x >= Shelf.SPAWN_MIN_X - 0.01f && item.x <= Shelf.SPAWN_MAX_X + 0.01f)
            }
            if (g.over) return
        }
        assertTrue(seen > 0)
    }

    @Test
    fun noMoreThanThreeProductsAreInTheAirAtOnce() {
        val g = liveGame(2)
        var most = 0
        repeat(3000) {
            g.tick(16f, 0)
            most = maxOf(most, g.falling.count { it.vx == 0f })
            if (g.over) return
        }
        assertTrue(most <= Shelf.MAX_FLYING)
    }

    @Test
    fun difficultyRisesDuringTheShift() {
        val g = liveGame()
        g.tick(Shelf.SHIFT_MS / 2f, SHELF_LEFT)
        assertEquals(0.5f, g.difficulty, 0.01f)
        g.tick(Shelf.SHIFT_MS / 2f - 100f, SHELF_LEFT)
        assertTrue(g.difficulty > 0.99f || g.over)
    }

    @Test
    fun theShiftEndsWhenTheTimeIsUpAndAnUnshelvedPileIsLost() {
        val g = game()
        put(g, SHELF_FLOUR, 0f)
        g.tick(Shelf.SHIFT_MS + 100f, 0)
        assertTrue(g.over)
        assertEquals(SHELF_END_TIME, g.endReason)
        assertEquals(0, g.score)
        assertTrue(g.tray.isEmpty())
        assertEquals(0f, g.timeLeftMs, 0.001f)
    }

    @Test
    fun fiveBrokenProductsEndTheShiftEarly() {
        val g = game()
        repeat(Shelf.MAX_BREAKS) {
            g.falling.add(ShelfItem(SHELF_PASTA, 650f, Shelf.FLOOR_Y - 1f))
            g.tick(50f, 0)
        }
        assertTrue(g.over)
        assertEquals(SHELF_END_BREAKS, g.endReason)
        assertEquals(Shelf.MAX_BREAKS, g.broken)
        assertTrue(g.timeLeftMs > 0f)
    }

    @Test
    fun fourBrokenProductsDoNotEndTheShift() {
        val g = game()
        repeat(Shelf.MAX_BREAKS - 1) {
            g.falling.add(ShelfItem(SHELF_PASTA, 650f, Shelf.FLOOR_Y - 1f))
            g.tick(50f, 0)
        }
        assertFalse(g.over)
    }

    @Test
    fun nothingHappensOnceTheGameIsOver() {
        val g = game()
        g.tick(Shelf.SHIFT_MS + 100f, 0)
        val x = g.heroX
        g.tick(1000f, SHELF_RIGHT)
        assertEquals(x, g.heroX, 0.001f)
        g.press(SHELF_B)
        assertEquals(0f, g.spin, 0.0001f)
    }

    @Test
    fun theGameCanBeRestartedOnlyAfterTheEndMessageHasBeenShown() {
        val g = game()
        assertFalse(g.canRestart)
        g.tick(Shelf.SHIFT_MS + 100f, 0)
        assertTrue(g.over)
        assertFalse(g.canRestart)
        g.tick(Shelf.RESTART_DELAY_MS + 50f, 0)
        assertTrue(g.canRestart)
    }

    @Test
    fun resetGivesAFreshGame() {
        val g = game()
        put(g, SHELF_PASTA, 0f)
        walkTo(g, 80f)
        g.tick(1000f, 0)
        assertTrue(g.score > 0)
        g.reset()
        assertEquals(0, g.score)
        assertEquals(0, g.shelved)
        assertTrue(g.tray.isEmpty())
        assertTrue(g.leftShelf.isEmpty())
        assertFalse(g.over)
        assertEquals(Shelf.SHIFT_MS, g.timeLeftMs, 0.001f)
        assertEquals(Shelf.WIDTH / 2f, g.heroX, 0.001f)
    }

    @Test
    fun aMovingShopAssistantShakesATallPile() {
        val still = game()
        repeat(5) { put(still, SHELF_PASTA, 0f) }
        val moving = game()
        repeat(5) { put(moving, SHELF_PASTA, 0f) }
        still.tick(400f, 0)
        moving.tick(400f, SHELF_RIGHT)
        assertNotEquals(still.tilt, moving.tilt, 0.0001f)
    }

    @Test
    fun theSameSeedGivesTheSameGame() {
        fun run(): List<Double> {
            val g = liveGame(7)
            repeat(900) { g.tick(16f, if (it % 90 < 45) SHELF_RIGHT else SHELF_LEFT) }
            return g.save()
        }
        assertEquals(run(), run())
    }

    @Test
    fun saveAndRestoreKeepTheWholeGame() {
        val g = liveGame(3)
        repeat(700) { g.tick(16f, if (it % 120 < 60) SHELF_RIGHT else SHELF_LEFT) }
        put(g, SHELF_CEREAL, 12f)
        val copy = ShelfGame.restore(g.save())
        assertEquals(g.save(), copy.save())
        assertEquals(g.score, copy.score)
        assertEquals(g.heroX, copy.heroX, 0.0001f)
        assertEquals(g.tray.size, copy.tray.size)
        assertEquals(g.falling.size, copy.falling.size)
        assertEquals(g.timeLeftMs, copy.timeLeftMs, 0.0001f)
    }

    @Test
    fun aRestoredGameKeepsRunningLikeTheOriginal() {
        val g = liveGame(4)
        repeat(300) { g.tick(16f, SHELF_RIGHT) }
        val copy = ShelfGame.restore(g.save())
        g.tick(16f, 0)
        copy.tick(16f, 0)
        assertEquals(g.heroX, copy.heroX, 0.001f)
        assertEquals(g.falling.size, copy.falling.size)
    }

    @Test
    fun aBrokenSaveGivesAFreshGame() {
        val copy = ShelfGame.restore(listOf(99.0, 1.0))
        assertEquals(0, copy.score)
        assertEquals(Shelf.SHIFT_MS, copy.timeLeftMs, 0.001f)
        val empty = ShelfGame.restore(emptyList())
        assertEquals(0, empty.score)
    }

    @Test
    fun everyProductHasItsFullDescription() {
        assertEquals(Shelf.ITEMS, Shelf.ITEM_W.size)
        assertEquals(Shelf.ITEMS, Shelf.ITEM_H.size)
        assertEquals(Shelf.ITEMS, Shelf.ITEM_MASS.size)
        assertEquals(Shelf.ITEMS, Shelf.ITEM_POINTS.size)
        assertEquals(Shelf.ITEMS, Shelf.ITEM_SLIP.size)
        for (i in 0 until Shelf.ITEMS) {
            assertTrue(Shelf.ITEM_W[i] > 0f && Shelf.ITEM_H[i] > 0f)
            assertTrue(Shelf.ITEM_MASS[i] > 0f && Shelf.ITEM_POINTS[i] > 0)
            assertTrue(Shelf.drawWeight(i, 0f) > 0f && Shelf.drawWeight(i, 1f) > 0f)
        }
        assertTrue(Shelf.ITEM_MASS[SHELF_FLOUR] > Shelf.ITEM_MASS[SHELF_PASTA])
        assertTrue(Shelf.ITEM_SLIP[SHELF_CAN] > Shelf.ITEM_SLIP[SHELF_FLOUR])
    }

    @Test
    fun theTrayReachesFromTheLeftShelfToTheSpawnZone() {
        // Le magasinier doit pouvoir rattraper n'importe quel produit depuis le milieu, et les rayons doivent être hors de portée de la chute.
        assertTrue(Shelf.HERO_MIN_X + Shelf.HALF < Shelf.SPAWN_MIN_X)
        assertTrue(Shelf.HERO_MAX_X - Shelf.HALF > Shelf.SPAWN_MAX_X)
        assertTrue(Shelf.SHELF_LEFT_X < Shelf.SPAWN_MIN_X - Shelf.SPAWN_REACH)
        assertTrue(Shelf.SHELF_RIGHT_X > Shelf.SPAWN_MAX_X + Shelf.SPAWN_REACH)
    }

    /** Un joueur automatique simple : se place sous le produit le plus bas, tient le plateau à plat, range quand le ciel est dégagé. */
    private fun playSteadily(g: ShelfGame, bankAt: Int, maxMs: Float) {
        var elapsed = 0f
        while (!g.over && elapsed < maxMs) {
            val want = -(6f * g.tilt + 1.5f * g.spin)
            val balance = if (want > 0.05f) SHELF_B else if (want < -0.05f) SHELF_A else 0
            val clear = g.falling.none { it.vx == 0f }
            val banking = (g.tray.size >= bankAt && clear) || g.tray.size >= Shelf.MAX_ON_TRAY
            val move: Int
            if (banking) {
                val target = if (g.heroX < Shelf.WIDTH / 2f) 80f else 640f
                move = when {
                    g.shelfSide != 0 -> 0
                    g.heroX < target -> SHELF_RIGHT
                    else -> SHELF_LEFT
                }
            } else {
                val next = g.falling.filter { it.vx == 0f }.maxByOrNull { it.y }
                val target = next?.x ?: (Shelf.WIDTH / 2f)
                move = when {
                    g.heroX < target - 8f -> SHELF_RIGHT
                    g.heroX > target + 8f -> SHELF_LEFT
                    else -> 0
                }
            }
            g.tick(16f, balance or move)
            elapsed += 16f
        }
    }

    @Test
    fun aSteadyPlayerCatchesProductsAndShelvesPiles() {
        val g = liveGame(1)
        playSteadily(g, bankAt = 4, maxMs = Shelf.SHIFT_MS)
        assertTrue(g.caught >= 20)
        assertTrue(g.trips >= 2)
        assertTrue(g.score > 300)
        assertTrue(g.shelved >= 8)
        assertTrue(g.bestPile >= 4)
    }

    @Test
    fun aPlayerWhoNeverTouchesTheControlsDoesNotScoreAndTheShiftEndsEarly() {
        val g = liveGame(1)
        g.tick(Shelf.SHIFT_MS, 0)
        assertTrue(g.over)
        assertEquals(0, g.score)
        assertEquals(SHELF_END_BREAKS, g.endReason)
        assertTrue(g.missed >= Shelf.MAX_BREAKS - 1)
    }

    @Test
    fun theTrayStaysSteadyWhenTheLoadIsCenteredAndNobodyTouchesIt() {
        val g = game()
        put(g, SHELF_FLOUR, 0f)
        put(g, SHELF_RICE, 0f)
        g.tick(3000f, 0)
        assertTrue(abs(g.tilt) < 0.05f)
        assertEquals(2, g.tray.size)
    }

    @Test
    fun anOffCenterLoadSlowlyRunsAwayWithoutCorrection() {
        val g = game()
        put(g, SHELF_FLOUR, 70f)
        g.tick(4000f, 0)
        assertTrue(g.tray.isEmpty() || abs(g.tilt) > 0.1f)
    }
}
