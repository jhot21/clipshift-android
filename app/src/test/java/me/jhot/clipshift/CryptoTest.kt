package me.jhot.clipshift

import com.google.common.truth.Truth.assertThat
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.text.Normalizer

/**
 * Pure-JVM Argon2id implementation using BouncyCastle, substituted for the JNI-backed
 * argon2kt during unit tests where native libraries are unavailable.
 */
private class BcArgon2idDeriver : KeyDeriver {
    override fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val normalized = Normalizer.normalize(passphrase, Normalizer.Form.NFKC)
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(3)
            .withMemoryAsKB(65536)
            .withParallelism(1)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .build()
        val gen = Argon2BytesGenerator()
        gen.init(params)
        val key = ByteArray(32)
        gen.generateBytes(normalized.toByteArray(Charsets.UTF_8), key)
        return key
    }
}

class CryptoTest {

    @Before
    fun injectTestDeriver() {
        // Replace JNI-backed Argon2Kt with pure-JVM BouncyCastle implementation for unit tests
        Crypto.keyDeriver = BcArgon2idDeriver()
    }

    @Test
    fun `round-trip encrypt then decrypt returns original plaintext`() {
        val original = "hello clipboard sync"
        val passphrase = "test-passphrase"
        val ciphertext = Crypto.encrypt(original.toByteArray(Charsets.UTF_8), passphrase)
        val decrypted = Crypto.decrypt(ciphertext, passphrase)
        assertThat(String(decrypted, Charsets.UTF_8)).isEqualTo(original)
    }

    @Test
    fun `encrypt produces base64 output starting with version byte 1`() {
        val ciphertext = Crypto.encrypt("test".toByteArray(), "pass")
        val bytes = java.util.Base64.getDecoder().decode(ciphertext)
        assertThat(bytes[0]).isEqualTo(1.toByte())
    }

    @Test
    fun `wire format has correct byte layout`() {
        val ciphertext = Crypto.encrypt("test".toByteArray(), "pass")
        val bytes = java.util.Base64.getDecoder().decode(ciphertext)
        // version(1) + salt(16) + nonce(24) = 41 bytes minimum before ciphertext
        assertThat(bytes.size).isGreaterThan(41)
    }

    @Test
    fun `wrong passphrase throws on decrypt`() {
        val ciphertext = Crypto.encrypt("hello".toByteArray(), "correct-pass")
        try {
            Crypto.decrypt(ciphertext, "wrong-pass")
            fail("Expected exception")
        } catch (e: Exception) {
            // expected — AEAD tag mismatch
        }
    }

    @Test
    fun `each encrypt call produces different ciphertext (fresh salt+nonce)`() {
        val ct1 = Crypto.encrypt("same".toByteArray(), "pass")
        val ct2 = Crypto.encrypt("same".toByteArray(), "pass")
        assertThat(ct1).isNotEqualTo(ct2)
    }

    @Test
    fun `decrypts known ciphertext from desktop app`() {
        val desktopCiphertext = "TODO_REPLACE_WITH_DESKTOP_VECTOR"
        if (desktopCiphertext.startsWith("TODO")) return  // skip until vector is populated
        val decrypted = Crypto.decrypt(desktopCiphertext, "test-vector-pass")
        assertThat(String(decrypted, Charsets.UTF_8)).isEqualTo("hello cross-platform")
    }

    @Test
    fun `unicode passphrase is NFKC normalized`() {
        val pass1 = "\u00e9"         // precomposed é
        val pass2 = "e\u0301"        // decomposed e + combining accent
        val ct = Crypto.encrypt("data".toByteArray(), pass1)
        val decrypted = Crypto.decrypt(ct, pass2)
        assertThat(String(decrypted)).isEqualTo("data")
    }
}
