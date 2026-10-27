package com.wanderwildwood.tenken

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import com.fr3ts0n.ecu.EcuDataItem
import com.wanderwildwood.tenken.obd.BluetoothAdapterLink
import com.wanderwildwood.tenken.obd.Car
import com.wanderwildwood.tenken.obd.FakeElm
import com.wanderwildwood.tenken.obd.Page
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A paired device, as the adapter picker lists it. */
data class Paired(val name: String, val address: String)

/** Everything about the phone's side of things, as opposed to the car's. */
data class PhoneState(
    val bluetoothOn: Boolean = true,
    val permitted: Boolean = false,
    /** Android has stopped asking: only the app's own settings page can grant it now. */
    val blocked: Boolean = false,
    val paired: List<Paired> = emptyList(),
    /** The adapter to use, by address; empty until one is chosen. */
    val adapter: String = "",
    val adapterName: String = "",
    val usUnits: Boolean = false,
)

class CarViewModel(app: Application) : AndroidViewModel(app) {

    val car = Car()

    private val prefs = app.getSharedPreferences("tenken", Context.MODE_PRIVATE)
    private val bluetooth = app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _phone = MutableStateFlow(
        PhoneState(
            adapter = prefs.getString(KEY_ADAPTER, "").orEmpty(),
            adapterName = prefs.getString(KEY_ADAPTER_NAME, "").orEmpty(),
            usUnits = prefs.getBoolean(KEY_US_UNITS, false),
            blocked = prefs.getBoolean(KEY_BLOCKED, false),
        ),
    )
    val phone: StateFlow<PhoneState> = _phone.asStateFlow()

    init {
        car.useUnits(if (_phone.value.usUnits) EcuDataItem.SYSTEM_IMPERIAL else EcuDataItem.SYSTEM_METRIC)
        refresh()
    }

    /** Look again at what can change behind the app's back: the radio, the permission, the pairings. */
    @SuppressLint("MissingPermission")
    fun refresh() {
        val permitted = getApplication<Application>()
            .checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        val on = bluetooth?.isEnabled == true
        val paired = if (permitted && on) {
            bluetooth?.bondedDevices.orEmpty()
                .map { Paired(name = it.name ?: it.address, address = it.address) }
                // The adapters first. They call themselves some version of OBD or ELM, or
                // after the chip inside; everything else a phone is paired with comes after.
                .sortedWith(compareBy({ !looksLikeAnAdapter(it.name) }, { it.name.lowercase() }))
        } else {
            emptyList()
        }
        val withSimulator = if (BuildConfig.DEBUG) paired + Paired(SIMULATED_NAME, SIMULATED) else paired
        if (permitted && prefs.getBoolean(KEY_BLOCKED, false)) prefs.edit().putBoolean(KEY_BLOCKED, false).apply()
        _phone.update { it.copy(bluetoothOn = on, permitted = permitted, blocked = !permitted && it.blocked, paired = withSimulator) }
    }

    /**
     * The answer to the permission prompt. A "no" that Android would not even explain
     * (no rationale, and it never showed) means the prompt will not come again, so the
     * Allow button has to become a way to the app's settings page.
     */
    fun permissionAnswered(granted: Boolean, canAskAgain: Boolean) {
        val blocked = !granted && !canAskAgain
        prefs.edit().putBoolean(KEY_BLOCKED, blocked).apply()
        _phone.update { it.copy(blocked = blocked) }
        refresh()
    }

    fun choose(device: Paired) {
        prefs.edit().putString(KEY_ADAPTER, device.address).putString(KEY_ADAPTER_NAME, device.name).apply()
        _phone.update { it.copy(adapter = device.address, adapterName = device.name) }
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        val address = _phone.value.adapter
        if (address.isEmpty()) return
        if (address == SIMULATED && BuildConfig.DEBUG) {
            car.connect(FakeElm())
            return
        }
        val device = runCatching { bluetooth?.getRemoteDevice(address) }.getOrNull() ?: return
        // No cancelDiscovery() here, though AndrOBD calls it first. On Android 12 it needs
        // BLUETOOTH_SCAN, which this app never asks for because it never scans, and without it
        // the call throws -- the first real Connect on a Kompakt closed the app. This app starts
        // no discovery, so there is none of its own to cancel.
        car.connect(BluetoothAdapterLink(device))
    }

    fun disconnect() = car.disconnect()

    fun open(page: Page) = car.open(page)

    fun toggleUnits() {
        val us = !_phone.value.usUnits
        prefs.edit().putBoolean(KEY_US_UNITS, us).apply()
        _phone.update { it.copy(usUnits = us) }
        car.useUnits(if (us) EcuDataItem.SYSTEM_IMPERIAL else EcuDataItem.SYSTEM_METRIC)
    }

    override fun onCleared() {
        car.close()
    }

    private fun looksLikeAnAdapter(name: String) =
        ADAPTER_WORDS.any { name.contains(it, ignoreCase = true) }

    private companion object {
        const val KEY_ADAPTER = "adapter"
        const val KEY_ADAPTER_NAME = "adapter_name"
        const val KEY_BLOCKED = "permission_blocked"
        const val KEY_US_UNITS = "us_units"
        const val SIMULATED = "simulated"
        const val SIMULATED_NAME = "Simulated adapter"
        val ADAPTER_WORDS = listOf("obd", "elm", "vgate", "vlink", "v-link", "icar", "veepeak", "konnwei", "kiwi")
    }
}
