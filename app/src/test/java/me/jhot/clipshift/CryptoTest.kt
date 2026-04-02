package me.jhot.clipshift

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

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
    fun `decrypt throws descriptive error on ciphertext shorter than minimum frame`() {
        // version(1) + salt(16) + nonce(24) = 41 bytes minimum; anything shorter must fail clearly
        val tooShort = java.util.Base64.getEncoder().encodeToString(ByteArray(10))
        try {
            Crypto.decrypt(tooShort, "any-passphrase")
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertThat(e.message).contains("too short")
        }
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
