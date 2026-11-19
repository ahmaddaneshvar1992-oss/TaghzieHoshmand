package ir.taghziehoshmand.app.ui.screens.gallery

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.core.JalaliDate
import ir.taghziehoshmand.app.data.model.ProgressPhoto
import ir.taghziehoshmand.app.ui.components.CurvedHeader
import ir.taghziehoshmand.app.ui.components.EmptyState
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** گالری عکس پیشرفت بدن (قبل و بعد) */
@Composable
fun PhotoGalleryScreen(nav: NavHostController) {
    val context = LocalContext.current
    val container = TaghzieApp.container(context)
    val scope = rememberCoroutineScope()

    val photos by container.photoDao.all().collectAsState(initial = emptyList())
    val weights by container.weightDao.since(DayUtil.today() - 1).collectAsState(initial = emptyList())
    val profile by container.prefs.profileFlow.collectAsState(initial = null)

    var compareDialog by remember { mutableStateOf(false) }

    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = withContext(Dispatchers.IO) {
                    val dir = File(context.filesDir, "photos").apply { mkdirs() }
                    val file = File(dir, "photo_${System.currentTimeMillis()}.jpg")
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file.absolutePath
                    }.getOrNull()
                }
                saved?.let {
                    val todayW = container.weightDao.byDay(DayUtil.today())?.weightKg
                        ?: profile?.weightKg
                    container.photoDao.insert(
                        ProgressPhoto(
                            dayId = DayUtil.today(),
                            filePath = it,
                            label = JalaliDate.full(DayUtil.date(DayUtil.today())),
                            weightKg = todayW
                        )
                    )
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        CurvedHeader(
            title = "گالری پیشرفت",
            subtitle = "عکس‌های قبل و بعد بدن خودتان",
            showBack = true,
            onBack = { nav.popBackStack() }
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "افزودن عکس امروز",
                emoji = "📸",
                onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            )
            Spacer(Modifier.height(10.dp))
            if (photos.size >= 2) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Teal.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .clickable { compareDialog = true }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚖️ مقایسه اولین و آخرین عکس", color = Navy, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
            }

            if (photos.isEmpty()) {
                EmptyState(
                    "هنوز عکسی ندارید. هر چند هفته در همان نور و حالت یک عکس بگیرید تا پیشرفت بدن را ببینید.",
                    "📷"
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(photos, key = { it.id }) { p ->
                        Card(shape = RoundedCornerShape(14.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                            Box {
                                AsyncImage(
                                    model = File(p.filePath),
                                    contentDescription = p.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(130.dp)
                                )
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x990A2540))
                                        .padding(6.dp)
                                        .align(Alignment.BottomCenter)
                                ) {
                                    Column {
                                        Text(
                                            JalaliDate.short(DayUtil.date(p.dayId)),
                                            color = Color.White,
                                            fontSize = 10.sp
                                        )
                                        p.weightKg?.let {
                                            Text("${Fa.weight(it)} کیلوگرم", color = Teal, fontSize = 10.sp)
                                        }
                                    }
                                }
                                Icon(
                                    Icons.Filled.Delete,
                                    "حذف",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .clickable {
                                            scope.launch {
                                                container.photoDao.delete(p.id)
                                                withContext(Dispatchers.IO) { File(p.filePath).delete() }
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(120.dp))
        }
    }

    if (compareDialog && photos.isNotEmpty()) {
        val first = photos.last()
        val last = photos.first()
        AlertDialog(
            onDismissRequest = { compareDialog = false },
            title = { Text("مقایسه قبل و بعد") },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            AsyncImage(
                                model = File(first.filePath), contentDescription = "قبل",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                            Text("قبل: ${JalaliDate.short(DayUtil.date(first.dayId))}", fontSize = 11.sp)
                            first.weightKg?.let { Text("${Fa.weight(it)} کیلوگرم", color = Teal, fontSize = 11.sp) }
                        }
                        Column(Modifier.weight(1f)) {
                            AsyncImage(
                                model = File(last.filePath), contentDescription = "بعد",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                            Text("بعد: ${JalaliDate.short(DayUtil.date(last.dayId))}", fontSize = 11.sp)
                            last.weightKg?.let { Text("${Fa.weight(it)} کیلوگرم", color = Teal, fontSize = 11.sp) }
                        }
                    }
                    val diff = (last.weightKg ?: 0.0) - (first.weightKg ?: 0.0)
                    if (diff != 0.0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "تغییر وزن بین این دو عکس: ${Fa.weight(kotlin.math.abs(diff))} کیلوگرم ${if (diff < 0) "کاهش ✅" else "افزایش"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { compareDialog = false }) { Text("بستن") } }
        )
    }
}
