package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages du Huit américain chacun sur son téléphone (voir [HuitOnline]). L'organisateur tient la partie : il envoie à chaque
 * invité sa propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface HuitMessage {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: HuitTable, val seat: Int, val taken: List<Int>) : HuitMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : HuitMessage

    /** Invité → organisateur : je pose cette carte ; [suit] (0 à 3) est la couleur choisie après un 8, -1 sinon. */
    data class Play(val card: Int, val suit: Int) : HuitMessage

    /** Invité → organisateur : je pioche une carte. */
    data object Draw : HuitMessage

    /** Invité → organisateur : je passe mon tour. */
    data object Pass : HuitMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : HuitMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun HuitTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current)
    .put("as", askedSuit).put("top", top).put("sk", stockSize)
    .put("hs", handSizes.toJson()).put("ro", roundOver).put("w", winner).put("bl", blocked)
    .put("st", settled).put("sd", started).put("pt", points.toJson())
    .put("hand", hand.toJson()).put("cd", canDraw).put("mp", mustPass)

private fun huitTableFromJson(json: JSONObject): HuitTable = HuitTable(
    playerCount = json.getInt("n"),
    roundNumber = json.getInt("r"),
    current = json.getInt("c"),
    askedSuit = json.getInt("as"),
    top = json.getInt("top"),
    stockSize = json.getInt("sk"),
    handSizes = json.getJSONArray("hs").ints(),
    roundOver = json.getBoolean("ro"),
    winner = json.getInt("w"),
    blocked = json.getBoolean("bl"),
    settled = json.getBoolean("st"),
    started = json.getBoolean("sd"),
    points = json.getJSONArray("pt").ints(),
    hand = json.getJSONArray("hand").ints(),
    canDraw = json.getBoolean("cd"),
    mustPass = json.getBoolean("mp")
)

internal fun HuitMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is HuitMessage.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is HuitMessage.Claim -> json.put("k", "claim").put("seat", seat)
        is HuitMessage.Play -> json.put("k", "play").put("card", card).put("suit", suit)
        HuitMessage.Draw -> json.put("k", "draw")
        HuitMessage.Pass -> json.put("k", "pass")
        HuitMessage.Next -> json.put("k", "next")
    }
    return json
}

internal fun huitMessageFromJson(json: JSONObject): HuitMessage? = when (json.getString("k")) {
    "view" -> HuitMessage.View(huitTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> HuitMessage.Claim(json.getInt("seat"))
    "play" -> HuitMessage.Play(json.getInt("card"), json.getInt("suit"))
    "draw" -> HuitMessage.Draw
    "pass" -> HuitMessage.Pass
    "next" -> HuitMessage.Next
    else -> null
}
