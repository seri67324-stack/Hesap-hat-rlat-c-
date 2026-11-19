package com.harcamatakip.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.harcamatakip.app.core.util.Notifier
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.data.local.AppDatabase
import com.harcamatakip.app.data.local.BudgetAlertDao
import com.harcamatakip.app.data.local.BudgetAlertStateEntity
import com.harcamatakip.app.data.local.ExpenseDao
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.domain.usecase.BudgetAlertPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aylık bütçelerin %80 ve %100 eşiklerinde bildirim gönderir.
 *
 * Her (bütçe, ay) için her eşik EN FAZLA 1 kez bildirilir; durum Room'daki `budget_alert_state`
 * tablosunda tutulur. Durum, bildirim gönderilmeden ÖNCE ve tek işlemde (transaction) kaydedilir;
 * bu yüzden aynı anda gelen iki kontrol (ör. işlem kaydı + günlük worker) aynı bildirimi iki kez atamaz.
 */
@Singleton
class BudgetAlertManager @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val db: AppDatabase,
    private val expenseDao: ExpenseDao,
    private val alertDao: BudgetAlertDao,
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository
) {
    private val mutex = Mutex()

    private data class Pending(val categoryId: Long, val level: Int, val spent: Long, val limit: Long)

    suspend fun check(today: LocalDate = LocalDate.now()) = mutex.withLock {
        val budgetList = budgets.getAll().filter { it.limitAmount > 0 }
        if (budgetList.isEmpty()) return@withLock

        val ym = YearMonth.from(today)
        val periodKey = ym.toString() // "2026-11"
        val sums = expenseDao.categorySumsBetween(ym.atDay(1).toEpochDay(), ym.atEndOfMonth().toEpochDay())
            .associate { it.categoryId to it.total }
        val totalSpent = sums.values.sum()
        // Eski sürümde DataStore'da tutulan bildirim anahtarları: güncelleme sonrası çift bildirimi önler
        val legacy = settings.notifiedKeys()

        val pending = db.withTransaction {
            alertDao.purgeExcept(periodKey)
            val result = mutableListOf<Pending>()
            for (b in budgetList) {
                val spent = if (b.categoryId == 0L) totalSpent else sums[b.categoryId] ?: 0L
                val stored = alertDao.get(b.categoryId, periodKey)
                val state = stored ?: BudgetAlertStateEntity(
                    categoryId = b.categoryId,
                    periodKey = periodKey,
                    sent80 = "$periodKey|budget|${b.categoryId}|80" in legacy,
                    sent100 = "$periodKey|budget|${b.categoryId}|100" in legacy
                )
                val decision = BudgetAlertPolicy.evaluate(spent, b.limitAmount, state)
                if (stored == null || decision.newState != stored) alertDao.upsert(decision.newState)
                decision.level?.let { result += Pending(b.categoryId, it, spent, b.limitAmount) }
            }
            result
        }
        if (pending.isEmpty()) return@withLock

        val cats = categories.getAll().associateBy { it.id }
        val symbol = settings.settingsOnce().currency.symbol
        for (p in pending) {
            val name = if (p.categoryId == 0L) "Aylık bütçen"
            else "${cats[p.categoryId]?.name ?: "Kategori"} bütçen"
            val title = if (p.level == BudgetAlertPolicy.LEVEL_100) "Bütçe aşıldı" else "Bütçenin %80'ine ulaştın"
            val text = "$name: ${p.spent.formatMoney(symbol)} / ${p.limit.formatMoney(symbol)}"
            Notifier.notify(ctx, Notifier.CH_BUDGET, "$periodKey|budget|${p.categoryId}|${p.level}".hashCode(), title, text)
        }
    }
}
