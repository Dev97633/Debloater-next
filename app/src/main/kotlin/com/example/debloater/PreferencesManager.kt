package com.example.debloater

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREFS_NAME = "debloater_prefs"
    private const val KEY_WHAT_IS_DEBLOATER_SHOWN = "what_is_debloater_shown"
    private const val KEY_MISUSE_WARNING_SHOWN = "misuse_warning_shown"
    private const val KEY_SHIZUKU_INFO_SHOWN = "shizuku_info_shown"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    fun isWhatIsDebloaterShown(): Boolean {
        return prefs.getBoolean(KEY_WHAT_IS_DEBLOATER_SHOWN, false)
    }
    
    fun setWhatIsDebloaterShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_WHAT_IS_DEBLOATER_SHOWN, shown).apply()
    }
    
    fun isMisuseWarningShown(): Boolean {
        return prefs.getBoolean(KEY_MISUSE_WARNING_SHOWN, false)
    }
    
    fun setMisuseWarningShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_MISUSE_WARNING_SHOWN, shown).apply()
    }
    
    fun isShizukuInfoShown(): Boolean {
        return prefs.getBoolean(KEY_SHIZUKU_INFO_SHOWN, false)
    }
    
    fun setShizukuInfoShown(shown: Boolean) {
        prefs.edit().putBoolean(KEY_SHIZUKU_INFO_SHOWN, shown).apply()
    }
}
