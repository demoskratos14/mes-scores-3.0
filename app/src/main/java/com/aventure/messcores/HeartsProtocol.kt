package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages de Cœurs chacun sur son téléphone (voir [HeartsOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface HeartsMsg {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: HeartsTable, val seat: Int, val taken: List<Int>) : HeartsMsg

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : HeartsMsg

    /** Invité → organisateur : je passe ces 3 cartes. */
    data class Pass(val cards: List<Int>) : HeartsMsg

    /** Invité → organisateur : je pose cette carte. */
    data class Play(val card: Int) : HeartsMsg

    /** Invité → organisateur : j'empoche le pli complet que j'ai gagné. */
    data object Collect : HeartsMsg

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : HeartsMsg
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun HeartsTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("ph", phase.name).put("c", current).put("po", passOffset)
    .put("pa", JSONArray().also { array -> passed.forEach { array.put(if (it) 1 else 0) } })
    .put("tr", JSONArray().also { array -> trick.forEach { array.put(JSONArray().put(it.player).put(it.card)) } })
    .put("tw", trickWinner).put("tp", tricksPlayed).put("hb", heartsBroken)
    .put("ro", roundOver).put("st", settled).put("sd", started)
    .put("hs", handSizes.toJson()).put("pt", points.toJson()).put("mo", moonShooter)
    .put("hand", hand.toJson()).put("rc", received.toJson())

private fun heartsTableFromJson(json: JSONObject): HeartsTable {
    val trick = json.getJSONArray("tr")
    return HeartsTable(
        playerCount = json.getInt("n"),
        roundNumber = json.getInt("r"),
        phase = HeartsPhase.valueOf(json.getString("ph")),
        current = json.getInt("c"),
        passOffset = json.getInt("po"),
        passed = json.getJSONArray("pa").ints().map { it != 0 },
        trick = (0 until trick.length()).map { index ->
            val play = trick.getJSONArray(index)
            HeartsPlay(play.getInt(0), play.getInt(1))
        },
        trickWinner = json.getInt("tw"),
        tricksPlayed = json.getInt("tp"),
        heartsBroken = json.getBoolean("hb"),
        roundOver = json.getBoolean("ro"),
        settled = json.getBoolean("st"),
        started = json.getBoolean("sd"),
        handSizes = json.getJSONArray("hs").ints(),
        points = json.getJSONArray("pt").ints(),
        moonShooter = json.getInt("mo"),
        hand = json.getJSONArray("hand").ints(),
        received = json.getJSONArray("rc").ints()
    )
}

internal fun HeartsMsg.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is HeartsMsg.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is HeartsMsg.Claim -> json.put("k", "claim").put("seat", seat)
        is HeartsMsg.Pass -> json.put("k", "pass").put("cards", cards.toJson())
        is HeartsMsg.Play -> json.put("k", "play").put("card", card)
        HeartsMsg.Collect -> json.put("k", "collect")
        HeartsMsg.Next -> json.put("k", "next")
    }
    return json
}

internal fun heartsMsgFromJson(json: JSONObject): HeartsMsg? = when (json.getString("k")) {
    "view" -> HeartsMsg.View(heartsTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> HeartsMsg.Claim(json.getInt("seat"))
    "pass" -> HeartsMsg.Pass(json.getJSONArray("cards").ints())
    "play" -> HeartsMsg.Play(json.getInt("card"))
    "collect" -> HeartsMsg.Collect
    "next" -> HeartsMsg.Next
    else -> null
}
