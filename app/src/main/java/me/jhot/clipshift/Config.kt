package me.jhot.clipshift

import android.content.Context
import android.os.Build
import java.util.UUID

data class AppConfig(
    val deviceId: String,
    val deviceName: String,
    val topic: String,
    val encryptionEnabled: Boolean,
    val paused: Boolean,
    val baseUrl: String,
)

private const val PREFS_NAME = "clipshift_prefs"
private const val KEY_DEVICE_ID = "device_id"
private const val KEY_DEVICE_NAME = "device_name"
private const val KEY_TOPIC = "topic"
private const val KEY_ENCRYPTION_ENABLED = "encryption_enabled"
private const val KEY_PAUSED = "paused"
private const val KEY_PASSPHRASE = "passphrase"
private const val KEY_BASE_URL = "base_url"

object Config {
    fun load(context: Context): AppConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, newId).apply()
            newId
        }
        return AppConfig(
            deviceId = deviceId,
            deviceName = prefs.getString(KEY_DEVICE_NAME, null) ?: Build.MODEL,
            topic = prefs.getString(KEY_TOPIC, "") ?: "",
            encryptionEnabled = prefs.getBoolean(KEY_ENCRYPTION_ENABLED, false),
            paused = prefs.getBoolean(KEY_PAUSED, false),
            baseUrl = prefs.getString(KEY_BASE_URL, "https://ntfy.sh") ?: "https://ntfy.sh",
        )
    }

    fun save(
        context: Context,
        topic: String,
        deviceName: String,
        encryptionEnabled: Boolean,
        paused: Boolean,
        baseUrl: String,
    ) {
        val cleanBaseUrl = baseUrl.trim().trimEnd('/').ifBlank { "https://ntfy.sh" }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_TOPIC, topic)
            .putString(KEY_DEVICE_NAME, deviceName)
            .putBoolean(KEY_ENCRYPTION_ENABLED, encryptionEnabled)
            .putBoolean(KEY_PAUSED, paused)
            .putString(KEY_BASE_URL, cleanBaseUrl)
            .apply()
    }

    fun setPaused(context: Context, paused: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PAUSED, paused)
            .apply()
    }

    fun loadPassphrase(context: Context): String? =
        EncryptedPrefs.get(context, KEY_PASSPHRASE)

    fun savePassphrase(context: Context, passphrase: String?) {
        if (passphrase == null) {
            EncryptedPrefs.remove(context, KEY_PASSPHRASE)
        } else {
            EncryptedPrefs.put(context, KEY_PASSPHRASE, passphrase)
        }
    }
}
