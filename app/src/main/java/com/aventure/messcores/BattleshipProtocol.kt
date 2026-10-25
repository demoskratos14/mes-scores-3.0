package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONObject

/** Ce qui s'est passé de particulier à la table, à afficher aux joueurs. */
internal enum class BattleshipNotice { NONE, PLAYER_LEFT, HOST_LEFT }

/**
 * Messages du touché coulé chacun sur son téléphone (voir [BattleshipOnline]). Aucune flotte ne circule pendant la
 * partie : le tireur envoie la case visée, c'est le téléphone du défenseur qui répond. Les flottes ne sont dévoilées
 * qu'à la fin ([Reveal]), pour que chacun vérifie que les réponses étaient honnêtes.
 */
internal sealed interface BsMessage {
    /** Organisateur → invité : les joueurs à la table et les réglages. */
    data class Lobby(val names: List<String>, val settings: BattleshipSettings, val notice: BattleshipNotice) : BsMessage

    /** Organisateur → invité : la partie commence, le joueur [first] tirera le premier. Chacun place sa flotte. */
    data class Start(val first: Int) : BsMessage

    /** Ma flotte est placée (elle n'est pas envoyée). */
    data object Ready : BsMessage

    /** Tireur → défenseur : un tir sur [cell]. */
    data class Fire(val cell: Int) : BsMessage

    /** Défenseur → tireur : la réponse au tir ; [gameOver] si toute la flotte du défenseur est coulée. */
    data class Report(val shot: BattleshipShot, val gameOver: Boolean) : BsMessage

    /** Fin de partie : ma flotte, dévoilée à l'adversaire. */
    data class Reveal(val ships: List<List<Int>>) : BsMessage
}

private fun intsToJson(values: List<Int>): JSONArray = JSONArray().also { array -> values.forEach { array.put(it) } }

private fun jsonToInts(array: JSONArray): List<Int> = (0 until array.length()).map { array.getInt(it) }

private fun BattleshipShot.toBsJson(): JSONObject =
    JSONObject().put("c", cell).put("o", outcome.name).put("s", intsToJson(sunk))

private fun bsShotFromJson(json: JSONObject): BattleshipShot =
    BattleshipShot(json.getInt("c"), ShotOutcome.valueOf(json.getString("o")), jsonToInts(json.getJSONArray("s")))

internal fun BsMessage.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is BsMessage.Lobby -> json.put("k", "lobby")
            .put("names", JSONArray().also { array -> names.forEach { array.put(it) } })
            .put("r", settings.shootAgainOnHit).put("notice", notice.name)
        is BsMessage.Start -> json.put("k", "start").put("first", first)
        BsMessage.Ready -> json.put("k", "ready")
        is BsMessage.Fire -> json.put("k", "fire").put("cell", cell)
        is BsMessage.Report -> json.put("k", "report").put("shot", shot.toBsJson()).put("over", gameOver)
        is BsMessage.Reveal -> json.put("k", "reveal")
            .put("ships", JSONArray().also { array -> ships.forEach { array.put(intsToJson(it)) } })
    }
    return json
}

/** Le message codé dans [json], ou null s'il n'est pas du touché coulé ; une valeur illisible lève une exception (rattrapée par [SyncCodec.decode]). */
internal fun bsMessageFromJson(json: JSONObject): BsMessage? = when (json.getString("k")) {
    "lobby" -> {
        val names = json.getJSONArray("names")
        BsMessage.Lobby(
            names = (0 until names.length()).map { names.getString(it) },
            settings = BattleshipSettings(shootAgainOnHit = json.getBoolean("r")),
            notice = BattleshipNotice.valueOf(json.getString("notice"))
        )
    }
    "start" -> BsMessage.Start(json.getInt("first"))
    "ready" -> BsMessage.Ready
    "fire" -> BsMessage.Fire(json.getInt("cell"))
    "report" -> BsMessage.Report(bsShotFromJson(json.getJSONObject("shot")), json.getBoolean("over"))
    "reveal" -> {
        val ships = json.getJSONArray("ships")
        BsMessage.Reveal((0 until ships.length()).map { jsonToInts(ships.getJSONArray(it)) })
    }
    else -> null
}
