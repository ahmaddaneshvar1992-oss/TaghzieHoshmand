package ir.taghziehoshmand.app.ui.screens.ai

import android.graphics.BitmapFactory
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
import kotlinx.coroutines.launch

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
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                sending = true
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) container.ai.analyzeFoodImage(bytes, today)
                }
                sending = false
            }
        }
    }

    fun send(text: String) {
        if (text.isBlank() || sending) return
        input = ""
        scope.launch {
            sending = true
            runCatching { container.ai.sendUserMessage(text.trim(), today) }
            sending = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                        if (!settings.ai.ready) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "💬 حالت آفلاین فعال است؛ برای پاسخ‌های پیشرفته‌تر و تحلیل عکس، سرویس هوش مصنوعی را در تنظیمات پیکربندی کنید.",
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
                            Text("در حال فکر کردن...", color = Teal, fontSize = 13.sp)
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
                    .background(Navy, CircleShape)
                    .clickable { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PhotoCamera, "تحلیل عکس غذا", tint = Color.White, modifier = Modifier.size(22.dp))
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
