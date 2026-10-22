package br.com.autotdah.regulacaoproduzmais.model

/** Tipo de regra de acesso de uma etapa. */
enum class RuleType {
    /** Somente o aplicativo escolhido fica liberado; os demais monitorados são bloqueados. */
    APP,

    /** Período livre: nenhum aplicativo é bloqueado pelo AutoTdah. */
    ALL,

    /** Todos os aplicativos monitorados ficam bloqueados (essenciais nunca são bloqueados). */
    NONE,
}

/** Etapa válida e imutável de uma programação. */
data class Step(
    val type: RuleType,
    val packageName: String?,
    val label: String,
    val minutes: Int,
) {
    val durationMs: Long get() = minutes * 60_000L

    companion object {
        const val LABEL_ALL = "Todos os aplicativos"
        const val LABEL_NONE = "Nenhum aplicativo"

        fun all(minutes: Int) = Step(RuleType.ALL, null, LABEL_ALL, minutes)
        fun none(minutes: Int) = Step(RuleType.NONE, null, LABEL_NONE, minutes)
        fun app(packageName: String, label: String, minutes: Int) =
            Step(RuleType.APP, packageName, label, minutes)
    }
}

/** Etapa em edição (ainda pode estar incompleta ou inválida). */
data class DraftStep(
    val type: RuleType? = null,
    val packageName: String? = null,
    val label: String? = null,
    val minutesText: String = "",
)

/** Leitura de tempo. [elapsedMs] é monotônico e só vale dentro da mesma inicialização ([bootId]). */
data class Now(val elapsedMs: Long, val wallMs: Long, val bootId: String)

fun interface TimeSource {
    fun now(): Now
}

enum class SessionStatus(val label: String) {
    CONFIGURED("Configurada"),
    RUNNING("Em execução"),
    PAUSED_USER("Pausada pelo usuário"),
    PAUSED_REBOOT("Pausada após reinício"),
    COMPLETED("Concluída"),
    ENDED_EARLY("Encerrada antecipadamente"),
    INTERRUPTED_TECHNICAL("Interrompida por falha técnica");

    val isActive: Boolean get() = this == RUNNING || isPaused
    val isPaused: Boolean get() = this == PAUSED_USER || this == PAUSED_REBOOT
    val isTerminal: Boolean get() = this == COMPLETED || this == ENDED_EARLY || this == INTERRUPTED_TECHNICAL
}

enum class StepOutcome { COMPLETED, INTERRUPTED }

data class StepResult(
    val index: Int,
    val outcome: StepOutcome,
    val executedMs: Long,
    val startedWallMs: Long,
    val endedWallMs: Long,
)

enum class EventType(val label: String) {
    SESSION_STARTED("Sessão iniciada"),
    STEP_STARTED("Etapa iniciada"),
    STEP_COMPLETED("Etapa concluída"),
    STEP_INTERRUPTED("Etapa interrompida"),
    PAUSED_USER("Pausa autenticada"),
    PAUSED_REBOOT("Pausada após reinício do aparelho"),
    RESUMED("Sessão retomada"),
    PROCESS_RECOVERED("Sessão recuperada após o app ser recriado"),
    ENDED_AUTH("Encerramento antecipado autenticado"),
    COMPLETED("Sessão concluída"),
    TECHNICAL_INTERRUPTION("Interrupção técnica"),
    DATA_RECOVERED("Dados restaurados da cópia de segurança"),
    ENFORCEMENT_UNAVAILABLE("Bloqueio indisponível (acessibilidade desativada)"),
    ENFORCEMENT_RESTORED("Bloqueio restabelecido"),
    SERVICE_UNAVAILABLE("Serviço em segundo plano não pôde ser iniciado"),
}

data class HistoryEvent(
    val id: String,
    val sessionId: String,
    val type: EventType,
    val wallMs: Long,
    val stepIndex: Int?,
    val detail: String?,
)

/**
 * Estado persistido da sessão. A fonte da verdade do tempo é:
 * consumido = [consumedInStepMs] + (elapsedRealtime atual − [anchorElapsedMs]),
 * válido apenas enquanto a inicialização atual for [anchorBootId].
 */
data class Session(
    val id: String,
    val steps: List<Step>,
    val status: SessionStatus,
    val currentIndex: Int,
    val consumedInStepMs: Long,
    val anchorElapsedMs: Long?,
    val anchorBootId: String?,
    val stepStartedWallMs: Long?,
    val sessionStartedWallMs: Long?,
    val lastPauseWallMs: Long?,
    val createdWallMs: Long,
    val updatedWallMs: Long,
    val endedWallMs: Long?,
    val pauseCount: Int,
    val enforcementOk: Boolean,
    val results: List<StepResult>,
    /** Eventos ainda não confirmados no histórico (gravação idempotente por id). */
    val outbox: List<HistoryEvent>,
    val formatVersion: Int = FORMAT_VERSION,
) {
    val currentStep: Step? get() = steps.getOrNull(currentIndex)
    val nextStep: Step? get() = steps.getOrNull(currentIndex + 1)
    val plannedMs: Long get() = steps.sumOf { it.durationMs }

    companion object {
        const val FORMAT_VERSION = 1
    }
}

data class Template(
    val id: String,
    val name: String,
    val steps: List<Step>,
    val createdWallMs: Long,
)

/** Resumo de uma sessão gravado no histórico. */
data class SessionSummary(
    val id: String,
    val startedWallMs: Long?,
    val endedWallMs: Long?,
    val status: SessionStatus,
    val plannedMs: Long,
    val executedMs: Long,
    val stepsCompleted: Int,
    val stepsInterrupted: Int,
    val pauses: Int,
    val steps: List<Step>,
    val updatedWallMs: Long,
)
