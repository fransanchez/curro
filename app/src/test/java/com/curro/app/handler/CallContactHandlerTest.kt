package com.curro.app.handler

import android.content.Context
import com.curro.app.R
import com.curro.app.data.permissions.CallPhonePermissionGate
import com.curro.app.data.permissions.ReadContactsPermissionGate
import com.curro.app.data.telephony.CallController
import com.curro.app.domain.handler.HandlerResult
import com.curro.app.domain.model.Contact
import com.curro.app.domain.model.CurroError
import com.curro.app.domain.model.FunctionCall
import com.curro.app.domain.repository.ContactsProvider
import com.curro.app.util.FakeAliasRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [CallContactHandler] (US-034 / SF-4.10).
 *
 * Uses inline fake implementations of [ContactsProvider], [AliasRepository],
 * [CallController], [ReadContactsPermissionGate], and [CallPhonePermissionGate]
 * to avoid any Android framework dependencies.
 *
 * Privacy: contact names and phone numbers appear only in sentinels passed to the
 * fakes — never in log assertions.
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
    // Inline fakes
    // -------------------------------------------------------------------------

    private class FakeContactsProvider(private val contacts: List<Contact>) : ContactsProvider {
        override suspend fun findByName(query: String): List<Contact> = contacts

        /** SF-7.2 — not exercised by the pre-SF-7.2 tests; returns null by default. */
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
    // Factory helpers
    // -------------------------------------------------------------------------

    private fun makeContact(
        name: String,
        phone: String?,
    ) = Contact(
        lookupKey = "lk-${name.lowercase()}",
        displayName = name,
        phoneNumbers = if (phone != null) listOf(phone) else emptyList(),
        photoUri = null,
    )

    private fun call(contact: String = "Pepito") =
        FunctionCall(action = "call_contact", params = mapOf("contact" to contact), confidence = 0.9f)

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
            contacts = FakeContactsProvider(directContacts),
            aliases = AnyQueryAliasRepository(aliasContacts),
            callController = controller,
            readContactsGate = FakeReadContactsPermissionGate(readContactsGranted),
            callPhoneGate = FakeCallPhonePermissionGate(callPhoneGranted),
            context = context,
        ) to controller
    }

    // -------------------------------------------------------------------------
    // Tests
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
    fun `alias with multiple results returns NeedsContactPick`() =
        runTest {
            val aliasContacts =
                listOf(
                    makeContact("Pepita A", "+34600000001"),
                    makeContact("Pepita B", "+34600000002"),
                )
            val (h, _) = handler(aliasContacts = aliasContacts)
            val result = h.handle(call("mi pepita"))
            assertInstanceOf(HandlerResult.NeedsContactPick::class.java, result)
            assertEquals(2, (result as HandlerResult.NeedsContactPick).candidates.size)
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
                    contacts = FakeContactsProvider(emptyList()),
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
}
