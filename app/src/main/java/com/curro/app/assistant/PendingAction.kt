package com.curro.app.assistant

import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact

/**
 * The action to invoke when the user resolves the `Confirming` state — either a
 * yes/no confirmation (spec §6 flow 2) or a multi-match contact pick (spec §6
 * flow 3).
 *
 * The [kind] discriminates the two cases. `LauncherPlaceholderScreen` routes
 * between [com.curro.app.presentation.assistant.ConfirmationOverlay] (SF-6.2)
 * and [com.curro.app.presentation.assistant.ContactPickerOverlay] (SF-6.3) on
 * this discriminator.
 *
 * @param functionName the catalog snake_case name (used for telemetry only).
 * @param kind the resolution shape.
 */
data class PendingAction(
    val functionName: String,
    val kind: Kind,
) {
    sealed interface Kind {
        /**
         * SF-6.2 — yes/no confirmation. Phase-6's confidence-graded path
         * lands here when `call_contact` confidence is in `[0.60, 0.85)`, or
         * when the always-confirm toggle is on, or when a Phase-2+ handler
         * (`send_whatsapp_reply`) explicitly returns `NeedsConfirmation`.
         */
        data class YesNo(
            val onConfirm: suspend () -> HandlerResult,
        ) : Kind

        /**
         * SF-6.3 — multiple matches; the launcher renders a picker overlay.
         * `onPick(contact)` invokes the handler with the user's choice.
         * `onPick(null)` signals "user said ninguna" and the handler returns
         * the cancellation copy.
         */
        data class PickContact(
            val candidates: List<Contact>,
            val onPick: suspend (Contact?) -> HandlerResult,
        ) : Kind
    }
}
