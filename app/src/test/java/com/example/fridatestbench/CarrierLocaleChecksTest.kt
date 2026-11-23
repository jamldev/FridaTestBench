package com.example.fridatestbench

import com.example.fridatestbench.checks.CarrierLocaleChecks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarrierLocaleChecksTest {
    @Test
    fun extractsMccFromValidSimOperatorCode() {
        assertEquals("310", CarrierLocaleChecks.mccFromOperator("310260"))
    }

    @Test
    fun rejectsInvalidSimOperatorCode() {
        assertNull(CarrierLocaleChecks.mccFromOperator("abc"))
        assertNull(CarrierLocaleChecks.mccFromOperator("1234"))
        assertNull(CarrierLocaleChecks.mccFromOperator(null))
    }

    @Test
    fun infersCurrencyFromCountryCode() {
        assertEquals("USD", CarrierLocaleChecks.currencyForCountry("us"))
        assertEquals("EUR", CarrierLocaleChecks.currencyForCountry("PT"))
        assertNull(CarrierLocaleChecks.currencyForCountry(null))
    }
}
