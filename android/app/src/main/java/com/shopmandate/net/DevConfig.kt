package com.shopmandate.net

import android.content.Context

/**
 * Persisted, overridable backend base URL (dev tool). Change it at runtime via the hidden
 * dialog (tap the "ShopMandate" logo 10×). The whole app reads the URL from here, so after
 * saving + restarting, every request goes to the new backend.
 */
object DevConfig {
    // Physical phone → laptop LAN IP (same Wi-Fi). Change via the hidden dialog (tap logo 10×)
    // if the laptop IP changes. Emulator would use http://10.0.2.2:5055/.
    const val DEFAULT_BASE_URL = "http://10.40.110.80:5055/"
    private const val PREFS = "shopmandate_dev"
    private const val KEY_BASE_URL = "base_url"

    fun getBaseUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun setBaseUrl(context: Context, url: String) {
        val normalized = url.trim().let { if (it.isEmpty() || it.endsWith("/")) it else "$it/" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BASE_URL, normalized.ifEmpty { DEFAULT_BASE_URL })
            .apply()
    }
}
