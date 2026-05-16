package com.curro.app.data.contacts

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Trivial test for [EmptyAliasRepository] (US-033 / SF-4.9).
 *
 * Phase-4 stub — always returns `emptyList()`. The contract is simple enough
 * that one test suffices; the real behaviour arrives in Phase 7.
 */
@DisplayName("EmptyAliasRepository (SF-4.9)")
class EmptyAliasRepositoryTest {
    @Test
    fun `resolveAlias always returns emptyList`() =
        runTest {
            val repo = EmptyAliasRepository()
            assertTrue(repo.resolveAlias("mi hija").isEmpty())
            assertTrue(repo.resolveAlias("pepito").isEmpty())
            assertTrue(repo.resolveAlias("").isEmpty())
        }
}
