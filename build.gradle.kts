import com.github.jk1.license.filter.DependencyFilter
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.license.report)
}

licenseReport {
    // Aggregate every subproject so the report covers the whole repo, not just root.
    projects = allprojects.toTypedArray()
    allowedLicensesFile = file("$rootDir/allowed-licenses.json")
    outputDir = "$rootDir/docs/licenses"
    // Normalise common license-name variants (e.g. "Apache 2.0" vs "Apache License, Version 2.0").
    filters = arrayOf<DependencyFilter>(LicenseBundleNormalizer())
    renderers = arrayOf<ReportRenderer>(InventoryHtmlReportRenderer("third-party-licenses.html"))
}

// `checkLicense` is intentionally NOT wired into the `check` lifecycle. Run it on demand
// (or from a release/CI workflow): `./gradlew checkLicense`. Local `check` stays fast.

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

// CVE pin — opentelemetry-api ≤ 1.61.0 is vulnerable to unbounded memory / CPU
// allocation during W3C Baggage parsing. The vuln is brought in transitively by
// org.jetbrains.kotlin:swift-export-embeddable (iOS targets). Kotlin's own roadmap
// hasn't bumped the dependency yet, so pin here. Drop once swift-export-embeddable
// ships with opentelemetry-api ≥ 1.62.0.
allprojects {
    configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.opentelemetry" && requested.name == "opentelemetry-api") {
                useVersion("1.62.0")
                because("CVE: unbounded baggage memory/CPU — patched in 1.62.0")
            }
        }
    }

    // Always run the test task — never UP-TO-DATE. Suites here are pure-Kotlin
    // (parser, code generator, KSP processor), all deterministic and quick (a few
    // seconds), so paying the re-run cost rules out the "is the green status
    // really fresh" doubt and matches Maven/surefire defaults consumers may
    // expect. Compile / KSP / jar tasks still cache normally.
    tasks.withType<Test>().configureEach {
        outputs.upToDateWhen { false }
    }
}

tasks.register("installGitHooks") {
    description = "Configures git to use .githooks/ directory"
    doLast {
        val exitCode =
            ProcessBuilder("git", "config", "core.hooksPath", ".githooks")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        check(exitCode == 0) { "git config core.hooksPath failed (exit $exitCode)" }
    }
}

tasks.named("prepareKotlinBuildScriptModel") {
    dependsOn("installGitHooks")
}

// Feature-file embedding — shared across modules whose tests need to read .feature files
// without runtime IO (e.g. iOS simulator sandbox). Each module gets its own GeneratedFeatures
// object populated with everything in src/commonTest/resources.
configure(listOf(project(":core"), project(":kotest"))) {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        val featureResourcesDir = layout.projectDirectory.dir("src/commonTest/resources")
        val generatedFeaturesDir = layout.buildDirectory.dir("generated/features/commonTest/kotlin")
        val projectName = project.name

        val generateTestFeatures =
            tasks.register("generateTestFeatures") {
                description = "Embeds .feature files into GeneratedFeatures.kt so tests can run without runtime IO."
                inputs.dir(featureResourcesDir).withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
                outputs.dir(generatedFeaturesDir)

                doLast {
                    val outFile =
                        generatedFeaturesDir
                            .get()
                            .file("io/mcol/behave/generated/GeneratedFeatures.kt")
                            .asFile
                    outFile.parentFile.mkdirs()

                    val baseDir = featureResourcesDir.asFile
                    val entries =
                        baseDir
                            .walkTopDown()
                            .filter { it.isFile && it.extension == "feature" }
                            .sortedBy { it.invariantSeparatorsPath }
                            .map { file ->
                                val path = file.relativeTo(baseDir).invariantSeparatorsPath
                                val text = file.readText()

                                fun escape(value: String): String =
                                    value
                                        .replace("\\", "\\\\")
                                        .replace("\"", "\\\"")
                                        .replace("\$", "\\\$")
                                        .replace("\r", "\\r")
                                        .replace("\n", "\\n")
                                        .replace("\t", "\\t")

                                val literal = "\"${escape(text)}\""
                                "    FeatureRegistry.register(\"${escape(path)}\", $literal)"
                            }.joinToString("\n")

                    outFile.writeText(
                        """
                        // Auto-generated by :$projectName:generateTestFeatures. Do not edit.
                        package io.mcol.behave.generated

                        import io.mcol.behave.runner.FeatureRegistry

                        object GeneratedFeatures {
                            fun install() {
                        $entries
                            }
                        }

                        """.trimIndent(),
                    )
                }
            }

        extensions
            .getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            .sourceSets
            .named("commonTest") {
                kotlin.srcDir(generateTestFeatures)
            }
    }
}

fun kotlinStringLiteral(value: String): String {
    val escaped =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\\$")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    return "\"$escaped\""
}
