package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La Chasse à l'émoji : difficulté, plateaux, déplacements, toucher et score d'une séance à plusieurs téléphones. */
class EmojiHuntTest {

    private val allSymbols = EmojiHunt.FAMILIES.flatten()

    // ---------- Les dessins ----------

    @Test
    fun chaqueDessinNeFaitPartieQueD_uneFamille() {
        assertEquals(allSymbols.size, allSymbols.toSet().size)
        assertTrue(EmojiHunt.FAMILIES.all { it.size >= 5 })
    }

    @Test
    fun lesFamillesRegroupentLesDessinsQuiSeRessemblent() {
        fun familyOf(symbol: String) = EmojiHunt.FAMILIES.single { symbol in it }
        // Un visage qui sourit et un autre qui fait un clin d'œil ; la France et les Pays-Bas.
        assertTrue("😉" in familyOf("😊"))
        assertTrue("🇳🇱" in familyOf("🇫🇷"))
    }

    // ---------- La difficulté ----------

    @Test
    fun lesPiegesSontPeuNombreuxAuDebutPuisAugmententJusqu_AuPlafond() {
        assertEquals(3, EmojiHunt.level(1).decoys)
        val decoys = (1..20).map { EmojiHunt.level(it).decoys }
        assertEquals(decoys.sorted(), decoys)
        assertEquals(EmojiHunt.MAX_DECOYS, EmojiHunt.level(50).decoys)
        // Un niveau absurde est ramené au premier.
        assertEquals(EmojiHunt.level(1), EmojiHunt.level(-4))
    }

    @Test
    fun leTempsDiminueEnMemeTempsQueLesPiegesAugmentent() {
        assertTrue(EmojiHunt.level(2).timeLimitMs < EmojiHunt.level(1).timeLimitMs)
        assertTrue(EmojiHunt.level(2).decoys > EmojiHunt.level(1).decoys)
        val times = (1..30).map { EmojiHunt.level(it).timeLimitMs }
        assertEquals(times.sortedDescending(), times)
        assertEquals(EmojiHunt.MIN_TIME_MS, EmojiHunt.level(99).timeLimitMs)
    }

    @Test
    fun lesDessinsNeBougentQuePassesLesPremiersNiveaux() {
        for (level in 1 until EmojiHunt.MOVE_FROM_LEVEL) assertFalse(EmojiHunt.level(level).moving)
        assertTrue(EmojiHunt.level(EmojiHunt.MOVE_FROM_LEVEL).moving)
        val speeds = (EmojiHunt.MOVE_FROM_LEVEL..40).map { EmojiHunt.level(it).speed }
        assertEquals(speeds.sorted(), speeds)
        assertTrue(speeds.last() > speeds.first())
    }

    @Test
    fun plusLeNiveauMonteEtPlusLesPiegesRessemblentAuDessinRecherche() {
        assertTrue(EmojiHunt.level(8).similarShare > EmojiHunt.level(1).similarShare)
        assertTrue(EmojiHunt.level(99).similarShare <= 1f)
    }

    // ---------- Les plateaux ----------

    @Test
    fun lePlateauNeDependQueDeLaGraineEtDuNiveau() {
        val a = EmojiHunt.generate(42L, 5)
        val b = EmojiHunt.generate(42L, 5)
        assertEquals(a.items, b.items)
        assertEquals(a.targetIndex, b.targetIndex)
        assertNotEquals(a.items, EmojiHunt.generate(43L, 5).items)
    }

    @Test
    fun leDessinRechercheEstUniqueEtLeNombreDePiegesEstCelDuNiveau() {
        for (level in listOf(1, 2, 5, 9, 14, 30)) {
            for (seed in 1L..20L) {
                val board = EmojiHunt.generate(seed, level)
                val target = board.target.symbol
                assertEquals(EmojiHunt.level(level).decoys + 1, board.items.size)
                assertEquals("niveau $level, graine $seed", 1, board.items.count { it.symbol == target })
                assertEquals(target, board.items[board.targetIndex].symbol)
            }
        }
    }

    @Test
    fun lesPiegesRessemblantsSontDeLaMemeFamilleQueLeDessinRecherche() {
        for (level in listOf(1, 4, 8, 12)) {
            for (seed in 1L..20L) {
                val board = EmojiHunt.generate(seed, level)
                val settings = board.level
                val family = EmojiHunt.FAMILIES.single { board.target.symbol in it }
                val similar = board.items.count { it.symbol in family } - 1
                val wanted = minOf(family.size - 1, Math.round(settings.decoys * settings.similarShare))
                assertEquals("niveau $level, graine $seed", wanted, similar)
            }
        }
    }

    @Test
    fun auDebutLesDessinsNeSeRecouvrentPas() {
        for (level in 1 until EmojiHunt.MOVE_FROM_LEVEL) {
            for (seed in 1L..20L) {
                val gap = EmojiHunt.minStartGap(EmojiHunt.generate(seed, level))
                assertTrue("niveau $level, graine $seed : $gap", gap >= 0.5f)
            }
        }
    }

    @Test
    fun lesDessinsFixesNeBougentPas() {
        val board = EmojiHunt.generate(7L, 2)
        for (index in board.items.indices) {
            assertEquals(board.positionAt(index, 0L), board.positionAt(index, 5_000L))
        }
    }

    @Test
    fun lesDessinsMobilesBougentEtRestentDansLEcran() {
        val board = EmojiHunt.generate(7L, 12)
        var moved = false
        for (index in board.items.indices) {
            for (t in 0L..20_000L step 250L) {
                val (x, y) = board.positionAt(index, t)
                assertTrue("x=$x", x in 0f..1f)
                assertTrue("y=$y", y in 0f..1f)
            }
            if (board.positionAt(index, 0L) != board.positionAt(index, 2_000L)) moved = true
        }
        assertTrue(moved)
        // Le même instant donne la même position : tous les téléphones voient la même chose.
        assertEquals(board.positionAt(3, 1_234L), EmojiHunt.generate(7L, 12).positionAt(3, 1_234L))
    }

    @Test
    fun leReboundRenvoieLeDessinVersLeCentre() {
        assertEquals(0.5f, EmojiHunt.bounce(0.5f, 0f), 0.0001f)
        assertEquals(0.6f, EmojiHunt.bounce(0.5f, 0.1f), 0.0001f)
        // Parti vers le bord droit, il repart dans l'autre sens.
        assertEquals(0.86f, EmojiHunt.bounce(0.9f, 0.1f), 0.0001f)
        assertEquals(0.17f, EmojiHunt.bounce(0.07f, -0.1f), 0.0001f)
        for (distance in -30..30) {
            val position = EmojiHunt.bounce(0.4f, distance / 7f)
            assertTrue(position in 0.07f..0.93f)
        }
    }

    // ---------- Toucher ----------

    @Test
    fun toucherUnDessinLeDesigne() {
        val board = EmojiHunt.generate(11L, 3)
        val (x, y) = board.positionAt(board.targetIndex, 0L)
        assertEquals(board.targetIndex, board.hit(x, y, 0L, reachX = 0.04f, reachY = 0.04f))
        assertEquals(-1, board.hit(x, y, 0L, reachX = 0.04f, reachY = 0.04f, ignored = setOf(board.targetIndex)))
    }

    @Test
    fun toucherLeVideNeDesigneRien() {
        val board = EmojiHunt.generate(11L, 1)
        assertEquals(-1, board.hit(-1f, -1f, 0L, 0.04f, 0.04f))
        assertEquals(-1, board.hit(0.5f, 0.5f, 0L, 0f, 0f))
    }

    @Test
    fun quandPlusieursDessinsSeRecouvrentLePlusProcheGagne() {
        val level = EmojiHunt.level(1)
        val board = EmojiBoard(
            level,
            listOf(EmojiItem("😀", 0.5f, 0.5f, 0f, 0f), EmojiItem("😃", 0.55f, 0.5f, 0f, 0f)),
            targetIndex = 0, columns = 1, rows = 1
        )
        assertEquals(1, board.hit(0.54f, 0.5f, 0L, 0.2f, 0.2f))
        assertEquals(0, board.hit(0.51f, 0.5f, 0L, 0.2f, 0.2f))
    }

    @Test
    fun leTempsDUnJoueurInclutLesPenalites() {
        assertEquals(800, EmojiHunt.resultMs(800L, 0))
        assertEquals(1_800, EmojiHunt.resultMs(800L, 1))
        assertEquals(2_300, EmojiHunt.resultMs(300L, 2))
        assertEquals(1, EmojiHunt.resultMs(0L, 0))
        assertEquals(EmojiHunt.MAX_RESULT_MS, EmojiHunt.resultMs(10_000_000L, 0))
    }

    @Test
    fun uneErreurRetireUneSecondeAuTempsRestant() {
        val level = EmojiHunt.level(1)
        assertEquals(level.timeLimitMs - 2_000L, EmojiHunt.remainingMs(level, 2_000L, 0))
        assertEquals(level.timeLimitMs - 3_000L, EmojiHunt.remainingMs(level, 2_000L, 1))
        assertTrue(EmojiHunt.remainingMs(level, level.timeLimitMs, 0) <= 0L)
    }

    // ---------- La séance à plusieurs téléphones ----------

    @Test
    fun lePlusRapideGagneLaManche() {
        val next = EmojiGame.of(3).resolve(listOf(900, 650, 1_200))!!
        assertEquals(listOf(0, 1, 0), next.wins)
        assertEquals(1, next.lastWinner)
        assertEquals(650, next.lastTimeMs)
        assertFalse(next.lastDraw)
        assertTrue(next.roundPlayed)
    }

    @Test
    fun celuiQuiNAPasTrouveNeGagnePasMaisLeDernierTrouveurSi() {
        val next = EmojiGame.of(3).resolve(listOf(EmojiHunt.MISS, 5_000, EmojiHunt.MISS))!!
        assertEquals(1, next.lastWinner)
        assertEquals(listOf(0, 1, 0), next.wins)
    }

    @Test
    fun egaliteAuMeilleurTempsOuPersonneNeTrouveLaMancheEstNulle() {
        val tie = EmojiGame.of(3).resolve(listOf(700, 700, 900))!!
        assertTrue(tie.lastDraw)
        assertEquals(listOf(0, 0, 0), tie.wins)

        val nobody = EmojiGame.of(2).resolve(listOf(EmojiHunt.MISS, EmojiHunt.MISS))!!
        assertTrue(nobody.lastDraw)
        assertEquals(EmojiGame.NONE, nobody.lastWinner)
        assertTrue(nobody.roundPlayed)
        // Une manche nulle ne compte dans aucune moyenne.
        assertEquals(listOf(0, 0), nobody.timedRounds)
    }

    @Test
    fun lePremierAuNombreDeManchesGagneLaPartie() {
        var game = EmojiGame.of(2)
        repeat(EmojiHunt.TARGET - 1) { game = game.resolve(listOf(500, 800))!! }
        assertFalse(game.isOver)
        game = game.resolve(listOf(500, 800))!!
        assertTrue(game.isOver)
        assertEquals(0, game.winner)
        // Plus de manche une fois la partie finie.
        assertNull(game.resolve(listOf(500, 800)))
        assertEquals(EmojiGame.of(2), game.newMatch())
    }

    @Test
    fun resolveRefuseUnNombreDeResultatsQuiNeCorrespondPasAuxJoueurs() {
        assertNull(EmojiGame.of(3).resolve(listOf(500, 800)))
        assertNull(EmojiGame.of(1).resolve(listOf(500)))
    }

    @Test
    fun laMoyenneNeCompteQueLesTempsTrouves() {
        var game = EmojiGame.of(2)
        game = game.resolve(listOf(400, 900))!!
        game = game.resolve(listOf(600, EmojiHunt.MISS))!!
        assertEquals(500, game.averageMsOf(0))
        assertEquals(900, game.averageMsOf(1))
        assertTrue(game.hasAverages)
        assertNull(EmojiGame.of(2).averageMsOf(0))
    }

    @Test
    fun unJoueurQuiPartLaisseLesAutresAvecLeurScore() {
        val game = EmojiGame.of(3).resolve(listOf(500, 800, 900))!!.removePlayer(0)
        assertEquals(2, game.players)
        assertEquals(listOf(0, 0), game.wins)
        assertFalse(game.roundPlayed)
        assertEquals(game, game.removePlayer(7))
    }
}
