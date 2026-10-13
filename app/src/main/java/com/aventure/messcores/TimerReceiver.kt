package com.aventure.messcores

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reçoit l'alarme système programmée par [TimerViewModel] à l'échéance du minuteur.
 * C'est elle qui sonne quand l'appli est en arrière-plan ou l'écran éteint, là où la
 * boucle interne du minuteur peut être suspendue par Android.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        TimerAlert.onTimerFinished(context, intent.getLongExtra(EXTRA_TOKEN, 0L))
    }

    companion object {
        /** Heure de fin du minuteur (ms) : identifie le minuteur pour ne signaler sa fin qu'une fois. */
        const val EXTRA_TOKEN = "timer_token"
    }
}
