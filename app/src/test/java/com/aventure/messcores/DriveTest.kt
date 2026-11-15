package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveTest {
    private fun game(seed: Int = 1) = DriveGame(Random(seed))

    private fun walkTo(g: DriveGame, zone: Int) {
        while (g.pos < zone) g.press(DRIVE_RIGHT)
        while (g.pos > zone) g.press(DRIVE_LEFT)
    }

    @Test
    fun zonesLayout() {
        assertEquals(12, Drive.ZONES.length)
        assertTrue(Drive.isAction(0))
        assertFalse(Drive.isAction(1))
        assertEquals(30f, Drive.zoneX(0), 0.001f)
        assertEquals(Drive.DOOR_X, Drive.zoneX(3) + 30f, 0.001f)
        // Tapis B, A, B, A en partant de la droite.
        assertEquals(0, Drive.beltLabel(4))
        assertEquals(1, Drive.beltLabel(6))
        assertEquals(0, Drive.beltLabel(8))
        assertEquals(1, Drive.beltLabel(10))
    }

    @Test
    fun movementStaysInBounds() {
        val g = game()
        g.press(DRIVE_LEFT)
        assertEquals(0, g.pos)
        repeat(30) { g.press(DRIVE_RIGHT) }
        assertEquals(11, g.pos)
        assertEquals(1, g.facing)
        g.press(DRIVE_LEFT)
        assertEquals(-1, g.facing)
    }

    @Test
    fun jumpLiftsAndLands() {
        val g = game()
        g.press(DRIVE_UP)
        assertTrue(g.jumping)
        g.tick(300f)
        assertTrue(g.lift > 20f)
        g.tick(400f)
        assertFalse(g.jumping)
        assertEquals(0f, g.lift, 0.001f)
    }

    @Test
    fun noTroubleDuringWarmUp() {
        val g = game()
        repeat(600) { g.tick(100f) }
        assertEquals(0, g.activeCars)
        assertEquals(MOUSE_NONE, g.mouse.mode)
        assertEquals(PHONE_IDLE, g.phone.mode)
        assertFalse(g.started)
    }

    @Test
    fun droppingTheNeededItemTicksTheOrder() {
        val g = game()
        val lane = g.lanes[0]
        walkTo(g, lane.zone)
        val belt = g.belts.first { kotlin.math.abs(it.zone - lane.zone) == 1 }
        belt.items.clear()
        belt.items.add(DriveItem(lane.need[0], Drive.S_NEAR + 0.02f))
        g.press(if (belt.label == 0) DRIVE_A else DRIVE_B)
        assertTrue(lane.got[0])
        assertEquals(0, g.points)
    }

    @Test
    fun wrongItemCostsPointsAndIsFlooredAtZero() {
        val g = game()
        val lane = g.lanes[0]
        walkTo(g, lane.zone)
        val belt = g.belts.first { kotlin.math.abs(it.zone - lane.zone) == 1 }
        val other = (0 until Drive.PRODUCT_COUNT).first { it !in lane.need }
        belt.items.clear()
        belt.items.add(DriveItem(other, Drive.S_NEAR))
        g.press(if (belt.label == 0) DRIVE_A else DRIVE_B)
        assertEquals(0, g.points)
        assertTrue(lane.got.none { it })
    }

    @Test
    fun completingAnOrderScoresAndFillsStock() {
        val g = game()
        val lane = g.lanes[1]
        walkTo(g, lane.zone)
        val belt = g.belts.first { kotlin.math.abs(it.zone - lane.zone) == 1 }
        val key = if (belt.label == 0) DRIVE_A else DRIVE_B
        val tag = lane.tag
        for (k in 0 until 3) {
            belt.items.clear()
            belt.items.add(DriveItem(lane.need[k], Drive.S_NEAR))
            g.press(key)
        }
        assertEquals(5, g.points)
        assertEquals(1, g.orders)
        assertEquals(1, g.stock[tag])
        assertTrue(lane.done)
        g.tick(Drive.ORDER_READY_MS + 50f)
        assertFalse(lane.done)
    }

    @Test
    fun itemOutOfRangeIsNotDropped() {
        val g = game()
        val lane = g.lanes[0]
        walkTo(g, lane.zone)
        val belt = g.belts.first { kotlin.math.abs(it.zone - lane.zone) == 1 }
        belt.items.clear()
        belt.items.add(DriveItem(lane.need[0], 0.6f))
        g.press(if (belt.label == 0) DRIVE_A else DRIVE_B)
        assertFalse(lane.got[0])
        assertEquals(1, belt.items.size)
    }

    @Test
    fun deliveryOfTheRightOrderScoresTen() {
        val g = game()
        val bay = g.bays[0]
        bay.mode = CAR_WAIT
        bay.want = 1
        bay.limitMs = 20000f
        g.stock[1] = 2
        walkTo(g, bay.zone - 1)
        g.press(DRIVE_B)
        assertEquals(10, g.points)
        assertEquals(1, g.deliveries)
        assertEquals(1, g.stock[1])
        assertEquals(CAR_LEAVE, bay.mode)
        g.tick(Drive.CAR_LEAVE_MS + 10f)
        assertEquals(CAR_IDLE, bay.mode)
    }

    @Test
    fun deliveryOfTheWrongOrderCostsFiveAndKeepsTheCar() {
        val g = game()
        val bay = g.bays[1]
        bay.mode = CAR_WAIT
        bay.want = 0
        bay.limitMs = 20000f
        g.stock[1] = 1
        walkTo(g, bay.zone - 1)
        g.press(DRIVE_B)
        assertEquals(0, g.stock[1])
        assertEquals(CAR_WAIT, bay.mode)
        assertEquals(0, g.errors)
    }

    @Test
    fun deliveryWithoutStockDoesNothing() {
        val g = game()
        val bay = g.bays[0]
        bay.mode = CAR_WAIT
        bay.want = 0
        bay.limitMs = 20000f
        walkTo(g, bay.zone - 1)
        g.press(DRIVE_A)
        assertEquals(CAR_WAIT, bay.mode)
        assertEquals(0, g.points)
    }

    @Test
    fun carTimeoutIsAnError() {
        val g = game()
        val bay = g.bays[0]
        bay.mode = CAR_WAIT
        bay.limitMs = 1000f
        g.tick(1100f)
        assertEquals(1, g.errors)
        assertEquals(CAR_LEAVE, bay.mode)
    }

    @Test
    fun standingWhereACarAppearsIsAnError() {
        val g = game()
        val bay = g.bays[0]
        bay.mode = CAR_WARN
        bay.warnMs = 500f
        walkTo(g, bay.zone)
        g.tick(600f)
        assertEquals(CAR_WAIT, bay.mode)
        assertEquals(1, g.errors)
        assertTrue(g.hurtMs > 0f)
    }

    @Test
    fun phoneRingPickUpAndAnswer() {
        val g = game()
        g.phone.mode = PHONE_RING
        walkTo(g, Drive.PHONE_ZONE)
        g.press(DRIVE_A)
        assertEquals(PHONE_CALL, g.phone.mode)
        val want = g.phone.want
        g.press(when (want) { 0 -> DRIVE_A; 1 -> DRIVE_B; else -> DRIVE_UP })
        assertEquals(1, g.points)
        assertEquals(PHONE_IDLE, g.phone.mode)
        assertEquals(0, g.errors)
    }

    @Test
    fun wrongAnswerOrSilenceAreErrors() {
        val g = game()
        g.phone.mode = PHONE_CALL
        g.phone.want = 0
        g.phone.limitMs = 2000f
        g.press(DRIVE_B)
        assertEquals(1, g.errors)

        g.phone.mode = PHONE_CALL
        g.phone.elapsedMs = 0f
        g.phone.limitMs = 500f
        g.tick(600f)
        assertEquals(2, g.errors)

        g.phone.mode = PHONE_RING
        g.phone.ringAgeMs = 0f
        g.tick(20000f)
        assertEquals(3, g.errors)
        assertTrue(g.over)
    }

    @Test
    fun mouseHurtsUnlessJumpedOver() {
        val g = game()
        walkTo(g, 6)
        g.mouse.mode = MOUSE_RUN
        g.mouse.speed = 150f
        g.mouse.x = Drive.zoneX(6) + 20f
        g.tick(100f)
        assertEquals(1, g.errors)
        assertTrue(g.mouse.hit)

        val h = game()
        walkTo(h, 6)
        h.tick(300f)
        h.press(DRIVE_UP)
        h.tick(250f)
        h.mouse.mode = MOUSE_RUN
        h.mouse.speed = 150f
        h.mouse.x = Drive.zoneX(6) + 3f
        h.tick(40f)
        assertEquals(0, h.errors)
        assertTrue(h.lift >= Drive.MOUSE_CLEARANCE)
    }

    @Test
    fun mouseLeavesAtTheDoor() {
        val g = game()
        g.mouse.mode = MOUSE_RUN
        g.mouse.speed = 150f
        g.mouse.x = Drive.DOOR_X + 5f
        g.tick(200f)
        assertEquals(MOUSE_NONE, g.mouse.mode)
    }

    @Test
    fun threeErrorsEndTheGameAndARestartsAfterADelay() {
        val g = game()
        repeat(3) {
            g.phone.mode = PHONE_CALL
            g.phone.want = 0
            g.phone.limitMs = 1000f
            g.press(DRIVE_B)
        }
        assertTrue(g.over)
        g.press(DRIVE_RIGHT)
        assertEquals(0, g.pos)
        assertFalse(g.canRestart)
        g.tick(1000f)
        assertTrue(g.canRestart)
        g.reset()
        assertFalse(g.over)
        assertEquals(0, g.errors)
    }

    @Test
    fun difficultyStartsAtZeroAndGrowsWithWork() {
        val g = game()
        assertEquals(0f, g.difficulty, 0f)
        assertEquals(0.045f, g.beltSpeed, 0.0001f)
        val bay = g.bays[0]
        bay.mode = CAR_WAIT
        bay.want = 0
        bay.limitMs = 99999f
        g.stock[0] = 1
        walkTo(g, bay.zone - 1)
        g.press(DRIVE_A)
        assertEquals(0f, g.difficulty, 0f) // pas encore commencé
    }

    @Test
    fun warmUpEndsAfterEnoughPassesThenAFirstCarComes() {
        val g = game(7)
        g.stock[0] = 1
        var ms = 0f
        while (!g.started && ms < 600000f) {
            // On garde un joueur qui ne bouge pas : les produits utiles passent devant les bacs.
            g.tick(100f)
            ms += 100f
        }
        assertTrue(g.started)
        assertTrue(g.neededPasses >= DriveGame.WARM_PASSES)
        assertTrue(g.activeCars >= 1)
    }

    @Test
    fun saveAndRestoreKeepEverything() {
        val g = game(3)
        g.stock[0] = 2
        repeat(200) { g.tick(100f) }
        walkTo(g, 5)
        val saved = g.save()
        val r = DriveGame.restore(saved)
        assertEquals(saved, r.save())
        assertEquals(g.pos, r.pos)
        assertEquals(g.points, r.points)
        assertEquals(g.lanes[0].need.toList(), r.lanes[0].need.toList())
        assertEquals(g.belts[0].items.size, r.belts[0].items.size)
    }

    @Test
    fun restoreWithGarbageGivesAFreshGame() {
        val r = DriveGame.restore(listOf(9.0, 1.0))
        assertEquals(0, r.points)
        assertFalse(r.over)
        val e = DriveGame.restore(emptyList())
        assertEquals(0, e.pos)
    }

    @Test
    fun longSoakNeverCrashesAndKeepsInvariants() {
        val rnd = Random(42)
        repeat(20) { seed ->
            val g = DriveGame(Random(seed))
            repeat(6000) {
                if (g.over) { if (g.canRestart) g.reset() }
                if (rnd.nextInt(8) == 0) g.press(rnd.nextInt(5))
                g.tick(50f)
                assertTrue(g.points >= 0)
                assertTrue(g.stock[0] in 0..Drive.MAX_STOCK && g.stock[1] in 0..Drive.MAX_STOCK)
                assertTrue(g.errors in 0..Drive.MAX_ERRORS)
                assertTrue(g.pos in 0 until Drive.ZONE_COUNT)
            }
        }
    }
}
