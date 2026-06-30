package io.mcol.behave.runner

import io.mcol.behave.gherkin.FeatureFileParser

/** Reuses the compile-time parser so runtime and build agree on what counts as malformed. */
internal actual fun featureStructureErrors(text: String): List<String> = FeatureFileParser.parse(text).errors.map { "line ${it.line}: ${it.message}" }
