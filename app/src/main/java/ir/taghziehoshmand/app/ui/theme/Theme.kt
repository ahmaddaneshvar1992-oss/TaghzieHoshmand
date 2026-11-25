package ir.taghziehoshmand.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = Navy,
    secondary = Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EBF2),
    onSecondaryContainer = Navy,
    tertiary = Green,
    onTertiary = Color.White,
    background = BgLight,
    onBackground = TextDark,
    surface = CardWhite,
    onSurface = TextDark,
    surfaceVariant = Color(0xFFEDF2F6),
    onSurfaceVariant = TextGray,
    outline = LineGray,
    error = Red,
    onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0A3A4C),
    onPrimaryContainer = TealSoft,
    secondary = Color(0xFF4FA3C7),
    onSecondary = Color.White,
    secondaryContainer = CardDark,
    onSecondaryContainer = TextLight,
    tertiary = Green,
    onTertiary = Color.White,
    background = BgDark,
    onBackground = TextLight,
    surface = CardDark2,
    onSurface = TextLight,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextLightGray,
    outline = LineDark,
    error = Red,
    onError = Color.White
)

/** حالت تم: سیستم / روشن / تیره */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * تم اپ — پیش‌فرض پالت سازمانی (سرمه‌ای/فیروزه‌ای).
 * با [dynamicColor] در اندروید ۱۲+ رنگ از کاغذدیسی سیستم گرفته می‌شود (Material You).
 */
@Composable
fun TaghzieTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val scheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= 31 -> {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx)
            else androidx.compose.material3.dynamicLightColorScheme(ctx)
        }
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}

/** برای کامپوننت‌هایی که باید مستقل از تم، سبک «کارت سفید روی سرمه‌ای» داشته باشند */
object DarkPanel {
    val bg = Navy
    val card = Color.White
    val onCard = TextDark
    val accent = Teal
}
