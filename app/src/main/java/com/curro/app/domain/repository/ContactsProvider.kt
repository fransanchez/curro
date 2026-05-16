package com.curro.app.domain.repository

import com.curro.app.domain.model.Contact

/**
 * Resolves a spoken name to 0, 1, or many [Contact] records (US-033 / SF-4.9).
 *
 * The production implementation queries `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`.
 * Tests inject a `FakeContactsQueryRunner` that returns hand-built rows — no real
 * `ContentResolver` or `Cursor` is touched in unit tests.
 */
interface ContactsProvider {
    /**
     * Resolve [query] (a spoken name, already trimmed by FunctionGemma) to [Contact] records.
     *
     * Normalisation: lowercase + accent-strip (Spanish locale, via [curroNormalize]) on both sides.
     * Matching:
     *   - Single-token query → word-boundary regex (`\bquery\b`) to avoid "ana" matching "Susana".
     *   - Multi-token query  → substring contains (word-boundary over a phrase is fragile).
     *
     * Returns:
     *   - `emptyList()` if [query] is blank or no rows match.
     *   - 1 element if exactly one contact matched.
     *   - N elements if N distinct `LOOKUP_KEY`s matched — the caller decides (SF-4.10 returns
     *     `CurroError.AmbiguousContact`).
     *
     * The caller checks `ReadContactsPermissionGate.isGranted()` before calling — the
     * implementation catches `SecurityException` and returns `emptyList()` defensively.
     */
    suspend fun findByName(query: String): List<Contact>
}
