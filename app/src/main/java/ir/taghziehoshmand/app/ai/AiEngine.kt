package ir.taghziehoshmand.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
 * موتور هوش مصنوعی تغذیه:
 * - اگر کاربر سرویس هوش مصنوعی را در تنظیمات پیکربندی کند، پاسخ‌ها از آن سرویس می‌آید.
 * - در غیر این صورت (و یا در خطا)، «متخصص آفلاین» فارسی پاسخ می‌دهد تا اپ همیشه کار کند.
 */
class AiEngine(
    private val context: Context,
    private val prefs: UserPrefs,
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

    /** ارسال پیام کاربر و دریافت پاسخ (آنلاین یا آفلاین) */
    suspend fun sendUserMessage(text: String, dayId: Long): String {
        chatDao.insert(ChatMsg(role = 0, text = text))
        val settings = prefs.settingsFlow.first()
        val snap = snapshot(dayId)
        val reply: String = if (settings.ai.ready) {
            try { chatViaApi(settings.ai, text, snap) }
            catch (e: Exception) {
                offlineReply(text, snap) + "\n\n⚠️ سرویس هوش مصنوعی در دسترس نبود، پاسخ آفلاین داده شد (${e.message?.take(80)})."
            }
        } else offlineReply(text, snap)
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

    /** تحلیل عکس غذا و تخمین کالری */
    suspend fun analyzeFoodImage(imageBytes: ByteArray, dayId: Long): String {
        val settings = prefs.settingsFlow.first()
        chatDao.insert(ChatMsg(role = 0, text = "[تحلیل عکس غذا] 📸"))
        if (!settings.ai.ready) {
            val snap = snapshot(dayId)
            val expert = OfflineExpert { q -> foodDao.search(q).first() }
            val reply = "تحلیل عکس به سرویس هوش مصنوعی نیاز دارد. در «تنظیمات → هوش مصنوعی» آدرس سرویس، کلید و مدل را وارد کنید (مثل OpenAI یا OpenRouter).\n\nتا آن زمان می‌توانید نام غذا را در جستجو بزنید و مقدارش را دستی ثبت کنید؛ اگر لازم شد پرسش‌تان را مستقیم از من بپرسید.\n\n" + expert.reply("وضعیت امروز", snap)
            chatDao.insert(ChatMsg(role = 1, text = reply))
            return reply
        }
        return try {
            val bmp = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } ?: throw IllegalStateException("تصویر نامعتبر")
            val scaled = withContext(Dispatchers.IO) { scaleForUpload(bmp) }
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
            reply
        } catch (e: Exception) {
            val reply = "تحلیل عکس ناموفق بود: ${e.message?.take(120)}\nنام غذا را دستی جستجو و ثبت کنید."
            chatDao.insert(ChatMsg(role = 1, text = reply))
            reply
        }
    }

    private fun scaleForUpload(bmp: Bitmap): Bitmap {
        val max = 1024
        val w = bmp.width
        val h = bmp.height
        if (w <= max && h <= max) return bmp
        val scale = if (w > h) max.toFloat() / w else max.toFloat() / h
        return Bitmap.createScaledBitmap(bmp, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
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
