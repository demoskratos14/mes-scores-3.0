package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvadersTest {

    private class S(val x: Float, val y: Float, val vy: Float = -Invaders.SHOT_SPEED, val hostile: Boolean = false)

    private class B(val x: Float, val vx: Float = Invaders.BONUS_SPEED, val points: Int = 150)

    /**
     * Une partie dans l'état voulu (construite par [InvadersGame.restore]) : par défaut la grille entière, centrée, en haut, les
     * quatre abris neufs, le vaisseau au centre, ni tir ni bonus, un tir ennemi dans 2 s et un bonus dans 20 s. [only] ne garde
     * en vie que les envahisseurs (rangée, colonne) listés.
     */
    private fun state(
        playerX: Float = 4.5f,
        alive: Boolean = true,
        lives: Int = 3,
        level: Int = 1,
        score: Int = 0,
        invulnerableMs: Int = 0,
        respawnMs: Int = 0,
        levelDelayMs: Int = 0,
        overMs: Int = 0,
        shotTimerMs: Int = 2000,
        bonusTimerMs: Int = 20_000,
        direction: Int = 1,
        originX: Float = Invaders.centeredOriginX(),
        originY: Float = 2f,
        only: List<Pair<Int, Int>>? = null,
        bunkers: Boolean = true,
        shots: List<S> = emptyList(),
        bonus: B? = null
    ): InvadersGame {
        var mask = 0
        if (only == null) {
            mask = (1 shl (Invaders.ROWS * Invaders.COLS)) - 1
        } else {
            for ((row, col) in only) mask = mask or (1 shl (row * Invaders.COLS + col))
        }
        val cells = BooleanArray(Invaders.BUNKERS * Invaders.BUNKER_ROWS * Invaders.BUNKER_COLS)
        if (bunkers) {
            for (b in 0 until Invaders.BUNKERS) {
                for (row in 0 until Invaders.BUNKER_ROWS) {
                    for (col in 0 until Invaders.BUNKER_COLS) cells[Invaders.cellIndex(b, row, col)] = Invaders.bunkerShape(row, col)
                }
            }
        }
        val words = (0 until (cells.size + 31) / 32).map { word ->
            var packed = 0
            for (i in 0 until 32) {
                val index = word * 32 + i
                if (index < cells.size && cells[index]) packed = packed or (1 shl i)
            }
            packed
        }
        val data = mutableListOf(
            score, lives, level, 0, if (alive) 1 else 0, playerX.toBits(),
            invulnerableMs, respawnMs, levelDelayMs, overMs, shotTimerMs, bonusTimerMs,
            if (direction > 0) 1 else 0, originX.toBits(), originY.toBits(), mask
        )
        data.addAll(words)
        data.add(shots.size)
        for (s in shots) data.addAll(listOf(s.x.toBits(), s.y.toBits(), s.vy.toBits(), if (s.hostile) 1 else 0))
        if (bonus == null) data.add(0) else data.addAll(listOf(1, bonus.x.toBits(), bonus.vx.toBits(), bonus.points))
        return InvadersGame.restore(data, Random(1))!!
    }

    /** Un tir du joueur juste sous l'envahisseur ([row], [col]) d'une grille de départ : il le touche au premier pas. */
    private fun shotAt(row: Int, col: Int): S =
        S(Invaders.centeredOriginX() + col * Invaders.SPACING_X, 2f + row * Invaders.SPACING_Y + 0.3f)

    private fun hostileShots(game: InvadersGame): Int = game.shots.count { it.hostile }

    private fun bunkerCellCount(game: InvadersGame): Int {
        var count = 0
        for (b in 0 until Invaders.BUNKERS) {
            for (row in 0 until Invaders.BUNKER_ROWS) {
                for (col in 0 until Invaders.BUNKER_COLS) if (game.isBunkerCell(b, row, col)) count++
            }
        }
        return count
    }

    // --- Réglages ---

    @Test
    fun theWavesChangeTheFacesEveryLevel() {
        for (wave in Invaders.WAVES) {
            assertEquals(Invaders.ROWS, wave.size)
            assertTrue(wave.all { it in Asteroids.ENEMIES.indices })
        }
        assertEquals(Invaders.WAVES[0][0], Invaders.typeAt(1, 0))
        assertEquals(Invaders.WAVES[1][2], Invaders.typeAt(2, 2))
        // Après la dernière vague, on repart de la première.
        assertEquals(Invaders.typeAt(1, 3), Invaders.typeAt(1 + Invaders.WAVES.size, 3))
        assertTrue(Invaders.typeAt(1, 0) != Invaders.typeAt(2, 0))
        assertTrue(Invaders.BONUS_TYPE in Asteroids.ENEMIES.indices)
    }

    @Test
    fun theGridSpeedsUpAsInvadersFall() {
        val full = Invaders.formationSpeed(1, Invaders.ROWS * Invaders.COLS)
        val half = Invaders.formationSpeed(1, 15)
        val last = Invaders.formationSpeed(1, 1)
        assertEquals(0.9f, full, 0.0001f)
        assertTrue(full < half && half < last)
        assertTrue(Invaders.formationSpeed(3, 30) > full)
    }

    @Test
    fun levelsMakeTheGameHarder() {
        assertEquals(2f, Invaders.startY(1), 0.0001f)
        assertEquals(2.35f, Invaders.startY(2), 0.0001f)
        assertEquals(Invaders.startY(6), Invaders.startY(20), 0.0001f)
        assertEquals(1f, Invaders.speedFactor(1), 0.0001f)
        assertEquals(2f, Invaders.speedFactor(50), 0.0001f)
        assertEquals(900, Invaders.shotDelayMs(1))
        assertEquals(350, Invaders.shotDelayMs(50))
        assertEquals(2, Invaders.maxEnemyShots(1))
        assertEquals(5, Invaders.maxEnemyShots(50))
        assertTrue(Invaders.enemyShotSpeed(5) > Invaders.enemyShotSpeed(1))
    }

    @Test
    fun theBunkersFitInTheFieldAndAreShaped() {
        for (b in 0 until Invaders.BUNKERS) {
            val left = Invaders.bunkerLeft(b)
            val right = left + Invaders.BUNKER_COLS * Invaders.BUNKER_CELL
            assertTrue(left > 0f && right < Invaders.WIDTH)
            if (b > 0) assertTrue(left > Invaders.bunkerLeft(b - 1) + Invaders.BUNKER_COLS * Invaders.BUNKER_CELL)
        }
        assertFalse(Invaders.bunkerShape(0, 0))
        assertFalse(Invaders.bunkerShape(0, Invaders.BUNKER_COLS - 1))
        assertFalse(Invaders.bunkerShape(Invaders.BUNKER_ROWS - 1, 2))
        assertTrue(Invaders.bunkerShape(1, 0))
        assertTrue(Invaders.BUNKER_Y + Invaders.BUNKER_ROWS * Invaders.BUNKER_CELL < Invaders.PLAYER_Y - Invaders.PLAYER_SPRITE / 2f)
    }

    // --- Début de partie ---

    @Test
    fun aNewGameHasTheWholeGridAndFourBunkers() {
        val game = InvadersGame(Random(1))
        assertEquals(Invaders.ROWS * Invaders.COLS, game.aliveCount)
        for (row in 0 until Invaders.ROWS) {
            for (col in 0 until Invaders.COLS) assertTrue(game.isAlive(row, col))
        }
        // La grille est centrée et à l'intérieur des marges.
        assertEquals(Invaders.WIDTH, game.invaderX(0) + game.invaderX(Invaders.COLS - 1), 0.001f)
        assertTrue(game.invaderX(0) - Invaders.SPRITE / 2f >= Invaders.MARGIN)
        assertEquals(Invaders.startY(1), game.invaderY(0), 0.0001f)
        assertEquals(80, bunkerCellCount(game))
        assertEquals(Invaders.LIVES, game.lives)
        assertEquals(1, game.level)
        assertEquals(0, game.score)
        assertTrue(game.playerAlive)
        assertTrue(game.invulnerableMs > 0)
        assertEquals(Invaders.WIDTH / 2f, game.playerX, 0.0001f)
        assertFalse(game.isOver)
        assertFalse(game.isBetweenLevels)
        assertNull(game.bonus)
    }

    @Test
    fun resetStartsOverAndReleasesTheControls() {
        val game = InvadersGame(Random(1))
        game.moveLeft = true
        game.fire = true
        game.tick(500)
        game.reset()
        assertEquals(0, game.score)
        assertEquals(Invaders.LIVES, game.lives)
        assertFalse(game.moveLeft)
        assertFalse(game.fire)
        assertEquals(30, game.aliveCount)
        assertTrue(game.shots.isEmpty())
    }

    @Test
    fun negativeTimeChangesNothing() {
        val game = state()
        val before = game.save()
        game.tick(-50)
        game.tick(0)
        assertEquals(before, game.save())
    }

    // --- Le vaisseau ---

    @Test
    fun theShipSlidesWhileTheButtonIsHeld() {
        val right = state()
        right.moveRight = true
        right.tick(500)
        assertEquals(4.5f + Invaders.PLAYER_SPEED * 0.5f, right.playerX, 0.01f)

        val left = state()
        left.moveLeft = true
        left.tick(500)
        assertEquals(4.5f - Invaders.PLAYER_SPEED * 0.5f, left.playerX, 0.01f)

        val both = state()
        both.moveLeft = true
        both.moveRight = true
        both.tick(500)
        assertEquals(4.5f, both.playerX, 0.0001f)
    }

    @Test
    fun theShipStopsAtTheEdges() {
        val right = state()
        right.moveRight = true
        right.tick(1500)
        assertEquals(Invaders.WIDTH - Invaders.PLAYER_MARGIN, right.playerX, 0.0001f)
        assertTrue(right.playerAlive)

        val left = state()
        left.moveLeft = true
        left.tick(1500)
        assertEquals(Invaders.PLAYER_MARGIN, left.playerX, 0.0001f)
    }

    // --- Les tirs du joueur ---

    @Test
    fun holdingFireShootsUpWithACooldown() {
        val game = state()
        game.fire = true
        game.tick(16)
        assertEquals(1, game.shots.size)
        val shot = game.shots[0]
        assertFalse(shot.hostile)
        assertEquals(-Invaders.SHOT_SPEED, shot.vy, 0.01f)
        assertEquals(4.5f, shot.x, 0.0001f)
        game.tick(16)
        assertEquals(1, game.shots.size) // le délai entre deux tirs n'est pas passé
        game.tick(Invaders.FIRE_COOLDOWN_MS.toLong())
        assertEquals(2, game.shots.size)
    }

    @Test
    fun neverMoreThanTwoPlayerShotsInTheAir() {
        val game = state()
        game.fire = true
        var most = 0
        repeat(120) {
            game.tick(16)
            most = maxOf(most, game.shots.count { !it.hostile })
        }
        assertEquals(Invaders.MAX_PLAYER_SHOTS, most)
    }

    @Test
    fun shotsLeaveTheField() {
        val game = state()
        game.fire = true
        game.tick(16)
        game.fire = false
        game.tick(1500)
        assertTrue(game.shots.isEmpty())
    }

    // --- Les envahisseurs abattus ---

    @Test
    fun eachRowPaysItsPoints() {
        for (row in 0 until Invaders.ROWS) {
            val game = state(only = listOf(row to 2), shots = listOf(shotAt(row, 2)))
            game.tick(16)
            assertEquals(0, game.aliveCount)
            assertEquals(Invaders.ROW_POINTS[row], game.score)
            assertTrue(game.shots.isEmpty())
        }
        assertTrue(Invaders.ROW_POINTS[0] > Invaders.ROW_POINTS[Invaders.ROWS - 1])
    }

    @Test
    fun aShotKillsOnlyOneInvader() {
        val game = state(shots = listOf(shotAt(4, 2)))
        game.tick(16)
        assertEquals(29, game.aliveCount)
        assertFalse(game.isAlive(4, 2))
        assertTrue(game.isAlive(4, 1) && game.isAlive(4, 3) && game.isAlive(3, 2))
        assertEquals(10, game.score)
        assertTrue(game.sparks.isNotEmpty())
    }

    // --- La grille ---

    @Test
    fun theGridMovesSideways() {
        val game = state()
        game.tick(500)
        assertEquals(Invaders.centeredOriginX() + 0.9f * 0.5f, game.originX, 0.02f)
        assertEquals(1, game.direction)
        assertEquals(2f, game.originY, 0.0001f)
    }

    @Test
    fun theGridTurnsAndDropsAtTheEdges() {
        val atRight = state(originX = 2.7f)
        atRight.tick(300)
        assertEquals(-1, atRight.direction)
        assertEquals(2f + Invaders.DROP, atRight.originY, 0.001f)

        val atLeft = state(originX = 0.8f, direction = -1)
        atLeft.tick(300)
        assertEquals(1, atLeft.direction)
        assertEquals(2f + Invaders.DROP, atLeft.originY, 0.001f)
    }

    @Test
    fun theGridStopsWhileTheShipIsGone() {
        val game = state(alive = false, lives = 2, respawnMs = Invaders.RESPAWN_DELAY_MS)
        val x = game.originX
        game.tick(500)
        assertEquals(x, game.originX, 0.0001f)
        assertEquals(0, hostileShots(game))
    }

    @Test
    fun invadersShootAndAreLimitedInNumber() {
        val game = state(shotTimerMs = 10)
        game.tick(16)
        assertEquals(1, hostileShots(game))
        val shot = game.shots.first { it.hostile }
        assertEquals(Invaders.enemyShotSpeed(1), shot.vy, 0.01f)
        // L'envahisseur qui tire est le plus bas de sa colonne.
        assertTrue(shot.y > game.invaderY(Invaders.ROWS - 1))

        val crowded = state(shotTimerMs = 10, shots = listOf(S(1f, 3f, 4.6f, true), S(8f, 3f, 4.6f, true)))
        crowded.tick(16)
        assertEquals(Invaders.maxEnemyShots(1), hostileShots(crowded))
    }

    // --- Les abris ---

    @Test
    fun aPlayerShotNibblesABunkerFromBelow() {
        val x = Invaders.bunkerLeft(0) + 0.1f
        val game = state(shots = listOf(S(x, Invaders.BUNKER_Y + 3 * Invaders.BUNKER_CELL + 0.28f)))
        game.tick(16)
        assertFalse(game.isBunkerCell(0, 3, 0))
        assertFalse(game.isBunkerCell(0, 2, 0)) // le tir creuse d'une case de plus
        assertTrue(game.isBunkerCell(0, 1, 0))
        assertTrue(game.shots.isEmpty())
        assertEquals(78, bunkerCellCount(game))
    }

    @Test
    fun anEnemyShotNibblesABunkerFromAbove() {
        val x = Invaders.bunkerLeft(0) + 0.5f
        val game = state(shots = listOf(S(x, Invaders.BUNKER_Y - 0.05f, Invaders.enemyShotSpeed(1), true)))
        game.tick(16)
        assertFalse(game.isBunkerCell(0, 0, 2))
        assertFalse(game.isBunkerCell(0, 1, 2))
        assertTrue(game.isBunkerCell(0, 2, 2))
        assertEquals(0, hostileShots(game))
        assertTrue(game.playerAlive)
    }

    @Test
    fun theGridCrushesTheBunkersItReaches() {
        // Un seul envahisseur, à la hauteur des abris, au-dessus de l'abri de gauche.
        val game = state(only = listOf(4 to 0), originY = 7f)
        assertTrue(game.isBunkerCell(0, 1, 5))
        game.tick(16)
        assertFalse(game.isBunkerCell(0, 1, 5))
        assertFalse(game.isBunkerCell(0, 2, 4))
        assertTrue(game.isBunkerCell(0, 1, 0))
        assertTrue(game.isBunkerCell(3, 1, 0))
    }

    // --- Les vies ---

    @Test
    fun anEnemyShotDestroysTheShipAndClearsTheEnemyShots() {
        val game = state(shots = listOf(S(4.5f, Invaders.PLAYER_Y - 0.1f, 4.6f, true), S(1f, 3f, 4.6f, true)))
        game.tick(16)
        assertFalse(game.playerAlive)
        assertEquals(2, game.lives)
        assertEquals(0, hostileShots(game))
        assertFalse(game.isOver)
    }

    @Test
    fun theProtectionAfterARespawnKeepsTheShipSafe() {
        val game = state(invulnerableMs = 1000, shots = listOf(S(4.5f, Invaders.PLAYER_Y - 0.1f, 4.6f, true)))
        game.tick(100)
        assertTrue(game.playerAlive)
        assertEquals(3, game.lives)
    }

    @Test
    fun theShipComesBackProtectedAtTheCentre() {
        val game = state(alive = false, lives = 2, respawnMs = Invaders.RESPAWN_DELAY_MS, playerX = 1f)
        game.tick(Invaders.RESPAWN_DELAY_MS.toLong() + 100)
        assertTrue(game.playerAlive)
        assertTrue(game.invulnerableMs > 0)
        assertEquals(Invaders.WIDTH / 2f, game.playerX, 0.0001f)
        assertEquals(2, game.lives)
    }

    @Test
    fun theLastLifeEndsTheGameAfterTheExplosion() {
        val game = state(lives = 1, shots = listOf(S(4.5f, Invaders.PLAYER_Y - 0.1f, 4.6f, true)))
        game.tick(16)
        assertFalse(game.playerAlive)
        assertEquals(0, game.lives)
        assertFalse(game.isOver)
        game.tick(Invaders.GAME_OVER_DELAY_MS.toLong() + 100)
        assertTrue(game.isOver)
        val score = game.score
        game.tick(1000)
        assertEquals(score, game.score)
    }

    @Test
    fun theGridReachingTheShipLosesAtOnce() {
        // Un seul envahisseur, déjà arrivé à la hauteur du vaisseau.
        val game = state(only = listOf(4 to 0), originY = 8.2f)
        game.tick(16)
        assertFalse(game.playerAlive)
        assertEquals(0, game.lives)
        game.tick(Invaders.GAME_OVER_DELAY_MS.toLong() + 100)
        assertTrue(game.isOver)
    }

    @Test
    fun anExtraLifeEveryTwoThousandPoints() {
        val game = state(score = 1990, lives = 2, only = listOf(4 to 2), shots = listOf(shotAt(4, 2)))
        game.tick(16)
        assertEquals(2000, game.score)
        assertEquals(3, game.lives)
    }

    @Test
    fun noLifeIsGivenBackToALostShip() {
        val game = state(
            alive = false, lives = 0, overMs = 1000, score = 1990,
            only = listOf(4 to 2), shots = listOf(shotAt(4, 2))
        )
        game.tick(16)
        assertEquals(2000, game.score)
        assertEquals(0, game.lives)
        assertFalse(game.playerAlive)
    }

    // --- Les niveaux ---

    @Test
    fun clearingTheGridStartsTheNextOneAfterAPause() {
        val game = state(only = listOf(4 to 2), shots = listOf(shotAt(4, 2)))
        game.tick(16)
        assertEquals(0, game.aliveCount)
        assertTrue(game.isBetweenLevels)
        assertEquals(1, game.level)
        game.tick(Invaders.LEVEL_DELAY_MS.toLong() + 100)
        assertEquals(2, game.level)
        assertFalse(game.isBetweenLevels)
        assertEquals(30, game.aliveCount)
        assertEquals(Invaders.startY(2), game.originY, 0.01f)
        assertEquals(80, bunkerCellCount(game))
        assertTrue(game.playerAlive)
    }

    // --- Le vaisseau bonus ---

    @Test
    fun aBonusShipCrossesTheTopAndPaysWhenShot() {
        val spawned = state(bonusTimerMs = 10)
        spawned.tick(16)
        val saucer = spawned.bonus
        assertNotNull(saucer)
        assertTrue(saucer!!.x < 0.5f || saucer.x > Invaders.WIDTH - 0.5f)
        assertTrue(saucer.points in Invaders.BONUS_POINTS.toList())

        val shot = state(bonus = B(4.5f, points = 150), shots = listOf(S(4.5f, Invaders.BONUS_Y + 0.2f)))
        shot.tick(16)
        assertNull(shot.bonus)
        assertEquals(150, shot.score)
    }

    @Test
    fun theBonusShipLeavesAndNeedsEnoughInvaders() {
        val leaving = state(bonus = B(8.9f))
        leaving.tick(500)
        assertNull(leaving.bonus)
        assertEquals(0, leaving.score)

        val few = state(only = listOf(4 to 0, 4 to 1, 4 to 2), bonusTimerMs = 10)
        few.tick(16)
        assertNull(few.bonus)
    }

    // --- Sauvegarde ---

    @Test
    fun aGameSurvivesASaveAndRestore() {
        val game = InvadersGame(Random(5))
        game.fire = true
        repeat(300) {
            game.moveRight = it % 80 < 40
            game.moveLeft = !game.moveRight
            game.tick(16)
        }
        val copy = InvadersGame.restore(game.save(), Random(5))
        assertNotNull(copy)
        assertEquals(game.save(), copy!!.save())
        assertEquals(game.score, copy.score)
        assertEquals(game.aliveCount, copy.aliveCount)
        assertEquals(bunkerCellCount(game), bunkerCellCount(copy))
    }

    @Test
    fun aBonusAndShotsSurviveASaveAndRestore() {
        val game = state(bonus = B(3f, -2.4f, 300), shots = listOf(S(2f, 5f), S(6f, 9f, 4.6f, true)), only = listOf(0 to 0, 4 to 5, 2 to 3))
        val copy = InvadersGame.restore(game.save(), Random(1))
        assertNotNull(copy)
        assertEquals(game.save(), copy!!.save())
        assertEquals(3, copy.aliveCount)
        assertTrue(copy.isAlive(2, 3) && !copy.isAlive(2, 2))
        assertEquals(300, copy.bonus!!.points)
        assertEquals(2, copy.shots.size)
    }

    @Test
    fun theSameSeedAndInputsGiveTheSameGame() {
        fun play(): List<Int> {
            val game = InvadersGame(Random(9))
            game.fire = true
            repeat(400) {
                game.moveLeft = it % 100 < 30
                game.moveRight = it % 100 in 50..80
                game.tick(16)
            }
            return game.save()
        }
        assertEquals(play(), play())
    }

    @Test
    fun aDamagedSaveIsRefused() {
        val good = state().save()
        assertNotNull(InvadersGame.restore(good))
        assertNull(InvadersGame.restore(emptyList()))
        assertNull(InvadersGame.restore(good.dropLast(1)))
        assertNull(InvadersGame.restore(good.take(10)))
        // Un nombre qui n'en est pas un à la place de la position du vaisseau ou de la grille.
        assertNull(InvadersGame.restore(good.toMutableList().also { it[5] = Float.NaN.toBits() }))
        assertNull(InvadersGame.restore(good.toMutableList().also { it[13] = Float.NaN.toBits() }))
        // Trop de vies, une partie finie qui garde des vies, un vaisseau en vol sans vie.
        assertNull(InvadersGame.restore(good.toMutableList().also { it[1] = Invaders.MAX_LIVES + 1 }))
        assertNull(InvadersGame.restore(good.toMutableList().also { it[3] = 1 }))
        assertNull(InvadersGame.restore(good.toMutableList().also { it[1] = 0 }))
        // Un sens de grille qui n'en est pas un, un masque d'envahisseurs trop grand.
        assertNull(InvadersGame.restore(good.toMutableList().also { it[12] = 2 }))
        assertNull(InvadersGame.restore(good.toMutableList().also { it[15] = 1 shl (Invaders.ROWS * Invaders.COLS) }))
        // Un tir hors du terrain.
        val withShot = state(shots = listOf(S(2f, 5f))).save()
        assertNotNull(InvadersGame.restore(withShot))
        assertNull(InvadersGame.restore(withShot.toMutableList().also { it[withShot.size - 5] = 99f.toBits() }))
    }
}
