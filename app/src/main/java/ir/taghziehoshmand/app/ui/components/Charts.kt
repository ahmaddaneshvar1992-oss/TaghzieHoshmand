package ir.taghziehoshmand.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.taghziehoshmand.app.core.Fa

/** حلقه پیشرفت دایره‌ای (مثل حلقه کالری داشبورد) */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 22.dp,
    color: Color = Color(0xFF00B4D8),
    trackColor: Color = Color(0x22FFFFFF),
    inner: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2 + 2f
            val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (progress.coerceIn(0f, 1f)) * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        inner()
    }
}

/** نمودار خطی نرم با گرادیان — برای روند وزن و کالری */
@Composable
fun LineChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF10B981),
    labels: List<String> = emptyList(),
    showLastBubble: Boolean = true,
    unitSuffix: String = ""
) {
    Box(modifier = modifier) {
        if (points.size < 2) {
            Text(
                "داده کافی برای نمودار وجود ندارد",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padLeft = 34f
            val padBottom = if (labels.isNotEmpty()) 26f else 10f
            val padTop = 26f
            val w = size.width - padLeft
            val h = size.height - padTop - padBottom
            val minV = points.min()
            val maxV = points.max()
            val span = (maxV - minV).coerceAtLeast(0.001f)
            val pad = span * 0.08f
            val lo = minV - pad
            val hi = maxV + pad
            fun px(i: Int) = Offset(padLeft + i * w / (points.size - 1), padTop + h - (points[i] - lo) / (hi - lo) * h)

            // محور افقی و برچسب‌های Y
            val axisPaint = android.graphics.Paint().apply {
                setColor(android.graphics.Color.argb(140, 120, 130, 140))
                textSize = 22f
                isAntiAlias = true
            }
            for (g in 0..3) {
                val yy = padTop + g * h / 3
                drawLine(Color(0x1A8494A5), Offset(padLeft, yy), Offset(padLeft + w, yy), strokeWidth = 1.5f)
                val v = hi - (hi - lo) * g / 3
                drawContext.canvas.nativeCanvas.drawText(
                    Fa.d(v.toDouble(), if (span < 4) 1 else 0),
                    0f, yy + 8f, axisPaint
                )
            }

            // مسیر نرم
            val path = Path()
            val first = px(0)
            path.moveTo(first.x, first.y)
            for (i in 1 until points.size) {
                val p0 = px(i - 1)
                val p1 = px(i)
                val midX = (p0.x + p1.x) / 2
                path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
            val fillPath = Path().apply {
                addPath(path)
                lineTo(padLeft + w, padTop + h)
                lineTo(padLeft, padTop + h)
                close()
            }
            // گرادیان زیر منحنی — با مسیر بسته تا محور افقی پر می‌شود
            clipPath(fillPath) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.02f)),
                        startY = padTop, endY = padTop + h
                    ),
                    topLeft = Offset(padLeft, padTop), size = androidx.compose.ui.geometry.Size(w, h)
                )
            }
            drawPath(path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))

            // نقاط
            points.forEachIndexed { i, _ ->
                drawCircle(color = lineColor, radius = 7f, center = px(i))
                drawCircle(color = Color.White, radius = 3.5f, center = px(i))
            }

            // حباب آخرین مقدار
            if (showLastBubble) {
                val last = px(points.size - 1)
                val text = Fa.d(points.last().toDouble(), if (span < 4) 1 else 0) + unitSuffix
                val bubblePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(230, 232, 247, 251)
                    isAntiAlias = true
                }
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(10, 37, 64)
                    textSize = 24f
                    isAntiAlias = true
                }
                val tw = textPaint.measureText(text)
                val bx = (last.x - tw - 26f).coerceAtLeast(padLeft)
                drawRoundRect(
                    color = Color(0xFFE8F7FB),
                    topLeft = Offset(bx, last.y - 46f),
                    size = androidx.compose.ui.geometry.Size(tw + 30f, 40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f, 30f)
                )
                drawContext.canvas.nativeCanvas.drawText(text, bx + 15f, last.y - 18f, textPaint)
            }

            // برچسب‌های X
            if (labels.isNotEmpty()) {
                val idx = if (labels.size > 6) {
                    val step = (labels.size - 1) / 5
                    (0..4).map { it * step } + listOf(labels.size - 1)
                } else labels.indices.toList()
                idx.forEach { i ->
                    val p = px(i)
                    drawContext.canvas.nativeCanvas.drawText(
                        labels[i], p.x - 26f, size.height - 6f, axisPaint
                    )
                }
            }
        }
    }
}

/** نمودار میله‌ای — برای کالری روزانه با خط هدف */
@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF00B4D8),
    overColor: Color = Color(0xFFF59E0B),
    goal: Float? = null
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val padBottom = 28f
            val padTop = 12f
            val w = size.width
            val h = size.height - padTop - padBottom
            val maxV = maxOf(values.maxOrNull() ?: 1f, goal ?: 1f) * 1.1f
            val barSpace = w / values.size
            val barW = barSpace * 0.5f
            values.forEachIndexed { i, v ->
                val bh = (v / maxV * h).coerceAtLeast(4f)
                val x = i * barSpace + (barSpace - barW) / 2
                val color = if (goal != null && v > goal * 1.05f) overColor else barColor
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, padTop + h - bh),
                    size = androidx.compose.ui.geometry.Size(barW, bh),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
                )
                val axisPaint = android.graphics.Paint().apply {
                    setColor(android.graphics.Color.argb(170, 90, 105, 120))
                    textSize = 20f
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText(labels[i], x + barW / 2 - 14f, size.height - 8f, axisPaint)
            }
        }
        if (goal != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padTop = 12f
                val padBottom = 28f
                val h = size.height - padTop - padBottom
                val maxV = maxOf(values.maxOrNull() ?: 1f, goal) * 1.1f
                val gy = padTop + h - (goal / maxV * h)
                drawLine(
                    color = Color(0xFFF59E0B),
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 3f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
            }
        }
    }
}

/** نوار ماکروها (پروتئین/کربوهیدرات/چربی) */
@Composable
fun MacroBar(
    proteinG: Double,
    carbG: Double,
    fatG: Double,
    modifier: Modifier = Modifier
) {
    val kcalP = proteinG * 4
    val kcalC = carbG * 4
    val kcalF = fatG * 9
    val total = (kcalP + kcalC + kcalF).coerceAtLeast(1.0)
    val segments = listOf(
        Triple(kcalP / total, Color(0xFF10B981), "پروتئین"),
        Triple(kcalC / total, Color(0xFF00B4D8), "کربوهیدرات"),
        Triple(kcalF / total, Color(0xFFF59E0B), "چربی")
    )
    Canvas(modifier = modifier) {
        var x = 0f
        segments.forEach { (frac, color, _) ->
            val w = size.width * frac.toFloat()
            drawRect(color = color, topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(w.coerceAtLeast(0f), size.height))
            x += w
        }
    }
}

/** نقطه روی نمودار پیش‌بینی */
data class ForecastPoint(val weight: Float, val label: String)

/**
 * نمودار پیش‌بینی وزن (ایده ۸) — سنجش‌های گذشته با خط ممتد،
 * آینده با خط‌چین و «بازه اطمینان» به‌صورت ناحیه کم‌رنگ + خط هدف.
 */
@Composable
fun ForecastChart(
    history: List<ForecastPoint>,
    forecast: List<ForecastPoint>,
    bandLo: List<Float>,
    bandHi: List<Float>,
    goalWeight: Float? = null,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF10B981),
    forecastColor: Color = Color(0xFF00B4D8)
) {
    Box(modifier = modifier) {
        if (history.size < 2) {
            Text(
                "برای پیش‌بینی، حداقل ۳ سنجش وزن لازم است",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
            return@Box
        }
        val allLabels = history.map { it.label } + forecast.map { it.label }
        val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = history.size + forecast.size
            val padLeft = 34f
            val padBottom = if (allLabels.isNotEmpty()) 26f else 10f
            val padTop = 26f
            val w = size.width - padLeft
            val h = size.height - padTop - padBottom

            val loCand = (bandLo + history.map { it.weight } + forecast.map { it.weight }).min()
            val hiCand = (bandHi + history.map { it.weight } + forecast.map { it.weight }).max()
            val goal = goalWeight
            val minV = listOfNotNull(loCand, goal).min()
            val maxV = listOfNotNull(hiCand, goal).max()
            val span = (maxV - minV).coerceAtLeast(0.001f)
            val pad = span * 0.08f
            val lo = minV - pad
            val hi = maxV + pad

            fun px(i: Int, v: Float) =
                Offset(padLeft + i * w / (n - 1).coerceAtLeast(1), padTop + h - (v - lo) / (hi - lo) * h)

            val axisPaint = android.graphics.Paint().apply {
                setColor(android.graphics.Color.argb(140, 120, 130, 140))
                textSize = 22f
                isAntiAlias = true
            }
            for (g in 0..3) {
                val yy = padTop + g * h / 3
                drawLine(Color(0x1A8494A5), Offset(padLeft, yy), Offset(padLeft + w, yy), strokeWidth = 1.5f)
                val v = hi - (hi - lo) * g / 3
                drawContext.canvas.nativeCanvas.drawText(Fa.d(v.toDouble(), if (span < 4) 1 else 0), 0f, yy + 8f, axisPaint)
            }

            // بازه اطمینان (فقط ناحیه پیش‌بینی)
            if (forecast.isNotEmpty() && bandLo.size == forecast.size) {
                val bandPath = Path()
                for (i in forecast.indices) {
                    val p = px(history.size + i, bandHi[i])
                    if (i == 0) bandPath.moveTo(p.x, p.y) else bandPath.lineTo(p.x, p.y)
                }
                for (i in forecast.indices.reversed()) {
                    bandPath.lineTo(px(history.size + i, bandLo[i]).x, px(history.size + i, bandLo[i]).y)
                }
                bandPath.close()
                drawPath(bandPath, color = forecastColor.copy(alpha = 0.13f))
            }

            // خط پیش‌بینی (خط‌چین) — از آخرین سنجش شروع می‌شود
            if (forecast.isNotEmpty()) {
                val fp = Path()
                val start = px(history.size - 1, history.last().weight)
                fp.moveTo(start.x, start.y)
                forecast.forEachIndexed { i, pt ->
                    fp.lineTo(px(history.size + i, pt.weight).x, px(history.size + i, pt.weight).y)
                }
                drawPath(
                    fp, color = forecastColor,
                    style = Stroke(width = 5f, cap = StrokeCap.Round,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
                )
            }

            // خط تاریخچه (ممتد)
            val path = Path()
            path.moveTo(px(0, history[0].weight).x, px(0, history[0].weight).y)
            for (i in 1 until history.size) {
                val p0 = px(i - 1, history[i - 1].weight)
                val p1 = px(i, history[i].weight)
                val midX = (p0.x + p1.x) / 2
                path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
            }
            val fillPath = Path().apply {
                addPath(path)
                lineTo(padLeft + (history.size - 1) * w / (n - 1).coerceAtLeast(1), padTop + h)
                lineTo(padLeft, padTop + h)
                close()
            }
            clipPath(fillPath) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0.02f)),
                        startY = padTop, endY = padTop + h
                    ),
                    topLeft = Offset(padLeft, padTop), size = androidx.compose.ui.geometry.Size(w, h)
                )
            }
            drawPath(path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
            history.forEachIndexed { i, _ ->
                drawCircle(color = lineColor, radius = 7f, center = px(i, history[i].weight))
                drawCircle(color = Color.White, radius = 3.5f, center = px(i, history[i].weight))
            }

            // خط هدف وزن
            if (goal != null) {
                val gy = px(0, goal).y
                drawLine(
                    Color(0xFFF59E0B), Offset(padLeft, gy), Offset(padLeft + w, gy),
                    strokeWidth = 3f,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                )
                drawContext.canvas.nativeCanvas.drawText("هدف", padLeft + 8f, gy - 10f, axisPaint)
            }

            // حباب وزن پیش‌بینی‌شده آخر
            if (forecast.isNotEmpty()) {
                val lastI = history.size + forecast.size - 1
                val last = px(lastI, forecast.last().weight)
                val text = Fa.d(forecast.last().weight.toDouble(), 1)
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(10, 37, 64)
                    textSize = 24f
                    isAntiAlias = true
                }
                val tw = textPaint.measureText(text)
                val bx = (last.x - tw - 12f).coerceAtMost(size.width - tw - 40f)
                drawRoundRect(
                    color = Color(0xFFE7F6FB),
                    topLeft = Offset(bx.coerceAtLeast(padLeft), last.y - 46f),
                    size = androidx.compose.ui.geometry.Size(tw + 30f, 40f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f, 30f)
                )
                drawContext.canvas.nativeCanvas.drawText(text, bx.coerceAtLeast(padLeft) + 15f, last.y - 18f, textPaint)
            }

            // برچسب‌های X (تاریخ شمسی)
            if (allLabels.isNotEmpty()) {
                val idx = if (allLabels.size > 6) {
                    val step = (allLabels.size - 1) / 5
                    (0..4).map { it * step } + listOf(allLabels.size - 1)
                } else allLabels.indices.toList()
                idx.forEach { i ->
                    val xx = padLeft + i * w / (n - 1).coerceAtLeast(1)
                    drawContext.canvas.nativeCanvas.drawText(allLabels[i], xx - 26f, size.height - 6f, axisPaint)
                }
            }
        }
    }
}
