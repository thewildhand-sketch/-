package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.JournalEntryEntity
import com.example.model.JournalSchema
import com.example.ui.theme.LedgerAmountStyle
import com.example.ui.theme.LocalLedgerColors

data class OperationGroup(
    val operationNum: String,
    val lines: List<JournalEntryEntity>,
    val totalDebit: Double,
    val totalCredit: Double,
    val primaryDate: String,
    val primarySummary: String,
    val branchName: String,
    val partyName: String,
)

@Composable
fun LedgerScreen(
    entries: List<JournalEntryEntity>,
    searchQuery: String,
    selectedMonth: String?,
    selectedBranch: String?,
    isExpandedWidth: Boolean,
    onSearchChange: (String) -> Unit,
    onMonthSelect: (String?) -> Unit,
    onBranchSelect: (String?) -> Unit,
    onEditOperation: (String) -> Unit,
    onDeleteOperation: (String) -> Unit,
    onGoToIntake: () -> Unit,
    onGoToExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val availableMonths = remember(entries) {
        entries.mapNotNull { it.month.takeIf { m -> m.isNotBlank() } }.distinct().sorted()
    }
    val availableBranches = remember(entries) {
        entries.mapNotNull { it.branchName.takeIf { b -> b.isNotBlank() } }.distinct().sorted()
    }

    val filtered = remember(entries, searchQuery, selectedMonth, selectedBranch) {
        val q = searchQuery.trim()
        entries.filter { e ->
            (selectedMonth.isNullOrBlank() || e.month == selectedMonth) &&
                (selectedBranch.isNullOrBlank() || e.branchName == selectedBranch) &&
                (q.isBlank() ||
                    e.operationNum.contains(q, true) ||
                    e.supplierName.contains(q, true) ||
                    e.customerName.contains(q, true) ||
                    e.mainAccountName.contains(q, true) ||
                    e.subAccountName.contains(q, true) ||
                    e.description.contains(q, true) ||
                    e.paymentInvoiceNum.contains(q, true) ||
                    e.shiftNum.contains(q, true))
        }
    }

    val grouped = remember(filtered) {
        filtered
            .groupBy { it.operationNum.ifBlank { "id_${it.id}" } }
            .map { (opKey, lines) ->
                val first = lines.first()
                OperationGroup(
                    operationNum = first.operationNum.ifBlank { "#${first.id}" },
                    lines = lines,
                    totalDebit = lines.sumOf { it.debit },
                    totalCredit = lines.sumOf { it.credit },
                    primaryDate = first.date,
                    primarySummary = first.description.ifBlank { first.transName.ifBlank { first.mainAccountName } },
                    branchName = first.branchName,
                    partyName = first.supplierName.ifBlank { first.customerName },
                )
            }
    }

    var selectedOpForDetail by remember { mutableStateOf<OperationGroup?>(null) }
    var pendingDeleteOp by remember { mutableStateOf<String?>(null) }

    if (pendingDeleteOp != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteOp = null },
            title = { Text("حذف العملية #${pendingDeleteOp}") },
            text = { Text("هل تريد حذف جميع سطور هذه العملية من الدفتر المحلي؟") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = pendingDeleteOp ?: return@Button
                        pendingDeleteOp = null
                        onDeleteOperation(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("حذف")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteOp = null }) {
                    Text("إلغاء")
                }
            },
        )
    }

    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LedgerSummaryBanner(
                    totalDebit = filtered.sumOf { it.debit },
                    totalCredit = filtered.sumOf { it.credit },
                    operationCount = grouped.size,
                    rowCount = filtered.size,
                    onGoToIntake = onGoToIntake,
                    onGoToExport = onGoToExport,
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ledger_search_input"),
                    placeholder = { Text("بحث برقم العملية، المورد، العميل، الحساب، الفاتورة...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "بحث") },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "مسح البحث")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }

            if (availableMonths.isNotEmpty() || availableBranches.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedMonth == null && selectedBranch == null,
                                onClick = {
                                    onMonthSelect(null)
                                    onBranchSelect(null)
                                },
                                label = { Text("الكل") },
                            )
                        }
                        items(availableMonths) { m ->
                            FilterChip(
                                selected = selectedMonth == m,
                                onClick = { onMonthSelect(if (selectedMonth == m) null else m) },
                                label = { Text("شهر $m") },
                            )
                        }
                        items(availableBranches) { b ->
                            FilterChip(
                                selected = selectedBranch == b,
                                onClick = { onBranchSelect(if (selectedBranch == b) null else b) },
                                label = { Text(b) },
                            )
                        }
                    }
                }
            }

            if (grouped.isEmpty()) {
                item {
                    EmptyLedgerCard(
                        hasAnyEntries = entries.isNotEmpty(),
                        onGoToIntake = onGoToIntake,
                    )
                }
            } else {
                items(grouped, key = { it.operationNum + "_" + it.lines.first().id }) { group ->
                    OperationGroupCard(
                        group = group,
                        isSelected = selectedOpForDetail?.operationNum == group.operationNum,
                        onSelectDetail = { selectedOpForDetail = group },
                        onEdit = { onEditOperation(group.operationNum) },
                        onDelete = { pendingDeleteOp = group.operationNum },
                    )
                }
            }
        }

        if (isExpandedWidth && selectedOpForDetail != null) {
            val detail = selectedOpForDetail!!
            Surface(
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight(),
                tonalElevation = 2.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                ColumnInspectorPane(group = detail, onClose = { selectedOpForDetail = null })
            }
        }
    }
}

@Composable
private fun LedgerSummaryBanner(
    totalDebit: Double,
    totalCredit: Double,
    operationCount: Int,
    rowCount: Int,
    onGoToIntake: () -> Unit,
    onGoToExport: () -> Unit,
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.84f),
        )
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "دفتر اليومية الموحد",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = "$operationCount عملية • $rowCount سطر قيد",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    )
                }
                BalanceStatusPill(debit = totalDebit, credit = totalCredit)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SummaryAmountBox(
                    label = "إجمالي المدين (debit)",
                    amount = formatMoney(totalDebit),
                    modifier = Modifier.weight(1f),
                )
                SummaryAmountBox(
                    label = "إجمالي الدائن (credit)",
                    amount = formatMoney(totalCredit),
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onGoToIntake,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ledger_quick_intake_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onPrimary,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إدخال / مسح جديد")
                }
                OutlinedButton(
                    onClick = onGoToExport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ledger_quick_export_btn"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تصدير XLSX")
                }
            }
        }
    }
}

@Composable
private fun SummaryAmountBox(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = amount,
                style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyLedgerCard(
    hasAnyEntries: Boolean,
    onGoToIntake: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (hasAnyEntries) Icons.Rounded.Search else Icons.Rounded.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = if (hasAnyEntries) "لا توجد نتائج مطابقة للبحث" else "دفتر اليومية فارغ حالياً",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = if (hasAnyEntries) {
                    "جرّب مسح الفلاتر أو البحث بكلمة أخرى."
                } else {
                    "ابدأ باستيراد ملف إكسيل السابق لتحميل أسماء الموردين والحسابات، أو التقط صور الفواتير وملفات الـ PDF لتوليد القيود تلقائياً."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!hasAnyEntries) {
                Button(
                    onClick = onGoToIntake,
                    modifier = Modifier.testTag("empty_ledger_start_btn"),
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("استيراد أو مسح فاتورة الآن")
                }
            }
        }
    }
}

@Composable
private fun OperationGroupCard(
    group: OperationGroup,
    isSelected: Boolean,
    onSelectDetail: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val ledgerColors = LocalLedgerColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("operation_card_${group.operationNum}")
            .clickable {
                expanded = !expanded
                onSelectDetail()
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else ledgerColors.borderSubtle,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = "#${group.operationNum}",
                            style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = group.primarySummary.ifBlank { "قيد محاسبي" },
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(
                            group.primaryDate.takeIf { it.isNotBlank() },
                            group.partyName.takeIf { it.isNotBlank() },
                            group.branchName.takeIf { it.isNotBlank() },
                        ).joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = "عرض السطور",
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "مدين: ${formatMoney(group.totalDebit)}",
                        style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        color = ledgerColors.debit,
                    )
                    Text(
                        text = "دائن: ${formatMoney(group.totalCredit)}",
                        style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        color = ledgerColors.credit,
                    )
                }
                BalanceStatusPill(debit = group.totalDebit, credit = group.totalCredit)
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = ledgerColors.borderSubtle)
                    Spacer(Modifier.height(8.dp))
                    group.lines.forEach { line ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val accTitle = listOf(line.mainAccountName, line.subAccountName)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" / ")
                                    .ifBlank { "حساب غير محدد" }
                                Text(
                                    text = accTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (line.description.isNotBlank()) {
                                    Text(
                                        text = line.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                if (line.debit > 0.0) {
                                    Text(
                                        text = "مدين ${formatMoney(line.debit)}",
                                        style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                                        color = ledgerColors.debit,
                                    )
                                }
                                if (line.credit > 0.0) {
                                    Text(
                                        text = "دائن ${formatMoney(line.credit)}",
                                        style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                                        color = ledgerColors.credit,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = onEdit,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تعديل الـ 51 عموداً")
                        }
                        TextButton(
                            onClick = onDelete,
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        ) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حذف")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnInspectorPane(
    group: OperationGroup,
    onClose: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("تفاصيل عملية #${group.operationNum}", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Clear, contentDescription = "إغلاق التفاصيل")
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.lines.forEachIndexed { lineIdx, line ->
                item {
                    Text(
                        text = "السطر ${lineIdx + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val values = line.toColumnValues()
                items(JournalSchema.EXACT_HEADERS.indices.toList()) { colIdx ->
                    val v = values.getOrElse(colIdx) { "" }
                    if (v.isNotBlank() && v != "0.00") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = JournalSchema.EXACT_HEADERS[colIdx],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = v,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }
    }
}
