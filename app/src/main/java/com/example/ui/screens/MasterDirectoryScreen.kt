package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.MasterRecordEntity
import com.example.model.MasterEntityType

@Composable
fun MasterDirectoryScreen(
    records: List<MasterRecordEntity>,
    onUpsertRecord: (MasterEntityType, String, String, String, String, Long) -> Unit,
    onDeleteRecord: (MasterRecordEntity) -> Unit,
    onGoToImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedType by remember { mutableStateOf(MasterEntityType.SUPPLIER) }
    var searchQuery by remember { mutableStateOf("") }
    var editingTarget by remember { mutableStateOf<MasterRecordEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = remember(records, selectedType, searchQuery) {
        val q = searchQuery.trim()
        records.filter {
            it.type == selectedType.name &&
                (q.isBlank() || it.name.contains(q, true) || it.code.contains(q, true) || it.category.contains(q, true))
        }
    }

    if (showAddDialog || editingTarget != null) {
        MasterRecordEditorDialog(
            type = selectedType,
            initial = editingTarget,
            onSave = { name, code, category, extra ->
                onUpsertRecord(selectedType, name, code, category, extra, editingTarget?.id ?: 0L)
                showAddDialog = false
                editingTarget = null
            },
            onDismiss = {
                showAddDialog = false
                editingTarget = null
            },
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("دليل الأسماء والحسابات", style = MaterialTheme.typography.titleLarge)
                Text(
                    "إجمالي السجلات: ${records.size} • يطابقها الذكاء الاصطناعي تلقائياً",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("directory_add_btn"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("إضافة")
            }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MasterEntityType.entries) { type ->
                val count = records.count { it.type == type.name }
                FilterChip(
                    selected = selectedType == type,
                    onClick = { selectedType = type },
                    label = { Text("${type.titleAr} ($count)") },
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().testTag("directory_search_input"),
            placeholder = { Text("بحث في ${selectedType.titleAr}...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "بحث") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        if (filtered.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("لا توجد سجلات في ${selectedType.titleAr} بعد", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "استورد ملف الإكسيل السابق لتعبئة الأسماء والأكواد تلقائياً أو أضفها يدوياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onGoToImport) {
                            Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("استيراد إكسيل")
                        }
                        Button(onClick = { showAddDialog = true }) {
                            Text("إضافة يدوية")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { "${it.type}_${it.id}_${it.normalizedName}" }) { rec ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (rec.code.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = rec.code,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.name, style = MaterialTheme.typography.titleSmall)
                                val meta = listOf(rec.category, rec.extra).filter { it.isNotBlank() }.joinToString(" • ")
                                if (meta.isNotBlank()) {
                                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { editingTarget = rec }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "تعديل")
                            }
                            IconButton(onClick = { onDeleteRecord(rec) }) {
                                Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MasterRecordEditorDialog(
    type: MasterEntityType,
    initial: MasterRecordEntity?,
    onSave: (String, String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var code by remember { mutableStateOf(initial?.code.orEmpty()) }
    var category by remember { mutableStateOf(initial?.category.orEmpty()) }
    var extra by remember { mutableStateOf(initial?.extra.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "إضافة إلى ${type.titleAr}" else "تعديل سجل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("الاسم المطابق للإكسيل *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("master_dialog_name_input"),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("الكود (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(if (type == MasterEntityType.SUB_ACCOUNT) "الحساب الرئيسي التابع له" else "التصنيف (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = extra,
                    onValueChange = { extra = it },
                    label = { Text("ملاحظات إضافية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, code, category, extra) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("master_dialog_save_btn"),
            ) {
                Text("حفظ")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
