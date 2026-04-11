package me.jhot.clipshift

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

sealed class CompressionAlgorithm {
    object Zstd : CompressionAlgorithm()
    data class Unknown(val name: String) : CompressionAlgorithm()
}

object Compression {

    fun compress(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        ZstdOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun decompress(bytes: ByteArray): ByteArray {
        return ZstdInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    }
}
