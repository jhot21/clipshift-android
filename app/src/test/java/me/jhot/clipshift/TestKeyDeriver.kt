package me.jhot.clipshift

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.text.Normalizer

/**
 * Pure-JVM Argon2id implementation using BouncyCastle, substituted for the JNI-backed
 * argon2kt during unit tests where native libraries are unavailable.
 */
internal class BcArgon2idDeriver : KeyDeriver {
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
