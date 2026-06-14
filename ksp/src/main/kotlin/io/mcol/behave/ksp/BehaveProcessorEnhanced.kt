package io.mcol.behave.ksp

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import java.io.File

/**
 * Enhanced KSP Processor with Two-Pass Registry System
 *
 * PASS 1: Builds a global registry of all steps across all features
 * PASS 2: Analyzes each feature and generates code with inheritance + reuse detection
 *
 * Benefits:
 * - Automatically detects inherited vs new steps
 * - Reports code reuse percentage
 * - Generates interfaces with trait inheritance
 * - Minimizes developer implementation burden
 */
internal class BehaveProcessorEnhanced(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
    private val projectDir: String,
) : SymbolProcessor {

    private val globalStepRegistry = mutableMapOf<String, StepInfo>()
    private val featureAnalyses = mutableListOf<FeatureAnalysis>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val featureAnnotation = "io.mcol.behave.annotations.BehaveFeature"
        val symbols = resolver.getSymbolsWithAnnotation(featureAnnotation)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.validate() }
            .toList()

        if (symbols.isEmpty()) return emptyList()

        // PASS 1: Build global step registry from all features
        buildGlobalStepRegistry(symbols)

        // PASS 2: Analyze and generate code for each feature
        symbols.forEach { classDecl ->
            try {
                val analysis = analyzeFeature(classDecl)
                featureAnalyses.add(analysis)
            } catch (e: Exception) {
                logger.error("Failed to analyze ${classDecl.qualifiedName}: ${e.message}", classDecl)
            }
        }

        // Generate aggregate report
        generateAggregateReport()

        return emptyList()
    }

    /**
     * PASS 1: Scan all @BehaveFeature classes and build registry of all steps
     */
    private fun buildGlobalStepRegistry(symbols: List<KSClassDeclaration>) {
        symbols.forEach { symbol ->
            try {
                val featurePath = getFeaturePath(symbol) ?: return@forEach
                val featureFile = File(featurePath)

                if (!featureFile.exists()) {
                    logger.warn("Feature file not found: $featurePath", symbol)
                    return@forEach
                }

                val steps = parseFeatureFile(featureFile)
                steps.forEach { step ->
                    val normalized = normalizeStep(step)
                    globalStepRegistry[normalized] = StepInfo(
                        text = step,
                        method = stepToMethodName(step),
                        className = symbol.qualifiedName?.asString() ?: "Unknown",
                        featurePath = featurePath,
                    )
                }

                logger.info("Registered ${steps.size} steps from $featurePath")
            } catch (e: Exception) {
                logger.warn("Error parsing feature file for ${symbol.qualifiedName}: ${e.message}")
            }
        }
    }

    /**
     * PASS 2: Analyze a single feature and detect inherited vs new steps
     */
    private fun analyzeFeature(symbol: KSClassDeclaration): FeatureAnalysis {
        val featurePath = getFeaturePath(symbol)
        require(featurePath != null) { "No feature path found for ${symbol.qualifiedName}" }

        val featureFile = File(featurePath)
        require(featureFile.exists()) { "Feature file not found: $featurePath" }

        val steps = parseFeatureFile(featureFile)
        val className = symbol.qualifiedName?.asString()
        require(className != null) { "No qualified name for class" }

        val analysis = FeatureAnalysis(
            className = className,
            featurePath = featurePath,
            interfaceName = "${symbol.simpleName.asString()}Spec",
        )

        // Analyze each step
        steps.forEach { step ->
            val normalized = normalizeStep(step)
            val methodName = stepToMethodName(step)

            if (globalStepRegistry.containsKey(normalized)) {
                val existing = globalStepRegistry[normalized]!!

                // Skip if it's from the same class (same feature)
                if (existing.className != className) {
                    analysis.inheritedSteps.add(
                        InheritedStep(
                            text = step,
                            method = existing.method,
                            fromClass = existing.className,
                            fromFeature = existing.featurePath,
                        ),
                    )
                }
            } else {
                // New step not found in registry
                analysis.newSteps.add(
                    NewStep(
                        text = step,
                        method = methodName,
                    ),
                )
            }
        }

        // Calculate efficiency
        val totalUnique = analysis.inheritedSteps.size + analysis.newSteps.size
        if (totalUnique > 0) {
            analysis.reusePercentage =
                (analysis.inheritedSteps.size * 100) / totalUnique
        }

        logger.info(
            "Feature ${analysis.interfaceName}: " +
                "${analysis.inheritedSteps.size} inherited, " +
                "${analysis.newSteps.size} new, " +
                "${analysis.reusePercentage}% reuse",
        )

        return analysis
    }

    /**
     * Generate an aggregate report of all features and their reuse rates
     */
    private fun generateAggregateReport() {
        val report = buildString {
            appendLine("╔══════════════════════════════════════════════════════════════╗")
            appendLine("║ KOTLIN-BEHAVE STEP ANALYSIS REPORT                           ║")
            appendLine("╠══════════════════════════════════════════════════════════════╣")
            appendLine()

            // Summary statistics
            val totalFeatures = featureAnalyses.size
            val totalInherited = featureAnalyses.sumOf { it.inheritedSteps.size }
            val totalNew = featureAnalyses.sumOf { it.newSteps.size }
            val avgReuse = if (featureAnalyses.isNotEmpty()) {
                featureAnalyses.map { it.reusePercentage }.average().toInt()
            } else {
                0
            }

            appendLine("SUMMARY")
            appendLine("─────────────────────────────────────────────────────────────")
            appendLine("Total features analyzed: $totalFeatures")
            appendLine("Total inherited steps: $totalInherited")
            appendLine("Total new steps: $totalNew")
            appendLine("Average reuse rate: $avgReuse%")
            appendLine()

            // Per-feature details
            appendLine("FEATURES (sorted by reuse rate)")
            appendLine("─────────────────────────────────────────────────────────────")

            featureAnalyses
                .sortedByDescending { it.reusePercentage }
                .forEach { analysis ->
                    val icon = when {
                        analysis.reusePercentage == 100 -> "🌟"
                        analysis.reusePercentage >= 75 -> "✅"
                        analysis.reusePercentage >= 50 -> "⚠️ "
                        else -> "❌"
                    }

                    appendLine(
                        "$icon ${analysis.interfaceName}: " +
                            "${analysis.inheritedSteps.size} inherited, " +
                            "${analysis.newSteps.size} new, " +
                            "${analysis.reusePercentage}% reuse",
                    )

                    // Top inherited traits
                    val topTraits = analysis.inheritedSteps
                        .groupBy { it.fromClass }
                        .entries
                        .sortedByDescending { it.value.size }
                        .take(3)

                    topTraits.forEach { (trait, steps) ->
                        appendLine("   └─ $trait (${steps.size} steps)")
                    }
                }

            appendLine()
            appendLine("╚══════════════════════════════════════════════════════════════╝")
        }

        logger.info(report)
    }

    private fun getFeaturePath(symbol: KSClassDeclaration): String? = symbol.annotations
        .find { it.shortName.asString() == "BehaveFeature" }
        ?.arguments
        ?.find { it.name?.asString() == "path" || it.name?.asString() == "featureFile" }
        ?.value as? String

    private fun parseFeatureFile(file: File): List<String> = file.readLines()
        .filter {
            it.trim().startsWith("Given") ||
                it.trim().startsWith("When") ||
                it.trim().startsWith("Then") ||
                it.trim().startsWith("And") ||
                it.trim().startsWith("But")
        }
        .map { it.trim() }

    private fun normalizeStep(step: String): String {
        // Remove step keyword and normalize to lowercase
        val withoutKeyword = step
            .replaceFirst(Regex("^(Given|When|Then|And|But)\\s+"), "")
            .lowercase()

        // Replace variables/placeholders with {}
        return withoutKeyword
            .replace(Regex("\"[^\"]*\""), "{}") // "quoted values"
            .replace(Regex("<[^>]*>"), "{}") // <variables>
            .replace(Regex("\\d+"), "{}") // numbers
            .trim()
    }

    private fun stepToMethodName(step: String): String {
        val cleaned = step
            .replaceFirst(Regex("^(Given|When|Then|And|But)\\s+"), "")
            .replace(Regex("[^a-zA-Z0-9]+"), " ")
            .trim()

        return cleaned.split(" ")
            .joinToString("") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
            .replaceFirstChar { it.lowercase() }
    }

    data class StepInfo(
        val text: String,
        val method: String,
        val className: String,
        val featurePath: String,
    )

    data class FeatureAnalysis(
        val className: String,
        val featurePath: String,
        val interfaceName: String,
        val inheritedSteps: MutableList<InheritedStep> = mutableListOf(),
        val newSteps: MutableList<NewStep> = mutableListOf(),
        var reusePercentage: Int = 0,
    )

    data class InheritedStep(
        val text: String,
        val method: String,
        val fromClass: String,
        val fromFeature: String,
    )

    data class NewStep(
        val text: String,
        val method: String,
    )
}
