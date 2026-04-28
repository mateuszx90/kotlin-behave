plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/detekt.yml")
    source.setFrom(
        fileTree("core/src") { include("**/*.kt") },
        fileTree("kotest/src") { include("**/*.kt") },
        fileTree("annotations/src") { include("**/*.kt") },
        fileTree("ksp/src") { include("**/*.kt") },
    )
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.5.0").editorConfigOverride(
            mapOf(
                "ktlint_standard_package-name" to "disabled",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_no-wildcard-imports" to "disabled",
                "ktlint_standard_function-naming" to "disabled",
            ),
        )
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**")
        ktlint("1.5.0")
    }
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
