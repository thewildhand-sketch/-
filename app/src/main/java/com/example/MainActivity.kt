package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.TableView
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ai.configureAppCheck
import com.example.data.local.AppDatabase
import com.example.data.local.LedgerRepository
import com.example.ui.IntakeUiState
import com.example.ui.LedgerViewModel
import com.example.ui.screens.ExportTemplateScreen
import com.example.ui.screens.IntakeHubScreen
import com.example.ui.screens.JournalReviewScreen
import com.example.ui.screens.LedgerScreen
import com.example.ui.screens.MasterDirectoryScreen
import com.example.ui.theme.DaftaryTheme

enum class AppTab(
    val route: String,
    val label: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector,
) {
    LEDGER("ledger", "الدفتر", Icons.Rounded.Book, Icons.Outlined.Book),
    INTAKE("intake", "إدخال وتحليل", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    DIRECTORY("directory", "الدليل", Icons.Rounded.Contacts, Icons.Outlined.Contacts),
    EXPORT("export", "التصدير", Icons.Rounded.TableView, Icons.Outlined.TableView),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        val db = AppDatabase.getInstance(applicationContext)
        val repository = LedgerRepository(
            journalDao = db.journalDao(),
            supplierDao = db.supplierDao(),
            accountDao = db.accountDao(),
            masterDao = db.masterDao(),
        )

        setContent {
            DaftaryTheme {
                val vm: LedgerViewModel = viewModel(factory = LedgerViewModel.Factory(repository))
                DaftaryAppRoot(viewModel = vm)
            }
        }
    }
}

@Composable
fun DaftaryAppRoot(viewModel: LedgerViewModel) {
    val context = LocalContext.current
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val allMasterRecords by viewModel.allMasterRecords.collectAsStateWithLifecycle()
    val intakeState by viewModel.intakeState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val monthFilter by viewModel.monthFilter.collectAsStateWithLifecycle()
    val branchFilter by viewModel.branchFilter.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.LEDGER) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val reviewBatch = intakeState as? IntakeUiState.ReviewBatch
    if (reviewBatch != null) {
        JournalReviewScreen(
            batch = reviewBatch,
            masterRecords = allMasterRecords,
            onSelectDraftIndex = viewModel::selectReviewDraftIndex,
            onUpdateLine = viewModel::updateDraftLine,
            onAddLine = viewModel::addDraftLine,
            onRemoveLine = viewModel::removeDraftLine,
            onAutoBalance = viewModel::autoBalanceCurrentDraft,
            onPickMasterForWarning = viewModel::applyMasterToWarning,
            onAddWarningAsMaster = viewModel::addWarningAsNewMaster,
            onSaveCurrentAndAdvance = viewModel::saveCurrentDraftAndAdvance,
            onSaveAll = viewModel::saveAllDrafts,
            onDiscard = viewModel::resetIntakeState,
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 840.dp
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                if (useRail) {
                    NavigationRail {
                        AppTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.activeIcon else tab.inactiveIcon,
                                        contentDescription = tab.label,
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}"),
                            )
                        }
                    }
                }

                when (currentTab) {
                    AppTab.LEDGER -> LedgerScreen(
                        entries = allEntries,
                        searchQuery = searchQuery,
                        selectedMonth = monthFilter,
                        selectedBranch = branchFilter,
                        isExpandedWidth = isExpanded,
                        onSearchChange = viewModel::setSearchQuery,
                        onMonthSelect = viewModel::setMonthFilter,
                        onBranchSelect = viewModel::setBranchFilter,
                        onEditOperation = viewModel::openOperationForEdit,
                        onDeleteOperation = viewModel::deleteOperation,
                        onGoToIntake = { currentTab = AppTab.INTAKE },
                        onGoToExport = { currentTab = AppTab.EXPORT },
                        modifier = Modifier.weight(1f),
                    )

                    AppTab.INTAKE -> IntakeHubScreen(
                        intakeState = intakeState,
                        masterCount = allMasterRecords.size,
                        onAnalyzeImages = { uris, type ->
                            viewModel.analyzeImageUris(context, uris, type)
                        },
                        onPdfPicked = { uri, name ->
                            viewModel.loadPdfForPreview(context, uri, name)
                        },
                        onTogglePdfPage = viewModel::togglePdfPageSelection,
                        onSelectAllPdfPages = viewModel::selectAllPdfPages,
                        onAnalyzeSelectedPdf = { viewModel.analyzeSelectedPdfPages(context) },
                        onSpreadsheetPicked = { uri, name ->
                            viewModel.loadSpreadsheetUri(context, uri, name)
                        },
                        onSelectPreviewSheet = viewModel::selectPreviewSheet,
                        onUpdateColumnMapping = viewModel::updatePreviewColumnMapping,
                        onConfirmSpreadsheetImport = viewModel::confirmSpreadsheetImport,
                        onImportRawText = viewModel::importRawTextInput,
                        onStartManualTemplate = viewModel::startManualTemplate,
                        onResetIntake = viewModel::resetIntakeState,
                        modifier = Modifier.weight(1f),
                    )

                    AppTab.DIRECTORY -> MasterDirectoryScreen(
                        records = allMasterRecords,
                        onUpsertRecord = viewModel::upsertMasterRecord,
                        onDeleteRecord = viewModel::deleteMasterRecord,
                        onGoToImport = { currentTab = AppTab.INTAKE },
                        modifier = Modifier.weight(1f),
                    )

                    AppTab.EXPORT -> ExportTemplateScreen(
                        allEntries = allEntries,
                        filteredEntries = viewModel.filteredEntries(),
                        onExportToUri = { uri, filteredOnly ->
                            viewModel.exportXlsxToUri(context, uri, filteredOnly)
                        },
                        onShareXlsx = { filteredOnly ->
                            viewModel.shareXlsxFile(context, filteredOnly)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
