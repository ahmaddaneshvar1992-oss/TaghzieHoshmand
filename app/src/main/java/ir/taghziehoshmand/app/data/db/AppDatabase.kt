package ir.taghziehoshmand.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ir.taghziehoshmand.app.data.model.ChatMsg
import ir.taghziehoshmand.app.data.model.ExerciseLog
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.model.MealEntry
import ir.taghziehoshmand.app.data.model.PlanItem
import ir.taghziehoshmand.app.data.model.ProgressPhoto
import ir.taghziehoshmand.app.data.model.Reminder
import ir.taghziehoshmand.app.data.model.StepLog
import ir.taghziehoshmand.app.data.model.WaterLog
import ir.taghziehoshmand.app.data.model.WeightLog

@Database(
    entities = [
        Food::class, MealEntry::class, WaterLog::class, ExerciseLog::class,
        WeightLog::class, ProgressPhoto::class, ChatMsg::class, PlanItem::class,
        Reminder::class, StepLog::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun foodDao(): FoodDao
    abstract fun mealDao(): MealDao
    abstract fun waterDao(): WaterDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun weightDao(): WeightDao
    abstract fun photoDao(): PhotoDao
    abstract fun chatDao(): ChatDao
    abstract fun planDao(): PlanDao
    abstract fun reminderDao(): ReminderDao
    abstract fun stepDao(): StepDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "taghzie.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
