package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages de Flip 7 chacun sur son téléphone (voir [Flip7Online]). L'organisateur tient la partie : il envoie à chaque invité la
 * table ([View], la même pour tous : rien n'y est caché) et n'attend des invités que leurs actions.
 */
internal sealed interface Flip7Message {
    /** Organisateur → invité : la table, la place [seat] de l'invité (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: Flip7Table, val seat: Int, val taken: List<Int>) : Flip7Message

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : Flip7Message

    /** Invité → organisateur : je pioche une carte. */
    data object Hit : Flip7Message

    /** Invité → organisateur : je m'arrête et je marque mes points. */
    data object Stay : Flip7Message

    /** Invité → organisateur : la carte Action que j'ai piochée s'applique à ce joueur. */
    data class Choose(val target: Int) : Flip7Message

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : Flip7Message
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun List<List<Int>>.toNestedJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it.toJson()) } }

private fun JSONArray.nestedInts(): List<List<Int>> = (0 until length()).map { getJSONArray(it).ints() }

private fun Flip7Event.toJson(): JSONObject = JSONObject().put("k", kind.name).put("p", player).put("c", card).put("o", other)

private fun flip7EventFromJson(json: JSONObject): Flip7Event =
    Flip7Event(Flip7EventKind.valueOf(json.getString("k")), json.getInt("p"), json.getInt("c"), json.getInt("o"))

private fun Flip7Pending.toJson(): JSONObject =
    JSONObject().put("ch", choice.name).put("c", card).put("s", source).put("t", targets.toJson())

private fun flip7PendingFromJson(json: JSONObject): Flip7Pending =
    Flip7Pending(Flip7Choice.valueOf(json.getString("ch")), json.getInt("c"), json.getInt("s"), json.getJSONArray("t").ints())

private fun Flip7Table.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current)
    .put("st", JSONArray().also { array -> status.forEach { array.put(it.name) } })
    .put("nu", numbers.toNestedJson()).put("ex", extras.toNestedJson()).put("sk", stockSize)
    .put("ro", roundOver).put("f7", flip7Player).put("w", winner)
    .put("se", settled).put("sd", started).put("pt", points.toJson()).put("ca", canAct)
    .also { json ->
        pending?.let { json.put("pe", it.toJson()) }
        lastEvent?.let { json.put("ev", it.toJson()) }
    }

private fun flip7TableFromJson(json: JSONObject): Flip7Table = Flip7Table(
    playerCount = json.getInt("n"),
    roundNumber = json.getInt("r"),
    current = json.getInt("c"),
    status = json.getJSONArray("st").let { array -> (0 until array.length()).map { Flip7Status.valueOf(array.getString(it)) } },
    numbers = json.getJSONArray("nu").nestedInts(),
    extras = json.getJSONArray("ex").nestedInts(),
    stockSize = json.getInt("sk"),
    pending = if (json.has("pe")) flip7PendingFromJson(json.getJSONObject("pe")) else null,
    lastEvent = if (json.has("ev")) flip7EventFromJson(json.getJSONObject("ev")) else null,
    roundOver = json.getBoolean("ro"),
    flip7Player = json.getInt("f7"),
    winner = json.getInt("w"),
    settled = json.getBoolean("se"),
    started = json.getBoolean("sd"),
    points = json.getJSONArray("pt").ints(),
    canAct = json.getBoolean("ca")
)

internal fun Flip7Message.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is Flip7Message.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is Flip7Message.Claim -> json.put("k", "claim").put("seat", seat)
        Flip7Message.Hit -> json.put("k", "hit")
        Flip7Message.Stay -> json.put("k", "stay")
        is Flip7Message.Choose -> json.put("k", "choose").put("target", target)
        Flip7Message.Next -> json.put("k", "next")
    }
    return json
}

internal fun flip7MessageFromJson(json: JSONObject): Flip7Message? = when (json.getString("k")) {
    "view" -> Flip7Message.View(flip7TableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> Flip7Message.Claim(json.getInt("seat"))
    "hit" -> Flip7Message.Hit
    "stay" -> Flip7Message.Stay
    "choose" -> Flip7Message.Choose(json.getInt("target"))
    "next" -> Flip7Message.Next
    else -> null
}
