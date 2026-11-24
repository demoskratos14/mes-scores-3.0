package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarbleShooterTest {

    private val up = (-PI / 2).toFloat()
    private val down = (PI / 2).toFloat()

    /** Un chemin tout droit, de gauche à droite, à la hauteur 3 : la bille de distance s est en x = s. Le trou est en x = 10. */
    private fun line() = MarblePath(floatArrayOf(0f, 10f), floatArrayOf(3f, 3f))

    /** La distance de la bille d'indice [i] d'une chaîne posée par [fieldGame] (la tête est à [leader]). */
    private fun sOf(i: Int, leader: Float = 6.5f): Float = leader - i * Marble.DIAMETER

    /** Une partie sur le chemin tout droit : une chaîne collée de billes [colors], rien à sortir de l'entrée, le lanceur en ([shooterX], 4,5). */
    private fun fieldGame(colors: List<Int>, shooterX: Float, leader: Float = 6.5f): MarbleGame {
        val g = MarbleGame(Random(1))
        g.loadField(line(), shooterX, 4.5f)
        g.setSupply(0)
        g.setMarbles(colors, leader)
        return g
    }

    private fun run(g: MarbleGame, ms: Int) {
        var left = ms
        while (left > 0) {
            val d = min(20, left)
            g.tick(d.toLong())
            left -= d
        }
    }

    /** Fait avancer le temps jusqu'à ce que la bille en vol se pose, au pas près. */
    private fun landShot(g: MarbleGame) {
        var guard = 0
        while (g.isFlying && guard++ < 200) g.tick(20)
        assertFalse(g.isFlying)
    }

    private fun colors(g: MarbleGame): List<Int> = (0 until g.marbleCount).map { g.marbleColor(it) }

    private fun assertSpaced(g: MarbleGame) {
        for (i in 0 until g.marbleCount - 1) {
            assertTrue("billes ${i} et ${i + 1} qui se chevauchent", g.marbleS(i) - g.marbleS(i + 1) >= Marble.DIAMETER - 0.01f)
        }
    }

    // --- Le chemin ---

    @Test
    fun leCheminDUneCarteCommenceHorsDuTerrainEtFinitAuTrou() {
        for (seed in 0 until 100) {
            val map = TdMap.generate(seed)
            val path = MarblePath.fromTdMap(map)
            assertTrue(path.y(0f) < 0f)
            val last = map.path.last()
            assertEquals(last % TdMap.COLS + 0.5f, path.holeX, 1e-4f)
            assertEquals(last / TdMap.COLS + 0.5f, path.holeY, 1e-4f)
            assertEquals(Marble.ENTRY_LEAD + map.path.size - 1, path.length, 1e-3f)
        }
    }

    @Test
    fun leCheminSeParcourtSansSaut() {
        val path = MarblePath.fromTdMap(TdMap.generate(7))
        var s = 0.1f
        while (s <= path.length) {
            val dx = path.x(s) - path.x(s - 0.1f)
            val dy = path.y(s) - path.y(s - 0.1f)
            assertTrue(sqrt(dx * dx + dy * dy) <= 0.1f + 1e-3f)
            s += 0.1f
        }
    }

    @Test
    fun laDirectionDuCheminEstUnVecteurUnitaire() {
        val path = MarblePath.fromTdMap(TdMap.generate(3))
        var s = 0f
        while (s <= path.length) {
            val dx = path.dirX(s)
            val dy = path.dirY(s)
            assertEquals(1f, dx * dx + dy * dy, 1e-3f)
            s += 0.37f
        }
    }

    @Test
    fun leLanceurEstSurUneCaseLibreLoinDuChemin() {
        for (seed in 0 until 50) {
            val g = MarbleGame(Random(seed))
            assertTrue(g.shooterX in 0f..TdMap.COLS.toFloat())
            assertTrue(g.shooterY in 0f..TdMap.ROWS.toFloat())
            var s = 0f
            while (s <= g.path.length) {
                val dx = g.shooterX - g.path.x(s)
                val dy = g.shooterY - g.path.y(s)
                assertTrue("graine $seed : lanceur sur le chemin", sqrt(dx * dx + dy * dy) >= 0.9f)
                s += 0.1f
            }
        }
    }

    // --- Réglages ---

    @Test
    fun lesReglagesDesNiveauxMontentSansDepasserLeurLimite() {
        assertEquals(4, Marble.colorsFor(1))
        assertEquals(Marble.PALETTE, Marble.colorsFor(50))
        for (level in 1 until 60) {
            assertTrue(Marble.colorsFor(level + 1) >= Marble.colorsFor(level))
            assertTrue(Marble.countFor(level + 1) >= Marble.countFor(level))
            assertTrue(Marble.speedFor(level + 1) >= Marble.speedFor(level))
        }
        assertTrue(Marble.countFor(1000) <= 70)
        assertTrue(Marble.speedFor(1000) <= 1.3f)
    }

    @Test
    fun lesPointsDUnGroupeGrandissentAvecSaTailleEtLaReaction() {
        assertEquals(30, Marble.chainPoints(3, 1))
        assertEquals(50, Marble.chainPoints(4, 1))
        assertEquals(60, Marble.chainPoints(3, 2))
        assertEquals(30, Marble.chainPoints(3, 0))
    }

    // --- Le départ et la sortie des billes ---

    @Test
    fun auDepartLaChaineEstVideEtToutesLesBillesAttendent() {
        val g = MarbleGame(Random(2))
        assertEquals(0, g.marbleCount)
        assertEquals(Marble.countFor(1), g.supply)
        assertEquals(1, g.level)
        assertEquals(0, g.score)
        assertFalse(g.isOver)
        assertTrue(g.current in 0 until Marble.colorsFor(1))
        assertTrue(g.next in 0 until Marble.colorsFor(1))
    }

    @Test
    fun lesBillesSortentEnChaineCollee() {
        val g = MarbleGame(Random(3))
        run(g, 20_000)
        assertTrue(g.marbleCount > 10)
        assertEquals(Marble.countFor(1), g.marbleCount + g.supply)
        assertFalse(g.isOver)
        for (i in 0 until g.marbleCount - 1) {
            val gap = g.marbleS(i) - g.marbleS(i + 1)
            assertTrue("écart $gap", gap >= Marble.DIAMETER - 0.01f && gap <= Marble.DIAMETER + 0.03f)
        }
    }

    @Test
    fun jamaisTroisBillesDeSuiteDeLaMemeCouleurALaSortie() {
        for (seed in 0 until 20) {
            val g = MarbleGame(Random(seed))
            run(g, 20_000)
            for (i in 0 until g.marbleCount) assertTrue(g.marbleColor(i) in 0 until Marble.colorsFor(1))
            for (i in 2 until g.marbleCount) {
                val same = g.marbleColor(i) == g.marbleColor(i - 1) && g.marbleColor(i) == g.marbleColor(i - 2)
                assertFalse("graine $seed : trois billes pareilles", same)
            }
        }
    }

    @Test
    fun auDebutLaChaineEntreVitePuisRalentit() {
        val g = MarbleGame(Random(4))
        run(g, 1_000)
        val fast = g.marbleS(0)
        assertTrue("s = $fast", fast > 2f)
        run(g, 12_000)
        val after = g.marbleS(0)
        run(g, 2_000)
        val slow = g.marbleS(0) - after
        assertTrue("avance de $slow en 2 s", slow < 2f * Marble.speedFor(1) + 0.1f)
    }

    @Test
    fun leModeDuJourDonneLaMemeCarteEtLesMemesBilles() {
        val seed = Marble.dailySeed(2026, 11, 23)
        val a = MarbleGame(Random(1))
        val b = MarbleGame(Random(2))
        a.start(MarbleMode.DAILY, seed)
        b.start(MarbleMode.DAILY, seed)
        run(a, 10_000)
        run(b, 10_000)
        assertEquals(a.path.length, b.path.length, 1e-4f)
        assertEquals(a.shooterX, b.shooterX, 1e-4f)
        assertEquals(a.marbleCount, b.marbleCount)
        assertEquals(colors(a), colors(b))
    }

    // --- Le lanceur ---

    @Test
    fun onNePeutPasTirerDeuxFoisDeSuiteEtLaSuivanteDevientLaCourante() {
        val g = fieldGame(listOf(0, 1, 2), 4f)
        g.setQueue(3, 4)
        assertTrue(g.shoot(up))
        assertFalse(g.shoot(up))
        assertEquals(4, g.current)
        assertEquals(3, g.flightColor)
        assertTrue(g.isFlying)
    }

    @Test
    fun onPeutEchangerLaBilleDuLanceurAvecLaSuivante() {
        val g = fieldGame(listOf(0, 1, 2), 4f)
        g.setQueue(3, 4)
        assertTrue(g.swap())
        assertEquals(4, g.current)
        assertEquals(3, g.next)
    }

    @Test
    fun leGuideDeViseeSArreteContreLaPremiereBille() {
        val x = sOf(2) - 0.1f
        val g = fieldGame(listOf(0, 1, 2, 3, 4), x)
        val guide = g.trajectory(up)
        assertEquals(2, guide.size)
        assertEquals(x, guide[0].first, 1e-4f)
        assertEquals(4.5f, guide[0].second, 1e-4f)
        assertEquals(x, guide[1].first, 1e-3f)
        assertTrue("y = ${guide[1].second}", guide[1].second in 3.2f..3.7f)
    }

    @Test
    fun leGuideDeViseeVaJusquAuBordQuandRienNEstTouche() {
        val g = fieldGame(listOf(0, 1, 2), 4f)
        val guide = g.trajectory(down)
        assertTrue(guide[1].second > TdMap.ROWS)
    }

    @Test
    fun unTirPerduNeCoutePasEtLibereLeLanceur() {
        val g = fieldGame(listOf(0, 1, 2), 4f)
        assertTrue(g.shoot(down))
        run(g, 1_500)
        assertFalse(g.isFlying)
        assertEquals(1, g.shotCount)
        assertEquals(3, g.marbleCount)
        assertEquals(0, g.score)
        assertFalse(g.isOver)
        assertTrue(g.shoot(down))
    }

    // --- L'insertion ---

    @Test
    fun uneBilleTireeSInsereDerriereLaBilleTouchee() {
        val g = fieldGame(listOf(3, 1, 2, 0, 3), sOf(2) - 0.1f)
        g.setQueue(5, 5)
        assertTrue(g.shoot(up))
        landShot(g)
        assertEquals(6, g.marbleCount)
        assertEquals(listOf(3, 1, 2, 5, 0, 3), colors(g))
        assertEquals(1, g.shotCount)
        assertEquals(0, g.score)
        run(g, 1_400)
        assertSpaced(g)
        assertTrue("tête à ${g.marbleS(0)}", g.marbleS(0) >= 6.5f + Marble.DIAMETER - 0.01f)
    }

    @Test
    fun uneBilleTireeSInsereDevantLaBilleToucheeSiElleArriveDuCoteDuTrou() {
        val g = fieldGame(listOf(3, 1, 2, 0, 3), sOf(2) + 0.1f)
        g.setQueue(5, 5)
        assertTrue(g.shoot(up))
        landShot(g)
        assertEquals(listOf(3, 1, 5, 2, 0, 3), colors(g))
        run(g, 1_400)
        assertSpaced(g)
    }

    @Test
    fun deuxBillesDeLaMemeCouleurNeSuffisentPas() {
        val g = fieldGame(listOf(3, 1, 2, 0, 4), sOf(2) - 0.1f)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        landShot(g)
        assertEquals(listOf(3, 1, 2, 2, 0, 4), colors(g))
        assertEquals(0, g.score)
        assertEquals(0, g.popCount)
    }

    // --- Les éclatements ---

    @Test
    fun troisBillesDeLaMemeCouleurEclatent() {
        val g = fieldGame(listOf(3, 1, 2, 2, 0, 4, 3), sOf(3) - 0.1f)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        landShot(g)
        assertEquals(listOf(3, 1, 0, 4, 3), colors(g))
        assertEquals(Marble.chainPoints(3, 1), g.score)
        assertEquals(1, g.popCount)
        assertEquals(1, g.lastChain)
        assertEquals(3, g.lastEffects.size)
        assertTrue(g.lastEffects.all { it.color == 2 })
    }

    @Test
    fun unGroupeDeQuatreRapporteUnBonus() {
        val g = fieldGame(listOf(3, 2, 2, 2, 0, 4), sOf(2) - 0.1f)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        landShot(g)
        assertEquals(listOf(3, 0, 4), colors(g))
        assertEquals(Marble.chainPoints(4, 1), g.score)
    }

    @Test
    fun unGroupeEnTeteEclateSansToucherLesAutres() {
        val g = fieldGame(listOf(2, 2, 1, 1), sOf(1) - 0.1f)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        landShot(g)
        // Les deux billes de tête et la bille tirée forment un groupe de trois : il éclate, les deux autres restent.
        assertEquals(listOf(1, 1), colors(g))
    }

    @Test
    fun leTrouRefermeFaitEclaterUnSecondGroupeAvecUneReactionEnChaine() {
        val g = fieldGame(listOf(3, 1, 1, 2, 2, 1, 3), sOf(4) - 0.1f)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        landShot(g)
        // Le premier groupe est parti ; les deux séries restantes ne se touchent pas encore.
        assertEquals(listOf(3, 1, 1, 1, 3), colors(g))
        assertEquals(Marble.chainPoints(3, 1), g.score)
        assertEquals(1, g.popCount)
        assertTrue("la série de tête attend : ${g.marbleS(0)}", g.marbleS(0) < 6.5f + 0.2f)
        run(g, 3_000)
        assertEquals(listOf(3, 3), colors(g))
        assertEquals(Marble.chainPoints(3, 1) + Marble.chainPoints(3, 2), g.score)
        assertEquals(2, g.popCount)
        assertEquals(2, g.lastChain)
        assertSpaced(g)
    }

    @Test
    fun laBilleDuLanceurRedevientJouableQuandSaCouleurDisparait() {
        val g = fieldGame(listOf(3, 1, 2, 2, 0, 4, 3), sOf(3) - 0.1f)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        landShot(g)
        // Il n'y a plus de bille 2 sur le chemin : ni la courante ni la suivante ne doivent l'être.
        val present = colors(g).toSet()
        assertTrue(g.current in present)
        assertTrue(g.next in present)
    }

    // --- Fin de niveau et fin de partie ---

    @Test
    fun viderLaChaineGagneLeNiveauEtEnLanceUnPlusLong() {
        val g = MarbleGame(Random(1))
        g.loadField(line(), 4f, 4.5f)
        g.setSupply(0)
        g.setMarbles(emptyList(), 6f)
        g.tick(20)
        assertEquals(2, g.level)
        assertEquals(Marble.LEVEL_BONUS, g.score)
        assertEquals(Marble.countFor(2), g.supply)
        assertEquals(0, g.marbleCount)
        assertFalse(g.isOver)
    }

    @Test
    fun laChaineQuiAtteintLeTrouFinitLaPartie() {
        val g = fieldGame(listOf(0, 1, 2), 4f, leader = 9.9f)
        run(g, 1_000)
        assertTrue(g.isOver)
        assertFalse(g.shoot(up))
        assertFalse(g.swap())
        val before = g.marbleS(0)
        run(g, 500)
        assertEquals(before, g.marbleS(0), 1e-6f)
    }

    @Test
    fun uneBilleNonSortieEmpecheLaVictoire() {
        val g = MarbleGame(Random(1))
        g.loadField(line(), 4f, 4.5f)
        g.setSupply(3)
        g.setMarbles(emptyList(), 6f)
        g.tick(20)
        assertEquals(1, g.level)
    }

    @Test
    fun recommencerRemetToutAZero() {
        val g = MarbleGame(Random(6))
        run(g, 10_000)
        g.reset()
        assertEquals(1, g.level)
        assertEquals(0, g.score)
        assertEquals(0, g.marbleCount)
        assertEquals(Marble.countFor(1), g.supply)
        assertFalse(g.isOver)
        assertFalse(g.isFlying)
    }

    // --- La sauvegarde ---

    @Test
    fun laSauvegardeRedonneLaMemePartie() {
        val g = MarbleGame(Random(5))
        run(g, 8_000)
        assertTrue(g.shoot(up))
        run(g, 40)
        val r = MarbleGame.restore(g.save(), Random(9))!!
        assertEquals(g.level, r.level)
        assertEquals(g.score, r.score)
        assertEquals(g.supply, r.supply)
        assertEquals(g.current, r.current)
        assertEquals(g.next, r.next)
        assertEquals(g.mode, r.mode)
        assertEquals(g.isOver, r.isOver)
        assertEquals(g.isFlying, r.isFlying)
        assertEquals(g.flightX, r.flightX, 1e-6f)
        assertEquals(g.flightY, r.flightY, 1e-6f)
        assertEquals(g.path.length, r.path.length, 1e-4f)
        assertEquals(g.shooterX, r.shooterX, 1e-6f)
        assertEquals(g.shooterY, r.shooterY, 1e-6f)
        assertEquals(g.marbleCount, r.marbleCount)
        for (i in 0 until g.marbleCount) {
            assertEquals(g.marbleS(i), r.marbleS(i), 1e-6f)
            assertEquals(g.marbleColor(i), r.marbleColor(i))
            assertEquals(g.marbleX(i), r.marbleX(i), 1e-5f)
            assertEquals(g.marbleY(i), r.marbleY(i), 1e-5f)
        }
    }

    @Test
    fun uneSauvegardeAbimeeEstRefusee() {
        val g = MarbleGame(Random(5))
        run(g, 8_000)
        val data = g.save()
        assertNotNull(MarbleGame.restore(data))
        assertNull(MarbleGame.restore(emptyList()))
        assertNull(MarbleGame.restore(data.dropLast(1)))
        assertNull(MarbleGame.restore(data.toMutableList().also { it[0] = 99 }))
        // La bille du lanceur n'est pas une couleur.
        assertNull(MarbleGame.restore(data.toMutableList().also { it[8] = 42 }))
        // La couleur de la première bille (juste après l'en-tête de 20 entiers et sa distance) n'est pas une couleur.
        assertNull(MarbleGame.restore(data.toMutableList().also { it[21] = 99 }))
        // Le nombre de billes annoncé n'est pas celui qui suit.
        assertNull(MarbleGame.restore(data.toMutableList().also { it[19] = it[19] + 1 }))
    }
}
