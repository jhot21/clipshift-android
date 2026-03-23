package me.jhot.clipshift

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TagParserTest {

    @Test
    fun `parses all fields from full tag string`() {
        val tags = TagParser.parse("v:1,did:550e8400-e29b-41d4-a716-446655440000,type:text,ts:1711065600000")
        assertThat(tags.version).isEqualTo(1)
        assertThat(tags.deviceId).isEqualTo("550e8400-e29b-41d4-a716-446655440000")
        assertThat(tags.contentType).isEqualTo("text")
        assertThat(tags.timestamp).isEqualTo(1711065600000L)
        assertThat(tags.encrypted).isFalse()
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
}
