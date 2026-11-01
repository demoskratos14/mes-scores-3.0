package com.aventure.messcores

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Les étapes d'une partie de rythme. */
internal enum class SessionStage {
    /** Les premières notes descendent avant que la musique ne démarre. */
    INTRO,
    PLAYING,
    PAUSED,
    FINISHED
}

/**
 * Une partie en cours : la musique (jouée par [player], déjà préparé), la partition et l'état du joueur ([run]).
 * L'écran appelle [tick] à chaque image et transmet les touches à [press] et [release]. Tous les instants « uptime »
 * sont ceux de `SystemClock.uptimeMillis()`, comme l'heure des touches reçues par Compose.
 *
 * [audioOffsetMs] est le décalage audio réglé par le joueur : positif, les notes arrivent plus tard sur la ligne
 * (casque Bluetooth, dont le son part en retard) ; négatif, plus tôt.
 *
 * Le lecteur met un instant à sortir du son après `start()` (démarrage de la sortie audio). Pour que la première note
 * tombe juste, la musique est lancée [startLatencyMs] avant l'instant 0, pendant l'introduction ; l'horloge ne se cale
 * sur le lecteur que lorsqu'il annonce enfin une position, d'un seul coup (pas en tirant l'horloge en arrière, ce qui
 * ralentissait le défilement). Le délai réellement observé est transmis à [onLatencyMeasured] pour la partie suivante.
 */
internal class RhythmSession(
    private val player: MediaPlayer,
    val chart: RhythmChart,
    private val audioOffsetMs: Int,
    startUptimeMs: Long,
    private val startLatencyMs: Int = RhythmPreferences.DEFAULT_LATENCY_MS,
    private val onLatencyMeasured: (Int) -> Unit = {}
) {
    val run = RhythmRun(chart)

    var stage by mutableStateOf(SessionStage.INTRO)
        private set

    /** L'instant de la chanson affiché à l'écran (décalage audio compris) : change à chaque image. */
    var songMs by mutableIntStateOf(-leadInMs())
        private set

    // Copies observables de l'état de [run], pour que l'affichage se mette à jour.
    var score by mutableIntStateOf(0)
        private set
    var combo by mutableIntStateOf(0)
        private set
    var multiplier by mutableIntStateOf(1)
        private set
    var judgement by mutableStateOf<Judgement?>(null)
        private set
    var judgementMs by mutableIntStateOf(0)
        private set

    private val clock = SongClock()
    private var startUptime = startUptimeMs
    private var completed = false
    private var released = false
    private var pausedFrom = SessionStage.INTRO
    private var pausedAtUptime = 0L

    // La musique est lancée (le lecteur ne sort pas encore de son) : on attend qu'il annonce une position.
    private var started = false
    private var startedAtUptime = 0L

    init {
        player.setOnCompletionListener { completed = true }
    }

    private fun leadInMs(): Int = chart.difficulty.approachMs + LEAD_IN_EXTRA_MS

    /** L'instant de la chanson (en ms, décalage audio compris) à l'uptime [uptimeMs]. */
    fun timeAt(uptimeMs: Long): Int {
        val raw = if (stage == SessionStage.INTRO) uptimeMs - startUptime - leadInMs() else clock.songTime(uptimeMs)
        return (raw - audioOffsetMs).toInt()
    }

    /** Avance la partie : à appeler à chaque image. */
    fun tick(uptimeMs: Long) {
        when (stage) {
            SessionStage.INTRO -> {
                val raw = uptimeMs - startUptime - leadInMs()
                if (!started && !released && raw >= -startLatencyMs) {
                    player.start()
                    started = true
                    startedAtUptime = uptimeMs
                }
                if (started) {
                    val position = if (released) 0L else player.currentPosition.toLong()
                    if (position > 0L) {
                        onLatencyMeasured(((uptimeMs - startedAtUptime) - position).toInt().coerceIn(0, RhythmPreferences.MAX_LATENCY_MS))
                        clock.hardSet(position, uptimeMs)
                        stage = SessionStage.PLAYING
                    } else if (raw >= START_TIMEOUT_MS) {
                        // Le lecteur n'annonce rien : on continue avec l'horloge du téléphone.
                        clock.hardSet(raw, uptimeMs)
                        stage = SessionStage.PLAYING
                    }
                }
            }
            SessionStage.PLAYING -> if (!released) clock.sync(player.currentPosition.toLong(), uptimeMs)
            else -> return
        }
        val now = timeAt(uptimeMs)
        songMs = now
        run.update(now)
        publish()
        if (stage == SessionStage.PLAYING) {
            if (run.unresolved == 0 && (completed || now > chart.durationMs)) {
                finish(now)
            } else if (completed && now > chart.durationMs + FINISH_GRACE_MS) {
                run.finish(now)
                finish(now)
            }
        }
    }

    private fun finish(now: Int) {
        run.finish(now)
        publish()
        stage = SessionStage.FINISHED
    }

    private fun publish() {
        score = run.score
        combo = run.combo
        multiplier = run.multiplier()
        judgement = run.lastJudgement
        judgementMs = run.lastJudgementMs
    }

    /** Le joueur touche la piste [lane] (à l'uptime [uptimeMs] de la touche). */
    fun press(lane: Int, uptimeMs: Long) {
        if (stage != SessionStage.PLAYING && stage != SessionStage.INTRO) return
        run.press(lane, timeAt(uptimeMs))
        publish()
    }

    /** Le joueur lève le doigt de la piste [lane]. */
    fun release(lane: Int, uptimeMs: Long) {
        if (stage != SessionStage.PLAYING && stage != SessionStage.INTRO) return
        run.release(lane, timeAt(uptimeMs))
        publish()
    }

    /** Met la partie en pause (musique comprise). Les notes maintenues sont lâchées. */
    fun pause(uptimeMs: Long) {
        if (stage != SessionStage.PLAYING && stage != SessionStage.INTRO) return
        for (lane in 0 until chart.lanes) run.release(lane, timeAt(uptimeMs))
        publish()
        pausedFrom = stage
        pausedAtUptime = uptimeMs
        if (stage == SessionStage.PLAYING && !released) player.pause()
        if (stage == SessionStage.INTRO && started && !released) {
            // Musique lancée pendant l'introduction : on la remet au début, elle repartira à la reprise.
            player.pause()
            player.seekTo(0)
            started = false
        }
        stage = SessionStage.PAUSED
    }

    /** Reprend là où la pause a eu lieu. */
    fun resume(uptimeMs: Long) {
        if (stage != SessionStage.PAUSED) return
        if (pausedFrom == SessionStage.INTRO) {
            startUptime += uptimeMs - pausedAtUptime
            stage = SessionStage.INTRO
        } else {
            if (!released) {
                player.start()
                clock.hardSet(player.currentPosition.toLong(), uptimeMs)
            }
            stage = SessionStage.PLAYING
        }
    }

    /** Libère le lecteur audio : à appeler quand la partie est quittée. */
    fun close() {
        if (released) return
        released = true
        player.setOnCompletionListener(null)
        player.release()
    }

    private companion object {
        /** Temps ajouté avant la musique, pour voir descendre les premières notes. */
        const val LEAD_IN_EXTRA_MS = 600

        /** Après la fin de la musique, temps laissé pour terminer les dernières notes avant de finir d'office. */
        const val FINISH_GRACE_MS = 3_000

        /** Si le lecteur n'a toujours pas annoncé de position à ce moment de la chanson (en ms), on avance sans lui. */
        const val START_TIMEOUT_MS = 800
    }
}

/** Les réglages du jeu de rythme, retenus d'une partie à l'autre : difficulté et décalage audio. */
internal object RhythmPreferences {
    private const val PREFS = "mes_scores_rhythm"
    private const val KEY_DIFFICULTY = "difficulty"
    private const val KEY_OFFSET = "audio_offset_ms"

    const val MAX_OFFSET_MS = 400
    const val OFFSET_STEP_MS = 10

    /** Délai entre `start()` et le premier son du lecteur : valeur de départ, puis celle mesurée sur le téléphone. */
    const val DEFAULT_LATENCY_MS = 150
    const val MAX_LATENCY_MS = 600
    private const val KEY_LATENCY = "start_latency_ms"

    fun loadLatency(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_LATENCY, DEFAULT_LATENCY_MS)
            .coerceIn(0, MAX_LATENCY_MS)

    /** Retient le délai mesuré, lissé avec le précédent pour ne pas suivre un à-coup isolé. */
    fun saveLatency(context: Context, measuredMs: Int) {
        val smoothed = (loadLatency(context) + measuredMs.coerceIn(0, MAX_LATENCY_MS)) / 2
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putInt(KEY_LATENCY, smoothed) }
    }

    fun loadDifficulty(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DIFFICULTY, RhythmDifficulty.MEDIUM.ordinal)
            .coerceIn(0, RhythmDifficulty.entries.size - 1)

    fun loadOffset(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_OFFSET, 0)
            .coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)

    fun save(context: Context, difficulty: Int, offsetMs: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(KEY_DIFFICULTY, difficulty)
            putInt(KEY_OFFSET, offsetMs)
        }
    }
}
