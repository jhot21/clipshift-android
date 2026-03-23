package me.jhot.clipshift

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

private const val ENCRYPTED_PREFS_FILE = "clipshift_secure"

object EncryptedPrefs {
    @Volatile
    private var instance: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences {
        return instance ?: synchronized(this) {
            instance ?: createPrefs(context.applicationContext).also { instance = it }
        }
    }

    private fun createPrefs(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
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
