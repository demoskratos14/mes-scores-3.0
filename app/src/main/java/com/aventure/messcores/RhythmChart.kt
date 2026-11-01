package com.aventure.messcores

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Identifiant du jeu de rythme parmi les jeux prédéfinis (écran à part : voir [RhythmScreen]). */
internal const val RHYTHM_ID = "builtin_rhythm"

/**
 * Niveaux de difficulté du jeu de rythme. Plus le niveau monte, plus il y a de pistes, de notes, d'accords (deux notes
 * à la fois) et moins la fenêtre pour toucher la note est large.
 */
internal enum class RhythmDifficulty(
    /** Nombre de pistes (colonnes). */
    val lanes: Int,
    /** Écart minimal entre deux notes d'une même piste. */
    val minLaneGapMs: Int,
    /** Écart minimal entre deux instants de notes, toutes pistes confondues. */
    val minGapMs: Int,
    /** Seuil de détection d'une attaque : plus il est haut, moins il y a de notes. */
    val sensitivity: Float,
    /** Nombre maximal de notes simultanées (accord). */
    val maxChord: Int,
    /** Durée minimale d'un son tenu pour qu'il devienne une note à maintenir. */
    val minHoldMs: Int,
    /** Temps que met une note pour descendre du haut de l'écran à la ligne de frappe. */
    val approachMs: Int,
    /** Facteur appliqué aux fenêtres de précision : plus de 1 pardonne davantage. */
    val windowScale: Float,
    /** Faux : pas de note dans une autre piste pendant qu'une note est maintenue (un seul doigt à la fois). */
    val notesDuringHold: Boolean
) {
    EASY(4, 600, 380, 1.6f, 1, 800, 2600, 1.3f, false),
    MEDIUM(4, 350, 230, 1.2f, 1, 550, 2200, 1.0f, true),
    HARD(5, 220, 130, 0.9f, 2, 450, 1800, 0.85f, true)
}

/** Une note : [timeMs] est l'instant où elle doit être touchée ; [holdMs] > 0 pour une note à maintenir. */
internal data class RhythmNote(val timeMs: Int, val lane: Int, val holdMs: Int = 0) {
    val isHold: Boolean get() = holdMs > 0
    val endMs: Int get() = timeMs + holdMs
}

/**
 * Le « spectrogramme » d'une chanson : pour chaque tranche de temps, l'énergie dans [FINE_BANDS] bandes de fréquences
 * réparties du grave à l'aigu (comme les barres d'un égaliseur graphique). On le calcule une seule fois par chanson ;
 * la partition (voir [RhythmAnalyzer.buildChart]) s'en déduit ensuite pour chaque difficulté.
 */
internal class Spectrogram(
    /** [frame][bande], en échelle logarithmique. */
    val bands: Array<FloatArray>,
    /** Durée entre deux tranches. */
    val hopMs: Float,
    /** Instant de la première tranche. */
    val firstFrameMs: Float,
    val durationMs: Int
) {
    val frames: Int get() = bands.size
    fun timeOf(frame: Int): Int = (firstFrameMs + frame * hopMs).roundToInt()
}

/** La partition d'une chanson pour une difficulté : les notes, triées par instant, et le niveau sonore de chaque piste. */
internal class RhythmChart(
    val difficulty: RhythmDifficulty,
    val notes: List<RhythmNote>,
    val durationMs: Int,
    private val hopMs: Float,
    private val firstFrameMs: Float,
    /** [piste][tranche] : niveau sonore de la piste entre 0 et 1, pour faire « danser » l'écran avec la musique. */
    private val levels: Array<FloatArray>
) {
    val lanes: Int get() = difficulty.lanes

    /** Niveau sonore (0 à 1) de la piste à l'instant [ms] de la chanson. */
    fun levelAt(lane: Int, ms: Int): Float {
        val row = levels.getOrNull(lane) ?: return 0f
        if (row.isEmpty()) return 0f
        val frame = ((ms - firstFrameMs) / hopMs).roundToInt().coerceIn(0, row.size - 1)
        return row[frame]
    }
}

/** Transformée de Fourier rapide (radix 2) sur [n] points, [n] étant une puissance de 2. */
internal class Fft(private val n: Int) {
    private val cosTable = FloatArray(n / 2) { cos(2.0 * PI * it / n).toFloat() }
    private val sinTable = FloatArray(n / 2) { sin(2.0 * PI * it / n).toFloat() }
    private val reversed = IntArray(n)

    init {
        require(n >= 2 && n and (n - 1) == 0) { "n doit être une puissance de 2" }
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) reversed[i] = Integer.reverse(i) ushr (32 - bits)
    }

    /** Transforme [re] et [im] en place (partie réelle et imaginaire). */
    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until n) {
            val j = reversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size / 2
            val step = n / size
            var start = 0
            while (start < n) {
                var k = 0
                for (j in start until start + half) {
                    val wr = cosTable[k]
                    val wi = -sinTable[k]
                    val tr = re[j + half] * wr - im[j + half] * wi
                    val ti = re[j + half] * wi + im[j + half] * wr
                    re[j + half] = re[j] - tr
                    im[j + half] = im[j] - ti
                    re[j] += tr
                    im[j] += ti
                    k += step
                }
                start += size
            }
            size = size shl 1
        }
    }
}

/**
 * Cherche dans l'audio les « attaques » (le flux spectral : une hausse brusque d'énergie dans une bande de fréquences)
 * et en fait des notes : les graves (grosse caisse, basse) à gauche, les aigus (mélodie, cymbales) à droite. Un son qui
 * dure après son attaque devient une note à maintenir.
 */
internal object RhythmAnalyzer {
    const val FFT_SIZE = 1024
    const val HOP = 128
    const val FINE_BANDS = 24

    /** Entre le dernier instant d'une note à maintenir et la note suivante de la piste. */
    private const val HOLD_RELEASE_GAP_MS = 150
    private const val MAX_HOLD_MS = 3_500
    private const val MIN_FLUX = 0.08f

    /**
     * Calcule le spectrogramme d'un signal mono ([samples], entre -1 et 1). [onProgress] reçoit un nombre entre 0 et 1 ;
     * [checkCancel] est appelée régulièrement et peut lever une exception pour interrompre le calcul.
     */
    fun spectrogram(
        samples: FloatArray,
        sampleRate: Int,
        onProgress: (Float) -> Unit = {},
        checkCancel: () -> Unit = {}
    ): Spectrogram {
        val frameCount = if (samples.size < FFT_SIZE) 0 else (samples.size - FFT_SIZE) / HOP + 1
        val fft = Fft(FFT_SIZE)
        val window = FloatArray(FFT_SIZE) { (0.5 - 0.5 * cos(2.0 * PI * it / (FFT_SIZE - 1))).toFloat() }
        val binHz = sampleRate.toFloat() / FFT_SIZE
        val loBin = max(2, (50f / binHz).roundToInt())
        val hiBin = min(FFT_SIZE / 2 - 1, max(loBin + FINE_BANDS, (min(5_000f, sampleRate * 0.45f) / binHz).roundToInt()))
        // Frontières des bandes, espacées en échelle logarithmique ; chaque bande a au moins une case.
        val edges = IntArray(FINE_BANDS + 1) {
            (loBin * (hiBin.toDouble() / loBin).pow(it.toDouble() / FINE_BANDS)).roundToInt()
        }
        for (i in 1..FINE_BANDS) if (edges[i] <= edges[i - 1]) edges[i] = edges[i - 1] + 1

        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val bands = Array(frameCount) { FloatArray(FINE_BANDS) }
        for (f in 0 until frameCount) {
            if (f % 256 == 0) {
                checkCancel()
                onProgress(f.toFloat() / frameCount)
            }
            val offset = f * HOP
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0f
            }
            fft.transform(re, im)
            val row = bands[f]
            for (b in 0 until FINE_BANDS) {
                var sum = 0f
                val hi = min(edges[b + 1], FFT_SIZE / 2)
                for (k in edges[b] until hi) sum += sqrt(re[k] * re[k] + im[k] * im[k])
                val mean = sum / max(1, hi - edges[b]) / (FFT_SIZE / 2)
                row[b] = ln(1f + 200f * mean)
            }
        }
        onProgress(1f)
        return Spectrogram(
            bands = bands,
            hopMs = HOP * 1000f / sampleRate,
            // Une attaque est repérée dès qu'elle entre dans la fenêtre d'analyse, avant que celle-ci soit centrée dessus :
            // on date donc chaque tranche aux trois quarts de sa fenêtre, ce qui donne les attaques à ± 10 ms.
            firstFrameMs = FFT_SIZE * 0.77f * 1000f / sampleRate,
            durationMs = (samples.size * 1000L / sampleRate).toInt()
        )
    }

    private class Candidate(val frame: Int, val lane: Int, val strength: Float)

    /** Fabrique la partition de la chanson pour la difficulté [difficulty]. */
    fun buildChart(spec: Spectrogram, difficulty: RhythmDifficulty): RhythmChart {
        val frames = spec.frames
        val lanes = difficulty.lanes
        val hop = spec.hopMs
        val level = Array(lanes) { FloatArray(frames) }
        val flux = Array(lanes) { FloatArray(frames) }
        for (l in 0 until lanes) {
            val first = l * FINE_BANDS / lanes
            val last = (l + 1) * FINE_BANDS / lanes
            val count = (last - first).toFloat()
            for (f in 0 until frames) {
                var sum = 0f
                var rise = 0f
                for (b in first until last) {
                    val v = spec.bands[f][b]
                    sum += v
                    if (f > 0) {
                        val delta = v - spec.bands[f - 1][b]
                        if (delta > 0f) rise += delta
                    }
                }
                level[l][f] = sum / count
                flux[l][f] = rise / count
            }
        }

        val candidates = ArrayList<Candidate>()
        for (l in 0 until lanes) candidates += laneOnsets(flux[l], l, hop, difficulty)

        val notes = chooseNotes(candidates, spec, difficulty)
        val withHolds = addHolds(notes, level, spec, difficulty)
        return RhythmChart(
            difficulty = difficulty,
            notes = withHolds,
            durationMs = spec.durationMs,
            hopMs = hop,
            firstFrameMs = spec.firstFrameMs,
            levels = Array(lanes) { normalized(level[it]) }
        )
    }

    /** Les attaques d'une piste : sommets du flux nettement au-dessus de la moyenne locale, espacés d'au moins l'écart de la difficulté. */
    private fun laneOnsets(flux: FloatArray, lane: Int, hopMs: Float, d: RhythmDifficulty): List<Candidate> {
        val frames = flux.size
        if (frames < 3) return emptyList()
        val prefix = DoubleArray(frames + 1)
        val prefixSq = DoubleArray(frames + 1)
        for (i in 0 until frames) {
            prefix[i + 1] = prefix[i] + flux[i]
            prefixSq[i + 1] = prefixSq[i] + flux[i].toDouble() * flux[i]
        }
        val around = max(2, (500f / hopMs).roundToInt())
        val peakSpan = max(1, (60f / hopMs).roundToInt())
        val minGapFrames = max(1, (d.minLaneGapMs / hopMs).roundToInt())

        val found = ArrayList<Candidate>()
        for (f in 1 until frames - 1) {
            val v = flux[f]
            if (v < MIN_FLUX) continue
            var isPeak = true
            for (k in max(0, f - peakSpan)..min(frames - 1, f + peakSpan)) {
                if (k != f && (flux[k] > v || (flux[k] == v && k < f))) {
                    isPeak = false
                    break
                }
            }
            if (!isPeak) continue
            val lo = max(0, f - around)
            val hi = min(frames - 1, f + around)
            val n = (hi - lo + 1).toDouble()
            val mean = (prefix[hi + 1] - prefix[lo]) / n
            val variance = max(0.0, (prefixSq[hi + 1] - prefixSq[lo]) / n - mean * mean)
            val sd = sqrt(variance)
            if (v <= mean + d.sensitivity * sd) continue
            val strength = ((v - mean) / (sd + 1e-4)).toFloat()
            val last = found.lastOrNull()
            if (last != null && f - last.frame < minGapFrames) {
                if (strength > last.strength) found[found.size - 1] = Candidate(f, lane, strength)
            } else {
                found += Candidate(f, lane, strength)
            }
        }
        return found
    }

    /** Regroupe les attaques simultanées en accords, puis espace les instants de notes d'au moins l'écart de la difficulté. */
    private fun chooseNotes(candidates: List<Candidate>, spec: Spectrogram, d: RhythmDifficulty): List<RhythmNote> {
        if (candidates.isEmpty()) return emptyList()
        val sorted = candidates.sortedWith(compareBy({ it.frame }, { -it.strength }))
        val together = max(1, (30f / spec.hopMs).roundToInt())
        val minGapFrames = max(1, (d.minGapMs / spec.hopMs).roundToInt())

        val groups = ArrayList<List<Candidate>>()
        var i = 0
        while (i < sorted.size) {
            val group = ArrayList<Candidate>()
            val startFrame = sorted[i].frame
            while (i < sorted.size && sorted[i].frame - startFrame <= together) {
                if (group.none { it.lane == sorted[i].lane }) group += sorted[i]
                i++
            }
            group.sortByDescending { it.strength }
            groups += group.take(d.maxChord)
        }

        val kept = ArrayList<List<Candidate>>()
        for (group in groups) {
            val last = kept.lastOrNull()
            if (last != null && group[0].frame - last[0].frame < minGapFrames) {
                // Trop proche de la note précédente : on garde la plus marquée des deux.
                if (group[0].strength > last[0].strength * 1.3f) kept[kept.size - 1] = group
            } else {
                kept += group
            }
        }
        return kept.flatMap { group ->
            group.sortedBy { it.lane }.map { RhythmNote(spec.timeOf(group[0].frame), it.lane) }
        }
    }

    /** Transforme en note à maintenir une note dont le son se prolonge, tant que la piste reste bien au-dessus de son niveau d'avant l'attaque. */
    private fun addHolds(
        notes: List<RhythmNote>,
        level: Array<FloatArray>,
        spec: Spectrogram,
        d: RhythmDifficulty
    ): List<RhythmNote> {
        val hop = spec.hopMs
        val frames = spec.frames
        if (frames == 0) return notes
        val maxHoldFrames = (MAX_HOLD_MS / hop).toInt()
        val before = max(1, (400f / hop).roundToInt())
        val probe = max(2, (120f / hop).roundToInt())
        val result = notes.toMutableList()
        val nextInLane = IntArray(notes.size) { Int.MAX_VALUE }
        val lastSeen = IntArray(d.lanes) { Int.MAX_VALUE }
        for (i in notes.indices.reversed()) {
            nextInLane[i] = lastSeen[notes[i].lane]
            lastSeen[notes[i].lane] = notes[i].timeMs
        }
        for ((index, note) in notes.withIndex()) {
            val row = level[note.lane]
            val f = ((note.timeMs - spec.firstFrameMs) / hop).roundToInt().coerceIn(0, frames - 1)
            // Niveau « tenu » : celui de la piste un instant après l'attaque (un coup sec est déjà retombé, un son tenu non).
            val sustain = row[min(frames - 1, f + probe)]
            var floor = sustain
            for (k in max(0, f - before)..f) floor = min(floor, row[k])
            if (sustain - floor < 0.05f) continue
            val threshold = floor + 0.6f * (sustain - floor)
            var g = f + probe
            while (g < frames && g - f < maxHoldFrames && row[g] >= threshold) g++
            var holdMs = ((g - 1 - f) * hop).toInt()
            val limit = nextInLane[index]
            if (limit != Int.MAX_VALUE) holdMs = min(holdMs, limit - HOLD_RELEASE_GAP_MS - note.timeMs)
            if (holdMs >= d.minHoldMs) result[index] = note.copy(holdMs = holdMs)
        }
        if (d.notesDuringHold) return result
        // Un seul doigt à la fois : on retire les notes des autres pistes jouées pendant qu'une note est maintenue.
        val filtered = ArrayList<RhythmNote>()
        var holdUntil = Int.MIN_VALUE
        for (note in result) {
            if (note.timeMs <= holdUntil) continue
            filtered += note
            if (note.isHold) holdUntil = note.endMs
        }
        return filtered
    }

    /** Ramène un niveau sonore entre 0 (le plus calme) et 1 (le plus fort, hors rares pointes). */
    private fun normalized(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val sorted = values.sortedArray()
        val low = sorted[(sorted.size * 0.05f).toInt()]
        val high = sorted[min(sorted.size - 1, (sorted.size * 0.98f).toInt())]
        val range = high - low
        if (range <= 1e-4f) return FloatArray(values.size)
        return FloatArray(values.size) { ((values[it] - low) / range).coerceIn(0f, 1f) }
    }
}

/** Le jugement d'une note touchée. */
internal enum class Judgement { PERFECT, GOOD, MISS }

/** La note finale, selon la précision. */
internal enum class RhythmGrade { S, A, B, C, D }

/** L'état d'une note pendant la partie. */
internal enum class NoteState { PENDING, HIT, HOLDING, DONE, MISSED, BROKEN }

/**
 * Une partie : juge les touches du joueur par rapport à la partition, compte le score, la série (combo) et les notes
 * réussies. Les instants sont ceux de la chanson, en millisecondes. Rien ici ne dépend d'Android.
 *
 * - Une note touchée à moins de [perfectMs] de son instant est « parfaite », à moins de [goodMs] elle est « bien » ;
 *   au-delà de [missMs] après son instant, elle est ratée.
 * - Une note à maintenir se réussit en gardant le doigt appuyé jusqu'à sa fin (à [holdToleranceMs] près).
 * - Le score est multiplié par 2, 3 puis 4 à partir de 10, 20 et 30 notes d'affilée.
 */
internal class RhythmRun(val chart: RhythmChart) {

    private val difficulty = chart.difficulty
    val notes: List<RhythmNote> = chart.notes
    val states = Array(notes.size) { NoteState.PENDING }

    val perfectMs = (BASE_PERFECT_MS * difficulty.windowScale).toInt()
    val goodMs = (BASE_GOOD_MS * difficulty.windowScale).toInt()
    val missMs = (BASE_MISS_MS * difficulty.windowScale).toInt()
    val holdToleranceMs = (BASE_HOLD_TOLERANCE_MS * difficulty.windowScale).toInt()

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    var perfect = 0
        private set
    var good = 0
        private set
    var missed = 0
        private set
    var holdsDone = 0
        private set
    var holdsBroken = 0
        private set

    /** Nombre de notes encore à jouer (pas encore touchées ni ratées, ou en cours de maintien). */
    var unresolved = notes.size
        private set

    /** Dernier jugement, pour l'afficher un instant : [lastJudgementMs] est l'instant (de la chanson) où il est tombé. */
    var lastJudgement: Judgement? = null
        private set
    var lastJudgementMs = 0
        private set

    // Pour chaque piste, les indices de ses notes dans l'ordre, et la première encore à jouer.
    private val laneNotes: Array<IntArray> = Array(difficulty.lanes) { lane ->
        notes.indices.filter { notes[it].lane == lane }.toIntArray()
    }
    private val cursor = IntArray(difficulty.lanes)
    private val holding = IntArray(difficulty.lanes) { -1 }

    fun multiplier(): Int = 1 + min(combo / 10, 3)

    private fun judge(judgement: Judgement, nowMs: Int) {
        lastJudgement = judgement
        lastJudgementMs = nowMs
    }

    private fun breakCombo() {
        combo = 0
    }

    private fun advanceCursor(lane: Int) {
        val list = laneNotes[lane]
        while (cursor[lane] < list.size && states[list[cursor[lane]]] != NoteState.PENDING) cursor[lane]++
    }

    /** Fait passer en « ratées » les notes trop en retard, et termine les notes maintenues jusqu'au bout. */
    fun update(nowMs: Int) {
        for (lane in laneNotes.indices) {
            val list = laneNotes[lane]
            advanceCursor(lane)
            while (cursor[lane] < list.size) {
                val i = list[cursor[lane]]
                if (nowMs <= notes[i].timeMs + missMs) break
                states[i] = NoteState.MISSED
                missed++
                unresolved--
                breakCombo()
                judge(Judgement.MISS, nowMs)
                advanceCursor(lane)
            }
            val h = holding[lane]
            if (h >= 0 && nowMs >= notes[h].endMs) completeHold(lane, nowMs)
        }
    }

    /** Le joueur touche la piste [lane] à l'instant [nowMs]. Renvoie le jugement, ou null si aucune note n'était à portée. */
    fun press(lane: Int, nowMs: Int): Judgement? {
        if (lane !in laneNotes.indices) return null
        update(nowMs)
        val list = laneNotes[lane]
        val i = if (cursor[lane] < list.size) list[cursor[lane]] else -1
        val inWindow = i >= 0 && abs(notes[i].timeMs - nowMs) <= goodMs
        if (!inWindow) {
            // Une touche dans le vide : elle casse la série (sauf en facile).
            if (difficulty != RhythmDifficulty.EASY) breakCombo()
            return null
        }
        val delta = abs(notes[i].timeMs - nowMs)
        val judgement = if (delta <= perfectMs) Judgement.PERFECT else Judgement.GOOD
        score += (if (judgement == Judgement.PERFECT) PERFECT_POINTS else GOOD_POINTS) * multiplier()
        if (judgement == Judgement.PERFECT) perfect++ else good++
        combo++
        maxCombo = max(maxCombo, combo)
        judge(judgement, nowMs)
        if (notes[i].isHold) {
            states[i] = NoteState.HOLDING
            holding[lane] = i
        } else {
            states[i] = NoteState.HIT
            unresolved--
        }
        advanceCursor(lane)
        return judgement
    }

    /** Le joueur lève le doigt de la piste [lane] : une note maintenue est réussie si la fin est proche, sinon elle est cassée. */
    fun release(lane: Int, nowMs: Int) {
        if (lane !in holding.indices) return
        val h = holding[lane]
        if (h < 0) return
        if (nowMs >= notes[h].endMs - holdToleranceMs) {
            completeHold(lane, nowMs)
        } else {
            states[h] = NoteState.BROKEN
            holding[lane] = -1
            holdsBroken++
            unresolved--
            breakCombo()
        }
    }

    private fun completeHold(lane: Int, nowMs: Int) {
        val h = holding[lane]
        if (h < 0) return
        states[h] = NoteState.DONE
        holding[lane] = -1
        holdsDone++
        unresolved--
        score += HOLD_POINTS * multiplier()
        judge(Judgement.PERFECT, nowMs)
    }

    /** Fin de la chanson : tout ce qui reste est joué comme si le temps était écoulé. */
    fun finish(nowMs: Int) {
        update(max(nowMs, (notes.maxOfOrNull { it.endMs } ?: 0) + missMs + 1))
    }

    /** Précision entre 0 et 1 : les notes parfaites comptent pleinement, les « bien » à 60 %, une note maintenue cassée retire 30 %. */
    val accuracy: Float
        get() {
            if (notes.isEmpty()) return 0f
            val weighted = perfect + 0.6f * good - 0.3f * holdsBroken
            return (weighted / notes.size).coerceIn(0f, 1f)
        }

    val grade: RhythmGrade
        get() = when {
            accuracy >= 0.95f -> RhythmGrade.S
            accuracy >= 0.85f -> RhythmGrade.A
            accuracy >= 0.70f -> RhythmGrade.B
            accuracy >= 0.50f -> RhythmGrade.C
            else -> RhythmGrade.D
        }

    companion object {
        const val BASE_PERFECT_MS = 70
        const val BASE_GOOD_MS = 130
        const val BASE_MISS_MS = 170
        const val BASE_HOLD_TOLERANCE_MS = 150
        const val PERFECT_POINTS = 100
        const val GOOD_POINTS = 60
        const val HOLD_POINTS = 50
    }
}

/**
 * L'horloge de la chanson. La position du lecteur audio n'est mise à jour que par à-coups : entre deux mises à jour, on
 * avance avec l'horloge du téléphone, et à chaque nouvelle position on se rapproche doucement de la vraie, pour que les
 * notes défilent sans saccade. Les instants « uptime » sont ceux de `SystemClock.uptimeMillis()`.
 */
internal class SongClock {
    // Instant de la chanson moins l'uptime : constant tant que la musique avance au rythme de l'horloge.
    private var offset = 0.0
    private var lastPosition = -1L

    /** La chanson démarre (position 0) à l'uptime [uptimeMs]. */
    fun start(uptimeMs: Long) {
        offset = -uptimeMs.toDouble()
        lastPosition = -1L
    }

    /** Recale d'un coup sur la position [positionMs] (après une pause). */
    fun hardSet(positionMs: Long, uptimeMs: Long) {
        offset = (positionMs - uptimeMs).toDouble()
        lastPosition = positionMs
    }

    /** Une position du lecteur audio, lue à l'uptime [uptimeMs]. */
    fun sync(positionMs: Long, uptimeMs: Long) {
        if (positionMs == lastPosition) return
        lastPosition = positionMs
        // Tant que le lecteur n'a pas vraiment démarré, il annonce 0 : on ne s'en sert pas.
        if (positionMs <= 0L) return
        val target = (positionMs - uptimeMs).toDouble()
        offset += (target - offset) * SMOOTHING
    }

    /** L'instant de la chanson (en ms) à l'uptime [uptimeMs]. */
    fun songTime(uptimeMs: Long): Long = (uptimeMs + offset).toLong()

    private companion object {
        const val SMOOTHING = 0.15
    }
}
