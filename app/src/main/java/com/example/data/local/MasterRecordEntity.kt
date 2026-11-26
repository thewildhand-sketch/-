package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.ArabicNormalizer

@Entity(
    tableName = "master_records",
    indices = [Index(value = ["type", "normalizedName"], unique = true)]
)
data class MasterRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val type: String, // MasterEntityType.name
    val code: String = "",
    val name: String,
    val category: String = "", // supplier classification or parent main account
    val extra: String = "",
    val normalizedName: String = ArabicNormalizer.normalize(name),
    val updatedAt: Long = System.currentTimeMillis(),
)
