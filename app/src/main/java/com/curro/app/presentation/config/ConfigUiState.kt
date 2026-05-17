package com.curro.app.presentation.config

import androidx.annotation.StringRes

/**
 * UI state for [ConfigMenuScreen] (SF-8.1 / US-050).
 *
 * The [sections] list is always 9 items; the ViewModel rebuilds it on every
 * Flow emission. [incomingCallEnabled] and [sendFailuresEnabled] mirror the
 * matching `SettingsRepository` flows and are used by the two inline
 * [ConfigSection.Toggle] rows.
 *
 * Both toggles are declared in SF-8.1 but their [ConfigSection.Toggle.onChange]
 * is inert — SF-8.7 and SF-8.8 wire the real setters respectively.
 */
data class ConfigUiState(
    val sections: List<ConfigSection>,
    val incomingCallEnabled: Boolean,
    val sendFailuresEnabled: Boolean,
)

/**
 * A single row in the config menu.
 *
 * - [Navigable] — a section with a chevron that navigates to a sub-route.
 * - [Toggle] — an inline switch (SF-8.1 renders but does not wire the switch).
 */
sealed interface ConfigSection {
    /**
     * A navigable section row (title + optional summary + right chevron).
     *
     * @param titleResId String resource for the section title.
     * @param summary Optional secondary line (e.g. "3 alias guardados").
     * @param route The nav route string pushed on tap.
     * @param destructive When true, [titleResId] renders in
     *   `MaterialTheme.colorScheme.error` (reset row only).
     */
    data class Navigable(
        @StringRes val titleResId: Int,
        val summary: String?,
        val route: String,
        val destructive: Boolean = false,
    ) : ConfigSection

    /**
     * An inline-toggle row (title + help line + a [Switch]).
     *
     * @param titleResId String resource for the toggle label.
     * @param helpResId Short help line always visible below the title.
     * @param value Current toggle state (from `SettingsRepository`).
     * @param onChangeWillBeWiredInSF Documentation breadcrumb identifying which
     *   SF will wire the setter. The SF-8.1 ViewModel logs a `Log.w` when the
     *   toggle is flipped — it is never silent about the missing behaviour.
     */
    data class Toggle(
        @StringRes val titleResId: Int,
        @StringRes val helpResId: Int,
        val value: Boolean,
        val onChangeWillBeWiredInSF: String,
    ) : ConfigSection
}
