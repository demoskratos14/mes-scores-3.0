package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table des Ronds, à afficher aux joueurs. */
internal enum class CirclesNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages des Ronds chacun sur son téléphone, de deux à [Circles.MAX_PLAYERS] joueurs (voir [CirclesOnline]).
 * L'organisateur tient la partie et envoie son état complet à chaque invité après chaque changement ; les invités ne
 * lui envoient que le résultat de leur série.
 *
 * Tout le monde joue en même temps, mais chacun sa propre série, sur son téléphone : les ronds sont tirés au hasard
 * localement et rien n'est échangé pendant la série. Seule la longueur de la série est envoyée, à la fin.
 */
internal sealed interface CirclesMessage {
    /**
     * Organisateur → invité : les joueurs à la table, la place [seat] du destinataire et l'état de la partie.
     * [level] est `null` tant que la partie n'est pas lancée. [round] numérote les parties lancées et sert à ignorer un
     * résultat arrivé en retard. [scores] : la série de chaque place ([CirclesOnline.PENDING] tant qu'elle joue).
     */
    data class Table(
        val names: List<String>,
        val seat: Int,
        val level: CirclesLevel?,
        val round: Int,
        val scores: List<Int>,
        val notice: CirclesNotice
    ) : CirclesMessage

    /** Invité → organisateur : ma série de [streak] ronds, à la fin de ma série de la partie [round]. */
    data class RunDone(val round: Int, val streak: Int) : CirclesMessage
}

internal fun CirclesMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is CirclesMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat)
            .put("level", level?.name ?: "NONE")
            .put("round", round)
            .put("scores", JSONArray(scores))
            .put("notice", notice.name)
        is CirclesMessage.RunDone -> json.put("k", "done").put("round", round).put("streak", streak)
    }
    return json
}

internal fun circlesMessageFromJson(json: JSONObject): CirclesMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        val count = names.length().coerceAtMost(Circles.MAX_PLAYERS)
        val scores = json.optJSONArray("scores")
        CirclesMessage.Table(
            names = (0 until count).map { names.getString(it) },
            seat = json.getInt("seat").coerceIn(0, (count - 1).coerceAtLeast(0)),
            level = CirclesLevel.entries.firstOrNull { it.name == json.optString("level") },
            round = json.getInt("round").coerceAtLeast(0),
            // Une série absurde est ramenée à une valeur permise ; une place sans score reste en attente.
            scores = if (scores == null) emptyList() else (0 until minOf(scores.length(), count)).map {
                scores.optInt(it, CirclesOnline.PENDING).coerceIn(CirclesOnline.PENDING, CirclesOnline.MAX_STREAK)
            },
            notice = CirclesNotice.entries.firstOrNull { it.name == json.optString("notice") } ?: CirclesNotice.NONE
        )
    }
    "done" -> CirclesMessage.RunDone(json.getInt("round"), json.getInt("streak"))
    else -> null
}
