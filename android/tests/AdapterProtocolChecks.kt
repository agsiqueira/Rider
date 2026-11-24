package com.agsiqueira.rider

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

fun main() {
    fun read(text: String, query: AdapterProtocol.Query): Pair<List<String>, String> {
        val output = ByteArrayOutputStream()
        val result = AdapterProtocol.query(ByteArrayInputStream(text.toByteArray(Charsets.US_ASCII)), output, query)
        return result to output.toString("US-ASCII")
    }
    fun fails(action: () -> Unit) {
        try { action(); error("Expected IOException") } catch (_: IOException) { }
    }
    check(read("ATI\rELM327 v1.4b\r\r>", AdapterProtocol.Query.ID) == (listOf("ELM327 v1.4b") to "ATI\r"))
    check(read("STN1155 v5.6.19\r>", AdapterProtocol.Query.FIRMWARE).first.single() == "STN1155 v5.6.19")
    check(read("ATRV\r13.1V\r>", AdapterProtocol.Query.VOLTAGE).second == "ATRV\r")
    check(AdapterProtocol.validateFirmware("STN1155 v5.6.19") == "STN1155 v5.6.19")
    check(AdapterProtocol.validateVoltage("13.1V") == "13.1V")
    fails { AdapterProtocol.validateFirmware("STN2255 v5.6.19") }
    fails { AdapterProtocol.validateVoltage("OK") }
    fails { read("?\r>", AdapterProtocol.Query.ID) }
    fails { read("CAN ERROR\r>", AdapterProtocol.Query.ID) }
    fails { read("STN1155 v5.6.19", AdapterProtocol.Query.FIRMWARE) }
    fails { read("x".repeat(4098) + ">", AdapterProtocol.Query.ID) }
    fails { AdapterProtocol.single(listOf("first", "second")) }
    check(AdapterProtocol.Query.entries.take(3).map { it.wire } == listOf("ATI", "STI", "ATRV"))
    println("13 protocol checks passed")
}
