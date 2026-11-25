package ir.taghziehoshmand.app.ui.screens.progress

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
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
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.HealthCalc
import ir.taghziehoshmand.app.core.JalaliDate
import ir.taghziehoshmand.app.core.WeightModel
import ir.taghziehoshmand.app.ui.components.ForecastChart
import ir.taghziehoshmand.app.ui.components.ForecastPoint
import ir.taghziehoshmand.app.ui.components.LineChart
import ir.taghziehoshmand.app.ui.components.RingProgress
import ir.taghziehoshmand.app.ui.components.SectionTitle
import ir.taghziehoshmand.app.ui.theme.Green
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch
import java.time.LocalDate

/** صفحه پیشرفت و آمار — مطابق طراحی مرجع */
@Composable
fun ProgressScreen(nav: NavHostController) {
    val container = TaghzieApp.container(LocalContext.current)
    val today = DayUtil.today()

    var rangeDays by remember { mutableIntStateOf(30) }
    val from = today - rangeDays + 1

    val profile by container.prefs.profileFlow.collectAsState(initial = null)
    val weights by container.weightDao.since(from).collectAsState(initial = emptyList())
    val dayTotals by container.mealDao.dayTotals(from, today).collectAsState(initial = emptyList())
    val waterToday by container.waterDao.totalMl(today).collectAsState(initial = 0)
    val stepsToday by container.stepDao.forDay(today).collectAsState(initial = null)
    val exerciseKcal by container.exerciseDao.kcalSum(today).collectAsState(initial = 0.0)

    val targetKcal = profile?.targets()?.targetKcal ?: 2000.0
    val weightPoints = weights.map { it.weightKg.toFloat() }
    val weightLabels = weights.map { JalaliDate.short(DayUtil.date(it.dayId)) }
    val weightDiff = if (weights.size >= 2) weights.last().weightKg - weights.first().weightKg else null

    val avgKcal = if (dayTotals.isNotEmpty()) dayTotals.map { it.kcal }.average() else 0.0
    val successfulDays = dayTotals.count { kotlin.math.abs(it.kcal - targetKcal) <= targetKcal * 0.12 }

    // ===== موتور تطبیقی وزن (نسخه ۱.۵ — ایده ۷ و ۸) =====
    val scope = rememberCoroutineScope()
    var targetUpdated by remember { mutableStateOf(false) }
    val weightSamples = remember(weights) { weights.map { WeightModel.Sample(it.dayId, it.weightKg) } }
    val trend = remember(weightSamples) { WeightModel.regression(weightSamples) }
    val forecast = remember(weightSamples) { WeightModel.forecast(weightSamples) }
    // TDEE تطبیقی: روی پنجره ثابت ۴۲ روزه مستقل از بازه انتخابی نمودار
    val adaptive = remember(weights, dayTotals) {
        val span = if (weights.isNotEmpty()) (today - weights.minOf { it.dayId }).toInt() else 0
        val fTdee = profile?.targets()?.tdee ?: 0.0
        WeightModel.adaptiveTdee(if (dayTotals.isNotEmpty()) dayTotals.map { it.kcal }.average() else 0.0, trend, fTdee, span)
    }
    val adaptiveDiff = adaptive?.let { kotlin.math.abs(it.tdee - targetKcal) ?: 0.0 }
    val forecastHorizonDays = listOf(7, 14, 21, 30)
    val fcHistory = remember(weights) {
        weights.map { ForecastPoint(it.weightKg.toFloat(), JalaliDate.short(DayUtil.date(it.dayId))) }
    }
    val forecastPts = remember(forecast) {
        forecast?.let { fc ->
            val last = weights.lastOrNull()?.dayId ?: today
            forecastHorizonDays.map { d ->
                ForecastPoint(
                    weight = fc.predictedKg(d).toFloat(),
                    label = JalaliDate.short(DayUtil.date(last + d))
                )
            }
        } ?: emptyList()
    }
    val bandLo = remember(forecast) { forecastHorizonDays.map { (forecast?.bandKg(it)?.let { b -> (forecast!!.predictedKg(it) - b).toFloat() } ?: 0f) } }
    val bandHi = remember(forecast) { forecastHorizonDays.map { (forecast?.bandKg(it)?.let { b -> (forecast!!.predictedKg(it) + b).toFloat() } ?: 0f) } }
    val goalWeightF = profile?.goalWeightKg?.takeIf { it > 0 }?.toFloat()
    val goalEta = forecast?.let { fc ->
        val p = profile
        if (p != null && p.goalWeightKg > 0) fc.daysToGoal(p.goalWeightKg, p.goal == ir.taghziehoshmand.app.core.Goal.LOSE) else null
    }

    // نمودار کالری ۷ روز اخیر
    val weekFrom = today - 6
    val weekTotals = dayTotals.filter { it.dayId >= weekFrom }
    val weekBars = (0..6).map { i ->
        val d = weekFrom + i
        weekTotals.firstOrNull { it.dayId == d }?.kcal?.toFloat() ?: 0f
    }
    val weekLabels = (0..6).map { i ->
        val d = DayUtil.date(weekFrom + i)
        listOf("ش", "ی", "د", "س", "چ", "پ", "ج")[JalaliDate.weekDayIndex(d)]
    }

    val stepGoal = profile?.stepGoal ?: 10000
    val steps = stepsToday ?: 0

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(ir.taghziehoshmand.app.ui.theme.Navy)
                .padding(top = 44.dp, bottom = 20.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Color(0x22FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 18.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("پیشرفت من", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { nav.navigate("reminders") }) {
                    Icon(Icons.Filled.Notifications, "یادآورها", tint = Color.White)
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            // کارت روند وزن
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("روند وزن (کیلوگرم)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(7, 30, 90).forEach { r ->
                                val sel = rangeDays == r
                                Box(
                                    Modifier
                                        .background(if (sel) Teal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                        .clickable { rangeDays = r }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("${Fa.num(r)} روز", fontSize = 11.sp, color = if (sel) Teal else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LineChart(
                        points = weightPoints,
                        labels = weightLabels,
                        lineColor = Green,
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                        unitSuffix = " کیلو"
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // کارت پیش‌بینی وزن آینده (ایده ۸)
            if (forecast != null && forecastPts.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("پیش‌بینی وزن آینده", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "خط‌چین = مسیر پیش‌بینی با ادامه روند فعلی؛ ناحیه کم‌رنگ = بازه اطمینان",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        ForecastChart(
                            history = fcHistory,
                            forecast = forecastPts,
                            bandLo = bandLo,
                            bandHi = bandHi,
                            goalWeight = goalWeightF,
                            modifier = Modifier.fillMaxWidth().height(200.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "پیش‌بینی ۳۰ روز آینده: حدود ${Fa.weight(forecast.predictedKg(30))} ± ${Fa.weight(forecast.bandKg(30))} کیلوگرم",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Teal,
                            fontWeight = FontWeight.Bold
                        )
                        goalEta?.let { d ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "با ادامه این روند، حدود ${Fa.num(d)} روز دیگر به وزن هدف (${Fa.weight(profile?.goalWeightKg ?: 0.0)} کیلوگرم) می‌رسید 🎯",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (trend != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                WeightModel.trendText(trend),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // کارت TDEE تطبیقی (ایده ۷)
            if (adaptive != null) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡ هدف کالری تطبیقی", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier
                                    .background(Teal.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("اعتماد ${Fa.num((adaptive.confidence * 100).toInt())}٪", fontSize = 11.sp, color = Teal)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "بر اساس دریافت واقعی کالری و روند وزن شما، مصرف انرژی روزانه بدنتان حدود ${Fa.kcal(adaptive.tdee)} کیلوکالری برآورد می‌شود (فرمول اولیه: ${Fa.kcal(adaptive.formulaTdee)}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        val diff = kotlin.math.abs(adaptive.tdee - targetKcal)
                        if (diff > 40 && profile != null && !targetUpdated) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Navy, RoundedCornerShape(14.dp))
                                    .clickable {
                                        val p = profile!!
                                        scope.launch {
                                            val (pr, cb, ft) = HealthCalc.macros(adaptive.tdee, p.weightKg, p.goal)
                                            container.prefs.saveProfile(
                                                p.copy(targetKcal = adaptive.tdee, proteinG = pr, carbG = cb, fatG = ft)
                                            )
                                            targetUpdated = true
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "به‌روزرسانی هدف کالری به ${Fa.kcal(adaptive.tdee)}",
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                            }
                        } else if (targetUpdated) {
                            Text("هدف کالری با موتور تطبیقی همگام شد ✅", style = MaterialTheme.typography.bodySmall, color = Green)
                        } else {
                            Text("هدف فعلی با برآورد واقعی بدن هم‌خوانی دارد ✅", style = MaterialTheme.typography.bodySmall, color = Green)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(12.dp))

            // سه کارت آمار
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    title = "میانگین کالری",
                    value = Fa.kcal(avgKcal),
                    unit = "کیلوکالری",
                    emoji = "🔥",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = if (weightDiff == null) "تغییر وزن:" else if (weightDiff < 0) "کاهش وزن:" else "افزایش وزن:",
                    value = if (weightDiff == null) "—" else Fa.weight(kotlin.math.abs(weightDiff)),
                    unit = "کیلوگرم",
                    emoji = "⚖️",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "روزهای موفق:",
                    value = "${Fa.num(successfulDays)} از ${Fa.num(dayTotals.size)}",
                    unit = "روز در هدف",
                    emoji = "✅",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // نمودار کالری هفتگی
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("کالری مصرفی ۷ روز اخیر", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(10.dp))
                    ir.taghziehoshmand.app.ui.components.BarChart(
                        values = weekBars,
                        labels = weekLabels,
                        goal = targetKcal.toFloat(),
                        modifier = Modifier.fillMaxWidth().height(170.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // هدف هفتگی: قدم و آب
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RingProgress(
                        progress = steps / stepGoal.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.size(110.dp),
                        strokeWidth = 10.dp,
                        color = Green,
                        trackColor = Color(0xFFEDF2F6)
                    ) {
                        Text("👟", fontSize = 24.sp)
                        Text("${Fa.num((steps * 100 / stepGoal.coerceAtLeast(1)).coerceAtMost(100))}٪", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("هدف هفتگی شما", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text("قدم امروز: ${Fa.num(steps)} از ${Fa.num(stepGoal)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("آب امروز: ${Fa.num(waterToday)} میلی‌لیتر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("ورزش امروز: ${Fa.kcal(exerciseKcal)} کیلوکالری", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Text("گالری عکس‌های قبل و بعد را در «گالری پیشرفت» ببینید.", style = MaterialTheme.typography.bodySmall, color = Teal)
                    }
                }
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, unit: String, emoji: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(6.dp))
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = ir.taghziehoshmand.app.ui.theme.Navy)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
