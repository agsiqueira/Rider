package com.agsiqueira.rider

fun main() {
    var checks = 0
    fun test(name: String, action: () -> Unit) {
        try { action(); checks++ } catch (error: Throwable) { throw AssertionError(name, error) }
    }
    fun good(value: String = "on") = KombiProtocol.Result(status = "decoded", clusterIdentity = "0F8800", wakeUpLine = value, protocolClosed = true)
    fun sequence(values: List<KombiProtocol.Result>, mode: IgnitionTest.Mode): Pair<String, List<IgnitionTest.Sample>> {
        var now = 0L
        var index = 0
        val output = mutableListOf<IgnitionTest.Sample>()
        val reason = IgnitionTest.run(mode, { values[(index++).coerceAtMost(values.lastIndex)] }, { now },
            { delay -> now += delay }, { false }, output::add)
        return reason to output
    }
    test("first two samples establish state without an edge") {
        val tracker = IgnitionTracker()
        check(tracker.update(good(), 0).ignition == "unknown")
        check(tracker.update(good(), 5000).event == "state_established")
        check(tracker.update(good(), 10000).event == null)
    }
    test("confirmed ON to OFF stops once") {
        val (reason, samples) = sequence(listOf(good(), good(), good("off"), good("off")), IgnitionTest.Mode.ON_OFF)
        check(reason == "confirmed_off_transition" && samples.size == 4)
        check(samples.count { it.state.event == "ignition_off" } == 1)
    }
    test("confirmed OFF to ON stops once") {
        val (reason, samples) = sequence(listOf(good("off"), good("off"), good(), good()), IgnitionTest.Mode.OFF_ON)
        check(reason == "confirmed_on_transition" && samples.size == 4)
        check(samples.count { it.state.event == "ignition_on" } == 1)
    }
    test("already on is not a new on event") {
        val (reason, samples) = sequence(listOf(good()), IgnitionTest.Mode.OFF_ON)
        check(reason == "sample_limit" && samples.size == 12)
        check(samples.none { it.state.event == "ignition_on" })
    }
    test("already off is not a new off event") {
        check(sequence(listOf(good("off")), IgnitionTest.Mode.ON_OFF).first == "sample_limit")
    }
    test("single opposite sample does not make an edge") {
        val tracker = IgnitionTracker()
        val events = listOf("on", "on", "off", "on", "on").mapIndexed { i, value -> tracker.update(good(value), i * 5000L).event }
        check(events.none { it == "ignition_on" || it == "ignition_off" })
    }
    test("missing access immediately makes state unknown") {
        val tracker = IgnitionTracker()
        tracker.update(good(), 0); tracker.update(good(), 5000)
        val lost = tracker.update(KombiProtocol.Result(status = "communication_error", protocolClosed = true), 10000)
        check(lost.ignition == "unknown" && lost.lastConfirmed == "on")
        tracker.update(good(), 15000)
        check(tracker.update(good(), 20000).event == "state_recovered")
    }
    test("change across gap is not precise ignition edge") {
        val lost = KombiProtocol.Result(status = "unavailable", protocolClosed = true)
        val (reason, samples) = sequence(listOf(good("off"), good("off"), lost, good(), good()), IgnitionTest.Mode.OFF_ON)
        check(reason == "sample_limit")
        check(samples.any { it.state.event == "change_observed_after_gap" })
        check(samples.none { it.state.event == "ignition_on" })
    }
    test("stale confirmation resets") {
        val tracker = IgnitionTracker()
        tracker.update(good(), 0)
        check(tracker.update(good(), 20000).ignition == "unknown")
        check(tracker.update(good(), 25000).event == "state_established")
    }
    test("duplicate or backward timestamp rejected") {
        val tracker = IgnitionTracker(); tracker.update(good(), 5)
        for (now in listOf(5L, 4L)) {
            var rejected = false
            try { tracker.update(good(), now) } catch (_: IllegalArgumentException) { rejected = true }
            check(rejected)
        }
    }
    test("unclosed protocol never confirms") {
        check(sequence(listOf(good().copy(protocolClosed = false)), IgnitionTest.Mode.ON_OFF).first == "review_required")
    }
    test("wrong identity cannot confirm") {
        val tracker = IgnitionTracker()
        check(tracker.update(good().copy(clusterIdentity = "OTHER"), 0).ignition == "unknown")
        check(tracker.update(good().copy(clusterIdentity = "OTHER"), 5000).ignition == "unknown")
    }
    test("pending alone ends before another request") {
        val (reason, samples) = sequence(listOf(KombiProtocol.Result(status = "pending", protocolClosed = true)), IgnitionTest.Mode.ON_OFF)
        check(reason == "response_pending_unresolved" && samples.size == 1 && samples.single().state.ignition == "unknown")
    }
    test("ambiguous response ends before another request") {
        val (reason, samples) = sequence(listOf(KombiProtocol.Result(status = "ambiguous_response", protocolClosed = true)), IgnitionTest.Mode.ON_OFF)
        check(reason == "review_required" && samples.size == 1)
    }
    test("cancel before start makes no reads") {
        var reads = 0
        val reason = IgnitionTest.run(IgnitionTest.Mode.ON_OFF, { reads++; good() }, { 0 }, {}, { true }, {})
        check(reason == "operator_cancelled" && reads == 0)
    }
    test("cancel during read discards result without an event") {
        var cancelled = false; var emitted = 0; var reads = 0
        val reason = IgnitionTest.run(IgnitionTest.Mode.ON_OFF,
            { reads++; cancelled = true; good() }, { 0 }, {}, { cancelled }, { emitted++ })
        check(reason == "operator_cancelled" && reads == 1 && emitted == 0)
    }
    test("cancel while waiting prevents another read") {
        var cancelled = false; var reads = 0
        val reason = IgnitionTest.run(IgnitionTest.Mode.ON_OFF,
            { reads++; good() }, { 0 }, { cancelled = true }, { cancelled }, {})
        check(reason == "operator_cancelled" && reads == 1)
    }
    test("failed access backs off within twelve attempts") {
        var now = 0L; val delays = mutableListOf<Long>(); var reads = 0
        val reason = IgnitionTest.run(IgnitionTest.Mode.ON_OFF,
            { reads++; KombiProtocol.Result(status = "communication_error", protocolClosed = true) }, { now },
            { delays.add(it); now += it }, { false }, {})
        check(reason == "sample_limit" && reads == 12)
        check(delays.take(3) == listOf(10000L, 20000L, 30000L) && delays.all { it <= 30000 })
    }
    test("mode prompts request correct key position") {
        val state = IgnitionTracker.State("off", "off", "state_established", 2)
        val sample = IgnitionTest.Sample(2, good("off"), state)
        check("TURN THE KEY ON NOW" in IgnitionTest.prompt(IgnitionTest.Mode.OFF_ON, sample))
        check("baseline" in IgnitionTest.prompt(IgnitionTest.Mode.ON_OFF, sample))
    }
    test("one sample says detected rather than confirmed") {
        val sample = IgnitionTest.Sample(1, good("off"), IgnitionTracker.State("unknown", "on", null, 1))
        val text = IgnitionTest.prompt(IgnitionTest.Mode.ON_OFF, sample)
        check("detected (1/2)" in text && "CONFIRMED" !in text)
    }
    println("$checks ignition checks passed")
}
