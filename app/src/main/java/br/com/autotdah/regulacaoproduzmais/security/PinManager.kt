package br.com.autotdah.regulacaoproduzmais.security

import br.com.autotdah.regulacaoproduzmais.data.Codecs
import br.com.autotdah.regulacaoproduzmais.data.SettingsRepository
import br.com.autotdah.regulacaoproduzmais.model.TimeSource

/** Protege o registro do PIN em repouso (no Android: chave AES no Android Keystore). */
interface SecretWrapper {
    fun wrap(plain: String): String
    fun unwrap(blob: String): String
}

/** Sem proteção extra (usado em testes). O PIN continua guardado apenas como hash com salt. */
object PlainWrapper : SecretWrapper {
    override fun wrap(plain: String) = "plain:$plain"
    override fun unwrap(blob: String) = blob.removePrefix("plain:")
}

class PinManager(
    private val settings: SettingsRepository,
    private val wrapper: SecretWrapper,
    private val time: TimeSource,
) {
    fun hasPin(): Boolean = settings.current.pinBlob != null

    /** Registro legível? Falso se a chave do Keystore foi perdida (ex.: restauração de backup). */
    fun isReadable(): Boolean = readRecord().second

    private fun readRecord(): Pair<PinRecord?, Boolean> {
        val blob = settings.current.pinBlob ?: return null to true
        return try {
            Codecs.pinRecord(wrapper.unwrap(blob)) to true
        } catch (_: Exception) {
            null to false
        }
    }

    /** Define um novo PIN. Para trocar um PIN existente, a interface exige o PIN atual antes. */
    fun setPin(pin: String) {
        require(PinHasher.isValidFormat(pin)) { "O PIN deve ter de 4 a 8 dígitos." }
        val blob = wrapper.wrap(Codecs.pinRecord(PinHasher.create(pin)))
        settings.update { it.copy(pinBlob = blob, attempts = AttemptState()) }
    }

    /** Verificação lenta por design (PBKDF2): chamar fora da thread principal. */
    @Synchronized
    fun check(pin: String): PinCheck {
        val (record, readable) = readRecord()
        val before = settings.current.attempts
        val (result, after) = PinVerifier.check(pin, record, readable, before, time.now())
        if (after != before) settings.update { it.copy(attempts = after) }
        return result
    }

    fun lockRemainingMs(): Long = AttemptLimiter.remainingLockMs(settings.current.attempts, time.now())
}
