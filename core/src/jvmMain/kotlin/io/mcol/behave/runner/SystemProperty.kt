package io.mcol.behave.runner

internal actual fun getSystemProperty(key: String): String? = System.getProperty(key)
