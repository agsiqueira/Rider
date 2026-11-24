package com.agsiqueira.rider

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
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
import androidx.core.content.FileProvider
import com.agsiqueira.rider.ui.theme.RIDERTheme
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.Future

class MainActivity : ComponentActivity() {
    private data class Paired(val name: String, val address: String)
    private enum class Operation { ADAPTER, BIKE, ON_OFF, OFF_ON }
    private var devices by mutableStateOf<List<Paired>>(emptyList())
    private var selected by mutableStateOf<String?>(null)
    private var permission by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var direction by mutableStateOf(IgnitionTest.Mode.ON_OFF)
    private var prompt by mutableStateOf("Select my paired OBDLink LX.")
    private var summary by mutableStateOf("")
    private var inferred by mutableStateOf("unknown")
    private var observation by mutableStateOf<KombiProtocol.Result?>(null)
    private var identity by mutableStateOf<AdapterProtocol.Identity?>(null)
    private var lastLog by mutableStateOf<File?>(null)
    private val worker = Executors.newSingleThreadExecutor()
    private val timer = Executors.newSingleThreadScheduledExecutor()
    private val connectionLock = Any()
    private var activeLink: RiderLink? = null
    private var job: Future<*>? = null
    @Volatile private var generation = 0
    @Volatile private var foreground = false
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) refreshDevices() else prompt = "Allow Nearby devices in my app settings, then return and refresh."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RIDERTheme {
                Scaffold(Modifier.fillMaxSize()) { padding ->
                    Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("RIDER", style = MaterialTheme.typography.headlineLarge)
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(prompt, style = MaterialTheme.typography.headlineSmall)
                                Text("Inferred ignition: ${inferred.uppercase()}")
                                if (summary.isNotEmpty()) Text(summary)
                                if (busy) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                    Button(onClick = { cancelOperation("Test cancelled. Ignition is unknown.") }) { Text("Stop test") }
                                }
                            }
                        }
                        if (!permission) Button(onClick = { permissionRequest.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                            Text("Allow Bluetooth access")
                        }
                        if (selected != null) {
                            Text("Selected: ${devices.find { it.address == selected }?.name ?: "paired device"}")
                            OutlinedButton(enabled = !busy, onClick = { selected = null }) { Text("Change adapter") }
                        } else {
                            OutlinedButton(enabled = !busy, onClick = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) { Text("Open Bluetooth settings") }
                            OutlinedButton(enabled = !busy, onClick = { refreshDevices() }) { Text("Refresh paired devices") }
                            devices.forEach { device ->
                                OutlinedButton(enabled = permission && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                                    selected = device.address
                                    observation = null
                                    identity = null
                                    inferred = "unknown"
                                    prompt = "Selected ${device.name}. Choose a check or ignition test."
                                }) { Text("${device.name} (${device.address.takeLast(5)})") }
                            }
                        }
                        Text("Test direction", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(enabled = !busy, onClick = { direction = IgnitionTest.Mode.ON_OFF }) {
                                Text((if (direction == IgnitionTest.Mode.ON_OFF) "Selected: " else "") + "ON → OFF")
                            }
                            OutlinedButton(enabled = !busy, onClick = { direction = IgnitionTest.Mode.OFF_ON }) {
                                Text((if (direction == IgnitionTest.Mode.OFF_ON) "Selected: " else "") + "OFF → ON")
                            }
                        }
                        Button(enabled = permission && selected != null && !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                            begin(if (direction == IgnitionTest.Mode.ON_OFF) Operation.ON_OFF else Operation.OFF_ON)
                        }) { Text("Start ignition test") }
                        OutlinedButton(enabled = permission && selected != null && !busy, onClick = { begin(Operation.BIKE) }) { Text("Read bike status once") }
                        OutlinedButton(enabled = permission && selected != null && !busy, onClick = { begin(Operation.ADAPTER) }) { Text("Check LX connection") }
                        if (lastLog != null && !busy) {
                            Button(onClick = { shareLog() }) { Text("Share last test log") }
                            Text("Saved: ${lastLog!!.name}")
                        }
                        identity?.let { Text("LX: ${it.firmware}\nInitial supply voltage: ${it.voltage}") }
                        observation?.let { Text("Raw wake-up line: ${it.wakeUpLine.uppercase()}\nCluster: ${it.clusterIdentity ?: "unconfirmed"}\nRead: ${it.status}\nProtocol closed: ${it.protocolClosed}") }
                        Text("Parked test, engine OFF. Keep this screen open. Up to 12 samples; five seconds between successful reads. The session stops when the requested transition is confirmed.")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        if (!busy) {
            refreshDevices()
            lastLog = File(filesDir, "diagnostics").listFiles()?.filter { it.name.endsWith(".jsonl") }
                ?.maxByOrNull { it.lastModified() }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshDevices() {
        permission = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (!permission) { devices = emptyList(); selected = null; return }
        try {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                devices = emptyList(); selected = null
                prompt = "Turn Bluetooth on in phone settings, then refresh."
                return
            }
            devices = adapter.bondedDevices.map { Paired(it.name ?: "Unnamed device", it.address) }
                .sortedWith(compareBy<Paired> { !it.name.contains("OBDLink", true) }.thenBy { it.name })
            if (devices.none { it.address == selected }) selected = null
            if (devices.isEmpty()) prompt = "Pair the LX in phone Bluetooth settings, then refresh."
        } catch (_: SecurityException) {
            permission = false; devices = emptyList(); selected = null
            prompt = "Bluetooth access was revoked. Grant Nearby devices access again."
        }
    }

    @SuppressLint("MissingPermission")
    private fun begin(operation: Operation) {
        if (busy || !foreground) return
        val address = selected ?: return
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) { permission = false; return }
        val token = ++generation
        busy = true
        identity = null; observation = null; inferred = "unknown"; lastLog = null; summary = ""
        prompt = "Connecting to the LX… Keep the engine OFF."
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        job = worker.submit {
            var journal: TestJournal? = null
            var link: RiderLink? = null
            var reason = "session_error"
            var errorText: String? = null
            var saved: File? = null
            val cancelled = { token != generation || !foreground || Thread.currentThread().isInterrupted }
            try {
                if (cancelled()) throw InterruptedException()
                val log = TestJournal(File(filesDir, "diagnostics"))
                journal = log
                log.record("session_start", mapOf("operation" to operation.name, "max_samples" to IgnitionTest.MAX_SAMPLES,
                    "interval_ms" to 5000, "background" to false))
                val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: throw IOException("Bluetooth is unavailable.")
                val connection = RiderLink(adapter.getRemoteDevice(address), timer, cancelled, log::record)
                link = connection
                synchronized(connectionLock) {
                    if (cancelled()) throw InterruptedException()
                    activeLink = connection
                }
                connection.connect()
                val adapterIdentity = connection.identity()
                log.record("adapter_verified", mapOf("elm" to adapterIdentity.elm, "firmware" to adapterIdentity.firmware, "voltage" to adapterIdentity.voltage))
                publish(token) { identity = adapterIdentity }
                if (operation == Operation.ADAPTER) {
                    reason = "adapter_verified"
                    publish(token) { prompt = "I can reach the LX. Adapter check passed." }
                } else if (operation == Operation.BIKE) {
                    val result = connection.bike()
                    log.record("observation", TestJournal.observation(result))
                    reason = result.status
                    publish(token) {
                        observation = result
                        prompt = if (result.status == "decoded" && result.protocolClosed) "One wake-up-line read completed.\nIgnition is awaiting confirmation." else "Bike check: ${result.status}\n${result.detail}"
                    }
                } else {
                    val mode = if (operation == Operation.ON_OFF) IgnitionTest.Mode.ON_OFF else IgnitionTest.Mode.OFF_ON
                    reason = IgnitionTest.run(mode, connection::bike, SystemClock::elapsedRealtime,
                        { delay -> Thread.sleep(delay) }, cancelled) { sample ->
                        log.record("sample", mapOf("sample" to sample.index, "observation" to TestJournal.observation(sample.observation),
                            "inference" to mapOf("ignition" to sample.state.ignition, "last_confirmed" to sample.state.lastConfirmed,
                                "event" to sample.state.event, "confirmations" to sample.state.confirmations)))
                        publish(token) {
                            observation = sample.observation
                            inferred = sample.state.ignition
                            prompt = IgnitionTest.prompt(mode, sample)
                            summary = "Sample ${sample.index}/${IgnitionTest.MAX_SAMPLES}"
                        }
                    }
                }
            } catch (_: InterruptedException) {
                reason = "operator_cancelled"
            } catch (error: Exception) {
                reason = if (cancelled()) "operator_cancelled" else "session_error"
                errorText = error.message ?: "Connection or log failure."
            } finally {
                link?.close()
                synchronized(connectionLock) { if (activeLink === link) activeLink = null }
                // Clearing interrupt only for finishing the local log; it never resumes vehicle reads.
                Thread.interrupted()
                try {
                    if (errorText != null) journal?.record("error", mapOf("message" to errorText))
                    saved = journal?.finish(reason)
                } catch (error: Exception) {
                    errorText = "The diagnostic log could not be finalized: ${error.message}"
                }
                val finalReason = reason
                val finalError = errorText
                val finalFile = saved
                runOnUiThread {
                    if (!isDestroyed && token == generation) {
                        busy = false
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        lastLog = finalFile
                        summary = "Ended: $finalReason" + if (finalFile != null) "\nLog saved. Connection closed." else "\nLog unavailable."
                        if (finalError != null) { inferred = "unknown"; prompt = "Test stopped: $finalError" }
                        else if (finalReason !in listOf("confirmed_on_transition", "confirmed_off_transition", "adapter_verified", "decoded")) {
                            inferred = "unknown"
                            prompt = "Test ended: $finalReason\n" + (observation?.detail ?: "")
                        }
                    } else if (!isDestroyed && !busy && finalFile != null) {
                        lastLog = finalFile
                        summary = "Test stopped. Log saved. Connection closed."
                    }
                }
            }
        }
    }

    private fun publish(token: Int, action: () -> Unit) {
        runOnUiThread { if (token == generation && foreground) action() }
    }
    private fun cancelOperation(message: String) {
        generation++
        synchronized(connectionLock) { activeLink?.close(); activeLink = null }
        job?.cancel(true)
        busy = false
        inferred = "unknown"
        observation = null
        prompt = message
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private fun shareLog() {
        val file = lastLog ?: return
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.diagnostics", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("RIDER diagnostic log", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Share RIDER test log"))
        } catch (error: Exception) { summary = "Could not share log: ${error.message}" }
    }
    override fun onStop() {
        foreground = false
        if (busy) cancelOperation("Test stopped because RIDER left the foreground. Ignition is unknown.")
        super.onStop()
    }
    override fun onDestroy() {
        worker.shutdownNow()
        timer.shutdownNow()
        super.onDestroy()
    }
}
