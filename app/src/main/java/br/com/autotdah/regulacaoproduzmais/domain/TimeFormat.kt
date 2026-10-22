package br.com.autotdah.regulacaoproduzmais.domain

import br.com.autotdah.regulacaoproduzmais.model.Step

object TimeFormat {
    /** 29:32 ou 1:05:09 */
    fun clock(ms: Long): String {
        val total = (ms.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 3h · 1h 20min · 45 min */
    fun duration(ms: Long): String {
        val totalMin = (ms.coerceAtLeast(0) + 30_000) / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}min"
        }
    }

    fun minutesLabel(ms: Long) = duration(ms)

    fun step(step: Step) = "${step.label} — ${step.minutes} min"
}
