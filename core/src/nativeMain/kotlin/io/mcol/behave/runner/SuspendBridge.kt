package io.mcol.behave.runner

import kotlinx.coroutines.runBlocking

internal actual fun <T> runSuspendBlocking(block: suspend () -> T): T = runBlocking { block() }
