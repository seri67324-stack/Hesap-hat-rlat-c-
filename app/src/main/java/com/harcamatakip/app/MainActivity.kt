package com.harcamatakip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harcamatakip.app.core.theme.HarcamaTakipTheme
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
}
