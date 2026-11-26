package com.example

import com.example.data.local.JournalEntryEntity
import com.example.data.local.MasterRecordEntity
import com.example.data.spreadsheet.XlsxReader
import com.example.data.spreadsheet.XlsxWriter
import com.example.domain.DoubleEntryGenerator
import com.example.domain.EntityMatcher
import com.example.model.JournalSchema
import com.example.model.MasterEntityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JournalEngineTest {

    @Test
    fun exactSchemaHasAll51ColumnsInOrder() {
        assertEquals(51, JournalSchema.EXACT_HEADERS.size)
        assertEquals("رقم العملية", JournalSchema.EXACT_HEADERS[0])
        assertEquals("Suppliar name", JournalSchema.EXACT_HEADERS[7])
        assertEquals("balanc", JournalSchema.EXACT_HEADERS[16])
        assertEquals("due date", JournalSchema.EXACT_HEADERS[21])
        assertEquals("due date", JournalSchema.EXACT_HEADERS[27])
        assertEquals("تورايخ أ . د", JournalSchema.EXACT_HEADERS[32])
        assertEquals("سنة اوراق الدفع ", JournalSchema.EXACT_HEADERS[34])
        assertEquals("البنك", JournalSchema.EXACT_HEADERS[50])
    }

    @Test
    fun tsvTemplateWithTotalsRowParsesAllColumns() {
        val row0 = "\t".repeat(14) + "0.00\t0.00\t0.00"
        val row1 = JournalSchema.EXACT_HEADERS.joinToString("\t")
        val row2 = MutableList(51) { "" }.apply {
            set(0, "101")
            set(1, "المشتريات")
            set(2, "مشتريات أغذية")
            set(6, "S-09")
            set(7, "شركة النور للتوريدات")
            set(12, "فاتورة رقم 55")
            set(14, "1500.00")
            set(15, "0.00")
            set(18, "2026-11-20")
        }.joinToString("\t")

        val parsed = XlsxReader.parseDelimitedText("$row0\n$row1\n$row2", "test.txt")
        assertEquals(1, parsed.sheets.size)
        val sheet = parsed.sheets.first()
        assertEquals(51, sheet.suggestedColumnMapping.size)
        val entries = XlsxReader.convertRowsToEntries(sheet.rows, sheet.suggestedColumnMapping, "test.txt")
        assertEquals(1, entries.size)
        assertEquals("شركة النور للتوريدات", entries.first().supplierName)
        assertEquals(1500.0, entries.first().debit, 0.001)
        assertEquals(1500.0, entries.first().balanc, 0.001)
    }

    @Test
    fun entityMatcherResolvesArabicVariationsToExactMasterName() {
        val masters = listOf(
            MasterRecordEntity(
                type = MasterEntityType.SUPPLIER.name,
                code = "SUP-42",
                name = "شركة الأمل للتجارة",
                category = "موردين أغذية",
            )
        )
        val draft = listOf(
            JournalEntryEntity(
                operationNum = "1",
                supplierName = "شركه الامل للتجاره",
                debit = 500.0,
            )
        )
        val matched = EntityMatcher.resolveDraftAgainstMaster(draft, masters)
        assertEquals("شركة الأمل للتجارة", matched.entries.first().supplierName)
        assertEquals("SUP-42", matched.entries.first().suppCode)
        assertEquals("موردين أغذية", matched.entries.first().supplierCategory)
        assertTrue(matched.warnings.isEmpty())
    }

    @Test
    fun autoBalanceBalancesUnbalancedDraft() {
        val lines = listOf(
            JournalEntryEntity(operationNum = "5", mainAccountName = "المشتريات", debit = 1000.0, credit = 0.0),
            JournalEntryEntity(operationNum = "5", mainAccountName = "ضريبة", debit = 140.0, credit = 0.0),
        )
        val balanced = DoubleEntryGenerator.autoBalance(lines, "5")
        assertTrue(DoubleEntryGenerator.isBalanced(balanced))
        assertEquals(1140.0, DoubleEntryGenerator.totalCredit(balanced), 0.001)
    }

    @Test
    fun xlsxRoundTripPreserves51ColumnsAndArabicValues() {
        val entries = listOf(
            JournalEntryEntity(
                operationNum = "7",
                mainAccountName = "المشتريات",
                subAccountName = "مخزون",
                suppCode = "S10",
                supplierName = "مؤسسة الفجر",
                description = "شراء بضاعة",
                debit = 250.0,
                credit = 0.0,
                date = "2026-11-26",
            ).withComputedDerived(),
            JournalEntryEntity(
                operationNum = "7",
                mainAccountName = "الموردين",
                subAccountName = "مؤسسة الفجر",
                suppCode = "S10",
                supplierName = "مؤسسة الفجر",
                description = "استحقاق المورد",
                debit = 0.0,
                credit = 250.0,
                date = "2026-11-26",
            ).withComputedDerived(),
        )
        val bytes = XlsxWriter.buildWorkbookBytes(entries, emptyList())
        assertTrue(bytes.isNotEmpty())
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
    }
}
