package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages du Yaniv chacun sur son téléphone (voir [YanivOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface YanivMessage {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: YanivTable, val seat: Int, val taken: List<Int>) : YanivMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : YanivMessage

    /** Invité → organisateur : je défausse ces cartes (une carte, une paire, un brelan ou une suite). */
    data class Discard(val cards: List<Int>) : YanivMessage

    /** Invité → organisateur : je pioche dans la pioche. */
    data object DrawStock : YanivMessage

    /** Invité → organisateur : je reprends cette carte dans la dernière défausse. */
    data class TakeDiscard(val card: Int) : YanivMessage

    /** Invité → organisateur : j'annonce « Yaniv ». */
    data object CallYaniv : YanivMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : YanivMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun List<List<Int>>.toNestedJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it.toJson()) } }

private fun JSONArray.nestedInts(): List<List<Int>> = (0 until length()).map { getJSONArray(it).ints() }

private fun YanivTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current).put("dr", drawing)
    .put("ld", lastDiscard.toJson()).put("sk", stockSize).put("hs", handSizes.toJson())
    .put("ro", roundOver).put("ca", caller).put("as", assafBy).put("w", winner)
    .put("st", settled).put("sd", started).put("pt", points.toJson())
    .put("fh", finalHands.toNestedJson()).put("hand", hand.toJson()).put("pe", pending.toJson())
    .put("cy", canCallYaniv).put("cs", canDrawFromStock)

private fun yanivTableFromJson(json: JSONObject): YanivTable = YanivTable(
    playerCount = json.getInt("n"),
    roundNumber = json.getInt("r"),
    current = json.getInt("c"),
    drawing = json.getBoolean("dr"),
    lastDiscard = json.getJSONArray("ld").ints(),
    stockSize = json.getInt("sk"),
    handSizes = json.getJSONArray("hs").ints(),
    roundOver = json.getBoolean("ro"),
    caller = json.getInt("ca"),
    assafBy = json.getInt("as"),
    winner = json.getInt("w"),
    settled = json.getBoolean("st"),
    started = json.getBoolean("sd"),
    points = json.getJSONArray("pt").ints(),
    finalHands = json.getJSONArray("fh").nestedInts(),
    hand = json.getJSONArray("hand").ints(),
    pending = json.getJSONArray("pe").ints(),
    canCallYaniv = json.getBoolean("cy"),
    canDrawFromStock = json.getBoolean("cs")
)

internal fun YanivMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is YanivMessage.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is YanivMessage.Claim -> json.put("k", "claim").put("seat", seat)
        is YanivMessage.Discard -> json.put("k", "discard").put("cards", cards.toJson())
        YanivMessage.DrawStock -> json.put("k", "stock")
        is YanivMessage.TakeDiscard -> json.put("k", "take").put("card", card)
        YanivMessage.CallYaniv -> json.put("k", "yaniv")
        YanivMessage.Next -> json.put("k", "next")
    }
    return json
}

internal fun yanivMessageFromJson(json: JSONObject): YanivMessage? = when (json.getString("k")) {
    "view" -> YanivMessage.View(yanivTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> YanivMessage.Claim(json.getInt("seat"))
    "discard" -> YanivMessage.Discard(json.getJSONArray("cards").ints())
    "stock" -> YanivMessage.DrawStock
    "take" -> YanivMessage.TakeDiscard(json.getInt("card"))
    "yaniv" -> YanivMessage.CallYaniv
    "next" -> YanivMessage.Next
    else -> null
}
