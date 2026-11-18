package ir.taghziehoshmand.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** انواع وعده غذایی */
object MealType {
    const val BREAKFAST = 0
    const val LUNCH = 1
    const val DINNER = 2
    const val SNACK = 3
    val names = listOf("صبحانه", "ناهار", "شام", "میان‌وعده")
}

/** منبع ثبت غذا */
object FoodSource {
    const val DATABASE = 0
    const val CUSTOM = 1
    const val BARCODE = 2
    const val AI_PHOTO = 3
}

/** آیتم پایگاه داده غذاها (ایرانی و بین‌المللی + غذاهای شخصی) */
@Entity(tableName = "foods")
data class Food(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val kcal100: Double,
    val protein100: Double,
    val carb100: Double,
    val fat100: Double,
    val servingGrams: Int,
    val servingLabel: String,
    val commonWeight: Int = 1,        // میزان رواج ۱ تا ۵
    val isCustom: Boolean = false,    // غذای شخصی کاربر
    val barcode: String? = null,      // بارکد محصول
    val favorite: Boolean = false,    // ستاره‌دار برای ثبت سریع
    val useCount: Int = 0             // شمارش استفاده (ثبت‌های پرمصرف)
)

/** ثبت یک غذا در وعده روز */
@Entity(tableName = "meal_entries")
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,                  // epochDay
    val mealType: Int,                // MealType
    val foodId: Long? = null,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double,
    val source: Int = FoodSource.DATABASE,
    val createdAt: Long = System.currentTimeMillis()
)

/** ثبت آب روزانه (میلی‌لیتر) */
@Entity(tableName = "water_logs")
data class WaterLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val ml: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/** ثبت فعالیت ورزشی */
@Entity(tableName = "exercise_logs")
data class ExerciseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val name: String,
    val minutes: Int,
    val met: Double,
    val kcal: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/** سنجش وزن */
@Entity(tableName = "weight_logs")
data class WeightLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val weightKg: Double,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** گالری عکس پیشرفت بدن */
@Entity(tableName = "photos")
data class ProgressPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val filePath: String,
    val label: String = "",
    val weightKg: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/** پیام‌های چت هوش مصنوعی */
@Entity(tableName = "chat")
data class ChatMsg(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: Int,                    // 0 کاربر، 1 ربات
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)

/** برنامه غذایی هفتگی */
@Entity(tableName = "plan_items")
data class PlanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val mealType: Int,
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double,
    val done: Boolean = false
)

/** یادآورها */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,                    // 0 وعده، 1 آب، 2 وزن
    val title: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val daysMask: Int = 127,          // بیت‌های شنبه..جمعه
    val createdAt: Long = System.currentTimeMillis()
)

/** قدم‌های روز (ثبت دستی یا از Health Connect) */
@Entity(tableName = "steps")
data class StepLog(
    @PrimaryKey val dayId: Long,
    val steps: Int
)

/** جمع کل روزانه برای نمودارها */
data class DayTotal(
    val dayId: Long,
    val kcal: Double,
    val protein: Double,
    val carb: Double,
    val fat: Double
)
