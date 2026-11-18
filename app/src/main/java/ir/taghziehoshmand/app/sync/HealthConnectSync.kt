package ir.taghziehoshmand.app.sync

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import ir.taghziehoshmand.app.data.db.AppDatabase
import ir.taghziehoshmand.app.data.prefs.UserPrefs
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * همگام‌سازی با Health Connect (جایگزین رسمی و مدرن Google Fit در اندروید).
 * وزن، آب مصرفی، کالری سوزانده‌شده و قدم‌ها با اجازه کاربر در Health Connect ثبت می‌شوند.
 */
class HealthConnectSync(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: UserPrefs
) {

    fun available(): Boolean =
        runCatching { HealthConnectClient.getOrCreate(context); true }.getOrDefault(false)

    private fun client(): HealthConnectClient? =
        if (available()) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    /** ارسال داده‌های یک روز به Health Connect */
    suspend fun syncDay(dayId: Long): SyncResult {
        val client = client() ?: return SyncResult(false, "Health Connect روی این دستگاه در دسترس نیست")
        val enabled = prefs.hcSyncFlow.first()
        if (!enabled) return SyncResult(false, "همگام‌سازی در تنظیمات فعال نیست")
        val date = LocalDate.ofEpochDay(dayId)
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val records = mutableListOf<androidx.health.connect.client.records.Record>()

        try {
            // وزن
            val weight = db.weightDao().byDay(dayId)
            weight?.let {
                records.add(
                    WeightRecord(
                        weight = Mass.kilograms(it.weightKg),
                        time = start,
                        zoneOffset = ZoneOffset.UTC
                    )
                )
            }
            // آب
            val waterMl = db.waterDao().totalMl(dayId).first()
            if (waterMl > 0) {
                records.add(
                    HydrationRecord(
                        startTime = start, endTime = end,
                        volume = Volume.milliliters(waterMl.toDouble()),
                        startZoneOffset = ZoneOffset.UTC,
                        endZoneOffset = ZoneOffset.UTC,
                    )
                )
            }
            // کالری سوزانده‌شده (ورزش)
            val burned = db.exerciseDao().kcalSum(dayId).first()
            if (burned > 0) {
                records.add(
                    TotalCaloriesBurnedRecord(
                        startTime = start, endTime = end,
                        energy = Energy.kilocalories(burned),
                        startZoneOffset = ZoneOffset.UTC,
                        endZoneOffset = ZoneOffset.UTC,
                    )
                )
            }
            // قدم‌ها
            val steps = db.stepDao().forDay(dayId).first() ?: 0
            if (steps > 0) {
                records.add(
                    StepsRecord(
                        startTime = start, endTime = end,
                        count = steps.toLong(),
                        startZoneOffset = ZoneOffset.UTC,
                        endZoneOffset = ZoneOffset.UTC,
                    )
                )
            }
            if (records.isEmpty()) return SyncResult(false, "داده‌ای برای همگام‌سازی این روز وجود ندارد")
            client.insertRecords(records)
            return SyncResult(true, "${records.size} رکورد در Health Connect ثبت شد")
        } catch (e: SecurityException) {
            return SyncResult(false, "مجوزهای Health Connect داده نشده است")
        } catch (e: Exception) {
            return SyncResult(false, "خطا: ${e.message?.take(100)}")
        }
    }

    data class SyncResult(val ok: Boolean, val message: String)
}
