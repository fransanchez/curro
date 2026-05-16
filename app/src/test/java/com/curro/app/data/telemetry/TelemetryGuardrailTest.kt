package com.curro.app.data.telemetry

import com.curro.app.data.telemetry.TelemetryGuardrail.GuardrailResult.Allow
import com.curro.app.data.telemetry.TelemetryGuardrail.GuardrailResult.Reject
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * Load-bearing CI test for the privacy boundary.
 *
 * Every FORBIDDEN example MUST produce [Reject].
 * Every ALLOWED example MUST produce [Allow].
 *
 * A failure here means the [TelemetryGuardrail] has been modified in a way that
 * either weakens the privacy contract (a forbidden case now passes) or breaks a
 * legitimate use case (an allowed case now fails). Both are CI-breaking regressions.
 *
 * This is the template every later privacy-boundary test follows (A4 in
 * docs/briefs/US-008-telemetry-plumbing.md). SFs that add events to [TelemetryGuardrail]'s
 * ALLOWED_PROPS MUST extend this fixture in the same PR.
 *
 * Test taxonomy:
 *  - [eventCases] — event(name, props) checks
 *  - [userPropertyCases] — check(key, value) for user properties
 */
@DisplayName("TelemetryGuardrail — privacy boundary")
class TelemetryGuardrailTest {
    /** A single event-check fixture row. */
    data class EventCase(
        val label: String,
        val name: String,
        val props: Map<String, Any>,
        val expectAllow: Boolean,
    )

    /** A single user-property-check fixture row. */
    data class UserPropertyCase(
        val label: String,
        val key: String,
        val value: String?,
        val expectAllow: Boolean,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventCases")
    fun `event check decisions`(case: EventCase) {
        val result = TelemetryGuardrail.check(case.name, case.props)
        if (case.expectAllow) {
            assertInstanceOf(
                Allow::class.java,
                result,
                "Expected Allow for '${case.label}' but got Reject: ${(result as? Reject)?.reason}",
            )
        } else {
            assertInstanceOf(
                Reject::class.java,
                result,
                "Expected Reject for '${case.label}' but got Allow",
            )
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("userPropertyCases")
    fun `user property check decisions`(case: UserPropertyCase) {
        val result = TelemetryGuardrail.check(case.key, case.value)
        if (case.expectAllow) {
            assertInstanceOf(
                Allow::class.java,
                result,
                "Expected Allow for '${case.label}' but got Reject: ${(result as? Reject)?.reason}",
            )
        } else {
            assertInstanceOf(
                Reject::class.java,
                result,
                "Expected Reject for '${case.label}' but got Allow",
            )
        }
    }

    companion object {
        // Privacy-boundary fixture methods are necessarily long — each case is a distinct
        // forbidden/allowed example. Extracting sub-methods would obscure the exhaustive
        // coverage and make the fixture harder to audit in a privacy review.
        @Suppress("LongMethod")
        @JvmStatic
        fun eventCases(): Stream<Arguments> =
            Stream.of(
                // --- FORBIDDEN — full name (two capital words) ---
                Arguments.of(
                    EventCase(
                        label = "reject: full name 'María García' in unknown key 'recipient'",
                        name = "call_started",
                        props = mapOf("recipient" to "María García"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: full name 'Pepe Martínez' in unknown key 'by'",
                        name = "crash",
                        props = mapOf("by" to "Pepe Martínez"),
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — phone number ---
                Arguments.of(
                    EventCase(
                        label = "reject: international phone in unknown key 'number'",
                        name = "called",
                        props = mapOf("number" to "+34 600 123 456"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: digits-only phone in unknown key 'number'",
                        name = "called",
                        props = mapOf("number" to "600123456"),
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — email ---
                Arguments.of(
                    EventCase(
                        label = "reject: email in unknown key 'contact'",
                        name = "crash",
                        props = mapOf("contact" to "fran@example.com"),
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — transcript-shaped value (> 32 chars) on whitelisted key ---
                Arguments.of(
                    EventCase(
                        label = "reject: transcript-shaped value on whitelisted key 'action'",
                        name = "function_called",
                        props = mapOf("action" to "Te espero a las siete en la puerta del médico"),
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — unknown event name ---
                Arguments.of(
                    EventCase(
                        label = "reject: event name not on whitelist",
                        name = "totally_unknown_event",
                        props = mapOf("key" to "value"),
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — unknown prop key on a known event ---
                Arguments.of(
                    EventCase(
                        label = "reject: unknown prop key 'recipient_name' on known event",
                        name = "function_called",
                        props = mapOf("recipient_name" to "Pepito"),
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — PII-shaped forbidden keys (caught as "not on whitelist") ---
                Arguments.of(
                    EventCase(
                        label = "reject: 'message' key not on whitelist",
                        name = "function_called",
                        props = mapOf("message" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'transcript' key not on whitelist",
                        name = "function_called",
                        props = mapOf("transcript" to "qué hora es"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'body' key not on whitelist",
                        name = "function_called",
                        props = mapOf("body" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'content' key not on whitelist",
                        name = "function_called",
                        props = mapOf("content" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'contact_name' key not on whitelist",
                        name = "function_called",
                        props = mapOf("contact_name" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'phone' key not on whitelist",
                        name = "function_called",
                        props = mapOf("phone" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'phone_number' key not on whitelist",
                        name = "function_called",
                        props = mapOf("phone_number" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'name' key not on whitelist",
                        name = "function_called",
                        props = mapOf("name" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'alias' key not on whitelist",
                        name = "function_called",
                        props = mapOf("alias" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'address' key not on whitelist",
                        name = "function_called",
                        props = mapOf("address" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'utterance' key not on whitelist",
                        name = "function_called",
                        props = mapOf("utterance" to "ok"),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: 'query' key not on whitelist",
                        name = "function_called",
                        props = mapOf("query" to "ok"),
                        expectAllow = false,
                    ),
                ),
                // --- ALLOWED — canonical function call ---
                Arguments.of(
                    EventCase(
                        label = "allow: function_called canonical",
                        name = "function_called",
                        props =
                            mapOf(
                                "action" to "tell_time",
                                "confidence_bucket" to "high",
                                "latency_ms" to 380,
                                "from_warm" to true,
                            ),
                        expectAllow = true,
                    ),
                ),
                // --- ALLOWED — STT failures ---
                Arguments.of(
                    EventCase(
                        label = "allow: stt_failed NO_MATCH",
                        name = "stt_failed",
                        props = mapOf("error_code" to "NO_MATCH"),
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "allow: stt_failed SPEECH_TIMEOUT",
                        name = "stt_failed",
                        props = mapOf("error_code" to "SPEECH_TIMEOUT"),
                        expectAllow = true,
                    ),
                ),
                // --- ALLOWED — model load ---
                Arguments.of(
                    EventCase(
                        label = "allow: model_loaded",
                        name = "model_loaded",
                        props =
                            mapOf(
                                "model" to "function_gemma_270m",
                                "load_ms" to 1200,
                                "cold_start" to true,
                            ),
                        expectAllow = true,
                    ),
                ),
                // --- ALLOWED — US-024 (SF-3.6) model_decide telemetry ---
                Arguments.of(
                    EventCase(
                        label = "allow: model_decide success",
                        name = "model_decide",
                        props =
                            mapOf(
                                "model" to "function_gemma_270m",
                                "outcome" to "success",
                                "latency_ms" to 415,
                            ),
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "allow: model_decide unknown_function",
                        name = "model_decide",
                        props =
                            mapOf(
                                "model" to "function_gemma_270m",
                                "outcome" to "unknown_function",
                                "latency_ms" to 510,
                            ),
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "allow: model_decide model_cold",
                        name = "model_decide",
                        props =
                            mapOf(
                                "model" to "function_gemma_270m",
                                "outcome" to "model_cold",
                                "latency_ms" to 0,
                            ),
                        expectAllow = true,
                    ),
                ),
                // --- FORBIDDEN — US-024 (SF-3.6) model_decide must NEVER carry utterance / action ---
                Arguments.of(
                    EventCase(
                        label = "reject: model_decide with utterance prop",
                        name = "model_decide",
                        props =
                            mapOf(
                                "model" to "function_gemma_270m",
                                "outcome" to "success",
                                "latency_ms" to 415,
                                "utterance" to "qué hora es",
                            ),
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "reject: model_decide with action prop",
                        name = "model_decide",
                        props =
                            mapOf(
                                "model" to "function_gemma_270m",
                                "outcome" to "success",
                                "latency_ms" to 415,
                                "action" to "tell_time",
                            ),
                        expectAllow = false,
                    ),
                ),
                // --- ALLOWED — handler_invoked (US-025 / SF-4.1) ---
                Arguments.of(
                    EventCase(
                        label = "allow: handler_invoked success",
                        name = "handler_invoked",
                        props = mapOf("function_name" to "tell_time", "outcome" to "success"),
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "allow: handler_invoked needs_confirmation",
                        name = "handler_invoked",
                        props = mapOf("function_name" to "call_contact", "outcome" to "needs_confirmation"),
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "allow: handler_invoked failed",
                        name = "handler_invoked",
                        props = mapOf("function_name" to "open_app", "outcome" to "failed"),
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    EventCase(
                        label = "allow: handler_invoked crash",
                        name = "handler_invoked",
                        props = mapOf("function_name" to "open_app", "outcome" to "crash"),
                        expectAllow = true,
                    ),
                ),
                // --- FORBIDDEN — handler_invoked must not carry utterance / params / contact_name ---
                Arguments.of(
                    EventCase(
                        label = "reject: handler_invoked with utterance prop",
                        name = "handler_invoked",
                        props =
                            mapOf(
                                "function_name" to "call_contact",
                                "outcome" to "success",
                                "utterance" to "llama a Pepito",
                            ),
                        expectAllow = false,
                    ),
                ),
                // --- ALLOWED — policy_decided (SF-6.1 / US-041) ---
                Arguments.of(
                    EventCase(
                        label = "allow: policy_decided execute",
                        name = "policy_decided",
                        props =
                            mapOf(
                                "function_name" to "call_contact",
                                "decision" to "execute",
                                "confidence_bucket" to "high",
                                "always_confirm_on" to false,
                            ),
                        expectAllow = true,
                    ),
                ),
                // --- FORBIDDEN — policy_decided with an unknown prop key ---
                // forbidden — raw confidence value never on the wire
                Arguments.of(
                    EventCase(
                        label = "reject: policy_decided with raw confidence value",
                        name = "policy_decided",
                        props =
                            mapOf(
                                "function_name" to "call_contact",
                                "decision" to "execute",
                                "confidence_bucket" to "high",
                                "always_confirm_on" to false,
                                "confidence" to 0.95f,
                            ),
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — policy_decided with a too-long function_name (> 32 chars; PII heuristic) ---
                Arguments.of(
                    EventCase(
                        label = "reject: policy_decided with long function_name value",
                        name = "policy_decided",
                        props =
                            mapOf(
                                "function_name" to "call_contact_with_a_very_long_extra_suffix_value",
                                "decision" to "execute",
                                "confidence_bucket" to "high",
                                "always_confirm_on" to false,
                            ),
                        expectAllow = false,
                    ),
                ),
                // --- ALLOWED — launcher lifecycle ---
                Arguments.of(
                    EventCase(
                        label = "allow: launcher_set_default",
                        name = "launcher_set_default",
                        props = mapOf("attempt" to 1),
                        expectAllow = true,
                    ),
                ),
                // --- ALLOWED — confidence policy ---
                Arguments.of(
                    EventCase(
                        label = "allow: confidence_below_threshold",
                        name = "confidence_below_threshold",
                        props =
                            mapOf(
                                "function" to "open_app",
                                "threshold" to "execute",
                                "delta" to 0.07,
                            ),
                        expectAllow = true,
                    ),
                ),
                // --- ALLOWED — empty props ---
                Arguments.of(
                    EventCase(
                        label = "allow: app_open empty props",
                        name = "app_open",
                        props = emptyMap(),
                        expectAllow = true,
                    ),
                ),
            )

        @Suppress("LongMethod")
        @JvmStatic
        fun userPropertyCases(): Stream<Arguments> =
            Stream.of(
                // --- FORBIDDEN — email as user property value ---
                Arguments.of(
                    UserPropertyCase(
                        label = "reject: email as user property value",
                        key = "locale",
                        value = "fran@example.com",
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — full name as user property value ---
                Arguments.of(
                    UserPropertyCase(
                        label = "reject: full name as user property value",
                        key = "locale",
                        value = "Fran Sánchez",
                        expectAllow = false,
                    ),
                ),
                // --- FORBIDDEN — unknown user property key ---
                Arguments.of(
                    UserPropertyCase(
                        label = "reject: 'user_email' key not on ALLOWED_USER_PROPS",
                        key = "user_email",
                        value = "anything",
                        expectAllow = false,
                    ),
                ),
                Arguments.of(
                    UserPropertyCase(
                        label = "reject: 'user_name' key not on ALLOWED_USER_PROPS",
                        key = "user_name",
                        value = "Fran Sánchez",
                        expectAllow = false,
                    ),
                ),
                // --- ALLOWED — safe scalar enum user properties ---
                Arguments.of(
                    UserPropertyCase(
                        label = "allow: locale es_ES",
                        key = "locale",
                        value = "es_ES",
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    UserPropertyCase(
                        label = "allow: device_variant redmi_15_8gb",
                        key = "device_variant",
                        value = "redmi_15_8gb",
                        expectAllow = true,
                    ),
                ),
                Arguments.of(
                    UserPropertyCase(
                        label = "allow: hyperos_version 2",
                        key = "hyperos_version",
                        value = "2",
                        expectAllow = true,
                    ),
                ),
                // --- ALLOWED — null value clears the property (always safe) ---
                Arguments.of(
                    UserPropertyCase(
                        label = "allow: null value on known key",
                        key = "locale",
                        value = null,
                        expectAllow = true,
                    ),
                ),
            )
    }
}
