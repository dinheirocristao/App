package br.com.autotdah.regulacaoproduzmais.domain

import br.com.autotdah.regulacaoproduzmais.model.DraftStep
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.model.Step

object Limits {
    const val MAX_STEPS = 10
    const val MIN_MINUTES = 1

    /** Limite por etapa (8 horas) para evitar valores acidentais como 3000 minutos. */
    const val MAX_MINUTES = 480
}

data class StepError(val index: Int?, val message: String)

sealed interface ValidationResult {
    data class Valid(val steps: List<Step>) : ValidationResult
    data class Invalid(val errors: List<StepError>) : ValidationResult
}

object StepValidator {

    /** Aceita somente dígitos (sem sinal, sem vírgula). */
    fun parseMinutes(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 4 || !t.all { it in '0'..'9' }) return null
        return t.toIntOrNull()
    }

    /** Soma dos minutos das etapas com duração válida. */
    fun totalMinutes(drafts: List<DraftStep>): Int =
        drafts.sumOf { d -> parseMinutes(d.minutesText)?.takeIf { it in Limits.MIN_MINUTES..Limits.MAX_MINUTES } ?: 0 }

    fun validate(drafts: List<DraftStep>, isInstalled: (String) -> Boolean = { true }): ValidationResult {
        val errors = mutableListOf<StepError>()
        if (drafts.isEmpty()) errors += StepError(null, "Adicione pelo menos uma etapa.")
        if (drafts.size > Limits.MAX_STEPS) errors += StepError(null, "O limite é de ${Limits.MAX_STEPS} etapas.")

        val steps = mutableListOf<Step>()
        drafts.forEachIndexed { i, d ->
            val n = i + 1
            val minutes = parseMinutes(d.minutesText)
            when {
                minutes == null -> errors += StepError(i, "Etapa $n: informe a duração em minutos (somente números).")
                minutes < Limits.MIN_MINUTES -> errors += StepError(i, "Etapa $n: a duração mínima é ${Limits.MIN_MINUTES} minuto.")
                minutes > Limits.MAX_MINUTES -> errors += StepError(i, "Etapa $n: a duração máxima por etapa é ${Limits.MAX_MINUTES} minutos (8 h).")
            }
            val step: Step? = when (d.type) {
                null -> { errors += StepError(i, "Etapa $n: escolha um aplicativo ou uma regra."); null }
                RuleType.ALL -> minutes?.let { Step.all(it) }
                RuleType.NONE -> minutes?.let { Step.none(it) }
                RuleType.APP -> {
                    val pkg = d.packageName
                    if (pkg.isNullOrBlank()) {
                        errors += StepError(i, "Etapa $n: escolha um aplicativo ou uma regra."); null
                    } else if (!isInstalled(pkg)) {
                        errors += StepError(i, "Etapa $n: o aplicativo “${d.label ?: pkg}” não está mais instalado. Escolha outro.")
                        null
                    } else minutes?.let { Step.app(pkg, d.label ?: pkg, it) }
                }
            }
            if (step != null && step.minutes in Limits.MIN_MINUTES..Limits.MAX_MINUTES) steps += step
        }
        return if (errors.isEmpty()) ValidationResult.Valid(steps) else ValidationResult.Invalid(errors)
    }

    fun toDraft(step: Step) = DraftStep(step.type, step.packageName, step.label, step.minutes.toString())
}
