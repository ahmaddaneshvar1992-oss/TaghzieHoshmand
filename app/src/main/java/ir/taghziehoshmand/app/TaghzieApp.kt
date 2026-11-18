package ir.taghziehoshmand.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import ir.taghziehoshmand.app.ai.AiEngine
import ir.taghziehoshmand.app.data.db.AppDatabase
import ir.taghziehoshmand.app.data.prefs.UserPrefs
import ir.taghziehoshmand.app.data.repo.FoodCatalog
import ir.taghziehoshmand.app.export.ExportManager
import ir.taghziehoshmand.app.service.ReminderScheduler
import ir.taghziehoshmand.app.sync.HealthConnectSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** کانتینر وابستگی‌ها (تزریق دستی) */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: AppDatabase by lazy { AppDatabase.build(context) }
    val prefs = UserPrefs(context)
    val catalog = FoodCatalog(context)

    val foodDao get() = db.foodDao()
    val mealDao get() = db.mealDao()
    val waterDao get() = db.waterDao()
    val exerciseDao get() = db.exerciseDao()
    val weightDao get() = db.weightDao()
    val photoDao get() = db.photoDao()
    val chatDao get() = db.chatDao()
    val planDao get() = db.planDao()
    val reminderDao get() = db.reminderDao()
    val stepDao get() = db.stepDao()

    val ai: AiEngine by lazy {
        AiEngine(context, prefs, chatDao, mealDao, weightDao, exerciseDao, foodDao, waterDao, stepDao)
    }
    val export: ExportManager by lazy { ExportManager(context, db, prefs) }
    val healthConnect: HealthConnectSync by lazy { HealthConnectSync(context, db, prefs) }
    val reminders: ReminderScheduler by lazy { ReminderScheduler(context, reminderDao) }

    init {
        appScope.launch { catalog.seedFoods(foodDao) }
        appScope.launch { reminders.rescheduleAll() }
    }
}

class TaghzieApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        createChannels()
        container = AppContainer(this)
    }

    private fun createChannels() {
        val nm = getSystemService<NotificationManager>() ?: return
        val ch = NotificationChannel(
            CHANNEL_REMINDERS,
            getString(ir.taghziehoshmand.app.R.string.channel_reminders),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = getString(ir.taghziehoshmand.app.R.string.channel_reminders_desc)
        }
        nm.createNotificationChannel(ch)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"

        fun container(context: Context): AppContainer =
            (context.applicationContext as TaghzieApp).container
    }
}
