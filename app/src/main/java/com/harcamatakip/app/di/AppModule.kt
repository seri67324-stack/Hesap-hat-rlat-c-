package com.harcamatakip.app.di

import android.content.Context
import androidx.room.Room
import com.harcamatakip.app.data.local.AppDatabase
import com.harcamatakip.app.data.local.BudgetDao
import com.harcamatakip.app.data.local.CategoryDao
import com.harcamatakip.app.data.local.ExpenseDao
import com.harcamatakip.app.data.local.RecurringDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "harcama.db").build()

    @Provides
    fun provideExpenseDao(db: AppDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideBudgetDao(db: AppDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideRecurringDao(db: AppDatabase): RecurringDao = db.recurringDao()
}
