package ir.taghziehoshmand.app.ui.screens.food

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import ir.taghziehoshmand.app.ai.AiEngine
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.model.MealEntry
import ir.taghziehoshmand.app.data.model.MealType
import ir.taghziehoshmand.app.data.repo.FoodCatalog
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.EmptyState
import ir.taghziehoshmand.app.ui.components.FaTextField
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** جستجو و افزودن غذا از پایگاه داده (ایرانی + بین‌المللی) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSearchScreen(nav: NavHostController, mealType: Int) {
    val container = TaghzieApp.container(LocalContext.current)
    val scope = rememberCoroutineScope()
    val today = DayUtil.today()

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<Food>>(emptyList()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selected by remember { mutableStateOf<Food?>(null) }
    var showSheet by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var alternatives by remember { mutableStateOf<List<Food>>(emptyList()) }

    val quickFoods by container.foodDao.frequent(10).collectAsState(initial = emptyList())

    // کل پایگاه داده در حافظه — با تغییر نسخه (غذای شخصی جدید / قلب علاقه‌مندی) دوباره بارگیری می‌شود
    var pool by remember { mutableStateOf<List<Food>?>(null) }
    var poolVersion by remember { mutableStateOf(0) }
    LaunchedEffect(poolVersion) {
        categories = container.foodDao.categories()
        pool = container.foodDao.pool()
    }
    // debounce تایپ: جستجو ۲۵۰ms بعد از آخرین کلید و خارج از رشته اصلی انجام می‌شود (تایپ روان)
    // رتبه‌بندی شخصی‌سازی‌شده (نسخه ۱.۵): علاقه‌مندی‌ها → پرکاربردترین‌های خود کاربر → رواج عمومی
    LaunchedEffect(query, category, poolVersion) {
        delay(250)
        val q = query.trim().replace("\u200C", " ")
        val p = pool
        results = withContext(Dispatchers.Default) {
            val personalRank = compareByDescending<Food> { it.favorite }
                .thenByDescending { it.useCount }
                .thenByDescending { it.commonWeight }
                .thenBy { it.name }
            when {
                q.isNotEmpty() -> {
                    val list = if (p != null) {
                        p.filter { it.name.replace("\u200C", " ").contains(q, ignoreCase = true) }
                    } else {
                        container.foodDao.search(q).first()
                    }
                    val filtered = if (category != null) list.filter { it.category == category } else list
                    filtered.sortedWith(personalRank).take(60)
                }
                category != null -> (p ?: emptyList()).filter { it.category == category }
                    .sortedWith(personalRank).take(60)
                else -> (p ?: emptyList()).sortedWith(personalRank).take(40)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            CurvedHeader(
                title = MealType.names[mealType] + " — افزودن غذا",
                showBack = true,
                onBack = { nav.popBackStack() }
            )

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(14.dp))

                // جستجو
                FaTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "جستجوی غذا... (مثل: قورمه‌سبزی، برنج، ماکارونی)",
                    trailing = {
                        IconButton(onClick = { nav.navigate("scanner?meal=$mealType") }) {
                            Icon(Icons.Filled.QrCodeScanner, "اسکن بارکد", tint = Teal)
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                // دسته‌بندی‌ها
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Box(
                            Modifier
                                .background(if (category == null) Teal else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                                .clickable { category = null }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text("همه", color = if (category == null) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
                        }
                    }
                    items(categories) { c ->
                        Box(
                            Modifier
                                .background(if (category == c) Teal else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                                .clickable { category = c }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                (FoodCatalog.categoryIcons[c]?.plus(" ") ?: "") + c,
                                color = if (category == c) Color.White else MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // غذاهای شخصی و افزودن
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (query.isBlank()) "غذاهای پرمصرف و پیشنهادی" else "نتایج جستجو",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { showCustomDialog = true }) {
                        Text("➕ غذای شخصی", color = Teal, fontWeight = FontWeight.Bold)
                    }
                }

                if (results.isEmpty()) {
                    EmptyState("چیزی پیدا نشد. می‌توانید «غذای شخصی» بسازید یا با بارکد محصول را پیدا کنید.", "🔍")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                        items(results, key = { it.id }) { f ->
                            FoodRowCard(
                                food = f,
                                selected = selected?.id == f.id,
                                onSelect = { selected = f; showSheet = false },
                                onAdd = { selected = f; showSheet = true }
                            )
                        }
                        item { Spacer(Modifier.height(110.dp)) }
                    }
                }
            }
        }

        // دکمه پایین
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .padding(bottom = 90.dp)
        ) {
            if (mealType >= 0) {
                PrimaryButton(
                    text = "ثبت مستقیم ${MealType.names[mealType]}",
                    emoji = "✅",
                    onClick = {
                        scope.launch {
                            selected?.let { f ->
                                val factor = f.servingGrams / 100.0
                                container.mealDao.insert(
                                    MealEntry(
                                        dayId = today, mealType = mealType, foodId = f.id,
                                        name = f.name, grams = f.servingGrams.toDouble(),
                                        kcal = f.kcal100 * factor, protein = f.protein100 * factor,
                                        carb = f.carb100 * factor, fat = f.fat100 * factor
                                    )
                                )
                                container.foodDao.incrementUse(f.id)
                                selected = null
                            }
                            nav.popBackStack()
                        }
                    },
                    enabled = selected != null
                )
            }
        }
    }

    // برگه افزودن با تنظیم مقدار — فقط با دکمه ＋ هر کارت باز می‌شود
    if (showSheet && selected != null) {
        AddFoodSheet(
            food = selected!!,
            mealType = mealType,
            onDismiss = { selected = null; showSheet = false; alternatives = emptyList() },
            onAdd = { f, grams ->
                scope.launch {
                    val factor = grams / 100.0
                    container.mealDao.insert(
                        MealEntry(
                            dayId = today, mealType = mealType, foodId = f.id,
                            name = f.name, grams = grams,
                            kcal = f.kcal100 * factor, protein = f.protein100 * factor,
                            carb = f.carb100 * factor, fat = f.fat100 * factor
                        )
                    )
                    container.foodDao.incrementUse(f.id)
                    selected = null
                    showSheet = false
                    alternatives = emptyList()
                }
            },
            onToggleFavorite = { f ->
                scope.launch {
                    container.foodDao.setFavorite(f.id, !f.favorite)
                    // قلب بلافاصله در برگه و لیست آپدیت شود + پایگاه در حافظه تازه شود
                    selected = f.copy(favorite = !f.favorite)
                    poolVersion++
                }
            },
            onAlternatives = { f ->
                scope.launch {
                    alternatives = container.ai.lowerCalorieAlternatives(f)
                }
            },
            alternatives = alternatives,
            onPickAlternative = { f ->
                selected = f
                alternatives = emptyList()
            }
        )
    }

    // گفتگوی غذای شخصی
    if (showCustomDialog) {
        CustomFoodDialog(
            onDismiss = { showCustomDialog = false },
            onSave = { f ->
                scope.launch {
                    container.foodDao.insert(f.copy(isCustom = true))
                    poolVersion++   // غذای شخصی جدید بلافاصله در لیست ظاهر شود
                    showCustomDialog = false
                }
            }
        )
    }
}

/** کارت ردیف غذا — کلیک روی کارت: انتخاب برای «ثبت مستقیم»؛ دکمه ＋: باز کردن برگه مقدار */
@Composable
fun FoodRowCard(food: Food, selected: Boolean = false, onSelect: () -> Unit = {}, onAdd: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Teal.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) BorderStroke(2.dp, Teal) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(Teal.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(FoodCatalog.categoryIcons[food.category] ?: "🍽", fontSize = 22.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        food.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (food.isCustom) {
                        Text(" شخصی", fontSize = 10.sp, color = Navy, modifier = Modifier.padding(start = 4.dp))
                    }
                    if (food.barcode != null) {
                        Text(" 📷", fontSize = 11.sp)
                    }
                }
                Text(
                    "${Fa.kcal(food.kcal100)} کالری در ۱۰۰ گرم",
                    style = MaterialTheme.typography.bodySmall,
                    color = Teal
                )
                Text(
                    "${food.servingLabel} • پ ${Fa.d(food.protein100)} ک ${Fa.d(food.carb100)} چ ${Fa.d(food.fat100)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .background(Teal, CircleShape)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Add, "افزودن", tint = Color.White)
            }
        }
    }
}

/** برگه تنظیم مقدار و افزودن غذا */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFoodSheet(
    food: Food,
    mealType: Int,
    onDismiss: () -> Unit,
    onAdd: (Food, Double) -> Unit,
    onToggleFavorite: (Food) -> Unit,
    onAlternatives: (Food) -> Unit,
    alternatives: List<Food>,
    onPickAlternative: (Food) -> Unit
) {
    var grams by remember(food.id) { mutableStateOf(food.servingGrams.toDouble().toString()) }
    val g = grams.toDoubleOrNull() ?: 0.0
    val factor = g / 100.0

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 20.dp).verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    food.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onToggleFavorite(food) }) {
                    Icon(
                        if (food.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        "علاقه‌مندی",
                        tint = if (food.favorite) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "${MealType.names[mealType]} • ${Fa.kcal(food.kcal100)} کالری در ۱۰۰ گرم",
                style = MaterialTheme.typography.bodySmall,
                color = Teal
            )
            Spacer(Modifier.height(12.dp))

            FaTextField(
                value = grams,
                onValueChange = { grams = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                label = "مقدار (گرم) — سهم پیشنهادی: ${food.servingLabel}",
                keyboardType = KeyboardType.Number
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 100, food.servingGrams, 200, 300).distinct().forEach { preset ->
                    Box(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .clickable { grams = preset.toString() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("${Fa.num(preset)} گرم", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(12.dp)) {
                    Text("کالری این سهم:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Text("${Fa.kcal(food.kcal100 * factor)} کیلوکالری", style = MaterialTheme.typography.titleMedium, color = Teal)
                }
                Row(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    Text("پروتئین ${Fa.d(food.protein100 * factor)} • کربوهیدرات ${Fa.d(food.carb100 * factor)} • چربی ${Fa.d(food.fat100 * factor)} گرم", style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { onAlternatives(food) }) {
                Text("🍏 پیشنهاد جایگزین کم‌کالری‌تر", color = Navy, fontWeight = FontWeight.Bold)
            }

            if (alternatives.isNotEmpty()) {
                Text("جایگزین‌های کم‌کالری‌تر:", style = MaterialTheme.typography.titleSmall)
                alternatives.forEach { alt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPickAlternative(alt) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("•", color = Teal)
                        Spacer(Modifier.width(6.dp))
                        Text(alt.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${Fa.kcal(alt.kcal100)} کالری/۱۰۰گرم", color = Teal, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text(
                    "برای دیدن جایگزین‌ها، دکمه بالا را بزنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))
            PrimaryButton(text = "افزودن به ${MealType.names[mealType]}", emoji = "✅", enabled = g > 0, onClick = { onAdd(food, g) })
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** گفتگوی ساخت غذای شخصی */
@Composable
private fun CustomFoodDialog(onDismiss: () -> Unit, onSave: (Food) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carb by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var serving by remember { mutableStateOf("100") }
    val kcalV = kcal.toDoubleOrNull() ?: 0.0
    val pV = protein.toDoubleOrNull() ?: 0.0
    val cV = carb.toDoubleOrNull() ?: 0.0
    val fV = fat.toDoubleOrNull() ?: 0.0

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("غذای شخصی جدید") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FaTextField(name, { name = it }, "نام غذا")
                Spacer(Modifier.height(8.dp))
                FaTextField(
                    value = kcal,
                    onValueChange = { kcal = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                    label = "کالری در ۱۰۰ گرم",
                    keyboardType = KeyboardType.Number
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FaTextField(protein, { protein = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "پروتئین (گرم)", Modifier.weight(1f), KeyboardType.Number)
                    FaTextField(carb, { carb = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "کربوهیدرات", Modifier.weight(1f), KeyboardType.Number)
                    FaTextField(fat, { fat = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, "چربی", Modifier.weight(1f), KeyboardType.Number)
                }
                Spacer(Modifier.height(8.dp))
                FaTextField(
                    value = serving,
                    onValueChange = { serving = it.filter { ch -> ch.isDigit() }.take(4) },
                    label = "سهم معمول (گرم)",
                    keyboardType = KeyboardType.Number
                )
                val calc = pV * 4 + cV * 4 + fV * 9
                if (kcalV > 0 && calc > 0 && abs(calc - kcalV) / kcalV > 0.3) {
                    Spacer(Modifier.height(6.dp))
                    Text("⚠️ کالری با ماکروها نمی‌خواند؛ تقریباً باید ${Fa.kcal(calc)} باشد.", color = Color(0xFFF59E0B), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && kcalV > 0) onSave(Food(name = name.trim(), category = "غذاهای ایرانی", kcal100 = kcalV, protein100 = pV, carb100 = cV, fat100 = fV, servingGrams = serving.toIntOrNull() ?: 100, servingLabel = "سهم شخصی (${Fa.num(serving)} گرم)", isCustom = true)) },
                enabled = name.isNotBlank() && kcalV > 0
            ) { Text("ذخیره", color = Teal, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
