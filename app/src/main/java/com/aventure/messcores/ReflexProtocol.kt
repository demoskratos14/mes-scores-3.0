package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table du Duel de réflexes, à afficher aux joueurs. */
internal enum class ReflexNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du Duel de réflexes chacun sur son téléphone (voir [ReflexOnline]). L'organisateur tient la partie et envoie
 * son état complet à l'invité après chaque changement ; l'invité ne lui envoie que ses actions.
 *
 * Chaque téléphone mesure lui-même le temps de réaction de son joueur, depuis son propre signal : un léger décalage
 * d'envoi entre les deux téléphones ne change donc rien, seul compte le temps de réaction.
 */
internal sealed interface ReflexMessage {
    /**
     * Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé.
     * Si [running], la manche [round] est lancée : chaque téléphone attend [delayMs] millisecondes puis donne le signal.
     */
    data class Table(
        val names: List<String>,
        val seat: Int,
        val match: ReflexMatch,
        val notice: ReflexNotice,
        val round: Int,
        val delayMs: Long,
        val running: Boolean
    ) : ReflexMessage

    /** Invité → organisateur : mon résultat à la manche [round] (un temps en ms, [Reflex.EARLY] ou [Reflex.MISS]). */
    data class Result(val round: Int, val reactionMs: Int) : ReflexMessage

    /** Invité → organisateur : lancer la manche suivante. */
    data object Start : ReflexMessage

    /** Invité → organisateur : nouvelle partie, une fois la partie finie. */
    data object NewMatch : ReflexMessage
}

internal fun ReflexMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is ReflexMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat).put("w1", match.wins1).put("w2", match.wins2).put("last", match.lastWinner)
            .put("ms", match.lastReactionMs).put("early", match.falseStart).put("draw", match.lastDraw)
            .put("notice", notice.name).put("round", round).put("delay", delayMs).put("run", running)
        is ReflexMessage.Result -> json.put("k", "result").put("round", round).put("ms", reactionMs)
        ReflexMessage.Start -> json.put("k", "start")
        ReflexMessage.NewMatch -> json.put("k", "new")
    }
    return json
}

internal fun reflexMessageFromJson(json: JSONObject): ReflexMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        ReflexMessage.Table(
            names = (0 until names.length()).map { names.getString(it) },
            seat = json.getInt("seat"),
            // Un score, un joueur ou une attente absurde est ramené à une valeur permise.
            match = ReflexMatch(
                wins1 = json.getInt("w1").coerceIn(0, Reflex.TARGET),
                wins2 = json.getInt("w2").coerceIn(0, Reflex.TARGET),
                lastWinner = json.getInt("last").coerceIn(0, 2),
                lastReactionMs = json.getInt("ms").coerceIn(0, 9_999),
                falseStart = json.getBoolean("early"),
                lastDraw = json.getBoolean("draw")
            ),
            notice = ReflexNotice.valueOf(json.getString("notice")),
            round = json.getInt("round").coerceAtLeast(0),
            delayMs = json.getLong("delay").coerceIn(0L, Reflex.MAX_DELAY_MS),
            running = json.getBoolean("run")
        )
    }
    "result" -> ReflexMessage.Result(json.getInt("round"), json.getInt("ms"))
    "start" -> ReflexMessage.Start
    "new" -> ReflexMessage.NewMatch
    else -> null
}
