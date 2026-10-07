package com.aventure.messcores

import kotlin.math.roundToInt

/**
 * Bilan d'un joueur. [bestScore] et [averageScore] ne sont renseignés que pour un jeu précis (les
 * scores de jeux différents ne se comparent pas : 40 points à Skyjo n'ont rien à voir avec 40 à la Belote).
 */
data class PlayerStats(
    val name: String,
    val played: Int,
    val wins: Int,
    val bestScore: Int?,
    val averageScore: Double?
) {
    /** Pourcentage de parties gagnées, arrondi. */
    val winRatePercent: Int get() = if (played == 0) 0 else (wins * 100.0 / played).roundToInt()
}

/** Un jeu présent dans le journal, avec le nombre de parties terminées. */
data class GameSummary(
    val gameId: String,
    val gameName: String,
    val lowestWins: Boolean,
    val finishedGames: Int
)

/**
 * Statistiques du journal. [overall] regroupe tous les jeux (parties et victoires seulement) ;
 * [byGame] détaille chaque jeu, avec meilleur score et moyenne.
 */
data class StatsReport(
    val finishedCount: Int,
    val inProgressCount: Int,
    val games: List<GameSummary>,
    val overall: List<PlayerStats>,
    val byGame: Map<String, List<PlayerStats>>
)

/**
 * Calcule les statistiques à partir des parties du journal. Fonctions pures (sans Android), testées
 * dans StatsCalculatorTest.
 *
 * Règles :
 * - seules les parties **terminées** comptent : une partie en cours n'a ni vainqueur ni score final ;
 * - le vainqueur est celui qui a le meilleur total selon le jeu (le plus bas à Skyjo) ; en cas d'égalité
 *   en tête, chaque joueur à égalité compte une victoire ; si tous les joueurs sont à égalité, personne
 *   ne gagne (comme dans le journal) ;
 * - un joueur est reconnu par son nom, sans tenir compte des majuscules ni des espaces autour.
 */
object StatsCalculator {

    private class Accumulator(val name: String) {
        var played = 0
        var wins = 0
        val scores = mutableListOf<Int>()
    }

    /** Index (dans [SavedGame.players]) des vainqueurs d'une partie terminée ; vide sinon. */
    fun winnersOf(game: SavedGame): List<Int> {
        if (!game.isFinished) return emptyList()
        val totals = game.totals()
        if (totals.distinct().size < 2) return emptyList()
        val best = if (game.gameRules.lowestWins) totals.min() else totals.max()
        return totals.indices.filter { totals[it] == best }
    }

    fun compute(saved: List<SavedGame>): StatsReport {
        // Du plus récent au plus ancien : le nom d'un jeu ou d'un joueur est celui de sa dernière apparition.
        val finished = saved.filter { it.isFinished }.sortedByDescending { it.savedAt }

        val overall = linkedMapOf<String, Accumulator>()
        val perGame = linkedMapOf<String, MutableMap<String, Accumulator>>()
        val gameNames = linkedMapOf<String, String>()
        val gameLowestWins = mutableMapOf<String, Boolean>()
        val gameCounts = mutableMapOf<String, Int>()

        for (game in finished) {
            val gameId = game.gameRules.id
            gameNames.getOrPut(gameId) { game.gameRules.name }
            gameLowestWins.getOrPut(gameId) { game.gameRules.lowestWins }
            gameCounts[gameId] = (gameCounts[gameId] ?: 0) + 1

            val totals = game.totals()
            val winners = winnersOf(game)
            val inThisGame = perGame.getOrPut(gameId) { linkedMapOf() }
            val seenInThisParty = mutableSetOf<String>()

            game.players.forEachIndexed { index, rawName ->
                val name = rawName.trim()
                val key = name.lowercase()
                // Nom vide, ou deux joueurs au même nom dans une même partie (fichier importé) : compté une fois.
                if (key.isEmpty() || !seenInThisParty.add(key)) return@forEachIndexed
                val total = totals.getOrNull(index) ?: return@forEachIndexed

                val all = overall.getOrPut(key) { Accumulator(name) }
                val one = inThisGame.getOrPut(key) { Accumulator(name) }
                all.played++
                one.played++
                if (index in winners) {
                    all.wins++
                    one.wins++
                }
                one.scores.add(total)
            }
        }

        val games = gameNames.map { (id, name) ->
            GameSummary(
                gameId = id,
                gameName = name,
                lowestWins = gameLowestWins[id] ?: false,
                finishedGames = gameCounts[id] ?: 0
            )
        }.sortedWith(compareByDescending<GameSummary> { it.finishedGames }.thenBy { it.gameName.lowercase() })

        return StatsReport(
            finishedCount = finished.size,
            inProgressCount = saved.size - finished.size,
            games = games,
            overall = overall.values.map { it.toStats(lowestWins = null) }.sortedWith(RANKING),
            byGame = perGame.mapValues { (id, players) ->
                players.values.map { it.toStats(lowestWins = gameLowestWins[id] ?: false) }.sortedWith(RANKING)
            }
        )
    }

    /** Plus de victoires d'abord, puis meilleur pourcentage, puis plus de parties, puis ordre alphabétique. */
    private val RANKING: Comparator<PlayerStats> =
        compareByDescending<PlayerStats> { it.wins }
            .thenByDescending { it.winRatePercent }
            .thenByDescending { it.played }
            .thenBy { it.name.lowercase() }

    /** [lowestWins] null : bilan tous jeux confondus, sans meilleur score ni moyenne. */
    private fun Accumulator.toStats(lowestWins: Boolean?): PlayerStats = PlayerStats(
        name = name,
        played = played,
        wins = wins,
        bestScore = if (lowestWins == null || scores.isEmpty()) null else if (lowestWins) scores.min() else scores.max(),
        averageScore = if (lowestWins == null || scores.isEmpty()) null else scores.average()
    )
}
