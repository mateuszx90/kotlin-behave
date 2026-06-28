package io.mcol.behave.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * `behaveLint` — parses every `.feature` under [featureDir], failing the build on any
 * unparseable file, and warns about step-definition methods declared in [stepSources] that no
 * feature references (dead steps).
 */
abstract class BehaveLintTask : DefaultTask() {

    @get:InputDirectory
    abstract val featureDir: DirectoryProperty

    @get:InputFiles
    @get:SkipWhenEmpty
    abstract val stepSources: ConfigurableFileCollection

    @TaskAction
    fun lint() {
        val featureFiles = featureDir.asFile.get()
            .walkTopDown()
            .filter { it.isFile && it.extension == "feature" }
            .toList()
        val features = featureFiles.associate { it.absolutePath to it.readText() }
        val sources = stepSources.files.filter { it.isFile && it.extension == "kt" }.map { it.readText() }

        val result = BehaveLint.analyze(features, sources)

        for (dead in result.deadSteps) {
            logger.warn("behaveLint: dead step definition '$dead' is not referenced by any feature")
        }

        if (result.hasParseErrors) {
            val report = result.parseErrors.joinToString("\n") { "  ${it.file}:${it.line}: ${it.message}" }
            throw GradleException("behaveLint: ${result.parseErrors.size} unparseable feature(s):\n$report")
        }

        logger.lifecycle(
            "behaveLint: ${features.size} feature(s) parsed, ${result.deadSteps.size} dead step(s) reported.",
        )
    }
}
