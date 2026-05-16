package com.curro.app.domain.model

/**
 * Sealed representation of a launcher tile's display label (SF-7.4 / US-048).
 *
 * Two shapes:
 *  - [Resource] — a static Spanish label from the string resource table (seed apps:
 *    WhatsApp, Llamadas, Cámara, Fotos). The composable calls `stringResource(resId)`.
 *  - [Text] — a dynamic label fetched from [android.content.pm.PackageManager.getApplicationLabel]
 *    for usage-derived tiles (apps the user actually opened that are not seed apps).
 *
 * Phase 8's config menu may let Fran override any label; both shapes are forward-compatible.
 */
sealed class AppLabel {
    /** A label resolved from the string resource table. */
    data class Resource(val resId: Int) : AppLabel()

    /** A label obtained at runtime from `PackageManager.getApplicationLabel`. */
    data class Text(val text: String) : AppLabel()
}
