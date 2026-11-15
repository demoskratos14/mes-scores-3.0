package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * La table du Domino telle qu'un téléphone la reçoit (voir [DominoRound.tableFor]) : la chaîne posée, la taille de la pioche, sa
 * propre main (les autres mains ne sont que des dominos sans valeur, de la bonne taille, tant que la manche n'est pas finie).
 */
internal data class DominoView(
    val playerCount: Int,
    val roundNumber: Int,
    val current: Int,
    val chain: List<PlacedDomino>,
    val stockSize: Int,
    val hands: List<List<Int>>,
    val opener: Int,
    val openingTile: Int,
    val roundOver: Boolean,
    val blocked: Boolean,
    val settled: Boolean
)

/** Ce qu'un joueur peut faire à son tour. */
internal enum class DominoOp { PLAY, DRAW, PASS, NEXT }

/**
 * Applique l'action [op] à la manche : le même code sert au téléphone de l'organisateur et aux actions reçues des invités.
 * [tile] et [atLeft] ne servent qu'à poser un domino. Faux si l'action n'est pas permise à cet instant (la manche ne change pas).
 * Le joueur qui agit a forcément sa main sous les yeux : on la découvre avant d'agir.
 */
internal fun DominoRound.perform(op: DominoOp, tile: Int = -1, atLeft: Boolean = false): Boolean = when (op) {
    DominoOp.PLAY -> {
        reveal()
        play(tile, atLeft)
    }
    DominoOp.DRAW -> {
        reveal()
        draw()
    }
    DominoOp.PASS -> {
        reveal()
        pass()
    }
    DominoOp.NEXT -> nextRound()
}

/**
 * Messages du Domino chacun sur son téléphone (voir [DominoOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * table et n'attend des invités que leurs actions.
 */
internal sealed interface DominoMessage {
    /** Organisateur → invité : la table vue de sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val view: DominoView, val seat: Int, val taken: List<Int>) : DominoMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : DominoMessage

    /** Invité → organisateur : j'effectue l'action [op] (voir [perform]). */
    data class Do(val op: DominoOp, val tile: Int = -1, val atLeft: Boolean = false) : DominoMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun DominoView.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current)
    .put("ch", JSONArray().also { array -> chain.forEach { array.put(JSONArray().put(it.tile).put(it.left).put(it.right)) } })
    .put("sk", stockSize)
    .put("hd", JSONArray().also { rows -> hands.forEach { rows.put(it.toJson()) } })
    .put("op", opener).put("ot", openingTile)
    .put("ro", roundOver).put("bl", blocked).put("se", settled)

private fun dominoViewFromJson(json: JSONObject): DominoView = DominoView(
    playerCount = json.getInt("n"),
    roundNumber = json.getInt("r"),
    current = json.getInt("c"),
    chain = json.getJSONArray("ch").let { rows ->
        (0 until rows.length()).map { i -> rows.getJSONArray(i).let { PlacedDomino(it.getInt(0), it.getInt(1), it.getInt(2)) } }
    },
    stockSize = json.getInt("sk"),
    hands = json.getJSONArray("hd").let { rows -> (0 until rows.length()).map { rows.getJSONArray(it).ints() } },
    opener = json.getInt("op"),
    openingTile = json.getInt("ot"),
    roundOver = json.getBoolean("ro"),
    blocked = json.getBoolean("bl"),
    settled = json.getBoolean("se")
)

internal fun DominoMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is DominoMessage.View -> json.put("k", "view").put("view", view.toJson()).put("seat", seat).put("taken", taken.toJson())
        is DominoMessage.Claim -> json.put("k", "claim").put("seat", seat)
        is DominoMessage.Do -> json.put("k", "do").put("op", op.name).put("tile", tile).put("left", atLeft)
    }
    return json
}

internal fun dominoMessageFromJson(json: JSONObject): DominoMessage? = when (json.getString("k")) {
    "view" -> DominoMessage.View(dominoViewFromJson(json.getJSONObject("view")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> DominoMessage.Claim(json.getInt("seat"))
    "do" -> DominoMessage.Do(DominoOp.valueOf(json.getString("op")), json.getInt("tile"), json.getBoolean("left"))
    else -> null
}
