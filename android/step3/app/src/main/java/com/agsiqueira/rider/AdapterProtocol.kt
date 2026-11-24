package com.agsiqueira.rider

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

/** Fixed commands for the bounded LX / read-only KOMBI check. */
internal object AdapterProtocol {
    enum class Query(val wire: String) {
        ID("ATI"), FIRMWARE("STI"), VOLTAGE("ATRV"),
        ECHO_OFF("ATE0"), LINEFEED_OFF("ATL0"), SPACES_ON("ATS1"), HEADERS_ON("ATH1"),
        DLC_OFF("ATD0"), FORMAT_OFF("ATCAF0"), FLOW_OFF("ATCFC0"), EXTENDED_OFF("ATCEA"),
        PROTOCOL("ATTP6"), SILENT_OFF("ATCSM0"), ADAPTIVE_OFF("ATAT0"), TIMEOUT("ATST64"),
        TX_HEADER("ATSH6F1"), RX_FILTER("ATCRA660"),
        CLUSTER_ID("600322F150000000"), WAKE_LINE("600322E11C000000"), CLOSE("ATPC")
    }
    data class Identity(val elm: String, val firmware: String, val voltage: String)

    fun query(input: InputStream, output: OutputStream, query: Query): List<String> =
        validateReply(exchange(input, output, query))

    fun validateReply(lines: List<String>): List<String> {
        if (lines.isEmpty()) throw IOException("Empty adapter reply.")
        if (lines.any { it == "?" || it.contains("ERROR", true) || it == "STOPPED" || it == "BUS BUSY" || it == "BUFFER FULL" || it == "UNABLE TO CONNECT" }) {
            throw IOException("Adapter error: " + lines.joinToString("; "))
        }
        return lines
    }

    fun exchange(input: InputStream, output: OutputStream, query: Query): List<String> {
        output.write((query.wire + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val reply = StringBuilder()
        while (reply.length <= 4096) {
            val byte = input.read() // Caller closes socket on timeout or lifecycle cancellation.
            if (byte < 0) throw IOException("The adapter disconnected.")
            if (byte == '>'.code) {
                val lines = reply.toString().replace('\r', '\n').split('\n')
                    .map { it.trim() }.filter { it.isNotEmpty() && !it.equals(query.wire, true) }
                return lines
            }
            if (byte !in 0..127) throw IOException("Unexpected non-ASCII adapter reply.")
            reply.append(byte.toChar())
        }
        throw IOException("Adapter response exceeded the size limit.")
    }

    fun single(lines: List<String>): String {
        if (lines.size != 1) throw IOException("Unexpected multiple adapter replies.")
        return lines.single()
    }

    fun validateFirmware(value: String): String {
        if (!Regex("STN1155 v[0-9]+(?:\\.[0-9]+)+").matches(value)) {
            throw IOException("This adapter did not identify as an OBDLink LX (STN1155).")
        }
        return value
    }

    fun validateVoltage(value: String): String {
        if (!Regex("[0-9]+(?:\\.[0-9]+)?V", RegexOption.IGNORE_CASE).matches(value)) {
            throw IOException("Unexpected voltage reply.")
        }
        return value
    }
}
