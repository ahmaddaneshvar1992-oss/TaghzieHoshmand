package ir.taghziehoshmand.app.data.repo

import android.content.Context
import ir.taghziehoshmand.app.data.db.AppDatabase
import ir.taghziehoshmand.app.data.db.FoodDao
import ir.taghziehoshmand.app.data.model.Food
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class FoodDto(
    @SerialName("n") val n: String,
    @SerialName("c") val c: String,
    @SerialName("k") val k: Double,
    @SerialName("p") val p: Double,
    @SerialName("cb") val cb: Double,
    @SerialName("f") val f: Double,
    @SerialName("s") val s: Int,
    @SerialName("sn") val sn: String,
    @SerialName("w") val w: Int
)

@Serializable
data class ExerciseDto(
    @SerialName("n") val n: String,
    @SerialName("m") val m: Double,
    @SerialName("c") val c: String
)

/** بارگذاری و نگهداری پایگاه داده غذاها و ورزش‌ها */
class FoodCatalog(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private var exercisesCache: List<ExerciseDto>? = null

    /** پرکردن پایگاه داده در اولین اجرا */
    suspend fun seedFoods(foodDao: FoodDao) {
        if (foodDao.count() > 0) return
        val raw = runCatching { context.assets.open("foods_fa.json").bufferedReader().use { it.readText() } }
            .getOrNull() ?: return
        val items = runCatching { json.decodeFromString<List<FoodDto>>(raw) }.getOrNull() ?: return
        val foods = items.map {
            Food(
                name = it.n, category = it.c,
                kcal100 = it.k, protein100 = it.p, carb100 = it.cb, fat100 = it.f,
                servingGrams = it.s, servingLabel = it.sn, commonWeight = it.w
            )
        }
        foodDao.insertAll(foods)
    }

    /** لیست فعالیت‌های ورزشی (از assets) */
    suspend fun exercises(): List<ExerciseDto> {
        exercisesCache?.let { return it }
        val raw = runCatching { context.assets.open("exercises_fa.json").bufferedReader().use { it.readText() } }
            .getOrNull() ?: return emptyList()
        val list = runCatching { json.decodeFromString<List<ExerciseDto>>(raw) }.getOrNull() ?: emptyList()
        exercisesCache = list
        return list
    }

    companion object {
        val categoryIcons = mapOf(
            "غذاهای ایرانی" to "🇮🇷",
            "غلات و نشاسته" to "🍚",
            "گوشت و پروتئین" to "🍗",
            "حبوبات" to "🫘",
            "لبنیات" to "🧀",
            "میوه‌ها" to "🍎",
            "سبزیجات" to "🥗",
            "آجیل و دانه" to "🥜",
            "نوشیدنی‌ها" to "🥤",
            "شیرینی و تنقلات" to "🍰",
            "سوپ و خورش" to "🍲",
            "فست‌فود" to "🍔",
            "صبحانه" to "🍳",
            "غذاهای دریایی" to "🐟",
            "غذاهای بین‌المللی" to "🌍",
            "سس و چاشنی" to "🧂"
        )
    }
}
