package com.aventure.messcores

/**
 * Évolution du total de chaque joueur, manche après manche : données de la courbe d'évolution
 * des scores (voir [ScoreChartDialog]).
 */
object ScoreProgress {
    /** Il faut au moins deux manches pour tracer une courbe. */
    const val MIN_POINTS = 2

    /**
     * `série[joueur][i]` = total du joueur après la (i + 1)-ème manche saisie, dans l'ordre des
     * joueurs de [game]. Renvoie null s'il n'y a pas assez de manches, ou pas d'historique à tracer
     * (mode compteur : seul le total courant est conservé).
     */
    fun series(game: SavedGame): List<List<Int>>? {
        val result = when (game.gameRules.scoreMode) {
            ScoreMode.TABLE -> tableSeries(game)
            ScoreMode.VARIABLE_TEAMS -> teamSeries(game)
            ScoreMode.COUNTER, ScoreMode.POKER, ScoreMode.RACKET -> return null
        }
        return result.takeIf { it.isNotEmpty() && it.all { values -> values.size >= MIN_POINTS } }
    }

    private fun tableSeries(game: SavedGame): List<List<Int>> {
        val rules = game.gameRules
        val rounds = game.cellSnapshots.orEmpty()
        // Les manches vides à la fin (la manche prête à être saisie) ne comptent pas.
        val filled = rounds.indexOfLast { round -> round.any { it.baseValue != null } } + 1
        return game.players.indices.map { player ->
            val values: List<Int?> = rounds.take(filled).map { round ->
                val cell = round.getOrNull(player)
                val base = cell?.baseValue
                if (cell == null || base == null) {
                    null
                } else {
                    val rule = rules.multipliers.find { it.id == cell.multiplierId } ?: GameRules.NORMAL_MULTIPLIER
                    val magnitude = base * rule.factor + rule.bonus
                    if (cell.isNegative) -magnitude else magnitude
                }
            }
            // Même calcul que le total affiché (bonus, décompte, score exact…) sur les n premières manches.
            (1..filled).map { n -> rules.tableTotal(values.take(n)) }
        }
    }

    private fun teamSeries(game: SavedGame): List<List<Int>> {
        val rounds = game.teamRounds.orEmpty()
        return game.players.indices.map { player ->
            var total = 0
            rounds.map { round ->
                total += round.deltas?.getOrNull(player) ?: if (player in round.teamAPlayers) round.value else -round.value
                total
            }
        }
    }
}
