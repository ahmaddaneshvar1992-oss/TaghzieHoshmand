package ir.taghziehoshmand.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextDirectionHeuristics
import java.io.File
import java.io.FileOutputStream

/** داده یک جدول در گزارش */
data class PdfTable(
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val weights: List<Float> = emptyList()
)

/**
 * ساخت گزارش PDF فارسی (راست‌به‌چپ) با PdfDocument — بدون کتابخانه خارجی.
 */
class PdfExporter {

    private val pageW = 595f
    private val pageH = 842f
    private val margin = 40f
    private val navy = Color.rgb(10, 37, 64)
    private val teal = Color.rgb(0, 180, 216)
    private val grayText = Color.rgb(74, 85, 104)
    private val altBg = Color.rgb(246, 248, 250)

    private val tpTitle = TextPaint().apply {
        color = navy; textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val tpCell = TextPaint().apply {
        color = grayText; textSize = 10.5f; typeface = Typeface.DEFAULT; isAntiAlias = true
    }
    private val tpHeader = TextPaint().apply {
        color = Color.WHITE; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
    }
    private val tpSummary = TextPaint().apply {
        color = navy; textSize = 11f; typeface = Typeface.DEFAULT; isAntiAlias = true
    }

    fun export(
        file: File,
        title: String,
        subtitle: String,
        summaryLines: List<String>,
        tables: List<PdfTable>
    ): Boolean {
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageNo).create())
        var canvas = page.canvas
        var y = drawHeader(canvas, title, subtitle)

        fun newPage() {
            doc.finishPage(page)
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW.toInt(), pageH.toInt(), pageNo).create())
            canvas = page.canvas
            y = margin
        }

        // خلاصه گزارش
        if (summaryLines.isNotEmpty()) {
            if (y + summaryLines.size * 18f + 30f > pageH - margin) newPage()
            summaryLines.forEach { line ->
                tpSummary.textAlign = Paint.Align.RIGHT
                canvas.drawText(line, pageW - margin, y, tpSummary)
                y += 18f
            }
            y += 14f
        }

        tables.forEach { table ->
            val usable = pageW - margin * 2
            val ws = table.weights.ifEmpty { List(table.headers.size) { 1f } }
            val totalW = ws.sum()
            val colWs = ws.map { usable * it / totalW }
            val bounds = mutableListOf<Pair<Float, Float>>()
            var right = pageW - margin
            colWs.forEach { w -> bounds.add(Pair(right - w, right)); right -= w }
            val rowH = 26f

            // عنوان جدول + سربرگ
            if (y + rowH * 3 > pageH - margin) newPage()
            tpTitle.textAlign = Paint.Align.RIGHT
            canvas.drawText(table.title, pageW - margin, y + 12f, tpTitle)
            y += 24f
            drawRowBg(canvas, y, rowH, navy)
            table.headers.forEachIndexed { i, h ->
                drawCell(canvas, h, bounds[i], y, tpHeader)
            }
            y += rowH

            table.rows.forEachIndexed { rIdx, row ->
                if (y + rowH > pageH - margin) {
                    newPage()
                    drawRowBg(canvas, y, rowH, navy)
                    table.headers.forEachIndexed { i, h -> drawCell(canvas, h, bounds[i], y, tpHeader) }
                    y += rowH
                }
                if (rIdx % 2 == 1) drawRowBg(canvas, y, rowH, altBg)
                row.forEachIndexed { i, cell -> drawCell(canvas, cell, bounds[i], y, tpCell) }
                y += rowH
            }
            y += 22f
        }

        // پانویس
        val footer = TextPaint().apply {
            color = grayText; textSize = 9f; isAntiAlias = true; textAlign = Paint.Align.CENTER
        }
        canvas.drawText("تغذیه هوشمند — صفحه ${toFa(pageNo)}", pageW / 2, pageH - 18f, footer)

        doc.finishPage(page)
        return try {
            FileOutputStream(file).use { doc.writeTo(it) }
            true
        } catch (e: Exception) {
            false
        } finally {
            doc.close()
        }
    }

    private fun drawRowBg(canvas: Canvas, y: Float, h: Float, color: Int) {
        canvas.drawRect(RectF(margin, y, pageW - margin, y + h), Paint().apply { setColor(color); isAntiAlias = true })
    }

    private fun drawHeader(canvas: Canvas, title: String, subtitle: String): Float {
        val h = 88f
        canvas.drawRect(RectF(0f, 0f, pageW, h), Paint().apply { color = navy; isAntiAlias = true })
        val tp = TextPaint().apply {
            color = Color.WHITE; textSize = 19f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(title, pageW / 2, 42f, tp)
        tp.textSize = 11f
        tp.color = Color.rgb(200, 220, 235)
        canvas.drawText(subtitle, pageW / 2, 64f, tp)
        canvas.drawRect(RectF(0f, h - 5f, pageW, h), Paint().apply { color = teal })
        return h + 26f
    }

    private fun drawCell(canvas: Canvas, text: String, bounds: Pair<Float, Float>, y: Float, tp: TextPaint) {
        val w = (bounds.second - bounds.first - 8f).toInt().coerceAtLeast(20)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, w)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setMaxLines(2)
            .build()
        canvas.save()
        canvas.translate(bounds.first + 4f, y + 5f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun toFa(v: Int): String {
        val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        return v.toString().map { if (it in '0'..'9') digits[it - '0'] else it }.joinToString("")
    }
}
