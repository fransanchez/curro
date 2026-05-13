package com.curro.app.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * Curro's rounded-corner shape scale. Locked by US-005 (SF-0.7).
 *
 * Warm/friendly bias: radii are slightly larger than M3 defaults — enough that everything feels
 * rounded and approachable without becoming cartoony. No sharp edges for a user with reduced fine
 * motor control.
 *
 * | Slot       | dp | M3 default | Use                                              |
 * |------------|----|------------|--------------------------------------------------|
 * | extraSmall | 8  | 4          | Chips, small inline tags (rare in Curro)         |
 * | small      | 16 | 8          | App tiles, list rows, contact picker rows        |
 * | medium     | 20 | 12         | Big buttons, cards, containers                   |
 * | large      | 28 | 16         | Large surfaces, the mic button                   |
 * | extraLarge | 36 | 28         | Rare — overlay sheets                            |
 *
 * Composables read shapes via [androidx.compose.material3.MaterialTheme].shapes.medium (etc.)
 * or directly via [CurroShapes].medium — both work because [CurroTheme] wires [CurroShapes]
 * into [androidx.compose.material3.MaterialTheme].shapes.
 */
val CurroShapes: Shapes =
    Shapes(
        extraSmall = RoundedCornerShape(8),
        small = RoundedCornerShape(16),
        medium = RoundedCornerShape(20),
        large = RoundedCornerShape(28),
        extraLarge = RoundedCornerShape(36),
    )
