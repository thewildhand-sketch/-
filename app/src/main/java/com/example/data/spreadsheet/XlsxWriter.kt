package com.example.data.spreadsheet

import android.content.Context
import android.net.Uri
import com.example.data.local.JournalEntryEntity
import com.example.data.local.MasterRecordEntity
import com.example.model.JournalSchema
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object XlsxWriter {
    fun writeToUri(
        context: Context,
        uri: Uri,
        entries: List<JournalEntryEntity>,
        masterRecords: List<MasterRecordEntity>,
    ) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            writeWorkbook(out, entries, masterRecords)
        } ?: throw IllegalStateException("تعذر فتح ملف التصدير للكتابة.")
    }

    fun writeToCacheFile(
        context: Context,
        fileName: String,
        entries: List<JournalEntryEntity>,
        masterRecords: List<MasterRecordEntity>,
    ): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { out ->
            writeWorkbook(out, entries, masterRecords)
        }
        return file
    }

    fun writeWorkbook(
        outputStream: OutputStream,
        entries: List<JournalEntryEntity>,
        masterRecords: List<MasterRecordEntity>,
    ) {
        val bytes = buildWorkbookBytes(entries, masterRecords)
        outputStream.write(bytes)
        outputStream.flush()
    }

    fun buildWorkbookBytes(
        entries: List<JournalEntryEntity>,
        masterRecords: List<MasterRecordEntity>,
    ): ByteArray {
        val sharedStrings = mutableListOf<String>()
        val sharedIndex = mutableMapOf<String, Int>()
        fun intern(value: String): Int {
            return sharedIndex.getOrPut(value) {
                val idx = sharedStrings.size
                sharedStrings.add(value)
                idx
            }
        }

        val sheet1Xml = buildJournalSheetXml(entries, ::intern)
        val sheet2Xml = buildMasterSheetXml(masterRecords, ::intern)
        val sharedStringsXml = buildSharedStringsXml(sharedStrings)

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            fun putEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            putEntry("[Content_Types].xml", CONTENT_TYPES_XML)
            putEntry("_rels/.rels", ROOT_RELS_XML)
            putEntry("xl/workbook.xml", WORKBOOK_XML)
            putEntry("xl/_rels/workbook.xml.rels", WORKBOOK_RELS_XML)
            putEntry("xl/styles.xml", STYLES_XML)
            putEntry("xl/sharedStrings.xml", sharedStringsXml)
            putEntry("xl/worksheets/sheet1.xml", sheet1Xml)
            putEntry("xl/worksheets/sheet2.xml", sheet2Xml)
        }
        return baos.toByteArray()
    }

    private fun buildJournalSheetXml(
        entries: List<JournalEntryEntity>,
        intern: (String) -> Int,
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView tabSelected="1" rightToLeft="1" workbookViewId="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews>""")
        sb.append("<cols>")
        JournalSchema.EXACT_HEADERS.forEachIndexed { idx, header ->
            val width = when (idx) {
                12, 39 -> 28
                1, 2, 5, 7, 23, 29 -> 22
                in JournalSchema.NUMERIC_COLUMN_INDICES -> 15
                else -> maxOf(14, header.length + 4)
            }
            sb.append("""<col min="${idx + 1}" max="${idx + 1}" width="$width" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")

        val firstDataRow = 3
        val lastDataRow = maxOf(firstDataRow, entries.size + 2)

        // Row 1: Summary Totals Formula Row (matching user's blank template structure above headers)
        sb.append("""<row r="1" ht="22" customHeight="1">""")
        val totalColumns = setOf(14, 15, 16, 24, 25, 30, 35, 36, 37, 38, 41)
        for (colIdx in totalColumns.sorted()) {
            val colLetter = columnLetter(colIdx)
            val cachedSum = when (colIdx) {
                14 -> entries.sumOf { it.debit }
                15 -> entries.sumOf { it.credit }
                16 -> entries.sumOf { it.debit - it.credit }
                24 -> entries.sumOf { it.salesTax2372 }
                25 -> entries.sumOf { it.customerTax1357 }
                30 -> entries.sumOf { it.withholdingTax1Pct2361 }
                35 -> entries.sumOf { it.goodReturns }
                36 -> entries.sumOf { it.damagedReturns }
                37 -> entries.sumOf { it.discounts }
                38 -> entries.sumOf { it.taxes }
                41 -> entries.sumOf { it.cashVariance }
                else -> 0.0
            }
            sb.append("""<c r="${colLetter}1" s="2"><f>SUBTOTAL(9,${colLetter}$firstDataRow:${colLetter}$lastDataRow)</f><v>${formatNum(cachedSum)}</v></c>""")
        }
        sb.append("</row>")

        // Row 2: Exact 51-column Header Row
        sb.append("""<row r="2" ht="26" customHeight="1">""")
        JournalSchema.EXACT_HEADERS.forEachIndexed { colIdx, header ->
            val ref = "${columnLetter(colIdx)}2"
            val sIdx = intern(header)
            sb.append("""<c r="$ref" t="s" s="1"><v>$sIdx</v></c>""")
        }
        sb.append("</row>")

        // Rows 3..N: Journal Data Rows
        entries.forEachIndexed { rowOffset, rawEntry ->
            val entry = rawEntry.withComputedDerived()
            val rowNum = firstDataRow + rowOffset
            val values = entry.toColumnValues()
            sb.append("""<row r="$rowNum">""")
            values.forEachIndexed { colIdx, textVal ->
                val ref = "${columnLetter(colIdx)}$rowNum"
                when {
                    colIdx == 16 -> {
                        val bal = entry.debit - entry.credit
                        sb.append("""<c r="$ref" s="3"><f>O$rowNum-P$rowNum</f><v>${formatNum(bal)}</v></c>""")
                    }
                    colIdx in JournalSchema.NUMERIC_COLUMN_INDICES -> {
                        val num = textVal.toDoubleOrNull() ?: 0.0
                        sb.append("""<c r="$ref" s="3"><v>${formatNum(num)}</v></c>""")
                    }
                    textVal.isNotBlank() -> {
                        val sIdx = intern(textVal)
                        sb.append("""<c r="$ref" t="s" s="0"><v>$sIdx</v></c>""")
                    }
                }
            }
            sb.append("</row>")
        }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildMasterSheetXml(
        records: List<MasterRecordEntity>,
        intern: (String) -> Int,
    ): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0"/></sheetViews>""")
        sb.append("""<cols><col min="1" max="5" width="22" customWidth="1"/></cols>""")
        sb.append("<sheetData>")

        val headers = listOf("النوع", "الكود", "الاسم", "التصنيف / الحساب الرئيسي", "ملاحظات")
        sb.append("""<row r="1" ht="24" customHeight="1">""")
        headers.forEachIndexed { idx, h ->
            sb.append("""<c r="${columnLetter(idx)}1" t="s" s="1"><v>${intern(h)}</v></c>""")
        }
        sb.append("</row>")

        records.forEachIndexed { idx, rec ->
            val r = idx + 2
            val cols = listOf(rec.type, rec.code, rec.name, rec.category, rec.extra)
            sb.append("""<row r="$r">""")
            cols.forEachIndexed { cIdx, v ->
                if (v.isNotBlank()) {
                    sb.append("""<c r="${columnLetter(cIdx)}$r" t="s" s="0"><v>${intern(v)}</v></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun buildSharedStringsXml(strings: List<String>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${strings.size}" uniqueCount="${strings.size}">""")
        for (str in strings) {
            sb.append("<si><t xml:space=\"preserve\">${escapeXml(str)}</t></si>")
        }
        sb.append("</sst>")
        return sb.toString()
    }

    private fun columnLetter(indexZeroBased: Int): String {
        var idx = indexZeroBased
        val sb = StringBuilder()
        while (idx >= 0) {
            sb.append(('A' + (idx % 26)))
            idx = idx / 26 - 1
        }
        return sb.reverse().toString()
    }

    private fun formatNum(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch.code >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>
</Types>"""

    private const val ROOT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private const val WORKBOOK_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="قيود اليومية" sheetId="1" r:id="rId1"/>
    <sheet name="دليل البيانات" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""

    private const val WORKBOOK_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>
</Relationships>"""

    private const val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="1">
    <numFmt numFmtId="164" formatCode="#,##0.00"/>
  </numFmts>
  <fonts count="3">
    <font><sz val="11"/><name val="Arial"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Arial"/></font>
    <font><b/><sz val="11"/><color rgb="FF0F6B54"/><name val="Arial"/></font>
  </fonts>
  <fills count="4">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F6B54"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFE6F4EF"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFD0D7DE"/></left>
      <right style="thin"><color rgb="FFD0D7DE"/></right>
      <top style="thin"><color rgb="FFD0D7DE"/></top>
      <bottom style="thin"><color rgb="FFD0D7DE"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
    <xf numFmtId="164" fontId="2" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1"/>
    <xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1"/>
  </cellXfs>
</styleSheet>"""
}
