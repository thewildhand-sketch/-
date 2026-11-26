package com.example.data.spreadsheet

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.local.JournalEntryEntity
import com.example.data.local.MasterRecordEntity
import com.example.domain.ArabicNormalizer
import com.example.model.JournalSchema
import com.example.model.JournalSourceType
import com.example.model.MasterEntityType
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

data class ParsedSheetData(
    val sheetName: String,
    val headerRowIndex: Int,
    val headers: List<String>,
    val rows: List<List<String>>,
    val suggestedColumnMapping: Map<Int, Int>, // sourceColIdx -> JournalSchema colIdx (0..50)
)

data class ParsedWorkbookResult(
    val fileName: String,
    val sheets: List<ParsedSheetData>,
)

object XlsxReader {
    fun readUri(context: Context, uri: Uri, displayName: String): ParsedWorkbookResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("تعذر فتح الملف المختار.")
        return readBytes(bytes, displayName)
    }

    fun readBytes(bytes: ByteArray, displayName: String): ParsedWorkbookResult {
        val isZip = bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte()
        return if (isZip) {
            parseXlsx(bytes, displayName)
        } else {
            val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            parseDelimitedText(text, displayName)
        }
    }

    fun parseDelimitedText(text: String, displayName: String = "نص مستورد"): ParsedWorkbookResult {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n")
            .split("\n")
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return ParsedWorkbookResult(displayName, emptyList())

        val delimiter = detectDelimiter(lines.take(5))
        val rawRows = lines.map { parseCsvLine(it, delimiter) }
        val sheet = buildSheetData(sheetName = "الورقة 1", rawRows = rawRows)
        return ParsedWorkbookResult(displayName, listOf(sheet))
    }

    private fun parseXlsx(bytes: ByteArray, displayName: String): ParsedWorkbookResult {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name.removePrefix("/")] = zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let { parseSharedStrings(ByteArrayInputStream(it)) }
            ?: emptyList()

        val relsMap = entries["xl/_rels/workbook.xml.rels"]
            ?.let { parseWorkbookRels(ByteArrayInputStream(it)) }
            ?: emptyMap()

        val workbookSheets = entries["xl/workbook.xml"]
            ?.let { parseWorkbookSheets(ByteArrayInputStream(it), relsMap) }
            ?: emptyList()

        val sheetsToRead = if (workbookSheets.isNotEmpty()) {
            workbookSheets
        } else {
            entries.keys
                .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                .sorted()
                .mapIndexed { idx, path -> "ورقة ${idx + 1}" to path }
        }

        val parsedSheets = sheetsToRead.mapNotNull { (name, path) ->
            val sheetBytes = entries[path] ?: return@mapNotNull null
            val rawRows = parseWorksheetXml(ByteArrayInputStream(sheetBytes), sharedStrings)
            if (rawRows.isEmpty()) return@mapNotNull null
            buildSheetData(sheetName = name, rawRows = rawRows)
        }

        return ParsedWorkbookResult(displayName, parsedSheets)
    }

    private fun buildSheetData(sheetName: String, rawRows: List<List<String>>): ParsedSheetData {
        val headerIdx = detectHeaderRowIndex(rawRows)
        val headers = rawRows.getOrElse(headerIdx) { emptyList() }.map { it.trim() }
        val dataRows = if (headerIdx + 1 < rawRows.size) {
            rawRows.subList(headerIdx + 1, rawRows.size)
        } else {
            emptyList()
        }.filter { row -> !isPlaceholderOrEmptyRow(row) }

        val mapping = suggestHeaderMapping(headers)
        return ParsedSheetData(
            sheetName = sheetName,
            headerRowIndex = headerIdx,
            headers = headers,
            rows = dataRows,
            suggestedColumnMapping = mapping,
        )
    }

    private fun detectHeaderRowIndex(rawRows: List<List<String>>): Int {
        val knownNorms = JournalSchema.EXACT_HEADERS.map { ArabicNormalizer.normalize(it) }.toSet()
        var bestIdx = 0
        var bestScore = -1
        val limit = minOf(rawRows.size, 8)
        for (i in 0 until limit) {
            val row = rawRows[i]
            val matches = row.count { cell ->
                val norm = ArabicNormalizer.normalize(cell)
                norm.isNotBlank() && knownNorms.contains(norm)
            }
            val nonNumericText = row.count { cell ->
                val t = cell.trim()
                t.isNotBlank() && t.toDoubleOrNull() == null && t != "#N/A" && t != "#REF!"
            }
            val score = matches * 10 + nonNumericText
            if (score > bestScore) {
                bestScore = score
                bestIdx = i
            }
        }
        return bestIdx
    }

    fun suggestHeaderMapping(headers: List<String>): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        val usedSchemaCols = mutableSetOf<Int>()
        val schemaNorms = JournalSchema.EXACT_HEADERS.map { ArabicNormalizer.normalize(it) }

        // First pass: exact normalized match in positional order (handles duplicate "due date")
        headers.forEachIndexed { srcIdx, header ->
            val norm = ArabicNormalizer.normalize(header)
            if (norm.isBlank()) return@forEachIndexed
            val matchIdx = schemaNorms.indices.firstOrNull { schemaIdx ->
                !usedSchemaCols.contains(schemaIdx) && schemaNorms[schemaIdx] == norm
            }
            if (matchIdx != null) {
                result[srcIdx] = matchIdx
                usedSchemaCols.add(matchIdx)
            }
        }

        // Second pass: Arabic/English synonym matching for external Excel files
        headers.forEachIndexed { srcIdx, header ->
            if (result.containsKey(srcIdx)) return@forEachIndexed
            val norm = ArabicNormalizer.normalize(header)
            val synonymCol = matchSynonymColumn(norm, usedSchemaCols)
            if (synonymCol != null) {
                result[srcIdx] = synonymCol
                usedSchemaCols.add(synonymCol)
            }
        }
        return result
    }

    private fun matchSynonymColumn(norm: String, used: Set<Int>): Int? {
        val candidates: List<Pair<List<String>, Int>> = listOf(
            listOf("رقم القيد", "رقم الحركه", "id", "entry id", "operation") to 0,
            listOf("الحساب الرئيسي", "main account", "حساب رئيسي") to 1,
            listOf("الحساب الفرعي", "sub account", "حساب فرعي") to 2,
            listOf("نوع الحركه", "نوع المعامله", "transaction", "trans") to 3,
            listOf("كود العميل", "customer code") to 4,
            listOf("اسم العميل", "العميل") to 5,
            listOf("كود المورد", "supplier code") to 6,
            listOf("اسم المورد", "المورد", "supplier name", "suppliar") to 7,
            listOf("رقم الفاتوره", "فاتوره", "invoice") to 9,
            listOf("البيان", "الوصف", "التفاصيل", "description") to 12,
            listOf("رقم الشيفت", "الشيفت", "shift") to 13,
            listOf("مدين", "المدين", "debit") to 14,
            listOf("دائن", "الدائن", "credit") to 15,
            listOf("الرصيد", "الفرق", "balance", "balanc") to 16,
            listOf("التاريخ", "تاريخ الفاتوره", "date") to 18,
            listOf("الشهر", "month") to 19,
            listOf("ملاحظات", "notes") to 39,
            listOf("كود الفرع", "branch code") to 47,
            listOf("اسم الفرع", "الفرع", "branch") to 48,
            listOf("تصنيف المورد", "فئه المورد") to 49,
            listOf("البنك", "bank") to 50,
        )
        for ((keys, col) in candidates) {
            if (used.contains(col)) continue
            if (keys.any { k -> norm.contains(ArabicNormalizer.normalize(k)) }) {
                return col
            }
        }
        return null
    }

    fun convertRowsToEntries(
        rows: List<List<String>>,
        columnMapping: Map<Int, Int>,
        sourceLabel: String,
    ): List<JournalEntryEntity> {
        return rows.mapNotNull { row ->
            val values = MutableList(JournalSchema.EXACT_HEADERS.size) { "" }
            columnMapping.forEach { (srcIdx, schemaIdx) ->
                if (schemaIdx in values.indices) {
                    values[schemaIdx] = row.getOrNull(srcIdx)?.trim().orEmpty()
                }
            }
            val entity = JournalEntryEntity.fromColumnValues(
                values = values,
                sourceType = JournalSourceType.EXCEL_IMPORT,
                sourceLabel = sourceLabel,
            ).withComputedDerived()

            val hasMeaningfulData = entity.debit != 0.0 ||
                entity.credit != 0.0 ||
                entity.supplierName.isNotBlank() ||
                entity.customerName.isNotBlank() ||
                entity.mainAccountName.isNotBlank() ||
                entity.description.isNotBlank()
            if (hasMeaningfulData) entity else null
        }
    }

    fun extractMasterRecordsFromGenericSheet(sheet: ParsedSheetData): List<MasterRecordEntity> {
        val records = mutableListOf<MasterRecordEntity>()
        val mapped = convertRowsToEntries(sheet.rows, sheet.suggestedColumnMapping, sheet.sheetName)
        if (mapped.isNotEmpty()) {
            for (e in mapped) {
                addIfValid(records, MasterEntityType.MAIN_ACCOUNT, e.mainAccountName)
                addIfValid(records, MasterEntityType.SUB_ACCOUNT, e.subAccountName, category = e.mainAccountName)
                addIfValid(records, MasterEntityType.TRANS_NAME, e.transName)
                addIfValid(records, MasterEntityType.SUPPLIER, e.supplierName, code = e.suppCode, category = e.supplierCategory)
                addIfValid(records, MasterEntityType.CUSTOMER, e.customerName, code = e.custCode)
                addIfValid(records, MasterEntityType.EMPLOYEE, e.employeeName, code = e.employeeCode)
                addIfValid(records, MasterEntityType.BRANCH, e.branchName, code = e.branchCode)
                addIfValid(records, MasterEntityType.BANK, e.bank)
            }
        }
        return records
    }

    private fun addIfValid(
        target: MutableList<MasterRecordEntity>,
        type: MasterEntityType,
        name: String,
        code: String = "",
        category: String = "",
    ) {
        val clean = name.trim()
        if (clean.isBlank() || clean == "#N/A" || clean == "#REF!" || clean == "0") return
        target.add(
            MasterRecordEntity(
                type = type.name,
                code = code.trim(),
                name = clean,
                category = category.trim(),
                normalizedName = ArabicNormalizer.normalize(clean),
            )
        )
    }

    private fun isPlaceholderOrEmptyRow(row: List<String>): Boolean {
        val nonEmpty = row.map { it.trim() }.filter { it.isNotEmpty() }
        if (nonEmpty.isEmpty()) return true
        return nonEmpty.all {
            it == "0" || it == "0.0" || it == "0.00" || it == "#N/A" || it == "#REF!"
        }
    }

    private fun detectDelimiter(sampleLines: List<String>): Char {
        val candidates = listOf('\t', ',', ';')
        return candidates.maxByOrNull { delim ->
            sampleLines.sumOf { line -> line.count { it == delim } }
        } ?: '\t'
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    current.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> {
                        result.add(current.toString())
                        current.clear()
                    }
                    else -> current.append(c)
                }
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")
        var inSi = false
        var inT = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            inSi = true
                            current.clear()
                        }
                        "t" -> if (inSi) inT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi && inT) current.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> inT = false
                        "si" -> {
                            strings.add(current.toString())
                            inSi = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return strings
    }

    private fun parseWorkbookRels(stream: InputStream): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                    val normalizedPath = if (target.startsWith("/")) {
                        target.removePrefix("/")
                    } else if (target.startsWith("xl/")) {
                        target
                    } else {
                        "xl/$target"
                    }
                    map[id] = normalizedPath
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun parseWorkbookSheets(
        stream: InputStream,
        rels: Map<String, String>,
    ): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                val relId = parser.getAttributeValue(null, "r:id")
                    ?: parser.getAttributeValue(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id",
                    )
                val path = relId?.let { rels[it] }
                if (!path.isNullOrBlank()) {
                    list.add(name to path)
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseWorksheetXml(
        stream: InputStream,
        sharedStrings: List<String>,
    ): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        var currentRow = mutableMapOf<Int, String>()
        var inRow = false
        var currentColIdx = 0
        var currentCellType = ""
        var inValueTag = false
        var inInlineTextTag = false
        val valueBuilder = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            inRow = true
                            currentRow = mutableMapOf()
                        }
                        "c" -> if (inRow) {
                            val cellRef = parser.getAttributeValue(null, "r").orEmpty()
                            currentColIdx = if (cellRef.isNotBlank()) columnIndexFromRef(cellRef) else (currentRow.keys.maxOrNull()?.plus(1) ?: 0)
                            currentCellType = parser.getAttributeValue(null, "t").orEmpty()
                            valueBuilder.clear()
                        }
                        "v" -> if (inRow) {
                            inValueTag = true
                            valueBuilder.clear()
                        }
                        "t" -> if (inRow && currentCellType == "inlineStr") {
                            inInlineTextTag = true
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inRow && (inValueTag || inInlineTextTag)) {
                        valueBuilder.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> inValueTag = false
                        "t" -> inInlineTextTag = false
                        "c" -> if (inRow) {
                            val raw = valueBuilder.toString()
                            val resolved = when (currentCellType) {
                                "s" -> {
                                    val idx = raw.trim().toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                                }
                                "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
                                else -> raw
                            }
                            currentRow[currentColIdx] = resolved
                        }
                        "row" -> {
                            inRow = false
                            val maxCol = currentRow.keys.maxOrNull() ?: -1
                            if (maxCol >= 0) {
                                val list = MutableList(maxCol + 1) { idx -> currentRow[idx].orEmpty() }
                                rows.add(list)
                            }
                        }
                    }
                }
            }
            event = parser.next()
        }
        return normalizePotentialSerialDates(rows)
    }

    private fun normalizePotentialSerialDates(rows: List<List<String>>): List<List<String>> {
        if (rows.isEmpty()) return rows
        val headerIdx = detectHeaderRowIndex(rows)
        val headers = rows.getOrElse(headerIdx) { emptyList() }
        val dateCols = headers.mapIndexedNotNull { idx, h ->
            val n = ArabicNormalizer.normalize(h)
            if (n == "date" || n.contains("due date") || n.contains("تاريخ")) idx else null
        }.toSet()
        if (dateCols.isEmpty()) return rows

        return rows.mapIndexed { rIdx, row ->
            if (rIdx <= headerIdx) row
            else row.mapIndexed { cIdx, cell ->
                if (cIdx in dateCols) {
                    val num = cell.trim().toDoubleOrNull()
                    if (num != null && num in 35000.0..60000.0) {
                        excelSerialToDate(num.toInt())
                    } else cell
                } else cell
            }
        }
    }

    private fun excelSerialToDate(serial: Int): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
        cal.add(Calendar.DAY_OF_YEAR, serial)
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(cal.time)
    }

    private fun columnIndexFromRef(ref: String): Int {
        var col = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch - 'a' + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }
}
