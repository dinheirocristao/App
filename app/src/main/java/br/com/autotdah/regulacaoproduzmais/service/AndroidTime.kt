package br.com.autotdah.regulacaoproduzmais.service

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import br.com.autotdah.regulacaoproduzmais.model.Now
import br.com.autotdah.regulacaoproduzmais.model.TimeSource

/**
 * Relógio do app. A identificação da inicialização usa Settings.Global.BOOT_COUNT
 * (incrementado a cada boot); se indisponível, usa o instante aproximado do boot.
 */
object AndroidTime : TimeSource {
    @Volatile private var bootId: String = "desconhecido"

    fun init(context: Context) {
        val count = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (_: Exception) {
            -1
        }
        bootId = if (count >= 0) {
            "bc:$count"
        } else {
            "bt:" + ((System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 600_000L)
        }
    }

    override fun now(): Now = Now(SystemClock.elapsedRealtime(), System.currentTimeMillis(), bootId)
}
