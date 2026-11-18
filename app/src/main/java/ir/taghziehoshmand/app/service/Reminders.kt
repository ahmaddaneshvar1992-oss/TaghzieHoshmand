package ir.taghziehoshmand.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import ir.taghziehoshmand.app.R
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.data.db.ReminderDao
import ir.taghziehoshmand.app.data.model.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val intent = Intent(context, ReminderReceiver::class.java)
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
                val now = LocalDateTime.now()
                val hour = now.hour
                val minute = now.minute
                val type = when {
                    hour in 6..10 -> 0          // صبحانه
                    hour in 11..15 -> 1         // ناهار
                    hour in 17..21 -> 2         // شام
                    else -> 3                   // عمومی / میان‌وعده
                }
                // یادآورهای این ساعت
                val reminders = container.reminderDao.enabled()
                val matched = reminders.filter { it.hour == hour && it.minute == minute }
                matched.forEach { notify(context, it.type, it.title) }
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
        val notif = NotificationCompat.Builder(context, TaghzieApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(title.ifBlank { "تغذیه هوشمند" })
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val nm = context.getSystemService<android.app.NotificationManager>()
        val id = (System.currentTimeMillis() and 0x7fffffff).toInt()
        try {
            nm?.notify(id, notif)
        } catch (_: SecurityException) {
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
