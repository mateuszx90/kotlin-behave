plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

tasks.register("installGitHooks") {
    description = "Configures git to use .githooks/ directory"
    doLast {
        exec { commandLine("git", "config", "core.hooksPath", ".githooks") }
    }
}

tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn("installGitHooks")
}
