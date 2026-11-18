package ir.taghziehoshmand.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import ir.taghziehoshmand.app.data.model.DayTotal
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
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE name LIKE '%' || :q || '%' ORDER BY commonWeight DESC, isCustom DESC, name LIMIT 60")
    fun search(q: String): Flow<List<Food>>

    @Query("SELECT * FROM foods ORDER BY commonWeight DESC, name LIMIT 120")
    fun all(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE isCustom = 1 ORDER BY id DESC")
    fun customs(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE favorite = 1 ORDER BY useCount DESC")
    fun favorites(): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE useCount > 0 ORDER BY useCount DESC LIMIT :limit")
    fun frequent(limit: Int = 12): Flow<List<Food>>

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): Food?

    @Query("SELECT * FROM foods WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): Food?

    @Query("SELECT DISTINCT category FROM foods ORDER BY category")
    suspend fun categories(): List<String>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Query("SELECT * FROM foods")
    suspend fun pool(): List<Food>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<Food>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(f: Food): Long

    @Update
    suspend fun update(f: Food)

    @Query("UPDATE foods SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUse(id: Long)

    @Query("UPDATE foods SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM foods WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: Long)
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_entries WHERE dayId = :day ORDER BY mealType, createdAt")
    fun byDay(day: Long): Flow<List<MealEntry>>

    @Query("SELECT COALESCE(SUM(kcal),0) FROM meal_entries WHERE dayId = :day")
    fun kcalSum(day: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(protein),0) FROM meal_entries WHERE dayId = :day")
    fun proteinSum(day: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(carb),0) FROM meal_entries WHERE dayId = :day")
    fun carbSum(day: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(fat),0) FROM meal_entries WHERE dayId = :day")
    fun fatSum(day: Long): Flow<Double>

    @Query("""
        SELECT dayId AS dayId, SUM(kcal) AS kcal, SUM(protein) AS protein,
               SUM(carb) AS carb, SUM(fat) AS fat
        FROM meal_entries WHERE dayId BETWEEN :from AND :to GROUP BY dayId ORDER BY dayId
    """)
    fun dayTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query("SELECT * FROM meal_entries WHERE dayId BETWEEN :from AND :to ORDER BY dayId, mealType")
    suspend fun between(from: Long, to: Long): List<MealEntry>

    @Insert
    suspend fun insert(e: MealEntry): Long

    @Query("DELETE FROM meal_entries WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WaterDao {
    @Query("SELECT * FROM water_logs WHERE dayId = :day ORDER BY createdAt DESC")
    fun byDay(day: Long): Flow<List<WaterLog>>

    @Query("SELECT COALESCE(SUM(ml),0) FROM water_logs WHERE dayId = :day")
    fun totalMl(day: Long): Flow<Int>

    @Query("""
        SELECT dayId AS dayId, COALESCE(SUM(ml),0) * 0.001 AS kcal, 0 AS protein, 0 AS carb, 0 AS fat
        FROM water_logs WHERE dayId BETWEEN :from AND :to GROUP BY dayId ORDER BY dayId
    """)
    fun dayTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query("SELECT * FROM water_logs WHERE dayId BETWEEN :from AND :to ORDER BY dayId")
    suspend fun between(from: Long, to: Long): List<WaterLog>

    @Insert
    suspend fun insert(w: WaterLog)

    @Query("DELETE FROM water_logs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercise_logs WHERE dayId = :day ORDER BY createdAt DESC")
    fun byDay(day: Long): Flow<List<ExerciseLog>>

    @Query("SELECT COALESCE(SUM(kcal),0) FROM exercise_logs WHERE dayId = :day")
    fun kcalSum(day: Long): Flow<Double>

    @Query("SELECT * FROM exercise_logs WHERE dayId BETWEEN :from AND :to ORDER BY dayId")
    suspend fun between(from: Long, to: Long): List<ExerciseLog>

    @Insert
    suspend fun insert(e: ExerciseLog)

    @Query("DELETE FROM exercise_logs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight_logs ORDER BY dayId ASC")
    fun all(): Flow<List<WeightLog>>

    @Query("SELECT * FROM weight_logs WHERE dayId >= :from ORDER BY dayId ASC")
    fun since(from: Long): Flow<List<WeightLog>>

    @Query("SELECT * FROM weight_logs ORDER BY dayId DESC LIMIT 1")
    suspend fun latest(): WeightLog?

    @Query("SELECT * FROM weight_logs WHERE dayId BETWEEN :from AND :to ORDER BY dayId")
    suspend fun between(from: Long, to: Long): List<WeightLog>

    @Query("SELECT * FROM weight_logs WHERE dayId = :day LIMIT 1")
    suspend fun byDay(day: Long): WeightLog?

    @Insert
    suspend fun insert(w: WeightLog)

    @Query("DELETE FROM weight_logs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos ORDER BY dayId DESC, id DESC")
    fun all(): Flow<List<ProgressPhoto>>

    @Insert
    suspend fun insert(p: ProgressPhoto)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat ORDER BY id ASC")
    fun all(): Flow<List<ChatMsg>>

    @Insert
    suspend fun insert(m: ChatMsg)

    @Query("DELETE FROM chat")
    suspend fun clear()
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM plan_items WHERE dayId BETWEEN :from AND :to ORDER BY dayId, mealType")
    fun range(from: Long, to: Long): Flow<List<PlanItem>>

    @Query("SELECT * FROM plan_items WHERE dayId = :day ORDER BY mealType")
    suspend fun forDay(day: Long): List<PlanItem>

    @Insert
    suspend fun insertAll(items: List<PlanItem>)

    @Query("UPDATE plan_items SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM plan_items WHERE dayId BETWEEN :from AND :to")
    suspend fun deleteRange(from: Long, to: Long)

    @Query("DELETE FROM plan_items WHERE dayId = :day AND mealType = :mealType AND name = :name")
    suspend fun deleteItem(day: Long, mealType: Int, name: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY type, hour, minute")
    fun all(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE enabled = 1")
    suspend fun enabled(): List<Reminder>

    @Insert
    suspend fun insert(r: Reminder): Long

    @Update
    suspend fun update(r: Reminder)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StepDao {
    @Query("SELECT steps FROM steps WHERE dayId = :day")
    fun forDay(day: Long): Flow<Int?>

    @Upsert
    suspend fun set(s: StepLog)

    @Query("SELECT * FROM steps WHERE dayId BETWEEN :from AND :to ORDER BY dayId")
    suspend fun between(from: Long, to: Long): List<StepLog>
}
