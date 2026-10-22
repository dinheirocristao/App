package br.com.autotdah.regulacaoproduzmais.data

import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.Template
import br.com.autotdah.regulacaoproduzmais.security.AttemptState
import java.io.File

sealed interface SessionLoad {
    data object Empty : SessionLoad
    data class Loaded(val session: Session, val fromBackup: Boolean) : SessionLoad
    data class Corrupt(val reason: String) : SessionLoad
}

interface SessionStore {
    fun load(): SessionLoad
    fun save(session: Session)
}

class FileSessionStore(dir: File, private val clock: () -> Long = System::currentTimeMillis) : SessionStore {
    private val file = AtomicTextFile(File(dir, "session.json"))

    override fun load(): SessionLoad {
        if (!file.anyExists()) return SessionLoad.Empty
        var lastError = "arquivo vazio"
        file.candidates().forEachIndexed { i, text ->
            try {
                return SessionLoad.Loaded(Codecs.session(Json.parse(text).asMap()), fromBackup = i > 0)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        file.quarantine(clock())
        return SessionLoad.Corrupt(lastError)
    }

    override fun save(session: Session) {
        file.write(Json.write(Codecs.session(session)))
    }
}

class TemplateStore(dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val file = AtomicTextFile(File(dir, "templates.json"))

    fun load(): List<Template> {
        for (text in file.candidates()) {
            try {
                return (Json.parse(text) as? List<*> ?: throw JsonException("Lista esperada")).map { Codecs.template(it.asMap()) }
            } catch (_: Exception) { }
        }
        if (file.anyExists()) file.quarantine(clock())
        return emptyList()
    }

    fun save(templates: List<Template>) = file.write(Json.write(templates.map { Codecs.template(it) }))

    fun upsert(t: Template) {
        val list = load().filterNot { it.id == t.id || it.name.equals(t.name, ignoreCase = true) }
        save(list + t)
    }

    fun delete(id: String) = save(load().filterNot { it.id == id })
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    /** Registro do PIN (hash + salt), possivelmente cifrado pelo Android Keystore. */
    val pinBlob: String? = null,
    val attempts: AttemptState = AttemptState(),
    val monitorAll: Boolean = true,
    val monitoredPackages: Set<String> = emptySet(),
)

class SettingsStore(dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val file = AtomicTextFile(File(dir, "settings.json"))

    /** Retorna as configurações e se foi preciso usar valores padrão por dados ilegíveis. */
    fun load(): Pair<AppSettings, Boolean> {
        if (!file.anyExists()) return AppSettings() to false
        for (text in file.candidates()) {
            try {
                val m = Json.parse(text).asMap()
                return AppSettings(
                    onboardingDone = m.bool("onboarding", false),
                    pinBlob = m.strOrNull("pin"),
                    attempts = Codecs.attempts(m["attempts"] as? Map<*, *>),
                    monitorAll = m.bool("monitorAll", true),
                    monitoredPackages = (m["monitored"] as? List<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet(),
                ) to false
            } catch (_: Exception) { }
        }
        file.quarantine(clock())
        return AppSettings() to true
    }

    fun save(s: AppSettings) = file.write(
        Json.write(
            mapOf(
                "onboarding" to s.onboardingDone,
                "pin" to s.pinBlob,
                "attempts" to Codecs.attempts(s.attempts),
                "monitorAll" to s.monitorAll,
                "monitored" to s.monitoredPackages.sorted(),
            ),
        ),
    )
}
