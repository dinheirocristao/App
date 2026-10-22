package br.com.autotdah.regulacaoproduzmais

import br.com.autotdah.regulacaoproduzmais.data.SessionLoad
import br.com.autotdah.regulacaoproduzmais.data.SessionStore
import br.com.autotdah.regulacaoproduzmais.model.Now
import br.com.autotdah.regulacaoproduzmais.model.Session
import br.com.autotdah.regulacaoproduzmais.model.Step
import br.com.autotdah.regulacaoproduzmais.model.TimeSource
import java.io.File
import java.io.IOException
import java.nio.file.Files

const val MIN = 60_000L
const val CHROME = "com.android.chrome"
const val INSTAGRAM = "com.instagram.android"

/** Relógio controlável: monotônico + civil + identificador de inicialização. */
class FakeTime(
    var elapsed: Long = 10_000,
    var wall: Long = 1_760_000_000_000,
    var boot: String = "boot-1",
) : TimeSource {
    override fun now() = Now(elapsed, wall, boot)

    fun advance(ms: Long) {
        elapsed += ms
        wall += ms
    }

    fun advanceMin(m: Int) = advance(m * MIN)

    /** Simula desligar o aparelho por [offMs] e ligar de novo. */
    fun reboot(offMs: Long) {
        boot += "+"
        elapsed = 8_000
        wall += offMs
    }
}

fun idGen(): () -> String {
    var n = 0
    return { "id-${++n}" }
}

fun sixSteps() = listOf(
    Step.app(CHROME, "Chrome", 30),
    Step.all(20),
    Step.app(CHROME, "Chrome", 40),
    Step.app(INSTAGRAM, "Instagram", 10),
    Step.none(50),
    Step.app(CHROME, "Chrome", 30),
)

fun tempDir(): File = Files.createTempDirectory("autotdah-test").toFile()

class MemoryStore : SessionStore {
    var saved: Session? = null
    var failSave = false
    var saves = 0

    override fun load(): SessionLoad = saved?.let { SessionLoad.Loaded(it, false) } ?: SessionLoad.Empty

    override fun save(session: Session) {
        if (failSave) throw IOException("disco cheio (simulado)")
        saves++
        saved = session
    }
}
