package com.aventure.messcores

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AsteroidsTest {

    /** Une roche immobile pour [state] (elle n'a pas de forme voulue : seules sa place, sa vitesse et sa taille comptent). */
    private class R(val x: Float, val y: Float, val size: Int = Asteroids.LARGE, val vx: Float = 0f, val vy: Float = 0f)

    private class S(val x: Float, val y: Float, val vx: Float = 0f, val vy: Float = 0f, val life: Int = 1000, val hostile: Boolean = false)

    private class U(
        val x: Float,
        val y: Float,
        val vx: Float = 2.1f,
        val type: Int = 0,
        val hp: Int = Asteroids.enemy(type).hp,
        val shotMs: Int = 900
    )

    /** Une roche loin du vaisseau (au centre par défaut) : elle garde le niveau en cours sans gêner. */
    private val farRock = R(0.5f, 0.5f)

    /** Une partie dans l'état voulu (construite par [AsteroidsGame.restore]) : le vaisseau au centre, une roche loin, un ennemi dans 20 s. */
    private fun state(
        shipX: Float = 4.5f,
        shipY: Float = 7f,
        shipVx: Float = 0f,
        shipVy: Float = 0f,
        angle: Float = 0f,
        alive: Boolean = true,
        lives: Int = 3,
        level: Int = 1,
        score: Int = 0,
        invulnerableMs: Int = 0,
        respawnMs: Int = 0,
        levelDelayMs: Int = 0,
        ufoTimerMs: Int = 20_000,
        overMs: Int = 0,
        rocks: List<R> = listOf(farRock),
        shots: List<S> = emptyList(),
        ufo: U? = null
    ): AsteroidsGame {
        val data = mutableListOf(
            score, lives, level, 0, if (alive) 1 else 0,
            shipX.toBits(), shipY.toBits(), shipVx.toBits(), shipVy.toBits(), angle.toBits(),
            invulnerableMs, respawnMs, levelDelayMs, ufoTimerMs, overMs
        )
        data.add(rocks.size)
        for (r in rocks) {
            data.addAll(listOf(r.x.toBits(), r.y.toBits(), r.vx.toBits(), r.vy.toBits(), r.size, 0, 0f.toBits(), 0f.toBits()))
        }
        data.add(shots.size)
        for (s in shots) {
            data.addAll(listOf(s.x.toBits(), s.y.toBits(), s.vx.toBits(), s.vy.toBits(), s.life, if (s.hostile) 1 else 0))
        }
        if (ufo == null) {
            data.add(0)
        } else {
            data.addAll(listOf(1, ufo.x.toBits(), ufo.y.toBits(), ufo.vx.toBits(), 0f.toBits(), ufo.type, 800, ufo.shotMs, ufo.hp))
        }
        return AsteroidsGame.restore(data, Random(1))!!
    }

    // --- Réglages ---

    @Test
    fun levelsAddRocksAndSpeed() {
        assertEquals(4, Asteroids.rocksFor(1))
        assertEquals(5, Asteroids.rocksFor(2))
        assertEquals(11, Asteroids.rocksFor(8))
        assertEquals(11, Asteroids.rocksFor(50))
        assertEquals(1f, Asteroids.speedFactor(1), 0.0001f)
        assertEquals(1.08f, Asteroids.speedFactor(2), 0.0001f)
        assertEquals(2f, Asteroids.speedFactor(100), 0.0001f)
    }

    @Test
    fun smallerRocksAreWorthMore() {
        assertEquals(20, Asteroids.pointsFor(Asteroids.LARGE))
        assertEquals(50, Asteroids.pointsFor(Asteroids.MEDIUM))
        assertEquals(100, Asteroids.pointsFor(Asteroids.SMALL))
        assertTrue(Asteroids.radiusOf(Asteroids.LARGE) > Asteroids.radiusOf(Asteroids.MEDIUM))
        assertTrue(Asteroids.radiusOf(Asteroids.MEDIUM) > Asteroids.radiusOf(Asteroids.SMALL))
    }

    @Test
    fun theEnemyCatalogueIsConsistent() {
        assertEquals(17, Asteroids.ENEMIES.size)
        assertTrue(Asteroids.ENEMIES.size <= Asteroids.ATLAS_COLS * 4) // tous tiennent dans la planche
        for (enemy in Asteroids.ENEMIES) {
            assertTrue(enemy.size > 0f)
            assertTrue(enemy.radius > 0f && enemy.radius <= enemy.size / 2f)
            assertTrue(enemy.hp in 1..4)
            assertTrue(enemy.points > 0 && enemy.fireMs > 0 && enemy.shotSpeed > 0f && enemy.speed > 0f)
            assertTrue(enemy.minLevel >= 1)
            assertTrue(enemy.look in Asteroids.LOOK_SPIN..Asteroids.LOOK_HEADING)
        }
        assertEquals(Asteroids.ENEMIES.last(), Asteroids.enemy(99))
        assertEquals(Asteroids.ENEMIES.first(), Asteroids.enemy(-3))
    }

    @Test
    fun levelsUnlockEnemies() {
        assertEquals(listOf(0, 1, 2), Asteroids.enemyTypesFor(1))
        assertEquals(listOf(0, 1, 2, 3, 4, 11), Asteroids.enemyTypesFor(2))
        assertEquals(Asteroids.ENEMIES.size, Asteroids.enemyTypesFor(8).size)
        var before = 0
        for (level in 1..10) {
            val count = Asteroids.enemyTypesFor(level).size
            assertTrue(count >= before)
            before = count
        }
        // Le premier niveau n'a que des tireurs au hasard, d'un seul coup.
        assertTrue(Asteroids.enemyTypesFor(1).all { Asteroids.enemy(it).aimError < 0f && Asteroids.enemy(it).hp == 1 })
    }

    @Test
    fun theHeadingFollowsTheCourse() {
        assertEquals(1.5708f, Asteroids.headingOf(2f, 0f), 0.001f)
        assertEquals(-1.5708f, Asteroids.headingOf(-2f, 0f), 0.001f)
        assertEquals(0f, Asteroids.headingOf(0f, -1f), 0.001f)
    }

    @Test
    fun theFieldWrapsAround() {
        assertEquals(8f, Asteroids.wrap(-1f, 9f), 0.0001f)
        assertEquals(1f, Asteroids.wrap(10f, 9f), 0.0001f)
        assertEquals(3f, Asteroids.wrap(3f, 9f), 0.0001f)
        assertEquals(-1f, Asteroids.wrapDelta(8f, 9f), 0.0001f)
        assertEquals(1f, Asteroids.wrapDelta(-8f, 9f), 0.0001f)
        assertEquals(3f, Asteroids.wrapDelta(3f, 9f), 0.0001f)
    }

    @Test
    fun everyRockShapeHasIrregularRadii() {
        for (shape in 0 until Asteroids.SHAPE_COUNT) {
            val radii = Asteroids.shapeRadii(shape)
            assertEquals(Asteroids.SHAPE_POINTS, radii.size)
            assertTrue(radii.all { it in 0.78f..1.12f })
        }
        // Une forme hors des bornes ne plante pas : elle est ramenée dans la liste.
        assertEquals(Asteroids.SHAPE_POINTS, Asteroids.shapeRadii(99).size)
    }

    // --- Début de partie ---

    @Test
    fun aNewGameStartsWithFourLargeRocksAwayFromTheShip() {
        val game = AsteroidsGame(Random(1))
        assertEquals(4, game.rocks.size)
        assertTrue(game.rocks.all { it.size == Asteroids.LARGE })
        for (rock in game.rocks) {
            val dx = Asteroids.wrapDelta(rock.x - game.shipX, Asteroids.WIDTH)
            val dy = Asteroids.wrapDelta(rock.y - game.shipY, Asteroids.HEIGHT)
            assertTrue(hypot(dx, dy) >= Asteroids.SPAWN_CLEARANCE)
        }
        assertEquals(Asteroids.LIVES, game.lives)
        assertEquals(1, game.level)
        assertEquals(0, game.score)
        assertTrue(game.shipAlive)
        assertTrue(game.invulnerableMs > 0)
        assertFalse(game.isOver)
        assertFalse(game.isBetweenLevels)
        assertNull(game.ufo)
    }

    @Test
    fun resetStartsOverAndReleasesTheControls() {
        val game = AsteroidsGame(Random(1))
        game.turnLeft = true
        game.fire = true
        game.tick(500)
        game.reset()
        assertEquals(0, game.score)
        assertEquals(Asteroids.LIVES, game.lives)
        assertFalse(game.turnLeft)
        assertFalse(game.fire)
        assertEquals(4, game.rocks.size)
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
    fun theShipTurnsWhileTheButtonIsHeld() {
        val right = state()
        right.turnRight = true
        right.tick(500)
        assertEquals(Asteroids.ROTATE_SPEED * 0.5f, right.shipAngle, 0.01f)

        val left = state()
        left.turnLeft = true
        left.tick(500)
        assertEquals(Asteroids.TWO_PI - Asteroids.ROTATE_SPEED * 0.5f, left.shipAngle, 0.01f)

        val still = state()
        still.tick(500)
        assertEquals(0f, still.shipAngle, 0.0001f)
    }

    @Test
    fun thrustPushesTheShipTowardItsNose() {
        val game = state()
        game.thrust = true
        game.tick(1000)
        assertTrue(game.thrusting)
        assertTrue(game.shipVy < -5f && game.shipVy > -7f)
        assertTrue(abs(game.shipVx) < 0.001f)
    }

    @Test
    fun thrustFollowsTheAngle() {
        // Le nez vers la droite : un quart de tour dans le sens des aiguilles d'une montre.
        val game = state(angle = Asteroids.TWO_PI / 4f)
        game.thrust = true
        game.tick(500)
        assertTrue(game.shipVx > 2f)
        assertTrue(abs(game.shipVy) < 0.01f)
    }

    @Test
    fun theShipKeepsDriftingAndNeverExceedsTheMaximumSpeed() {
        val drifting = state(shipVy = -4f)
        drifting.tick(1000)
        assertTrue(drifting.shipVy < -2.5f) // frottement très léger : il file encore
        assertFalse(drifting.thrusting)

        val fast = state()
        fast.thrust = true
        fast.tick(5000)
        assertEquals(Asteroids.MAX_SPEED, hypot(fast.shipVx, fast.shipVy), 0.01f)
    }

    @Test
    fun theShipComesOutOnTheOtherSide() {
        val game = state(shipX = 0.1f, shipVx = -5f)
        game.tick(100)
        assertTrue(game.shipX > 8f)
        assertTrue(game.shipAlive)
    }

    // --- Les tirs ---

    @Test
    fun holdingFireShootsAlongTheNoseWithACooldown() {
        val game = state()
        game.fire = true
        game.tick(16)
        assertEquals(1, game.shots.size)
        val shot = game.shots[0]
        assertFalse(shot.hostile)
        assertEquals(-Asteroids.SHOT_SPEED, shot.vy, 0.01f)
        assertEquals(0f, shot.vx, 0.01f)
        game.tick(16)
        assertEquals(1, game.shots.size) // le délai entre deux tirs n'est pas passé
        game.tick(Asteroids.FIRE_COOLDOWN_MS.toLong())
        assertEquals(2, game.shots.size)
    }

    @Test
    fun neverMoreThanFourShotsInTheAir() {
        val game = state()
        game.fire = true
        var most = 0
        repeat(120) {
            game.tick(16)
            most = maxOf(most, game.shots.count { !it.hostile })
        }
        assertEquals(Asteroids.MAX_SHOTS, most)
    }

    @Test
    fun shotsFadeAway() {
        val game = state()
        game.fire = true
        game.tick(16)
        game.fire = false
        game.tick(Asteroids.SHOT_LIFE_MS.toLong() + 50)
        assertTrue(game.shots.isEmpty())
    }

    // --- Les roches ---

    /** Un vaisseau en bas, une roche de la taille [size] au-dessus, un seul tir droit dessus : la roche est touchée. */
    private fun shootRock(size: Int, extra: List<R> = emptyList(), score: Int = 0, lives: Int = 3): AsteroidsGame {
        val game = state(shipY = 9f, rocks = listOf(R(4.5f, 3f, size)) + extra, score = score, lives = lives)
        game.fire = true
        game.tick(16)
        game.fire = false
        game.tick(800)
        return game
    }

    @Test
    fun aLargeRockBreaksIntoTwoMediumOnes() {
        val game = shootRock(Asteroids.LARGE, extra = listOf(farRock))
        assertEquals(20, game.score)
        assertEquals(3, game.rocks.size)
        assertEquals(2, game.rocks.count { it.size == Asteroids.MEDIUM })
        assertEquals(1, game.rocks.count { it.size == Asteroids.LARGE })
        assertTrue(game.shots.isEmpty())
    }

    @Test
    fun aMediumRockBreaksIntoTwoSmallOnes() {
        val game = shootRock(Asteroids.MEDIUM, extra = listOf(farRock))
        assertEquals(50, game.score)
        assertEquals(2, game.rocks.count { it.size == Asteroids.SMALL })
    }

    @Test
    fun aSmallRockJustDisappears() {
        val game = shootRock(Asteroids.SMALL, extra = listOf(farRock))
        assertEquals(100, game.score)
        assertEquals(listOf(Asteroids.LARGE), game.rocks.map { it.size })
    }

    @Test
    fun rocksDriftAndWrapAround() {
        val game = state(rocks = listOf(R(8.9f, 3f, Asteroids.SMALL, vx = 3f, vy = 0f)))
        game.tick(200)
        val rock = game.rocks[0]
        assertTrue(rock.x < 1f)
        assertEquals(3f, rock.y, 0.0001f)
    }

    // --- Les niveaux ---

    @Test
    fun clearingTheLastRockStartsTheNextLevelAfterAPause() {
        val game = shootRock(Asteroids.SMALL)
        assertTrue(game.rocks.isEmpty())
        assertTrue(game.isBetweenLevels)
        assertEquals(1, game.level)
        game.tick(Asteroids.LEVEL_DELAY_MS.toLong() + 100)
        assertEquals(2, game.level)
        assertFalse(game.isBetweenLevels)
        assertEquals(Asteroids.rocksFor(2), game.rocks.size)
        assertTrue(game.rocks.all { it.size == Asteroids.LARGE })
        assertTrue(game.shipAlive)
    }

    // --- Les vies ---

    @Test
    fun touchingARockDestroysTheShip() {
        val game = state(rocks = listOf(R(4.5f, 7f)))
        game.tick(16)
        assertFalse(game.shipAlive)
        assertEquals(2, game.lives)
        assertTrue(game.sparks.isNotEmpty())
        assertFalse(game.isOver)
    }

    @Test
    fun theProtectionAfterARespawnKeepsTheShipSafe() {
        val game = state(rocks = listOf(R(4.5f, 7f)), invulnerableMs = 1000)
        game.tick(500)
        assertTrue(game.shipAlive)
        assertEquals(3, game.lives)
        game.tick(600)
        assertFalse(game.shipAlive) // la protection est finie, la roche est toujours là
    }

    @Test
    fun theShipWaitsForAClearCentreThenComesBackProtected() {
        // Une roche immobile au centre : le vaisseau ne peut pas revenir.
        val blocked = state(alive = false, lives = 2, respawnMs = Asteroids.RESPAWN_DELAY_MS, rocks = listOf(R(4.5f, 7f)))
        blocked.tick(4000)
        assertFalse(blocked.shipAlive)

        val free = state(alive = false, lives = 2, respawnMs = Asteroids.RESPAWN_DELAY_MS)
        free.tick(Asteroids.RESPAWN_DELAY_MS.toLong() + 100)
        assertTrue(free.shipAlive)
        assertTrue(free.invulnerableMs > 0)
        assertEquals(Asteroids.WIDTH / 2f, free.shipX, 0.0001f)
        assertEquals(Asteroids.HEIGHT / 2f, free.shipY, 0.0001f)
        assertEquals(2, free.lives)
    }

    @Test
    fun anEnemyShotDestroysTheShip() {
        val game = state(shots = listOf(S(4.5f, 7f, hostile = true)))
        game.tick(16)
        assertFalse(game.shipAlive)
        assertEquals(2, game.lives)
        assertTrue(game.shots.isEmpty())
    }

    @Test
    fun theLastLifeEndsTheGameAfterTheExplosion() {
        val game = state(lives = 1, rocks = listOf(R(4.5f, 7f)))
        game.tick(16)
        assertFalse(game.shipAlive)
        assertEquals(0, game.lives)
        assertFalse(game.isOver)
        game.tick(Asteroids.GAME_OVER_DELAY_MS.toLong() + 100)
        assertTrue(game.isOver)
        val score = game.score
        game.tick(1000)
        assertEquals(score, game.score)
    }

    @Test
    fun anExtraLifeEveryTenThousandPoints() {
        val game = shootRock(Asteroids.SMALL, extra = listOf(farRock), score = 9950, lives = 2)
        assertEquals(10_050, game.score)
        assertEquals(3, game.lives)
    }

    @Test
    fun noLifeIsGivenBackToALostShip() {
        // La dernière vie est perdue ; un tir encore en l'air casse une roche et passe les 10 000 points : la partie finit quand même.
        val game = state(
            alive = false, lives = 0, overMs = 1000, score = 9950,
            rocks = listOf(R(4.5f, 3f, Asteroids.SMALL), farRock),
            shots = listOf(S(4.5f, 3.5f, vy = -12f, life = 500))
        )
        game.tick(100)
        assertEquals(10_050, game.score)
        assertEquals(0, game.lives)
        assertFalse(game.shipAlive)
    }

    // --- Les ennemis ---

    @Test
    fun anEnemyComesInFromASide() {
        val game = state(ufoTimerMs = 10)
        game.tick(16)
        val enemy = game.ufo
        assertNotNull(enemy)
        assertTrue(enemy!!.type in Asteroids.enemyTypesFor(1))
        assertEquals(Asteroids.enemy(enemy.type).hp, enemy.hp)
        assertTrue(enemy.x < 0.5f || enemy.x > Asteroids.WIDTH - 0.5f)
    }

    @Test
    fun theEnemyComesFromTheTypesOfTheLevel() {
        val game = state(level = 5, ufoTimerMs = 10)
        game.tick(16)
        assertTrue(game.ufo!!.type in Asteroids.enemyTypesFor(5))
    }

    @Test
    fun noEnemyWhileTheShipIsGone() {
        val game = state(alive = false, lives = 2, respawnMs = Asteroids.RESPAWN_DELAY_MS, ufoTimerMs = 10, rocks = listOf(R(4.5f, 7f)))
        game.tick(500)
        assertNull(game.ufo)
    }

    @Test
    fun shootingAnEnemyScoresItsPoints() {
        val saucer = state(ufo = U(1f, 3f, type = 0), shots = listOf(S(1f, 3f, life = 500)))
        saucer.tick(16)
        assertNull(saucer.ufo)
        assertEquals(Asteroids.enemy(0).points, saucer.score)

        val fighter = state(ufo = U(1f, 3f, type = 11), shots = listOf(S(1f, 3f, life = 500)))
        fighter.tick(16)
        assertNull(fighter.ufo)
        assertEquals(Asteroids.enemy(11).points, fighter.score)
        assertTrue(fighter.score > saucer.score)
    }

    @Test
    fun aToughEnemyTakesSeveralShots() {
        val twoShots = state(ufo = U(1f, 3f, type = 16), shots = List(2) { S(1f, 3f, life = 500) })
        twoShots.tick(16)
        assertNotNull(twoShots.ufo)
        assertEquals(2, twoShots.ufo!!.hp)
        assertEquals(0, twoShots.score)
        assertTrue(twoShots.shots.isEmpty())

        val fourShots = state(ufo = U(1f, 3f, type = 16), shots = List(4) { S(1f, 3f, life = 500) })
        fourShots.tick(16)
        assertNull(fourShots.ufo)
        assertEquals(Asteroids.enemy(16).points, fourShots.score)
    }

    @Test
    fun anEnemyLeavesTheField() {
        val game = state(ufo = U(9.5f, 3f))
        game.tick(500)
        assertNull(game.ufo)
        assertEquals(0, game.score)
    }

    @Test
    fun aRandomShooterFiresAtItsOwnSpeed() {
        val game = state(ufo = U(4.5f, 2f, vx = 0.5f, type = 0, shotMs = 10))
        game.tick(16)
        val shot = game.shots.firstOrNull { it.hostile }
        assertNotNull(shot)
        assertEquals(Asteroids.enemy(0).shotSpeed, hypot(shot!!.vx, shot.vy), 0.01f)
    }

    @Test
    fun anAimingEnemyShootsAtTheShip() {
        val game = state(ufo = U(4.5f, 2f, vx = 0.5f, type = 11, shotMs = 10))
        game.tick(16)
        val shot = game.shots.firstOrNull { it.hostile }
        assertNotNull(shot)
        // Le vaisseau est en bas : le tir descend presque tout droit.
        assertTrue(shot!!.vy > 5f)
        assertTrue(abs(shot.vx) < 1f)
    }

    @Test
    fun aFighterPointsAlongItsCourse() {
        val right = state(ufo = U(1f, 3f, vx = 2.1f, type = 11))
        right.tick(100)
        assertEquals(1.5708f, right.ufo!!.heading, 0.05f)

        val left = state(ufo = U(8f, 3f, vx = -2.1f, type = 11))
        left.tick(100)
        assertEquals(-1.5708f, left.ufo!!.heading, 0.05f)
    }

    @Test
    fun ramEnemyAndShipAreBothDestroyed() {
        for (type in listOf(0, 16)) {
            val game = state(ufo = U(4.5f, 7f, vx = 0f, type = type))
            game.tick(16)
            assertNull(game.ufo)
            assertFalse(game.shipAlive)
            assertEquals(2, game.lives)
            assertEquals(0, game.score)
        }
    }

    // --- Sauvegarde ---

    @Test
    fun aGameSurvivesASaveAndRestore() {
        val game = AsteroidsGame(Random(5))
        game.turnRight = true
        game.thrust = true
        game.fire = true
        repeat(200) {
            game.tick(16)
            if (it == 80) game.turnRight = false
        }
        val copy = AsteroidsGame.restore(game.save(), Random(5))
        assertNotNull(copy)
        assertEquals(game.save(), copy!!.save())
        assertEquals(game.score, copy.score)
        assertEquals(game.lives, copy.lives)
        assertEquals(game.rocks.size, copy.rocks.size)
    }

    @Test
    fun anEnemySurvivesASaveAndRestore() {
        val game = state(ufo = U(2f, 3f, type = 14, hp = 1))
        val copy = AsteroidsGame.restore(game.save(), Random(1))
        assertNotNull(copy)
        assertEquals(game.save(), copy!!.save())
        assertEquals(14, copy.ufo!!.type)
        assertEquals(1, copy.ufo!!.hp)
    }

    @Test
    fun theSameSeedAndInputsGiveTheSameGame() {
        fun play(): List<Int> {
            val game = AsteroidsGame(Random(9))
            game.thrust = true
            game.fire = true
            repeat(300) {
                game.turnRight = it % 40 < 20
                game.tick(16)
            }
            return game.save()
        }
        assertEquals(play(), play())
    }

    @Test
    fun aDamagedSaveIsRefused() {
        val good = state().save()
        assertNotNull(AsteroidsGame.restore(good))
        assertNull(AsteroidsGame.restore(emptyList()))
        assertNull(AsteroidsGame.restore(good.dropLast(1)))
        assertNull(AsteroidsGame.restore(good.take(10)))
        // Un nombre qui n'en est pas un à la place de la position du vaisseau.
        assertNull(AsteroidsGame.restore(good.toMutableList().also { it[5] = Float.NaN.toBits() }))
        // Trop de vies.
        assertNull(AsteroidsGame.restore(good.toMutableList().also { it[1] = Asteroids.MAX_LIVES + 1 }))
        // Une partie finie qui garde des vies.
        assertNull(AsteroidsGame.restore(good.toMutableList().also { it[3] = 1 }))
        // Un vaisseau en vol sans vie.
        assertNull(AsteroidsGame.restore(good.toMutableList().also { it[1] = 0 }))
        // Un ennemi d'un type qui n'existe pas, sans point de vie ou avec plus que son type n'en a.
        val withEnemy = state(ufo = U(1f, 3f)).save()
        assertNotNull(AsteroidsGame.restore(withEnemy))
        val type = withEnemy.size - 4
        val hp = withEnemy.size - 1
        assertNull(AsteroidsGame.restore(withEnemy.toMutableList().also { it[type] = Asteroids.ENEMIES.size }))
        assertNull(AsteroidsGame.restore(withEnemy.toMutableList().also { it[type] = -1 }))
        assertNull(AsteroidsGame.restore(withEnemy.toMutableList().also { it[hp] = 0 }))
        assertNull(AsteroidsGame.restore(withEnemy.toMutableList().also { it[hp] = 9 }))
    }
}
