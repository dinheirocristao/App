package br.com.autotdah.regulacaoproduzmais

import br.com.autotdah.regulacaoproduzmais.data.Codecs
import br.com.autotdah.regulacaoproduzmais.security.AttemptLimiter
import br.com.autotdah.regulacaoproduzmais.security.AttemptState
import br.com.autotdah.regulacaoproduzmais.security.PinCheck
import br.com.autotdah.regulacaoproduzmais.security.PinHasher
import br.com.autotdah.regulacaoproduzmais.security.PinVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinTest {
    private val time = FakeTime()
    private val record = PinHasher.create("4821", iterations = 1_000)

    @Test
    fun formatoDoPin() {
        assertTrue(PinHasher.isValidFormat("1234"))
        assertTrue(PinHasher.isValidFormat("12345678"))
        assertFalse(PinHasher.isValidFormat("123"))
        assertFalse(PinHasher.isValidFormat("12a4"))
        assertFalse(PinHasher.isValidFormat("123456789"))
    }

    @Test
    fun pinNaoFicaEmTextoPuroESaltMuda() {
        val json = Codecs.pinRecord(record)
        assertFalse(json.contains("4821"))
        val outro = PinHasher.create("4821", iterations = 1_000)
        assertNotEquals(record.saltB64, outro.saltB64)
        assertNotEquals(record.hashB64, outro.hashB64)
        assertTrue(PinHasher.verify("4821", Codecs.pinRecord(json)))
    }

    @Test
    fun t09_pinCorretoAutoriza() {
        val (res, estado) = PinVerifier.check("4821", record, true, AttemptState(), time.now())
        assertTrue(res is PinCheck.Ok)
        assertEquals(0, estado.failures)
    }

    @Test
    fun t10_pinIncorretoERecusadoComLimitacaoProgressiva() {
        var st = AttemptState()
        var r: PinCheck
        PinVerifier.check("0000", record, true, st, time.now()).also { r = it.first; st = it.second }
        assertEquals(PinCheck.Wrong(2, 0), r)
        PinVerifier.check("1111", record, true, st, time.now()).also { r = it.first; st = it.second }
        assertEquals(PinCheck.Wrong(1, 0), r)
        PinVerifier.check("2222", record, true, st, time.now()).also { r = it.first; st = it.second }
        assertEquals(PinCheck.Wrong(0, 30_000), r)

        // Bloqueado: nem o PIN certo passa.
        time.advance(10_000)
        PinVerifier.check("4821", record, true, st, time.now()).also { r = it.first; st = it.second }
        assertEquals(PinCheck.Locked(20_000), r)

        // Bloqueio sobrevive a reinício do aparelho.
        // (o app normaliza e salva o estado ao abrir na nova inicialização).
        time.reboot(5 * MIN)
        st = AttemptLimiter.normalize(st, time.now())
        assertEquals(30_000L, AttemptLimiter.remainingLockMs(st, time.now()))

        // Nova falha após o bloqueio dobra o tempo.
        time.advance(31_000)
        PinVerifier.check("9999", record, true, st, time.now()).also { r = it.first; st = it.second }
        assertEquals(PinCheck.Wrong(0, 60_000), r)

        time.advance(61_000)
        PinVerifier.check("4821", record, true, st, time.now()).also { r = it.first; st = it.second }
        assertTrue(r is PinCheck.Ok)
        assertEquals(AttemptState(), st)
    }

    @Test
    fun limiteMaximoDoBloqueio() {
        assertEquals(15 * MIN, AttemptLimiter.lockDurationMs(40))
        assertEquals(0L, AttemptLimiter.lockDurationMs(2))
    }

    @Test
    fun semPinOuRegistroIlegivel() {
        assertEquals(PinCheck.NotConfigured, PinVerifier.check("1234", null, true, AttemptState(), time.now()).first)
        assertEquals(PinCheck.Unavailable, PinVerifier.check("1234", null, false, AttemptState(), time.now()).first)
    }
}
