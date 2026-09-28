package com.wanderwildwood.tenken.obd

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue

/**
 * An ELM327 that is not there: it answers the way a real adapter on a real car does, so the
 * whole conversation can be exercised on an emulator and in tests, with no car and no
 * Bluetooth.
 *
 * The car it pretends to be is a small petrol engine idling with its check-engine light on,
 * one stored code and one pending. The first request to clear the codes is refused the way
 * an engine that is running refuses it, and the next one is accepted, so both answers can
 * be seen.
 *
 * It is offered only in debug builds. A simulated car that could be picked by mistake is
 * the one thing an app like this must never show a person looking at their own car.
 */
class FakeElm : Adapter {

    override val name = "Simulated adapter"

    // What the adapter reads (the app's requests) and what it writes (its answers). Queues
    // rather than Java's piped streams, which die with whichever thread last wrote to them --
    // and AndrOBD writes every request from a thread of its own.
    private val requests = ByteQueue()
    private val answers = ByteQueue()

    @Volatile private var open = true
    private var headers = false
    private var clearsRefused = 0
    private var stored = listOf(0x0139)
    private var pending = listOf(0x2647)
    private var milOn = true

    /** How many clear requests to refuse before accepting one. */
    var refuseClears = 1

    override fun open(): Pair<InputStream, OutputStream> {
        Thread(::serve, "fake-elm").apply { isDaemon = true }.start()
        return answers.input to requests.output
    }

    override fun close() {
        open = false
        requests.close()
        answers.close()
    }

    private fun serve() {
        val line = StringBuilder()
        try {
            while (open) {
                val c = requests.input.read()
                if (c < 0) break
                when (val ch = c.toChar()) {
                    '\r' -> {
                        val request = line.toString().uppercase().replace(" ", "")
                        line.clear()
                        if (request.isNotEmpty()) reply(answer(request))
                    }
                    '\n' -> Unit
                    else -> line.append(ch)
                }
            }
        } catch (_: Exception) {
            // The pipe closed under us, which is what disconnecting is.
        }
    }

    private fun reply(lines: List<String>) {
        val text = lines.joinToString(separator = "\r", postfix = "\r\r>")
        answers.output.write(text.toByteArray())
    }

    /** The answer to one request, as the lines an adapter would send before its prompt. */
    internal fun answer(request: String): List<String> {
        if (request.startsWith("AT")) return listOf(at(request.removePrefix("AT")))
        val data = obd(request) ?: return listOf("NODATA")
        return listOf(if (headers) frame(data) else data)
    }

    private fun at(command: String): String = when {
        command == "Z" || command == "WS" || command == "I" -> "ELM327 v1.5"
        command == "H1" -> "OK".also { headers = true }
        command == "H0" -> "OK".also { headers = false }
        command == "DP" -> "AUTO, ISO 15765-4 (CAN 11/500)"
        command == "DPN" -> "A6"
        command == "RV" -> "13.9V"
        else -> "OK"
    }

    /** An 11-bit CAN frame from the engine: its address, a length byte, then the data. */
    private fun frame(data: String) = "7E8" + "%02X".format(data.length / 2) + data

    private fun obd(request: String): String? = when {
        request == "0100" -> "4100" + mask(SUPPORTED)
        request.length == 4 && request.startsWith("01") -> {
            val pid = request.substring(2).toInt(16)
            live(pid)?.let { "41%02X".format(pid) + it }
        }
        // Freeze frame: mode 02, a PID, and the frame number.
        // A car keeps a freeze frame only while a code is stored.
        request.startsWith("02") && stored.isEmpty() -> null
        request.length == 6 && request.startsWith("02") -> {
            val pid = request.substring(2, 4).toInt(16)
            val frame = request.substring(4)
            when {
                pid == 0 -> "4200$frame" + mask(SUPPORTED)
                pid == 2 -> "4202${frame}2647"
                else -> frozen(pid)?.let { "42%02X".format(pid) + frame + it }
            }
        }
        request == "03" -> codes("43", stored)
        request == "07" -> codes("47", pending)
        request == "0A" -> "4A00"
        request == "04" -> clear()
        // It reports no vehicle information at all, which some cars really do.
        request.startsWith("09") -> null
        else -> null
    }

    private fun clear(): String {
        if (clearsRefused < refuseClears) {
            clearsRefused++
            // 0x22: conditions not correct. What a running engine says.
            return "7F0422"
        }
        stored = emptyList()
        pending = emptyList()
        milOn = false
        return "44"
    }

    private fun codes(service: String, list: List<Int>) =
        service + "%02X".format(list.size) + list.joinToString("") { "%04X".format(it) }

    private fun live(pid: Int): String? = when (pid) {
        0x01 -> "%02X".format((if (milOn) 0x80 else 0) or (stored.size + pending.size)) + "076504"
        0x04 -> "3D"          // engine load 24 %
        0x05 -> "7F"          // coolant 87 °C
        0x06 -> "83"          // short-term fuel trim +2.3 %
        0x07 -> "86"          // long-term fuel trim +4.7 %
        0x0B -> "20"          // manifold pressure 32 kPa
        0x0C -> "0C80"        // 800 rpm
        0x0D -> "00"          // standing still
        0x0E -> "94"          // timing advance 10°
        0x0F -> "3C"          // intake air 20 °C
        0x11 -> "24"          // throttle 14 %
        else -> null
    }

    // The engine was a little colder and a little busier when the code was set.
    private fun frozen(pid: Int): String? = when (pid) {
        0x05 -> "5A"
        0x0C -> "0FA0"
        0x0D -> "1E"
        else -> live(pid)
    }

    private fun mask(pids: Set<Int>): String {
        var bits = 0L
        for (pid in pids) bits = bits or (1L shl (32 - pid))
        return "%08X".format(bits)
    }

    private companion object {
        val SUPPORTED = setOf(0x01, 0x04, 0x05, 0x06, 0x07, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x11)
    }
}

/** Bytes from any thread to any thread, with an end. */
private class ByteQueue {
    private val bytes = LinkedBlockingQueue<Int>()
    @Volatile private var closed = false

    val input = object : InputStream() {
        override fun read(): Int {
            while (true) {
                bytes.poll(50, java.util.concurrent.TimeUnit.MILLISECONDS)?.let { return it }
                if (closed) return -1
            }
        }

        override fun available(): Int = if (closed && bytes.isEmpty()) throw java.io.IOException("closed") else bytes.size
    }

    val output = object : OutputStream() {
        override fun write(b: Int) {
            if (closed) throw java.io.IOException("closed")
            bytes.put(b and 0xFF)
        }
    }

    fun close() {
        closed = true
    }
}
