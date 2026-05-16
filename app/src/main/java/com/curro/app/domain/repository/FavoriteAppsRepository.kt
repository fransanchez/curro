package com.curro.app.domain.repository

import com.curro.app.domain.model.FavoriteApp
import kotlinx.coroutines.flow.Flow

/**
 * Repository that provides the user's favourite launcher app tiles (SF-1.4 / US-012).
 *
 * SF-7.4 implementation ([com.curro.app.data.apps.RecencyFavoriteAppsRepositoryImpl])
 * returns the top-4 apps by recency-weighted usage, falling back to the Phase-1 seed apps
 * (WhatsApp, Llamadas, Cámara, Fotos) when usage is sparse. Recomputed at most every 24 h
 * for home-grid stability (master-plan §Phase-7 risk b — "feels the same every day").
 *
 * Phase-8: a dynamic implementation will also let Fran edit the list from the config menu.
 */
interface FavoriteAppsRepository {
    /**
     * A [Flow] of the current favourite tiles. Emits immediately on subscription and on
     * every `ON_RESUME` thereafter. Never completes; collect from a supervised scope.
     */
    fun observeFavorites(): Flow<List<FavoriteApp>>
}
