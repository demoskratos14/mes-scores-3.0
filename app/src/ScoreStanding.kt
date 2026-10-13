package com.aventure.messcores

/*
 * Classement et fin de partie : fonctions pures (sans état), appelées par [ScoreViewModel].
 */

/**
 * Rang (1 = premier) de chaque joueur d'après [totals] ; les ex æquo partagent le même rang.
 * Si [phases] est fourni (Phase 10), on classe d'abord par phase atteinte, puis par score à phase égale.
 */
internal fun computeRanks(totals: List<Int>, lowestWins: Boolean, phases: List<Int>? = null): List<Int> {
    if (phases != null) {
        fun isBetter(a: Int, b: Int): Boolean = phases[a] > phases[b] ||
            (phases[a] == phases[b] && if (lowestWins) totals[a] < totals[b] else totals[a] > totals[b])
        return totals.indices.map { me -> totals.indices.count { isBetter(it, me) } + 1 }
    }
    return totals.map { mine -> totals.count { if (lowestWins) it < mine else it > mine } + 1 }
}

/**
 * Vrai si au moins un joueur a franchi le seuil de score de [condition], dans le sens configuré, en
 * ayant au moins [EndCondition.minLead] points d'avance sur chacun des autres (2 au ping-pong :
 * à 10-10 ou 11-10, la partie continue). Un score plafond éventuel termine la partie dans tous les cas.
 */
internal fun thresholdReached(condition: EndCondition, totals: List<Int>): Boolean {
    val threshold = condition.scoreThreshold ?: return false
    // Score plafond (30 au badminton) : la partie se termine sans exiger l'écart.
    condition.maxScore?.let { cap ->
        val capped = when (condition.thresholdDirection) {
            ThresholdDirection.ABOVE -> totals.any { it >= cap }
            ThresholdDirection.BELOW -> totals.any { it <= cap }
        }
        if (capped) return true
    }
    return totals.indices.any { i ->
        val total = totals[i]
        val others = totals.filterIndexed { j, _ -> j != i }
        when (condition.thresholdDirection) {
            ThresholdDirection.ABOVE -> total >= threshold && (others.isEmpty() || total - others.max() >= condition.minLead)
            ThresholdDirection.BELOW -> total <= threshold && (others.isEmpty() || others.min() - total >= condition.minLead)
        }
    }
}

/** Vrai si plusieurs joueurs sont à égalité à la meilleure place du classement. */
internal fun hasTiedLeaders(totals: List<Int>, lowestWins: Boolean): Boolean {
    if (totals.isEmpty()) return false
    val best = if (lowestWins) totals.min() else totals.max()
    return totals.count { it == best } > 1
}
