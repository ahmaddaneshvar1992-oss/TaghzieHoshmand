package ir.taghziehoshmand.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.taghziehoshmand.app.data.prefs.Settings
import ir.taghziehoshmand.app.ui.RootNav
import ir.taghziehoshmand.app.ui.theme.TaghzieTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

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
        setContent {
            val settings by container.prefs.settingsFlow.collectAsState(initial = Settings())
            TaghzieTheme(mode = settings.darkMode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    RootNav()
                }
            }
        }
    }
}
