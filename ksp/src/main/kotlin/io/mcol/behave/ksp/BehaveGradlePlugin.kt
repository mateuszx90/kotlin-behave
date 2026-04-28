package io.mcol.behave.ksp

import org.gradle.api.Plugin
import org.gradle.api.Project

class BehaveGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.afterEvaluate {
            val featureDir = project.findProperty("behave.featureDir") as? String
                ?: readFeatureDirFromKspArgs(project)
                ?: "src/test/resources"

            project.tasks.matching { it.name.startsWith("ksp") }.configureEach { task ->
                task.inputs.dir(project.layout.projectDirectory.dir(featureDir))
            }
        }
    }

    private fun readFeatureDirFromKspArgs(project: Project): String? = runCatching {
        val kspExt = project.extensions.findByName("ksp") ?: return null
        val argsMethod = kspExt.javaClass.methods.firstOrNull { it.name == "getArguments" }
            ?: return null

        @Suppress("UNCHECKED_CAST")
        val args = argsMethod.invoke(kspExt) as? Map<String, String> ?: return null
        args["behave.featureDir"]
    }.getOrNull()
}
