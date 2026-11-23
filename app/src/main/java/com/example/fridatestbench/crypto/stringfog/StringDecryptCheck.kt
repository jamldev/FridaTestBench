package com.example.fridatestbench.crypto.stringfog

import com.github.megatronking.stringfog.annotation.StringFogIgnore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class StringDecryptCheck {

    var stringFogFirst: String = ""
        private set
    var stringFogSecond: String = ""
        private set
    var stringFogThird: String = ""
        private set
    var stringFogFourth: String = ""
        private set
    var stringFogFifth: String = ""
        private set

    var aesFirst: String = ""
        private set
    var aesSecond: String = ""
        private set
    var aesThird: String = ""
        private set
    var aesFourth: String = ""
        private set
    var aesFifth: String = ""
        private set

    var xorFirst: String = ""
        private set
    var xorSecond: String = ""
        private set
    var xorThird: String = ""
        private set
    var xorFourth: String = ""
        private set
    var xorFifth: String = ""
        private set

    // StringFog rewrites these literals to calls to its XOR decryptor at build time.
    fun decryptStrings() {
        stringFogFirst = "Morning sunlight spills across the kitchen floor before breakfast begins."
        stringFogSecond = "Small boats drift quietly past the lighthouse after the storm."
        stringFogThird = "Fresh snow covers every garden path beneath the pale moon."
        stringFogFourth = "Children collect smooth stones beside the river during summer holidays."
        stringFogFifth = "Warm bread fills the bakery with comfort on rainy afternoons."
    }

    fun decryptAesStrings() {
        val cipher = Cipher.getInstance(AesFixtures.transformation)
        val decoder = Base64.getDecoder()

        fun decrypt(encoded: String): String {
            val payload = decoder.decode(encoded)
            val nonceSize = AesFixtures.NONCE_SIZE
            val parameters = GCMParameterSpec(AesFixtures.TAG_BITS, payload, 0, nonceSize)
            cipher.init(Cipher.DECRYPT_MODE, AesFixtures.key, parameters)
            val plaintext = cipher.doFinal(payload, nonceSize, payload.size - nonceSize)
            return String(plaintext, Charsets.UTF_8)
        }

        aesFirst = decrypt(AesFixtures.firstPayload)
        aesSecond = decrypt(AesFixtures.secondPayload)
        aesThird = decrypt(AesFixtures.thirdPayload)
        aesFourth = decrypt(AesFixtures.fourthPayload)
        aesFifth = decrypt(AesFixtures.fifthPayload)
    }

    fun decryptCustomXorStrings() {
        xorFirst = decryptXorString(XorFixtures.firstCiphertext, XorFixtures.firstKey)
        xorSecond = decryptXorString(XorFixtures.secondCiphertext, XorFixtures.secondKey)
        xorThird = decryptXorString(XorFixtures.thirdCiphertext, XorFixtures.thirdKey)
        xorFourth = decryptXorString(XorFixtures.fourthCiphertext, XorFixtures.fourthKey)
        xorFifth = decryptXorString(XorFixtures.fifthCiphertext, XorFixtures.fifthKey)
    }

    // Both arguments are Base64 encoded so the encrypted data and key can contain any byte.
    private fun decryptXorString(ciphertext: String, key: String): String {
        val decoder = Base64.getDecoder()
        val bytes = decoder.decode(ciphertext)
        val keyBytes = decoder.decode(key)
        if (keyBytes.isEmpty()) throw IllegalArgumentException()

        for (index in bytes.indices) {
            val keyByte = keyBytes[index % keyBytes.size]
            bytes[index] = (bytes[index].toInt() xor keyByte.toInt()).toByte()
        }
        return String(bytes, Charsets.UTF_8)
    }

    @StringFogIgnore
    private object AesFixtures {
        const val NONCE_SIZE = 12
        const val TAG_BITS = 128
        val transformation = "AES/GCM/NoPadding"
        val key = SecretKeySpec(
            Base64.getDecoder().decode("slvOg5pZHpnzHDHMacaf+PSF9tkVfiYHAkblYWpp98M="),
            "AES",
        )

        // Each fixture contains a random nonce followed by its ciphertext and authentication tag.
        val firstPayload =
            "tqhZ74bp4tLhfHY8hMV4SH+2r8V67J4YB+TWFqdOXdb6JpYudP0Kq0vF3oK3FiPY764zcteXWH0DK6bCcXWIt9tO2evnXcLBTtiJDDFkQ7pvrr8IsM3d3VJxgIY="
        val secondPayload =
            "rc8S4hQIjP7hQA5k3j2iQ4qBtt/k+tbt9WaflMG+jYobKhlGDht/2vvIkd/Bhp5b0W2bVNLesyE7QxZla9C4AbpYjKwVmI35GVOtmU3ISu41bO3H7SO7oxt/guur6cPyKA=="
        val thirdPayload =
            "TLltiZxGCapyCQwtkQGPezTZz6zz0LgMVbmiZaS+qSEZ1fJJovrysKa1kI/VrTerkEn/NSrwKxpKoJPEx+fkEHJnf7pghSsxtYxF5elI0zpZRTIFxrmgFBBdc4APEg=="
        val fourthPayload =
            "YPUFtKIw7gNHdCYmGpsOSOiLQGpxFmQyGZjGKo9XIdOWJ88DP+w/IZTZxu06+VjFUrx8tnS9uzF88o1kCWS+3YBPqOR+vg6EIklEmbuteM/8BZb86oOprPGb7o8VDpk5"
        val fifthPayload =
            "Snulhokotu904ZV3WI38Sl7oV3c1mRPPlSNWn0jEzpr2lkDYYxL0kjE2suvTFcbARrKpkInpfqlfh50Eb3D6nGPn8WDKOjf5mN5ifyCEZbMKncEh/CS8wDA="
    }

    @StringFogIgnore
    private object XorFixtures {
        val firstCiphertext = "Zuiww8pxHOe4bo0J20qO7VXvudWPfVn4tGueTNwCjKxH4rLEyj9d7alqiUzJSp7lT+Olh855SO6vYZQDxkQ="
        val firstKey = "IYfcp68fPIvdD/tsqGrpjA=="

        val secondCiphertext = "T1H7iNjoXMBlBSbnmE05PHpF4MHW+A/Kbgki+40YJW5oSfrPxr1bxmEJM+GOH2k7dULx2pXpR8wmHzPonwQmIDtF+MfW9gE="
        val secondKey = "GyaUqLWdL6kGbEeJ621JTg=="

        val thirdCiphertext = "h85OlE1GrQwb1u8djjanQbXOTZpXVehHBsrvHZo2sUiizkiNSkrjAFLV71iXOrpA5p5Fm1ZK7hRc"
        val thirdKey = "xu4s+DgjjWdyooo9/F/UJA=="

        val fourthCiphertext = "algZkqrCLPc9uThiZ/+flB5WDte6y2pE9eo/dXzsicdcVQ/brcZqQPz8a3Rg55SeHlYO3afXakfg/Dt0Ow=="
        val fourthKey = "PjB8ssmjSjSUmUsHFYn65w=="

        val fifthCiphertext = "grtuTkJfryjH7MlMrchdbqbyaVdBVfs3hunEWuHTTz2hvG5VDk7nOob4yV7u1xx4r7FyGkNV/THP9MsR"
        val fifthKey = "ztIaOi46j1+mmqw/jb88HQ=="
    }
}
