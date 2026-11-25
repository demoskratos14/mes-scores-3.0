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
        val game = RunnerGame(seed = 1L, startLives = 1)
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

    @Test
    fun laVitesseEtLaDifficulteMontentToutLeLongDeLaPartie() {
        var last = 0f
        for (d in 0..3000 step 100) {
            assertTrue(Runner.speedAt(d.toFloat()) >= last)
            last = Runner.speedAt(d.toFloat())
        }
        assertTrue(Runner.speedAt(1000f) > Runner.speedAt(0f) * 1.5f)
        assertEquals(0f, Runner.hardAt(0f), 0f)
        assertEquals(1f, Runner.hardAt(100_000f), 0f)
        // Les rangées se resserrent : en temps de réaction, et donc plus encore en mètres au début qu'à la fin ramené à la vitesse.
        fun meanGapSeconds(from: Float, to: Float): Float {
            val gaps = ArrayList<Float>()
            for (seed in 1L..20L) {
                val zs = ArrayList<Float>()
                val g = RunnerGame(seed)
                // On lit les rangées générées loin devant en faisant avancer la partie (le bot s'en occupe).
                val bot = Bot(g)
                var guard = 0
                while (!g.isOver && g.dist < to && guard++ < 400_000) {
                    bot.act()
                    g.tick(16)
                    for (t in g.things) if (t.kind.ordinal >= RunnerKind.LOW.ordinal && t.z in from..to && t.z !in zs) zs.add(t.z)
                }
                val rows = zs.sorted().fold(ArrayList<Float>()) { acc, z -> if (acc.isEmpty() || z - acc.last() > 1f) acc.add(z); acc }
                for (i in 1 until rows.size) gaps.add((rows[i] - rows[i - 1]) / Runner.speedAt(rows[i]))
            }
            return gaps.average().toFloat()
        }
        assertTrue(meanGapSeconds(1800f, 2600f) < meanGapSeconds(60f, 700f))
    }

    @Test
    fun ilNYAQueTroisSortesDObstacles() {
        val models = HashSet<String>()
        val kinds = HashSet<RunnerKind>()
        for (seed in 1L..30L) {
            val game = RunnerGame(seed)
            val bot = Bot(game)
            var guard = 0
            while (!game.isOver && game.dist < 2000f && guard++ < 200_000) {
                bot.act()
                game.tick(16)
                for (t in game.things) if (t.kind.ordinal >= RunnerKind.LOW.ordinal) {
                    models.add(t.model)
                    kinds.add(t.kind)
                }
            }
        }
        assertEquals(setOf("rail_coal_pile", "rail_carriage", "rail_gate"), models)
        assertEquals(setOf(RunnerKind.LOW, RunnerKind.TALL, RunnerKind.BAR), kinds)
    }

    // --- Les virages ---

    @Test
    fun leTraceSeRaccordeEnChaqueVirage() {
        val game = RunnerGame(5L)
        val path = game.path
        assertTrue(path.size > 2)
        val a = DoubleArray(2)
        val b = DoubleArray(2)
        for (i in 1 until path.size) {
            path.position(i - 1, path.start(i), 0f, a)
            path.position(i, path.start(i), 0f, b)
            assertEquals(a[0], b[0], 1e-6)
            assertEquals(a[1], b[1], 1e-6)
            assertEquals(Math.floorMod(path.quarter(i - 1) + path.turn(i), 4), Math.floorMod(path.quarter(i), 4))
            assertTrue(path.turn(i) == 1 || path.turn(i) == -1)
        }
        assertEquals(148.8f, path.start(1), 0.01f)
    }

    @Test
    fun lesVirageSontAssezEcartesEtRienNEstPoseAutour() {
        for (seed in 1L..20L) {
            val game = RunnerGame(seed)
            val bot = Bot(game)
            var guard = 0
            while (!game.isOver && game.dist < 2500f && guard++ < 400_000) {
                bot.act()
                game.tick(16)
            }
            val path = game.path
            for (i in 2 until path.size) assertTrue(path.start(i) - path.start(i - 1) >= 55f * Runner.UNIT * 0.6f)
            for (i in 1 until path.size) {
                for (t in game.things) {
                    assertFalse(
                        "objet dans la zone d'un virage (graine $seed)",
                        t.z > path.start(i) - Runner.CORNER_CLEAR_BEFORE + 0.01f && t.z < path.start(i) + Runner.CORNER_CLEAR_AFTER - 0.01f
                    )
                }
            }
        }
    }

    private fun gameAtFirstCorner(): RunnerGame {
        val game = RunnerGame(seed = 2L, startLives = 1)
        game.things.clear()
        return game
    }

    @Test
    fun unGesteDansLeSensDuVirageLePrendEtLaVueTourne() {
        val game = gameAtFirstCorner()
        val corner = game.nextCornerZ!!
        val dir = game.nextCornerDir
        runPast(game, corner - 5f)
        assertTrue(game.turnIsOpen)
        if (dir < 0) game.moveLeft() else game.moveRight()
        runPast(game, corner + 0.5f)
        assertFalse(game.isOver)
        assertEquals(1, game.turnsTaken)
        assertEquals(1, game.seg)
        assertTrue(Math.abs(game.turnVisual) > 1f)
        runPast(game, corner + 40f)
        assertEquals(0f, game.turnVisual, 0f)
    }

    @Test
    fun sansGesteOnSEcraseContreLeMur() {
        val game = gameAtFirstCorner()
        runPast(game, game.nextCornerZ!! + 2f)
        assertTrue(game.isOver)
    }

    @Test
    fun unGesteDansLeMauvaisSensNeTournePas() {
        val game = gameAtFirstCorner()
        val corner = game.nextCornerZ!!
        val wrong = -game.nextCornerDir
        runPast(game, corner - 5f)
        if (wrong < 0) game.moveLeft() else game.moveRight()
        runPast(game, corner + 2f)
        assertTrue(game.isOver)
    }

    @Test
    fun unGesteTropTotChangeJusteDeVoie() {
        val game = gameAtFirstCorner()
        val dir = game.nextCornerDir
        runPast(game, 20f)
        assertFalse(game.turnIsOpen)
        if (dir < 0) game.moveLeft() else game.moveRight()
        assertEquals(dir, game.lane)
        runPast(game, game.nextCornerZ!! + 2f)
        assertTrue(game.isOver)
    }

    @Test
    fun avecUneVieDeReserveOnRepartDansLeBonSensApresUnVirageRate() {
        val game = gameAtFirstCorner()
        game.things.add(pickup(RunnerKind.SHIELD, 0, 8f))
        runPast(game, 12f)
        assertTrue(game.shield)
        runPast(game, game.nextCornerZ!! + 2f)
        assertFalse(game.isOver)
        assertEquals(1, game.seg)
        assertFalse(game.shield)
    }

    // --- L'éclair, l'aimant et les améliorations ---

    @Test
    fun lEclairRendInvincibleEtDefonceLesObstacles() {
        val game = emptyGame()
        game.things.add(pickup(RunnerKind.BOOST, 0, 5f))
        val wagon = obstacle(RunnerKind.TALL, 0, 40f)
        val gate = obstacle(RunnerKind.BAR, 0, 60f)
        game.things.add(wagon)
        game.things.add(gate)
        var fastest = 0f
        runPast(game, 80f) { g -> fastest = maxOf(fastest, g.speed) }
        assertFalse(game.isOver)
        assertTrue(wagon.smashed && gate.smashed)
        assertEquals(0, game.hits)
        assertTrue("super-vitesse : ${fastest}", fastest > Runner.speedAt(game.dist) + Runner.BOOST_SPEED * 0.8f)
    }

    @Test
    fun lEclairPrendLesVirageTouteSeul() {
        val game = RunnerGame(2L, RunnerLevels(boost = RunnerShop.MAX_LEVEL))
        game.things.clear()
        game.things.add(pickup(RunnerKind.BOOST, 0, 20f))
        val corner = game.nextCornerZ!!
        runPast(game, corner + 1f)
        assertFalse(game.isOver)
        assertEquals(1, game.turnsTaken)
    }

    @Test
    fun apresLEclairOnResteProtegeUnMoment() {
        val game = emptyGame()
        game.things.add(pickup(RunnerKind.BOOST, 0, 5f))
        var protectedAtEnd = false
        var seenBoost = false
        runPast(game, 400f) { g ->
            if (g.boostLeft > 0f) seenBoost = true
            if (seenBoost && g.boostLeft == 0f && g.invuln > 0f) protectedAtEnd = true
        }
        assertTrue(seenBoost)
        assertTrue(protectedAtEnd)
    }

    private fun farLaneCoinCaught(magnetLevel: Int): Pair<Boolean, Float> {
        val game = RunnerGame(1L, RunnerLevels(magnet = magnetLevel))
        game.things.clear()
        game.moveLeft()
        game.things.add(pickup(RunnerKind.MAGNET, -1, 5f))
        val coin = pickup(RunnerKind.COIN, 1, 14f)
        game.things.add(coin)
        var slid = 1.25f
        runPast(game, 15f) { slid = minOf(slid, coin.x) }
        return coin.taken to slid
    }

    @Test
    fun lAimantAttireLesPiecesDeToutesLesVoiesSeulementAuNiveauVoulu() {
        val (low, lowSlid) = farLaneCoinCaught(0)
        assertFalse(low)
        assertEquals(1.25f, lowSlid, 0f)
        val (high, highSlid) = farLaneCoinCaught(RunnerShop.ALL_LANES_LEVEL)
        assertTrue(high)
        assertTrue("la pièce doit glisser vers le coureur", highSlid < 0f)
    }

    @Test
    fun lesNiveauxAllongentLesBonus() {
        for (u in RunnerUpgrade.entries) {
            for (l in 1..RunnerShop.MAX_LEVEL) assertTrue(RunnerShop.seconds(u, l) > RunnerShop.seconds(u, l - 1))
        }
        val star = RunnerGame(1L, RunnerLevels(star = 2)).apply { things.clear(); things.add(pickup(RunnerKind.STAR, 0, 2f)) }
        runPast(star, 4f)
        assertTrue(star.starLeft > RunnerShop.starSeconds(0).toFloat())
        assertTrue(RunnerShop.magnetAhead(3) > RunnerShop.magnetAhead(0))
    }

    private fun gameWithCoins(n: Int): RunnerGame {
        val game = emptyGame()
        // Des diamants (5 pièces chacun) alignés devant le coureur.
        for (i in 0 until n / Runner.GEM_COINS) game.things.add(pickup(RunnerKind.GEM, 0, 5f + i * 1.2f))
        runPast(game, 5f + n / Runner.GEM_COINS * 1.2f + 2f) { g ->
            g.things.removeAll { it.kind.ordinal >= RunnerKind.LOW.ordinal }
            if (g.turnIsOpen) {
                if (g.nextCornerDir < 0) g.moveLeft() else g.moveRight()
            }
        }
        return game
    }

    @Test
    fun uneAmeliorationSAchetAvecLesPiecesDeLaPartieSansToucherAuScore() {
        val game = gameWithCoins(150)
        assertEquals(150, game.coins)
        val score = game.score
        assertEquals(150, game.purse)
        assertTrue(game.buy(RunnerUpgrade.MAGNET))
        assertEquals(1, game.levels.magnet)
        assertEquals(0, game.purse)
        // Les pièces attrapées et le score ne baissent pas quand on dépense.
        assertEquals(150, game.coins)
        assertEquals(score, game.score)
        assertFalse(game.buy(RunnerUpgrade.MAGNET))
        assertEquals(1, game.levels.magnet)
    }

    @Test
    fun onNeDepassePasLeNiveauMaximum() {
        val game = gameWithCoins(5000)
        repeat(RunnerShop.MAX_LEVEL) { assertTrue(game.buy(RunnerUpgrade.BOOST)) }
        assertFalse(game.buy(RunnerUpgrade.BOOST))
        assertEquals(RunnerShop.MAX_LEVEL, game.levels.boost)
    }

    @Test
    fun lesAmeliorationsAchetesSeSententSurLesBonusSuivants() {
        val game = gameWithCoins(150)
        game.buy(RunnerUpgrade.STAR)
        game.things.add(pickup(RunnerKind.STAR, 0, game.dist + 4f))
        runPast(game, game.dist + 6f)
        assertTrue(game.starLeft > RunnerShop.starSeconds(0).toFloat())
    }

    @Test
    fun uneNouvellePartieRepartDeZeroAvecTroisVies() {
        val game = RunnerGame(9L)
        assertEquals(Runner.START_LIVES, game.lives)
        assertEquals(RunnerLevels(), game.levels)
        assertEquals(0, game.purse)
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
            if (g.turnIsOpen) {
                if (g.nextCornerDir < 0) g.moveLeft() else g.moveRight()
            }
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
