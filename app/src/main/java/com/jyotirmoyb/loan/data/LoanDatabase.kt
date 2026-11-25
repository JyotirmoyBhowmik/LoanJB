package com.jyotirmoyb.loan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SavedLoanEntity::class, CompareScenarioEntity::class, CatalogOverrideEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LoanDatabase : RoomDatabase() {
    abstract fun loanDao(): LoanDao

    companion object {
        @Volatile
        private var INSTANCE: LoanDatabase? = null

        fun getInstance(context: Context): LoanDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LoanDatabase::class.java,
                    "loanjb_offline.db"
                ).fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
        }
    }
}
