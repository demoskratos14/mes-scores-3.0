package com.aventure.messcores

import kotlin.math.sqrt
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleShooterTest {

    private fun newGame(seed: Int = 1) = BubbleGame(Random(seed))

    /** Une partie dont le tableau est vidé : on y pose les bulles voulues, sans le hasard du départ. */
    private fun emptyGame(seed: Int = 1) = newGame(seed).also { it.clearBoard() }

    private fun colorsOnBoard(game: BubbleGame): Set<Int> =
        (0 until Bubble.MAX_ROWS).flatMap { r -> (0 until Bubble.COLS).map { c -> game.colorAt(r, c) } }
            .filter { it != BubbleGame.EMPTY }
            .toSet()

    // --- Le départ ---

    @Test
    fun leDepartPoseLesRangeesDuNiveauUnEtLaisseLeResteVide() {
        val game = newGame()
        val rows = Bubble.rowsFor(1)
        for (r in 0 until Bubble.MAX_ROWS) {
            for (c in 0 until Bubble.COLS) {
                val color = game.colorAt(r, c)
                if (r < rows) assertTrue(color in 0 until Bubble.colorsFor(1)) else assertEquals(BubbleGame.EMPTY, color)
            }
        }
        assertEquals(rows * Bubble.COLS, game.bubbleCount)
        assertEquals(1, game.level)
        assertEquals(0, game.score)
        assertFalse(game.isOver)
        assertFalse(game.isFlying)
    }

    @Test
    fun leLanceurNeChargeQueDesCouleursPresentesSurLeTableau() {
        val game = newGame()
        val present = colorsOnBoard(game)
        assertTrue(game.current in present)
        assertTrue(game.next in present)
    }

    @Test
    fun lesReglagesSuiventLeNiveau() {
        assertEquals(4, Bubble.colorsFor(1))
        assertEquals(4, Bubble.colorsFor(2))
        assertEquals(5, Bubble.colorsFor(3))
        assertEquals(6, Bubble.colorsFor(5))
        assertEquals(6, Bubble.colorsFor(40))
        assertEquals(5, Bubble.rowsFor(1))
        assertEquals(9, Bubble.rowsFor(5))
        assertEquals(9, Bubble.rowsFor(30))
        assertEquals(7, Bubble.shotsPerDrop(1))
        assertEquals(6, Bubble.shotsPerDrop(3))
        assertEquals(5, Bubble.shotsPerDrop(5))
        assertEquals(4, Bubble.shotsPerDrop(7))
        assertEquals(4, Bubble.shotsPerDrop(50))
    }

    // --- La visée ---

    @Test
    fun laViseeResteDansLesAnglesPermis() {
        assertEquals(Bubble.MIN_ANGLE, Bubble.clampAngle(0f), 1e-6f)
        assertEquals(Bubble.MAX_ANGLE, Bubble.clampAngle(4f), 1e-6f)
        assertEquals(1f, Bubble.clampAngle(1f), 1e-6f)
        // Tout droit au-dessus du lanceur.
        assertEquals(Bubble.START_ANGLE, Bubble.angleTo(0f, 1f), 1e-4f)
        // Un doigt à la hauteur du lanceur ou plus bas vise le plus plat possible, du bon côté.
        assertEquals(Bubble.MIN_ANGLE, Bubble.angleTo(1f, 0f), 1e-6f)
        assertEquals(Bubble.MIN_ANGLE, Bubble.angleTo(1f, -1f), 1e-6f)
        assertEquals(Bubble.MAX_ANGLE, Bubble.angleTo(-1f, 0f), 1e-6f)
        assertEquals(Bubble.MAX_ANGLE, Bubble.angleTo(-1f, -1f), 1e-6f)
    }

    @Test
    fun uneRangeeSurDeuxEstDecaleeEtLesVoisinesSeToucheExactement() {
        val game = newGame()
        assertFalse(game.isShifted(0))
        assertTrue(game.isShifted(1))
        assertEquals(0.5f, game.centerX(0, 0), 1e-6f)
        assertEquals(1.0f, game.centerX(1, 0), 1e-6f)
        assertEquals(Bubble.ROW_H, game.centerY(1) - game.centerY(0), 1e-6f)
        // Deux bulles voisines de deux rangées se touchent : leurs centres sont à un diamètre l'un de l'autre.
        val dx = game.centerX(1, 0) - game.centerX(0, 0)
        val dy = game.centerY(1) - game.centerY(0)
        assertEquals(1f, sqrt(dx * dx + dy * dy), 1e-4f)
    }

    @Test
    fun leGuideVaToutDroitSurUnTableauVide() {
        val game = emptyGame()
        val path = game.trajectory(Bubble.START_ANGLE)
        assertEquals(2, path.size)
        assertEquals(Bubble.SHOOTER_X, path.first().first, 1e-4f)
        assertEquals(Bubble.SHOOTER_Y, path.first().second, 1e-4f)
        assertEquals(Bubble.SHOOTER_X, path.last().first, 1e-3f)
        assertEquals(Bubble.RADIUS, path.last().second, 1e-3f)
    }

    @Test
    fun leGuideRebonditSurLesMursSansJamaisLesDepasser() {
        val game = emptyGame()
        val path = game.trajectory(0.5f)
        assertTrue(path.size >= 3)
        path.forEach {
            assertTrue(it.first >= Bubble.RADIUS - 1e-3f)
            assertTrue(it.first <= Bubble.WIDTH - Bubble.RADIUS + 1e-3f)
        }
    }

    @Test
    fun leGuideS_arreteContreUneBulle() {
        val game = emptyGame()
        // Rangée 3 (décalée) : sa bulle de la colonne 5 est centrée en x = 6, y ≈ 3,1 ; le tir droit passe à 0,75 d'elle.
        game.place(3, 5, 0)
        val last = game.trajectory(Bubble.START_ANGLE).last()
        assertTrue(last.second in 3.4f..3.7f)
    }

    // --- Les tirs ---

    @Test
    fun unTirDroitSurTableauVideSePoseAuPlafondLaOuIlVise() {
        val game = emptyGame()
        game.place(0, 0, 4) // de quoi garder la couleur 4 « présente » pour la bulle suivante
        game.setQueue(3, 4)
        assertTrue(game.shoot(Bubble.START_ANGLE))
        assertTrue(game.isFlying)
        game.tick(2000)
        assertFalse(game.isFlying)
        // Le lanceur est à x = 5,25 : la case la plus proche au plafond est la colonne 5 (centre en 5,5).
        assertEquals(3, game.colorAt(0, 5))
        assertEquals(2, game.bubbleCount)
        assertEquals(4, game.current)
    }

    @Test
    fun onNePeutPasTirerPendantUnTirNiApresLaFin() {
        val game = newGame()
        assertTrue(game.shoot(Bubble.START_ANGLE))
        assertFalse(game.shoot(Bubble.START_ANGLE))
        assertFalse(game.swap())
        game.tick(5000)
        assertFalse(game.isFlying)
        assertTrue(game.swap())
    }

    @Test
    fun leTirTouchantTroisBullesDeMemeCouleurLesFaitEclater() {
        val game = emptyGame()
        game.place(0, 0, 1)
        game.place(0, 4, 0)
        game.place(0, 5, 0)
        game.setQueue(0, 0)
        game.shoot(Bubble.START_ANGLE)
        game.tick(3000)
        // La bulle se pose en rangée 1, colonne 4, contre les deux bulles de la rangée du dessus : trois bulles rouges, elles éclatent.
        assertEquals(3, game.lastPopped)
        assertEquals(0, game.lastFallen)
        assertEquals(30, game.score)
        assertEquals(1, game.bubbleCount)
        assertEquals(1, game.level)
        assertEquals(0, game.shotsSinceDrop)
    }

    @Test
    fun unGroupeDeDeuxNEclatePasEtCompteCommeUnTirManque() {
        val game = emptyGame()
        game.place(0, 0, 0)
        game.settle(0, 1, 0)
        assertEquals(0, game.lastPopped)
        assertEquals(0, game.score)
        assertEquals(2, game.bubbleCount)
        assertEquals(1, game.shotsSinceDrop)
    }

    @Test
    fun lesBullesQuiNeTiennentPlusAuPlafondTombent() {
        val game = emptyGame()
        game.place(0, 5, 0)
        game.place(0, 4, 0)
        game.place(1, 5, 1) // n'est tenue que par la bulle (0, 5) : la colonne 6 de la rangée 0 est vide
        game.place(0, 0, 2) // garde le tableau non vide, accrochée au plafond
        game.settle(0, 3, 0)
        assertEquals(3, game.lastPopped)
        assertEquals(1, game.lastFallen)
        assertEquals(3 * Bubble.POP_POINTS + Bubble.FALL_POINTS, game.score)
        assertEquals(1, game.bubbleCount)
        assertEquals(2, game.colorAt(0, 0))
    }

    @Test
    fun uneBulleTenueParUneAutreChaineRestePosee() {
        val game = emptyGame()
        game.place(0, 0, 0)
        game.place(1, 0, 1) // (1, 0) est décalée : elle touche (0, 0) et (0, 1)
        game.place(2, 0, 2) // (2, 0) touche (1, 0) : tenue par la chaîne
        assertEquals(0, game.dropFloating())
        assertEquals(3, game.bubbleCount)
    }

    // --- Le plafond, le niveau, la fin ---

    @Test
    fun lePlafondDescendApresAssezDeTirsManques() {
        val game = emptyGame()
        val misses = Bubble.shotsPerDrop(1)
        // Des couleurs en alternance, jamais trois de suite : aucun tir ne fait rien éclater.
        for (i in 0 until misses) game.settle(0, i, i % 2)
        assertEquals(misses + Bubble.COLS, game.bubbleCount)
        assertEquals(0, game.shotsSinceDrop)
        // La rangée neuve est pleine, et les bulles posées ont glissé d'une rangée sans changer de place.
        for (c in 0 until Bubble.COLS) assertTrue(game.colorAt(0, c) != BubbleGame.EMPTY)
        for (i in 0 until misses) assertEquals(i % 2, game.colorAt(1, i))
        assertTrue(game.isShifted(0))
        assertFalse(game.isShifted(1))
        assertFalse(game.isOver)
    }

    @Test
    fun uneBulleSurLaLigneRougeFinitLaPartie() {
        val game = emptyGame()
        game.settle(Bubble.LOSE_ROW, 3, 0)
        assertTrue(game.isOver)
        assertFalse(game.shoot(1f))
        assertFalse(game.swap())
    }

    @Test
    fun viderLeTableauDonneUnBonusEtLeNiveauSuivant() {
        val game = emptyGame()
        game.place(0, 0, 0)
        game.place(0, 1, 0)
        game.settle(0, 2, 0)
        assertEquals(2, game.level)
        assertEquals(3 * Bubble.POP_POINTS + Bubble.LEVEL_BONUS, game.score)
        assertFalse(game.isOver)
        assertEquals(Bubble.rowsFor(2) * Bubble.COLS, game.bubbleCount)
        assertEquals(0, game.shotsSinceDrop)
        assertTrue(colorsOnBoard(game).all { it < Bubble.colorsFor(2) })
        assertTrue(game.current in colorsOnBoard(game))
    }

    @Test
    fun uneNouvellePartieRepartDeZero() {
        val game = newGame()
        game.settle(Bubble.LOSE_ROW, 3, 0)
        assertTrue(game.isOver)
        game.reset()
        assertFalse(game.isOver)
        assertEquals(0, game.score)
        assertEquals(1, game.level)
        assertEquals(Bubble.rowsFor(1) * Bubble.COLS, game.bubbleCount)
    }

    @Test
    fun onPeutEchangerLaBulleDuLanceurEtLaSuivante() {
        val game = newGame()
        game.setQueue(1, 2)
        assertTrue(game.swap())
        assertEquals(2, game.current)
        assertEquals(1, game.next)
    }

    @Test
    fun desTirsAuHasardNeFontJamaisPlanterLeJeu() {
        val random = Random(3)
        val game = newGame(3)
        var shots = 0
        while (!game.isOver && shots < 300) {
            val angle = Bubble.MIN_ANGLE + random.nextFloat() * (Bubble.MAX_ANGLE - Bubble.MIN_ANGLE)
            assertTrue(game.shoot(angle))
            game.tick(5000)
            assertFalse(game.isFlying)
            assertTrue(game.bubbleCount <= Bubble.MAX_ROWS * Bubble.COLS)
            assertTrue(game.score >= 0)
            shots++
        }
        assertTrue(shots > 0)
    }

    @Test
    fun lesChutesRapportentDePlusEnPlusParBulle() {
        assertEquals(0, Bubble.fallPoints(0))
        assertEquals(20, Bubble.fallPoints(1))
        assertEquals(60, Bubble.fallPoints(2))
        assertEquals(140, Bubble.fallPoints(3))
        assertEquals(300, Bubble.fallPoints(4))
        // Le doublement s'arrête au maximum : au-delà, chaque bulle de plus vaut ce maximum.
        assertEquals(Bubble.FALL_POINTS_MAX, Bubble.fallPoints(20) - Bubble.fallPoints(19))
    }

    @Test
    fun uneGrappeQuiTombeRapporteLeBaremeProgressif() {
        val game = emptyGame()
        game.place(0, 5, 0)
        game.place(0, 4, 0)
        game.place(1, 5, 1) // tenue par (0, 5) seulement
        game.place(2, 5, 1) // tenue par (1, 5) : elle tombe avec elle
        game.place(0, 0, 2)
        game.settle(0, 3, 0)
        assertEquals(2, game.lastFallen)
        assertEquals(3 * Bubble.POP_POINTS + Bubble.fallPoints(2), game.score)
        assertEquals(30 + 60, game.score)
    }

    @Test
    fun leTirNoteLesBullesPartiesPourLAnimation() {
        val game = emptyGame()
        game.place(0, 5, 0)
        game.place(0, 4, 0)
        game.place(1, 5, 1)
        game.place(0, 0, 2)
        assertEquals(0, game.shotCount)
        game.settle(0, 3, 0)
        assertEquals(1, game.shotCount)
        val effects = game.lastEffects
        assertEquals(4, effects.size)
        assertEquals(3, effects.count { !it.falling })
        val fallen = effects.single { it.falling }
        assertEquals(1, fallen.color)
        // La bulle est notée là où elle se trouvait.
        assertEquals(game.centerX(1, 5), fallen.x, 1e-6f)
        assertEquals(game.centerY(1), fallen.y, 1e-6f)
        // Un tir manqué ne fait partir aucune bulle.
        game.settle(0, 1, 2)
        assertEquals(2, game.shotCount)
        assertTrue(game.lastEffects.isEmpty())
    }

    // --- La sauvegarde ---

    @Test
    fun laSauvegardeRedonneLaMemePartie() {
        val game = newGame(7)
        game.settle(Bubble.rowsFor(1), 0, 0)
        val data = game.save()
        val copy = BubbleGame.restore(data)
        assertNotNull(copy)
        copy!!
        assertEquals(data, copy.save())
        assertEquals(game.level, copy.level)
        assertEquals(game.score, copy.score)
        assertEquals(game.current, copy.current)
        assertEquals(game.next, copy.next)
        assertEquals(game.shotsSinceDrop, copy.shotsSinceDrop)
        for (r in 0 until Bubble.MAX_ROWS) {
            for (c in 0 until Bubble.COLS) assertEquals(game.colorAt(r, c), copy.colorAt(r, c))
        }
    }

    @Test
    fun desDonneesQuiNeSontPasUnePartieSontRefusees() {
        val data = newGame().save()
        assertNull(BubbleGame.restore(emptyList()))
        assertNull(BubbleGame.restore(data.drop(1)))
        assertNull(BubbleGame.restore(data.toMutableList().also { it[0] = 99 }))
        assertNull(BubbleGame.restore(data.toMutableList().also { it[data.size - 1] = 99 }))
        assertNull(BubbleGame.restore(data.toMutableList().also { it[6] = -1 }))
    }
}
