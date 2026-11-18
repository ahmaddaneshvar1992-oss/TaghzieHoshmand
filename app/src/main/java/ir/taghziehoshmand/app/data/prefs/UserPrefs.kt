package ir.taghziehoshmand.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.taghziehoshmand.app.core.ActivityLevel
import ir.taghziehoshmand.app.core.Gender
import ir.taghziehoshmand.app.core.Goal
import ir.taghziehoshmand.app.core.HealthCalc
import ir.taghziehoshmand.app.core.HealthResults
import ir.taghziehoshmand.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "taghzie_prefs")

/** پروفایل بدنی کاربر */
@Serializable
data class UserProfile(
    val name: String = "",
    val gender: Gender = Gender.MALE,
    val age: Int = 0,
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0,
    val goalWeightKg: Double = 0.0,
    val activity: ActivityLevel = ActivityLevel.SEDENTARY,
    val goal: Goal = Goal.KEEP,
    val targetKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbG: Double = 0.0,
    val fatG: Double = 0.0,
    val waterGoalMl: Int = 2500,
    val stepGoal: Int = 10000,
    val createdAt: Long = 0
) {
    val isValid: Boolean
        get() = heightCm > 0 && weightKg > 0 && age > 0

    fun results(): HealthResults =
        HealthCalc.calculate(weightKg, heightCm, age, gender, activity, goal)

    fun targets(): HealthResults = if (targetKcal > 0)
        HealthResults(
            bmi = HealthCalc.bmi(weightKg, heightCm),
            bmiLabel = HealthCalc.bmiLabel(HealthCalc.bmi(weightKg, heightCm)),
            bmr = results().bmr,
            tdee = results().tdee,
            targetKcal = targetKcal,
            proteinG = proteinG, carbG = carbG, fatG = fatG,
            waterGoalMl = waterGoalMl,
            idealWeight = results().idealWeight
        ) else results()
}

/** تنظیمات برنامه و هوش مصنوعی */
@Serializable
data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = ""
) {
    val ready: Boolean get() = baseUrl.startsWith("http") && apiKey.isNotBlank() && model.isNotBlank()
}

@Serializable
data class Settings(
    val darkMode: ThemeMode = ThemeMode.SYSTEM,
    val ai: AiConfig = AiConfig(),
    val autoWaterReminder: Boolean = true
)

class UserPrefs(private val context: Context) {

    private object K {
        val PROFILE = stringPreferencesKey("profile_json")
        val SETTINGS = stringPreferencesKey("settings_json")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val PIN = stringPreferencesKey("pin")
        val HC_SYNC = booleanPreferencesKey("hc_sync_enabled")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val profileFlow: Flow<UserProfile?> = context.dataStore.data.map { p ->
        p[K.PROFILE]?.let { runCatching { json.decodeFromString<UserProfile>(it) }.getOrNull() }
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data.map { p ->
        p[K.SETTINGS]?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
    }

    val onboardedFlow: Flow<Boolean> = context.dataStore.data.map { p -> p[K.ONBOARDED] ?: false }

    val pinFlow: Flow<String?> = context.dataStore.data.map { p -> p[K.PIN] }

    val hcSyncFlow: Flow<Boolean> = context.dataStore.data.map { p -> p[K.HC_SYNC] ?: false }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { it[K.PROFILE] = json.encodeToString(profile) }
    }

    suspend fun saveSettings(settings: Settings) {
        context.dataStore.edit { it[K.SETTINGS] = json.encodeToString(settings) }
    }

    suspend fun setOnboarded(v: Boolean) {
        context.dataStore.edit { it[K.ONBOARDED] = v }
    }

    suspend fun setPin(pin: String?) {
        context.dataStore.edit {
            if (pin.isNullOrBlank()) it.remove(K.PIN) else it[K.PIN] = pin
        }
    }

    suspend fun setHcSync(v: Boolean) {
        context.dataStore.edit { it[K.HC_SYNC] = v }
    }

    /** ساخت پروفایل از داده‌های آنبوردینگ با محاسبه کامل اهداف */
    fun buildProfile(
        name: String, gender: Gender, age: Int, heightCm: Double, weightKg: Double,
        goalWeightKg: Double, activity: ActivityLevel, goal: Goal
    ): UserProfile {
        val r = HealthCalc.calculate(weightKg, heightCm, age, gender, activity, goal)
        return UserProfile(
            name = name.ifBlank { "کاربر" },
            gender = gender, age = age,
            heightCm = heightCm, weightKg = weightKg,
            goalWeightKg = if (goalWeightKg > 0) goalWeightKg else r.idealWeight,
            activity = activity, goal = goal,
            targetKcal = r.targetKcal,
            proteinG = r.proteinG, carbG = r.carbG, fatG = r.fatG,
            waterGoalMl = r.waterGoalMl,
            stepGoal = 10000,
            createdAt = System.currentTimeMillis()
        )
    }
}
