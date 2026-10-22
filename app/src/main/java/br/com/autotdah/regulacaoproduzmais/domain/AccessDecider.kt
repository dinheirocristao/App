package br.com.autotdah.regulacaoproduzmais.domain

import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.Step

enum class AccessDecision { ALLOW, BLOCK }

/**
 * Critério único de abrangência do bloqueio:
 * - Só são controlados aplicativos que aparecem na gaveta de apps ([launchablePackages]).
 * - Nunca são bloqueados: o próprio AutoTdah e os [exemptPackages] (tela inicial, Telefone,
 *   Configurações, teclado, interface do sistema, instalador/permissões, emergência).
 * - Com [monitorAll] = false, apenas os [monitoredPackages] são controlados.
 */
data class AccessContext(
    val ownPackage: String,
    val exemptPackages: Set<String>,
    val launchablePackages: Set<String>,
    val monitorAll: Boolean,
    val monitoredPackages: Set<String>,
)

object AccessDecider {

    /** Regra vigente: só existe com a sessão em execução. Pausada ou encerrada = sem bloqueio. */
    fun activeRule(session: Session?): Step? =
        session?.takeIf { it.status == SessionStatus.RUNNING }?.currentStep

    fun isControlled(pkg: String, ctx: AccessContext): Boolean {
        if (pkg == ctx.ownPackage || pkg in ctx.exemptPackages) return false
        if (pkg !in ctx.launchablePackages) return false
        if (!ctx.monitorAll && pkg !in ctx.monitoredPackages) return false
        return true
    }

    fun decide(rule: Step?, pkg: String, ctx: AccessContext): AccessDecision {
        if (rule == null || !isControlled(pkg, ctx)) return AccessDecision.ALLOW
        return when (rule.type) {
            RuleType.ALL -> AccessDecision.ALLOW
            RuleType.NONE -> AccessDecision.BLOCK
            RuleType.APP -> if (pkg == rule.packageName) AccessDecision.ALLOW else AccessDecision.BLOCK
        }
    }

    fun ruleDescription(step: Step): String = when (step.type) {
        RuleType.ALL -> "Uso livre: nenhum aplicativo é bloqueado"
        RuleType.NONE -> "Todos os aplicativos monitorados ficam bloqueados"
        RuleType.APP -> "Apenas ${step.label} está liberado"
    }
}
