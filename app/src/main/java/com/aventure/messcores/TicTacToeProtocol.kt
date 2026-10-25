package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table de Morpion, à afficher aux joueurs. */
internal enum class TicTacToeNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du Morpion chacun sur son téléphone (voir [TicTacToeOnline]). L'organisateur tient la partie et envoie
 * son état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface TttMessage {
    /** Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé. */
    data class Table(val names: List<String>, val seat: Int, val match: TicTacToeMatch, val notice: TicTacToeNotice) : TttMessage

    /** Invité → organisateur : je joue la case [index] (0 à 8). */
    data class Move(val index: Int) : TttMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie. */
    data object NewRound : TttMessage
}

internal fun TttMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is TttMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("board", match.board).put("starter", match.starter)
            .put("wx", match.winsX).put("wo", match.winsO).put("d", match.draws).put("notice", notice.name)
        is TttMessage.Move -> json.put("k", "move").put("i", index)
        TttMessage.NewRound -> json.put("k", "new")
    }
    return json
}

internal fun tttMessageFromJson(json: JSONObject): TttMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        TttMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat"),
            match = TicTacToeMatch(
                // Une grille illisible redevient vide ; un premier joueur ou un score absurde est ramené à une valeur permise.
                board = TicTacToe.format(TicTacToe.parse(json.getString("board"))),
                starter = json.getInt("starter").coerceIn(0, 1),
                winsX = json.getInt("wx").coerceAtLeast(0),
                winsO = json.getInt("wo").coerceAtLeast(0),
                draws = json.getInt("d").coerceAtLeast(0)
            ),
            notice = TicTacToeNotice.valueOf(json.getString("notice"))
        )
    }
    "move" -> TttMessage.Move(json.getInt("i"))
    "new" -> TttMessage.NewRound
    else -> null
}
