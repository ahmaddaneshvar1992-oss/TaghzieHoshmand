package ir.taghziehoshmand.app.ui.screens.barcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import ir.taghziehoshmand.app.TaghzieApp
import ir.taghziehoshmand.app.core.DayUtil
import ir.taghziehoshmand.app.core.Fa
import ir.taghziehoshmand.app.data.model.Food
import ir.taghziehoshmand.app.data.model.MealEntry
import ir.taghziehoshmand.app.service.OpenFoodFacts
import ir.taghziehoshmand.app.ui.components.PrimaryButton
import ir.taghziehoshmand.app.ui.theme.Navy
import ir.taghziehoshmand.app.ui.theme.Teal
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** اسکن بارکد محصولات با دوربین (ML Kit) و جستجو در Open Food Facts */
@Composable
fun BarcodeScannerScreen(nav: NavHostController, mealType: Int) {
    val context = LocalContext.current
    val container = TaghzieApp.container(context)
    val scope = rememberCoroutineScope()
    val today = DayUtil.today()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var found by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Food?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var manualBarcode by remember { mutableStateOf("") }
    var manualDialog by remember { mutableStateOf(false) }
    var manualFoodDialog by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(true) }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analyzerExecutor.shutdown() } }

    fun search(barcode: String) {
        scanning = false
        found = barcode
        scope.launch {
            val existing = container.foodDao.byBarcode(barcode)
            if (existing != null) {
                result = existing
            } else {
                val ofa = OpenFoodFacts.lookup(barcode)
                if (ofa != null) {
                    val id = container.foodDao.insert(ofa)
                    result = ofa.copy(id = id)
                } else {
                    notFound = true
                }
            }
        }
    }

    fun addToMeal(f: Food) {
        scope.launch {
            val factor = f.servingGrams / 100.0
            container.mealDao.insert(
                MealEntry(
                    dayId = today, mealType = mealType, foodId = f.id,
                    name = f.name, grams = f.servingGrams.toDouble(),
                    kcal = f.kcal100 * factor, protein = f.protein100 * factor,
                    carb = f.carb100 * factor, fat = f.fat100 * factor,
                    source = ir.taghziehoshmand.app.data.model.FoodSource.BARCODE
                )
            )
            container.foodDao.incrementUse(f.id)
            nav.popBackStack()
        }
    }

    Column(Modifier.fillMaxSize().background(Navy)) {
        Box(Modifier.fillMaxWidth().padding(top = 44.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Text("اسکن بارکد محصول", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (hasPermission && scanning && found == null) {
                val lifecycleOwner = LocalLifecycleOwner.current
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_CODE_128)
                                .build()
                            val scanner = BarcodeScanning.getClient(options)
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .apply {
                                    setAnalyzer(analyzerExecutor) { proxy ->
                                        val media = proxy.image
                                        if (media != null) {
                                            val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                                            scanner.process(img)
                                                .addOnSuccessListener { codes ->
                                                    codes.firstOrNull()?.rawValue?.let { raw ->
                                                        if (scanning && found == null) search(raw)
                                                    }
                                                }
                                                .addOnCompleteListener { proxy.close() }
                                        } else proxy.close()
                                    }
                                }
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                            } catch (_: Exception) {
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // قاب اسکن
                Box(
                    Modifier
                        .size(260.dp)
                        .align(Alignment.Center)
                        .background(Color(0x22000000), RoundedCornerShape(24.dp))
                )
                Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                    Text(
                        "بارکد را داخل کادر بگیرید 🔍",
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.background(Color(0x66000000), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (found != null) "بارکد: ${Fa.num(found ?: "")}" else "دوربین غیرفعال است",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { found = null; result = null; notFound = false; scanning = true }) {
                            Text("اسکن مجدد", color = Teal)
                        }
                    }
                }
            }
        }

        // نوار پایین: ورود دستی بارکد
        Row(
            Modifier
                .fillMaxWidth()
                .background(Navy)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = manualBarcode,
                onValueChange = { manualBarcode = it.filter { c -> c.isDigit() }.take(14) },
                label = { Text("بارکد دستی", color = Color(0xFF9DB2C4)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Box(
                Modifier
                    .background(Teal, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clickableShim {
                        if (manualBarcode.length >= 6) search(manualBarcode)
                        else manualDialog = true
                    }
            ) {
                Text("جستجو", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(90.dp))
    }

    // نتیجه محصول
    result?.let { f ->
        AlertDialog(
            onDismissRequest = { result = null; found = null; scanning = true },
            title = { Text(f.name) },
            text = {
                Column {
                    Text("در هر ۱۰۰ گرم:", fontWeight = FontWeight.Bold)
                    Text("کالری: ${Fa.kcal(f.kcal100)} • پروتئین: ${Fa.d(f.protein100)} گرم")
                    Text("کربوهیدرات: ${Fa.d(f.carb100)} گرم • چربی: ${Fa.d(f.fat100)} گرم")
                    Text("سهم بسته‌بندی: ${f.servingLabel} — ${Fa.kcal(f.kcal100 * f.servingGrams / 100)} کیلوکالری", color = Teal)
                }
            },
            confirmButton = {
                TextButton(onClick = { addToMeal(f) }) { Text("افزودن به ${mealName(mealType)}", color = Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { result = null; found = null; scanning = true }) { Text("بستن") } }
        )
    }

    // محصول یافت نشد
    if (notFound) {
        AlertDialog(
            onDismissRequest = { notFound = false },
            title = { Text("محصول یافت نشد") },
            text = { Text("این بارکد در پایگاه بین‌المللی Open Food Facts ثبت نشده است (یا اینترنت در دسترس نیست). می‌توانید اطلاعات محصول را دستی وارد کنید.") },
            confirmButton = {
                TextButton(onClick = { notFound = false; manualFoodDialog = true }) { Text("ورود دستی محصول", color = Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { notFound = false; found = null; scanning = true }) { Text("بستن") }
            }
        )
    }

    if (manualDialog) {
        AlertDialog(
            onDismissRequest = { manualDialog = false },
            title = { Text("بارکد نامعتبر") },
            text = { Text("بارکد باید حداقل ۶ رقم باشد.") },
            confirmButton = { TextButton(onClick = { manualDialog = false }) { Text("باشه") } }
        )
    }

    // ورود دستی محصول برای بارکد
    if (manualFoodDialog) {
        var name by remember { mutableStateOf("") }
        var kcal by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manualFoodDialog = false },
            title = { Text("محصول جدید") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("نام محصول") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = kcal,
                        onValueChange = { kcal = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        label = { Text("کالری در ۱۰۰ گرم") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val k = kcal.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && k > 0) {
                        val f = Food(
                            name = name.trim(), category = "غذاهای بین‌المللی",
                            kcal100 = k, protein100 = 0.0, carb100 = 0.0, fat100 = 0.0,
                            servingGrams = 100, servingLabel = "۱۰۰ گرم",
                            barcode = found, isCustom = false
                        )
                        scope.launch {
                            val id = container.foodDao.insert(f)
                            addToMeal(f.copy(id = id))
                        }
                        manualFoodDialog = false
                    }
                }) { Text("ذخیره و افزودن", color = Teal, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { manualFoodDialog = false }) { Text("انصراف") } }
        )
    }
}

private fun mealName(meal: Int): String = when (meal) {
    0 -> "صبحانه"; 1 -> "ناهار"; 2 -> "شام"; else -> "میان‌وعده"
}

private fun Modifier.clickableShim(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
