package com.curro.app.data.telephony

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Places a phone call directly via `Intent.ACTION_CALL` (US-034 / SF-4.10).
 *
 * **NOT `ACTION_DIAL`** — the spec (§6 flow 1) requires the call to fire without
 * an extra user tap on the dialer. `ACTION_CALL` requires `CALL_PHONE` permission.
 *
 * Phase 5 consideration: if `InCallService` support is added (spec §8), wire
 * call-state observations here.
 */
interface CallController {
    /**
     * Fires `Intent.ACTION_CALL` to [number]. Returns `true` on success.
     *
     * Returns `false` if:
     *   - [number] is blank.
     *   - `CALL_PHONE` is missing (SecurityException swallowed).
     *   - No activity can handle the intent (ActivityNotFoundException swallowed).
     *
     * Callers are responsible for resolving and cleaning the number before passing it.
     * CALL_PHONE gate check should be performed by the caller (handler) before invoking.
     */
    fun call(number: String): Boolean
}

/**
 * Production [CallController] using `Intent.ACTION_CALL` + `FLAG_ACTIVITY_NEW_TASK`.
 * The NEW_TASK flag is required when starting an activity from a non-Activity context
 * (handlers run inside a ViewModel coroutine, not in an Activity).
 */
class IntentCallController
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CallController {
        override fun call(number: String): Boolean {
            val cleaned = number.trim().ifEmpty { return false }
            val uri = Uri.parse("tel:" + Uri.encode(cleaned))
            val intent = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (_: SecurityException) {
                false
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
