package br.com.autotdah.regulacaoproduzmais.domain

import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.HistoryEvent
import br.com.autotdah.regulacaoproduzmais.model.Now
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.model.StepOutcome
import br.com.autotdah.regulacaoproduzmais.model.StepResult
import java.util.UUID

/**
 * Máquina de estados pura da sessão (sem Android, sem interface).
 *
 * Regras de tempo:
 * - Em execução, o tempo corre inclusive com a tela desligada (relógio monotônico).
 * - Pausada (pelo usuário ou por reinício), o tempo fica congelado.
 * - O relógio civil (data/hora do aparelho) nunca é usado para medir duração; serve só
 *   para registrar quando os eventos aconteceram.
 * - Se a inicialização do aparelho mudou, a âncora monotônica é inválida: a sessão é pausada
 *   com o tempo consumido até o último ponto salvo (checkpoint).
 */
class SessionEngine(private val newId: () -> String = { UUID.randomUUID().toString() }) {

    fun create(steps: List<Step>, now: Now): Session {
        require(steps.isNotEmpty()) { "Programação sem etapas" }
        require(steps.size <= Limits.MAX_STEPS) { "Mais de ${Limits.MAX_STEPS} etapas" }
        require(steps.all { it.minutes in Limits.MIN_MINUTES..Limits.MAX_MINUTES }) { "Duração inválida" }
        return Session(
            id = newId(), steps = steps, status = SessionStatus.CONFIGURED, currentIndex = 0,
            consumedInStepMs = 0, anchorElapsedMs = null, anchorBootId = null,
            stepStartedWallMs = null, sessionStartedWallMs = null, lastPauseWallMs = null,
            createdWallMs = now.wallMs, updatedWallMs = now.wallMs, endedWallMs = null,
            pauseCount = 0, enforcementOk = true, results = emptyList(), outbox = emptyList(),
        )
    }

    fun start(s: Session, now: Now): Session {
        check(s.status == SessionStatus.CONFIGURED) { "Sessão já iniciada" }
        val running = s.copy(
            status = SessionStatus.RUNNING, currentIndex = 0, consumedInStepMs = 0,
            anchorElapsedMs = now.elapsedMs, anchorBootId = now.bootId,
            stepStartedWallMs = now.wallMs, sessionStartedWallMs = now.wallMs, updatedWallMs = now.wallMs,
        )
        return running.withEvents(
            event(s, EventType.SESSION_STARTED, now.wallMs, null,
                "${s.steps.size} etapa(s) · total ${TimeFormat.duration(s.plannedMs)}"),
            event(s, EventType.STEP_STARTED, now.wallMs, 0, "Etapa 1: ${TimeFormat.step(s.steps[0])}"),
        )
    }

    /** A âncora monotônica ainda é confiável nesta inicialização? */
    fun anchorValid(s: Session, now: Now): Boolean =
        s.anchorElapsedMs != null && s.anchorBootId == now.bootId && now.elapsedMs >= s.anchorElapsedMs

    /** Tempo consumido na etapa atual. */
    fun consumedMs(s: Session, now: Now): Long =
        if (s.status == SessionStatus.RUNNING && anchorValid(s, now)) {
            s.consumedInStepMs + (now.elapsedMs - s.anchorElapsedMs!!)
        } else s.consumedInStepMs

    fun remainingMs(s: Session, now: Now): Long {
        val step = s.currentStep ?: return 0
        if (s.status.isTerminal) return 0
        return (step.durationMs - consumedMs(s, now)).coerceAtLeast(0)
    }

    fun executedMs(s: Session, now: Now): Long =
        s.results.sumOf { it.executedMs } + if (s.status.isActive) consumedMs(s, now).coerceAtMost(s.currentStep?.durationMs ?: 0) else 0

    /**
     * Avança a sessão de acordo com o tempo. Processa várias etapas de uma vez se o app ficou
     * muito tempo sem ser executado (ex.: tela desligada, modo soneca), carregando o excedente.
     * Retorna a mesma instância se nada mudou.
     */
    fun tick(s: Session, now: Now): Session {
        if (s.status != SessionStatus.RUNNING) return s
        if (!anchorValid(s, now)) return pauseForReboot(s, now)

        var idx = s.currentIndex
        var consumed = consumedMs(s, now)
        var stepStartWall = s.stepStartedWallMs
        val results = s.results.toMutableList()
        val events = mutableListOf<HistoryEvent>()

        while (true) {
            val step = s.steps[idx]
            if (consumed < step.durationMs) break
            val overflow = consumed - step.durationMs
            val endWall = now.wallMs - overflow
            results += StepResult(idx, StepOutcome.COMPLETED, step.durationMs, stepStartWall ?: (endWall - step.durationMs), endWall)
            events += event(s, EventType.STEP_COMPLETED, endWall, idx, "Etapa ${idx + 1}: ${TimeFormat.step(step)}")
            if (idx == s.steps.lastIndex) {
                events += event(s, EventType.COMPLETED, endWall, idx,
                    "Todas as ${s.steps.size} etapas concluídas · ${TimeFormat.duration(s.plannedMs)}")
                return s.copy(
                    status = SessionStatus.COMPLETED, consumedInStepMs = 0,
                    anchorElapsedMs = null, anchorBootId = null, endedWallMs = endWall,
                    results = results, updatedWallMs = now.wallMs,
                ).withEvents(events)
            }
            idx++
            consumed = overflow
            stepStartWall = endWall
            events += event(s, EventType.STEP_STARTED, endWall, idx, "Etapa ${idx + 1}: ${TimeFormat.step(s.steps[idx])}")
        }
        if (idx == s.currentIndex) return s
        return s.copy(
            currentIndex = idx, consumedInStepMs = consumed, anchorElapsedMs = now.elapsedMs,
            stepStartedWallMs = stepStartWall, results = results, updatedWallMs = now.wallMs,
        ).withEvents(events)
    }

    /** Re-ancora o tempo consumido para que o arquivo salvo reflita o progresso até agora. */
    fun checkpoint(s: Session, now: Now): Session {
        val t = tick(s, now)
        if (t.status != SessionStatus.RUNNING) return t
        return t.copy(consumedInStepMs = consumedMs(t, now), anchorElapsedMs = now.elapsedMs, updatedWallMs = now.wallMs)
    }

    private fun pauseForReboot(s: Session, now: Now): Session {
        val remaining = (s.currentStep!!.durationMs - s.consumedInStepMs).coerceAtLeast(0)
        return s.copy(
            status = SessionStatus.PAUSED_REBOOT, anchorElapsedMs = null, anchorBootId = null,
            lastPauseWallMs = now.wallMs, pauseCount = s.pauseCount + 1, updatedWallMs = now.wallMs,
        ).withEvents(event(s, EventType.PAUSED_REBOOT, now.wallMs, s.currentIndex,
            "Etapa ${s.currentIndex + 1} pausada com ${TimeFormat.clock(remaining)} restantes"))
    }

    fun pauseByUser(s: Session, now: Now): Session {
        val t = tick(s, now)
        if (t.status != SessionStatus.RUNNING) return t
        val consumed = consumedMs(t, now)
        val remaining = (t.currentStep!!.durationMs - consumed).coerceAtLeast(0)
        return t.copy(
            status = SessionStatus.PAUSED_USER, consumedInStepMs = consumed, anchorElapsedMs = null,
            anchorBootId = null, lastPauseWallMs = now.wallMs, pauseCount = t.pauseCount + 1,
            updatedWallMs = now.wallMs,
        ).withEvents(event(t, EventType.PAUSED_USER, now.wallMs, t.currentIndex,
            "Etapa ${t.currentIndex + 1} pausada com ${TimeFormat.clock(remaining)} restantes"))
    }

    fun resume(s: Session, now: Now): Session {
        check(s.status.isPaused) { "Sessão não está pausada" }
        val remaining = (s.currentStep!!.durationMs - s.consumedInStepMs).coerceAtLeast(0)
        return s.copy(
            status = SessionStatus.RUNNING, anchorElapsedMs = now.elapsedMs, anchorBootId = now.bootId,
            updatedWallMs = now.wallMs,
        ).withEvents(event(s, EventType.RESUMED, now.wallMs, s.currentIndex,
            "Etapa ${s.currentIndex + 1} retomada com ${TimeFormat.clock(remaining)} restantes"))
    }

    fun endEarly(s: Session, now: Now): Session {
        val t = if (s.status == SessionStatus.RUNNING) tick(s, now) else s
        if (!t.status.isActive) return t
        return finishInterrupted(t, now, SessionStatus.ENDED_EARLY, EventType.ENDED_AUTH, "Encerrada pelo usuário com PIN")
    }

    fun markTechnicalInterruption(s: Session, now: Now, reason: String): Session {
        if (!s.status.isActive) return s
        return finishInterrupted(s, now, SessionStatus.INTERRUPTED_TECHNICAL, EventType.TECHNICAL_INTERRUPTION, reason)
    }

    private fun finishInterrupted(t: Session, now: Now, status: SessionStatus, type: EventType, detail: String): Session {
        val step = t.currentStep!!
        val consumed = consumedMs(t, now).coerceAtMost(step.durationMs)
        val result = StepResult(t.currentIndex, StepOutcome.INTERRUPTED, consumed, t.stepStartedWallMs ?: now.wallMs, now.wallMs)
        return t.copy(
            status = status, consumedInStepMs = consumed, anchorElapsedMs = null, anchorBootId = null,
            endedWallMs = now.wallMs, results = t.results + result, updatedWallMs = now.wallMs,
        ).withEvents(
            event(t, EventType.STEP_INTERRUPTED, now.wallMs, t.currentIndex,
                "Etapa ${t.currentIndex + 1}: ${TimeFormat.duration(consumed)} de ${step.minutes} min executados"),
            event(t, type, now.wallMs, t.currentIndex, detail),
        )
    }

    /** Registra (uma única vez por mudança) se o bloqueio pode ou não ser aplicado. */
    fun reportEnforcement(s: Session, ok: Boolean, now: Now): Session {
        if (!s.status.isActive || s.enforcementOk == ok) return s
        val type = if (ok) EventType.ENFORCEMENT_RESTORED else EventType.ENFORCEMENT_UNAVAILABLE
        return s.copy(enforcementOk = ok, updatedWallMs = now.wallMs).withEvents(event(s, type, now.wallMs, s.currentIndex, null))
    }

    fun note(s: Session, type: EventType, now: Now, detail: String?): Session =
        s.withEvents(event(s, type, now.wallMs, s.currentIndex, detail))

    fun summary(s: Session, now: Now) = br.com.autotdah.regulacaoproduzmais.model.SessionSummary(
        id = s.id, startedWallMs = s.sessionStartedWallMs, endedWallMs = s.endedWallMs, status = s.status,
        plannedMs = s.plannedMs, executedMs = executedMs(s, now),
        stepsCompleted = s.results.count { it.outcome == StepOutcome.COMPLETED },
        stepsInterrupted = s.results.count { it.outcome == StepOutcome.INTERRUPTED },
        pauses = s.pauseCount, steps = s.steps, updatedWallMs = s.updatedWallMs,
    )

    private fun event(s: Session, type: EventType, wall: Long, index: Int?, detail: String?) =
        HistoryEvent(newId(), s.id, type, wall, index, detail)
}

fun Session.withEvents(vararg events: HistoryEvent): Session = copy(outbox = outbox + events)
fun Session.withEvents(events: List<HistoryEvent>): Session = copy(outbox = outbox + events)
