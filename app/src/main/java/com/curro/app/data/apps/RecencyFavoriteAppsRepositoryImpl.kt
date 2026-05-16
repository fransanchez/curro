package com.curro.app.data.apps

import com.curro.app.assistant.TimeProvider
import com.curro.app.data.local.AppUsageDao
import com.curro.app.data.local.AppUsageEntity
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.model.FavoriteApp
import com.curro.app.domain.repository.FavoriteAppsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Recency-weighted favourites repository (SF-7.4 / US-048).
 *
 * **Scoring** (`local-data` "Favourite apps → home grid"):
 *
 * ```
 * score = openCount × max(0, 1 − daysSince / 30)
 * ```
 *
 * - 0–30 days: linear decay from 1.0 → 0.0.
 * - 30+ days: clamped to 0.0; the app is excluded from the usage-derived list
 *   and seed padding fills its slot.
 *
 * **Stability** (`local-data` rule 5 + master-plan §Phase-7 risk b):
 * [observeFavorites] recomputes the score-and-rank exactly once every
 * [RECOMPUTE_INTERVAL_MS] (24 h). Every successful `app_usage.upsert` bumps
 * the data via [com.curro.app.data.apps.CoroutineAppUsageBumper], but the home
 * grid does NOT re-emit until the timer tick.
 *
 * **Phase-8 seam**: Phase 8's "Actualizar favoritas" button will publish to
 * [recomputeTrigger]; SF-7.4 declares the surface but does not wire a UI for it.
 *
 * **Seed padding** ([SeedAppResolver]): when the scored top-N has < [FAVOURITES_COUNT]
 * entries, the Phase-1 seeds fill the remaining slots in canonical order. Seeds already
 * present in the usage-derived list are skipped.
 *
 * @param appUsageDao Source of open-count + last-opened data from Room.
 * @param timeProvider Seam for wall-clock (testable — use [TestTimeProvider] in tests).
 * @param seedAppResolver Resolves seed app metadata and dynamic-package labels.
 * @param ioDispatcher All PackageManager + Room calls run on this dispatcher.
 */
@Singleton
class RecencyFavoriteAppsRepositoryImpl
    @Inject
    constructor(
        private val appUsageDao: AppUsageDao,
        private val timeProvider: TimeProvider,
        private val seedAppResolver: SeedAppResolver,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FavoriteAppsRepository {
        /**
         * Phase-8 trigger seam. A publish to this flow forces an immediate recompute
         * without waiting for the 24-h timer.
         *
         * `internal` so [com.curro.app.di.ConfigMenuViewModel] (Phase 8) can reach it via
         * casting the interface to the impl. Alternatively, Phase 8 may add a
         * `FavoriteAppsRepository.recompute()` method — either is fine.
         */
        internal val recomputeTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        override fun observeFavorites(): Flow<List<FavoriteApp>> {
            val timerFlow =
                flow {
                    while (true) {
                        delay(RECOMPUTE_INTERVAL_MS)
                        emit(Unit)
                    }
                }
            return flow {
                emit(loadFavorites()) // initial emission on subscribe
                merge(timerFlow, recomputeTrigger).collect {
                    emit(loadFavorites())
                }
            }.flowOn(ioDispatcher)
        }

        private suspend fun loadFavorites(): List<FavoriteApp> {
            val now = timeProvider.now()
            val raw = appUsageDao.topByOpenCount(USAGE_FETCH_LIMIT)
            val scored =
                raw
                    .mapNotNull { entity ->
                        val score = scoreEntity(entity, now)
                        if (score > 0.0) entity to score else null // 30+ days stale → drop
                    }.sortedByDescending { it.second }

            val usageDerived =
                scored
                    .mapNotNull { (entity, _) -> seedAppResolver.toFavoriteApp(entity.packageName) }
                    .take(FAVOURITES_COUNT)

            if (usageDerived.size >= FAVOURITES_COUNT) return usageDerived

            val seeds =
                seedAppResolver
                    .seedFavorites()
                    .filter { seed ->
                        seed.resolvedPackage != null &&
                            usageDerived.none { it.resolvedPackage == seed.resolvedPackage }
                    }

            return (usageDerived + seeds).take(FAVOURITES_COUNT)
        }

        /**
         * Computes the recency-weighted score for [entity] relative to [now].
         *
         * `score = openCount × max(0, 1 − daysSince / DECAY_DAYS)`.
         *
         * Internal visibility for unit-testing the formula in isolation.
         */
        internal fun scoreEntity(
            entity: AppUsageEntity,
            now: Long,
        ): Double {
            val daysSince = (now - entity.lastOpenedAtMs).toDouble() / DAY_MS
            val decay = max(0.0, 1.0 - daysSince / DECAY_DAYS)
            return entity.openCount * decay
        }

        private companion object {
            const val FAVOURITES_COUNT = 4
            const val RECOMPUTE_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24 h
            const val USAGE_FETCH_LIMIT = 20
            const val DAY_MS = 24L * 60 * 60 * 1000
            const val DECAY_DAYS = 30.0
        }
    }
