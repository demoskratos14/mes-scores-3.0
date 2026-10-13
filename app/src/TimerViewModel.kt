package com.aventure.messcores

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TimerMode { STOPWATCH, COUNTDOWN }

/**
 * État du chronomètre/minuteur, partagé entre tous les écrans via le bouton flottant.
 * Vit au niveau de l'Activity (comme [ScoreViewModel]) donc continue de tourner et
 * garde son état quand on navigue d'un écran à l'autre.
 */
class TimerViewModel(application: Application) : AndroidViewModel(application) {

    var mode by mutableStateOf(TimerMode.STOPWATCH)
        private set

    var isRunning by mutableStateOf(false)
        private set

    /** Temps écoulé, utilisé en mode chronomètre. */
    var elapsedMillis by mutableLongStateOf(0L)
        private set

    /** Durée totale du minuteur, réglable avant de le démarrer. */
    var countdownDurationMillis by mutableLongStateOf(5 * 60 * 1000L)
        private set

    /** Temps restant, utilisé en mode minuteur. */
    var countdownRemainingMillis by mutableLongStateOf(countdownDurationMillis)
        private set

    /** Vrai dès que le minuteur vient d'atteindre zéro, jusqu'à la prochaine réinitialisation. */
    var justFinished by mutableStateOf(false)
        private set

    private var tickJob: Job? = null

    /** Heure de fin prévue du minuteur en cours (ms, horloge murale) ; sert d'identifiant à l'alerte. */
    private var alarmToken = 0L

    // Le temps est calculé à partir de l'horloge du téléphone et non en ajoutant 1 s à chaque
    // tour de boucle, pour ne pas prendre de retard avec le temps.
    private var runStartRealtime = 0L
    private var baseElapsed = 0L
    private var baseRemaining = 0L

    fun selectMode(newMode: TimerMode) {
        if (isRunning) return
        mode = newMode
    }

    fun setCountdownMinutes(minutes: Int) {
        if (isRunning) return
        val duration = minutes.coerceIn(1, 180) * 60 * 1000L
        countdownDurationMillis = duration
        countdownRemainingMillis = duration
    }

    fun start() {
        if (isRunning) return
        if (mode == TimerMode.COUNTDOWN && countdownRemainingMillis <= 0) {
            countdownRemainingMillis = countdownDurationMillis
        }
        isRunning = true
        justFinished = false
        runStartRealtime = SystemClock.elapsedRealtime()
        baseElapsed = elapsedMillis
        baseRemaining = countdownRemainingMillis
        if (mode == TimerMode.COUNTDOWN) scheduleAlarm(baseRemaining)
        tickJob = viewModelScope.launch {
            while (isRunning) {
                delay(TICK_MILLIS)
                syncFromClock()
                if (mode == TimerMode.COUNTDOWN && countdownRemainingMillis == 0L) {
                    isRunning = false
                    justFinished = true
                    // Vibration + son si l'appli est visible, notification sinon. Si l'alarme système
                    // a déjà sonné, TimerAlert ne signale pas la fin une deuxième fois.
                    TimerAlert.onTimerFinished(getApplication<Application>(), alarmToken)
                    cancelAlarm()
                }
            }
        }
    }

    // ---------- Alarme système ----------
    // La boucle ci-dessus s'arrête quand Android endort l'appli (écran éteint, arrière-plan).
    // L'alarme système, elle, se déclenche à l'heure exacte et réveille TimerReceiver.
    // setAlarmClock ne demande aucune autorisation spéciale et reste exacte même en mode Doze
    // (contrepartie : Android affiche une icône d'alarme dans la barre d'état pendant le minuteur).

    private fun alarmIntent(): PendingIntent {
        val app = getApplication<Application>()
        return PendingIntent.getBroadcast(
            app, ALARM_REQUEST_CODE,
            Intent(app, TimerReceiver::class.java).putExtra(TimerReceiver.EXTRA_TOKEN, alarmToken),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // setAlarmClock est exempté de SCHEDULE_EXACT_ALARM (alarme visible de l'utilisateur) : l'alerte de lint est un faux positif.
    @SuppressLint("MissingPermission")
    private fun scheduleAlarm(remainingMillis: Long) {
        // Le jeton est fixé même si l'alarme échoue : la boucle interne signalera quand même la fin.
        val triggerAt = System.currentTimeMillis() + remainingMillis
        alarmToken = triggerAt
        try {
            val app = getApplication<Application>()
            val manager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val showApp = PendingIntent.getActivity(
                app, 0, Intent(app, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showApp), alarmIntent())
        } catch (e: Exception) {
            // Alarme impossible : le minuteur interne continue de fonctionner, appli au premier plan.
        }
    }

    private fun cancelAlarm() {
        try {
            val manager = getApplication<Application>().getSystemService(Context.ALARM_SERVICE) as AlarmManager
            manager.cancel(alarmIntent())
        } catch (e: Exception) {
            // Rien à annuler.
        }
    }

    private fun syncFromClock() {
        val spent = SystemClock.elapsedRealtime() - runStartRealtime
        when (mode) {
            TimerMode.STOPWATCH -> elapsedMillis = baseElapsed + spent
            TimerMode.COUNTDOWN -> countdownRemainingMillis = (baseRemaining - spent).coerceAtLeast(0)
        }
    }

    fun pause() {
        if (isRunning) syncFromClock()
        isRunning = false
        tickJob?.cancel()
        cancelAlarm()
    }

    fun reset() {
        pause()
        elapsedMillis = 0L
        countdownRemainingMillis = countdownDurationMillis
        justFinished = false
    }

    private companion object {
        const val TICK_MILLIS = 200L
        const val ALARM_REQUEST_CODE = 7001
    }

    override fun onCleared() {
        // ViewModel.onCleared() est vide : pas d'appel à super.
        tickJob?.cancel()
        cancelAlarm()
    }
}
