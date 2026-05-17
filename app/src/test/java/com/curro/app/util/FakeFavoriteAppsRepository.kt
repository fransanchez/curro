package com.curro.app.util

import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [FavoriteAppsRepository] for unit tests (SF-8.8 / US-058).
 *
 * Records invocations of [clearUsage] for assertion.
 */
class FakeFavoriteAppsRepository : FavoriteAppsRepository {
    private val favoritesFlow = MutableStateFlow<List<FavoriteApp>>(emptyList())
    var clearUsageCallCount: Int = 0

    override fun observeFavorites(): Flow<List<FavoriteApp>> = favoritesFlow

    override suspend fun clearUsage() {
        clearUsageCallCount++
    }
}
