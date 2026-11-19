package ir.taghziehoshmand.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import ir.taghziehoshmand.app.data.db.ChatDao
import ir.taghziehoshmand.app.data.db.ExerciseDao
import ir.taghziehoshmand.app.data.db.FoodDao
import ir.taghziehoshmand.app.data.db.MealDao
import ir.taghziehoshmand.app.data.db.WeightDao
import ir.taghziehoshmand.app.data.model.ChatMsg
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.prefs.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * موتور هوش مصنوعی تغذیه — دومُد با سوئیچ خودکار:
 * - اینترنت وصل + حالت آنلاین فعال → پاسخ از سرویس ابری (پیش‌فرض رایگان یا سرویس کاربر).
 * - اینترنت قطع → بلافاصله (بدون انتظارِ تایم‌اوت) «متخصص آفلاین» فارسی پاسخ می‌دهد.
 * - خطای سرویس ابری در میانه کار → فال‌بک خودکار به آفلاین.
 */
class AiEngine(
    private val context: Context,
    private val prefs: UserPrefs,
    private val connectivity: ir.taghziehoshmand.app.core.ConnectivityObserver,
    private val chatDao: ChatDao,
    private val mealDao: MealDao,
    private val weightDao: WeightDao,
    private val exerciseDao: ExerciseDao,
    private val foodDao: FoodDao,
    private val waterDao: ir.taghziehoshmand.app.data.db.WaterDao,
    private val stepDao: ir.taghziehoshmand.app.data.db.StepDao
) {

    /** خلاصه وضعیت امروز برای پاسخ‌دهی */
    suspend fun snapshot(dayId: Long): UserSnapshot {
        val profile = prefs.profileFlow.first()
        val kcal = mealDao.kcalSum(dayId).first()
        val protein = mealDao.proteinSum(dayId).first()
        val carb = mealDao.carbSum(dayId).first()
        val fat = mealDao.fatSum(dayId).first()
        val waterMl = waterDao.totalMl(dayId).first()
        val waterGoal = profile?.waterGoalMl ?: 2500
        val burned = exerciseDao.kcalSum(dayId).first()
        val steps = stepDao.forDay(dayId).first() ?: 0
        val target = profile?.targets()?.targetKcal ?: 2000.0
        val trend = weightTrendText()
        return UserSnapshot(
            profile = profile,
            kcalToday = kcal, proteinToday = protein, carbToday = carb, fatToday = fat,
            targetKcal = target,
            waterMl = waterMl, waterGoalMl = waterGoal,
            exerciseKcal = burned, steps = steps,
            weightTrend = trend,
            remainingKcal = target - kcal
        )
    }

    private suspend fun weightTrendText(): String? {
        val all = weightDao.since(java.time.LocalDate.now().minusDays(60).toEpochDay()).first()
        if (all.size < 2) return all.lastOrNull()?.let { "${ir.taghziehoshmand.app.core.Fa.weight(it.weightKg)} کیلوگرم" }
        val first = all.first().weightKg
        val last = all.last().weightKg
        val diff = last - first
        val dir = if (abs(diff) < 0.15) "ثابت" else if (diff < 0) "کاهش ${ir.taghziehoshmand.app.core.Fa.weight(abs(diff))} کیلو" else "افزایش ${ir.taghziehoshmand.app.core.Fa.weight(diff)} کیلو"
        return "${ir.taghziehoshmand.app.core.Fa.weight(last)} کیلوگرم، $dir در ${all.size} سنجش اخیر"
    }

    /** ارسال پیام کاربر و دریافت پاسخ — سوئیچ خودکار آنلاین/آفلاین بر اساس وضعیت اینترنت */
    suspend fun sendUserMessage(text: String, dayId: Long): String {
        chatDao.insert(ChatMsg(role = 0, text = text))
        val settings = prefs.settingsFlow.first()
        val snap = snapshot(dayId)
        val netUp = connectivity.now()
        val reply: String = when {
            // کاربر حالت آفلاین را در تنظیمات انتخاب کرده
            !settings.aiOnline ->
                offlineReply(text, snap) + "\n\n⚙️ حالت آفلاین در تنظیمات انتخاب شده است؛ برای پاسخ‌های ابری آن را فعال کنید."

            // اینترنت قطع است → پاسخ فوری آفلاین، بدون انتظار برای تایم‌اوت شبکه
            !netUp ->
                offlineReply(text, snap) + "\n\n📶 اینترنت در دسترس نیست؛ پاسخ از پایگاه دانش داخلی داده شد. با وصل شدن اینترنت، پاسخ‌های ابری فعال می‌شوند."

            // آنلاین: سرویس ابری (کاربر یا پیش‌فرض رایگان) — در خطا فال‌بک آفلاین
            else -> try {
                chatViaApi(settings.ai.effective(), text, snap)
            } catch (e: Exception) {
                offlineReply(text, snap) + "\n\n⚠️ سرویس ابری موقتاً پاسخ نداد (${e.message?.take(60)})؛ پاسخ آفلاین داده شد."
            }
        }
        chatDao.insert(ChatMsg(role = 1, text = reply))
        return reply
    }

    private suspend fun chatViaApi(cfg: ir.taghziehoshmand.app.data.prefs.AiConfig, text: String, snap: UserSnapshot): String {
        val history = chatDao.all().first().takeLast(8).map {
            AiMsg(if (it.role == 0) "user" else "assistant", it.text)
        }
        val context = Prompts.userContext(OfflineContext.summary(snap))
        val messages = mutableListOf(
            AiMsg("system", Prompts.CHAT_SYSTEM),
            AiMsg("system", context)
        )
        messages.addAll(history)
        messages.add(AiMsg("user", text))
        return AiHttp.chat(cfg.baseUrl, cfg.apiKey, cfg.model, messages)
    }

    private suspend fun offlineReply(text: String, snap: UserSnapshot): String {
        val expert = OfflineExpert { q -> foodDao.search(q).first() }
        return expert.reply(text, snap)
    }

    /**
     * تحلیل عکس غذا — سه‌لایه:
     * ۱) سرویس بینایی اختصاصی کاربر (اگر تنظیم شده و آنلاین) → تحلیل کامل با مدل دیداری
     * ۲) طبقه‌بندی روی دستگاه با ML Kit (همیشه کار می‌کند، حتی آفلاین) → لیبل مشخص → پایگاه داده
     * ۳) لیبل عمومی → غنی‌سازی با مدل متنی ابری یا پیشنهاد از دسته متناظر پایگاه داده
     */
    suspend fun analyzeFoodImage(imageBytes: ByteArray, dayId: Long): String {
        val settings = prefs.settingsFlow.first()
        chatDao.insert(ChatMsg(role = 0, text = "[تحلیل عکس غذا] 📸"))
        val snap = snapshot(dayId)
        val netUp = connectivity.now()

        // لایه ۱: مدل بینایی اختصاصی کاربر
        if (settings.ai.ready && netUp) {
            try {
                val scaled = withContext(Dispatchers.IO) {
                    FoodVision.decodeScaled(imageBytes, 1024) ?: throw IllegalStateException("تصویر نامعتبر")
                }
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                val cfg = settings.ai
                val reply = AiHttp.chat(
                    cfg.baseUrl, cfg.apiKey, cfg.model,
                    listOf(
                        AiMsg("system", Prompts.CHAT_SYSTEM),
                        AiMsg("user", listOf(Prompts.IMAGE_ANALYSIS, AiHttp.ImagePart(dataUrl)))
                    )
                )
                chatDao.insert(ChatMsg(role = 1, text = reply))
                return reply
            } catch (e: Exception) {
                // خطای سرویس دیداری → ادامه به لایه روی دستگاه
            }
        }

        // لایه ۲ و ۳: تحلیل روی دستگاه (ML Kit) + غنی‌سازی متنی اختیاری
        val bitmap = withContext(Dispatchers.IO) { FoodVision.decodeScaled(imageBytes) }
        val reply = if (bitmap == null) {
            "تصویر نامعتبر است؛ عکس دیگری بگیرید یا نام غذا را دستی جستجو و ثبت کنید."
        } else {
            val enrich: (suspend (String) -> String)? = if (netUp && settings.aiOnline) { prompt ->
                AiHttp.chat(
                    settings.ai.effective().baseUrl, settings.ai.effective().apiKey, settings.ai.effective().model,
                    listOf(AiMsg("system", Prompts.CHAT_SYSTEM), AiMsg("user", prompt)),
                    maxTokens = 500
                )
            } else null
            FoodVision.analyze(bitmap, foodDao, snap, enrich)
        }
        chatDao.insert(ChatMsg(role = 1, text = reply))
        return reply
    }

    /** پیشنهاد جایگزین کم‌کالری‌تر از پایگاه داده */
    suspend fun lowerCalorieAlternatives(food: Food, limit: Int = 5): List<Food> {
        val all = foodDao.pool()
        val maxKcal = food.kcal100 * 0.75
        return all.filter {
            it.id != food.id && it.kcal100 < maxKcal && it.kcal100 > 20 &&
                (it.category == food.category || similarCategory(it.category, food.category))
        }.sortedBy { abs(it.kcal100 - food.kcal100 * 0.55) }.take(limit)
    }

    private fun similarCategory(a: String, b: String): Boolean = when (setOf(a, b)) {
        setOf("غذاهای ایرانی", "سوپ و خورش"), setOf("غذاهای ایرانی", "غلات و نشاسته"),
        setOf("گوشت و پروتئین", "غذاهای دریایی"), setOf("فست‌فود", "غذاهای بین‌المللی"),
        setOf("میوه‌ها", "شیرینی و تنقلات"), setOf("لبنیات", "نوشیدنی‌ها") -> true
        else -> a == b
    }
}

/** خلاصه متنی وضعیت کاربر برای پرامپت */
object OfflineContext {
    fun summary(s: UserSnapshot): String {
        val p = s.profile
        return listOfNotNull(
            p?.let { "نام: ${it.name} | جنسیت: ${if (it.gender == ir.taghziehoshmand.app.core.Gender.MALE) "مرد" else "زن"} | سن: ${it.age} | قد: ${it.heightCm} سانتی‌متر | وزن: ${it.weightKg} کیلوگرم | هدف: ${it.goal.label} (${it.goalWeightKg} کیلوگرم) | سطح فعالیت: ${it.activity.label}" },
            "کالری امروز: ${s.kcalToday.toInt()} از هدف ${s.targetKcal.toInt()} کیلوکالری",
            "ماکرو امروز: پروتئین ${s.proteinToday.toInt()} گرم، کربوهیدرات ${s.carbToday.toInt()} گرم، چربی ${s.fatToday.toInt()} گرم",
            "آب: ${s.waterMl} از ${s.waterGoalMl} میلی‌لیتر | ورزش: ${s.exerciseKcal.toInt()} کیلوکالری | قدم: ${s.steps}",
            s.weightTrend?.let { "روند وزن: $it" }
        ).joinToString("\n")
    }
}
