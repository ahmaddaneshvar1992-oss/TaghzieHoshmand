package ir.taghziehoshmand.app.ui.screens.mealplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.ai.MealPlanner
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.JalaliDate
import ir.taghziehoshmand.app.data.model.MealType
import ir.taghziehoshmand.app.data.model.PlanItem
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.EmptyState
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.components.SectionTitle
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** برنامه غذایی روزانه و هفتگی — ساخته‌شده بر اساس کالری هدف کاربر */
@Composable
fun MealPlanScreen(nav: NavHostController) {
    val container = TaghzieApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()
    val today = DayUtil.today()
    val weekStart = DayUtil.startOfJalaliWeek(today)

    val profile by container.prefs.profileFlow.collectAsState(initial = null)
    var items by remember { mutableStateOf<List<PlanItem>>(emptyList()) }
    var selectedDay by remember { mutableIntStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    val targetKcal = profile?.targets()?.targetKcal ?: 2000.0

    fun reloadItems() {
        scope.launch { items = container.planDao.range(weekStart, weekStart + 6).first() }
    }

    LaunchedEffect(Unit) { reloadItems() }

    val dayIds = (0..6).map { weekStart + it }
    val selectedDayId = dayIds[selectedDay]
    val dayItems = items.filter { it.dayId == selectedDayId }.sortedBy { it.mealType }
    val dayKcal = dayItems.sumOf { it.kcal }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CurvedHeader(
            title = "برنامه غذایی هفتگی",
            subtitle = "هدف روزانه: ${Fa.kcal(targetKcal)} کیلوکالری",
            showBack = true,
            onBack = { nav.popBackStack() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            PrimaryButton(
                text = if (generating) "در حال ساخت برنامه..." else "ساخت برنامه هفتگی جدید",
                emoji = "✨",
                enabled = !generating,
                onClick = {
                    scope.launch {
                        generating = true
                        val planner = MealPlanner(container.foodDao)
                        val plan = planner.weeklyPlan(weekStart, targetKcal)
                        container.planDao.deleteRange(weekStart, weekStart + 6)
                        container.planDao.insertAll(plan)
                        items = container.planDao.range(weekStart, weekStart + 6).first()
                        generating = false
                        toast = "برنامه هفتگی با ${Fa.num(plan.size)} آیتم ساخته شد"
                    }
                }
            )

            toast?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Teal, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(14.dp))

            // انتخاب روز هفته
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0..6).toList()) { i ->
                    val d = DayUtil.date(dayIds[i])
                    val selected = i == selectedDay
                    Box(
                        Modifier
                            .background(if (selected) Teal else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                            .clickable { selectedDay = i }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(JalaliDate.weekDay(d), fontSize = 12.sp, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            Text(JalaliDate.short(d), fontSize = 10.sp, color = if (selected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${JalaliDate.weekDay(DayUtil.date(selectedDayId))} — جمع ${Fa.kcal(dayKcal)} کیلوکالری",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (dayItems.isEmpty()) {
                EmptyState(
                    "برای این روز برنامه‌ای ساخته نشده. دکمه «ساخت برنامه هفتگی جدید» را بزنید تا هوش مصنوعی بر اساس کالری هدف شما برنامه بچیند.",
                    "📋"
                )
            } else {
                (0..3).forEach { meal ->
                    val mealItems = dayItems.filter { it.mealType == meal }
                    if (mealItems.isEmpty()) return@forEach
                    SectionTitle("${mealEmoji(meal)} ${MealType.names[meal]} — ${Fa.kcal(mealItems.sumOf { it.kcal })} کالری")
                    mealItems.forEach { p ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { scope.launch { container.planDao.setDone(p.id, !p.done); reloadItems() } }) {
                                    Icon(
                                        if (p.done) Icons.Filled.Check else Icons.Filled.Close,
                                        contentDescription = "انجام شد",
                                        tint = if (p.done) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (p.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "${Fa.gram(p.grams)} گرم • ${Fa.kcal(p.kcal)} کالری • پ ${Fa.d(p.protein)} ک ${Fa.d(p.carb)} چ ${Fa.d(p.fat)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Teal
                                    )
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        val planner = MealPlanner(container.foodDao)
                                        val swap = planner.swapItem(p)
                                        if (swap != null) {
                                            container.planDao.deleteItem(p.dayId, p.mealType, p.name)
                                            val factor = p.grams / 100.0
                                            container.planDao.insertAll(
                                                listOf(
                                                    PlanItem(
                                                        dayId = p.dayId, mealType = p.mealType,
                                                        name = swap.name, grams = p.grams,
                                                        kcal = swap.kcal100 * factor, protein = swap.protein100 * factor,
                                                        carb = swap.carb100 * factor, fat = swap.fat100 * factor,
                                                        done = p.done
                                                    )
                                                )
                                            )
                                            reloadItems()
                                            toast = "«${p.name}» با «${swap.name}» جایگزین شد"
                                        }
                                    }
                                }) {
                                    Icon(Icons.Filled.SwapHoriz, "جایگزین", tint = Navy)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "بازگشت به خانه",
                emoji = "🏠",
                container = Navy,
                onClick = { nav.popBackStack() }
            )
            Spacer(Modifier.height(120.dp))
        }
    }
}

private fun mealEmoji(meal: Int): String = when (meal) {
    0 -> "🍳"; 1 -> "🍛"; 2 -> "🌙"; else -> "🍎"
}
