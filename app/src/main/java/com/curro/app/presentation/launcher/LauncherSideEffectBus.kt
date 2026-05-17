package com.curro.app.presentation.launcher

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton channel for one-shot side effects directed at the launcher UI (SF-8.7 / US-057).
 *
 * Background services (e.g. `FailedCommandExporter`) emit [LauncherSideEffect] events here;
 * the launcher's ViewModel collects them and routes each to the appropriate Android operation
 * (e.g. opening the share chooser for [LauncherSideEffect.ShareText]).
 *
 * **Why a SharedFlow and not a Channel**: the bus must survive the launcher ViewModel being
 * recreated (e.g. process death + resume). A `@Singleton` SharedFlow outlives any single
 * ViewModel; a Channel would be tied to the consumer's lifecycle. With `replay = 0` and
 * `extraBufferCapacity = 1`, the bus drops stale effects when no collector is active —
 * acceptable for the prototype (the user can tap "share" again).
 */
@Singleton
class LauncherSideEffectBus
    @Inject
    constructor() {
        private val _effects =
            MutableSharedFlow<LauncherSideEffect>(
                replay = 0,
                extraBufferCapacity = 1,
            )

        /** Observable stream of side effects. Collect this in the launcher ViewModel. */
        val effects: SharedFlow<LauncherSideEffect> = _effects.asSharedFlow()

        /**
         * Emit a side effect. Drops the effect if no collector is active (extraBufferCapacity
         * absorbs one pending effect while the ViewModel is re-subscribing).
         */
        fun emit(effect: LauncherSideEffect) {
            _effects.tryEmit(effect)
        }
    }
