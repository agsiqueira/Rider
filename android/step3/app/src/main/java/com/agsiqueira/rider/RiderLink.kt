package com.agsiqueira.rider

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
internal class RiderLink(device: BluetoothDevice, private val timer: ScheduledExecutorService,
                         private val cancelled: () -> Boolean,
                         private val trace: (String, Map<String, Any?>) -> Unit) : Closeable {
    private val socket = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
    private val timedOut = AtomicBoolean(false)
    private var synchronizedStream = false

    private fun <T> bounded(seconds: Long, action: () -> T): T {
        if (cancelled()) throw IOException("Operation cancelled.")
        val completed = AtomicBoolean(false)
        val timeout = timer.schedule({
            if (completed.compareAndSet(false, true)) {
                timedOut.set(true)
                close()
            }
        }, seconds, TimeUnit.SECONDS)
        try {
            val value = action()
            if (timedOut.get()) throw IOException("Bluetooth operation timed out.")
            if (cancelled()) throw IOException("Operation cancelled.")
            return value
        } finally {
            completed.set(true)
            timeout.cancel(false)
        }
    }

    fun connect() = bounded(12) { socket.connect() }
    fun query(query: AdapterProtocol.Query): List<String> = bounded(4) {
        synchronizedStream = false
        trace("command", mapOf("command" to query.wire))
        val lines = AdapterProtocol.exchange(socket.inputStream, socket.outputStream, query)
        synchronizedStream = true
        trace("reply", mapOf("command" to query.wire, "lines" to lines))
        AdapterProtocol.validateReply(lines)
    }
    fun identity(): AdapterProtocol.Identity {
        val elm = AdapterProtocol.single(query(AdapterProtocol.Query.ID))
        val firmware = AdapterProtocol.validateFirmware(AdapterProtocol.single(query(AdapterProtocol.Query.FIRMWARE)))
        val voltage = AdapterProtocol.validateVoltage(AdapterProtocol.single(query(AdapterProtocol.Query.VOLTAGE)))
        return AdapterProtocol.Identity(elm, firmware, voltage)
    }
    fun bike(): KombiProtocol.Result = KombiProtocol.check(::query) {
        synchronizedStream && !timedOut.get() && !cancelled()
    }
    override fun close() { try { socket.close() } catch (_: IOException) { } }
}
