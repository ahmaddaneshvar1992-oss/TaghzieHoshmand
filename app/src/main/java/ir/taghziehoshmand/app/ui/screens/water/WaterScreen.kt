package ir.taghziehoshmand.app.ui.screens.water

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.JalaliDate
import ir.taghziehoshmand.app.data.model.WaterLog
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.EmptyState
import ir.taghziehoshmand.app.ui.components.RingProgress
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/** پیگیری مصرف آب روزانه */
@Composable
fun WaterScreen(nav: NavHostController) {
    val container = TaghzieApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()
    val today = DayUtil.today()

    val logs by container.waterDao.byDay(today).collectAsState(initial = emptyList())
    val totalMl by container.waterDao.totalMl(today).collectAsState(initial = 0)
    val profile by container.prefs.profileFlow.collectAsState(initial = null)
    val goal = profile?.waterGoalMl ?: 2500

    var customDialog by remember { mutableStateOf(false) }
    var customMl by remember { mutableStateOf("") }
    var ramadanSaved by remember { mutableStateOf(false) }
    val settings by container.prefs.settingsFlow.collectAsState(initial = ir.taghziehoshmand.app.data.prefs.Settings())
    val appContext = LocalContext.current

    fun add(ml: Int) {
        scope.launch {
            container.waterDao.insert(WaterLog(dayId = today, ml = ml))
            // ویجت صفحه اصلی هم همگام شود
            runCatching { ir.taghziehoshmand.app.service.HomeWidgetProvider.updateAll(appContext) }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CurvedHeader(
            title = "مصرف آب",
            subtitle = "${JalaliDate.full(DayUtil.date(today))} — هدف: ${Fa.num(goal)} میلی‌لیتر",
            showBack = true,
            onBack = { nav.popBackStack() }
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RingProgress(
                    progress = totalMl.toFloat() / goal.coerceAtLeast(1),
                    modifier = Modifier.size(210.dp),
                    strokeWidth = 18.dp,
                    color = Teal,
                    trackColor = Color(0xFFE1EEF5)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💧", fontSize = 30.sp)
                        Text(Fa.num(totalMl), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
                        Text("از ${Fa.num(goal)} میلی‌لیتر", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${Fa.num((totalMl * 100 / goal.coerceAtLeast(1)).coerceAtMost(100))}٪",
                            color = Teal,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // دکمه‌های افزودن سریع
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WaterButton("لیوان", "🥛", 250, Modifier.weight(1f)) { add(250) }
                WaterButton("بطری", "🍶", 500, Modifier.weight(1f)) { add(500) }
                WaterButton("پارچ", "🫗", 750, Modifier.weight(1f)) { add(750) }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
                    .clickable { customDialog = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("➕ مقدار دلخواه...", color = Teal, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            // توزیع آب در حالت رمضان (نسخه ۱.۵)
            if (settings.ramadanMode) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🕌", fontSize = 18.sp)
                            Spacer(Modifier.width(8.dp))
                            Text("توزیع آب در رمضان", fontWeight = FontWeight.Bold, color = Navy)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "هدف ${Fa.num(goal)} میلی‌لیتر را فقط بین افطار (${settings.iftarLabel()}) تا سحر (${settings.suhoorLabel()}) بنوشید؛ هر ۹۰ دقیقه یک لیوان بزرگ و با افطار و سحر، دو لیوان.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Teal, RoundedCornerShape(12.dp))
                                .clickable {
                                    scope.launch {
                                        // حذف یادآورهای قبلی رمضان و ساخت از نو
                                        container.reminderDao.all().first()
                                            .filter { it.title.contains("رمضان") }
                                            .forEach { r ->
                                                container.reminders.cancel(r.id)
                                                container.reminderDao.delete(r.id)
                                            }
                                        val span = (settings.suhoorMinute + 1440 - settings.iftarMinute) % 1440
                                        val count = (span / 90).coerceIn(2, 8)
                                        var t = settings.iftarMinute + 30
                                        repeat(count) {
                                            val hh = (t / 60) % 24
                                            val mm = t % 60
                                            container.reminders.schedule(
                                                ir.taghziehoshmand.app.data.model.Reminder(
                                                    type = 1, title = "یادآور آب رمضان 💧", hour = hh, minute = mm
                                                )
                                            )
                                            t += 90
                                        }
                                        ramadanSaved = true
                                    }
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ساخت یادآور آب در بازه افطار تا سحر",
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                        }
                        if (ramadanSaved) {
                            Spacer(Modifier.height(6.dp))
                            Text("یادآورها ساخته شد ✅ در «یادآورها» قابل ویرایش است.", style = MaterialTheme.typography.bodySmall, color = Teal)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // توصیه
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Teal.copy(alpha = 0.1f))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (settings.ramadanMode)
                            if (totalMl < goal * 0.5) "در پنجره افطار تا سحر، آب را به‌تدریج بنوشید؛ با افطار دو لیوان شروع کنید."
                            else if (totalMl < goal) "خوب پیش رفته‌اید؛ تا سحر به هدف برسید."
                            else "هدف آب امروز کامل شد؛ عالی بود! 🎉"
                        else
                            if (totalMl < goal * 0.5) "عقب افتاده‌اید! همین حالا دو لیوان آب بنوشید."
                            else if (totalMl < goal) "خوب پیش رفته‌اید؛ ادامه دهید تا هدف کامل شود."
                            else "هدف آب امروز کامل شد؛ عالی بود! 🎉",
                        style = MaterialTheme.typography.bodySmall,
                        color = Navy,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // لیست ثبت‌ها
            Text("ثبت‌های امروز", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (logs.isEmpty()) {
                EmptyState("امروز هنوز آبی ثبت نشده است.", "💧")
            } else {
                logs.forEach { log ->
                    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(log.createdAt), ZoneId.systemDefault())
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("💧", fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("${Fa.num(log.ml)} میلی‌لیتر", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Text(
                                "${Fa.num("%02d:%02d".format(time.hour, time.minute))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(onClick = { scope.launch { container.waterDao.delete(log.id) } }) {
                                Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "برای یادآوری منظم آب، صفحه «یادآورها» را تنظیم کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(120.dp))
        }
    }

    if (customDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text("مقدار دلخواه آب") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = customMl,
                    onValueChange = { customMl = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("میلی‌لیتر") }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val ml = customMl.toIntOrNull() ?: 0
                    if (ml in 10..3000) {
                        add(ml)
                        customDialog = false
                        customMl = ""
                    }
                }) { Text("افزودن", color = Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { customDialog = false }) { Text("انصراف") }
            }
        )
    }
}

@Composable
private fun WaterButton(label: String, emoji: String, ml: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(92.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("${Fa.num(ml)} میلی‌لیتر", fontSize = 10.sp, color = Teal, fontWeight = FontWeight.Bold)
        }
    }
}
