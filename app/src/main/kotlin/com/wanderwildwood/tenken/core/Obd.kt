package com.wanderwildwood.tenken.core

/**
 * The small decisions about what a car said, kept apart from the Bluetooth and the screens
 * so that each can be checked without either.
 */

/** Where a trouble code came from, which is most of what it means. */
enum class CodeKind {
    /** Confirmed, and what turns the check-engine light on. */
    STORED,

    /** Seen once and not yet confirmed. It becomes stored if it happens again. */
    PENDING,

    /** Kept by the car until it has seen for itself that the fault is gone. No tool clears it. */
    PERMANENT;

    companion object {
        /** From the OBD service the code was read with: 03, 07 or 0A. */
        fun of(service: Int?): CodeKind = when (service) {
            0x07 -> PENDING
            0x0A -> PERMANENT
            else -> STORED
        }
    }
}

data class TroubleCode(val code: String, val description: String, val kind: CodeKind)

/**
 * The order codes are shown in: what is lit first, then what is coming, then what is merely
 * remembered, and the same code once only, under the kind that matters most.
 */
fun List<TroubleCode>.forShowing(): List<TroubleCode> =
    groupBy { it.code }
        .map { (_, same) -> same.minBy { it.kind.ordinal } }
        .sortedWith(compareBy({ it.kind.ordinal }, { it.code }))

/**
 * Which computer to talk to when more than one answers.
 *
 * The emissions diagnostics this app reads live in the engine computer, so that is the
 * one: 7E8 on an 11-bit CAN car, 18DAF110 on a 29-bit one, 10 on the older K-line cars.
 * Failing those, the lowest address, which by convention is the engine's.
 */
fun chooseEcu(addresses: Set<Int>): Int? =
    ENGINE_ADDRESSES.firstOrNull { it in addresses } ?: addresses.minOrNull()

private val ENGINE_ADDRESSES = listOf(0x7E8, 0x18DAF110, 0x10)

/** What the car said to a request to clear its codes. */
sealed interface ClearAnswer {
    data object Accepted : ClearAnswer
    data class Refused(val reason: Int) : ClearAnswer

    companion object {
        /**
         * Read one line from the adapter. A positive answer is 44; a refusal is 7F 04 and a
         * reason. Either may come with a CAN header in front, so the line is read from the
         * end for the one and searched for the other.
         */
        fun of(line: String): ClearAnswer? {
            val text = line.uppercase().filter { it.isLetterOrDigit() }
            val refusal = text.indexOf("7F04")
            if (refusal >= 0 && text.length >= refusal + 6) {
                return Refused(text.substring(refusal + 4, refusal + 6).toInt(16))
            }
            if (text == "44" || (text.length in 7..8 && text.endsWith("0144"))) return Accepted
            return null
        }
    }
}

/** The reasons a car gives for refusing, by the number it gives them. */
object Refusal {
    const val CONDITIONS_NOT_CORRECT = 0x22
    const val NOT_SUPPORTED = 0x11
    const val BUSY = 0x21
}
