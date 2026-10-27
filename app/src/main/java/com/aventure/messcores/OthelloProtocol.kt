package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table d'Othello, à afficher aux joueurs. */
internal enum class OthelloNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages de l'Othello chacun sur son téléphone (voir [OthelloOnline]). L'organisateur tient la partie et envoie
 * son état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface OthMessage {
    /** Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé. */
    data class Table(val names: List<String>, val seat: Int, val match: OthelloMatch, val notice: OthelloNotice) : OthMessage

    /** Invité → organisateur : je joue la case [index] (0 à 63). */
    data class Move(val index: Int) : OthMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie. */
    data object NewRound : OthMessage
}

internal fun OthMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is OthMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("board", match.board).put("starter", match.starter)
            .put("turn", match.turn).put("last", match.last).put("passed", match.passed)
            .put("w0", match.wins0).put("w1", match.wins1).put("d", match.draws).put("notice", notice.name)
        is OthMessage.Move -> json.put("k", "move").put("i", index)
        OthMessage.NewRound -> json.put("k", "new")
    }
    return json
}

internal fun othMessageFromJson(json: JSONObject): OthMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        OthMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat"),
            match = OthelloMatch(
                // Une grille illisible redevient la position de départ ; une valeur absurde est ramenée à une valeur permise.
                board = Othello.format(Othello.parse(json.getString("board"))),
                starter = json.getInt("starter").coerceIn(0, 1),
                turn = json.getInt("turn").coerceIn(1, 2),
                last = json.getInt("last").coerceIn(-1, Othello.CELLS - 1),
                passed = json.getInt("passed").coerceIn(0, 2),
                wins0 = json.getInt("w0").coerceAtLeast(0),
                wins1 = json.getInt("w1").coerceAtLeast(0),
                draws = json.getInt("d").coerceAtLeast(0)
            ),
            notice = OthelloNotice.valueOf(json.getString("notice"))
        )
    }
    "move" -> OthMessage.Move(json.getInt("i"))
    "new" -> OthMessage.NewRound
    else -> null
}
