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

    /** Le nombre de bulles du niveau dessiné [level]. */
    private fun handmadeBubbleCount(level: Int): Int = Bubble.handmade(level)!!.sumOf { row -> row.count { it != '.' } }

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
        assertEquals(handmadeBubbleCount(2), game.bubbleCount)
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

    // --- Les niveaux dessinés à la main ---

    @Test
    fun lesNiveauxDessinesSontValides() {
        assertTrue(Bubble.HANDMADE.isNotEmpty())
        for (number in 1..Bubble.HANDMADE.size) {
            val pattern = Bubble.handmade(number)!!
            assertTrue("niveau $number : trop de rangées", pattern.size <= Bubble.rowsFor(30))
            pattern.forEach { row ->
                assertEquals("niveau $number : une rangée n'a pas ${Bubble.COLS} cases", Bubble.COLS, row.length)
                assertTrue("niveau $number : caractère inconnu", row.all { it == '.' || it in '0'..'5' })
            }
            val game = emptyGame()
            game.loadLevel(number)
            assertEquals(handmadeBubbleCount(number), game.bubbleCount)
            assertTrue("niveau $number : couleur trop haute", colorsOnBoard(game).all { it < Bubble.colorsFor(number) })
            // Aucune bulle ne flotte : elles tiennent toutes au plafond, directement ou par d'autres.
            assertEquals("niveau $number : des bulles flottent", 0, game.dropFloating())
            // La première rangée est pleine : le tableau s'accroche.
            for (c in 0 until Bubble.COLS) assertEquals(pattern[0][c] != '.', game.colorAt(0, c) != BubbleGame.EMPTY)
        }
    }

    @Test
    fun apresLesNiveauxDessinesLesTableauxSontTiresAuHasard() {
        val game = emptyGame()
        val number = Bubble.HANDMADE.size + 1
        game.loadLevel(number)
        assertEquals(Bubble.rowsFor(number) * Bubble.COLS, game.bubbleCount)
    }

    // --- Les modes ---

    @Test
    fun enModeZenLePlafondNeDescendJamais() {
        val game = newGame()
        game.start(BubbleMode.ZEN)
        game.clearBoard()
        // Dix tirs manqués (des couleurs en alternance : aucun groupe) : au classique, le plafond serait descendu.
        for (i in 0 until 10) game.settle(0, i, i % 2)
        assertEquals(10, game.bubbleCount)
        assertEquals(0, game.shotsSinceDrop)
        assertFalse(game.isShifted(0))
    }

    @Test
    fun leDefiDuJourDonneLeMemeDepartPourLaMemeGraine() {
        assertEquals(20261114, Bubble.dailySeed(2026, 11, 14))
        val a = BubbleGame(Random(1))
        val b = BubbleGame(Random(99))
        a.start(BubbleMode.DAILY, 20261114)
        b.start(BubbleMode.DAILY, 20261114)
        assertEquals(BubbleMode.DAILY, a.mode)
        assertEquals(a.current, b.current)
        assertEquals(a.next, b.next)
        // Même un tableau tiré au hasard (au-delà des niveaux dessinés) est le même.
        a.loadLevel(Bubble.HANDMADE.size + 2)
        b.loadLevel(Bubble.HANDMADE.size + 2)
        for (r in 0 until Bubble.MAX_ROWS) {
            for (c in 0 until Bubble.COLS) assertEquals(a.colorAt(r, c), b.colorAt(r, c))
        }
        // Recommencer le même jour redonne le même départ.
        val first = a.current
        a.reset()
        assertEquals(first, a.current)
    }

    @Test
    fun lesNumerosDeModeInconnusDonnentLeClassique() {
        assertEquals(BubbleMode.ZEN, BubbleMode.fromIndex(BubbleMode.ZEN.ordinal))
        assertEquals(BubbleMode.CLASSIC, BubbleMode.fromIndex(99))
        assertEquals(BubbleMode.CLASSIC, BubbleMode.fromIndex(-1))
    }

    // --- Les échanges ---

    @Test
    fun lesEchangesSontLimitesEtSeGagnentEnFaisantPartirDesBulles() {
        val game = emptyGame()
        assertEquals(Bubble.SWAPS_START, game.swapCharges)
        assertTrue(game.swap())
        assertEquals(0, game.swapCharges)
        assertFalse(game.swap())
        game.place(0, 9, 5) // garde le tableau non vide
        for (i in 0 until 5) {
            game.place(0, 0, 0)
            game.place(0, 1, 0)
            game.settle(0, 2, 0)
            assertEquals(3, game.lastPopped)
            assertEquals(minOf(i + 1, Bubble.SWAPS_MAX), game.swapCharges)
        }
        assertEquals(Bubble.SWAPS_MAX, game.swapCharges)
    }

    // --- Les bulles spéciales ---

    @Test
    fun lesBullesSpecialesSeDebloquentAvecLesNiveaux() {
        assertTrue(Bubble.unlockedSpecials(1).isEmpty())
        assertEquals(listOf(Bubble.BOMB), Bubble.unlockedSpecials(2))
        assertEquals(listOf(Bubble.BOMB, Bubble.RAINBOW), Bubble.unlockedSpecials(3))
        assertEquals(listOf(Bubble.BOMB, Bubble.RAINBOW, Bubble.LIGHTNING), Bubble.unlockedSpecials(4))
        assertFalse(Bubble.isSpecial(Bubble.PALETTE - 1))
        assertTrue(Bubble.isSpecial(Bubble.BOMB))
    }

    @Test
    fun uneJaugePleineFaitArriverUneBulleSpecialeMaisPasAuNiveauUn() {
        val game = emptyGame()
        game.loadLevel(3)
        game.clearBoard()
        game.setSpecialCharge(Bubble.SPECIAL_THRESHOLD)
        game.setQueue(0, 1)
        game.settle(0, 0, 0)
        assertTrue(game.next in listOf(Bubble.BOMB, Bubble.RAINBOW))

        val first = emptyGame()
        first.setSpecialCharge(Bubble.SPECIAL_THRESHOLD)
        first.setQueue(0, 1)
        first.settle(0, 0, 0)
        assertFalse(Bubble.isSpecial(first.next))
    }

    @Test
    fun laBombeFaitEclaterToutesSesVoisines() {
        val game = emptyGame()
        // La case (1, 5), en rangée décalée, a pour voisines (0, 5), (0, 6), (1, 4), (1, 6), (2, 5) et (2, 6).
        game.place(0, 5, 0)
        game.place(0, 6, 1)
        game.place(1, 4, 2)
        game.place(1, 6, 3)
        game.place(2, 5, 4)
        game.place(2, 6, 0)
        game.place(0, 0, 5) // loin de la bombe, et accrochée au plafond
        game.settle(1, 5, Bubble.BOMB)
        assertEquals(6, game.lastPopped)
        assertEquals(0, game.lastFallen)
        assertEquals(6 * Bubble.POP_POINTS, game.score)
        assertEquals(1, game.bubbleCount)
        assertEquals(BubbleGame.EMPTY, game.colorAt(1, 5)) // la bombe ne reste pas
        assertEquals(1, game.lastEffects.count { it.color == Bubble.BOMB })
        assertEquals(6, game.lastEffects.count { it.color != Bubble.BOMB && !it.falling })
    }

    @Test
    fun lEclairVideToutesLaRangeeOuIlSePose() {
        val game = emptyGame()
        game.place(0, 0, 0)
        game.place(1, 0, 1)
        game.place(2, 0, 2)
        game.place(2, 3, 3)
        game.place(2, 5, 4)
        game.place(0, 7, 5)
        game.settle(2, 8, Bubble.LIGHTNING)
        assertEquals(3, game.lastPopped)
        assertEquals(3 * Bubble.POP_POINTS, game.score)
        assertEquals(3, game.bubbleCount)
        for (c in 0 until Bubble.COLS) assertEquals(BubbleGame.EMPTY, game.colorAt(2, c))
    }

    @Test
    fun lArcEnCielPrendLaCouleurQuiFaitLePlusGrosGroupe() {
        val game = emptyGame()
        game.place(0, 0, 1)
        game.place(0, 1, 1)
        game.place(2, 0, 3)
        game.place(0, 9, 5) // garde le tableau non vide
        // Posé en (1, 0) : en couleur 1 il fait un groupe de trois avec (0, 0) et (0, 1), en couleur 3 un groupe de deux avec (2, 0).
        game.settle(1, 0, Bubble.RAINBOW)
        assertEquals(3, game.lastPopped)
        // La bulle (2, 0), n'étant plus tenue par (1, 0), tombe.
        assertEquals(1, game.lastFallen)
        assertEquals(3 * Bubble.POP_POINTS + Bubble.fallPoints(1), game.score)
    }

    @Test
    fun lArcEnCielSansVoisinePrendUneCouleurDuTableau() {
        val game = emptyGame()
        game.place(0, 9, 4)
        game.settle(0, 2, Bubble.RAINBOW)
        val color = game.colorAt(0, 2)
        assertTrue(color in 0 until Bubble.PALETTE)
        assertEquals(2, game.bubbleCount)
    }

    @Test
    fun leLanceurNeChargeJamaisUneCouleurAbsenteDuTableauApresDesTirsAuHasard() {
        val random = Random(11)
        val game = newGame(11)
        var shots = 0
        while (!game.isOver && shots < 150) {
            if (!Bubble.isSpecial(game.current)) assertTrue(game.current in colorsOnBoard(game))
            game.shoot(Bubble.MIN_ANGLE + random.nextFloat() * (Bubble.MAX_ANGLE - Bubble.MIN_ANGLE))
            game.tick(5000)
            shots++
        }
        assertTrue(shots > 0)
    }

    // --- Le guide ---

    @Test
    fun leGuideLongMontreAuMoinsAutantDeRebondsQueLeCourt() {
        val game = emptyGame()
        val none = game.trajectory(0.5f, 0)
        val short = game.trajectory(0.5f, 2)
        val long = game.trajectory(0.5f, 5)
        assertEquals(3, none.size) // le lanceur, le premier rebond, puis la fin du tracé
        assertTrue(short.size >= none.size)
        assertTrue(long.size >= short.size)
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
    fun laSauvegardeGardeLeModeLesEchangesEtLaBulleEnVol() {
        val game = newGame(5)
        game.start(BubbleMode.ZEN)
        game.swap()
        assertTrue(game.shoot(1.2f))
        game.tick(100) // deux unités de vol : la bulle est encore en l'air
        assertTrue(game.isFlying)
        val copy = BubbleGame.restore(game.save())
        assertNotNull(copy)
        copy!!
        assertEquals(BubbleMode.ZEN, copy.mode)
        assertEquals(game.swapCharges, copy.swapCharges)
        assertTrue(copy.isFlying)
        assertEquals(game.flightX, copy.flightX, 1e-6f)
        assertEquals(game.flightY, copy.flightY, 1e-6f)
        // Les deux terminent le même vol et posent la bulle au même endroit.
        game.tick(5000)
        copy.tick(5000)
        assertFalse(copy.isFlying)
        for (r in 0 until Bubble.MAX_ROWS) {
            for (c in 0 until Bubble.COLS) assertEquals(game.colorAt(r, c), copy.colorAt(r, c))
        }
    }

    @Test
    fun uneSauvegardeAvecUnVolImpossibleEstRefusee() {
        val data = newGame().save()
        // Un vol annoncé, mais sans direction : la bulle n'ira nulle part.
        assertNull(BubbleGame.restore(data.toMutableList().also { it[12] = 1 }))
        // Un mode qui n'existe pas.
        assertNull(BubbleGame.restore(data.toMutableList().also { it[8] = 7 }))
        // Une ancienne version de la sauvegarde.
        assertNull(BubbleGame.restore(data.toMutableList().also { it[0] = 1 }))
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
