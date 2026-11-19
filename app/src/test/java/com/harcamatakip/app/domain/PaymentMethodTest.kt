package com.harcamatakip.app.domain

import com.harcamatakip.app.domain.model.PaymentMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentMethodTest {

    @Test
    fun normalize_mapsLegacyCardToCreditAndUnknownToCash() {
        assertEquals(PaymentMethod.CREDIT_CARD, PaymentMethod.normalize("CARD"))
        assertEquals(PaymentMethod.CASH, PaymentMethod.normalize("???"))
        assertEquals(PaymentMethod.CASH, PaymentMethod.normalize(null))
        assertEquals(PaymentMethod.DEBIT_CARD, PaymentMethod.normalize(PaymentMethod.DEBIT_CARD))
    }

    @Test
    fun labels_areTurkish() {
        assertEquals("Nakit", PaymentMethod.label(PaymentMethod.CASH))
        assertEquals("Banka Kartı", PaymentMethod.label(PaymentMethod.DEBIT_CARD))
        assertEquals("Kredi Kartı", PaymentMethod.label(PaymentMethod.CREDIT_CARD))
        assertEquals("Kredi Kartı", PaymentMethod.label("CARD"))
    }

    @Test
    fun isCard_onlyForCards() {
        assertFalse(PaymentMethod.isCard(PaymentMethod.CASH))
        assertTrue(PaymentMethod.isCard(PaymentMethod.DEBIT_CARD))
        assertTrue(PaymentMethod.isCard("CARD"))
    }

    @Test
    fun fromText_readsCsvValues() {
        assertEquals(PaymentMethod.CASH, PaymentMethod.fromText("Nakit"))
        assertEquals(PaymentMethod.DEBIT_CARD, PaymentMethod.fromText("Banka Kartı"))
        assertEquals(PaymentMethod.CREDIT_CARD, PaymentMethod.fromText("KREDİ KARTI")) // Türkçe büyük harf
        assertEquals(PaymentMethod.CREDIT_CARD, PaymentMethod.fromText("Kart"))         // eski CSV
        assertNull(PaymentMethod.fromText("çek"))
        assertNull(PaymentMethod.fromText(null))
    }

    @Test
    fun cleanLabel_trimsCollapsesSpacesAndLimitsLength() {
        assertEquals("Garanti •1234", PaymentMethod.cleanLabel("  Garanti    •1234  "))
        assertEquals(PaymentMethod.LABEL_MAX, PaymentMethod.cleanLabel("a".repeat(50)).length)
    }
}
