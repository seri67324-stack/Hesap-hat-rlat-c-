package com.harcamatakip.app.domain.model

import com.harcamatakip.app.data.local.CategoryEntity
import com.harcamatakip.app.core.util.TR
import com.harcamatakip.app.data.local.ExpenseEntity
import com.harcamatakip.app.data.local.LEGACY_CARD_TARGET
import java.time.LocalDate

/** Listelerde gösterilen işlem + kategorisi. */
data class TxItem(val expense: ExpenseEntity, val category: CategoryEntity?)

enum class Frequency(val label: String) {
    WEEKLY("Haftalık"),
    MONTHLY("Aylık"),
    YEARLY("Yıllık")
}

/** Ödeme yöntemi kodları. Veritabanında bu kodlar saklanır; etiketler yalnızca arayüz içindir. */
object PaymentMethod {
    const val CASH = "CASH"
    const val DEBIT_CARD = "DEBIT_CARD"
    const val CREDIT_CARD = "CREDIT_CARD"

    /** Eski sürümlerdeki tek "Kart" kodu. Yalnızca eski yedek/CSV dosyalarını okumak için tutulur. */
    const val CARD = "CARD"

    /** Arayüzde gösterilen sıra. */
    val all: List<String> = listOf(CASH, DEBIT_CARD, CREDIT_CARD)

    /** Bilinmeyen/eski kodları geçerli bir koda çevirir ("CARD" -> [LEGACY_CARD_TARGET], bilinmeyen -> CASH). */
    fun normalize(code: String?): String = when (code) {
        CASH, DEBIT_CARD, CREDIT_CARD -> code
        CARD -> LEGACY_CARD_TARGET
        else -> CASH
    }

    fun label(code: String?): String = when (normalize(code)) {
        DEBIT_CARD -> "Banka Kartı"
        CREDIT_CARD -> "Kredi Kartı"
        else -> "Nakit"
    }

    fun isCard(code: String?): Boolean = normalize(code) != CASH

    /** CSV'deki Türkçe metni kod'a çevirir; tanınmazsa null. Eski "Kart" kredi kartına eşlenir. */
    fun fromText(text: String?): String? = when (text?.trim()?.lowercase(TR)) {
        "nakit", "cash" -> CASH
        "banka kartı", "banka karti", "banka" -> DEBIT_CARD
        "kredi kartı", "kredi karti", "kredi" -> CREDIT_CARD
        "kart", "card" -> LEGACY_CARD_TARGET
        else -> null
    }

    /** Kart/hesap etiketi için en fazla uzunluk. */
    const val LABEL_MAX = 20

    fun cleanLabel(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(LABEL_MAX)
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
