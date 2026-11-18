package ir.taghziehoshmand.app.ui.screens.auth

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.ui.components.FaTextField
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ورود و ثبت‌نام محلی (کاملاً آفلاین).
 * ثبت‌نام: نام + رمز ۴ رقمی اختیاری برای قفل برنامه.
 * ورود: بررسی رمز قبلی.
 */
@Composable
fun AuthScreen(
    mode: AuthMode,
    onDone: () -> Unit
) {
    val container = TaghzieApp.container(androidx.compose.ui.platform.LocalContext.current)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val isRegister = mode == AuthMode.REGISTER

    Box(Modifier.fillMaxSize().background(Navy)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(80.dp))
            Text("🥗", fontSize = 52.sp)
            Spacer(Modifier.height(12.dp))
            Text("تغذیه هوشمند", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "مشاور تغذیه و کالری‌شماری شخصی شما",
                color = Color(0xFF9DB2C4),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(22.dp)) {
                    Text(
                        if (isRegister) "ثبت‌نام" else "ورود",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Navy,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(18.dp))

                    if (isRegister) {
                        FaTextField(value = name, onValueChange = { name = it }, label = "نام شما (مثلاً: سارا)")
                        Spacer(Modifier.height(12.dp))
                    }

                    FaTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                        label = if (isRegister) "رمز ۴ رقمی (اختیاری)" else "رمز ۴ رقمی",
                        keyboardType = KeyboardType.NumberPassword
                    )

                    if (isRegister) {
                        Spacer(Modifier.height(12.dp))
                        FaTextField(
                            value = pin2,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin2 = it },
                            label = "تکرار رمز (اختیاری)",
                            keyboardType = KeyboardType.NumberPassword
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "اگر رمز نگذارید، ورود به برنامه بدون قفل انجام می‌شود.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(18.dp))
                    PrimaryButton(
                        text = if (isRegister) "ثبت‌نام و شروع" else "ورود",
                        emoji = if (isRegister) "🚀" else "🔓",
                        onClick = {
                            scope.launch {
                                if (isRegister) {
                                    if (pin.isNotBlank() && pin != pin2) {
                                        error = "دو رمز با هم یکسان نیستند"
                                    } else {
                                        if (pin.isNotBlank()) container.prefs.setPin(pin)
                                        onDone()
                                    }
                                } else {
                                    val saved = container.prefs.pinFlow.first()
                                    if (pin == saved) onDone() else error = "رمز اشتباه است"
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "همه داده‌ها فقط روی خود گوشی شما ذخیره می‌شود",
                color = Color(0xFF7E93A8),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

enum class AuthMode { REGISTER, LOGIN }

/** صفحه قفل با رمز */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    AuthScreen(mode = AuthMode.LOGIN, onDone = onUnlock)
}
