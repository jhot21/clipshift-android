package me.jhot.clipshift

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64

private const val VERSION: Byte = 1
private const val SALT_LEN = 16
private const val NONCE_LEN = 24
private const val KEY_LEN = 32
private const val MAC_BITS = 128

/**
 * Functional interface for key derivation.  The production implementation uses Argon2id via
 * argon2kt (native JNI).  Tests may inject a pure-JVM substitute via [Crypto.keyDeriver].
 */
fun interface KeyDeriver {
    fun deriveKey(passphrase: String, salt: ByteArray): ByteArray
}

object Crypto {

    /**
     * Replaceable key deriver.  Override in unit tests to avoid the argon2 JNI dependency.
     * The default implementation uses Argon2id with t=3, m=65536, p=1, len=32.
     */
    var keyDeriver: KeyDeriver = Argon2idDeriver()

    fun encrypt(plaintext: ByteArray, passphrase: String): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { random.nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        val key = keyDeriver.deriveKey(passphrase, salt)

        val (subKey, subNonce) = xchacha20Params(key, nonce)
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(subKey), MAC_BITS, subNonce))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        var len = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        len += cipher.doFinal(out, len)

        val result = ByteArray(1 + SALT_LEN + NONCE_LEN + len)
        result[0] = VERSION
        System.arraycopy(salt, 0, result, 1, SALT_LEN)
        System.arraycopy(nonce, 0, result, 1 + SALT_LEN, NONCE_LEN)
        System.arraycopy(out, 0, result, 1 + SALT_LEN + NONCE_LEN, len)

        return Base64.getEncoder().encodeToString(result)
    }

    fun decrypt(base64Ciphertext: String, passphrase: String): ByteArray {
        val bytes = Base64.getDecoder().decode(base64Ciphertext)
        val version = bytes[0]
        if (version > VERSION) throw IllegalArgumentException("Unsupported version: $version")

        val salt = bytes.copyOfRange(1, 1 + SALT_LEN)
        val nonce = bytes.copyOfRange(1 + SALT_LEN, 1 + SALT_LEN + NONCE_LEN)
        val ciphertext = bytes.copyOfRange(1 + SALT_LEN + NONCE_LEN, bytes.size)
        val key = keyDeriver.deriveKey(passphrase, salt)

        val (subKey, subNonce) = xchacha20Params(key, nonce)
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(subKey), MAC_BITS, subNonce))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        var len = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        len += cipher.doFinal(out, len)
        return out.copyOf(len)
    }

    /**
     * Derives XChaCha20 subkey and 12-byte sub-nonce from a 32-byte key and 24-byte nonce.
     * Uses HChaCha20 on the first 16 bytes of the nonce to produce the subkey,
     * then constructs a 12-byte nonce as [0,0,0,0] || nonce[16..24].
     */
    private fun xchacha20Params(key: ByteArray, nonce: ByteArray): Pair<ByteArray, ByteArray> {
        val subKey = hChacha20(key, nonce.copyOfRange(0, 16))
        val subNonce = ByteArray(12)
        // subNonce[0..3] = 0 (already zero)
        System.arraycopy(nonce, 16, subNonce, 4, 8)
        return Pair(subKey, subNonce)
    }

    /**
     * HChaCha20: produces a 32-byte output from a 32-byte key and 16-byte input.
     * This is the XChaCha20 subkey derivation function (RFC draft).
     */
    private fun hChacha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        // ChaCha20 constants
        val c0 = 0x61707865.toInt()
        val c1 = 0x3320646e.toInt()
        val c2 = 0x79622d32.toInt()
        val c3 = 0x6b206574.toInt()

        fun le32(b: ByteArray, i: Int): Int =
            (b[i].toInt() and 0xff) or
            ((b[i+1].toInt() and 0xff) shl 8) or
            ((b[i+2].toInt() and 0xff) shl 16) or
            ((b[i+3].toInt() and 0xff) shl 24)

        var x0 = c0
        var x1 = c1
        var x2 = c2
        var x3 = c3
        var x4  = le32(key, 0)
        var x5  = le32(key, 4)
        var x6  = le32(key, 8)
        var x7  = le32(key, 12)
        var x8  = le32(key, 16)
        var x9  = le32(key, 20)
        var x10 = le32(key, 24)
        var x11 = le32(key, 28)
        var x12 = le32(nonce16, 0)
        var x13 = le32(nonce16, 4)
        var x14 = le32(nonce16, 8)
        var x15 = le32(nonce16, 12)

        fun quarterRound(a: Int, b: Int, c: Int, d: Int): IntArray {
            var va = a; var vb = b; var vc = c; var vd = d
            va += vb; vd = vd xor va; vd = (vd shl 16) or (vd ushr 16)
            vc += vd; vb = vb xor vc; vb = (vb shl 12) or (vb ushr 20)
            va += vb; vd = vd xor va; vd = (vd shl 8) or (vd ushr 24)
            vc += vd; vb = vb xor vc; vb = (vb shl 7) or (vb ushr 25)
            return intArrayOf(va, vb, vc, vd)
        }

        repeat(10) {
            // Column rounds
            quarterRound(x0, x4, x8, x12).also { x0=it[0]; x4=it[1]; x8=it[2]; x12=it[3] }
            quarterRound(x1, x5, x9, x13).also { x1=it[0]; x5=it[1]; x9=it[2]; x13=it[3] }
            quarterRound(x2, x6, x10, x14).also { x2=it[0]; x6=it[1]; x10=it[2]; x14=it[3] }
            quarterRound(x3, x7, x11, x15).also { x3=it[0]; x7=it[1]; x11=it[2]; x15=it[3] }
            // Diagonal rounds
            quarterRound(x0, x5, x10, x15).also { x0=it[0]; x5=it[1]; x10=it[2]; x15=it[3] }
            quarterRound(x1, x6, x11, x12).also { x1=it[0]; x6=it[1]; x11=it[2]; x12=it[3] }
            quarterRound(x2, x7, x8, x13).also { x2=it[0]; x7=it[1]; x8=it[2]; x13=it[3] }
            quarterRound(x3, x4, x9, x14).also { x3=it[0]; x4=it[1]; x9=it[2]; x14=it[3] }
        }

        // HChaCha20 output: first 4 and last 4 words (NOT added back to initial state)
        fun putLe32(out: ByteArray, offset: Int, v: Int) {
            out[offset]   = (v and 0xff).toByte()
            out[offset+1] = ((v ushr 8) and 0xff).toByte()
            out[offset+2] = ((v ushr 16) and 0xff).toByte()
            out[offset+3] = ((v ushr 24) and 0xff).toByte()
        }

        val out = ByteArray(32)
        putLe32(out, 0, x0)
        putLe32(out, 4, x1)
        putLe32(out, 8, x2)
        putLe32(out, 12, x3)
        putLe32(out, 16, x12)
        putLe32(out, 20, x13)
        putLe32(out, 24, x14)
        putLe32(out, 28, x15)
        return out
    }
}

/**
 * Production key deriver using Argon2id via the argon2kt JNI library.
 * Parameters: t=3, m=65536, p=1, hash=32 bytes, NFKC-normalized passphrase.
 */
class Argon2idDeriver : KeyDeriver {
    override fun deriveKey(passphrase: String, salt: ByteArray): ByteArray {
        val normalized = Normalizer.normalize(passphrase, Normalizer.Form.NFKC)
        val argon2Kt = Argon2Kt()
        val result = argon2Kt.hash(
            mode = Argon2Mode.ARGON2_ID,
            password = normalized.toByteArray(Charsets.UTF_8),
            salt = salt,
            tCostInIterations = 3,
            mCostInKibibyte = 65536,
            parallelism = 1,
            hashLengthInBytes = KEY_LEN,
        )
        return result.rawHashAsByteArray()
    }
}
