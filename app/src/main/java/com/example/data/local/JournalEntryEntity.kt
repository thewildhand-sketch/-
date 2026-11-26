package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.model.JournalSourceType
import java.util.Locale

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["col_01_operation_num"]),
        Index(value = ["col_08_suppliar_name"]),
        Index(value = ["col_19_date"]),
    ]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "col_01_operation_num")
    val operationNum: String = "",                     // 0: رقم العملية

    @ColumnInfo(name = "col_02_main_account_name")
    val mainAccountName: String = "",                  // 1: Main account name

    @ColumnInfo(name = "col_03_sub_account_name")
    val subAccountName: String = "",                   // 2: Sub account name

    @ColumnInfo(name = "col_04_trans_name")
    val transName: String = "",                        // 3: Trans. Name

    @ColumnInfo(name = "col_05_cust_code")
    val custCode: String = "",                         // 4: cust. code

    @ColumnInfo(name = "col_06_customer_name")
    val customerName: String = "",                     // 5: customer name

    @ColumnInfo(name = "col_07_supp_code")
    val suppCode: String = "",                         // 6: supp. code

    @ColumnInfo(name = "col_08_suppliar_name")
    val supplierName: String = "",                     // 7: Suppliar name

    @ColumnInfo(name = "col_09_h")
    val hField: String = "",                           // 8: H

    @ColumnInfo(name = "col_10_payment_invoice_num")
    val paymentInvoiceNum: String = "",                // 9: payment invoice num.

    @ColumnInfo(name = "col_11_employee_code")
    val employeeCode: String = "",                     // 10: كود الموظف

    @ColumnInfo(name = "col_12_employee_name")
    val employeeName: String = "",                     // 11: اسم الموظف

    @ColumnInfo(name = "col_13_description")
    val description: String = "",                      // 12: بيان

    @ColumnInfo(name = "col_14_shift_num")
    val shiftNum: String = "",                         // 13: shift num.

    @ColumnInfo(name = "col_15_debit")
    val debit: Double = 0.0,                           // 14: debit

    @ColumnInfo(name = "col_16_credit")
    val credit: Double = 0.0,                          // 15: credit

    @ColumnInfo(name = "col_17_balanc")
    val balanc: Double = 0.0,                          // 16: balanc

    @ColumnInfo(name = "col_18_condition")
    val condition: String = "",                        // 17: condition

    @ColumnInfo(name = "col_19_date")
    val date: String = "",                             // 18: date

    @ColumnInfo(name = "col_20_month")
    val month: String = "",                            // 19: Month

    @ColumnInfo(name = "col_21_payment_cheque_num")
    val paymentChequeNum: String = "",                 // 20: Payment cheque num.

    @ColumnInfo(name = "col_22_payment_due_date")
    val paymentChequeDueDate: String = "",             // 21: due date (Payment Cheque)

    @ColumnInfo(name = "col_23_payment_cheque_supp_code")
    val paymentChequeSupplierCode: String = "",        // 22: the supplier code of Payment Cheque

    @ColumnInfo(name = "col_24_payment_cheque_supp_name")
    val paymentChequeSupplierName: String = "",        // 23: the supplier name of Payment Cheque

    @ColumnInfo(name = "col_25_sales_tax_2372")
    val salesTax2372: Double = 0.0,                    // 24: sales tax2372

    @ColumnInfo(name = "col_26_customer_tax_1357")
    val customerTax1357: Double = 0.0,                 // 25: customer's tax1357

    @ColumnInfo(name = "col_27_received_cheque_num")
    val receivedChequeNum: String = "",                // 26: Received cheque num.

    @ColumnInfo(name = "col_28_received_due_date")
    val receivedChequeDueDate: String = "",            // 27: due date (Received Cheque)

    @ColumnInfo(name = "col_29_received_cheque_cust_code")
    val receivedChequeCustomerCode: String = "",       // 28: the customer code of received cheque

    @ColumnInfo(name = "col_30_received_cheque_cust_name")
    val receivedChequeCustomerName: String = "",       // 29: the customer name of received cheque

    @ColumnInfo(name = "col_31_withholding_tax_1pct_2361")
    val withholdingTax1Pct2361: Double = 0.0,          // 30: خصم ضرائب ا ت ص 1%2361

    @ColumnInfo(name = "col_32_payment_cheque_due_month")
    val paymentChequeDueMonth: String = "",            // 31: شهر استحقاقات اوراق الدفع

    @ColumnInfo(name = "col_33_payment_paper_dates")
    val paymentPaperDates: String = "",                // 32: تورايخ أ . د

    @ColumnInfo(name = "col_34_month_2")
    val month2: String = "",                           // 33: الشهر 2

    @ColumnInfo(name = "col_35_payment_paper_year")
    val paymentPaperYear: String = "",                 // 34: سنة اوراق الدفع 

    @ColumnInfo(name = "col_36_good_returns")
    val goodReturns: Double = 0.0,                     // 35: المرتجعات سليمة

    @ColumnInfo(name = "col_37_damaged_returns")
    val damagedReturns: Double = 0.0,                  // 36: تالفة

    @ColumnInfo(name = "col_38_discounts")
    val discounts: Double = 0.0,                       // 37: الخصومات

    @ColumnInfo(name = "col_39_taxes")
    val taxes: Double = 0.0,                           // 38: الضرائب

    @ColumnInfo(name = "col_40_notes")
    val notes: String = "",                            // 39: ملاحظات

    @ColumnInfo(name = "col_41_visa_count")
    val visaCount: Double = 0.0,                       // 40: عدد الفيزا

    @ColumnInfo(name = "col_42_cash_variance")
    val cashVariance: Double = 0.0,                    // 41: عجز / زيادة

    @ColumnInfo(name = "col_43_purchase_invoices_month")
    val purchaseInvoicesMonth: String = "",            // 42: شهر فواتير المشتريات

    @ColumnInfo(name = "col_44_customer_cheque_num")
    val customerChequeNum: String = "",                // 43: رقم شيك العملاء

    @ColumnInfo(name = "col_45_incoming_cheque_date")
    val incomingChequeDate: String = "",               // 44: تاريخ الشيك الوارد

    @ColumnInfo(name = "col_46_client_ar")
    val clientAr: String = "",                         // 45: العميل

    @ColumnInfo(name = "col_47_visa_number")
    val visaNumber: String = "",                       // 46: رقم الفيزا

    @ColumnInfo(name = "col_48_branch_code")
    val branchCode: String = "",                       // 47: كود الفرع

    @ColumnInfo(name = "col_49_branch_name")
    val branchName: String = "",                       // 48: اسم الفرع

    @ColumnInfo(name = "col_50_supplier_category")
    val supplierCategory: String = "",                 // 49: تصنيف المورد

    @ColumnInfo(name = "col_51_bank")
    val bank: String = "",                             // 50: البنك

    @ColumnInfo(name = "source_type")
    val sourceType: String = JournalSourceType.MANUAL.name,

    @ColumnInfo(name = "source_label")
    val sourceLabel: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun withComputedDerived(): JournalEntryEntity {
        val computedBalanc = debit - credit
        val extractedMonth = month.ifBlank { extractMonthFromDate(date) }
        val extractedPayMonth = paymentChequeDueMonth.ifBlank { extractMonthFromDate(paymentChequeDueDate) }
        val extractedPayYear = paymentPaperYear.ifBlank { extractYearFromDate(paymentChequeDueDate) }
        val resolvedMonth2 = month2.ifBlank { extractedMonth }
        val resolvedPurchMonth = purchaseInvoicesMonth.ifBlank {
            if (paymentInvoiceNum.isNotBlank() || supplierName.isNotBlank()) extractedMonth else ""
        }
        val resolvedClient = clientAr.ifBlank { customerName }
        val resolvedChequeDate = incomingChequeDate.ifBlank { receivedChequeDueDate }
        val resolvedCustChequeNum = customerChequeNum.ifBlank { receivedChequeNum }
        return copy(
            balanc = computedBalanc,
            month = extractedMonth,
            paymentChequeDueMonth = extractedPayMonth,
            paymentPaperYear = extractedPayYear,
            month2 = resolvedMonth2,
            purchaseInvoicesMonth = resolvedPurchMonth,
            clientAr = resolvedClient,
            incomingChequeDate = resolvedChequeDate,
            customerChequeNum = resolvedCustChequeNum,
        )
    }

    fun toColumnValues(): List<String> = listOf(
        operationNum,
        mainAccountName,
        subAccountName,
        transName,
        custCode,
        customerName,
        suppCode,
        supplierName,
        hField,
        paymentInvoiceNum,
        employeeCode,
        employeeName,
        description,
        shiftNum,
        formatNum(debit),
        formatNum(credit),
        formatNum(debit - credit),
        condition,
        date,
        month,
        paymentChequeNum,
        paymentChequeDueDate,
        paymentChequeSupplierCode,
        paymentChequeSupplierName,
        formatNum(salesTax2372),
        formatNum(customerTax1357),
        receivedChequeNum,
        receivedChequeDueDate,
        receivedChequeCustomerCode,
        receivedChequeCustomerName,
        formatNum(withholdingTax1Pct2361),
        paymentChequeDueMonth,
        paymentPaperDates,
        month2,
        paymentPaperYear,
        formatNum(goodReturns),
        formatNum(damagedReturns),
        formatNum(discounts),
        formatNum(taxes),
        notes,
        formatNum(visaCount),
        formatNum(cashVariance),
        purchaseInvoicesMonth,
        customerChequeNum,
        incomingChequeDate,
        clientAr,
        visaNumber,
        branchCode,
        branchName,
        supplierCategory,
        bank,
    )

    companion object {
        private fun formatNum(value: Double): String =
            String.format(Locale.US, "%.2f", value)

        private fun extractMonthFromDate(dateStr: String): String {
            val parts = dateStr.trim().split("-", "/", ".")
            if (parts.size < 2) return ""
            return parts[1].trimStart('0').ifEmpty { "" }
        }

        private fun extractYearFromDate(dateStr: String): String {
            val parts = dateStr.trim().split("-", "/", ".")
            if (parts.size < 3) return ""
            val first = parts.firstOrNull().orEmpty()
            val last = parts.lastOrNull().orEmpty()
            return when {
                first.length == 4 -> first
                last.length == 4 -> last
                else -> ""
            }
        }

        fun fromColumnValues(
            values: List<String>,
            sourceType: JournalSourceType = JournalSourceType.EXCEL_IMPORT,
            sourceLabel: String = "",
        ): JournalEntryEntity {
            fun getStr(index: Int): String = values.getOrNull(index)?.trim().orEmpty()
            fun getNum(index: Int): Double =
                getStr(index)
                    .replace(",", "")
                    .replace(" ", "")
                    .toDoubleOrNull() ?: 0.0

            val debitVal = getNum(14)
            val creditVal = getNum(15)
            val balancRaw = getStr(16)
            val balancVal = if (balancRaw.isBlank()) debitVal - creditVal else getNum(16)

            return JournalEntryEntity(
                operationNum = getStr(0),
                mainAccountName = getStr(1),
                subAccountName = getStr(2),
                transName = getStr(3),
                custCode = getStr(4),
                customerName = getStr(5),
                suppCode = getStr(6),
                supplierName = getStr(7),
                hField = getStr(8),
                paymentInvoiceNum = getStr(9),
                employeeCode = getStr(10),
                employeeName = getStr(11),
                description = getStr(12),
                shiftNum = getStr(13),
                debit = debitVal,
                credit = creditVal,
                balanc = balancVal,
                condition = getStr(17),
                date = getStr(18),
                month = getStr(19),
                paymentChequeNum = getStr(20),
                paymentChequeDueDate = getStr(21),
                paymentChequeSupplierCode = getStr(22),
                paymentChequeSupplierName = getStr(23),
                salesTax2372 = getNum(24),
                customerTax1357 = getNum(25),
                receivedChequeNum = getStr(26),
                receivedChequeDueDate = getStr(27),
                receivedChequeCustomerCode = getStr(28),
                receivedChequeCustomerName = getStr(29),
                withholdingTax1Pct2361 = getNum(30),
                paymentChequeDueMonth = getStr(31),
                paymentPaperDates = getStr(32),
                month2 = getStr(33),
                paymentPaperYear = getStr(34),
                goodReturns = getNum(35),
                damagedReturns = getNum(36),
                discounts = getNum(37),
                taxes = getNum(38),
                notes = getStr(39),
                visaCount = getNum(40),
                cashVariance = getNum(41),
                purchaseInvoicesMonth = getStr(42),
                customerChequeNum = getStr(43),
                incomingChequeDate = getStr(44),
                clientAr = getStr(45),
                visaNumber = getStr(46),
                branchCode = getStr(47),
                branchName = getStr(48),
                supplierCategory = getStr(49),
                bank = getStr(50),
                sourceType = sourceType.name,
                sourceLabel = sourceLabel,
            )
        }
    }
}
