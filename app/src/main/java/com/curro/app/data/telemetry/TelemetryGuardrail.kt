package com.curro.app.data.telemetry

/**
 * Privacy boundary every telemetry call routes through before reaching any SDK.
 *
 * Two layers of defence (Q4-Resolved in docs/briefs/US-008-telemetry-plumbing.md):
 *
 * 1. KEY WHITELIST (primary) — every (eventName → propKey) pair must be registered
 *    in [ALLOWED_PROPS] below. Unknown event names AND unknown prop keys for a known
 *    event both result in [GuardrailResult.Reject]. Adding a new event requires updating
 *    this map AND adding fixture cases to `TelemetryGuardrailTest`. Code review surfaces
 *    both diffs — this is the load-bearing privacy gate.
 *
 * 2. VALUE HEURISTIC (secondary) — even whitelisted values are inspected for PII
 *    shapes: email addresses, phone numbers, full names (two-capital-words), or strings
 *    longer than [MAX_VALUE_LEN] characters (transcript proxy). A reject here is a bug:
 *    the developer must shorten the value, hash it, or narrow the whitelist's
 *    value-shape contract. No escape hatch — see A3.
 *
 * No escape hatch: no `@Suppress("PII")` annotation, no per-event opt-out. Every future
 * long value (e.g. a UUID) is whitelisted with an explicit value-shape validator.
 * See A3 in docs/briefs/US-008-telemetry-plumbing.md.
 *
 * All [Regex] objects are compiled once at object initialisation to keep the hot path cheap.
 *
 * See docs/curro-spec-v1.0.md §12 (v1.1) and CLAUDE.md → Privacy & telemetry.
 */
object TelemetryGuardrail {
    /** Pre-compiled to keep the hot path cheap — never instantiate per-call. */
    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val PHONE = Regex("""^\+?\d[\d ()\-]{6,}$""")
    private val FULL_NAME = Regex("""\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+ [A-ZÁÉÍÓÚÑ][a-záéíóúñ]+\b""")
    private const val MAX_VALUE_LEN = 32

    /**
     * Whitelisted events and their allowed property keys.
     *
     * Each entry maps an event name to the set of prop keys that entry permits.
     * An event with an empty set permits no props (only call with `emptyMap()`).
     *
     * Adding a new event = adding a row here + adding fixture cases to
     * `TelemetryGuardrailTest` in the same PR.
     */
    private val ALLOWED_PROPS: Map<String, Set<String>> =
        mapOf(
            // SF-3.6 — FunctionGemma decision loop
            "function_called" to setOf("action", "confidence_bucket", "latency_ms", "from_warm"),
            // SF-3.6 — model_decide: emitted on every decide() call (success + failure).
            //   outcome ∈ {success, invalid_json, unknown_function, model_cold, oom, other}
            //   latency_ms is the wall-clock measured at the ViewModel layer (US-024).
            //   The utterance, action, params: NEVER on the wire.
            "model_decide" to setOf("model", "outcome", "latency_ms"),
            // SF-2.x — STT failures
            "stt_failed" to setOf("error_code"),
            // SF-3.5 — model warm-up service
            "model_loaded" to setOf("model", "load_ms", "cold_start"),
            "model_killed_by_system" to setOf("model", "uptime_s"),
            // SF-1.x — launcher lifecycle
            "launcher_set_default" to setOf("attempt"),
            // SF-4.1 (US-025) — handler outcome per dispatch. outcome ∈ {success, needs_confirmation, failed, crash}.
            // function_name is the snake_case catalog action name — NEVER a param value, NEVER an utterance.
            "handler_invoked" to setOf("function_name", "outcome"),
            // SF-5.x — confidence policy
            "confidence_below_threshold" to setOf("function", "threshold", "delta"),
            // SF-6.1 (US-041) — ConfidencePolicy result per turn.
            //   decision ∈ {execute, confirm, clarify}
            //   confidence_bucket ∈ {low, mid, high} — bucketed in the coordinator so the raw
            //     confidence value never appears in telemetry.
            //   always_confirm_on is a Boolean — SF-6.4 wires the real value from DataStore.
            //   function_name is the snake_case catalog name — NEVER a param value, NEVER an utterance.
            "policy_decided" to setOf("function_name", "decision", "confidence_bucket", "always_confirm_on"),
            // App lifecycle smoke (no props)
            "app_open" to emptySet(),
            // SF-7.5 (US-049) — failure-log telemetry. The transcript and details are
            // PII (spec §12) — explicitly NOT on this whitelist. The Phase-8 UI reads
            // them from the local Room table; PostHog/Firebase see counts only.
            //   kind ∈ {invalid_output, unknown_function, handler_error} — ≤ 16 chars
            //   function_name ∈ catalog snake_case OR "unknown" — ≤ 32 chars
            "command_failed" to setOf("kind", "function_name"),
            // SF-8.7 (US-056) — incoming-call announcement outcome. The phone number,
            // contact name, and alias are PII (spec §12) — explicitly NOT on this
            // whitelist. Only the bucket-shaped outcome reaches PostHog/Firebase.
            //   outcome ∈ {answered, declined, timed_out, other} — ≤ 10 chars
            "incoming_call_announced" to setOf("outcome"),
        )

    /** Whitelisted user-property keys (scalar enums only — never PII). */
    private val ALLOWED_USER_PROPS: Set<String> =
        setOf(
            "locale",
            "device_variant",
            "hyperos_version",
        )

    /**
     * Checks whether an event with [name] and [props] is safe to emit.
     *
     * Returns [GuardrailResult.Allow] if the call is safe.
     * Returns [GuardrailResult.Reject] with a developer-readable reason if not.
     *
     * Guard-style early returns are the correct idiom for a validator (@Suppress ReturnCount).
     */
    @Suppress("ReturnCount")
    fun check(
        name: String,
        props: Map<String, Any>,
    ): GuardrailResult {
        val allowedKeys =
            ALLOWED_PROPS[name]
                ?: return GuardrailResult.Reject("event '$name' is not registered in ALLOWED_PROPS")
        for ((key, value) in props) {
            if (key !in allowedKeys) {
                return GuardrailResult.Reject("event '$name': prop key '$key' is not on the whitelist")
            }
            valueHeuristic(value)?.let { reason ->
                return GuardrailResult.Reject("event '$name'.'$key': $reason")
            }
        }
        return GuardrailResult.Allow
    }

    /**
     * Checks whether a user-property [userPropertyKey] / [value] pair is safe to set.
     *
     * Returns [GuardrailResult.Allow] if safe; [GuardrailResult.Reject] otherwise.
     * Guard-style early returns — see first overload's @Suppress rationale.
     */
    @Suppress("ReturnCount")
    fun check(
        userPropertyKey: String,
        value: String?,
    ): GuardrailResult {
        if (userPropertyKey !in ALLOWED_USER_PROPS) {
            return GuardrailResult.Reject(
                "user property '$userPropertyKey' is not registered in ALLOWED_USER_PROPS",
            )
        }
        // null clears the property — always safe.
        value ?: return GuardrailResult.Allow
        valueHeuristic(value)?.let { reason ->
            return GuardrailResult.Reject("user property '$userPropertyKey': $reason")
        }
        return GuardrailResult.Allow
    }

    /**
     * Heuristic: returns a human-readable reason if [value] looks like PII, or null if safe.
     *
     * Non-String values (Int, Boolean, Long, Double) stringify safely.
     * Guard-style returns (@Suppress ReturnCount) — each clause has its own reason string.
     */
    @Suppress("ReturnCount")
    private fun valueHeuristic(value: Any): String? {
        val s = value.toString() // Int/Boolean/Long/Double all stringify safely
        if (s.length > MAX_VALUE_LEN) {
            return "value length ${s.length} exceeds $MAX_VALUE_LEN-char limit (transcript proxy)"
        }
        if (EMAIL.containsMatchIn(s)) return "value matches email shape"
        if (PHONE.matches(s)) return "value matches phone-number shape"
        if (FULL_NAME.containsMatchIn(s)) return "value matches two-capital-words full-name shape"
        return null
    }

    /** The result of a guardrail check. */
    sealed interface GuardrailResult {
        /** The call is safe to forward to the SDK. */
        data object Allow : GuardrailResult

        /**
         * The call must be dropped.
         *
         * [reason] is a developer-readable explanation of *why* the call was rejected.
         * It must never appear in production logs visible to the user.
         */
        data class Reject(val reason: String) : GuardrailResult
    }
}
