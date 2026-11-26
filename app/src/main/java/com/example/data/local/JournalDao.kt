package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY CAST(col_01_operation_num AS INTEGER) DESC, created_at DESC, id ASC")
    fun observeAllEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries ORDER BY CAST(col_01_operation_num AS INTEGER) ASC, id ASC")
    suspend fun getAllEntriesAsc(): List<JournalEntryEntity>

    @Query("SELECT col_01_operation_num FROM journal_entries")
    suspend fun getAllOperationNums(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<JournalEntryEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: JournalEntryEntity): Long

    @Update
    suspend fun update(entry: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM journal_entries WHERE col_01_operation_num = :operationNum")
    suspend fun deleteByOperationNum(operationNum: String)

    @Query("DELETE FROM journal_entries")
    suspend fun deleteAll()
}
