package com.curro.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for Curro.
 *
 * Hilt is wired here in SF-0.1. DI modules (DatabaseModule, RepositoryModule,
 * HandlerModule, MlModule, VoiceModule) arrive in SF-0.2.
 */
@HiltAndroidApp
class CurroApp : Application()
