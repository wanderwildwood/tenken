package com.wanderwildwood.tenken.obd

import com.fr3ts0n.ecu.Conversion
import com.fr3ts0n.ecu.EcuCodeItem
import com.fr3ts0n.ecu.EcuDataItem
import com.fr3ts0n.ecu.EcuDataPv
import com.fr3ts0n.ecu.prot.obd.ElmProt
import com.fr3ts0n.ecu.prot.obd.ObdProt
import com.fr3ts0n.prot.StreamHandler
import com.fr3ts0n.prot.TelegramListener
import com.fr3ts0n.prot.TelegramWriter
import com.fr3ts0n.pvs.PvList
import com.wanderwildwood.tenken.core.ClearAnswer
import com.wanderwildwood.tenken.core.CodeKind
import com.wanderwildwood.tenken.core.TroubleCode
import com.wanderwildwood.tenken.core.chooseEcu
import com.wanderwildwood.tenken.core.forShowing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** How far along the road to the car the conversation has got. */
enum class Link {
    /** Nothing open. */
    OFFLINE,

    /** Opening the Bluetooth link to the adapter. */
    CONNECTING,

    /** The adapter is answering, and is asking the car. */
    ASKING,

    /** The car is answering. */
    READY,
}

/** Why the last attempt ended, when it ended badly. */
enum class Failure { COULD_NOT_CONNECT, LOST }

/** Which of the car's services is being read. */
enum class Page(val service: Int) {
    NONE(ObdProt.OBD_SVC_NONE),
    CODES(ObdProt.OBD_SVC_READ_CODES),
    LIVE(ObdProt.OBD_SVC_DATA),
    FREEZE(ObdProt.OBD_SVC_FREEZEFRAME),
    INFO(ObdProt.OBD_SVC_VEH_INFO),
}

/** One line of numbers: what it is, what it reads, and in what. */
data class Reading(val label: String, val value: String, val unit: String)

/** How a request to clear the codes ended. */
sealed interface Cleared {
    data object Done : Cleared
    data class Refused(val reason: Int) : Cleared

    /** The adapter took the request and the car said nothing either way. */
    data object NoAnswer : Cleared
}

data class CarState(
    val link: Link = Link.OFFLINE,
    val adapter: String? = null,
    /** The adapter is up but the car will not answer it: the key is off, most often. */
    val carSilent: Boolean = false,
    val failure: Failure? = null,
    val page: Page = Page.NONE,
    /** True from asking for the codes until the car has answered all three kinds. */
    val readingCodes: Boolean = false,
    val codes: List<TroubleCode> = emptyList(),
    /** Null until the car has said. */
    val milOn: Boolean? = null,
    val clearing: Boolean = false,
    val cleared: Cleared? = null,
    val readings: List<Reading> = emptyList(),
)

/**
 * The conversation with one car through one adapter.
 *
 * AndrOBD's protocol classes do the talking — the ELM327's commands, the OBD services, and
 * the tables of what every number and every code means. This class decides what to ask and
 * turns what comes back into [state].
 *
 * Every call into the protocol goes through one worker thread, so nothing on the main
 * thread ever waits on a car. The lists the protocol fills are read once a second rather than
 * on every change, which on this screen is the difference between a steady page and one
 * that repaints forty times while a single row of numbers arrives.
 */
class Car {

    private val _state = MutableStateFlow(CarState())
    val state: StateFlow<CarState> = _state.asStateFlow()

    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "obd").apply { isDaemon = true } }
    private var ticker: ScheduledFuture<*>? = null

    private var adapter: Adapter? = null
    private var stream: StreamHandler? = null

    /** A generation per connection, so a stale thread's last word is ignored. */
    @Volatile private var generation = 0

    // What was last sent, and whether the MIL answer is still to come.
    private val lastRequest = AtomicReference("")
    @Volatile private var awaitingMil = false
    @Volatile private var clearAnswer: ClearAnswer? = null
    @Volatile private var clearReplied = false

    private val events = PropertyChangeListener { event -> onEvent(event) }

    /** Watches every request the protocol sends, so the answers can be matched to it. */
    private val requests = object : TelegramWriter {
        override fun writeTelegram(buffer: CharArray): Int = seen(buffer)
        override fun writeTelegram(buffer: CharArray, type: Int, id: Any?): Int = seen(buffer)
        private fun seen(buffer: CharArray): Int {
            lastRequest.set(String(buffer).uppercase().replace(" ", ""))
            return buffer.size
        }
    }

    init {
        elm.addPropertyChangeListener(events)
    }

    fun connect(to: Adapter) {
        worker.execute {
            closeLink()
            val mine = ++generation
            adapter = to
            _state.value = CarState(link = Link.CONNECTING, adapter = to.name, page = _state.value.page)

            val (input, output) = try {
                to.open()
            } catch (e: Exception) {
                if (mine == generation) {
                    adapter = null
                    _state.update { it.copy(link = Link.OFFLINE, failure = Failure.COULD_NOT_CONNECT) }
                }
                return@execute
            }

            val handler = StreamHandler(input, output)
            handler.setMessageHandler(TelegramListener { line -> heard(String(line)); elm.handleTelegram(line) })
            stream = handler
            // The watcher first, so a request is on record before it is on the wire, and a
            // quick answer can never arrive ahead of it.
            elm.addTelegramWriter(requests)
            elm.addTelegramWriter(handler)

            Thread({
                handler.run()
                // The read loop ends only when the link does.
                worker.execute {
                    if (mine == generation) {
                        closeLink()
                        _state.update { it.copy(link = Link.OFFLINE, failure = Failure.LOST, clearing = false) }
                    }
                }
            }, "obd-rx").apply { isDaemon = true }.start()

            _state.update { it.copy(link = Link.ASKING) }
            elm.setService(ObdProt.OBD_SVC_NONE, true)
            elm.reset()
        }
    }

    fun disconnect() {
        worker.execute {
            generation++
            closeLink()
            _state.update { CarState(page = it.page) }
        }
    }

    /** Show one of the car's services, or [Page.NONE] to stop asking for anything. */
    fun open(page: Page) {
        worker.execute {
            _state.update {
                it.copy(page = page, readings = emptyList(), cleared = if (page == Page.CODES) it.cleared else null)
            }
            ask(page)
        }
    }

    /** Stop polling while nobody is looking, without dropping the link. */
    fun pause() = worker.execute { if (_state.value.link == Link.READY) elm.setService(ObdProt.OBD_SVC_NONE, false) }

    fun resume() = worker.execute { ask(_state.value.page) }

    fun readCodesAgain() = worker.execute { _state.update { it.copy(cleared = null) }; ask(Page.CODES) }

    /**
     * Ask the car to forget its codes, and then read them again.
     *
     * The answer is waited for and reported. AndrOBD sent the request and went straight on to
     * reading the codes back, so a car that refused — as every Honda does with the engine
     * running — left the old codes on the screen and no word of why.
     */
    fun clear() {
        worker.execute {
            if (_state.value.link != Link.READY) return@execute
            _state.update { it.copy(clearing = true, cleared = null) }
            clearAnswer = null
            clearReplied = false
            elm.setService(ObdProt.OBD_SVC_CLEAR_CODES, false)

            val deadline = System.currentTimeMillis() + CLEAR_WAIT_MS
            while (!clearReplied && System.currentTimeMillis() < deadline) Thread.sleep(50)

            val result = when (val answer = clearAnswer) {
                ClearAnswer.Accepted -> Cleared.Done
                is ClearAnswer.Refused -> Cleared.Refused(answer.reason)
                null -> Cleared.NoAnswer
            }
            _state.update { it.copy(clearing = false, cleared = result) }
            ask(Page.CODES)
        }
    }

    /** Metric or US units for everything read from here on. */
    fun useUnits(system: Int) {
        worker.execute { EcuDataItem.cnvSystem = system; snapshot() }
    }

    fun close() {
        worker.execute {
            generation++
            closeLink()
            elm.removePropertyChangeListener(events)
        }
        worker.shutdown()
    }

    // --- on the worker ---

    private fun ask(page: Page) {
        ticker?.cancel(false)
        ticker = null
        if (_state.value.link != Link.READY) return

        if (page == Page.CODES) {
            awaitingMil = true
            _state.update { it.copy(readingCodes = true) }
        }
        // The protocol ignores a request for the service it is already on, which would make
        // "read them again" a press that does nothing. Stepping through none first makes it ask.
        if (elm.service == page.service) elm.setService(ObdProt.OBD_SVC_NONE, false)
        if (page == Page.FREEZE) {
            // Frame 0, the one every car keeps: the moment its first stored code was set.
            elm.setFreezeFrame_Id(0)
        } else {
            elm.setService(page.service, true)
        }
        if (page != Page.NONE) {
            ticker = worker.scheduleWithFixedDelay(::snapshot, 300, SNAPSHOT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private fun closeLink() {
        ticker?.cancel(false)
        ticker = null
        elm.setService(ObdProt.OBD_SVC_NONE, true)
        stream?.let { elm.removeTelegramWriter(it) }
        elm.removeTelegramWriter(requests)
        stream = null
        adapter?.close()
        adapter = null
    }

    /** Copy what the protocol has gathered into [state], if it differs from what is there. */
    private fun snapshot() {
        val page = _state.value.page
        val codes = if (page == Page.CODES) codes() else _state.value.codes
        val readings = when (page) {
            Page.LIVE, Page.FREEZE -> readings(ObdProt.PidPvs)
            Page.INFO -> readings(ObdProt.VidPvs)
            else -> emptyList()
        }
        _state.update {
            if (it.codes == codes && it.readings == readings) it else it.copy(codes = codes, readings = readings)
        }
    }

    // --- on the protocol's own threads ---

    /** Every line the adapter sends, seen before the protocol acts on it. */
    private fun heard(line: String) {
        val request = lastRequest.get()
        if (request == "04") {
            ClearAnswer.of(line)?.let { clearAnswer = it }
            if ('>' in line) clearReplied = true
        }
        // The codes come as three requests, 03, 07 and 0A, always in that order. The prompt
        // after the last is the end of the reading, whatever the car said to it.
        if (request == "0A" && '>' in line && _state.value.readingCodes) {
            worker.schedule({
                snapshot()
                _state.update { it.copy(readingCodes = false) }
            }, 100, TimeUnit.MILLISECONDS)
        }
    }

    private fun onEvent(event: PropertyChangeEvent) {
        when (event.propertyName) {
            ElmProt.PROP_STATUS -> onStatus(event.newValue as ElmProt.STAT)

            ElmProt.PROP_ECU_ADDRESS -> {
                @Suppress("UNCHECKED_CAST")
                val addresses = (event.newValue as? Set<Int>).orEmpty().toSet()
                if (addresses.size > 1) chooseEcu(addresses)?.let { worker.execute { elm.setEcuAddress(it) } }
            }

            ObdProt.PROP_NUM_CODES -> if (awaitingMil) {
                // The first count after asking is the answer to 01 01, whose top bit is the lamp.
                awaitingMil = false
                val value = (event.newValue as? Int) ?: return
                _state.update { it.copy(milOn = value and 0x80 != 0) }
            }
        }
    }

    private fun onStatus(status: ElmProt.STAT) {
        when (status) {
            ElmProt.STAT.ECU_DETECTED, ElmProt.STAT.CONNECTED -> {
                val wasReady = _state.value.link == Link.READY
                _state.update { if (it.link == Link.OFFLINE) it else it.copy(link = Link.READY, carSilent = false) }
                // The page someone opened before the car answered is asked for now.
                if (!wasReady && _state.value.link == Link.READY) worker.execute { ask(_state.value.page) }
            }
            // UNABLE TO CONNECT, and the bus errors: the adapter is there, the car is not.
            ElmProt.STAT.DISCONNECTED, ElmProt.STAT.BUSERROR ->
                _state.update { if (it.link == Link.OFFLINE) it else it.copy(carSilent = true) }
            else -> Unit
        }
    }

    private fun codes(): List<TroubleCode> = synchronized(ObdProt.tCodes) {
        ObdProt.tCodes.values.toList()
    }.mapNotNull { item ->
        val code = item as? EcuCodeItem ?: return@mapNotNull null
        val text = code.get(EcuCodeItem.FID_CODE)?.toString() ?: return@mapNotNull null
        // The protocol puts a P0000 in the list to mean "none"; the screen says that in words.
        if (text.trimStart('P', 'C', 'B', 'U').all { it == '0' }) return@mapNotNull null
        TroubleCode(
            code = text,
            description = code.get(EcuCodeItem.FID_DESCRIPT)?.toString().orEmpty(),
            kind = CodeKind.of(code.get(EcuCodeItem.FID_STATUS) as? Int),
        )
    }.forShowing()

    private fun readings(list: PvList): List<Reading> = synchronized(list) {
        list.values.toList()
    }.filterIsInstance<EcuDataPv>()
        // The engine's numbers first and the monitor status words after them: what is read
        // with the engine running is the first thing on the page, not a page and a half down.
        .sortedWith(
            compareBy(
                { it.getAsInt(EcuDataPv.FID_PID) in STATUS_PIDS },
                { it.getAsInt(EcuDataPv.FID_PID) },
                { it.getAsInt(EcuDataPv.FID_OFS) },
            ),
        )
        .map(::reading)

    private fun reading(pv: EcuDataPv): Reading {
        val raw = pv.get(EcuDataPv.FID_VALUE)
        val value = when {
            raw == null -> "–"
            else -> runCatching {
                @Suppress("UNCHECKED_CAST")
                val conversion = (pv.get(EcuDataPv.FID_CNVID) as? Array<Conversion?>)?.get(EcuDataItem.cnvSystem)
                if (conversion != null && raw is Number) {
                    conversion.physToPhysFmtString(raw, pv.get(EcuDataPv.FID_FORMAT) as? String)
                } else {
                    raw.toString()
                }
            }.getOrDefault(raw.toString())
        }
        return Reading(
            label = pv.get(EcuDataPv.FID_DESCRIPT)?.toString().orEmpty(),
            value = value.trim(),
            unit = runCatching { pv.units }.getOrNull().orEmpty(),
        )
    }

    companion object {
        /** One protocol per process: AndrOBD keeps its data lists in statics, so there can only be one car. */
        val elm = ElmProt()

        /** Monitor status since the codes were cleared (01), and for this drive (41). */
        private val STATUS_PIDS = setOf(0x01, 0x41)

        private const val SNAPSHOT_MS = 1000L
        private const val CLEAR_WAIT_MS = 5000L
    }
}
