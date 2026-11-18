package com.harcamatakip.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.harcamatakip.app.core.theme.ThemeMode
import com.harcamatakip.app.core.util.AppCurrency
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
    val weekStart: DayOfWeek = DayOfWeek.MONDAY
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
                ?: DayOfWeek.MONDAY
        )
    }

    suspend fun settingsOnce(): AppSettings = settings.first()

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.DYNAMIC] = enabled }
    suspend fun setCurrency(currency: AppCurrency) = store.edit { it[Keys.CURRENCY] = currency.name }
    suspend fun setWeekStart(day: DayOfWeek) = store.edit { it[Keys.WEEK_START] = day.name }

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
