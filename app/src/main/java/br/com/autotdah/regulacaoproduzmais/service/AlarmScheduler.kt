package br.com.autotdah.regulacaoproduzmais.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus

/**
 * Agenda um despertar aproximado para o fim da etapa (sem permissão de alarme exato).
 * Serve só para atualizar notificação e bloqueio mais cedo com o aparelho ocioso;
 * a contagem em si não depende dele.
 */
object AlarmScheduler {
    fun schedule(context: Context, s: Session?, remainingMs: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (s?.status != SessionStatus.RUNNING) return
        try {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + remainingMs + 1_000, pi)
        } catch (_: Exception) { }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SessionController.init(context)
        SessionController.tick()
    }
}
