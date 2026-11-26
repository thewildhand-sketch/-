package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.ArabicNormalizer

@Entity(
    tableName = "accounts",
    indices = [Index(value = ["normalized_main", "normalized_sub"], unique = true)]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "main_account_name")
    val mainAccountName: String,
    @ColumnInfo(name = "sub_account_name")
    val subAccountName: String = "",
    @ColumnInfo(name = "trans_name")
    val transName: String = "",
    @ColumnInfo(name = "account_code")
    val accountCode: String = "",
    @ColumnInfo(name = "notes")
    val notes: String = "",
    @ColumnInfo(name = "normalized_main")
    val normalizedMain: String = ArabicNormalizer.normalize(mainAccountName),
    @ColumnInfo(name = "normalized_sub")
    val normalizedSub: String = ArabicNormalizer.normalize(subAccountName),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
