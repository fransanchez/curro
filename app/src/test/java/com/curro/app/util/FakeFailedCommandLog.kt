package com.curro.app.util

import com.curro.app.data.local.FailedCommandEntity
import com.curro.app.data.local.FailureKind
import com.curro.app.domain.repository.FailedCommandLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Captured invocation for assertion in tests. */
data class RecordedCall(val transcript: String, val kind: FailureKind, val details: String)

/**
 * In-memory [FailedCommandLog] fake for unit tests.
 *
 * Captures every [record] invocation in [records]. Tests assert on this list
 * rather than verifying mock calls, which is more readable and avoids Mockk
 * setup for suspend funs.
 */
class FakeFailedCommandLog : FailedCommandLog {
    val records: MutableList<RecordedCall> = mutableListOf()
    private val flow = MutableStateFlow<List<FailedCommandEntity>>(emptyList())

    override suspend fun record(
        transcript: String,
        kind: FailureKind,
        details: String,
    ) {
        records += RecordedCall(transcript, kind, details)
    }

    override fun observeRecent(limit: Int): Flow<List<FailedCommandEntity>> = flow

    override suspend fun count(): Int = records.size

    override suspend fun deleteAll() {
        records.clear()
        flow.value = emptyList()
    }
}
