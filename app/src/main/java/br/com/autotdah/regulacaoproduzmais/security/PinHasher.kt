package br.com.autotdah.regulacaoproduzmais.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PIN nunca é armazenado: guardamos somente salt + derivação PBKDF2-HMAC-SHA256. */
data class PinRecord(val saltB64: String, val hashB64: String, val iterations: Int, val version: Int = 1)

object PinHasher {
    const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun isValidFormat(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    fun create(pin: String, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): PinRecord {
        require(isValidFormat(pin)) { "PIN deve ter de 4 a 8 dígitos" }
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val hash = derive(pin, salt, iterations)
        val enc = Base64.getEncoder()
        return PinRecord(enc.encodeToString(salt), enc.encodeToString(hash), iterations)
    }

    fun verify(pin: String, record: PinRecord): Boolean {
        if (!isValidFormat(pin)) return false
        val dec = Base64.getDecoder()
        val salt = dec.decode(record.saltB64)
        val expected = dec.decode(record.hashB64)
        val actual = derive(pin, salt, record.iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }
}
