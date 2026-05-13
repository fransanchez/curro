package com.curro.app.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Curro's 7-step spacing scale (Q1 — plain object; see docs/briefs/US-004-curro-theme.md A1).
 *
 * Senior-first generosity: the gap BETWEEN tap targets is [xl] or [xxl], not [m].
 * Read via [CurroSpacing] directly — never raw `.dp` literals outside this package.
 *
 * | Token | dp | Use                                                              |
 * |-------|----|------------------------------------------------------------------|
 * | none  | 0  | Explicit-zero for Modifier.padding(...) defaults                 |
 * | xs    | 4  | Tight inner gaps within a component (icon ↔ label inside button) |
 * | s     | 8  | Inner padding, tight gaps within a card                          |
 * | m     | 16 | Standard padding (Material's 16-dp grid baseline)                |
 * | l     | 24 | Section spacing                                                  |
 * | xl    | 32 | Screen-level padding, gaps between tap targets                   |
 * | xxl   | 48 | Extra-generous gap between adjacent big buttons / app tiles      |
 */
object CurroSpacing {
    val none: Dp = 0.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
}
