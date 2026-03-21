package io.mcol.behave.runner

internal actual fun <T> runSuspendBlocking(block: suspend () -> T): T =
    error("runWithPerScenarioRunner is not supported on JavaScript")
