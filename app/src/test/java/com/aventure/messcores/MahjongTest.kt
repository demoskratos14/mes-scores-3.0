package com.aventure.messcores

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MahjongTest {

    /** Une partie reprise d'un état rangé à la main : seuls les emplacements de [kinds] (emplacement -> sorte de tuile) sont occupés. */
    private fun crafted(level: MahjongLevel, kinds: Map<Int, Int>): MahjongGame {
        val size = level.layout.size
        val data = ArrayList<Int>()
        data.addAll(listOf(level.ordinal, MahjongGame.PLAYING, 0, 0, 0, -1))
        for (i in 0 until size) data.add(kinds[i] ?: 0)
        for (i in 0 until size) data.add(if (i in kinds) 1 else 0)
        data.add(0)
        return MahjongGame.restore(data)
    }

    /** Joue la partie au hasard : à chaque coup, une paire tirée parmi celles qu'on peut retirer, jusqu'à la victoire ou l'impasse. */
    private fun playRandomly(game: MahjongGame, random: Random) {
        while (game.state == MahjongGame.PLAYING) {
            val free = (0 until game.size).filter { game.isFree(it) }
            val moves = ArrayList<Pair<Int, Int>>()
            for (a in free.indices) {
                for (b in a + 1 until free.size) {
                    if (MahjongTiles.canMatch(game.kindAt(free[a]), game.kindAt(free[b]))) moves.add(free[a] to free[b])
                }
            }
            if (moves.isEmpty()) return
            val (a, b) = moves[random.nextInt(moves.size)]
            game.tap(a)
            game.tap(b)
        }
    }

    /** Vrai si l'une des [attempts] parties jouées au hasard à partir de la donne de [game] est gagnée (la donne a donc une solution). */
    private fun canBeWon(game: MahjongGame, attempts: Int): Boolean {
        val saved = game.save()
        repeat(attempts) { n ->
            val copy = MahjongGame.restore(saved)
            playRandomly(copy, Random(n))
            if (copy.state == MahjongGame.WON) return true
        }
        return false
    }

    private fun aliveKinds(game: MahjongGame): List<Int> =
        (0 until game.size).filter { !game.isRemoved(it) }.map { game.kindAt(it) }.sorted()

    // ---------- Les plateaux ----------

    @Test
    fun layoutsHaveTheAnnouncedTileCounts() {
        for (level in MahjongLevel.entries) {
            assertEquals(level.name, level.tileCount, level.layout.size)
            assertEquals(level.name, 0, level.tileCount % 2)
        }
        assertEquals(62, MahjongLevel.EASY.tileCount)
        assertEquals(104, MahjongLevel.MEDIUM.tileCount)
        assertEquals(144, MahjongLevel.HARD.tileCount)
    }

    @Test
    fun turtleHasTheClassicLayers() {
        val perLayer = MahjongLevel.HARD.layout.spots.groupingBy { it.z }.eachCount()
        assertEquals(mapOf(0 to 87, 1 to 36, 2 to 16, 3 to 4, 4 to 1), perLayer)
    }

    @Test
    fun tilesOfOneLayerNeverOverlap() {
        for (level in MahjongLevel.entries) {
            val spots = level.layout.spots
            for (a in spots.indices) {
                for (b in a + 1 until spots.size) {
                    if (spots[a].z != spots[b].z) continue
                    val apart = abs(spots[a].x - spots[b].x) >= 2 || abs(spots[a].y - spots[b].y) >= 2
                    assertTrue("${level.name} : ${spots[a]} et ${spots[b]} se chevauchent", apart)
                }
            }
        }
    }

    @Test
    fun everyUpperTileRestsOnSomethingBelow() {
        for (level in MahjongLevel.entries) {
            val spots = level.layout.spots
            for (spot in spots.filter { it.z > 0 }) {
                val supported = spots.any {
                    it.z == spot.z - 1 && abs(it.x - spot.x) <= 1 && abs(it.y - spot.y) <= 1
                }
                assertTrue("${level.name} : $spot flotte dans le vide", supported)
            }
        }
    }

    @Test
    fun drawOrderGoesFromTheGroundUpAndCoversEveryTile() {
        for (level in MahjongLevel.entries) {
            val layout = level.layout
            assertEquals((0 until layout.size).toList(), layout.drawOrder.sorted())
            val layers = layout.drawOrder.map { layout.spots[it].z }
            assertEquals(layers.sorted(), layers)
        }
    }

    // ---------- Les tuiles ----------

    @Test
    fun fullSetHasOneHundredFortyFourTiles() {
        val pairs = MahjongTiles.allPairs()
        assertEquals(72, pairs.size)
        val counts = IntArray(MahjongTiles.KINDS)
        for ((a, b) in pairs) {
            assertTrue(MahjongTiles.canMatch(a, b))
            counts[a]++
            counts[b]++
        }
        for (kind in 0 until MahjongTiles.FLOWERS) assertEquals("sorte $kind", 4, counts[kind])
        for (kind in MahjongTiles.FLOWERS until MahjongTiles.KINDS) assertEquals("sorte $kind", 1, counts[kind])
    }

    @Test
    fun flowersMatchEachOtherAndSeasonsMatchEachOther() {
        assertTrue(MahjongTiles.canMatch(MahjongTiles.FLOWERS, MahjongTiles.FLOWERS + 3))
        assertTrue(MahjongTiles.canMatch(MahjongTiles.SEASONS + 1, MahjongTiles.SEASONS + 2))
        assertFalse(MahjongTiles.canMatch(MahjongTiles.FLOWERS, MahjongTiles.SEASONS))
        assertTrue(MahjongTiles.canMatch(MahjongTiles.DOTS + 2, MahjongTiles.DOTS + 2))
        assertFalse(MahjongTiles.canMatch(MahjongTiles.DOTS + 2, MahjongTiles.BAMBOO + 2))
        assertFalse(MahjongTiles.canMatch(MahjongTiles.WINDS, MahjongTiles.WINDS + 1))
    }

    // ---------- Les tuiles libres ----------

    @Test
    fun freeTilesFollowTheCoverAndSideRules() {
        // Plateau facile : 8 × 5 au sol (emplacement = rangée × 8 + colonne), 6 × 3 à l'étage 1 (de 40 à 57), 4 au sommet (de 58 à 61).
        val game = MahjongGame(MahjongLevel.EASY, Random(1))
        assertTrue("le coin du sol est libre", game.isFree(0))
        assertFalse("une tuile entre deux voisines est bloquée", game.isFree(1))
        assertFalse("une tuile sous une autre est bloquée", game.isFree(2 * 8 + 3))
        assertTrue("la première tuile du sommet a son côté gauche dégagé", game.isFree(58))
        assertFalse("la deuxième est bloquée des deux côtés", game.isFree(59))
        assertTrue("la dernière a son côté droit dégagé", game.isFree(61))
    }

    @Test
    fun theSingleTopTileOfTheTurtleIsFree() {
        val game = MahjongGame(MahjongLevel.HARD, Random(2))
        assertTrue(game.isFree(game.size - 1))
        val wingLeft = MahjongLevel.HARD.layout.spots.indexOf(MahjongSpot(0, 7, 0))
        assertTrue("la tuile de côté à gauche est libre", game.isFree(wingLeft))
        val blockedByWing = MahjongLevel.HARD.layout.spots.indexOf(MahjongSpot(2, 6, 0))
        assertFalse("la tuile voisine de la tuile de côté est bloquée des deux côtés", game.isFree(blockedByWing))
    }

    // ---------- La donne ----------

    @Test
    fun dealUsesWholePairsOfTheFullSet() {
        for (level in MahjongLevel.entries) {
            val game = MahjongGame(level, Random(3))
            val counts = IntArray(MahjongTiles.KINDS)
            for (i in 0 until game.size) counts[game.kindAt(i)]++
            for (kind in 0 until MahjongTiles.FLOWERS) {
                assertTrue("${level.name} : sorte $kind en ${counts[kind]} exemplaires", counts[kind] % 2 == 0 && counts[kind] <= 4)
            }
            assertEquals(0, (counts[MahjongTiles.FLOWERS] + counts[MahjongTiles.FLOWERS + 1] + counts[MahjongTiles.FLOWERS + 2] + counts[MahjongTiles.FLOWERS + 3]) % 2)
            assertEquals(0, (counts[MahjongTiles.SEASONS] + counts[MahjongTiles.SEASONS + 1] + counts[MahjongTiles.SEASONS + 2] + counts[MahjongTiles.SEASONS + 3]) % 2)
            assertEquals(level.tileCount, counts.sum())
        }
    }

    @Test
    fun everyLevelDealCanBeWon() {
        for (level in MahjongLevel.entries) {
            for (seed in listOf(7, 8)) {
                val game = MahjongGame(level, Random(seed))
                assertTrue("${level.name}, graine $seed : donne sans solution trouvée", canBeWon(game, 1500))
            }
        }
    }

    @Test
    fun winningEndsTheGameWithAScore() {
        val game = MahjongGame(MahjongLevel.EASY, Random(11))
        val saved = game.save()
        var won: MahjongGame? = null
        for (n in 0 until 1500) {
            val copy = MahjongGame.restore(saved)
            playRandomly(copy, Random(n))
            if (copy.state == MahjongGame.WON) {
                won = copy
                break
            }
        }
        val finished = won
        assertNotNull(finished)
        assertEquals(0, finished!!.remaining)
        assertTrue(finished.isFinished)
        assertTrue(finished.score >= MahjongLevel.EASY.tileCount)
        // Une partie finie ne bouge plus.
        assertEquals(MahjongGame.TAP_IGNORED, finished.tap(0))
        assertFalse(finished.undo())
        assertFalse(finished.shuffle())
    }

    // ---------- Les gestes ----------

    @Test
    fun tappingSelectsThenMatchesAPair() {
        val game = crafted(MahjongLevel.EASY, (0 until 62).associateWith { 0 })
        assertEquals(MahjongGame.TAP_SELECTED, game.tap(0))
        assertEquals(0, game.selected)
        assertEquals(MahjongGame.TAP_MATCHED, game.tap(7))
        assertEquals(-1, game.selected)
        assertEquals(60, game.remaining)
        assertTrue(game.isRemoved(0))
        assertTrue(game.isRemoved(7))
    }

    @Test
    fun tappingAnotherTileMovesTheChoiceAndTappingTwiceCancelsIt() {
        val kinds = (0 until 62).associateWith { 0 }.toMutableMap()
        kinds[7] = 1
        val game = crafted(MahjongLevel.EASY, kinds)
        assertEquals(MahjongGame.TAP_SELECTED, game.tap(0))
        assertEquals("sorte différente : le choix change", MahjongGame.TAP_SELECTED, game.tap(7))
        assertEquals(7, game.selected)
        assertEquals(62, game.remaining)
        assertEquals(MahjongGame.TAP_UNSELECTED, game.tap(7))
        assertEquals(-1, game.selected)
    }

    @Test
    fun blockedAndRemovedTilesDoNothing() {
        val game = crafted(MahjongLevel.EASY, (0 until 62).associateWith { 0 })
        assertEquals(MahjongGame.TAP_BLOCKED, game.tap(1))
        assertEquals(-1, game.selected)
        assertEquals(MahjongGame.TAP_SELECTED, game.tap(0))
        assertEquals(MahjongGame.TAP_MATCHED, game.tap(7))
        assertEquals(MahjongGame.TAP_IGNORED, game.tap(0))
        assertEquals(MahjongGame.TAP_IGNORED, game.tap(-1))
        assertEquals(MahjongGame.TAP_IGNORED, game.tap(62))
    }

    @Test
    fun tappingAFlowerThenAnotherFlowerRemovesThem() {
        val kinds = (0 until 62).associateWith { 0 }.toMutableMap()
        kinds[0] = MahjongTiles.FLOWERS
        kinds[7] = MahjongTiles.FLOWERS + 2
        val game = crafted(MahjongLevel.EASY, kinds)
        assertEquals(MahjongGame.TAP_SELECTED, game.tap(0))
        assertEquals(MahjongGame.TAP_MATCHED, game.tap(7))
    }

    @Test
    fun removingATileFreesTheOnesItBlocked() {
        val game = crafted(MahjongLevel.EASY, (0 until 62).associateWith { 0 })
        assertFalse(game.isFree(1))
        game.tap(0)
        game.tap(7)
        assertTrue("la voisine de droite n'est plus bloquée à gauche", game.isFree(1))
        assertTrue(game.isFree(6))
    }

    // ---------- Annuler, indice, mélange ----------

    @Test
    fun undoPutsTheLastPairBack() {
        val game = crafted(MahjongLevel.EASY, (0 until 62).associateWith { 0 })
        assertFalse(game.canUndo)
        assertFalse(game.undo())
        game.tap(0)
        game.tap(7)
        assertTrue(game.canUndo)
        assertTrue(game.undo())
        assertEquals(62, game.remaining)
        assertFalse(game.isRemoved(0))
        assertFalse(game.isRemoved(7))
        assertFalse(game.canUndo)
    }

    @Test
    fun hintShowsAFreeMatchingPairAndCostsPoints() {
        val game = MahjongGame(MahjongLevel.MEDIUM, Random(5))
        assertEquals(0, game.hints)
        // Une donne neuve a toujours une première paire à retirer.
        val pair = game.hint()
        assertNotNull(pair)
        assertTrue(game.isFree(pair!!.first))
        assertTrue(game.isFree(pair.second))
        assertTrue(pair.first != pair.second)
        assertTrue(MahjongTiles.canMatch(game.kindAt(pair.first), game.kindAt(pair.second)))
        assertEquals(1, game.hints)
    }

    @Test
    fun hintPrefersTheChosenTile() {
        val kinds = (0 until 62).associateWith { 0 }.toMutableMap()
        kinds[0] = 5
        kinds[7] = 5
        val game = crafted(MahjongLevel.EASY, kinds)
        game.tap(0)
        val pair = game.hint()
        assertEquals(0 to 7, pair)
    }

    @Test
    fun hintIsNullWhenNoPairIsLeft() {
        // Quatre tuiles en rangée : les deux du milieu sont bloquées, les deux du bout sont de sortes différentes.
        val game = crafted(MahjongLevel.EASY, mapOf(0 to 0, 1 to 1, 2 to 0, 3 to 1))
        assertFalse(game.hasMove())
        assertNull(game.hint())
        assertEquals(0, game.hints)
    }

    @Test
    fun shuffleDealsTheRemainingTilesAgainAndKeepsTheGameWinnable() {
        val game = crafted(MahjongLevel.EASY, mapOf(0 to 0, 1 to 1, 2 to 0, 3 to 1))
        val before = aliveKinds(game)
        assertFalse(game.hasMove())
        assertTrue(game.shuffle())
        assertEquals(before, aliveKinds(game))
        assertEquals(1, game.shuffles)
        assertTrue(game.hasMove())
        assertFalse("on ne peut plus annuler avant un mélange", game.canUndo)
        assertTrue(canBeWon(game, 50))
    }

    @Test
    fun shuffleOfTwoStackedTilesLosesTheGame() {
        // La tuile (1, 1) du sol (emplacement 9) porte la tuile (1, 1) de l'étage 1 (emplacement 40) : aucune des deux ne peut jamais être retirée.
        val game = crafted(MahjongLevel.EASY, mapOf(9 to 0, 40 to 0))
        assertFalse(game.hasMove())
        assertFalse(game.shuffle())
        assertEquals(MahjongGame.LOST, game.state)
        assertTrue(game.isFinished)
        assertEquals(0, game.score)
    }

    // ---------- Le score ----------

    @Test
    fun scoreRewardsSpeedAndPenalisesHelp() {
        // Niveau difficile : 144 tuiles, temps de référence de 8 s par paire, soit 576 s.
        assertEquals(1440 + 576 * 2, Mahjong.scoreFor(MahjongLevel.HARD, 0, 0, 0))
        assertEquals(1440, Mahjong.scoreFor(MahjongLevel.HARD, 576, 0, 0))
        assertEquals(1440, Mahjong.scoreFor(MahjongLevel.HARD, 5000, 0, 0))
        assertEquals(1440 - 2 * Mahjong.HINT_COST - Mahjong.SHUFFLE_COST, Mahjong.scoreFor(MahjongLevel.HARD, 600, 2, 1))
        // Niveau facile : 31 paires, 248 s de référence.
        assertEquals(620 + (248 - 100) * 2 - 50, Mahjong.scoreFor(MahjongLevel.EASY, 100, 1, 0))
    }

    @Test
    fun aWonGameAlwaysScoresAtLeastOnePointPerTile() {
        assertEquals(MahjongLevel.HARD.tileCount, Mahjong.scoreFor(MahjongLevel.HARD, 100_000, 100, 100))
        assertEquals(MahjongLevel.EASY.tileCount, Mahjong.scoreFor(MahjongLevel.EASY, 100_000, 100, 100))
    }

    @Test
    fun recordsAreKeptPerLevel() {
        val keys = MahjongLevel.entries.map { Mahjong.recordKey(it) }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals("builtin_mahjong_hard", Mahjong.recordKey(MahjongLevel.HARD))
    }

    // ---------- Le temps ----------

    @Test
    fun timerCountsOnlyWhileThePlayIsOn() {
        val game = MahjongGame(MahjongLevel.EASY, Random(4))
        game.tick(2500)
        assertEquals(2, game.seconds)
        game.tick(-100)
        assertEquals(2500L, game.elapsedMs)
        game.reset()
        assertEquals(0L, game.elapsedMs)
    }

    // ---------- Rotation de l'écran ----------

    @Test
    fun savedGameComesBackIdentical() {
        val game = MahjongGame(MahjongLevel.MEDIUM, Random(6))
        playRandomly(game, Random(9))
        val copy = MahjongGame.restore(game.save())
        assertEquals(game.level, copy.level)
        assertEquals(game.state, copy.state)
        assertEquals(game.remaining, copy.remaining)
        assertEquals(game.hints, copy.hints)
        assertEquals(game.shuffles, copy.shuffles)
        assertEquals(game.elapsedMs, copy.elapsedMs)
        assertEquals(game.canUndo, copy.canUndo)
        for (i in 0 until game.size) {
            assertEquals(game.isRemoved(i), copy.isRemoved(i))
            if (!game.isRemoved(i)) assertEquals(game.kindAt(i), copy.kindAt(i))
        }
    }

    @Test
    fun savedGameKeepsItsChosenTileAndItsHistory() {
        val game = crafted(MahjongLevel.EASY, (0 until 62).associateWith { 0 })
        game.tap(0)
        game.tap(7)
        game.tick(4200)
        game.tap(6)
        val copy = MahjongGame.restore(game.save())
        assertEquals(6, copy.selected)
        assertEquals(4, copy.seconds)
        assertTrue(copy.undo())
        assertEquals(62, copy.remaining)
    }

    @Test
    fun invalidSavesGiveAFreshGame() {
        for (data in listOf<List<Int>>(emptyList(), listOf(1, 2, 3), listOf(99, 0, 0, 0, 0, -1))) {
            val game = MahjongGame.restore(data)
            assertEquals(game.size, game.remaining)
            assertEquals(MahjongGame.PLAYING, game.state)
        }
        // Un nombre impair de tuiles d'une sorte (une seule tuile de la sorte 5 reste) : la partie ne pourrait pas se finir,
        // la sauvegarde est refusée et on repart d'une partie neuve de 62 tuiles (acceptée, elle n'en aurait que 3).
        val oddKinds = ArrayList<Int>()
        oddKinds.addAll(listOf(MahjongLevel.EASY.ordinal, MahjongGame.PLAYING, 0, 0, 0, -1))
        for (i in 0 until 62) oddKinds.add(if (i == 0) 5 else 0)
        for (i in 0 until 62) oddKinds.add(if (i < 3) 1 else 0)
        oddKinds.add(0)
        assertEquals(62, MahjongGame.restore(oddKinds).remaining)
    }
}
