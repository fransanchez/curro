package com.curro.app.util

import com.curro.app.domain.model.LaunchableApp
import com.curro.app.domain.repository.InstalledAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Shared test fake for [InstalledAppsRepository] (SF-8.3 / US-052).
 *
 * Emit new app lists via [appsFlow] to drive [observeAllLaunchable] in tests.
 */
class FakeInstalledAppsRepository : InstalledAppsRepository {
    val appsFlow: MutableStateFlow<List<LaunchableApp>> = MutableStateFlow(emptyList())

    override fun observeAllLaunchable(): Flow<List<LaunchableApp>> = appsFlow
}
