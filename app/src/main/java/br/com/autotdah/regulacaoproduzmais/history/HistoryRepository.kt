package br.com.autotdah.regulacaoproduzmais.history

import br.com.autotdah.regulacaoproduzmais.model.HistoryEvent
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.SessionSummary

/** Identificador usado para ocorrências não vinculadas a uma sessão legível. */
const val SYSTEM_SESSION_ID = "sistema"

interface HistoryRepository {
    fun upsertSession(summary: SessionSummary)

    /** Idempotente: eventos com id já gravado são ignorados. */
    fun insertEvents(events: List<HistoryEvent>)
    fun sessions(): List<SessionSummary>
    fun events(sessionId: String): List<HistoryEvent>
    fun clear()
}

class InMemoryHistoryRepository : HistoryRepository {
    private val sessions = LinkedHashMap<String, SessionSummary>()
    private val events = LinkedHashMap<String, HistoryEvent>()

    @Synchronized override fun upsertSession(summary: SessionSummary) { sessions[summary.id] = summary }
    @Synchronized override fun insertEvents(events: List<HistoryEvent>) {
        events.forEach { this.events.putIfAbsent(it.id, it) }
    }
    @Synchronized override fun sessions() = sessions.values.sortedByDescending { it.startedWallMs ?: it.updatedWallMs }
    @Synchronized override fun events(sessionId: String) = events.values.filter { it.sessionId == sessionId }.sortedBy { it.wallMs }
    @Synchronized override fun clear() { sessions.clear(); events.clear() }
}

/** Situação exibida no histórico. Sessões “ativas” que não são a sessão atual ficam como desconhecidas. */
fun displayStatus(summary: SessionSummary, activeSessionId: String?): String = when {
    summary.status.isActive && summary.id != activeSessionId -> "Estado desconhecido"
    summary.status == SessionStatus.RUNNING -> "Em andamento"
    summary.status.isPaused -> "Pausada"
    else -> summary.status.label
}
