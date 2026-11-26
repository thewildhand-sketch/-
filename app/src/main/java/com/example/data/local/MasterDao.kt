package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MasterDao {
    @Query("SELECT * FROM master_records ORDER BY type ASC, name ASC")
    fun observeAllRecords(): Flow<List<MasterRecordEntity>>

    @Query("SELECT * FROM master_records ORDER BY type ASC, name ASC")
    suspend fun getAllRecords(): List<MasterRecordEntity>

    @Query("SELECT * FROM master_records WHERE type = :type AND normalizedName = :normalizedName LIMIT 1")
    suspend fun findExact(type: String, normalizedName: String): MasterRecordEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(records: List<MasterRecordEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: MasterRecordEntity): Long

    @Update
    suspend fun update(record: MasterRecordEntity)

    @Query("DELETE FROM master_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM master_records WHERE type = :type")
    suspend fun deleteByType(type: String)
}
