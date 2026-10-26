package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table du jeu des allumettes, à afficher aux joueurs. */
internal enum class NimNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du jeu des allumettes chacun sur son téléphone (voir [NimOnline]). L'organisateur tient la partie et envoie
 * son état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface NimMessage {
    /** Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé. */
    data class Table(val names: List<String>, val seat: Int, val match: NimMatch, val notice: NimNotice) : NimMessage

    /** Invité → organisateur : je retire [count] allumettes (1 à 3). */
    data class Take(val count: Int) : NimMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie. */
    data object NewRound : NimMessage
}

internal fun NimMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is NimMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("left", match.remaining).put("turn", match.turn).put("starter", match.starter)
            .put("loser", match.loser).put("w1", match.wins1).put("w2", match.wins2).put("notice", notice.name)
        is NimMessage.Take -> json.put("k", "take").put("n", count)
        NimMessage.NewRound -> json.put("k", "new")
    }
    return json
}

internal fun nimMessageFromJson(json: JSONObject): NimMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        NimMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat"),
            // Un tas, un joueur ou un score absurde est ramené à une valeur permise.
            match = json.getInt("left").coerceIn(0, Nim.START).let { left ->
                NimMatch(
                    remaining = left,
                    turn = json.getInt("turn").coerceIn(1, 2),
                    starter = json.getInt("starter").coerceIn(0, 1),
                    // Le perdant n'existe que si le tas est vide.
                    loser = if (left == 0) json.getInt("loser").coerceIn(1, 2) else 0,
                    wins1 = json.getInt("w1").coerceAtLeast(0),
                    wins2 = json.getInt("w2").coerceAtLeast(0)
                )
            },
            notice = NimNotice.valueOf(json.getString("notice"))
        )
    }
    "take" -> NimMessage.Take(json.getInt("n"))
    "new" -> NimMessage.NewRound
    else -> null
}
