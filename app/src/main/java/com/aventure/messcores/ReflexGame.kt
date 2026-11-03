package com.aventure.messcores

/**
 * La séance du Duel de réflexes chacun sur son téléphone, pour deux joueurs ou plus (jusqu'à [Reflex.MAX_PLAYERS]).
 *
 * Les joueurs sont repérés par leur place à la table : 0 pour l'organisateur, puis les invités dans l'ordre d'arrivée.
 * Chaque téléphone mesure le temps de réaction de son joueur depuis son propre signal ; l'organisateur rassemble les
 * résultats d'une manche et les compare avec [resolve]. Le premier à [target] manches gagnées remporte la partie.
 *
 * Un résultat de manche est un temps en millisecondes (positif), [Reflex.EARLY] (faux départ) ou [Reflex.MISS]
 * (pas touché à temps).
 */
internal data class ReflexGame(
    /** Manches gagnées par place. */
    val wins: List<Int>,
    /** Somme des temps de réaction par place, et nombre de temps pris en compte (voir [averageMsOf]). */
    val totalMs: List<Int>,
    val timedRounds: List<Int>,
    /** Les résultats de la dernière manche par place (vide tant qu'aucune manche n'est jouée). */
    val lastResults: List<Int> = emptyList(),
    /** La place qui a gagné la dernière manche, ou [NONE]. */
    val lastWinner: Int = NONE,
    /** Dernière manche nulle : égalité exacte au meilleur temps, ou personne n'a de temps. On la rejoue. */
    val lastDraw: Boolean = false,
    val target: Int = Reflex.TARGET
) {
    /** Le nombre de joueurs à la table. */
    val players: Int get() = wins.size

    /** La place du vainqueur de la partie, ou [NONE] si elle continue. */
    val winner: Int get() = wins.indexOfFirst { it >= target }.let { if (it < 0) NONE else it }

    val isOver: Boolean get() = winner != NONE

    /** Vrai quand une manche a été jouée et résolue depuis le début de la partie (gagnée ou nulle). */
    val roundPlayed: Boolean get() = lastWinner != NONE || lastDraw

    /** Le temps de réaction du vainqueur de la dernière manche, 0 s'il a gagné sans temps (les autres ont fait un faux départ). */
    val lastReactionMs: Int get() = lastResults.getOrNull(lastWinner)?.coerceAtLeast(0) ?: 0

    /** Les places qui ont fait un faux départ à la dernière manche. */
    val falseStarters: List<Int> get() = lastResults.indices.filter { lastResults[it] == Reflex.EARLY }

    fun winsOf(seat: Int): Int = wins.getOrElse(seat) { 0 }

    /** Nombre de manches dont le temps de la place [seat] est compté dans sa moyenne. */
    fun timedRoundsOf(seat: Int): Int = timedRounds.getOrElse(seat) { 0 }

    /**
     * Temps de réaction moyen de la place [seat] depuis le début de la partie, en millisecondes arrondies, ou `null` si
     * elle n'a encore aucun temps. Les faux départs, les manches non touchées et les manches nulles ne comptent pas :
     * ce ne sont pas des temps de réaction.
     */
    fun averageMsOf(seat: Int): Int? {
        val count = timedRoundsOf(seat)
        if (count == 0) return null
        return (totalMs.getOrElse(seat) { 0 } + count / 2) / count
    }

    /** Vrai quand au moins un joueur a deux temps ou plus : c'est là qu'une moyenne apporte quelque chose. */
    val hasAverages: Boolean get() = timedRounds.any { it >= 2 }

    /**
     * Compare les résultats de la manche, un par place. Les joueurs qui ont touché après le signal sont classés par
     * temps : le plus rapide gagne, tous leurs temps comptent dans les moyennes. Un faux départ ne peut pas gagner.
     * Si personne n'a de temps, le seul joueur sans faux départ gagne la manche ; sinon, comme en cas d'égalité exacte
     * au meilleur temps, la manche est nulle : personne ne marque.
     * Renvoie `null` si la partie est finie ou si le nombre de résultats ne correspond pas aux joueurs.
     */
    fun resolve(results: List<Int>): ReflexGame? {
        if (isOver || players < Reflex.MIN_PLAYERS || results.size != players) return null
        val r = results.map { if (it < 0) Reflex.MISS else it.coerceAtMost(MAX_REACTION_MS) }
        val touched = r.indices.filter { r[it] > 0 }
        val winnerSeat = if (touched.isNotEmpty()) {
            val best = touched.minOf { r[it] }
            touched.filter { r[it] == best }.singleOrNull() ?: NONE
        } else {
            // Personne n'a de temps : les autres ont fait un faux départ ou n'ont pas touché.
            val clean = r.indices.filter { r[it] != Reflex.EARLY }
            if (clean.size == 1) clean.single() else NONE
        }
        if (winnerSeat == NONE) return copy(lastResults = r, lastWinner = NONE, lastDraw = true)
        return copy(
            wins = wins.mapIndexed { seat, n -> if (seat == winnerSeat) n + 1 else n },
            totalMs = totalMs.mapIndexed { seat, ms -> ms + r[seat].coerceAtLeast(0) },
            timedRounds = timedRounds.mapIndexed { seat, n -> if (r[seat] > 0) n + 1 else n },
            lastResults = r,
            lastWinner = winnerSeat,
            lastDraw = false
        )
    }

    /** Une nouvelle partie avec les mêmes joueurs : les scores et les moyennes repartent de zéro. */
    fun newMatch(): ReflexGame = of(players, target)

    /**
     * Un joueur quitte la table : sa place disparaît (les suivantes remontent d'un cran) et les autres gardent leur
     * score. Le détail de la dernière manche est effacé, ses places n'étant plus les mêmes.
     */
    fun removePlayer(seat: Int): ReflexGame {
        if (seat !in 0 until players) return this
        fun List<Int>.without() = filterIndexed { index, _ -> index != seat }
        return copy(
            wins = wins.without(),
            totalMs = totalMs.without(),
            timedRounds = timedRounds.without(),
            lastResults = emptyList(),
            lastWinner = NONE,
            lastDraw = false
        )
    }

    companion object {
        /** Aucune place : pas de vainqueur. */
        const val NONE = -1

        /** Au-delà de dix secondes, le temps n'a plus de sens (écran laissé de côté) : on le plafonne. */
        const val MAX_REACTION_MS = 9_999

        /** Une partie neuve pour [players] joueurs. */
        fun of(players: Int, target: Int = Reflex.TARGET) = ReflexGame(
            wins = List(players) { 0 },
            totalMs = List(players) { 0 },
            timedRounds = List(players) { 0 },
            target = target
        )
    }
}
