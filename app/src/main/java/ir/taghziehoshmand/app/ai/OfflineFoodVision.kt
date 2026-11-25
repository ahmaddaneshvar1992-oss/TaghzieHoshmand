package ir.taghziehoshmand.app.ai

import android.content.Context
import android.graphics.Bitmap
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * موتور بینایی روی دستگاه (نسخه ۱.۵ — ایده «تشخیص آفلاین واقعی غذا»):
 * مدل MobileNetV1 کوانتیزه (۲۲۴×۲۲۴) با TFLite بدون اینترنت اجرا می‌شود و
 * برچسب‌های مرتبط با غذا از ۱۰۰۰ کلاس ImageNet به فارسی ترجمه و به پایگاه غذایی
 * اپ پیوند داده می‌شوند. دقت آفلاین «تقریبی» است و جایگزین مدل ابری بینایی نیست.
 */
object OfflineFoodVision {

    private const val MODEL_FILE = "mobilenet_v1_1.0_224_quant.tflite"
    private const val LABELS_FILE = "labels_mobilenet_quant_v1_224.txt"
    private const val DIM = 224

    /** برچسب‌های غذایی ImageNet → نام فارسی (برچسب‌های غیرغذایی نادیده گرفته می‌شوند) */
    private val faMap = mapOf(
        "guacamole" to "دسر سالسا (گواکاموله)",
        "consomme" to "سوپ آب‌رنگ",
        "hot pot" to "خورش",
        "trifle" to "دسر لایه‌ای",
        "ice cream" to "بستنی",
        "ice lolly" to "بستنی یخی",
        "French loaf" to "نان باگت",
        "bagel" to "نان بگل",
        "pretzel" to "نان پرتزل",
        "cheeseburger" to "برگر",
        "hotdog" to "هات‌داگ",
        "mashed potato" to "پوره سیب‌زمینی",
        "head cabbage" to "کلم",
        "broccoli" to "بروکلی",
        "cauliflower" to "گل‌کلم",
        "zucchini" to "کدو سبز",
        "spaghetti squash" to "کدو اسپاگتی",
        "acorn squash" to "کدو تنبل",
        "butternut squash" to "کدو کره‌ای",
        "cucumber" to "خیار",
        "artichoke" to "آرتیشو",
        "bell pepper" to "فلفل دلمه‌ای",
        "cardoon" to "کنگر فرنگی",
        "mushroom" to "قارچ",
        "Granny Smith" to "سیب سبز",
        "strawberry" to "توت‌فرنگی",
        "orange" to "پرتقال",
        "lemon" to "لیمو",
        "fig" to "انجیر",
        "pineapple" to "آناناس",
        "banana" to "موز",
        "jackfruit" to "جک‌فروت",
        "custard apple" to "سیب کاستارد",
        "pomegranate" to "انار",
        "carbonara" to "پاستا",
        "chocolate sauce" to "سس شکلاتی",
        "dough" to "خمیر نان",
        "meat loaf" to "خوراک گوشت",
        "pizza" to "پیتزا",
        "potpie" to "پای گوشت",
        "burrito" to "بوریتو",
        "red wine" to "نوشیدنی",
        "espresso" to "قهوه",
        "cup" to "فنجان نوشیدنی",
        "eggnog" to "نوشیدنی شیری",
        "corn" to "ذرت",
        "soup bowl" to "کاسه سوپ",
        "coffee mug" to "فنجان قهوه",
        "coffeepot" to "قهوه‌جوش",
        "water bottle" to "بطری آب",
        "wine bottle" to "بطری نوشیدنی",
        "wok" to "تابه غذا",
        "plate" to "بشقاب غذا",
        "tray" to "سینی غذا"
    )

    /** یک تشخیص آفلاین */
    data class Detection(val labelFa: String, val labelEn: String, val confidence: Float)

    @Volatile private var interpreter: org.tensorflow.lite.Interpreter? = null
    @Volatile private var labels: List<String>? = null

    private fun loadModelFile(context: Context, name: String): MappedByteBuffer {
        context.assets.openFd(name).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { ch ->
                return ch.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }

    private fun ensure(context: Context): Boolean {
        if (interpreter != null && labels != null) return true
        synchronized(this) {
            if (interpreter != null && labels != null) return true
            return try {
                val opts = org.tensorflow.lite.Interpreter.Options().apply { numThreads = 4 }
                interpreter = org.tensorflow.lite.Interpreter(loadModelFile(context, MODEL_FILE), opts)
                labels = context.assets.open(LABELS_FILE).bufferedReader().readLines()
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * تشخیص غذا در عکس — کاملاً آفلاین.
     * فقط برچسب‌های شناخته‌شدهٔ غذایی با اطمینان بالای [minConf] برمی‌گردند.
     */
    fun detect(context: Context, bitmap: Bitmap, topK: Int = 3, minConf: Float = 0.08f): List<Detection> {
        if (!ensure(context)) return emptyList()
        val itp = interpreter ?: return emptyList()
        val lbls = labels ?: return emptyList()
        return try {
            val resized = if (bitmap.width != DIM || bitmap.height != DIM)
                Bitmap.createScaledBitmap(bitmap, DIM, DIM, true) else bitmap
            val input = ByteBuffer.allocateDirect(1 * DIM * DIM * 3).order(ByteOrder.nativeOrder())
            val pixels = IntArray(DIM * DIM)
            resized.getPixels(pixels, 0, DIM, 0, 0, DIM, DIM)
            for (p in pixels) {
                input.put(((p shr 16) and 0xFF).toByte()) // R
                input.put(((p shr 8) and 0xFF).toByte())  // G
                input.put((p and 0xFF).toByte())          // B
            }
            input.rewind()
            val output = Array(1) { ByteArray(lbls.size) }
            itp.run(input, output)
            val probs = output[0].map { (it.toInt() and 0xFF) / 255f }
            probs.withIndex()
                .filter { (i, v) -> v >= minConf && lbls.getOrNull(i)?.let { faMap.containsKey(it) } == true }
                .sortedByDescending { it.value }
                .take(topK)
                .mapNotNull { (i, v) ->
                    val en = lbls.getOrNull(i) ?: return@mapNotNull null
                    val fa = faMap[en] ?: return@mapNotNull null
                    Detection(fa, en, v)
                }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
