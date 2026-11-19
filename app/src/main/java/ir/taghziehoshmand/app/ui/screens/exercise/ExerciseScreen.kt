package ir.taghziehoshmand.app.ui.screens.exercise

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.model.ExerciseLog
import ir.taghziehoshmand.app.data.model.StepLog
import ir.taghziehoshmand.app.data.repo.ExerciseDto
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.EmptyState
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.theme.Green
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch

/** ثبت فعالیت ورزشی و محاسبه کالری سوزانده‌شده */
@Composable
fun ExerciseScreen(nav: NavHostController) {
    val container = TaghzieApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()
    val today = DayUtil.today()

    val logs by container.exerciseDao.byDay(today).collectAsState(initial = emptyList())
    val burned by container.exerciseDao.kcalSum(today).collectAsState(initial = 0.0)
    val steps by container.stepDao.forDay(today).collectAsState(initial = null)
    val profile by container.prefs.profileFlow.collectAsState(initial = null)

    var exercises by remember { mutableStateOf<List<ExerciseDto>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ExerciseDto?>(null) }
    var minutes by remember { mutableStateOf("30") }
    var stepsDialog by remember { mutableStateOf(false) }
    var stepsInput by remember { mutableStateOf("") }

    LaunchedEffectEx { exercises = container.catalog.exercises() }

    val weight = profile?.weightKg ?: 70.0
    val filtered = exercises.filter { query.isBlank() || it.n.contains(query.trim()) || it.c.contains(query.trim()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CurvedHeader(
            title = "ورزش و فعالیت",
            subtitle = "${JalaliDateShort(today)} — سوزانده‌شده: ${Fa.kcal(burned)} کیلوکالری",
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

            // خلاصه امروز
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(56.dp).background(Green.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text("🔥", fontSize = 24.sp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("کالری سوزانده امروز", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${Fa.kcal(burned)} کیلوکالری", style = MaterialTheme.typography.headlineMedium, color = Green)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("قدم‌ها: ${Fa.num(steps ?: 0)}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "هدف: ${Fa.num(profile?.stepGoal ?: 10000)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "ویرایش قدم",
                            style = MaterialTheme.typography.labelMedium,
                            color = Teal,
                            modifier = Modifier.clickable { stepsDialog = true; stepsInput = (steps ?: 0).toString() }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // فعالیت‌های امروز
            if (logs.isNotEmpty()) {
                Text("فعالیت‌های امروز", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                logs.forEach { log ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(log.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${Fa.num(log.minutes)} دقیقه • ${Fa.kcal(log.kcal)} کیلوکالری • MET ${Fa.d(log.met, 1)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Teal
                                )
                            }
                            IconButton(onClick = { scope.launch { container.exerciseDao.delete(log.id) } }) {
                                Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text("انتخاب فعالیت ورزشی", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("جستجو در فعالیت‌ها (مثل: دویدن، وزنه، یوگا)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(10.dp))

            filtered.forEach { ex ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable {
                        selected = ex
                        minutes = "30"
                    }
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.n, style = MaterialTheme.typography.titleSmall)
                            Text(ex.c, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${Fa.kcal(ex.m * weight * 0.5)} کالری\nدر ۳۰ دقیقه",
                            style = MaterialTheme.typography.bodySmall,
                            color = Green,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }

            Spacer(Modifier.height(120.dp))
        }
    }

    // گفتگوی ثبت فعالیت
    selected?.let { ex ->
        val kcal = ex.m * weight * (minutes.toDoubleOrNull() ?: 0.0) / 60.0
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(ex.n) },
            text = {
                Column {
                    Text("MET این فعالیت: ${Fa.d(ex.m, 1)} — محاسبه بر اساس وزن ${Fa.weight(weight)} کیلوگرم", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("مدت (دقیقه)") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("کالری سوزانده: ${Fa.kcal(kcal)} کیلوکالری", style = MaterialTheme.typography.titleMedium, color = Green)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = minutes.toIntOrNull() ?: 0
                    if (m in 1..600) {
                        scope.launch {
                            container.exerciseDao.insert(
                                ExerciseLog(
                                    dayId = today, name = ex.n, minutes = m,
                                    met = ex.m, kcal = ex.m * weight * m / 60.0
                                )
                            )
                        }
                        selected = null
                    }
                }) { Text("ثبت", color = Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("انصراف") } }
        )
    }

    // گفتگوی قدم‌ها
    if (stepsDialog) {
        AlertDialog(
            onDismissRequest = { stepsDialog = false },
            title = { Text("ثبت قدم‌های امروز") },
            text = {
                OutlinedTextField(
                    value = stepsInput,
                    onValueChange = { stepsInput = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("تعداد قدم") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = stepsInput.toIntOrNull() ?: 0
                    if (s >= 0) {
                        scope.launch { container.stepDao.set(StepLog(dayId = today, steps = s)) }
                        stepsDialog = false
                    }
                }) { Text("ذخیره", color = Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { stepsDialog = false }) { Text("انصراف") } }
        )
    }
}

@Composable
private fun LaunchedEffectEx(block: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

private fun JalaliDateShort(dayId: Long): String =
    ir.taghziehoshmand.app.core.JalaliDate.full(ir.taghziehoshmand.app.core.DayUtil.date(dayId))
