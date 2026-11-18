package ir.taghziehoshmand.app.export

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * نوشتن فایل Excel واقعی (.xlsx) بدون کتابخانه خارجی — ساختار ZIP مبتنی بر Open XML.
 * شیت‌ها راست‌به‌چپ (rightToLeft) ساخته می‌شوند تا فارسی درست نمایش داده شود.
 */
object MiniXlsx {

    /** sheets: جفت نام شیت و ردیف‌ها (ستون اول معمولاً سربرگ است) */
    fun write(file: File, sheets: List<Pair<String, List<List<String>>>>) {
        FileOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                put(zos, "[Content_Types].xml", contentTypes(sheets.size))
                put(zos, "_rels/.rels", rootRels())
                put(zos, "xl/workbook.xml", workbookXml(sheets))
                put(zos, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
                put(zos, "xl/styles.xml", stylesXml())
                sheets.forEachIndexed { i, (_, rows) ->
                    put(zos, "xl/worksheets/sheet${i + 1}.xml", sheetXml(rows))
                }
            }
        }
    }

    private fun put(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun contentTypes(sheetCount: Int): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
${(1..sheetCount).joinToString("\n") { i -> "<Override PartName=\"/xl/worksheets/sheet$i.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" }}
</Types>""".trim()

    private fun rootRels(): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".trim()

    private fun workbookXml(sheets: List<Pair<String, List<List<String>>>>): String {
        val list = sheets.mapIndexed { i, (name, _) ->
            "<sheet name=\"${escape(name)}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>"
        }.joinToString("\n")
        return """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>
$list
</sheets>
</workbook>""".trim()
    }

    private fun workbookRels(count: Int): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
${(1..count).joinToString("\n") { i -> "<Relationship Id=\"rId$i\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet$i.xml\"/>" }}
<Relationship Id="rId${count + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".trim()

    private fun stylesXml(): String = """
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3">
<font><sz val="11"/><name val="Calibri"/></font>
<font><b/><sz val="12"/><color rgb="FF0A2540"/><name val="Calibri"/></font>
<font><sz val="11"/><color rgb="FF00B4D8"/><name val="Calibri"/></font>
</fonts>
<fills count="3">
<fill><patternFill patternType="none"/></fill>
<fill><patternFill patternType="gray125"/></fill>
<fill><patternFill patternType="solid"><fgColor rgb="FFEAF4FB"/><bgColor indexed="64"/></patternFill></fill>
</fills>
<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="3">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="true" applyFill="true"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="true"/>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>""".trim()

    private fun sheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>
<sheetData>""")
        rows.forEachIndexed { r, row ->
            sb.append("<row r=\"${r + 1}\">")
            row.forEachIndexed { c, cell ->
                val ref = colName(c) + (r + 1)
                if (cell.isEmpty()) return@forEachIndexed
                val numeric = cell.toDoubleOrNull()
                val style = if (r == 0) " s=\"1\"" else ""
                if (numeric != null && !cell.contains('/')) {
                    sb.append("<c r=\"$ref\"$style t=\"n\"><v>${numeric}</v></c>")
                } else {
                    sb.append("<c r=\"$ref\"$style t=\"inlineStr\"><is><t>${escape(cell)}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
}
