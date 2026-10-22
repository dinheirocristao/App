package br.com.autotdah.regulacaoproduzmais.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.autotdah.regulacaoproduzmais.R
import br.com.autotdah.regulacaoproduzmais.domain.TimeFormat
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.ui.MainActivity

object Notifications {
    const val CH_SESSION = "sessao_em_andamento"
    const val CH_ALERTS = "avisos"
    const val SESSION_ID = 10
    private const val ALERT_ID = 11

    fun createChannels(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CH_SESSION, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Sessão em andamento")
                .setDescription("Mostra a etapa atual e o tempo restante.")
                .setShowBadge(false)
                .build(),
        )
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CH_ALERTS, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Avisos")
                .setDescription("Mudança de etapa, conclusão e pausa após reinício.")
                .build(),
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun sessionNotification(context: Context, s: Session?, remainingMs: Long): Notification {
        val b = NotificationCompat.Builder(context, CH_SESSION)
            .setSmallIcon(R.drawable.ic_stat_autotdah)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        val step = s?.currentStep
        if (s != null && s.status == SessionStatus.RUNNING && step != null) {
            b.setContentTitle("AutoTdah · Etapa ${s.currentIndex + 1} de ${s.steps.size}")
                .setContentText("${step.label} · restam cerca de ${TimeFormat.duration(remainingMs)}")
                .setSubText("Em execução")
                .setWhen(System.currentTimeMillis() + remainingMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            b.setContentTitle("AutoTdah").setContentText(s?.status?.label ?: "Nenhuma programação ativa")
        }
        return b.build()
    }

    fun updateSession(context: Context, s: Session?, remainingMs: Long) {
        if (s?.status == SessionStatus.RUNNING) {
            notifySafe(context, SESSION_ID, sessionNotification(context, s, remainingMs))
        } else {
            NotificationManagerCompat.from(context).cancel(SESSION_ID)
        }
    }

    fun alert(context: Context, title: String, text: String) {
        val n = NotificationCompat.Builder(context, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_autotdah)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        notifySafe(context, ALERT_ID, n)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun notifySafe(context: Context, id: Int, n: Notification) {
        if (!canPost(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) { }
    }
}
