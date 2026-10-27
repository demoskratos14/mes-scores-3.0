package com.aventure.messcores

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

/**
 * Une séance d'échecs entre deux joueurs : la manche en cours (les coups joués), qui joue les blancs, le score et
 * les fins décidées hors de l'échiquier (abandon, nulle d'un commun accord). Sert aux échecs sur un seul téléphone
 * comme à deux (voir [ChessOnline]) : l'organisateur la tient, l'invité en reçoit une copie à chaque changement.
 *
 * Les places sont 0 (premier joueur, l'organisateur en ligne) et 1 (deuxième joueur, l'invité). Les couleurs
 * s'échangent à chaque manche : [whiteSeat] est la place qui joue les blancs.
 */
internal data class ChessMatch(
    /** Les coups de la manche, en notation de coordonnées séparés par des espaces (« e2e4 e7e5 »). */
    val moves: String = "",
    val whiteSeat: Int = 0,
    val wins0: Int = 0,
    val wins1: Int = 0,
    val draws: Int = 0,
    /** Place du joueur qui a abandonné la manche, ou -1. */
    val resigned: Int = -1,
    /** Vrai si la manche s'est finie par une nulle d'un commun accord. */
    val agreed: Boolean = false,
    /** Place du joueur qui propose la nulle et attend la réponse de l'autre, ou -1. */
    val drawOffer: Int = -1
) {
    /** La manche en cours, rejouée à partir des coups. */
    val game: ChessGame by lazy { ChessGame.fromMoves(moves) }

    val result: ChessResult?
        get() = when {
            resigned in 0..1 -> ChessResult(winnerWhite = (1 - resigned) == whiteSeat, reason = ChessReason.RESIGNATION)
            agreed -> ChessResult(winnerWhite = null, reason = ChessReason.AGREEMENT)
            else -> game.result
        }

    val isOver: Boolean get() = result != null

    /** Place du joueur à qui c'est de jouer. */
    val currentSeat: Int get() = if (game.position.whiteToMove) whiteSeat else 1 - whiteSeat

    /** Place du gagnant de la manche finie, ou -1 pour une manche nulle (ou pas finie). */
    val winnerSeat: Int
        get() = when (result?.winnerWhite) {
            true -> whiteSeat
            false -> 1 - whiteSeat
            null -> -1
        }

    /** Vrai si la place [seat] joue les blancs dans cette manche. */
    fun playsWhite(seat: Int): Boolean = seat == whiteSeat

    /** La séance après le coup de [seat], ou null si ce n'est pas son tour ou si le coup n'est pas permis. */
    fun play(move: ChessMove, seat: Int): ChessMatch? {
        if (isOver || seat != currentSeat) return null
        val next = game.play(move) ?: return null
        val played = next.moves.last().toText()
        return copy(
            moves = if (moves.isEmpty()) played else "$moves $played",
            // Jouer sans répondre à la proposition de nulle de l'adversaire la refuse.
            drawOffer = if (drawOffer == 1 - seat) -1 else drawOffer
        ).scored()
    }

    /** La séance après l'abandon de [seat], ou null si la manche est finie. */
    fun resign(seat: Int): ChessMatch? {
        if (isOver || seat !in 0..1) return null
        return copy(resigned = seat, drawOffer = -1).scored()
    }

    /** La séance quand [seat] propose la nulle, ou null s'il y a déjà une proposition ou si la manche est finie. */
    fun offerDraw(seat: Int): ChessMatch? {
        if (isOver || seat !in 0..1 || drawOffer != -1) return null
        return copy(drawOffer = seat)
    }

    /** La séance quand [seat] répond à la proposition de nulle de l'autre joueur, ou null s'il n'y en a pas. */
    fun answerDraw(seat: Int, accept: Boolean): ChessMatch? {
        if (isOver || seat !in 0..1 || drawOffer != 1 - seat) return null
        return if (accept) copy(agreed = true, drawOffer = -1).scored() else copy(drawOffer = -1)
    }

    /** La séance quand les deux joueurs, sur le même téléphone, s'accordent sur la nulle. */
    fun agreeDraw(): ChessMatch? {
        if (isOver) return null
        return copy(agreed = true, drawOffer = -1).scored()
    }

    /** Manche suivante (les couleurs s'échangent), ou null tant que la manche n'est pas finie. */
    fun newRound(): ChessMatch? {
        if (!isOver) return null
        return copy(moves = "", whiteSeat = 1 - whiteSeat, resigned = -1, agreed = false, drawOffer = -1)
    }

    // Ajoute la manche qui vient de se finir au score ; à appeler une seule fois, sur l'état qui vient de la finir.
    private fun scored(): ChessMatch {
        if (result == null) return this
        return when (winnerSeat) {
            0 -> copy(wins0 = wins0 + 1)
            1 -> copy(wins1 = wins1 + 1)
            else -> copy(draws = draws + 1)
        }
    }
}

/** Garde la séance quand l'écran tourne (échecs sur un seul téléphone). */
internal val ChessMatchSaver: Saver<ChessMatch, Any> = listSaver(
    save = { listOf(it.moves, it.whiteSeat, it.wins0, it.wins1, it.draws, it.resigned, it.agreed, it.drawOffer) },
    restore = {
        ChessMatch(
            moves = it[0] as String,
            whiteSeat = it[1] as Int,
            wins0 = it[2] as Int,
            wins1 = it[3] as Int,
            draws = it[4] as Int,
            resigned = it[5] as Int,
            agreed = it[6] as Boolean,
            drawOffer = it[7] as Int
        )
    }
)
