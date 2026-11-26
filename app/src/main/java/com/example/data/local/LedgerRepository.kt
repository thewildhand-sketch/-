package com.example.data.local

import com.example.domain.ArabicNormalizer
import com.example.model.MasterEntityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LedgerRepository(
    private val journalDao: JournalDao,
    private val supplierDao: SupplierDao,
    private val accountDao: AccountDao,
    private val masterDao: MasterDao,
) {
    val allEntries: Flow<List<JournalEntryEntity>> = journalDao.observeAllEntries()
    val allSuppliers: Flow<List<SupplierEntity>> = supplierDao.observeAll()
    val allAccounts: Flow<List<AccountEntity>> = accountDao.observeAll()

    val allMasterRecords: Flow<List<MasterRecordEntity>> = combine(
        supplierDao.observeAll(),
        accountDao.observeAll(),
        masterDao.observeAllRecords(),
    ) { suppliers, accounts, others ->
        projectUnifiedMasters(suppliers, accounts, others)
    }

    suspend fun getNextOperationNumber(): Int {
        val maxExisting = journalDao.getAllOperationNums()
            .mapNotNull { it.trim().toIntOrNull() }
            .maxOrNull() ?: 0
        return maxExisting + 1
    }

    suspend fun getAllEntriesAsc(): List<JournalEntryEntity> = journalDao.getAllEntriesAsc()

    suspend fun getAllMasterRecords(): List<MasterRecordEntity> {
        return projectUnifiedMasters(
            supplierDao.getAll(),
            accountDao.getAll(),
            masterDao.getAllRecords(),
        )
    }

    private fun projectUnifiedMasters(
        suppliers: List<SupplierEntity>,
        accounts: List<AccountEntity>,
        others: List<MasterRecordEntity>,
    ): List<MasterRecordEntity> {
        val supplierRows = suppliers.map { s ->
            MasterRecordEntity(
                id = s.id,
                type = MasterEntityType.SUPPLIER.name,
                code = s.suppCode,
                name = s.supplierName,
                category = s.supplierCategory,
                extra = s.notes,
                normalizedName = s.normalizedName,
                updatedAt = s.updatedAt,
            )
        }

        val mainAccounts = accounts
            .filter { it.mainAccountName.isNotBlank() }
            .distinctBy { it.normalizedMain }
            .map { a ->
                MasterRecordEntity(
                    id = a.id,
                    type = MasterEntityType.MAIN_ACCOUNT.name,
                    code = a.accountCode,
                    name = a.mainAccountName,
                    category = "",
                    extra = a.notes,
                    normalizedName = a.normalizedMain,
                    updatedAt = a.updatedAt,
                )
            }

        val subAccounts = accounts
            .filter { it.subAccountName.isNotBlank() }
            .map { a ->
                MasterRecordEntity(
                    id = a.id,
                    type = MasterEntityType.SUB_ACCOUNT.name,
                    code = a.accountCode,
                    name = a.subAccountName,
                    category = a.mainAccountName,
                    extra = a.transName,
                    normalizedName = a.normalizedSub,
                    updatedAt = a.updatedAt,
                )
            }

        val filteredOthers = others.filter {
            it.type != MasterEntityType.SUPPLIER.name &&
                it.type != MasterEntityType.MAIN_ACCOUNT.name &&
                it.type != MasterEntityType.SUB_ACCOUNT.name
        }

        return supplierRows + mainAccounts + subAccounts + filteredOthers
    }

    suspend fun saveJournalOperation(entries: List<JournalEntryEntity>) {
        if (entries.isEmpty()) return
        val normalized = entries.map { it.withComputedDerived() }
        journalDao.insertAll(normalized)
        extractAndUpsertMastersFromEntries(normalized)
    }

    suspend fun updateEntry(entry: JournalEntryEntity) {
        val computed = entry.withComputedDerived()
        journalDao.update(computed)
        extractAndUpsertMastersFromEntries(listOf(computed))
    }

    suspend fun deleteEntryById(id: Long) = journalDao.deleteById(id)

    suspend fun deleteOperation(operationNum: String) = journalDao.deleteByOperationNum(operationNum)

    suspend fun clearAllEntries() = journalDao.deleteAll()

    suspend fun upsertMasterRecord(
        type: MasterEntityType,
        name: String,
        code: String = "",
        category: String = "",
        extra: String = "",
        existingId: Long = 0L,
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        when (type) {
            MasterEntityType.SUPPLIER -> upsertSupplierInternal(cleanName, code, category, extra, existingId)
            MasterEntityType.MAIN_ACCOUNT -> upsertAccountInternal(
                mainName = cleanName,
                subName = "",
                transName = extra,
                code = code,
                notes = category,
                existingId = existingId,
            )
            MasterEntityType.SUB_ACCOUNT -> upsertAccountInternal(
                mainName = category.trim().ifBlank { "حسابات عامة" },
                subName = cleanName,
                transName = extra,
                code = code,
                notes = "",
                existingId = existingId,
            )
            else -> {
                val norm = ArabicNormalizer.normalize(cleanName)
                val existing = masterDao.findExact(type.name, norm)
                val record = MasterRecordEntity(
                    id = if (existingId != 0L) existingId else (existing?.id ?: 0L),
                    type = type.name,
                    code = code.trim().ifEmpty { existing?.code.orEmpty() },
                    name = cleanName,
                    category = category.trim().ifEmpty { existing?.category.orEmpty() },
                    extra = extra.trim().ifEmpty { existing?.extra.orEmpty() },
                    normalizedName = norm,
                )
                masterDao.upsert(record)
            }
        }
    }

    private suspend fun upsertSupplierInternal(
        name: String,
        code: String,
        category: String,
        notes: String,
        existingId: Long = 0L,
    ): Boolean {
        val norm = ArabicNormalizer.normalize(name)
        val existing = supplierDao.findByNormalizedName(norm)
        val mergedCode = code.trim().ifEmpty { existing?.suppCode.orEmpty() }
        val mergedCat = category.trim().ifEmpty { existing?.supplierCategory.orEmpty() }
        val mergedNotes = notes.trim().ifEmpty { existing?.notes.orEmpty() }
        if (existing != null && existingId == 0L &&
            mergedCode == existing.suppCode &&
            mergedCat == existing.supplierCategory &&
            mergedNotes == existing.notes
        ) {
            return false
        }
        supplierDao.upsert(
            SupplierEntity(
                id = if (existingId != 0L) existingId else (existing?.id ?: 0L),
                suppCode = mergedCode,
                supplierName = name,
                supplierCategory = mergedCat,
                notes = mergedNotes,
                normalizedName = norm,
            )
        )
        return true
    }

    private suspend fun upsertAccountInternal(
        mainName: String,
        subName: String,
        transName: String,
        code: String,
        notes: String,
        existingId: Long = 0L,
    ): Boolean {
        val cleanMain = mainName.trim()
        val cleanSub = subName.trim()
        if (cleanMain.isEmpty() && cleanSub.isEmpty()) return false
        val normMain = ArabicNormalizer.normalize(cleanMain)
        val normSub = ArabicNormalizer.normalize(cleanSub)
        val existing = accountDao.findExact(normMain, normSub)
        val mergedTrans = transName.trim().ifEmpty { existing?.transName.orEmpty() }
        val mergedCode = code.trim().ifEmpty { existing?.accountCode.orEmpty() }
        val mergedNotes = notes.trim().ifEmpty { existing?.notes.orEmpty() }
        if (existing != null && existingId == 0L &&
            mergedTrans == existing.transName &&
            mergedCode == existing.accountCode &&
            mergedNotes == existing.notes
        ) {
            return false
        }
        accountDao.upsert(
            AccountEntity(
                id = if (existingId != 0L) existingId else (existing?.id ?: 0L),
                mainAccountName = cleanMain,
                subAccountName = cleanSub,
                transName = mergedTrans,
                accountCode = mergedCode,
                notes = mergedNotes,
                normalizedMain = normMain,
                normalizedSub = normSub,
            )
        )
        return true
    }

    suspend fun bulkUpsertMasterRecords(records: List<MasterRecordEntity>): Int {
        var addedOrUpdated = 0
        for (raw in records) {
            val cleanName = raw.name.trim()
            if (cleanName.isEmpty()) continue
            when (raw.type) {
                MasterEntityType.SUPPLIER.name -> {
                    if (upsertSupplierInternal(cleanName, raw.code, raw.category, raw.extra)) {
                        addedOrUpdated++
                    }
                }
                MasterEntityType.MAIN_ACCOUNT.name -> {
                    if (upsertAccountInternal(cleanName, "", raw.extra, raw.code, raw.category)) {
                        addedOrUpdated++
                    }
                }
                MasterEntityType.SUB_ACCOUNT.name -> {
                    val parentMain = raw.category.trim().ifBlank { "حسابات عامة" }
                    if (upsertAccountInternal(parentMain, cleanName, raw.extra, raw.code, "")) {
                        addedOrUpdated++
                    }
                }
                else -> {
                    val norm = ArabicNormalizer.normalize(cleanName)
                    val existing = masterDao.findExact(raw.type, norm)
                    if (existing == null) {
                        masterDao.upsert(raw.copy(id = 0L, name = cleanName, normalizedName = norm))
                        addedOrUpdated++
                    } else {
                        val mergedCode = existing.code.ifBlank { raw.code.trim() }
                        val mergedCat = existing.category.ifBlank { raw.category.trim() }
                        val mergedExtra = existing.extra.ifBlank { raw.extra.trim() }
                        if (mergedCode != existing.code || mergedCat != existing.category || mergedExtra != existing.extra) {
                            masterDao.upsert(
                                existing.copy(
                                    code = mergedCode,
                                    category = mergedCat,
                                    extra = mergedExtra,
                                    updatedAt = System.currentTimeMillis(),
                                )
                            )
                            addedOrUpdated++
                        }
                    }
                }
            }
        }
        return addedOrUpdated
    }

    suspend fun deleteMasterRecord(record: MasterRecordEntity) {
        when (record.type) {
            MasterEntityType.SUPPLIER.name -> supplierDao.deleteById(record.id)
            MasterEntityType.MAIN_ACCOUNT.name,
            MasterEntityType.SUB_ACCOUNT.name,
            -> accountDao.deleteById(record.id)
            else -> masterDao.deleteById(record.id)
        }
    }

    suspend fun extractAndUpsertMastersFromEntries(entries: List<JournalEntryEntity>): Int {
        val collected = mutableListOf<MasterRecordEntity>()
        fun add(type: MasterEntityType, name: String, code: String = "", category: String = "", extra: String = "") {
            val clean = name.trim()
            if (clean.isBlank() || clean == "#N/A" || clean == "#REF!" || clean == "0") return
            collected.add(
                MasterRecordEntity(
                    type = type.name,
                    code = code.trim(),
                    name = clean,
                    category = category.trim(),
                    extra = extra.trim(),
                    normalizedName = ArabicNormalizer.normalize(clean),
                )
            )
        }

        for (e in entries) {
            if (e.subAccountName.isBlank()) {
                add(MasterEntityType.MAIN_ACCOUNT, e.mainAccountName, extra = e.transName)
            } else {
                add(MasterEntityType.SUB_ACCOUNT, e.subAccountName, category = e.mainAccountName, extra = e.transName)
            }
            add(MasterEntityType.TRANS_NAME, e.transName)
            add(MasterEntityType.CUSTOMER, e.customerName, code = e.custCode)
            add(MasterEntityType.CUSTOMER, e.receivedChequeCustomerName, code = e.receivedChequeCustomerCode)
            add(MasterEntityType.CUSTOMER, e.clientAr)
            add(MasterEntityType.SUPPLIER, e.supplierName, code = e.suppCode, category = e.supplierCategory)
            add(
                MasterEntityType.SUPPLIER,
                e.paymentChequeSupplierName,
                code = e.paymentChequeSupplierCode,
                category = e.supplierCategory,
            )
            add(MasterEntityType.EMPLOYEE, e.employeeName, code = e.employeeCode)
            add(MasterEntityType.BRANCH, e.branchName, code = e.branchCode)
            add(MasterEntityType.BANK, e.bank)
        }
        return bulkUpsertMasterRecords(collected)
    }
}
