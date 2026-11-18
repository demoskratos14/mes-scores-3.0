package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * Messages du Tarot chacun sur son téléphone (voir [TarotOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * propre vue de la table ([View], jamais la main des autres) et n'attend des invités que leurs actions.
 */
internal sealed interface TarotMsg {
    /** Organisateur → invité : la table vue par sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: TarotTable, val seat: Int, val taken: List<Int>) : TarotMsg

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : TarotMsg

    /** Invité → organisateur : j'annonce [level] (0 passe, 1 petite, 2 garde, 3 garde sans, 4 garde contre). */
    data class Bid(val level: Int) : TarotMsg

    /** Invité → organisateur : j'appelle cette carte (à 5 joueurs). */
    data class Call(val card: Int) : TarotMsg

    /** Invité → organisateur : j'écarte ces cartes (aucune en garde sans et en garde contre), avec ou sans chelem annoncé. */
    data class Discard(val cards: List<Int>, val slam: Boolean) : TarotMsg

    /** Invité → organisateur : je montre une poignée. */
    data object Handful : TarotMsg

    /** Invité → organisateur : je pose cette carte. */
    data class Play(val card: Int) : TarotMsg

    /** Invité → organisateur : j'empoche le pli complet que j'ai gagné. */
    data object Collect : TarotMsg

    /** Invité → organisateur : donne suivante, une fois la manche finie et inscrite. */
    data object Next : TarotMsg
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun TarotOutcome.toJson(): JSONObject = JSONObject()
    .put("tk", taker).put("pa", partner).put("ct", contract).put("ah", attackHalf).put("bo", bouts)
    .put("rq", required).put("pt", points).put("df", difference).put("su", success)
    .put("pe", petitAuBout).put("hf", handful).put("xh", extraHandful).put("sa", slamAnnounced)
    .put("ss", slamSucceeded).put("ds", defenseSlam).put("un", unit).put("de", deltas.toJson())

private fun tarotOutcomeFromJson(json: JSONObject): TarotOutcome = TarotOutcome(
    taker = json.getInt("tk"),
    partner = json.getInt("pa"),
    contract = json.getInt("ct"),
    attackHalf = json.getInt("ah"),
    bouts = json.getInt("bo"),
    required = json.getInt("rq"),
    points = json.getInt("pt"),
    difference = json.getInt("df"),
    success = json.getBoolean("su"),
    petitAuBout = json.getInt("pe"),
    handful = json.getInt("hf"),
    extraHandful = json.getInt("xh"),
    slamAnnounced = json.getBoolean("sa"),
    slamSucceeded = json.getBoolean("ss"),
    defenseSlam = json.getBoolean("ds"),
    unit = json.getInt("un"),
    deltas = json.getJSONArray("de").ints()
)

private fun TarotTable.toJson(): JSONObject {
    val json = JSONObject()
        .put("n", playerCount).put("r", roundNumber).put("dl", dealer).put("ph", phase.name).put("c", current)
        .put("bi", bids.toJson()).put("tk", taker).put("ct", contract).put("cc", calledCard).put("pa", partner)
        .put("dg", dog.toJson()).put("sl", slamAnnounced)
        .put("tr", JSONArray().also { array -> trick.forEach { array.put(JSONArray().put(it.player).put(it.card)) } })
        .put("tw", trickWinner).put("tp", tricksPlayed)
        .put("ro", roundOver).put("st", settled).put("sd", started).put("po", passedOut)
        .put("hs", handSizes.toJson())
        .put(
            "hf",
            JSONArray().also { array ->
                handfuls.forEach { array.put(JSONObject().put("p", it.player).put("l", it.level).put("t", it.trumps.toJson())) }
            }
        )
        .put("dt", discardedTrumps.toJson())
        .put("hand", hand.toJson())
    outcome?.let { json.put("ou", it.toJson()) }
    return json
}

private fun tarotTableFromJson(json: JSONObject): TarotTable {
    val trick = json.getJSONArray("tr")
    val handfuls = json.getJSONArray("hf")
    return TarotTable(
        playerCount = json.getInt("n"),
        roundNumber = json.getInt("r"),
        dealer = json.getInt("dl"),
        phase = TarotPhase.valueOf(json.getString("ph")),
        current = json.getInt("c"),
        bids = json.getJSONArray("bi").ints(),
        taker = json.getInt("tk"),
        contract = json.getInt("ct"),
        calledCard = json.getInt("cc"),
        partner = json.getInt("pa"),
        dog = json.getJSONArray("dg").ints(),
        slamAnnounced = json.getBoolean("sl"),
        trick = (0 until trick.length()).map { index ->
            val play = trick.getJSONArray(index)
            TarotPlay(play.getInt(0), play.getInt(1))
        },
        trickWinner = json.getInt("tw"),
        tricksPlayed = json.getInt("tp"),
        roundOver = json.getBoolean("ro"),
        settled = json.getBoolean("st"),
        started = json.getBoolean("sd"),
        passedOut = json.getBoolean("po"),
        handSizes = json.getJSONArray("hs").ints(),
        handfuls = (0 until handfuls.length()).map { index ->
            val item = handfuls.getJSONObject(index)
            TarotHandful(item.getInt("p"), item.getInt("l"), item.getJSONArray("t").ints())
        },
        discardedTrumps = json.getJSONArray("dt").ints(),
        outcome = json.optJSONObject("ou")?.let { tarotOutcomeFromJson(it) },
        hand = json.getJSONArray("hand").ints()
    )
}

internal fun TarotMsg.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is TarotMsg.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is TarotMsg.Claim -> json.put("k", "claim").put("seat", seat)
        is TarotMsg.Bid -> json.put("k", "bid").put("level", level)
        is TarotMsg.Call -> json.put("k", "call").put("card", card)
        is TarotMsg.Discard -> json.put("k", "discard").put("cards", cards.toJson()).put("slam", slam)
        TarotMsg.Handful -> json.put("k", "handful")
        is TarotMsg.Play -> json.put("k", "play").put("card", card)
        TarotMsg.Collect -> json.put("k", "collect")
        TarotMsg.Next -> json.put("k", "next")
    }
    return json
}

internal fun tarotMsgFromJson(json: JSONObject): TarotMsg? = when (json.getString("k")) {
    "view" -> TarotMsg.View(tarotTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> TarotMsg.Claim(json.getInt("seat"))
    "bid" -> TarotMsg.Bid(json.getInt("level"))
    "call" -> TarotMsg.Call(json.getInt("card"))
    "discard" -> TarotMsg.Discard(json.getJSONArray("cards").ints(), json.getBoolean("slam"))
    "handful" -> TarotMsg.Handful
    "play" -> TarotMsg.Play(json.getInt("card"))
    "collect" -> TarotMsg.Collect
    "next" -> TarotMsg.Next
    else -> null
}
