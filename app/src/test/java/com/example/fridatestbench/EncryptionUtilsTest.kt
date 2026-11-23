package com.example.fridatestbench

import com.example.fridatestbench.crypto.EncryptionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EncryptionUtilsTest {
    private val encryptionUtils = EncryptionUtils()
    private val payload = "super secret DCL PAYLOAD"

    @Test
    fun base64RoundTripRestoresPayload() {
        val encoded = encryptionUtils.encryptToBase64(payload)

        assertEquals("c3VwZXIgc2VjcmV0IERDTCBQQVlMT0FE", encoded)
        assertEquals(payload, encryptionUtils.decryptFromBase64(encoded))
    }

    @Test
    fun aes256RoundTripRestoresPayload() {
        val encrypted = encryptionUtils.encryptAes256(payload)

        assertNotEquals(payload, encrypted)
        assertEquals(payload, encryptionUtils.decryptAes256(encrypted))
    }

    @Test
    fun combinedRoundTripExecutesBothPaths() {
        val snapshot = encryptionUtils.runRoundTrips(payload)

        assertEquals(payload, snapshot.base64Decoded)
        assertEquals(payload, snapshot.aesDecrypted)
    }
}
