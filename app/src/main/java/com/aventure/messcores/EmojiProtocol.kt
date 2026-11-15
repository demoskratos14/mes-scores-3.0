package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table de la Chasse à l'émoji, à afficher aux joueurs. */
internal enum class EmojiNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages de la Chasse à l'émoji chacun sur son téléphone (voir [EmojiOnline]). L'organisateur tient la partie et envoie
 * son état complet à chaque invité après chaque changement ; les invités ne lui envoient que leurs actions.
 *
 * Le plateau n'est jamais envoyé : seule la graine l'est ([EmojiMessage.Table.seed]), et chaque téléphone en tire le même
 * plateau (voir [EmojiHunt.generate]). Chaque téléphone mesure lui-même le temps de son joueur depuis l'affichage du
 * plateau : un léger décalage d'envoi entre les téléphones ne change rien.
 */
internal sealed interface EmojiMessage {
    /**
     * Organisateur → invité : les joueurs à la table, la place [seat] du destinataire, la séance et ce qui s'est passé.
     * Si [running], la manche [round] est lancée : chaque téléphone attend [delayMs] millisecondes puis affiche le
     * plateau tiré de [seed] au niveau [round].
     */
    data class Table(
        val names: List<String>,
        val seat: Int,
        val match: EmojiGame,
        val notice: EmojiNotice,
        val round: Int,
        val seed: Long,
        val delayMs: Long,
        val running: Boolean
    ) : EmojiMessage

    /** Invité → organisateur : mon résultat à la manche [round] (un temps en ms, ou [EmojiHunt.MISS]). */
    data class Result(val round: Int, val timeMs: Int) : EmojiMessage

    /** Invité → organisateur : lancer la manche suivante. */
    data object Start : EmojiMessage

    /** Invité → organisateur : nouvelle partie, une fois la partie finie. */
    data object NewMatch : EmojiMessage
}

internal fun EmojiMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is EmojiMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat)
            .put("w", JSONArray(match.wins)).put("t", JSONArray(match.totalMs)).put("c", JSONArray(match.timedRounds))
            .put("res", JSONArray(match.lastResults)).put("last", match.lastWinner).put("draw", match.lastDraw)
            .put("notice", notice.name).put("round", round).put("seed", seed).put("delay", delayMs).put("run", running)
        is EmojiMessage.Result -> json.put("k", "result").put("round", round).put("ms", timeMs)
        EmojiMessage.Start -> json.put("k", "start")
        EmojiMessage.NewMatch -> json.put("k", "new")
    }
    return json
}

// Bornes d'un message reçu : de quoi tenir une très longue séance, sans laisser passer de valeur absurde.
private const val MAX_TIMED_ROUNDS = 10_000
private const val MAX_TOTAL_MS = MAX_TIMED_ROUNDS * EmojiHunt.MAX_RESULT_MS

/** Les [size] premiers entiers du tableau [key], chacun ramené entre [min] et [max] (0 s'il manque). */
private fun JSONObject.emojiInts(key: String, size: Int, min: Int, max: Int): List<Int> {
    val array = optJSONArray(key)
    return List(size) { i -> (array?.optInt(i, 0) ?: 0).coerceIn(min, max) }
}

internal fun emojiMessageFromJson(json: JSONObject): EmojiMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        val count = names.length().coerceAtMost(EmojiHunt.MAX_PLAYERS)
        val hasResults = json.optJSONArray("res")?.length() == count
        EmojiMessage.Table(
            names = (0 until count).map { names.getString(it) },
            seat = json.getInt("seat").coerceIn(0, (count - 1).coerceAtLeast(0)),
            // Un score, un joueur ou une attente absurde est ramené à une valeur permise.
            match = EmojiGame(
                wins = json.emojiInts("w", count, 0, EmojiHunt.TARGET),
                totalMs = json.emojiInts("t", count, 0, MAX_TOTAL_MS),
                timedRounds = json.emojiInts("c", count, 0, MAX_TIMED_ROUNDS),
                lastResults = if (hasResults) json.emojiInts("res", count, EmojiHunt.MISS, EmojiHunt.MAX_RESULT_MS) else emptyList(),
                lastWinner = json.optInt("last", EmojiGame.NONE).coerceIn(EmojiGame.NONE, count - 1),
                lastDraw = json.optBoolean("draw", false)
            ),
            notice = EmojiNotice.valueOf(json.getString("notice")),
            round = json.getInt("round").coerceAtLeast(0),
            seed = json.getLong("seed"),
            delayMs = json.getLong("delay").coerceIn(0L, EmojiHunt.MAX_DELAY_MS),
            running = json.getBoolean("run")
        )
    }
    "result" -> EmojiMessage.Result(json.getInt("round"), json.getInt("ms"))
    "start" -> EmojiMessage.Start
    "new" -> EmojiMessage.NewMatch
    else -> null
}
