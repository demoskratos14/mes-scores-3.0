package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages du Président chacun sur son téléphone (voir [PresidentOnline]). L'organisateur tient la partie : il envoie à chaque
 * invité sa propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface PresMessage {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: PresidentTable, val seat: Int, val taken: List<Int>) : PresMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : PresMessage

    /** Invité → organisateur : je pose ces cartes. */
    data class Play(val cards: List<Int>) : PresMessage

    /** Invité → organisateur : je passe mon tour. */
    data object Pass : PresMessage

    /** Invité → organisateur : je rends ces cartes (échange du début de manche). */
    data class Give(val cards: List<Int>) : PresMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : PresMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun PresidentTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("ph", phase.name).put("c", current)
    .put("top", top.toJson()).put("tp", topPlayer).put("sk", skipped)
    .put("hs", handSizes.toJson()).put("o", order.toJson()).put("pt", points.toJson())
    .put("st", settled).put("sd", started)
    .put(
        "ex",
        JSONArray().also { array ->
            exchanges.forEach { e ->
                array.put(
                    JSONObject().put("h", e.high).put("l", e.low).put("s", e.size)
                        .put("th", e.toHigh.toJson()).put("tl", e.toLow.toJson()).put("d", e.done)
                )
            }
        }
    )
    .put("hand", hand.toJson()).put("g", giveCount)

private fun presidentTableFromJson(json: JSONObject): PresidentTable {
    val exchanges = json.getJSONArray("ex")
    return PresidentTable(
        playerCount = json.getInt("n"),
        roundNumber = json.getInt("r"),
        phase = PresidentPhase.valueOf(json.getString("ph")),
        current = json.getInt("c"),
        top = json.getJSONArray("top").ints(),
        topPlayer = json.getInt("tp"),
        skipped = json.getInt("sk"),
        handSizes = json.getJSONArray("hs").ints(),
        order = json.getJSONArray("o").ints(),
        points = json.getJSONArray("pt").ints(),
        settled = json.getBoolean("st"),
        started = json.getBoolean("sd"),
        exchanges = (0 until exchanges.length()).map { index ->
            val e = exchanges.getJSONObject(index)
            ExchangeView(
                high = e.getInt("h"),
                low = e.getInt("l"),
                size = e.getInt("s"),
                toHigh = e.getJSONArray("th").ints(),
                toLow = e.getJSONArray("tl").ints(),
                done = e.getBoolean("d")
            )
        },
        hand = json.getJSONArray("hand").ints(),
        giveCount = json.getInt("g")
    )
}

internal fun PresMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is PresMessage.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is PresMessage.Claim -> json.put("k", "claim").put("seat", seat)
        is PresMessage.Play -> json.put("k", "play").put("cards", cards.toJson())
        PresMessage.Pass -> json.put("k", "pass")
        is PresMessage.Give -> json.put("k", "give").put("cards", cards.toJson())
        PresMessage.Next -> json.put("k", "next")
    }
    return json
}

internal fun presMessageFromJson(json: JSONObject): PresMessage? = when (json.getString("k")) {
    "view" -> PresMessage.View(presidentTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> PresMessage.Claim(json.getInt("seat"))
    "play" -> PresMessage.Play(json.getJSONArray("cards").ints())
    "pass" -> PresMessage.Pass
    "give" -> PresMessage.Give(json.getJSONArray("cards").ints())
    "next" -> PresMessage.Next
    else -> null
}
