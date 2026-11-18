package com.wanderwildwood.tenken.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** Whatever carries characters to an ELM327 and back. */
interface Adapter {
    /** The name to show a person: the one the adapter gave itself when it was paired. */
    val name: String

    /** Blocks until the link is up, and throws if it cannot be brought up. */
    fun open(): Pair<InputStream, OutputStream>

    fun close()
}

/**
 * A Bluetooth ELM327 over the serial port profile.
 *
 * The cheap adapters are serial ports with a radio attached, and a good many of them publish
 * no service record at all, so the ordinary lookup by the SPP UUID fails on them. When it
 * does, the socket is opened on RFCOMM channel 1 directly, which is where every one of them
 * listens. That fallback is AndrOBD's, and it is the reason the Panlong connects.
 *
 * Insecure first: these adapters pair with a fixed PIN and cannot do anything more, and
 * asking for an encrypted link is what makes some of them refuse the connection.
 */
@SuppressLint("MissingPermission") // Checked by the caller before an adapter is ever made.
class BluetoothAdapterLink(private val device: BluetoothDevice) : Adapter {

    override val name: String = device.name ?: device.address

    // Held while it is still connecting too, so that close() can cut short an attempt that
    // would otherwise block for as long as the radio keeps trying.
    @Volatile private var socket: BluetoothSocket? = null
    /** One link per connection: once closed, it stays closed. */
    @Volatile private var closed = false

    override fun open(): Pair<InputStream, OutputStream> {
        val byRecord = device.createInsecureRfcommSocketToServiceRecord(SPP)
        socket = byRecord
        val opened = try {
            if (closed) throw IOException("Closed before connecting to $name")
            byRecord.also { it.connect() }
        } catch (first: IOException) {
            runCatching { byRecord.close() }
            if (closed) throw IOException("Closed while connecting to $name", first)
            try {
                val channel = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val byChannel = channel.invoke(device, 1) as BluetoothSocket
                socket = byChannel
                if (closed) {
                    runCatching { byChannel.close() }
                    throw IOException("Closed while connecting to $name")
                }
                byChannel.also { it.connect() }
            } catch (second: Exception) {
                runCatching { socket?.close() }
                throw IOException("Could not connect to $name", first)
            }
        }
        if (closed) {
            runCatching { opened.close() }
            throw IOException("Closed while connecting to $name")
        }
        return opened.inputStream to opened.outputStream
    }

    override fun close() {
        closed = true
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
