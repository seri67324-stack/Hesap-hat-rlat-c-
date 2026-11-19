package com.harcamatakip.app.domain.usecase

import com.harcamatakip.app.data.local.BudgetAlertStateEntity

/**
 * Bütçe uyarısı karar mantığı. Her (bütçe, dönem) için:
 *  - %80 eşiği en fazla 1 kez,
 *  - %100 (aşım) eşiği en fazla 1 kez bildirilir.
 * Aynı kategoride art arda işlem eklense bile durum kayıtlı olduğundan tekrar bildirim gitmez.
 */
object BudgetAlertPolicy {
    const val LEVEL_80 = 80
    const val LEVEL_100 = 100

    data class Decision(
        /** Gönderilecek bildirim seviyesi (80 / 100) veya null (bildirim yok). */
        val level: Int?,
        val newState: BudgetAlertStateEntity
    )

    fun evaluate(spent: Long, limit: Long, state: BudgetAlertStateEntity): Decision {
        if (limit <= 0) return Decision(null, state)
        val reached100 = spent >= limit
        val reached80 = spent * 5 >= limit * 4 // spent / limit >= 0.8, tam sayı hesabı
        return when {
            reached100 && !state.sent100 ->
                // %100 geçildiyse %80 uyarısı artık anlamsız: ikisi de işaretlenir
                Decision(LEVEL_100, state.copy(sent80 = true, sent100 = true))
            reached80 && !reached100 && !state.sent80 ->
                Decision(LEVEL_80, state.copy(sent80 = true))
            else -> Decision(null, state)
        }
    }
}
