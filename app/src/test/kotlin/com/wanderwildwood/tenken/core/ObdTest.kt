package com.wanderwildwood.tenken.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdTest {

    @Test
    fun theKindComesFromTheServiceTheCodeWasReadWith() {
        assertEquals(CodeKind.STORED, CodeKind.of(0x03))
        assertEquals(CodeKind.PENDING, CodeKind.of(0x07))
        assertEquals(CodeKind.PERMANENT, CodeKind.of(0x0A))
        assertEquals(CodeKind.STORED, CodeKind.of(null))
    }

    @Test
    fun aCodeReadTwiceIsShownOnceUnderItsMostPressingKind() {
        val shown = listOf(
            TroubleCode("P2647", "rocker arm", CodeKind.PENDING),
            TroubleCode("P0420", "catalyst", CodeKind.PERMANENT),
            TroubleCode("P0139", "O2 sensor", CodeKind.STORED),
            TroubleCode("P0420", "catalyst", CodeKind.STORED),
        ).forShowing()

        assertEquals(listOf("P0139", "P0420", "P2647"), shown.map { it.code })
        assertEquals(CodeKind.STORED, shown.first { it.code == "P0420" }.kind)
    }

    @Test
    fun theEngineIsChosenWhenSeveralComputersAnswer() {
        assertEquals(0x7E8, chooseEcu(setOf(0x7E9, 0x7E8, 0x7EA)))
        assertEquals(0x18DAF110, chooseEcu(setOf(0x18DAF118, 0x18DAF110)))
        assertEquals(0x10, chooseEcu(setOf(0x10, 0x18)))
        assertEquals(0x7E9, chooseEcu(setOf(0x7EA, 0x7E9)))
        assertNull(chooseEcu(emptySet()))
    }

    @Test
    fun clearAnswersAreReadWithOrWithoutAHeader() {
        assertEquals(ClearAnswer.Accepted, ClearAnswer.of("44"))
        assertEquals(ClearAnswer.Accepted, ClearAnswer.of("7E80144"))
        assertEquals(ClearAnswer.Refused(0x22), ClearAnswer.of("7F0422"))
        assertEquals(ClearAnswer.Refused(0x22), ClearAnswer.of("7E8037F0422"))
        assertEquals(ClearAnswer.Refused(0x22), ClearAnswer.of("7F 04 22"))
        // The echo of the request, the prompt, and the adapter's own words are not answers.
        assertNull(ClearAnswer.of("04"))
        assertNull(ClearAnswer.of(">"))
        assertNull(ClearAnswer.of("NODATA"))
        assertNull(ClearAnswer.of("SEARCHING..."))
    }
}
