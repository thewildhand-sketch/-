package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.JournalEntryEntity
import com.example.data.local.MasterRecordEntity
import com.example.domain.DoubleEntryGenerator
import com.example.model.JournalSourceType
import com.example.model.MasterEntityType
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiDocExtractor"

data class ExtractedDocumentDraft(
    val summaryTitle: String,
    val documentType: String,
    val entries: List<JournalEntryEntity>,
)

class GeminiDocumentExtractor {

    private val journalLineSchema = Schema.obj(
        properties = mapOf(
            "mainAccountName" to Schema.string("Main account name matched from master list when available"),
            "subAccountName" to Schema.string("Sub account name matched from master list when available"),
            "transName" to Schema.string("Transaction type name (Trans. Name)"),
            "description" to Schema.string("Clear Arabic description (بيان) for this journal line"),
            "debit" to Schema.double("Debit amount (>= 0)"),
            "credit" to Schema.double("Credit amount (>= 0)"),
        )
    )

    private val documentSchema = Schema.obj(
        properties = mapOf(
            "documentType" to Schema.string("One of: purchase_invoice, sales_shift, payment_cheque, received_cheque, return_note, general_entry"),
            "summaryTitle" to Schema.string("Concise Arabic title summarizing party, invoice/shift number, and total"),
            "date" to Schema.string("Document date in YYYY-MM-DD format"),
            "invoiceNum" to Schema.string("Invoice number if present"),
            "shiftNum" to Schema.string("Sales shift number if present"),
            "supplierName" to Schema.string("Exact supplier name from master list if present"),
            "supplierCode" to Schema.string("Supplier code if known"),
            "supplierCategory" to Schema.string("Supplier classification if known"),
            "customerName" to Schema.string("Exact customer name from master list if present"),
            "customerCode" to Schema.string("Customer code if known"),
            "branchName" to Schema.string("Branch name if present"),
            "branchCode" to Schema.string("Branch code if known"),
            "employeeName" to Schema.string("Employee or cashier name if present"),
            "employeeCode" to Schema.string("Employee code if known"),
            "bank" to Schema.string("Bank name if present"),
            "chequeNum" to Schema.string("Cheque number if present"),
            "dueDate" to Schema.string("Cheque due date YYYY-MM-DD if present"),
            "condition" to Schema.string("Payment condition e.g. نقدي or آجل or فيزا or شيك"),
            "salesTax2372" to Schema.double("Sales/VAT tax amount"),
            "customerTax1357" to Schema.double("Customer tax amount"),
            "withholdingTax1Pct2361" to Schema.double("1% withholding tax amount"),
            "goodReturns" to Schema.double("Good returns amount"),
            "damagedReturns" to Schema.double("Damaged returns amount"),
            "discounts" to Schema.double("Discounts amount"),
            "taxes" to Schema.double("Total taxes amount"),
            "visaCount" to Schema.double("Count or total of Visa transactions"),
            "visaNumber" to Schema.string("Visa slip or terminal number"),
            "cashVariance" to Schema.double("Cash over/short variance (عجز / زيادة)"),
            "notes" to Schema.string("Additional remarks in Arabic"),
            "lines" to Schema.array(
                items = journalLineSchema,
                description = "Balanced double-entry lines where sum(debit) == sum(credit)"
            ),
        ),
        optionalProperties = listOf(
            "invoiceNum", "shiftNum", "supplierName", "supplierCode", "supplierCategory",
            "customerName", "customerCode", "branchName", "branchCode", "employeeName",
            "employeeCode", "bank", "chequeNum", "dueDate", "condition",
            "salesTax2372", "customerTax1357", "withholdingTax1Pct2361",
            "goodReturns", "damagedReturns", "discounts", "taxes",
            "visaCount", "visaNumber", "cashVariance", "notes"
        )
    )

    private val rootResponseSchema = Schema.obj(
        properties = mapOf(
            "documents" to Schema.array(
                items = documentSchema,
                description = "All distinct invoices, sales shifts, or cheques detected across the provided pages/images"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = rootResponseSchema
                temperature = 0.1f
            }
        )
    }

    suspend fun extractFromBitmaps(
        bitmaps: List<Bitmap>,
        masterRecords: List<MasterRecordEntity>,
        startingOpNum: Int,
        sourceType: JournalSourceType,
        sourceLabel: String,
    ): List<ExtractedDocumentDraft> {
        if (bitmaps.isEmpty()) return emptyList()
        val promptText = buildSystemPrompt(masterRecords)
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        text(promptText)
                        bitmaps.forEach { bmp -> image(bmp) }
                    }
                )
            }
            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                throw IllegalStateException("لم يُرجع نموذج الذكاء الاصطناعي أي بيانات من الصور.")
            }
            parseDocumentsJson(jsonText, startingOpNum, sourceType, sourceLabel)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini extraction timed out", e)
            throw IllegalStateException("انتهت مهلة تحليل المستندات (60 ثانية). جرب تقليل عدد الصفحات في الدفعة.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini extraction failed", e)
            throw IllegalStateException(e.localizedMessage ?: "فشل تحليل المستندات عبر الذكاء الاصطناعي.")
        }
    }

    suspend fun extractFromRawText(
        rawText: String,
        masterRecords: List<MasterRecordEntity>,
        startingOpNum: Int,
        sourceLabel: String = "نص مخصص",
    ): List<ExtractedDocumentDraft> {
        if (rawText.isBlank()) return emptyList()
        val promptText = buildSystemPrompt(masterRecords) + "\n\nالنص المراد تحويله لقيود يومية:\n" + rawText
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(promptText)
            }
            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                throw IllegalStateException("لم يُرجع نموذج الذكاء الاصطناعي أي بيانات.")
            }
            parseDocumentsJson(jsonText, startingOpNum, JournalSourceType.EXCEL_IMPORT, sourceLabel)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini text extraction timed out", e)
            throw IllegalStateException("انتهت مهلة تحليل النص (60 ثانية).")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini text extraction failed", e)
            throw IllegalStateException(e.localizedMessage ?: "فشل تحويل النص إلى قيود.")
        }
    }

    private fun buildSystemPrompt(masterRecords: List<MasterRecordEntity>): String {
        fun formatMaster(type: MasterEntityType, limit: Int = 80): String {
            val list = masterRecords.filter { it.type == type.name }.take(limit)
            if (list.isEmpty()) return "غير متوفر"
            return list.joinToString(" | ") { rec ->
                buildString {
                    append(rec.name)
                    if (rec.code.isNotBlank()) append(" [كود:${rec.code}]")
                    if (rec.category.isNotBlank()) append(" (${rec.category})")
                }
            }
        }

        return """
            أنت محاسب خبير متخصص في تسجيل قيود اليومية المزدوجة باللغة العربية والإنجليزية.
            حلّل جميع الصور أو النصوص المرفقة واستخرج كل فاتورة مشتريات أو شيفت مبيعات أو شيك دفع/قبض كعنصر منفصل في مصفوفة documents.

            قواعد إلزامية:
            1. طابِق أسماء الموردين والعملاء والحسابات الرئيسية والفرعية وأنواع المعاملات والفروع والموظفين والبنوك حرفياً مع القوائم التالية المستوردة من إكسيل المستخدم إن وجدت مطابقة قريبة:
               - الموردين: ${formatMaster(MasterEntityType.SUPPLIER)}
               - العملاء: ${formatMaster(MasterEntityType.CUSTOMER)}
               - الحسابات الرئيسية (Main account name): ${formatMaster(MasterEntityType.MAIN_ACCOUNT)}
               - الحسابات الفرعية (Sub account name): ${formatMaster(MasterEntityType.SUB_ACCOUNT)}
               - أسماء المعاملات (Trans. Name): ${formatMaster(MasterEntityType.TRANS_NAME)}
               - الفروع: ${formatMaster(MasterEntityType.BRANCH)}
               - الموظفين: ${formatMaster(MasterEntityType.EMPLOYEE)}
               - البنوك: ${formatMaster(MasterEntityType.BANK)}
            2. لكل مستند، ولّد أسطر قيد مزدوج متوازن تماماً (مجموع debit يساوي مجموع credit بدقة).
               - في فواتير المشتريات: اجعل المشتريات/المصروف والضريبة في جانب المدين (debit)، وحساب المورد أو الخزينة/البنك وخصم الضرائب 1% في جانب الدائن (credit).
               - في شيفتات المبيعات: اجعل النقدية والفيزا والعجز (إن وجد) في جانب المدين (debit)، وإيراد المبيعات والضرائب والزيادة (إن وجدت) في جانب الدائن (credit)، وسجّل shift num. وعدد الفيزا ورقم الفيزا وعجز/زيادة.
            3. إذا كان المستند غير واضح تماماً، استخرج كل الأرقام والنصوص الظاهرة بدقة ولا تخترع أرقاماً وهمية.
            4. صيغة التاريخ تكون YYYY-MM-DD (تاريخ اليوم الافتراضي إذا غاب التاريخ: ${DoubleEntryGenerator.todayFormatted()}).
        """.trimIndent()
    }

    private fun parseDocumentsJson(
        jsonString: String,
        startingOpNum: Int,
        sourceType: JournalSourceType,
        sourceLabel: String,
    ): List<ExtractedDocumentDraft> {
        val root = JSONObject(jsonString)
        val docsArray = root.optJSONArray("documents") ?: return emptyList()
        val drafts = mutableListOf<ExtractedDocumentDraft>()

        for (i in 0 until docsArray.length()) {
            val doc = docsArray.optJSONObject(i) ?: continue
            val opNum = (startingOpNum + i).toString()
            val docType = doc.optString("documentType", "general_entry")
            val summaryTitle = doc.optString("summaryTitle", "قيد مستخرج ${i + 1}")
            val date = doc.optString("date", DoubleEntryGenerator.todayFormatted())
                .ifBlank { DoubleEntryGenerator.todayFormatted() }
            val invoiceNum = doc.optString("invoiceNum", "")
            val shiftNum = doc.optString("shiftNum", "")
            val supplierName = doc.optString("supplierName", "")
            val supplierCode = doc.optString("supplierCode", "")
            val supplierCategory = doc.optString("supplierCategory", "")
            val customerName = doc.optString("customerName", "")
            val customerCode = doc.optString("customerCode", "")
            val branchName = doc.optString("branchName", "")
            val branchCode = doc.optString("branchCode", "")
            val employeeName = doc.optString("employeeName", "")
            val employeeCode = doc.optString("employeeCode", "")
            val bank = doc.optString("bank", "")
            val chequeNum = doc.optString("chequeNum", "")
            val dueDate = doc.optString("dueDate", "")
            val condition = doc.optString("condition", "")
            val salesTax2372 = doc.optDouble("salesTax2372", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val customerTax1357 = doc.optDouble("customerTax1357", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val withholding1Pct = doc.optDouble("withholdingTax1Pct2361", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val goodReturns = doc.optDouble("goodReturns", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val damagedReturns = doc.optDouble("damagedReturns", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val discounts = doc.optDouble("discounts", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val taxes = doc.optDouble("taxes", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val visaCount = doc.optDouble("visaCount", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val visaNumber = doc.optString("visaNumber", "")
            val cashVariance = doc.optDouble("cashVariance", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val notes = doc.optString("notes", "")

            val isPaymentCheque = docType == "payment_cheque"
            val isReceivedCheque = docType == "received_cheque"

            val linesArray = doc.optJSONArray("lines")
            val entries = mutableListOf<JournalEntryEntity>()
            if (linesArray != null) {
                for (l in 0 until linesArray.length()) {
                    val lineObj = linesArray.optJSONObject(l) ?: continue
                    val debit = lineObj.optDouble("debit", 0.0).takeIf { !it.isNaN() } ?: 0.0
                    val credit = lineObj.optDouble("credit", 0.0).takeIf { !it.isNaN() } ?: 0.0
                    val applyDocMetrics = (l == 0)
                    entries.add(
                        JournalEntryEntity(
                            operationNum = opNum,
                            mainAccountName = lineObj.optString("mainAccountName", ""),
                            subAccountName = lineObj.optString("subAccountName", ""),
                            transName = lineObj.optString("transName", ""),
                            custCode = customerCode,
                            customerName = customerName,
                            suppCode = supplierCode,
                            supplierName = supplierName,
                            paymentInvoiceNum = invoiceNum,
                            employeeCode = employeeCode,
                            employeeName = employeeName,
                            description = lineObj.optString("description", summaryTitle),
                            shiftNum = shiftNum,
                            debit = debit,
                            credit = credit,
                            balanc = debit - credit,
                            condition = condition,
                            date = date,
                            paymentChequeNum = if (isPaymentCheque) chequeNum else "",
                            paymentChequeDueDate = if (isPaymentCheque) dueDate else "",
                            paymentChequeSupplierCode = if (isPaymentCheque) supplierCode else "",
                            paymentChequeSupplierName = if (isPaymentCheque) supplierName else "",
                            salesTax2372 = if (applyDocMetrics) salesTax2372 else 0.0,
                            customerTax1357 = if (applyDocMetrics) customerTax1357 else 0.0,
                            receivedChequeNum = if (isReceivedCheque) chequeNum else "",
                            receivedChequeDueDate = if (isReceivedCheque) dueDate else "",
                            receivedChequeCustomerCode = if (isReceivedCheque) customerCode else "",
                            receivedChequeCustomerName = if (isReceivedCheque) customerName else "",
                            withholdingTax1Pct2361 = if (applyDocMetrics) withholding1Pct else 0.0,
                            goodReturns = if (applyDocMetrics) goodReturns else 0.0,
                            damagedReturns = if (applyDocMetrics) damagedReturns else 0.0,
                            discounts = if (applyDocMetrics) discounts else 0.0,
                            taxes = if (applyDocMetrics) taxes else 0.0,
                            notes = notes,
                            visaCount = if (applyDocMetrics) visaCount else 0.0,
                            cashVariance = if (applyDocMetrics) cashVariance else 0.0,
                            visaNumber = visaNumber,
                            branchCode = branchCode,
                            branchName = branchName,
                            supplierCategory = supplierCategory,
                            bank = bank,
                            sourceType = sourceType.name,
                            sourceLabel = sourceLabel,
                        ).withComputedDerived()
                    )
                }
            }

            if (entries.isNotEmpty()) {
                drafts.add(
                    ExtractedDocumentDraft(
                        summaryTitle = summaryTitle,
                        documentType = docType,
                        entries = entries,
                    )
                )
            }
        }
        return drafts
    }
}
