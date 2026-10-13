package com.aventure.messcores

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Signal de fin de minuteur.
 *
 * - Appli visible : vibration + bip d'alarme.
 * - Appli en arrière-plan ou écran éteint : notification sonore (ou, si les notifications sont
 *   refusées, vibration + bip). Elle est déclenchée par l'alarme système programmée dans
 *   [TimerViewModel], via [TimerReceiver], donc même si l'appli est endormie.
 *
 * Ne plante jamais si le téléphone ne sait pas faire l'un des signaux.
 */
object TimerAlert {

    private const val CHANNEL_ID = "timer_finished"
    private const val NOTIFICATION_ID = 4201

    /** Mis à jour par MainActivity (onStart / onStop). */
    @Volatile
    var appInForeground = false

    /** Dernière échéance signalée : évite un double signal quand le minuteur interne et l'alarme système se déclenchent ensemble. */
    @Volatile
    private var lastAlertToken = 0L

    /**
     * Signale la fin du minuteur dont l'échéance est [token] (heure de fin en millisecondes).
     * Appelée à la fois par [TimerViewModel] et par [TimerReceiver] ; le premier arrivé gagne.
     */
    @Synchronized
    fun onTimerFinished(context: Context, token: Long) {
        if (token != 0L && token == lastAlertToken) return
        lastAlertToken = token
        val appContext = context.applicationContext
        if (appInForeground || !postNotification(appContext)) {
            play(appContext)
        }
    }

    fun cancelNotification(context: Context) {
        try {
            context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            // Rien à annuler.
        }
    }

    /** Vibration + bip d'alarme. */
    fun play(context: Context) {
        val appContext = context.applicationContext
        try {
            vibrate(appContext)
        } catch (e: Exception) {
            // Pas de vibreur ou permission refusée : on ignore.
        }
        try {
            beep()
        } catch (e: Exception) {
            // Pas de son disponible : on ignore.
        }
    }

    /** Affiche la notification de fin. Renvoie false si elle n'a pas pu l'être (permission refusée, erreur). */
    private fun postNotification(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val vibration = longArrayOf(0, 500, 250, 500, 250, 500)

            // minSdk = 26 : les canaux de notification existent toujours.
            val channel = NotificationChannel(
                CHANNEL_ID, context.getString(R.string.timer_countdown), NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.timer_channel_description)
                setSound(
                    alarmSound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = vibration
            }
            manager.createNotificationChannel(channel)

            val openApp = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(R.string.timer_notification_title))
                .setContentText(context.getString(R.string.timer_notification_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openApp)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun vibrate(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 250, 500, 250, 500), -1))
    }

    private fun beep() {
        val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
        tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)
        Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 2000)
    }
}
