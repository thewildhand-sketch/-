package com.example.domain

import com.example.data.local.JournalEntryEntity
import com.example.model.EntryTemplateKind
import com.example.model.JournalSourceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object DoubleEntryGenerator {
    fun todayFormatted(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun totalDebit(lines: List<JournalEntryEntity>): Double = lines.sumOf { it.debit }

    fun totalCredit(lines: List<JournalEntryEntity>): Double = lines.sumOf { it.credit }

    fun balanceDifference(lines: List<JournalEntryEntity>): Double =
        totalDebit(lines) - totalCredit(lines)

    fun isBalanced(lines: List<JournalEntryEntity>): Boolean =
        lines.isNotEmpty() && abs(balanceDifference(lines)) < 0.01 && totalDebit(lines) > 0.0

    fun autoBalance(lines: List<JournalEntryEntity>, operationNum: String): List<JournalEntryEntity> {
        val diff = balanceDifference(lines)
        if (abs(diff) < 0.01) return lines
        val template = lines.firstOrNull() ?: JournalEntryEntity(operationNum = operationNum, date = todayFormatted())
        val balancingLine = if (diff > 0) {
            JournalEntryEntity(
                operationNum = operationNum,
                mainAccountName = template.mainAccountName.ifBlank { "الموردين" },
                subAccountName = "حساب متمم دائن",
                transName = template.transName.ifBlank { "موازنة قيد" },
                description = "سطر موازنة قيد عملية رقم $operationNum",
                debit = 0.0,
                credit = diff,
                date = template.date.ifBlank { todayFormatted() },
                branchCode = template.branchCode,
                branchName = template.branchName,
                sourceType = template.sourceType,
                sourceLabel = template.sourceLabel,
            ).withComputedDerived()
        } else {
            JournalEntryEntity(
                operationNum = operationNum,
                mainAccountName = template.mainAccountName.ifBlank { "المشتريات" },
                subAccountName = "حساب متمم مدين",
                transName = template.transName.ifBlank { "موازنة قيد" },
                description = "سطر موازنة قيد عملية رقم $operationNum",
                debit = abs(diff),
                credit = 0.0,
                date = template.date.ifBlank { todayFormatted() },
                branchCode = template.branchCode,
                branchName = template.branchName,
                sourceType = template.sourceType,
                sourceLabel = template.sourceLabel,
            ).withComputedDerived()
        }
        return lines + balancingLine
    }

    fun buildTemplateLines(
        kind: EntryTemplateKind,
        operationNum: String,
    ): List<JournalEntryEntity> {
        val today = todayFormatted()
        return when (kind) {
            EntryTemplateKind.PURCHASE_INVOICE -> listOf(
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "المشتريات",
                    subAccountName = "مشتريات بضاعة",
                    transName = "فاتورة مشتريات",
                    description = "قيد فاتورة مشتريات",
                    debit = 0.0,
                    credit = 0.0,
                    date = today,
                    condition = "آجل",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "الموردين",
                    subAccountName = "حساب المورد",
                    transName = "فاتورة مشتريات",
                    description = "استحقاق مورد فاتورة مشتريات",
                    debit = 0.0,
                    credit = 0.0,
                    date = today,
                    condition = "آجل",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
            )
            EntryTemplateKind.SALES_SHIFT -> listOf(
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "النقدية والبنوك",
                    subAccountName = "خزينة الفرع / فيزا",
                    transName = "شيفت مبيعات",
                    description = "توريد مبيعات الشيفت",
                    debit = 0.0,
                    credit = 0.0,
                    date = today,
                    condition = "نقدي",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "المبيعات",
                    subAccountName = "إيرادات المبيعات",
                    transName = "شيفت مبيعات",
                    description = "إثبات مبيعات الشيفت",
                    debit = 0.0,
                    credit = 0.0,
                    date = today,
                    condition = "نقدي",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
            )
            EntryTemplateKind.PAYMENT_CHEQUE -> listOf(
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "الموردين",
                    subAccountName = "سداد مورد بشيك",
                    transName = "أوراق دفع",
                    description = "إصدار شيك دفع لمورد",
                    date = today,
                    condition = "شيك",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "أوراق الدفع",
                    subAccountName = "شيكات صادرة",
                    transName = "أوراق دفع",
                    description = "أوراق دفع مستحقة",
                    date = today,
                    condition = "شيك",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
            )
            EntryTemplateKind.RECEIVED_CHEQUE -> listOf(
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "أوراق القبض",
                    subAccountName = "شيكات واردة",
                    transName = "أوراق قبض",
                    description = "استلام شيك من عميل",
                    date = today,
                    condition = "شيك",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "العملاء",
                    subAccountName = "تحصيل عملاء بشيك",
                    transName = "أوراق قبض",
                    description = "تخفيض رصيد العميل بشيك",
                    date = today,
                    condition = "شيك",
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
            )
            EntryTemplateKind.RETURN_OR_DISCOUNT -> listOf(
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "الموردين",
                    subAccountName = "مرتجعات وخصومات",
                    transName = "إشعار خصم/مرتجع",
                    description = "إثبات مرتجع أو خصم مكتسب",
                    date = today,
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
                JournalEntryEntity(
                    operationNum = operationNum,
                    mainAccountName = "المشتريات",
                    subAccountName = "مرتجع مشتريات",
                    transName = "إشعار خصم/مرتجع",
                    description = "تخفيض المشتريات بالمرتجع",
                    date = today,
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
            )
            EntryTemplateKind.GENERAL_ENTRY -> listOf(
                JournalEntryEntity(
                    operationNum = operationNum,
                    transName = "قيد يومية",
                    date = today,
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
                JournalEntryEntity(
                    operationNum = operationNum,
                    transName = "قيد يومية",
                    date = today,
                    sourceType = JournalSourceType.MANUAL.name,
                ).withComputedDerived(),
            )
        }
    }
}
