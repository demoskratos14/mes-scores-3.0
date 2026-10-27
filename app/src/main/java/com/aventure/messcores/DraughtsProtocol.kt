package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table de dames, à afficher aux joueurs. */
internal enum class DraughtsNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages des dames chacun sur son téléphone (voir [DraughtsOnline]). L'organisateur tient la partie et envoie
 * son état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface DrMessage {
    /** Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé. */
    data class Table(val names: List<String>, val seat: Int, val match: DraughtsMatch, val notice: DraughtsNotice) : DrMessage

    /** Invité → organisateur : je joue ce coup (la case de départ puis chaque case d'arrivée). */
    data class Move(val path: List<Int>) : DrMessage

    /** Invité → organisateur : j'abandonne la partie. */
    data object Resign : DrMessage

    /** Invité → organisateur : partie suivante, une fois la partie finie. */
    data object NewRound : DrMessage
}

internal fun DraughtsMatch.toJson(): JSONObject = JSONObject()
    .put("board", board).put("starter", starter).put("turn", turn).put("quiet", quiet).put("result", result)
    .put("last", JSONArray().also { array -> last.forEach { array.put(it) } })
    .put("wa", winsA).put("wb", winsB).put("d", draws)

/** Séance lue depuis son JSON ; une valeur absurde est ramenée à une valeur permise. */
internal fun draughtsMatchFromJson(json: JSONObject): DraughtsMatch {
    val last = json.optJSONArray("last")
    return DraughtsMatch(
        board = Draughts.format(Draughts.parse(json.getString("board"))),
        starter = json.getInt("starter").coerceIn(0, 1),
        turn = json.getInt("turn").coerceIn(Draughts.WHITE, Draughts.BLACK),
        quiet = json.getInt("quiet").coerceIn(0, Draughts.QUIET_LIMIT),
        result = json.getInt("result").coerceIn(Draughts.ONGOING, Draughts.DRAW),
        last = if (last == null) emptyList() else (0 until minOf(last.length(), MAX_PATH)).map { last.getInt(it) }
            .filter { it in 0 until Draughts.CELLS },
        winsA = json.getInt("wa").coerceAtLeast(0),
        winsB = json.getInt("wb").coerceAtLeast(0),
        draws = json.getInt("d").coerceAtLeast(0)
    )
}

// Une suite de prises ne dépasse jamais 20 pièces : un chemin plus long est illisible.
private const val MAX_PATH = 21

internal fun DrMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is DrMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("match", match.toJson()).put("notice", notice.name)
        is DrMessage.Move -> json.put("k", "move").put("path", JSONArray().also { array -> path.forEach { array.put(it) } })
        DrMessage.Resign -> json.put("k", "resign")
        DrMessage.NewRound -> json.put("k", "new")
    }
    return json
}

internal fun drMessageFromJson(json: JSONObject): DrMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        DrMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat"),
            match = draughtsMatchFromJson(json.getJSONObject("match")),
            notice = DraughtsNotice.valueOf(json.getString("notice"))
        )
    }
    "move" -> {
        val path = json.getJSONArray("path")
        DrMessage.Move((0 until minOf(path.length(), MAX_PATH)).map { path.getInt(it) })
    }
    "resign" -> DrMessage.Resign
    "new" -> DrMessage.NewRound
    else -> null
}
