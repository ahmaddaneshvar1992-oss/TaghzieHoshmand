package ir.taghziehoshmand.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.db.FoodDao
import ir.taghziehoshmand.app.data.model.Food
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * بینایی غذا روی دستگاه (ML Kit گوگل — کاملاً آفلاین):
 * ۱) لیبل مشخص غذایی (مثل Pizza، Sushi، Cake) → جستجو در پایگاه داده → مشخصات کامل
 * ۲) لیبل عمومی (Food/Meal/Fast food...) → غنی‌سازی با مدل متنی ابری (حدس اعلام‌شده)
 *    یا در آفلاین: پیشنهاد از دسته متناظر پایگاه داده
 */
object FoodVision {

    /** لیبل‌های مشخص غذایی ML Kit → کلیدواژه جستجوی فارسی در پایگاه داده */
    private val specificLabels = linkedMapOf(
        "Pizza" to "پیتزا",
        "Sushi" to "سوشی",
        "Cheeseburger" to "برگر",
        "Hamburger" to "برگر",
        "Hot dog" to "هات داگ",
        "Sandwich" to "ساندویچ",
        "Cake" to "کیک",
        "Cookie" to "بیسکویت",
        "Doughnut" to "دونات",
        "Donut" to "دونات",
        "Bread" to "نان",
        "Pie" to "کیک",
        "Muffin" to "کاپ‌کیک",
        "Waffle" to "وافل",
        "Bagel" to "نان",
        "Croissant" to "نان شیرین",
        "Pancake" to "پنکیک",
        "Cheese" to "پنیر",
        "Butter" to "کره",
        "Honey" to "عسل",
        "Egg" to "تخم مرغ",
        "Omelette" to "املت",
        "Chocolate" to "شکلات",
        "Rice" to "برنج",
        "Pasta" to "ماکارونی",
        "Noodles" to "ماکارونی",
        "Salad" to "سالاد",
        "Soup" to "سوپ",
        "Stew" to "خورش",
        "Curry" to "خورش",
        "Steak" to "استیک",
        "Fried chicken" to "مرغ سوخاری",
        "Ice cream" to "بستنی",
        "Popcorn" to "پاپ کورن",
        "French fries" to "سیب زمینی سرخ کرده",
        "Fruit" to "میوه",
        "Apple" to "سیب",
        "Banana" to "موز",
        "Orange" to "پرتقال",
        "Lemon" to "لیمو",
        "Lime" to "لیمو",
        "Mango" to "انبه",
        "Peach" to "هلو",
        "Pear" to "گلابی",
        "Pineapple" to "آناناس",
        "Strawberry" to "توت فرنگی",
        "Cherry" to "گیلاس",
        "Watermelon" to "هندوانه",
        "Melon" to "طالبی",
        "Grape" to "انگور",
        "Grapes" to "انگور",
        "Pomegranate" to "انار",
        "Date" to "خرما",
        "Apricot" to "زردآلو",
        "Fig" to "انجیر",
        "Coconut" to "نارگیل",
        "Avocado" to "آووکادو",
        "Olive" to "زیتون",
        "Raisin" to "کشمش",
        "Almond" to "بادام",
        "Walnut" to "گردو",
        "Peanut" to "بادام زمینی",
        "Cashew" to "بادام هندی",
        "Potato" to "سیب زمینی",
        "Tomato" to "گوجه فرنگی",
        "Cucumber" to "خیار",
        "Onion" to "پیاز",
        "Carrot" to "هویج",
        "Yogurt" to "ماست",
        "Juice" to "آبمیوه",
        "Coffee" to "قهوه",
        "Milk" to "شیر",
        "Tea" to "چای",
        "Shrimp" to "میگو",
        "Fish" to "ماهی",
        "Chicken" to "مرغ",
        "Beef" to "گوشت",
        "Kebab" to "کباب",
        "Falafel" to "فلافل"
    )

    /** ترجمه فارسی لیبل‌های ML Kit برای نمایش در پاسخ */
    private val labelFa = mapOf(
        "Food" to "غذا", "Dish" to "غذا", "Meal" to "وعده غذایی", "Cuisine" to "غذا",
        "Produce" to "محصولات کشاورزی", "Ingredient" to "ماده غذایی",
        "Snack" to "تنقلات", "Dessert" to "دسر", "Baked goods" to "نان‌ها",
        "Fast food" to "فست‌فود", "Fruit" to "میوه", "Vegetable" to "سبزیجات",
        "Junk food" to "فست‌فود", "Comfort food" to "غذا", "Fried food" to "غذای سرخ‌کردنی",
        "Breakfast" to "صبحانه", "Drink" to "نوشیدنی", "Beverage" to "نوشیدنی",
        "Kitchen appliance" to "لوازم آشپزخانه", "Tableware" to "ظروف", "Plate" to "بشقاب", "Bowl" to "کاسه"
    )

    private fun faLabel(label: String): String =
        labelFa[label] ?: specificLabels[label] ?: label

    /**
     * نمرهٔ تطبیق نام غذا با کلیدواژه — جلوگیری از تطبیق زیررشته‌ایِ نادرست
     * (مثلاً کلیدواژهٔ «میوه» نباید «آب‌میوه صنعتی» را برگرداند؛ نیم‌فاصله اتصالِ داخل کلمه است، نه مرز کلمه)
     */
    fun matchScore(name: String, keyword: String): Int {
        fun isBoundary(c: Char) = !c.isLetter() && c != '\u200C'
        fun score(n: String, k: String): Int {
            if (n == k) return 4
            // شروع‌شدن با کلیدواژه فقط وقتی معتبر است که بلافاصله بعدش مرز کلمه باشد («نان بربری» بله، «سیب‌زمینی» برای «سیب» نه)
            if (n.startsWith(k) && (n.length == k.length || isBoundary(n[k.length]))) return 3
            val idx = n.indexOf(k)
            if (idx >= 0) {
                val beforeOk = idx == 0 || isBoundary(n[idx - 1])
                val afterIdx = idx + k.length
                val afterOk = afterIdx >= n.length || isBoundary(n[afterIdx])
                return if (beforeOk && afterOk) 2 else 0
            }
            return 0
        }
        // گذر ۱: متن خام — نیم‌فاصله داخل کلمه حساب می‌شود («میوه» با «آب‌میوه» تطبیق نمی‌کند)
        score(name, keyword).takeIf { it > 0 }?.let { return it }
        // گذر ۲: حذف نیم‌فاصله — «تخم مرغ» با «تخم‌مرغ آب‌پز» تطبیق می‌کند
        val n2 = name.replace("\u200C", "")
        val k2a = keyword.replace("\u200C", "")
        score(n2, k2a).takeIf { it > 0 }?.let { return it }
        score(n2, k2a.replace(" ", "")).takeIf { it > 0 }?.let { return it }
        // گذر ۳: بدون هیچ فاصله‌ای — برای عبارت‌های چندکلمه‌ای («سیب زمینی سرخ کرده» ↔ «سیب‌زمینی سرخ‌کرده»)
        return score(n2.replace(" ", ""), k2a.replace(" ", ""))
    }

    /** لیبل‌های عمومی → دسته پایگاه داده برای پیشنهاد */
    private val categoryHints = mapOf(
        "Fast food" to "فست‌فود",
        "Fruit" to "میوه‌ها",
        "Vegetable" to "سبزیجات",
        "Cake" to "شیرینی و تنقلات",
        "Cookie" to "شیرینی و تنقلات",
        "Pie" to "شیرینی و تنقلات",
        "Icing" to "شیرینی و تنقلات",
        "Bread" to "غلات و نشاسته",
        "Juice" to "نوشیدنی‌ها",
        "Coffee" to "نوشیدنی‌ها",
        "Wine" to "نوشیدنی‌ها",
        "Cheeseburger" to "فست‌فود",
        "Hot dog" to "فست‌فود"
    )

    /** کلاینت ML Kit یک‌بار ساخته و همیشه بازاستفاده می‌شود — ساخت/بستن مکرر خودش تأخیر دارد */
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    /** اجرای ML Kit روی بیت‌مپ (روی دستگاه، بدون اینترنت) */
    suspend fun label(bitmap: Bitmap): List<Pair<String, Float>> =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    cont.resume(labels.sortedByDescending { it.confidence }
                        .filter { it.confidence > 0.35f }
                        .take(8)
                        .map { it.text to it.confidence })
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }

    /** ساخت پاسخ کامل فارسی از روی عکس */
    suspend fun analyze(
        bitmap: Bitmap,
        foodDao: FoodDao,
        snap: UserSnapshot,
        enrichOnline: (suspend (String) -> String)?
    ): String {
        val labels = try { label(bitmap) } catch (e: Exception) {
            return "تحلیل عکس روی دستگاه ناموفق بود: ${e.message?.take(80)}\nنام غذا را دستی جستجو و ثبت کنید."
        }
        if (labels.isEmpty()) {
            return "نتوانستم چیزی قابل‌توجه در عکس پیدا کنم. عکس را نزدیک‌تر و با نور بهتر دوباره بگیرید، یا نام غذا را جستجو کنید."
        }
        val labelsText = labels.joinToString("، ") { "${faLabel(it.first)} ${Fa.num((it.second * 100).roundToInt())}٪" }

        // ۱) لیبل مشخص غذایی → تطبیق مرزیِ کلمه در پایگاه داده (نه اولین تطبیق زیررشته‌ای!)
        val specific = labels.firstOrNull { it.first in specificLabels }
        if (specific != null) {
            val keyword = specificLabels[specific.first]!!
            val head = "📸 تحلیل روی دستگاه (ML Kit):\nبه نظر می‌رسد «${faLabel(specific.first)}» باشد (اطمینان ${Fa.num((specific.second * 100).roundToInt())}٪).\n\n"
            val best = foodDao.search(keyword).first()
                .mapNotNull { f -> matchScore(f.name, keyword).takeIf { it > 0 }?.let { f to it } }
                .maxByOrNull { it.second }?.first
            if (best != null) return head + foodDetails(best, snap)

            // تطبیق مطمئن نبود → صادقانه: گزینه‌های رایج همان دسته پیشنهاد می‌شود (بدون جواب کنسروی غلط)
            val category = categoryHints[specific.first]
            if (category != null) {
                val pool = foodDao.pool().filter { it.category == category }.sortedByDescending { it.commonWeight }.take(3)
                if (pool.isNotEmpty()) {
                    val sb = StringBuilder(head)
                    sb.append("نام دقیق این «${faLabel(specific.first)}» را روی دستگاه نمی‌توانم تشخیص بدهم؛ رایج‌ترین گزینه‌های این دسته:\n")
                    pool.forEach { f ->
                        sb.append("• ${f.name} — حدود ${Fa.kcal(f.kcal100)} کیلوکالری در ۱۰۰ گرم\n")
                    }
                    sb.append("\nاگر نام دقیقش را می‌دانید، در «ثبت وعده» جستجو و ثبت کنید تا محاسبات دقیق بماند.\nبرچسب‌های تصویر: $labelsText")
                    return sb.toString()
                }
            }
            return head + "این غذا با همین نام در پایگاه داده پیدا نشد؛ لطفاً نام دقیق آن را در «ثبت وعده» جستجو و ثبت کنید.\n\nبرچسب‌های تصویر: $labelsText"
        }

        // ۲) لیبل عمومی → غنی‌سازی ابری یا پیشنهاد دسته
        val enrich = enrichOnline
        if (enrich != null) {
            val prompt = "کاربر از غذایی عکس گرفته و مدل بینایی روی گوشی فقط این برچسب‌های عمومی را داده است: $labelsText.\n" +
                "بر اساس همین نشانه‌ها، محتمل‌ترین غذای ایرانی یا بین‌المللی را حدس بزن و درباره آن پاسخ کامل بده: نام غذا، کالری تقریبی ۱۰۰ گرم و یک سهم معمول، پروتئین/کربوهیدرات/چربی تقریبی، و یک نکته. " +
                "مهم: صریح بگو این یک «حدس بر اساس برچسب‌ها» است و کاربر باید نام دقیق غذا را تأیید یا اصلاح کند. پاسخ کوتاه (حداکثر ۸ خط) و فقط فارسی."
            val ai = runCatching { enrich(prompt) }.getOrNull()
            if (!ai.isNullOrBlank()) {
                return "📸 تحلیل روی دستگاه: برچسب‌های عمومی ($labelsText)\n\n" +
                    "🤖 ادامه پاسخ از هوش مصنوعی ابری:\n$ai\n\n⚠️ این حدس بر اساس برچسب‌های کلی تصویر است؛ نام دقیق غذا را تأیید یا اصلاح کنید."
            }
        }

        // ۳) آفلاین: پیشنهاد از دسته متناظر پایگاه داده
        val category = labels.firstOrNull { it.first in categoryHints }?.let { categoryHints[it.first] }
        return if (category != null) {
            val pool = foodDao.pool().filter { it.category == category }.sortedByDescending { it.commonWeight }.take(3)
            val sb = StringBuilder("📸 برچسب‌های تصویر: $labelsText\n\nتشخیص دقیق غذا روی دستگاه ممکن نشد، اما عکس شبیه دسته «$category» است. گزینه‌های پرطرفدار این دسته:\n")
            pool.forEach { f ->
                sb.append("• ${f.name} — حدود ${Fa.kcal(f.kcal100)} کیلوکالری در ۱۰۰ گرم\n")
            }
            sb.append("\nاگر غذای اصلی را از میان آن‌ها می‌شناسید، نامش را در «ثبت وعده» جستجو و ثبت کنید؛ یا با اینترنت وصل، سرویس بینایی کامل فعال می‌شود.")
            sb.toString()
        } else {
            "📸 برچسب‌های تصویر: $labelsText\n\nتشخیص غذا با مدل روی دستگاه برای این عکس دقیق نیست. دو راه دارید:\n• نام غذا را در «ثبت وعده» جستجو و ثبت کنید\n• در تنظیمات، سرویس بینایی ابری (سازگار با OpenAI با مدل دیداری مثل gpt-4o-mini) اضافه کنید تا عکس کامل تحلیل شود"
        }
    }

    /** مشخصات کامل یک غذای پایگاه داده + جایگاهش در کالری امروز */
    private fun foodDetails(f: Food, snap: UserSnapshot): String {
        val servingKcal = f.kcal100 * f.servingGrams / 100
        val remaining = snap.remainingKcal
        val share = if (remaining > 0) (servingKcal / remaining * 100).roundToInt().coerceAtMost(999) else 0
        return buildString {
            append("«${f.name}» (${f.category})\n\n")
            append("🌍 به‌ازای هر ۱۰۰ گرم:\n")
            append("• کالری: ${Fa.kcal(f.kcal100)} کیلوکالری\n")
            append("• پروتئین: ${Fa.d(f.protein100)} گرم | کربوهیدرات: ${Fa.d(f.carb100)} گرم | چربی: ${Fa.d(f.fat100)} گرم\n\n")
            append("🍽️ سهم معمول (${f.servingLabel} ≈ ${Fa.gram(f.servingGrams.toDouble())} گرم):\n")
            append("• کالری: ${Fa.kcal(servingKcal)} کیلوکالری\n")
            append("• پروتئین: ${Fa.d(f.protein100 * f.servingGrams / 100)} گرم | کربوهیدرات: ${Fa.d(f.carb100 * f.servingGrams / 100)} گرم | چربی: ${Fa.d(f.fat100 * f.servingGrams / 100)} گرم\n\n")
            append("📊 نسبت به امروز: این سهم حدود ${Fa.num(share)}٪ از کالری باقی‌مانده امروز (${Fa.kcal(remaining)} کیلوکالری) را مصرف می‌کند.\n")
            append(when {
                remaining <= 0 -> "سقف کالری امروز پر شده؛ اگر می‌خورید، ۳۰ دقیقه پیاده‌روی جبران خوبی است."
                share > 70 -> "این سهم کالری باقی‌مانده را تقریباً کامل می‌کند؛ با احتیاط بخورید."
                else -> "جای خوبی در برنامه امروز دارد؛ ثبتش کنید تا محاسبات دقیق بماند."
            })
        }
    }

    /** خواندن و کوچک‌کردن عکس برای تحلیل — نمونه‌برداری (inSampleSize) برای جلوگیری از OOM روی عکس‌های بزرگ گالری */
    fun decodeScaled(bytes: ByteArray, maxSide: Int = 1024): Bitmap? {
        // ۱) فقط ابعاد را می‌خوانیم تا عکس خیلی بزرگ، حافظه را نگیرد
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        // ۲) نمونه‌برداری: بزرگ‌ترین توان دو که خروجی آن حدود maxSide می‌ماند
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val w = bmp.width
        val h = bmp.height
        if (w <= maxSide && h <= maxSide) return bmp
        val scale = if (w > h) maxSide.toFloat() / w else maxSide.toFloat() / h
        return Bitmap.createScaledBitmap(bmp, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
    }
}
