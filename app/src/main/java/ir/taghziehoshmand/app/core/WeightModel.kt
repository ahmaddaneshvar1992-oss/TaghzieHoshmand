package ir.taghziehoshmand.app.core

import kotlin.math.round
import kotlin.math.sqrt

/**
 * موتور علمی وزن — ویژگی‌های نسخه ۱.۵:
 *  • هموارسازی EWMA برای حذف نوسان وزن روزانه (آب، نمک، روده)
 *  • رگرسیون خطی روی سنجش‌ها برای شیب واقعی روند
 *  • TDEE تطبیقی از موازنه انرژی واقعی (دریافت کالری − ذخیره انرژی وزن)
 *  • پیش‌بینی وزن آینده با بازه اطمینان
 *
 * همه توابع «خالص» هستند تا قابل آزمون و استفاده در UI و موتور AI باشند.
 */
object WeightModel {

    /** یک سنجش وزن: epochDay و وزن بر حسب کیلوگرم */
    data class Sample(val dayId: Long, val weightKg: Double)

    /** نتیجه رگرسیون خطی روی سنجش‌ها */
    data class Trend(
        val slopeKgPerDay: Double,   // شیب روند وزن (منفی = کاهش)
        val residualStd: Double,     // انحراف معیار باقیمانده‌ها (نوسان پیرامون خط)
        val ewmaLast: Double,        // آخرین وزن هموارشده (مبنای پیش‌بینی)
        val samples: Int             // تعداد سنجش‌ها
    ) {
        /** تغییر وزن در یک هفته بر اساس روند (کیلوگرم) */
        val weeklyChangeKg: Double get() = slopeKgPerDay * 7.0
    }

    /** هموارسازی نمایی ساده با آلفا = ۰٫۳ — وزن آخر به‌عنوان نقطه شروع */
    private fun ewma(weights: List<Double>): Double {
        var s = weights.first()
        for (w in weights.drop(1)) s = 0.3 * w + 0.7 * s
        return s
    }

    /**
     * رگرسیون خطی وزن بر حسب زمان (روز). حداقل ۳ سنجش لازم است.
     * x به «روز اول» نرمال می‌شود برای پایداری عددی epochDay بزرگ.
     */
    fun regression(samples: List<Sample>): Trend? {
        if (samples.size < 3) return null
        val ordered = samples.sortedBy { it.dayId }
        val x0 = ordered.first().dayId
        val xs = ordered.map { (it.dayId - x0).toDouble() }
        val ys = ordered.map { it.weightKg }
        val mx = xs.average()
        val my = ys.average()
        var sxy = 0.0
        var sxx = 0.0
        for (i in xs.indices) {
            sxy += (xs[i] - mx) * (ys[i] - my)
            sxx += (xs[i] - mx) * (xs[i] - mx)
        }
        if (sxx < 1e-9) return null
        val slope = sxy / sxx
        val residuals = ys.indices.map { ys[it] - (slope * xs[it] + my) }
        val dof = (xs.size - 2).coerceAtLeast(1)
        val residualStd = sqrt(residuals.sumOf { it * it } / dof).coerceAtLeast(0.05)
        return Trend(
            slopeKgPerDay = slope,
            residualStd = residualStd,
            ewmaLast = ewma(ys),
            samples = xs.size
        )
    }

    /** برآورد TDEE تطبیقی از موازنه انرژی واقعی */
    data class AdaptiveTdee(
        val tdee: Double,           // مقدار نهایی (ترکیب فرمول + مشاهده)
        val observed: Double,       // برآورد صرفاً از داده‌ها
        val confidence: Double,     // ۰ تا ۱ — بر اساس حجم و طول داده
        val formulaTdee: Double     // مقدار فرمول Mifflin برای مقایسه
    )

    /**
     * TDEE تطبیقی (ایده ۷):
     * ۱) از موازنه انرژی: TDEE واقعی ≈ میانگین دریافت کالری − ۷۷۰۰ × شیب وزن (kg/day)
     *    (۷۷۰۰ کیلوکالری ≈ انرژی ذخیره‌شده در هر کیلوگرم بافت بدن)
     * ۲) محدودسازی منطقی به ±۳۰٪ مقدار فرمول (حفاظ در برابر داده پرنوسان)
     * ۳) ترکیب وزن‌دار با مقدار فرمول؛ اعتماد با تعداد روز داده بالا می‌رود.
     *
     * @param avgIntakeKcal میانگین دریافت کالری در همان بازه سنجش وزن
     * @param daysSpan      طول بازه داده (روز) — برای اعتماد
     */
    fun adaptiveTdee(avgIntakeKcal: Double, trend: Trend?, formulaTdee: Double, daysSpan: Int): AdaptiveTdee? {
        if (trend == null || trend.samples < 3 || daysSpan < 10 || avgIntakeKcal <= 0 || formulaTdee <= 0) return null
        val observed = avgIntakeKcal - 7700.0 * trend.slopeKgPerDay
        val clamped = observed.coerceIn(formulaTdee * 0.7, formulaTdee * 1.3)
        val confidence = ((daysSpan / 42.0) * 0.8 + 0.2).coerceIn(0.2, 1.0)
        val blended = formulaTdee * (1 - confidence) + clamped * confidence
        return AdaptiveTdee(
            tdee = round(blended),
            observed = round(observed),
            confidence = confidence,
            formulaTdee = formulaTdee
        )
    }

    /** پیش‌بینی وزن (ایده ۸) */
    data class Forecast(
        val lastDayId: Long,        // روز آخرین سنجش
        val startWeightKg: Double,  // وزن هموارشده آخرین سنجش
        val slopeKgPerDay: Double,
        val residualStd: Double
    ) {
        /** وزن پیش‌بینی‌شده [days] روز بعد از آخرین سنجش */
        fun predictedKg(days: Int): Double = startWeightKg + slopeKgPerDay * days

        /** شعاع بازه اطمینان (±) برای [days] روز بعد — با رشد مربعیِ زمان */
        fun bandKg(days: Int): Double =
            1.96 * residualStd * sqrt((days / 14.0).coerceAtLeast(0.15))

        /**
         * روزهای باقی‌مانده تا وزن هدف اگر روند در جهت هدف ادامه یابد؛
         * اگر روند مخالف هدف باشد یا هدف بی‌معنا باشد null.
         */
        fun daysToGoal(goalWeightKg: Double, goalIsLose: Boolean): Int? {
            if (goalWeightKg <= 0) return null
            val diff = goalWeightKg - startWeightKg
            val movingToward = if (goalIsLose) slopeKgPerDay < -0.01 else slopeKgPerDay > 0.01
            if (!movingToward || diff == 0.0) return null
            if (goalIsLose && diff > 0) return null
            if (!goalIsLose && diff < 0) return null
            // |فاصله تا هدف| ÷ |سرعت تغییر وزن|
            val days = (kotlin.math.abs(diff) / kotlin.math.abs(slopeKgPerDay)).roundToInt()
            return if (days in 1..730) days else null
        }

        private fun Double.roundToInt(): Int = round(this).toInt()
    }

    fun forecast(samples: List<Sample>): Forecast? {
        val t = regression(samples) ?: return null
        val last = samples.maxByOrNull { it.dayId } ?: return null
        return Forecast(last.dayId, t.ewmaLast, t.slopeKgPerDay, t.residualStd)
    }

    /** متن فارسی شیب روند برای نمایش و کانتکست AI */
    fun trendText(t: Trend): String {
        val perWeek = t.weeklyChangeKg
        return when {
            kotlin.math.abs(perWeek) < 0.05 ->
                "وزن تقریباً ثابت است (${Fa.weight(t.ewmaLast)} کیلوگرم)"
            perWeek < 0 ->
                "روند کاهش ${Fa.weight(-perWeek)} کیلوگرم در هفته (وزن فعلی هموارشده: ${Fa.weight(t.ewmaLast)})"
            else ->
                "روند افزایش ${Fa.weight(perWeek)} کیلوگرم در هفته (وزن فعلی هموارشده: ${Fa.weight(t.ewmaLast)})"
        }
    }
}
