@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ir.taghziehoshmand.app.ui.screens.food

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.ai.ParsedFood
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.JalaliDate
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.model.MealEntry
import ir.taghziehoshmand.app.data.model.MealType
import ir.taghziehoshmand.app.data.model.PlanItem
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.EmptyState
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.theme.Teal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/** صفحه ثبت وعده‌های غذایی روز */
@Composable
fun MealLogScreen(nav: NavHostController) {
    val context = LocalContext.current
    val container = TaghzieApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()
    val today = DayUtil.today()

    val entries by container.mealDao.byDay(today).collectAsState(initial = emptyList())
    val dayKcal by container.mealDao.kcalSum(today).collectAsState(initial = 0.0)
    val protein by container.mealDao.proteinSum(today).collectAsState(initial = 0.0)
    val carb by container.mealDao.carbSum(today).collectAsState(initial = 0.0)
    val fat by container.mealDao.fatSum(today).collectAsState(initial = 0.0)
    val frequent by container.foodDao.frequent(10).collectAsState(initial = emptyList())
    val favorites by container.foodDao.favorites().collectAsState(initial = emptyList())
    val profile by container.prefs.profileFlow.collectAsState(initial = null)

    var selectedMeal by remember { mutableIntStateOf(MealType.BREAKFAST) }
    var planItems by remember { mutableStateOf<List<PlanItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        planItems = container.planDao.forDay(today)
    }

    // ===== ثبت صوتی غذا (نسخه ۱.۵): گفتار فارسی → استخراج با AI → تأیید یک‌ضربه‌ای =====
    var voiceState by remember { mutableStateOf("idle") } // idle | listening | extracting
    var voiceTranscript by remember { mutableStateOf("") }
    var voiceItems by remember { mutableStateOf<List<ParsedFood>>(emptyList()) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var voiceMessage by remember { mutableStateOf<String?>(null) }

    val speech = remember {
        if (android.speech.SpeechRecognizer.isRecognitionAvailable(context))
            android.speech.SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    fun logVoiceItems() {
        val items = voiceItems
        if (items.isEmpty()) return
        scope.launch {
            items.forEach { f ->
                container.mealDao.insert(
                    MealEntry(
                        dayId = today, mealType = selectedMeal, foodId = null,
                        name = f.name, grams = f.grams, kcal = f.kcal,
                        protein = f.protein, carb = f.carb, fat = f.fat,
                        source = ir.taghziehoshmand.app.data.model.FoodSource.AI_PHOTO
                    )
                )
            }
            showVoiceSheet = false
            voiceItems = emptyList()
            voiceState = "idle"
        }
    }

    fun startListening() {
        val s = speech
        if (s == null) {
            voiceMessage = "قابلیت تشخیص گفتار روی این دستگاه در دسترس نیست."
            return
        }
        voiceState = "listening"
        voiceMessage = null
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "مثلاً: دو کباب و نصف برنج خوردم")
        }
        s.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { voiceState = "extracting" }
            override fun onError(error: Int) {
                voiceState = "idle"
                voiceMessage = when (error) {
                    android.speech.SpeechRecognizer.ERROR_NO_MATCH,
                    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی شنیده نشد؛ دوباره تلاش کنید."
                    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "مجوز میکروفن داده نشده است."
                    else -> "تشخیص گفتار ناموفق بود؛ دوباره تلاش کنید یا دستی ثبت کنید."
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) {
                    voiceState = "idle"
                    voiceMessage = "متنی شنیده نشد؛ دوباره تلاش کنید."
                    return
                }
                voiceTranscript = text
                voiceState = "extracting"
                scope.launch {
                    val items = container.ai.extractFoodsFromVoice(text)
                    voiceState = "idle"
                    if (items.isNullOrEmpty()) {
                        voiceMessage = "غذایی از جمله «$text» شناسایی نشد؛ دقیق‌تر بگویید یا دستی ثبت کنید."
                    } else {
                        voiceItems = items
                        showVoiceSheet = true
                    }
                }
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })
        s.startListening(intent)
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
        else voiceMessage = "برای ثبت صوتی، مجوز میکروفن را تأیید کنید."
    }

    fun startVoiceFlow() {
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) startListening() else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    fun addFood(f: Food, grams: Double = f.servingGrams.toDouble(), meal: Int = selectedMeal) {
        scope.launch {
            val factor = grams / 100.0
            container.mealDao.insert(
                MealEntry(
                    dayId = today, mealType = meal, foodId = f.id,
                    name = f.name, grams = grams,
                    kcal = f.kcal100 * factor, protein = f.protein100 * factor,
                    carb = f.carb100 * factor, fat = f.fat100 * factor,
                    source = if (f.isCustom) ir.taghziehoshmand.app.data.model.FoodSource.CUSTOM else ir.taghziehoshmand.app.data.model.FoodSource.DATABASE
                )
            )
            container.foodDao.incrementUse(f.id)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CurvedHeader(
            title = "ثبت وعده‌های امروز",
            subtitle = JalaliDate.full(DayUtil.date(today)),
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

            // زبانه‌های وعده‌ها با جمع کالری هر وعده
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealType.names.forEachIndexed { i, name ->
                    val mealKcal = entries.filter { it.mealType == i }.sumOf { it.kcal }
                    val selected = selectedMeal == i
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (selected) Teal else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMeal = i }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(name, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (mealKcal > 0) Fa.kcal(mealKcal) + " کالری" else "—",
                                color = if (selected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // لیست ثبت‌های این وعده
            val mealEntries = entries.filter { it.mealType == selectedMeal }
            if (mealEntries.isEmpty()) {
                EmptyState("برای ${MealType.names[selectedMeal]} هنوز چیزی ثبت نشده است.\nاز دکمه‌های پایین غذا اضافه کنید.", "🍽")
            } else {
                mealEntries.forEach { e ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${Fa.gram(e.grams)} گرم • ${Fa.kcal(e.kcal)} کالری",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Teal
                                )
                                Text(
                                    "پ ${Fa.d(e.protein)} • ک ${Fa.d(e.carb)} • چ ${Fa.d(e.fat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { scope.launch { container.mealDao.delete(e.id) } }) {
                                Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ثبت سریع پرمصرف‌ها
            val quick = (favorites.ifEmpty { frequent }).take(8)
            if (quick.isNotEmpty()) {
                Text("ثبت سریع ⚡", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quick) { f ->
                        Box(
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                .clickable { addFood(f) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(f.name, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text(
                                    "${Fa.kcal(f.kcal100 * f.servingGrams / 100)} کالری • ${f.servingLabel}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // از برنامه غذایی امروز
            if (planItems.isNotEmpty()) {
                Text("از برنامه غذایی امروز 📋", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                planItems.filter { !it.done }.take(6).forEach { p ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${MealType.names[p.mealType]} • ${Fa.gram(p.grams)} گرم • ${Fa.kcal(p.kcal)} کالری",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box(
                                Modifier
                                    .background(Teal.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            container.mealDao.insert(
                                                MealEntry(
                                                    dayId = today, mealType = p.mealType, name = p.name, grams = p.grams,
                                                    kcal = p.kcal, protein = p.protein, carb = p.carb, fat = p.fat
                                                )
                                            )
                                            container.planDao.setDone(p.id, true)
                                            planItems = container.planDao.forDay(today)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("ثبت ✓", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // جمع کل روز
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("جمع کل امروز", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${Fa.kcal(dayKcal)} از ${Fa.kcal(profile?.targets()?.targetKcal ?: 2000.0)} کیلوکالری",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Teal
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("پروتئین ${Fa.d(protein)} گرم", style = MaterialTheme.typography.bodySmall)
                        Text("کربوهیدرات ${Fa.d(carb)} گرم", style = MaterialTheme.typography.bodySmall)
                        Text("چربی ${Fa.d(fat)} گرم", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton(
                    text = "افزودن غذا",
                    emoji = "➕",
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate("foodSearch?meal=$selectedMeal") }
                )
                Box(Modifier.weight(1f)) {
                    PrimaryButton(
                        text = "اسکن بارکد",
                        emoji = "📷",
                        onClick = { nav.navigate("scanner?meal=$selectedMeal") }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = "تحلیل عکس غذا با هوش مصنوعی",
                emoji = "📸",
                container = ir.taghziehoshmand.app.ui.theme.Navy,
                onClick = { nav.navigate(ir.taghziehoshmand.app.ui.Routes.AI) }
            )
            Spacer(Modifier.height(8.dp))
            PrimaryButton(
                text = when (voiceState) {
                    "listening" -> "در حال شنیدن… بگویید چه خوردید"
                    "extracting" -> "در حال استخراج غذاها…"
                    else -> "ثبت صوتی غذا"
                },
                emoji = if (voiceState == "idle") "🎤" else "⏳",
                enabled = voiceState == "idle",
                container = Color(0xFF0E7490),
                onClick = { startVoiceFlow() }
            )
            voiceMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color(0xFFEF4444))
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    // شیت تأیید غذاهای شناسایی‌شده از گفتار
    if (showVoiceSheet) {
        ModalBottomSheet(onDismissRequest = { showVoiceSheet = false }) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
                Text("غذاها از گفتار شما", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text("«$voiceTranscript»", style = MaterialTheme.typography.bodySmall, color = Teal)
                Spacer(Modifier.height(10.dp))
                voiceItems.forEachIndexed { i, f ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "${Fa.gram(f.grams)} گرم • ${Fa.kcal(f.kcal)} کیلوکالری • پ ${Fa.d(f.protein)} ک ${Fa.d(f.carb)} چ ${Fa.d(f.fat)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { voiceItems = voiceItems.filterIndexed { idx, _ -> idx != i } }) {
                                Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFEF4444))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = if (voiceItems.size == 1) "ثبت این غذا در ${MealType.names[selectedMeal]}" else "ثبت ${Fa.num(voiceItems.size)} غذا در ${MealType.names[selectedMeal]}",
                    emoji = "✅",
                    enabled = voiceItems.isNotEmpty(),
                    onClick = { logVoiceItems() }
                )
            }
        }
    }
}
