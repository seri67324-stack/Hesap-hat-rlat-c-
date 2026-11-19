package com.harcamatakip.app.domain

import com.harcamatakip.app.core.util.changePercent
import com.harcamatakip.app.core.util.formatSignedPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PercentFormatTest {
    @Test fun positiveHasPlusSign() = assertEquals("+%12", formatSignedPercent(12.4))
    @Test fun negativeHasMinusSign() = assertEquals("−%8", formatSignedPercent(-7.6))
    @Test fun nearZeroHasNoSign() {
        assertEquals("%0", formatSignedPercent(0.2))
        assertEquals("%0", formatSignedPercent(-0.4))
    }

    @Test
    fun changePercent_basicCases() {
        assertEquals(50.0, changePercent(150, 100)!!, 0.0001)
        assertEquals(-100.0, changePercent(0, 100)!!, 0.0001)
        assertNull(changePercent(10, 0)) // önceki dönem yoksa karşılaştırma yok
    }
}
