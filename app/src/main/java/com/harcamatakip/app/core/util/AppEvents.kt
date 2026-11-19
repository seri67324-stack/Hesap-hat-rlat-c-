package com.harcamatakip.app.core.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Bildirimden gelen tek seferlik uygulama içi istekler (ör. "yedek al"). */
object AppEvents {
    private val _startBackup = MutableStateFlow(false)
    val startBackup: StateFlow<Boolean> = _startBackup.asStateFlow()

    fun requestBackup() { _startBackup.value = true }
    fun consumeBackup() { _startBackup.value = false }
}
