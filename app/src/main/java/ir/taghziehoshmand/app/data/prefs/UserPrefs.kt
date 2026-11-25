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

    /**
     * پیکربندی مؤثر: اگر کاربر سرویس خودش را تنظیم نکرده باشد، سرویس ابری
     * پیش‌فرضِ رایگان (بدون نیاز به کلید واقعی) استفاده می‌شود تا حالت آنلاین
     * از همان ابتدا کار کند. هنگام قطع اینترنت، خودکار موتور آفلاین جایگزین می‌شود.
     */
    fun effective(): AiConfig = if (ready) this else AiConfig(
        baseUrl = baseUrl.ifBlank { DEFAULT_BASE_URL },
        apiKey = apiKey.ifBlank { DEFAULT_KEY },
        model = model.ifBlank { DEFAULT_MODEL }
    )

    /** آیا کاربر سرویس اختصاصی خودش را تنظیم کرده؟ */
    val isCustom: Boolean get() = ready

    companion object {
        /** سرویس ابری پیش‌فرض رایگان و سازگار با OpenAI (پاسخ فارسی) */
        const val DEFAULT_BASE_URL = "https://text.pollinations.ai/v1"
        const val DEFAULT_KEY = "pollinations" // سرویس بدون کلید است؛ مقدار فقط برای اعتبارسنجی فرم
        const val DEFAULT_MODEL = "openai"
        val DEFAULT = AiConfig(DEFAULT_BASE_URL, DEFAULT_KEY, DEFAULT_MODEL)
    }
}

@Serializable
data class Settings(
    val darkMode: ThemeMode = ThemeMode.SYSTEM,
    val ai: AiConfig = AiConfig(),
    val autoWaterReminder: Boolean = true,
    /** سوئیچ خودکار: true = با اینترنت پاسخ ابری، بدون اینترنت پاسخ آفلاین */
    val aiOnline: Boolean = true,
    /** حافظه بلندمدت چت‌بات — نکات کلیدی که AI درباره کاربر یاد گرفته (نسخه ۱.۵) */
    val aiMemory: String = "",
    /** حالت رمضان — پنجره غذایی افطار تا سحر و توزیع آب (نسخه ۱.۵) */
    val ramadanMode: Boolean = false,
    /** ساعت سحر به‌صورت دقیقه از نیمه‌شب (پیش‌فرض ۰۴:۳۰) */
    val suhoorMinute: Int = 270,
    /** ساعت افطار به‌صورت دقیقه از نیمه‌شب (پیش‌فرض ۱۸:۱۵) */
    val iftarMinute: Int = 1095,
    /** رنگ پویا (Material You) در اندروید ۱۲+ — غیرپیش‌فرض؛ تم سازمانی سرمه‌ای/فیروزه‌ای حفظ می‌شود */
    val dynamicColor: Boolean = false
) {
    /** برچسب زمان سحر مثل ۰۴:۳۰ */
    fun suhoorLabel(): String = minuteLabel(suhoorMinute)

    /** برچسب زمان افطار مثل ۱۸:۱۵ */
    fun iftarLabel(): String = minuteLabel(iftarMinute)

    private fun minuteLabel(total: Int): String {
        val h = total / 60
        val m = total % 60
        return ir.taghziehoshmand.app.core.Fa.num(String.format(java.util.Locale.US, "%02d:%02d", h, m))
    }
}

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
