package com.curro.app.presentation.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Senior-first dimension contract for Curro (spec §3, §11; see docs/briefs/US-004-curro-theme.md A2).
 *
 * These are mechanical invariants, not a scale: every touch target / mic button / card / list
 * row in the app must respect them. US-005 (SF-0.7) may move colours, typography, and shapes;
 * these numbers do not move.
 *
 * Rationale: the only validated user (Fran's father) has reduced fine motor control and
 * deteriorated-but-functional vision. Material 3's 48 dp tap target is the FLOOR for normal
 * apps; Curro's user needs ≥ 96 dp.
 */
object Dimens {
    /** Minimum tap-target size — twice Material 3's 48 dp. Spec §3, §11. */
    val MinTapTarget: Dp = 96.dp

    /** Main launcher mic button: ≥ 40 % of the screen height. Spec §11. See Q3 / A3. */
    const val MicButtonMinHeightFraction: Float = 0.40f

    /** Standard big primary button height — alias of [MinTapTarget] for readability at the call site. */
    val BigButtonHeight: Dp = 96.dp

    /** Big card / list-row minimum height (clickable variant). */
    val BigRowHeight: Dp = 96.dp

    /** Glyph size inside a 96 dp IconButton — the senior-first "icon you can see". */
    val LargeIconSize: Dp = 48.dp

    /** Card elevation — pinned here so the 2.dp literal in BigCard stays out of presentation/common/. See A11. */
    val CardElevation: Dp = 2.dp
}
