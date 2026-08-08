package com.android.alftendev.sync

import android.content.Context
import android.provider.Settings
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.preference.PreferenceManager
import com.android.alftendev.MyApplication
import java.util.UUID

object SyncPreferences {
    const val KEY_ENABLED = "sync_enabled"
    const val KEY_SERVER_URL = "sync_server_url"
    const val KEY_DEVICE_NAME = "sync_device_name"
    const val KEY_WIFI_ONLY = "sync_wifi_only"
    const val KEY_EXCLUDED_PACKAGES = "sync_excluded_packages"
    const val KEY_RETENTION_DAYS = "sync_retention_days"

    private const val SECURE_FILE = "notification_archive_secure"
    private const val TOKEN_KEY = "api_token"
    private const val DEVICE_ID_KEY = "sync_device_id"

    private val defaultExcluded = setOf(
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.authy.authy",
        "com.onepassword.android",
        "com.lastpass.lpandroid",
        "com.bitwarden.android",
        "pl.pkobp.iko",
        "com.zenithbank.eazymoney"
    )

    private fun secure(context: Context) = EncryptedSharedPreferences.create(
        context,
        SECURE_FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun prefs() = PreferenceManager.getDefaultSharedPreferences(MyApplication.application)

    fun isEnabled() = prefs().getBoolean(KEY_ENABLED, false)
    fun setEnabled(value: Boolean) = prefs().edit().putBoolean(KEY_ENABLED, value).commit()
    fun serverUrl() = prefs().getString(KEY_SERVER_URL, "")?.trim()?.trimEnd('/').orEmpty()
    fun wifiOnly() = prefs().getBoolean(KEY_WIFI_ONLY, false)
    fun setWifiOnly(value: Boolean) = prefs().edit().putBoolean(KEY_WIFI_ONLY, value).commit()
    fun deviceName() = prefs().getString(KEY_DEVICE_NAME, android.os.Build.MODEL)?.trim().orEmpty()
    fun retentionDays() = prefs().getString(KEY_RETENTION_DAYS, "0")?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    fun token(context: Context) = secure(context).getString(TOKEN_KEY, "").orEmpty()
    fun setToken(context: Context, value: String) = secure(context).edit().putString(TOKEN_KEY, value.trim()).apply()
    fun hasToken(context: Context) = token(context).isNotBlank()

    fun deviceId(context: Context): String {
        val prefs = secure(context)
        val existing = prefs.getString(DEVICE_ID_KEY, null)
        if (existing != null) return existing
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val generated = if (androidId.isNullOrBlank()) UUID.randomUUID().toString() else androidId
        prefs.edit().putString(DEVICE_ID_KEY, generated).commit()
        return generated
    }

    fun isExcluded(packageName: String): Boolean {
        val configured = prefs().getString(KEY_EXCLUDED_PACKAGES, "").orEmpty()
            .split(',', '\n').map(String::trim).filter(String::isNotBlank).toSet()
        return packageName in defaultExcluded || packageName in configured
    }
}
