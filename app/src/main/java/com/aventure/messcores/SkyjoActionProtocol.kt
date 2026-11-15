package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * La table du Skyjo Action telle qu'un téléphone la reçoit (voir [SkyjoActionRound.tableFor]) : les cartes cachées n'ont pas de valeur
 * (0), les pioches ne sont connues que par leur taille, et les secrets (carte en main, trois cartes, cartes regardées) ne sont
 * remplis que pour celui qui doit agir.
 */
internal data class SkyjoTable(
    val playerCount: Int,
    val roundNumber: Int,
    val current: Int,
    val phase: SkyjoPhase,
    val turnNumber: Int,
    val endBy: Int,
    val roundOver: Boolean,
    val settled: Boolean,
    val grids: List<List<SkyjoCell?>>,
    val stockSize: Int,
    val discardSize: Int,
    val discardTop: Int?,
    val actionRow: List<SkyjoAction>,
    val actionStockSize: Int,
    val actionDiscard: List<SkyjoAction>,
    val hands: List<List<SkyjoHeldAction>>,
    val held: Int?,
    val heldFromDiscard: Boolean,
    val three: List<Int>,
    val firstPick: Pair<Int, Int>?,
    val meteorQueue: List<Int>,
    val peek: List<SkyjoPeek>,
    val bonusQueue: List<Int>,
    val events: List<SkyjoEvent>
)

/** Ce qu'un joueur peut faire à son tour ; les nombres [SkyjoMessage.Do.a] et [SkyjoMessage.Do.b] disent sur quoi. */
internal enum class SkyjoOp {
    DRAW_STOCK, TAKE_DISCARD, TAKE_ACTION_ROW, TAKE_ACTION_DECK, PLAY_ACTION, DISCARD_ACTION,
    PLACE, FLIP, BONUS_ROW, BONUS_DECK, SKIP_BONUS, REACTIVATE, THREE_KEEP, THREE_NONE,
    SWAP, EXCHANGE, LOOK, SHOW_PEEK, CLOSE_PEEK, METEOR, STEAL, NEXT
}

/**
 * Applique l'action [op] à la manche : le même code sert au téléphone de l'organisateur et aux actions reçues des invités.
 * [a] et [b] : place, joueur ou numéro de carte selon l'action ; [flag] : « ligne » pour Regarder (sinon colonne).
 * Faux si l'action n'est pas permise à cet instant (la manche ne change pas).
 */
internal fun SkyjoActionRound.perform(op: SkyjoOp, a: Int = -1, b: Int = -1, flag: Boolean = false): Boolean = when (op) {
    SkyjoOp.DRAW_STOCK -> drawFromStock()
    SkyjoOp.TAKE_DISCARD -> takeFromDiscard()
    SkyjoOp.TAKE_ACTION_ROW -> takeActionFromRow(a)
    SkyjoOp.TAKE_ACTION_DECK -> takeActionFromDeck()
    SkyjoOp.PLAY_ACTION -> playAction(a)
    SkyjoOp.DISCARD_ACTION -> discardAction(a)
    SkyjoOp.PLACE -> placeHeld(a)
    SkyjoOp.FLIP -> discardHeldAndFlip(a)
    SkyjoOp.BONUS_ROW -> takeBonusFromRow(a)
    SkyjoOp.BONUS_DECK -> takeBonusFromDeck()
    SkyjoOp.SKIP_BONUS -> skipBonus()
    SkyjoOp.REACTIVATE -> reactivate(a)
    SkyjoOp.THREE_KEEP -> threeKeep(a, b)
    SkyjoOp.THREE_NONE -> threeNone(a)
    SkyjoOp.SWAP -> swapPick(a)
    SkyjoOp.EXCHANGE -> exchangePick(a, b)
    SkyjoOp.LOOK -> look(a, b, flag)
    SkyjoOp.SHOW_PEEK -> showPeek()
    // Sur un téléphone invité, les cartes regardées sont déjà visibles : « Cacher » les referme directement.
    SkyjoOp.CLOSE_PEEK -> {
        showPeek()
        closePeek()
    }
    SkyjoOp.METEOR -> meteorPick(a, b)
    SkyjoOp.STEAL -> steal(a, b)
    SkyjoOp.NEXT -> nextRound()
}

/**
 * Messages du Skyjo Action chacun sur son téléphone (voir [SkyjoActionOnline]). L'organisateur tient la partie : il envoie à chaque
 * invité sa table et n'attend des invités que leurs actions.
 */
internal sealed interface SkyjoMessage {
    /** Organisateur → invité : la table vue de sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val table: SkyjoTable, val seat: Int, val taken: List<Int>) : SkyjoMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : SkyjoMessage

    /** Invité → organisateur : j'effectue l'action [op] (voir [perform]). */
    data class Do(val op: SkyjoOp, val a: Int = -1, val b: Int = -1, val flag: Boolean = false) : SkyjoMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

// Une case : -1 pour une place vide, sinon (valeur + 2) × 2, plus 1 si la carte est face visible.
private fun SkyjoCell?.code(): Int = if (this == null) -1 else (card - SkyjoActionScoring.MIN_VALUE) * 2 + if (up) 1 else 0

private fun cellFromCode(code: Int): SkyjoCell? =
    if (code < 0) null else SkyjoCell(card = code / 2 + SkyjoActionScoring.MIN_VALUE, up = code % 2 == 1)

private fun SkyjoEvent.toJson(): JSONObject =
    JSONObject().put("k", kind.name).put("p", player).put("o", other).put("a", action?.ordinal ?: -1)

private fun skyjoEventFromJson(json: JSONObject): SkyjoEvent = SkyjoEvent(
    kind = SkyjoEventKind.valueOf(json.getString("k")),
    player = json.getInt("p"),
    other = json.getInt("o"),
    action = SkyjoAction.entries.getOrNull(json.getInt("a"))
)

private fun SkyjoTable.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current).put("ph", phase.name).put("tn", turnNumber)
    .put("eb", endBy).put("ro", roundOver).put("se", settled)
    .put("gr", JSONArray().also { rows -> grids.forEach { grid -> rows.put(JSONArray().also { a -> grid.forEach { a.put(it.code()) } }) } })
    .put("sk", stockSize).put("ds", discardSize)
    .put("ar", JSONArray().also { array -> actionRow.forEach { array.put(it.ordinal) } })
    .put("as", actionStockSize)
    .put("ad", JSONArray().also { array -> actionDiscard.forEach { array.put(it.ordinal) } })
    .put(
        "hd",
        JSONArray().also { rows ->
            hands.forEach { list ->
                rows.put(JSONArray().also { a -> list.forEach { a.put(JSONArray().put(it.action.ordinal).put(it.unlockTurn)) } })
            }
        }
    )
    .put("hf", heldFromDiscard)
    .put("th", three.toJson())
    .put("mq", meteorQueue.toJson())
    .put("pk", JSONArray().also { array -> peek.forEach { array.put(JSONArray().put(it.player).put(it.cell).put(it.card)) } })
    .put("bq", bonusQueue.toJson())
    .put("ev", JSONArray().also { array -> events.forEach { array.put(it.toJson()) } })
    .also { json ->
        discardTop?.let { json.put("dt", it) }
        held?.let { json.put("h", it) }
        firstPick?.let { json.put("fp", JSONArray().put(it.first).put(it.second)) }
    }

private fun skyjoTableFromJson(json: JSONObject): SkyjoTable {
    val actions = SkyjoAction.entries
    return SkyjoTable(
        playerCount = json.getInt("n"),
        roundNumber = json.getInt("r"),
        current = json.getInt("c"),
        phase = SkyjoPhase.valueOf(json.getString("ph")),
        turnNumber = json.getInt("tn"),
        endBy = json.getInt("eb"),
        roundOver = json.getBoolean("ro"),
        settled = json.getBoolean("se"),
        grids = json.getJSONArray("gr").let { rows -> (0 until rows.length()).map { i -> rows.getJSONArray(i).ints().map { cellFromCode(it) } } },
        stockSize = json.getInt("sk"),
        discardSize = json.getInt("ds"),
        discardTop = if (json.has("dt")) json.getInt("dt") else null,
        actionRow = json.getJSONArray("ar").ints().map { actions[it] },
        actionStockSize = json.getInt("as"),
        actionDiscard = json.getJSONArray("ad").ints().map { actions[it] },
        hands = json.getJSONArray("hd").let { rows ->
            (0 until rows.length()).map { i ->
                val list = rows.getJSONArray(i)
                (0 until list.length()).map { j -> list.getJSONArray(j).let { SkyjoHeldAction(actions[it.getInt(0)], it.getInt(1)) } }
            }
        },
        held = if (json.has("h")) json.getInt("h") else null,
        heldFromDiscard = json.getBoolean("hf"),
        three = json.getJSONArray("th").ints(),
        firstPick = if (json.has("fp")) json.getJSONArray("fp").let { it.getInt(0) to it.getInt(1) } else null,
        meteorQueue = json.getJSONArray("mq").ints(),
        peek = json.getJSONArray("pk").let { rows ->
            (0 until rows.length()).map { i -> rows.getJSONArray(i).let { SkyjoPeek(it.getInt(0), it.getInt(1), it.getInt(2)) } }
        },
        bonusQueue = json.getJSONArray("bq").ints(),
        events = json.getJSONArray("ev").objects().map { skyjoEventFromJson(it) }
    )
}

internal fun SkyjoMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is SkyjoMessage.View -> json.put("k", "view").put("table", table.toJson()).put("seat", seat).put("taken", taken.toJson())
        is SkyjoMessage.Claim -> json.put("k", "claim").put("seat", seat)
        is SkyjoMessage.Do -> json.put("k", "do").put("op", op.name).put("a", a).put("b", b).put("f", flag)
    }
    return json
}

internal fun skyjoMessageFromJson(json: JSONObject): SkyjoMessage? = when (json.getString("k")) {
    "view" -> SkyjoMessage.View(skyjoTableFromJson(json.getJSONObject("table")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> SkyjoMessage.Claim(json.getInt("seat"))
    "do" -> SkyjoMessage.Do(SkyjoOp.valueOf(json.getString("op")), json.getInt("a"), json.getInt("b"), json.getBoolean("f"))
    else -> null
}
