package br.com.autotdah.regulacaoproduzmais

import br.com.autotdah.regulacaoproduzmais.domain.SessionEngine
import br.com.autotdah.regulacaoproduzmais.domain.StepValidator
import br.com.autotdah.regulacaoproduzmais.domain.TimeFormat
import br.com.autotdah.regulacaoproduzmais.model.DraftStep
import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.RuleType
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.model.StepOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {
    private val time = FakeTime()
    private val engine = SessionEngine(idGen())

    private fun started(steps: List<Step>): Session = engine.start(engine.create(steps, time.now()), time.now())

    private fun types(s: Session) = s.outbox.map { it.type }

    @Test
    fun t01_programacaoDeUmaEtapa() {
        var s = started(listOf(Step.app(CHROME, "Chrome", 25)))
        assertEquals(SessionStatus.RUNNING, s.status)
        time.advanceMin(24)
        s = engine.tick(s, time.now())
        assertEquals(SessionStatus.RUNNING, s.status)
        assertEquals(1 * MIN, engine.remainingMs(s, time.now()))
        time.advanceMin(1)
        s = engine.tick(s, time.now())
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals(1, s.results.size)
        assertEquals(StepOutcome.COMPLETED, s.results[0].outcome)
        assertEquals(
            listOf(EventType.SESSION_STARTED, EventType.STEP_STARTED, EventType.STEP_COMPLETED, EventType.COMPLETED),
            types(s),
        )
    }

    @Test
    fun t02_programacaoComSeisEtapas() {
        var s = started(sixSteps())
        val esperado = listOf(30, 20, 40, 10, 50, 30)
        for ((i, minutos) in esperado.withIndex()) {
            assertEquals(i, s.currentIndex)
            assertEquals(minutos * MIN, engine.remainingMs(s, time.now()))
            time.advanceMin(minutos)
            s = engine.tick(s, time.now())
        }
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals(6, s.results.size)
        assertTrue(s.results.all { it.outcome == StepOutcome.COMPLETED })
    }

    @Test
    fun t03_programacaoComDezEtapas() {
        val steps = (1..10).map { if (it % 2 == 0) Step.all(5) else Step.app(CHROME, "Chrome", 5) }
        var s = started(steps)
        repeat(10) { i ->
            assertEquals(i, s.currentIndex)
            time.advanceMin(5)
            s = engine.tick(s, time.now())
        }
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals(10, s.results.size)
        assertEquals(50 * MIN, engine.executedMs(s, time.now()))
    }

    @Test
    fun t04_somaDosMinutos() {
        assertEquals(180 * MIN, engine.create(sixSteps(), time.now()).plannedMs)
        val drafts = sixSteps().map { StepValidator.toDraft(it) } +
            DraftStep(RuleType.ALL, null, null, "abc") // inválida: não entra na soma
        assertEquals(180, StepValidator.totalMinutes(drafts))
        assertEquals("3h", TimeFormat.duration(180 * MIN))
        assertEquals("1h 20min", TimeFormat.duration(80 * MIN))
    }

    @Test
    fun t05_transicaoAutomatica() {
        var s = started(sixSteps())
        time.advanceMin(29)
        assertSame("Sem mudança, a mesma instância é devolvida", s, engine.tick(s, time.now()))
        time.advanceMin(1)
        s = engine.tick(s, time.now())
        assertEquals(1, s.currentIndex)
        assertEquals(RuleType.ALL, s.currentStep!!.type)
        assertEquals(20 * MIN, engine.remainingMs(s, time.now()))
        val novos = types(s).takeLast(2)
        assertEquals(listOf(EventType.STEP_COMPLETED, EventType.STEP_STARTED), novos)
    }

    @Test
    fun t06_conclusaoDaUltimaEtapa() {
        val inicio = time.wall
        var s = started(listOf(Step.all(10), Step.none(5)))
        time.advanceMin(15)
        s = engine.tick(s, time.now())
        assertEquals(SessionStatus.COMPLETED, s.status)
        assertEquals(inicio + 15 * MIN, s.endedWallMs)
        assertEquals(0L, engine.remainingMs(s, time.now()))
        assertEquals(EventType.COMPLETED, s.outbox.last().type)
    }

    @Test
    fun t19_alteracaoDoRelogioCivilNaoAfetaADuracao() {
        var s = started(sixSteps())
        time.elapsed += 10 * MIN
        time.wall -= 3 * 60 * MIN // usuário atrasou o relógio em 3 horas
        s = engine.tick(s, time.now())
        assertEquals(20 * MIN, engine.remainingMs(s, time.now()))
        time.elapsed += 5 * MIN
        time.wall += 24 * 60 * MIN // e depois adiantou um dia
        assertEquals(15 * MIN, engine.remainingMs(s, time.now()))
        assertEquals(0, s.currentIndex)
    }

    @Test
    fun t20_telaDesligadaSemExecucaoPorMuitoTempo() {
        val inicio = time.wall
        var s = started(sixSteps())
        time.advanceMin(95) // 30 + 20 + 40 + 5: o app não rodou nada nesse intervalo
        s = engine.tick(s, time.now())
        assertEquals(3, s.currentIndex)
        assertEquals(5 * MIN, engine.remainingMs(s, time.now()))
        assertEquals(3, s.results.size)
        assertEquals(inicio + 30 * MIN, s.results[0].endedWallMs)
        assertEquals(inicio + 50 * MIN, s.results[1].endedWallMs)
        assertEquals(inicio + 90 * MIN, s.results[2].endedWallMs)
    }

    @Test
    fun pausaCongelaOTempoERetomadaContinua() {
        var s = started(sixSteps())
        time.advanceMin(10)
        s = engine.pauseByUser(s, time.now())
        assertEquals(SessionStatus.PAUSED_USER, s.status)
        time.advanceMin(120)
        assertEquals(20 * MIN, engine.remainingMs(s, time.now()))
        s = engine.resume(s, time.now())
        time.advanceMin(20)
        s = engine.tick(s, time.now())
        assertEquals(1, s.currentIndex)
        assertEquals(1, s.pauseCount)
    }

    @Test
    fun encerramentoRegistraEtapaInterrompida() {
        var s = started(sixSteps())
        time.advanceMin(12)
        s = engine.endEarly(s, time.now())
        assertEquals(SessionStatus.ENDED_EARLY, s.status)
        assertEquals(StepOutcome.INTERRUPTED, s.results.last().outcome)
        assertEquals(12 * MIN, s.results.last().executedMs)
        assertEquals(listOf(EventType.STEP_INTERRUPTED, EventType.ENDED_AUTH), types(s).takeLast(2))
    }
}
