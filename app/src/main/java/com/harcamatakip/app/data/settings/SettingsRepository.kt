package com.harcamatakip.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.harcamatakip.app.core.theme.ThemeMode
import com.harcamatakip.app.core.util.AppCurrency
import com.harcamatakip.app.domain.usecase.BackupReminderPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val currency: AppCurrency = AppCurrency.TRY,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    /** Yedek hatırlatma aralığı (gün); 0 = kapalı. */
    val backupIntervalDays: Int = BackupReminderPolicy.DEFAULT_INTERVAL_DAYS,
    /** Son başarılı yedeğin günü (epoch day), hiç alınmadıysa null. */
    val lastBackupDay: Long? = null,
    /** Hatırlatıcı sayımının başladığı ilk gün (ilk kurulum/ilk açılış). */
    val backupBaselineDay: Long? = null,
    val backupSnoozeDay: Long? = null,
    val backupNudgeDay: Long? = null
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic")
        val CURRENCY = stringPreferencesKey("currency")
        val WEEK_START = stringPreferencesKey("week_start")
        val NOTIFIED = stringSetPreferencesKey("notified")
        val BACKUP_INTERVAL = intPreferencesKey("backup_interval_days")
        val LAST_BACKUP = longPreferencesKey("last_backup_day")
        val BACKUP_BASELINE = longPreferencesKey("backup_baseline_day")
        val BACKUP_SNOOZE = longPreferencesKey("backup_snooze_day")
        val BACKUP_NUDGE = longPreferencesKey("backup_nudge_day")
    }

    private val store get() = ctx.dataStore

    val settings: Flow<AppSettings> = ctx.dataStore.data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            currency = p[Keys.CURRENCY]?.let { runCatching { AppCurrency.valueOf(it) }.getOrNull() }
                ?: AppCurrency.TRY,
            weekStart = p[Keys.WEEK_START]?.let { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?: DayOfWeek.MONDAY,
            backupIntervalDays = p[Keys.BACKUP_INTERVAL] ?: BackupReminderPolicy.DEFAULT_INTERVAL_DAYS,
            lastBackupDay = p[Keys.LAST_BACKUP],
            backupBaselineDay = p[Keys.BACKUP_BASELINE],
            backupSnoozeDay = p[Keys.BACKUP_SNOOZE],
            backupNudgeDay = p[Keys.BACKUP_NUDGE]
        )
    }

    suspend fun settingsOnce(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.DYNAMIC] = enabled }
    suspend fun setCurrency(currency: AppCurrency) = store.edit { it[Keys.CURRENCY] = currency.name }
    suspend fun setWeekStart(day: DayOfWeek) = store.edit { it[Keys.WEEK_START] = day.name }

    // ---- Yedek hatırlatıcısı
    suspend fun setBackupInterval(days: Int) = store.edit {
        it[Keys.BACKUP_INTERVAL] = days.coerceAtLeast(0)
        // Aralık değişince sayım bugünden yeniden başlar
        it.remove(Keys.BACKUP_SNOOZE)
    }

    /** Başarılı yedekten sonra: son yedek günü kaydedilir, erteleme temizlenir. */
    suspend fun markBackupDone(today: Long) = store.edit {
        it[Keys.LAST_BACKUP] = today
        it.remove(Keys.BACKUP_SNOOZE)
    }

    /** İlk açılışta bir kez: hatırlatıcı sayımının başlangıç günü. */
    suspend fun ensureBackupBaseline(today: Long) = store.edit {
        if (it[Keys.BACKUP_BASELINE] == null) it[Keys.BACKUP_BASELINE] = today
    }

    suspend fun snoozeBackup(snoozeDay: Long) = store.edit { it[Keys.BACKUP_SNOOZE] = snoozeDay }
    suspend fun markBackupNudged(today: Long) = store.edit { it[Keys.BACKUP_NUDGE] = today }

    /** Aynı bildirimi tekrar tekrar göndermemek için gönderilenlerin anahtarları. */
    suspend fun notifiedKeys(): Set<String> =
        store.data.map { it[Keys.NOTIFIED] ?: emptySet() }.first()

    /** Anahtarlar "yyyy-MM|..." ile başlar; eski aylara ait olanlar temizlenir. */
    suspend fun addNotified(key: String, currentYearMonth: String) {
        store.edit { p ->
            val old = p[Keys.NOTIFIED] ?: emptySet()
            p[Keys.NOTIFIED] = old.filter { it.startsWith(currentYearMonth) }.toSet() + key
        }
    }
}
