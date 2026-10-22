package br.com.autotdah.regulacaoproduzmais.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecider
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecision
import br.com.autotdah.regulacaoproduzmais.service.SessionController
import br.com.autotdah.regulacaoproduzmais.ui.BlockActivity

/**
 * Observa apenas QUAL aplicativo veio para a frente (evento de mudança de janela).
 * Não lê textos, não captura tela, não recupera conteúdo de janelas
 * (canRetrieveWindowContent = false) e não envia nada para a internet.
 */
class BlockerAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastForeground: String? = null
    private var lastBlockedPkg: String? = null
    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        SessionController.init(applicationContext)
        SessionController.reportEnforcement(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        onForeground(pkg)
    }

    private fun onForeground(pkg: String) {
        val catalog = SessionController.catalog
        // Janelas transitórias (teclado, barra do sistema, sobreposições) não mudam o app em uso.
        val relevant = pkg == packageName || pkg in catalog.launchablePackages() || pkg in catalog.homePackages()
        if (!relevant) return
        lastForeground = pkg
        if (pkg != packageName) evaluate(pkg)
    }

    private fun evaluate(pkg: String) {
        SessionController.tick()
        val rule = AccessDecider.activeRule(SessionController.session.value) ?: return
        val ctx = SessionController.catalog.accessContext(SessionController.settings.current)
        if (AccessDecider.decide(rule, pkg, ctx) == AccessDecision.BLOCK) showBlock(pkg)
    }

    private fun showBlock(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        // Evita abrir a tela de bloqueio em sequência para o mesmo evento repetido.
        if (pkg == lastBlockedPkg && (now - lastBlockAt < 700 || BlockActivity.visible)) return
        lastBlockedPkg = pkg
        lastBlockAt = now
        val intent = Intent(this, BlockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(BlockActivity.EXTRA_PKG, pkg)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    /** Reaplica a regra ao app que já está aberto (ex.: a etapa mudou enquanto ele estava na tela). */
    fun reevaluateNow() {
        main.post {
            val pkg = lastForeground
            if (pkg != null && pkg != packageName) evaluate(pkg)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        if (SessionController.isInitialized) SessionController.reportEnforcement(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instance: BlockerAccessibilityService? = null
            private set

        fun reevaluate() {
            instance?.reevaluateNow()
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, BlockerAccessibilityService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
