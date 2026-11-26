package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.local.JournalEntryEntity
import com.example.data.local.MasterRecordEntity
import com.example.domain.DoubleEntryGenerator
import com.example.model.JournalSourceType
import com.example.model.MasterEntityType
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "GeminiDocExtractor"
private const val GEMINI_MODEL = "gemini-2.5-flash"
private const val GEMINI_ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

data class ExtractedDocumentDraft(
    val summaryTitle: String,
    val documentType: String,
    val entries: List<JournalEntryEntity>,
)

class GeminiDocumentExtractor {

    private fun requireApiKey(): String {
        val key = BuildConfig.GEMINI_API_KEY.trim()
        if (key.isEmpty() ||
            key == "GEMINI_API_KEY_DEFAULT_VALUE" ||
            key == "YOUR_GEMINI_API_KEY"
        ) {
            throw IllegalStateException(
                "مفتاح GEMINI_API_KEY غير مهيأ. أضف مفتاح Gemini API في لوحة Secrets داخل AI Studio للمتابعة."
            )
        }
        return key
    }

    private fun buildResponseSchema(): JSONObject {
        fun stringProp(description: String) = JSONObject()
            .put("type", "STRING")
            .put("description", description)

        fun numberProp(description: String) = JSONObject()
            .put("type", "NUMBER")
            .put("description", description)

        val lineProperties = JSONObject()
            .put("mainAccountName", stringProp("Main account name matched from master list when available"))
            .put("subAccountName", stringProp("Sub account name matched from master list when available"))
            .put("transName", stringProp("Transaction type name (Trans. Name)"))
            .put("description", stringProp("Clear Arabic description (بيان) for this journal line"))
            .put("debit", numberProp("Debit amount (>= 0)"))
            .put("credit", numberProp("Credit amount (>= 0)"))

        val lineSchema = JSONObject()
            .put("type", "OBJECT")
            .put("properties", lineProperties)
            .put(
                "required",
                JSONArray(listOf("mainAccountName", "subAccountName", "transName", "description", "debit", "credit"))
            )

        val docProperties = JSONObject()
            .put("documentType", stringProp("One of: purchase_invoice, sales_shift, payment_cheque, received_cheque, return_note, general_entry"))
            .put("summaryTitle", stringProp("Concise Arabic title summarizing party, invoice/shift number, and total"))
            .put("date", stringProp("Document date in YYYY-MM-DD format"))
            .put("invoiceNum", stringProp("Invoice number if present"))
            .put("shiftNum", stringProp("Sales shift number if present"))
            .put("supplierName", stringProp("Exact supplier name from master list if present"))
            .put("supplierCode", stringProp("Supplier code if known"))
            .put("supplierCategory", stringProp("Supplier classification if known"))
            .put("customerName", stringProp("Exact customer name from master list if present"))
            .put("customerCode", stringProp("Customer code if known"))
            .put("branchName", stringProp("Branch name if present"))
            .put("branchCode", stringProp("Branch code if known"))
            .put("employeeName", stringProp("Employee or cashier name if present"))
            .put("employeeCode", stringProp("Employee code if known"))
            .put("bank", stringProp("Bank name if present"))
            .put("chequeNum", stringProp("Cheque number if present"))
            .put("dueDate", stringProp("Cheque due date YYYY-MM-DD if present"))
            .put("condition", stringProp("Payment condition e.g. نقدي or آجل or فيزا or شيك"))
            .put("salesTax2372", numberProp("Sales/VAT tax amount"))
            .put("customerTax1357", numberProp("Customer tax amount"))
            .put("withholdingTax1Pct2361", numberProp("1% withholding tax amount"))
            .put("goodReturns", numberProp("Good returns amount"))
            .put("damagedReturns", numberProp("Damaged returns amount"))
            .put("discounts", numberProp("Discounts amount"))
            .put("taxes", numberProp("Total taxes amount"))
            .put("visaCount", numberProp("Count or total of Visa transactions"))
            .put("visaNumber", stringProp("Visa slip or terminal number"))
            .put("cashVariance", numberProp("Cash over/short variance (عجز / زيادة)"))
            .put("notes", stringProp("Additional remarks in Arabic"))
            .put(
                "lines",
                JSONObject()
                    .put("type", "ARRAY")
                    .put("description", "Balanced double-entry lines where sum(debit) == sum(credit)")
                    .put("items", lineSchema)
            )

        val docSchema = JSONObject()
            .put("type", "OBJECT")
            .put("properties", docProperties)
            .put("required", JSONArray(listOf("documentType", "summaryTitle", "date", "lines")))

        return JSONObject()
            .put("type", "OBJECT")
            .put(
                "properties",
                JSONObject().put(
                    "documents",
                    JSONObject()
                        .put("type", "ARRAY")
                        .put("description", "All distinct invoices, sales shifts, or cheques detected across the provided pages/images")
                        .put("items", docSchema)
                )
            )
            .put("required", JSONArray(listOf("documents")))
    }

    suspend fun extractFromBitmaps(
        bitmaps: List<Bitmap>,
        masterRecords: List<MasterRecordEntity>,
        startingOpNum: Int,
        sourceType: JournalSourceType,
        sourceLabel: String,
    ): List<ExtractedDocumentDraft> {
        if (bitmaps.isEmpty()) return emptyList()
        val apiKey = requireApiKey()
        val promptText = buildSystemPrompt(masterRecords)

        return try {
            val jsonText = withTimeout(60_000L) {
                withContext(Dispatchers.IO) {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", promptText))
                        bitmaps.forEach { bmp ->
                            val baos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                            put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject()
                                        .put("mimeType", "image/jpeg")
                                        .put("data", base64)
                                )
                            )
                        }
                    }
                    callGeminiRest(apiKey, parts)
                }
            }
            if (jsonText.isBlank()) {
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
        val apiKey = requireApiKey()
        val promptText = buildSystemPrompt(masterRecords) + "\n\nالنص المراد تحويله لقيود يومية:\n" + rawText

        return try {
            val jsonText = withTimeout(60_000L) {
                withContext(Dispatchers.IO) {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", promptText))
                    }
                    callGeminiRest(apiKey, parts)
                }
            }
            if (jsonText.isBlank()) {
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

    private fun callGeminiRest(apiKey: String, parts: JSONArray): String {
        val payload = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", parts)
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.1)
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", buildResponseSchema())
            )

        val connection = (URL(GEMINI_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 55_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        return try {
            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                val apiError = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                val friendly = when (statusCode) {
                    400 -> "طلب غير صالح لنموذج Gemini: ${apiError.ifBlank { body.take(160) }}"
                    401, 403 -> "مفتاح Gemini API غير صالح أو لا يملك الصلاحية المطلوبة. تحقق من GEMINI_API_KEY في لوحة Secrets."
                    429 -> "تم تجاوز حد الطلبات المؤقت لخدمة Gemini. حاول مرة أخرى بعد لحظات."
                    else -> "خطأ من خدمة Gemini ($statusCode): ${apiError.ifBlank { body.take(160) }}"
                }
                throw IllegalStateException(friendly)
            }

            val responseJson = JSONObject(body)
            val candidates = responseJson.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val respParts = content?.optJSONArray("parts")
            respParts?.optJSONObject(0)?.optString("text").orEmpty()
        } finally {
            connection.disconnect()
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
