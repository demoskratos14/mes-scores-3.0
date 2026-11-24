package com.aventure.messcores

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunnerGameTest {

    /** Une partie dont la voie est vidée : on y pose soi-même ce qu'on veut tester, loin devant. */
    private fun emptyGame(): RunnerGame {
        val game = RunnerGame(seed = 1L)
        game.things.clear()
        return game
    }

    private fun obstacle(kind: RunnerKind, lane: Int, z: Float): RunnerThing {
        val prop = when (kind) {
            RunnerKind.LOW -> Runner.LOW[0]
            RunnerKind.TALL -> Runner.TALL[0]
            else -> Runner.BAR
        }
        return RunnerThing(kind, lane, z, prop.model, prop.scale, prop.yaw, prop.halfDepth, 0f, prop.top)
    }

    private fun pickup(kind: RunnerKind, lane: Int, z: Float, y: Float = Runner.COIN_GROUND_Y): RunnerThing =
        RunnerThing(kind, lane, z, "item", 1f, 0f, 0.4f, y, 0f)

    /** Avance jusqu'à ce que le coureur ait dépassé la distance [z] (ou que la partie finisse). [onFrame] agit avant chaque image. */
    private fun runPast(game: RunnerGame, z: Float, onFrame: (RunnerGame) -> Unit = {}) {
        var guard = 0
        while (!game.isOver && game.dist < z && guard++ < 100_000) {
            onFrame(game)
            game.tick(16)
        }
    }

    // --- La voie générée ---

    @Test
    fun laMemeGraineDonneLaMemeVoie() {
        val a = RunnerGame(seed = 42L)
        val b = RunnerGame(seed = 42L)
        assertEquals(a.things.size, b.things.size)
        for (i in a.things.indices) {
            assertEquals(a.things[i].kind, b.things[i].kind)
            assertEquals(a.things[i].lane, b.things[i].lane)
            assertEquals(a.things[i].z, b.things[i].z, 0f)
        }
    }

    @Test
    fun lePremierObstacleEstAssezLoinDuDepart() {
        for (seed in 1L..20L) {
            val game = RunnerGame(seed)
            val first = game.things.filter { it.kind.ordinal >= RunnerKind.LOW.ordinal }.minOf { it.z }
            assertTrue(first >= Runner.FREE_START_M)
        }
    }

    @Test
    fun uneRangeeNeFermeJamaisLesTroisVoiesParDesWagons() {
        for (seed in 1L..30L) {
            val game = RunnerGame(seed)
            val bot = Bot(game)
            var guard = 0
            while (!game.isOver && game.dist < 1500f && guard++ < 200_000) {
                bot.act()
                game.tick(16)
                val rows = game.things.filter { it.kind.ordinal >= RunnerKind.LOW.ordinal }.groupBy { Math.round(it.z * 10f) }
                for (row in rows.values) {
                    assertTrue("trois wagons côte à côte (graine $seed)", row.count { it.kind == RunnerKind.TALL } < 3)
                    // Une barre barre toujours les trois voies, pour qu'on sache qu'il faut se baisser.
                    if (row.any { it.kind == RunnerKind.BAR }) assertEquals(3, row.size)
                }
            }
        }
    }

    @Test
    fun deuxRangeesSontToujoursAssezEspaceesPourChangerDeVoie() {
        for (seed in 1L..10L) {
            val game = RunnerGame(seed)
            val zs = game.things.filter { it.kind.ordinal >= RunnerKind.LOW.ordinal }.map { it.z }.distinct().sorted()
            for (i in 1 until zs.size) assertTrue(zs[i] - zs[i - 1] >= Runner.MIN_GAP_M)
        }
    }

    @Test
    fun unJoueurAttentifTraverseTroisKilometresSansMourir() {
        for (seed in 1L..25L) {
            val game = RunnerGame(seed)
            val bot = Bot(game)
            var guard = 0
            while (!game.isOver && game.dist < 3000f && guard++ < 400_000) {
                bot.act()
                game.tick(16)
            }
            assertFalse("le robot a perdu à ${game.dist} m (graine $seed)", game.isOver)
        }
    }

    // --- Les mouvements ---

    @Test
    fun onChangeDeVoieSansSortirDesTrois() {
        val game = emptyGame()
        game.moveLeft(); game.moveLeft(); game.moveLeft()
        assertEquals(-1, game.lane)
        game.moveRight(); game.moveRight(); game.moveRight()
        assertEquals(1, game.lane)
    }

    @Test
    fun laPositionRejointLaVoieVisee() {
        val game = emptyGame()
        game.moveRight()
        runPast(game, 3f)
        assertEquals(Runner.LANE_W, game.x, 1e-4f)
    }

    @Test
    fun unSautMontePuisRetombeEtOnNePeutPasSauterEnLAir() {
        val game = emptyGame()
        game.jump()
        game.tick(100)
        assertTrue(game.isAirborne)
        val height = game.y
        game.jump() // sans effet en l'air
        game.tick(16)
        assertTrue(game.y > 0f && game.y != height)
        runPast(game, game.dist + 20f)
        assertEquals(0f, game.y, 0f)
        assertFalse(game.isAirborne)
    }

    @Test
    fun seBaisserDureUnTempsPuisLeCoureurSeReleve() {
        val game = emptyGame()
        game.slide()
        assertTrue(game.isSliding)
        game.tick(100); game.tick(100); game.tick(100); game.tick(100); game.tick(100); game.tick(100); game.tick(100); game.tick(100)
        assertFalse(game.isSliding)
    }

    @Test
    fun seBaisserEnLAirFaitRetomberPuisGlisser() {
        val game = emptyGame()
        game.jump()
        game.tick(100)
        game.slide()
        runPast(game, game.dist + 15f)
        assertEquals(0f, game.y, 0f)
    }

    @Test
    fun laVitesseAugmenteJusquASonMaximum() {
        assertEquals(Runner.START_SPEED, Runner.speedAt(0f), 0f)
        assertTrue(Runner.speedAt(300f) > Runner.speedAt(100f))
        assertEquals(Runner.MAX_SPEED, Runner.speedAt(100_000f), 0f)
    }

    // --- Les obstacles ---

    @Test
    fun unObstacleBasSeSaute() {
        val standing = emptyGame().apply { things.add(obstacle(RunnerKind.LOW, 0, 30f)) }
        runPast(standing, 40f)
        assertTrue(standing.isOver)

        val jumping = emptyGame().apply { things.add(obstacle(RunnerKind.LOW, 0, 30f)) }
        runPast(jumping, 40f) { g -> if (30f - g.dist < g.speed * 0.30f && !g.isAirborne) g.jump() }
        assertFalse(jumping.isOver)
    }

    @Test
    fun unWagonNeSePassePasEnSautant() {
        val game = emptyGame().apply { things.add(obstacle(RunnerKind.TALL, 0, 30f)) }
        runPast(game, 40f) { g -> if (30f - g.dist < g.speed * 0.30f && !g.isAirborne) g.jump() }
        assertTrue(game.isOver)
    }

    @Test
    fun unWagonSeContourneEnChangeantDeVoie() {
        val game = emptyGame().apply { things.add(obstacle(RunnerKind.TALL, 0, 30f)) }
        game.moveRight()
        runPast(game, 40f)
        assertFalse(game.isOver)
    }

    @Test
    fun uneBarreSePasseEnSeBaissant() {
        val standing = emptyGame().apply { things.add(obstacle(RunnerKind.BAR, 0, 30f)) }
        runPast(standing, 40f)
        assertTrue(standing.isOver)

        val ducking = emptyGame().apply { things.add(obstacle(RunnerKind.BAR, 0, 30f)) }
        runPast(ducking, 40f) { g -> if (30f - g.dist < g.speed * 0.22f && !g.isSliding) g.slide() }
        assertFalse(ducking.isOver)
    }

    @Test
    fun unObstacleDUneAutreVoieNeGenePas() {
        val game = emptyGame().apply { things.add(obstacle(RunnerKind.TALL, 1, 30f)) }
        runPast(game, 40f)
        assertFalse(game.isOver)
    }

    // --- Vies, bouclier ---

    @Test
    fun uneVieDeReserveAbsorbeUnChocPuisLeCoureurEstInvulnerable() {
        val game = emptyGame().apply {
            things.add(pickup(RunnerKind.HEART, 0, 10f))
            things.add(obstacle(RunnerKind.TALL, 0, 30f))
            things.add(obstacle(RunnerKind.TALL, 0, 34f))
        }
        runPast(game, 45f)
        assertFalse(game.isOver)
        assertEquals(1, game.lives) // 2 vies, une seule perdue : le deuxième obstacle, juste après, ne coûte rien
        assertEquals(1, game.hits)
    }

    @Test
    fun leBouclierAbsorbeUnChocEtDisparait() {
        val game = emptyGame().apply {
            things.add(pickup(RunnerKind.SHIELD, 0, 10f))
            things.add(obstacle(RunnerKind.TALL, 0, 30f))
        }
        runPast(game, 40f)
        assertFalse(game.isOver)
        assertFalse(game.shield)
        assertEquals(1, game.lives)
    }

    @Test
    fun leNombreDeViesEstPlafonne() {
        val game = emptyGame()
        for (i in 0 until 6) game.things.add(pickup(RunnerKind.HEART, 0, 10f + i * 2f))
        runPast(game, 40f)
        assertEquals(Runner.MAX_LIVES, game.lives)
    }

    // --- Pièces et bonus ---

    @Test
    fun unePieceRamasseeRapporteDesPoints() {
        val coin = pickup(RunnerKind.COIN, 0, 20f)
        val game = emptyGame().apply { things.add(coin) }
        runPast(game, 30f)
        assertEquals(1, game.coins)
        assertTrue(coin.taken)
        // Les points de la distance parcourue (au moins 30 m) et ceux de la pièce.
        assertTrue(game.score >= 30 + Runner.COIN_POINTS)
    }

    @Test
    fun unePieceDUneAutreVoieEstManqueeSaufAvecLAimant() {
        val plain = emptyGame().apply { things.add(pickup(RunnerKind.COIN, 1, 20f)) }
        runPast(plain, 30f)
        assertEquals(0, plain.coins)

        val magnet = emptyGame().apply {
            things.add(pickup(RunnerKind.MAGNET, 0, 10f))
            things.add(pickup(RunnerKind.COIN, 1, 20f))
        }
        runPast(magnet, 30f)
        assertEquals(1, magnet.coins)
    }

    @Test
    fun unePieceEnLAirDemandeUnSaut() {
        val ground = emptyGame().apply { things.add(pickup(RunnerKind.COIN, 0, 20f, Runner.COIN_AIR_Y)) }
        runPast(ground, 30f)
        assertEquals(0, ground.coins)

        val jumping = emptyGame().apply { things.add(pickup(RunnerKind.COIN, 0, 20f, Runner.COIN_AIR_Y)) }
        runPast(jumping, 30f) { g -> if (20f - g.dist < g.speed * 0.30f && !g.isAirborne) g.jump() }
        assertEquals(1, jumping.coins)
    }

    @Test
    fun unDiamantVautCinqPieces() {
        val game = emptyGame().apply { things.add(pickup(RunnerKind.GEM, 0, 20f)) }
        runPast(game, 30f)
        assertEquals(1, game.gems)
        assertEquals(Runner.GEM_COINS, game.coins)
    }

    @Test
    fun lEtoileDoubleLesPointsDeLaDistance() {
        val plain = emptyGame()
        runPast(plain, 100f)
        val star = emptyGame().apply { things.add(pickup(RunnerKind.STAR, 0, 8f)) }
        runPast(star, 100f)
        assertTrue(star.score > plain.score + 40)
    }

    @Test
    fun uneFoisFinieLaPartieNeBougePlus() {
        val game = emptyGame().apply { things.add(obstacle(RunnerKind.TALL, 0, 30f)) }
        runPast(game, 40f)
        assertTrue(game.isOver)
        val dist = game.dist
        game.tick(100)
        game.jump()
        assertEquals(dist, game.dist, 0f)
    }

    // --- La caméra ---

    @Test
    fun lesPiedsDuCoureurSontDansLaVueEtLHorizonAuDessus() {
        val cam = RunnerView.camera(0f)
        val foot = RunnerView.project(cam, 0f, 0f, 0f, 540f, 1170f)
        assertNotNull(foot)
        assertEquals(270f, foot!![0], 1f)
        assertTrue(foot[1] > 600f && foot[1] < 1170f)
        val horizon = RunnerView.horizon(cam) * 1170f
        assertTrue(horizon > 0f && horizon < foot[1])
    }

    @Test
    fun unPointDerriereLaCameraNEstPasProjete() {
        assertEquals(null, RunnerView.project(RunnerView.camera(0f), 0f, 0f, 20f, 540f, 1170f))
    }

    // --- Les fichiers du jeu ---

    @Test
    fun tousLesModelesSontLaEtPorteDesCouleursParSommet() {
        for (name in Runner.allModels()) {
            val file = File("src/main/assets/runner/$name.glb")
            assertTrue("modèle absent : $name", file.exists())
            val mesh = GlbParser.parse(file.readBytes())
            assertTrue("$name est vide", mesh.triangleCount > 0)
            assertNotNull("$name n'a pas de couleurs", mesh.colors)
        }
    }

    @Test
    fun lesImagesDuCoureurSontToutesLa() {
        val counts = mapOf("run" to 12, "hurt" to 10, "die" to 10)
        for ((set, count) in counts) {
            for (i in 0 until count) {
                val n = if (i < 10) "0$i" else "$i"
                assertTrue("image absente : $set $n", File("src/main/assets/runner/assassin_${set}_$n.png").exists())
            }
        }
    }

    /** Un joueur attentif : évite les wagons, saute les obstacles bas, se baisse sous les barres. */
    private class Bot(private val g: RunnerGame) {
        fun act() {
            val ahead = g.things.filter { it.z + 2.2f > g.dist && it.z < g.dist + 40f && it.kind.ordinal >= RunnerKind.LOW.ordinal && !it.taken }
            if (ahead.isEmpty()) return
            val z0 = ahead.minOf { it.z }
            val row = ahead.filter { it.z - z0 < 0.5f }
            val dz = z0 - g.dist
            val tall = row.filter { it.kind == RunnerKind.TALL }.map { it.lane }.toSet()
            val any = row.map { it.lane }.toSet()
            val byDistance = listOf(0, -1, 1).sortedBy { Math.abs(it - g.lane) }
            val want = byDistance.firstOrNull { it !in any } ?: byDistance.first { it !in tall }
            if (dz < 30f) {
                if (want < g.lane) g.moveLeft() else if (want > g.lane) g.moveRight()
            }
            if (g.lane == want || Math.abs(g.x - want * Runner.LANE_W) < 0.3f) {
                val mine = row.firstOrNull { it.lane == want } ?: return
                if (mine.kind == RunnerKind.LOW && dz < g.speed * 0.30f && !g.isAirborne) g.jump()
                if (mine.kind == RunnerKind.BAR && dz < g.speed * 0.22f && !g.isSliding) g.slide()
            }
        }
    }
}
