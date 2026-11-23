package com.example.fridatestbench.crypto

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptionSnapshot(
    val plaintext: String,
    val base64Encoded: String,
    val base64Decoded: String,
    val aesEncryptedBase64: String,
    val aesDecrypted: String,
    val aesKeyBase64: String,
    val aesIvBase64: String,
)

/**
 * Explicit crypto operations intended for dynamic-instrumentation testing.
 * The fixed key and IV make traces deterministic and must not be copied into
 * production code. Base64 is encoding rather than cryptographic encryption.
 */
class EncryptionUtils {

    fun runRoundTrips(plaintext: String): EncryptionSnapshot {
        val base64Encoded = encryptToBase64(plaintext)
        val base64Decoded = decryptFromBase64(base64Encoded)

        val aesEncrypted = encryptAes256(plaintext)
        val aesDecrypted = decryptAes256(aesEncrypted)

        return EncryptionSnapshot(
            plaintext = plaintext,
            base64Encoded = base64Encoded,
            base64Decoded = base64Decoded,
            aesEncryptedBase64 = aesEncrypted,
            aesDecrypted = aesDecrypted,
            aesKeyBase64 = Base64.getEncoder().encodeToString(AES_256_KEY),
            aesIvBase64 = Base64.getEncoder().encodeToString(AES_IV),
        )
    }

    fun encryptToBase64(plaintext: String): String {
        return Base64.getEncoder().encodeToString(plaintext.toByteArray(StandardCharsets.UTF_8))
    }

    fun decryptFromBase64(encoded: String): String {
        val decoded = Base64.getDecoder().decode(encoded)
        return String(decoded, StandardCharsets.UTF_8)
    }

    fun encryptAes256(plaintext: String): String {
        val cipher = newAesCipher(Cipher.ENCRYPT_MODE)
        val encrypted = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decryptAes256(encryptedBase64: String): String {
        val cipher = newAesCipher(Cipher.DECRYPT_MODE)
        val decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64))
        return String(decrypted, StandardCharsets.UTF_8)
    }

    private fun newAesCipher(mode: Int): Cipher {
        return Cipher.getInstance(AES_TRANSFORMATION).apply {
            init(
                mode,
                SecretKeySpec(AES_256_KEY, "AES"),
                IvParameterSpec(AES_IV),
            )
        }
    }

    private companion object {
        const val AES_TRANSFORMATION = "AES/CBC/PKCS5Padding"

        val AES_256_KEY = "0123456789ABCDEF0123456789ABCDEF"
            .toByteArray(StandardCharsets.UTF_8)

        val AES_IV = "ABCDEF0123456789"
            .toByteArray(StandardCharsets.UTF_8)
    }
}
