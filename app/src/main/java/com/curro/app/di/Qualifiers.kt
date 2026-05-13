package com.curro.app.di

import javax.inject.Qualifier

/** Marks the [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.IO]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Marks the [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.Main.immediate]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/** Marks the [kotlinx.coroutines.CoroutineDispatcher] backed by [kotlinx.coroutines.Dispatchers.Default]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Marks the application-lifetime [kotlinx.coroutines.CoroutineScope] (SupervisorJob + Main.immediate). See A6. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
