package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.JournalEntryEntity
import com.example.domain.DoubleEntryGenerator
import com.example.model.JournalSchema

@Composable
fun ExportTemplateScreen(
    allEntries: List<JournalEntryEntity>,
    filteredEntries: List<JournalEntryEntity>,
    onExportToUri: (Uri, Boolean) -> Unit,
    onShareXlsx: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filteredOnly by remember { mutableStateOf(false) }
    val targetEntries = if (filteredOnly) filteredEntries else allEntries

    val createXlsxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ),
    ) { uri ->
        if (uri != null) {
            onExportToUri(uri, filteredOnly)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.TableView, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("تصدير شيت إكسيل XLSX جاهز", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "يتم التصدير بنفس ترتيب الـ 51 عموداً الحرفي في الملف المرفق، مع صف الإجماليات أعلى العناوين (SUBTOTAL)، معادلات حساب balanc تلقائياً، وورقة ثانية لدليل الموردين والحسابات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !filteredOnly,
                            onClick = { filteredOnly = false },
                            label = { Text("كل القيود (${allEntries.size})") },
                        )
                        FilterChip(
                            selected = filteredOnly,
                            onClick = { filteredOnly = true },
                            label = { Text("المفلترة فقط (${filteredEntries.size})") },
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val defaultName = "قيود_يومية_${DoubleEntryGenerator.todayFormatted()}.xlsx"
                                createXlsxLauncher.launch(defaultName)
                            },
                            enabled = targetEntries.isNotEmpty(),
                            modifier = Modifier.weight(1.2f).testTag("export_save_xlsx_btn"),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حفظ ملف XLSX")
                        }

                        OutlinedButton(
                            onClick = { onShareXlsx(filteredOnly) },
                            enabled = targetEntries.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("export_share_xlsx_btn"),
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("مشاركة")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "معاينة هيكل الأعمدة الـ 51 (${targetEntries.size} صف):",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(6.dp))
            val scrollState = rememberScrollState()
            val previewRows = targetEntries.take(8)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .border(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                // Row 1: Summary Totals
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))) {
                    JournalSchema.EXACT_HEADERS.indices.forEach { colIdx ->
                        val text = when (colIdx) {
                            14 -> formatMoney(targetEntries.sumOf { it.debit })
                            15 -> formatMoney(targetEntries.sumOf { it.credit })
                            16 -> formatMoney(targetEntries.sumOf { it.debit - it.credit })
                            else -> ""
                        }
                        GridCell(text = text, bold = true)
                    }
                }
                // Row 2: Exact Headers
                Row(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                    JournalSchema.EXACT_HEADERS.forEach { header ->
                        GridCell(
                            text = header,
                            bold = true,
                            textColor = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                // Data Rows
                previewRows.forEach { entry ->
                    val vals = entry.toColumnValues()
                    Row(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        vals.forEach { cell ->
                            GridCell(text = cell)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    text: String,
    bold: Boolean = false,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(128.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outline)
            .padding(horizontal = 8.dp, vertical = 7.dp),
    )
}
