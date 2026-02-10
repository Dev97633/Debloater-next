package com.dev.debloater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class SafetyLevel {
    SAFE,
    CAUTION,
    RISKY;

    val label: String
        get() = name.lowercase()

    companion object {
        fun fromString(value: String?): SafetyLevel {
            return when (value?.lowercase()) {
                "safe" -> SAFE
                "caution" -> CAUTION
                "risky" -> RISKY
                else -> CAUTION
            }
        }
    }
}

object SafetyClassifier {
    private val defaultSafetyList = mapOf(
        "com.android.systemui" to SafetyLevel.RISKY,
        "com.android.settings" to SafetyLevel.RISKY,
        "com.android.phone" to SafetyLevel.RISKY,
        "com.android.providers.settings" to SafetyLevel.RISKY,
        "com.android.providers.media" to SafetyLevel.RISKY,
        "com.android.packageinstaller" to SafetyLevel.RISKY,
        "com.google.android.gms" to SafetyLevel.CAUTION,
        "com.google.android.gsf" to SafetyLevel.CAUTION,
        "com.android.vending" to SafetyLevel.CAUTION,
        "com.google.android.youtube" to SafetyLevel.SAFE,
        "com.google.android.apps.photos" to SafetyLevel.SAFE,
        "com.facebook.katana" to SafetyLevel.SAFE,
        "com.netflix.mediaclient" to SafetyLevel.SAFE
    )

    @Volatile
    private var safetyList: Map<String, SafetyLevel> = defaultSafetyList

    fun classify(packageName: String): SafetyLevel {
        return safetyList[packageName] ?: SafetyLevel.CAUTION
    }

    fun resetToDefault() {
        safetyList = defaultSafetyList
    }

    suspend fun updateFromRemote(url: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }

            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val updated = mutableMapOf<String, SafetyLevel>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val packageName = keys.next()
                    updated[packageName] = SafetyLevel.fromString(json.optString(packageName))
                }
                if (updated.isNotEmpty()) {
                    safetyList = updated.toMap()
                }
                updated.size
            }
        }
    }
}
