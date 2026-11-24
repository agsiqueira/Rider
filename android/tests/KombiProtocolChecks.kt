package com.agsiqueira.rider

import java.io.IOException
import com.agsiqueira.rider.AdapterProtocol.Query

fun main() {
    var checks = 0
    fun test(name: String, action: () -> Unit) {
        try { action(); checks++ } catch (error: Throwable) { throw AssertionError(name, error) }
    }
    class Script(val identity: List<String> = listOf("660 F1 06 62 F1 50 0F 88 00"),
                 val wake: List<String> = listOf("660 F1 04 62 E1 1C 01 FF FF"),
                 val failAt: Query? = null, val interrupted: Boolean = false) {
        val commands = mutableListOf<Query>()
        fun read(query: Query): List<String> {
            commands.add(query)
            if (query == failAt) throw IOException("Simulated transport failure")
            return AdapterProtocol.validateReply(when (query) {
                Query.CLUSTER_ID -> identity
                Query.WAKE_LINE -> wake
                else -> listOf("OK")
            })
        }
        fun run() = KombiProtocol.check(::read) { !interrupted }
    }
    test("successful on read") {
        val script = Script(); val result = script.run()
        check(result.status == "decoded" && result.wakeUpLine == "on")
        check(result.ignition == "unknown" && result.protocolClosed)
        check(script.commands.filter { it == Query.CLUSTER_ID || it == Query.WAKE_LINE } == listOf(Query.CLUSTER_ID, Query.WAKE_LINE))
        check(script.commands.last() == Query.CLOSE)
    }
    test("off read") { check(Script(wake = listOf("660 F1 04 62 E1 1C 00 FF FF")).run().wakeUpLine == "off") }
    test("identity mismatch blocks wake") {
        val script = Script(identity = listOf("660 F1 06 62 F1 50 0F 88 10"))
        check(script.run().status == "identity_unconfirmed")
        check(Query.WAKE_LINE !in script.commands)
    }
    val pending = "660 F1 03 7F 22 78 FF FF FF"
    test("identity pending blocks wake") {
        val script = Script(identity = listOf(pending)); val result = script.run()
        check(result.status == "pending" && result.stage == "identity" && result.protocolClosed)
        check(Query.WAKE_LINE !in script.commands)
    }
    test("wake pending remains unknown") {
        val result = Script(wake = listOf(pending)).run()
        check(result.status == "pending" && result.wakeUpLine == "unknown")
    }
    test("pending then final identity") {
        check(Script(identity = listOf(pending, "660 F1 06 62 F1 50 0F 88 00")).run().status == "decoded")
    }
    test("pending then final wake") {
        check(Script(wake = listOf(pending, "660 F1 04 62 E1 1C 01 FF FF")).run().status == "decoded")
    }
    test("duplicate identical final identity") {
        val frame = "660 F1 06 62 F1 50 0F 88 00"
        val script = Script(identity = listOf(frame, frame))
        check(script.run().status == "ambiguous_response")
        check(Query.WAKE_LINE !in script.commands)
    }
    test("conflicting wake values") {
        check(Script(wake = listOf("660 F1 04 62 E1 1C 00 FF FF", "660 F1 04 62 E1 1C 01 FF FF")).run().status == "ambiguous_response")
    }
    test("wrong source rejected") { check(Script(wake = listOf("661 F1 04 62 E1 1C 01 FF FF")).run().status == "invalid_response") }
    test("wrong recipient rejected") { check(Script(wake = listOf("660 F2 04 62 E1 1C 01 FF FF")).run().status == "invalid_response") }
    test("wrong DID rejected") { check(Script(wake = listOf("660 F1 04 62 E1 1D 01 FF FF")).run().status == "invalid_response") }
    test("invalid enum rejected") { check(Script(wake = listOf("660 F1 04 62 E1 1C FF FF FF")).run().status == "invalid_value") }
    test("truncated response rejected") { check(Script(wake = listOf("660 F1 04 62 E1 1C")).run().status == "invalid_response") }
    test("multiframe rejected") { check(Script(wake = listOf("660 F1 10 62 E1 1C 01 FF FF")).run().status == "invalid_response") }
    test("negative reply remains unknown") { check(Script(wake = listOf("660 F1 03 7F 22 31 FF FF FF")).run().status == "negative_response") }
    test("no data") { check(Script(identity = listOf("NO DATA")).run().status == "unavailable") }
    test("CAN error distinct from valid frame") { check(Script(identity = listOf("CAN ERROR")).run().status == "communication_error") }
    test("setup failure blocks reads") {
        val script = Script(failAt = Query.PROTOCOL)
        check(script.run().status == "communication_error")
        check(Query.CLUSTER_ID !in script.commands && Query.WAKE_LINE !in script.commands)
    }
    test("no cleanup on interrupted stream") {
        val script = Script(failAt = Query.CLUSTER_ID, interrupted = true)
        val result = script.run()
        check(!result.protocolClosed && result.wakeUpLine == "unknown" && Query.CLOSE !in script.commands)
    }
    test("cleanup failure invalidates decoded observation") {
        val result = Script(failAt = Query.CLOSE).run()
        check(result.status == "cleanup_failed" && result.wakeUpLine == "unknown" && !result.protocolClosed)
    }
    test("pending limit") { check(Script(wake = List(10) { pending }).run().status == "invalid_response") }
    test("wrong-service pending cannot prefix a final") {
        check(Script(wake = listOf("660 F1 03 7F 2E 78 FF FF FF", "660 F1 04 62 E1 1C 01 FF FF")).run().status == "ambiguous_response")
    }
    test("all diagnostic frames read only") {
        val payloads = AdapterProtocol.Query.entries.map { it.wire }.filter { it.startsWith("60") }
        check(payloads == listOf("600322F150000000", "600322E11C000000"))
    }
    println("$checks KOMBI checks passed")
}
