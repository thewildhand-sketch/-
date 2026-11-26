package com.example.domain

import com.example.data.local.JournalEntryEntity
import com.example.data.local.MasterRecordEntity
import com.example.model.MasterEntityType

data class MatchWarning(
    val lineIndex: Int,
    val type: MasterEntityType,
    val extractedName: String,
    val extractedCode: String,
    val suggestedMatch: MasterRecordEntity?,
)

data class MatchedDraftResult(
    val entries: List<JournalEntryEntity>,
    val warnings: List<MatchWarning>,
)

object EntityMatcher {
    fun findBestMatch(
        queryName: String,
        queryCode: String,
        records: List<MasterRecordEntity>,
        type: MasterEntityType,
    ): Pair<MasterRecordEntity?, Boolean> {
        val scoped = records.filter { it.type == type.name }
        if (scoped.isEmpty()) return null to false

        val cleanCode = queryCode.trim()
        if (cleanCode.isNotBlank()) {
            scoped.firstOrNull { it.code.trim().equals(cleanCode, ignoreCase = true) }
                ?.let { return it to true }
        }

        val normQuery = ArabicNormalizer.normalize(queryName)
        if (normQuery.isBlank()) return null to false

        scoped.firstOrNull { it.normalizedName == normQuery }?.let { return it to true }

        val best = scoped
            .map { it to ArabicNormalizer.tokenSimilarity(queryName, it.name) }
            .maxByOrNull { it.second }

        return if (best != null && best.second >= 0.78) {
            best.first to true
        } else if (best != null && best.second >= 0.40) {
            best.first to false
        } else {
            null to false
        }
    }

    fun resolveDraftAgainstMaster(
        entries: List<JournalEntryEntity>,
        masterRecords: List<MasterRecordEntity>,
    ): MatchedDraftResult {
        val warnings = mutableListOf<MatchWarning>()
        val resolved = entries.mapIndexed { idx, entry ->
            var updated = entry

            if (entry.supplierName.isNotBlank()) {
                val (match, confident) = findBestMatch(
                    entry.supplierName,
                    entry.suppCode,
                    masterRecords,
                    MasterEntityType.SUPPLIER,
                )
                if (match != null && confident) {
                    updated = updated.copy(
                        supplierName = match.name,
                        suppCode = match.code.ifBlank { entry.suppCode },
                        supplierCategory = match.category.ifBlank { entry.supplierCategory },
                    )
                } else {
                    warnings.add(
                        MatchWarning(
                            lineIndex = idx,
                            type = MasterEntityType.SUPPLIER,
                            extractedName = entry.supplierName,
                            extractedCode = entry.suppCode,
                            suggestedMatch = match,
                        )
                    )
                }
            }

            if (entry.customerName.isNotBlank()) {
                val (match, confident) = findBestMatch(
                    entry.customerName,
                    entry.custCode,
                    masterRecords,
                    MasterEntityType.CUSTOMER,
                )
                if (match != null && confident) {
                    updated = updated.copy(
                        customerName = match.name,
                        custCode = match.code.ifBlank { entry.custCode },
                        clientAr = match.name,
                    )
                } else {
                    warnings.add(
                        MatchWarning(
                            lineIndex = idx,
                            type = MasterEntityType.CUSTOMER,
                            extractedName = entry.customerName,
                            extractedCode = entry.custCode,
                            suggestedMatch = match,
                        )
                    )
                }
            }

            if (entry.mainAccountName.isNotBlank()) {
                val (match, confident) = findBestMatch(
                    entry.mainAccountName,
                    "",
                    masterRecords,
                    MasterEntityType.MAIN_ACCOUNT,
                )
                if (match != null && confident) {
                    updated = updated.copy(mainAccountName = match.name)
                } else {
                    warnings.add(
                        MatchWarning(
                            lineIndex = idx,
                            type = MasterEntityType.MAIN_ACCOUNT,
                            extractedName = entry.mainAccountName,
                            extractedCode = "",
                            suggestedMatch = match,
                        )
                    )
                }
            }

            if (entry.subAccountName.isNotBlank()) {
                val (match, confident) = findBestMatch(
                    entry.subAccountName,
                    "",
                    masterRecords,
                    MasterEntityType.SUB_ACCOUNT,
                )
                if (match != null && confident) {
                    updated = updated.copy(subAccountName = match.name)
                } else {
                    warnings.add(
                        MatchWarning(
                            lineIndex = idx,
                            type = MasterEntityType.SUB_ACCOUNT,
                            extractedName = entry.subAccountName,
                            extractedCode = "",
                            suggestedMatch = match,
                        )
                    )
                }
            }

            if (entry.branchName.isNotBlank()) {
                val (match, confident) = findBestMatch(
                    entry.branchName,
                    entry.branchCode,
                    masterRecords,
                    MasterEntityType.BRANCH,
                )
                if (match != null && confident) {
                    updated = updated.copy(
                        branchName = match.name,
                        branchCode = match.code.ifBlank { entry.branchCode },
                    )
                }
            }

            if (entry.employeeName.isNotBlank()) {
                val (match, confident) = findBestMatch(
                    entry.employeeName,
                    entry.employeeCode,
                    masterRecords,
                    MasterEntityType.EMPLOYEE,
                )
                if (match != null && confident) {
                    updated = updated.copy(
                        employeeName = match.name,
                        employeeCode = match.code.ifBlank { entry.employeeCode },
                    )
                }
            }

            if (entry.bank.isNotBlank()) {
                val (match, confident) = findBestMatch(
                    entry.bank,
                    "",
                    masterRecords,
                    MasterEntityType.BANK,
                )
                if (match != null && confident) {
                    updated = updated.copy(bank = match.name)
                }
            }

            updated.withComputedDerived()
        }

        return MatchedDraftResult(
            entries = resolved,
            warnings = warnings.distinctBy { Triple(it.lineIndex, it.type, it.extractedName) },
        )
    }
}
