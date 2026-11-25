package ir.taghziehoshmand.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.ui.screens.ai.AiChatScreen
import ir.taghziehoshmand.app.ui.screens.auth.AuthScreen
import ir.taghziehoshmand.app.ui.screens.auth.AuthMode
import ir.taghziehoshmand.app.ui.screens.auth.LockScreen
import ir.taghziehoshmand.app.ui.screens.barcode.BarcodeScannerScreen
import ir.taghziehoshmand.app.ui.screens.dashboard.DashboardScreen
import ir.taghziehoshmand.app.ui.screens.exercise.ExerciseScreen
import ir.taghziehoshmand.app.ui.screens.food.FoodSearchScreen
import ir.taghziehoshmand.app.ui.screens.food.MealLogScreen
import ir.taghziehoshmand.app.ui.screens.gallery.PhotoGalleryScreen
import ir.taghziehoshmand.app.ui.screens.mealplan.MealPlanScreen
import ir.taghziehoshmand.app.ui.screens.onboarding.OnboardingScreen
import ir.taghziehoshmand.app.ui.screens.profile.BodyProfileScreen
import ir.taghziehoshmand.app.ui.screens.progress.ProgressScreen
import ir.taghziehoshmand.app.ui.screens.reminders.RemindersScreen
import ir.taghziehoshmand.app.ui.screens.settings.SettingsScreen
import ir.taghziehoshmand.app.ui.screens.water.WaterScreen
import ir.taghziehoshmand.app.ui.theme.Navy
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val HOME = "home"
    const val FOOD = "food"
    const val AI = "ai"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    val TABS = listOf(HOME, FOOD, AI, PROGRESS, PROFILE)
}

/** ناوبری ریشه با تصمیم‌گیری شروع: ثبت‌نام / قفل / خانه — pendingTarget برای deep-link نوتیفیکیشن */
@Composable
fun RootNav(pendingTarget: String? = null, pendingSeq: Long = 0L) {
    val context = LocalContext.current
    val container = TaghzieApp.container(context)
    val nav = rememberNavController()

    val startRoute by produceState<String?>(null) {
        val onboarded = container.prefs.onboardedFlow.first()
        val profile = container.prefs.profileFlow.first()
        val pin = container.prefs.pinFlow.first()
        value = when {
            !onboarded || profile == null -> "auth"
            pin != null -> "lock"
            else -> Routes.HOME
        }
    }

    if (startRoute == null) {
        Box(Modifier.fillMaxSize().background(Navy))
        return
    }

    // باز شدن مستقیم صفحه آب با ضربه روی یادآور آب — بر اساس مسیر فعلی، تا بعد از باز کردن قفل PIN هم کار کند
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    LaunchedEffect(currentRoute, pendingTarget, pendingSeq) {
        if (currentRoute == Routes.HOME && pendingTarget == "water") {
            nav.navigate("water") { launchSingleTop = true }
        }
    }

    NavHost(navController = nav, startDestination = startRoute!!) {
        composable("auth") {
            AuthScreen(
                mode = AuthMode.REGISTER,
                onDone = { nav.navigate("onboarding") { popUpTo("auth") { inclusive = true } } }
            )
        }
        composable("lock") {
            LockScreen(onUnlock = { nav.navigate(Routes.HOME) { popUpTo("lock") { inclusive = true } } })
        }
        composable("onboarding") {
            OnboardingScreen(
                initialName = "",
                onDone = { nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } }
            )
        }

        composable(Routes.HOME) {
            MainScaffold(nav, currentTab = Routes.HOME) { DashboardScreen(nav) }
        }
        composable(Routes.FOOD) {
            MainScaffold(nav, currentTab = Routes.FOOD) { MealLogScreen(nav) }
        }
        composable(Routes.AI) {
            MainScaffold(nav, currentTab = Routes.AI) { AiChatScreen(nav) }
        }
        composable(Routes.PROGRESS) {
            MainScaffold(nav, currentTab = Routes.PROGRESS) { ProgressScreen(nav) }
        }
        composable(Routes.PROFILE) {
            MainScaffold(nav, currentTab = Routes.PROFILE) { BodyProfileScreen(nav) }
        }

        composable(
            "foodSearch?meal={meal}",
            arguments = listOf(navArgument("meal") { type = NavType.IntType; defaultValue = 0 })
        ) { entry ->
            FoodSearchScreen(nav, mealType = entry.arguments?.getInt("meal") ?: 0)
        }
        composable("water") { WaterScreen(nav) }
        composable("exercise") { ExerciseScreen(nav) }
        composable("plan") { MealPlanScreen(nav) }
        composable("gallery") { PhotoGalleryScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("reminders") { RemindersScreen(nav) }
        composable(
            "scanner?meal={meal}",
            arguments = listOf(navArgument("meal") { type = NavType.IntType; defaultValue = 0 })
        ) { entry ->
            BarcodeScannerScreen(nav, mealType = entry.arguments?.getInt("meal") ?: 0)
        }
    }
}

/** اسکلت صفحه‌های اصلی با نوار پایین شناور */
@Composable
fun MainScaffold(nav: NavHostController, currentTab: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        BottomBar(nav, currentTab)
    }
}

@Composable
private fun BottomBar(nav: NavHostController, currentTab: String) {
    data class Item(val route: String, val label: String, val icon: ImageVector)

    val items = listOf(
        Item(Routes.HOME, "خانه", Icons.Filled.Home),
        Item(Routes.FOOD, "تغذیه", Icons.Filled.Restaurant),
        Item(Routes.AI, "هوش مصنوعی", Icons.Filled.AutoAwesome),
        Item(Routes.PROGRESS, "پیشرفت", Icons.Filled.BarChart),
        Item(Routes.PROFILE, "پروفایل", Icons.Filled.Person)
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(70.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentTab == item.route
                val isAi = item.route == Routes.AI
                if (isAi) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { nav.navigate(item.route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true } }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(
                                        if (selected) ir.taghziehoshmand.app.ui.theme.Teal else ir.taghziehoshmand.app.ui.theme.Teal.copy(alpha = 0.14f),
                                        RoundedCornerShape(16.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(item.icon, null, tint = if (selected) Color.White else ir.taghziehoshmand.app.ui.theme.Teal, modifier = Modifier.size(24.dp))
                            }
                            Text(
                                item.label,
                                fontSize = 10.sp,
                                color = if (selected) ir.taghziehoshmand.app.ui.theme.Teal else Color(0xFF5A6B7C),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clickable { nav.navigate(item.route) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true } }
                    ) {
                        Icon(
                            item.icon, null,
                            tint = if (selected) ir.taghziehoshmand.app.ui.theme.Teal else Color(0xFF7C8EA0),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            item.label,
                            fontSize = 10.5.sp,
                            color = if (selected) ir.taghziehoshmand.app.ui.theme.Teal else Color(0xFF7C8EA0),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
