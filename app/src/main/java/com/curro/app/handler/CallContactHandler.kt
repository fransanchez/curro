package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.apps.curroNormalize
import com.curro.app.data.local.AliasSource
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.data.telephony.CallController
import com.curro.app.domain.alias.RelationalTerms
import com.curro.app.domain.handler.FunctionHandler
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.AliasRepository
import com.curro.app.domain.repository.ContactsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Places a phone call to a contact resolved by spoken name or alias (US-034 / SF-4.10).
 *
 * Resolution order:
 *   1. `aliases.resolveAlias(query)` — Phase-4 stub always empty; Phase-7 wire-up resolves "mi hija".
 *   2. If alias returns empty → `contacts.findByName(query)`.
 *
 * Outcomes:
 *   - 0 candidates → `Failed(copy_contact_not_found, ContactNotFound)`.
 *   - 1 candidate → place call → `Spoken(copy_calling)`.
 *   - N > 1 → SF-6.3 `NeedsContactPick(prompt, candidates, onPick)` (the coordinator
 *     routes through the `Confirming` state and the picker overlay).
 *   - No phone number → `Failed(copy_contact_not_found, ContactNotFound)`.
 *   - CALL_PHONE denied → `Failed(copy_perm_missing_calls, PermissionDenied)`.
 *   - READ_CONTACTS denied → `Failed(copy_perm_missing_contacts, ReadContactsPermissionMissing)`.
 *   - `callController.call` returns false (SecurityException edge) → `Failed(PermissionDenied)`.
 *
 * `needs_confirmation: CONDITIONAL` in the catalog. Phase 6's `ConfidencePolicy`
 * gates the dispatcher; if the policy says Execute and the lookup is multi-
 * match, this handler escalates via `NeedsContactPick` (spec §6 flow 3).
 *
 * Privacy: contact names and phone numbers are NEVER logged.
 */
class CallContactHandler
    @Inject
    constructor(
        private val contacts: ContactsProvider,
        private val aliases: AliasRepository,
        private val callController: CallController,
        private val readContactsGate: ReadContactsPermissionGate,
        private val callPhoneGate: CallPhonePermissionGate,
        @ApplicationContext private val context: Context,
    ) : FunctionHandler {
        override val functionName: String = "call_contact"

        @Suppress("ReturnCount", "CyclomaticComplexMethod")
        override suspend fun handle(call: FunctionCall): HandlerResult {
            val rawQuery = (call.params["contact"] as? String).orEmpty().trim()
            if (rawQuery.isEmpty()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_contact_not_found, ""),
                    CurroError.ContactNotFound(""),
                )
            }

            // READ_CONTACTS gate — checked before any ContentResolver query.
            if (!readContactsGate.isGranted()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_contacts),
                    CurroError.ReadContactsPermissionMissing,
                )
            }

            val normalisedQuery = rawQuery.lowercase().curroNormalize()

            // 1. Alias hit → direct call (SF-7.2 path).
            val aliasMatches = aliases.resolveAlias(rawQuery)
            if (aliasMatches.isNotEmpty()) {
                return placeCallOrFail(aliasMatches.first(), rawQuery)
            }

            // 2. Stale-alias detection (SF-7.3 re-learn) — DAO has the row but the
            //    LOOKUP_KEY no longer resolves. Distinguished from "no alias at all"
            //    so we can speak copy_alias_unresolved instead of the generic ask.
            val storedAlias = aliases.findStoredAlias(rawQuery)
            if (storedAlias != null) {
                return enterReLearnMode(rawQuery, storedAlias.displayName)
            }

            // 3. Relational term, no alias yet → enter learning (SF-7.3 happy path).
            if (normalisedQuery in RelationalTerms.all) {
                return enterLearningMode(rawQuery)
            }

            // 4. Phase-4/Phase-6 path: name lookup + disambig. NOT learning.
            val candidates = contacts.findByName(rawQuery)
            return when {
                candidates.isEmpty() ->
                    HandlerResult.Failed(
                        context.getString(R.string.copy_contact_not_found, rawQuery),
                        CurroError.ContactNotFound(rawQuery),
                    )
                candidates.size > 1 -> buildPickResult(rawQuery, candidates)
                else -> placeCallOrFail(candidates.first(), rawQuery)
            }
        }

        /**
         * SF-7.3 learning mode (spec flow 4 happy path).
         *
         * Reads up to [LEARNING_CANDIDATE_LIMIT] contacts (alphabetical), composes
         * `copy_alias_ask` (+ `copy_alias_ask_more` if there are more than 5), returns
         * a [HandlerResult.NeedsContactPick] whose `onPick` is bound to
         * [learningPickCallback]. **The only code path that calls [AliasRepository.learn].**
         */
        @Suppress("ReturnCount")
        private suspend fun enterLearningMode(rawQuery: String): HandlerResult {
            val all = contacts.findAll()
            if (all.isEmpty()) {
                return HandlerResult.Failed(
                    speech = context.getString(R.string.copy_alias_no_contacts),
                    reason = CurroError.ContactNotFound(rawQuery),
                )
            }
            val candidates = all.take(LEARNING_CANDIDATE_LIMIT)
            val namesCsv = candidates.joinToString(", ") { it.displayName }
            val askPrompt = context.getString(R.string.copy_alias_ask, rawQuery, namesCsv)
            val prompt =
                if (all.size > LEARNING_CANDIDATE_LIMIT) {
                    "$askPrompt ${context.getString(R.string.copy_alias_ask_more)}"
                } else {
                    askPrompt
                }
            return HandlerResult.NeedsContactPick(
                prompt = prompt,
                candidates = candidates,
                onPick = { picked -> learningPickCallback(rawQuery, picked) },
            )
        }

        /**
         * SF-7.3 re-learn mode (stale-`LOOKUP_KEY` path).
         *
         * Speaks `copy_alias_unresolved` (with the old display name for context).
         * Reuses [learningPickCallback] — a successful pick triggers `aliasRepository.learn(...)`
         * which REPLACEs the stale row via the unique-index `OnConflictStrategy.REPLACE`.
         */
        @Suppress("ReturnCount")
        private suspend fun enterReLearnMode(
            rawQuery: String,
            oldDisplayName: String,
        ): HandlerResult {
            val all = contacts.findAll()
            if (all.isEmpty()) {
                return HandlerResult.Failed(
                    speech = context.getString(R.string.copy_alias_no_contacts),
                    reason = CurroError.ContactNotFound(rawQuery),
                )
            }
            val candidates = all.take(LEARNING_CANDIDATE_LIMIT)
            val prompt = context.getString(R.string.copy_alias_unresolved, rawQuery, oldDisplayName)
            return HandlerResult.NeedsContactPick(
                prompt = prompt,
                candidates = candidates,
                onPick = { picked -> learningPickCallback(rawQuery, picked) },
            )
        }

        /**
         * SF-7.3 learning callback (shared between learning and re-learn modes).
         *
         * - `picked == null` (user said "ninguna") → defer-to-Fran copy; **no alias saved**.
         * - `picked != null` → `aliasRepository.learn(rawQuery, picked, LEARNED)` THEN place
         *   the call; the speech is the combined `copy_alias_saved`. One TTS pass.
         *
         * Pin: learn FIRST, then call. If the call fails (permission), the alias is still
         * saved — the user has taught Curro who "mi hija" is even if the immediate call
         * doesn't go through.
         */
        private suspend fun learningPickCallback(
            rawQuery: String,
            picked: Contact?,
        ): HandlerResult {
            if (picked == null) {
                return HandlerResult.Spoken(
                    speech = context.getString(R.string.copy_alias_defer_to_fran, rawQuery),
                )
            }
            aliases.learn(rawQuery, picked, AliasSource.LEARNED)
            val callResult = placeCallOrFail(picked, rawQuery)
            return when (callResult) {
                is HandlerResult.Spoken ->
                    HandlerResult.Spoken(
                        speech = context.getString(R.string.copy_alias_saved, rawQuery, picked.displayName),
                        screen = callResult.screen,
                    )
                // Permission failure or other Failed — keep the underlying error copy.
                // The alias is still saved though (rule: learn BEFORE call).
                else -> callResult
            }
        }

        /**
         * SF-6.3 (US-043) — multi-match → return [HandlerResult.NeedsContactPick]
         * with the disambiguation prompt + the picker resolver.
         *
         * The gender heuristic checks the query's last character: ending in
         * `"o"` → masculine ("Mejor llámalo…"); else feminine ("Mejor
         * llámala…"). Acceptable for the prototype since the canonical case
         * (multiple "María"s) is feminine; Phase 7 may override per-contact.
         */
        private fun buildPickResult(
            rawQuery: String,
            candidates: List<Contact>,
        ): HandlerResult {
            val prompt = buildDisambigPrompt(rawQuery, candidates)
            return HandlerResult.NeedsContactPick(
                prompt = prompt,
                candidates = candidates,
                onPick = { picked ->
                    if (picked == null) {
                        HandlerResult.Spoken(context.getString(R.string.copy_cancel_no_call))
                    } else {
                        placeCallOrFail(picked, rawQuery)
                    }
                },
            )
        }

        private fun buildDisambigPrompt(
            rawQuery: String,
            candidates: List<Contact>,
        ): String {
            val masculine = rawQuery.lowercase().endsWith("o")
            return when (candidates.size) {
                DISAMBIG_TWO ->
                    context.getString(
                        if (masculine) R.string.copy_disambig_ask_two_masc else R.string.copy_disambig_ask_two,
                        candidates.size,
                        rawQuery,
                        candidates[0].displayName,
                        candidates[1].displayName,
                    )
                DISAMBIG_THREE ->
                    context.getString(
                        if (masculine) R.string.copy_disambig_ask_three_masc else R.string.copy_disambig_ask_three,
                        candidates.size,
                        rawQuery,
                        candidates[0].displayName,
                        candidates[1].displayName,
                        candidates[2].displayName,
                    )
                else -> {
                    val firstThree =
                        candidates.take(DISAMBIG_THREE).joinToString(", ") { it.displayName }
                    context.getString(
                        R.string.copy_disambig_ask_n,
                        candidates.size,
                        rawQuery,
                        firstThree,
                    )
                }
            }
        }

        private companion object {
            const val DISAMBIG_TWO = 2
            const val DISAMBIG_THREE = 3
            const val LEARNING_CANDIDATE_LIMIT = 5
        }

        /**
         * Attempts to place the call.
         *
         * First phone number wins (Phase 6 may add a phone picker).
         * No phone number on file → graceful [CurroError.ContactNotFound].
         * CALL_PHONE denied or controller returns false → [CurroError.PermissionDenied].
         */
        @Suppress("ReturnCount")
        private fun placeCallOrFail(
            contact: Contact,
            originalQuery: String,
        ): HandlerResult {
            val number = contact.phoneNumbers.firstOrNull()
            if (number.isNullOrBlank()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_contact_not_found, originalQuery),
                    CurroError.ContactNotFound(originalQuery),
                )
            }
            if (!callPhoneGate.isGranted()) {
                return HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_calls),
                    CurroError.PermissionDenied,
                )
            }
            val ok = callController.call(number)
            return if (ok) {
                HandlerResult.Spoken(context.getString(R.string.copy_calling, contact.displayName))
            } else {
                HandlerResult.Failed(
                    context.getString(R.string.copy_perm_missing_calls),
                    CurroError.PermissionDenied,
                )
            }
        }
    }
