package com.agsiqueira.rider

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

/** Adapter-local queries only. No vehicle diagnostic payloads are exposed. */
internal object AdapterProtocol {
    enum class Query(val wire: String) { ID("ATI"), FIRMWARE("STI"), VOLTAGE("ATRV") }
    data class Identity(val elm: String, val firmware: String, val voltage: String)

    fun query(input: InputStream, output: OutputStream, query: Query): List<String> {
        output.write((query.wire + "\r").toByteArray(Charsets.US_ASCII))
        output.flush()
        val reply = StringBuilder()
        while (reply.length <= 4096) {
            val byte = input.read() // Caller closes socket on timeout or lifecycle cancellation.
            if (byte < 0) throw IOException("The adapter disconnected.")
            if (byte == '>'.code) {
                val lines = reply.toString().replace('\r', '\n').split('\n')
                    .map { it.trim() }.filter { it.isNotEmpty() && !it.equals(query.wire, true) }
                if (lines.isEmpty() || lines.any { it == "?" || it.contains("ERROR", true) || it == "STOPPED" }) {
                    throw IOException("The adapter did not return a valid ${query.wire} reply.")
                }
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
