package com.aventure.messcores

import android.content.Context
import android.content.Intent

/**
 * Mise en forme du classement en texte, pour le partager (messages, mail…). Fonctions pures,
 * testées dans ResultTextTest.
 */
object ResultText {

    private fun medal(rank: Int): String = when (rank) {
        1 -> "🥇"
        2 -> "🥈"
        3 -> "🥉"
        else -> "$rank."
    }

    /**
     * Classement d'une partie. [totals] et [ranks] ont le même index que [players] ; les ex æquo
     * partagent le même rang. [winners] n'est renseigné que si la partie est terminée.
     */
    fun game(
        context: Context,
        gameName: String,
        players: List<String>,
        totals: List<Int>,
        ranks: List<Int>,
        finished: Boolean,
        winners: List<Int>
    ): String {
        val lines = mutableListOf<String>()
        lines.add(context.getString(if (finished) R.string.result_game_finished else R.string.result_game_ongoing, gameName))
        players.indices.sortedWith(compareBy({ ranks.getOrElse(it) { Int.MAX_VALUE } }, { it })).forEach { i ->
            lines.add(context.getString(R.string.result_player_total, medal(ranks.getOrElse(i) { 1 }), players[i], totals.getOrElse(i) { 0 }))
        }
        if (finished && winners.isNotEmpty()) {
            lines.add("")
            lines.add(
                context.getString(
                    if (winners.size > 1) R.string.result_winners else R.string.result_winner,
                    winners.joinToString(context.getString(R.string.result_and)) { players[it] }
                )
            )
        }
        lines.add("")
        lines.add(context.getString(R.string.result_footer))
        return lines.joinToString("\n")
    }

    /** Classement final d'un championnat : [podium] liste les index des participants, du 1er au 4e. */
    fun tournament(context: Context, participants: List<String>, podium: List<Int>): String {
        val lines = mutableListOf(context.getString(R.string.result_tournament_title))
        podium.forEachIndexed { place, index ->
            val name = participants.getOrNull(index) ?: "?"
            lines.add("${medal(place + 1)} $name")
        }
        lines.add("")
        lines.add(context.getString(R.string.result_footer))
        return lines.joinToString("\n")
    }

    /** Classement final de poules : [rows] vient de [TournamentState.standings] (les ex æquo partagent le rang). */
    fun poules(context: Context, participants: List<String>, rows: List<PouleRow>): String {
        val lines = mutableListOf(context.getString(R.string.result_poules_title))
        rows.forEach { row ->
            val name = participants.getOrNull(row.playerIndex) ?: "?"
            val wins = context.quantity(R.plurals.victories_count, row.wins)
            val matches = context.quantity(R.plurals.matches_count, row.played)
            lines.add(context.getString(R.string.result_poule_row, medal(row.rank), name, wins, matches))
        }
        lines.add("")
        lines.add(context.getString(R.string.result_footer))
        return lines.joinToString("\n")
    }

    /** Ouvre le menu de partage d'Android avec [text]. */
    fun share(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_ranking)))
    }
}
