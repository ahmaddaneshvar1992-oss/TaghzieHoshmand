package ir.taghziehoshmand.app.ai

import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.db.FoodDao
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.model.MealType
import ir.taghziehoshmand.app.data.model.PlanItem
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * تولید برنامه غذایی روزانه/هفتگی شخصی‌سازی‌شده بر اساس کالری هدف کاربر.
 * کاملاً آفلاین و بر پایه پایگاه داده غذاهای ایرانی و بین‌المللی کار می‌کند.
 */
class MealPlanner(private val foodDao: FoodDao) {

    private fun mealPools(foods: List<Food>): Map<Int, List<Food>> {
        val usable = foods.filter { it.kcal100 > 15 && it.kcal100 < 900 }
        fun cat(vararg names: String) = usable.filter { it.category in names }
        return mapOf(
            MealType.BREAKFAST to cat("صبحانه", "لبنیات", "حبوبات", "غلات و نشاسته", "آجیل و دانه", "میوه‌ها"),
            MealType.LUNCH to cat("غذاهای ایرانی", "سوپ و خورش", "غلات و نشاسته", "گوشت و پروتئین", "حبوبات", "غذاهای بین‌المللی"),
            MealType.DINNER to cat("سوپ و خورش", "گوشت و پروتئین", "لبنیات", "سبزیجات", "غذاهای بین‌المللی", "غذاهای ایرانی", "صبحانه"),
            MealType.SNACK to cat("میوه‌ها", "آجیل و دانه", "شیرینی و تنقلات", "لبنیات", "نوشیدنی‌ها", "سبزیجات")
        )
    }

    private fun share(mealType: Int) = when (mealType) {
        MealType.BREAKFAST -> 0.25
        MealType.LUNCH -> 0.35
        MealType.DINNER -> 0.30
        else -> 0.10
    }

    private fun pick(pool: List<Food>, rnd: Random): Food? {
        if (pool.isEmpty()) return null
        // انتخاب وزنی: غذاهای پرتکرار شانس بیشتر
        val total = pool.sumOf { it.commonWeight.toDouble() }
        var r = rnd.nextDouble() * total
        for (f in pool) {
            r -= f.commonWeight
            if (r <= 0) return f
        }
        return pool.last()
    }

    private fun gramsFor(food: Food, kcalTarget: Double): Double =
        (kcalTarget * 100.0 / food.kcal100).coerceIn(25.0, 550.0).roundToInt().toDouble()

    /** ساخت برنامه برای یک روز */
    suspend fun dayPlan(dayId: Long, targetKcal: Double): List<PlanItem> {
        val foods = foodDao.pool()
        if (foods.isEmpty()) return emptyList()
        val pools = mealPools(foods)
        val rnd = Random(dayId * 31 + targetKcal.roundToInt())
        val items = mutableListOf<PlanItem>()
        for (meal in 0..3) {
            val mealTarget = targetKcal * share(meal)
            val pool = pools[meal] ?: continue
            if (pool.isEmpty()) continue
            val main = pick(pool, rnd) ?: continue
            var spent = main.kcal100 * gramsFor(main, mealTarget) / 100
            items.add(item(dayId, meal, main, gramsFor(main, mealTarget)))
            // اگر غذا کالری کم داشت، یک همراه اضافه کن (مثل برنج با خورش)
            val left = mealTarget - spent
            if (left > 90 && meal != MealType.SNACK) {
                val sidePool = pool.filter { it.id != main.id && it.kcal100 >= main.kcal100 * 0.5 }
                val side = pick(sidePool.ifEmpty { pool }, rnd)
                if (side != null) {
                    val g = gramsFor(side, left)
                    items.add(item(dayId, meal, side, g))
                    spent += side.kcal100 * g / 100
                }
            }
        }
        return items
    }

    /** برنامه هفتگی (۷ روز) */
    suspend fun weeklyPlan(startDay: Long, targetKcal: Double): List<PlanItem> {
        val out = mutableListOf<PlanItem>()
        repeat(7) { i -> out.addAll(dayPlan(startDay + i, targetKcal)) }
        return out
    }

    /** جایگزین یک آیتم برنامه با غذای مشابه از پایگاه داده */
    suspend fun swapItem(item: PlanItem): Food? {
        val foods = foodDao.pool()
        val pools = mealPools(foods)
        val pool = pools[item.mealType] ?: return null
        val candidates = pool.filter {
            it.id != item.id && it.kcal100 > item.kcal / item.grams * 100 * 0.55 &&
                it.kcal100 < item.kcal / item.grams * 100 * 1.45 &&
                it.name != item.name
        }
        val best = candidates.shuffled().firstOrNull() ?: pool.shuffled().firstOrNull() ?: return null
        return best
    }

    private fun item(dayId: Long, meal: Int, f: Food, grams: Double) = PlanItem(
        dayId = dayId, mealType = meal,
        name = f.name, grams = grams,
        kcal = f.kcal100 * grams / 100,
        protein = f.protein100 * grams / 100,
        carb = f.carb100 * grams / 100,
        fat = f.fat100 * grams / 100
    )

    /** متنی برای نمایش/چت: مثل طراحی ربات */
    suspend fun planText(targetKcal: Double, dayId: Long): String {
        val items = dayPlan(dayId, targetKcal)
        if (items.isEmpty()) return "پایگاه داده غذا آماده نیست؛ چند لحظه بعد دوباره امتحان کنید."
        val sb = StringBuilder("برنامه غذایی امروز با هدف ${Fa.kcal(targetKcal)} کیلوکالری:\n")
        var lastMeal = -1
        for (it in items) {
            if (it.mealType != lastMeal) {
                sb.append("\n${MealType.names[it.mealType]}:\n")
                lastMeal = it.mealType
            }
            sb.append("• ${it.name} — ${Fa.gram(it.grams)} گرم (تقریباً ${Fa.kcal(it.kcal)} کالری)\n")
        }
        sb.append("\nنکته: نان و برنج را به میزان گفته‌شده محدود کنید و سبزی را کم نکنید.")
        return sb.toString()
    }
}
