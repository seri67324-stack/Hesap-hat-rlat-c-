package com.harcamatakip.app.core.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** Önceki döneme göre değişimi "+%12" / "−%8" / "%0" biçiminde yazar (Türkçe yüzde yazımı: işaret, sonra %). */
fun formatSignedPercent(pct: Double): String {
    val rounded = abs(pct).roundToLong()
    return when {
        rounded == 0L -> "%0"
        pct > 0 -> "+%$rounded"
        else -> "−%$rounded"
    }
}

/** Değişim yüzdesi hesabı; önceki değer 0 ise karşılaştırma anlamsızdır (null). */
fun changePercent(current: Long, previous: Long): Double? =
    if (previous > 0) (current - previous).toDouble() * 100.0 / previous else null
