package ir.taghziehoshmand.app.core

import kotlinx.serialization.Serializable

@Serializable
enum class Gender { MALE, FEMALE }

@Serializable
enum class ActivityLevel(val label: String, val factor: Double) {
    SEDENTARY("کم‌تحرک (کار پشت میز)", 1.2),
    LIGHT("سبک (ورزش ۱ تا ۳ روز در هفته)", 1.375),
    MODERATE("متوسط (ورزش ۳ تا ۵ روز در هفته)", 1.55),
    ACTIVE("زیاد (ورزش ۶ تا ۷ روز در هفته)", 1.725),
    VERY_ACTIVE("خیلی زیاد (کار فیزیکی سنگین)", 1.9)
}

@Serializable
enum class Goal(val label: String) {
    LOSE("کاهش وزن"),
    KEEP("حفظ وزن"),
    GAIN("افزایش وزن")
}

/** نتایج محاسبات بدنی */
data class HealthResults(
    val bmi: Double,
    val bmiLabel: String,
    val bmr: Double,
    val tdee: Double,
    val targetKcal: Double,
    val proteinG: Double,
    val carbG: Double,
    val fatG: Double,
    val waterGoalMl: Int,
    val idealWeight: Double
)

object HealthCalc {

    fun bmi(weightKg: Double, heightCm: Double): Double {
        if (heightCm <= 0 || weightKg <= 0) return 0.0
        val h = heightCm / 100.0
        return weightKg / (h * h)
    }

    fun bmiLabel(bmi: Double): String = when {
        bmi <= 0 -> "نامشخص"
        bmi < 18.5 -> "کم‌وزن"
        bmi < 25 -> "وزن نرمال"
        bmi < 30 -> "اضافه‌وزن"
        else -> "چاقی"
    }

    fun bmiColor(bmi: Double): Int = when {
        bmi < 18.5 -> 2   // فیروزه‌ای
        bmi < 25 -> 1     // سبز
        bmi < 30 -> 3     // کهربایی
        else -> 4         // قرمز
    }

    /** مترم Mifflin–St Jeor */
    fun bmr(weightKg: Double, heightCm: Double, age: Int, gender: Gender): Double {
        if (weightKg <= 0 || heightCm <= 0 || age <= 0) return 0.0
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * age
        return if (gender == Gender.MALE) base + 5 else base - 161
    }

    fun tdee(bmr: Double, level: ActivityLevel): Double = bmr * level.factor

    /** کالری هدف بر اساس هدف وزن */
    fun targetKcal(tdee: Double, goal: Goal, gender: Gender = Gender.MALE): Double = when (goal) {
        Goal.LOSE -> (tdee - 500).coerceAtLeast(if (gender == Gender.FEMALE) 1200.0 else 1500.0)
        Goal.KEEP -> tdee
        Goal.GAIN -> tdee + 300
    }

    /** اهداف ماکرو (گرم) — پروتئین بر اساس وزن بدن، چربی درصد ثابت، بقیه کربوهیدرات */
    fun macros(targetKcal: Double, weightKg: Double, goal: Goal): Triple<Double, Double, Double> {
        val proteinPerKg = when (goal) {
            Goal.LOSE -> 2.0
            Goal.KEEP -> 1.6
            Goal.GAIN -> 1.8
        }
        val fatPerKg = when (goal) {
            Goal.LOSE -> 0.8
            else -> 1.0
        }
        var protein = proteinPerKg * weightKg
        var fat = fatPerKg * weightKg
        // محدودیت: پروتئین+چربی حداکثر ۶۵٪ کالری
        val maxSumKcal = targetKcal * 0.65
        val sumKcal = protein * 4 + fat * 9
        if (sumKcal > maxSumKcal && sumKcal > 0) {
            val scale = maxSumKcal / sumKcal
            protein *= scale
            fat *= scale
        }
        val carb = ((targetKcal - protein * 4 - fat * 9) / 4).coerceAtLeast(0.0)
        return Triple(kotlin.math.round(protein), kotlin.math.round(carb), kotlin.math.round(fat))
    }

    fun waterGoal(weightKg: Double): Int =
        ((weightKg * 35).toInt() / 250 * 250).coerceIn(1500, 4000)

    fun idealWeight(heightCm: Double): Double {
        if (heightCm <= 0) return 0.0
        val h = heightCm / 100.0
        return kotlin.math.round(22.0 * h * h * 10) / 10
    }

    fun calculate(
        weightKg: Double, heightCm: Double, age: Int, gender: Gender,
        level: ActivityLevel, goal: Goal
    ): HealthResults {
        val bmi = bmi(weightKg, heightCm)
        val bmr = bmr(weightKg, heightCm, age, gender)
        val tdee = tdee(bmr, level)
        val target = targetKcal(tdee, goal, gender)
        val (p, c, f) = macros(target, weightKg, goal)
        return HealthResults(
            bmi = bmi,
            bmiLabel = bmiLabel(bmi),
            bmr = kotlin.math.round(bmr),
            tdee = kotlin.math.round(tdee),
            targetKcal = kotlin.math.round(target),
            proteinG = p,
            carbG = c,
            fatG = f,
            waterGoalMl = waterGoal(weightKg),
            idealWeight = idealWeight(heightCm)
        )
    }
}
