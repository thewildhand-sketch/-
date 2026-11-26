package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ExtractedDocumentDraft
import com.example.data.ai.GeminiDocumentExtractor
import com.example.data.ai.PdfPageProcessor
import com.example.data.ai.PdfPageThumbnail
import com.example.data.local.JournalEntryEntity
import com.example.data.local.LedgerRepository
import com.example.data.local.MasterRecordEntity
import com.example.data.spreadsheet.ParsedWorkbookResult
import com.example.data.spreadsheet.XlsxReader
import com.example.data.spreadsheet.XlsxWriter
import com.example.domain.DoubleEntryGenerator
import com.example.domain.EntityMatcher
import com.example.domain.MatchWarning
import com.example.model.EntryTemplateKind
import com.example.model.JournalSourceType
import com.example.model.MasterEntityType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LedgerViewModel"

data class PdfPreviewState(
    val uri: Uri,
    val displayName: String,
    val totalPages: Int,
    val thumbnails: List<PdfPageThumbnail>,
    val selectedPages: Set<Int>,
)

data class DraftOperationReview(
    val summaryTitle: String,
    val documentType: String,
    val operationNum: String,
    val replacingOperationNum: String? = null,
    val lines: List<JournalEntryEntity>,
    val warnings: List<MatchWarning>,
)

sealed interface IntakeUiState {
    data object Idle : IntakeUiState
    data class Processing(
        val message: String,
        val currentStep: Int = 0,
        val totalSteps: Int = 0,
    ) : IntakeUiState

    data class PdfSelection(val preview: PdfPreviewState) : IntakeUiState

    data class SpreadsheetPreview(
        val workbook: ParsedWorkbookResult,
        val selectedSheetIndex: Int,
        val columnMapping: Map<Int, Int>,
    ) : IntakeUiState

    data class ReviewBatch(
        val drafts: List<DraftOperationReview>,
        val currentIndex: Int = 0,
    ) : IntakeUiState

    data class Error(val message: String) : IntakeUiState
}

class LedgerViewModel(
    private val repository: LedgerRepository,
    private val aiExtractor: GeminiDocumentExtractor = GeminiDocumentExtractor(),
) : ViewModel() {

    val allEntries: StateFlow<List<JournalEntryEntity>> = repository.allEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMasterRecords: StateFlow<List<MasterRecordEntity>> = repository.allMasterRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _intakeState = MutableStateFlow<IntakeUiState>(IntakeUiState.Idle)
    val intakeState: StateFlow<IntakeUiState> = _intakeState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _monthFilter = MutableStateFlow<String?>(null)
    val monthFilter: StateFlow<String?> = _monthFilter.asStateFlow()

    private val _branchFilter = MutableStateFlow<String?>(null)
    val branchFilter: StateFlow<String?> = _branchFilter.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun setSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun setMonthFilter(month: String?) {
        _monthFilter.value = month
    }

    fun setBranchFilter(branch: String?) {
        _branchFilter.value = branch
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun resetIntakeState() {
        _intakeState.value = IntakeUiState.Idle
    }

    // --- Photo & Camera Extraction ---
    fun analyzeImageUris(
        context: Context,
        uris: List<Uri>,
        sourceType: JournalSourceType,
        sourceLabel: String = "صور مستندات",
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _intakeState.value = IntakeUiState.Processing("جاري تجهيز الصور للتحليل...", 0, uris.size)
            try {
                val masters = withContext(Dispatchers.IO) { repository.getAllMasterRecords() }
                var nextOp = withContext(Dispatchers.IO) { repository.getNextOperationNumber() }
                val allDrafts = mutableListOf<ExtractedDocumentDraft>()
                val batches = uris.chunked(2)

                batches.forEachIndexed { batchIdx, batchUris ->
                    _intakeState.value = IntakeUiState.Processing(
                        message = "جاري تحليل الدفعة ${batchIdx + 1} من ${batches.size} بالذكاء الاصطناعي...",
                        currentStep = batchIdx + 1,
                        totalSteps = batches.size,
                    )
                    val bitmaps = withContext(Dispatchers.IO) {
                        batchUris.map { PdfPageProcessor.decodeImageUriForAi(context, it) }
                    }
                    val extracted = aiExtractor.extractFromBitmaps(
                        bitmaps = bitmaps,
                        masterRecords = masters,
                        startingOpNum = nextOp,
                        sourceType = sourceType,
                        sourceLabel = sourceLabel,
                    )
                    allDrafts.addAll(extracted)
                    nextOp += maxOf(1, extracted.size)
                }

                if (allDrafts.isEmpty()) {
                    _intakeState.value = IntakeUiState.Error("لم يتم العثور على قيود أو فواتير واضحة في الصور المختارة.")
                    return@launch
                }

                openReviewFromExtracted(allDrafts, masters)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "analyzeImageUris failed", e)
                _intakeState.value = IntakeUiState.Error(e.localizedMessage ?: "حدث خطأ أثناء تحليل الصور.")
            }
        }
    }

    // --- Multi-Page PDF Preview & Extraction ---
    fun loadPdfForPreview(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch {
            _intakeState.value = IntakeUiState.Processing("جاري قراءة صفحات ملف PDF...")
            try {
                val preview = withContext(Dispatchers.IO) {
                    val total = PdfPageProcessor.getPdfPageCount(context, uri)
                    val thumbs = PdfPageProcessor.renderPdfThumbnails(context, uri)
                    PdfPreviewState(
                        uri = uri,
                        displayName = displayName,
                        totalPages = total,
                        thumbnails = thumbs,
                        selectedPages = (0 until total).toSet(),
                    )
                }
                _intakeState.value = IntakeUiState.PdfSelection(preview)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadPdfForPreview failed", e)
                _intakeState.value = IntakeUiState.Error(e.localizedMessage ?: "تعذر قراءة ملف PDF.")
            }
        }
    }

    fun togglePdfPageSelection(pageIndex: Int) {
        val current = (_intakeState.value as? IntakeUiState.PdfSelection)?.preview ?: return
        val nextSet = if (current.selectedPages.contains(pageIndex)) {
            current.selectedPages - pageIndex
        } else {
            current.selectedPages + pageIndex
        }
        _intakeState.value = IntakeUiState.PdfSelection(current.copy(selectedPages = nextSet))
    }

    fun selectAllPdfPages(selectAll: Boolean) {
        val current = (_intakeState.value as? IntakeUiState.PdfSelection)?.preview ?: return
        val nextSet = if (selectAll) (0 until current.totalPages).toSet() else emptySet()
        _intakeState.value = IntakeUiState.PdfSelection(current.copy(selectedPages = nextSet))
    }

    fun analyzeSelectedPdfPages(context: Context) {
        val current = (_intakeState.value as? IntakeUiState.PdfSelection)?.preview ?: return
        val pages = current.selectedPages.sorted()
        if (pages.isEmpty()) {
            _snackbarMessage.value = "يرجى اختيار صفحة واحدة على الأقل."
            return
        }
        viewModelScope.launch {
            try {
                val masters = withContext(Dispatchers.IO) { repository.getAllMasterRecords() }
                var nextOp = withContext(Dispatchers.IO) { repository.getNextOperationNumber() }
                val allDrafts = mutableListOf<ExtractedDocumentDraft>()
                val pageBatches = pages.chunked(2)

                pageBatches.forEachIndexed { batchIdx, batchPages ->
                    _intakeState.value = IntakeUiState.Processing(
                        message = "جاري استخراج القيود من صفحات PDF (${batchPages.first() + 1}..${batchPages.last() + 1} من ${current.totalPages})...",
                        currentStep = batchIdx + 1,
                        totalSteps = pageBatches.size,
                    )
                    val bitmaps = withContext(Dispatchers.IO) {
                        PdfPageProcessor.renderPdfPagesForAi(context, current.uri, batchPages)
                    }
                    val extracted = aiExtractor.extractFromBitmaps(
                        bitmaps = bitmaps,
                        masterRecords = masters,
                        startingOpNum = nextOp,
                        sourceType = JournalSourceType.PDF_BATCH,
                        sourceLabel = current.displayName,
                    )
                    allDrafts.addAll(extracted)
                    nextOp += maxOf(1, extracted.size)
                }

                if (allDrafts.isEmpty()) {
                    _intakeState.value = IntakeUiState.Error("لم يتم التعرف على فواتير أو شيفتات في صفحات الـ PDF المحددة.")
                    return@launch
                }

                openReviewFromExtracted(allDrafts, masters)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "analyzeSelectedPdfPages failed", e)
                _intakeState.value = IntakeUiState.Error(e.localizedMessage ?: "فشل تحليل ملف PDF.")
            }
        }
    }

    // --- Spreadsheet & Raw Text Import ---
    fun loadSpreadsheetUri(context: Context, uri: Uri, displayName: String) {
        viewModelScope.launch {
            _intakeState.value = IntakeUiState.Processing("جاري قراءة ملف البيانات ($displayName)...")
            try {
                val workbook = withContext(Dispatchers.IO) {
                    XlsxReader.readUri(context, uri, displayName)
                }
                if (workbook.sheets.isEmpty()) {
                    _intakeState.value = IntakeUiState.Error("الملف فارغ أو لا يحتوي على أوراق بيانات صالحة.")
                    return@launch
                }
                val firstSheet = workbook.sheets.first()
                _intakeState.value = IntakeUiState.SpreadsheetPreview(
                    workbook = workbook,
                    selectedSheetIndex = 0,
                    columnMapping = firstSheet.suggestedColumnMapping,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "loadSpreadsheetUri failed", e)
                _intakeState.value = IntakeUiState.Error(e.localizedMessage ?: "تعذر قراءة ملف الإكسيل.")
            }
        }
    }

    fun selectPreviewSheet(sheetIndex: Int) {
        val current = _intakeState.value as? IntakeUiState.SpreadsheetPreview ?: return
        val sheet = current.workbook.sheets.getOrNull(sheetIndex) ?: return
        _intakeState.value = current.copy(
            selectedSheetIndex = sheetIndex,
            columnMapping = sheet.suggestedColumnMapping,
        )
    }

    fun updatePreviewColumnMapping(sourceColIndex: Int, targetSchemaColIndex: Int?) {
        val current = _intakeState.value as? IntakeUiState.SpreadsheetPreview ?: return
        val mutable = current.columnMapping.toMutableMap()
        if (targetSchemaColIndex == null) {
            mutable.remove(sourceColIndex)
        } else {
            mutable[sourceColIndex] = targetSchemaColIndex
        }
        _intakeState.value = current.copy(columnMapping = mutable)
    }

    fun confirmSpreadsheetImport(importJournalRows: Boolean) {
        val current = _intakeState.value as? IntakeUiState.SpreadsheetPreview ?: return
        val sheet = current.workbook.sheets.getOrNull(current.selectedSheetIndex) ?: return
        viewModelScope.launch {
            _intakeState.value = IntakeUiState.Processing("جاري استيراد البيانات وحفظها محلياً...")
            try {
                val (importedEntriesCount, masterUpdatedCount) = withContext(Dispatchers.IO) {
                    val entries = XlsxReader.convertRowsToEntries(
                        rows = sheet.rows,
                        columnMapping = current.columnMapping,
                        sourceLabel = current.workbook.fileName,
                    )
                    val mastersFromAllSheets = current.workbook.sheets
                        .flatMap { XlsxReader.extractMasterRecordsFromGenericSheet(it) }
                    val mastersSaved = repository.bulkUpsertMasterRecords(mastersFromAllSheets)

                    val savedEntriesCount = if (importJournalRows && entries.isNotEmpty()) {
                        var nextOp = repository.getNextOperationNumber()
                        val normalized = entries.map { row ->
                            if (row.operationNum.isBlank()) {
                                row.copy(operationNum = (nextOp++).toString())
                            } else row
                        }
                        repository.saveJournalOperation(normalized)
                        normalized.size
                    } else 0

                    savedEntriesCount to mastersSaved
                }

                _intakeState.value = IntakeUiState.Idle
                _snackbarMessage.value = if (importJournalRows) {
                    "تم استيراد $importedEntriesCount قيد وتحديث $masterUpdatedCount سجل في الدليل"
                } else {
                    "تم تحديث الدليل بـ $masterUpdatedCount سجل من ملف الإكسيل"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "confirmSpreadsheetImport failed", e)
                _intakeState.value = IntakeUiState.Error(e.localizedMessage ?: "فشل استيراد البيانات.")
            }
        }
    }

    fun importRawTextInput(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            _snackbarMessage.value = "يرجى لصق نص أو جدول أولاً."
            return
        }
        val lines = trimmed.lines().filter { it.isNotBlank() }
        val looksTabular = lines.size >= 2 && (lines.first().contains('\t') || lines.first().count { it == ',' } >= 3)
        if (looksTabular) {
            val workbook = XlsxReader.parseDelimitedText(trimmed, "بيانات جدولية ملصوقة")
            val firstSheet = workbook.sheets.firstOrNull()
            if (firstSheet != null && firstSheet.suggestedColumnMapping.size >= 2) {
                _intakeState.value = IntakeUiState.SpreadsheetPreview(
                    workbook = workbook,
                    selectedSheetIndex = 0,
                    columnMapping = firstSheet.suggestedColumnMapping,
                )
                return
            }
        }

        viewModelScope.launch {
            _intakeState.value = IntakeUiState.Processing("جاري تحويل النص إلى قيود يومية متوازنة...")
            try {
                val masters = withContext(Dispatchers.IO) { repository.getAllMasterRecords() }
                val nextOp = withContext(Dispatchers.IO) { repository.getNextOperationNumber() }
                val drafts = aiExtractor.extractFromRawText(trimmed, masters, nextOp)
                if (drafts.isEmpty()) {
                    _intakeState.value = IntakeUiState.Error("تعذر استخراج قيود واضحة من النص المدخل.")
                    return@launch
                }
                openReviewFromExtracted(drafts, masters)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "importRawTextInput failed", e)
                _intakeState.value = IntakeUiState.Error(e.localizedMessage ?: "فشل تحليل النص.")
            }
        }
    }

    // --- Manual & Edit Flows ---
    fun startManualTemplate(kind: EntryTemplateKind) {
        viewModelScope.launch {
            val nextOp = withContext(Dispatchers.IO) { repository.getNextOperationNumber() }.toString()
            val lines = DoubleEntryGenerator.buildTemplateLines(kind, nextOp)
            _intakeState.value = IntakeUiState.ReviewBatch(
                drafts = listOf(
                    DraftOperationReview(
                        summaryTitle = "${kind.labelAr} (عملية #$nextOp)",
                        documentType = kind.name,
                        operationNum = nextOp,
                        lines = lines,
                        warnings = emptyList(),
                    )
                ),
                currentIndex = 0,
            )
        }
    }

    fun openOperationForEdit(operationNum: String) {
        val opLines = allEntries.value.filter { it.operationNum == operationNum }
        if (opLines.isEmpty()) return
        _intakeState.value = IntakeUiState.ReviewBatch(
            drafts = listOf(
                DraftOperationReview(
                    summaryTitle = "تعديل عملية #$operationNum",
                    documentType = opLines.first().transName.ifBlank { "قيد يومية" },
                    operationNum = operationNum,
                    replacingOperationNum = operationNum,
                    lines = opLines,
                    warnings = emptyList(),
                )
            ),
            currentIndex = 0,
        )
    }

    private suspend fun openReviewFromExtracted(
        extracted: List<ExtractedDocumentDraft>,
        masters: List<MasterRecordEntity>,
    ) {
        val reviews = extracted.map { draft ->
            val matched = EntityMatcher.resolveDraftAgainstMaster(draft.entries, masters)
            val opNum = matched.entries.firstOrNull()?.operationNum.orEmpty()
            DraftOperationReview(
                summaryTitle = draft.summaryTitle,
                documentType = draft.documentType,
                operationNum = opNum,
                lines = matched.entries,
                warnings = matched.warnings,
            )
        }
        _intakeState.value = IntakeUiState.ReviewBatch(drafts = reviews, currentIndex = 0)
    }

    // --- Review Sheet Mutations ---
    fun selectReviewDraftIndex(index: Int) {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        if (index in batch.drafts.indices) {
            _intakeState.value = batch.copy(currentIndex = index)
        }
    }

    fun updateDraftLine(lineIndex: Int, transform: (JournalEntryEntity) -> JournalEntryEntity) {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return
        val updatedLines = draft.lines.mapIndexed { idx, line ->
            if (idx == lineIndex) transform(line).withComputedDerived() else line
        }
        val updatedDraft = draft.copy(lines = updatedLines)
        val updatedDrafts = batch.drafts.toMutableList().apply { set(batch.currentIndex, updatedDraft) }
        _intakeState.value = batch.copy(drafts = updatedDrafts)
    }

    fun addDraftLine() {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return
        val base = draft.lines.firstOrNull()
        val newLine = JournalEntryEntity(
            operationNum = draft.operationNum,
            date = base?.date ?: DoubleEntryGenerator.todayFormatted(),
            branchCode = base?.branchCode.orEmpty(),
            branchName = base?.branchName.orEmpty(),
            sourceType = base?.sourceType ?: JournalSourceType.MANUAL.name,
            sourceLabel = base?.sourceLabel.orEmpty(),
        ).withComputedDerived()
        val updatedDraft = draft.copy(lines = draft.lines + newLine)
        val updatedDrafts = batch.drafts.toMutableList().apply { set(batch.currentIndex, updatedDraft) }
        _intakeState.value = batch.copy(drafts = updatedDrafts)
    }

    fun removeDraftLine(lineIndex: Int) {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return
        if (draft.lines.size <= 1) return
        val updatedLines = draft.lines.filterIndexed { idx, _ -> idx != lineIndex }
        val updatedDraft = draft.copy(lines = updatedLines)
        val updatedDrafts = batch.drafts.toMutableList().apply { set(batch.currentIndex, updatedDraft) }
        _intakeState.value = batch.copy(drafts = updatedDrafts)
    }

    fun autoBalanceCurrentDraft() {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return
        val balanced = DoubleEntryGenerator.autoBalance(draft.lines, draft.operationNum)
        val updatedDraft = draft.copy(lines = balanced)
        val updatedDrafts = batch.drafts.toMutableList().apply { set(batch.currentIndex, updatedDraft) }
        _intakeState.value = batch.copy(drafts = updatedDrafts)
    }

    fun applyMasterToWarning(warning: MatchWarning, selectedMaster: MasterRecordEntity) {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return
        val updatedLines = draft.lines.mapIndexed { idx, line ->
            if (idx != warning.lineIndex) line else when (warning.type) {
                MasterEntityType.SUPPLIER -> line.copy(
                    supplierName = selectedMaster.name,
                    suppCode = selectedMaster.code.ifBlank { line.suppCode },
                    supplierCategory = selectedMaster.category.ifBlank { line.supplierCategory },
                )
                MasterEntityType.CUSTOMER -> line.copy(
                    customerName = selectedMaster.name,
                    custCode = selectedMaster.code.ifBlank { line.custCode },
                    clientAr = selectedMaster.name,
                )
                MasterEntityType.MAIN_ACCOUNT -> line.copy(mainAccountName = selectedMaster.name)
                MasterEntityType.SUB_ACCOUNT -> line.copy(subAccountName = selectedMaster.name)
                MasterEntityType.TRANS_NAME -> line.copy(transName = selectedMaster.name)
                MasterEntityType.EMPLOYEE -> line.copy(
                    employeeName = selectedMaster.name,
                    employeeCode = selectedMaster.code.ifBlank { line.employeeCode },
                )
                MasterEntityType.BRANCH -> line.copy(
                    branchName = selectedMaster.name,
                    branchCode = selectedMaster.code.ifBlank { line.branchCode },
                )
                MasterEntityType.BANK -> line.copy(bank = selectedMaster.name)
            }.withComputedDerived()
        }
        val remainingWarnings = draft.warnings.filterNot { it == warning }
        val updatedDraft = draft.copy(lines = updatedLines, warnings = remainingWarnings)
        val updatedDrafts = batch.drafts.toMutableList().apply { set(batch.currentIndex, updatedDraft) }
        _intakeState.value = batch.copy(drafts = updatedDrafts)
    }

    fun addWarningAsNewMaster(warning: MatchWarning) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.upsertMasterRecord(
                    type = warning.type,
                    name = warning.extractedName,
                    code = warning.extractedCode,
                )
            }
            val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return@launch
            val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return@launch
            val remaining = draft.warnings.filterNot { it == warning }
            val updatedDraft = draft.copy(warnings = remaining)
            val updatedDrafts = batch.drafts.toMutableList().apply { set(batch.currentIndex, updatedDraft) }
            _intakeState.value = batch.copy(drafts = updatedDrafts)
            _snackbarMessage.value = "تمت إضافة \"${warning.extractedName}\" إلى الدليل"
        }
    }

    fun saveCurrentDraftAndAdvance() {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                draft.replacingOperationNum?.let { repository.deleteOperation(it) }
                repository.saveJournalOperation(draft.lines.map { it.copy(id = 0L) })
            }
            val remainingDrafts = batch.drafts.filterIndexed { idx, _ -> idx != batch.currentIndex }
            if (remainingDrafts.isEmpty()) {
                _intakeState.value = IntakeUiState.Idle
                _snackbarMessage.value = "تم حفظ القيد (عملية #${draft.operationNum}) في الدفتر"
            } else {
                val nextIdx = batch.currentIndex.coerceAtMost(remainingDrafts.lastIndex)
                _intakeState.value = IntakeUiState.ReviewBatch(remainingDrafts, nextIdx)
                _snackbarMessage.value = "تم حفظ عملية #${draft.operationNum} — متبقي ${remainingDrafts.size}"
            }
        }
    }

    fun saveAllDrafts() {
        val batch = _intakeState.value as? IntakeUiState.ReviewBatch ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (draft in batch.drafts) {
                    draft.replacingOperationNum?.let { repository.deleteOperation(it) }
                    repository.saveJournalOperation(draft.lines.map { it.copy(id = 0L) })
                }
            }
            val count = batch.drafts.size
            _intakeState.value = IntakeUiState.Idle
            _snackbarMessage.value = "تم حفظ جميع القيود ($count عملية) بنجاح"
        }
    }

    // --- Ledger & Master CRUD ---
    fun deleteOperation(operationNum: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteOperation(operationNum) }
            _snackbarMessage.value = "تم حذف العملية #$operationNum"
        }
    }

    fun upsertMasterRecord(
        type: MasterEntityType,
        name: String,
        code: String,
        category: String,
        extra: String,
        existingId: Long = 0L,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.upsertMasterRecord(type, name, code, category, extra, existingId)
            }
            _snackbarMessage.value = "تم حفظ \"${name.trim()}\" في الدليل"
        }
    }

    fun deleteMasterRecord(record: MasterRecordEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteMasterRecord(record) }
        }
    }

    // --- Export XLSX ---
    fun filteredEntries(): List<JournalEntryEntity> {
        val q = _searchQuery.value.trim()
        val m = _monthFilter.value
        val b = _branchFilter.value
        return allEntries.value.filter { e ->
            val matchMonth = m.isNullOrBlank() || e.month == m
            val matchBranch = b.isNullOrBlank() || e.branchName == b
            val matchQuery = q.isBlank() ||
                e.operationNum.contains(q, ignoreCase = true) ||
                e.supplierName.contains(q, ignoreCase = true) ||
                e.customerName.contains(q, ignoreCase = true) ||
                e.mainAccountName.contains(q, ignoreCase = true) ||
                e.subAccountName.contains(q, ignoreCase = true) ||
                e.description.contains(q, ignoreCase = true) ||
                e.paymentInvoiceNum.contains(q, ignoreCase = true) ||
                e.shiftNum.contains(q, ignoreCase = true)
            matchMonth && matchBranch && matchQuery
        }
    }

    fun exportXlsxToUri(context: Context, uri: Uri, filteredOnly: Boolean) {
        viewModelScope.launch {
            try {
                val entries = if (filteredOnly) filteredEntries() else withContext(Dispatchers.IO) { repository.getAllEntriesAsc() }
                val masters = withContext(Dispatchers.IO) { repository.getAllMasterRecords() }
                withContext(Dispatchers.IO) {
                    XlsxWriter.writeToUri(context, uri, entries, masters)
                }
                _snackbarMessage.value = "تم تصدير شيت الإكسيل (${entries.size} سطر) بنجاح"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "exportXlsxToUri failed", e)
                _snackbarMessage.value = "فشل تصدير ملف الإكسيل: ${e.localizedMessage}"
            }
        }
    }

    fun shareXlsxFile(context: Context, filteredOnly: Boolean) {
        viewModelScope.launch {
            try {
                val entries = if (filteredOnly) filteredEntries() else withContext(Dispatchers.IO) { repository.getAllEntriesAsc() }
                val masters = withContext(Dispatchers.IO) { repository.getAllMasterRecords() }
                val fileName = "قيود_يومية_${DoubleEntryGenerator.todayFormatted()}.xlsx"
                val file = withContext(Dispatchers.IO) {
                    XlsxWriter.writeToCacheFile(context, fileName, entries, masters)
                }
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_SUBJECT, fileName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "مشاركة ملف الإكسيل XLSX"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "shareXlsxFile failed", e)
                _snackbarMessage.value = "تعذر مشاركة الملف: ${e.localizedMessage}"
            }
        }
    }

    class Factory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LedgerViewModel(repository) as T
        }
    }
}
