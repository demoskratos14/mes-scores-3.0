package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RhythmChartTest {

    private val rate = 11_025

    /** Une chanson synthétique : une salve de [freq] Hz de [burstMs] ms à chacun des instants [atMs], sur [totalMs] ms. */
    private fun song(totalMs: Int, bursts: List<Triple<Int, Int, Double>>): FloatArray {
        val samples = FloatArray(totalMs * rate / 1000)
        for ((atMs, burstMs, freq) in bursts) {
            val start = atMs * rate / 1000
            val length = burstMs * rate / 1000
            for (i in 0 until length) {
                if (start + i >= samples.size) break
                // Un début franc puis une petite décroissance : comme une note jouée.
                val envelope = if (burstMs < 150) 1.0 - i.toDouble() / length else 1.0
                samples[start + i] += (0.6 * envelope * sin(2 * PI * freq * i / rate)).toFloat()
            }
        }
        return samples
    }

    private fun chartOf(samples: FloatArray, difficulty: RhythmDifficulty): RhythmChart =
        RhythmAnalyzer.buildChart(RhythmAnalyzer.spectrogram(samples, rate), difficulty)

    // --- FFT ---

    @Test
    fun laTransformeeDeFourierTrouveLaFrequenceDUnSinus() {
        val n = 1024
        val re = FloatArray(n) { sin(2 * PI * 32 * it / n).toFloat() }
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        val magnitudes = FloatArray(n / 2) { kotlin.math.sqrt(re[it] * re[it] + im[it] * im[it]) }
        assertEquals(32, magnitudes.indices.maxByOrNull { magnitudes[it] })
        assertEquals(n / 2f, magnitudes[32], 1f)
    }

    // --- Analyse ---

    @Test
    fun uneSuiteDeCoupsRegulieredonneDesNotesAuxBonsInstants() {
        // Un coup grave toutes les 500 ms pendant 10 secondes.
        val times = (1..18).map { it * 500 }
        val samples = song(10_000, times.map { Triple(it, 80, 100.0) })
        val chart = chartOf(samples, RhythmDifficulty.MEDIUM)
        assertTrue("trop peu de notes : ${chart.notes.size}", chart.notes.size >= 15)
        assertTrue("trop de notes : ${chart.notes.size}", chart.notes.size <= 25)
        // Chaque note est proche d'un coup (à 60 ms près : la fenêtre d'analyse fait 93 ms).
        for (note in chart.notes) {
            val nearest = times.minOf { abs(it - note.timeMs) }
            assertTrue("note à ${note.timeMs} ms loin de tout coup ($nearest ms)", nearest <= 60)
        }
        // Un coup grave tombe (presque toujours) dans la piste de gauche.
        assertTrue(chart.notes.count { it.lane == 0 } >= chart.notes.size * 3 / 4)
    }

    @Test
    fun lesAigusTombentADroiteEtLesGravesAGauche() {
        val low = song(6_000, listOf(Triple(1_000, 80, 100.0)))
        val high = song(6_000, listOf(Triple(1_000, 80, 3_000.0)))
        val lowLane = chartOf(low, RhythmDifficulty.HARD).notes.map { it.lane }
        val highLane = chartOf(high, RhythmDifficulty.HARD).notes.map { it.lane }
        assertTrue(lowLane.isNotEmpty() && highLane.isNotEmpty())
        assertTrue("grave : $lowLane aigu : $highLane", lowLane.max() < highLane.min())
        assertEquals(4, highLane.max())
    }

    @Test
    fun unSonQuiDureDevientUneNoteAMaintenir() {
        val samples = song(8_000, listOf(Triple(2_000, 2_000, 800.0)))
        val chart = chartOf(samples, RhythmDifficulty.MEDIUM)
        val hold = chart.notes.firstOrNull { it.isHold }
        assertTrue("aucune note maintenue : ${chart.notes}", hold != null)
        assertTrue("début ${hold!!.timeMs}", abs(hold.timeMs - 2_000) <= 100)
        assertTrue("durée ${hold.holdMs}", hold.holdMs in 1_400..2_100)
    }

    @Test
    fun uneSalveCourteNEstPasUneNoteAMaintenir() {
        val samples = song(6_000, listOf(Triple(2_000, 80, 800.0)))
        val chart = chartOf(samples, RhythmDifficulty.MEDIUM)
        assertTrue(chart.notes.isNotEmpty())
        assertTrue(chart.notes.none { it.isHold })
    }

    @Test
    fun lesNiveauxRespectentLeurNombreDePistesEtLEcartMinimal() {
        val times = (1..60).map { it * 150 }
        val samples = song(10_000, times.mapIndexed { i, t -> Triple(t, 60, if (i % 2 == 0) 100.0 else 2_500.0) })
        for (difficulty in RhythmDifficulty.values()) {
            val chart = chartOf(samples, difficulty)
            assertEquals(difficulty.lanes, chart.lanes)
            assertTrue(chart.notes.all { it.lane in 0 until difficulty.lanes })
            assertEquals(chart.notes.sortedWith(compareBy({ it.timeMs }, { it.lane })), chart.notes)
            val starts = chart.notes.map { it.timeMs }.distinct()
            // Deux instants de notes sont espacés d'au moins l'écart minimal (à une tranche près).
            assertTrue(starts.zipWithNext().all { (a, b) -> b - a >= difficulty.minGapMs - 25 })
            if (difficulty.maxChord == 1) assertEquals(chart.notes.size, starts.size)
        }
    }

    @Test
    fun lesNiveauxDifficilesOntPlusDeNotes() {
        val times = (1..70).map { it * 190 }
        val samples = song(14_000, times.map { Triple(it, 60, 150.0) })
        val easy = chartOf(samples, RhythmDifficulty.EASY).notes.size
        val medium = chartOf(samples, RhythmDifficulty.MEDIUM).notes.size
        val hard = chartOf(samples, RhythmDifficulty.HARD).notes.size
        assertTrue("facile $easy, moyen $medium, difficile $hard", easy <= medium && medium <= hard && easy < hard)
    }

    @Test
    fun enFacileUneNoteMaintenueEstSeule() {
        val samples = song(10_000, listOf(Triple(2_000, 2_500, 800.0), Triple(3_000, 80, 100.0), Triple(3_600, 80, 3_000.0)))
        val chart = chartOf(samples, RhythmDifficulty.EASY)
        val hold = chart.notes.firstOrNull { it.isHold } ?: return // pas de maintien détecté : rien à vérifier
        assertTrue(chart.notes.none { it !== hold && it.timeMs > hold.timeMs && it.timeMs <= hold.endMs })
    }

    @Test
    fun unSilenceNeDonneAucuneNote() {
        val chart = chartOf(FloatArray(rate * 5), RhythmDifficulty.HARD)
        assertTrue(chart.notes.isEmpty())
        assertEquals(0f, chart.levelAt(0, 1_000), 0f)
    }

    @Test
    fun unSignalTropCourtDonneUneChansonVide() {
        val spec = RhythmAnalyzer.spectrogram(FloatArray(100), rate)
        assertEquals(0, spec.frames)
        assertTrue(RhythmAnalyzer.buildChart(spec, RhythmDifficulty.EASY).notes.isEmpty())
    }

    @Test
    fun leNiveauSonoreSuitLaMusique() {
        val samples = song(6_000, listOf(Triple(3_000, 1_000, 100.0)))
        val chart = chartOf(samples, RhythmDifficulty.MEDIUM)
        assertTrue(chart.levelAt(0, 3_500) > 0.8f)
        assertTrue(chart.levelAt(0, 1_000) < 0.2f)
    }

    // --- Partie ---

    private fun chart(vararg notes: RhythmNote, difficulty: RhythmDifficulty = RhythmDifficulty.MEDIUM) =
        RhythmChart(difficulty, notes.toList(), 20_000, 10f, 0f, Array(difficulty.lanes) { FloatArray(0) })

    @Test
    fun uneNoteToucheeJusteEstParfaite() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0)))
        assertEquals(Judgement.PERFECT, run.press(0, 1_030))
        assertEquals(1, run.perfect)
        assertEquals(100, run.score)
        assertEquals(1, run.combo)
        assertEquals(0, run.unresolved)
        assertEquals(NoteState.HIT, run.states[0])
    }

    @Test
    fun uneNoteUnPeuDecaleeEstBien() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0)))
        assertEquals(Judgement.GOOD, run.press(0, 900))
        assertEquals(60, run.score)
    }

    @Test
    fun uneToucheDansLeVideCasseLaSerie() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0), RhythmNote(5_000, 0)))
        run.press(0, 1_000)
        assertEquals(1, run.combo)
        assertNull(run.press(0, 3_000))
        assertEquals(0, run.combo)
        // Hors de la fenêtre : la note suivante n'est pas touchée.
        assertEquals(1, run.unresolved)
    }

    @Test
    fun enFacileUneToucheDansLeVideNeCassePasLaSerie() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0), RhythmNote(5_000, 0), difficulty = RhythmDifficulty.EASY))
        run.press(0, 1_000)
        run.press(0, 3_000)
        assertEquals(1, run.combo)
    }

    @Test
    fun uneToucheDansUneAutrePisteNeToucheRien() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0)))
        assertNull(run.press(2, 1_000))
        assertEquals(1, run.unresolved)
    }

    @Test
    fun uneNoteTropEnRetardEstRatee() {
        val run = RhythmRun(chart(RhythmNote(1_000, 1), RhythmNote(2_000, 1)))
        run.update(1_000 + run.missMs + 1)
        assertEquals(1, run.missed)
        assertEquals(NoteState.MISSED, run.states[0])
        assertEquals(NoteState.PENDING, run.states[1])
        assertEquals(Judgement.MISS, run.lastJudgement)
    }

    @Test
    fun lesNotesRateesCassentLaSerie() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0), RhythmNote(2_000, 0)))
        run.press(0, 1_000)
        run.update(5_000)
        assertEquals(0, run.combo)
        assertEquals(1, run.maxCombo)
        assertEquals(1, run.missed)
    }

    @Test
    fun leMultiplicateurMonteTousLesDixNotes() {
        val notes = (0 until 35).map { RhythmNote(1_000 + it * 500, 0) }
        val run = RhythmRun(chart(*notes.toTypedArray()))
        assertEquals(1, run.multiplier())
        notes.forEachIndexed { i, note ->
            if (i == 10) assertEquals(2, run.multiplier())
            if (i == 20) assertEquals(3, run.multiplier())
            if (i == 30) assertEquals(4, run.multiplier())
            run.press(0, note.timeMs)
        }
        // 10 notes à x1, 10 à x2, 10 à x3, 5 à x4.
        assertEquals(100 * (10 + 20 + 30 + 20), run.score)
        assertEquals(35, run.maxCombo)
    }

    @Test
    fun uneNoteMaintenueJusquAuBoutEstReussie() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0, holdMs = 1_000)))
        run.press(0, 1_000)
        assertEquals(NoteState.HOLDING, run.states[0])
        assertEquals(1, run.unresolved)
        run.release(0, 1_950)
        assertEquals(NoteState.DONE, run.states[0])
        assertEquals(1, run.holdsDone)
        assertEquals(0, run.unresolved)
        assertEquals(100 + 50, run.score)
    }

    @Test
    fun uneNoteMaintenueTermineeToutSeuleSiLeDoigtResteAppuye() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0, holdMs = 1_000)))
        run.press(0, 1_000)
        run.update(2_001)
        assertEquals(NoteState.DONE, run.states[0])
        assertEquals(1, run.holdsDone)
    }

    @Test
    fun leverLeDoigtTropTotCasseLaNoteMaintenue() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0, holdMs = 1_000)))
        run.press(0, 1_000)
        run.release(0, 1_400)
        assertEquals(NoteState.BROKEN, run.states[0])
        assertEquals(1, run.holdsBroken)
        assertEquals(0, run.combo)
        assertEquals(0, run.unresolved)
    }

    @Test
    fun uneNoteMaintenueRatee() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0, holdMs = 1_000)))
        run.update(3_000)
        assertEquals(NoteState.MISSED, run.states[0])
        assertEquals(0, run.holdsDone)
    }

    @Test
    fun finirLaChansonResoutToutCeQuiReste() {
        val run = RhythmRun(chart(RhythmNote(1_000, 0), RhythmNote(2_000, 1, holdMs = 500)))
        run.press(1, 2_000)
        run.finish(0)
        assertEquals(0, run.unresolved)
        assertEquals(1, run.missed)
        assertEquals(1, run.holdsDone)
    }

    @Test
    fun laNoteFinaleDependDeLaPrecision() {
        val notes = (0 until 20).map { RhythmNote(1_000 + it * 500, 0) }
        fun gradeWith(hits: Int): RhythmGrade {
            val run = RhythmRun(chart(*notes.toTypedArray()))
            notes.take(hits).forEach { run.press(0, it.timeMs) }
            run.finish(0)
            return run.grade
        }
        assertEquals(RhythmGrade.S, gradeWith(20))
        assertEquals(RhythmGrade.A, gradeWith(18))
        assertEquals(RhythmGrade.B, gradeWith(15))
        assertEquals(RhythmGrade.C, gradeWith(11))
        assertEquals(RhythmGrade.D, gradeWith(4))
        assertEquals(RhythmGrade.D, RhythmRun(chart()).grade)
    }

    @Test
    fun lesFenetresSontPlusLargesEnFacile() {
        val easy = RhythmRun(chart(difficulty = RhythmDifficulty.EASY))
        val hard = RhythmRun(chart(difficulty = RhythmDifficulty.HARD))
        assertTrue(easy.goodMs > hard.goodMs)
        assertTrue(easy.perfectMs < easy.goodMs && easy.goodMs < easy.missMs)
    }

    // --- Horloge ---

    @Test
    fun lHorlogeAvanceAvecLeTelephoneEntreDeuxPositions() {
        val clock = SongClock()
        clock.start(10_000)
        assertEquals(0, clock.songTime(10_000))
        assertEquals(750, clock.songTime(10_750))
    }

    @Test
    fun lHorlogeSeRapprocheDeLaPositionDuLecteur() {
        val clock = SongClock()
        clock.start(10_000)
        // Le lecteur a en réalité 200 ms de retard : à l'uptime 10_000 + t, sa position est t - 200.
        for (t in 300..3_000 step 20) clock.sync((t - 200).toLong(), 10_000L + t)
        val estimate = clock.songTime(13_000)
        assertTrue("estimation $estimate", abs(estimate - 2_800) <= 5)
    }

    @Test
    fun lHorlogeIgnoreLaPositionZeroDuDebut() {
        val clock = SongClock()
        clock.start(10_000)
        clock.sync(0, 10_100)
        assertEquals(500, clock.songTime(10_500))
    }

    @Test
    fun apresUnePauseLHorlogeRepartDeLaBonnePosition() {
        val clock = SongClock()
        clock.start(10_000)
        clock.hardSet(4_000, 50_000)
        assertEquals(4_250, clock.songTime(50_250))
        assertNotEquals(0, clock.songTime(50_250))
    }
}
