package com.agsiqueira.rider

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.agsiqueira.rider.ui.theme.RIDERTheme
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    private data class Paired(val name: String, val address: String)
    private var devices by mutableStateOf<List<Paired>>(emptyList())
    private var selected by mutableStateOf<String?>(null)
    private var permission by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("Choose my paired OBDLink LX to check the connection.")
    private var identity by mutableStateOf<AdapterProtocol.Identity?>(null)
    private var bikeResult by mutableStateOf<KombiProtocol.Result?>(null)
    private var diagnosticDetails by mutableStateOf("")
    private val worker = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val socketLock = Any()
    private var activeSocket: BluetoothSocket? = null
    @Volatile private var generation = 0
    @Volatile private var foreground = false

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) refreshDevices() else {
            message = "Allow Nearby devices in my app settings, then return and refresh."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RIDERTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Column(Modifier.padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("RIDER", style = MaterialTheme.typography.headlineLarge)
                        Text("Bike connection", style = MaterialTheme.typography.titleLarge)
                        Text(message)
                        if (!permission) {
                            Button(onClick = { permissionRequest.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                                Text("Allow Bluetooth access")
                            }
                        }
                        OutlinedButton(enabled = !busy, onClick = {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }) { Text("Open Bluetooth settings") }
                        OutlinedButton(enabled = !busy, onClick = { refreshDevices() }) { Text("Refresh paired devices") }
                        devices.forEach { device ->
                            OutlinedButton(enabled = permission && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                                selected = device.address
                                identity = null
                                bikeResult = null
                                diagnosticDetails = ""
                                message = "Selected ${device.name}. Ready to check the adapter."
                            }) {
                                Text((if (selected == device.address) "Selected: " else "") + device.name + " (" + device.address.takeLast(5) + ")")
                            }
                        }
                        Button(enabled = permission && selected != null && !busy, onClick = { checkAdapter() },
                            modifier = Modifier.fillMaxWidth()) { Text("Check LX connection") }
                        Button(enabled = permission && selected != null && !busy, onClick = { checkAdapter(withBike = true) },
                            modifier = Modifier.fillMaxWidth()) { Text("Read bike status once") }
                        if (busy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { cancelCheck("Connection check cancelled.") }) { Text("Cancel") }
                        }
                        identity?.let { found ->
                            Text("Adapter verified", style = MaterialTheme.typography.titleLarge)
                            Text("ELM identity: ${found.elm}\nFirmware: ${found.firmware}\nSupply voltage: ${found.voltage}")
                            Text("The check finished and disconnected.")
                        }
                        HorizontalDivider()
                        bikeResult?.let { result ->
                            Text("Wake-up line: ${result.wakeUpLine.uppercase()}", style = MaterialTheme.typography.headlineSmall)
                            Text("Cluster: ${result.clusterIdentity ?: "unconfirmed"}")
                            Text("Check: ${result.status} (${result.stage})")
                            if (result.rawResponse.isNotEmpty()) Text("Response: ${result.rawResponse}")
                            Text(result.detail)
                            Text("Diagnostic protocol closed: ${if (result.protocolClosed) "yes" else "not confirmed"}")
                        }
                        Text("Ignition: awaiting confirmation")
                        Text("The bike check takes one read-only observation. It does not monitor in the background or infer ignition from voltage.")
                        if (diagnosticDetails.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("RIDER diagnostic check", diagnosticDetails))
                            }) { Text("Copy diagnostic details") }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        permission = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!busy) refreshDevices()
    }

    @SuppressLint("MissingPermission")
    private fun refreshDevices() {
        permission = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!permission) {
            devices = emptyList()
            selected = null
            return
        }
        try {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                devices = emptyList()
                selected = null
                message = "Turn Bluetooth on in the phone settings, then refresh."
                return
            }
            devices = adapter.bondedDevices.map { Paired(it.name ?: "Unnamed device", it.address) }
                .sortedWith(compareBy<Paired> { !it.name.contains("OBDLink", true) }.thenBy { it.name })
            if (devices.none { it.address == selected }) selected = null
            if (devices.isEmpty()) message = "No paired devices found. Pair the LX in Bluetooth settings, then refresh."
        } catch (_: SecurityException) {
            permission = false
            devices = emptyList()
            selected = null
            message = "Bluetooth access was revoked. Grant Nearby devices access to continue."
        }
    }

    @SuppressLint("MissingPermission")
    private fun checkAdapter(withBike: Boolean = false) {
        if (busy || !foreground) return
        val address = selected ?: return
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            permission = false
            return
        }
        val token = ++generation
        busy = true
        identity = null
        bikeResult = null
        diagnosticDetails = ""
        message = "Connecting to the selected adapter…"
        worker.execute {
            var socket: BluetoothSocket? = null
            val expired = AtomicBoolean(false)
            val trace = StringBuilder()
            var synchronizedStream = false
            try {
                val adapter = getSystemService(BluetoothManager::class.java)?.adapter
                    ?: throw IOException("Bluetooth is unavailable.")
                val device = adapter.getRemoteDevice(address)
                val connection = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                socket = connection
                synchronized(socketLock) {
                    if (token != generation || !foreground) throw IOException("Check cancelled.")
                    activeSocket = connection
                }
                fun <T> bounded(seconds: Long, operation: () -> T): T {
                    val completed = AtomicBoolean(false)
                    val deadline = timer.schedule({
                        if (completed.compareAndSet(false, true)) {
                            expired.set(true)
                            try { connection.close() } catch (_: IOException) { }
                        }
                    }, seconds, TimeUnit.SECONDS)
                    try {
                        val result = operation()
                        if (expired.get()) throw IOException("Adapter check timed out.")
                        return result
                    } finally {
                        completed.set(true)
                        deadline.cancel(false)
                    }
                }
                bounded(12) { connection.connect() }
                val input = connection.inputStream
                val output = connection.outputStream
                fun exchange(query: AdapterProtocol.Query): List<String> = bounded(4) {
                    if (token != generation || !foreground) throw IOException("Check cancelled.")
                    synchronizedStream = false
                    trace.append("Command: ${query.wire}\n")
                    val lines = AdapterProtocol.exchange(input, output, query)
                    synchronizedStream = true
                    trace.append(lines.joinToString("\n")).append("\n")
                    AdapterProtocol.validateReply(lines)
                }
                fun read(query: AdapterProtocol.Query) = AdapterProtocol.single(exchange(query))
                val elm = read(AdapterProtocol.Query.ID)
                val firmware = AdapterProtocol.validateFirmware(read(AdapterProtocol.Query.FIRMWARE))
                val voltage = AdapterProtocol.validateVoltage(read(AdapterProtocol.Query.VOLTAGE))
                val found = AdapterProtocol.Identity(elm, firmware, voltage)
                val bike = if (withBike) KombiProtocol.check(::exchange) {
                    synchronizedStream && token == generation && foreground && !expired.get()
                } else null
                connection.close()
                publish(token) {
                    identity = found
                    bikeResult = bike
                    diagnosticDetails = trace.toString()
                    busy = false
                    message = when {
                        bike == null -> "I can reach the LX. Adapter check passed."
                        bike.status == "decoded" && bike.protocolClosed -> "My wake-up-line read is complete."
                        else -> "My bike check ended with ${bike.status}. Ignition remains unknown."
                    }
                }
            } catch (error: Exception) {
                publish(token) {
                    busy = false
                    diagnosticDetails = trace.toString()
                    message = if (expired.get()) "Connection timed out. Close MotoScan and Windows LX tools, then try again."
                    else "Check stopped: ${error.message ?: "Bluetooth connection failed"}"
                }
            } finally {
                try { socket?.close() } catch (_: IOException) { }
                synchronized(socketLock) { if (activeSocket === socket) activeSocket = null }
            }
        }
    }

    private fun publish(token: Int, action: () -> Unit) {
        runOnUiThread { if (token == generation && foreground) action() }
    }

    private fun cancelCheck(status: String) {
        generation++
        synchronized(socketLock) {
            try { activeSocket?.close() } catch (_: IOException) { }
            activeSocket = null
        }
        busy = false
        identity = null
        bikeResult = null
        diagnosticDetails = ""
        message = status
    }

    override fun onStop() {
        foreground = false
        cancelCheck("Connection check paused. Return and start another check when ready.")
        super.onStop()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        timer.shutdownNow()
        super.onDestroy()
    }
}
