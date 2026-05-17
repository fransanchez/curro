package com.curro.app.data.local

import com.curro.app.assistant.TimeProvider
import com.curro.app.di.IoDispatcher
import com.curro.app.domain.repository.FailedCommandLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [FailedCommandLog] (SF-7.5 / US-049).
 *
 * The cap-at-50 invariant is enforced by [FailedCommandDao.insertAndTrim] —
 * an atomic `@Transaction`. See [FailedCommandDao] Kdoc.
 *
 * All DAO calls are dispatched to [ioDispatcher] via [withContext] to keep
 * the main thread free. The [observeRecent] flow is returned as-is from Room;
 * Room manages its own dispatcher for `Flow`-returning queries.
 *
 * **Privacy**: [FailedCommandEntity.transcript] is PII. It stays in the local
 * Room table only; the telemetry call site in [com.curro.app.assistant.AssistantCoordinator]
 * emits `kind` + `function_name` exclusively. See [FailedCommandLog] Kdoc.
 */
@Singleton
class RoomFailedCommandLog
    @Inject
    constructor(
        private val dao: FailedCommandDao,
        private val timeProvider: TimeProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FailedCommandLog {
        override suspend fun record(
            transcript: String,
            kind: FailureKind,
            details: String,
        ) = withContext(ioDispatcher) {
            dao.insertAndTrim(
                FailedCommandEntity(
                    transcript = transcript,
                    kind = kind,
                    details = details,
                    timestampMs = timeProvider.now(),
                ),
            )
        }

        override fun observeRecent(limit: Int): Flow<List<FailedCommandEntity>> = dao.observeRecent(limit)

        override suspend fun count(): Int = withContext(ioDispatcher) { dao.count() }

        override suspend fun deleteAll() = withContext(ioDispatcher) { dao.deleteAll() }

        // SF-8.8 (US-057) — export support
        override fun observeUnsent(limit: Int): Flow<List<FailedCommandEntity>> = dao.observeUnsent(limit)

        override suspend fun markSent(ids: List<Long>) = withContext(ioDispatcher) { dao.markSent(ids) }
    }
