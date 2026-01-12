package com.example.debloater

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {

    private const val PREFS_NAME = "debloater_prefs"

    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    private const val KEY_WHAT_IS_DEBLOATER = "what_is_debloater"
    private const val KEY_MISUSE_WARNING = "misuse_warning"
    private const val KEY_SHIZUKU_INFO = "shizuku_info"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ✅ Onboarding
    fun isOnboardingComplete(): Boolean =
        prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)

    fun setOnboardingComplete(complete: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, complete).apply()
    }

    // ✅ What is Debloater dialog
    fun isWhatIsDebloaterShown(): Boolean =
        prefs.getBoolean(KEY_WHAT_IS_DEBLOATER, false)

    fun setWhatIsDebloaterShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_WHAT_IS_DEBLOATER, shown).apply()
    }

    // ✅ Misuse warning dialog
    fun isMisuseWarningShown(): Boolean =
        prefs.getBoolean(KEY_MISUSE_WARNING, false)

    fun setMisuseWarningShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_MISUSE_WARNING, shown).apply()
    }

    // ✅ Shizuku info dialog
    fun isShizukuInfoShown(): Boolean =
        prefs.getBoolean(KEY_SHIZUKU_INFO, false)

    fun setShizukuInfoShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_SHIZUKU_INFO, shown).apply()
    }
}
