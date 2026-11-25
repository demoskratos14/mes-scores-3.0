package com.aventure.messcores

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TowerDefenseTest {

    private fun newGame(seed: Int = 1) = TdGame(Random(seed), seed)

    /** Une case libre voisine du chemin (côté droit ou gauche) : on y construit sans se soucier du hasard. */
    private fun freeCellNearPath(game: TdGame): Pair<Int, Int> {
        for (cell in game.map.path) {
            val row = cell / TdMap.COLS
            val col = cell % TdMap.COLS
            for (c in listOf(col - 1, col + 1)) if (game.map.isBuildable(row, c)) return Pair(row, c)
        }
        throw AssertionError("aucune case libre")
    }

    // ---- Cartes ----

    @Test
    fun uneCarteEstValideQuelleQueSoitLaGraine() {
        for (seed in 0 until 300) {
            val map = TdMap.generate(seed)
            val path = map.path
            assertEquals("entrée en haut (graine $seed)", 0, path.first() / TdMap.COLS)
            assertEquals("sortie en bas (graine $seed)", TdMap.ROWS - 1, path.last() / TdMap.COLS)
            assertEquals("cases toutes différentes (graine $seed)", path.size, path.toSet().size)
            for (i in 0 until path.size - 1) {
                val a = path[i]
                val b = path[i + 1]
                val step = Math.abs(a / TdMap.COLS - b / TdMap.COLS) + Math.abs(a % TdMap.COLS - b % TdMap.COLS)
                assertEquals("cases voisines (graine $seed, rang $i)", 1, step)
            }
            assertTrue("chemin assez long (graine $seed)", path.size >= 25)
            // Aucun décor sur le chemin, et des places pour les tours.
            assertTrue(path.all { map.decor[it] < 0 })
            val buildable = (0 until TdMap.ROWS).sumOf { r -> (0 until TdMap.COLS).count { c -> map.isBuildable(r, c) } }
            assertTrue("assez de places (graine $seed) : $buildable", buildable >= 50)
        }
    }

    @Test
    fun uneMemeGraineDonneLaMemeCarte() {
        assertEquals(TdMap.generate(7).path.toList(), TdMap.generate(7).path.toList())
        assertEquals(TdMap.generate(7).decor.toList(), TdMap.generate(7).decor.toList())
        assertFalse(TdMap.generate(7).path.toList() == TdMap.generate(8).path.toList())
    }

    @Test
    fun lesTuilesSontCellesDuBonCote() {
        val map = TdMap.generate(3)
        // Marais : une des deux tuiles unies ; chemin : jamais une tuile de marais.
        for (r in 0 until TdMap.ROWS) for (c in 0 until TdMap.COLS) {
            val tile = map.tileIndex(r, c)
            assertTrue(tile in 0 until 56)
            if (map.isPath(r, c)) assertFalse("chemin sur du marais en ($r, $c)", tile in TdTiles.SWAMP)
            else assertTrue(tile in TdTiles.SWAMP)
        }
        // Une case de chemin entourée de chemin a la tuile toute claire, dans les deux thèmes.
        assertEquals(511, TdTiles.SIGNATURES[TdTiles.best(511)])
        assertEquals(511, TdTiles.SIGNATURES_SUMMER[TdTiles.best(511, TdMap.THEME_SUMMER)])
        // Une case de fond n'a pas de chemin : l'une des deux tuiles unies.
        assertEquals(0, TdTiles.SIGNATURES[TdTiles.SWAMP[0]])
        assertEquals(0, TdTiles.SIGNATURES_SUMMER[TdTiles.SWAMP[1]])
        assertEquals(56, TdTiles.SIGNATURES.size)
        assertEquals(56, TdTiles.SIGNATURES_SUMMER.size)
    }

    @Test
    fun lesDeuxThemesSontTiresSelonLaGraine() {
        val themes = (0 until 300).map { TdMap.generate(it).theme }
        assertTrue(themes.all { it == TdMap.THEME_SWAMP || it == TdMap.THEME_SUMMER })
        assertTrue("les deux thèmes sortent", themes.count { it == TdMap.THEME_SWAMP } in 80..220)
        assertEquals(TdMap.generate(42).theme, TdMap.generate(42).theme)
        // Chaque thème donne des tuiles valides pour toutes ses cases.
        for (seed in 0 until 40) {
            val map = TdMap.generate(seed)
            for (r in 0 until TdMap.ROWS) for (c in 0 until TdMap.COLS) assertTrue(map.tileIndex(r, c) in 0 until 56)
        }
    }

    @Test
    fun unEnnemiQuiArriveALaBaseFaitUnFlashNoir() {
        val game = newGame()
        assertEquals(0f, game.baseFlashMs, 0f)
        game.startWave()
        // Un ennemi tout près de la sortie.
        val e = TdEnemy(77, TdEnemyKind.GOBLIN, 10f)
        e.dist = (game.map.path.size - 1) - 0.01f
        game.enemies.add(e)
        game.tick(50)
        assertEquals(TowerDefense.START_LIVES - 1, game.lives)
        assertTrue(game.baseFlashMs > 0f)
        // Le flash s'éteint.
        repeat(20) { game.tick(50) }
        assertEquals(0f, game.baseFlashMs, 0f)
    }

    @Test
    fun uneMortEtUnImpactLaissentUnEffetPourLEcran() {
        val game = newGame()
        val tower = defend(game, TdTowerKind.WOODEN, TowerDefense.MAX_LEVEL)
        val enemy = TdEnemy(5, TdEnemyKind.GOBLIN, 1f)
        val tx = tower.cell % TdMap.COLS + 0.5f
        val ty = tower.cell / TdMap.COLS + 0.5f
        var bestDist = 0f
        var best = Float.MAX_VALUE
        var d = 0f
        while (d < game.map.path.size - 1) {
            enemy.dist = d
            val dd = Math.hypot((game.enemyX(enemy) - tx).toDouble(), (game.enemyY(enemy) - ty).toDouble()).toFloat()
            if (dd < best) { best = dd; bestDist = d }
            d += 0.1f
        }
        enemy.dist = bestDist
        game.enemies.add(enemy)
        var sawImpact = false
        var sawDeath = false
        repeat(40) {
            game.tick(30)
            if (game.flashes.any { !it.death }) sawImpact = true
            if (game.flashes.any { it.death }) sawDeath = true
        }
        assertTrue(sawImpact)
        assertTrue(sawDeath)
        // Les effets finissent par s'éteindre.
        repeat(40) { game.tick(30) }
        assertTrue(game.flashes.isEmpty())
    }

    @Test
    fun laSignatureDUneCaseDeChemin() {
        val map = TdMap.generate(5)
        // Le centre est toujours clair, et une case hors chemin n'a aucun point clair.
        for (cell in map.path) assertTrue(map.tileSignature(cell / TdMap.COLS, cell % TdMap.COLS) and (1 shl 4) != 0)
        assertEquals(0, map.tileSignature(0, if (map.entryCol == 0) 2 else 0))
    }

    // ---- Construire, améliorer, vendre ----

    @Test
    fun onConstruitSeulementSurUneCaseLibreEtAvecAssezDArgent() {
        val game = newGame()
        val (row, col) = freeCellNearPath(game)
        val pathCell = game.map.path[3]
        assertFalse(game.build(pathCell / TdMap.COLS, pathCell % TdMap.COLS, TdTowerKind.WOODEN))
        assertTrue(game.build(row, col, TdTowerKind.WOODEN))
        assertEquals(TowerDefense.START_GOLD - TdTowerKind.WOODEN.cost, game.gold)
        // Case prise.
        assertFalse(game.build(row, col, TdTowerKind.WOODEN))
        // Pas assez d'argent : 100 pièces restantes, une tour de brique en coûte 90, puis plus assez.
        val other = (0 until TdMap.ROWS * TdMap.COLS).first { it != row * TdMap.COLS + col && game.map.isBuildable(it / TdMap.COLS, it % TdMap.COLS) }
        assertTrue(game.build(other / TdMap.COLS, other % TdMap.COLS, TdTowerKind.BRICK))
        val third = (0 until TdMap.ROWS * TdMap.COLS).first { game.towerAt(it) == null && game.map.isBuildable(it / TdMap.COLS, it % TdMap.COLS) }
        assertFalse(game.build(third / TdMap.COLS, third % TdMap.COLS, TdTowerKind.BRICK))
    }

    @Test
    fun ameliorerCoutePlusCherEtVendreRendUnePart() {
        val game = newGame()
        val (row, col) = freeCellNearPath(game)
        game.build(row, col, TdTowerKind.WOODEN)
        val tower = game.towerAt(row * TdMap.COLS + col)!!
        assertEquals(TdTowerKind.WOODEN.cost, game.upgradeCost(tower))
        assertEquals(TdTowerKind.WOODEN.cost * 2, TowerDefense.upgradeCost(TdTowerKind.WOODEN, 2))
        val before = game.gold
        assertTrue(game.upgrade(tower))
        assertEquals(2, tower.level)
        assertEquals(before - TdTowerKind.WOODEN.cost, game.gold)
        assertTrue(TowerDefense.damageOf(tower.kind, 2) > TowerDefense.damageOf(tower.kind, 1))
        assertTrue(TowerDefense.rangeOf(tower.kind, 2) > TowerDefense.rangeOf(tower.kind, 1))
        // Vente : 60 % de ce qui a été dépensé (50 + 50).
        val gold = game.gold
        assertEquals(60, game.sellValue(tower))
        assertTrue(game.sell(tower))
        assertEquals(gold + 60, game.gold)
        assertNull(game.towerAt(row * TdMap.COLS + col))
    }

    @Test
    fun uneTourAuNiveauMaximumNeSAmelioreePlus() {
        val game = newGame()
        val (row, col) = freeCellNearPath(game)
        game.build(row, col, TdTowerKind.WOODEN)
        val tower = game.towerAt(row * TdMap.COLS + col)!!
        tower.level = TowerDefense.MAX_LEVEL
        assertNull(game.upgradeCost(tower))
        assertFalse(game.upgrade(tower))
    }

    // ---- Vagues ----

    @Test
    fun lesVaguesGrossissentEtLeChefArriveToutesLesCinq() {
        assertTrue(TowerDefense.waveEnemies(2).size > TowerDefense.waveEnemies(1).size)
        assertEquals(setOf(TdEnemyKind.GOBLIN), TowerDefense.waveEnemies(1).toSet())
        assertFalse(TowerDefense.waveEnemies(4).contains(TdEnemyKind.CHIEF))
        assertEquals(1, TowerDefense.waveEnemies(5).count { it == TdEnemyKind.CHIEF })
        assertEquals(2, TowerDefense.waveEnemies(10).count { it == TdEnemyKind.CHIEF })
        assertTrue(TowerDefense.waveEnemies(1000).size <= 40 + 1000 / 5)
        assertTrue(TowerDefense.hpFactor(10) > TowerDefense.hpFactor(2))
        assertEquals(1f, TowerDefense.hpFactor(1), 0f)
    }

    @Test
    fun lesEnnemisSortentUnParUnPuisArriventEtFontPerdreDesVies() {
        val game = newGame()
        assertTrue(game.startWave())
        assertFalse(game.startWave()) // une vague est déjà en cours
        game.tick(10)
        assertEquals(1, game.enemies.size)
        assertEquals(TowerDefense.waveEnemies(1).size - 1, game.pending)
        // Sans aucune tour, tout le monde finit par arriver.
        repeat(2000) { game.tick(100) }
        assertEquals(0, game.enemies.size)
        assertEquals(TowerDefense.START_LIVES - TowerDefense.waveEnemies(1).size, game.lives)
        assertFalse(game.waveActive)
    }

    @Test
    fun laPartieSArreteALaDerniereVie() {
        val game = newGame()
        // Sans tour, les vagues s'enchaînent jusqu'à la dernière vie.
        var guard = 0
        while (!game.isOver && guard++ < 50) {
            game.startWave()
            repeat(5000) { if (!game.isOver) game.tick(100) }
        }
        assertTrue(game.isOver)
        assertEquals(0, game.lives)
        val score = game.score
        game.tick(1000)
        assertEquals(score, game.score)
        assertFalse(game.startWave())
    }

    // ---- Combat ----

    private fun defend(game: TdGame, kind: TdTowerKind, level: Int = 1): TdTower {
        // Une tour posée au bord du chemin, sans payer : on teste le combat, pas l'économie.
        val (row, col) = freeCellNearPath(game)
        game.build(row, col, kind)
        return game.towerAt(row * TdMap.COLS + col)!!.also { it.level = level }
    }

    @Test
    fun uneTourTueDesEnnemisEtRapporteArgentEtPoints() {
        val game = newGame()
        game.startWave()
        // Une grosse tour par case libre près du chemin : la vague 1 ne passe pas.
        val spots = ArrayList<Int>()
        for (cell in game.map.path) {
            val row = cell / TdMap.COLS
            val col = cell % TdMap.COLS
            for (c in listOf(col - 1, col + 1)) if (game.map.isBuildable(row, c) && (row * TdMap.COLS + c) !in spots) spots.add(row * TdMap.COLS + c)
        }
        for (spot in spots.take(10)) {
            game.towers.add(TdTower(spot, TdTowerKind.WOODEN, TowerDefense.MAX_LEVEL))
        }
        val goldBefore = game.gold
        repeat(3000) { game.tick(50) }
        assertEquals(TowerDefense.START_LIVES, game.lives)
        assertTrue(game.score >= TowerDefense.waveEnemies(1).sumOf { TowerDefense.killPoints(it) })
        assertTrue(game.gold > goldBefore)
        assertFalse(game.waveActive)
        assertTrue(game.waveClearMs >= 0f)
    }

    @Test
    fun laFinDeVagueDonneUnBonus() {
        val game = newGame()
        game.startWave()
        game.queue.clear()
        val gold = game.gold
        val score = game.score
        game.tick(10)
        assertFalse(game.waveActive)
        assertEquals(gold + TowerDefense.waveBonusGold(1), game.gold)
        assertEquals(score + TowerDefense.waveBonusPoints(1), game.score)
        assertTrue(game.waveClearMs > 0f)
    }

    @Test
    fun laTourBleueRalentitLesEnnemis() {
        val game = newGame()
        val slow = defend(game, TdTowerKind.DEBUFF)
        val sx = slow.cell % TdMap.COLS + 0.5f
        val sy = slow.cell / TdMap.COLS + 0.5f
        val enemy = TdEnemy(1, TdEnemyKind.THUG, 100000f)
        // On place l'ennemi à portée, au bord du chemin.
        var bestDist = 0f
        var best = Float.MAX_VALUE
        var d = 0f
        while (d < game.map.path.size - 1) {
            enemy.dist = d
            val dd = Math.hypot((game.enemyX(enemy) - sx).toDouble(), (game.enemyY(enemy) - sy).toDouble()).toFloat()
            if (dd < best) { best = dd; bestDist = d }
            d += 0.1f
        }
        enemy.dist = bestDist
        game.enemies.add(enemy)
        repeat(60) { game.tick(50) }
        assertTrue("l'ennemi est ralenti", enemy.slowFactor < 1f || enemy.slowMs > 0f)
    }

    @Test
    fun laTourVerteRenforceSesVoisines() {
        val game = newGame()
        val (row, col) = freeCellNearPath(game)
        game.towers.add(TdTower(row * TdMap.COLS + col, TdTowerKind.WOODEN, 1))
        val archer = game.towers[0]
        assertEquals(0f, game.boostFor(archer), 0f)
        // Une tour verte contre l'archère (cases voisines, libres).
        val neighbor = listOf(Pair(row, col - 1), Pair(row, col + 1), Pair(row - 1, col), Pair(row + 1, col))
            .first { (r, c) -> game.map.isBuildable(r, c) && game.towerAt(r * TdMap.COLS + c) == null }
        game.towers.add(TdTower(neighbor.first * TdMap.COLS + neighbor.second, TdTowerKind.HEAL, 2))
        assertEquals(TowerDefense.boostOf(2), game.boostFor(archer), 0.0001f)
        assertTrue(game.damageOf(archer) > TowerDefense.damageOf(TdTowerKind.WOODEN, 1))
        // La tour verte ne se renforce pas elle-même, et le renfort est plafonné.
        assertEquals(0f, game.boostFor(game.towers[1]), 0f)
        repeat(20) { game.towers.add(TdTower(1000 + it, TdTowerKind.HEAL, 4)) }
        assertTrue(game.boostFor(archer) <= TowerDefense.MAX_BOOST)
    }

    @Test
    fun laBouleDeFeuBlesseLesVoisinsDeLaCible() {
        val game = newGame()
        val tower = defend(game, TdTowerKind.BRICK)
        val tx = tower.cell % TdMap.COLS + 0.5f
        val ty = tower.cell / TdMap.COLS + 0.5f
        val a = TdEnemy(1, TdEnemyKind.THUG, 1000f)
        val b = TdEnemy(2, TdEnemyKind.THUG, 1000f)
        // Deux ennemis côte à côte sur le chemin, le plus proche de la tour en premier.
        var bestDist = 0f
        var best = Float.MAX_VALUE
        var d = 0f
        while (d < game.map.path.size - 2) {
            a.dist = d
            val dd = Math.hypot((game.enemyX(a) - tx).toDouble(), (game.enemyY(a) - ty).toDouble()).toFloat()
            if (dd < best) { best = dd; bestDist = d }
            d += 0.1f
        }
        a.dist = bestDist
        b.dist = bestDist - 0.3f
        game.enemies.add(a)
        game.enemies.add(b)
        repeat(80) { game.tick(50) }
        assertTrue("la cible est blessée", a.hp < a.maxHp || b.hp < b.maxHp)
        assertTrue("le voisin aussi", a.hp < a.maxHp && b.hp < b.maxHp)
    }

    @Test
    fun lExplosionDeLaBriqueMarqueSaZoneParUnAnneauDeFumee() {
        val game = newGame()
        val tower = defend(game, TdTowerKind.BRICK)
        val tx = tower.cell % TdMap.COLS + 0.5f
        val ty = tower.cell / TdMap.COLS + 0.5f
        val enemy = TdEnemy(1, TdEnemyKind.THUG, 1000f)
        var bestDist = 0f
        var best = Float.MAX_VALUE
        var d = 0f
        while (d < game.map.path.size - 1) {
            enemy.dist = d
            val dd = Math.hypot((game.enemyX(enemy) - tx).toDouble(), (game.enemyY(enemy) - ty).toDouble()).toFloat()
            if (dd < best) { best = dd; bestDist = d }
            d += 0.1f
        }
        enemy.dist = bestDist
        game.enemies.add(enemy)
        var ring: TdFlash? = null
        repeat(80) {
            game.tick(30)
            if (ring == null) ring = game.flashes.firstOrNull { it.radius > 0f }
        }
        assertNotNull(ring)
        assertEquals(TdTowerKind.BRICK.splash, ring!!.radius, 0f)
        // Les autres tours n'ont pas de zone, donc pas d'anneau.
        val other = newGame()
        val wood = defend(other, TdTowerKind.WOODEN)
        assertEquals(0f, TdTowerKind.WOODEN.splash, 0f)
        assertNotNull(wood)
    }

    @Test
    fun uneVagueNEstPasFinieTantQuUnEnnemiEstEnVie() {
        val game = newGame()
        game.startWave()
        game.tick(10)
        game.queue.clear()
        game.tick(10)
        assertTrue(game.waveActive)
    }

    @Test
    fun lEnnemiRegardeDansLaDirectionDuChemin() {
        val game = newGame()
        val enemy = TdEnemy(1, TdEnemyKind.GOBLIN, 10f)
        enemy.dist = 0.2f
        assertEquals(TdDir.FRONT, game.enemyDir(enemy)) // le chemin part vers le bas
        assertEquals(game.map.entryCol + 0.5f, game.enemyX(enemy), 0.0001f)
        enemy.dist = (game.map.path.size - 1).toFloat()
        assertEquals(TdMap.ROWS - 0.5f, game.enemyY(enemy), 0.0001f)
    }

    // ---- Sauvegarde ----

    @Test
    fun laPartieSeRelitApresUneSauvegarde() {
        val game = newGame(11)
        val (row, col) = freeCellNearPath(game)
        game.build(row, col, TdTowerKind.WOODEN)
        game.upgrade(game.towers[0])
        game.startWave()
        repeat(80) { game.tick(50) }
        val copy = TdGame.restore(game.save())
        assertNotNull(copy)
        copy!!
        assertEquals(game.save(), copy.save())
        assertEquals(game.wave, copy.wave)
        assertEquals(game.gold, copy.gold)
        assertEquals(game.map.path.toList(), copy.map.path.toList())
        assertEquals(game.towers.size, copy.towers.size)
        assertEquals(game.enemies.size, copy.enemies.size)
        // Les deux parties évoluent de la même façon (les projectiles en vol ne sont pas gardés : on les laisse partir).
        game.projectiles.clear()
        repeat(100) { game.tick(50); copy.tick(50) }
        assertEquals(game.save(), copy.save())
    }

    @Test
    fun uneSauvegardeAbimeeEstRefusee() {
        val game = newGame(4)
        val (row, col) = freeCellNearPath(game)
        game.build(row, col, TdTowerKind.WOODEN)
        game.startWave()
        game.tick(100)
        val data = game.save()
        assertNotNull(TdGame.restore(data))
        assertNull(TdGame.restore(emptyList()))
        assertNull(TdGame.restore(data.dropLast(1)))
        assertNull(TdGame.restore(data + 5))
        assertNull(TdGame.restore(listOf(99) + data.drop(1)))
        // Vies impossibles.
        assertNull(TdGame.restore(data.toMutableList().also { it[4] = 500 }))
        // Une tour sur le chemin.
        val onPath = data.toMutableList()
        val towerAt = 10 + game.pending
        onPath[towerAt + 1] = game.map.path[2]
        assertNull(TdGame.restore(onPath))
    }
}
