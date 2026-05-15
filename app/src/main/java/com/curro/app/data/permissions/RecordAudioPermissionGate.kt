package com.curro.app.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PermissionGate] for `Manifest.permission.RECORD_AUDIO` (SF-2.3 / US-017).
 *
 * The runtime request is fired by the launcher screen via
 * `rememberLauncherForActivityResult(RequestPermission())`; this gate is the read-side
 * the ViewModel consults on every mic press to decide between "go to listening" or
 * "emit RequestRecordAudio side-effect".
 */
@Singleton
internal class RecordAudioPermissionGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PermissionGate {
        override fun isGranted(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }
