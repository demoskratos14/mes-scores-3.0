package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table, à afficher aux joueurs. */
internal enum class DuoNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages des jeux à deux joueurs chacun sur son téléphone (Gomoku, Pipopipette : voir [DuoOnline]). L'organisateur
 * tient la partie et envoie son état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface DuoMessage {
    /** Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance (texte, voir [DuoMatch.encode]) et ce qui s'est passé. */
    data class Table(val names: List<String>, val seat: Int, val match: String, val notice: DuoNotice) : DuoMessage

    /** Invité → organisateur : je joue [move] (la case ou le trait, selon le jeu). */
    data class Move(val move: Int) : DuoMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie. */
    data object NewRound : DuoMessage
}

internal fun DuoMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is DuoMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("match", match).put("notice", notice.name)
        is DuoMessage.Move -> json.put("k", "move").put("i", move)
        DuoMessage.NewRound -> json.put("k", "new")
    }
    return json
}

internal fun duoMessageFromJson(json: JSONObject): DuoMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        DuoMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat").coerceIn(0, 1),
            match = json.getString("match"),
            notice = DuoNotice.valueOf(json.getString("notice"))
        )
    }
    "move" -> DuoMessage.Move(json.getInt("i"))
    "new" -> DuoMessage.NewRound
    else -> null
}
