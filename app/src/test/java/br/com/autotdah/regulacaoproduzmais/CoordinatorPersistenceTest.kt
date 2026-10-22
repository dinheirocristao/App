package br.com.autotdah.regulacaoproduzmais

import br.com.autotdah.regulacaoproduzmais.data.FileSessionStore
import br.com.autotdah.regulacaoproduzmais.domain.OpResult
import br.com.autotdah.regulacaoproduzmais.domain.SessionCoordinator
import br.com.autotdah.regulacaoproduzmais.history.InMemoryHistoryRepository
import br.com.autotdah.regulacaoproduzmais.history.SYSTEM_SESSION_ID
import br.com.autotdah.regulacaoproduzmais.history.displayStatus
import br.com.autotdah.regulacaoproduzmais.model.EventType
import br.com.autotdah.regulacaoproduzmais.model.SessionStatus
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.security.AttemptState
import br.com.autotdah.regulacaoproduzmais.security.AuthProof
import br.com.autotdah.regulacaoproduzmais.security.PinCheck
import br.com.autotdah.regulacaoproduzmais.security.PinHasher
import br.com.autotdah.regulacaoproduzmais.security.PinVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Testes de integração: máquina de estados + arquivo real em disco + histórico. */
class CoordinatorPersistenceTest {
    private val dir = tempDir()
    private val time = FakeTime()
    private val history = InMemoryHistoryRepository()
    private val ids = idGen()

    /** Cada nova instância equivale a um processo novo (app fechado, recriado ou aparelho reiniciado). */
    private fun newProcess() = SessionCoordinator(FileSessionStore(dir), history, time, ids).also { it.ensureLoaded() }

    private fun eventTypes(sessionId: String) = history.events(sessionId).map { it.type }

    private fun proof(): AuthProof {
        val rec = PinHasher.create("2468", iterations = 1_000)
        return (PinVerifier.check("2468", rec, true, AttemptState(), time.now()).first as PinCheck.Ok).proof
    }

    private fun start(c: SessionCoordinator, steps: List<Step> = sixSteps()) =
        ((c.start(steps) as OpResult.Ok).session!!)

    @Test
    fun t07_estadoPersisteAoFecharAInterface() {
        val a = newProcess()
        val s = start(a)
        time.advanceMin(10)
        a.tick()
        val b = newProcess()
        assertEquals(s.id, b.session!!.id)
        assertEquals(SessionStatus.RUNNING, b.session!!.status)
        assertEquals(20 * MIN, b.remainingMs())
    }

    @Test
    fun t08_recuperacaoAposRecriacaoDoProcesso() {
        val a = newProcess()
        val s = start(a)
        time.advanceMin(5)
        a.checkpoint()
        time.advanceMin(7) // processo morto sem salvar: tempo continua correndo
        val b = newProcess()
        assertEquals(s.id, b.session!!.id)
        assertEquals(18 * MIN, b.remainingMs())
        assertTrue(eventTypes(s.id).contains(EventType.PROCESS_RECOVERED))
        assertEquals(1, history.sessions().size)
        assertTrue(b.start(sixSteps()) is OpResult.Failure) // não cria segunda execução
    }

    @Test
    fun t09_t11_pausaEEncerramentoAutenticados() {
        val a = newProcess()
        val s = start(a)
        time.advanceMin(8)
        assertTrue(a.pause(proof()) is OpResult.Ok)
        assertEquals(SessionStatus.PAUSED_USER, a.session!!.status)
        time.advanceMin(60)
        assertEquals(22 * MIN, a.remainingMs())
        assertTrue(a.resume() is OpResult.Ok)
        time.advanceMin(2)
        assertTrue(a.end(proof()) is OpResult.Ok)
        val fim = newProcess().session!!
        assertEquals(SessionStatus.ENDED_EARLY, fim.status)
        val resumo = history.sessions().single()
        assertEquals(SessionStatus.ENDED_EARLY, resumo.status)
        assertEquals(1, resumo.stepsInterrupted)
        assertEquals(1, resumo.pauses)
        assertEquals(10 * MIN, resumo.executedMs)
        assertTrue(eventTypes(s.id).containsAll(listOf(EventType.PAUSED_USER, EventType.RESUMED, EventType.ENDED_AUTH)))
    }

    @Test
    fun t12_registroDeEventosNoHistorico() {
        val a = newProcess()
        val s = start(a, listOf(Step.app(CHROME, "Chrome", 30), Step.all(20)))
        time.advanceMin(50)
        a.tick()
        assertEquals(
            listOf(
                EventType.SESSION_STARTED, EventType.STEP_STARTED, EventType.STEP_COMPLETED,
                EventType.STEP_STARTED, EventType.STEP_COMPLETED, EventType.COMPLETED,
            ),
            eventTypes(s.id),
        )
        assertEquals("Concluída", displayStatus(history.sessions().single(), null))
        assertTrue("Caixa de saída esvaziada após gravar", a.session!!.outbox.isEmpty())
    }

    @Test
    fun t13_t14_reinicioPausaPreservaTempoERetoma() {
        val a = newProcess()
        val s = start(a)
        time.advanceMin(7)
        a.checkpoint()
        time.reboot(offMs = 2 * 60 * MIN) // celular ficou desligado 2 horas
        val b = newProcess()
        assertEquals(SessionStatus.PAUSED_REBOOT, b.session!!.status)
        assertEquals(23 * MIN, b.remainingMs())
        time.advanceMin(45)
        assertEquals("Tempo congelado até retomar", 23 * MIN, b.remainingMs())

        assertTrue(b.resume() is OpResult.Ok)
        time.advanceMin(23)
        b.tick()
        assertEquals(1, b.session!!.currentIndex)
        assertEquals(s.id, b.session!!.id)
    }

    @Test
    fun t15_semSessoesDuplicadasComVariosSinaisDeInicializacao() {
        val a = newProcess()
        val s = start(a)
        time.advanceMin(3)
        a.checkpoint()
        time.reboot(60_000)
        val b = newProcess()
        repeat(3) { b.onBootSignal() }
        newProcess().onBootSignal()
        assertEquals(1, history.sessions().size)
        assertEquals(1, eventTypes(s.id).count { it == EventType.PAUSED_REBOOT })
        // Sinal de boot atrasado depois de retomar na mesma inicialização não pausa de novo.
        b.resume()
        b.onBootSignal()
        assertEquals(SessionStatus.RUNNING, b.session!!.status)
    }

    @Test
    fun t17_acessibilidadeDesativadaRegistraUmaVez() {
        val a = newProcess()
        val s = start(a)
        a.reportEnforcement(false)
        a.reportEnforcement(false)
        a.reportEnforcement(true)
        a.reportEnforcement(true)
        val tipos = eventTypes(s.id)
        assertEquals(1, tipos.count { it == EventType.ENFORCEMENT_UNAVAILABLE })
        assertEquals(1, tipos.count { it == EventType.ENFORCEMENT_RESTORED })
    }

    @Test
    fun t18_falhasDePersistencia() {
        val store = MemoryStore().apply { failSave = true }
        val c = SessionCoordinator(store, history, time, ids)
        val r = c.start(sixSteps())
        assertTrue(r is OpResult.Failure)
        assertNull("Sem salvar, a sessão não começa", c.session)

        store.failSave = false
        start(c)
        store.failSave = true
        time.advanceMin(30)
        c.tick()
        assertEquals("Mantém o avanço em memória", 1, c.session!!.currentIndex)
        assertNotNull(c.persistError)
        store.failSave = false
        c.tick()
        assertNull(c.persistError)
        assertEquals(1, store.saved!!.currentIndex)
    }

    @Test
    fun arquivoCorrompidoNaoEApagadoERegistraInterrupcao() {
        File(dir, "session.json").writeText("{ isto não é json")
        val c = newProcess()
        assertNull(c.session)
        assertNotNull(c.loadProblem)
        assertTrue(dir.listFiles()!!.any { it.name.contains("corrompido") })
        assertEquals(EventType.TECHNICAL_INTERRUPTION, history.events(SYSTEM_SESSION_ID).single().type)
    }

    @Test
    fun copiaDeSegurancaRecuperaArquivoDanificado() {
        val a = newProcess()
        val s = start(a)
        time.advanceMin(4)
        a.checkpoint() // gera .bak com o estado anterior íntegro
        File(dir, "session.json").writeText("lixo")
        val b = newProcess()
        assertEquals(s.id, b.session!!.id)
        assertEquals(SessionStatus.RUNNING, b.session!!.status)
        assertTrue(eventTypes(s.id).contains(EventType.DATA_RECOVERED))
    }

    @Test
    fun sessaoAtivaQueNaoEAAtualApareceComoEstadoDesconhecido() {
        val a = newProcess()
        start(a)
        val resumo = history.sessions().single()
        assertEquals("Em andamento", displayStatus(resumo, a.session!!.id))
        assertEquals("Estado desconhecido", displayStatus(resumo, "outra"))
    }
}
