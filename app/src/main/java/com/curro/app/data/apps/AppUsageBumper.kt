package com.curro.app.data.apps

import com.curro.app.assistant.TimeProvider
import com.curro.app.data.local.AppUsageDao
import com.curro.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Records a successful app launch (SF-7.4 / US-048).
 *
 * Centralised seam: both [com.curro.app.handler.OpenAppHandler] (voice path)
 * and [com.curro.app.presentation.launcher.LauncherViewModel] (tile tap) go
 * through [AppLauncher.launch] which calls this on success. No other caller.
 *
 * **Fire-and-forget** on [com.curro.app.di.ApplicationScope]: the write to
 * `app_usage` survives even if the calling ViewModel is cleared (the tile tap
 * → activity transition can race with `onCleared`).
 *
 * Tests inject a synchronous fake ([com.curro.app.util.FakeAppUsageBumper]).
 */
interface AppUsageBumper {
    /** Records one open of [packageName]. Called only after a successful launch. */
    fun bumpAsync(packageName: String)
}

/**
 * Production [AppUsageBumper] — fires a coroutine on [scope] ([ApplicationScope])
 * that calls [AppUsageDao.upsert] off the main thread.
 *
 * Using [ApplicationScope] (not `viewModelScope`) ensures the Room write completes
 * even if the triggering Activity is finishing (tile tap → `onCleared` race).
 */
@Singleton
class CoroutineAppUsageBumper
    @Inject
    constructor(
        private val dao: AppUsageDao,
        private val timeProvider: TimeProvider,
        @ApplicationScope private val scope: CoroutineScope,
    ) : AppUsageBumper {
        override fun bumpAsync(packageName: String) {
            scope.launch { dao.upsert(packageName, timeProvider.now()) }
        }
    }
