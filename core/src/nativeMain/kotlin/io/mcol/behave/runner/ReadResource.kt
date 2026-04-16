package io.mcol.behave.runner

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread

@OptIn(ExperimentalForeignApi::class)
internal actual fun readResource(path: String): String {
    val file = fopen(path, "r") ?: error("Resource not found: $path")
    try {
        val bufferSize = 4096
        val result = StringBuilder()
        memScoped {
            val buffer = allocArray<ByteVar>(bufferSize)
            while (true) {
                val read = fread(buffer, 1.convert(), bufferSize.convert(), file).toInt()
                if (read <= 0) break
                result.append(buffer.readBytes(read).decodeToString())
            }
        }
        return result.toString()
    } finally {
        fclose(file)
    }
}
