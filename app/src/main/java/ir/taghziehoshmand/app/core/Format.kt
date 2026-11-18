package ir.taghziehoshmand.app.core

/** تبدیل اعداد لاتین به فارسی و قالب‌بندی‌های رایج */
object Fa {
    private val faDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    fun num(v: Any?): String {
        val s = v?.toString() ?: return ""
        val sb = StringBuilder(s.length)
        for (c in s) sb.append(if (c in '0'..'9') faDigits[c - '0'] else c)
        return sb.toString()
    }

    /** عدد اعشاری با حداکثر [digits] رقم، بدون صفرهای اضافه */
    fun d(v: Double, digits: Int = 1): String {
        val s = if (v == v.toLong().toDouble()) v.toLong().toString()
        else String.format(java.util.Locale.US, "%.${digits}f", v).removeSuffix("0").removeSuffix(".")
        return num(s)
    }

    fun kcal(v: Double): String = num(kotlin.math.round(v).toLong())

    /** وزن مثل ۷۸٫۵ */
    fun weight(v: Double): String = d(v, 1)

    fun gram(v: Double): String = d(v, 0)
}

/** محاسبات ساده روی روز (epochDay) */
object DayUtil {
    fun today(): Long = java.time.LocalDate.now().toEpochDay()
    fun date(dayId: Long): java.time.LocalDate = java.time.LocalDate.ofEpochDay(dayId)
    fun addDays(dayId: Long, n: Int): Long = dayId + n

    /** شروع هفته شمسی (شنبه) */
    fun startOfJalaliWeek(dayId: Long): Long {
        val d = date(dayId)
        // DayOfWeek: MONDAY=1..SUNDAY=7 ؛ شنبه در ایران آغاز هفته است
        val back = when (d.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> 0
            java.time.DayOfWeek.SUNDAY -> 1
            java.time.DayOfWeek.MONDAY -> 2
            java.time.DayOfWeek.TUESDAY -> 3
            java.time.DayOfWeek.WEDNESDAY -> 4
            java.time.DayOfWeek.THURSDAY -> 5
            else -> 6
        }
        return dayId - back
    }
}
