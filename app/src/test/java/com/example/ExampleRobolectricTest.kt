package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("دفتر القيود", appName)
  }

  @Test
  fun `xlsx writer and reader round trip works on Android XmlPullParser`() {
    val entries = listOf(
      com.example.data.local.JournalEntryEntity(
        operationNum = "12",
        mainAccountName = "المشتريات",
        subAccountName = "خامات",
        suppCode = "S12",
        supplierName = "شركة الهدى",
        description = "فاتورة خامات",
        debit = 500.0,
        credit = 0.0,
        date = "2026-11-26",
      ).withComputedDerived()
    )
    val bytes = com.example.data.spreadsheet.XlsxWriter.buildWorkbookBytes(entries, emptyList())
    val parsed = com.example.data.spreadsheet.XlsxReader.readBytes(bytes, "roundtrip.xlsx")
    assertEquals(2, parsed.sheets.size)
    val journalSheet = parsed.sheets.first()
    val restored = com.example.data.spreadsheet.XlsxReader.convertRowsToEntries(
      journalSheet.rows,
      journalSheet.suggestedColumnMapping,
      "roundtrip.xlsx",
    )
    assertEquals(1, restored.size)
    assertEquals("شركة الهدى", restored.first().supplierName)
    assertEquals(500.0, restored.first().debit, 0.001)
  }

  @Test
  fun `room database persists suppliers accounts and 51 column journal entries`() =
    kotlinx.coroutines.test.runTest {
      val context = ApplicationProvider.getApplicationContext<Context>()
      val db = androidx.room.Room.inMemoryDatabaseBuilder(
        context,
        com.example.data.local.AppDatabase::class.java,
      ).allowMainThreadQueries().build()

      try {
        val repo = com.example.data.local.LedgerRepository(
          journalDao = db.journalDao(),
          supplierDao = db.supplierDao(),
          accountDao = db.accountDao(),
          masterDao = db.masterDao(),
        )
        repo.saveJournalOperation(
          listOf(
            com.example.data.local.JournalEntryEntity(
              operationNum = "99",
              mainAccountName = "المشتريات",
              subAccountName = "مشتريات أغذية",
              transName = "فاتورة مشتريات",
              suppCode = "SUP-99",
              supplierName = "شركة الوفاء للتوريدات",
              supplierCategory = "أغذية",
              debit = 1200.0,
              credit = 0.0,
              date = "2026-11-26",
            )
          )
        )
        val entries = repo.getAllEntriesAsc()
        val suppliers = db.supplierDao().getAll()
        val accounts = db.accountDao().getAll()

        assertEquals(1, entries.size)
        assertEquals(1, suppliers.size)
        assertEquals("شركة الوفاء للتوريدات", suppliers.first().supplierName)
        assertEquals("SUP-99", suppliers.first().suppCode)
        assertEquals(1, accounts.size)
        assertEquals("المشتريات", accounts.first().mainAccountName)
        assertEquals("مشتريات أغذية", accounts.first().subAccountName)
      } finally {
        db.close()
      }
    }
}
