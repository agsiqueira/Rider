package com.agsiqueira.rider

/** Inferred state, separate from the raw wake-up-line observation. */
internal class IgnitionTracker {
    data class State(val ignition: String, val lastConfirmed: String, val event: String?, val confirmations: Int)
    private var current = "unknown"
    private var confirmed = "unknown"
    private var pending: String? = null
    private var count = 0
    private var lastValid: Long? = null
    private var lastUpdate: Long? = null
    private var gap = false

    private fun unavailable() {
        current = "unknown"
        pending = null
        count = 0
        gap = true
    }

    fun update(observation: KombiProtocol.Result, nowMs: Long): State {
        require(nowMs >= 0 && (lastUpdate == null || nowMs > lastUpdate!!)) { "Monotonic sample times must increase" }
        lastUpdate = nowMs
        if (lastValid != null && nowMs - lastValid!! > 15_000) unavailable()
        val valid = observation.status == "decoded" && observation.clusterIdentity == "0F8800" &&
            observation.protocolClosed && observation.wakeUpLine in listOf("on", "off")
        var event: String? = null
        if (!valid) unavailable() else {
            val value = observation.wakeUpLine
            lastValid = nowMs
            if (pending == value) count = (count + 1).coerceAtMost(2) else { pending = value; count = 1 }
            if (value != current) current = "unknown"
            if (count == 2) {
                if (current != value) {
                    event = when {
                        confirmed == "unknown" -> "state_established"
                        gap -> if (value == confirmed) "state_recovered" else "change_observed_after_gap"
                        value != confirmed -> "ignition_$value"
                        else -> null
                    }
                    confirmed = value
                    current = value
                    gap = false
                }
            }
        }
        return State(current, confirmed, event, count)
    }
}

internal object IgnitionTest {
    enum class Mode { ON_OFF, OFF_ON }
    data class Sample(val index: Int, val observation: KombiProtocol.Result, val state: IgnitionTracker.State)
    const val MAX_SAMPLES = 12

    fun prompt(mode: Mode, sample: Sample): String {
        val state = sample.state
        val target = if (mode == Mode.ON_OFF) "off" else "on"
        val baseline = if (target == "on") "off" else "on"
        if (state.event == "ignition_$target") return "IGNITION ${target.uppercase()} TRANSITION CONFIRMED.\nTest complete."
        if (state.ignition == baseline) return "IGNITION ${baseline.uppercase()} CONFIRMED\nTURN THE KEY ${target.uppercase()} NOW.\nKeep the engine OFF."
        if (state.ignition == target) return "${target.uppercase()} is confirmed, but a new transition was not observed.\nTurn the key ${baseline.uppercase()} to establish the baseline."
        if (sample.observation.status == "decoded" && sample.observation.protocolClosed) {
            return "${sample.observation.wakeUpLine.uppercase()} detected (${state.confirmations}/2).\nKeep the key in that position until confirmed."
        }
        return "IGNITION UNKNOWN\n${sample.observation.detail.ifBlank { sample.observation.status }}"
    }

    fun run(mode: Mode, sample: () -> KombiProtocol.Result, clock: () -> Long,
            pause: (Long) -> Unit, cancelled: () -> Boolean, emit: (Sample) -> Unit): String {
        val tracker = IgnitionTracker()
        var failures = 0
        val desired = if (mode == Mode.ON_OFF) "ignition_off" else "ignition_on"
        for (index in 1..MAX_SAMPLES) {
            if (cancelled()) return "operator_cancelled"
            val observation = sample()
            if (cancelled()) return "operator_cancelled"
            val state = tracker.update(observation, clock())
            emit(Sample(index, observation, state))
            if (state.event == desired) return "confirmed_${desired.removePrefix("ignition_")}_transition"
            if (observation.status == "pending") return "response_pending_unresolved"
            if (!observation.protocolClosed || observation.status !in listOf("decoded", "unavailable", "communication_error")) {
                return "review_required"
            }
            failures = if (observation.status == "decoded") 0 else failures + 1
            if (index < MAX_SAMPLES) pause((5_000L * (1L shl failures.coerceAtMost(3))).coerceAtMost(30_000))
        }
        return "sample_limit"
    }
}
