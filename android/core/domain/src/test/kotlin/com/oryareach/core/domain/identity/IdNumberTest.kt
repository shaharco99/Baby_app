package com.oryareach.core.domain.identity

import io.kotest.matchers.shouldBe
import org.junit.Test

class IdNumberTest {

    @Test
    fun `accepts numbers whose check digit is right`() {
        isValidIsraeliId("123456782") shouldBe true
        isValidIsraeliId("000000018") shouldBe true
    }

    @Test
    fun `pads a short number with leading zeros before checking`() {
        isValidIsraeliId("18") shouldBe true
    }

    @Test
    fun `rejects a mistyped digit`() {
        isValidIsraeliId("123456789") shouldBe false
        isValidIsraeliId("123456783") shouldBe false
    }

    @Test
    fun `rejects empty, all zeros, too long and non-digits`() {
        isValidIsraeliId("") shouldBe false
        isValidIsraeliId("000000000") shouldBe false
        isValidIsraeliId("1234567820") shouldBe false
        isValidIsraeliId("12345678a") shouldBe false
    }

    @Test
    fun `input keeps digits only, nine at most`() {
        idNumberInput("12-345 678 29") shouldBe "123456782"
        idNumberInput("abc") shouldBe ""
    }
}
