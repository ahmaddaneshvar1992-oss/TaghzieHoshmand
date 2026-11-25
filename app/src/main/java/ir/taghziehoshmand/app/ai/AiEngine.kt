package ir.taghziehoshmand.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.WeightModel
import ir.taghziehoshmand.app.data.db.ChatDao
import ir.taghziehoshmand.app.data.db.ExerciseDao
import ir.taghziehoshmand.app.data.db.FoodDao
import ir.taghziehoshmand.app.data.db.MealDao
import ir.taghziehoshmand.app.data.db.WeightDao
import ir.taghziehoshmand.app.data.model.ChatMsg
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.model.FoodSource
import ir.taghziehoshmand.app.data.model.MealEntry
import ir.taghziehoshmand.app.data.model.MealType
import ir.taghziehoshmand.app.data.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import kotlin.math.abs

/**
 * موتور هوش مصنوعی تغذیه — دومُد با سوئیچ خودکار:
 * - اینترنت وصل + حالت آنلاین فعال → پاسخ از سرویس ابری (پیش‌فرض رایگان یا سرویس کاربر).
 * - اینترنت قطع → بلافاصله (بدون انتظارِ تایم‌اوت) «متخصص آفلاین» فارسی پاسخ می‌دهد.
 * - خطای سرویس ابری در میانه کار → فال‌بک خودکار به آفلاین.
 *
 * جدید در نسخه ۱.۵:
 * - حافظه بلندمدت چت‌بات (System prompt داینامیک + استخراج نکات ماندگار)
 * - کانتکست موتور تطبیقی: TDEE واقعی، روند وزن و پیش‌بینی آینده در پاسخ‌های AI
 * - تحلیل چندغذایی یک عکس با ثبت جداگانهٔ هر غذا در «ثبت وعده»
 * - استخراج غذا از جمله صوتی (ثبت صوتی)
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
    private val stepDao: ir.taghziehoshmand.app.data.db.StepDao,
    private val scope: CoroutineScope
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
        if (all.size < 2) return all.lastOrNull()?.let { "${Fa.weight(it.weightKg)} کیلوگرم" }
        val first = all.first().weightKg
        val last = all.last().weightKg
        val diff = last - first
        val dir = if (abs(diff) < 0.15) "ثابت" else if (diff < 0) "کاهش ${Fa.weight(abs(diff))} کیلو" else "افزایش ${Fa.weight(diff)} کیلو"
        return "${Fa.weight(last)} کیلوگرم، $dir در ${all.size} سنجش اخیر"
    }

    /**
     * خلاصه «موتور تطبیقی» برای کانتکست AI (ایده ۷ و ۸):
     * روند واقعی وزن، TDEE مشاهده‌شده از داده و پیش‌بینی ۳۰ روز آینده.
     */
    suspend fun adaptiveInsightText(): String? {
        val profile = prefs.profileFlow.first() ?: return null
        val today = DayUtil.today()
        val from = today - 41
        val weights = weightDao.since(from).first().map { WeightModel.Sample(it.dayId, it.weightKg) }
        val trend = WeightModel.regression(weights) ?: return null
        val parts = mutableListOf("موتور تطبیقی وزن: " + WeightModel.trendText(trend))
        val totals = mealDao.dayTotals(from, today).first()
        if (totals.isNotEmpty()) {
            val span = (today - weights.minOf { it.dayId }).toInt().coerceAtLeast(10)
            val formulaTdee = profile.targets().tdee
            val adaptive = WeightModel.adaptiveTdee(totals.map { it.kcal }.average(), trend, formulaTdee, span)
            adaptive?.let {
                parts.add("TDEE فرمولی: ${Fa.kcal(it.formulaTdee)} | TDEE واقعی مشاهده‌شده ≈ ${Fa.kcal(it.observed)} | مقدار تطبیقی: ${Fa.kcal(it.tdee)} کیلوکالری (اعتماد ${Fa.num((it.confidence * 100).toInt())}٪)")
            }
        }
        WeightModel.forecast(weights)?.let { fc ->
            parts.add("پیش‌بینی وزن ۳۰ روز آینده: حدود ${Fa.weight(fc.predictedKg(30))} ± ${Fa.weight(fc.bandKg(30))} کیلوگرم")
            if (profile.goalWeightKg > 0) {
                fc.daysToGoal(profile.goalWeightKg, profile.goal == ir.taghziehoshmand.app.core.Goal.LOSE)?.let { d ->
                    parts.add("با ادامه این روند، حدود ${Fa.num(d)} روز دیگر به وزن هدف (${Fa.weight(profile.goalWeightKg)} کیلوگرم) می‌رسید")
                }
            }
        }
        return parts.joinToString("\n")
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
                offlineReply(text, snap) + "\n\n⚠️ ${e.message?.take(180) ?: "سرویس ابری موقتاً در دسترس نبود"}\nدر همین حال پاسخ از پایگاه دانش داخلی داده شد."
            }
        }
        chatDao.insert(ChatMsg(role = 1, text = reply))
        // استخراج حافظه بلندمدت — بی‌صدا و موازی؛ خطا تأثیری روی پاسخ ندارد
        if (netUp && settings.aiOnline) {
            scope.launch { runCatching { maybeExtractMemory(text, reply) } }
        }
        return reply
    }

    /** System prompt داینامیک: قواعد پایه + حافظه بلندمدت + حالت رمضان */
    private fun dynamicSystemPrompt(settings: ir.taghziehoshmand.app.data.prefs.Settings): String {
        val sb = StringBuilder(Prompts.CHAT_SYSTEM)
        Prompts.memorySection(settings.aiMemory)?.let {
            sb.append("\n").append(it).append("\n")
        }
        if (settings.ramadanMode) {
            sb.append("\n").append(Prompts.ramadanSection(settings.suhoorLabel(), settings.iftarLabel())).append("\n")
        }
        return sb.toString()
    }

    private suspend fun chatViaApi(cfg: ir.taghziehoshmand.app.data.prefs.AiConfig, text: String, snap: UserSnapshot): String {
        val settings = prefs.settingsFlow.first()
        // تاریخچه بدون آخرین پیام — همان پیام فعلی کاربر است که پایین‌تر جداگانه اضافه می‌شود
        // (بدون این، آخرین پیام دو بار به سرویس ابری ارسال می‌شد)؛ پنجره به ۱۲ پیام گسترش یافته (حافظه بلندتر)
        val history = chatDao.all().first().dropLast(1).takeLast(12).map {
            AiMsg(if (it.role == 0) "user" else "assistant", it.text)
        }
        val context = Prompts.userContext(OfflineContext.summary(snap))
        val adaptive = adaptiveInsightText()?.let { "\n" + it }
        val messages = mutableListOf(
            AiMsg("system", dynamicSystemPrompt(settings)),
            AiMsg("system", context + (adaptive ?: ""))
        )
        messages.addAll(history)
        messages.add(AiMsg("user", text))
        return AiHttp.chat(cfg.baseUrl, cfg.apiKey, cfg.model, messages)
    }

    /**
     * حافظه بلندمدت (ایده ۴): هر ۴ پیام کاربر، نکات ماندگار گفتگو توسط خود مدل استخراج
     * و به حافظه در DataStore اضافه می‌شود؛ از این پس همه پاسخ‌ها با آن شخصی می‌شوند.
     */
    private suspend fun maybeExtractMemory(userText: String, reply: String) {
        val settings = prefs.settingsFlow.first()
        val userCount = chatDao.all().first().count { it.role == 0 }
        if (userCount % 4 != 0) return
        val cfg = settings.ai.effective()
        val convo = "کاربر: $userText\nدستیار: ${reply.take(500)}"
        val out = try {
            AiHttp.chat(cfg.baseUrl, cfg.apiKey, cfg.model,
                listOf(AiMsg("system", Prompts.MEMORY_EXTRACT + convo)),
                maxTokens = 200, temperature = 0.3)
        } catch (e: Exception) {
            return // حافظه بهینه است؛ خطا نادیده گرفته می‌شود
        }
        val newLines = out.lines().map { it.trim() }.filter { it.startsWith("-") && it.length > 5 }.take(4)
        if (newLines.isEmpty()) return
        val old = settings.aiMemory.lines().filter { it.isNotBlank() }
        val merged = (old + newLines)
            .distinctBy { it.trim().lowercase() }
            .takeLast(12)
            .joinToString("\n")
        prefs.saveSettings(settings.copy(aiMemory = merged.take(1200)))
    }

    private suspend fun offlineReply(text: String, snap: UserSnapshot): String {
        val expert = OfflineExpert { q -> foodDao.search(q).first() }
        return expert.reply(text, snap)
    }

    /** تحلیل عکس غذا — نسخه چندغذایی (ایده ۱): همه غذاهای یک سفره جدا شناسایی و ثبت می‌شوند */
    suspend fun analyzeFoodImage(imageBytes: ByteArray, dayId: Long): String {
        val settings = prefs.settingsFlow.first()
        chatDao.insert(ChatMsg(role = 0, text = "[تحلیل عکس غذا] 📸"))
        // یک‌بار decode — هم برای بینایی آفلاین و هم مسیر ابری
        val bmp = withContext(Dispatchers.IO) {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }
        if (!connectivity.now()) {
            // تشخیص آفلاین واقعی با موتور بینایی روی دستگاه (نسخه ۱.۵)
            val offline = offlineVisionReply(bmp)
            if (offline != null) {
                chatDao.insert(ChatMsg(role = 1, text = offline))
                return offline
            }
            val snap = snapshot(dayId)
            val expert = OfflineExpert { q -> foodDao.search(q).first() }
            val reply = "📶 اینترنت متصل نیست و تحلیل عکس به مدل بینایی ابری نیاز دارد.\n\n" +
                "برای همین الان به‌صورت آفلاین کمک می‌کنم: نام غذا را در «ثبت وعده» جستجو و مقدارش را ثبت کنید؛ یا همین‌جا از من بپرسید:\n\n" +
                expert.reply("وضعیت امروز", snap)
            chatDao.insert(ChatMsg(role = 1, text = reply))
            return reply
        }
        if (!settings.ai.ready) {
            // اگر سرویس بینایی تنظیم نشده، اول موتور آفلاین روی دستگاه امتحان می‌شود
            val offline = offlineVisionReply(bmp)
            if (offline != null) {
                chatDao.insert(ChatMsg(role = 1, text = offline))
                return offline
            }
            val snap = snapshot(dayId)
            val expert = OfflineExpert { q -> foodDao.search(q).first() }
            val reply = "تحلیل عکس به سرویس بینایی نیاز دارد (سرویس پیش‌فرض رایگان فقط متن را پشتیبانی می‌کند). در «تنظیمات → هوش مصنوعی» سرویس سازگار با OpenAI خود را وارد کنید؛ مثلاً OpenAI با مدل gpt-4o-mini یا OpenRouter با یک مدل بینایی.\n\n" +
                "تا آن زمان: نام غذا را در جستجو بزنید و مقدارش را دستی ثبت کنید؛ اگر لازم شد پرسش‌تان را مستقیم از من بپرسید.\n\n" + expert.reply("وضعیت امروز", snap)
            chatDao.insert(ChatMsg(role = 1, text = reply))
            return reply
        }
        return try {
            if (bmp == null) throw IllegalStateException("تصویر نامعتبر")
            val scaled = withContext(Dispatchers.IO) { scaleForUpload(bmp) }
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, baos)
            val dataUrl = "data:image/jpeg;base64," + Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            val cfg = settings.ai
            val reply = AiHttp.chat(
                cfg.baseUrl, cfg.apiKey, cfg.model,
                listOf(
                    AiMsg("system", dynamicSystemPrompt(settings)),
                    AiMsg("user", listOf(Prompts.IMAGE_ANALYSIS_MULTI, AiHttp.ImagePart(dataUrl)))
                )
            )
            // ثبت خودکار هر غذای شناسایی‌شده به‌صورت جداگانه
            val items = parseFoodItems(reply)
            val finalReply = if (items.isNotEmpty()) {
                val mealType = mealTypeByClock()
                items.forEach { f ->
                    mealDao.insert(
                        MealEntry(
                            dayId = dayId, mealType = mealType, foodId = null,
                            name = f.name, grams = f.grams, kcal = f.kcal,
                            protein = f.protein, carb = f.carb, fat = f.fat,
                            source = FoodSource.AI_PHOTO
                        )
                    )
                }
                val logged = items.joinToString("\n") { "• ${it.name} — ${Fa.gram(it.grams)} گرم — ${Fa.kcal(it.kcal)} کیلوکالری" }
                reply + "\n\n✅ هر غذا جداگانه در «ثبت وعده امروز» ذخیره شد:\n" + logged
            } else reply
            chatDao.insert(ChatMsg(role = 1, text = finalReply))
            finalReply
        } catch (e: Exception) {
            val reply = "تحلیل عکس موفق نشد: ${e.message?.take(180) ?: "خطای ناشناخته"}\nنام غذا را دستی جستجو و ثبت کنید."
            chatDao.insert(ChatMsg(role = 1, text = reply))
            reply
        }
    }

    /** وعده بر اساس ساعت روز — برای ثبت خودکار از عکس و صوت */
    private fun mealTypeByClock(): Int {
        val h = LocalTime.now().hour
        return when {
            h < 10 -> MealType.BREAKFAST
            h < 16 -> MealType.LUNCH
            h < 22 -> MealType.DINNER
            else -> MealType.SNACK
        }
    }

    /**
     * تشخیص آفلاین واقعی غذا (نسخه ۱.۵) — موتور بینایی روی دستگاه (TFLite).
     * خروجی null یعنی مدل چیزی شناسایی نکرد یا در دسترس نیست.
     */
    private suspend fun offlineVisionReply(bmp: Bitmap?): String? {
        if (bmp == null) return null
        val dets = withContext(Dispatchers.Default) {
            runCatching { OfflineFoodVision.detect(context, bmp) }.getOrDefault(emptyList())
        }
        if (dets.isEmpty()) return null
        val lines = dets.map { d ->
            val match = runCatching { foodDao.search(d.labelFa).first().firstOrNull() }.getOrNull()
            val matchText = match?.let { m ->
                " → برای ثبت: «${m.name}» (${Fa.kcal(m.kcal100)} کیلوکالری در ۱۰۰ گرم)"
            } ?: ""
            "• ${d.labelFa} — اطمینان ${Fa.num((d.confidence * 100).toInt())}٪$matchText"
        }.joinToString("\n")
        return "📷 تحلیل آفلاین عکس با موتور بینایی روی دستگاه (بدون نیاز به اینترنت):\n\n$lines\n\n" +
            "⚠️ تشخیص آفلاین تقریبی است؛ برای تحلیل دقیق‌تر با مدل ابری و ثبت خودکار هر غذا، اینترنت را وصل کنید و دوباره عکس بفرستید."
    }

    /**
     * استخراج غذا از جمله صوتی (ثبت صوتی — نسخه ۱.۵).
     * خروجی null یعنی: اینترنت قطع بود، سرویس خطا داد یا غذایی شناسایی نشد.
     */
    suspend fun extractFoodsFromVoice(text: String): List<ParsedFood>? {
        if (text.isBlank()) return null
        val settings = prefs.settingsFlow.first()
        if (!connectivity.now()) return null
        val cfg = settings.ai.effective()
        val out = try {
            AiHttp.chat(cfg.baseUrl, cfg.apiKey, cfg.model,
                listOf(AiMsg("system", Prompts.VOICE_EXTRACT + text)),
                maxTokens = 350, temperature = 0.2)
        } catch (e: Exception) {
            return null
        }
        val items = parseFoodItems(out)
        return if (items.isEmpty()) null else items
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

    companion object {

        /**
         * پارس خطوط «غذا N:» از پاسخ مدل (تحلیل عکس چندغذایی و استخراج صوتی).
         * اعداد فارسی/عربی به لاتین تبدیل می‌شوند؛ آیتم بدون کالری نادیده گرفته می‌شود.
         */
        fun parseFoodItems(reply: String): List<ParsedFood> {
            val items = mutableListOf<ParsedFood>()
            for (raw in reply.lines()) {
                val line = Fa.latin(raw.trim())
                if (!line.startsWith("غذا")) continue
                val body = line.substringAfter(":", "").trim()
                if (body.isBlank()) continue
                val grams = Regex("(\\d+(?:\\.\\d+)?)\\s*گرم").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val kcal = Regex("(\\d+(?:\\.\\d+)?)\\s*کالری").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                if (kcal <= 0.0) continue
                val protein = Regex("پ\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val carb = Regex("ک\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val fat = Regex("چ\\s*[:：]\\s*(\\d+(?:\\.\\d+)?)").find(body)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                val name = body
                    .replace(Regex("\\d+(?:\\.\\d+)?\\s*گرم"), "")
                    .replace(Regex("\\d+(?:\\.\\d+)?\\s*کالری"), "")
                    .replace(Regex("[پکچ]\\s*[:：]\\s*\\d+(?:\\.\\d+)?"), "")
                    .replace("—", "").trim()
                    .trimEnd('-', '–', ' ').trim()
                if (name.isBlank()) continue
                items.add(ParsedFood(name = name, grams = if (grams > 0) grams else 100.0, kcal = kcal, protein = protein, carb = carb, fat = fat))
                if (items.size >= 8) break // سقف منطقی برای یک عکس/جمله
            }
            return items
        }
    }
}

/** یک غذای پارس‌شده از پاسخ مدل (عکس یا صوت) */
data class ParsedFood(
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double
)

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
