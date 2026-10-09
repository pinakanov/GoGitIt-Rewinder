package com.gogitit.rewinder

import com.gogitit.rewinder.pipeline.RegexSensitiveFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SensitiveFilterTest {
    private val filter = RegexSensitiveFilter()

    private fun assertRedacted(input: String, secret: String) {
        val result = filter.filter(input)
        assertFalse(result.text.contains(secret), "leaked '$secret' in: ${result.text}")
        assertTrue(result.redactedCount > 0)
    }

    @Test fun passwords() = assertRedacted("ok so the wifi password is hunter2 got it", "hunter2")
    @Test fun pins() = assertRedacted("my PIN: 4821", "4821")
    @Test fun emails() = assertRedacted("send it to jc.sample@example.com later", "jc.sample@example.com")
    @Test fun cards() = assertRedacted("card is 4111 1111 1111 1111 exp soon", "4111 1111 1111 1111")
    @Test fun phMobile() = assertRedacted("call me at 0917 123 4567", "123 4567")
    @Test fun phMobileIntl() = assertRedacted("or +63 917 123 4567", "917 123 4567")
    @Test fun longIds() = assertRedacted("student number 202310456", "202310456")

    @Test
    fun leavesOrdinarySpeechAlone() {
        val text = "Meet at 3 near room 402, bring 2 copies of the 10 page draft."
        val result = filter.filter(text)
        assertEquals(text, result.text)
        assertEquals(0, result.redactedCount)
    }
}
