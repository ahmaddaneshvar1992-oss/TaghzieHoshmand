package ir.taghziehoshmand.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.ActivityLevel
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.Gender
import ir.taghziehoshmand.app.core.Goal
import ir.taghziehoshmand.app.core.HealthCalc
import ir.taghziehoshmand.app.data.model.WeightLog
import ir.taghziehoshmand.app.data.prefs.UserProfile
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.FaTextField
import ir.taghziehoshmand.app.ui.components.LineChart
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.components.SectionTitle
import ir.taghziehoshmand.app.ui.theme.Amber
import ir.taghziehoshmand.app.ui.theme.Green
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Red
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch

/** پروفایل بدنی — ویرایش داده‌ها، BMI و نمودار وزن */
@Composable
fun BodyProfileScreen(nav: NavHostController) {
    val container = TaghzieApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()

    val profile by container.prefs.profileFlow.collectAsState(initial = null)
    val weights by container.weightDao.since(DayUtil.today() - 29).collectAsState(initial = emptyList())

    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var goalWeight by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.MALE) }
    var activity by remember { mutableStateOf(ActivityLevel.SEDENTARY) }
    var goal by remember { mutableStateOf(Goal.KEEP) }
    var showActivityPicker by remember { mutableStateOf(false) }
    var showGoalPicker by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (!loaded) {
            loaded = true
            name = p.name
            weight = p.weightKg.toString()
            height = p.heightCm.toString()
            age = p.age.toString()
            goalWeight = p.goalWeightKg.toString()
            gender = p.gender
            activity = p.activity
            goal = p.goal
        }
    }

    val w = weight.toDoubleOrNull() ?: 0.0
    val h = height.toDoubleOrNull() ?: 0.0
    val bmi = HealthCalc.bmi(w, h)
    val r = if (w > 0 && h > 0 && (age.toIntOrNull() ?: 0) > 0)
        HealthCalc.calculate(w, h, age.toIntOrNull() ?: 0, gender, activity, goal) else null

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CurvedHeader(
            title = "پروفایل بدنی",
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

            FaTextField(name, { name = it }, "نام")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FaTextField(weight, { weight = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, "وزن (کیلوگرم)", Modifier.weight(1f), KeyboardType.Number)
                FaTextField(height, { height = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, "قد (سانتی‌متر)", Modifier.weight(1f), KeyboardType.Number)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FaTextField(age, { age = it.filter { c -> c.isDigit() }.take(3) }, "سن (سال)", Modifier.weight(1f), KeyboardType.Number)
                FaTextField(goalWeight, { goalWeight = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, "وزن هدف", Modifier.weight(1f), KeyboardType.Number)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceChip("مرد 👨", gender == Gender.MALE, Modifier.weight(1f)) { gender = Gender.MALE }
                ChoiceChip("زن 👩", gender == Gender.FEMALE, Modifier.weight(1f)) { gender = Gender.FEMALE }
            }
            Spacer(Modifier.height(10.dp))

            PickerRow(label = "سطح فعالیت", value = activity.label) { showActivityPicker = true }
            Spacer(Modifier.height(10.dp))
            PickerRow(label = "هدف", value = goal.label) { showGoalPicker = true }

            Spacer(Modifier.height(16.dp))

            // BMI
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BMI شما", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (bmi > 0) "${Fa.d(bmi, 1)} — ${HealthCalc.bmiLabel(bmi)}" else "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Teal
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // خط‌کش BMI
                    Box(Modifier.fillMaxWidth().height(14.dp).background(Color(0xFFEDF2F6), RoundedCornerShape(7.dp))) {
                        Row(Modifier.fillMaxSize()) {
                            val fractions = listOf(18.5f, 25f, 30f)
                            val span = 42f - 14f
                            val seg1 = ((18.5f - 14f) / span).coerceIn(0f, 1f)
                            val seg2 = ((25f - 18.5f) / span).coerceIn(0f, 1f)
                            val seg3 = ((30f - 25f) / span).coerceIn(0f, 1f)
                            Box(Modifier.fillMaxWidth(seg1).fillMaxHeight().background(Teal.copy(alpha = 0.5f)))
                            Box(Modifier.fillMaxWidth(seg2).fillMaxHeight().background(Green.copy(alpha = 0.6f)))
                            Box(Modifier.fillMaxWidth(seg3).fillMaxHeight().background(Amber.copy(alpha = 0.7f)))
                            Box(Modifier.fillMaxWidth().fillMaxHeight().background(Red.copy(alpha = 0.7f)))
                        }
                        if (bmi in 14.0..42.0) {
                            val pos = ((bmi - 14) / 28).toFloat().coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .fillMaxWidth(pos)
                                    .fillMaxHeight()
                            ) {
                                Box(
                                    Modifier
                                        .size(width = 6.dp, height = 20.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(Navy, RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("۱۴", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("۴۲", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (r != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "وزن ایده‌آل برای قد شما: ${Fa.weight(r.idealWeight)} کیلوگرم • BMR: ${Fa.kcal(r.bmr)} • TDEE: ${Fa.kcal(r.tdee)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SectionTitle("نمودار تغییرات وزن — ۳۰ روز گذشته")
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    LineChart(
                        points = weights.map { it.weightKg.toFloat() },
                        labels = weights.map { ir.taghziehoshmand.app.core.JalaliDate.short(ir.taghziehoshmand.app.core.DayUtil.date(it.dayId)) },
                        lineColor = Teal,
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        unitSuffix = " کیلو"
                    )
                }
            }

            if (r != null) {
                Spacer(Modifier.height(14.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("اهداف محاسبه‌شده", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(6.dp))
                        Text("کالری هدف: ${Fa.kcal(r.targetKcal)} کیلوکالری روزانه", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("پروتئین ${Fa.kcal(r.proteinG)} • کربوهیدرات ${Fa.kcal(r.carbG)} • چربی ${Fa.kcal(r.fatG)} گرم", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("آب: ${Fa.num(r.waterGoalMl)} میلی‌لیتر در روز", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            toast?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Green, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "ذخیره اطلاعات",
                emoji = "💾",
                enabled = w > 0 && h > 0 && (age.toIntOrNull() ?: 0) > 0,
                onClick = {
                    scope.launch {
                        val p = profile ?: return@launch
                        val newProfile = p.copy(
                            name = name.ifBlank { p.name },
                            weightKg = w, heightCm = h,
                            age = age.toIntOrNull() ?: p.age,
                            goalWeightKg = goalWeight.toDoubleOrNull() ?: p.goalWeightKg,
                            gender = gender, activity = activity, goal = goal
                        ).let { np ->
                            val calc = HealthCalc.calculate(np.weightKg, np.heightCm, np.age, np.gender, np.activity, np.goal)
                            np.copy(
                                targetKcal = calc.targetKcal,
                                proteinG = calc.proteinG, carbG = calc.carbG, fatG = calc.fatG,
                                waterGoalMl = calc.waterGoalMl
                            )
                        }
                        container.prefs.saveProfile(newProfile)
                        if (kotlin.math.abs(newProfile.weightKg - p.weightKg) > 0.05) {
                            container.weightDao.insert(WeightLog(dayId = DayUtil.today(), weightKg = newProfile.weightKg))
                        }
                        toast = "اطلاعات ذخیره شد ✅"
                    }
                }
            )
            Spacer(Modifier.height(120.dp))
        }
    }

    if (showActivityPicker) {
        OptionDialog(
            title = "سطح فعالیت",
            options = ActivityLevel.entries.map { it.label },
            selected = activity.label,
            onSelect = { i -> activity = ActivityLevel.entries[i]; showActivityPicker = false },
            onDismiss = { showActivityPicker = false }
        )
    }
    if (showGoalPicker) {
        OptionDialog(
            title = "هدف وزن",
            options = Goal.entries.map { it.label },
            selected = goal.label,
            onSelect = { i -> goal = Goal.entries[i]; showGoalPicker = false },
            onDismiss = { showGoalPicker = false }
        )
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(50.dp)
            .background(if (selected) Teal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Teal else MaterialTheme.colorScheme.onSurface, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = Teal, textAlign = TextAlign.End)
            Spacer(Modifier.width(6.dp))
            Text("✏️", fontSize = 14.sp)
        }
    }
}

@Composable
private fun OptionDialog(title: String, options: List<String>, selected: String, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, opt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (opt == selected) Teal.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { onSelect(i) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(opt, Modifier.weight(1f), color = if (opt == selected) Teal else MaterialTheme.colorScheme.onSurface)
                        if (opt == selected) Text("✓", color = Teal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}
