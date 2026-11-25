package ir.taghziehoshmand.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.JalaliDate
import ir.taghziehoshmand.app.data.db.AppDatabase
import ir.taghziehoshmand.app.data.model.MealType
import ir.taghziehoshmand.app.data.prefs.Settings
import ir.taghziehoshmand.app.data.prefs.UserPrefs
import ir.taghziehoshmand.app.data.prefs.UserProfile
import ir.taghziehoshmand.app.service.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.roundToInt

/** ساختار فایل پشتیبان */
@Serializable
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val profile: String? = null,          // JSON پروفایل
    val settings: String? = null,
    val meals: List<MealBk> = emptyList(),
    val waters: List<WaterBk> = emptyList(),
    val exercises: List<ExBk> = emptyList(),
    val weights: List<WtBk> = emptyList(),
    val steps: List<StBk> = emptyList(),
    val customFoods: List<FoodBk> = emptyList(),
    val plan: List<PlanBk> = emptyList(),
    val reminders: List<RemBk> = emptyList()
)

@Serializable
data class MealBk(val dayId: Long, val mealType: Int, val name: String, val grams: Double, val kcal: Double, val protein: Double, val carb: Double, val fat: Double, val source: Int)
@Serializable
data class WaterBk(val dayId: Long, val ml: Int)
@Serializable
data class ExBk(val dayId: Long, val name: String, val minutes: Int, val met: Double, val kcal: Double)
@Serializable
data class WtBk(val dayId: Long, val weightKg: Double)
@Serializable
data class StBk(val dayId: Long, val steps: Int)
@Serializable
data class FoodBk(val name: String, val category: String, val kcal100: Double, val protein100: Double, val carb100: Double, val fat100: Double, val servingGrams: Int, val servingLabel: String, val barcode: String? = null)
@Serializable
data class PlanBk(val dayId: Long, val mealType: Int, val name: String, val grams: Double, val kcal: Double, val protein: Double, val carb: Double, val fat: Double, val done: Boolean)
@Serializable
data class RemBk(val type: Int, val title: String, val hour: Int, val minute: Int, val enabled: Boolean, val daysMask: Int)

/**
 * خروجی گزارش (PDF / Excel / CSV) و پشتیبان‌گیری کامل داده‌ها.
 * کاملاً آفلاین؛ فایل‌ها در پوشه داخلی exports ذخیره و با FileProvider به اشتراک گذاشته می‌شوند.
 */
class ExportManager(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: UserPrefs
) {
    val exportsDir: File = File(context.filesDir, "exports").apply { mkdirs() }

    private val json = Json { ignoreUnknownKeys = true }

    private fun titleDate(from: Long, to: Long): String {
        val f = JalaliDate.full(DayUtil.date(from))
        val t = JalaliDate.full(DayUtil.date(to))
        return if (from == to) f else "$f تا $t"
    }

    private suspend fun collect(from: Long, to: Long): Triple<List<PdfTable>, List<String>, List<List<String>>> {
        val meals = db.mealDao().between(from, to)
        val weights = db.weightDao().between(from, to)
        val waters = db.waterDao().between(from, to)
        val exercises = db.exerciseDao().between(from, to)

        val mealRows = meals.map {
            listOf(
                JalaliDate.short(DayUtil.date(it.dayId)),
                MealType.names[it.mealType],
                it.name,
                Fa.gram(it.grams) + " گرم",
                Fa.kcal(it.kcal),
                Fa.d(it.protein), Fa.d(it.carb), Fa.d(it.fat)
            )
        }
        val mealTable = PdfTable(
            title = "وعده‌های غذایی ثبت‌شده",
            headers = listOf("تاریخ", "وعده", "غذا", "مقدار", "کالری", "پروتئین", "کربو", "چربی"),
            rows = mealRows,
            weights = listOf(1.1f, 0.9f, 2.4f, 1.1f, 1f, 0.9f, 0.9f, 0.9f)
        )

        val weightRows = weights.map {
            listOf(JalaliDate.short(DayUtil.date(it.dayId)), Fa.weight(it.weightKg) + " کیلوگرم", it.note ?: "")
        }
        val waterRows = waters.map {
            listOf(JalaliDate.short(DayUtil.date(it.dayId)), Fa.num(it.ml) + " میلی‌لیتر")
        }
        val exRows = exercises.map {
            listOf(JalaliDate.short(DayUtil.date(it.dayId)), it.name, Fa.num(it.minutes) + " دقیقه", Fa.kcal(it.kcal) + " کیلوکالری")
        }

        val tables = buildList {
            if (mealRows.isNotEmpty()) add(mealTable)
            if (weightRows.isNotEmpty()) add(PdfTable("سنجش‌های وزن", listOf("تاریخ", "وزن", "یادداشت"), weightRows, listOf(1f, 1.2f, 2.2f)))
            if (waterRows.isNotEmpty()) add(PdfTable("مصرف آب", listOf("تاریخ", "میزان"), waterRows, listOf(1f, 1.4f)))
            if (exRows.isNotEmpty()) add(PdfTable("فعالیت‌های ورزشی", listOf("تاریخ", "فعالیت", "مدت", "کالری سوزانده"), exRows, listOf(1f, 1.6f, 1f, 1.2f)))
        }

        val days = (from..to).count()
        val totalKcal = meals.sumOf { it.kcal }
        val avgKcal = if (days > 0 && meals.isNotEmpty()) totalKcal / days.coerceAtLeast(1) else 0.0
        val wDiff = if (weights.size >= 2) weights.last().weightKg - weights.first().weightKg else null
        val summary = listOf(
            "بازه گزارش: ${titleDate(from, to)}",
            "تعداد وعده‌های ثبت‌شده: ${Fa.num(meals.size)}",
            "میانگین کالری روزانه: ${Fa.kcal(avgKcal)} کیلوکالری",
            wDiff?.let { "تغییر وزن در بازه: ${Fa.weight(kotlin.math.abs(it))} کیلوگرم (${if (it < 0) "کاهش" else "افزایش"})" },
            "کل کالری سوزانده‌شده با ورزش: ${Fa.kcal(exercises.sumOf { it.kcal })} کیلوکالری",
            "کل آب مصرفی: ${Fa.num(waters.sumOf { it.ml })} میلی‌لیتر"
        ).filterNotNull()

        val csvRows = listOf(
            listOf("تاریخ", "وعده", "غذا", "مقدار(گرم)", "کالری", "پروتئین(گرم)", "کربوهیدرات(گرم)", "چربی(گرم)")
        ) + meals.map {
            listOf(
                JalaliDate.full(DayUtil.date(it.dayId)),
                MealType.names[it.mealType],
                it.name,
                it.grams.roundToInt().toString(),
                it.kcal.roundToInt().toString(),
                it.protein.toString(), it.carb.toString(), it.fat.toString()
            )
        }
        return Triple(tables, summary, csvRows)
    }

    suspend fun exportPdf(from: Long, to: Long): File? {
        val (tables, summary, _) = collect(from, to)
        if (tables.isEmpty()) return null
        val file = File(exportsDir, "taghzie_report_${from}_${to}.pdf")
        val ok = PdfExporter().export(
            file,
            title = "گزارش تغذیه هوشمند",
            subtitle = titleDate(from, to),
            summaryLines = summary,
            tables = tables
        )
        return if (ok) file else null
    }

    suspend fun exportXlsx(from: Long, to: Long): File? {
        val (_, _, csvRows) = collect(from, to)
        val weights = db.weightDao().between(from, to)
        val exercises = db.exerciseDao().between(from, to)
        val waters = db.waterDao().between(from, to)
        if (csvRows.size <= 1 && weights.isEmpty() && exercises.isEmpty() && waters.isEmpty()) return null
        val file = File(exportsDir, "taghzie_report_${from}_${to}.xlsx")
        MiniXlsx.write(
            file,
            listOf(
                "وعده‌های غذایی" to csvRows,
                "وزن" to (listOf(listOf("تاریخ", "وزن (کیلوگرم)")) + weights.map { listOf(JalaliDate.full(DayUtil.date(it.dayId)), it.weightKg.toString()) }),
                "آب" to (listOf(listOf("تاریخ", "میزان (میلی‌لیتر)")) + waters.map { listOf(JalaliDate.full(DayUtil.date(it.dayId)), it.ml.toString()) }),
                "ورزش" to (listOf(listOf("تاریخ", "فعالیت", "دقیقه", "کالری")) + exercises.map { listOf(JalaliDate.full(DayUtil.date(it.dayId)), it.name, it.minutes.toString(), it.kcal.roundToInt().toString()) })
            )
        )
        return file
    }

    suspend fun exportCsv(from: Long, to: Long): File? {
        val (_, _, rows) = collect(from, to)
        if (rows.size <= 1) return null
        val file = File(exportsDir, "taghzie_report_${from}_${to}.csv")
        file.writeText("\uFEFF" + rows.joinToString("\r\n") { r ->
            r.joinToString(",") { c -> if (c.contains(',') || c.contains('"')) "\"${c.replace("\"", "\"\"")}\"" else c }
        }, Charsets.UTF_8)
        return file
    }

    /** پشتیبان‌گیری کامل به یک Uri (ذخیره‌سازی توسط کاربر) */
    suspend fun backupTo(uri: Uri): Boolean {
        val profile = runCatching { prefs.profileFlow.first() }.getOrNull()
        val data = BackupData(
            profile = profile?.let { json.encodeToString(it) },
            settings = runCatching { json.encodeToString(prefs.settingsFlow.first()) }.getOrNull(),
            meals = db.mealDao().between(0, Long.MAX_VALUE).map { MealBk(it.dayId, it.mealType, it.name, it.grams, it.kcal, it.protein, it.carb, it.fat, it.source) },
            waters = db.waterDao().between(0, Long.MAX_VALUE).map { WaterBk(it.dayId, it.ml) },
            exercises = db.exerciseDao().between(0, Long.MAX_VALUE).map { ExBk(it.dayId, it.name, it.minutes, it.met, it.kcal) },
            weights = db.weightDao().between(0, Long.MAX_VALUE).map { WtBk(it.dayId, it.weightKg) },
            steps = db.stepDao().between(0, Long.MAX_VALUE).map { StBk(it.dayId, it.steps) },
            customFoods = db.foodDao().customs().first().map {
                FoodBk(it.name, it.category, it.kcal100, it.protein100, it.carb100, it.fat100, it.servingGrams, it.servingLabel, it.barcode)
            },
            plan = db.planDao().range(0, Long.MAX_VALUE).first().map {
                PlanBk(it.dayId, it.mealType, it.name, it.grams, it.kcal, it.protein, it.carb, it.fat, it.done)
            },
            reminders = db.reminderDao().all().first().map {
                RemBk(it.type, it.title, it.hour, it.minute, it.enabled, it.daysMask)
            }
        )
        return try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.encodeToString(BackupData.serializer(), data).toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            false
        }
    }

    /** بازیابی از فایل پشتیبان — جایگزینی کامل داده‌ها + بازیابی پروفایل/تنظیمات + زمان‌بندی مجدد یادآورها */
    suspend fun restoreFrom(uri: Uri): Boolean {
        return try {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: return false
            val data = json.decodeFromString(BackupData.serializer(), text)
            val dbx = db
            // پاک‌سازی جدول‌های داده — بازیابی بدون تکرار، حتی اگر چند بار اجرا شود
            dbx.mealDao().clearAll()
            dbx.waterDao().clearAll()
            dbx.exerciseDao().clearAll()
            dbx.weightDao().clearAll()
            dbx.planDao().clearAll()
            dbx.foodDao().clearCustoms()
            dbx.reminderDao().clearAll()
            dbx.mealDao().let { d ->
                data.meals.forEach { d.insert(ir.taghziehoshmand.app.data.model.MealEntry(dayId = it.dayId, mealType = it.mealType, name = it.name, grams = it.grams, kcal = it.kcal, protein = it.protein, carb = it.carb, fat = it.fat, source = it.source)) }
            }
            data.weights.forEach { dbx.weightDao().insert(ir.taghziehoshmand.app.data.model.WeightLog(dayId = it.dayId, weightKg = it.weightKg)) }
            data.waters.forEach { dbx.waterDao().insert(ir.taghziehoshmand.app.data.model.WaterLog(dayId = it.dayId, ml = it.ml)) }
            data.exercises.forEach { dbx.exerciseDao().insert(ir.taghziehoshmand.app.data.model.ExerciseLog(dayId = it.dayId, name = it.name, minutes = it.minutes, met = it.met, kcal = it.kcal)) }
            data.steps.forEach { dbx.stepDao().set(ir.taghziehoshmand.app.data.model.StepLog(it.dayId, it.steps)) }
            data.customFoods.forEach {
                dbx.foodDao().insert(
                    ir.taghziehoshmand.app.data.model.Food(name = it.name, category = it.category, kcal100 = it.kcal100, protein100 = it.protein100, carb100 = it.carb100, fat100 = it.fat100, servingGrams = it.servingGrams, servingLabel = it.servingLabel, barcode = it.barcode, isCustom = true)
                )
            }
            dbx.planDao().insertAll(data.plan.map { ir.taghziehoshmand.app.data.model.PlanItem(dayId = it.dayId, mealType = it.mealType, name = it.name, grams = it.grams, kcal = it.kcal, protein = it.protein, carb = it.carb, fat = it.fat, done = it.done) })
            data.reminders.forEach { dbx.reminderDao().insert(ir.taghziehoshmand.app.data.model.Reminder(type = it.type, title = it.title, hour = it.hour, minute = it.minute, enabled = it.enabled, daysMask = it.daysMask)) }
            // پروفایل و تنظیمات — بکاپ‌های نسخه‌های قدیمی بدون این فیلدها هستند و رد می‌شوند
            data.profile?.let { p -> runCatching { prefs.saveProfile(json.decodeFromString<UserProfile>(p)) } }
            data.settings?.let { s -> runCatching { prefs.saveSettings(json.decodeFromString<Settings>(s)) } }
            prefs.setOnboarded(true)
            // یادآورهای بازیابی‌شده دوباره زمان‌بندی شوند
            ReminderScheduler(context, dbx.reminderDao()).rescheduleAll()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** اینتنت اشتراک/ذخیره فایل */
    fun shareIntent(file: File, mime: String): Intent {
        val uri: Uri = FileProvider.getUriForFile(context, "ir.taghziehoshmand.app.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
