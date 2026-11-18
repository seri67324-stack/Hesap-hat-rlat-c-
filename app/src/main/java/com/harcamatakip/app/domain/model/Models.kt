package com.harcamatakip.app.domain.model

import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.data.local.ExpenseEntity
import java.time.LocalDate

/** Listelerde gösterilen işlem + kategorisi. */
data class TxItem(val expense: ExpenseEntity, val category: CategoryEntity?)

enum class Frequency(val label: String) {
    WEEKLY("Haftalık"),
    MONTHLY("Aylık"),
    YEARLY("Yıllık")
}

object PaymentMethod {
    const val CASH = "CASH"
    const val CARD = "CARD"
    fun label(code: String) = if (code == CARD) "Kart" else "Nakit"
}

enum class Period(val label: String) {
    WEEK("Hafta"),
    MONTH("Ay"),
    YEAR("Yıl"),
    CUSTOM("Özel")
}

object RecurrenceUtil {
    /**
     * Başlangıç gününden itibaren, afterDay'den SONRAKİ ilk vade gününü verir.
     * Aylık işlemlerde ay sonu kaymasını önlemek için her zaman başlangıç gününden hesaplanır.
     */
    fun nextAfter(startDay: Long, afterDay: Long, frequency: String): Long {
        val start = LocalDate.ofEpochDay(startDay)
        val freq = runCatching { Frequency.valueOf(frequency) }.getOrDefault(Frequency.MONTHLY)
        var k = 1L
        while (true) {
            val candidate = when (freq) {
                Frequency.WEEKLY -> start.plusWeeks(k)
                Frequency.MONTHLY -> start.plusMonths(k)
                Frequency.YEARLY -> start.plusYears(k)
            }.toEpochDay()
            if (candidate > afterDay) return candidate
            k++
        }
    }
}
