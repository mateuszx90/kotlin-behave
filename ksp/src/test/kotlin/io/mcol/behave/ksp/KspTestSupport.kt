package io.mcol.behave.ksp

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.sourcesGeneratedBySymbolProcessor
import java.io.File
import java.nio.file.Files

/**
 * Reusable harness for END-TO-END KSP generation tests.
 *
 * It runs the REAL [BehaveProcessorProvider] over inline Kotlin sources via kctfork
 * (compile-testing) in KSP2 mode, and returns both the compilation outcome and the
 * generated Kotlin sources so tests can assert on the produced output.
 *
 * Crucially, the processor does NOT read `.feature` files from the compiled sources — it
 * reads them from DISK using the `behave.projectDir` + `behave.featureDir` KSP options
 * (see [BehaveProcessor.resolveFeatureFile]). So this harness writes each feature file into
 * a temp project directory and wires those two options accordingly; the `path` passed to
 * `@BehaveFeature(...)` must match the key used in [compile]'s `features` map.
 */
object KspTestSupport {

    /** Outcome of a single processor run. */
    data class Outcome(
        val exitCode: KotlinCompilation.ExitCode,
        val messages: String,
        val generatedSources: List<File>,
    ) {
        val succeeded: Boolean get() = exitCode == KotlinCompilation.ExitCode.OK

        /** Read the single generated file whose name contains [nameContains]; fails if absent. */
        fun generated(nameContains: String): String = generatedOrNull(nameContains)
            ?: error(
                "No generated source matching '$nameContains'. " +
                    "Generated: ${generatedSources.map { it.name }}",
            )

        fun generatedOrNull(nameContains: String): String? = generatedSources.firstOrNull { it.name.contains(nameContains) }?.readText()

        fun hasGenerated(nameContains: String): Boolean = generatedSources.any { it.name.contains(nameContains) }
    }

    /** Convenience factory mirroring [SourceFile.kotlin]. */
    fun source(name: String, contents: String): SourceFile = SourceFile.kotlin(name, contents)

    /**
     * Compile [sources] with the Behave processor, after writing [features] to disk.
     *
     * @param features map of feature-file path (relative to [featureDir]) to its raw Gherkin
     *                 content. Each `@BehaveFeature("<path>")` in the sources must reference a key here.
     * @param sources inline Kotlin sources (the `@BehaveFeature` classes, `@StepsMixin`
     *                interfaces, `@TypeConverter` functions, supporting types, …).
     * @param featureDir directory (under the temp project dir) holding the feature files; also
     *                   passed as the `behave.featureDir` KSP option.
     */
    fun compile(
        features: Map<String, String>,
        sources: List<SourceFile>,
        featureDir: String = "features",
    ): Outcome {
        val projectDir = Files.createTempDirectory("behave-ksp").toFile()
        for ((path, content) in features) {
            val file = File(projectDir, "$featureDir/$path")
            file.parentFile.mkdirs()
            file.writeText(content)
        }

        val compilation = KotlinCompilation().apply {
            this.sources = sources
            inheritClassPath = true
            verbose = false
            configureKsp(useKsp2 = true) {
                symbolProcessorProviders += BehaveProcessorProvider()
                processorOptions["behave.projectDir"] = projectDir.absolutePath
                processorOptions["behave.featureDir"] = featureDir
            }
        }

        val result: JvmCompilationResult = compilation.compile()
        return Outcome(
            exitCode = result.exitCode,
            messages = result.messages,
            generatedSources = result.sourcesGeneratedBySymbolProcessor.toList(),
        )
    }
}
