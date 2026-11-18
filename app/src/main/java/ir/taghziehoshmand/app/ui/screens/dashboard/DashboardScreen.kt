package ir.taghziehoshmand.app.ui.screens.dashboard

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.HealthCalc
import ir.taghziehoshmand.app.data.model.FoodSource
import ir.taghziehoshmand.app.ui.Routes
import ir.taghziehoshmand.app.ui.components.InfoStatCard
import ir.taghziehoshmand.app.ui.components.QuickActionCard
import ir.taghziehoshmand.app.ui.components.RingProgress
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal

/** داشبورد — مطابق طراحی مرجع: پس‌زمینه سرمه‌ای، حلقه کالری، کارت‌های سفید */
@Composable
fun DashboardScreen(nav: NavHostController) {
    val container = TaghzieApp.container(LocalContext.current)
    val today = DayUtil.today()

    val profile by container.prefs.profileFlow.collectAsState(initial = null)
    val kcal by container.mealDao.kcalSum(today).collectAsState(initial = 0.0)
    val protein by container.mealDao.proteinSum(today).collectAsState(initial = 0.0)
    val carb by container.mealDao.carbSum(today).collectAsState(initial = 0.0)
    val fat by container.mealDao.fatSum(today).collectAsState(initial = 0.0)
    val waterMl by container.waterDao.totalMl(today).collectAsState(initial = 0)
    val exerciseKcal by container.exerciseDao.kcalSum(today).collectAsState(initial = 0.0)
    val steps by container.stepDao.forDay(today).collectAsState(initial = null)

    val targets = profile?.targets()
    val targetKcal = targets?.targetKcal ?: 2000.0
    val remaining = (targetKcal - kcal).coerceAtLeast(0.0)
    val progress = if (targetKcal > 0) (kcal / targetKcal).toFloat().coerceIn(0f, 1f) else 0f
    val bmi = HealthCalc.bmi(profile?.weightKg ?: 0.0, profile?.heightCm ?: 0.0)

    Box(Modifier.fillMaxSize().background(Navy)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            // هدر
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { nav.navigate("settings") }) {
                    Icon(Icons.Filled.Menu, "منو", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "تغذیه هوشمند",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text("🥗", fontSize = 26.sp)
            }

            Spacer(Modifier.height(12.dp))

            // حلقه کالری
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingProgress(
                    progress = progress,
                    modifier = Modifier.size(230.dp),
                    strokeWidth = 20.dp,
                    color = Teal,
                    trackColor = Color(0x33FFFFFF)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${Fa.kcal(targetKcal)} / ${Fa.kcal(kcal)}",
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("کالری", color = Teal, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${Fa.kcal(remaining)} کالری باقی‌مانده",
                            color = Color(0xFF9DB2C4),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("•", color = Teal)
                Text(
                    " از هدف روزانه شما ",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Text("•", color = Teal)
            }
            Spacer(Modifier.height(16.dp))

            // کارت‌های وزن/قد/BMI
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoStatCard(
                    label = "وزن فعلی:",
                    value = Fa.weight(profile?.weightKg ?: 0.0),
                    emoji = "⚖️",
                    note = "کیلوگرم",
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate(Routes.PROFILE) }
                )
                InfoStatCard(
                    label = "قد:",
                    value = Fa.num(profile?.heightCm?.toInt() ?: 0),
                    emoji = "📏",
                    note = "سانتی‌متر",
                    modifier = Modifier.weight(1f)
                )
                InfoStatCard(
                    label = "BMI:",
                    value = Fa.d(bmi, 1),
                    emoji = "🧍",
                    note = HealthCalc.bmiLabel(bmi),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "عملیات سریع",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("ثبت وعده", "🍽", { nav.navigate(Routes.FOOD) }, Modifier.weight(1f))
                QuickActionCard("هوش مصنوعی", "✨", { nav.navigate(Routes.AI) }, Modifier.weight(1f))
                QuickActionCard("برنامه غذایی", "📋", { nav.navigate("plan") }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionCard("آب روزانه", "💧", { nav.navigate("water") }, Modifier.weight(1f))
                QuickActionCard("ورزش", "🏃", { nav.navigate("exercise") }, Modifier.weight(1f))
                QuickActionCard("گالری پیشرفت", "📸", { nav.navigate("gallery") }, Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))

            // خلاصه آب و ورزش
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniProgressCard(
                    title = "آب امروز",
                    value = "${Fa.num(waterMl)} از ${Fa.num(profile?.waterGoalMl ?: 2500)} میلی‌لیتر",
                    progress = if ((profile?.waterGoalMl ?: 2500) > 0) waterMl.toFloat() / (profile?.waterGoalMl ?: 2500) else 0f,
                    emoji = "💧",
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate("water") }
                )
                MiniProgressCard(
                    title = "ورزش امروز",
                    value = "${Fa.kcal(exerciseKcal)} کیلوکالری سوزانده${if ((steps ?: 0) > 0) " • ${Fa.num(steps)} قدم" else ""}",
                    progress = (exerciseKcal / 400.0).toFloat().coerceIn(0f, 1f),
                    emoji = "🔥",
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate("exercise") }
                )
            }

            Spacer(Modifier.height(18.dp))

            // ماکروهای امروز
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x14FFFFFF), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ماکروهای امروز", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.weight(1f))
                        Text("هدف: ${Fa.kcal(targetKcal)} کالری", color = Color(0xFF9DB2C4), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    MacroRow("پروتئین", protein, targets?.proteinG ?: 0.0, Color(0xFF10B981))
                    Spacer(Modifier.height(8.dp))
                    MacroRow("کربوهیدرات", carb, targets?.carbG ?: 0.0, Teal)
                    Spacer(Modifier.height(8.dp))
                    MacroRow("چربی", fat, targets?.fatG ?: 0.0, Color(0xFFF59E0B))
                }
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun MacroRow(label: String, current: Double, target: Double, color: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(
                "${Fa.d(current)} از ${Fa.kcal(target)} گرم",
                color = Color(0xFFB9CFE0),
                fontSize = 12.sp,
                textAlign = TextAlign.End
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0x22FFFFFF), RoundedCornerShape(3.dp))
        ) {
            val p = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth(p)
                    .height(6.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
        }
    }
}

/** کارت کوچک پیشرفت روی پس‌زمینه سرمه‌ای */
@Composable
private fun MiniProgressCard(
    title: String,
    value: String,
    progress: Float,
    emoji: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp)
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(Color(0xFFEDF2F6), RoundedCornerShape(4.dp))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(Teal, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}
