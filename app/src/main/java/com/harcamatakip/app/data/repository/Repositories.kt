package com.harcamatakip.app.data.repository

import androidx.room.withTransaction
import com.harcamatakip.app.data.local.AppDatabase
import com.harcamatakip.app.data.local.BudgetDao
import com.harcamatakip.app.data.local.BudgetEntity
import com.harcamatakip.app.data.local.CategoryDao
import com.harcamatakip.app.data.local.CategoryMonthRow
import com.harcamatakip.app.data.local.QuickTemplateDao
import com.harcamatakip.app.data.local.MonthSumRow
import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.DefaultCategories
import com.harcamatakip.app.data.local.ExpenseDao
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.RecurringDao
import com.harcamatakip.app.data.local.RecurringEntity
import com.harcamatakip.app.domain.model.RecurrenceUtil
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpenseRepository @Inject constructor(private val dao: ExpenseDao) {
    fun observeAll(): Flow<List<ExpenseEntity>> = dao.observeAll()
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<ExpenseEntity>> = dao.observeBetween(fromDay, toDay)
    fun observeMonthSums(fromDay: Long): Flow<List<MonthSumRow>> = dao.observeMonthSums(fromDay)
    fun observeCategoryMonthSums(fromDay: Long): Flow<List<CategoryMonthRow>> = dao.observeCategoryMonthSums(fromDay)
    fun observeRecentLabels(limit: Int = 6): Flow<List<String>> = dao.observeRecentLabels(limit)
    suspend fun getAll(): List<ExpenseEntity> = dao.getAll()
    suspend fun getById(id: Long): ExpenseEntity? = dao.getById(id)
    suspend fun insert(e: ExpenseEntity): Long = dao.insert(e)
    suspend fun update(e: ExpenseEntity) = dao.update(e)
    suspend fun delete(e: ExpenseEntity) = dao.delete(e)
    suspend fun deleteById(id: Long) { dao.getById(id)?.let { dao.delete(it) } }
}

@Singleton
class CategoryRepository @Inject constructor(
    private val db: AppDatabase,
    private val dao: CategoryDao,
    private val expenseDao: ExpenseDao,
    private val recurringDao: RecurringDao,
    private val budgetDao: BudgetDao,
    private val templateDao: QuickTemplateDao
) {
    fun observeAll(): Flow<List<CategoryEntity>> = dao.observeAll()
    suspend fun getAll(): List<CategoryEntity> = dao.getAll()
    suspend fun add(c: CategoryEntity): Long = dao.insert(c)
    suspend fun update(c: CategoryEntity) = dao.update(c)

    /** İlk açılışta hazır kategorileri ekler; "Diğer" kategorilerinin varlığını garanti eder. */
    suspend fun ensureDefaults() {
        if (dao.count() == 0) {
            dao.insertAll(DefaultCategories.all)
            return
        }
        if (dao.getFallback(false) == null) dao.insert(DefaultCategories.fallbackExpense)
        if (dao.getFallback(true) == null) dao.insert(DefaultCategories.fallbackIncome)
    }

    /** Kategoriyi siler; işlemleri ve tekrarlayanları aynı türdeki "Diğer"e taşır. */
    suspend fun deleteAndReassign(c: CategoryEntity) {
        if (c.isFallback) return
        db.withTransaction {
            val fallback = dao.getFallback(c.isIncome)
            if (fallback != null) {
                expenseDao.reassign(c.id, fallback.id)
                recurringDao.reassign(c.id, fallback.id)
                templateDao.reassign(c.id, fallback.id)
            }
            budgetDao.deleteByCategory(c.id)
            dao.delete(c)
        }
    }
}

@Singleton
class BudgetRepository @Inject constructor(private val dao: BudgetDao) {
    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()
    suspend fun getAll(): List<BudgetEntity> = dao.getAll()

    /** categoryId = 0 genel bütçe demektir. */
    suspend fun set(categoryId: Long, limit: Long) =
        dao.upsert(BudgetEntity(categoryId = categoryId, limitAmount = limit))

    suspend fun remove(categoryId: Long) = dao.deleteByCategory(categoryId)
}

@Singleton
class RecurringRepository @Inject constructor(
    private val dao: RecurringDao,
    private val expenseDao: ExpenseDao
) {
    fun observeAll(): Flow<List<RecurringEntity>> = dao.observeAll()
    suspend fun add(r: RecurringEntity): Long = dao.insert(r)
    suspend fun update(r: RecurringEntity) = dao.update(r)
    suspend fun delete(r: RecurringEntity) = dao.delete(r)

    /**
     * Vadesi gelmiş (bugün dahil) tekrarlayan işlemleri gerçek işlem olarak ekler
     * ve bir sonraki vadeyi ilerletir. Aynı gün için çift kayıt oluşmaz.
     */
    suspend fun processDue(today: Long): Int {
        var added = 0
        for (r in dao.getActive()) {
            var due = r.nextDueDay
            while (due <= today) {
                if (expenseDao.countForRecurring(r.id, due) == 0) {
                    expenseDao.insert(
                        ExpenseEntity(
                            amount = r.amount,
                            isIncome = r.isIncome,
                            categoryId = r.categoryId,
                            date = due,
                            note = r.note,
                            paymentMethod = r.paymentMethod,
                            paymentLabel = r.paymentLabel,
                            recurringId = r.id
                        )
                    )
                    added++
                }
                due = RecurrenceUtil.nextAfter(r.startDay, due, r.frequency)
            }
            if (due != r.nextDueDay) dao.update(r.copy(nextDueDay = due))
        }
        return added
    }

    /** Yarın vadesi gelen aktif tekrarlayanlar (hatırlatma için). */
    suspend fun dueTomorrow(today: Long): List<RecurringEntity> =
        dao.getActive().filter { it.nextDueDay == today + 1 }
}
