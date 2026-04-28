package io.mcol.behave.runner

internal actual fun readResource(path: String): String = Thread
    .currentThread()
    .contextClassLoader
    .getResourceAsStream(path)
    ?.bufferedReader()
    ?.readText()
    ?: error("Resource not found: $path")
