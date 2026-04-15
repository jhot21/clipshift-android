package me.jhot.clipshift

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

sealed class CompressionAlgorithm {
    object Gzip : CompressionAlgorithm()
    object Png : CompressionAlgorithm()
    object Jpeg : CompressionAlgorithm()
    object WebP : CompressionAlgorithm()
    object Heic : CompressionAlgorithm()
    data class Unknown(val name: String) : CompressionAlgorithm()
}

object Compression {

    fun compress(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun decompress(bytes: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    }
}
