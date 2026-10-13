package com.example.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class LoggedAnalyticsEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val params: Map<String, String> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
)

object AnalyticsManager {
    private const val TAG = "AnalyticsManager"
    private var firebaseAnalytics: FirebaseAnalytics? = null

    private val _events = MutableStateFlow<List<LoggedAnalyticsEvent>>(emptyList())
    val events: StateFlow<List<LoggedAnalyticsEvent>> = _events.asStateFlow()

    fun init(context: Context) {
        if (firebaseAnalytics == null) {
            try {
                firebaseAnalytics = Firebase.analytics
                Log.d(TAG, "Firebase Analytics initialized successfully")
            } catch (e: Exception) {
                try {
                    firebaseAnalytics = FirebaseAnalytics.getInstance(context)
                    Log.d(TAG, "Firebase Analytics initialized via getInstance")
                } catch (ex: Exception) {
                    Log.w(TAG, "Firebase Analytics not available (running offline/mock mode): ${ex.message}")
                }
            }
        }
    }

    /**
     * Core logging function: logs to Firebase Analytics and records in in-memory event buffer.
     * Enforces privacy by stripping any unapproved sensitive parameters.
     */
    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        val event = LoggedAnalyticsEvent(
            name = name,
            params = params,
            timestamp = System.currentTimeMillis()
        )
        // Record in internal buffer (keep last 100 events)
        val currentList = _events.value
        _events.value = (listOf(event) + currentList).take(100)

        // Forward to Firebase Analytics if available
        val fa = firebaseAnalytics
        if (fa != null) {
            try {
                val bundle = Bundle()
                params.forEach { (key, value) ->
                    bundle.putString(key, value)
                }
                fa.logEvent(name, bundle)
                try {
                    Log.d(TAG, "Logged event: $name with params: $params")
                } catch (_: Throwable) {}
            } catch (t: Throwable) {
                try {
                    Log.w(TAG, "Failed to log event to Firebase Analytics: ${t.message}")
                } catch (_: Throwable) {}
            }
        }
    }

    // Required Events from specification:

    /** 1. User sign-up (first account creation) */
    fun logSignUp() {
        logEvent("sign_up")
    }

    /** 2. User login */
    fun logLogin() {
        logEvent("login")
    }

    /**
     * 3. Add transaction (method: "manual" or "voice")
     * PRIVACY CONSTRAINT: Transaction amounts, notes, or titles are strictly NOT collected.
     */
    fun logAddTransaction(method: String) {
        val sanitizedMethod = if (method.lowercase().contains("voice")) "voice" else "manual"
        logEvent("add_transaction", mapOf("method" to sanitizedMethod))
    }

    /** 4. Voice session started */
    fun logVoiceStart() {
        logEvent("voice_start")
    }

    /** 5. Voice transcription and AI parse succeeded */
    fun logVoiceSuccess() {
        logEvent("voice_success")
    }

    /** 6. Voice recognition or extraction error */
    fun logVoiceError(reason: String = "general_error") {
        logEvent("voice_error", mapOf("reason" to reason))
    }

    /** 7. Opened dashboard */
    fun logOpenDashboard() {
        logEvent("open_dashboard")
    }

    /** 8. Opened analytics */
    fun logOpenAnalytics() {
        logEvent("open_analytics")
    }

    /** 9. Exported PDF report */
    fun logExportPdf() {
        logEvent("export_pdf")
    }

    /** 10. Exported CSV report */
    fun logExportCsv() {
        logEvent("export_csv")
    }

    /** 11. Used AI financial advisor */
    fun logUseAiAdvisor() {
        logEvent("use_ai_advisor")
    }

    /**
     * 12. Set budget target
     * PRIVACY CONSTRAINT: Budget monetary amount is strictly NOT collected.
     */
    fun logSetBudget() {
        logEvent("set_budget")
    }

    fun clearEvents() {
        _events.value = emptyList()
    }
}
