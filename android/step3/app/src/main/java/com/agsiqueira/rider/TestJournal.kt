package com.agsiqueira.rider

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** Only finalized JSONL logs are offered for explicit user sharing. */
internal class TestJournal(directory: File) {
    private val base = "rider-test-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.jsonl"
    private val partial: File
    private val target: File
    private val writer: java.io.BufferedWriter
    private var bytes = 0
    init {
        if (!directory.exists() && !directory.mkdirs()) throw IOException("Cannot create the diagnostic directory.")
        partial = File(directory, "$base.partial")
        target = File(directory, base)
        if (!partial.createNewFile()) throw IOException("Cannot create a new diagnostic log.")
        writer = partial.outputStream().bufferedWriter(Charsets.UTF_8)
    }
    fun record(event: String, fields: Map<String, Any?> = emptyMap()) {
        val line = JSONObject(fields + mapOf("schema_version" to 1, "event_type" to event,
            "timestamp_utc" to Instant.now().toString())).toString() + "\n"
        val size = line.toByteArray(Charsets.UTF_8).size
        if (bytes + size > 2_000_000) throw IOException("Diagnostic log reached its size limit.")
        writer.write(line)
        writer.flush()
        bytes += size
    }
    fun finish(reason: String): File {
        try { record("session_end", mapOf("reason" to reason)) } finally { writer.close() }
        if (!partial.renameTo(target)) throw IOException("Could not finalize the diagnostic log.")
        return target
    }
    companion object {
        fun observation(result: KombiProtocol.Result): Map<String, Any?> = mapOf(
            "status" to result.status, "stage" to result.stage, "cluster_identity" to result.clusterIdentity,
            "wake_up_line" to result.wakeUpLine, "raw_response" to result.rawResponse,
            "protocol_closed" to result.protocolClosed, "detail" to result.detail,
            "ignition" to result.ignition)
    }
}
