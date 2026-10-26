package com.aventure.messcores

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Partage d'une partie entre deux téléphones : un téléphone, l'organisateur, détient la partie. L'invité
 * ne la modifie jamais lui-même : il envoie ce qu'il fait ([SyncOp]) à l'organisateur, qui l'applique puis
 * renvoie l'état complet de la partie ([SyncMessage.State]) à tous les téléphones connectés.
 */
internal sealed interface SyncOp {
    data class SetCell(val round: Int, val player: Int, val baseValue: Int?, val isNegative: Boolean) : SyncOp
    data class SetMultiplier(val round: Int, val player: Int, val multiplierId: String) : SyncOp
    data class IncrementCounter(val player: Int, val delta: Int) : SyncOp
    data class SetPhase(val player: Int, val phase: Int) : SyncOp
    data class AddTeamRound(
        val teamALabel: String,
        val teamAPlayers: Set<Int>,
        val baseValue: Int,
        val isNegative: Boolean,
        val multiplierId: String
    ) : SyncOp
    data class AppendTeamRound(val round: TeamRound) : SyncOp
    data class ReplaceTeamRound(val index: Int, val round: TeamRound) : SyncOp
    data class RemoveTeamRound(val index: Int) : SyncOp
    data class SetPlayerColor(val player: Int, val colorArgb: Int) : SyncOp
    data object Undo : SyncOp
    data object Redo : SyncOp
}

/**
 * Le tour de dés partagé entre les téléphones d'une partie de dés : à qui c'est de jouer, les dés ([turn], voir
 * [DiceTurn.snapshot]) et, au Cochon, le total du tour et s'il est perdu.
 */
internal data class DiceTable(val player: Int, val turn: List<Int>, val pigTotal: Int, val pigBust: Boolean)

/** Un message échangé entre les téléphones. */
internal sealed interface SyncMessage {
    /** L'état complet de la partie, de l'organisateur vers les invités. [canUndo] et [canRedo] : boutons Annuler et Rétablir. */
    data class State(val game: SavedGame, val canUndo: Boolean, val canRedo: Boolean) : SyncMessage

    /** Une action de l'invité, à faire exécuter par l'organisateur. */
    data class Operation(val op: SyncOp) : SyncMessage

    /** Un message du Mastermind chacun sur son téléphone (voir [MastermindOnline]). */
    data class Mm(val mm: MmMessage) : SyncMessage

    /** Un message du touché coulé chacun sur son téléphone (voir [BattleshipOnline]). */
    data class Bs(val bs: BsMessage) : SyncMessage

    /** Un message du Morpion chacun sur son téléphone (voir [TicTacToeOnline]). */
    data class Ttt(val ttt: TttMessage) : SyncMessage

    /** Un message du Puissance 4 chacun sur son téléphone (voir [Connect4Online]). */
    data class C4(val c4: C4Message) : SyncMessage

    /** Un message du jeu des allumettes chacun sur son téléphone (voir [NimOnline]). */
    data class Nim(val nim: NimMessage) : SyncMessage

    /** Un message de Pierre-feuille-ciseaux chacun sur son téléphone (voir [RockPaperScissorsOnline]). */
    data class Rps(val rps: RpsMessage) : SyncMessage

    /** Le tour de dés en cours, envoyé par le téléphone du joueur qui joue (l'organisateur le relaie aux autres). */
    data class Dice(val table: DiceTable) : SyncMessage
}

/** Exécute sur la partie de l'organisateur l'action reçue d'un invité. */
internal fun SyncOp.applyTo(game: ScoreViewModel) {
    when (this) {
        is SyncOp.SetCell -> game.setCell(round, player, baseValue, isNegative)
        is SyncOp.SetMultiplier -> game.setMultiplier(round, player, multiplierId)
        is SyncOp.IncrementCounter -> game.incrementCounter(player, delta)
        is SyncOp.SetPhase -> game.setPhase(player, phase)
        is SyncOp.AddTeamRound -> game.addTeamRound(teamALabel, teamAPlayers, baseValue, isNegative, multiplierId)
        is SyncOp.AppendTeamRound -> game.appendTeamRound(round)
        is SyncOp.ReplaceTeamRound -> game.replaceTeamRound(index, round)
        is SyncOp.RemoveTeamRound -> game.removeTeamRound(index)
        is SyncOp.SetPlayerColor -> game.setPlayerColor(player, Color(colorArgb))
        SyncOp.Undo -> game.undo()
        SyncOp.Redo -> game.redo()
    }
}

private fun SyncOp.toJson(): JSONObject {
    val json = JSONObject()
    when (this) {
        is SyncOp.SetCell -> {
            json.put("op", "setCell").put("round", round).put("player", player).put("negative", isNegative)
            if (baseValue != null) json.put("value", baseValue)
        }
        is SyncOp.SetMultiplier ->
            json.put("op", "setMultiplier").put("round", round).put("player", player).put("multiplier", multiplierId)
        is SyncOp.IncrementCounter -> json.put("op", "increment").put("player", player).put("delta", delta)
        is SyncOp.SetPhase -> json.put("op", "setPhase").put("player", player).put("phase", phase)
        is SyncOp.AddTeamRound -> {
            json.put("op", "addTeamRound").put("label", teamALabel).put("value", baseValue)
                .put("negative", isNegative).put("multiplier", multiplierId)
            json.put("players", JSONArray(teamAPlayers.sorted()))
        }
        is SyncOp.AppendTeamRound -> json.put("op", "appendTeamRound").put("round", round.toJson())
        is SyncOp.ReplaceTeamRound -> json.put("op", "replaceTeamRound").put("index", index).put("round", round.toJson())
        is SyncOp.RemoveTeamRound -> json.put("op", "removeTeamRound").put("index", index)
        is SyncOp.SetPlayerColor -> json.put("op", "setColor").put("player", player).put("color", colorArgb)
        SyncOp.Undo -> json.put("op", "undo")
        SyncOp.Redo -> json.put("op", "redo")
    }
    return json
}

private fun syncOpFromJson(json: JSONObject): SyncOp? = when (json.getString("op")) {
    "setCell" -> SyncOp.SetCell(
        round = json.getInt("round"),
        player = json.getInt("player"),
        baseValue = if (json.has("value")) json.getInt("value") else null,
        isNegative = json.optBoolean("negative")
    )
    "setMultiplier" -> SyncOp.SetMultiplier(json.getInt("round"), json.getInt("player"), json.getString("multiplier"))
    "increment" -> SyncOp.IncrementCounter(json.getInt("player"), json.getInt("delta"))
    "setPhase" -> SyncOp.SetPhase(json.getInt("player"), json.getInt("phase"))
    "addTeamRound" -> {
        val players = json.getJSONArray("players")
        SyncOp.AddTeamRound(
            teamALabel = json.getString("label"),
            teamAPlayers = (0 until players.length()).map { players.getInt(it) }.toSet(),
            baseValue = json.getInt("value"),
            isNegative = json.optBoolean("negative"),
            multiplierId = json.getString("multiplier")
        )
    }
    "appendTeamRound" -> SyncOp.AppendTeamRound(teamRoundFromJson(json.getJSONObject("round")))
    "replaceTeamRound" -> SyncOp.ReplaceTeamRound(json.getInt("index"), teamRoundFromJson(json.getJSONObject("round")))
    "removeTeamRound" -> SyncOp.RemoveTeamRound(json.getInt("index"))
    "setColor" -> SyncOp.SetPlayerColor(json.getInt("player"), json.getInt("color"))
    "undo" -> SyncOp.Undo
    "redo" -> SyncOp.Redo
    else -> null
}

/** Met un [SyncMessage] en octets (JSON compressé) et inversement. */
internal object SyncCodec {

    /** Version du protocole : deux téléphones de versions incompatibles s'ignorent au lieu de se tromper. */
    const val VERSION = 2

    /** Taille maximale d'un message : celle qu'accepte Nearby Connections pour un envoi d'octets. */
    const val MAX_BYTES = 32 * 1024

    // Garde-fou à la lecture : un message décompressé ne dépasse jamais cette taille.
    private const val MAX_DECODED_BYTES = 2_000_000

    fun encode(message: SyncMessage): ByteArray {
        val json = JSONObject().put("v", VERSION)
        when (message) {
            is SyncMessage.State -> json.put("t", "state").put("game", message.game.toJson())
                .put("undo", message.canUndo).put("redo", message.canRedo)
            is SyncMessage.Operation -> json.put("t", "op").put("op", message.op.toJson())
            is SyncMessage.Mm -> json.put("t", "mm").put("mm", message.mm.toJson())
            is SyncMessage.Bs -> json.put("t", "bs").put("bs", message.bs.toJson())
            is SyncMessage.Ttt -> json.put("t", "ttt").put("ttt", message.ttt.toJson())
            is SyncMessage.C4 -> json.put("t", "c4").put("c4", message.c4.toJson())
            is SyncMessage.Nim -> json.put("t", "nim").put("nim", message.nim.toJson())
            is SyncMessage.Rps -> json.put("t", "rps").put("rps", message.rps.toJson())
            is SyncMessage.Dice -> json.put("t", "dice").put("player", message.table.player)
                .put("turn", JSONArray().also { array -> message.table.turn.forEach { array.put(it) } })
                .put("pig", message.table.pigTotal).put("bust", message.table.pigBust)
        }
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    /** Le message codé dans [bytes], ou null s'il est illisible, trop gros ou d'une autre version. */
    fun decode(bytes: ByteArray): SyncMessage? {
        return try {
            val raw = inflate(bytes) ?: return null
            val json = JSONObject(String(raw, Charsets.UTF_8))
            if (json.optInt("v") != VERSION) return null
            when (json.optString("t")) {
                "state" -> SyncMessage.State(
                    game = SavedGame.fromJson(json.getJSONObject("game")),
                    canUndo = json.optBoolean("undo"),
                    canRedo = json.optBoolean("redo")
                )
                "op" -> syncOpFromJson(json.getJSONObject("op"))?.let { SyncMessage.Operation(it) }
                "mm" -> mmMessageFromJson(json.getJSONObject("mm"))?.let { SyncMessage.Mm(it) }
                "bs" -> bsMessageFromJson(json.getJSONObject("bs"))?.let { SyncMessage.Bs(it) }
                "ttt" -> tttMessageFromJson(json.getJSONObject("ttt"))?.let { SyncMessage.Ttt(it) }
                "c4" -> c4MessageFromJson(json.getJSONObject("c4"))?.let { SyncMessage.C4(it) }
                "nim" -> nimMessageFromJson(json.getJSONObject("nim"))?.let { SyncMessage.Nim(it) }
                "rps" -> rpsMessageFromJson(json.getJSONObject("rps"))?.let { SyncMessage.Rps(it) }
                "dice" -> {
                    val turn = json.getJSONArray("turn")
                    SyncMessage.Dice(
                        DiceTable(
                            player = json.getInt("player"),
                            turn = (0 until turn.length()).map { turn.getInt(it) },
                            pigTotal = json.getInt("pig"),
                            pigBust = json.getBoolean("bust")
                        )
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            // Message corrompu ou venant d'une autre version : on l'ignore.
            null
        }
    }

    private fun inflate(bytes: ByteArray): ByteArray? {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_DECODED_BYTES) return null
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }
}
