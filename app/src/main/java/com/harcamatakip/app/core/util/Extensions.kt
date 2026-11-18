package com.harcamatakip.app.core.util

import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

val TR: Locale = Locale("tr", "TR")

enum class AppCurrency(val symbol: String, val label: String) {
    TRY("₺", "Türk Lirası (₺)"),
    USD("$", "ABD Doları ($)"),
    EUR("€", "Euro (€)"),
    GBP("£", "İngiliz Sterlini (£)")
}

/** Kuruş cinsinden tutarı "1.234,50 ₺" biçiminde yazar. */
fun Long.formatMoney(symbol: String = "₺"): String =
    String.format(TR, "%,.2f", this / 100.0) + " " + symbol

/** Grafik eksenleri için kısa biçim: 1500 TL -> "1,5 B", 2.000.000 TL -> "2 Mn". */
fun Long.formatCompact(): String {
    val v = this / 100.0
    fun trim(x: Double) = String.format(TR, "%.1f", x).removeSuffix(",0")
    return when {
        abs(v) >= 1_000_000 -> trim(v / 1_000_000) + " Mn"
        abs(v) >= 1_000 -> trim(v / 1_000) + " B"
        else -> trim(v)
    }
}

/** Düzenleme alanı için "1234,50" biçimi. */
fun Long.formatPlain(): String = String.format(TR, "%.2f", this / 100.0)

/** Kullanıcının yazdığı metni "123,45" biçimine indirger (en fazla 2 ondalık). */
fun sanitizeAmountInput(raw: String): String {
    val sb = StringBuilder()
    var hasSep = false
    var decimals = 0
    for (c in raw) {
        if (c in '0'..'9') {
            if (!hasSep) {
                sb.append(c)
            } else if (decimals < 2) {
                sb.append(c)
                decimals++
            }
        } else if ((c == ',' || c == '.') && !hasSep) {
            hasSep = true
            sb.append(',')
        }
    }
    return sb.toString().take(12)
}

/** "123,45" -> 12345 (kuruş). Geçersiz veya sıfır ise null. */
fun parseAmountToKurus(text: String): Long? {
    val t = text.trim().replace(" ", "").replace(',', '.')
    if (t.isEmpty()) return null
    val bd = t.toBigDecimalOrNull() ?: return null
    if (bd.signum() <= 0) return null
    return bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
}

fun LocalDate.fmt(pattern: String): String =
    format(DateTimeFormatter.ofPattern(pattern, TR))

fun dayHeader(epochDay: Long, today: LocalDate = LocalDate.now()): String {
    val d = LocalDate.ofEpochDay(epochDay)
    return when (d) {
        today -> "Bugün"
        today.minusDays(1) -> "Dün"
        else -> d.fmt("d MMMM yyyy, EEEE")
    }
}
