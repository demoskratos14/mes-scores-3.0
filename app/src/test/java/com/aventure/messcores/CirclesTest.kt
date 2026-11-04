package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CirclesTest {

    @Test
    fun lePremierRondDureLaDureeDeDepart() {
        assertEquals(Circles.START_LIFETIME_MS, Circles.lifetimeMs(0))
    }

    @Test
    fun chaqueRondTouchePrendUnPeuMoinsDeTemps() {
        assertEquals(Circles.START_LIFETIME_MS - Circles.LIFETIME_STEP_MS, Circles.lifetimeMs(1))
        assertTrue(Circles.lifetimeMs(10) < Circles.lifetimeMs(5))
    }

    @Test
    fun laDureeNeDescendJamaisSousLePlancher() {
        assertEquals(Circles.MIN_LIFETIME_MS, Circles.lifetimeMs(1_000))
        assertTrue(Circles.lifetimeMs(100) >= Circles.MIN_LIFETIME_MS)
    }

    @Test
    fun leRondRetrecitEnLigneDroiteJusquAZero() {
        assertEquals(50f, Circles.radiusAt(0, 2_000, 50f), 0.001f)
        assertEquals(25f, Circles.radiusAt(1_000, 2_000, 50f), 0.001f)
        assertEquals(0f, Circles.radiusAt(2_000, 2_000, 50f), 0.001f)
        assertEquals(0f, Circles.radiusAt(5_000, 2_000, 50f), 0.001f)
    }

    @Test
    fun leRondEstVivantJusqueSaDureeDeVie() {
        assertTrue(Circles.isAlive(1_999, 2_000))
        assertFalse(Circles.isAlive(2_000, 2_000))
    }

    @Test
    fun unTouchDansLeRondLeTouche() {
        assertTrue(Circles.isHit(105f, 100f, 100f, 100f, radius = 20f, maxRadius = 50f))
        assertFalse(Circles.isHit(160f, 100f, 100f, 100f, radius = 20f, maxRadius = 50f))
    }

    @Test
    fun unRondMinusculResteTouchableSurSaPartieMinimale() {
        // Rayon réel de 2 px, mais 40 % du rayon de départ (20 px) restent touchables.
        assertTrue(Circles.isHit(115f, 100f, 100f, 100f, radius = 2f, maxRadius = 50f))
        assertFalse(Circles.isHit(130f, 100f, 100f, 100f, radius = 2f, maxRadius = 50f))
    }

    @Test
    fun leRondApparaitToujoursEntierDansLaZone() {
        val random = Random(42)
        repeat(500) {
            val c = Circles.randomCenter(400f, 800f, 50f, random = random)
            assertTrue(c.x in 50f..350f)
            assertTrue(c.y in 50f..750f)
        }
    }

    @Test
    fun uneZoneTropPetiteDonneSonCentre() {
        val c = Circles.randomCenter(60f, 60f, 50f)
        assertEquals(30f, c.x, 0.001f)
        assertEquals(30f, c.y, 0.001f)
    }

    @Test
    fun leRondSuivantApparaitLoinDuPrecedent() {
        val random = Random(7)
        val previous = CirclePoint(200f, 400f)
        repeat(200) {
            val c = Circles.randomCenter(400f, 800f, 50f, previous, random)
            val distance = kotlin.math.hypot(c.x - previous.x, c.y - previous.y)
            assertTrue("trop près : $distance", distance >= 50f * 2.5f)
        }
    }

    @Test
    fun laPartieNeFinitQuQuandToutLeMondeAJoue() {
        var game = CirclesGame(players = 3)
        assertEquals(0, game.current)
        assertFalse(game.isOver)
        game = game.finishRun(4)
        assertEquals(1, game.current)
        game = game.finishRun(7).finishRun(2)
        assertTrue(game.isOver)
        assertEquals(listOf(4, 7, 2), game.streaks)
    }

    @Test
    fun laPlusLongueSerieGagne() {
        val game = CirclesGame(players = 3).finishRun(4).finishRun(9).finishRun(2)
        assertEquals(9, game.best)
        assertEquals(listOf(1), game.winners)
        assertEquals(listOf(1, 0, 2), game.ranking())
    }

    @Test
    fun alEgaliteLesJoueursSontExAequo() {
        val game = CirclesGame(players = 3).finishRun(5).finishRun(3).finishRun(5)
        assertEquals(listOf(0, 2), game.winners)
    }

    @Test
    fun personneNeGagneSiAucunRondNEstTouche() {
        val game = CirclesGame(players = 2).finishRun(0).finishRun(0)
        assertTrue(game.isOver)
        assertTrue(game.winners.isEmpty())
    }

    @Test
    fun pasDeGagnantTantQueLaPartieContinue() {
        val game = CirclesGame(players = 2).finishRun(6)
        assertTrue(game.winners.isEmpty())
    }

    @Test
    fun uneSerieNegativeEstRamenee0() {
        assertEquals(listOf(0), CirclesGame(players = 1).finishRun(-3).streaks)
    }

    @Test
    fun unJoueurSeulAUneSeriePourSeBattre() {
        val game = CirclesGame(players = 1).finishRun(12)
        assertTrue(game.isOver)
        assertEquals(listOf(0), game.winners)
    }

    @Test
    fun uneSerieDeTropEstIgnoree() {
        val game = CirclesGame(players = 1).finishRun(3).finishRun(8)
        assertEquals(listOf(3), game.streaks)
    }

    @Test
    fun uneNouvellePartieGardeLesJoueursEtEfface() {
        val next = CirclesGame(players = 2).finishRun(3).finishRun(4).newGame()
        assertEquals(2, next.players)
        assertTrue(next.streaks.isEmpty())
        assertEquals(0, next.current)
    }

    @Test
    fun laSerieDUnJoueurQuiNAPasJoueEstInconnue() {
        val game = CirclesGame(players = 3).finishRun(2)
        assertEquals(2, game.streakOf(0))
        assertEquals(null, game.streakOf(1))
    }

    // --- Niveau difficile : les têtes de mort ---

    @Test
    fun auNiveauNormalIlNYAJamaisDeTeteDeMort() {
        assertEquals(0, Circles.skullCount(CirclesLevel.NORMAL, 0))
        assertEquals(0, Circles.skullCount(CirclesLevel.NORMAL, 500))
    }

    @Test
    fun auNiveauDifficileLesTetesDeMortArriventApresQuelquesRonds() {
        assertEquals(0, Circles.skullCount(CirclesLevel.HARD, 0))
        assertEquals(0, Circles.skullCount(CirclesLevel.HARD, Circles.SKULLS_FROM_STREAK - 1))
        assertEquals(1, Circles.skullCount(CirclesLevel.HARD, Circles.SKULLS_FROM_STREAK))
    }

    @Test
    fun lesTetesDeMortSontDePlusEnPlusNombreusesMaisPlafonnees() {
        val second = Circles.SKULLS_FROM_STREAK + Circles.SKULLS_STEP
        assertEquals(1, Circles.skullCount(CirclesLevel.HARD, second - 1))
        assertEquals(2, Circles.skullCount(CirclesLevel.HARD, second))
        assertEquals(Circles.MAX_SKULLS, Circles.skullCount(CirclesLevel.HARD, 1_000))
    }

    @Test
    fun toucherUneTeteDeMortSeFaitDansSonRayonSansMarge() {
        val skull = CirclePoint(100f, 100f)
        assertTrue(Circles.isSkullHit(100f, 100f, skull, 30f))
        assertTrue(Circles.isSkullHit(130f, 100f, skull, 30f))
        assertFalse(Circles.isSkullHit(131f, 100f, skull, 30f))
    }

    @Test
    fun lesTetesDeMortRestentDansLaZoneLoinDuRondEtLesUnesDesAutres() {
        val width = 1_000f
        val height = 1_600f
        val maxRadius = 100f
        val skullRadius = maxRadius * Circles.SKULL_RADIUS_FRACTION
        val random = Random(42)
        repeat(300) {
            val circle = Circles.randomCenter(width, height, maxRadius, random = random)
            val skulls = Circles.randomSkulls(width, height, circle, maxRadius, skullRadius, Circles.MAX_SKULLS, random)
            skulls.forEach { skull ->
                assertTrue(skull.x - skullRadius >= 0f && skull.x + skullRadius <= width)
                assertTrue(skull.y - skullRadius >= 0f && skull.y + skullRadius <= height)
                // Jamais de contact avec le rond (à son rayon de départ) : on ne touche pas l'un en visant l'autre.
                assertTrue(kotlin.math.hypot(skull.x - circle.x, skull.y - circle.y) >= maxRadius + skullRadius)
            }
            skulls.forEachIndexed { i, a ->
                skulls.drop(i + 1).forEach { b ->
                    assertTrue(kotlin.math.hypot(a.x - b.x, a.y - b.y) >= 2f * skullRadius)
                }
            }
        }
    }

    @Test
    fun surUnGrandEcranOnPlaceToutesLesTetesDeMortDemandees() {
        val skulls = Circles.randomSkulls(1_000f, 1_600f, CirclePoint(500f, 800f), 100f, 80f, 3, Random(7))
        assertEquals(3, skulls.size)
    }

    @Test
    fun pasDeTeteDeMortQuandOnNEnDemandePasOuQueLaZoneEstTropPetite() {
        val circle = CirclePoint(50f, 50f)
        assertTrue(Circles.randomSkulls(1_000f, 1_000f, circle, 100f, 80f, 0).isEmpty())
        assertTrue(Circles.randomSkulls(100f, 100f, circle, 100f, 80f, 3).isEmpty())
    }

    @Test
    fun leNiveauSeRetrouveParSonNumeroEtLeRecordEstAPartAuNiveauDifficile() {
        assertEquals(CirclesLevel.NORMAL, CirclesLevel.fromIndex(0))
        assertEquals(CirclesLevel.HARD, CirclesLevel.fromIndex(1))
        // Un numéro qui ne désigne rien (préférence abîmée) redonne le niveau normal.
        assertEquals(CirclesLevel.NORMAL, CirclesLevel.fromIndex(99))
        assertEquals(CirclesLevel.NORMAL, CirclesLevel.fromIndex(-1))
        assertEquals(CIRCLES_ID, CirclesLevel.NORMAL.recordId)
        assertTrue(CirclesLevel.HARD.recordId != CIRCLES_ID)
    }
}
