package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages du Rami chacun sur son téléphone (voir [RamiOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface RamiMessage {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: RamiTable, val seat: Int, val taken: List<Int>) : RamiMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : RamiMessage

    /** Invité → organisateur : je pioche dans la pioche. */
    data object DrawStock : RamiMessage

    /** Invité → organisateur : je reprends la carte du dessus de la défausse. */
    data object TakeDiscard : RamiMessage

    /** Invité → organisateur : je pose ces cartes en combinaisons. */
    data class Meld(val cards: List<Int>) : RamiMessage

    /** Invité → organisateur : j'ajoute ces cartes à la combinaison [meld] de la table. */
    data class Attach(val cards: List<Int>, val meld: Int) : RamiMessage

    /** Invité → organisateur : je remplace un joker de la combinaison [meld] par [card]. */
    data class Swap(val card: Int, val meld: Int) : RamiMessage

    /** Invité → organisateur : je défausse [card], ce qui finit mon tour. */
    data class Discard(val card: Int) : RamiMessage

    /** Invité → organisateur : manche suivante, une fois la manche finie et inscrite. */
    data object Next : RamiMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun List<List<Int>>.toNestedJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it.toJson()) } }

private fun JSONArray.nestedInts(): List<List<Int>> = (0 until length()).map { getJSONArray(it).ints() }

private fun List<Boolean>.toBooleanJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun JSONArray.booleans(): List<Boolean> = (0 until length()).map { getBoolean(it) }

private fun RamiMeld.toJson(): JSONObject = JSONObject().put("c", cards.toJson()).put("r", run).put("k", key)

private fun ramiMeldFromJson(json: JSONObject): RamiMeld =
    RamiMeld(cards = json.getJSONArray("c").ints(), run = json.getBoolean("r"), key = json.getInt("k"))

private fun List<RamiMeld>.toMeldsJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it.toJson()) } }

private fun JSONArray.melds(): List<RamiMeld> = (0 until length()).map { ramiMeldFromJson(getJSONObject(it)) }

private fun RamiTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current).put("dr", drawing)
    .put("td", topDiscard).put("sk", stockSize).put("hs", handSizes.toJson())
    .put("op", opened.toBooleanJson()).put("ml", melds.toMeldsJson())
    .put("ro", roundOver).put("w", winner).put("st", settled).put("sd", started)
    .put("pt", points.toJson()).put("fh", finalHands.toNestedJson()).put("hand", hand.toJson())
    .put("cs", canDrawFromStock)

private fun ramiTableFromJson(json: JSONObject): RamiTable = RamiTable(
    playerCount = json.getInt("n"),
    roundNumber = json.getInt("r"),
    current = json.getInt("c"),
    drawing = json.getBoolean("dr"),
    topDiscard = json.getInt("td"),
    stockSize = json.getInt("sk"),
    handSizes = json.getJSONArray("hs").ints(),
    opened = json.getJSONArray("op").booleans(),
    melds = json.getJSONArray("ml").melds(),
    roundOver = json.getBoolean("ro"),
    winner = json.getInt("w"),
    settled = json.getBoolean("st"),
    started = json.getBoolean("sd"),
    points = json.getJSONArray("pt").ints(),
    finalHands = json.getJSONArray("fh").nestedInts(),
    hand = json.getJSONArray("hand").ints(),
    canDrawFromStock = json.getBoolean("cs")
)

internal fun RamiMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is RamiMessage.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is RamiMessage.Claim -> json.put("k", "claim").put("seat", seat)
        RamiMessage.DrawStock -> json.put("k", "stock")
        RamiMessage.TakeDiscard -> json.put("k", "take")
        is RamiMessage.Meld -> json.put("k", "meld").put("cards", cards.toJson())
        is RamiMessage.Attach -> json.put("k", "attach").put("cards", cards.toJson()).put("meld", meld)
        is RamiMessage.Swap -> json.put("k", "swap").put("card", card).put("meld", meld)
        is RamiMessage.Discard -> json.put("k", "discard").put("card", card)
        RamiMessage.Next -> json.put("k", "next")
    }
    return json
}

internal fun ramiMessageFromJson(json: JSONObject): RamiMessage? = when (json.getString("k")) {
    "view" -> RamiMessage.View(ramiTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> RamiMessage.Claim(json.getInt("seat"))
    "stock" -> RamiMessage.DrawStock
    "take" -> RamiMessage.TakeDiscard
    "meld" -> RamiMessage.Meld(json.getJSONArray("cards").ints())
    "attach" -> RamiMessage.Attach(json.getJSONArray("cards").ints(), json.getInt("meld"))
    "swap" -> RamiMessage.Swap(json.getInt("card"), json.getInt("meld"))
    "discard" -> RamiMessage.Discard(json.getInt("card"))
    "next" -> RamiMessage.Next
    else -> null
}
