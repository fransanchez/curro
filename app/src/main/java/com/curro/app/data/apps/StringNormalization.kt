package com.curro.app.data.apps

import java.text.Normalizer
import java.util.Locale

// String normalization utilities for colloquial Spanish app-name matching (US-027 / SF-4.3).
// [curroNormalize] is the single canonical way to compare user utterances to app labels across
// the entire codebase — HelpHandler (US-029 / SF-4.5) imports it from here too.
// [levenshtein] is O(n*m) time / O(min(n,m)) space, safe for the handler's ~150-app
// list at ≤ 10-char queries (sub-millisecond on the Redmi 15).

/** NFD-strips combining diacritics + recomposes to NFC. `"cámara"` → `"camara"`. */
internal fun String.normalizeAccents(): String {
    val nfd = Normalizer.normalize(this, Normalizer.Form.NFD)
    val stripped = nfd.replace(Regex("\\p{Mn}+"), "")
    return Normalizer.normalize(stripped, Normalizer.Form.NFC)
}

/** Lowercase (Spanish locale) + accent-strip. `"WhatsApp"` → `"whatsapp"`. */
internal fun String.curroNormalize(): String = this.lowercase(Locale("es")).normalizeAccents()

/**
 * Classic 2-row Levenshtein distance.
 *
 * Returns the edit distance (insertions + deletions + substitutions). Both inputs are assumed
 * already normalised by the caller (`curroNormalize`). Inputs are treated as char sequences;
 * Unicode surrogate pairs are not decomposed (acceptable for Latin + Spanish alphabet).
 */
@Suppress("ReturnCount")
internal fun levenshtein(
    a: String,
    b: String,
): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val (short, long) = if (a.length <= b.length) a to b else b to a
    var prev = IntArray(short.length + 1) { it }
    var curr = IntArray(short.length + 1)
    for (j in 1..long.length) {
        curr[0] = j
        for (i in 1..short.length) {
            val cost = if (short[i - 1] == long[j - 1]) 0 else 1
            curr[i] =
                minOf(
                    prev[i] + 1,
                    curr[i - 1] + 1,
                    prev[i - 1] + cost,
                )
        }
        val swap = prev
        prev = curr
        curr = swap
    }
    return prev[short.length]
}
