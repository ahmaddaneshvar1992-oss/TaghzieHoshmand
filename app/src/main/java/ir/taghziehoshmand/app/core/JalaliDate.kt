package ir.taghziehoshmand.app.core

import java.time.LocalDate
import java.time.DayOfWeek

/** تبدیل تاریخ میلادی ↔ شمسی و قالب‌بندی فارسی تاریخ */
object JalaliDate {

    val monthNames = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )

    val weekDayNames = mapOf(
        DayOfWeek.SATURDAY to "شنبه",
        DayOfWeek.SUNDAY to "یکشنبه",
        DayOfWeek.MONDAY to "دوشنبه",
        DayOfWeek.TUESDAY to "سه‌شنبه",
        DayOfWeek.WEDNESDAY to "چهارشنبه",
        DayOfWeek.THURSDAY to "پنجشنبه",
        DayOfWeek.FRIDAY to "جمعه"
    )

    private val gDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    private val jDays = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    /** خروجی: [سال، ماه، روز] شمسی */
    fun toJalali(gy0: Int, gm0: Int, gd0: Int): IntArray {
        val gy = gy0 - 1600
        var gDayNo = 365L * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        var i = 0
        while (i < gm0 - 1) { gDayNo += gDays[i]; i++ }
        if (gm0 > 2 && ((gy0 % 4 == 0 && gy0 % 100 != 0) || (gy0 % 400 == 0))) gDayNo++
        gDayNo += gd0 - 1
        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053
        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461
        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }
        i = 0
        while (i < 11 && jDayNo >= jDays[i]) { jDayNo -= jDays[i]; i++ }
        return intArrayOf(jy.toInt(), i + 1, (jDayNo + 1).toInt())
    }

    fun toJalali(d: LocalDate): IntArray = toJalali(d.year, d.monthValue, d.dayOfMonth)

    /** خروجی: [سال، ماه، روز] میلادی */
    fun toGregorian(jy0: Int, jm0: Int, jd0: Int): IntArray {
        val jy = jy0 - 979
        var jDayNo = 365L * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4
        var i = 0
        while (i < jm0 - 1) { jDayNo += jDays[i]; i++ }
        jDayNo += jd0 - 1
        var gDayNo = jDayNo + 79
        var gy = 1600 + 400 * (gDayNo / 146097)
        gDayNo %= 146097
        var leap = true
        if (gDayNo >= 36525) {
            gDayNo--
            gy += 100 * (gDayNo / 36524)
            gDayNo %= 36524
            if (gDayNo >= 365) gDayNo++ else leap = false
        }
        gy += 4 * (gDayNo / 1461)
        gDayNo %= 1461
        if (gDayNo >= 366) { leap = false; gDayNo--; gy += gDayNo / 365; gDayNo %= 365 }
        i = 0
        while (i < 12 && gDayNo >= gDays[i] + (if (i == 1 && leap) 1 else 0)) {
            gDayNo -= gDays[i] + (if (i == 1 && leap) 1 else 0)
            i++
        }
        return intArrayOf(gy.toInt(), i + 1, (gDayNo + 1).toInt())
    }

    /** مثال: ۱۸ آبان ۱۴۰۴ */
    fun full(d: LocalDate): String {
        val j = toJalali(d)
        return "${Fa.num(j[2])} ${monthNames[j[1] - 1]} ${Fa.num(j[0])}"
    }

    /** مثال: ۱۸ آبان */
    fun short(d: LocalDate): String {
        val j = toJalali(d)
        return "${Fa.num(j[2])} ${monthNames[j[1] - 1]}"
    }

    /** مثال: آبان ۱۴۰۴ */
    fun monthYear(d: LocalDate): String {
        val j = toJalali(d)
        return "${monthNames[j[1] - 1]} ${Fa.num(j[0])}"
    }

    /** مثال: شنبه */
    fun weekDay(d: LocalDate): String = weekDayNames[d.dayOfWeek] ?: ""

    /** شماره روز هفته: ۰=شنبه ... ۶=جمعه */
    fun weekDayIndex(d: LocalDate): Int = when (d.dayOfWeek) {
        DayOfWeek.SATURDAY -> 0
        DayOfWeek.SUNDAY -> 1
        DayOfWeek.MONDAY -> 2
        DayOfWeek.TUESDAY -> 3
        DayOfWeek.WEDNESDAY -> 4
        DayOfWeek.THURSDAY -> 5
        else -> 6
    }

    /** تعداد روزهای ماه شمسی */
    fun jalaliMonthLength(jy: Int, jm: Int): Int =
        if (jm <= 6) 31 else if (jm <= 11) 30 else if (isJalaliLeap(jy)) 30 else 29

    private fun isJalaliLeap(jy: Int): Boolean {
        val g = toGregorian(jy, 12, 30)
        val g2 = toGregorian(jy, 12, 29)
        return !(g[1] == 12 && g[2] == 30) || (g2[1] == 12 && g2[2] == 29)
    }
}
