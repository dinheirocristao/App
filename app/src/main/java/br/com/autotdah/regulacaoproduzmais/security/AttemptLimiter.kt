package br.com.autotdah.regulacaoproduzmais.security

import br.com.autotdah.regulacaoproduzmais.model.Now
import kotlin.math.min

/** Estado persistido das tentativas de PIN. */
data class AttemptState(
    val failures: Int = 0,
    val lockedUntilElapsedMs: Long = 0,
    val bootId: String? = null,
)

/**
 * Limitação progressiva: 3 tentativas livres; depois bloqueio de 30 s, 60 s, 2 min, 4 min...
 * até 15 min por tentativa errada. Reiniciar o aparelho não zera o bloqueio: na nova
 * inicialização o bloqueio volta a valer pelo tempo cheio correspondente.
 */
object AttemptLimiter {
    const val FREE_ATTEMPTS = 3
    const val BASE_LOCK_MS = 30_000L
    const val MAX_LOCK_MS = 15 * 60_000L

    fun lockDurationMs(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val exp = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return min(BASE_LOCK_MS shl exp, MAX_LOCK_MS)
    }

    fun normalize(state: AttemptState, now: Now): AttemptState {
        if (state.bootId == null || state.bootId == now.bootId) return state
        val lock = lockDurationMs(state.failures)
        return state.copy(lockedUntilElapsedMs = if (lock > 0) now.elapsedMs + lock else 0, bootId = now.bootId)
    }

    fun remainingLockMs(state: AttemptState, now: Now): Long {
        val st = normalize(state, now)
        if (st.bootId != null && st.bootId != now.bootId) return 0
        return (st.lockedUntilElapsedMs - now.elapsedMs).coerceAtLeast(0)
    }

    fun onFailure(state: AttemptState, now: Now): AttemptState {
        val failures = normalize(state, now).failures + 1
        val lock = lockDurationMs(failures)
        return AttemptState(failures, if (lock > 0) now.elapsedMs + lock else 0, now.bootId)
    }

    fun attemptsBeforeLock(state: AttemptState): Int = (FREE_ATTEMPTS - state.failures).coerceAtLeast(0)

    fun onSuccess(): AttemptState = AttemptState()
}
