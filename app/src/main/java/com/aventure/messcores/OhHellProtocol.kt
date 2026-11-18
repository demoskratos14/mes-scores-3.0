package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages d'Oh Hell chacun sur son téléphone (voir [OhHellOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface OhHellMsg {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: OhHellTable, val seat: Int, val taken: List<Int>) : OhHellMsg

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : OhHellMsg

    /** Invité → organisateur : j'annonce ce nombre de plis. */
    data class Bid(val value: Int) : OhHellMsg

    /** Invité → organisateur : je pose cette carte. */
    data class Play(val card: Int) : OhHellMsg

    /** Invité → organisateur : j'empoche le pli complet que j'ai gagné. */
    data object Collect : OhHellMsg

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : OhHellMsg
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun OhHellTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("cc", cardCount).put("ph", phase.name).put("c", current).put("tm", trump)
    .put("bd", bids.toJson()).put("tn", tricksWon.toJson())
    .put("tr", JSONArray().also { array -> trick.forEach { array.put(JSONArray().put(it.player).put(it.card)) } })
    .put("tw", trickWinner).put("tp", tricksPlayed)
    .put("ro", roundOver).put("st", settled).put("sd", started)
    .put("hs", handSizes.toJson()).put("pt", points.toJson()).put("hand", hand.toJson())

private fun ohHellTableFromJson(json: JSONObject): OhHellTable {
    val trick = json.getJSONArray("tr")
    return OhHellTable(
        playerCount = json.getInt("n"),
        roundNumber = json.getInt("r"),
        cardCount = json.getInt("cc"),
        phase = OhHellPhase.valueOf(json.getString("ph")),
        current = json.getInt("c"),
        trump = json.getInt("tm"),
        bids = json.getJSONArray("bd").ints(),
        tricksWon = json.getJSONArray("tn").ints(),
        trick = (0 until trick.length()).map { index ->
            val play = trick.getJSONArray(index)
            OhHellPlay(play.getInt(0), play.getInt(1))
        },
        trickWinner = json.getInt("tw"),
        tricksPlayed = json.getInt("tp"),
        roundOver = json.getBoolean("ro"),
        settled = json.getBoolean("st"),
        started = json.getBoolean("sd"),
        handSizes = json.getJSONArray("hs").ints(),
        points = json.getJSONArray("pt").ints(),
        hand = json.getJSONArray("hand").ints()
    )
}

internal fun OhHellMsg.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is OhHellMsg.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is OhHellMsg.Claim -> json.put("k", "claim").put("seat", seat)
        is OhHellMsg.Bid -> json.put("k", "bid").put("value", value)
        is OhHellMsg.Play -> json.put("k", "play").put("card", card)
        OhHellMsg.Collect -> json.put("k", "collect")
        OhHellMsg.Next -> json.put("k", "next")
    }
    return json
}

internal fun ohHellMsgFromJson(json: JSONObject): OhHellMsg? = when (json.getString("k")) {
    "view" -> OhHellMsg.View(ohHellTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> OhHellMsg.Claim(json.getInt("seat"))
    "bid" -> OhHellMsg.Bid(json.getInt("value"))
    "play" -> OhHellMsg.Play(json.getInt("card"))
    "collect" -> OhHellMsg.Collect
    "next" -> OhHellMsg.Next
    else -> null
}
