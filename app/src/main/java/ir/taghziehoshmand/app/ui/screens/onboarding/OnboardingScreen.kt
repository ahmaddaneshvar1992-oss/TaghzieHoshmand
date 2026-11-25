package ir.taghziehoshmand.app.ui.screens.onboarding

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.ActivityLevel
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.Gender
import ir.taghziehoshmand.app.core.Goal
import ir.taghziehoshmand.app.data.model.Reminder
import ir.taghziehoshmand.app.data.model.WeightLog
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.ui.components.FaTextField
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch

/** آنبوردینگ ۵ مرحله‌ای — سوالات اولیه و ساخت پروفایل با محاسبات کامل */
@Composable
fun OnboardingScreen(
    initialName: String,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val container = TaghzieApp.container(context)
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(initialName) }
    var gender by remember { mutableStateOf<Gender?>(null) }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var activity by remember { mutableStateOf<ActivityLevel?>(null) }
    var goal by remember { mutableStateOf<Goal?>(null) }
    var goalWeight by remember { mutableStateOf("") }

    val r = runCatching {
        ir.taghziehoshmand.app.core.HealthCalc.calculate(
            weight.toDoubleOrNull() ?: 0.0,
            height.toDoubleOrNull() ?: 0.0,
            age.toIntOrNull() ?: 0,
            gender ?: Gender.MALE,
            activity ?: ActivityLevel.SEDENTARY,
            goal ?: Goal.KEEP
        )
    }.getOrNull()

    fun valid(step: Int): Boolean = when (step) {
        0 -> name.isNotBlank() && gender != null
        1 -> (age.toIntOrNull() ?: 0) in 10..100 && (height.toDoubleOrNull() ?: 0.0) in 100.0..230.0 &&
            (weight.toDoubleOrNull() ?: 0.0) in 30.0..300.0
        2 -> activity != null
        3 -> goal != null && (goalWeight.toDoubleOrNull() ?: 0.0) > 0
        else -> true
    }

    Box(Modifier.fillMaxSize().background(Navy)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Spacer(Modifier.height(28.dp))
            // نشانگر مراحل
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(5) { i ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (i == step) 12.dp else 8.dp)
                            .background(if (i <= step) Teal else Color(0x44FFFFFF), CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                listOf("خوش آمدید 👋", "داده‌های بدنی 📏", "سطح فعالیت 🏃", "هدف شما 🎯", "خلاصه و شروع ✅")[step],
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    when (step) {
                        0 -> {
                            FaTextField(value = name, onValueChange = { name = it }, label = "نام شما")
                            Spacer(Modifier.height(16.dp))
                            Text("جنسیت:", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ChoiceChip("مرد 👨", gender == Gender.MALE, Modifier.weight(1f)) { gender = Gender.MALE }
                                ChoiceChip("زن 👩", gender == Gender.FEMALE, Modifier.weight(1f)) { gender = Gender.FEMALE }
                            }
                        }
                        1 -> {
                            FaTextField(value = age, onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) }, label = "سن (سال)", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(12.dp))
                            FaTextField(value = height, onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = "قد (سانتی‌متر)", keyboardType = KeyboardType.Number)
                            Spacer(Modifier.height(12.dp))
                            FaTextField(value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = "وزن فعلی (کیلوگرم)", keyboardType = KeyboardType.Number)
                        }
                        2 -> {
                            Text("در یک هفته معمولی، چقدر فعال هستید؟", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            ActivityLevel.entries.forEach { lvl ->
                                ChoiceRow(lvl.label, activity == lvl) { activity = lvl }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        3 -> {
                            Text("هدف شما چیست؟", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Goal.entries.forEach { g ->
                                    ChoiceChip(g.label, goal == g, Modifier.weight(1f)) { goal = g }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            FaTextField(
                                value = goalWeight,
                                onValueChange = { goalWeight = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                                label = "وزن هدف (کیلوگرم)",
                                keyboardType = KeyboardType.Number
                            )
                            if (goal == Goal.LOSE && r != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "پیشنهاد برای کاهش سالم: حدود ${Fa.weight(r.idealWeight * 0.95)} کیلوگرم",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Teal
                                )
                            }
                        }
                        4 -> {
                            if (r != null && gender != null) {
                                SummaryRow("BMI", "${Fa.d(r.bmi, 1)} — ${r.bmiLabel}")
                                SummaryRow("متابولیسم پایه (BMR)", "${Fa.kcal(r.bmr)} کیلوکالری")
                                SummaryRow("کالری روزانه (TDEE)", "${Fa.kcal(r.tdee)} کیلوکالری")
                                SummaryRow("کالری هدف شما", "${Fa.kcal(r.targetKcal)} کیلوکالری")
                                SummaryRow("پروتئین / کربوهیدرات / چربی", "${Fa.kcal(r.proteinG)} / ${Fa.kcal(r.carbG)} / ${Fa.kcal(r.fatG)} گرم")
                                SummaryRow("هدف آب روزانه", "${Fa.num(r.waterGoalMl)} میلی‌لیتر")
                                SummaryRow("وزن ایده‌آل", "${Fa.weight(r.idealWeight)} کیلوگرم")
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "می‌توانید همه این اهداف را بعداً در صفحه «پروفایل بدنی» تغییر دهید.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .background(Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                            .clickable { step-- },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("→", color = Color.White, fontSize = 22.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                PrimaryButton(
                    text = if (step == 4) "شروع کنیم!" else "مرحله بعد",
                    enabled = valid(step),
                    onClick = {
                        if (step < 4) {
                            step++
                        } else {
                            scope.launch {
                                val profile = container.prefs.buildProfile(
                                    name = name.trim(),
                                    gender = gender ?: Gender.MALE,
                                    age = age.toIntOrNull() ?: 0,
                                    heightCm = height.toDoubleOrNull() ?: 0.0,
                                    weightKg = weight.toDoubleOrNull() ?: 0.0,
                                    goalWeightKg = goalWeight.toDoubleOrNull() ?: 0.0,
                                    activity = activity ?: ActivityLevel.SEDENTARY,
                                    goal = goal ?: Goal.KEEP
                                )
                                container.prefs.saveProfile(profile)
                                container.prefs.setOnboarded(true)
                                // اولین سنجش وزن
                                container.weightDao.insert(WeightLog(dayId = DayUtil.today(), weightKg = profile.weightKg))
                                // یادآورهای پیش‌فرض
                                val dao = container.reminderDao
                                if (dao.enabled().isEmpty()) {
                                    dao.insert(Reminder(type = 0, title = "وقت صبحانه", hour = 8, minute = 0))
                                    dao.insert(Reminder(type = 0, title = "وقت ناهار", hour = 13, minute = 0))
                                    dao.insert(Reminder(type = 0, title = "وقت شام", hour = 20, minute = 0))
                                    dao.insert(Reminder(type = 1, title = "لیوان آب", hour = 10, minute = 0))
                                    dao.insert(Reminder(type = 1, title = "لیوان آب", hour = 14, minute = 0))
                                    dao.insert(Reminder(type = 1, title = "لیوان آب", hour = 18, minute = 0))
                                    dao.insert(Reminder(type = 2, title = "سنجش وزن هفتگی", hour = 9, minute = 0, daysMask = 32))
                                    container.reminders.rescheduleAll()
                                }
                                onDone()
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .background(
                if (selected) Teal.copy(alpha = 0.15f) else Color(0xFFF2F5F8),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Teal else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) Teal.copy(alpha = 0.15f) else Color(0xFFF2F5F8),
                RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), color = if (selected) Teal else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        if (selected) Text("✓", color = Teal, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = Navy, textAlign = TextAlign.End)
    }
}
