package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table de Pierre-feuille-ciseaux, à afficher aux joueurs. */
internal enum class RpsNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages de Pierre-feuille-ciseaux chacun sur son téléphone (voir [RockPaperScissorsOnline]). L'organisateur tient
 * la partie et envoie son état à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 */
internal sealed interface RpsMessage {
    /**
     * Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la partie et ce qui s'est passé.
     * Le choix de l'organisateur y est caché ([RpsMatch.maskedFor]) tant que l'invité n'a pas choisi lui aussi.
     */
    data class Table(val names: List<String>, val seat: Int, val match: RpsMatch, val notice: RpsNotice) : RpsMessage

    /** Invité → organisateur : mon choix (1 pierre, 2 feuille, 3 ciseaux, 4 lézard, 5 Spock ; les deux derniers seulement en version « lézard Spock »). */
    data class Pick(val choice: Int) : RpsMessage

    /** Invité → organisateur : manche suivante, ou nouvelle partie si celle-ci est finie. */
    data object Next : RpsMessage
}

internal fun RpsMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is RpsMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("m", match.encode()).put("notice", notice.name)
        is RpsMessage.Pick -> json.put("k", "pick").put("c", choice)
        RpsMessage.Next -> json.put("k", "next")
    }
    return json
}

internal fun rpsMessageFromJson(json: JSONObject): RpsMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        RpsMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat").coerceIn(0, 1),
            match = RpsMatch.decode(json.getString("m")),
            notice = RpsNotice.entries.firstOrNull { it.name == json.getString("notice") } ?: RpsNotice.NONE
        )
    }
    "pick" -> RpsMessage.Pick(json.getInt("c"))
    "next" -> RpsMessage.Next
    else -> null
}
