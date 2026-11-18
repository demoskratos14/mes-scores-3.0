package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages de Barbu chacun sur son téléphone (voir [BarbuOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface BarbuMsg {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: BarbuTable, val seat: Int, val taken: List<Int>) : BarbuMsg

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : BarbuMsg

    /** Invité → organisateur : j'annonce ce contrat (le numéro de [BarbuContract.ordinal]). */
    data class Choose(val contract: Int) : BarbuMsg

    /** Invité → organisateur : je pose cette carte. */
    data class Play(val card: Int) : BarbuMsg

    /** Invité → organisateur : j'empoche le pli complet que j'ai gagné. */
    data object Collect : BarbuMsg

    /** Invité → organisateur : donne suivante, une fois la donne finie et inscrite. */
    data object Next : BarbuMsg
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun BarbuTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("dc", declarer).put("ph", phase.name)
    .put("ct", contract?.ordinal ?: -1).put("c", current).put("us", usedMasks.toJson())
    .put("tr", JSONArray().also { array -> trick.forEach { array.put(JSONArray().put(it.player).put(it.card)) } })
    .put("tw", trickWinner).put("tp", tricksPlayed).put("tk", tricksWon.toJson())
    .put("ro", roundOver).put("st", settled).put("sd", started).put("fi", finished)
    .put("hs", handSizes.toJson()).put("pt", points.toJson()).put("hand", hand.toJson())
    .put("dl", dominoLow.toJson()).put("dh", dominoHigh.toJson()).put("db", dominoBase)
    .put("fo", finishOrder.toJson()).put("sk", skipped.toJson())

private fun barbuTableFromJson(json: JSONObject): BarbuTable {
    val trick = json.getJSONArray("tr")
    return BarbuTable(
        playerCount = json.getInt("n"),
        roundNumber = json.getInt("r"),
        declarer = json.getInt("dc"),
        phase = BarbuPhase.valueOf(json.getString("ph")),
        contract = BarbuContract.entries.getOrNull(json.getInt("ct")),
        current = json.getInt("c"),
        usedMasks = json.getJSONArray("us").ints(),
        trick = (0 until trick.length()).map { index ->
            val play = trick.getJSONArray(index)
            BarbuPlay(play.getInt(0), play.getInt(1))
        },
        trickWinner = json.getInt("tw"),
        tricksPlayed = json.getInt("tp"),
        tricksWon = json.getJSONArray("tk").ints(),
        roundOver = json.getBoolean("ro"),
        settled = json.getBoolean("st"),
        started = json.getBoolean("sd"),
        finished = json.getBoolean("fi"),
        handSizes = json.getJSONArray("hs").ints(),
        points = json.getJSONArray("pt").ints(),
        hand = json.getJSONArray("hand").ints(),
        dominoLow = json.getJSONArray("dl").ints(),
        dominoHigh = json.getJSONArray("dh").ints(),
        dominoBase = json.getInt("db"),
        finishOrder = json.getJSONArray("fo").ints(),
        skipped = json.getJSONArray("sk").ints()
    )
}

internal fun BarbuMsg.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is BarbuMsg.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is BarbuMsg.Claim -> json.put("k", "claim").put("seat", seat)
        is BarbuMsg.Choose -> json.put("k", "choose").put("contract", contract)
        is BarbuMsg.Play -> json.put("k", "play").put("card", card)
        BarbuMsg.Collect -> json.put("k", "collect")
        BarbuMsg.Next -> json.put("k", "next")
    }
    return json
}

internal fun barbuMsgFromJson(json: JSONObject): BarbuMsg? = when (json.getString("k")) {
    "view" -> BarbuMsg.View(barbuTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> BarbuMsg.Claim(json.getInt("seat"))
    "choose" -> BarbuMsg.Choose(json.getInt("contract"))
    "play" -> BarbuMsg.Play(json.getInt("card"))
    "collect" -> BarbuMsg.Collect
    "next" -> BarbuMsg.Next
    else -> null
}
