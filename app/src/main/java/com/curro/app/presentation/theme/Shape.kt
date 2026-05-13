package com.curro.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * Curro's rounded-corner shape scale.
 *
 * Placeholder values — replaced by the real Curro radii in US-005 (SF-0.7)
 * without touching any composable.
 *
 * Every radius is annotated PLACEHOLDER (US-005) in the KDoc above; US-005 replaces
 * these values wholesale without touching any composable consumer.
 *
 * Composables read shapes via [androidx.compose.material3.MaterialTheme].shapes.medium (etc.)
 * or directly via [CurroShapes].medium — both work because [CurroTheme] wires
 * [CurroShapes] into [androidx.compose.material3.MaterialTheme].shapes.
 *
 * PLACEHOLDER (US-005) — all radii below.
 */
val CurroShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(8),
        small = RoundedCornerShape(12),
        medium = RoundedCornerShape(16),
        large = RoundedCornerShape(24),
        extraLarge = RoundedCornerShape(32),
    )
