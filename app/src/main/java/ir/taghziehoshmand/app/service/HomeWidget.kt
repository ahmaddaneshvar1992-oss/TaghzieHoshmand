package ir.taghziehoshmand.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.getSystemService
import ir.taghziehoshmand.app.MainActivity
import ir.taghziehoshmand.app.R
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.model.WaterLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ویجت صفحه اصلی (نسخه ۱.۵): کالری و آب امروز + ثبت یک‌ضربه‌ای لیوان آب
 * بدون باز کردن اپ. به‌روزرسانی: باز شدن اپ، ثبت آب، و هر ۳۰ دقیقه توسط سیستم.
 */
class HomeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { render(context, manager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {

        /** رندر یک نمونه ویجت با داده امروز */
        suspend fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val container = TaghzieApp.container(context)
            val today = DayUtil.today()
            val kcal = runCatching { container.mealDao.kcalSum(today).first() }.getOrDefault(0.0)
            val target = runCatching {
                container.prefs.profileFlow.first()?.targets()?.targetKcal ?: 2000.0
            }.getOrDefault(2000.0)
            val water = runCatching { container.waterDao.totalMl(today).first() }.getOrDefault(0)
            val waterGoal = runCatching {
                container.prefs.profileFlow.first()?.waterGoalMl ?: 2500
            }.getOrDefault(2500)

            val views = RemoteViews(context.packageName, R.layout.widget_home).apply {
                setTextViewText(R.id.widget_kcal, "${Fa.kcal(kcal)} / ${Fa.kcal(target)} کیلوکالری")
                setProgressBar(
                    R.id.widget_kcal_bar, 100,
                    ((kcal / target.coerceAtLeast(1.0)) * 100).toInt().coerceIn(0, 100), false
                )
                setTextViewText(R.id.widget_water, "${Fa.num(water)} / ${Fa.num(waterGoal)} میلی‌لیتر آب")
                setProgressBar(
                    R.id.widget_water_bar, 100,
                    ((water.toDouble() / waterGoal.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100), false
                )
                // دکمه ثبت آب — یک لیوان ۲۵۰ میلی‌لیتری بدون باز کردن اپ
                val addIntent = Intent(context, WidgetWaterReceiver::class.java)
                val addPi = PendingIntent.getBroadcast(
                    context, 777001, addIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_water_add, addPi)
                // کلیک روی بدنه ویجت → باز شدن اپ
                val appIntent = Intent(context, MainActivity::class.java)
                val appPi = PendingIntent.getActivity(
                    context, 777002, appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, appPi)
            }
            runCatching { manager.updateAppWidget(appWidgetId, views) }
        }

        /** به‌روزرسانی همه نمونه‌های ویجت روی صفحه اصلی — از داخل اپ صدا زده می‌شود */
        fun updateAll(context: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                val manager = context.getSystemService<AppWidgetManager>() ?: return@launch
                val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java))
                ids.forEach { runCatching { render(context, manager, it) } }
            }
        }
    }
}

/** دکمه «＋» ویجت: ثبت یک لیوان آب و به‌روزرسانی فوری ویجت */
class WidgetWaterReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = TaghzieApp.container(context)
                val today = DayUtil.today()
                container.waterDao.insert(WaterLog(dayId = today, ml = 250))
                val manager = context.getSystemService<AppWidgetManager>()
                if (manager != null) {
                    val ids = manager.getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java))
                    ids.forEach { runCatching { HomeWidgetProvider.render(context, manager, it) } }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
