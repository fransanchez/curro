package com.curro.app.domain.repository

import com.curro.app.domain.model.FavoriteApp
import kotlinx.coroutines.flow.Flow

/**
 * Repository that provides the user's favourite launcher app tiles (SF-1.4 / US-012).
 *
 * Phase-1 implementation ([com.curro.app.data.apps.StaticFavoriteAppsRepositoryImpl])
 * returns a static four-item list resolved from [android.content.pm.PackageManager].
 *
 * Re-emits on `ProcessLifecycleOwner ON_RESUME` so tiles update when apps are installed
 * or removed while Curro is in the background.
 *
 * Phase-8: a dynamic implementation will read the list from the local DB (Room) and allow
 * Fran to edit it from the config menu.
 */
interface FavoriteAppsRepository {
    /**
     * A [Flow] of the current favourite tiles. Emits immediately on subscription and on
     * every `ON_RESUME` thereafter. Never completes; collect from a supervised scope.
     */
    fun observeFavorites(): Flow<List<FavoriteApp>>
}
