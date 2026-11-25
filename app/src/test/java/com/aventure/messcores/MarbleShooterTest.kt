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

    // --- La carte et le chemin ---

    @Test
    fun leCheminNeSeCroiseNiNeSeFrole() {
        for (seed in 0 until 300) {
            val map = MarbleMap.generate(seed)
            val cells = map.path.toList()
            assertEquals("graine $seed : cases en double", cells.size, cells.toSet().size)
            assertTrue("graine $seed : longueur ${cells.size}", cells.size in MarbleMap.MIN_CELLS..53)
            for (i in cells.indices) {
                val row = cells[i] / MarbleMap.COLS
                val col = cells[i] % MarbleMap.COLS
                assertTrue(row in 0 until MarbleMap.ROWS && col in 0 until MarbleMap.COLS)
                assertFalse("graine $seed : le chemin passe sur le lanceur", MarbleMap.isReserved(row, col))
                if (i > 0) {
                    val dr = row - cells[i - 1] / MarbleMap.COLS
                    val dc = col - cells[i - 1] % MarbleMap.COLS
                    assertEquals("graine $seed : deux cases qui se suivent se touchent", 1, kotlin.math.abs(dr) + kotlin.math.abs(dc))
                }
                // Deux cases qui ne se suivent pas ne se touchent jamais par un côté.
                for (j in cells.indices) {
                    if (kotlin.math.abs(i - j) <= 1) continue
                    val other = cells[j]
                    val side = kotlin.math.abs(row - other / MarbleMap.COLS) + kotlin.math.abs(col - other % MarbleMap.COLS)
                    assertTrue("graine $seed : cases $i et $j collées", side > 1)
                }
            }
        }
    }

    @Test
    fun leCheminEntreParUnBordEtFinitNImporteOu() {
        val holes = HashSet<Int>()
        var holeNotOnBottomRow = false
        for (seed in 0 until 100) {
            val map = MarbleMap.generate(seed)
            val row = map.entryRow
            val col = map.entryCol
            assertTrue("graine $seed : l'entrée n'est pas sur un bord", row == 0 || row == MarbleMap.ROWS - 1 || col == 0 || col == MarbleMap.COLS - 1)
            // La case d'avant l'entrée est hors du terrain.
            val outRow = row + map.entryDy
            val outCol = col + map.entryDx
            assertTrue("graine $seed : l'entrée ne donne pas sur l'extérieur", outRow !in 0 until MarbleMap.ROWS || outCol !in 0 until MarbleMap.COLS)
            assertEquals(1, kotlin.math.abs(map.entryDx) + kotlin.math.abs(map.entryDy))
            holes.add(map.path.last())
            if (map.holeRow != MarbleMap.ROWS - 1) holeNotOnBottomRow = true
        }
        assertTrue("trous : ${holes.size}", holes.size >= 20)
        assertTrue(holeNotOnBottomRow)
    }

    @Test
    fun laCarteNeDependQueDeLaGraine() {
        val a = MarbleMap.generate(42)
        val b = MarbleMap.generate(42)
        assertEquals(a.path.toList(), b.path.toList())
        assertEquals(a.decor.toList(), b.decor.toList())
        assertEquals(a.entryDx, b.entryDx)
        assertEquals(a.entryDy, b.entryDy)
        assertFalse(a.path.toList() == MarbleMap.generate(43).path.toList())
    }

    @Test
    fun lesDecorsNeTouchentNiLeCheminNiLeLanceur() {
        for (seed in 0 until 100) {
            val map = MarbleMap.generate(seed)
            for (row in 0 until MarbleMap.ROWS) {
                for (col in 0 until MarbleMap.COLS) {
                    if (map.decor[row * MarbleMap.COLS + col] < 0) continue
                    assertFalse("graine $seed : décor sur le chemin", map.isPath(row, col))
                    assertFalse("graine $seed : décor sur le lanceur", MarbleMap.isReserved(row, col))
                }
            }
        }
    }

    @Test
    fun leSolDeLaCarteALaBonneTuile() {
        val map = MarbleMap.generate(7)
        for (row in 0 until MarbleMap.ROWS) {
            for (col in 0 until MarbleMap.COLS) {
                val tile = map.tileIndex(row, col)
                assertTrue(tile in 0 until TdTiles.SIGNATURES.size)
                if (!map.isPath(row, col)) assertTrue(tile in TdTiles.SWAMP.toList())
            }
        }
    }

    @Test
    fun leThemeDeLaCarteSuitSaGraineCommeAuTowerDefense() {
        val themes = HashSet<Int>()
        for (seed in 0 until 100) {
            val map = MarbleMap.generate(seed)
            assertEquals(TdMap.themeOf(seed), map.theme)
            themes.add(map.theme)
        }
        assertEquals(setOf(TdMap.THEME_SWAMP, TdMap.THEME_SUMMER), themes)
    }

    @Test
    fun leCheminDUneCarteCommenceHorsDuTerrainEtFinitAuTrou() {
        for (seed in 0 until 100) {
            val map = MarbleMap.generate(seed)
            val path = MarblePath.fromMap(map)
            val x = path.x(0f)
            val y = path.y(0f)
            assertTrue("graine $seed : départ ($x, $y) dans le terrain", x < 0f || x > MarbleMap.COLS || y < 0f || y > MarbleMap.ROWS)
            assertEquals(map.holeCol + 0.5f, path.holeX, 1e-4f)
            assertEquals(map.holeRow + 0.5f, path.holeY, 1e-4f)
            assertEquals(Marble.ENTRY_LEAD + map.path.size - 1, path.length, 1e-3f)
        }
    }

    @Test
    fun leCheminSeParcourtSansSaut() {
        val path = MarblePath.fromMap(MarbleMap.generate(7))
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
        val path = MarblePath.fromMap(MarbleMap.generate(3))
        var s = 0f
        while (s <= path.length) {
            val dx = path.dirX(s)
            val dy = path.dirY(s)
            assertEquals(1f, dx * dx + dy * dy, 1e-3f)
            s += 0.37f
        }
    }

    @Test
    fun leLanceurEstAuCentreLoinDuChemin() {
        for (seed in 0 until 50) {
            val g = MarbleGame(Random(seed))
            assertEquals(MarbleMap.SHOOTER_COL + 0.5f, g.shooterX, 1e-4f)
            assertEquals(MarbleMap.SHOOTER_ROW + 0.5f, g.shooterY, 1e-4f)
            var s = 0f
            while (s <= g.path.length) {
                val dx = g.shooterX - g.path.x(s)
                val dy = g.shooterY - g.path.y(s)
                assertTrue("graine $seed : lanceur trop près du chemin", sqrt(dx * dx + dy * dy) >= 1.4f)
                s += 0.1f
            }
        }
    }

    @Test
    fun uneBilleQuiArriveDuDehorsNePeutPasEtreTouchee() {
        // Le chemin vient de la gauche, hors du terrain : les billes à x < 0 sont invisibles.
        val g = MarbleGame(Random(1))
        g.loadField(MarblePath(floatArrayOf(-4f, 10f), floatArrayOf(3f, 3f)), 4f, 4.5f)
        g.setSupply(0)
        g.setChain(listOf(8.0f to 0, 7.45f to 1, 1.0f to 2, 0.45f to 3))
        assertTrue(g.isMarbleVisible(0))
        assertFalse(g.isMarbleVisible(3))
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
        // La série de devant recule vers la queue, qui avance.
        val head = g.marbleS(0)
        run(g, 100)
        assertTrue("la tête doit reculer : $head puis ${g.marbleS(0)}", g.marbleS(0) < head)
        run(g, 2_900)
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


    // --- Le recul du train de devant ---

    @Test
    fun apresUnTrouLaTeteRecuePendantQueLaQueueAvance() {
        val g = MarbleGame(Random(1))
        g.loadField(line(), 4f, 4.5f)
        g.setSupply(0)
        g.setChain(listOf(8.0f to 3, 7.45f to 4, 5.0f to 1, 4.45f to 2))
        run(g, 100)
        assertTrue("la tête recule : ${g.marbleS(0)}", g.marbleS(0) < 8.0f - 0.3f)
        assertTrue("la queue avance : ${g.marbleS(3)}", g.marbleS(3) > 4.45f)
        // Le train de devant recule d'un bloc : ses billes restent collées.
        assertEquals(Marble.DIAMETER, g.marbleS(0) - g.marbleS(1), 0.01f)
        run(g, 1_500)
        assertEquals(listOf(3, 4, 1, 2), colors(g))
        assertSpaced(g)
        for (i in 0 until g.marbleCount - 1) assertTrue(g.marbleS(i) - g.marbleS(i + 1) <= Marble.DIAMETER + 0.03f)
    }

    @Test
    fun uneReactionEnChaineFaitReculerToutLeTrainDeDevant() {
        // Quatre billes isolées en tête, puis deux 1, deux 2, un 1 et deux billes de queue : le tir de 2 fait éclater les 2,
        // les 1 se rejoignent et éclatent à leur tour, et les quatre billes de tête reculent d'un bloc.
        val g = fieldGame(listOf(3, 4, 3, 4, 1, 1, 2, 2, 1, 4, 3), sOf(7) - 0.1f)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        landShot(g)
        assertEquals(1, g.popCount)
        val start = g.marbleS(0)
        run(g, 100)
        assertTrue("la tête recule : $start puis ${g.marbleS(0)}", g.marbleS(0) < start)
        var guard = 0
        while (g.popCount < 2 && guard++ < 200) g.tick(20)
        assertEquals(2, g.popCount)
        assertEquals(2, g.lastChain)
        val atChain = g.marbleS(0)
        run(g, 150)
        assertTrue("tout le train recule encore : $atChain puis ${g.marbleS(0)}", g.marbleS(0) < atChain - 0.1f)
        for (i in 0 until 3) {
            val gap = g.marbleS(i) - g.marbleS(i + 1)
            assertTrue("écart $gap", gap >= Marble.DIAMETER - 0.01f && gap <= Marble.DIAMETER + 0.03f)
        }
        run(g, 2_500)
        assertEquals(listOf(3, 4, 3, 4, 4, 3), colors(g))
        assertEquals(Marble.chainPoints(3, 1) + Marble.chainPoints(3, 2), g.score)
        assertSpaced(g)
    }

    @Test
    fun uneBilleSortieSeuleFonceRejoindreLaChaineQuiLAttend() {
        val g = MarbleGame(Random(1))
        g.loadField(line(), 4f, 4.5f)
        g.setChain(listOf(6.0f to 3, 5.45f to 4))
        g.setSupply(1)
        g.tick(20)
        // La queue avait éclaté : la nouvelle bille sort seule de l'entrée.
        assertEquals(3, g.marbleCount)
        assertEquals(0f, g.marbleS(2), 0.2f)
        val head = g.marbleS(0)
        run(g, 500)
        // La chaîne attend la bille qui arrive : sa tête ne recule pas et n'avance pas.
        assertEquals(head, g.marbleS(0), 1e-4f)
        assertTrue("la bille avance : ${g.marbleS(2)}", g.marbleS(2) > 1f)
        run(g, 1_000)
        assertEquals(3, g.marbleCount)
        assertSpaced(g)
        assertTrue("la chaîne repart : ${g.marbleS(0)}", g.marbleS(0) >= head && g.marbleS(0) < head + 0.5f)
        for (i in 0 until g.marbleCount - 1) assertTrue(g.marbleS(i) - g.marbleS(i + 1) <= Marble.DIAMETER + 0.03f)
    }

    @Test
    fun laSauvegardeGardeLaBilleQuiFonceRejoindreLaChaine() {
        val g = MarbleGame(Random(1))
        g.loadField(line(), 4f, 4.5f)
        g.setChain(listOf(6.0f to 3, 5.45f to 4))
        g.setSupply(1)
        g.tick(20)
        val r = MarbleGame.restore(g.save(), Random(2))!!
        run(g, 300)
        run(r, 300)
        assertEquals(g.marbleCount, r.marbleCount)
        for (i in 0 until g.marbleCount) assertEquals(g.marbleS(i), r.marbleS(i), 1e-4f)
    }


    // --- Les billes spéciales ---

    /** La bille d'indice [i] d'une chaîne de [fieldGame] se trouve à sOf(i) : on tire dessus depuis le côté de derrière. */
    private fun shootAt(g: MarbleGame, special: Int) {
        g.setQueue(special, 1)
        assertTrue(g.shoot(up))
        landShot(g)
    }

    @Test
    fun uneBombeFaitEclaterLesBillesAutourDeLaBilleTouchee() {
        val g = fieldGame(listOf(0, 1, 2, 3, 4, 5, 0, 1, 2), sOf(4) - 0.1f)
        shootAt(g, Marble.BOMB)
        // Deux billes de chaque côté de la bille touchée (à moins de 1,2) : cinq billes, la bombe ne reste pas dans la chaîne.
        assertEquals(listOf(0, 1, 1, 2), colors(g))
        assertEquals(Marble.chainPoints(5, 1), g.score)
        assertEquals(1, g.popCount)
        assertEquals(5 + 1, g.lastEffects.size)
        assertEquals(Marble.BOMB, g.lastEffects.last().color)
        assertEquals(1, g.lastChain)
    }

    @Test
    fun uneBombeSurUneSeuleBilleLaFaitEclater() {
        val g = fieldGame(listOf(0), 6.4f)
        shootAt(g, Marble.BOMB)
        // La chaîne est vidée : le niveau est gagné et le suivant commence.
        assertEquals(0, g.marbleCount)
        assertEquals(2, g.level)
        assertEquals(Marble.chainPoints(1, 1) + Marble.LEVEL_BONUS, g.score)
    }

    @Test
    fun unEclairFaitEclaterToutesLesBillesDeLaCouleurTouchee() {
        val g = fieldGame(listOf(2, 1, 2, 3, 2, 0, 2), sOf(2) - 0.1f)
        shootAt(g, Marble.LIGHTNING)
        assertEquals(listOf(1, 3, 0), colors(g))
        assertEquals(Marble.chainPoints(4, 1), g.score)
        assertEquals(4 + 1, g.lastEffects.size)
        assertEquals(Marble.LIGHTNING, g.lastEffects.last().color)
    }

    @Test
    fun unArcEnCielPrendLaCouleurQuiFaitLePlusGrosGroupe() {
        // Entre le 3 (devant) et les deux 4 (derrière) : le groupe de 4 est le plus gros, il éclate.
        val g = fieldGame(listOf(0, 3, 4, 4, 1), sOf(2) + 0.1f)
        shootAt(g, Marble.RAINBOW)
        assertEquals(listOf(0, 3, 1), colors(g))
        assertEquals(Marble.chainPoints(3, 1), g.score)
    }

    @Test
    fun unArcEnCielQuiNeFaitPasDeGroupeS_insereCommeUneCouleur() {
        val g = fieldGame(listOf(0, 1, 2, 3), sOf(1) - 0.1f)
        shootAt(g, Marble.RAINBOW)
        assertEquals(5, g.marbleCount)
        assertTrue(colors(g).all { it in 0 until Marble.PALETTE })
        assertEquals(0, g.score)
    }

    @Test
    fun uneBilleSpecialeNeResteJamaisDansLaChaine() {
        for (special in listOf(Marble.BOMB, Marble.RAINBOW, Marble.LIGHTNING)) {
            val g = fieldGame(listOf(0, 1, 2, 3, 4, 5), sOf(2) - 0.1f)
            shootAt(g, special)
            assertTrue("spéciale $special", colors(g).all { it in 0 until Marble.PALETTE })
        }
    }

    @Test
    fun lesBillesQuiEclatentChargentLaJaugeEtUneBilleSpecialeArrive() {
        val g = fieldGame(listOf(3, 1, 2, 2, 0, 4, 3), sOf(3) - 0.1f)
        g.setLevel(2)
        assertEquals(0f, g.specialProgress, 1e-6f)
        g.setSpecialCharge(Marble.SPECIAL_THRESHOLD - 3)
        g.setQueue(2, 2)
        assertTrue(g.shoot(up))
        // Un groupe de trois va éclater : la jauge est pleine au moment où la suivante passe au lanceur.
        landShot(g)
        assertEquals(1f, g.specialProgress, 1e-6f)
        g.setQueue(5, 5)
        g.setSpecialCharge(Marble.SPECIAL_THRESHOLD)
        assertTrue(g.shoot(up))
        assertEquals(Marble.BOMB, g.next)
        assertEquals(0f, g.specialProgress, 1e-6f)
    }

    @Test
    fun auNiveauUnAucuneBilleSpecialeNArrive() {
        val g = fieldGame(listOf(3, 1, 2, 0, 4), sOf(2) - 0.1f)
        g.setSpecialCharge(Marble.SPECIAL_THRESHOLD * 3)
        assertEquals(0f, g.specialProgress, 1e-6f)
        g.setQueue(0, 1)
        assertTrue(g.shoot(up))
        assertTrue(g.next in 0 until Marble.PALETTE)
    }

    @Test
    fun lesBillesSpecialesSeDebloquentNiveauApresNiveau() {
        assertEquals(emptyList<Int>(), Marble.unlockedSpecials(1))
        assertEquals(listOf(Marble.BOMB), Marble.unlockedSpecials(2))
        assertEquals(listOf(Marble.BOMB, Marble.RAINBOW), Marble.unlockedSpecials(3))
        assertEquals(listOf(Marble.BOMB, Marble.RAINBOW, Marble.LIGHTNING), Marble.unlockedSpecials(4))
        assertTrue(Marble.isSpecial(Marble.BOMB))
        assertFalse(Marble.isSpecial(Marble.PALETTE - 1))
    }

    @Test
    fun uneBilleSpecialeDuLanceurNEstPasRemplaceeQuandSaCouleurDisparait() {
        val g = fieldGame(listOf(3, 1, 2, 2, 0, 4, 3), sOf(3) - 0.1f)
        g.setQueue(2, Marble.BOMB)
        assertTrue(g.shoot(up))
        landShot(g)
        // La bille d'attente passée au lanceur est une bombe : elle reste une bombe, même si sa « couleur » n'est plus sur le chemin.
        assertEquals(Marble.BOMB, g.current)
    }

    @Test
    fun laSauvegardeGardeLaJaugeEtLesBillesSpeciales() {
        val g = fieldGame(listOf(3, 1, 2, 0, 4), sOf(2) - 0.1f)
        g.setLevel(3)
        g.setSpecialCharge(7)
        g.setQueue(Marble.RAINBOW, Marble.BOMB)
        val r = MarbleGame.restore(g.save(), Random(2))!!
        assertEquals(Marble.RAINBOW, r.current)
        assertEquals(Marble.BOMB, r.next)
        assertEquals(g.specialProgress, r.specialProgress, 1e-6f)
        assertEquals(3, r.level)
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
        // La couleur de la première bille (juste après l'en-tête de 21 entiers et sa distance) n'est pas une couleur.
        assertNull(MarbleGame.restore(data.toMutableList().also { it[22] = 99 }))
        // La jauge des billes spéciales est négative.
        assertNull(MarbleGame.restore(data.toMutableList().also { it[19] = -1 }))
        // Le nombre de billes annoncé n'est pas celui qui suit.
        assertNull(MarbleGame.restore(data.toMutableList().also { it[20] = it[20] + 1 }))
    }
}
