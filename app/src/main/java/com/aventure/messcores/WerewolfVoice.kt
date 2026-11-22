package com.aventure.messcores

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Durée d'attente par caractère quand la voix n'est pas disponible : le texte reste affiché le temps de le lire. */
private const val SILENT_MS_PER_CHAR = 55L

/** Attente maximale de l'initialisation de la voix avant de passer en lecture silencieuse. */
private const val VOICE_INIT_WAIT_MS = 2_000L

/**
 * La voix du narrateur du Loup-garou : lit un texte à voix haute avec la synthèse vocale d'Android, dans la langue de l'appli.
 * Si le téléphone n'a pas de moteur vocal, [say] attend simplement le temps de lire le texte : la partie continue, le texte
 * reste affiché sur l'écran du narrateur.
 */
internal class WerewolfVoice(context: Context, private val locale: Locale) {

    @Volatile
    private var ready = false

    @Volatile
    private var failed = false

    private val waiting = ConcurrentHashMap<String, CancellableContinuation<Unit>>()

    private var tts: TextToSpeech? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts
                    if (engine != null) {
                        val result = engine.setLanguage(locale)
                        // Langue absente : on garde la voix par défaut du téléphone.
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w("WerewolfVoice", "Langue vocale indisponible : $locale")
                        }
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit

                            override fun onDone(utteranceId: String?) {
                                utteranceId?.let { waiting.remove(it)?.resume(Unit) }
                            }

                            @Deprecated("Deprecated in Java")
                            override fun onError(utteranceId: String?) {
                                utteranceId?.let { waiting.remove(it)?.resume(Unit) }
                            }
                        })
                        ready = true
                    }
                } else {
                    failed = true
                }
            }
        } catch (e: Exception) {
            Log.w("WerewolfVoice", "Synthèse vocale impossible", e)
            failed = true
        }
    }

    /** Lit [text] et rend la main quand c'est fini. */
    suspend fun say(text: String) {
        if (text.isBlank()) return
        var waited = 0L
        while (!ready && !failed && waited < VOICE_INIT_WAIT_MS) {
            delay(100)
            waited += 100
        }
        val engine = tts
        if (!ready || engine == null) {
            delay(text.length * SILENT_MS_PER_CHAR)
            return
        }
        val id = UUID.randomUUID().toString()
        try {
            suspendCancellableCoroutine { continuation ->
                waiting[id] = continuation
                continuation.invokeOnCancellation {
                    waiting.remove(id)
                    engine.stop()
                }
                val started = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                if (started != TextToSpeech.SUCCESS) {
                    waiting.remove(id)
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        } catch (e: Exception) {
            Log.w("WerewolfVoice", "Lecture impossible", e)
            delay(text.length * SILENT_MS_PER_CHAR)
        }
    }

    /** Coupe la voix en cours. */
    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w("WerewolfVoice", "Arrêt impossible", e)
        }
        waiting.values.toList().forEach { if (it.isActive) it.resume(Unit) }
        waiting.clear()
    }

    /** Libère le moteur vocal (à la fermeture de l'écran). */
    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w("WerewolfVoice", "Fermeture impossible", e)
        }
        tts = null
    }
}

/** Vibrations du Loup-garou : de courtes pulsations, pour prévenir un joueur sans que les autres l'entendent. */
internal object WerewolfHaptics {

    /** Une légère vibration de [ms] millisecondes. Ne plante jamais si le téléphone n'a pas de vibreur. */
    fun buzz(context: Context, ms: Long = 250L) {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator?.hasVibrator() == true) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (e: Exception) {
            Log.w("WerewolfHaptics", "Vibration impossible", e)
        }
    }
}
