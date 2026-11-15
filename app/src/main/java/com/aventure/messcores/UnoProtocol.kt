package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages du Uno chacun sur son téléphone (voir [UnoOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface UnoMessage {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: UnoTable, val seat: Int, val taken: List<Int>) : UnoMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : UnoMessage

    /** Invité → organisateur : je pose cette carte ; [color] (0 à 3) est la couleur choisie après un joker, -1 sinon. */
    data class Play(val card: Int, val color: Int) : UnoMessage

    /** Invité → organisateur : je pioche une carte. */
    data object Draw : UnoMessage

    /** Invité → organisateur : je passe mon tour. */
    data object Pass : UnoMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : UnoMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun UnoEvent.toJson(): JSONObject = JSONObject().put("p", player).put("c", card).put("v", victim).put("d", drawn)

private fun unoEventFromJson(json: JSONObject): UnoEvent =
    UnoEvent(player = json.getInt("p"), card = json.getInt("c"), victim = json.getInt("v"), drawn = json.getInt("d"))

private fun UnoTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current).put("dir", direction)
    .put("ac", askedColor).put("top", top).put("sk", stockSize)
    .put("hs", handSizes.toJson()).put("ro", roundOver).put("w", winner).put("bl", blocked)
    .put("st", settled).put("sd", started).put("pt", points.toJson())
    .put("hand", hand.toJson()).put("dc", drawnCard).put("cd", canDraw).put("mp", mustPass)
    .also { json -> lastEvent?.let { json.put("ev", it.toJson()) } }

private fun unoTableFromJson(json: JSONObject): UnoTable = UnoTable(
    playerCount = json.getInt("n"),
    roundNumber = json.getInt("r"),
    current = json.getInt("c"),
    direction = json.getInt("dir"),
    askedColor = json.getInt("ac"),
    top = json.getInt("top"),
    stockSize = json.getInt("sk"),
    handSizes = json.getJSONArray("hs").ints(),
    lastEvent = if (json.has("ev")) unoEventFromJson(json.getJSONObject("ev")) else null,
    roundOver = json.getBoolean("ro"),
    winner = json.getInt("w"),
    blocked = json.getBoolean("bl"),
    settled = json.getBoolean("st"),
    started = json.getBoolean("sd"),
    points = json.getJSONArray("pt").ints(),
    hand = json.getJSONArray("hand").ints(),
    drawnCard = json.getInt("dc"),
    canDraw = json.getBoolean("cd"),
    mustPass = json.getBoolean("mp")
)

internal fun UnoMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is UnoMessage.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is UnoMessage.Claim -> json.put("k", "claim").put("seat", seat)
        is UnoMessage.Play -> json.put("k", "play").put("card", card).put("color", color)
        UnoMessage.Draw -> json.put("k", "draw")
        UnoMessage.Pass -> json.put("k", "pass")
        UnoMessage.Next -> json.put("k", "next")
    }
    return json
}

internal fun unoMessageFromJson(json: JSONObject): UnoMessage? = when (json.getString("k")) {
    "view" -> UnoMessage.View(unoTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> UnoMessage.Claim(json.getInt("seat"))
    "play" -> UnoMessage.Play(json.getInt("card"), json.getInt("color"))
    "draw" -> UnoMessage.Draw
    "pass" -> UnoMessage.Pass
    "next" -> UnoMessage.Next
    else -> null
}
