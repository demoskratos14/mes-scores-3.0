package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table du Simon, à afficher aux joueurs. */
internal enum class SimonNotice { NONE, PLAYER_LEFT, GAME_CANCELLED, HOST_LEFT }

/** Les deux modes du Simon à plusieurs téléphones (les mêmes que sur un seul téléphone). */
internal enum class SimonMode {
    /** Chacun sa série : tout le monde joue en même temps sa propre suite, le meilleur score gagne. */
    SERIES,

    /** Chacun son tour : une seule suite pour tous, une erreur élimine, le dernier en jeu gagne. */
    TURNS
}

/**
 * Messages du Simon chacun sur son téléphone (voir [SimonOnline]). L'organisateur tient la partie et envoie son état
 * complet à chaque invité après chaque changement ; les invités ne lui envoient que leurs résultats.
 *
 * En mode [SimonMode.SERIES], chaque téléphone joue sa propre suite sans rien échanger : seul le score final est envoyé.
 * En mode [SimonMode.TURNS], la suite commune vit chez l'organisateur : elle est envoyée à tous, et le joueur dont c'est
 * le tour dit à l'organisateur s'il l'a reproduite en entier.
 */
internal sealed interface SimonMessage {
    /**
     * Organisateur → invité : les joueurs à la table, la place [seat] du destinataire et l'état de la partie.
     * [mode] est `null` tant que la partie n'est pas lancée. [round] numérote les étapes (une partie lancée, puis chaque
     * tour en « chacun son tour ») et sert à ignorer un résultat arrivé en retard.
     * [scores] : en « chacun sa série », le score de chaque place ([SimonOnline.PENDING] tant qu'elle joue).
     * [sequence], [eliminated], [current] et [lastOut] : la partie « chacun son tour » (voir [SimonTurns]).
     */
    data class Table(
        val names: List<String>,
        val seat: Int,
        val mode: SimonMode?,
        val round: Int,
        val scores: List<Int>,
        val sequence: List<Int>,
        val eliminated: List<Int>,
        val current: Int,
        val lastOut: Int,
        val notice: SimonNotice
    ) : SimonMessage

    /** Invité → organisateur : mon score à la fin de ma série ([SimonMode.SERIES]), à l'étape [round]. */
    data class SeriesDone(val round: Int, val score: Int) : SimonMessage

    /** Invité → organisateur : j'ai reproduit la suite en entier ([reproduced]) ou je me suis trompé, à l'étape [round] ([SimonMode.TURNS]). */
    data class TurnResult(val round: Int, val reproduced: Boolean) : SimonMessage
}

// Bornes d'un message reçu : de quoi tenir une très longue partie, sans laisser passer de valeur absurde.
private const val MAX_SEQUENCE = 2_000
private const val MAX_SCORE = 2_000

internal fun SimonMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is SimonMessage.Table -> json.put("k", "table")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("seat", seat)
            .put("mode", mode?.name ?: "NONE")
            .put("round", round)
            .put("scores", JSONArray(scores))
            .put("seq", JSONArray(sequence))
            .put("out", JSONArray(eliminated))
            .put("cur", current)
            .put("last", lastOut)
            .put("notice", notice.name)
        is SimonMessage.SeriesDone -> json.put("k", "done").put("round", round).put("score", score)
        is SimonMessage.TurnResult -> json.put("k", "turn").put("round", round).put("ok", reproduced)
    }
    return json
}

private fun JSONObject.simonInts(key: String): List<Int> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optInt(it, 0) }
}

internal fun simonMessageFromJson(json: JSONObject): SimonMessage? = when (json.getString("k")) {
    "table" -> {
        val names = json.getJSONArray("names")
        val count = names.length().coerceAtMost(Simon.MAX_PLAYERS)
        val mode = SimonMode.entries.firstOrNull { it.name == json.optString("mode") }
        SimonMessage.Table(
            names = (0 until count).map { names.getString(it) },
            seat = json.getInt("seat").coerceIn(0, (count - 1).coerceAtLeast(0)),
            mode = mode,
            round = json.getInt("round").coerceAtLeast(0),
            // Un score ou une touche absurde est ramené à une valeur permise.
            scores = json.simonInts("scores").take(count).map { it.coerceIn(SimonOnline.PENDING, MAX_SCORE) },
            sequence = json.simonInts("seq").take(MAX_SEQUENCE).map { it.coerceIn(0, Simon.PADS - 1) },
            eliminated = json.simonInts("out").filter { it in 0 until count }.distinct(),
            current = json.getInt("cur").coerceIn(0, (count - 1).coerceAtLeast(0)),
            lastOut = json.optInt("last", -1).coerceIn(-1, (count - 1).coerceAtLeast(-1)),
            notice = SimonNotice.entries.firstOrNull { it.name == json.optString("notice") } ?: SimonNotice.NONE
        )
    }
    "done" -> SimonMessage.SeriesDone(json.getInt("round"), json.getInt("score"))
    "turn" -> SimonMessage.TurnResult(json.getInt("round"), json.getBoolean("ok"))
    else -> null
}
