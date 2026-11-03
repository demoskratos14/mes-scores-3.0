package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table du Duel de réflexes, à afficher aux joueurs. */
internal enum class ReflexNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du Duel de réflexes chacun sur son téléphone, de deux à [Reflex.MAX_PLAYERS] joueurs (voir [ReflexOnline]).
 * L'organisateur tient la partie et envoie son état complet à chaque invité après chaque changement ; les invités ne lui
 * envoient que leurs actions.
 *
 * Chaque téléphone mesure lui-même le temps de réaction de son joueur, depuis son propre signal : un léger décalage
 * d'envoi entre les téléphones ne change donc rien, seul compte le temps de réaction.
 */
internal sealed interface ReflexMessage {
    /**
     * Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé.
     * Si [running], la manche [round] est lancée : chaque téléphone attend [delayMs] millisecondes puis donne le signal.
     */
    data class Table(
        val names: List<String>,
        val seat: Int,
        val match: ReflexGame,
        val notice: ReflexNotice,
        val round: Int,
        val delayMs: Long,
        val running: Boolean
    ) : ReflexMessage

    /** Invité → organisateur : mon résultat à la manche [round] (un temps en ms, [Reflex.EARLY] ou [Reflex.MISS]). */
    data class Result(val round: Int, val reactionMs: Int) : ReflexMessage

    /** Invité → organisateur : lancer la manche suivante (une fois la partie commencée). */
    data object Start : ReflexMessage

    /** Invité → organisateur : nouvelle partie, une fois la partie finie. */
    data object NewMatch : ReflexMessage
}

internal fun ReflexMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is ReflexMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat)
            .put("w", JSONArray(match.wins)).put("t", JSONArray(match.totalMs)).put("c", JSONArray(match.timedRounds))
            .put("res", JSONArray(match.lastResults)).put("last", match.lastWinner).put("draw", match.lastDraw)
            .put("notice", notice.name).put("round", round).put("delay", delayMs).put("run", running)
        is ReflexMessage.Result -> json.put("k", "result").put("round", round).put("ms", reactionMs)
        ReflexMessage.Start -> json.put("k", "start")
        ReflexMessage.NewMatch -> json.put("k", "new")
    }
    return json
}

// Bornes d'un message reçu : de quoi tenir une très longue séance, sans laisser passer de valeur absurde.
private const val MAX_TIMED_ROUNDS = 10_000
private const val MAX_TOTAL_MS = MAX_TIMED_ROUNDS * ReflexGame.MAX_REACTION_MS

/** Les [size] premiers entiers du tableau [key], chacun ramené entre [min] et [max] (0 s'il manque). */
private fun JSONObject.ints(key: String, size: Int, min: Int, max: Int): List<Int> {
    val array = optJSONArray(key)
    return List(size) { i -> (array?.optInt(i, 0) ?: 0).coerceIn(min, max) }
}

internal fun reflexMessageFromJson(json: JSONObject): ReflexMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        val count = names.length().coerceAtMost(Reflex.MAX_PLAYERS)
        val hasResults = json.optJSONArray("res")?.length() == count
        ReflexMessage.Table(
            names = (0 until count).map { names.getString(it) },
            seat = json.getInt("seat").coerceIn(0, (count - 1).coerceAtLeast(0)),
            // Un score, un joueur ou une attente absurde est ramené à une valeur permise.
            match = ReflexGame(
                wins = json.ints("w", count, 0, Reflex.TARGET),
                totalMs = json.ints("t", count, 0, MAX_TOTAL_MS),
                timedRounds = json.ints("c", count, 0, MAX_TIMED_ROUNDS),
                lastResults = if (hasResults) json.ints("res", count, Reflex.MISS, ReflexGame.MAX_REACTION_MS) else emptyList(),
                lastWinner = json.optInt("last", ReflexGame.NONE).coerceIn(ReflexGame.NONE, count - 1),
                lastDraw = json.optBoolean("draw", false)
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
