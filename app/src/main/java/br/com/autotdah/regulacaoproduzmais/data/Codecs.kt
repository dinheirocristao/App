package br.com.autotdah.regulacaoproduzmais.data

import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.HistoryEvent
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.model.StepOutcome
import br.com.autotdah.regulacaoproduzmais.model.StepResult
import br.com.autotdah.regulacaoproduzmais.model.Template
import br.com.autotdah.regulacaoproduzmais.security.AttemptState
import br.com.autotdah.regulacaoproduzmais.security.PinRecord

/** Conversão entre modelos e JSON. Lança [JsonException] para dados incompletos ou incoerentes. */
object Codecs {

    fun step(s: Step): Map<String, Any?> = mapOf(
        "type" to s.type.name, "pkg" to s.packageName, "label" to s.label, "minutes" to s.minutes,
    )

    fun step(m: Map<*, *>): Step {
        val type = enumOf<RuleType>(m.str("type"))
        val pkg = m.strOrNull("pkg")
        if (type == RuleType.APP && pkg.isNullOrBlank()) throw JsonException("Etapa de app sem pacote")
        val minutes = m.int("minutes")
        if (minutes <= 0) throw JsonException("Duração inválida")
        return Step(type, pkg, m.str("label"), minutes)
    }

    fun steps(l: List<*>): List<Step> = l.map { step(it.asMap()) }

    fun event(e: HistoryEvent): Map<String, Any?> = mapOf(
        "id" to e.id, "session" to e.sessionId, "type" to e.type.name, "wall" to e.wallMs,
        "step" to e.stepIndex, "detail" to e.detail,
    )

    fun event(m: Map<*, *>) = HistoryEvent(
        m.str("id"), m.str("session"), enumOf(m.str("type")), m.long("wall"), m.intOrNull("step"), m.strOrNull("detail"),
    )

    private fun result(r: StepResult): Map<String, Any?> = mapOf(
        "index" to r.index, "outcome" to r.outcome.name, "executed" to r.executedMs,
        "started" to r.startedWallMs, "ended" to r.endedWallMs,
    )

    private fun result(m: Map<*, *>) = StepResult(
        m.int("index"), enumOf<StepOutcome>(m.str("outcome")), m.long("executed"), m.long("started"), m.long("ended"),
    )

    fun session(s: Session): Map<String, Any?> = mapOf(
        "v" to s.formatVersion,
        "id" to s.id,
        "steps" to s.steps.map { step(it) },
        "status" to s.status.name,
        "index" to s.currentIndex,
        "consumed" to s.consumedInStepMs,
        "anchor" to s.anchorElapsedMs,
        "boot" to s.anchorBootId,
        "stepStarted" to s.stepStartedWallMs,
        "sessionStarted" to s.sessionStartedWallMs,
        "lastPause" to s.lastPauseWallMs,
        "created" to s.createdWallMs,
        "updated" to s.updatedWallMs,
        "ended" to s.endedWallMs,
        "pauses" to s.pauseCount,
        "enforcementOk" to s.enforcementOk,
        "results" to s.results.map { result(it) },
        "outbox" to s.outbox.map { event(it) },
    )

    fun session(m: Map<*, *>): Session {
        val version = m.int("v")
        if (version > Session.FORMAT_VERSION) throw JsonException("Formato $version mais novo que o suportado")
        val steps = steps(m.list("steps"))
        val index = m.int("index")
        val status = enumOf<SessionStatus>(m.str("status"))
        val consumed = m.long("consumed")
        if (steps.isEmpty()) throw JsonException("Sessão sem etapas")
        if (index !in steps.indices) throw JsonException("Etapa atual fora do intervalo")
        if (consumed < 0) throw JsonException("Tempo consumido negativo")
        // Sessão "em execução" sem âncora é aceita: a máquina de estados a trata como reinício.
        val anchor = m.longOrNull("anchor")
        val boot = m.strOrNull("boot")
        return Session(
            id = m.str("id"), steps = steps, status = status, currentIndex = index,
            consumedInStepMs = consumed, anchorElapsedMs = anchor, anchorBootId = boot,
            stepStartedWallMs = m.longOrNull("stepStarted"), sessionStartedWallMs = m.longOrNull("sessionStarted"),
            lastPauseWallMs = m.longOrNull("lastPause"), createdWallMs = m.long("created"),
            updatedWallMs = m.long("updated"), endedWallMs = m.longOrNull("ended"),
            pauseCount = m.intOrNull("pauses") ?: 0, enforcementOk = m.bool("enforcementOk", true),
            results = m.list("results").map { result(it.asMap()) },
            outbox = m.list("outbox").map { event(it.asMap()) },
            formatVersion = version,
        )
    }

    fun template(t: Template): Map<String, Any?> = mapOf(
        "id" to t.id, "name" to t.name, "steps" to t.steps.map { step(it) }, "created" to t.createdWallMs,
    )

    fun template(m: Map<*, *>) = Template(m.str("id"), m.str("name"), steps(m.list("steps")), m.long("created"))

    fun pinRecord(r: PinRecord): String = Json.write(
        mapOf("salt" to r.saltB64, "hash" to r.hashB64, "iter" to r.iterations, "v" to r.version),
    )

    fun pinRecord(text: String): PinRecord {
        val m = Json.parse(text).asMap()
        return PinRecord(m.str("salt"), m.str("hash"), m.int("iter"), m.intOrNull("v") ?: 1)
    }

    fun attempts(a: AttemptState): Map<String, Any?> =
        mapOf("failures" to a.failures, "lockedUntil" to a.lockedUntilElapsedMs, "boot" to a.bootId)

    fun attempts(m: Map<*, *>?): AttemptState = if (m == null) AttemptState() else
        AttemptState(m.intOrNull("failures") ?: 0, m.longOrNull("lockedUntil") ?: 0, m.strOrNull("boot"))
}
