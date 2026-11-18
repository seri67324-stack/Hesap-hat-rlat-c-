package com.harcamatakip.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.harcamatakip.app.core.util.Notifier
import com.harcamatakip.app.data.repository.CategoryRepository
import com.harcamatakip.app.worker.RecurringWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class ExpenseApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var categoryRepository: CategoryRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        CoroutineScope(Dispatchers.IO).launch { categoryRepository.ensureDefaults() }
        scheduleRecurringWork()
    }

    private fun scheduleRecurringWork() {
        val wm = WorkManager.getInstance(this)
        // Günlük kontrol (cihaz yeniden başlasa da WorkManager işi korur)
        wm.enqueueUniquePeriodicWork(
            "recurring_daily",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RecurringWorker>(1, TimeUnit.DAYS).build()
        )
        // Uygulama her açıldığında bir kez de çalıştır
        wm.enqueueUniqueWork(
            "recurring_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RecurringWorker>().build()
        )
    }
}
