package com.harcamatakip.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harcamatakip.app.core.theme.HarcamaTakipTheme
import com.harcamatakip.app.core.util.AppEvents
import com.harcamatakip.app.core.util.Notifier
import com.harcamatakip.app.data.settings.AppSettings
import com.harcamatakip.app.data.settings.SettingsRepository
import com.harcamatakip.app.presentation.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Yalnızca ilk açılışta (ekran döndürmede bildirim isteği tekrarlanmasın)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            HarcamaTakipTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                AppNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Yedek hatırlatma bildirimine dokunulduysa ana ekranda yedek akışı başlatılır. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(Notifier.EXTRA_ACTION) == Notifier.ACTION_BACKUP) {
            intent?.removeExtra(Notifier.EXTRA_ACTION)
            AppEvents.requestBackup()
        }
    }
}
