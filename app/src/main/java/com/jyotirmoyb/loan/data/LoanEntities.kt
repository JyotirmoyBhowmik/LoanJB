package com.jyotirmoyb.loan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(tableName = "saved_loans")
data class SavedLoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nickname: String,
    val lender: String,
    val categoryId: String,
    val categoryName: String,
    val variantSummary: String,
    val inputJson: String,
    val paidMonthsCsv: String = "",
    val completedChecklistCsv: String = "",
    val reminderEnabled: Boolean = false,
    val reminderDaysBefore: Int = 3,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@JsonClass(generateAdapter = true)
@Entity(tableName = "compare_scenarios")
data class CompareScenarioEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val categoryId: String,
    val variantSummary: String,
    val inputJson: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "catalog_override")
data class CatalogOverrideEntity(
    @PrimaryKey val id: Int = 1,
    val catalogJson: String,
    val updatedAtMillis: Long = System.currentTimeMillis()
)
