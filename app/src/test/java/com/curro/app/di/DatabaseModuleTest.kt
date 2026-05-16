package com.curro.app.di

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.curro.app.data.local.CurroDatabase
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke tests for [DatabaseModule] (SF-7.1 / US-045).
 *
 * Verifies that [CurroDatabase] exposes all three DAO accessors and that an
 * in-memory database can be constructed without error — the same code path that
 * [DatabaseModule.provideCurroDatabase] exercises at runtime (minus the file path).
 *
 * Full DAO behaviour is tested in the individual DAO test classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class DatabaseModuleTest {
    private lateinit var db: CurroDatabase

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                CurroDatabase::class.java,
            ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `contactAliasDao is non-null`() {
        assertNotNull(db.contactAliasDao())
    }

    @Test
    fun `appUsageDao is non-null`() {
        assertNotNull(db.appUsageDao())
    }

    @Test
    fun `failedCommandDao is non-null`() {
        assertNotNull(db.failedCommandDao())
    }
}
