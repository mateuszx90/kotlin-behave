package io.mcol.behave.runner

/**
 * In-memory registry of feature-file content keyed by path.
 *
 * When populated (typically by a build-generated installer), [loadFeature] will serve feature
 * content from here instead of reading from the filesystem/classpath via [readResource]. This
 * lets KMP tests run on platforms where runtime resource reading is unavailable or inconvenient
 * (e.g. iOS simulator sandbox).
 */
object FeatureRegistry {
    private val features = mutableMapOf<String, String>()

    fun register(path: String, content: String) {
        features[path] = content
    }

    internal fun get(path: String): String? = features[path]
}
