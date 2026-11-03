package com.aventure.messcores

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Les records des jeux jouables seul (Ronds, Simon) : la règle du record et sa sauvegarde d'une séance à l'autre. */
@RunWith(RobolectricTestRunner::class)
class RecordsTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun unScoreNulNEstJamaisUnRecord() {
        assertFalse(RecordRules.isNewRecord(previous = 0, score = 0))
        assertFalse(RecordRules.isNewRecord(previous = 5, score = 0))
    }

    @Test
    fun lePremierScoreAuDessusDeZeroEstUnRecord() {
        assertTrue(RecordRules.isNewRecord(previous = 0, score = 1))
    }

    @Test
    fun ilFautBattreLeRecordPasSeulementL_egaler() {
        assertFalse(RecordRules.isNewRecord(previous = 7, score = 7))
        assertFalse(RecordRules.isNewRecord(previous = 7, score = 6))
        assertTrue(RecordRules.isNewRecord(previous = 7, score = 8))
    }

    @Test
    fun sansRecordLeJeuEnAZero() {
        assertEquals(0, GameRecords.load(context, CIRCLES_ID))
    }

    @Test
    fun unRecordBattuEstGardeEtRelu() {
        val first = GameRecords.submit(context, SIMON_ID, 4)
        assertEquals(RecordResult(record = 4, isNew = true), first)
        assertEquals(4, GameRecords.load(context, SIMON_ID))
    }

    @Test
    fun unScoreMoinsBonLaisseLeRecord() {
        GameRecords.submit(context, SIMON_ID, 9)
        val worse = GameRecords.submit(context, SIMON_ID, 3)
        assertEquals(RecordResult(record = 9, isNew = false), worse)
        assertEquals(9, GameRecords.load(context, SIMON_ID))
    }

    @Test
    fun leMemeMorceauADesRecordsSepareParDifficulte() {
        val song = RhythmRecords.songId("Ma chanson", 4_200_000L)
        assertEquals(RhythmRecords.key(song, 1), RhythmRecords.key(song, 1))
        assertNotEquals(RhythmRecords.key(song, 0), RhythmRecords.key(song, 2))
        GameRecords.submit(context, RhythmRecords.key(song, 0), 9_000)
        GameRecords.submit(context, RhythmRecords.key(song, 2), 4_000)
        assertEquals(9_000, GameRecords.load(context, RhythmRecords.key(song, 0)))
        assertEquals(4_000, GameRecords.load(context, RhythmRecords.key(song, 2)))
        assertEquals(0, GameRecords.load(context, RhythmRecords.key(song, 1)))
    }

    @Test
    fun deuxMorceauxOntChacunSonRecord() {
        val a = RhythmRecords.songId("Morceau", 1_000L)
        val b = RhythmRecords.songId("Morceau", 2_000L)
        assertNotEquals(RhythmRecords.key(a, 1), RhythmRecords.key(b, 1))
        assertEquals("Morceau", RhythmRecords.songId("Morceau", 0L))
    }

    @Test
    fun chaqueJeuAsonRecord() {
        GameRecords.submit(context, SIMON_ID, 12)
        GameRecords.submit(context, CIRCLES_ID, 5)
        assertEquals(12, GameRecords.load(context, SIMON_ID))
        assertEquals(5, GameRecords.load(context, CIRCLES_ID))
    }
}
