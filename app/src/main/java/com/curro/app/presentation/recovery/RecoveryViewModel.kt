package com.curro.app.presentation.recovery

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.curro.app.data.recovery.RecoveryStateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * ViewModel for [RecoveryScreen].
 *
 * Deliberately minimal — Recovery Mode exists precisely because the normal Hilt
 * graph (AssistantCoordinator, LauncherViewModel, etc.) may be crashing. This
 * ViewModel only touches [RecoveryStateRepository] and produces a settings Intent.
 *
 * The Activity calls [android.app.Activity.recreate] after [onRetry] — no further
 * state is needed here; recreating clears the RecoveryScreen composition and
 * re-evaluates [RecoveryStateRepository.isRecoveryPending] (now false) in
 * [MainActivity.onCreate], which routes to [CurroNavHost] instead.
 */
@HiltViewModel
class RecoveryViewModel
    @Inject
    constructor(
        private val recovery: RecoveryStateRepository,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        /**
         * Clears the recovery flag and returns an [Intent] for [Settings.ACTION_HOME_SETTINGS].
         *
         * The caller (the composable) starts this intent so the user can switch to a
         * different launcher without leaving Recovery Mode in an inconsistent state.
         */
        fun onOpenSystemSettings(): Intent {
            recovery.acknowledgeRecovery()
            val intent = Intent(Settings.ACTION_HOME_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        }

        /**
         * Clears the recovery flag so that the next [MainActivity.onCreate] routes
         * to [CurroNavHost] instead of [RecoveryScreen].
         *
         * The Activity calls [android.app.Activity.recreate] immediately after this
         * returns; there is no further ViewModel state to update.
         */
        fun onRetry() {
            recovery.acknowledgeRecovery()
        }
    }
