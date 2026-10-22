package br.com.autotdah.regulacaoproduzmais.service

import android.content.Context
import br.com.autotdah.regulacaoproduzmais.accessibility.AppCatalog
import br.com.autotdah.regulacaoproduzmais.accessibility.BlockerAccessibilityService
import br.com.autotdah.regulacaoproduzmais.data.FileSessionStore
import br.com.autotdah.regulacaoproduzmais.data.SettingsRepository
import br.com.autotdah.regulacaoproduzmais.data.SettingsStore
import br.com.autotdah.regulacaoproduzmais.data.TemplateStore
import br.com.autotdah.regulacaoproduzmais.domain.OpResult
import br.com.autotdah.regulacaoproduzmais.domain.SessionCoordinator
import br.com.autotdah.regulacaoproduzmais.history.HistoryDb
import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.security.AuthProof
import br.com.autotdah.regulacaoproduzmais.security.KeystoreWrapper
import br.com.autotdah.regulacaoproduzmais.security.PinManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Ponto único de acesso à sessão para interface, serviço, acessibilidade e receptores.
 * Toda mudança passa pelo [SessionCoordinator] (que persiste antes de expor o novo estado)
 * e, em seguida, aplica os efeitos do Android: notificação, alarme, serviço e bloqueio.
 */
object SessionController {
    private lateinit var app: Context
    lateinit var coordinator: SessionCoordinator
        private set
    lateinit var history: HistoryDb
        private set
    lateinit var templates: TemplateStore
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var pins: PinManager
        private set
    lateinit var catalog: AppCatalog
        private set

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session

    /** Incrementado a cada operação, para a interface reler avisos (erros de gravação etc.). */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    @Volatile private var initialized = false
    @Volatile private var serviceProblemNoted = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        app = context.applicationContext
        AndroidTime.init(app)
        val dir = File(app.noBackupFilesDir, "estado")
        history = HistoryDb(app)
        templates = TemplateStore(dir)
        settings = SettingsRepository(SettingsStore(dir), AndroidTime)
        pins = PinManager(settings, KeystoreWrapper(), AndroidTime)
        catalog = AppCatalog(app)
        coordinator = SessionCoordinator(FileSessionStore(dir), history, AndroidTime)
        initialized = true
        coordinator.ensureLoaded()
        if (coordinator.pausedByRebootOnLoad) {
            Notifications.alert(app, "Programação pausada após reinício", "O progresso foi preservado. Abra o AutoTdah e toque em “Continuar programação”.")
        }
        afterChange(null, force = true)
    }

    val isInitialized: Boolean get() = initialized

    private inline fun <T> op(block: () -> T): T {
        val before = coordinator.session
        val result = block()
        afterChange(before)
        return result
    }

    fun tick(): Session? = op { coordinator.tick() }
    fun checkpoint(): Session? = op { coordinator.checkpoint() }
    fun onBootSignal(): Session? = op { coordinator.onBootSignal() }
    fun start(steps: List<Step>): OpResult = op { coordinator.start(steps) }
    fun resume(): OpResult = op { coordinator.resume() }
    fun pause(proof: AuthProof): OpResult = op { coordinator.pause(proof) }
    fun end(proof: AuthProof): OpResult = op { coordinator.end(proof) }
    fun reportEnforcement(ok: Boolean): Session? = op { coordinator.reportEnforcement(ok) }

    fun onServiceStartFailed(e: Exception) {
        if (serviceProblemNoted) return
        serviceProblemNoted = true
        op { coordinator.note(EventType.SERVICE_UNAVAILABLE, e.javaClass.simpleName) }
    }

    fun remainingMs(): Long = coordinator.remainingMs()

    private fun afterChange(before: Session?, force: Boolean = false) {
        val s = coordinator.session
        _session.value = s
        _revision.value = _revision.value + 1
        val changed = force || before?.id != s?.id || before?.status != s?.status || before?.currentIndex != s?.currentIndex
        if (!changed) return

        BlockerAccessibilityService.reevaluate()
        Notifications.updateSession(app, s, coordinator.remainingMs())
        AlarmScheduler.schedule(app, s, coordinator.remainingMs())
        if (s?.status == SessionStatus.RUNNING) SessionService.ensureRunning(app) else SessionService.stop(app)

        if (!force && before?.status != s?.status) {
            when (s?.status) {
                SessionStatus.PAUSED_REBOOT -> Notifications.alert(app, "Programação pausada após reinício",
                    "O progresso foi preservado. Abra o AutoTdah e toque em “Continuar programação”.")
                SessionStatus.COMPLETED -> Notifications.alert(app, "Programação concluída", "Todas as etapas foram cumpridas.")
                else -> Unit
            }
        } else if (!force && s != null && s.status == SessionStatus.RUNNING && before?.currentIndex != s.currentIndex) {
            s.currentStep?.let { Notifications.alert(app, "Etapa ${s.currentIndex + 1} de ${s.steps.size}", "Agora: ${it.label} — ${it.minutes} min") }
        }
    }
}
