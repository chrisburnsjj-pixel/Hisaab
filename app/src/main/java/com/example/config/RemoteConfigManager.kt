package com.example.config

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object RemoteConfigManager {
    private const val TAG = "RemoteConfigManager"

    // Parameter Keys
    const val KEY_HOME_CTA_VARIANT = "home_cta_variant"
    const val KEY_ONBOARDING_ENABLED = "onboarding_enabled"
    const val KEY_ADVISOR_PROMPT_POSITION = "advisor_prompt_position"

    // Default Values
    const val DEFAULT_HOME_CTA_VARIANT = "voice_first"
    const val DEFAULT_ONBOARDING_ENABLED = true
    const val DEFAULT_ADVISOR_PROMPT_POSITION = "dashboard"

    private var remoteConfig: FirebaseRemoteConfig? = null

    // State Flows consumed by UI
    private val _homeCtaVariant = MutableStateFlow(DEFAULT_HOME_CTA_VARIANT)
    val homeCtaVariant: StateFlow<String> = _homeCtaVariant.asStateFlow()

    private val _onboardingEnabled = MutableStateFlow(DEFAULT_ONBOARDING_ENABLED)
    val onboardingEnabled: StateFlow<Boolean> = _onboardingEnabled.asStateFlow()

    private val _advisorPromptPosition = MutableStateFlow(DEFAULT_ADVISOR_PROMPT_POSITION)
    val advisorPromptPosition: StateFlow<String> = _advisorPromptPosition.asStateFlow()

    private val _fetchStatus = MutableStateFlow("Uninitialized")
    val fetchStatus: StateFlow<String> = _fetchStatus.asStateFlow()

    private val _lastFetchTimestamp = MutableStateFlow(0L)
    val lastFetchTimestamp: StateFlow<Long> = _lastFetchTimestamp.asStateFlow()

    // Debug overrides (set from /debug screen)
    private var overrideHomeCtaVariant: String? = null
    private var overrideOnboardingEnabled: Boolean? = null
    private var overrideAdvisorPromptPosition: String? = null

    /**
     * Initialize Firebase Remote Config with default parameters, sensible caching,
     * and immediate asynchronous fetch-and-activate.
     */
    fun init(context: Context, coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        coroutineScope.launch {
            try {
                val rc = Firebase.remoteConfig
                remoteConfig = rc

                // Set sensible caching: 0 seconds in debug builds for instant testing, 1 hour in release
                val configSettings = remoteConfigSettings {
                    minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0L else 3600L
                    fetchTimeoutInSeconds = 10L
                }
                rc.setConfigSettingsAsync(configSettings)

                // Define defaults
                val defaults: Map<String, Any> = mapOf(
                    KEY_HOME_CTA_VARIANT to DEFAULT_HOME_CTA_VARIANT,
                    KEY_ONBOARDING_ENABLED to DEFAULT_ONBOARDING_ENABLED,
                    KEY_ADVISOR_PROMPT_POSITION to DEFAULT_ADVISOR_PROMPT_POSITION
                )
                rc.setDefaultsAsync(defaults)

                applyConfigValues(rc)

                // Attempt to fetch and activate
                fetchAndActivate()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Remote Config init failed, using resilient code defaults: ${e.message}")
                _fetchStatus.value = "Fallback to Code Defaults"
                applyFallbackDefaults()
            }
        }
    }

    /**
     * Fetches and activates latest Remote Config values. Safe against network drops or service errors.
     */
    suspend fun fetchAndActivate(): Boolean {
        val rc = remoteConfig ?: return false
        return try {
            val updated = rc.fetchAndActivate().await()
            _lastFetchTimestamp.value = System.currentTimeMillis()
            _fetchStatus.value = if (updated) "Fetched & Activated New Config" else "Active (Cached / Up to date)"
            applyConfigValues(rc)
            Log.d(TAG, "Remote Config activated successfully. Status: ${_fetchStatus.value}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Remote Config fetch failed (${e.message}). Falling back to cached/default values.")
            _fetchStatus.value = "Fetch Failed (Using Defaults: ${e.localizedMessage ?: "Network error"})"
            applyConfigValues(rc)
            false
        }
    }

    private fun applyConfigValues(rc: FirebaseRemoteConfig) {
        val rawHomeCta = rc.getString(KEY_HOME_CTA_VARIANT)
        val rawOnboarding = rc.getBoolean(KEY_ONBOARDING_ENABLED)
        val rawAdvisorPos = rc.getString(KEY_ADVISOR_PROMPT_POSITION)

        _homeCtaVariant.value = overrideHomeCtaVariant ?: if (rawHomeCta in listOf("voice_first", "manual_first")) rawHomeCta else DEFAULT_HOME_CTA_VARIANT
        _onboardingEnabled.value = overrideOnboardingEnabled ?: rawOnboarding
        _advisorPromptPosition.value = overrideAdvisorPromptPosition ?: if (rawAdvisorPos in listOf("dashboard", "budget_page")) rawAdvisorPos else DEFAULT_ADVISOR_PROMPT_POSITION
    }

    private fun applyFallbackDefaults() {
        _homeCtaVariant.value = overrideHomeCtaVariant ?: DEFAULT_HOME_CTA_VARIANT
        _onboardingEnabled.value = overrideOnboardingEnabled ?: DEFAULT_ONBOARDING_ENABLED
        _advisorPromptPosition.value = overrideAdvisorPromptPosition ?: DEFAULT_ADVISOR_PROMPT_POSITION
    }

    // --- Developer Debug Screen Overrides ---

    fun setOverrideHomeCtaVariant(variant: String?) {
        overrideHomeCtaVariant = variant
        remoteConfig?.let { applyConfigValues(it) } ?: applyFallbackDefaults()
    }

    fun setOverrideOnboardingEnabled(enabled: Boolean?) {
        overrideOnboardingEnabled = enabled
        remoteConfig?.let { applyConfigValues(it) } ?: applyFallbackDefaults()
    }

    fun setOverrideAdvisorPromptPosition(position: String?) {
        overrideAdvisorPromptPosition = position
        remoteConfig?.let { applyConfigValues(it) } ?: applyFallbackDefaults()
    }

    fun resetAllOverrides() {
        overrideHomeCtaVariant = null
        overrideOnboardingEnabled = null
        overrideAdvisorPromptPosition = null
        remoteConfig?.let { applyConfigValues(it) } ?: applyFallbackDefaults()
    }

    fun getActiveOverrides(): Map<String, Any?> {
        return mapOf(
            KEY_HOME_CTA_VARIANT to overrideHomeCtaVariant,
            KEY_ONBOARDING_ENABLED to overrideOnboardingEnabled,
            KEY_ADVISOR_PROMPT_POSITION to overrideAdvisorPromptPosition
        )
    }
}
