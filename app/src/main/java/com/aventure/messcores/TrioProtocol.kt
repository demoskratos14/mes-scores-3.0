package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/**
 * La table du Trio telle qu'un téléphone la reçoit (voir [TrioRound.tableFor]) : les trios, les cartes retournées, sa propre main
 * (les autres mains et le milieu ne sont que des cartes sans valeur, de la bonne taille, tant que la manche n'est pas finie).
 */
internal data class TrioView(
    val playerCount: Int,
    val roundNumber: Int,
    val current: Int,
    val hands: List<List<Int>>,
    val center: List<Int>,
    val trios: List<List<Int>>,
    val reveals: List<TrioReveal>,
    val mismatch: Boolean,
    val lastTrio: Int,
    val roundOver: Boolean,
    val winner: Int,
    val winReason: TrioWin,
    val settled: Boolean
)

/** Ce qu'un joueur peut faire à son tour. */
internal enum class TrioOp {
    /** Retourner la plus petite carte cachée de la main du joueur [target]. */
    LOW,

    /** Retourner la plus grande carte cachée de la main du joueur [target]. */
    HIGH,

    /** Retourner la carte [target] du milieu. */
    CENTER,

    /** Terminer un tour perdu. */
    END,

    /** Passer à la manche suivante. */
    NEXT
}

/**
 * Applique l'action [op] à la manche : le même code sert au téléphone de l'organisateur et aux actions reçues des invités.
 * [target] est le joueur (pour [TrioOp.LOW] et [TrioOp.HIGH]) ou la place dans le milieu (pour [TrioOp.CENTER]). Faux si l'action
 * n'est pas permise à cet instant (la manche ne change pas).
 */
internal fun TrioRound.perform(op: TrioOp, target: Int = -1): Boolean = when (op) {
    TrioOp.LOW -> flipHand(target, high = false)
    TrioOp.HIGH -> flipHand(target, high = true)
    TrioOp.CENTER -> flipCenter(target)
    TrioOp.END -> endTurn()
    TrioOp.NEXT -> nextRound()
}

/**
 * Messages du Trio chacun sur son téléphone (voir [TrioOnline]). L'organisateur tient la partie : il envoie à chaque invité sa
 * table et n'attend des invités que leurs actions.
 */
internal sealed interface TrioMessage {
    /** Organisateur → invité : la table vue de sa place [seat] (-1 : pas encore de place) et les places déjà prises par d'autres téléphones. */
    data class View(val view: TrioView, val seat: Int, val taken: List<Int>) : TrioMessage

    /** Invité → organisateur : je joue à la place [seat] (-1 : je la quitte). */
    data class Claim(val seat: Int) : TrioMessage

    /** Invité → organisateur : j'effectue l'action [op] sur [target] (voir [perform]). */
    data class Do(val op: TrioOp, val target: Int = -1) : TrioMessage
}

private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

private fun List<Int>.toJson(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

private fun List<List<Int>>.toRowsJson(): JSONArray = JSONArray().also { rows -> forEach { rows.put(it.toJson()) } }

private fun JSONArray.rows(): List<List<Int>> = (0 until length()).map { getJSONArray(it).ints() }

private fun TrioView.toJson(): JSONObject = JSONObject()
    .put("n", playerCount).put("r", roundNumber).put("c", current)
    .put("hd", hands.toRowsJson())
    .put("ce", center.toJson())
    .put("tr", trios.toRowsJson())
    .put("rv", JSONArray().also { array ->
        reveals.forEach { array.put(JSONArray().put(it.zone.name).put(it.player).put(it.index).put(it.value)) }
    })
    .put("mm", mismatch).put("lt", lastTrio)
    .put("ro", roundOver).put("w", winner).put("wr", winReason.name).put("se", settled)

private fun trioViewFromJson(json: JSONObject): TrioView = TrioView(
    playerCount = json.getInt("n"),
    roundNumber = json.getInt("r"),
    current = json.getInt("c"),
    hands = json.getJSONArray("hd").rows(),
    center = json.getJSONArray("ce").ints(),
    trios = json.getJSONArray("tr").rows(),
    reveals = json.getJSONArray("rv").let { rows ->
        (0 until rows.length()).map { i ->
            rows.getJSONArray(i).let { TrioReveal(TrioZone.valueOf(it.getString(0)), it.getInt(1), it.getInt(2), it.getInt(3)) }
        }
    },
    mismatch = json.getBoolean("mm"),
    lastTrio = json.getInt("lt"),
    roundOver = json.getBoolean("ro"),
    winner = json.getInt("w"),
    winReason = TrioWin.valueOf(json.getString("wr")),
    settled = json.getBoolean("se")
)

internal fun TrioMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is TrioMessage.View -> json.put("k", "view").put("view", view.toJson()).put("seat", seat).put("taken", taken.toJson())
        is TrioMessage.Claim -> json.put("k", "claim").put("seat", seat)
        is TrioMessage.Do -> json.put("k", "do").put("op", op.name).put("target", target)
    }
    return json
}

internal fun trioMessageFromJson(json: JSONObject): TrioMessage? = when (json.getString("k")) {
    "view" -> TrioMessage.View(trioViewFromJson(json.getJSONObject("view")), json.getInt("seat"), json.getJSONArray("taken").ints())
    "claim" -> TrioMessage.Claim(json.getInt("seat"))
    "do" -> TrioMessage.Do(TrioOp.valueOf(json.getString("op")), json.getInt("target"))
    else -> null
}
