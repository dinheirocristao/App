package br.com.autotdah.regulacaoproduzmais.domain

import br.com.autotdah.regulacaoproduzmais.data.SessionLoad
import br.com.autotdah.regulacaoproduzmais.data.SessionStore
import br.com.autotdah.regulacaoproduzmais.history.HistoryRepository
import br.com.autotdah.regulacaoproduzmais.history.SYSTEM_SESSION_ID
import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.HistoryEvent
import br.com.autotdah.regulacaoproduzmais.model.Now
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.model.TimeSource
import br.com.autotdah.regulacaoproduzmais.security.AuthProof
import java.util.UUID

sealed interface OpResult {
    data class Ok(val session: Session?) : OpResult
    data class Failure(val message: String) : OpResult
}

/**
 * Coordena máquina de estados + persistência + histórico, sem depender do Android.
 *
 * Ordem de gravação em cada transição:
 * 1. o novo estado (com os eventos pendentes na "caixa de saída") é salvo no arquivo;
 * 2. os eventos são gravados no histórico (idempotente por id);
 * 3. a caixa de saída é esvaziada.
 * Se o processo morrer entre 1 e 3, os eventos são regravados na próxima abertura sem duplicar.
 */
class SessionCoordinator(
    private val store: SessionStore,
    private val history: HistoryRepository,
    private val time: TimeSource,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    val engine = SessionEngine(newId)
    private val lock = Any()
    private var loaded = false

    @Volatile var session: Session? = null
        private set

    @Volatile var persistError: String? = null
        private set

    @Volatile var loadProblem: String? = null
        private set

    /** Verdadeiro se, ao abrir neste processo, a sessão foi pausada por reinício do aparelho. */
    @Volatile var pausedByRebootOnLoad: Boolean = false
        private set

    fun now(): Now = time.now()

    fun ensureLoaded(): Session? = synchronized(lock) {
        if (!loaded) load()
        session
    }

    private fun load() {
        loaded = true
        val now = time.now()
        val result = try { store.load() } catch (e: Exception) { SessionLoad.Corrupt(e.message ?: "erro de leitura") }
        when (result) {
            SessionLoad.Empty -> session = null
            is SessionLoad.Loaded -> {
                var s = result.session
                if (result.fromBackup) {
                    s = engine.note(s, EventType.DATA_RECOVERED, now, "O arquivo principal estava danificado; foi usada a última cópia íntegra.")
                }
                if (s.status == SessionStatus.RUNNING) {
                    s = if (engine.anchorValid(s, now)) {
                        engine.tick(engine.note(s, EventType.PROCESS_RECOVERED, now, null), now)
                    } else {
                        pausedByRebootOnLoad = true
                        engine.tick(s, now) // inicialização diferente → pausa por reinício
                    }
                }
                commit(s)
            }
            is SessionLoad.Corrupt -> {
                session = null
                loadProblem = "Não foi possível ler a programação salva (${result.reason}). " +
                    "Os arquivos foram preservados e a ocorrência foi registrada no histórico como interrupção técnica."
                try {
                    history.insertEvents(listOf(HistoryEvent(newId(), SYSTEM_SESSION_ID, EventType.TECHNICAL_INTERRUPTION,
                        now.wallMs, null, "Dados da sessão ilegíveis: ${result.reason}")))
                } catch (_: Exception) { }
            }
        }
    }

    private fun commit(next: Session): Boolean {
        session = next
        return try {
            store.save(next)
            persistError = null
            flushHistory()
            true
        } catch (e: Exception) {
            persistError = "Não foi possível salvar o progresso (${e.message ?: "erro"}). Nova tentativa em instantes."
            false
        }
    }

    private fun flushHistory() {
        val s = session ?: return
        try {
            if (s.outbox.isNotEmpty()) history.insertEvents(s.outbox)
            if (s.status != SessionStatus.CONFIGURED) history.upsertSession(engine.summary(s, time.now()))
        } catch (e: Exception) {
            persistError = "Falha ao gravar o histórico (${e.message ?: "erro"})."
            return
        }
        if (s.outbox.isNotEmpty()) {
            val cleared = s.copy(outbox = emptyList())
            session = cleared
            try { store.save(cleared) } catch (_: Exception) { /* regravação idempotente depois */ }
        }
    }

    fun start(steps: List<Step>): OpResult = synchronized(lock) {
        if (!loaded) load()
        if (session?.status?.isActive == true) return OpResult.Failure("Já existe uma programação ativa.")
        val now = time.now()
        val started = try {
            engine.start(engine.create(steps, now), now)
        } catch (e: IllegalArgumentException) {
            return OpResult.Failure("Programação inválida: ${e.message}")
        }
        try {
            store.save(started)
        } catch (e: Exception) {
            return OpResult.Failure("Não foi possível salvar o estado da sessão. A programação não foi iniciada.")
        }
        session = started
        persistError = null
        flushHistory()
        OpResult.Ok(session)
    }

    fun tick(): Session? = synchronized(lock) {
        if (!loaded) load()
        val s = session ?: return null
        val next = engine.tick(s, time.now())
        if (next !== s) commit(next) else if (persistError != null) commit(s)
        session
    }

    fun checkpoint(): Session? = synchronized(lock) {
        if (!loaded) load()
        val s = session ?: return null
        if (s.status != SessionStatus.RUNNING) return s
        commit(engine.checkpoint(s, time.now()))
        session
    }

    /** Sinal de inicialização do aparelho. Idempotente: vários sinais não criam efeitos repetidos. */
    fun onBootSignal(): Session? = tick()

    fun pause(@Suppress("UNUSED_PARAMETER") proof: AuthProof): OpResult = synchronized(lock) {
        if (!loaded) load()
        val s = session ?: return OpResult.Failure("Nenhuma programação ativa.")
        if (s.status != SessionStatus.RUNNING) return OpResult.Failure("A programação não está em execução.")
        commit(engine.pauseByUser(s, time.now()))
        OpResult.Ok(session)
    }

    fun resume(): OpResult = synchronized(lock) {
        if (!loaded) load()
        val s = session ?: return OpResult.Failure("Nenhuma programação para continuar.")
        if (!s.status.isPaused) return OpResult.Failure("A programação não está pausada.")
        if (!commit(engine.resume(s, time.now()))) {
            return OpResult.Failure(persistError ?: "Falha ao salvar.")
        }
        OpResult.Ok(session)
    }

    fun end(@Suppress("UNUSED_PARAMETER") proof: AuthProof): OpResult = synchronized(lock) {
        if (!loaded) load()
        val s = session ?: return OpResult.Failure("Nenhuma programação ativa.")
        if (!s.status.isActive) return OpResult.Failure("A programação já terminou.")
        commit(engine.endEarly(s, time.now()))
        OpResult.Ok(session)
    }

    fun markTechnicalInterruption(reason: String): Session? = synchronized(lock) {
        if (!loaded) load()
        val s = session ?: return null
        commit(engine.markTechnicalInterruption(s, time.now(), reason))
        session
    }

    fun reportEnforcement(ok: Boolean): Session? = synchronized(lock) {
        if (!loaded) load()
        val s = session ?: return null
        val next = engine.reportEnforcement(s, ok, time.now())
        if (next !== s) commit(next)
        session
    }

    fun note(type: EventType, detail: String?) = synchronized(lock) {
        val s = session ?: return@synchronized
        if (s.status.isActive) commit(engine.note(s, type, time.now(), detail))
    }

    fun remainingMs(): Long = session?.let { engine.remainingMs(it, time.now()) } ?: 0
    fun executedMs(): Long = session?.let { engine.executedMs(it, time.now()) } ?: 0

    fun clearLoadProblem() { loadProblem = null }
}
