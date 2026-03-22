package io.mcol.behave.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import java.io.File

private enum class Kind { PLACEHOLDER, QUOTED, VARIABLE }
private data class Tok(val pos: Int, val kind: Kind, val name: String)

class BehaveProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>,
    private val projectDir: String,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val featureAnnotation = "io.mcol.behave.annotations.BehaveFeature"
        val symbols = resolver.getSymbolsWithAnnotation(featureAnnotation)
            .filterIsInstance<KSClassDeclaration>()

        for (classDecl in symbols) {
            processClass(classDecl, resolver)
        }

        return emptyList()
    }

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

        // Parse feature file
        val parsed = FeatureFileParser.parse(featureFile.readText())

        // Resolve type mappings
        val typeMappings = resolveTypeMappings(behaveTypes, resolver)

        // Generate method names with collision resolution
        val stepPairs = parsed.steps.map { it.keyword to it.text }
        val methodNames = MethodNameGenerator.resolveCollisions(stepPairs)

        // Build GeneratedStep list
        val generatedSteps = parsed.steps.mapIndexed { i, step ->
            val methodName = methodNames[i]
            val params = resolveParams(step, typeMappings, classDecl)
            val rawExpr = io.mcol.behave.ksp.CodeGenerator.escapeStepExpression(
                io.mcol.behave.ksp.CodeGenerator.replaceOutlineVariables(
                    io.mcol.behave.ksp.CodeGenerator.replaceQuotedLiterals(step.text)
                )
            )
            io.mcol.behave.ksp.CodeGenerator.GeneratedStep(
                methodName = methodName,
                params = params,
                stepExpression = rawExpr,
                originalKeyword = step.keyword,
                originalText = step.text,
            )
        }

        // Validate concrete values against declared placeholder types
        validateTypes(generatedSteps, parsed.allStepInstances, classDecl)

        // Generate Row class descriptors for DataTable steps.
        // Auto-generated row classes (simple name ending in "Row") are emitted as data classes.
        // User-defined types (FQN containing ".") are included for field-mapping code but not re-emitted.
        val rowClasses = generatedSteps
            .filter { step ->
                step.params.any { param ->
                    val innerType = param.typeName.removePrefix("List<").removeSuffix(">")
                    param.typeName.startsWith("List<") && innerType.endsWith("Row")
                }
            }
            .map { step ->
                val listParam = step.params.first { it.typeName.startsWith("List<") }
                val rowClassName = listParam.typeName.removePrefix("List<").removeSuffix(">")
                val isUserDefined = rowClassName.contains(".")
                // Find the step's DataTable columns; each cell is a String (DataTable rows are Map<String,String>)
                val originalStep = parsed.steps.first { it.keyword == step.originalKeyword && it.text == step.originalText }
                val rowParams = originalStep.tableColumns.map { col ->
                    io.mcol.behave.ksp.CodeGenerator.StepParam(name = col, typeName = "String")
                }
                io.mcol.behave.ksp.CodeGenerator.GeneratedRowClass(
                    name = rowClassName,
                    params = rowParams,
                    shouldEmit = !isUserDefined,
                )
            }

        val interfaceName = "${className}Spec"
        val iface = io.mcol.behave.ksp.CodeGenerator.GeneratedInterface(
            packageName = packageName,
            interfaceName = interfaceName,
            implementingClassName = className,
            steps = generatedSteps,
            rowClasses = rowClasses,
        )

        val source = io.mcol.behave.ksp.CodeGenerator.render(iface)

        val outputFile = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = false),
            packageName = packageName,
            fileName = interfaceName,
        )
        outputFile.writer().use { it.write(source) }
    }

    private fun resolveTypeMappings(
        behaveTypes: List<Triple<String, KSType?, List<String>>>,
        resolver: Resolver,
    ): List<io.mcol.behave.ksp.CodeGenerator.TypeMapping> {
        return behaveTypes.mapNotNull { (placeholder, type, fields) ->
            val typeDecl = type?.declaration as? KSClassDeclaration ?: return@mapNotNull null
            val typeName = buildString {
                val pkg = typeDecl.packageName.asString()
                if (pkg.isNotEmpty()) { append(pkg); append(".") }
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
                    )
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
                Kind.QUOTED -> "String" to tok.name   // tok.name is variable name or "string"
                Kind.VARIABLE -> "String" to tok.name
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
    ) {
        if (allRawSteps.isEmpty()) return

        for (genStep in generatedSteps) {
            val placeholderTypes = TypeValidator.extractPlaceholderTypes(genStep.stepExpression)
            if (placeholderTypes.isEmpty()) continue

            val normalisedGen = FeatureFileParser.normalise(genStep.originalKeyword, genStep.originalText)
            val matchingRaw = allRawSteps.filter { raw ->
                FeatureFileParser.normalise(raw.keyword, raw.text) == normalisedGen
            }

            for (raw in matchingRaw) {
                val concreteValues = TypeValidator.extractConcreteValues(raw.text, genStep.originalText)
                for ((i, value) in concreteValues.withIndex()) {
                    if (i >= placeholderTypes.size) break
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
