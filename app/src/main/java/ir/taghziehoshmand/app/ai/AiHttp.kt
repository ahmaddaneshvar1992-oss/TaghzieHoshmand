package ir.taghziehoshmand.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** پیام چت — content می‌تواند متن ساده یا آرایه چندوجهی (متن + تصویر) باشد */
data class AiMsg(val role: String, val content: Any)

/**
 * کلاینت سبک برای هر سرویس سازگار با OpenAI (OpenAI، OpenRouter، Groq، Ollama و…)
 * کاربر آدرس پایه، کلید و نام مدل را در تنظیمات وارد می‌کند.
 */
object AiHttp {

    private val json = Json { ignoreUnknownKeys = true }

    /** خطای سرویس با پیام فارسی تمیز (بدون JSON خام) — retryable یعنی تلاش مجدد ارزش دارد */
    class AiServiceException(val retryable: Boolean, message: String) : Exception(message)

    private val retryDelays = listOf(1500L, 4000L)

    suspend fun chat(
        baseUrl: String, apiKey: String, model: String,
        messages: List<AiMsg>, maxTokens: Int = 900, temperature: Double = 0.7
    ): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", contentToJson(m.content))
                    })
                }
            })
        }.toString()

        // تا ۳ تلاش با تأخیر برای خطاهای گذرا (۴۰۲/۴۲۹/۵xx/قطعی لحظه‌ای شبکه)
        repeat(retryDelays.size + 1) { attempt ->
            try {
                return@withContext chatOnce(baseUrl, apiKey, body)
            } catch (e: AiServiceException) {
                if (!e.retryable || attempt == retryDelays.size) throw e
                delay(retryDelays[attempt])
            } catch (e: java.io.IOException) {
                if (attempt == retryDelays.size)
                    throw AiServiceException(false, "ارتباط با سرویس ابری برقرار نشد؛ اتصال اینترنت را بررسی کنید.")
                delay(retryDelays[attempt])
            }
        }
        throw AiServiceException(false, "سرویس ابری پاسخ نداد؛ لطفاً دوباره امتحان کنید.")
    }

    private fun chatOnce(baseUrl: String, apiKey: String, body: String): String {
        val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw httpError(code, text)
            return extractContent(text)
        } finally {
            conn.disconnect()
        }
    }

    /** تبدیل خطای HTTP به پیام فارسی قابل‌فهم؛ ۴۰۲/۴۲۹/۵xx گذرا هستند و تلاش مجدد می‌شوند */
    private fun httpError(code: Int, body: String): AiServiceException {
        val msg = when {
            code == 402 -> "سهمیه رایگان سرویس ابری موقتاً تمام شده است؛ چند دقیقه بعد دوباره امتحان کنید یا در «تنظیمات ← هوش مصنوعی» یک سرویس شخصی معرفی کنید."
            code == 429 -> "ترافیک سرویس ابری زیاد است؛ لطفاً کمی بعد دوباره امتحان کنید."
            code in 500..599 -> "سرور سرویس ابری موقتاً در دسترس نیست؛ کمی بعد دوباره امتحان کنید."
            code == 401 || code == 403 -> "کلید سرویس ابری نامعتبر است؛ «تنظیمات ← هوش مصنوعی» را بررسی کنید."
            else -> "سرویس ابری پاسخ مناسب نداد (کد $code)."
        }
        return AiServiceException(code == 402 || code == 429 || code in 500..599, msg)
    }

    private fun contentToJson(content: Any): kotlinx.serialization.json.JsonElement = when (content) {
        is String -> kotlinx.serialization.json.JsonPrimitive(content)
        is List<*> -> buildJsonArray {
            content.forEach { part ->
                when (part) {
                    is String -> add(buildJsonObject {
                        put("type", "text"); put("text", part)
                    })
                    is ImagePart -> add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject { put("url", part.dataUrl) })
                    })
                }
            }
        }
        else -> kotlinx.serialization.json.JsonPrimitive(content.toString())
    }

    data class ImagePart(val dataUrl: String)

    /** استخراج متن پاسخ از پاسخ استاندارد OpenAI */
    fun extractContent(raw: String): String {
        val root = json.parseToJsonElement(raw)
        val choices = root.jsonObject["choices"] as? JsonArray ?: throw IllegalStateException("پاسخ نامعتبر")
        val message = (choices.firstOrNull()?.jsonObject?.get("message") as? JsonObject)
            ?: throw IllegalStateException("پاسخ بدون پیام")
        val content = message["content"] ?: return ""
        return when {
            content is kotlinx.serialization.json.JsonPrimitive -> content.jsonPrimitive.content
            content is JsonArray -> content.joinToString("\n") { el ->
                el.jsonObject["text"]?.jsonPrimitive?.content ?: ""
            }.trim()
            else -> content.toString()
        }.trim()
    }

    suspend fun get(urlStr: String, headers: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream?.let {
                BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).use { r -> r.readText() }
            } ?: ""
        } finally {
            conn.disconnect()
        }
    }
}
