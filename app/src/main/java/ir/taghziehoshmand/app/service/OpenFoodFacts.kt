package ir.taghziehoshmand.app.service

import ir.taghziehoshmand.app.ai.AiHttp
import ir.taghziehoshmand.app.data.model.Food
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** جستجوی محصول بین‌المللی بر اساس بارکد از Open Food Facts */
object OpenFoodFacts {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(barcode: String): Food? {
        if (barcode.isBlank() || !barcode.all { it.isDigit() }) return null
        return try {
            val raw = AiHttp.get(
                "https://world.openfoodfacts.org/api/v2/product/$barcode.json",
                headers = mapOf("User-Agent" to "TaghzieHoshmand/1.0 (Android)")
            )
            val root = json.parseToJsonElement(raw).jsonObject
            val status = root["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            if (status != 1) return null
            val product = root["product"]?.jsonObject ?: return null
            val nutr = product["nutriments"]?.jsonObject
            fun num(vararg keys: String): Double? {
                for (k in keys) {
                    val v = nutr?.get(k)?.jsonPrimitive?.content?.toDoubleOrNull()
                    if (v != null && v > 0) return v
                }
                return null
            }
            val name = product["product_name_fa"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: product["product_name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: "محصول $barcode"
            val kcal = num("energy-kcal_100g", "energy-kcal") ?: (num("energy_100g", "energy")?.let { it / 4.184 })
            if (kcal == null || kcal <= 0) return null
            Food(
                name = name,
                category = "غذاهای بین‌المللی",
                kcal100 = kcal,
                protein100 = num("proteins_100g") ?: 0.0,
                carb100 = num("carbohydrates_100g") ?: 0.0,
                fat100 = num("fat_100g") ?: 0.0,
                servingGrams = num("serving_quantity")?.toInt()?.coerceIn(10, 600) ?: 100,
                servingLabel = "سهم بسته‌بندی",
                commonWeight = 1,
                isCustom = false,
                barcode = barcode
            )
        } catch (e: Exception) {
            null
        }
    }
}
