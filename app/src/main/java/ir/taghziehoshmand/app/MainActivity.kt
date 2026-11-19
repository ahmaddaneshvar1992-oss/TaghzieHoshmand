package ir.taghziehoshmand.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.taghziehoshmand.app.data.prefs.Settings
import ir.taghziehoshmand.app.ui.RootNav
import ir.taghziehoshmand.app.ui.theme.TaghzieTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** هدف ناوبری از نوتیفیکیشن: (مسیر، شناسه ضربه) — شناسه برای اجرای مجدد لازم است */
    private val navTarget = mutableStateOf<Pair<String, Long>?>(null)

    /** فارسی‌سازی کامل و راست‌به‌چپ */
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale("fa")
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = TaghzieApp.container(this)
        readTarget(intent)
        setContent {
            val settings by container.prefs.settingsFlow.collectAsState(initial = Settings())
            TaghzieTheme(mode = settings.darkMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RootNav(
                        pendingTarget = navTarget.value?.first,
                        pendingSeq = navTarget.value?.second ?: 0L
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readTarget(intent)
    }

    private fun readTarget(intent: Intent?) {
        val target = intent?.getStringExtra("nav_target")?.takeIf { it.isNotBlank() } ?: return
        navTarget.value = target to intent.getLongExtra("nav_seq", System.currentTimeMillis())
    }
}
