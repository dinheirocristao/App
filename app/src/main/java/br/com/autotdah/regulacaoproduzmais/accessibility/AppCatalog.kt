package br.com.autotdah.regulacaoproduzmais.accessibility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import br.com.autotdah.regulacaoproduzmais.data.AppSettings
import br.com.autotdah.regulacaoproduzmais.domain.AccessContext
import java.text.Collator
import java.util.Locale

/**
 * Lista de aplicativos que aparecem na gaveta (consulta declarada no manifesto, sem
 * QUERY_ALL_PACKAGES) e conjunto de pacotes essenciais que nunca são bloqueados.
 */
class AppCatalog(private val context: Context) {

    data class AppEntry(val packageName: String, val label: String)

    private val pm: PackageManager = context.packageManager

    @Volatile private var apps: List<AppEntry> = emptyList()
    @Volatile private var appSet: Set<String> = emptySet()
    @Volatile private var appsAt = 0L
    @Volatile private var exempt: Set<String> = emptySet()
    @Volatile private var homes: Set<String> = emptySet()
    @Volatile private var exemptAt = 0L

    @Suppress("DEPRECATION")
    @Synchronized
    fun launchableApps(force: Boolean = false): List<AppEntry> {
        val now = SystemClock.elapsedRealtime()
        if (!force && appsAt != 0L && now - appsAt < TTL) return apps
        val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        apps = list
        appSet = list.mapTo(HashSet()) { it.packageName }
        appsAt = now
        return list
    }

    fun launchablePackages(): Set<String> {
        launchableApps()
        return appSet
    }

    fun isInstalled(pkg: String): Boolean = pkg in launchablePackages() || pm.getLaunchIntentForPackage(pkg) != null

    fun label(pkg: String): String? = launchableApps().firstOrNull { it.packageName == pkg }?.label

    fun icon(pkg: String): Drawable? = try {
        pm.getApplicationIcon(pkg)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun homePackages(): Set<String> {
        refreshExempt()
        return homes
    }

    fun exemptPackages(): Set<String> {
        refreshExempt()
        return exempt
    }

    @Suppress("DEPRECATION")
    @Synchronized
    private fun refreshExempt() {
        val now = SystemClock.elapsedRealtime()
        if (exemptAt != 0L && now - exemptAt < TTL) return
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val h = pm.queryIntentActivities(homeIntent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
        val set = HashSet<String>(STATIC_EXEMPT)
        set += h
        try {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { set += it }
        } catch (_: Exception) { }
        try {
            context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { set += it.packageName }
        } catch (_: Exception) { }
        homes = h
        exempt = set
        exemptAt = now
    }

    fun accessContext(settings: AppSettings) = AccessContext(
        ownPackage = context.packageName,
        exemptPackages = exemptPackages(),
        launchablePackages = launchablePackages(),
        monitorAll = settings.monitorAll,
        monitoredPackages = settings.monitoredPackages,
    )

    companion object {
        private const val TTL = 30_000L

        /** Essenciais: sistema, Configurações (para o usuário sempre poder desativar), telefone e emergência. */
        val STATIC_EXEMPT = setOf(
            "android",
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.incallui",
            "com.samsung.android.incallui",
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.settings.intelligence",
            "com.samsung.android.app.telephonyui",
        )
    }
}
