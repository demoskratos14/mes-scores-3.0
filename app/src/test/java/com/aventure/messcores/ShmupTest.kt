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

class ShmupTest {

    private fun newGame() = ShmupGame(Random(1))

    /** Un ennemi de [type] arrivé en (x, y), qui ne tirera pas de sitôt. */
    private fun enemyAt(type: EnemyType, x: Float, y: Float, hp: Int = type.hp) =
        ShmupGame.Enemy(type, spawnX = x, spawnY = y, dir = 1f, phase = 0f, hp = hp, shootInMs = 100_000L)

    /** Une partie déjà au plateau [stage], au tour [loop], avec [stageMs] ms écoulées (on retouche la sauvegarde d'une partie neuve). */
    private fun gameAt(stage: Int, stageMs: Long = 0L, loop: Int = 0): ShmupGame {
        val data = newGame().save().toMutableList()
        // Rangs dans la sauvegarde (voir ShmupGame.save) : 8 = plateau, 9 = tour, 11 = temps du plateau.
        data[8] = stage
        data[9] = loop
        data[11] = stageMs.toInt()
        return ShmupGame.restore(data, Random(1))!!
    }

    /** Monte l'arme de [levels] niveaux en ramassant des bonus. */
    private fun raiseWeapon(game: ShmupGame, levels: Int) {
        repeat(levels) {
            game.pickups += ShmupGame.Pickup(game.playerX, game.playerY, true)
            game.tick(50)
        }
    }

    /** Ramasse un bonus (ou un malus) tout de suite, sans laisser le temps passer. */
    private fun pickUp(game: ShmupGame, bonus: Boolean) {
        game.pickups += ShmupGame.Pickup(game.playerX, game.playerY, bonus)
        game.tick(10)
    }

    /** Fait arriver le boss du plateau en cours et renvoie la partie une fois le boss en jeu. */
    private fun withBoss(stage: Int, loop: Int = 0): ShmupGame {
        val game = gameAt(stage, Shmup.STAGE_MS - 100L, loop)
        game.tick(300)
        assertEquals(ShmupPhase.BOSS, game.phase)
        return game
    }

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
    fun auDepartLeVaisseauTireUnSeulMissile() {
        val game = newGame()
        game.tick(60)
        assertEquals(1, game.weapon)
        assertEquals(1, game.bullets.size)
        assertTrue(game.bullets.single().vy < 0f)
        assertFalse(game.laserOn)
    }

    @Test
    fun chaqueNiveauDArmeATonTir() {
        val missiles = mapOf(1 to 1, 2 to 2, 3 to 3, 4 to 2)
        for ((level, count) in missiles) {
            val game = newGame()
            raiseWeapon(game, level - 1)
            assertEquals(level, game.weapon)
            game.bullets.clear()
            // On avance par petits pas jusqu'à la prochaine salve : tous ses missiles partent au même instant.
            while (game.bullets.isEmpty()) game.tick(10)
            assertEquals("missiles au niveau $level", count, game.bullets.size)
            assertTrue(game.bullets.all { it.lvl == level })
            assertEquals(level >= 4, game.laserOn)
        }
    }

    @Test
    fun armeMonteJusquAuLaserPuisChaqueCycleDonneUnSatellite() {
        // (arme du vaisseau, satellites, niveau d'arme des satellites) après chaque bonus ramassé.
        val expected = listOf(
            Triple(2, 0, 1), Triple(3, 0, 1), Triple(4, 0, 1),
            Triple(1, 1, 1), Triple(2, 1, 1), Triple(3, 1, 1), Triple(4, 1, 1),
            Triple(1, 2, 1), Triple(2, 2, 1), Triple(3, 2, 1), Triple(4, 2, 1),
            Triple(1, 3, 1), Triple(2, 3, 1), Triple(3, 3, 1), Triple(4, 3, 1),
            Triple(4, 3, 2), Triple(4, 3, 3), Triple(4, 3, 4)
        )
        val game = newGame()
        assertFalse(game.satelliteLaserOn)
        for ((step, state) in expected.withIndex()) {
            pickUp(game, true)
            assertEquals("bonus ${step + 1}", state, Triple(game.weapon, game.satellites, game.satWeapon))
        }
        assertTrue(game.laserOn && game.satelliteLaserOn)
        // Tout est au maximum : un bonus de plus ne change rien mais rapporte des points.
        val before = game.score
        pickUp(game, true)
        assertEquals(Triple(4, 3, 4), Triple(game.weapon, game.satellites, game.satWeapon))
        assertEquals(before + 200, game.score)
    }

    @Test
    fun unMalusBaisseSeulementLArmeDuVaisseau() {
        val game = newGame()
        repeat(5) { pickUp(game, true) }
        assertEquals(Triple(2, 1, 1), Triple(game.weapon, game.satellites, game.satWeapon))
        pickUp(game, false)
        assertEquals(Triple(1, 1, 1), Triple(game.weapon, game.satellites, game.satWeapon))
        pickUp(game, false)
        assertEquals(Triple(1, 1, 1), Triple(game.weapon, game.satellites, game.satWeapon))
    }

    @Test
    fun unSatelliteTourneAutourDuVaisseauEtTireCommeLui() {
        val game = newGame()
        repeat(4) { pickUp(game, true) }
        assertEquals(1, game.satellites)
        val distance = hypot(game.satelliteX(0) - game.playerX, game.satelliteY(0) - game.playerY)
        assertEquals(Shmup.ORBIT_RADIUS, distance, 0.01f)
        val x0 = game.satelliteX(0)
        game.tick(300)
        assertTrue(abs(game.satelliteX(0) - x0) > 0.05f)
        // Une salve : un missile du vaisseau et un du satellite, tous deux au niveau 1.
        game.bullets.clear()
        while (game.bullets.isEmpty()) game.tick(10)
        assertEquals(2, game.bullets.size)
        assertTrue(game.bullets.all { it.lvl == 1 })
    }

    @Test
    fun lesSatellitesSontRepartisAutourDuVaisseau() {
        val game = newGame()
        repeat(12) { pickUp(game, true) }
        assertEquals(3, game.satellites)
        val angles = (0 until 3).map { kotlin.math.atan2(game.satelliteY(it) - game.playerY, game.satelliteX(it) - game.playerX) }
        assertEquals(3, angles.map { (it * 100).toInt() }.toSet().size)
    }

    @Test
    fun unSatelliteEncaisseLeCoupALaPlaceDUneVie() {
        val game = newGame()
        repeat(4) { pickUp(game, true) }
        game.enemyBullets += ShmupGame.Bullet(game.playerX, game.playerY, 0f, 0f)
        game.tick(50)
        assertEquals(0, game.satellites)
        assertEquals(Shmup.LIVES, game.lives)
        assertTrue(game.invulnMs > 0L)
        // Sans satellite, le coup suivant coûte une vie.
        game.tick(Shmup.INVULN_MS)
        game.enemyBullets += ShmupGame.Bullet(game.playerX, game.playerY, 0f, 0f)
        game.tick(50)
        assertEquals(Shmup.LIVES - 1, game.lives)
    }

    @Test
    fun unSatellitePerduSeRemplaceEtGardeLeNiveauDesAutres() {
        val game = newGame()
        repeat(18) { pickUp(game, true) }
        game.enemyBullets += ShmupGame.Bullet(game.playerX, game.playerY, 0f, 0f)
        game.tick(50)
        assertEquals(Triple(4, 2, 4), Triple(game.weapon, game.satellites, game.satWeapon))
        assertEquals(Shmup.LIVES, game.lives)
        // L'arme est au laser : le bonus suivant la remet au niveau 1 et rend le satellite, au niveau commun.
        pickUp(game, true)
        assertEquals(Triple(1, 3, 4), Triple(game.weapon, game.satellites, game.satWeapon))
    }

    @Test
    fun unePastilleTombeEtSort() {
        val game = newGame()
        game.pickups += ShmupGame.Pickup(1f, 1f, true)
        game.tick(1000)
        assertTrue(game.pickups.single().y > 1f)
        game.tick(10_000)
        assertTrue(game.pickups.isEmpty())
        assertEquals(1, game.weapon)
    }

    @Test
    fun leLaserTouchePlusieursEnnemisDansSaColonne() {
        val game = newGame()
        raiseWeapon(game, 3)
        assertTrue(game.laserOn)
        val inColumn = enemyAt(EnemyType.CORVETTE, game.playerX, 5f)
        val behind = enemyAt(EnemyType.CORVETTE, game.playerX, 2f)
        val aside = enemyAt(EnemyType.CORVETTE, game.playerX + 3f, 5f)
        game.enemies += listOf(inColumn, behind, aside)
        game.tick(200)
        assertTrue(inColumn.hp < EnemyType.CORVETTE.hp - 2)
        assertTrue(behind.hp < EnemyType.CORVETTE.hp - 2)
        assertEquals(EnemyType.CORVETTE.hp, aside.hp)
    }

    @Test
    fun unMissileDetruitUnEnnemiEtRapporteDesPoints() {
        val game = newGame()
        game.enemies += enemyAt(EnemyType.WASP, 4.5f, 8f)
        game.bullets += ShmupGame.Bullet(4.5f, 8.4f, 0f, -Shmup.MISSILE_SPEED, Shmup.MISSILE_DAMAGE, 1)
        game.tick(50)
        assertTrue(game.enemies.isEmpty())
        assertEquals(EnemyType.WASP.points, game.score)
    }

    @Test
    fun unEnnemiRobusteEncaisseSeveralMissiles() {
        val game = newGame()
        game.enemies += enemyAt(EnemyType.TANK_TOP, 4.5f, 8f)
        game.bullets += ShmupGame.Bullet(4.5f, 8.4f, 0f, -Shmup.MISSILE_SPEED, Shmup.MISSILE_DAMAGE, 1)
        game.tick(50)
        assertEquals(1, game.enemies.size)
        assertEquals(EnemyType.TANK_TOP.hp - Shmup.MISSILE_DAMAGE, game.enemies.single().hp)
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
    fun lesEnnemisArriventAvecLeurPlateauEtLeurPalier() {
        // Plateau 1 : que des vaisseaux, de plus en plus variés.
        assertTrue(Shmup.poolAt(0, 1).isNotEmpty())
        assertTrue(Shmup.poolAt(0, 1).size < Shmup.poolAt(0, 3).size)
        assertTrue(Shmup.poolAt(0, Shmup.SUBLEVELS).none { it.ground })
        // Les chars arrivent au plateau 2, les tourelles au plateau 3.
        assertTrue(EnemyType.TANK in Shmup.poolAt(1, 1))
        assertFalse(EnemyType.TANK_TOP in Shmup.poolAt(1, 1))
        assertTrue(EnemyType.TANK_TOP in Shmup.poolAt(1, 2))
        assertFalse(EnemyType.FALL_TURRET in Shmup.poolAt(1, Shmup.SUBLEVELS))
        assertTrue(EnemyType.FALL_TURRET in Shmup.poolAt(2, 1))
        // Un type déjà débloqué reste disponible, et tout est là au dernier plateau.
        assertTrue(Shmup.poolAt(0, 1).all { it in Shmup.poolAt(3, 1) })
        assertEquals(EnemyType.values().filter { !it.boss }, Shmup.poolAt(3, Shmup.SUBLEVELS))
        assertTrue(EnemyType.values().filter { !it.boss }.all { it.weight > 0 && it.hp > 0 && it.h > 0f && it.ratio > 0f })
    }

    @Test
    fun chaquePlateauAUnBossQuiNEstJamaisTire() {
        assertEquals(EnemyType.BOSS_SAUCER, Shmup.bossOf(0))
        assertEquals(EnemyType.BOSS_TANK, Shmup.bossOf(1))
        assertEquals(EnemyType.BOSS_SHUTTLE, Shmup.bossOf(2))
        assertEquals(EnemyType.BOSS_LAUNCHER, Shmup.bossOf(3))
        for (stage in 0 until Shmup.STAGES) assertTrue(Shmup.bossOf(stage).boss)
        for (stage in 0 until Shmup.STAGES) for (sub in 1..Shmup.SUBLEVELS) assertTrue(Shmup.poolAt(stage, sub).none { it.boss })
        // Les boss sont bien plus longs à détruire que n'importe quel ennemi ordinaire.
        val strongest = EnemyType.values().filter { !it.boss }.maxOf { it.hp }
        assertTrue(EnemyType.values().filter { it.boss }.all { it.hp > strongest * 5 })
    }

    @Test
    fun laDifficulteMonteAvecLePlateauEtLeTour() {
        assertTrue(Shmup.spawnEveryMs(0, 3, 0) < Shmup.spawnEveryMs(0, 1, 0))
        assertTrue(Shmup.spawnEveryMs(3, 1, 0) < Shmup.spawnEveryMs(0, 1, 0))
        assertTrue(Shmup.spawnEveryMs(0, 1, 1) < Shmup.spawnEveryMs(0, 1, 0))
        assertEquals(350L, Shmup.spawnEveryMs(3, 3, 100))
        assertTrue(Shmup.extraWaveChance(3, 1) > Shmup.extraWaveChance(0, 0))
        assertEquals(0f, Shmup.extraWaveChance(0, 0), 0f)
        assertTrue(Shmup.extraWaveChance(3, 100) <= 0.6f)
        assertEquals(1, newGame().level)
        assertEquals(Shmup.STAGES + 1, gameAt(0, loop = 1).level)
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
                Move.BOSS -> assertTrue("$type n'est pas un boss complet", type.boss && type.speed > 0f && type.lane > 0f && type.amp > 0f)
            }
            if (type.boss) {
                assertEquals(Move.BOSS, type.move)
                assertTrue("$type n'a pas de second tir", type.fire2 != Fire.NONE && type.fire2EveryMs > 0L)
            } else {
                assertTrue(type.move != Move.BOSS)
            }
        }
    }

    @Test
    fun chaqueMouvementFiniParSortirDuTerrain() {
        for (type in EnemyType.values().filter { !it.boss }) {
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
    fun unBossNeSortJamaisEtSeBalanceSurSaLigne() {
        for (type in EnemyType.values().filter { it.boss }) {
            val e = enemyAt(type, Shmup.WIDTH / 2f, -type.h / 2f)
            for (t in 0..600) {
                e.ageMs = t * 100L
                e.place()
                assertFalse("$type est parti", e.isGone())
                assertTrue(abs(e.x - Shmup.WIDTH / 2f) <= type.amp + 0.001f)
            }
            assertEquals(type.lane, e.y, 0.3f)
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
    fun leBossPasseAuSecondTirQuandIlEstALaMoitieDeSaVie() {
        val game = newGame()
        game.movePlayerTo(8f, 14f)
        val type = EnemyType.BOSS_SAUCER
        // Pleine vie : un anneau de 12 tirs ; sous la moitié : la spirale (2 tirs par salve).
        val full = ShmupGame.Enemy(type, 4.5f, 4f, 1f, 0f, type.hp, shootInMs = 0L)
        game.enemies += full
        game.tick(50)
        assertEquals(12, game.enemyBullets.size)

        val hurtGame = newGame()
        hurtGame.movePlayerTo(8f, 14f)
        hurtGame.enemies += ShmupGame.Enemy(type, 4.5f, 4f, 1f, 0f, type.hp / 2 - 1, maxHp = type.hp, shootInMs = 0L)
        hurtGame.tick(50)
        assertEquals(2, hurtGame.enemyBullets.size)
    }

    @Test
    fun desEnnemisArriventAuFilDuTemps() {
        val game = newGame()
        game.tick(1500)
        assertTrue(game.enemies.isNotEmpty())
        assertTrue(game.scroll > 0f)
    }

    @Test
    fun aLaFinDuPlateauLeBossArriveEtLeDefilementSArrete() {
        val game = withBoss(0)
        val boss = game.boss
        assertNotNull(boss)
        assertEquals(EnemyType.BOSS_SAUCER, boss!!.type)
        assertEquals(boss.type.hp, boss.maxHp)
        assertTrue(game.bossIntroMs > 0L)
        game.tick(1500)
        assertEquals(0f, game.scrollSpeed, 0.001f)
        val scroll = game.scroll
        game.tick(500)
        assertEquals(scroll, game.scroll, 0.001f)
        assertEquals(ShmupPhase.BOSS, game.phase)
    }

    @Test
    fun leBossAttendQueLesEnnemisSoientPartis() {
        val game = gameAt(0, Shmup.STAGE_MS - 50L)
        game.enemies += enemyAt(EnemyType.CORVETTE, 4.5f, 3f)
        game.tick(200)
        assertEquals(ShmupPhase.CLEARING, game.phase)
        assertNull(game.boss)
        // Au bout du délai maximal, il arrive quand même.
        game.tick(Shmup.CLEAR_WAIT_MS + 100L)
        assertEquals(ShmupPhase.BOSS, game.phase)
        assertNotNull(game.boss)
    }

    @Test
    fun vaincreLeBossMeneAuPlateauSuivant() {
        val game = withBoss(0)
        game.boss!!.hp = 0
        game.tick(50)
        assertEquals(ShmupPhase.TRANSITION, game.phase)
        assertTrue(game.enemyBullets.isEmpty())
        assertEquals(0, game.stage)
        // Le décor change au milieu du saut en vitesse lumière.
        game.tick(Shmup.TRANSITION_MS / 2 + 100L)
        assertEquals(1, game.stage)
        assertTrue(game.transitionProgress in 0.4f..0.8f)
        game.tick(Shmup.TRANSITION_MS)
        assertEquals(ShmupPhase.NORMAL, game.phase)
        assertEquals(1, game.stage)
        assertTrue(game.stageMs in 1L..3000L)
        assertEquals(0f, game.transitionProgress, 0f)
        assertTrue(game.scrollSpeed > 0f)
    }

    @Test
    fun unBossVaincuLaisseUnBonus() {
        val game = withBoss(0)
        val boss = game.boss!!
        boss.hp = 1
        game.bullets += ShmupGame.Bullet(boss.x, boss.y, 0f, 0f, Shmup.MISSILE_DAMAGE, 1)
        game.tick(50)
        assertEquals(1, game.pickups.size)
        assertTrue(game.pickups.single().bonus)
        assertEquals(EnemyType.BOSS_SAUCER.points, game.score)
    }

    @Test
    fun apresLeDernierPlateauToutRecommenceEnPlusDifficile() {
        val game = withBoss(Shmup.STAGES - 1)
        assertEquals(EnemyType.BOSS_LAUNCHER, game.boss!!.type)
        game.boss!!.hp = 0
        game.tick(50)
        game.tick(Shmup.TRANSITION_MS + 100L)
        assertEquals(0, game.stage)
        assertEquals(1, game.loop)
        assertEquals(Shmup.STAGES + 1, game.level)
        assertEquals(ShmupPhase.NORMAL, game.phase)
        // Au tour suivant le boss est plus résistant.
        val second = withBoss(0, loop = 1)
        assertTrue(second.boss!!.maxHp > EnemyType.BOSS_SAUCER.hp)
    }

    @Test
    fun uneLongueSimulationSurChaquePlateauNeBlessePasLeMoteur() {
        for (stage in 0 until Shmup.STAGES) {
            val game = gameAt(stage, 0L, loop = stage % 2)
            game.movePlayerTo(4.5f, 8f)
            var ms = 0L
            while (ms < 40_000L && !game.isOver) {
                game.tick(33)
                ms += 33
            }
            assertTrue(game.enemies.size < 100)
            assertTrue(game.enemyBullets.size < 1500)
            assertTrue(game.sparks.size <= Shmup.MAX_SPARKS)
        }
    }

    @Test
    fun reinitialiserRemetLaPartieAZero() {
        val game = gameAt(2, 10_000L, loop = 1)
        raiseWeapon(game, 5)
        game.tick(3000)
        game.reset()
        assertEquals(Shmup.LIVES, game.lives)
        assertEquals(0, game.score)
        assertEquals(0L, game.timeMs)
        assertEquals(1, game.weapon)
        assertEquals(0, game.satellites)
        assertEquals(1, game.satWeapon)
        assertEquals(0, game.stage)
        assertEquals(0, game.loop)
        assertEquals(ShmupPhase.NORMAL, game.phase)
        assertTrue(game.bullets.isEmpty() && game.enemies.isEmpty() && game.enemyBullets.isEmpty() && game.pickups.isEmpty())
    }

    @Test
    fun laSauvegardeSeRestaureIdentique() {
        val game = gameAt(2, 20_000L, loop = 1)
        game.movePlayerTo(3f, 12f)
        raiseWeapon(game, 5)
        game.pickups += ShmupGame.Pickup(1f, 1f, false)
        game.tick(30_000)
        val saved = game.save()
        val restored = ShmupGame.restore(saved, Random(2))!!
        assertEquals(saved, restored.save())
        assertEquals(game.playerX, restored.playerX, 0f)
        assertEquals(game.score, restored.score)
        assertEquals(game.weapon, restored.weapon)
        assertEquals(game.satellites, restored.satellites)
        assertEquals(game.satWeapon, restored.satWeapon)
        assertEquals(game.stage, restored.stage)
        assertEquals(game.phase, restored.phase)
        assertEquals(game.enemies.size, restored.enemies.size)
        assertEquals(game.pickups.size, restored.pickups.size)
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
        // Une arme, un plateau ou une phase hors limites sont refusés.
        for ((index, value) in listOf(7 to 9, 8 to 9, 9 to -1, 10 to 99, 20 to 9, 21 to 0)) {
            val broken = saved.toMutableList()
            broken[index] = value
            assertNull("rang $index", ShmupGame.restore(broken))
        }
        // Un type d'ennemi inconnu est refusé.
        val game = newGame()
        game.enemies += enemyAt(EnemyType.WASP, 4f, 1f)
        val withEnemy = game.save().toMutableList()
        withEnemy[withEnemy.size - 11] = 999
        assertNull(ShmupGame.restore(withEnemy))
    }
}
