package ir.taghziehoshmand.app.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import ir.taghziehoshmand.app.MainActivity
import ir.taghziehoshmand.app.R
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.db.ReminderDao
import ir.taghziehoshmand.app.data.model.Reminder
import ir.taghziehoshmand.app.data.model.WaterLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** زمان‌بند یادآورها با AlarmManager */
class ReminderScheduler(private val context: Context, private val dao: ReminderDao) {

    private val alarm get() = context.getSystemService<AlarmManager>()!!

    suspend fun rescheduleAll() {
        val list = dao.enabled()
        list.forEach { scheduleNext(it) }
    }

    suspend fun schedule(reminder: Reminder) {
        dao.insert(reminder)
        scheduleNext(reminder)
    }

    fun cancel(reminderId: Long) {
        val pi = pendingIntent(reminderId.toInt())
        alarm.cancel(pi)
    }

    suspend fun update(reminder: Reminder) {
        dao.update(reminder)
        cancel(reminder.id)
        if (reminder.enabled) scheduleNext(reminder)
    }

    suspend fun delete(reminder: Reminder) {
        cancel(reminder.id)
        dao.delete(reminder.id)
    }

    /** زمان بعدی اجرای یادآور بر اساس ماسک روزها */
    fun nextTrigger(reminder: Reminder): Long {
        val now = LocalDateTime.now()
        val time = LocalTime.of(reminder.hour, reminder.minute)
        var day = LocalDate.now()
        if (now.toLocalTime().isAfter(time)) day = day.plusDays(1)
        repeat(8) {
            val bit = dayBit(day)
            if (reminder.daysMask and (1 shl bit) != 0) {
                val dt = day.atTime(time)
                if (dt.isAfter(now)) {
                    return dt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
            day = day.plusDays(1)
        }
        return now.plusHours(1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun scheduleNext(reminder: Reminder) {
        val trigger = nextTrigger(reminder)
        val pi = pendingIntent(reminder.id.toInt())
        val canExact = if (Build.VERSION.SDK_INT >= 31) alarm.canScheduleExactAlarms() else true
        if (canExact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        else alarm.set(AlarmManager.RTC_WAKEUP, trigger, pi)
    }

    private fun pendingIntent(code: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("reminder_id", code)
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        /** بیت ۰ = شنبه ... بیت ۶ = جمعه */
        fun dayBit(d: LocalDate): Int = when (d.dayOfWeek) {
            DayOfWeek.SATURDAY -> 0
            DayOfWeek.SUNDAY -> 1
            DayOfWeek.MONDAY -> 2
            DayOfWeek.TUESDAY -> 3
            DayOfWeek.WEDNESDAY -> 4
            DayOfWeek.THURSDAY -> 5
            else -> 6
        }
    }
}

/** نمایش نوتیفیکیشن یادآور و زمان‌بندی اجرای بعدی */
class ReminderReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val container = TaghzieApp.container(context)
        val pending = goAsync()
        scope.launch {
            try {
                // یادآور مستقیماً با شناسه از اینتنت پیدا می‌شود؛ حتی اگر آلارم با تأخیر (Doze) اجرا شود
                val reminderId = intent.getLongExtra("reminder_id", -1L)
                val byId = if (reminderId >= 0) container.reminderDao.byId(reminderId) else null
                if (byId != null && byId.enabled) {
                    notify(context, byId.type, byId.title)
                } else if (byId == null) {
                    // فال‌بک دفاعی برای آلارم‌های بدون شناسه: پنجره ۲ دقیقه‌ای
                    val now = LocalDateTime.now()
                    val windowStart = now.minusMinutes(2).toLocalTime()
                    val nowTime = now.toLocalTime()
                    val matched = container.reminderDao.enabled().filter {
                        val t = LocalTime.of(it.hour, it.minute)
                        !t.isBefore(windowStart) && !t.isAfter(nowTime)
                    }
                    matched.forEach { notify(context, it.type, it.title) }
                }
                // زمان‌بندی مجدد برای اجرای بعدی
                container.reminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, type: Int, title: String) {
        val body = when (type) {
            0 -> "وقت صبحانه است؛ یک وعده متعادل با پروتئین بخورید 🍳"
            1 -> "یک لیوان آب بنوشید و در اپ ثبت کنید 💧"
            2 -> "شام را سبک و زودتر بخورید 🌙"
            3 -> "میان‌وعده سالم: میوه، آجیل یا ماست 🥗"
            else -> "یادآوری تغذیه هوشمند"
        }
        val isWater = type == 1 || title.contains("آب")
        val notifId = (System.currentTimeMillis() and 0x7fffffff).toInt()
        // ضربه روی نوتیفیکیشن → باز شدن اپ؛ یادآور آب مستقیم صفحه آب را باز می‌کند
        val contentIntent = PendingIntent.getActivity(
            context,
            notifId,
            Intent(context, MainActivity::class.java).apply {
                putExtra("nav_target", if (isWater) "water" else "")
                putExtra("nav_seq", System.currentTimeMillis())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, TaghzieApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title.ifBlank { "تغذیه هوشمند" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // دکمه ثبت سریع آب: یک لیوان بدون باز شدن اپ ثبت می‌شود
        if (isWater) {
            val addIntent = Intent(context, WaterQuickAddReceiver::class.java).putExtra("notif_id", notifId)
            val addPi = PendingIntent.getBroadcast(
                context, WaterQuickAddReceiver.ACTION_CODE, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "ثبت یک لیوان 💧", addPi)
        }
        val nm = context.getSystemService<NotificationManager>()
        try {
            nm?.notify(notifId, builder.build())
        } catch (_: SecurityException) {
        }
    }
}

/** ثبت سریع یک لیوان آب از دکمه نوتیفیکیشن — بدون باز شدن اپ */
class WaterQuickAddReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CODE = 4242
        private const val OK_NOTIF_ID = 999001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val container = TaghzieApp.container(context)
        val notifId = intent.getIntExtra("notif_id", -1)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val today = DayUtil.today()
                container.waterDao.insert(WaterLog(dayId = today, ml = 250))
                val total = container.waterDao.totalMl(today).first()
                val goal = container.prefs.profileFlow.first()?.waterGoalMl ?: 2500
                val nm = context.getSystemService<NotificationManager>()
                if (notifId != -1) nm?.cancel(notifId)
                val ok = NotificationCompat.Builder(context, TaghzieApp.CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_logo)
                    .setContentTitle("ثبت شد ✅")
                    .setContentText("یک لیوان آب (۲۵۰ میلی‌لیتر) ثبت شد — مجموع امروز: ${Fa.num(total)} از ${Fa.num(goal)} میلی‌لیتر")
                    .setTimeoutAfter(8000)
                    .setAutoCancel(true)
                    .build()
                nm?.notify(OK_NOTIF_ID, ok)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

/** زمان‌بندی مجدد پس از ری‌استارت دستگاه */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = TaghzieApp.container(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                container.reminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
