package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/*
 * Score du tennis et du padel : points (0, 15, 30, 40, avantage), jeux, sets, tie-break.
 * Fonctions pures (sans état Android), testées dans RacketScoringTest.
 *
 * Toute la partie se déduit de la liste des points gagnés ([RacketMatch.points]) : 0 = point pour le
 * camp 1 (premier joueur ou première équipe de la liste), 1 = point pour le camp 2. Annuler un point,
 * c'est donc retirer le dernier de la liste ; rien d'autre n'est à défaire.
 */

/** Façon de jouer le set décisif (le dernier, celui qui désigne le vainqueur du match). */
enum class FinalSetMode {
    /** Set normal, avec un tie-break à 7 points à 6 jeux partout. */
    TIEBREAK,

    /** Le set décisif est remplacé par un super tie-break à 10 points (2 points d'écart). */
    SUPER_TIEBREAK,

    /** Pas de tie-break : on joue jusqu'à 2 jeux d'écart (à partir de 6). */
    ADVANTAGE
}

/**
 * Réglages d'un match.
 *
 * @param setsToWin nombre de sets à gagner : 1, 2 (meilleur des 3) ou 3 (meilleur des 5).
 * @param goldenPoint vrai : à 40-40, le point suivant désigne le gagnant du jeu (point d'or du padel).
 *   Faux : il faut 2 points d'écart (avantage).
 * @param finalSet façon de jouer le set décisif.
 */
data class RacketConfig(
    val setsToWin: Int = 2,
    val goldenPoint: Boolean = false,
    val finalSet: FinalSetMode = FinalSetMode.TIEBREAK
) {
    fun toJson(): JSONObject = JSONObject()
        .put("setsToWin", setsToWin)
        .put("goldenPoint", goldenPoint)
        .put("finalSet", finalSet.name)

    companion object {
        /** Réglages de départ d'un jeu : tennis classique, ou padel (point d'or). */
        fun defaultsFor(rules: GameRules): RacketConfig =
            if (rules.id == PADEL_ID) RacketConfig(setsToWin = 2, goldenPoint = true) else RacketConfig()

        const val TENNIS_ID = "builtin_tennis"
        const val PADEL_ID = "builtin_padel"

        fun fromJson(obj: JSONObject): RacketConfig {
            val finalSet = try {
                FinalSetMode.valueOf(obj.optString("finalSet", FinalSetMode.TIEBREAK.name))
            } catch (e: IllegalArgumentException) {
                FinalSetMode.TIEBREAK
            }
            return RacketConfig(
                setsToWin = obj.optInt("setsToWin", 2).coerceIn(1, 3),
                goldenPoint = obj.optBoolean("goldenPoint", false),
                finalSet = finalSet
            )
        }
    }
}

/** Un match enregistré : ses réglages et, dans l'ordre, le camp (0 ou 1) qui a gagné chaque point. */
data class RacketMatch(val config: RacketConfig, val points: List<Int>) {
    fun toJson(): JSONObject = JSONObject()
        .put("config", config.toJson())
        .put("points", JSONArray(points))

    companion object {
        fun fromJson(obj: JSONObject): RacketMatch {
            val array = obj.optJSONArray("points")
            val points = if (array == null) emptyList() else (0 until array.length()).map { if (array.getInt(it) == 0) 0 else 1 }
            val config = obj.optJSONObject("config")?.let { RacketConfig.fromJson(it) } ?: RacketConfig()
            return RacketMatch(config, points)
        }
    }
}

/**
 * Un set terminé. [a] et [b] sont les jeux de chaque camp ; pour un super tie-break ([superTiebreak]),
 * ce sont les points du tie-break (10-7). [tiebreakLoser] : points du perdant dans le tie-break d'un
 * set à 7-6 (le « 4 » de 7-6(4)).
 */
data class SetScore(
    val a: Int,
    val b: Int,
    val tiebreakLoser: Int? = null,
    val superTiebreak: Boolean = false
) {
    /** Camp qui a gagné le set (0 ou 1). */
    val winner: Int get() = if (a > b) 0 else 1
}

/** État d'un match à un instant donné. Se construit avec [RacketScoring.replay]. */
data class RacketState(
    val config: RacketConfig,
    /** Sets terminés, dans l'ordre. */
    val sets: List<SetScore> = emptyList(),
    /** Jeux du set en cours. */
    val gamesA: Int = 0,
    val gamesB: Int = 0,
    /** Points bruts du jeu en cours (0, 1, 2, 3… : voir [pointText]) ou du tie-break en cours. */
    val pointsA: Int = 0,
    val pointsB: Int = 0,
    val inTiebreak: Boolean = false,
    /** Vrai si le tie-break en cours est le super tie-break qui remplace le set décisif. */
    val superTiebreak: Boolean = false,
    /** Camp vainqueur du match (0 ou 1), null tant qu'il n'est pas fini. */
    val winner: Int? = null
) {
    val setsWonA: Int get() = sets.count { it.winner == 0 }
    val setsWonB: Int get() = sets.count { it.winner == 1 }

    fun setsWon(side: Int): Int = if (side == 0) setsWonA else setsWonB

    fun games(side: Int): Int = if (side == 0) gamesA else gamesB

    fun rawPoints(side: Int): Int = if (side == 0) pointsA else pointsB

    /** Numéro (à partir de 1) du set en cours ou du dernier set joué. */
    val currentSetNumber: Int get() = if (winner != null) sets.size else sets.size + 1

    /** Vrai si [side] a l'avantage (jeu normal, 40-40 passé, sans point d'or). */
    fun hasAdvantage(side: Int): Boolean {
        if (inTiebreak || config.goldenPoint || winner != null) return false
        val mine = rawPoints(side)
        val other = rawPoints(1 - side)
        return mine >= 3 && other >= 3 && mine > other
    }

    /** Vrai à égalité (40-40, 40 partout après un avantage perdu) sans point d'or. */
    val isDeuce: Boolean
        get() = !inTiebreak && !config.goldenPoint && winner == null && pointsA == pointsB && pointsA >= 3

    /** Vrai quand le prochain point décide du jeu à 40-40 (point d'or). */
    val isGoldenPoint: Boolean
        get() = !inTiebreak && config.goldenPoint && winner == null && pointsA == 3 && pointsB == 3

    /**
     * Points de [side] à afficher : « 0 », « 15 », « 30 » ou « 40 » dans un jeu, le nombre de points dans
     * un tie-break. L'avantage ([hasAdvantage]) se montre à part : ici le camp qui l'a affiche « 40 ».
     */
    fun pointText(side: Int): String {
        val raw = rawPoints(side)
        if (inTiebreak) return raw.toString()
        return when (raw) {
            0 -> "0"
            1 -> "15"
            2 -> "30"
            else -> "40"
        }
    }
}

object RacketScoring {

    /** Jeux nécessaires pour gagner un set (avec 2 jeux d'écart). */
    const val GAMES_TO_WIN_SET = 6

    /** Points d'un tie-break de set (2 points d'écart). */
    const val TIEBREAK_POINTS = 7

    /** Points du super tie-break (2 points d'écart). */
    const val SUPER_TIEBREAK_POINTS = 10

    /** État de départ d'un match (0-0, rien de joué). */
    fun initial(config: RacketConfig): RacketState =
        RacketState(config = config, inTiebreak = startsWithSuper(config, 0, 0), superTiebreak = startsWithSuper(config, 0, 0))

    /** Rejoue tous les points dans l'ordre. Les points saisis après la fin du match sont ignorés. */
    fun replay(config: RacketConfig, points: List<Int>): RacketState {
        var state = initial(config)
        for (side in points) {
            if (state.winner != null) break
            state = addPoint(state, side)
        }
        return state
    }

    fun replay(match: RacketMatch): RacketState = replay(match.config, match.points)

    /** Ajoute un point au camp [side] (0 ou 1). Sans effet si le match est terminé. */
    fun addPoint(state: RacketState, side: Int): RacketState {
        if (state.winner != null) return state
        val pointsA = state.pointsA + if (side == 0) 1 else 0
        val pointsB = state.pointsB + if (side == 1) 1 else 0

        if (state.inTiebreak) {
            val target = if (state.superTiebreak) SUPER_TIEBREAK_POINTS else TIEBREAK_POINTS
            if (maxOf(pointsA, pointsB) >= target && kotlin.math.abs(pointsA - pointsB) >= 2) {
                val winner = if (pointsA > pointsB) 0 else 1
                val set = if (state.superTiebreak) {
                    SetScore(pointsA, pointsB, superTiebreak = true)
                } else {
                    SetScore(
                        a = if (winner == 0) GAMES_TO_WIN_SET + 1 else GAMES_TO_WIN_SET,
                        b = if (winner == 1) GAMES_TO_WIN_SET + 1 else GAMES_TO_WIN_SET,
                        tiebreakLoser = minOf(pointsA, pointsB)
                    )
                }
                return finishSet(state, set)
            }
            return state.copy(pointsA = pointsA, pointsB = pointsB)
        }

        val gameWinner = gameWinner(pointsA, pointsB, state.config.goldenPoint)
            ?: return state.copy(pointsA = pointsA, pointsB = pointsB)
        val gamesA = state.gamesA + if (gameWinner == 0) 1 else 0
        val gamesB = state.gamesB + if (gameWinner == 1) 1 else 0
        val deciding = isDecidingSet(state.config, state.setsWonA, state.setsWonB)
        val tiebreakAllowed = !(deciding && state.config.finalSet == FinalSetMode.ADVANTAGE)
        return when {
            gamesA >= GAMES_TO_WIN_SET && gamesA - gamesB >= 2 -> finishSet(state, SetScore(gamesA, gamesB))
            gamesB >= GAMES_TO_WIN_SET && gamesB - gamesA >= 2 -> finishSet(state, SetScore(gamesA, gamesB))
            gamesA == GAMES_TO_WIN_SET && gamesB == GAMES_TO_WIN_SET && tiebreakAllowed ->
                state.copy(gamesA = gamesA, gamesB = gamesB, pointsA = 0, pointsB = 0, inTiebreak = true)
            else -> state.copy(gamesA = gamesA, gamesB = gamesB, pointsA = 0, pointsB = 0)
        }
    }

    /** Camp qui gagne le jeu avec ces points bruts, ou null si le jeu continue. */
    internal fun gameWinner(pointsA: Int, pointsB: Int, goldenPoint: Boolean): Int? {
        if (goldenPoint) {
            // À 3-3 (40-40), le point suivant est décisif : le premier à 4 points gagne.
            return when {
                pointsA >= 4 -> 0
                pointsB >= 4 -> 1
                else -> null
            }
        }
        if (maxOf(pointsA, pointsB) >= 4 && kotlin.math.abs(pointsA - pointsB) >= 2) {
            return if (pointsA > pointsB) 0 else 1
        }
        return null
    }

    /** Vrai si le set que l'on va jouer est le set décisif (chaque camp à un set de la victoire). */
    internal fun isDecidingSet(config: RacketConfig, setsWonA: Int, setsWonB: Int): Boolean =
        setsWonA == config.setsToWin - 1 && setsWonB == config.setsToWin - 1

    private fun startsWithSuper(config: RacketConfig, setsWonA: Int, setsWonB: Int): Boolean =
        config.finalSet == FinalSetMode.SUPER_TIEBREAK && isDecidingSet(config, setsWonA, setsWonB)

    private fun finishSet(state: RacketState, set: SetScore): RacketState {
        val sets = state.sets + set
        val wonA = sets.count { it.winner == 0 }
        val wonB = sets.count { it.winner == 1 }
        val winner = when {
            wonA >= state.config.setsToWin -> 0
            wonB >= state.config.setsToWin -> 1
            else -> null
        }
        val superNext = winner == null && startsWithSuper(state.config, wonA, wonB)
        return RacketState(
            config = state.config,
            sets = sets,
            inTiebreak = superNext,
            superTiebreak = superNext,
            winner = winner
        )
    }

    /** Camp (0 ou 1) d'un joueur : la première moitié de la liste forme le camp 1 (1 contre 1, 2 contre 2, 2 contre 1). */
    fun sideOf(playerIndex: Int, playerCount: Int): Int = if (playerIndex < (playerCount + 1) / 2) 0 else 1

    /** Joueurs d'un camp, dans l'ordre de la liste. */
    fun playersOfSide(side: Int, playerCount: Int): List<Int> =
        (0 until playerCount).filter { sideOf(it, playerCount) == side }

    /**
     * Façons de former les camps avec [playerCount] joueurs, chacune donnée par les index des joueurs
     * du camp 1 (le plus nombreux s'il y a une différence). Quand les camps sont de même taille, deux
     * répartitions qui ne diffèrent que par l'échange des camps comptent pour une : on garde celle
     * où le joueur 0 est dans le camp 1. 2 joueurs : 1 façon ; 3 joueurs : 3 ; 4 joueurs : 3.
     */
    fun teamSplits(playerCount: Int): List<List<Int>> {
        val size = (playerCount + 1) / 2
        fun combos(from: Int, left: Int): List<List<Int>> = when {
            left == 0 -> listOf(emptyList())
            from >= playerCount -> emptyList()
            else -> combos(from + 1, left - 1).map { listOf(from) + it } + combos(from + 1, left)
        }
        val all = combos(0, size)
        return if (playerCount % 2 == 0) all.filter { 0 in it } else all
    }

    /** Nouvel ordre des joueurs (anciens index) pour que ceux de [side0] forment le camp 1 : eux d'abord, puis les autres. */
    fun orderForSide0(playerCount: Int, side0: List<Int>): List<Int> =
        side0 + (0 until playerCount).filter { it !in side0 }

    /** Sets gagnés par chaque joueur (ceux d'un même camp ont le même nombre). */
    fun setsByPlayer(state: RacketState, playerCount: Int): List<Int> =
        (0 until playerCount).map { state.setsWon(sideOf(it, playerCount)) }

    /**
     * Score en chiffres, sans mot (donc sans traduction) : « 6-4 3-6 7-6(4) », le super tie-break entre
     * crochets « [10-7] », puis les jeux du set en cours s'il y en a un (« 2-1 »). Chaîne vide avant le premier jeu.
     */
    fun scoreLine(state: RacketState): String {
        val parts = state.sets.map { set ->
            when {
                set.superTiebreak -> "[${set.a}-${set.b}]"
                set.tiebreakLoser != null -> "${set.a}-${set.b}(${set.tiebreakLoser})"
                else -> "${set.a}-${set.b}"
            }
        }.toMutableList()
        if (state.winner == null) {
            if (state.superTiebreak) {
                if (state.pointsA + state.pointsB > 0) parts.add("[${state.pointsA}-${state.pointsB}]")
            } else if (state.gamesA + state.gamesB > 0) {
                parts.add("${state.gamesA}-${state.gamesB}")
            }
        }
        return parts.joinToString(" ")
    }
}
