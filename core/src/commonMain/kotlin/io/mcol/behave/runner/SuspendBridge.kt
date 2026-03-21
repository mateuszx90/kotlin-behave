package io.mcol.behave.runner

/** Runs a suspend block synchronously. JVM: uses runBlocking. JS/Native: unsupported. */
internal expect fun <T> runSuspendBlocking(block: suspend () -> T): T
