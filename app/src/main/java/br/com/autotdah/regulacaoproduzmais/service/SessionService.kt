package br.com.autotdah.regulacaoproduzmais.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import br.com.autotdah.regulacaoproduzmais.accessibility.BlockerAccessibilityService
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que mantém a sessão viva com a interface fechada.
 * O tempo NÃO depende deste laço: ele apenas avança etapas, salva pontos de controle
 * (a cada 15 s) e atualiza a notificação. Se o Android pausar ou matar o serviço,
 * o tempo correto é recalculado pelo relógio monotônico na próxima execução.
 */
class SessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loop: Job? = null
    private var receiverRegistered = false

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SHUTDOWN) SessionController.checkpoint() else SessionController.tick()
        }
    }

    override fun onCreate() {
        super.onCreate()
        SessionController.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SHUTDOWN)
            }
            ContextCompat.registerReceiver(this, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        var n = 0L
        while (true) {
            val s = SessionController.tick()
            if (s?.status != SessionStatus.RUNNING) break
            n++
            if (n % 15 == 0L) SessionController.checkpoint()
            if (n % 10 == 0L) SessionController.reportEnforcement(BlockerAccessibilityService.isEnabled(this))
            if (n % 30 == 0L) Notifications.updateSession(this, s, SessionController.remainingMs())
            delay(1_000)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun enterForeground(): Boolean = try {
        val n = Notifications.sessionNotification(this, SessionController.session.value, SessionController.remainingMs())
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.SESSION_ID, n, type)
        true
    } catch (e: Exception) {
        SessionController.onServiceStartFailed(e)
        false
    }

    override fun onDestroy() {
        scope.cancel()
        if (receiverRegistered) {
            try { unregisterReceiver(systemReceiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        SessionController.checkpoint()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun ensureRunning(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, SessionService::class.java))
            } catch (e: Exception) {
                // Ex.: Android 12+ proíbe iniciar a partir do segundo plano. O tempo segue correto;
                // o serviço volta quando o app for aberto.
                SessionController.onServiceStartFailed(e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }
}
