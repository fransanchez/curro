package com.curro.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Boots [HiltTestApplication] instead of [CurroApp] for instrumented tests, so
 * the Hilt rule can swap modules via `@UninstallModules` + `@BindValue`.
 *
 * Wired by `app/build.gradle.kts` (`defaultConfig.testInstrumentationRunner`
 * declared in SF-0.1; class realised here in SF-0.2).
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        name: String?,
        ctx: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, ctx)
}
