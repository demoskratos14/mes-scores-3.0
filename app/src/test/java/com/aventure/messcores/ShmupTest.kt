package com.aventure.messcores

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShmupTest {

    private fun newGame() = ShmupGame(Random(1))

    /** Un ennemi de [type] arrivé en (x, y), qui ne tirera pas de sitôt. */
    private fun enemyAt(type: EnemyType, x: Float, y: Float, hp: Int = type.hp) =
        ShmupGame.Enemy(type, spawnX = x, spawnY = y, dir = 1f, phase = 0f, hp = hp, shootInMs = 100_000L)

    @Test
    fun leVaisseauResteDansLeTerrain() {
        val game = newGame()
        game.movePlayerTo(-5f, -5f)
        assertEquals(Shmup.SHIP_W / 2f, game.playerX, 0.0001f)
        assertEquals(Shmup.SHIP_H / 2f, game.playerY, 0.0001f)
        game.movePlayerTo(50f, 50f)
        assertEquals(Shmup.WIDTH - Shmup.SHIP_W / 2f, game.playerX, 0.0001f)
        assertEquals(Shmup.HEIGHT - Shmup.SHIP_H / 2f, game.playerY, 0.0001f)
    }

    @Test
    fun leVaisseauTireToutSeulDeuxTirsALaFois() {
        val game = newGame()
        game.tick(60)
        assertTrue(game.bullets.size >= 2)
        assertEquals(0, game.bullets.size % 2)
        assertTrue(game.bullets.all { it.vy < 0f })
    }

    @Test
    fun unTirDetruitUnEnnemiEtRapporteDesPoints() {
        val game = newGame()
        game.enemies += enemyAt(EnemyType.WASP, 4.5f, 8f)
        game.bullets += ShmupGame.Bullet(4.5f, 8.4f, 0f, -Shmup.BULLET_SPEED)
        game.tick(50)
        assertTrue(game.enemies.isEmpty())
        assertEquals(EnemyType.WASP.points, game.score)
    }

    @Test
    fun unEnnemiRobusteEncaisseSeveralTirs() {
        val game = newGame()
        game.enemies += enemyAt(EnemyType.K24, 4.5f, 8f)
        game.bullets += ShmupGame.Bullet(4.5f, 8.4f, 0f, -Shmup.BULLET_SPEED)
        game.tick(50)
        assertEquals(1, game.enemies.size)
        assertEquals(EnemyType.K24.hp - 1, game.enemies.single().hp)
        assertEquals(0, game.score)
    }

    @Test
    fun toucherUnEnnemiCouteUneVieEtProtegeUnMoment() {
        val game = newGame()
        game.enemies += enemyAt(EnemyType.WASP, game.playerX, game.playerY)
        game.tick(50)
        assertEquals(Shmup.LIVES - 1, game.lives)
        assertTrue(game.invulnMs > 0L)
        // Protégé : un second ennemi au même endroit ne coûte rien.
        game.enemies += enemyAt(EnemyType.WASP, game.playerX, game.playerY)
        game.tick(50)
        assertEquals(Shmup.LIVES - 1, game.lives)
    }

    @Test
    fun uneTourelleAuSolNeBlessePasAuContact() {
        val game = newGame()
        game.enemies += enemyAt(EnemyType.FALL_TURRET, game.playerX, game.playerY)
        game.tick(50)
        assertEquals(Shmup.LIVES, game.lives)
    }

    @Test
    fun unGrosEnnemiSurvitAuContact() {
        val game = newGame()
        game.enemies += enemyAt(EnemyType.CORVETTE, game.playerX, game.playerY - 1f)
        game.tick(50)
        assertEquals(Shmup.LIVES - 1, game.lives)
        // Il encaisse le choc (trois tirs d'un coup), plus d'éventuels tirs du joueur : il reste en jeu.
        assertEquals(1, game.enemies.size)
        assertTrue(game.enemies.single().hp <= EnemyType.CORVETTE.hp - 3)
    }

    @Test
    fun unTirEnnemiToucheLeVaisseau() {
        val game = newGame()
        game.enemyBullets += ShmupGame.Bullet(game.playerX, game.playerY, 0f, 0f)
        game.tick(50)
        assertEquals(Shmup.LIVES - 1, game.lives)
        assertTrue(game.enemyBullets.isEmpty())
    }

    @Test
    fun laPartieFinitQuandLesTroisViesSontPerdues() {
        val game = newGame()
        var hits = 0
        while (!game.isOver && hits < 10) {
            game.enemies += enemyAt(EnemyType.WASP, game.playerX, game.playerY)
            game.tick(50)
            game.tick(Shmup.INVULN_MS)
            hits++
        }
        assertTrue(game.isOver)
        assertEquals(0, game.lives)
        assertTrue(hits <= Shmup.LIVES)
    }

    @Test
    fun leNiveauEtLaDifficulteMontentAvecLeTemps() {
        assertEquals(1, Shmup.levelAt(0L))
        assertEquals(2, Shmup.levelAt(Shmup.LEVEL_EVERY_MS))
        assertTrue(Shmup.spawnEveryMs(5) < Shmup.spawnEveryMs(1))
        assertEquals(600L, Shmup.spawnEveryMs(100))
    }

    @Test
    fun lesNouveauxEnnemisArriventAvecLesNiveaux() {
        assertTrue(Shmup.poolAt(1).isNotEmpty())
        assertTrue(Shmup.poolAt(1).size < Shmup.poolAt(4).size)
        assertEquals(EnemyType.values().toList(), Shmup.poolAt(100))
        assertTrue(EnemyType.values().all { it.weight > 0 && it.hp > 0 && it.h > 0f && it.ratio > 0f })
    }

    @Test
    fun chaqueEnnemiADesReglagesCoherents() {
        for (type in EnemyType.values()) {
            if (type.fire != Fire.NONE) assertTrue("$type tire sans cadence", type.fireEveryMs > 0L && type.bulletSpeed > 0f)
            when (type.move) {
                Move.STRAIGHT, Move.SINE, Move.ZIGZAG, Move.DIVE, Move.DIAGONAL, Move.CIRCLE ->
                    assertTrue("$type n'avance pas", type.speed > 0f)
                Move.STRAFE, Move.HOVER -> assertTrue("$type n'a pas de vitesse", type.speed > 0f)
                Move.SWOOP -> assertTrue("$type n'a pas d'amplitude", type.amp > 0f)
                Move.GROUND, Move.ROLL -> assertTrue(type.ground)
            }
        }
    }

    @Test
    fun chaqueMouvementFiniParSortirDuTerrain() {
        for (type in EnemyType.values()) {
            val x = if (type.move == Move.DIAGONAL || type.move == Move.STRAFE || type.move == Move.SWOOP) 0.8f else 4.5f
            val e = enemyAt(type, x, -type.h / 2f)
            var ms = 0L
            while (!e.isGone() && ms < 60_000L) {
                ms += 100L
                e.ageMs = ms
                if (type.move == Move.DIVE && e.ageMs >= ShmupGame.Enemy.DIVE_LOCK_MS && e.aux < 0f) e.aux = 4.5f
                e.place()
                assertTrue("$type: position invalide", e.x.isFinite() && e.y.isFinite())
            }
            assertTrue("$type ne sort jamais du terrain", e.isGone())
        }
    }

    @Test
    fun lesMouvementsSuiventLeurTrajectoire() {
        // Droit : il descend à vitesse constante sans changer de colonne.
        val straight = enemyAt(EnemyType.K24, 3f, 0f).also { it.ageMs = 1000L; it.place() }
        assertEquals(3f, straight.x, 0.0001f)
        assertEquals(EnemyType.K24.speed, straight.y, 0.0001f)

        // Sol : il défile exactement avec le fond.
        val ground = enemyAt(EnemyType.FALL_TURRET, 3f, 0f).also { it.ageMs = 2000L; it.place() }
        assertEquals(Shmup.SCROLL_SPEED * 2f, ground.y, 0.0001f)

        // Traversée : une fois sur sa ligne, il ne descend plus et glisse de côté.
        val strafe = enemyAt(EnemyType.K23, 0.8f, -1f).also { it.ageMs = 5000L; it.place() }
        assertEquals(EnemyType.K23.lane, strafe.y, 0.0001f)
        assertTrue(strafe.x > 0.8f)

        // Plongeon : il vise la position retenue du joueur.
        val dive = enemyAt(EnemyType.L9G, 1f, -1f)
        dive.aux = 7f
        dive.ageMs = 4000L
        dive.place()
        assertEquals(7f, dive.x, 0.0001f)

        // Arrêt : il tient sa ligne le temps prévu, puis repart vers le haut.
        val hover = enemyAt(EnemyType.D9, 4.5f, -1f)
        hover.ageMs = 4000L
        hover.place()
        assertEquals(EnemyType.D9.lane, hover.y, 0.2f)
        hover.ageMs = 20_000L
        hover.place()
        assertTrue(hover.y < EnemyType.D9.lane)

        // Zigzag : il reste dans son amplitude.
        val zig = enemyAt(EnemyType.D14, 4.5f, 0f)
        for (t in 0..100) {
            zig.ageMs = t * 100L
            zig.place()
            assertTrue(abs(zig.x - 4.5f) <= EnemyType.D14.amp + 0.001f)
        }
    }

    @Test
    fun chaqueTirALeBonNombreDeProjectiles() {
        val expected = mapOf(
            Fire.STREAM to 1, Fire.TWIN to 2, Fire.AIMED to 1, Fire.TRIPLE to 3,
            Fire.SPREAD5 to 5, Fire.RADIAL8 to 8, Fire.RADIAL12 to 12, Fire.SPIRAL to 2
        )
        for ((fire, count) in expected) {
            val type = EnemyType.values().first { it.fire == fire }
            val game = newGame()
            game.movePlayerTo(8f, 14f)
            val e = ShmupGame.Enemy(type, 2f, 4f, 1f, 0f, type.hp, shootInMs = 0L)
            game.enemies += e
            game.tick(50)
            assertEquals("tir $fire", count, game.enemyBullets.size)
        }
    }

    @Test
    fun unTirVisePointeVersLeJoueur() {
        val game = newGame()
        game.movePlayerTo(8f, 14f)
        val type = EnemyType.values().first { it.fire == Fire.AIMED }
        game.enemies += ShmupGame.Enemy(type, 2f, 4f, 1f, 0f, type.hp, shootInMs = 0L)
        game.tick(50)
        val b = game.enemyBullets.single()
        assertTrue(b.vx > 0f && b.vy > 0f)
    }

    @Test
    fun desEnnemisArriventAuFilDuTemps() {
        val game = newGame()
        game.tick(1500)
        assertTrue(game.enemies.isNotEmpty())
    }

    @Test
    fun uneLongueSimulationNeBlessePasLeMoteur() {
        val game = newGame()
        game.movePlayerTo(4.5f, 8f)
        var ms = 0L
        while (ms < 120_000L && !game.isOver) {
            game.tick(33)
            ms += 33
        }
        assertTrue(game.enemies.size < 60)
        assertTrue(game.enemyBullets.size < 600)
        assertTrue(game.sparks.size <= Shmup.MAX_SPARKS)
    }

    @Test
    fun reinitialiserRemetLaPartieAZero() {
        val game = newGame()
        game.tick(3000)
        game.reset()
        assertEquals(Shmup.LIVES, game.lives)
        assertEquals(0, game.score)
        assertEquals(0L, game.timeMs)
        assertTrue(game.bullets.isEmpty() && game.enemies.isEmpty() && game.enemyBullets.isEmpty())
    }

    @Test
    fun laSauvegardeSeRestaureIdentique() {
        val game = newGame()
        game.movePlayerTo(3f, 12f)
        game.tick(30_000)
        val saved = game.save()
        val restored = ShmupGame.restore(saved, Random(2))!!
        assertEquals(saved, restored.save())
        assertEquals(game.playerX, restored.playerX, 0f)
        assertEquals(game.score, restored.score)
        assertEquals(game.enemies.size, restored.enemies.size)
        for ((a, b) in game.enemies.zip(restored.enemies)) {
            assertEquals(a.type, b.type)
            assertEquals(a.x, b.x, 0.0001f)
            assertEquals(a.y, b.y, 0.0001f)
        }
    }

    @Test
    fun uneSauvegardeAbimeeEstRefusee() {
        assertNull(ShmupGame.restore(emptyList()))
        assertNull(ShmupGame.restore(listOf(1, 2, 3)))
        val saved = newGame().save()
        assertNull(ShmupGame.restore(saved + 0))
        assertNull(ShmupGame.restore(saved.dropLast(1)))
        assertNull(ShmupGame.restore(listOf(99) + saved.drop(1)))
        // Un type d'ennemi inconnu est refusé.
        val game = newGame()
        game.enemies += enemyAt(EnemyType.WASP, 4f, 1f)
        val withEnemy = game.save().toMutableList()
        withEnemy[withEnemy.size - 10] = 999
        assertNull(ShmupGame.restore(withEnemy))
    }
}
