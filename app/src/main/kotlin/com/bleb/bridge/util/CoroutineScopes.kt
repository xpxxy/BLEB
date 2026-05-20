package com.bleb.bridge.util

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoroutineScopes @Inject constructor() {
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("BLEB", "Unhandled coroutine exception", throwable)
    }

    val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + exceptionHandler
    )
}
