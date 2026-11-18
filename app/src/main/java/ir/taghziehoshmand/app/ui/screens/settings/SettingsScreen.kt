package ir.taghziehoshmand.app.ui.screens.settings

import android.Manifest
import android.content.Intent
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.prefs.AiConfig
import ir.taghziehoshmand.app.data.prefs.Settings
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.components.SectionTitle
import ir.taghziehoshmand.app.ui.screens.profile.ChoiceChip
import ir.taghziehoshmand.app.ui.theme.Green
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch

/** تنظیمات: ظاهر، هوش مصنوعی، خروجی گزارش، پشتیبان‌گیری، Health Connect */
@Composable
fun SettingsScreen(nav: NavHostController) {
    val context = LocalContext.current
    val container = TaghzieApp.container(context)
    val scope = rememberCoroutineScope()

    val settings by container.prefs.settingsFlow.collectAsState(initial = Settings())
    val hcSync by container.prefs.hcSyncFlow.collectAsState(initial = false)
    var baseUrl by remember(settings.ai.baseUrl) { mutableStateOf(settings.ai.baseUrl) }
    var apiKey by remember(settings.ai.apiKey) { mutableStateOf(settings.ai.apiKey) }
    var model by remember(settings.ai.model) { mutableStateOf(settings.ai.model) }
    var toast by remember { mutableStateOf<String?>(null) }

    val hcAvailable = container.healthConnect.available()

    // پشتیبان‌گیری و بازیابی
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = container.export.backupTo(uri)
                toast = if (ok) "پشتیبان‌گیری کامل شد ✅" else "پشتیبان‌گیری ناموفق بود"
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = container.export.restoreFrom(uri)
                toast = if (ok) "داده‌ها بازیابی شد ✅" else "فایل پشتیبان معتبر نیست"
            }
        }
    }

    // مجوز Health Connect
    val hcPermissions = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(HydrationRecord::class),
        HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class)
    )
    val hcPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { toast = "پاسخ مجوزهای Health Connect ثبت شد؛ اکنون می‌توانید همگام‌سازی کنید" }

    // مجوز نوتیفیکیشن
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        toast = if (it) "مجوز نوتیفیکیشن داده شد" else "بدون این مجوز یادآورها نمایش داده نمی‌شوند"
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CurvedHeader(title = "تنظیمات", showBack = true, onBack = { nav.popBackStack() })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(14.dp))

            // ظاهر
            SectionTitle("ظاهر و پوسته")
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "سیستم" to ir.taghziehoshmand.app.ui.theme.ThemeMode.SYSTEM,
                        "روشن ☀️" to ir.taghziehoshmand.app.ui.theme.ThemeMode.LIGHT,
                        "تیره 🌙" to ir.taghziehoshmand.app.ui.theme.ThemeMode.DARK
                    ).forEach { (label, mode) ->
                        ChoiceChip(label, settings.darkMode == mode, Modifier.weight(1f)) {
                            scope.launch { container.prefs.saveSettings(settings.copy(darkMode = mode)) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // هوش مصنوعی
            SectionTitle("هوش مصنوعی (اختیاری)")
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "برای پاسخ‌های پیشرفته چت‌بات و تحلیل عکس غذا، هر سرویس سازگار با OpenAI را پیکربندی کنید (مثل OpenAI، OpenRouter یا Groq). بدون پیکربندی، چت‌بات به‌صورت آفلاین پاسخ می‌دهد.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("آدرس پایه API (مثلاً https://api.openai.com/v1)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("کلید API") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("نام مدل (مثلاً gpt-4o-mini)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "ذخیره تنظیمات هوش مصنوعی",
                        emoji = "🤖",
                        container = Navy,
                        onClick = {
                            scope.launch {
                                container.prefs.saveSettings(settings.copy(ai = AiConfig(baseUrl.trim(), apiKey.trim(), model.trim())))
                                toast = "تنظیمات هوش مصنوعی ذخیره شد ✅"
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // خروجی گزارش
            SectionTitle("خروجی گزارش")
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text("گزارش ۳۰ روز اخیر (وعده‌ها، وزن، آب و ورزش):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            Modifier.weight(1f).background(Teal, RoundedCornerShape(12.dp)).clickable {
                                scope.launch {
                                    val f = container.export.exportPdf(DayUtil.today() - 29, DayUtil.today())
                                    if (f != null) share(context, container.export.shareIntent(f, "application/pdf"))
                                    else toast = "داده‌ای برای گزارش وجود ندارد"
                                }
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("PDF 📄", color = Color.White, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1f).background(Navy, RoundedCornerShape(12.dp)).clickable {
                                scope.launch {
                                    val f = container.export.exportXlsx(DayUtil.today() - 29, DayUtil.today())
                                    if (f != null) share(context, container.export.shareIntent(f, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                    else toast = "داده‌ای برای گزارش وجود ندارد"
                                }
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Excel 📊", color = Color.White, fontWeight = FontWeight.Bold) }
                        Box(
                            Modifier.weight(1f).background(Color(0xFF10B981), RoundedCornerShape(12.dp)).clickable {
                                scope.launch {
                                    val f = container.export.exportCsv(DayUtil.today() - 29, DayUtil.today())
                                    if (f != null) share(context, container.export.shareIntent(f, "text/csv"))
                                    else toast = "داده‌ای برای گزارش وجود ندارد"
                                }
                            }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("CSV 📃", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // پشتیبان‌گیری
            SectionTitle("پشتیبان‌گیری و بازیابی")
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text("کل داده‌های شما (وعده‌ها، وزن، آب، ورزش، برنامه، یادآورها) در یک فایل JSON ذخیره یا بازیابی می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).background(Teal, RoundedCornerShape(12.dp)).clickable {
                            backupLauncher.launch("taghzie_backup.json")
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("پشتیبان‌گیری 💾", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).background(Navy, RoundedCornerShape(12.dp)).clickable {
                            restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("بازیابی ♻️", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Health Connect
            SectionTitle("همگام‌سازی سلامت (Google Fit / Health Connect)")
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (hcAvailable) "Health Connect روی این دستگاه فعال است. وزن، آب، کالری ورزشی و قدم‌ها می‌توانند در آن ثبت شوند."
                        else "اپ Health Connect روی این دستگاه نصب نیست. از فروشگاه Play آن را نصب کنید؛ این سرویس جایگزین رسمی Google Fit در اندروید جدید است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hcAvailable) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFF59E0B)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("ثبت خودکار داده‌ها در Health Connect", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = hcSync,
                            onCheckedChange = { v -> scope.launch { container.prefs.setHcSync(v) } },
                            colors = SwitchDefaults.colors(checkedTrackColor = Teal)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).background(Teal, RoundedCornerShape(12.dp)).clickable {
                            hcPermissionLauncher.launch(hcPermissions)
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("دریافت مجوزها 🔑", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(Modifier.weight(1f).background(Green, RoundedCornerShape(12.dp)).clickable {
                            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            scope.launch {
                                val res = container.healthConnect.syncDay(DayUtil.today())
                                toast = res.message
                            }
                        }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            Text("همگام‌سازی امروز 🔄", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // یادآورها و نوتیفیکیشن
            SectionTitle("یادآورها و نوتیفیکیشن")
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    TextButton(onClick = { nav.navigate("reminders") }) {
                        Text("⏰ مدیریت یادآورها (وعده غذایی، آب، وزن)", color = Teal, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }) {
                        Text("🔔 درخواست مجوز نوتیفیکیشن", color = Navy)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // درباره
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(14.dp)) {
                    Text("درباره «تغذیه هوشمند»", fontWeight = FontWeight.Bold, color = Navy)
                    Spacer(Modifier.height(6.dp))
                    Text("نسخه ${Fa.num("1.0.0")} — کاملاً فارسی و آفلاین. داده‌ها فقط روی گوشی شما ذخیره می‌شوند. پایگاه غذایی: ۳۳۹ غذای ایرانی و بین‌المللی. چت‌بات آفلاین + پشتیبانی از سرویس‌های OpenAI-compatible.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            toast?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Teal, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(120.dp))
        }
    }
}

private fun share(context: android.content.Context, intent: Intent) {
    val chooser = Intent.createChooser(intent, "ذخیره یا اشتراک گزارش")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
