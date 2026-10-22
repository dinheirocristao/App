package br.com.autotdah.regulacaoproduzmais.data

import br.com.autotdah.regulacaoproduzmais.model.TimeSource
import br.com.autotdah.regulacaoproduzmais.security.AttemptLimiter

/** Configurações em memória + gravação imediata em disco. */
class SettingsRepository(private val store: SettingsStore, time: TimeSource) {

    @Volatile var current: AppSettings
        private set

    /** Verdadeiro se o arquivo estava ilegível e os padrões foram restaurados. */
    val wasReset: Boolean

    @Volatile var lastError: String? = null
        private set

    init {
        val (loaded, reset) = store.load()
        wasReset = reset
        // Na nova inicialização do aparelho, o bloqueio de tentativas volta a valer por inteiro.
        current = loaded.copy(attempts = AttemptLimiter.normalize(loaded.attempts, time.now()))
        if (current != loaded) persist()
    }

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        current = transform(current)
        persist()
    }

    private fun persist() {
        lastError = try {
            store.save(current)
            null
        } catch (e: Exception) {
            "Falha ao salvar configurações: ${e.message}"
        }
    }
}
