package com.aventure.messcores

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Un joueur qui change de table de poker : son nom, son tapis et sa couleur (0 = pas de couleur connue). */
data class PokerTransferPlayer(val name: String, val stack: Int, val colorArgb: Int)

/**
 * Fichier d'échange pour réunir des tables d'un tournoi : la table qui ferme exporte ses joueurs,
 * l'autre table les importe. Fonctions pures (sans Android), testées dans PokerReseatTest.
 */
object PokerTransfer {
    private const val FORMAT = "mes-scores-poker-players"
    private const val VERSION = 1

    /** Texte JSON du fichier à enregistrer. */
    fun export(players: List<PokerTransferPlayer>, exportedAt: Long): String =
        JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedAt", exportedAt)
            put("players", JSONArray().also { array ->
                players.forEach { p ->
                    array.put(JSONObject().apply {
                        put("name", p.name)
                        put("stack", p.stack)
                        put("color", p.colorArgb)
                    })
                }
            })
        }.toString(2)

    /**
     * Joueurs lus dans [text], ou null si ce n'est pas un fichier de joueurs de poker (autre format,
     * version plus récente, JSON abîmé, aucun joueur valide). Les entrées sans nom ou sans jeton sont ignorées.
     */
    fun parse(text: String): List<PokerTransferPlayer>? {
        return try {
            val root = JSONObject(text)
            if (root.optString("format") != FORMAT) return null
            if (root.optInt("version", 1) > VERSION) return null
            val array = root.getJSONArray("players")
            val players = (0 until array.length()).mapNotNull { i ->
                val entry = array.optJSONObject(i) ?: return@mapNotNull null
                val name = entry.optString("name").trim()
                val stack = entry.optInt("stack", 0)
                if (name.isEmpty() || stack <= 0) null else PokerTransferPlayer(name, stack, entry.optInt("color", 0))
            }
            players.ifEmpty { null }
        } catch (e: JSONException) {
            null
        }
    }
}
