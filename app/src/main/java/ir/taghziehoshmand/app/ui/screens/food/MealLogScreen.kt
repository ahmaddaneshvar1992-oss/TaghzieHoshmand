package ir.taghziehoshmand.app.ui.screens.food

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
import kotlinx.coroutines.launch

/** صفحه ثبت وعده‌های غذایی روز */
@Composable
fun MealLogScreen(nav: NavHostController) {
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
            Spacer(Modifier.height(120.dp))
        }
    }
}
