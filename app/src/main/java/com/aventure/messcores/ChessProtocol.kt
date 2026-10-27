package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table d'échecs, à afficher aux joueurs. */
internal enum class ChessNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages des échecs chacun sur son téléphone (voir [ChessOnline]). L'organisateur tient la partie et envoie son
 * état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface ChessMessage {
    /** Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé. */
    data class Table(val names: List<String>, val seat: Int, val match: ChessMatch, val notice: ChessNotice) : ChessMessage

    /** Invité → organisateur : je joue [move], en notation de coordonnées (« e2e4 », « e7e8q »). */
    data class Move(val move: String) : ChessMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie. */
    data object NewRound : ChessMessage

    /** Invité → organisateur : j'abandonne la manche. */
    data object Resign : ChessMessage

    /** Invité → organisateur : je propose la nulle. */
    data object OfferDraw : ChessMessage

    /** Invité → organisateur : j'accepte ([accept]) ou je refuse la nulle proposée par l'organisateur. */
    data class AnswerDraw(val accept: Boolean) : ChessMessage
}

// Un coup illisible ou interdit arrête la lecture : on ne garde que les coups qui se rejouent.
private fun cleanMoves(text: String): String = ChessGame.fromMoves(text).moves.joinToString(" ") { it.toText() }

internal fun ChessMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is ChessMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("moves", match.moves).put("white", match.whiteSeat)
            .put("w0", match.wins0).put("w1", match.wins1).put("d", match.draws)
            .put("resigned", match.resigned).put("agreed", match.agreed).put("offer", match.drawOffer)
            .put("notice", notice.name)
        is ChessMessage.Move -> json.put("k", "move").put("m", move)
        ChessMessage.NewRound -> json.put("k", "new")
        ChessMessage.Resign -> json.put("k", "resign")
        ChessMessage.OfferDraw -> json.put("k", "offer")
        is ChessMessage.AnswerDraw -> json.put("k", "answer").put("accept", accept)
    }
    return json
}

internal fun chessMessageFromJson(json: JSONObject): ChessMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        ChessMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat"),
            match = ChessMatch(
                moves = cleanMoves(json.getString("moves")),
                whiteSeat = json.getInt("white").coerceIn(0, 1),
                wins0 = json.getInt("w0").coerceAtLeast(0),
                wins1 = json.getInt("w1").coerceAtLeast(0),
                draws = json.getInt("d").coerceAtLeast(0),
                resigned = json.getInt("resigned").coerceIn(-1, 1),
                agreed = json.getBoolean("agreed"),
                drawOffer = json.getInt("offer").coerceIn(-1, 1)
            ),
            notice = ChessNotice.valueOf(json.getString("notice"))
        )
    }
    "move" -> ChessMessage.Move(json.getString("m"))
    "new" -> ChessMessage.NewRound
    "resign" -> ChessMessage.Resign
    "offer" -> ChessMessage.OfferDraw
    "answer" -> ChessMessage.AnswerDraw(json.getBoolean("accept"))
    else -> null
}
