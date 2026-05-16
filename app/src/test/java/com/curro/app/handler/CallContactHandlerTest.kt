package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.local.AliasSource
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.data.telephony.CallController
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.AliasRecord
import com.curro.app.domain.repository.ContactsProvider
import com.curro.app.util.FakeAliasRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CallContactHandler] (US-034 / SF-4.10, extended SF-7.3 / US-047).
 *
 * Uses inline fake implementations of [ContactsProvider], [FakeAliasRepository],
 * [CallController], [ReadContactsPermissionGate], and [CallPhonePermissionGate]
 * to avoid any Android framework dependencies.
 *
 * Privacy: contact names and phone numbers appear only in sentinels passed to the
 * fakes — never in log assertions.
 *
 * Groups:
 *  - SF-4.10 / SF-6.3 — existing cases (preserved unchanged)
 *  - SF-7.2 / US-046 — alias-first resolution
 *  - SF-7.3 / US-047 — Group L (11 cases): alias-learning + re-learn subflow
 */
@DisplayName("CallContactHandler (SF-4.10)")
class CallContactHandlerTest {
    private val context: Context = mockk()

    // -------------------------------------------------------------------------
    // Sentinel format templates — assert resource choice via prefix, not literal.
    // -------------------------------------------------------------------------
    @BeforeEach
    fun setUp() {
        val templates =
            mapOf(
                R.string.copy_contact_not_found to "NOT_FOUND:%s",
                R.string.copy_contact_ambiguous_phase4 to "AMBIGUOUS",
                R.string.copy_perm_missing_contacts to "PERM_CONTACTS",
                R.string.copy_perm_missing_calls to "PERM_CALLS",
                R.string.copy_calling to "CALLING:%s",
                R.string.copy_cancel_no_call to "CANCEL_NO_CALL",
                R.string.copy_disambig_ask_two to "ASK_TWO:%d|%s|%s|%s",
                R.string.copy_disambig_ask_two_masc to "ASK_TWO_MASC:%d|%s|%s|%s",
                R.string.copy_disambig_ask_three to "ASK_THREE:%d|%s|%s|%s|%s",
                R.string.copy_disambig_ask_three_masc to "ASK_THREE_MASC:%d|%s|%s|%s|%s",
                R.string.copy_disambig_ask_n to "ASK_N:%d|%s|%s",
                // SF-7.3 strings
                R.string.copy_alias_ask to "ALIAS_ASK:%s|%s",
                R.string.copy_alias_ask_more to "ALIAS_ASK_MORE",
                R.string.copy_alias_saved to "ALIAS_SAVED:%s|%s",
                R.string.copy_alias_defer_to_fran to "ALIAS_DEFER:%s",
                R.string.copy_alias_no_contacts to "ALIAS_NO_CONTACTS",
                R.string.copy_alias_unresolved to "ALIAS_UNRESOLVED:%s|%s",
            )
        every { context.getString(any()) } answers { templates[arg<Int>(0)] ?: "" }
        every { context.getString(any(), *anyVararg<Any>()) } answers {
            val template = templates[arg<Int>(0)] ?: ""
            val rawArg = if (args.size > 1) args[1] else null
            val formatArgs: Array<out Any?> =
                when (rawArg) {
                    is Array<*> -> rawArg
                    null -> emptyArray()
                    else -> arrayOf(rawArg)
                }
            if (formatArgs.isEmpty()) template else String.format(template, *formatArgs)
        }
    }

    // -------------------------------------------------------------------------
    // Inline fakes (pre-SF-7.3 tests use these directly via the `handler()` factory)
    // -------------------------------------------------------------------------

    private class InlineContactsProvider(
        private val contacts: List<Contact>,
        private val allContacts: List<Contact> = emptyList(),
    ) : ContactsProvider {
        override suspend fun findByName(query: String): List<Contact> = contacts

        override suspend fun findAll(): List<Contact> = allContacts

        override suspend fun findByLookupKey(lookupKey: String): Contact? = null
    }

    private class FakeCallController(private val result: Boolean = true) : CallController {
        var lastNumber: String? = null

        override fun call(number: String): Boolean {
            lastNumber = number
            return result
        }
    }

    private class FakeReadContactsPermissionGate(private val granted: Boolean) : ReadContactsPermissionGate {
        override fun isGranted(): Boolean = granted
    }

    private class FakeCallPhonePermissionGate(private val granted: Boolean) : CallPhonePermissionGate {
        override fun isGranted(): Boolean = granted
    }

    // -------------------------------------------------------------------------
    // Shared Group-L contacts
    // -------------------------------------------------------------------------

    private val antonio = makeContact("Antonio Pérez", "+34600000001", "lk-antonio")
    private val carmen = makeContact("Carmen López", "+34600000002", "lk-carmen")
    private val lucia = makeContact("Lucía Ruiz", "+34600000003", "lk-lucia")
    private val mariag = makeContact("María García", "+34600000004", "lk-mariag")
    private val pepito = makeContact("Pepito Sánchez", "+34600000005", "lk-pepito")

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    private fun makeContact(
        name: String,
        phone: String?,
        lookupKey: String = "lk-${name.lowercase()}",
    ) = Contact(
        lookupKey = lookupKey,
        displayName = name,
        phoneNumbers = if (phone != null) listOf(phone) else emptyList(),
        photoUri = null,
    )

    private fun call(contact: String = "Pepito") =
        FunctionCall(action = "call_contact", params = mapOf("contact" to contact), confidence = 0.9f)

    private fun callOf(
        action: String,
        vararg params: Pair<String, String>,
    ) = FunctionCall(action = action, params = params.toMap(), confidence = 0.9f)

    /**
     * Alias-returning fake that returns [aliasContacts] for ANY query — the pre-SF-7.2
     * tests don't care which alias was queried, only what came back. SF-7.2 adds the
     * keyed [FakeAliasRepository] for the alias-first-resolution test.
     */
    private inner class AnyQueryAliasRepository(private val contacts: List<Contact>) : FakeAliasRepository() {
        override suspend fun resolveAlias(alias: String): List<Contact> = contacts
    }

    @Suppress("LongParameterList")
    private fun handler(
        directContacts: List<Contact> = emptyList(),
        aliasContacts: List<Contact> = emptyList(),
        callResult: Boolean = true,
        readContactsGranted: Boolean = true,
        callPhoneGranted: Boolean = true,
    ): Pair<CallContactHandler, FakeCallController> {
        val controller = FakeCallController(callResult)
        return CallContactHandler(
            contacts = InlineContactsProvider(directContacts),
            aliases = AnyQueryAliasRepository(aliasContacts),
            callController = controller,
            readContactsGate = FakeReadContactsPermissionGate(readContactsGranted),
            callPhoneGate = FakeCallPhonePermissionGate(callPhoneGranted),
            context = context,
        ) to controller
    }

    /**
     * SF-7.3 handler factory — uses the shared [FakeAliasRepository] and a
     * fully configurable [InlineContactsProvider] with both findByName and findAll.
     */
    @Suppress("LongParameterList")
    private fun handlerL(
        fakeAlias: FakeAliasRepository = FakeAliasRepository(),
        byNameContacts: Map<String, List<Contact>> = emptyMap(),
        allContacts: List<Contact> = emptyList(),
        callResult: Boolean = true,
        readContactsGranted: Boolean = true,
        callPhoneGranted: Boolean = true,
    ): Triple<CallContactHandler, FakeCallController, FakeAliasRepository> {
        val controller = FakeCallController(callResult)
        val contactsProvider =
            object : ContactsProvider {
                override suspend fun findByName(query: String): List<Contact> = byNameContacts[query] ?: emptyList()

                override suspend fun findAll(): List<Contact> = allContacts

                override suspend fun findByLookupKey(lookupKey: String): Contact? = null
            }
        return Triple(
            CallContactHandler(
                contacts = contactsProvider,
                aliases = fakeAlias,
                callController = controller,
                readContactsGate = FakeReadContactsPermissionGate(readContactsGranted),
                callPhoneGate = FakeCallPhonePermissionGate(callPhoneGranted),
                context = context,
            ),
            controller,
            fakeAlias,
        )
    }

    // -------------------------------------------------------------------------
    // Tests — SF-4.10 / SF-6.3 (pre-SF-7.3, preserved unchanged)
    // -------------------------------------------------------------------------

    @Test
    fun `empty contact param returns ContactNotFound`() =
        runTest {
            val (h, _) = handler(directContacts = listOf(makeContact("Pepito", "+34600000001")))
            val result = h.handle(FunctionCall("call_contact", mapOf("contact" to ""), 0.9f))
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val err = (result as HandlerResult.Failed).reason
            assertInstanceOf(CurroError.ContactNotFound::class.java, err)
        }

    @Test
    fun `missing contact param key returns ContactNotFound`() =
        runTest {
            val (h, _) = handler()
            val result = h.handle(FunctionCall("call_contact", emptyMap(), 0.9f))
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            assertInstanceOf(CurroError.ContactNotFound::class.java, (result as HandlerResult.Failed).reason)
        }

    @Test
    fun `READ_CONTACTS denied returns ReadContactsPermissionMissing`() =
        runTest {
            val (h, _) = handler(readContactsGranted = false)
            val result = h.handle(call())
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            assertEquals(CurroError.ReadContactsPermissionMissing, failed.reason)
            assertEquals("PERM_CONTACTS", failed.speech)
        }

    @Test
    fun `no contact found returns ContactNotFound with copy`() =
        runTest {
            val (h, _) = handler(directContacts = emptyList())
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            assertInstanceOf(CurroError.ContactNotFound::class.java, failed.reason)
            assertEquals("NOT_FOUND:Pepito", failed.speech)
        }

    @Test
    fun `multiple direct contacts returns NeedsContactPick with the candidates`() =
        runTest {
            // SF-6.3 (US-043) — Phase-6 replaces the Phase-4 Failed(AmbiguousContact) with
            // a NeedsContactPick result. The coordinator routes through the picker
            // overlay.
            val contacts = listOf(makeContact("Pepito A", "+34600000001"), makeContact("Pepito B", "+34600000002"))
            val (h, _) = handler(directContacts = contacts)
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            val pick = result as HandlerResult.NeedsContactPick
            assertEquals(2, pick.candidates.size)
            assertEquals(contacts, pick.candidates)
            // Pepito ends in "o" → masculine ask-two copy.
            assertEquals("ASK_TWO_MASC:2|Pepito|Pepito A|Pepito B", pick.prompt)
        }

    @Test
    fun `single contact with phone number places call and returns Spoken`() =
        runTest {
            val (h, ctrl) = handler(directContacts = listOf(makeContact("Pepito", "+34600000001")))
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertEquals("CALLING:Pepito", (result as HandlerResult.Spoken).speech)
            assertEquals("+34600000001", ctrl.lastNumber)
        }

    @Test
    fun `contact has no phone number returns ContactNotFound`() =
        runTest {
            val (h, ctrl) = handler(directContacts = listOf(makeContact("Pepito", null)))
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            assertInstanceOf(CurroError.ContactNotFound::class.java, (result as HandlerResult.Failed).reason)
            assertEquals(null, ctrl.lastNumber)
        }

    @Test
    fun `CALL_PHONE denied returns PermissionDenied`() =
        runTest {
            val (h, _) =
                handler(
                    directContacts = listOf(makeContact("Pepito", "+34600000001")),
                    callPhoneGranted = false,
                )
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            assertEquals(CurroError.PermissionDenied, failed.reason)
            assertEquals("PERM_CALLS", failed.speech)
        }

    @Test
    fun `callController returning false maps to PermissionDenied`() =
        runTest {
            val (h, _) =
                handler(
                    directContacts = listOf(makeContact("Pepito", "+34600000001")),
                    callResult = false,
                )
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            assertEquals(CurroError.PermissionDenied, (result as HandlerResult.Failed).reason)
        }

    @Test
    fun `alias takes precedence over direct contact lookup`() =
        runTest {
            val aliasContact = makeContact("Mi hija", "+34600000099")
            // Even with a direct contact list, alias wins
            val directContact = makeContact("Hija", "+34600000001")
            val (h, ctrl) =
                handler(
                    directContacts = listOf(directContact),
                    aliasContacts = listOf(aliasContact),
                )
            val result = h.handle(call("mi hija"))
            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertEquals("+34600000099", ctrl.lastNumber)
        }

    @Test
    fun `alias empty falls back to direct contact lookup`() =
        runTest {
            val (h, ctrl) =
                handler(
                    directContacts = listOf(makeContact("Pepito", "+34600000001")),
                    aliasContacts = emptyList(),
                )
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertEquals("+34600000001", ctrl.lastNumber)
        }

    @Test
    fun `first phone number in list is used when contact has multiple`() =
        runTest {
            val contact =
                Contact(
                    lookupKey = "lk-pepito",
                    displayName = "Pepito",
                    phoneNumbers = listOf("+34600000001", "+34600000002"),
                    photoUri = null,
                )
            val (h, ctrl) = handler(directContacts = listOf(contact))
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertEquals("+34600000001", ctrl.lastNumber)
        }

    @Test
    fun `alias with multiple results uses first alias contact directly (SF-7_3 changed behaviour)`() =
        runTest {
            // SF-7.3 changed: when resolveAlias returns N contacts, placeCallOrFail uses
            // the first one directly. The alias lookup is considered authoritative —
            // no secondary picker is offered for alias-resolved contacts.
            val aliasContacts =
                listOf(
                    makeContact("Pepita A", "+34600000001"),
                    makeContact("Pepita B", "+34600000002"),
                )
            val (h, ctrl) = handler(aliasContacts = aliasContacts)
            val result = h.handle(call("mi pepita"))
            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertEquals("+34600000001", ctrl.lastNumber)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SF-6.3 / US-043 — NeedsContactPick disambiguation cases.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `three feminine matches uses copy_disambig_ask_three`() =
        runTest {
            val contacts =
                listOf(
                    makeContact("María García", "+34600000001"),
                    makeContact("María López", "+34600000002"),
                    makeContact("María Ruiz", "+34600000003"),
                )
            val (h, _) = handler(directContacts = contacts)
            val result = h.handle(call("María"))
            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            // María ends in "a" → feminine ask-three.
            assertEquals(
                "ASK_THREE:3|María|María García|María López|María Ruiz",
                (result as HandlerResult.NeedsContactPick).prompt,
            )
        }

    @Test
    fun `three masculine-query matches uses ask_three_masc`() =
        runTest {
            val contacts =
                listOf(
                    makeContact("Pepito A", "+34600000001"),
                    makeContact("Pepito B", "+34600000002"),
                    makeContact("Pepito C", "+34600000003"),
                )
            val (h, _) = handler(directContacts = contacts)
            val result = h.handle(call("Pepito"))
            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            assertEquals(
                "ASK_THREE_MASC:3|Pepito|Pepito A|Pepito B|Pepito C",
                (result as HandlerResult.NeedsContactPick).prompt,
            )
        }

    @Test
    fun `four matches uses copy_disambig_ask_n with the first three names`() =
        runTest {
            val contacts =
                listOf(
                    makeContact("María García", "+34600000001"),
                    makeContact("María López", "+34600000002"),
                    makeContact("María Ruiz", "+34600000003"),
                    makeContact("María Sánchez", "+34600000004"),
                )
            val (h, _) = handler(directContacts = contacts)
            val result = h.handle(call("María"))
            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            assertEquals(
                "ASK_N:4|María|María García, María López, María Ruiz",
                (result as HandlerResult.NeedsContactPick).prompt,
            )
            assertEquals(4, result.candidates.size)
        }

    @Test
    fun `NeedsContactPick onPick with valid contact places the call`() =
        runTest {
            val contacts =
                listOf(
                    makeContact("María García", "+34600000001"),
                    makeContact("María López", "+34600000002"),
                )
            val (h, ctrl) = handler(directContacts = contacts)
            val result = h.handle(call("María")) as HandlerResult.NeedsContactPick
            val pickResult = result.onPick(contacts[1])
            assertInstanceOf(HandlerResult.Spoken::class.java, pickResult)
            assertEquals("CALLING:María López", (pickResult as HandlerResult.Spoken).speech)
            assertEquals("+34600000002", ctrl.lastNumber)
        }

    @Test
    fun `NeedsContactPick onPick(null) returns Spoken(copy_cancel_no_call)`() =
        runTest {
            val contacts =
                listOf(
                    makeContact("María García", "+34600000001"),
                    makeContact("María López", "+34600000002"),
                )
            val (h, ctrl) = handler(directContacts = contacts)
            val result = h.handle(call("María")) as HandlerResult.NeedsContactPick
            val pickResult = result.onPick(null)
            assertInstanceOf(HandlerResult.Spoken::class.java, pickResult)
            assertEquals("CANCEL_NO_CALL", (pickResult as HandlerResult.Spoken).speech)
            assertEquals(null, ctrl.lastNumber)
        }

    @Test
    fun `whitespace-only contact param treated as empty and returns ContactNotFound`() =
        runTest {
            val (h, _) = handler()
            val result = h.handle(FunctionCall("call_contact", mapOf("contact" to "   "), 0.9f))
            assertInstanceOf(HandlerResult.Failed::class.java, result)
            assertInstanceOf(CurroError.ContactNotFound::class.java, (result as HandlerResult.Failed).reason)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SF-7.2 / US-046 — alias-first resolution (RoomAliasRepository wired in)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `alias hit resolves contact directly without falling back to findByName`() =
        runTest {
            // Arrange: alias repo returns a contact for "mi hija"; direct contacts are empty.
            val luciaContact = makeContact("Lucía Ruiz", "+34600000099")
            val fakeRepo = FakeAliasRepository()
            fakeRepo.resolveAliasResult["mi hija"] = listOf(luciaContact)
            val controller = FakeCallController()
            val h =
                CallContactHandler(
                    contacts = InlineContactsProvider(emptyList()),
                    aliases = fakeRepo,
                    callController = controller,
                    readContactsGate = FakeReadContactsPermissionGate(true),
                    callPhoneGate = FakeCallPhonePermissionGate(true),
                    context = context,
                )
            // Act
            val result = h.handle(FunctionCall("call_contact", mapOf("contact" to "mi hija"), 0.95f))
            // Assert: call placed on Lucía's number, not via findByName
            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertEquals("CALLING:Lucía Ruiz", (result as HandlerResult.Spoken).speech)
            assertEquals("+34600000099", controller.lastNumber)
        }

    // ─────────────────────────────────────────────────────────────────────────
    // SF-7.3 / US-047 — Group L: alias-learning + re-learn subflow (11 cases)
    // ─────────────────────────────────────────────────────────────────────────

    /** L1. Relational term + alias miss → NeedsContactPick with copy_alias_ask. */
    @Test
    fun `L1 relationalTerm_aliasMiss_returnsNeedsContactPick_with_copy_alias_ask`() =
        runTest {
            // Exactly 5 contacts → no _more suffix.
            val (h, _, fakeAlias) = handlerL(allContacts = listOf(antonio, carmen, lucia, mariag, pepito))

            val result = h.handle(callOf("call_contact", "contact" to "mi hija"))

            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            val pick = result as HandlerResult.NeedsContactPick
            // Template: "ALIAS_ASK:%s|%s" → %1$s = "mi hija", %2$s = csv of names
            assertEquals(
                "ALIAS_ASK:mi hija|Antonio Pérez, Carmen López, Lucía Ruiz, María García, Pepito Sánchez",
                pick.prompt,
            )
            assertEquals(5, pick.candidates.size)
            assertTrue(fakeAlias.learnCalls.isEmpty())
        }

    /** L2. Relational term + more than 5 contacts → prompt appends copy_alias_ask_more. */
    @Test
    fun `L2 relationalTerm_moreThan5Contacts_promptAppends_copy_alias_ask_more`() =
        runTest {
            val thirtyContacts = (1..30).map { Contact("lk-$it", "Nombre$it", listOf("+34$it"), null) }
            val (h, _, fakeAlias) = handlerL(allContacts = thirtyContacts)

            val result = h.handle(callOf("call_contact", "contact" to "mi hija"))

            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            val pick = result as HandlerResult.NeedsContactPick
            assertTrue(
                pick.prompt.endsWith("ALIAS_ASK_MORE"),
                "prompt should end with _more sentinel but was: ${pick.prompt}",
            )
            assertEquals(5, pick.candidates.size)
            assertTrue(fakeAlias.learnCalls.isEmpty())
        }

    /**
     * L3. Relational term + user picks candidate → alias persisted, combined copy_alias_saved,
     * call placed.
     */
    @Test
    fun `L3 relationalTerm_userPicksCandidate_persistsAlias_speaksCopyAliasSaved_thenPlacesCall`() =
        runTest {
            val (h, ctrl, fakeAlias) = handlerL(allContacts = listOf(antonio, lucia))

            val firstResult = h.handle(callOf("call_contact", "contact" to "mi hija"))
            val pick = firstResult as HandlerResult.NeedsContactPick

            val onPickResult = pick.onPick(lucia)

            assertInstanceOf(HandlerResult.Spoken::class.java, onPickResult)
            val spoken = onPickResult as HandlerResult.Spoken
            // Template: "ALIAS_SAVED:%s|%s" → alias="mi hija", displayName="Lucía Ruiz"
            assertEquals("ALIAS_SAVED:mi hija|Lucía Ruiz", spoken.speech)
            // Pin: learn was called with alias + lookupKey + LEARNED
            assertEquals(1, fakeAlias.learnCalls.size)
            assertEquals("mi hija", fakeAlias.learnCalls.first().alias)
            assertEquals(lucia.lookupKey, fakeAlias.learnCalls.first().contactLookupKey)
            assertEquals(AliasSource.LEARNED, fakeAlias.learnCalls.first().source)
            // Call was placed on Lucía's number.
            assertEquals(lucia.phoneNumbers.first(), ctrl.lastNumber)
        }

    /**
     * L4. Relational term + user picks "ninguna" → copy_alias_defer_to_fran; no alias saved;
     * no call placed. (Rule-3 half: learning path with Ninguna.)
     */
    @Test
    fun `L4 relationalTerm_userPicksNinguna_speaks_copy_alias_defer_to_fran_noAliasSaved_noCallPlaced`() =
        runTest {
            val (h, ctrl, fakeAlias) = handlerL(allContacts = listOf(antonio, lucia))

            val firstResult = h.handle(callOf("call_contact", "contact" to "mi hija"))
            val pick = firstResult as HandlerResult.NeedsContactPick

            val onPickResult = pick.onPick(null) // "Ninguna"

            assertInstanceOf(HandlerResult.Spoken::class.java, onPickResult)
            val spoken = onPickResult as HandlerResult.Spoken
            assertEquals("ALIAS_DEFER:mi hija", spoken.speech)
            // Pin: no learn, no call.
            assertTrue(fakeAlias.learnCalls.isEmpty())
            assertNull(ctrl.lastNumber)
        }

    /** L5. Relational term + zero contacts → Failed(copy_alias_no_contacts). */
    @Test
    fun `L5 relationalTerm_zeroContacts_returns_copy_alias_no_contacts_failed`() =
        runTest {
            val (h, _, _) = handlerL(allContacts = emptyList())

            val result = h.handle(callOf("call_contact", "contact" to "mi hija"))

            assertInstanceOf(HandlerResult.Failed::class.java, result)
            val failed = result as HandlerResult.Failed
            assertEquals("ALIAS_NO_CONTACTS", failed.speech)
            assertInstanceOf(CurroError.ContactNotFound::class.java, failed.reason)
        }

    /** L6. Existing alias resolves directly; no learning offered (SF-7.2 regression). */
    @Test
    fun `L6 existingAlias_resolvesDirectly_noLearningOffered`() =
        runTest {
            val fakeAlias = FakeAliasRepository()
            fakeAlias.resolveAliasResult["mi hija"] = listOf(lucia)
            val (h, ctrl, _) = handlerL(fakeAlias = fakeAlias)

            val result = h.handle(callOf("call_contact", "contact" to "mi hija"))

            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertTrue(fakeAlias.learnCalls.isEmpty())
            assertEquals(lucia.phoneNumbers.first(), ctrl.lastNumber)
        }

    /** L7. Stale alias (DAO has row; lookup-key returns null) → NeedsContactPick with copy_alias_unresolved. */
    @Test
    fun `L7 staleAlias_lookupKeyDoesNotResolve_entersReLearnFlow_with_copy_alias_unresolved`() =
        runTest {
            val fakeAlias = FakeAliasRepository()
            // resolveAlias returns empty (stale), findStoredAlias returns the old record.
            fakeAlias.resolveAliasResult["mi hija"] = emptyList()
            fakeAlias.findStoredAliasResult["mi hija"] = AliasRecord("Lucía Ruiz", AliasSource.LEARNED)
            val (h, _, _) = handlerL(fakeAlias = fakeAlias, allContacts = listOf(antonio, carmen, mariag))

            val result = h.handle(callOf("call_contact", "contact" to "mi hija"))

            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            val pick = result as HandlerResult.NeedsContactPick
            // Template: "ALIAS_UNRESOLVED:%s|%s" → alias="mi hija", oldName="Lucía Ruiz"
            assertEquals("ALIAS_UNRESOLVED:mi hija|Lucía Ruiz", pick.prompt)
            assertEquals(3, pick.candidates.size)
        }

    /** L8. Stale alias + re-learn: user picks new contact → learn() called + call placed. */
    @Test
    fun `L8 staleAlias_reLearn_userPicksNewContact_replacesAlias`() =
        runTest {
            val fakeAlias = FakeAliasRepository()
            fakeAlias.resolveAliasResult["mi hija"] = emptyList()
            fakeAlias.findStoredAliasResult["mi hija"] = AliasRecord("Lucía Ruiz", AliasSource.LEARNED)
            val (h, ctrl, _) = handlerL(fakeAlias = fakeAlias, allContacts = listOf(carmen, mariag))

            val firstResult = h.handle(callOf("call_contact", "contact" to "mi hija"))
            val pick = firstResult as HandlerResult.NeedsContactPick

            val onPickResult = pick.onPick(carmen)

            assertInstanceOf(HandlerResult.Spoken::class.java, onPickResult)
            assertEquals(carmen.lookupKey, fakeAlias.learnCalls.first().contactLookupKey)
            assertTrue((onPickResult as HandlerResult.Spoken).speech.contains("mi hija"))
            assertTrue(onPickResult.speech.contains("Carmen López"))
            assertEquals(carmen.phoneNumbers.first(), ctrl.lastNumber)
        }

    /** L9. Non-relational query + single match → call placed; no learn. */
    @Test
    fun `L9 nonRelationalQuery_singleMatch_doesNotLearn`() =
        runTest {
            val fakeAlias = FakeAliasRepository()
            val (h, ctrl, _) =
                handlerL(
                    fakeAlias = fakeAlias,
                    byNameContacts = mapOf("Pepito" to listOf(pepito)),
                )

            val result = h.handle(callOf("call_contact", "contact" to "Pepito"))

            assertInstanceOf(HandlerResult.Spoken::class.java, result)
            assertTrue(fakeAlias.learnCalls.isEmpty())
            assertEquals(pepito.phoneNumbers.first(), ctrl.lastNumber)
        }

    /** L10. Non-relational query + 3 matches → SF-6.3 disambig path; no learning even after onPick. */
    @Test
    fun `L10 nonRelationalQuery_threeMatches_returnsNeedsContactPick_learnNeverCalled_evenOnPick`() =
        runTest {
            val marial = makeContact("María López", "+34600000010", "lk-marial")
            val mariar = makeContact("María Ruiz", "+34600000011", "lk-mariar")
            val fakeAlias = FakeAliasRepository()
            val (h, ctrl, _) =
                handlerL(
                    fakeAlias = fakeAlias,
                    byNameContacts = mapOf("María" to listOf(mariag, marial, mariar)),
                )

            val firstResult = h.handle(callOf("call_contact", "contact" to "María"))
            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, firstResult)
            val pick = firstResult as HandlerResult.NeedsContactPick
            // This is the SF-6.3 disambig prompt, not the SF-7.3 learning prompt.
            assertTrue(pick.prompt.startsWith("ASK_THREE:"), "expected disambig prompt, got: ${pick.prompt}")

            // Pick one candidate.
            val onPickResult = pick.onPick(mariag)
            assertInstanceOf(HandlerResult.Spoken::class.java, onPickResult)
            assertEquals(mariag.phoneNumbers.first(), ctrl.lastNumber)
            // Rule-3 invariant: no alias saved.
            assertTrue(fakeAlias.learnCalls.isEmpty(), "learn must NOT be called on disambig path")
        }

    /**
     * L11. Disambig pick: valid pick places call but does NOT learn (explicit rule-3 invariant test).
     * Verifies the structural fence between SF-6.3 buildPickResult and SF-7.3 learningPickCallback.
     */
    @Test
    fun `L11 disambigPath_userPickValid_placesCallButDoesNotLearn`() =
        runTest {
            val fakeAlias = FakeAliasRepository()
            val (h, ctrl, _) =
                handlerL(
                    fakeAlias = fakeAlias,
                    byNameContacts = mapOf("Pepito" to listOf(makeContact("Pepito A", "+34600000001"), pepito)),
                )

            val firstResult = h.handle(callOf("call_contact", "contact" to "Pepito"))
            val pick = firstResult as HandlerResult.NeedsContactPick

            pick.onPick(pepito)

            assertEquals(pepito.phoneNumbers.first(), ctrl.lastNumber)
            // The structural fence: SF-6.3 buildPickResult.onPick NEVER calls aliasRepository.learn.
            assertEquals(0, fakeAlias.learnCalls.size)
        }
}
