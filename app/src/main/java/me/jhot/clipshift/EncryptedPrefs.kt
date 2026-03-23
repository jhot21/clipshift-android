package me.jhot.clipshift

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

private const val ENCRYPTED_PREFS_FILE = "clipshift_secure"

object EncryptedPrefs {
    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        ENCRYPTED_PREFS_FILE,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun get(context: Context, key: String): String? = prefs(context).getString(key, null)
    fun put(context: Context, key: String, value: String) =
        prefs(context).edit().putString(key, value).apply()
    fun remove(context: Context, key: String) =
        prefs(context).edit().remove(key).apply()
}
