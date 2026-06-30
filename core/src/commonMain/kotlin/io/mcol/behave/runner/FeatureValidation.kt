package io.mcol.behave.runner

/**
 * Structural validation of a raw `.feature`, shared with the compile-time pipeline. On JVM it
 * delegates to the very `io.mcol.behave.gherkin.FeatureFileParser` the KSP processor uses, so the
 * runtime rejects exactly the malformed features the build does (missing `Feature:`, a step outside
 * any scenario, a Scenario Outline with no `Examples` or an undefined `<variable>`). Returns one
 * message per problem; empty when the feature is well-formed.
 *
 * Non-JVM platforms return empty: the validator is JVM-only, and in practice every feature is
 * validated at build time by KSP anyway — this gate catches the post-build-edit / hand-loaded case.
 */
internal expect fun featureStructureErrors(text: String): List<String>
