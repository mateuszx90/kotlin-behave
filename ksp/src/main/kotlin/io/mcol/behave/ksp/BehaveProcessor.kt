package io.mcol.behave.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType
import java.io.File

private enum class Kind { PLACEHOLDER, QUOTED, VARIABLE, NUMBER }
private data class Tok(val pos: Int, val kind: Kind, val name: String)

/**
 * Key for matching a generated step method against a mixin method.
 * name + canonical parameter type names. Reading the docs: param types are simple names
 * (kotlin.* stripped). Suspend modifier is not part of the key — mixin authors are
 * expected to declare `suspend fun`.
 */
private data class MixinMethodKey(val name: String, val paramTypes: List<String>)

private data class MixinInfo(val qualifiedName: String, val simpleName: String)

class BehaveProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
    private val projectDir: String,
) : SymbolProcessor {

    private var mixinRegistry: Map<MixinMethodKey, MixinInfo> = emptyMap()

    // Files that declare `@StepsMixin` interfaces. Every generated spec must declare a
    // dependency on these (and run in aggregating mode) so that adding, removing, or
    // editing a mixin invalidates the cached spec output.
    private var mixinSourceFiles: List<KSFile> = emptyList()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val mixins = collectMixins(resolver)
        mixinRegistry = buildMixinRegistry(mixins)
        mixinSourceFiles = mixins.mapNotNull { it.containingFile }.distinct()

        val featureAnnotation = "io.mcol.behave.annotations.BehaveFeature"
        val symbols = resolver.getSymbolsWithAnnotation(featureAnnotation)
            .filterIsInstance<KSClassDeclaration>()

        for (classDecl in symbols) {
            processClass(classDecl, resolver)
        }

        return emptyList()
    }

    private fun collectMixins(resolver: Resolver): List<KSClassDeclaration> {
        val mixinAnnotation = "io.mcol.behave.annotations.StepsMixin"
        return resolver.getSymbolsWithAnnotation(mixinAnnotation)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.classKind == ClassKind.INTERFACE }
            .toList()
    }

    private fun buildMixinRegistry(mixins: List<KSClassDeclaration>): Map<MixinMethodKey, MixinInfo> {
        val registry = mutableMapOf<MixinMethodKey, MixinInfo>()
        for (mixin in mixins) {
            val qName = mixin.qualifiedName?.asString()
            if (qName == null) {
                logger.warn(
                    "@StepsMixin interface '${mixin.simpleName.asString()}' has no qualified name " +
                        "(likely in the default package) — skipped. Move it into a named package.",
                    mixin,
                )
                continue
            }
            val info = MixinInfo(qualifiedName = qName, simpleName = mixin.simpleName.asString())
            for (func in mixin.getDeclaredFunctions()) {
                if (func.isAbstract) continue // mixin entries must carry a default body to be useful
                val methodName = func.simpleName.asString()
                val paramTypes = func.parameters.map { canonicalTypeName(it.type.resolve()) }
                val key = MixinMethodKey(methodName, paramTypes)
                val prior = registry[key]
                if (prior != null && prior.qualifiedName != qName) {
                    logger.warn(
                        "Step method '$methodName(${paramTypes.joinToString()})' is declared by both " +
                            "${prior.qualifiedName} and $qName — first one wins ($qName ignored).",
                        mixin,
                    )
                    continue
                }
                registry[key] = info
            }
        }
        return registry
    }

    /** Reduce KSP type names to the same form `CodeGenerator` uses on the generated side. */
    private fun canonicalTypeName(type: KSType): String {
        val raw = type.declaration.qualifiedName?.asString()
            ?: type.declaration.simpleName.asString()
        val stripped = raw.removePrefix("kotlin.").removePrefix("kotlin.collections.")
        val baseShort = if (stripped.startsWith("kotlin.")) stripped.substringAfterLast('.') else stripped
        if (type.arguments.isEmpty()) return baseShort
        val args = type.arguments.joinToString(", ") {
            it.type?.resolve()?.let { t -> canonicalTypeName(t) } ?: "*"
        }
        return "$baseShort<$args>"
    }

    private fun canonicalGeneratedParamType(typeName: String): String = typeName.removePrefix("kotlin.").removePrefix("kotlin.collections.")

    private fun processClass(classDecl: KSClassDeclaration, resolver: Resolver) {
        val className = classDecl.simpleName.asString()
        val packageName = classDecl.packageName.asString()

        // Read @BehaveFeature path
        val featureAnnotation = classDecl.annotations.first {
            it.shortName.asString() == "BehaveFeature"
        }
        val featurePath = featureAnnotation.arguments
            .first { it.name?.asString() == "path" }
            .value as String
        val generateTest = featureAnnotation.arguments
            .firstOrNull { it.name?.asString() == "generateTest" }
            ?.value as? Boolean ?: true

        // Check which lifecycle interfaces the class implements
        val superTypeNames = classDecl.superTypes.map { ref ->
            ref.resolve().declaration.qualifiedName?.asString()
        }.toSet()
        val hasScenarioRunner = "io.mcol.behave.steps.ScenarioRunner" in superTypeNames
        val hasBeforeScenario = "io.mcol.behave.steps.BeforeScenario" in superTypeNames ||
            "io.mcol.behave.steps.ScenarioHooks" in superTypeNames
        val hasAfterScenario = "io.mcol.behave.steps.AfterScenario" in superTypeNames ||
            "io.mcol.behave.steps.ScenarioHooks" in superTypeNames

        // Resolve feature file
        val featureDir = options["behave.featureDir"] ?: "src/commonTest/resources"
        val featureFile = File(projectDir, "$featureDir/$featurePath")
        if (!featureFile.exists()) {
            logger.error(
                "Feature file not found: ${featureFile.absolutePath}",
                classDecl,
            )
            return
        }

        // Read @BehaveType annotations
        val behaveTypes = classDecl.annotations
            .filter { it.shortName.asString() == "BehaveType" }
            .map { ann ->
                val placeholder = ann.arguments.firstOrNull { it.name?.asString() == "placeholder" }?.value as? String ?: ""
                val type = ann.arguments.firstOrNull { it.name?.asString() == "type" }?.value as? KSType
                val fields = (ann.arguments.firstOrNull { it.name?.asString() == "fields" }?.value as? List<*>)
                    ?.mapNotNull { it as? String } ?: emptyList()
                Triple(placeholder, type, fields)
            }.toList()

        // Validate @BehaveType entries
        for ((placeholder, type, fields) in behaveTypes) {
            if (type == null) continue
            val typeDecl = type.declaration as? KSClassDeclaration ?: continue
            val constructorParams = typeDecl.primaryConstructor?.parameters ?: emptyList()
            if (placeholder.isEmpty() && fields.isEmpty() && constructorParams.isEmpty()) {
                logger.error(
                    "@BehaveType auto-detect on type '${typeDecl.simpleName.asString()}' which has zero primary constructor parameters",
                    classDecl,
                )
            }
        }

        // Read @BehaveCast from implementing class methods
        val behaveCasts = mutableMapOf<String, Set<Int>>() // methodName -> set of param indices with @BehaveCast
        for (func in classDecl.getDeclaredFunctions()) {
            val funcName = func.simpleName.asString()
            val castIndices = func.parameters.mapIndexedNotNull { idx, param ->
                val hasCast = param.annotations.any { it.shortName.asString() == "BehaveCast" }
                if (hasCast) idx else null
            }.toSet()
            if (castIndices.isNotEmpty()) {
                behaveCasts[funcName] = castIndices
            }
        }

        // Parse feature file
        val parsed = FeatureFileParser.parse(featureFile.readText())
        for (error in parsed.errors) {
            logger.error("${featureFile.toURI()}:${error.line}: ${error.message}", classDecl)
        }
        if (parsed.hasErrors) return

        // Resolve type mappings
        val typeMappings = resolveTypeMappings(behaveTypes, resolver)

        // Generate method names with collision resolution
        val stepPairs = parsed.steps.map { it.keyword to it.text }
        val methodNames = MethodNameGenerator.resolveCollisions(stepPairs)

        // Build a map from normalised key to all pre-dedup template texts (for type unification)
        val templatesByNormKey = parsed.allStepTemplates.groupBy {
            FeatureFileParser.normalise(it.keyword, it.text)
        }

        // Build GeneratedStep list
        val generatedSteps = parsed.steps.mapIndexed { i, step ->
            buildGeneratedStep(step, methodNames[i], typeMappings, templatesByNormKey, behaveCasts, classDecl)
        }

        // Validate concrete values against declared placeholder types
        validateTypes(generatedSteps, parsed.allStepInstances, classDecl, behaveCasts)

        val rowClasses = buildRowClasses(generatedSteps, parsed.steps)

        // Match each generated step against the mixin registry. A step whose
        // (methodName, paramTypes) matches a @StepsMixin method becomes "inherited" —
        // omitted from the spec's abstract methods, with the spec extending that mixin.
        val inheritedByMethod = mutableMapOf<String, String>() // method name -> qualified mixin
        val mixinSimpleNames = linkedSetOf<String>()
        for (step in generatedSteps) {
            val paramTypes = step.params.map { canonicalGeneratedParamType(it.typeName) }
            val key = MixinMethodKey(step.methodName, paramTypes)
            val info = mixinRegistry[key] ?: continue
            inheritedByMethod[step.methodName] = info.qualifiedName
            mixinSimpleNames.add(info.qualifiedName)
        }

        val interfaceName = "${className}Spec"
        val iface = io.mcol.behave.ksp.CodeGenerator.GeneratedInterface(
            packageName = packageName,
            interfaceName = interfaceName,
            implementingClassName = className,
            featurePath = featurePath,
            generateTest = generateTest,
            hasScenarioRunner = hasScenarioRunner,
            hasBeforeScenario = hasBeforeScenario,
            hasAfterScenario = hasAfterScenario,
            steps = generatedSteps,
            rowClasses = rowClasses,
            inheritedMixins = mixinSimpleNames.toList(),
            inheritedMethodNames = inheritedByMethod.keys.toSet(),
        )

        val source = io.mcol.behave.ksp.CodeGenerator.render(iface)

        // Track every source that can change what the generated spec looks like:
        //  - the @BehaveFeature class file (always)
        //  - every @StepsMixin file in the compilation (any change affects what's inherited)
        // aggregating = true so that adding a NEW mixin file later also invalidates this output.
        val depSources = (listOfNotNull(classDecl.containingFile) + mixinSourceFiles).distinct()
        val outputFile = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, sources = depSources.toTypedArray()),
            packageName = packageName,
            fileName = interfaceName,
        )
        outputFile.writer().use { it.write(source) }
    }

    private fun buildGeneratedStep(
        step: FeatureFileParser.ParsedStep,
        methodName: String,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
        templatesByNormKey: Map<String, List<FeatureFileParser.ParsedStep>>,
        behaveCasts: Map<String, Set<Int>>,
        classDecl: KSClassDeclaration,
    ): io.mcol.behave.ksp.CodeGenerator.GeneratedStep {
        val params = resolveParams(step, typeMappings, classDecl)
        var rawExpr = io.mcol.behave.ksp.CodeGenerator.escapeStepExpression(
            io.mcol.behave.ksp.CodeGenerator.replaceOutlineVariables(
                io.mcol.behave.ksp.CodeGenerator.replaceNumberLiterals(
                    io.mcol.behave.ksp.CodeGenerator.replaceQuotedLiterals(step.text),
                ),
            ),
        )
        if (rawExpr.contains("{word}")) {
            val normKey = FeatureFileParser.normalise(step.keyword, step.text)
            val siblings = templatesByNormKey[normKey] ?: emptyList()
            if (siblings.any { sibling -> Regex("\"[^\"]*\"").containsMatchIn(sibling.text) }) {
                rawExpr = rawExpr.replace("{word}", "{string}")
            }
        }
        val castIndices = behaveCasts[methodName] ?: emptySet()
        val castParams = mutableMapOf<Int, String>()
        for (idx in castIndices) {
            if (idx < params.size) castParams[idx] = params[idx].typeName
        }
        return io.mcol.behave.ksp.CodeGenerator.GeneratedStep(
            methodName = methodName,
            params = params,
            stepExpression = rawExpr,
            originalKeyword = step.keyword,
            originalText = step.text,
            castParams = castParams,
        )
    }

    private fun buildRowClasses(
        generatedSteps: List<io.mcol.behave.ksp.CodeGenerator.GeneratedStep>,
        parsedSteps: List<FeatureFileParser.ParsedStep>,
    ): List<io.mcol.behave.ksp.CodeGenerator.GeneratedRowClass> = generatedSteps
        .filter { step ->
            step.params.any { param ->
                val innerType = param.typeName.removePrefix("List<").removeSuffix(">")
                param.typeName.startsWith("List<") && innerType.endsWith("Row")
            }
        }
        .map { step ->
            val listParam = step.params.first { it.typeName.startsWith("List<") }
            val rowClassName = listParam.typeName.removePrefix("List<").removeSuffix(">")
            val originalStep = parsedSteps.first { it.keyword == step.originalKeyword && it.text == step.originalText }
            val rowParams = originalStep.tableColumns.map { col ->
                io.mcol.behave.ksp.CodeGenerator.StepParam(name = col, typeName = "String")
            }
            io.mcol.behave.ksp.CodeGenerator.GeneratedRowClass(
                name = rowClassName,
                params = rowParams,
                shouldEmit = !rowClassName.contains("."),
            )
        }

    private fun resolveTypeMappings(
        behaveTypes: List<Triple<String, KSType?, List<String>>>,
        resolver: Resolver,
    ): List<io.mcol.behave.ksp.CodeGenerator.TypeMapping> {
        return behaveTypes.mapNotNull { (placeholder, type, fields) ->
            val typeDecl = type?.declaration as? KSClassDeclaration ?: return@mapNotNull null
            val typeName = buildString {
                val pkg = typeDecl.packageName.asString()
                if (pkg.isNotEmpty()) {
                    append(pkg)
                    append(".")
                }
                append(typeDecl.simpleName.asString())
            }
            val constructorParams = typeDecl.primaryConstructor?.parameters
                ?.map { it.name?.asString() ?: "" } ?: emptyList()
            val resolvedFields = if (fields.isNotEmpty()) fields else constructorParams
            io.mcol.behave.ksp.CodeGenerator.TypeMapping(
                placeholder = placeholder,
                typeName = typeName,
                fields = resolvedFields,
            )
        }
    }

    private fun resolveParams(
        step: FeatureFileParser.ParsedStep,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
        classDecl: KSClassDeclaration,
    ): List<io.mcol.behave.ksp.CodeGenerator.StepParam> {
        // Always extract inline params (quoted literals, {placeholders}, <variables>)
        val inlineParams = resolveInlineParams(step.text, typeMappings)
        if (!step.hasDataTable) return inlineParams
        // DataTable steps: prepend any inline string params before the rows param
        return inlineParams + resolveDataTableParams(step, typeMappings)
    }

    private fun resolveInlineParams(
        text: String,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
    ): List<io.mcol.behave.ksp.CodeGenerator.StepParam> {
        // Find quoted string ranges first so we can exclude <variable> found inside "..."
        val quotedRanges = Regex("\"[^\"]*\"").findAll(text).map { it.range }.toList()
        fun inQuotes(pos: Int) = quotedRanges.any { pos in it }

        // Collect all dynamic tokens in left-to-right order

        val toks = buildList {
            Regex("\\{([^}]+)}").findAll(text).forEach {
                add(Tok(it.range.first, Kind.PLACEHOLDER, it.groupValues[1]))
            }
            // "..." — if content is exactly <variable>, use the variable name; else use "string"
            Regex("\"([^\"]*)\"").findAll(text).forEach {
                val inner = it.groupValues[1]
                val varName = Regex("^<([^>]+)>$").find(inner)?.groupValues?.get(1)
                add(Tok(it.range.first, Kind.QUOTED, varName ?: "string"))
            }
            // <variable> tokens NOT inside "..."
            Regex("<([^>]+)>").findAll(text).filter { !inQuotes(it.range.first) }.forEach {
                add(Tok(it.range.first, Kind.VARIABLE, it.groupValues[1]))
            }
            // Standalone number literals (not inside quotes or placeholders)
            // Doubles first, then integers — same order as CodeGenerator.replaceNumberLiterals
            val coveredRanges = map { it.pos..(it.pos) } // approximate; refine below
            Regex("""(?<!\S)-?\d+\.\d+(?!\S)""").findAll(text)
                .filter { m -> !inQuotes(m.range.first) && none { tok -> m.range.first in (tok.pos..tok.pos) } }
                .forEach { add(Tok(it.range.first, Kind.NUMBER, "double")) }
            Regex("""(?<!\S)-?\d+(?!\S)""").findAll(text)
                .filter { m ->
                    !inQuotes(m.range.first) &&
                        none { tok -> m.range.first >= tok.pos && m.range.first <= tok.pos + 10 && tok.kind == Kind.NUMBER }
                }
                .forEach { add(Tok(it.range.first, Kind.NUMBER, "int")) }
        }.sortedBy { it.pos }

        val placeholderNames = toks.filter { it.kind == Kind.PLACEHOLDER }.map { it.name }

        // Apply field-based type mappings first (group multiple {placeholders} into one typed param)
        val params = mutableListOf<io.mcol.behave.ksp.CodeGenerator.StepParam>()
        val usedPlaceholders = mutableSetOf<String>()
        for (mapping in typeMappings.filter { it.fields.isNotEmpty() }) {
            if (mapping.fields.all { it in placeholderNames }) {
                params.add(
                    io.mcol.behave.ksp.CodeGenerator.StepParam(
                        name = mapping.typeName.substringAfterLast('.').replaceFirstChar { it.lowercase() },
                        typeName = mapping.typeName,
                    ),
                )
                usedPlaceholders.addAll(mapping.fields)
            }
        }

        // Process remaining tokens left-to-right
        val placeholderMappings = typeMappings.filter { it.placeholder.isNotEmpty() }
        val nameCounters = mutableMapOf<String, Int>()

        for (tok in toks) {
            if (tok.kind == Kind.PLACEHOLDER && tok.name in usedPlaceholders) continue

            val (typeName, baseName) = when (tok.kind) {
                Kind.PLACEHOLDER -> {
                    val custom = placeholderMappings.firstOrNull { it.placeholder == tok.name }
                    when {
                        custom != null -> custom.typeName to tok.name
                        tok.name in io.mcol.behave.ksp.CodeGenerator.builtinTypes ->
                            io.mcol.behave.ksp.CodeGenerator.builtinTypes[tok.name]!! to tok.name
                        else -> "String" to tok.name
                    }
                }
                Kind.QUOTED -> "String" to tok.name // tok.name is variable name or "string"
                Kind.VARIABLE -> "String" to tok.name
                Kind.NUMBER -> {
                    val kotlinType = io.mcol.behave.ksp.CodeGenerator.builtinTypes[tok.name] ?: "Int"
                    kotlinType to tok.name
                }
            }

            val idx = nameCounters.getOrDefault(baseName, 0)
            val totalWithBase = toks.count { it.name == baseName }
            val paramName = if (idx == 0 && totalWithBase == 1) baseName else "$baseName$idx"
            nameCounters[baseName] = idx + 1
            params.add(io.mcol.behave.ksp.CodeGenerator.StepParam(paramName, typeName))
        }

        return params
    }

    private fun validateTypes(
        generatedSteps: List<io.mcol.behave.ksp.CodeGenerator.GeneratedStep>,
        allRawSteps: List<FeatureFileParser.RawStep>,
        classDecl: KSClassDeclaration,
        behaveCasts: Map<String, Set<Int>> = emptyMap(),
    ) {
        if (allRawSteps.isEmpty()) return

        for (genStep in generatedSteps) {
            val placeholderTypes = TypeValidator.extractPlaceholderTypes(genStep.stepExpression)
            if (placeholderTypes.isEmpty()) continue

            val castIndices = behaveCasts[genStep.methodName] ?: emptySet()

            val normalisedGen = FeatureFileParser.normalise(genStep.originalKeyword, genStep.originalText)
            val matchingRaw = allRawSteps.filter { raw ->
                FeatureFileParser.normalise(raw.keyword, raw.text) == normalisedGen
            }

            for (raw in matchingRaw) {
                val concreteValues = TypeValidator.extractConcreteValues(raw.text, genStep.originalText)
                for ((i, value) in concreteValues.withIndex()) {
                    if (i >= placeholderTypes.size) break
                    if (i in castIndices) continue // @BehaveCast suppresses validation
                    val expectedType = placeholderTypes[i]
                    val pattern = TypeValidator.typeValidationPatterns[expectedType] ?: continue
                    if (!pattern.matches(value)) {
                        logger.error(
                            "Type mismatch in scenario '${raw.scenarioName}': " +
                                "value '$value' does not match {$expectedType} in step '${raw.keyword} ${raw.text}'",
                            classDecl,
                        )
                    }
                }
            }
        }
    }

    private fun resolveDataTableParams(
        step: FeatureFileParser.ParsedStep,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
    ): List<io.mcol.behave.ksp.CodeGenerator.StepParam> {
        val columns = step.tableColumns
        if (columns.isEmpty()) {
            return listOf(io.mcol.behave.ksp.CodeGenerator.StepParam("rows", "List<io.mcol.behave.model.DataTableRow>"))
        }

        // Check if a single type covers all columns
        val singleMatch = typeMappings.firstOrNull { mapping ->
            when {
                mapping.fields.isNotEmpty() -> mapping.fields.toSet() == columns.toSet()
                mapping.placeholder.isEmpty() -> columns.all { col ->
                    mapping.fields.isEmpty() || col in mapping.fields
                }
                else -> false
            }
        }

        if (singleMatch != null && typeMappings.size == 1) {
            return listOf(io.mcol.behave.ksp.CodeGenerator.StepParam("rows", "List<${singleMatch.typeName}>"))
        }

        // Multiple groups → generate a Row class
        val methodName = MethodNameGenerator.generate(step.keyword, step.text)
        val rowClassName = methodName.replaceFirstChar { it.uppercase() } + "Row"
        return listOf(io.mcol.behave.ksp.CodeGenerator.StepParam("rows", "List<$rowClassName>"))
    }
}
