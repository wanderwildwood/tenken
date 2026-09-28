package com.wanderwildwood.tenken.obd

import com.wanderwildwood.tenken.core.CodeKind
import com.wanderwildwood.tenken.core.Refusal
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The whole conversation, AndrOBD's protocol and all, against the simulated adapter.
 *
 * One test class only: the protocol keeps its data in statics, so two cars at once would
 * read each other's codes.
 */
class CarTest {

    private lateinit var car: Car
    private lateinit var fake: FakeElm

    @Before
    fun connect() {
        car = Car()
        fake = FakeElm()
        car.connect(fake)
        waitFor("the car to answer") { car.state.value.link == Link.READY }
    }

    @After
    fun disconnect() {
        car.disconnect()
        waitFor("the link to close") { car.state.value.link == Link.OFFLINE }
    }

    @Test
    fun readsTheCodesAndSaysWhichKindEachIs() {
        car.open(Page.CODES)
        waitFor("the codes") { !car.state.value.readingCodes && car.state.value.codes.size == 2 }

        val codes = car.state.value.codes
        assertEquals("P0139", codes[0].code)
        assertEquals(CodeKind.STORED, codes[0].kind)
        assertEquals("P2647", codes[1].code)
        assertEquals(CodeKind.PENDING, codes[1].kind)
        assertEquals(true, car.state.value.milOn)
    }

    @Test
    fun aRefusedClearIsReportedAndTheCodesStay() {
        car.open(Page.CODES)
        waitFor("the codes") { !car.state.value.readingCodes && car.state.value.codes.size == 2 }

        car.clear()
        waitFor("the answer") { car.state.value.cleared != null }
        assertEquals(Cleared.Refused(Refusal.CONDITIONS_NOT_CORRECT), car.state.value.cleared)
        waitFor("the codes read back") { !car.state.value.readingCodes }
        assertEquals(2, car.state.value.codes.size)

        car.clear()
        waitFor("the second answer") { car.state.value.cleared == Cleared.Done }
        waitFor("the empty list") { !car.state.value.readingCodes && car.state.value.codes.isEmpty() }
        assertEquals(false, car.state.value.milOn)
    }

    @Test
    fun readingThemAgainAsksTheCarAgain() {
        car.open(Page.CODES)
        waitFor("the codes") { !car.state.value.readingCodes && car.state.value.codes.size == 2 }

        car.readCodesAgain()
        waitFor("a second reading to start") { car.state.value.readingCodes }
        waitFor("it to finish") { !car.state.value.readingCodes }
        assertEquals(2, car.state.value.codes.size)
    }

    @Test
    fun liveDataReadsTheEngine() {
        car.open(Page.LIVE)
        waitFor("engine RPM") { reading("Engine RPM") != null }
        assertEquals("800", reading("Engine RPM")!!.value.substringBefore('.'))
    }

    @Test
    fun theFreezeFrameIsTheMomentTheCodeWasSet() {
        car.open(Page.FREEZE)
        waitFor("the frozen engine speed") { reading("Engine RPM") != null }
        // 1000 rpm and 50 degrees frozen, against 800 and 87 live: these are the frame's own
        // numbers, not the engine's present ones.
        assertEquals("1000", reading("Engine RPM")!!.value.substringBefore('.'))
        waitFor("the frozen coolant") { reading("Coolant temperature") != null }
        assertEquals("50", reading("Coolant temperature")!!.value.substringBefore('.'))
    }

    @Test
    fun aCarThatReportsNoVehicleInformationGivesAnEmptyList() {
        car.open(Page.INFO)
        Thread.sleep(2500)
        assertTrue(car.state.value.readings.isEmpty())
        assertFalse(car.state.value.carSilent)
    }

    private fun reading(label: String) =
        car.state.value.readings.firstOrNull { it.label.contains(label, ignoreCase = true) }

    private fun waitFor(what: String, timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Gave up waiting for $what. State: ${car.state.value}")
    }
}
