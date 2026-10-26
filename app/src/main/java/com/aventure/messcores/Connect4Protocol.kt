package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table de Puissance 4, à afficher aux joueurs. */
internal enum class Connect4Notice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du Puissance 4 chacun sur son téléphone (voir [Connect4Online]). L'organisateur tient la partie et envoie
 * son état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface C4Message {
    /** Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé. */
    data class Table(val names: List<String>, val seat: Int, val match: Connect4Match, val notice: Connect4Notice) : C4Message

    /** Invité → organisateur : je lâche un jeton dans la colonne [column] (0 à 6). */
    data class Drop(val column: Int) : C4Message

    /** Invité → organisateur : manche suivante, une fois la manche finie. */
    data object NewRound : C4Message
}

internal fun C4Message.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is C4Message.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("board", match.board).put("starter", match.starter)
            .put("wr", match.winsRed).put("wy", match.winsYellow).put("d", match.draws).put("notice", notice.name)
        is C4Message.Drop -> json.put("k", "drop").put("c", column)
        C4Message.NewRound -> json.put("k", "new")
    }
    return json
}

internal fun c4MessageFromJson(json: JSONObject): C4Message? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        C4Message.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat"),
            match = Connect4Match(
                // Une grille illisible redevient vide ; un premier joueur ou un score absurde est ramené à une valeur permise.
                board = Connect4.format(Connect4.parse(json.getString("board"))),
                starter = json.getInt("starter").coerceIn(0, 1),
                winsRed = json.getInt("wr").coerceAtLeast(0),
                winsYellow = json.getInt("wy").coerceAtLeast(0),
                draws = json.getInt("d").coerceAtLeast(0)
            ),
            notice = Connect4Notice.valueOf(json.getString("notice"))
        )
    }
    "drop" -> C4Message.Drop(json.getInt("c"))
    "new" -> C4Message.NewRound
    else -> null
}
