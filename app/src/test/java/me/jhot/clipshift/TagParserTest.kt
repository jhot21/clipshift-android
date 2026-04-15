package me.jhot.clipshift

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TagParserTest {

    @Test
    fun `parses all fields from full tag string`() {
        val tags = TagParser.parse("v:1,did:550e8400-e29b-41d4-a716-446655440000,type:text,ts:1711065600000,compression:gzip")
        assertThat(tags.version).isEqualTo(1)
        assertThat(tags.deviceId).isEqualTo("550e8400-e29b-41d4-a716-446655440000")
        assertThat(tags.contentType).isEqualTo("text")
        assertThat(tags.timestamp).isEqualTo(1711065600000L)
        assertThat(tags.encrypted).isFalse()
        assertThat(tags.compression).isEqualTo(CompressionAlgorithm.Gzip)
    }

    @Test
    fun `detects encrypted flag`() {
        val tags = TagParser.parse("v:1,did:uuid,type:text,ts:0,encrypted")
        assertThat(tags.encrypted).isTrue()
    }

    @Test
    fun `missing tags produce null fields`() {
        val tags = TagParser.parse("")
        assertThat(tags.version).isNull()
        assertThat(tags.deviceId).isNull()
        assertThat(tags.contentType).isNull()
        assertThat(tags.timestamp).isNull()
        assertThat(tags.encrypted).isFalse()
    }

    @Test
    fun `non-numeric version tag is ignored, not thrown`() {
        val tags = TagParser.parse("v:abc")
        assertThat(tags.version).isNull()
    }

    @Test
    fun `version greater than 1 is parsed correctly for caller to reject`() {
        val tags = TagParser.parse("v:2")
        assertThat(tags.version).isEqualTo(2)
    }

    @Test
    fun `non-numeric ts tag is ignored`() {
        val tags = TagParser.parse("ts:notanumber")
        assertThat(tags.timestamp).isNull()
    }

    @Test
    fun `unknown tags are silently ignored`() {
        val tags = TagParser.parse("v:1,unknowntag,foo:bar")
        assertThat(tags.version).isEqualTo(1)
    }

    @Test
    fun `compression gzip tag is parsed as Gzip`() {
        val tags = TagParser.parse("v:1,did:uuid,type:text,ts:0,compression:gzip")
        assertThat(tags.compression).isEqualTo(CompressionAlgorithm.Gzip)
    }

    @Test
    fun `compression png tag is parsed as Png`() {
        val tags = TagParser.parse("v:1,did:uuid,type:image,ts:0,compression:png")
        assertThat(tags.compression).isEqualTo(CompressionAlgorithm.Png)
    }

    @Test
    fun `compression jpeg tag is parsed as Jpeg`() {
        val tags = TagParser.parse("v:1,did:uuid,type:image,ts:0,compression:jpeg")
        assertThat(tags.compression).isEqualTo(CompressionAlgorithm.Jpeg)
    }

    @Test
    fun `compression webp tag is parsed as WebP`() {
        val tags = TagParser.parse("v:1,did:uuid,type:image,ts:0,compression:webp")
        assertThat(tags.compression).isEqualTo(CompressionAlgorithm.WebP)
    }

    @Test
    fun `compression heic tag is parsed as Heic`() {
        val tags = TagParser.parse("v:1,did:uuid,type:image,ts:0,compression:heic")
        assertThat(tags.compression).isEqualTo(CompressionAlgorithm.Heic)
    }

    @Test
    fun `unknown compression tag is parsed as Unknown with preserved name`() {
        val tags = TagParser.parse("v:1,did:uuid,type:text,ts:0,compression:bmp")
        assertThat(tags.compression).isEqualTo(CompressionAlgorithm.Unknown("bmp"))
    }

    @Test
    fun `absent compression tag is null`() {
        val tags = TagParser.parse("v:1,did:uuid,type:text,ts:0")
        assertThat(tags.compression).isNull()
    }
}
