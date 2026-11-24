package com.agsiqueira.rider

import org.json.JSONObject
import java.nio.file.Files
import java.io.File
import java.io.IOException

fun main() {
    var checks = 0
    fun test(name: String, action: () -> Unit) {
        try { action(); checks++ } catch (error: Throwable) { throw AssertionError(name, error) }
    }
    val dir = Files.createTempDirectory("rider-log-checks").toFile()
    try {
        test("working log has partial suffix only") {
            val log = TestJournal(dir)
            check(dir.listFiles()!!.all { it.name.endsWith(".partial") })
            val final = log.finish("test_complete")
            check(final.name.endsWith(".jsonl") && final.exists())
            check(JSONObject(final.readLines().last()).getString("reason") == "test_complete")
        }
        test("nested observation and control characters round trip") {
            val log = TestJournal(dir)
            val message = "quoted \"reply\"\r\nwith \\ backslash"
            val observation = KombiProtocol.Result(status = "pending", detail = message)
            log.record("sample", mapOf("observation" to TestJournal.observation(observation), "inference" to mapOf("ignition" to "unknown", "event" to null)))
            val final = log.finish("response_pending_unresolved")
            val parsed = JSONObject(final.readLines().first())
            check(parsed.getJSONObject("observation").getString("detail") == message)
            check(parsed.getJSONObject("inference").isNull("event"))
            check(parsed.getInt("schema_version") == 1)
        }
        test("logs do not overwrite previous sessions") {
            val first = TestJournal(dir).finish("first")
            val second = TestJournal(dir).finish("second")
            check(first != second && first.exists() && second.exists())
        }
        test("size limit prevents finalized oversized log") {
            val log = TestJournal(dir)
            var rejected = false
            try { log.record("oversized", mapOf("value" to "x".repeat(2_000_001))) } catch (_: IOException) { rejected = true }
            check(rejected)
            check(log.finish("log_limit").length() < 2_000_000)
        }
        test("unusable directory fails before hardware can be opened") {
            val invalid = File(dir, "not-a-directory"); invalid.writeText("existing")
            var rejected = false
            try { TestJournal(invalid) } catch (_: IOException) { rejected = true }
            check(rejected)
        }
        println("$checks journal checks passed")
    } finally { dir.deleteRecursively() }
}
