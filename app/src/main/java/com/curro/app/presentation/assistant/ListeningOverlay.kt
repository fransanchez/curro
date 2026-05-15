package com.curro.app.presentation.assistant

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.curro.app.BuildConfig
import com.curro.app.R
import com.curro.app.presentation.launcher.ListeningState
import com.curro.app.presentation.theme.CurroListeningTintDark
import com.curro.app.presentation.theme.CurroListeningTintLight
import com.curro.app.presentation.theme.CurroSpacing
import com.curro.app.presentation.theme.CurroTheme

/**
 * Listening overlay (SF-2.4 / US-018) — the calm, predictable visual that signals "I'm
 * listening" or "I'm speaking back to you" without distracting movement.
 *
 * Drawn on top of the launcher home via `AnimatedVisibility` in
 * [com.curro.app.presentation.launcher.LauncherPlaceholderScreen] (US-017). `Idle` is
 * never rendered — the wrapping `AnimatedVisibility` ensures the composable is not in
 * the tree.
 *
 * Visual modes (also see [ListeningState] kdoc):
 * - [ListeningState.Starting] / [ListeningState.Listening]: tint, "Te escucho…" headline,
 *   live partial transcript, ANIMATED audio-wave.
 * - [ListeningState.Speaking]: tint, "Te escucho…" KEPT (avoid shift), spoken text, STATIC wave.
 * - [ListeningState.Error]: tint, error MESSAGE replacing "Te escucho…", no transcript, STATIC wave.
 *
 * Senior-first rules upheld (spec §3, §11; brief §9):
 * - `displayMedium` (48 sp) headline + `bodyLarge` (20 sp) transcript — both well above
 *   Material defaults.
 * - `onBackground` text on `CurroListeningTint*` (11.8:1 light / 12.7:1 dark) — well above the
 *   4.5:1 floor.
 * - No fussy animation: 5 bars, 1.2-s period, smooth ease — calmer than Material's spinner.
 * - Layout does not shift as partials arrive — the headline position is fixed; transcript
 *   wraps and ellipses at 4 lines.
 * - Headline + transcript are `liveRegion = Polite` so TalkBack announces updates.
 */
@Composable
fun ListeningOverlay(
    state: ListeningState,
    modifier: Modifier = Modifier,
    debugJson: String? = null,
) {
    // Defensive — US-017's AnimatedVisibility already filters Idle, but a stray render
    // (e.g. preview misuse) should not paint over the launcher.
    if (state is ListeningState.Idle) return

    val tint = if (isSystemInDarkTheme()) CurroListeningTintDark else CurroListeningTintLight
    val isActive = state is ListeningState.Listening || state is ListeningState.Starting

    Surface(
        modifier = modifier.fillMaxSize(),
        color = tint,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = CurroSpacing.l),
            ) {
                Headline(state)

                Spacer(modifier = Modifier.height(CurroSpacing.l))

                Transcript(state)

                // SF-3.6 (US-024) — debug-only JSON surface for the decision smoke test.
                // Rendered only when the build is debug AND the state is Processing AND the
                // ViewModel has produced a parsed FunctionCall. Phase 5 removes this block.
                if (BuildConfig.DEBUG && state is ListeningState.Processing && debugJson != null) {
                    Spacer(modifier = Modifier.height(CurroSpacing.m))
                    Text(
                        text = debugJson,
                        modifier = Modifier.padding(horizontal = CurroSpacing.m),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Spacer between transcript and wave — present whether transcript shows or not, so
                // the wave's vertical position stays stable.
                Spacer(modifier = Modifier.height(CurroSpacing.xl))

                AudioWaveIndicator(animated = isActive)
            }
        }
    }
}

@Composable
private fun Headline(state: ListeningState) {
    val headline =
        when (state) {
            is ListeningState.Error -> state.message
            else -> stringResource(R.string.copy_listening_prompt)
        }
    Text(
        text = headline,
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .testTag(TAG_HEADLINE)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun Transcript(state: ListeningState) {
    val transcript =
        when (state) {
            is ListeningState.Listening -> state.partialText
            is ListeningState.Speaking -> state.text
            else -> ""
        }
    if (transcript.isNotEmpty()) {
        Text(
            text = transcript,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = TRANSCRIPT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .testTag(TAG_TRANSCRIPT)
                    .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * Five-bar audio-wave indicator (US-018 §11 — design rationale).
 *
 * 5 bars, 1.2-s period, phased peaks. When [animated] is true the bars pulse via a
 * `rememberInfiniteTransition`; when false (Speaking / Error) the bars hold at
 * [STATIC_HEIGHT_FRACTION] mid-height — a constant non-zero visual that says "the assistant
 * is engaged" without claiming to be receiving input.
 *
 * Pure Compose — no Lottie (avoids ~250 KB asset + JSON dep).
 */
@Composable
private fun AudioWaveIndicator(
    animated: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "audiowave")
    val testTag = if (animated) TAG_WAVE_ANIMATED else TAG_WAVE_STATIC

    Row(
        horizontalArrangement = Arrangement.spacedBy(CurroSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.testTag(testTag),
    ) {
        repeat(BAR_COUNT) { i ->
            val phaseMs = (i * (PERIOD_MS / BAR_COUNT.toFloat())).toInt()
            val heightFraction =
                if (animated) {
                    val animatedFraction by
                        transition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 1.0f,
                            animationSpec =
                                infiniteRepeatable(
                                    animation =
                                        tween(
                                            durationMillis = PERIOD_MS,
                                            easing = FastOutSlowInEasing,
                                            delayMillis = phaseMs,
                                        ),
                                    repeatMode = RepeatMode.Reverse,
                                ),
                            label = "bar$i",
                        )
                    animatedFraction
                } else {
                    STATIC_HEIGHT_FRACTION
                }
            Box(
                modifier =
                    Modifier
                        .width(BAR_WIDTH)
                        .height(PEAK_HEIGHT * heightFraction)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.small,
                        ),
            )
        }
    }
}

// ─── Test tags & tuning constants ────────────────────────────────────────────

/** Public test tag for the headline — used by `ListeningOverlayTest` to assert position stability. */
const val TAG_HEADLINE: String = "ListeningOverlay/Headline"

/** Public test tag for the live transcript / spoken text Text node. */
const val TAG_TRANSCRIPT: String = "ListeningOverlay/Transcript"

/** Public test tag for the audio-wave when it is animating. */
const val TAG_WAVE_ANIMATED: String = "ListeningOverlay/Wave/Animated"

/** Public test tag for the audio-wave in its static (Speaking / Error) mode. */
const val TAG_WAVE_STATIC: String = "ListeningOverlay/Wave/Static"

/** Bars in the audio-wave. 5 is the smallest count that reads as "wave" not "bars" (US-018 §11). */
private const val BAR_COUNT: Int = 5

/** Animation period — 1.2 s. Slower than Material spinners (0.5–0.8 s); senior-first calm. */
private const val PERIOD_MS: Int = 1_200

/** Held mid-height in the Speaking/Error static mode. */
private const val STATIC_HEIGHT_FRACTION: Float = 0.55f

/** Transcript ellipses after this many wrapped lines (kept finite to prevent layout shift). */
private const val TRANSCRIPT_MAX_LINES: Int = 4

private val BAR_WIDTH = 12.dp
private val PEAK_HEIGHT = 48.dp

// ─── Previews (4) ────────────────────────────────────────────────────────────

@Preview(name = "Listening — Light, short partial", widthDp = 412, heightDp = 800)
@Composable
private fun ListeningLightShortPreview() {
    CurroTheme {
        ListeningOverlay(state = ListeningState.Listening("Hola"))
    }
}

@Preview(
    name = "Listening — Dark, long partial",
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = 412,
    heightDp = 800,
)
@Composable
private fun ListeningDarkLongPreview() {
    CurroTheme {
        ListeningOverlay(
            state =
                ListeningState.Listening(
                    "Llama a Pepe el de los olivos y dile que vamos al campo el sábado por la mañana",
                ),
        )
    }
}

@Preview(name = "Speaking — Light, medium text", widthDp = 412, heightDp = 800)
@Composable
private fun SpeakingLightPreview() {
    CurroTheme {
        ListeningOverlay(state = ListeningState.Speaking("Llamando a Pepito."))
    }
}

@Preview(
    name = "Listening — fontScale 2.0, long partial",
    widthDp = 412,
    heightDp = 800,
    fontScale = 2.0f,
)
@Composable
private fun ListeningLargeFontPreview() {
    CurroTheme {
        ListeningOverlay(
            state =
                ListeningState.Listening(
                    "Léeme los mensajes que me han llegado de mi hija y de Lucía",
                ),
        )
    }
}

@Preview(name = "Error — Light", widthDp = 412, heightDp = 800)
@Composable
private fun ErrorLightPreview() {
    CurroTheme {
        ListeningOverlay(state = ListeningState.Error("No te he oído bien, ¿puedes repetirlo?"))
    }
}
