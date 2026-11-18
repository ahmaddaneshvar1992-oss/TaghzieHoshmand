package ir.taghziehoshmand.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal

/** هدر سرمه‌ای با انحنای پایین — مطابق طراحی‌های مرجع */
@Composable
fun CurvedHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 168.dp else 150.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(if (subtitle != null) 168.dp else 150.dp)) {
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(0f, size.height - 46f)
                quadraticBezierTo(size.width / 2, size.height + 34f, size.width, size.height - 46f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(path, color = Navy)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 34.dp, start = 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "بازگشت",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = Color(0xFFB9CFE0),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { trailing() }
            } else {
                Spacer(Modifier.width(48.dp))
            }
        }
    }
}

/** کارت آماری سفید با آیکون (مثل کارت‌های BMI / قد / وزن داشبورد) */
@Composable
fun InfoStatCard(
    label: String,
    value: String,
    emoji: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .height(108.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Text(emoji, fontSize = 24.sp)
            }
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = Teal)
            }
        }
    }
}

/** کارت اقدام سریع داشبورد */
@Composable
fun QuickActionCard(
    label: String,
    emoji: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(96.dp).clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    Teal.copy(alpha = 0.12f),
                    RoundedCornerShape(14.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** فیلد ورودی استاندارد برنامه */
@Composable
fun FaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailing,
        singleLine = singleLine,
        minLines = minLines,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Teal,
            unfocusedIndicatorColor = MaterialTheme.colorScheme.outline
        )
    )
}

/** عنوان بخش با خط فیروزه‌ای زیر آن */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Box(
                Modifier
                    .padding(top = 4.dp)
                    .width(64.dp)
                    .height(3.dp)
                    .background(Teal, RoundedCornerShape(2.dp))
            )
        }
        trailing?.invoke()
    }
}

/** حالت خالی */
@Composable
fun EmptyState(text: String, emoji: String = "🍽", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 44.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** دکمه اصلی برنامه (فیروزه‌ای گرد) */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emoji: String? = null,
    container: Color = Teal
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(if (enabled) container else container.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Text(emoji, fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
