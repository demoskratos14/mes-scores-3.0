package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages du poker chacun sur son téléphone (voir [PokerOnline]). L'organisateur tient la partie et ses cartes : il envoie à
 * chaque invité sa propre vue de la table ([View], jamais les cartes des autres avant l'abattage) et n'attend des invités
 * que leurs actions.
 */
internal sealed interface PokerMessage {
    /**
     * Organisateur → invité : la partie vue de la place [seat] (-1 : pas encore de place), avec ses cartes et seulement les
     * cartes communes retournées (voir [PokerEngine.viewFor]), et les places déjà prises par d'autres téléphones.
     */
    data class View(val game: PokerGame, val seat: Int, val taken: List<Int>) : PokerMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : PokerMessage

    /** Invité → organisateur : je me couche. */
    data object Fold : PokerMessage

    /** Invité → organisateur : je checke. */
    data object Check : PokerMessage

    /** Invité → organisateur : je suis. */
    data object Call : PokerMessage

    /** Invité → organisateur : je fais tapis. */
    data object AllIn : PokerMessage

    /** Invité → organisateur : je mise ou relance pour une mise totale de [to] jetons dans le tour. */
    data class Raise(val to: Int) : PokerMessage

    /** Invité → organisateur : on règle l'abattage d'après les cartes. */
    data object Showdown : PokerMessage

    /** Invité → organisateur : main suivante. */
    data object NextHand : PokerMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

internal fun PokerMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is PokerMessage.View -> json.put("k", "view").put("game", game.toJson()).put("seat", seat)
            .put("taken", JSONArray(taken))
        is PokerMessage.Claim -> json.put("k", "claim").put("seat", seat)
        PokerMessage.Fold -> json.put("k", "fold")
        PokerMessage.Check -> json.put("k", "check")
        PokerMessage.Call -> json.put("k", "call")
        PokerMessage.AllIn -> json.put("k", "allin")
        is PokerMessage.Raise -> json.put("k", "raise").put("to", to)
        PokerMessage.Showdown -> json.put("k", "showdown")
        PokerMessage.NextHand -> json.put("k", "next")
    }
    return json
}

internal fun pokerMessageFromJson(json: JSONObject): PokerMessage? = when (json.getString("k")) {
    "view" -> PokerMessage.View(PokerGame.fromJson(json.getJSONObject("game")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> PokerMessage.Claim(json.getInt("seat"))
    "fold" -> PokerMessage.Fold
    "check" -> PokerMessage.Check
    "call" -> PokerMessage.Call
    "allin" -> PokerMessage.AllIn
    "raise" -> PokerMessage.Raise(json.getInt("to"))
    "showdown" -> PokerMessage.Showdown
    "next" -> PokerMessage.NextHand
    else -> null
}
