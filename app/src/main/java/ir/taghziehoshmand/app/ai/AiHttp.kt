package ir.taghziehoshmand.app.ai

import kotlinx.coroutines.Dispatchers
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

        val url = URL(baseUrl.trimEnd('/') + "/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 90000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(300)}")
            extractContent(text)
        } finally {
            conn.disconnect()
        }
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
