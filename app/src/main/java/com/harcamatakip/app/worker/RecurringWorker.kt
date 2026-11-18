package com.harcamatakip.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harcamatakip.app.core.util.Notifier
import com.harcamatakip.app.core.util.formatMoney
import com.harcamatakip.app.data.repository.BudgetAlertManager
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.data.repository.RecurringRepository
import com.harcamatakip.app.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.time.YearMonth

/**
 * Günlük çalışır: vadesi gelen tekrarlayan işlemleri ekler,
 * yarın ödemesi olanları hatırlatır ve bütçe uyarılarını kontrol eder.
 */
@HiltWorker
class RecurringWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val recurring: RecurringRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
    private val alerts: BudgetAlertManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val todayDate = LocalDate.now()
            val today = todayDate.toEpochDay()
            recurring.processDue(today)

            val cats = categories.getAll().associateBy { it.id }
            val symbol = settings.settingsOnce().currency.symbol
            val ymKey = YearMonth.from(todayDate).toString()
            val sent = settings.notifiedKeys()

            for (r in recurring.dueTomorrow(today)) {
                if (r.isIncome) continue
                val key = "$ymKey|due|${r.id}|$today"
                if (key in sent) continue
                val name = r.note.ifBlank { cats[r.categoryId]?.name ?: "Ödeme" }
                Notifier.notify(
                    applicationContext, Notifier.CH_DUE, key.hashCode(),
                    "Yarın ödemen var", "$name: ${r.amount.formatMoney(symbol)}"
                )
                settings.addNotified(key, ymKey)
            }

            alerts.check()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
