package com.example.dormia.data

import android.content.Context

data class SleepDiagnostics(
    val serviceRunning: Boolean = false,
    val subscriptionActive: Boolean = false,
    val lastEventType: String = "",
    val lastConfidence: Int? = null,
    val lastEventMs: Long? = null,
    val lastError: String = ""
)

object SleepDiagnosticsStore {
    private const val PREFS_NAME = "dormia_sleep_diagnostics"
    private const val KEY_SERVICE_RUNNING = "service_running"
    private const val KEY_SUBSCRIPTION_ACTIVE = "subscription_active"
    private const val KEY_LAST_EVENT_TYPE = "last_event_type"
    private const val KEY_LAST_CONFIDENCE = "last_confidence"
    private const val KEY_LAST_EVENT_MS = "last_event_ms"
    private const val KEY_LAST_ERROR = "last_error"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setServiceRunning(context: Context, running: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_RUNNING, running).apply()
    }

    fun setSubscriptionActive(context: Context, active: Boolean) {
        prefs(context).edit().putBoolean(KEY_SUBSCRIPTION_ACTIVE, active).apply()
    }

    fun setLastEvent(context: Context, type: String, confidence: Int? = null) {
        prefs(context).edit()
            .putString(KEY_LAST_EVENT_TYPE, type)
            .putLong(KEY_LAST_EVENT_MS, System.currentTimeMillis())
            .apply()
        if (confidence != null) {
            prefs(context).edit().putInt(KEY_LAST_CONFIDENCE, confidence).apply()
        }
    }

    fun setLastError(context: Context, error: String) {
        prefs(context).edit().putString(KEY_LAST_ERROR, error).apply()
    }

    fun clearError(context: Context) {
        prefs(context).edit().putString(KEY_LAST_ERROR, "").apply()
    }

    fun snapshot(context: Context): SleepDiagnostics {
        val p = prefs(context)
        val confidence = if (p.contains(KEY_LAST_CONFIDENCE)) p.getInt(KEY_LAST_CONFIDENCE, 0) else null
        val eventMs = if (p.contains(KEY_LAST_EVENT_MS)) p.getLong(KEY_LAST_EVENT_MS, 0L) else null
        return SleepDiagnostics(
            serviceRunning = p.getBoolean(KEY_SERVICE_RUNNING, false),
            subscriptionActive = p.getBoolean(KEY_SUBSCRIPTION_ACTIVE, false),
            lastEventType = p.getString(KEY_LAST_EVENT_TYPE, "").orEmpty(),
            lastConfidence = confidence,
            lastEventMs = eventMs,
            lastError = p.getString(KEY_LAST_ERROR, "").orEmpty()
        )
    }
}
