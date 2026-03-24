package me.jhot.clipshift

data class ParsedTags(
    val version: Int?,
    val deviceId: String?,
    val contentType: String?,
    val encrypted: Boolean,
    val timestamp: Long?,
)

object TagParser {
    fun parse(tagString: String): ParsedTags {
        if (tagString.isBlank()) return ParsedTags(null, null, null, false, null)

        var version: Int? = null
        var deviceId: String? = null
        var contentType: String? = null
        var encrypted = false
        var timestamp: Long? = null

        for (tag in tagString.split(",")) {
            when {
                tag == "encrypted" -> encrypted = true
                tag.startsWith("v:") -> version = tag.removePrefix("v:").toIntOrNull()
                tag.startsWith("did:") -> deviceId = tag.removePrefix("did:")
                tag.startsWith("type:") -> contentType = tag.removePrefix("type:")
                tag.startsWith("ts:") -> timestamp = tag.removePrefix("ts:").toLongOrNull()
            }
        }
        return ParsedTags(version, deviceId, contentType, encrypted, timestamp)
    }
}
