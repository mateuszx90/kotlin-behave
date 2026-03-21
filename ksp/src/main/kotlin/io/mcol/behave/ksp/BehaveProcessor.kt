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
                io.mcol.behave.ksp.CodeGenerator.replaceOutlineVariables(step.text)
            )
            io.mcol.behave.ksp.CodeGenerator.GeneratedStep(
                methodName = methodName,
                params = params,
                stepExpression = rawExpr,
                originalKeyword = step.keyword,
                originalText = step.text,
            )
        }

        // Generate Row classes for multi-group DataTable steps
        val rowClasses = generatedSteps
            .filter { step -> step.params.any { it.typeName.startsWith("List<") && it.typeName.endsWith("Row>") } }
            .map { step ->
                val listParam = step.params.first { it.typeName.startsWith("List<") }
                val rowClassName = listParam.typeName.removePrefix("List<").removeSuffix(">")
                // Find the step's DataTable columns and map to the registered types
                val originalStep = parsed.steps.first { it.keyword == step.originalKeyword && it.text == step.originalText }
                val rowParams = originalStep.tableColumns.map { col ->
                    val typeMapping = typeMappings.firstOrNull { tm ->
                        (tm.placeholder == col) || (tm.fields.isNotEmpty() && col in tm.fields) ||
                        (tm.placeholder.isEmpty() && tm.fields.isEmpty() && col in tm.fields)
                    }
                    io.mcol.behave.ksp.CodeGenerator.StepParam(
                        name = col,
                        typeName = typeMapping?.typeName ?: "String",
                    )
                }
                io.mcol.behave.ksp.CodeGenerator.GeneratedRowClass(rowClassName, rowParams)
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
        if (step.hasDataTable) {
            return resolveDataTableParams(step, typeMappings)
        }
        return resolveInlineParams(step.text, typeMappings)
    }

    private fun resolveInlineParams(
        text: String,
        typeMappings: List<io.mcol.behave.ksp.CodeGenerator.TypeMapping>,
    ): List<io.mcol.behave.ksp.CodeGenerator.StepParam> {
        // Extract all tokens: {placeholder} and <variable>
        val placeholderTokens = Regex("\\{([^}]+)}").findAll(text).map { it.groupValues[1] }.toList()
        val variableTokens = Regex("<([^>]+)>").findAll(text).map { it.groupValues[1] }.toList()

        val params = mutableListOf<io.mcol.behave.ksp.CodeGenerator.StepParam>()
        val usedPlaceholders = mutableSetOf<String>()

        // Priority 1: field-explicit type mappings
        // Priority 2: field-auto-detect type mappings
        // Priority 3: placeholder type mappings
        val fieldMappings = typeMappings.filter { it.fields.isNotEmpty() || (it.placeholder.isEmpty() && it.fields.isEmpty()) }
        val placeholderMappings = typeMappings.filter { it.placeholder.isNotEmpty() }

        // Find field-based matches
        for (mapping in fieldMappings) {
            val allMatch = mapping.fields.isNotEmpty() && mapping.fields.all { it in placeholderTokens }
            if (allMatch) {
                params.add(
                    io.mcol.behave.ksp.CodeGenerator.StepParam(
                        name = mapping.typeName.substringAfterLast('.').replaceFirstChar { it.lowercase() },
                        typeName = mapping.typeName,
                    )
                )
                usedPlaceholders.addAll(mapping.fields)
            }
        }

        // Remaining placeholders
        val remainingPlaceholders = placeholderTokens.filter { it !in usedPlaceholders }
        val nameCounters = mutableMapOf<String, Int>()

        for (p in remainingPlaceholders) {
            val customMapping = placeholderMappings.firstOrNull { it.placeholder == p }
            val (typeName, baseName) = when {
                customMapping != null -> customMapping.typeName to p
                p in io.mcol.behave.ksp.CodeGenerator.builtinTypes ->
                    io.mcol.behave.ksp.CodeGenerator.builtinTypes[p]!! to p
                else -> "String" to p
            }
            val count = nameCounters.getOrDefault(baseName, 0)
            val paramName = if (count == 0 && placeholderTokens.count { it == p } == 1) baseName
                           else "$baseName$count"
            nameCounters[baseName] = count + 1
            params.add(io.mcol.behave.ksp.CodeGenerator.StepParam(paramName, typeName))
        }

        // <variable> tokens → String params
        for (v in variableTokens) {
            params.add(io.mcol.behave.ksp.CodeGenerator.StepParam(v, "String"))
        }

        return params
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
