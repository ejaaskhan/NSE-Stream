package com.example.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.BuildConfig
import java.net.URI
import java.util.Locale

/**
 * Centralized API configuration for NSE STREAM v1.0.1.
 *
 * - Default base URL comes from `BuildConfig.DEFAULT_API_BASE_URL` (public, non-secret).
 * - Optional runtime override is persisted locally via SharedPreferences so users can switch
 *   to any authorized JSON movie server without recompiling or exposing private credentials in the APK.
 */
object ApiConfig {

    private const val PREFS_NAME = "nse_stream_api_config"
    private const val KEY_CUSTOM_BASE_URL = "custom_api_base_url"

    const val USER_AGENT = "NSEStream/${BuildConfig.VERSION_NAME} (Android)"
    const val PAGE_SIZE = 24
    const val CONNECT_TIMEOUT_SECONDS = 12L
    const val READ_TIMEOUT_SECONDS = 15L
    const val CALL_TIMEOUT_SECONDS = 22L

    val DEFAULT_BASE_URL: String
        get() = BuildConfig.DEFAULT_API_BASE_URL.trimEnd('/')

    @Volatile
    private var overrideBaseUrl: String? = null

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_CUSTOM_BASE_URL, null)
        overrideBaseUrl = saved?.takeIf { isValidBaseUrl(it) }?.let { normalizeBaseUrl(it) }
    }

    fun getActiveBaseUrl(context: Context? = null): String {
        if (overrideBaseUrl == null && context != null) {
            init(context)
        }
        return overrideBaseUrl ?: DEFAULT_BASE_URL
    }

    fun isUsingArchiveCatalog(baseUrl: String = getActiveBaseUrl()): Boolean {
        val lower = baseUrl.lowercase(Locale.US)
        return lower.contains("archive.org")
    }

    fun setCustomBaseUrl(context: Context, rawUrl: String): Result<String> {
        val normalized = normalizeBaseUrl(rawUrl)
        if (!isValidBaseUrl(normalized)) {
            return Result.failure(
                IllegalArgumentException("Enter a valid http:// or https:// server URL (e.g., https://archive.org)")
            )
        }
        overrideBaseUrl = normalized
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_BASE_URL, normalized)
            .apply()
        return Result.success(normalized)
    }

    fun resetToDefault(context: Context): String {
        overrideBaseUrl = null
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CUSTOM_BASE_URL)
            .apply()
        return DEFAULT_BASE_URL
    }

    internal fun setBaseUrlForTesting(url: String?) {
        overrideBaseUrl = url?.let { normalizeBaseUrl(it) }
    }

    fun normalizeBaseUrl(rawUrl: String): String {
        return rawUrl.trim().trimEnd('/')
    }

    fun isValidBaseUrl(url: String): Boolean {
        val clean = normalizeBaseUrl(url)
        if (clean.isEmpty()) return false
        return try {
            val uri = URI(clean)
            val scheme = uri.scheme?.lowercase(Locale.US)
            (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun isNetworkAvailable(context: Context?): Boolean {
        if (context == null) return true
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }
}
