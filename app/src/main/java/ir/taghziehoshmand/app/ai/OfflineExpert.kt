package ir.taghziehoshmand.app.ai

import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.HealthCalc
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.prefs.UserProfile
import kotlin.math.roundToInt

/** وضعیت لحظه‌ای کاربر که به پاسخ‌دهی (آنلاین و آفلاین) داده می‌شود */
data class UserSnapshot(
    val profile: UserProfile?,
    val kcalToday: Double,
    val proteinToday: Double,
    val carbToday: Double,
    val fatToday: Double,
    val targetKcal: Double,
    val waterMl: Int,
    val waterGoalMl: Int,
    val exerciseKcal: Double,
    val steps: Int,
    val weightTrend: String?,      // مثلا: «۷۸٫۵ کیلوگرم، کاهش ۱٫۲ کیلو در ۳۰ روز»
    val remainingKcal: Double
)

/** متخصص آفلاین تغذیه — بدون اینترنت هم پاسخ می‌دهد (موتور قاعده‌محور فارسی) */
class OfflineExpert(private val searchFood: suspend (String) -> List<Food>) {

    companion object {
        private val mealCalories = listOf(450, 600, 550, 150)
        private val breakfastIdeas = listOf(
            "نیمرو با نان سنگک", "عدسی با نان بربری", "نان و پنیر و گردو و چای",
            "حلیم گندم", "شیر با نان تست و عسل", "املت گوجه با نان", "کره و عسل با نان لواش"
        )
        private val lunchIdeas = listOf(
            "قورمه‌سبزی با برنج کم‌روغن", "چلوکباب کوبیده با سالاد شیرازی", "خوراک لوبیا با نان",
            "زرشک‌پلو با مرغ", "عدس‌پلو با کشمش و پیاز داغ", "کوکو سبزی با ماست", "فسنجان با برنج"
        )
        private val dinnerIdeas = listOf(
            "سوپ جو با مرغ", "ماهی گریل با سالاد", "خوراک مرغ و قارچ", "کوکو سیب‌زمینی با سالاد فصل",
            "ماست و خیار و گردو با نان", "میگو پخته با سبزیجات بخارپز", "تخم‌مرغ آب‌پز و سبزی"
        )
        private val snackIdeas = listOf(
            "یک عدد میوه", "مشتی آجیل خام", "ماست کم‌چرب", "سبزی خوردن و پنیر",
            "خیار و هویج", "کشمش و نخودچی"
        )
        private val tips = listOf(
            "برای کاهش وزن، نیم تا یک کیلوگرم در هفته کاهش، سالم‌ترین سرعت است.",
            "پروتئین کافی (حدود ۱٫۶ تا ۲ گرم به‌ازای هر کیلو وزن بدن) به حفظ عضلات کمک می‌کند.",
            "روزانه ۵ تا ۷ سرو میوه و سبزی، فیبر و سیری کافی می‌دهد.",
            "قبل از غذا یک لیوان آب بنوشید؛ به کنترل اشتها کمک می‌کند.",
            "سنگین‌ترین وعده را قبل از ساعت ۶ عصر بخورید؛ بدن شب‌ها کالری‌سوزی کمتری دارد.",
            "غذا را آرام و با جویدن کامل بخورید؛ احساس سیری ۲۰ دقیقه بعد به مغز می‌رسد.",
            "شام را حداقل ۲ ساعت قبل از خواب بخورید.",
            "میان‌وعده سالم را از قبل آماده کنید تا سراغ تنقلات نروید."
        )
    }

    suspend fun reply(text: String, snap: UserSnapshot): String {
        val t = text.trim()
        val lower = t.replace("‌", "").lowercase()
        val has: (String) -> Boolean = { lower.contains(it.replace("‌", "")) }

        // سلام و شروع مکالمه
        if (has("سلام") || t.isBlank() || has("درخدمت")) return greeting(snap)

        // پرسش کالری یک غذای مشخص: «چلوکباب چند کالری داره»
        if (has("کالری") && !has("امروز") && !has("باقیمانده") && !has("سوخته")) {
            val found = findFoodInText(t)
            if (found != null) return foodAnswer(found)
        }

        // وضعیت امروز
        if (has("امروز") && (has("کالری") || has("خورده") || has("مصرف"))) return todaySummary(snap)

        // باقی‌مانده کالری
        if (has("باقیمانده") || has("بقیه") || (has("می‌تونم") && has("کالری"))) {
            return "کالری باقی‌مانده امروز شما ${Fa.kcal(snap.remainingKcal)} کیلوکالری است." +
                (if (snap.remainingKcal < 150)
                    " سقف تقریباً پر شده؛ اگر گرسنه‌اید، یک میان‌وعده سبک مثل سبزی و پنیر بخورید."
                else " می‌توانید یک وعده کامل یا دو میان‌وعده سبک با همین میزان داشته باشید.")
        }

        // برنامه غذایی
        if (has("برنامه") || has("رژیم")) {
            val goal = snap.profile?.goal
            return weeklyPlanText(snap, weekly = has("هفتگی") || has("هفت"))
        }

        // جایگزین غذا
        if (has("جایگزین")) {
            val found = findFoodInText(t)
            if (found != null) return alternativesText(found)
            return "برای پیشنهاد جایگزین، نام غذا را بنویسید؛ مثلاً «جایگزین برنج سفید چی بخورم؟»"
        }

        // آب
        if (has("آب") && !has("آب‌پز")) return waterAnswer(snap)

        // ورزش و کالری سوزانده
        if (has("ورزش") || has("سوخته") || has("تمرین") || has("قدم")) {
            val burned = snap.exerciseKcal
            val base = if (burned > 0) "امروز با ورزش ${Fa.kcal(burned)} کیلوکالری سوزانده‌اید و ${Fa.num(snap.steps)} قدم برداشته‌اید. "
            else "امروز هنوز ورزشی ثبت نکرده‌اید؛ فقط ۳۰ دقیقه پیاده‌روی تند حدود ۱۵۰ کیلوکالری می‌سوزاند. "
            return base + "پیاده‌روی بعد از شام هم به کنترل قند خون کمک می‌کند."
        }

        // وزن و پیشرفت
        if (has("وزن") || has("پیشرفت")) {
            return snap.weightTrend?.let {
                "وضعیت وزن شما: $it. " + adviceByGoal(snap)
            } ?: "هنوز سنجش وزنی ثبت نشده. هر هفته صبح ناشتا وزنتان را ثبت کنید تا روند را تحلیل کنم."
        }

        // وعده‌های خاص
        if (has("صبحانه")) return mealSuggest("صبحانه", breakfastIdeas, mealCalories[0], snap)
        if (has("ناهار")) return mealSuggest("ناهار", lunchIdeas, mealCalories[1], snap)
        if (has("شام")) return mealSuggest("شام", dinnerIdeas, mealCalories[2], snap)
        if (has("میان‌وعده") || has("عصرانه")) return mealSuggest("میان‌وعده", snackIdeas, mealCalories[3], snap)

        // پروتئین و ماکرو
        if (has("پروتئین")) {
            val target = snap.profile?.proteinG ?: 0.0
            return "امروز ${Fa.d(snap.proteinToday)} گرم پروتئین مصرف کرده‌اید" +
                (if (target > 0) " از هدف ${Fa.kcal(target)} گرمی. " else ". ") +
                "منابع خوب: مرغ، تخم‌مرغ، ماهی، حبوبات، لبنیات کم‌چرب و آجیل."
        }
        if (has("کربوهیدرات")) return "کربوهیدرات امروز شما ${Fa.d(snap.carbToday)} گرم است. منابع سالم: برنج قهوه‌ای، نان سنگک، جو، حبوبات و میوه‌ها؛ برنج و نان را نصف‌به‌نصف با سبزیجات ترکیب کنید."
        if (has("چربی") || has("چرب")) return "چربی امروز شما ${Fa.d(snap.fatToday)} گرم است. روغن را به یک تا دو قاشق غذاخوری در روز محدود کنید و از روغن زیتون یا سرخ‌کردن سبک استفاده کنید."

        // BMI
        if (has("bmi") || has("بی‌ام")) {
            val p = snap.profile ?: return "اول پروفایل بدنی‌تان را کامل کنید تا BMI را حساب کنم."
            val bmi = HealthCalc.bmi(p.weightKg, p.heightCm)
            return "BMI فعلی شما ${Fa.d(bmi, 1)} یعنی «${HealthCalc.bmiLabel(bmi)}» است. وزن ایده‌آل برای قد ${Fa.num(p.heightCm)} سانتی‌متر حدود ${Fa.d(p.results().idealWeight)} کیلوگرم است."
        }

        // کالری هدف
        if (has("هدف") || has("tdee") || has("تدی")) {
            val p = snap.profile ?: return "برای محاسبه کالری هدف، پروفایل بدنی را کامل کنید."
            return "کالری هدف روزانه شما ${Fa.kcal(p.targetKcal)} کیلوکالری است (هدف: ${p.goal.label}). توزیع پیشنهادی: صبحانه ۲۵٪، ناهار ۳۵٪، شام ۳۰٪ و میان‌وعده ۱۰٪."
        }

        return fallback(snap)
    }

    private fun greeting(snap: UserSnapshot): String {
        val name = snap.profile?.name?.takeIf { it.isNotBlank() } ?: "دوست من"
        return "سلام $name! 👋\nمشاور تغذیه هوشمند و شخصی شما هستم.\n\nمی‌توانم:\n• کالری و ماکروهای امروز را گزارش بدهم\n• برنامه غذایی روزانه یا هفتگی بنویسم\n• برای هر غذایی جایگزین کم‌کالری‌تر پیشنهاد کنم\n• وضعیت آب، ورزش و وزن‌تان را تحلیل کنم\n\nبپرسید: «امروز چقدر کالری خوردم؟» یا «برنامه غذایی هفتگی بده»."
    }

    private fun todaySummary(snap: UserSnapshot): String {
        val pct = if (snap.targetKcal > 0) (snap.kcalToday / snap.targetKcal * 100).roundToInt() else 0
        return "گزارش امروز:\n• کالری: ${Fa.kcal(snap.kcalToday)} از ${Fa.kcal(snap.targetKcal)} کیلوکالری (${"${Fa.num(pct)}٪"})\n• پروتئین: ${Fa.d(snap.proteinToday)} گرم\n• کربوهیدرات: ${Fa.d(snap.carbToday)} گرم\n• چربی: ${Fa.d(snap.fatToday)} گرم\n• آب: ${Fa.num(snap.waterMl)} از ${Fa.num(snap.waterGoalMl)} میلی‌لیتر\n• ورزش: ${Fa.kcal(snap.exerciseKcal)} کیلوکالری سوزانده شده\n\n${if (pct < 80) "هنوز جا برای وعده‌های اصلی دارید." else if (pct <= 105) "عالی! دقیقاً در محدوده هدف هستید." else "از سقف هدف گذشت کرده‌اید؛ ۳۰ دقیقه پیاده‌روی جبران خوبی است."}"
    }

    private fun mealSuggest(meal: String, ideas: List<String>, kcal: Int, snap: UserSnapshot): String {
        val a = ideas.random()
        val b = ideas.filter { it != a }.random()
        val budget = if (snap.remainingKcal < kcal) snap.remainingKcal.roundToInt().coerceAtLeast(120) else kcal
        return "دو پیشنهاد $meal با حدود ${Fa.num(budget)} کیلوکالری:\n• $a (تقریباً ${Fa.num(kcal)} کالری)\n• $b (تقریباً ${Fa.num(kcal)} کالری)\n\nنکته: با یک بشقاب سبزی خوردن شروع کنید تا سیری زودتر برسد."
    }

    private suspend fun findFoodInText(text: String): Food? {
        if (text.length < 4) return null
        val results = searchFood(text)
        return results.firstOrNull()
    }

    private fun foodAnswer(f: Food): String =
        "«${f.name}» به‌ازای هر ۱۰۰ گرم:\n• کالری: ${Fa.kcal(f.kcal100)} کیلوکالری\n• پروتئین: ${Fa.d(f.protein100)} گرم\n• کربوهیدرات: ${Fa.d(f.carb100)} گرم\n• چربی: ${Fa.d(f.fat100)} گرم\n\nسهم استاندارد «${f.servingLabel}» حدود ${Fa.kcal(f.kcal100 * f.servingGrams / 100)} کیلوکالری است."

    private fun alternativesText(f: Food): String {
        val ratio = 0.8
        return "به‌جای «${f.name}» (${Fa.kcal(f.kcal100)} کالری در ۱۰۰ گرم) این گزینه‌های کم‌کالری‌تر را امتحان کنید:\n• همان غذا با سهم ${Fa.num((f.servingGrams * ratio).roundToInt())} گرمی — حدود ${Fa.kcal(f.kcal100 * f.servingGrams * ratio / 100)} کالری\n• نسخه بخارپز یا آب‌پز به‌جای سرخ‌شده (۳۰ تا ۴۰٪ کمتر)\n• نصف سهم + یک بشقاب سالاد یا سبزی بخارپز برای سیری بیشتر\n\nدر صفحه «ثبت وعده» دکمه «جایگزین کم‌کالری» دقیق‌ترین گزینه‌ها را از پایگاه داده پیشنهاد می‌دهد."
    }

    private fun waterAnswer(snap: UserSnapshot): String {
        val pct = if (snap.waterGoalMl > 0) (snap.waterMl * 100 / snap.waterGoalMl) else 0
        return "آب امروز: ${Fa.num(snap.waterMl)} از ${Fa.num(snap.waterGoalMl)} میلی‌لیتر (${"${Fa.num(pct)}٪"}).\n" +
            if (pct < 50) "عقب افتاده‌اید! همین حالا دو لیوان آب بنوشید و یادآور آب را فعال کنید."
            else if (pct < 100) "خوب پیش رفته‌اید؛ هر یک ساعت یک لیوان آب بنوشید."
            else "فوق‌العاده! هدف آب امروز کامل شده است."
    }

    private fun adviceByGoal(snap: UserSnapshot): String = when (snap.profile?.goal) {
        ir.taghziehoshmand.app.core.Goal.LOSE -> "با همین روند در ${Fa.num(((snap.profile.weightKg - snap.profile.goalWeightKg).coerceAtLeast(0.5) / 0.5).roundToInt())} هفته به وزن هدف می‌رسید؛ برنامه را با تعادل پروتئین بالا ادامه دهید."
        ir.taghziehoshmand.app.core.Goal.GAIN -> "افزایش آرام با پروتئین کافی و تمرین قدرتی، بهترین مسیر است."
        else -> "حفظ وزن یعنی پایداری؛ کالری را نزدیک هدف نگه دارید."
    }

    fun weeklyPlanText(snap: UserSnapshot, weekly: Boolean = true): String {
        val target = snap.targetKcal.takeIf { it > 0 } ?: 2000.0
        val breakfast = (target * 0.25).roundToInt()
        val lunch = (target * 0.35).roundToInt()
        val dinner = (target * 0.30).roundToInt()
        val snack = (target * 0.10).roundToInt()
        val head = if (weekly)
            "این یک برنامه غذایی هفتگی متعادل با ${Fa.kcal(target)} کیلوکالری در روز است (برای همه روزها با جایگزینی بین گزینه‌ها):"
        else
            "برنامه غذایی امروز با ${Fa.kcal(target)} کیلوکالری:"
        val day = listOf(
            "• صبحانه: ${breakfastIdeas.random()} (تقریباً ${Fa.num(breakfast)} کالری)",
            "• میان‌وعده: ${snackIdeas.random()} (تقریباً ${Fa.num(snack)} کالری)",
            "• ناهار: ${lunchIdeas.random()} (تقریباً ${Fa.num(lunch)} کالری)",
            "• شام: ${dinnerIdeas.random()} (تقریباً ${Fa.num(dinner)} کالری)"
        ).joinToString("\n")
        val tip = "نکته: روزانه ۲ لیتر آب بنوشید و ${tips.random().take(60)}..."
        return "$head\n$day\n\n$tip"
    }

    private fun fallback(snap: UserSnapshot): String {
        val t = tips.random()
        return "این پرسش را دقیق‌تر بپرسید تا بهتر راهنمایی کنم. مثلاً:\n• «امروز چقدر کالری خوردم؟»\n• «جایگزین پیتزا چی بخورم؟»\n• «برنامه غذایی هفتگی بده»\n\nدر همین حین یک نکته تغذیه‌ای: $t"
    }
}
