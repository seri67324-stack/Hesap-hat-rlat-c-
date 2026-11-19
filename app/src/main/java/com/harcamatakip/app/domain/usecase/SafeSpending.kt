package com.harcamatakip.app.domain.usecase

import com.harcamatakip.app.core.util.formatMoney
import java.time.LocalDate

/**
 * Günlük güvenli harcama limiti. [remaining] kuruş; bütçe aşıldıysa 0 ve [exceededBy] > 0.
 * [daysLeft] bugün dahil dönem sonuna kalan gün sayısıdır (en az 1).
 */
data class SafeDailyLimit(
    val remaining: Long,
    val exceededBy: Long,
    val daysLeft: Int,
    val perDay: Long
) {
    val isExceeded: Boolean get() = exceededBy > 0
}

object SafeSpending {

    fun daysLeftInclusive(today: LocalDate, periodEnd: LocalDate): Int =
        (periodEnd.toEpochDay() - today.toEpochDay() + 1).coerceAtLeast(1L).toInt()

    /** Limit yoksa (<= 0) null döner. */
    fun compute(limit: Long, spent: Long, today: LocalDate, periodEnd: LocalDate): SafeDailyLimit? {
        if (limit <= 0) return null
        val days = daysLeftInclusive(today, periodEnd)
        val remaining = limit - spent
        return if (remaining >= 0) SafeDailyLimit(remaining, 0, days, remaining / days)
        else SafeDailyLimit(0, -remaining, days, 0)
    }

    /** Tek satırlık özet metni. */
    fun summary(r: SafeDailyLimit, symbol: String): String = when {
        r.isExceeded -> "Aylık bütçeni ${r.exceededBy.formatMoney(symbol)} aştın"
        r.daysLeft == 1 -> "Bugün en fazla ${r.perDay.formatMoney(symbol)} harcayabilirsin (ayın son günü)"
        else -> "Ay sonuna kadar günde ortalama ${r.perDay.formatMoney(symbol)} harcayabilirsin (${r.daysLeft} gün kaldı)"
    }
}
