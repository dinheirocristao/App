package br.com.autotdah.regulacaoproduzmais

import br.com.autotdah.regulacaoproduzmais.data.SettingsRepository
import br.com.autotdah.regulacaoproduzmais.data.SettingsStore
import br.com.autotdah.regulacaoproduzmais.security.PinCheck
import br.com.autotdah.regulacaoproduzmais.security.PinManager
import br.com.autotdah.regulacaoproduzmais.security.PlainWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** PIN + limitação de tentativas persistidos em disco (configurações reais). */
class PinManagerTest {
    private val dir = tempDir()
    private val time = FakeTime()

    private fun manager() = PinManager(SettingsRepository(SettingsStore(dir), time), PlainWrapper, time)

    @Test
    fun pinPersisteEBloqueioSobreviveAReaberturaDoApp() {
        val a = manager()
        assertFalse(a.hasPin())
        a.setPin("1357")
        assertFalse("PIN não pode aparecer no arquivo", File(dir, "settings.json").readText().contains("1357"))

        repeat(3) { a.check("0000") }
        val b = manager() // app reaberto
        assertTrue(b.hasPin())
        assertTrue(b.check("1357") is PinCheck.Locked)
        time.advance(31_000)
        assertTrue(b.check("1357") is PinCheck.Ok)
        assertEquals(0L, b.lockRemainingMs())
    }

    @Test
    fun registroIlegivelNaoAutoriza() {
        val a = manager()
        a.setPin("2468")
        val corrompido = File(dir, "settings.json").readText().replace("\\\"hash\\\"", "\\\"hax\\\"")
        File(dir, "settings.json").writeText(corrompido)
        val b = manager()
        assertFalse(b.isReadable())
        assertEquals(PinCheck.Unavailable, b.check("2468"))
    }
}
