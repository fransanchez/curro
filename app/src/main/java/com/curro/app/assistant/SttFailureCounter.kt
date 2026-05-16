package com.curro.app.assistant

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks consecutive STT failures for the spec §6 flow 6 policy
 * (1st → `copy_stt_fail_1`, 2nd → `copy_stt_fail_2`, 3rd → `copy_stt_fail_3`).
 *
 * - [recordFailure] increments the count and returns the new value (≥ 1).
 * - [recordSuccess] resets the count to 0.
 *
 * **Caller decides which message to speak.** This class is intentionally
 * dumb — it doesn't know about `strings.xml`; the coordinator picks the copy
 * based on the returned count. The counter has no upper bound (returns 4, 5,
 * 6, … if the caller forgets to reset on hitting 3) — the coordinator's
 * `GIVE_UP_THRESHOLD = 3` is the policy, this class is the mechanism.
 *
 * **Thread-safety**: the count is mutated only from the coordinator's
 * `Main.immediate` scope. No synchronisation needed. (If a future caller wires
 * this from a different thread, switch to an `AtomicInteger`. Pin: not now.)
 *
 * **Lifetime**: `@Singleton`. The counter survives the launcher's lifecycle —
 * but **not** process death, which is correct: if the process died, the user
 * is starting over and "consecutive" no longer applies.
 */
@Singleton
class SttFailureCounter
    @Inject
    constructor() {
        private var count: Int = 0

        /** Call after each STT failure. Returns the new count (≥ 1). */
        fun recordFailure(): Int {
            count += 1
            return count
        }

        /**
         * Call after any successful turn (final transcript delivered + validated).
         * Resets the count to 0.
         */
        fun recordSuccess() {
            count = 0
        }

        /** Test-only — never read in production code. */
        internal fun peek(): Int = count
    }
