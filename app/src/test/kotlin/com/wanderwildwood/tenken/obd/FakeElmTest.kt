package com.wanderwildwood.tenken.obd

import org.junit.Assert.assertEquals
import org.junit.Test

/** The simulator is only worth testing against if it answers the way a car does. */
class FakeElmTest {

    @Test
    fun itAnswersWithAndWithoutHeaders() {
        val elm = FakeElm()
        assertEquals(listOf("410C0C80"), elm.answer("010C"))
        elm.answer("ATH1")
        assertEquals(listOf("7E804410C0C80"), elm.answer("010C"))
    }

    @Test
    fun itRefusesTheFirstClearAndAcceptsTheNext() {
        val elm = FakeElm()
        assertEquals(listOf("7F0422"), elm.answer("04"))
        assertEquals(listOf("44"), elm.answer("04"))
        assertEquals(listOf("4300"), elm.answer("03"))
    }

    @Test
    fun theFreezeFrameCarriesItsFrameNumber() {
        val elm = FakeElm()
        assertEquals(listOf("420C000FA0"), elm.answer("020C00"))
    }
}
