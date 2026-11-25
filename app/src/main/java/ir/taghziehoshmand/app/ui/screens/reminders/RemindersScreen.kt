package ir.taghziehoshmand.app.ui.screens.reminders

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.model.Reminder
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.EmptyState
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch

private val dayLabels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

/** صفحه یادآورها: وعده غذایی، آب، وزن */
@Composable
fun RemindersScreen(nav: NavHostController) {
    val context = LocalContext.current
    val container = TaghzieApp.container(context)
    val scope = rememberCoroutineScope()

    val reminders by container.reminderDao.all().collectAsState(initial = emptyList())
    var editDialog by remember { mutableStateOf<Reminder?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            CurvedHeader(title = "یادآورهای هوشمند", showBack = true, onBack = { nav.popBackStack() })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "یادآورها با نوتیفیکیشن سیستم کار می‌کنند — حتی وقتی اپ بسته است.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))

                if (reminders.isEmpty()) {
                    EmptyState("یادآوری ندارید. با دکمه ➕ یک یادآور بسازید.", "⏰")
                } else {
                    reminders.forEach { r ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable { editDialog = r }
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .width(44.dp)
                                        .height(44.dp)
                                        .background(
                                            when (r.type) { 1 -> Teal.copy(alpha = 0.15f); 2 -> Color(0xFFF59E0B).copy(alpha = 0.15f); else -> Navy.copy(alpha = 0.08f) },
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (r.type == 1) "💧" else if (r.type == 2) "⚖️" else "🍽", fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.title, style = MaterialTheme.typography.titleSmall, color = if (r.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        "${Fa.num("%02d:%02d".format(r.hour, r.minute))} • ${reminderType(r.type)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Teal
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        dayLabels.forEachIndexed { i, d ->
                                            val on = (r.daysMask and (1 shl i)) != 0
                                            Box(
                                                Modifier
                                                    .background(if (on) Teal else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(d, fontSize = 9.sp, color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                                Switch(
                                    checked = r.enabled,
                                    onCheckedChange = { v ->
                                        scope.launch {
                                            container.reminders.update(r.copy(enabled = v))
                                        }
                                    },
                                    colors = SwitchDefaults.colors(checkedTrackColor = Teal)
                                )
                                IconButton(onClick = {
                                    scope.launch { container.reminders.delete(r) }
                                }) {
                                    Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(90.dp))
            }
        }

        // دکمه شناور افزودن
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Box(
                Modifier
                    .width(58.dp)
                    .height(58.dp)
                    .background(Teal, RoundedCornerShape(18.dp))
                    .clickable { addDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, "افزودن یادآور", tint = Color.White, modifier = Modifier.height(26.dp))
            }
        }
    }

    // گفتگوی افزودن/ویرایش
    val showDialog = addDialog || editDialog != null
    if (showDialog) {
        val editing = editDialog
        var type by remember(showDialog) { mutableStateOf(editing?.type ?: 0) }
        var title by remember(showDialog) { mutableStateOf(editing?.title ?: "") }
        var hour by remember(showDialog) { mutableStateOf((editing?.hour ?: 8).toString()) }
        var minute by remember(showDialog) { mutableStateOf("%02d".format(editing?.minute ?: 0)) }
        var daysMask by remember(showDialog) { mutableStateOf(editing?.daysMask ?: 127) }

        AlertDialog(
            onDismissRequest = { addDialog = false; editDialog = null },
            title = { Text(if (editing == null) "یادآور جدید" else "ویرایش یادآور") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("🍽 وعده" to 0, "💧 آب" to 1, "⚖️ وزن" to 2).forEach { (label, t) ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .background(if (type == t) Teal.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .clickable { type = t; if (title.isBlank()) title = when (t) { 1 -> "لیوان آب"; 2 -> "سنجش وزن"; else -> "وقت وعده" } }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, color = if (type == t) Teal else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, label = { Text("عنوان") })
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hour,
                            onValueChange = { hour = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("ساعت (۰-۲۳)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = minute,
                            onValueChange = { minute = it.filter { c -> c.isDigit() }.take(2) },
                            label = { Text("دقیقه") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("روزها:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dayLabels.forEachIndexed { i, d ->
                            val on = (daysMask and (1 shl i)) != 0
                            Box(
                                Modifier
                                    .background(if (on) Teal else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .clickable { daysMask = if (on) daysMask and (1 shl i).inv() else daysMask or (1 shl i) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(d, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { daysMask = 127 }) { Text("همه روزها", color = Teal) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val h = hour.toIntOrNull() ?: 0
                        val m = minute.toIntOrNull() ?: 0
                        if (h in 0..23 && m in 0..59 && title.isNotBlank()) {
                            scope.launch {
                                if (editing != null) {
                                    container.reminders.update(editing.copy(type = type, title = title, hour = h, minute = m, daysMask = daysMask))
                                } else {
                                    container.reminders.schedule(Reminder(type = type, title = title, hour = h, minute = m, daysMask = daysMask))
                                }
                                addDialog = false; editDialog = null
                            }
                        }
                    },
                    enabled = (hour.toIntOrNull() ?: 0) in 0..23 && title.isNotBlank()
                ) { Text("ذخیره", color = Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { addDialog = false; editDialog = null }) { Text("انصراف") } }
        )
    }
}

private fun reminderType(type: Int): String = when (type) {
    0 -> "وعده غذایی"; 1 -> "مصرف آب"; else -> "سنجش وزن"
}
