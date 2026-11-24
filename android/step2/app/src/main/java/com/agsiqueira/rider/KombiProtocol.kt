package com.agsiqueira.rider

import java.io.IOException
import com.agsiqueira.rider.AdapterProtocol.Query

/** One identity read and, only after it matches, one wake-up-line read. */
internal object KombiProtocol {
    data class Result(
        val status: String = "incomplete", val stage: String = "setup",
        val clusterIdentity: String? = null, val wakeUpLine: String = "unknown",
        val rawResponse: String = "", val protocolClosed: Boolean = false,
        val detail: String = "", val ignition: String = "unknown"
    )
    private class Stop(val status: String, message: String) : IOException(message)
    private val setup = listOf(Query.ECHO_OFF, Query.LINEFEED_OFF, Query.SPACES_ON, Query.HEADERS_ON,
        Query.DLC_OFF, Query.FORMAT_OFF, Query.FLOW_OFF, Query.EXTENDED_OFF, Query.PROTOCOL,
        Query.SILENT_OFF, Query.ADAPTIVE_OFF, Query.TIMEOUT, Query.TX_HEADER, Query.RX_FILTER)
    private val pending = listOf(0x7F, 0x22, 0x78)
    private val expectedIdentity = listOf(0x62, 0xF1, 0x50, 0x0F, 0x88, 0x00)

    fun parse(lines: List<String>): List<Int> {
        if (lines == listOf("NO DATA")) throw Stop("unavailable", "The cluster did not reply. Ignition remains unknown.")
        if (lines.size !in 1..9) throw Stop("invalid_response", "Too many or missing CAN frames.")
        val payloads = lines.map { line ->
            val text = line.filterNot { it.isWhitespace() }.uppercase()
            if (!Regex("660(?:[0-9A-F]{2}){3,8}").matches(text)) {
                throw Stop("invalid_response", "Unexpected CAN source or frame format.")
            }
            val data = text.substring(3).chunked(2).map { it.toInt(16) }
            if (data[0] != 0xF1 || data[1] !in 1..6 || data.size < data[1] + 2) {
                throw Stop("invalid_response", "Wrong recipient, unsupported frame type, or truncated data.")
            }
            data.subList(2, 2 + data[1]).toList()
        }
        if (payloads.dropLast(1).any { it != pending }) {
            throw Stop("ambiguous_response", "Multiple final replies. The check stopped without confirming a value.")
        }
        return payloads.last()
    }

    private fun hex(payload: List<Int>) = payload.joinToString(" ") { "%02X".format(it) }

    private fun checkRead(payload: List<Int>) {
        if (payload == pending) throw Stop("pending", "The cluster is still responding. No further diagnostic read was sent.")
        if (payload.size == 3 && payload.take(2) == listOf(0x7F, 0x22)) {
            throw Stop("negative_response", "The cluster declined the read (${hex(payload)}).")
        }
    }

    fun check(read: (Query) -> List<String>, canClose: () -> Boolean): Result {
        var result = Result()
        var configured = false
        try {
            setup.forEach { query ->
                configured = true
                if (read(query) != listOf("OK")) throw Stop("setup_failed", "Adapter setup failed at ${query.wire}.")
            }
            result = result.copy(stage = "identity")
            val identity = parse(read(Query.CLUSTER_ID))
            result = result.copy(rawResponse = hex(identity))
            checkRead(identity)
            if (identity != expectedIdentity) throw Stop("identity_unconfirmed", "The cluster identity did not match the tested profile.")
            result = result.copy(clusterIdentity = "0F8800", stage = "wake_up_line")
            val payload = parse(read(Query.WAKE_LINE))
            result = result.copy(rawResponse = hex(payload))
            checkRead(payload)
            if (payload.size != 4 || payload.take(3) != listOf(0x62, 0xE1, 0x1C)) {
                throw Stop("invalid_response", "The response did not match the requested wake-up-line read.")
            }
            if (payload[3] !in 0..1) throw Stop("invalid_value", "Unsupported wake-up-line value.")
            result = result.copy(status = "decoded", wakeUpLine = if (payload[3] == 1) "on" else "off",
                detail = "One valid wake-up-line observation; ignition is not yet confirmed.")
        } catch (stop: Stop) {
            result = result.copy(status = stop.status, detail = stop.message ?: stop.status)
        } catch (error: IOException) {
            result = result.copy(status = "communication_error", detail = error.message ?: "Communication failed.")
        } finally {
            if (configured && canClose()) {
                try {
                    val closed = read(Query.CLOSE) == listOf("OK")
                    result = result.copy(protocolClosed = closed)
                    if (!closed) result = result.copy(status = "cleanup_failed", wakeUpLine = "unknown", detail = "Protocol closure was not confirmed.")
                } catch (error: IOException) {
                    result = result.copy(status = "cleanup_failed", wakeUpLine = "unknown", detail = "Protocol closure failed: ${error.message}")
                }
            } else if (configured) {
                result = result.copy(wakeUpLine = "unknown", detail = result.detail + " Cleanup skipped because the stream was interrupted.")
            }
        }
        return result
    }
}
