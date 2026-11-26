package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.model.EntryTemplateKind
import com.example.model.JournalSchema
import com.example.model.JournalSourceType
import com.example.ui.IntakeUiState
import com.example.ui.PdfPreviewState
import java.io.File

@Composable
fun IntakeHubScreen(
    intakeState: IntakeUiState,
    masterCount: Int,
    onAnalyzeImages: (List<Uri>, JournalSourceType) -> Unit,
    onPdfPicked: (Uri, String) -> Unit,
    onTogglePdfPage: (Int) -> Unit,
    onSelectAllPdfPages: (Boolean) -> Unit,
    onAnalyzeSelectedPdf: () -> Unit,
    onSpreadsheetPicked: (Uri, String) -> Unit,
    onSelectPreviewSheet: (Int) -> Unit,
    onUpdateColumnMapping: (Int, Int?) -> Unit,
    onConfirmSpreadsheetImport: (Boolean) -> Unit,
    onImportRawText: (String) -> Unit,
    onStartManualTemplate: (EntryTemplateKind) -> Unit,
    onResetIntake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectedPhotos = remember { mutableStateListOf<Uri>() }
    var selectedImageSourceType by remember { mutableStateOf(JournalSourceType.INVOICE_PHOTO) }
    var rawTextInput by remember { mutableStateOf("") }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotos.addAll(uris)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            selectedPhotos.add(uri)
        }
        pendingCameraUri = null
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onPdfPicked(uri, resolveDisplayName(context, uri, "ملف_مجمع.pdf"))
        }
    }

    val spreadsheetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            onSpreadsheetPicked(uri, resolveDisplayName(context, uri, "بيانات_محاسبية.xlsx"))
        }
    }

    when (intakeState) {
        is IntakeUiState.Processing -> {
            BackHandler(enabled = false) {}
            ProcessingStateView(
                message = intakeState.message,
                currentStep = intakeState.currentStep,
                totalSteps = intakeState.totalSteps,
                modifier = modifier,
            )
        }

        is IntakeUiState.PdfSelection -> {
            BackHandler { onResetIntake() }
            PdfSelectionView(
                preview = intakeState.preview,
                onTogglePage = onTogglePdfPage,
                onSelectAll = onSelectAllPdfPages,
                onAnalyze = onAnalyzeSelectedPdf,
                onCancel = onResetIntake,
                modifier = modifier,
            )
        }

        is IntakeUiState.SpreadsheetPreview -> {
            BackHandler { onResetIntake() }
            SpreadsheetPreviewView(
                state = intakeState,
                onSelectSheet = onSelectPreviewSheet,
                onUpdateMapping = onUpdateColumnMapping,
                onImportAll = { onConfirmSpreadsheetImport(true) },
                onImportMasterOnly = { onConfirmSpreadsheetImport(false) },
                onCancel = onResetIntake,
                modifier = modifier,
            )
        }

        is IntakeUiState.Error -> {
            BackHandler { onResetIntake() }
            IntakeErrorView(
                message = intakeState.message,
                onBack = onResetIntake,
                modifier = modifier,
            )
        }

        is IntakeUiState.Idle, is IntakeUiState.ReviewBatch -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "مركز الإدخال والتحليل الذكي",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "البيانات المسجلة في الدليل المحلي حالياً: $masterCount سجل — سيتم استخدامها لتطابق أسماء الموردين والحسابات بدقة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                // 1. Excel / CSV / TSV Import
                item {
                    IntakeSectionCard(
                        icon = Icons.Rounded.TableChart,
                        title = "1. استيراد ملف إكسيل أو CSV / TSV",
                        subtitle = "استيراد ملفات الإكسيل السابقة لبناء دليل الموردين والحسابات أو استيراد قيود جاهزة.",
                    ) {
                        Button(
                            onClick = {
                                spreadsheetPickerLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/csv",
                                        "text/tab-separated-values",
                                        "text/plain",
                                        "*/*",
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("intake_pick_excel_btn"),
                        ) {
                            Icon(Icons.Rounded.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار ملف إكسيل / CSV / نص")
                        }
                    }
                }

                // 2. Photos of Invoices & Sales Shifts
                item {
                    IntakeSectionCard(
                        icon = Icons.Rounded.PhotoLibrary,
                        title = "2. صور الفواتير وشيفتات المبيعات",
                        subtitle = "التقط صوراً بالكاميرا أو اختر عدة صور لتحليلها وتوليد قيود مزدوجة متوازنة تلقائياً.",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedImageSourceType == JournalSourceType.INVOICE_PHOTO,
                                onClick = { selectedImageSourceType = JournalSourceType.INVOICE_PHOTO },
                                label = { Text("فواتير مشتريات") },
                            )
                            FilterChip(
                                selected = selectedImageSourceType == JournalSourceType.SALES_SHIFT,
                                onClick = { selectedImageSourceType = JournalSourceType.SALES_SHIFT },
                                label = { Text("شيفتات مبيعات") },
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    multiPhotoLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("intake_pick_photos_btn"),
                            ) {
                                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("معرض الصور")
                            }

                            OutlinedButton(
                                onClick = {
                                    val captureDir = File(context.cacheDir, "captures").apply { mkdirs() }
                                    val photoFile = File(captureDir, "capture_${System.currentTimeMillis()}.jpg")
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        photoFile,
                                    )
                                    pendingCameraUri = uri
                                    cameraLauncher.launch(uri)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("intake_camera_btn"),
                            ) {
                                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("تصوير بالكاميرا")
                            }
                        }

                        if (selectedPhotos.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(selectedPhotos.indices.toList()) { idx ->
                                    Box(modifier = Modifier.size(76.dp)) {
                                        AsyncImage(
                                            model = selectedPhotos[idx],
                                            contentDescription = "صورة مستند ${idx + 1}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(10.dp)),
                                        )
                                        IconButton(
                                            onClick = { selectedPhotos.removeAt(idx) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(24.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                    RoundedCornerShape(50),
                                                ),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Close,
                                                contentDescription = "إزالة الصورة",
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    val copy = selectedPhotos.toList()
                                    selectedPhotos.clear()
                                    onAnalyzeImages(copy, selectedImageSourceType)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("intake_analyze_photos_btn"),
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("تحليل (${selectedPhotos.size}) صورة وتوليد القيود")
                            }
                        }
                    }
                }

                // 3. Combined PDF with Invoices/Shifts
                item {
                    IntakeSectionCard(
                        icon = Icons.Rounded.PictureAsPdf,
                        title = "3. ملف PDF مجمّع به فواتير أو شيفتات",
                        subtitle = "استعراض صفحات الـ PDF وتحديد الصفحات المطلوبة لاستخراج كل فاتورة أو شيفت على حدة.",
                    ) {
                        Button(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("intake_pick_pdf_btn"),
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار ملف PDF مجمّع")
                        }
                    }
                }

                // 4. Raw Text / Other Formats + Manual Templates
                item {
                    IntakeSectionCard(
                        icon = Icons.Rounded.EditNote,
                        title = "4. لصق بيانات أو إدخال يدوي سريع",
                        subtitle = "الصق نص فاتورة، بيانات واتساب، أو جدول منسوخ (Tab/CSV) أو اختر قالباً يدوياً.",
                    ) {
                        OutlinedTextField(
                            value = rawTextInput,
                            onValueChange = { rawTextInput = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("intake_raw_text_input"),
                            minLines = 3,
                            maxLines = 6,
                            placeholder = { Text("الصق هنا نص الفاتورة أو صفوف جدول منسوخة...") },
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val text = rawTextInput
                                rawTextInput = ""
                                onImportRawText(text)
                            },
                            enabled = rawTextInput.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("intake_analyze_text_btn"),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("تحويل النص إلى قيد يومية")
                        }

                        Spacer(Modifier.height(10.dp))
                        Text("أو أنشئ قيداً يدوياً من قالب:", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(EntryTemplateKind.entries) { kind ->
                                OutlinedButton(
                                    onClick = { onStartManualTemplate(kind) },
                                    modifier = Modifier.testTag("manual_template_${kind.name}"),
                                ) {
                                    Text(kind.labelAr)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntakeSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ProcessingStateView(
    message: String,
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(44.dp))
                Text(message, style = MaterialTheme.typography.titleSmall)
                if (totalSteps > 0) {
                    LinearProgressIndicator(
                        progress = { currentStep.toFloat() / totalSteps.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "دفعة $currentStep من $totalSteps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfSelectionView(
    preview: PdfPreviewState,
    onTogglePage: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onAnalyze: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preview.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "عدد الصفحات: ${preview.totalPages} • المحدد: ${preview.selectedPages.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { onSelectAll(preview.selectedPages.size < preview.totalPages) }) {
                Text(if (preview.selectedPages.size < preview.totalPages) "تحديد الكل" else "إلغاء التحديد")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(preview.thumbnails, key = { it.pageIndex }) { thumb ->
                val isSelected = preview.selectedPages.contains(thumb.pageIndex)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTogglePage(thumb.pageIndex) },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    ),
                ) {
                    Box {
                        Image(
                            bitmap = thumb.bitmap.asImageBitmap(),
                            contentDescription = "صفحة ${thumb.pageIndex + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f),
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = { onTogglePage(thumb.pageIndex) })
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                "ص ${thumb.pageIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("إلغاء")
            }
            Button(
                onClick = onAnalyze,
                enabled = preview.selectedPages.isNotEmpty(),
                modifier = Modifier
                    .weight(2f)
                    .testTag("pdf_analyze_selected_btn"),
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("تحليل (${preview.selectedPages.size}) صفحة")
            }
        }
    }
}

@Composable
private fun SpreadsheetPreviewView(
    state: IntakeUiState.SpreadsheetPreview,
    onSelectSheet: (Int) -> Unit,
    onUpdateMapping: (Int, Int?) -> Unit,
    onImportAll: () -> Unit,
    onImportMasterOnly: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheet = state.workbook.sheets.getOrNull(state.selectedSheetIndex)
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("معاينة استيراد: ${state.workbook.fileName}", style = MaterialTheme.typography.titleMedium)

        if (state.workbook.sheets.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.workbook.sheets.indices.toList()) { idx ->
                    FilterChip(
                        selected = idx == state.selectedSheetIndex,
                        onClick = { onSelectSheet(idx) },
                        label = { Text(state.workbook.sheets[idx].sheetName) },
                    )
                }
            }
        }

        if (sheet != null) {
            Text(
                "تم ربط ${state.columnMapping.size} عمود تلقائياً من أصل ${sheet.headers.size} • عدد الصفوف: ${sheet.rows.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text("مطابقة الأعمدة (اضغط لتعديل أي عمود):", style = MaterialTheme.typography.labelLarge)
                }
                items(sheet.headers.indices.toList()) { srcIdx ->
                    val srcHeader = sheet.headers[srcIdx].ifBlank { "عمود ${srcIdx + 1}" }
                    val mappedIdx = state.columnMapping[srcIdx]
                    var menuOpen by remember { mutableStateOf(false) }

                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { menuOpen = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(srcHeader, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(
                                text = if (mappedIdx != null) JournalSchema.EXACT_HEADERS[mappedIdx] else "غير مربوط (تجاهل)",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (mappedIdx != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("تجاهل هذا العمود") },
                                onClick = {
                                    onUpdateMapping(srcIdx, null)
                                    menuOpen = false
                                },
                            )
                            JournalSchema.EXACT_HEADERS.forEachIndexed { schemaIdx, schemaName ->
                                DropdownMenuItem(
                                    text = { Text("${schemaIdx + 1}. $schemaName") },
                                    onClick = {
                                        onUpdateMapping(srcIdx, schemaIdx)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (sheet.rows.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(6.dp))
                        Text("عينة من الصفوف الأولى:", style = MaterialTheme.typography.labelLarge)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            sheet.rows.take(3).forEach { row ->
                                Card(
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.width(240.dp),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        row.take(6).forEachIndexed { cIdx, cell ->
                                            if (cell.isNotBlank()) {
                                                Text(
                                                    "${sheet.headers.getOrElse(cIdx) { "" }}: $cell",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onImportMasterOnly,
                    modifier = Modifier.weight(1f).testTag("import_master_only_btn"),
                ) {
                    Text("تحديث الدليل فقط")
                }
                Button(
                    onClick = onImportAll,
                    modifier = Modifier.weight(1f).testTag("import_all_entries_btn"),
                ) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استيراد الدليل والقيود")
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("إلغاء")
            }
        }
    }
}

@Composable
private fun IntakeErrorView(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                Text("تعذر إتمام العملية", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onBack) {
                    Text("العودة للمحاولة مجدداً")
                }
            }
        }
    }
}

private fun resolveDisplayName(context: Context, uri: Uri, fallback: String): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                return cursor.getString(idx) ?: fallback
            }
        }
    }
    return uri.lastPathSegment ?: fallback
}
