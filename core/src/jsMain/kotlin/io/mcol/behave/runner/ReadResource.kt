package io.mcol.behave.runner

internal actual fun readResource(path: String): String =
    js("require('fs').readFileSync(require('path').resolve(__dirname, path), 'utf8')") as String
