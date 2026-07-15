package io.mcol.behave.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * `behaveLint` — parses every `.feature` under [featureDir], failing the build on any
 * unparseable file, and warns about step-definition methods declared in [stepSources] that no
 * feature references (dead steps).
 */
@CacheableTask
abstract class BehaveLintTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val featureDir: DirectoryProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stepSources: ConfigurableFileCollection

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun lint() {
        val baseDir = featureDir.asFile.get()

        val featureFiles = baseDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "feature" }
            .toList()

        val features = featureFiles.associate { file ->
            val relativePath = file.relativeTo(baseDir).path
            relativePath to file.readText()
        }

        val sources = stepSources.files
            .filter { it.isFile && it.extension == "kt" }
            .map { it.readText() }

        val result = BehaveLint.analyze(features, sources)
        val reportBuilder = StringBuilder()

        for (dead in result.deadSteps) {
            val msg = "behaveLint: dead step definition '$dead' is not referenced by any feature"
            logger.warn(msg)
            reportBuilder.appendLine(msg)
        }

        if (result.hasParseErrors) {
            val errorsLog =
                result.parseErrors.joinToString("\n") { "  ${it.file}:${it.line}: ${it.message}" }
            reportBuilder.appendLine("behaveLint: ${result.parseErrors.size} unparseable feature(s):\n$errorsLog")
        }

        val summary =
            "behaveLint: ${features.size} feature(s) parsed, ${result.deadSteps.size} dead step(s) reported."
        logger.lifecycle(summary)
        reportBuilder.appendLine(summary)

        val actualReportFile = reportFile.get().asFile
        actualReportFile.writeText(reportBuilder.toString())

        if (result.hasParseErrors) {
            val errorsLog = result.parseErrors.joinToString("\n") {
                "  ${it.file}:${it.line}: ${it.message}"
            }
            throw GradleException(
                "behaveLint: ${result.parseErrors.size} unparseable feature(s):\n$errorsLog\n" +
                    "See full report at: ${actualReportFile.absolutePath}",
            )
        }
    }
}
