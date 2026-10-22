package br.com.autotdah.regulacaoproduzmais

import br.com.autotdah.regulacaoproduzmais.data.Codecs
import br.com.autotdah.regulacaoproduzmais.data.Json
import br.com.autotdah.regulacaoproduzmais.data.JsonException
import br.com.autotdah.regulacaoproduzmais.data.SettingsStore
import br.com.autotdah.regulacaoproduzmais.data.TemplateStore
import br.com.autotdah.regulacaoproduzmais.data.AppSettings
import br.com.autotdah.regulacaoproduzmais.domain.SessionEngine
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.model.Template
import br.com.autotdah.regulacaoproduzmais.security.AttemptState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class JsonAndTemplatesTest {

    @Test
    fun jsonIdaEVoltaComCaracteresEspeciais() {
        val v = mapOf("t" to "Aspas \" barra \\ quebra\n acentuação ç ã 🎯", "n" to -42L, "l" to listOf(true, null, 1.5))
        assertEquals(v, Json.parse(Json.write(v)))
    }

    @Test
    fun jsonInvalidoLancaErro() {
        for (bad in listOf("{", "[1,]", "{\"a\" 1}", "tru", "\"sem fim", "{} lixo")) {
            try {
                Json.parse(bad)
                fail("Deveria falhar: $bad")
            } catch (_: JsonException) { }
        }
    }

    @Test
    fun sessaoIdaEVolta() {
        val time = FakeTime()
        val e = SessionEngine(idGen())
        var s = e.start(e.create(sixSteps(), time.now()), time.now())
        time.advanceMin(45)
        s = e.tick(s, time.now())
        assertEquals(s, Codecs.session(Json.parse(Json.write(Codecs.session(s))) as Map<*, *>))
    }

    @Test
    fun modelosSalvarCarregarSubstituirExcluir() {
        val dir = tempDir()
        val store = TemplateStore(dir)
        val foco = Template("t1", "Foco de 3 horas", sixSteps(), 1L)
        store.upsert(foco)
        assertEquals(listOf(foco), store.load())

        val alterado = foco.copy(id = "t2", steps = listOf(Step.none(15)))
        store.upsert(alterado) // mesmo nome substitui
        assertEquals(listOf(alterado), store.load())

        store.delete("t2")
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun configuracoesIlegiveisVoltamAoPadraoSemApagarOArquivo() {
        val dir = tempDir()
        val store = SettingsStore(dir)
        val cfg = AppSettings(true, "blob", AttemptState(2, 5, "b"), false, setOf(INSTAGRAM))
        store.save(cfg)
        assertEquals(cfg to false, store.load())
        File(dir, "settings.json").writeText("x")
        File(dir, "settings.json.bak").writeText("y")
        assertEquals(AppSettings() to true, store.load())
        assertTrue(dir.listFiles()!!.any { it.name.contains("corrompido") })
    }
}
