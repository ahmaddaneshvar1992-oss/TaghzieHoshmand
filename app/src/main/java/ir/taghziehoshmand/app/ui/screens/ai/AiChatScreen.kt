package ir.taghziehoshmand.app.ui.screens.ai

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import ir.taghziehoshmand.app.data.model.ChatMsg
import ir.taghziehoshmand.app.ui.Routes
import ir.taghziehoshmand.app.ui.components.FaTextField
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** چت هوش مصنوعی تغذیه — مطابق طراحی مرجع */
@Composable
fun AiChatScreen(nav: NavHostController) {
    val context = LocalContext.current
    val container = TaghzieApp.container(context)
    val scope = rememberCoroutineScope()
    val today = DayUtil.today()

    val messages by container.chatDao.all().collectAsState(initial = emptyList())
    val profile by container.prefs.profileFlow.collectAsState(initial = null)
    val settings by container.prefs.settingsFlow.collectAsState(initial = ir.taghziehoshmand.app.data.prefs.Settings())
    val isOnline by container.connectivity.online.collectAsState()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // متن نشانگر «در حال کار» بسته به نوع درخواست (متن یا تحلیل عکس) تغییر می‌کند
    var busyText by remember { mutableStateOf("در حال فکر کردن...") }
    val listState = rememberLazyListState()

    val aiModeText = when {
        !settings.aiOnline -> "⚙️ حالت آفلاین (از تنظیمات)"
        isOnline -> "🟢 آنلاین — پاسخ از هوش مصنوعی ابری"
        else -> "🔴 آفلاین — پایگاه دانش داخلی"
    }
    val aiModeColor = when {
        !settings.aiOnline || !isOnline -> Color(0xFF8A94A6)
        else -> Teal
    }

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // خواندن عکس و تحلیل — هر خطا به‌صورت پیام ربات داخل چت نمایش داده می‌شود (بدون شکست بی‌صدا)
    suspend fun analyzeImageUri(uri: Uri) {
        busyText = "در حال تحلیل عکس 📸 (چند ثانیه)..."
        sending = true
        try {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (bytes == null) {
                container.chatDao.insert(
                    ChatMsg(role = 1, text = "⚠️ خواندن عکس ممکن نشد؛ لطفاً دوباره تلاش کنید یا عکس دیگری انتخاب کنید.")
                )
            } else {
                container.ai.analyzeFoodImage(bytes, today)
            }
        } catch (e: Exception) {
            container.chatDao.insert(
                ChatMsg(
                    role = 1,
                    text = "⚠️ تحلیل عکس با خطا مواجه شد (${e.message?.take(90)}).\nلطفاً دوباره تلاش کنید یا نام غذا را در «ثبت وعده» جستجو و ثبت کنید."
                )
            )
        } finally {
            sending = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { analyzeImageUri(uri) }
    }

    // گرفتن عکس با دوربین سیستم — چون مانیفست مجوز CAMERA را اعلام کرده، باید قبل از اجرای دوربین مجوز رانتایم گرفته شود؛
    // در غیر این صورت دوربین سیستم باعث SecurityException و بسته‌شدن اپ می‌شود
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCameraUri
        if (ok && uri != null) scope.launch { analyzeImageUri(uri) }
    }

    fun startCameraCapture() {
        val dir = java.io.File(context.filesDir, "photos").apply { mkdirs() }
        val file = java.io.File(dir, "food_${System.currentTimeMillis()}.jpg")
        val uri = try {
            androidx.core.content.FileProvider.getUriForFile(context, "ir.taghziehoshmand.app.fileprovider", file)
        } catch (e: Exception) {
            Toast.makeText(context, "ایجاد فایل عکس ممکن نشد", Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = uri
        try {
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "اجرای دوربین ممکن نشد؛ از گالری استفاده کنید", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCameraCapture()
        else Toast.makeText(context, "برای گرفتن عکس، مجوز دوربین را تأیید کنید؛ یا از گالری انتخاب کنید", Toast.LENGTH_LONG).show()
    }

    fun shootFoodPhoto() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) startCameraCapture() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun send(text: String) {
        if (text.isBlank() || sending) return
        input = ""
        busyText = "در حال فکر کردن..."
        scope.launch {
            sending = true
            runCatching { container.ai.sendUserMessage(text.trim(), today) }
            sending = false
        }
    }

    // imePadding: وقتی صفحه‌کلید باز می‌شود، کل صفحه (و نوار ورودی) بالای صفحه‌کلید می‌آید
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // هدر سرمه‌ای با ربات
        Box(
            Modifier
                .fillMaxWidth()
                .background(Navy)
                .padding(top = 40.dp, bottom = 22.dp)
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(58.dp)
                        .background(Teal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🤖", fontSize = 28.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text("هوش مصنوعی تغذیه", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("مشاور تغذیه هوشمند و شخصی شما", color = Color(0xFFB9CFE0), fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                // نشانگر زنده حالت: آنلاین (ابری) / آفلاین (پایگاه دانش داخلی)
                Box(
                    Modifier
                        .background(aiModeColor.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(aiModeText, color = Color.White, fontSize = 11.sp)
                }
            }
        }

        // پیام‌ها
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(20.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("سلام ${profile?.name ?: ""}! 👋", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "مشاور تغذیه شما هستم. می‌توانید بپرسید:\n• امروز چقدر کالری خوردم؟\n• برنامه غذایی هفتگی بده\n• جایگزین برنج سفید چی بخورم؟\n• وضعیت آب و ورزش من چطوره؟",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!settings.aiOnline || !isOnline) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (!settings.aiOnline)
                                    "⚙️ حالت آفلاین فعال است؛ پاسخ‌ها از پایگاه دانش داخلی داده می‌شود. برای پاسخ‌های ابری، در تنظیمات فعالش کنید."
                                else
                                    "📶 اینترنت متصل نیست؛ همین حالا هم پاسخ می‌دهم (پایگاه دانش داخلی). با وصل شدن اینترنت، پاسخ‌های ابری خودکار فعال می‌شوند.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Teal
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { m ->
                    if (m.role == 0) {
                        // پیام کاربر — سمت راست (شروع در RTL)
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                Modifier
                                    .weight(1f),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Box(
                                    Modifier
                                        .background(Teal, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        m.text,
                                        color = Color.White,
                                        fontSize = 13.5.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // پاسخ ربات — با آواتار
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Box(
                                    Modifier
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        m.text,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.5.sp,
                                        lineHeight = 21.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .background(Teal.copy(alpha = 0.16f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🤖", fontSize = 18.sp)
                            }
                        }
                    }
                }
                if (sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(busyText, color = Teal, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // چیپ‌های پیشنهادی
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
        ) {
            items(listOf("📅 برنامه هفتگی", "🔥 کالری امروز", "🍃 جایگزین غذا", "💧 وضعیت آب", "⚖️ پیشرفت وزن")) { chip ->
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
                        .clickable { send(chip.substringAfter(" ")) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(chip, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // نوار ورودی
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(Teal, CircleShape)
                    .clickable { shootFoodPhoto() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PhotoCamera, "گرفتن عکس غذا", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .background(Navy, CircleShape)
                    .clickable { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PhotoLibrary, "انتخاب از گالری", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            FaTextField(
                value = input,
                onValueChange = { input = it },
                label = "سوال یا درخواست خود را بنویسید...",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .background(if (input.isNotBlank() && !sending) Teal else Teal.copy(alpha = 0.4f), CircleShape)
                    .clickable { send(input) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "ارسال", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}
