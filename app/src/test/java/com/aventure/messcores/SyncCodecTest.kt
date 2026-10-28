package com.aventure.messcores

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "fr")
class SyncCodecTest {

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }

    @Test
    fun lEtatDeLaPartieSurvitALEnvoi() {
        val game = ScoreViewModel()
        game.initGame(listOf("Anna", "Ben"), GameRules(id = "t", name = "T"))
        game.setCell(0, 0, 12, false)
        game.setCell(0, 1, 5, true)

        val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.State(game.snapshot(), canUndo = true, canRedo = false)))

        decoded as SyncMessage.State
        assertEquals(listOf("Anna", "Ben"), decoded.game.players)
        assertEquals("t", decoded.game.gameRules.id)
        assertEquals(12, decoded.game.cellSnapshots!![0][0].baseValue)
        assertEquals(true, decoded.game.cellSnapshots!![0][1].isNegative)
        assertTrue(decoded.canUndo)
        assertFalse(decoded.canRedo)
    }

    @Test
    fun leModeJouableDeLOrganisateurSurvitALEnvoi() {
        val game = ScoreViewModel()
        game.initGame(listOf("Anna", "Ben"), GameRules(id = "t", name = "T"))

        val playable = SyncCodec.decode(SyncCodec.encode(SyncMessage.State(game.snapshot(), canUndo = false, canRedo = false, playable = true)))
        val scoresOnly = SyncCodec.decode(SyncCodec.encode(SyncMessage.State(game.snapshot(), canUndo = false, canRedo = false)))

        assertTrue((playable as SyncMessage.State).playable)
        assertFalse((scoresOnly as SyncMessage.State).playable)
    }

    @Test
    fun chaqueActionSurvitALEnvoi() {
        val teamRound = TeamRound("Équipe A", setOf(0, 2), 40, deltas = listOf(40, -20, 40, -60))
        val ops = listOf(
            SyncOp.SetCell(2, 1, 17, false),
            SyncOp.SetCell(0, 0, null, false),
            SyncOp.SetCell(3, 2, 5, true),
            SyncOp.SetMultiplier(1, 0, "x2"),
            SyncOp.IncrementCounter(1, -3),
            SyncOp.SetPhase(0, 4),
            SyncOp.AddTeamRound("Preneur", setOf(1, 3), 56, true, "normal"),
            SyncOp.AppendTeamRound(teamRound),
            SyncOp.ReplaceTeamRound(2, teamRound),
            SyncOp.RemoveTeamRound(1),
            SyncOp.SetPlayerColor(1, 0xFF336699.toInt()),
            SyncOp.Undo,
            SyncOp.Redo
        )
        for (op in ops) {
            val decoded = SyncCodec.decode(SyncCodec.encode(SyncMessage.Operation(op)))
            assertEquals(op.toString(), SyncMessage.Operation(op), decoded)
        }
    }

    @Test
    fun desOctetsIllisiblesSontIgnores() {
        assertNull(SyncCodec.decode(ByteArray(0)))
        assertNull(SyncCodec.decode(byteArrayOf(1, 2, 3, 4)))
        assertNull(SyncCodec.decode(gzip("pas du json")))
        assertNull(SyncCodec.decode(gzip("""{"v":1,"t":"inconnu"}""")))
        assertNull(SyncCodec.decode(gzip("""{"v":1,"t":"op","op":{"op":"inconnue"}}""")))
        // Champ obligatoire manquant.
        assertNull(SyncCodec.decode(gzip("""{"v":1,"t":"op","op":{"op":"setCell"}}""")))
    }

    @Test
    fun uneAutreVersionDuProtocoleEstIgnoree() {
        val message = JSONObject().put("v", SyncCodec.VERSION + 1).put("t", "op")
            .put("op", JSONObject().put("op", "undo")).toString()
        assertNull(SyncCodec.decode(gzip(message)))
        val current = JSONObject().put("v", SyncCodec.VERSION).put("t", "op")
            .put("op", JSONObject().put("op", "undo")).toString()
        assertNotNull(SyncCodec.decode(gzip(current)))
    }

    @Test
    fun uneGrossePartieTientDansUnMessage() {
        val game = ScoreViewModel()
        game.initGame((1..12).map { "Joueur $it" }, GameRules(id = "t", name = "T"))
        val random = java.util.Random(42)
        repeat(120) { round -> (0 until 12).forEach { player -> game.setCell(round, player, random.nextInt(200) - 50, false) } }

        val bytes = SyncCodec.encode(SyncMessage.State(game.snapshot(), canUndo = true, canRedo = true))

        assertTrue("taille : ${bytes.size}", bytes.size <= SyncCodec.MAX_BYTES)
    }
}
