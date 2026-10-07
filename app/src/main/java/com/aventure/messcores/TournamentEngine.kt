package com.aventure.messcores

import androidx.annotation.StringRes
import kotlin.random.Random

/**
 * Un match du championnat. [playerAIndex]/[playerBIndex] référencent l'index du participant dans
 * [TournamentState.participants] ; null en cas de "bye" (qualification directe faute d'adversaire,
 * pour les effectifs qui ne sont pas une puissance de 2). [label] identifie les matchs spéciaux du
 * dernier tour ("Finale", "Petite finale") ; null pour un tour normal.
 */
data class TournamentMatch(
    val round: Int,
    val label: String? = null,
    val playerAIndex: Int? = null,
    val playerBIndex: Int? = null,
    val winnerIndex: Int? = null
) {
    val isBye: Boolean get() = playerAIndex == null || playerBIndex == null
}

/** Façon de jouer un championnat. */
enum class TournamentFormat(@StringRes val labelRes: Int) {
    /** Le perdant est éliminé ; demi-finales, finale et petite finale à la fin. */
    KNOCKOUT(R.string.format_knockout),
    /** Poules : chaque participant rencontre tous les autres, le classement se fait aux victoires. */
    ROUND_ROBIN(R.string.format_round_robin)
}

/**
 * Une ligne du classement d'une poule. Les participants à égalité parfaite partagent le même [rank].
 */
data class PouleRow(
    val playerIndex: Int,
    val rank: Int,
    val played: Int,
    val wins: Int
) {
    val losses: Int get() = played - wins
}

/**
 * État complet d'un championnat. Immuable : chaque action de [TournamentEngine] en renvoie un nouveau,
 * ce qui le rend trivial à tester (aucun état Compose, aucune dépendance Android).
 */
data class TournamentState(
    val participants: List<String> = emptyList(),
    val rounds: List<List<TournamentMatch>> = emptyList(),
    val finished: Boolean = false,
    val format: TournamentFormat = TournamentFormat.KNOCKOUT
) {
    /** Vrai si un championnat (en cours ou terminé) existe. */
    val hasTournament: Boolean get() = rounds.isNotEmpty()

    /** Vrai si des matchs des tours suivants [roundIndex] ont déjà été joués (donc perdus si on corrige ce tour). */
    fun hasPlayedAfter(roundIndex: Int): Boolean =
        // En poules, les journées sont indépendantes : corriger un match n'en fait perdre aucun autre.
        format == TournamentFormat.KNOCKOUT &&
            rounds.drop(roundIndex + 1).any { round -> round.any { !it.isBye && it.winnerIndex != null } }

    /**
     * Classement d'une poule (vide en élimination directe) : victoires, puis, à égalité, victoires dans
     * les matchs joués entre les participants restés à égalité. Si cela ne suffit pas, ils partagent le
     * même rang. Valable en cours de championnat comme à la fin.
     */
    fun standings(): List<PouleRow> {
        if (format != TournamentFormat.ROUND_ROBIN) return emptyList()
        val decided = rounds.flatten().filter { it.winnerIndex != null }
        val wins = IntArray(participants.size)
        val played = IntArray(participants.size)
        decided.forEach { match ->
            val a = match.playerAIndex
            val b = match.playerBIndex
            val winner = match.winnerIndex
            if (a != null && b != null && winner != null) {
                played[a]++
                played[b]++
                wins[winner]++
            }
        }
        // Victoires obtenues contre les seuls participants qui ont le même nombre de victoires que [p].
        fun headToHeadWins(p: Int): Int {
            val tied = participants.indices.filter { wins[it] == wins[p] }.toSet()
            return decided.count { match ->
                val a = match.playerAIndex
                val b = match.playerBIndex
                match.winnerIndex == p && a != null && b != null && a in tied && b in tied
            }
        }
        val h2h = participants.indices.map { headToHeadWins(it) }
        val order = participants.indices.sortedWith(
            compareBy<Int>({ -wins[it] }, { -h2h[it] }, { it })
        )
        val rows = mutableListOf<PouleRow>()
        order.forEachIndexed { position, p ->
            val previous = order.getOrNull(position - 1)
            val sameAsPrevious = previous != null && wins[previous] == wins[p] && h2h[previous] == h2h[p]
            val rank = if (sameAsPrevious) rows.last().rank else position + 1
            rows.add(PouleRow(playerIndex = p, rank = rank, played = played[p], wins = wins[p]))
        }
        return rows
    }

    /** Vainqueur du championnat, une fois [finished] vrai (null en poules s'il y a égalité en tête). */
    fun championIndex(): Int? {
        if (!finished) return null
        if (format == TournamentFormat.ROUND_ROBIN) {
            val leaders = standings().filter { it.rank == 1 }
            return leaders.singleOrNull()?.playerIndex
        }
        val last = rounds.lastOrNull() ?: return null
        val finalMatch = last.find { it.label == "Finale" } ?: last.firstOrNull()
        return finalMatch?.winnerIndex
    }

    /** Vainqueur de la petite finale (3e place), s'il y en a une. */
    fun thirdPlaceIndex(): Int? =
        rounds.lastOrNull()?.find { it.label == "Petite finale" }?.winnerIndex

    /**
     * Classement final, de la 1re place à la 4e au maximum : vainqueur et perdant de la finale,
     * puis vainqueur et perdant de la petite finale. À 3 participants (pas de petite finale),
     * la 3e place revient au perdant du seul match réellement joué au premier tour.
     * Liste vide tant que le championnat n'est pas terminé.
     */
    fun podium(): List<Int> {
        if (!finished) return emptyList()
        if (format == TournamentFormat.ROUND_ROBIN) return standings().take(4).map { it.playerIndex }
        val last = rounds.lastOrNull() ?: return emptyList()
        val finalMatch = last.find { it.label == "Finale" } ?: last.singleOrNull() ?: return emptyList()

        val result = mutableListOf<Int>()
        finalMatch.winnerIndex?.let { result.add(it) }
        loserOf(finalMatch)?.let { result.add(it) }

        val smallFinal = last.find { it.label == "Petite finale" }
        if (smallFinal != null) {
            smallFinal.winnerIndex?.let { result.add(it) }
            loserOf(smallFinal)?.let { result.add(it) }
        } else if (rounds.size >= 2) {
            val eliminated = rounds[rounds.size - 2].filter { !it.isBye }.mapNotNull { loserOf(it) }
            if (eliminated.size == 1) result.add(eliminated.first())
        }
        return result
    }

    private fun loserOf(match: TournamentMatch): Int? {
        val winner = match.winnerIndex ?: return null
        return if (match.playerAIndex == winner) match.playerBIndex else match.playerAIndex
    }
}

/**
 * Règles du championnat à élimination directe : le vainqueur de chaque match passe au tour suivant.
 * Dès que le tour ne compte plus que 2 matchs (les demi-finales) et que les deux sont joués sans
 * "bye", le tour suivant regroupe automatiquement la Finale (les deux vainqueurs) et la Petite
 * finale (les deux perdants). Les effectifs qui ne sont pas une puissance de 2 sont comblés par des
 * qualifications directes ("byes") uniquement au premier tour.
 */
object TournamentEngine {

    /**
     * Démarre un championnat à élimination directe. Par défaut l'ordre du tableau est tiré au sort
     * ([random] est injectable pour les tests). Avec [ordered], il suit l'ordre de [names] : le 1er
     * affronte le 2e, le 3e le 4e… et, s'il manque des joueurs pour remplir le tableau, ce sont les
     * derniers de la liste qui sont qualifiés d'office.
     */
    fun start(names: List<String>, random: Random = Random.Default, ordered: Boolean = false): TournamentState {
        if (names.size < 2) return TournamentState(participants = names)

        var bracketSize = 1
        while (bracketSize < names.size) bracketSize *= 2

        // Les byes sont répartis à raison d'un par match au maximum : deux places vides face à face
        // donneraient un match impossible à jouer.
        val byeCount = bracketSize - names.size
        val pairings = mutableListOf<Pair<Int, Int?>>()
        if (ordered) {
            val order = names.indices.toList()
            val playing = names.size - byeCount // toujours pair : chacun de ces participants a un adversaire
            var i = 0
            while (i + 1 < playing) {
                pairings.add(order[i] to order[i + 1])
                i += 2
            }
            for (k in playing until names.size) pairings.add(order[k] to null)
        } else {
            val order = names.indices.shuffled(random)
            for (k in 0 until byeCount) pairings.add(order[k] to null)
            var k = byeCount
            while (k + 1 < order.size) {
                pairings.add(order[k] to order[k + 1])
                k += 2
            }
            pairings.shuffle(random)
        }

        val firstRound = pairings.map { (a, b) -> TournamentMatch(round = 1, playerAIndex = a, playerBIndex = b) }
        return settle(TournamentState(participants = names, rounds = listOf(firstRound)))
    }

    /**
     * Démarre des poules : chaque participant rencontre une fois tous les autres. Les matchs sont répartis
     * en journées (méthode du cercle) où personne ne joue deux fois ; avec un nombre impair de
     * participants, un participant se repose à chaque journée (il n'apparaît dans aucun match).
     */
    fun startRoundRobin(names: List<String>): TournamentState {
        if (names.size < 2) return TournamentState(participants = names, format = TournamentFormat.ROUND_ROBIN)

        val slots = mutableListOf<Int?>().apply { addAll(names.indices.toList()) }
        if (slots.size % 2 == 1) slots.add(null) // place « fantôme » : qui l'affronte se repose
        val n = slots.size
        val rounds = mutableListOf<List<TournamentMatch>>()
        for (r in 0 until n - 1) {
            val matches = mutableListOf<TournamentMatch>()
            for (i in 0 until n / 2) {
                val a = slots[i]
                val b = slots[n - 1 - i]
                if (a != null && b != null) matches.add(TournamentMatch(round = r + 1, playerAIndex = a, playerBIndex = b))
            }
            rounds.add(matches)
            // Le premier reste en place, tous les autres avancent d'un cran.
            slots.add(1, slots.removeAt(n - 1))
        }
        return TournamentState(participants = names, rounds = rounds, finished = false, format = TournamentFormat.ROUND_ROBIN)
    }

    /**
     * Désigne [winner] (index dans les participants) comme vainqueur du match indiqué.
     * Sans effet si le match n'existe pas, si [winner] n'y joue pas, ou si le match a déjà un
     * résultat (il faut d'abord le corriger avec [resetMatch], sinon les tours suivants divergeraient).
     */
    fun setWinner(state: TournamentState, roundIndex: Int, matchIndex: Int, winner: Int): TournamentState {
        val match = state.rounds.getOrNull(roundIndex)?.getOrNull(matchIndex) ?: return state
        if (match.winnerIndex != null) return state
        if (match.playerAIndex != winner && match.playerBIndex != winner) return state
        val updated = state.replaceMatch(roundIndex, matchIndex, match.copy(winnerIndex = winner))
        if (state.format == TournamentFormat.ROUND_ROBIN) {
            // Poules : terminées dès que tous les matchs ont un vainqueur.
            return updated.copy(finished = updated.rounds.all { round -> round.all { it.winnerIndex != null } })
        }
        return settle(updated)
    }

    /**
     * Annule le résultat d'un match (erreur de saisie) pour pouvoir désigner à nouveau le vainqueur.
     * Les tours suivants, qui découlaient de ce résultat, sont supprimés : ils sont régénérés quand
     * le tour est de nouveau complet. Sans effet sur un match sans résultat ou un "bye".
     */
    fun resetMatch(state: TournamentState, roundIndex: Int, matchIndex: Int): TournamentState {
        val match = state.rounds.getOrNull(roundIndex)?.getOrNull(matchIndex) ?: return state
        if (match.isBye || match.winnerIndex == null) return state
        if (state.format == TournamentFormat.ROUND_ROBIN) {
            // Poules : seul ce match est à rejouer, les autres journées ne dépendent pas de lui.
            return state.replaceMatch(roundIndex, matchIndex, match.copy(winnerIndex = null)).copy(finished = false)
        }
        val kept = state.rounds.take(roundIndex + 1)
        return state.copy(rounds = kept, finished = false)
            .replaceMatch(roundIndex, matchIndex, match.copy(winnerIndex = null))
    }

    private fun TournamentState.replaceMatch(roundIndex: Int, matchIndex: Int, match: TournamentMatch) =
        copy(
            rounds = rounds.mapIndexed { r, round ->
                if (r == roundIndex) round.mapIndexed { m, old -> if (m == matchIndex) match else old } else round
            }
        )

    /**
     * Fait avancer le championnat tant que le tour courant est complet : qualifie les byes, puis
     * génère le tour suivant (ou la Finale + Petite finale), ou termine le championnat.
     */
    private fun settle(initial: TournamentState): TournamentState {
        var rounds = initial.rounds
        var finished = initial.finished
        while (!finished) {
            val current = rounds.lastOrNull() ?: break
            // Un bye n'a pas d'adversaire : son seul joueur passe automatiquement.
            val resolved = current.map {
                if (it.winnerIndex == null && it.isBye) it.copy(winnerIndex = it.playerAIndex ?: it.playerBIndex) else it
            }
            rounds = rounds.dropLast(1) + listOf(resolved)
            if (resolved.any { it.winnerIndex == null }) break

            val next = resolved.first().round + 1
            when {
                // Le tour Finale + Petite finale vient de se terminer.
                resolved.any { it.label != null } -> finished = true
                // Championnat à 2 participants : ce match unique EST la finale.
                resolved.size == 1 -> finished = true
                // Demi-finales terminées, sans bye : Finale (vainqueurs) + Petite finale (perdants).
                resolved.size == 2 && resolved.none { it.isBye } -> {
                    val winners = resolved.map { it.winnerIndex!! }
                    val losers = resolved.map { if (it.playerAIndex == it.winnerIndex) it.playerBIndex!! else it.playerAIndex!! }
                    rounds = rounds + listOf(
                        listOf(
                            TournamentMatch(next, "Finale", winners[0], winners[1]),
                            TournamentMatch(next, "Petite finale", losers[0], losers[1])
                        )
                    )
                }
                // Tour normal : les vainqueurs sont réappariés pour le tour suivant.
                else -> {
                    val winners = resolved.map { it.winnerIndex!! }
                    rounds = rounds + listOf(
                        winners.chunked(2).map { TournamentMatch(next, null, it[0], it.getOrNull(1)) }
                    )
                }
            }
        }
        return initial.copy(rounds = rounds, finished = finished)
    }
}
