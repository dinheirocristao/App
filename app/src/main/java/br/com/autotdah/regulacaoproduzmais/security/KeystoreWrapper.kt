package br.com.autotdah.regulacaoproduzmais.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra o registro do PIN (que já é só hash + salt) com uma chave AES-256-GCM que nunca sai do
 * Android Keystore. Se o Keystore falhar no aparelho, grava o registro sem essa camada extra.
 */
class KeystoreWrapper : SecretWrapper {
    private val alias = "autotdah_pin_v1"

    override fun wrap(plain: String): String = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        "ks1:" + b64(cipher.iv) + ":" + b64(ct)
    } catch (_: Exception) {
        PlainWrapper.wrap(plain)
    }

    override fun unwrap(blob: String): String {
        if (blob.startsWith("plain:")) return PlainWrapper.unwrap(blob)
        val parts = blob.split(":")
        require(parts.size == 3 && parts[0] == "ks1") { "Formato desconhecido" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, unb64(parts[1])))
        return String(cipher.doFinal(unb64(parts[2])), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String) = Base64.getDecoder().decode(s)

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
