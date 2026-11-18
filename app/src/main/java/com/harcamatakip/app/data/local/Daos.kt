package com.harcamatakip.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** SQL'de hesaplanan aylık toplam satırı. [ym] = "2026-11" biçimindedir. */
data class MonthSumRow(val ym: String, val isIncome: Boolean, val total: Long)

/** SQL'de hesaplanan kategori + ay bazlı gider toplamı. */
data class CategoryMonthRow(val categoryId: Long, val ym: String, val total: Long)

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    /** Yalnızca [fromDay]..[toDay] (epoch day, uçlar dahil) aralığındaki işlemler; date indeksini kullanır. */
    @Query("SELECT * FROM expenses WHERE date BETWEEN :fromDay AND :toDay ORDER BY date DESC, id DESC")
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<ExpenseEntity>>

    /** [fromDay]'den bu yana aylık gelir/gider toplamları (gruplama SQLite'ta yapılır). */
    @Query(
        "SELECT strftime('%Y-%m', date * 86400, 'unixepoch') AS ym, isIncome, SUM(amount) AS total " +
            "FROM expenses WHERE date >= :fromDay GROUP BY ym, isIncome"
    )
    fun observeMonthSums(fromDay: Long): Flow<List<MonthSumRow>>

    /** [fromDay]'den bu yana kategori ve ay bazında gider toplamları. */
    @Query(
        "SELECT categoryId, strftime('%Y-%m', date * 86400, 'unixepoch') AS ym, SUM(amount) AS total " +
            "FROM expenses WHERE isIncome = 0 AND date >= :fromDay GROUP BY categoryId, ym"
    )
    fun observeCategoryMonthSums(fromDay: Long): Flow<List<CategoryMonthRow>>

    @Query("SELECT * FROM expenses")
    suspend fun getAll(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: Long): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE recurringId = :recurringId AND date = :day")
    suspend fun countForRecurring(recurringId: Long, day: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ExpenseEntity>)

    @Update
    suspend fun update(e: ExpenseEntity)

    @Delete
    suspend fun delete(e: ExpenseEntity)

    @Query("UPDATE expenses SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun reassign(fromId: Long, toId: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isIncome ASC, isFallback ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE isFallback = 1 AND isIncome = :income LIMIT 1")
    suspend fun getFallback(income: Boolean): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<CategoryEntity>)

    @Update
    suspend fun update(c: CategoryEntity)

    @Delete
    suspend fun delete(c: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(b: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteByCategory(categoryId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY nextDueDay ASC")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring")
    suspend fun getAll(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE active = 1")
    suspend fun getActive(): List<RecurringEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(r: RecurringEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<RecurringEntity>)

    @Update
    suspend fun update(r: RecurringEntity)

    @Delete
    suspend fun delete(r: RecurringEntity)

    @Query("UPDATE recurring SET categoryId = :toId WHERE categoryId = :fromId")
    suspend fun reassign(fromId: Long, toId: Long)

    @Query("DELETE FROM recurring")
    suspend fun deleteAll()
}
