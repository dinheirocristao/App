package br.com.autotdah.regulacaoproduzmais.security

import br.com.autotdah.regulacaoproduzmais.model.Now

sealed interface PinCheck {
    data class Ok(val proof: AuthProof) : PinCheck

    /** PIN incorreto. [attemptsLeft] = tentativas antes do bloqueio temporário; [lockMs] > 0 se bloqueou. */
    data class Wrong(val attemptsLeft: Int, val lockMs: Long) : PinCheck
    data class Locked(val remainingMs: Long) : PinCheck
    data object NotConfigured : PinCheck
    data object Unavailable : PinCheck
}

/** Verificação de PIN com limitação de tentativas, independente do Android. */
object PinVerifier {

    /** Resultado + novo estado de tentativas a ser persistido. */
    fun check(pin: String, record: PinRecord?, recordReadable: Boolean, state: AttemptState, now: Now): Pair<PinCheck, AttemptState> {
        if (!recordReadable) return PinCheck.Unavailable to state
        if (record == null) return PinCheck.NotConfigured to state
        val normalized = AttemptLimiter.normalize(state, now)
        val locked = AttemptLimiter.remainingLockMs(normalized, now)
        if (locked > 0) return PinCheck.Locked(locked) to normalized
        return if (PinHasher.verify(pin, record)) {
            PinCheck.Ok(AuthProof()) to AttemptLimiter.onSuccess()
        } else {
            val next = AttemptLimiter.onFailure(normalized, now)
            PinCheck.Wrong(AttemptLimiter.attemptsBeforeLock(next), AttemptLimiter.remainingLockMs(next, now)) to next
        }
    }
}
