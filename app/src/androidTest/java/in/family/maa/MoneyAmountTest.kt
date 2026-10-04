package `in`.family.maa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class MoneyAmountTest {
    @Test fun acceptsCompleteNumericAmountsAndValidRegionalGrouping() {
        val corpus = mapOf(
            "1,000" to "1000", "1,00,000" to "100000", "100,000.00" to "100000",
            "₹ 1,234.50" to "1234.5", "Rs. 125.25" to "125.25", "INR 500" to "500",
            "१२३.५० रुपये" to "123.5", "₹१००" to "100", "0.01" to "0.01",
            "100 rupees" to "100", "rupees 100" to "100", "00100" to "100",
            "100 रुपये 50 पैसे" to "100.5", "100 rupees and 5 paise" to "100.05", "50 paise" to "0.5"
        )
        corpus.forEach { (input, expected) ->
            val actual = MoneyAmount.parse(input)
            assertEquals(input, BigDecimal(expected), actual)
            assertTrue(input, actual!!.scale() in 0..2)
        }
    }
    @Test fun acceptsSupportedEnglishAndHindiWholeNumberSpeech() {
        val corpus = mapOf(
            "one hundred" to "100", "one hundred and fifty" to "150", "twenty-five rupees" to "25",
            "one thousand two hundred thirty four" to "1234", "one lakh" to "100000",
            "one hundred thousand" to "100000", "one thousand and one" to "1001",
            "one point zero five" to "1.05", "one point fifty" to "1.5",
            "one hundred rupees and fifty paise" to "100.5",
            "पांच सौ रुपये" to "500", "पाँच हजार" to "5000", "एक हजार दो सौ पचास" to "1250",
            "बारह सौ पचास" to "1250", "पच्चीस रुपये" to "25", "एक लाख" to "100000",
            "एक सौ रुपये और पाँच पैसे" to "100.05", "पचास पैसे" to "0.5",
            "एक दशमलव शून्य पाँच" to "1.05", "एक दशमलव पचास" to "1.5"
        )
        corpus.forEach { (input, expected) -> assertEquals(input, BigDecimal(expected), MoneyAmount.parse(input)) }
    }
    @Test fun rejectsAuditRegressionsAndConflictingNumbersInsteadOfExtractingASubstring() {
        val corpus = listOf(
            "-50", "+50", "−50", "125.999", "100.999", "not 100, 200", "not100200", "100 or 200",
            "100 200", "100 and 200", "one and two", "one two", "100 rupees and 200 rupees", "not one hundred",
            "₹100 extra", "100?", "1e3", "NaN", "Infinity", "1,00", "1,000,00", "1,2,000", "100,00",
            "0", "0.00", "zero", "शून्य", "100000.01", "1,00,001", "one lakh one", "एक लाख एक",
            "one hundred thousand and one", "one million", "one hundred hundred", "one thousand two thousand",
            "one thousand one lakh", "one hundred and", "one thousand and", "one point one two three",
            "one point five point five", "100 rupees 100 paise", "500 paise", "50%", "50\n100", "\u202e100",
            "one hundred-50", "बीस पाँच", "साढ़े तीन सौ", "ढाई सौ", "पाँच या दस", "100.", ".50"
        )
        corpus.forEach { assertNull("Must ask again for: $it", MoneyAmount.parse(it)) }
    }
    @Test fun everyAcceptedBoundaryIsPositiveBoundedAndCanonical() {
        listOf("0.01", "1", "99999.99", "100000", "100000.00", "zero rupees and one paisa").forEach {
            val amount = MoneyAmount.parse(it)
            assertNotNull(it, amount)
            assertTrue(it, amount!! > BigDecimal.ZERO && amount <= BigDecimal("100000"))
            assertTrue(it, amount.scale() in 0..2)
        }
    }
}
