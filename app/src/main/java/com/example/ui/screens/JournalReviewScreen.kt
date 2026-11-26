package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.JournalEntryEntity
import com.example.data.local.MasterRecordEntity
import com.example.domain.DoubleEntryGenerator
import com.example.domain.MatchWarning
import com.example.model.MasterEntityType
import com.example.ui.DraftOperationReview
import com.example.ui.IntakeUiState
import com.example.ui.theme.LedgerAmountStyle
import com.example.ui.theme.LocalLedgerColors
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalReviewScreen(
    batch: IntakeUiState.ReviewBatch,
    masterRecords: List<MasterRecordEntity>,
    onSelectDraftIndex: (Int) -> Unit,
    onUpdateLine: (Int, (JournalEntryEntity) -> JournalEntryEntity) -> Unit,
    onAddLine: () -> Unit,
    onRemoveLine: (Int) -> Unit,
    onAutoBalance: () -> Unit,
    onPickMasterForWarning: (MatchWarning, MasterRecordEntity) -> Unit,
    onAddWarningAsMaster: (MatchWarning) -> Unit,
    onSaveCurrentAndAdvance: () -> Unit,
    onSaveAll: () -> Unit,
    onDiscard: () -> Unit,
) {
    BackHandler { onDiscard() }

    val draft = batch.drafts.getOrNull(batch.currentIndex) ?: return
    val totalDebit = DoubleEntryGenerator.totalDebit(draft.lines)
    val totalCredit = DoubleEntryGenerator.totalCredit(draft.lines)
    val isBalanced = abs(totalDebit - totalCredit) < 0.01 && totalDebit > 0.0
    val ledgerColors = LocalLedgerColors.current

    var pickerRequest by remember { mutableStateOf<Pair<Int, MasterEntityType>?>(null) }
    var warningPickerTarget by remember { mutableStateOf<MatchWarning?>(null) }

    if (pickerRequest != null) {
        val (lineIdx, entityType) = pickerRequest!!
        MasterRecordPickerDialog(
            title = "اختيار من دليل ${entityType.titleAr}",
            records = masterRecords.filter { it.type == entityType.name },
            onSelect = { selected ->
                onUpdateLine(lineIdx) { line ->
                    when (entityType) {
                        MasterEntityType.SUPPLIER -> line.copy(
                            supplierName = selected.name,
                            suppCode = selected.code.ifBlank { line.suppCode },
                            supplierCategory = selected.category.ifBlank { line.supplierCategory },
                        )
                        MasterEntityType.CUSTOMER -> line.copy(
                            customerName = selected.name,
                            custCode = selected.code.ifBlank { line.custCode },
                            clientAr = selected.name,
                        )
                        MasterEntityType.MAIN_ACCOUNT -> line.copy(mainAccountName = selected.name)
                        MasterEntityType.SUB_ACCOUNT -> line.copy(subAccountName = selected.name)
                        MasterEntityType.TRANS_NAME -> line.copy(transName = selected.name)
                        MasterEntityType.EMPLOYEE -> line.copy(
                            employeeName = selected.name,
                            employeeCode = selected.code.ifBlank { line.employeeCode },
                        )
                        MasterEntityType.BRANCH -> line.copy(
                            branchName = selected.name,
                            branchCode = selected.code.ifBlank { line.branchCode },
                        )
                        MasterEntityType.BANK -> line.copy(bank = selected.name)
                    }
                }
                pickerRequest = null
            },
            onDismiss = { pickerRequest = null },
        )
    }

    if (warningPickerTarget != null) {
        val w = warningPickerTarget!!
        MasterRecordPickerDialog(
            title = "تطابق ${w.type.titleAr} (${w.extractedName})",
            records = masterRecords.filter { it.type == w.type.name },
            onSelect = { selected ->
                onPickMasterForWarning(w, selected)
                warningPickerTarget = null
            },
            onDismiss = { warningPickerTarget = null },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = draft.summaryTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "عملية #${draft.operationNum} • مراجعة قبل الحفظ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDiscard, modifier = Modifier.testTag("review_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (batch.drafts.size > 1) {
                        TextButton(
                            onClick = onSaveAll,
                            modifier = Modifier.testTag("review_save_all_btn"),
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("حفظ الكل (${batch.drafts.size})")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onAddLine,
                        modifier = Modifier.weight(1f).testTag("review_add_line_btn"),
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة سطر")
                    }
                    Button(
                        onClick = onSaveCurrentAndAdvance,
                        modifier = Modifier.weight(1.4f).testTag("review_save_btn"),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (batch.drafts.size > 1) "حفظ والانتقال للتالي" else "حفظ في الدفتر")
                    }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (batch.drafts.size > 1) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(batch.drafts) { idx, itemDraft ->
                            FilterChip(
                                selected = idx == batch.currentIndex,
                                onClick = { onSelectDraftIndex(idx) },
                                label = { Text("${idx + 1}. #${itemDraft.operationNum}") },
                            )
                        }
                    }
                }
            }

            // Live Balance Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isBalanced) ledgerColors.debitBg.copy(alpha = 0.45f)
                        else ledgerColors.warningBg.copy(alpha = 0.65f),
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isBalanced) ledgerColors.debit.copy(alpha = 0.4f) else ledgerColors.warning,
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("توازن القيد المزدوج", style = MaterialTheme.typography.titleSmall)
                            BalanceStatusPill(debit = totalDebit, credit = totalCredit)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "إجمالي المدين: ${formatMoney(totalDebit)}",
                                style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                color = ledgerColors.debit,
                            )
                            Text(
                                "إجمالي الدائن: ${formatMoney(totalCredit)}",
                                style = LedgerAmountStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                color = ledgerColors.credit,
                            )
                        }
                        if (!isBalanced && abs(totalDebit - totalCredit) >= 0.01) {
                            OutlinedButton(
                                onClick = onAutoBalance,
                                modifier = Modifier.fillMaxWidth().testTag("review_auto_balance_btn"),
                            ) {
                                Icon(Icons.Rounded.Balance, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("موازنة القيد تلقائياً بإضافة سطر متمم (${formatMoney(abs(totalDebit - totalCredit))})")
                            }
                        }
                    }
                }
            }

            // Unmatched Entity Warnings
            if (draft.warnings.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ledgerColors.warningBg.copy(alpha = 0.55f)),
                        border = BorderStroke(1.dp, ledgerColors.warning),
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = ledgerColors.warning)
                                Text("أسماء غير مطابقة حرفياً في الدليل (${draft.warnings.size})", style = MaterialTheme.typography.titleSmall)
                            }
                            draft.warnings.forEach { w ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            "${w.type.titleAr}: \"${w.extractedName}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (w.suggestedMatch != null) {
                                            Text(
                                                "أقرب اسم في الإكسيل: ${w.suggestedMatch.name}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (w.suggestedMatch != null) {
                                                Button(
                                                    onClick = { onPickMasterForWarning(w, w.suggestedMatch) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                ) {
                                                    Text("اعتماد المقترح", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { warningPickerTarget = w },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            ) {
                                                Text("اختر من الدليل", style = MaterialTheme.typography.labelSmall)
                                            }
                                            TextButton(
                                                onClick = { onAddWarningAsMaster(w) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Text("حفظ كجديد", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lines
            itemsIndexed(draft.lines) { lineIdx, line ->
                EditableJournalLineCard(
                    lineIndex = lineIdx,
                    canDelete = draft.lines.size > 1,
                    line = line,
                    onUpdate = { transform -> onUpdateLine(lineIdx, transform) },
                    onPickFromMaster = { entityType -> pickerRequest = lineIdx to entityType },
                    onRemove = { onRemoveLine(lineIdx) },
                )
            }
        }
    }
}

@Composable
private fun EditableJournalLineCard(
    lineIndex: Int,
    canDelete: Boolean,
    line: JournalEntryEntity,
    onUpdate: ((JournalEntryEntity) -> JournalEntryEntity) -> Unit,
    onPickFromMaster: (MasterEntityType) -> Unit,
    onRemove: () -> Unit,
) {
    var showAll51Columns by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("سطر القيد #${lineIndex + 1}", style = MaterialTheme.typography.titleSmall)
                if (canDelete) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف السطر",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MasterPickableField(
                    label = "Main account name",
                    value = line.mainAccountName,
                    onValueChange = { v -> onUpdate { it.copy(mainAccountName = v) } },
                    onPickClick = { onPickFromMaster(MasterEntityType.MAIN_ACCOUNT) },
                    modifier = Modifier.weight(1f),
                )
                MasterPickableField(
                    label = "Sub account name",
                    value = line.subAccountName,
                    onValueChange = { v -> onUpdate { it.copy(subAccountName = v) } },
                    onPickClick = { onPickFromMaster(MasterEntityType.SUB_ACCOUNT) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericTextField(
                    label = "مدين (debit)",
                    value = line.debit,
                    onNumberChange = { n -> onUpdate { it.copy(debit = n) } },
                    modifier = Modifier.weight(1f),
                )
                NumericTextField(
                    label = "دائن (credit)",
                    value = line.credit,
                    onNumberChange = { n -> onUpdate { it.copy(credit = n) } },
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = line.description,
                onValueChange = { v -> onUpdate { it.copy(description = v) } },
                label = { Text("البيان") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MasterPickableField(
                    label = "المورد (Suppliar name)",
                    value = line.supplierName,
                    onValueChange = { v -> onUpdate { it.copy(supplierName = v) } },
                    onPickClick = { onPickFromMaster(MasterEntityType.SUPPLIER) },
                    modifier = Modifier.weight(1f),
                )
                MasterPickableField(
                    label = "العميل (customer name)",
                    value = line.customerName,
                    onValueChange = { v -> onUpdate { it.copy(customerName = v, clientAr = v) } },
                    onPickClick = { onPickFromMaster(MasterEntityType.CUSTOMER) },
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAll51Columns = !showAll51Columns }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showAll51Columns) "إخفاء باقي الـ 51 عموداً" else "عرض وتعديل باقي الـ 51 عموداً (الفاتورة، الشيفت، الضرائب، الشيكات، الفرع...)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (showAll51Columns) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            AnimatedVisibility(visible = showAll51Columns) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    Text("بيانات الحركة والتاريخ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("رقم العملية", line.operationNum, { v -> onUpdate { it.copy(operationNum = v) } }, Modifier.weight(1f))
                        TextEditField("date", line.date, { v -> onUpdate { it.copy(date = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MasterPickableField("Trans. Name", line.transName, { v -> onUpdate { it.copy(transName = v) } }, { onPickFromMaster(MasterEntityType.TRANS_NAME) }, Modifier.weight(1f))
                        TextEditField("condition", line.condition, { v -> onUpdate { it.copy(condition = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("Month", line.month, { v -> onUpdate { it.copy(month = v) } }, Modifier.weight(1f))
                        TextEditField("الشهر 2", line.month2, { v -> onUpdate { it.copy(month2 = v) } }, Modifier.weight(1f))
                        TextEditField("H", line.hField, { v -> onUpdate { it.copy(hField = v) } }, Modifier.weight(0.7f))
                    }

                    Text("الأكواد والأطراف والفروع والموظفين", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("supp. code", line.suppCode, { v -> onUpdate { it.copy(suppCode = v) } }, Modifier.weight(1f))
                        TextEditField("تصنيف المورد", line.supplierCategory, { v -> onUpdate { it.copy(supplierCategory = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("cust. code", line.custCode, { v -> onUpdate { it.copy(custCode = v) } }, Modifier.weight(1f))
                        TextEditField("العميل", line.clientAr, { v -> onUpdate { it.copy(clientAr = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("كود الموظف", line.employeeCode, { v -> onUpdate { it.copy(employeeCode = v) } }, Modifier.weight(1f))
                        MasterPickableField("اسم الموظف", line.employeeName, { v -> onUpdate { it.copy(employeeName = v) } }, { onPickFromMaster(MasterEntityType.EMPLOYEE) }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("كود الفرع", line.branchCode, { v -> onUpdate { it.copy(branchCode = v) } }, Modifier.weight(1f))
                        MasterPickableField("اسم الفرع", line.branchName, { v -> onUpdate { it.copy(branchName = v) } }, { onPickFromMaster(MasterEntityType.BRANCH) }, Modifier.weight(1f))
                    }

                    Text("الفاتورة والشيفت والفيزا والعجز", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("payment invoice num.", line.paymentInvoiceNum, { v -> onUpdate { it.copy(paymentInvoiceNum = v) } }, Modifier.weight(1f))
                        TextEditField("shift num.", line.shiftNum, { v -> onUpdate { it.copy(shiftNum = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericTextField("عدد الفيزا", line.visaCount, { n -> onUpdate { it.copy(visaCount = n) } }, Modifier.weight(1f))
                        TextEditField("رقم الفيزا", line.visaNumber, { v -> onUpdate { it.copy(visaNumber = v) } }, Modifier.weight(1f))
                        NumericTextField("عجز / زيادة", line.cashVariance, { n -> onUpdate { it.copy(cashVariance = n) } }, Modifier.weight(1f))
                    }
                    TextEditField("شهر فواتير المشتريات", line.purchaseInvoicesMonth, { v -> onUpdate { it.copy(purchaseInvoicesMonth = v) } }, Modifier.fillMaxWidth())

                    Text("الضرائب والمرتجعات والخصومات", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericTextField("sales tax2372", line.salesTax2372, { n -> onUpdate { it.copy(salesTax2372 = n) } }, Modifier.weight(1f))
                        NumericTextField("customer's tax1357", line.customerTax1357, { n -> onUpdate { it.copy(customerTax1357 = n) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericTextField("خصم ضرائب ا ت ص 1%2361", line.withholdingTax1Pct2361, { n -> onUpdate { it.copy(withholdingTax1Pct2361 = n) } }, Modifier.weight(1f))
                        NumericTextField("الضرائب", line.taxes, { n -> onUpdate { it.copy(taxes = n) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericTextField("المرتجعات سليمة", line.goodReturns, { n -> onUpdate { it.copy(goodReturns = n) } }, Modifier.weight(1f))
                        NumericTextField("تالفة", line.damagedReturns, { n -> onUpdate { it.copy(damagedReturns = n) } }, Modifier.weight(1f))
                        NumericTextField("الخصومات", line.discounts, { n -> onUpdate { it.copy(discounts = n) } }, Modifier.weight(1f))
                    }

                    Text("أوراق الدفع والقبض والبنك", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("Payment cheque num.", line.paymentChequeNum, { v -> onUpdate { it.copy(paymentChequeNum = v) } }, Modifier.weight(1f))
                        TextEditField("due date (دفع)", line.paymentChequeDueDate, { v -> onUpdate { it.copy(paymentChequeDueDate = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("supplier code PayCheque", line.paymentChequeSupplierCode, { v -> onUpdate { it.copy(paymentChequeSupplierCode = v) } }, Modifier.weight(1f))
                        TextEditField("supplier name PayCheque", line.paymentChequeSupplierName, { v -> onUpdate { it.copy(paymentChequeSupplierName = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("شهر استحقاقات اوراق الدفع", line.paymentChequeDueMonth, { v -> onUpdate { it.copy(paymentChequeDueMonth = v) } }, Modifier.weight(1f))
                        TextEditField("تورايخ أ . د", line.paymentPaperDates, { v -> onUpdate { it.copy(paymentPaperDates = v) } }, Modifier.weight(1f))
                        TextEditField("سنة اوراق الدفع ", line.paymentPaperYear, { v -> onUpdate { it.copy(paymentPaperYear = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("Received cheque num.", line.receivedChequeNum, { v -> onUpdate { it.copy(receivedChequeNum = v) } }, Modifier.weight(1f))
                        TextEditField("due date (قبض)", line.receivedChequeDueDate, { v -> onUpdate { it.copy(receivedChequeDueDate = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("cust code RecCheque", line.receivedChequeCustomerCode, { v -> onUpdate { it.copy(receivedChequeCustomerCode = v) } }, Modifier.weight(1f))
                        TextEditField("cust name RecCheque", line.receivedChequeCustomerName, { v -> onUpdate { it.copy(receivedChequeCustomerName = v) } }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextEditField("رقم شيك العملاء", line.customerChequeNum, { v -> onUpdate { it.copy(customerChequeNum = v) } }, Modifier.weight(1f))
                        TextEditField("تاريخ الشيك الوارد", line.incomingChequeDate, { v -> onUpdate { it.copy(incomingChequeDate = v) } }, Modifier.weight(1f))
                    }
                    MasterPickableField("البنك", line.bank, { v -> onUpdate { it.copy(bank = v) } }, { onPickFromMaster(MasterEntityType.BANK) }, Modifier.fillMaxWidth())
                    TextEditField("ملاحظات", line.notes, { v -> onUpdate { it.copy(notes = v) } }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MasterPickableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onPickClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onPickClick) {
                Icon(Icons.Rounded.Search, contentDescription = "اختيار $label", modifier = Modifier.size(18.dp))
            }
        },
    )
}

@Composable
private fun TextEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        singleLine = true,
    )
}

@Composable
private fun NumericTextField(
    label: String,
    value: Double,
    onNumberChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(value) {
        mutableStateOf(if (value == 0.0) "" else String.format(Locale.US, "%.2f", value))
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onNumberChange(raw.replace(",", "").toDoubleOrNull() ?: 0.0)
        },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        placeholder = { Text("0.00") },
    )
}

@Composable
private fun MasterRecordPickerDialog(
    title: String,
    records: List<MasterRecordEntity>,
    onSelect: (MasterRecordEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var q by remember { mutableStateOf("") }
    val filtered = remember(records, q) {
        if (q.isBlank()) records else records.filter {
            it.name.contains(q, true) || it.code.contains(q, true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    placeholder = { Text("بحث بالاسم أو الكود...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (filtered.isEmpty()) {
                    Text(
                        "لا توجد سجلات مطابقة في هذا القسم. يمكنك استيراد ملف إكسيل أو إضافتها في الدليل.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.height(260.dp)) {
                        items(filtered, key = { it.id }) { rec ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(rec) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                            ) {
                                Text(rec.name, style = MaterialTheme.typography.bodyMedium)
                                val sub = listOfNotNull(
                                    rec.code.takeIf { it.isNotBlank() }?.let { "كود: $it" },
                                    rec.category.takeIf { it.isNotBlank() },
                                ).joinToString(" • ")
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        },
    )
}
