package me.jhot.clipshift

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class CompressionTest {

    @Test
    fun `round-trip compress then decompress returns original bytes`() {
        val original = "hello clipboard sync".repeat(200).toByteArray(Charsets.UTF_8)
        val result = Compression.decompress(Compression.compress(original))
        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `compress reduces size for highly compressible input`() {
        val original = "a".repeat(10_000).toByteArray(Charsets.UTF_8)
        val compressed = Compression.compress(original)
        assertThat(compressed.size).isLessThan(original.size)
    }

    @Test
    fun `decompress throws on corrupt input`() {
        assertThrows(Exception::class.java) {
            Compression.decompress("not zstd data".toByteArray())
        }
    }

    @Test
    fun `compress empty byte array round-trips correctly`() {
        val original = ByteArray(0)
        assertThat(Compression.decompress(Compression.compress(original))).isEqualTo(original)
    }
}
