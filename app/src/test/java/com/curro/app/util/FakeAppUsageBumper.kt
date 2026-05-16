package com.curro.app.util

import com.curro.app.data.apps.AppUsageBumper

/**
 * Synchronous test double for [AppUsageBumper] (SF-7.4 / US-048).
 *
 * Captures every [bumpAsync] call in [bumpedPackages]. Synchronous — no coroutine
 * is involved — so tests can assert immediately after the call under test returns.
 */
class FakeAppUsageBumper : AppUsageBumper {
    val bumpedPackages: MutableList<String> = mutableListOf()

    override fun bumpAsync(packageName: String) {
        bumpedPackages += packageName
    }
}
