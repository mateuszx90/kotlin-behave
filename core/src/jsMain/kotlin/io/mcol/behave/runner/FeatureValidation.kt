package io.mcol.behave.runner

/** No structural validation on JS — FeatureFileParser is JVM-only; KSP validates at build. */
internal actual fun featureStructureErrors(text: String): List<String> = emptyList()
