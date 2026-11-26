package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.ArabicNormalizer

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["normalized_name"], unique = true)]
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "supp_code")
    val suppCode: String = "",
    @ColumnInfo(name = "suppliar_name")
    val supplierName: String,
    @ColumnInfo(name = "supplier_category")
    val supplierCategory: String = "",
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "normalized_name")
    val normalizedName: String = ArabicNormalizer.normalize(supplierName),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
