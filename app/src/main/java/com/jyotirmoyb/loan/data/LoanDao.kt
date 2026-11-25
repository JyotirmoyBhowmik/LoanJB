package com.jyotirmoyb.loan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM saved_loans ORDER BY createdAtMillis DESC")
    fun observeSavedLoans(): Flow<List<SavedLoanEntity>>

    @Query("SELECT * FROM saved_loans ORDER BY createdAtMillis DESC")
    suspend fun getAllSavedLoansOnce(): List<SavedLoanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedLoan(loan: SavedLoanEntity): Long

    @Update
    suspend fun updateSavedLoan(loan: SavedLoanEntity)

    @Query("DELETE FROM saved_loans WHERE id = :id")
    suspend fun deleteSavedLoanById(id: Long)

    @Query("DELETE FROM saved_loans")
    suspend fun clearAllSavedLoans()

    @Query("SELECT * FROM compare_scenarios ORDER BY createdAtMillis DESC")
    fun observeCompareScenarios(): Flow<List<CompareScenarioEntity>>

    @Query("SELECT * FROM compare_scenarios ORDER BY createdAtMillis DESC")
    suspend fun getAllCompareScenariosOnce(): List<CompareScenarioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompareScenario(scenario: CompareScenarioEntity): Long

    @Query("DELETE FROM compare_scenarios WHERE id = :id")
    suspend fun deleteCompareScenarioById(id: Long)

    @Query("DELETE FROM compare_scenarios")
    suspend fun clearAllCompareScenarios()

    @Query("SELECT * FROM catalog_override WHERE id = 1")
    fun observeCatalogOverride(): Flow<CatalogOverrideEntity?>

    @Query("SELECT * FROM catalog_override WHERE id = 1")
    suspend fun getCatalogOverrideOnce(): CatalogOverrideEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCatalogOverride(override: CatalogOverrideEntity)

    @Query("DELETE FROM catalog_override")
    suspend fun clearCatalogOverride()
}
