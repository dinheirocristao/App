package br.com.autotdah.regulacaoproduzmais

import br.com.autotdah.regulacaoproduzmais.domain.AccessContext
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecider
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecision.ALLOW
import br.com.autotdah.regulacaoproduzmais.domain.AccessDecision.BLOCK
import br.com.autotdah.regulacaoproduzmais.domain.SessionEngine
import br.com.autotdah.regulacaoproduzmais.domain.StepValidator
import br.com.autotdah.regulacaoproduzmais.domain.ValidationResult
import br.com.autotdah.regulacaoproduzmais.model.DraftStep
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.model.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationAndAccessTest {

    private fun errors(r: ValidationResult) = (r as ValidationResult.Invalid).errors.map { it.message }

    @Test
    fun rejeitaDuracoesInvalidas() {
        for (txt in listOf("-5", "abc", "", "0", "12,5", "481", "99999")) {
            val r = StepValidator.validate(listOf(DraftStep(RuleType.ALL, null, null, txt)))
            assertTrue("Deveria rejeitar '$txt'", r is ValidationResult.Invalid)
        }
        assertTrue(StepValidator.validate(listOf(DraftStep(RuleType.ALL, null, null, "480"))) is ValidationResult.Valid)
    }

    @Test
    fun rejeitaEtapaSemRegraEProgramacaoVaziaOuGrandeDemais() {
        assertTrue(errors(StepValidator.validate(emptyList())).first().contains("pelo menos uma"))
        assertTrue(errors(StepValidator.validate(listOf(DraftStep(null, null, null, "10")))).first().contains("escolha"))
        val onze = List(11) { DraftStep(RuleType.ALL, null, null, "5") }
        assertTrue(errors(StepValidator.validate(onze)).any { it.contains("10 etapas") })
    }

    @Test
    fun aceitaProgramacoesParciais() {
        val r = StepValidator.validate(listOf(DraftStep(RuleType.APP, CHROME, "Chrome", "30"), DraftStep(RuleType.NONE, null, null, "15")))
        val steps = (r as ValidationResult.Valid).steps
        assertEquals(listOf(Step.app(CHROME, "Chrome", 30), Step.none(15)), steps)
    }

    @Test
    fun t16_etapaComAplicativoDesinstalado() {
        val r = StepValidator.validate(
            listOf(DraftStep(RuleType.APP, "com.removido", "App removido", "10")),
            isInstalled = { it != "com.removido" },
        )
        assertTrue(errors(r).first().contains("não está mais instalado"))

        // Durante a sessão, se o app liberado sumir, os demais continuam bloqueados e nada quebra.
        val ctx = ctx()
        val regra = Step.app("com.removido", "App removido", 10)
        assertEquals(BLOCK, AccessDecider.decide(regra, INSTAGRAM, ctx))
        assertEquals(ALLOW, AccessDecider.decide(regra, "com.android.settings", ctx))
    }

    private fun ctx(monitorAll: Boolean = true, monitored: Set<String> = emptySet()) = AccessContext(
        ownPackage = "br.com.autotdah.regulacaoproduzmais",
        exemptPackages = setOf("com.android.settings", "com.android.dialer", "com.launcher"),
        launchablePackages = setOf(CHROME, INSTAGRAM, "com.whatsapp", "com.android.settings", "com.android.dialer"),
        monitorAll = monitorAll,
        monitoredPackages = monitored,
    )

    @Test
    fun regraDeAplicativoEspecifico() {
        val regra = Step.app(CHROME, "Chrome", 30)
        assertEquals(ALLOW, AccessDecider.decide(regra, CHROME, ctx()))
        assertEquals(BLOCK, AccessDecider.decide(regra, INSTAGRAM, ctx()))
        assertEquals(BLOCK, AccessDecider.decide(regra, "com.whatsapp", ctx()))
    }

    @Test
    fun essenciaisEOProprioAppNuncaSaoBloqueados() {
        val regra = Step.none(50)
        for (pkg in listOf("br.com.autotdah.regulacaoproduzmais", "com.android.settings", "com.android.dialer", "com.launcher", "com.android.systemui")) {
            assertEquals("$pkg não pode ser bloqueado", ALLOW, AccessDecider.decide(regra, pkg, ctx()))
        }
    }

    @Test
    fun regrasTodosENenhum() {
        assertEquals(ALLOW, AccessDecider.decide(Step.all(20), INSTAGRAM, ctx()))
        assertEquals(BLOCK, AccessDecider.decide(Step.none(20), INSTAGRAM, ctx()))
        assertEquals(BLOCK, AccessDecider.decide(Step.none(20), CHROME, ctx()))
    }

    @Test
    fun somenteAppsMonitoradosSelecionados() {
        val c = ctx(monitorAll = false, monitored = setOf(INSTAGRAM))
        assertEquals(BLOCK, AccessDecider.decide(Step.none(20), INSTAGRAM, c))
        assertEquals(ALLOW, AccessDecider.decide(Step.none(20), "com.whatsapp", c))
    }

    @Test
    fun semSessaoEmExecucaoNaoHaRegra() {
        val time = FakeTime()
        val e = SessionEngine(idGen())
        val configurada = e.create(sixSteps(), time.now())
        assertNull(AccessDecider.activeRule(configurada))
        val rodando = e.start(configurada, time.now())
        assertEquals(CHROME, AccessDecider.activeRule(rodando)!!.packageName)
        assertNull(AccessDecider.activeRule(e.pauseByUser(rodando, time.now())))
        assertNull(AccessDecider.activeRule(null))
    }
}
